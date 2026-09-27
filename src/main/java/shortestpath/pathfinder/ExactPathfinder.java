package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import shortestpath.pathfinder.exact.ExactForwardSearch;
import shortestpath.pathfinder.exact.PreparedHeuristic;
import shortestpath.pathfinder.exact.PreparedRoutingAccount;
import shortestpath.pathfinder.exact.ReverseLabels;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.pathfinder.exact.SiteGraph;
import shortestpath.pathfinder.exact.TargetOverlay;

/** ActiveSearch adapter for the exact core. */
public final class ExactPathfinder implements ActiveSearch
{
	private final PathfinderStats stats = new PathfinderStats();
	private final int start;
	private final Set<Integer> targets;
	private final Runnable completionCallback;
	private final CollisionMap collision;
	private final Supplier<RoutingStatic> routingStatic;
	private final PreparedRoutingAccount account;
	private SiteGraph graph;
	private final long cutoffMillis;
	private final double heuristicWeight;
	private final long accountPrepareNanos;
	private volatile long routingStaticNanos;
	private volatile long graphPrepareNanos;
	private final String failure;
	private volatile boolean cancelled;
	private volatile boolean done;
	private volatile List<PathStep> path;
	private volatile PathfinderResult result;
	private volatile ExactForwardSearch.Counters exactStats;
	private volatile long reverseSearchNanos;
	private volatile long heuristicPrepareNanos;
	private volatile long forwardSearchNanos;

	public ExactPathfinder(PathfinderConfig config, RoutingStatic routingStatic, int start, Set<Integer> targets,
		Runnable completionCallback)
	{
		this(config, constant(routingStatic), start, targets, completionCallback);
	}

	public ExactPathfinder(PathfinderConfig config, RoutingStatic routingStatic, int start, Set<Integer> targets,
		Runnable completionCallback, double heuristicWeight)
	{
		this(config, constant(routingStatic), start, targets, completionCallback, heuristicWeight);
	}

