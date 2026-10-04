package shortestpath.pathfinder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import shortestpath.leagues.LeagueModeState;
import shortestpath.pathfinder.exact.ExactForwardSearch;
import shortestpath.pathfinder.exact.ExactRoute;
import shortestpath.pathfinder.exact.ExactRoutingSession;
import shortestpath.pathfinder.exact.ExactWalkCanonicalizer;
import shortestpath.pathfinder.exact.PreparedRoutingAccount;
import shortestpath.pathfinder.exact.PreparedTarget;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.pathfinder.exact.SearchRestrictions;
import shortestpath.pathfinder.exact.SiteGraph;

/** ActiveSearch adapter for the exact core. */
public final class ExactPathfinder implements ActiveSearch
{
	private final PathfinderStats stats = new PathfinderStats();
	private final int start;
	private final Set<Integer> targets;
	private final Runnable completionCallback;
	private final CollisionMap collision;
	private final Supplier<RoutingStatic> routingStatic;
	private final ExactRoutingSession session;
	private final PreparedRoutingAccount account;
	private final SearchRestrictions restrictions;
	private final long cutoffMillis;
	private final double heuristicWeight;
	private final long accountPrepareNanos;
	private volatile long routingStaticNanos;
	private volatile long graphPrepareNanos;
	private final String failure;
	private volatile boolean cancelled;
	private volatile boolean done;
	private volatile List<PathStep> path;
	private volatile List<PathStep> provisionalPath;
	private volatile PathfinderResult result;
	private volatile ExactForwardSearch.Counters exactStats;
	private volatile long reverseSearchNanos;
	private volatile long heuristicPrepareNanos;
	private volatile long forwardSearchNanos;
	private volatile long walkCanonicalizeNanos;
	private volatile List<ExactWalkCanonicalizer.Diagnostic> walkDiagnostics = List.of();
	private volatile boolean graphReused;
	private volatile boolean targetReused;

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
		this(config, routingStatic, null, start, targets, completionCallback, heuristicWeight);
	}

	/**
	 * Creates a search that reuses the account graph and prepared targets held by {@code session}
	 * across queries; {@code null} prepares everything for this search alone.
	 */
	public ExactPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic,
		ExactRoutingSession session, int start, Set<Integer> targets, Runnable completionCallback)
	{
		this(config, routingStatic, session, start, targets, completionCallback,
			config == null ? 1 : config.getExactHeuristicWeight());
	}

	public ExactPathfinder(PathfinderConfig config, RoutingStatic routingStatic, ExactRoutingSession session,
		int start, Set<Integer> targets, Runnable completionCallback, double heuristicWeight)
	{
		this(config, constant(routingStatic), session, start, targets, completionCallback, heuristicWeight);
	}

	public ExactPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic,
		ExactRoutingSession session, int start, Set<Integer> targets, Runnable completionCallback,
		double heuristicWeight)
	{
		if (config == null || routingStatic == null || targets == null) throw new NullPointerException();
		if (!(heuristicWeight > 0) || !Double.isFinite(heuristicWeight))
			throw new IllegalArgumentException("heuristic weight must be positive and finite");
		this.start = start;
		this.targets = Set.copyOf(targets);
		this.completionCallback = completionCallback;
		this.collision = config.getMap();
		this.routingStatic = routingStatic;
		this.session = session == null ? new ExactRoutingSession() : session;
		long phaseStarted = System.nanoTime();
		this.account = config.prepareExactRoutingAccount(true);
		this.accountPrepareNanos = System.nanoTime() - phaseStarted;
		this.restrictions = restrictions(config, this.targets);
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

	/**
	 * The same positional gates legacy checks per edge: {@code avoidWilderness} and the league's
	 * always-blocked region, each lifted when a target lies inside the gated area (the user asked
	 * for that tile, so the route must be allowed to reach it).
	 */
	private static SearchRestrictions restrictions(PathfinderConfig config, Set<Integer> targets)
	{
		boolean avoidWilderness = config.isAvoidWilderness() && !WildernessChecker.isInWilderness(targets);
		LeagueModeState league = config.getLeagueModeState();
		IntPredicate blockedRegion = null;
		if (league != null && league.isSeasonal())
		{
			boolean targetInBlockedRegion = false;
			for (int target : targets)
			{
				targetInBlockedRegion |= league.isInBlockedRegion(target);
			}
			if (!targetInBlockedRegion)
			{
				blockedRegion = league::isInBlockedRegion;
			}
		}
		return SearchRestrictions.of(avoidWilderness, blockedRegion);
	}

	private ExactPathfinder(int start, Set<Integer> targets, Runnable completionCallback, String failure)
	{
		this.start = start;
		this.targets = Set.copyOf(targets);
		this.completionCallback = completionCallback;
		this.collision = null;
		this.routingStatic = null;
		this.session = null;
		this.account = null;
		this.restrictions = SearchRestrictions.none();
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
	{
		List<PathStep> shown = provisionalPath;
		return shown != null ? shown : path;
	}

	/**
	 * Shows {@code previous} as this search's path until the search completes, so recalculating a
	 * route keeps the old one on screen instead of collapsing it to the start tile. Call before
	 * running the search; a cancelled search keeps showing it.
	 */
	public void showUntilDone(List<PathStep> previous)
	{
		provisionalPath = previous == null || previous.size() < 2 ? null : List.copyOf(previous);
	}

	/** Whether {@link #getPath()} is still the previous route passed to {@link #showUntilDone}. */
	public boolean isShowingProvisionalPath()
	{
		return provisionalPath != null;
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

	/** Time spent choosing each walking leg's canonical walk. */
	public long getWalkCanonicalizeNanos()
	{ return walkCanonicalizeNanos;
	}

	/**
	 * Walking legs that kept the search's own walk because the canonicaliser disagreed with the
	 * route about them; empty when every leg was canonicalised.
	 */
	public List<ExactWalkCanonicalizer.Diagnostic> getWalkDiagnostics()
	{ return walkDiagnostics;
	}

	/** Whether the account graph came from the session rather than being built for this search. */
	public boolean isGraphReused()
	{ return graphReused;
	}

	/** Whether this search's target set was already prepared in the session. */
	public boolean isTargetReused()
	{ return targetReused;
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
			ExactRoutingSession.Lookup<SiteGraph> graph = session.graph(staticData, account);
			graphPrepareNanos = System.nanoTime() - phaseStarted;
			graphReused = graph.reused();
			AtomicBoolean timedOut = new AtomicBoolean();
			// A zero cutoff means no cutoff for the exact backend.
			SearchDeadline deadline = cutoffMillis > 0 ? new SearchDeadline(cutoffMillis) : null;
			int[] packedTargets = targets.stream().mapToInt(Integer::intValue).toArray();
			ExactForwardSearch.Result best = null;
			int bestTarget = firstTarget();
			int partialCost = PathfinderResult.NO_PATH_COST;
			exactStats = ExactForwardSearch.Counters.empty();
			if (!cancelled && packedTargets.length != 0)
			{
				// One search towards every target at once; it ends at the cheapest one to reach.
				phaseStarted = System.nanoTime();
				ExactRoutingSession.Lookup<PreparedTarget> prepared = session.target(graph.value(), collision,
					packedTargets);
				targetReused = prepared.reused();
				if (targetReused)
				{
					heuristicPrepareNanos = System.nanoTime() - phaseStarted;
				}
				else
				{
					reverseSearchNanos = prepared.value().reverseSearchNanos();
					heuristicPrepareNanos = prepared.value().heuristicPrepareNanos();
				}
				phaseStarted = System.nanoTime();
				ExactForwardSearch.Result current = prepared.value().search(start,
					() ->
					{
						if (cancelled) return true;
						if (deadline != null && deadline.expired())
						{
							timedOut.set(true);
							return true;
						}
						return false;
					}, heuristicWeight, restrictions, deadline == null ? ExactForwardSearch.NO_PROGRESS : deadline::progressed);
				forwardSearchNanos = System.nanoTime() - phaseStarted;
				stats.nodesChecked += current.counters().statesPopped();
				stats.transportsChecked += current.counters().transportCandidates();
				exactStats = current.counters();
				ExactRoute found = null;
				if (current.reached())
				{
					best = current;
					found = current.route();
				}
				else if (timedOut.get() && !cancelled)
				{
					// Like legacy: a cut-off search still routes to the tile it got closest to.
					found = current.closestRoute();
					partialCost = current.closestCost();
				}
				if (found != null)
				{
					// Publish only the canonical path, so the render thread never shows the raw one:
					// each walking leg's canonical walk among the equally cheap ones.
					phaseStarted = System.nanoTime();
					ExactWalkCanonicalizer.Result canonical = new ExactWalkCanonicalizer(collision, account,
						restrictions).canonicalize(found);
					walkCanonicalizeNanos = System.nanoTime() - phaseStarted;
					walkDiagnostics = canonical.diagnostics();
					path = canonical.path();
					if (best != null) bestTarget = last(path);
				}
			}

			if (cancelled)
				result = new PathfinderResult(start, firstTarget(), false, List.of(new PathStep(start, false)), start,
					PathfinderResult.NO_PATH_COST, stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
					PathTerminationReason.CANCELLED);
			else if (timedOut.get())
				result = new PathfinderResult(start, bestTarget, best != null, path,
					last(path), best == null ? partialCost : best.cost(), stats.nodesChecked,
					stats.transportsChecked, System.nanoTime() - started, PathTerminationReason.CUTOFF_REACHED);
			else if (best != null)
				result = new PathfinderResult(start, bestTarget, true, path, last(path), best.cost(), stats.nodesChecked,
					stats.transportsChecked, System.nanoTime() - started, PathTerminationReason.TARGET_REACHED);
			else
				result = new PathfinderResult(start, firstTarget(), false, path, last(path), PathfinderResult.NO_PATH_COST,
					stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
					PathTerminationReason.SEARCH_EXHAUSTED);
		}
		// Errors too: the static build allocates the large search arrays, where OOM is the most
		// plausible fatal failure; an uncaught Error would leave result null and display as
		// "unreachable" rather than a backend failure. Matches runQuery's catch.
		catch (RuntimeException | Error error)
		{
			exactStats = exactStats == null ? ExactForwardSearch.Counters.empty() : exactStats;
			result = new PathfinderResult(start, firstTarget(), false, path, last(path), PathfinderResult.NO_PATH_COST,
				stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
				PathTerminationReason.BACKEND_FAILURE,
				"Exact backend failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
		}
		finally
		{
			if (!cancelled) provisionalPath = null;
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