	/**
	 * Creates a search whose static routing data is obtained from {@code routingStatic} when the
	 * search runs, so a first-use build happens on the pathfinding thread rather than here.
	 */
	public ExactPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic, int start,
		Set<Integer> targets, Runnable completionCallback)
	{
		this(config, routingStatic, start, targets, completionCallback,
			config == null ? 1 : config.getExactHeuristicWeight());
	}

	public ExactPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic, int start,
		Set<Integer> targets, Runnable completionCallback, double heuristicWeight)
	{
		if (config == null || routingStatic == null || targets == null) throw new NullPointerException();
		if (!(heuristicWeight > 0) || !Double.isFinite(heuristicWeight))
			throw new IllegalArgumentException("heuristic weight must be positive and finite");
		this.start = start;
		this.targets = Set.copyOf(targets);
		this.completionCallback = completionCallback;
		this.collision = config.getMap();
		this.routingStatic = routingStatic;
		long phaseStarted = System.nanoTime();
		this.account = config.prepareExactRoutingAccount(true);
		this.accountPrepareNanos = System.nanoTime() - phaseStarted;
		this.cutoffMillis = config.getCalculationCutoffMillis();
		this.heuristicWeight = heuristicWeight;
		this.failure = null;
		this.path = List.of(new PathStep(start, false));
	}

	private static Supplier<RoutingStatic> constant(RoutingStatic routingStatic)
	{
		if (routingStatic == null) throw new NullPointerException();
		return () -> routingStatic;
	}

	private ExactPathfinder(int start, Set<Integer> targets, Runnable completionCallback, String failure)
	{
		this.start = start;
		this.targets = Set.copyOf(targets);
		this.completionCallback = completionCallback;
		this.collision = null;
		this.routingStatic = null;
		this.account = null;
		this.cutoffMillis = 0;
		this.heuristicWeight = 1;
		this.accountPrepareNanos = 0;
		this.failure = failure;
		this.path = List.of(new PathStep(start, false));
	}

	public static ExactPathfinder failed(int start, Set<Integer> targets, Runnable completionCallback, Throwable error)
	{
		String detail = error.getMessage();
		return new ExactPathfinder(start, targets, completionCallback,
			"Exact backend unavailable: " + (detail == null ? error.getClass().getSimpleName() : detail));
	}

	@Override
	public void cancel()
	{
		cancelled = true;
	}

	@Override
	public int getStart()
	{ return start;
	}

	@Override
	public Set<Integer> getTargets()
	{ return targets;
	}

	@Override
	public List<PathStep> getPath()
	{ return path;
	}

	@Override
	public boolean isDone()
	{ return done;
	}

	@Override
	public PathfinderResult getResult()
	{
		return stats.started && stats.ended ? result : null;
	}

	@Override
	public PathfinderStats getStats()
	{
		return stats.started && stats.ended ? stats : null;
	}

	/** Completed exact-search counters, or {@code null} while the search is running. */
	public ExactForwardSearch.Counters getExactStats()
	{
		return stats.started && stats.ended ? exactStats : null;
	}

	public long getAccountPrepareNanos()
	{ return accountPrepareNanos;
	}

	public long getRoutingStaticNanos()
	{ return routingStaticNanos;
	}

	public long getGraphPrepareNanos()
	{ return graphPrepareNanos;
	}

	public long getReverseSearchNanos()
	{ return reverseSearchNanos;
	}

	public long getHeuristicPrepareNanos()
	{ return heuristicPrepareNanos;
	}

	public long getForwardSearchNanos()
	{ return forwardSearchNanos;
	}

	@Override
	public void run()
	{
		stats.start();
		long started = System.nanoTime();
		try
		{
			if (failure != null)
			{
				exactStats = ExactForwardSearch.Counters.empty();
				result = new PathfinderResult(start, firstTarget(), false, path, start, PathfinderResult.NO_PATH_COST,
					0, 0, System.nanoTime() - started, PathTerminationReason.BACKEND_FAILURE, failure);
				return;
			}
			long phaseStarted = System.nanoTime();
			RoutingStatic staticData = routingStatic.get();
			routingStaticNanos = System.nanoTime() - phaseStarted;
			phaseStarted = System.nanoTime();
			graph = new SiteGraph(staticData, account);
			graphPrepareNanos = System.nanoTime() - phaseStarted;
			AtomicBoolean timedOut = new AtomicBoolean();
			// A zero cutoff means no cutoff for the exact backend.
			SearchDeadline deadline = cutoffMillis > 0 ? new SearchDeadline(cutoffMillis) : null;
			List<Integer> ordered = new ArrayList<>(targets);
			ordered.sort(Integer::compareUnsigned);
			ExactForwardSearch.Result best = null;
			ExactForwardSearch.Counters totalExactStats = null;
			int bestTarget = firstTarget();
			for (int target : ordered)
			{
				if (cancelled) break;
				TargetOverlay overlay = new TargetOverlay(graph, collision, target);
				phaseStarted = System.nanoTime();
				ReverseLabels reverse = ReverseLabels.compute(overlay);
				reverseSearchNanos += System.nanoTime() - phaseStarted;
				phaseStarted = System.nanoTime();
				PreparedHeuristic heuristic = PreparedHeuristic.prepare(overlay, reverse);
				heuristicPrepareNanos += System.nanoTime() - phaseStarted;
				phaseStarted = System.nanoTime();
				ExactForwardSearch.Result current = ExactForwardSearch.search(overlay, heuristic, start,
					() ->
					{
						if (cancelled) return true;
						if (deadline != null && deadline.expired())
						{
							timedOut.set(true);
							return true;
						}
						return false;
					}, heuristicWeight);
				forwardSearchNanos += System.nanoTime() - phaseStarted;
				stats.nodesChecked += current.counters().statesPopped();
				stats.transportsChecked += current.counters().transportCandidates();
				totalExactStats = totalExactStats == null ? current.counters() : totalExactStats.plus(current.counters());
				if (current.cancelled()) break;
				if (current.reached() && (best == null || current.cost() < best.cost()))
				{
					best = current;
					bestTarget = target;
					path = current.path();
				}
				if (best != null && best.cost() == 0) break;
			}

			if (cancelled)
				result = new PathfinderResult(start, firstTarget(), false, List.of(new PathStep(start, false)), start,
					PathfinderResult.NO_PATH_COST, stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
					PathTerminationReason.CANCELLED);
			else if (timedOut.get())
				result = new PathfinderResult(start, bestTarget, best != null, path,
					last(path), best == null ? PathfinderResult.NO_PATH_COST : best.cost(), stats.nodesChecked,
					stats.transportsChecked, System.nanoTime() - started, PathTerminationReason.CUTOFF_REACHED);
			else if (best != null)
				result = new PathfinderResult(start, bestTarget, true, path, last(path), best.cost(), stats.nodesChecked,
					stats.transportsChecked, System.nanoTime() - started, PathTerminationReason.TARGET_REACHED);
			else
				result = new PathfinderResult(start, firstTarget(), false, path, last(path), PathfinderResult.NO_PATH_COST,
					stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
					PathTerminationReason.SEARCH_EXHAUSTED);
			exactStats = totalExactStats == null ? ExactForwardSearch.Counters.empty() : totalExactStats;
		}
		catch (RuntimeException error)
		{
			exactStats = exactStats == null ? ExactForwardSearch.Counters.empty() : exactStats;
			result = new PathfinderResult(start, firstTarget(), false, path, last(path), PathfinderResult.NO_PATH_COST,
				stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
				PathTerminationReason.BACKEND_FAILURE,
				"Exact backend failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
		}
		finally
		{
			done = !cancelled;
			stats.end();
			if (completionCallback != null) completionCallback.run();
		}
	}

	private int firstTarget()
	{
		return targets.stream().min(Integer::compareUnsigned).orElse(Integer.MIN_VALUE);
	}

	private static int last(List<PathStep> steps)
	{
		return steps.isEmpty() ? Integer.MIN_VALUE : steps.get(steps.size() - 1).getPackedPosition();
	}
}
