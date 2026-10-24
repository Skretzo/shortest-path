package shortestpath.pathfinder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import shortestpath.leagues.LeagueModeState;
import shortestpath.pathfinder.astar.AStarForwardSearch;
import shortestpath.pathfinder.astar.AStarRoute;
import shortestpath.pathfinder.astar.AStarRoutingSession;
import shortestpath.pathfinder.astar.AStarWalkCanonicalizer;
import shortestpath.pathfinder.astar.InGameWalkRewriter;
import shortestpath.pathfinder.astar.PreparedRoutingAccount;
import shortestpath.pathfinder.astar.PreparedTarget;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.pathfinder.astar.SearchRestrictions;
import shortestpath.pathfinder.astar.SiteGraph;
import lombok.Getter;

/** ActiveSearch adapter for the A* core. */
public final class AStarPathfinder implements ActiveSearch
{
	private final PathfinderStats stats = new PathfinderStats();
	@Getter private final int start;
	@Getter private final Targets targets;
	// The states the search terminates on: the targets plus each blocked target's fallback tiles,
	// resolved the same way the legacy backend resolves them (issue #640), each in the bank layers
	// where the player holds that target's items. {@code null} for a search that failed to start.
	private final GoalStates goals;
	private final Runnable completionCallback;
	private final CollisionMap collision;
	private final Supplier<RoutingStatic> routingStatic;
	private final AStarRoutingSession session;
	private final PreparedRoutingAccount account;
	private final SearchRestrictions restrictions;
	private final UnlockSuggestions unlocks;
	private final long cutoffMillis;
	private final double heuristicWeight;
	@Getter private final long accountPrepareNanos;
	private volatile long routingStaticNanos;
	private volatile long graphPrepareNanos;
	private final String failure;
	private volatile boolean cancelled;
	private volatile boolean done;
	private volatile List<PathStep> path;
	private volatile List<PathStep> provisionalPath;
	private volatile PathfinderResult result;
	private volatile AStarForwardSearch.Counters aStarStats;
	private volatile long reverseSearchNanos;
	private volatile long heuristicPrepareNanos;
	private volatile long forwardSearchNanos;
	private volatile long walkCanonicalizeNanos;
	private volatile List<AStarWalkCanonicalizer.Diagnostic> walkDiagnostics = List.of();
	private volatile long walkRewriteNanos;
	private volatile long unlockSearchNanos;
	private volatile List<UnlockSuggestions.Suggestion> unlockSuggestions = List.of();
	private volatile List<Integer> clickPoints = List.of();
	private volatile List<Integer> keptStepIndices = List.of();
	private volatile boolean graphReused;
	private volatile boolean targetReused;

	public AStarPathfinder(PathfinderConfig config, RoutingStatic routingStatic, int start, Set<Integer> targets,
		Runnable completionCallback)
	{
		this(config, constant(routingStatic), start, targets, completionCallback);
	}

	public AStarPathfinder(PathfinderConfig config, RoutingStatic routingStatic, int start, Set<Integer> targets,
		Runnable completionCallback, double heuristicWeight)
	{
		this(config, constant(routingStatic), start, targets, completionCallback, heuristicWeight);
	}

	/**
	 * Creates a search whose static routing data is obtained from {@code routingStatic} when the
	 * search runs, so a first-use build happens on the pathfinding thread rather than here.
	 */
	public AStarPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic, int start,
		Set<Integer> targets, Runnable completionCallback)
	{
		this(config, routingStatic, start, targets, completionCallback,
			config == null ? 1 : config.getAStarHeuristicWeight());
	}

	public AStarPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic, int start,
		Set<Integer> targets, Runnable completionCallback, double heuristicWeight)
	{
		this(config, routingStatic, null, start, targets, completionCallback, heuristicWeight);
	}

	/**
	 * Creates a search that reuses the account graph and prepared targets held by {@code session}
	 * across queries; {@code null} prepares everything for this search alone.
	 */
	public AStarPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic,
		AStarRoutingSession session, int start, Set<Integer> targets, Runnable completionCallback)
	{
		this(config, routingStatic, session, start, targets, completionCallback,
			config == null ? 1 : config.getAStarHeuristicWeight());
	}

	public AStarPathfinder(PathfinderConfig config, RoutingStatic routingStatic, AStarRoutingSession session,
		int start, Set<Integer> targets, Runnable completionCallback, double heuristicWeight)
	{
		this(config, constant(routingStatic), session, start, targets, completionCallback, heuristicWeight);
	}

	public AStarPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic,
		AStarRoutingSession session, int start, Set<Integer> targets, Runnable completionCallback,
		double heuristicWeight)
	{
		this(config, routingStatic, session, start, Targets.at(targets), completionCallback,
			heuristicWeight);
	}

	public AStarPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic,
		AStarRoutingSession session, int start, Targets targets, Runnable completionCallback)
	{
		this(config, routingStatic, session, start, targets, completionCallback,
			config == null ? 1 : config.getAStarHeuristicWeight());
	}

	/**
	 * A search that ends at whichever of {@code targets} is cheapest to reach holding that target's
	 * items; see {@link GoalStates}.
	 */
	public AStarPathfinder(PathfinderConfig config, Supplier<RoutingStatic> routingStatic,
		AStarRoutingSession session, int start, Targets targets, Runnable completionCallback,
		double heuristicWeight)
	{
		if (config == null || routingStatic == null || targets == null) throw new NullPointerException();
		if (!(heuristicWeight > 0) || !Double.isFinite(heuristicWeight))
			throw new IllegalArgumentException("heuristic weight must be positive and finite");
		this.start = start;
		this.targets = targets;
		this.completionCallback = completionCallback;
		this.collision = config.getMap();
		this.routingStatic = routingStatic;
		this.session = session == null ? new AStarRoutingSession() : session;
		this.goals = GoalStates.resolve(config, start, targets);
		long phaseStarted = System.nanoTime();
		this.account = config.prepareAStarRoutingAccount(true, goals.needsBank());
		this.accountPrepareNanos = System.nanoTime() - phaseStarted;
		this.restrictions = restrictions(config, targets.points());
		this.unlocks = config.unlockSuggestions(goals.needsBank());
		this.cutoffMillis = config.getCalculationCutoffMillis();
		this.heuristicWeight = heuristicWeight;
		this.failure = null;
		this.path = List.of(new PathStep(start, BankVisitState.CARRIED));
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

	private AStarPathfinder(int start, Targets targets, Runnable completionCallback, String failure)
	{
		this.start = start;
		this.targets = targets;
		this.completionCallback = completionCallback;
		this.collision = null;
		this.routingStatic = null;
		this.session = null;
		this.account = null;
		this.restrictions = SearchRestrictions.none();
		this.unlocks = null;
		this.goals = null;
		this.cutoffMillis = 0;
		this.heuristicWeight = 1;
		this.accountPrepareNanos = 0;
		this.failure = failure;
		this.path = List.of(new PathStep(start, BankVisitState.CARRIED));
	}

	public static AStarPathfinder failed(int start, Targets targets, Runnable completionCallback, Throwable error)
	{
		String detail = error.getMessage();
		return new AStarPathfinder(start, targets, completionCallback,
			"A* backend unavailable: " + (detail == null ? error.getClass().getSimpleName() : detail));
	}

	@Override
	public void cancel()
	{
		cancelled = true;
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
	@Override
	public boolean isShowingProvisionalPath()
	{
		return provisionalPath != null;
	}

	@Override
	public boolean isDone()
	{ return done;
	}

	@Override
	public String backendName()
	{
		return PathfinderBackend.ASTAR.toString();
	}

	/**
	 * Always {@code false}: sailing dispatch bypasses the backend choice and
	 * builds a {@link shortestpath.pathfinder.sailing.SailingPathfinder}
	 * directly, so an A* search is never a sailing search.
	 */
	@Override
	public boolean isSailing()
	{
		return false;
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

	/** Completed A* search counters, or {@code null} while the search is running. */
	public AStarForwardSearch.Counters getAStarStats()
	{
		return stats.started && stats.ended ? aStarStats : null;
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
	public List<AStarWalkCanonicalizer.Diagnostic> getWalkDiagnostics()
	{ return walkDiagnostics;
	}

	/** Empty until found, after the result is published, and for a cancelled search. */
	@Override
	public List<UnlockSuggestions.Suggestion> getUnlockSuggestions()
	{ return cancelled ? List.of() : unlockSuggestions;
	}

	/** Time spent finding what to unlock after the search found the target unreachable. */
	public long getUnlockSearchNanos()
	{ return unlockSearchNanos;
	}

	/** Time spent rewriting the canonical walking legs into the game's own walking paths. */
	public long getWalkRewriteNanos()
	{ return walkRewriteNanos;
	}

	/**
	 * Ascending indices into the route's steps of the tiles the player clicks to walk it in game;
	 * empty until a route is found.
	 */
	@Override
	public List<Integer> getClickPoints()
	{ return clickPoints;
	}

	/**
	 * Ascending indices into the route's steps of the walking steps that kept the canonical step
	 * because no in-game click realises them; empty until a route is found.
	 */
	public List<Integer> getKeptStepIndices()
	{ return keptStepIndices;
	}

	/** Whether the account graph came from the session rather than being built for this search. */
	public boolean isGraphReused()
	{ return graphReused;
	}

	/** Whether this search's target set was already prepared in the session. */
	// Static data is built once; only the search that built it spends measurable time on it.
	private static final long STATIC_BUILD_THRESHOLD_NANOS = 1_000_000;

	@Override
	public Map<String, String> debugStats()
	{
		Map<String, String> entries = new LinkedHashMap<>();
		entries.put("Forward search", PathfinderStats.millis(getForwardSearchNanos()));
		entries.put("Walk canonicalize", PathfinderStats.millis(getWalkCanonicalizeNanos()));
		if (!getWalkDiagnostics().isEmpty())
		{
			entries.put("Legs kept as found", Integer.toString(getWalkDiagnostics().size()));
		}
		int keptSteps = getKeptStepIndices().size();
		entries.put("Walk rewrite", PathfinderStats.millis(getWalkRewriteNanos())
			+ (keptSteps > 0 ? ", " + keptSteps + " kept" : ""));
		entries.put("Target prep", isTargetReused() ? "reused"
			: PathfinderStats.millis(getReverseSearchNanos() + getHeuristicPrepareNanos()));
		entries.put("Graph prep", isGraphReused() ? "reused"
			: PathfinderStats.millis(getGraphPrepareNanos()));
		entries.put("Account prep", PathfinderStats.millis(getAccountPrepareNanos()));
		if (getUnlockSearchNanos() > 0)
		{
			entries.put("Unlock search", PathfinderStats.millis(getUnlockSearchNanos())
				+ ", " + getUnlockSuggestions().size() + " found");
		}
		if (getRoutingStaticNanos() >= STATIC_BUILD_THRESHOLD_NANOS)
		{
			entries.put("Static data", PathfinderStats.millis(getRoutingStaticNanos()));
		}
		return entries;
	}

	public boolean isTargetReused()
	{ return targetReused;
	}

	@Override
	public void run()
	{
		stats.start();
		long started = System.nanoTime();
		// Set when the search finds its target unreachable and there may be something to unlock.
		RoutingStatic unreachableIn = null;
		try
		{
			if (failure != null)
			{
				aStarStats = AStarForwardSearch.Counters.empty();
				result = new PathfinderResult(start, firstTarget(), false, path, start, PathfinderResult.NO_PATH_COST,
					0, 0, System.nanoTime() - started, PathTerminationReason.BACKEND_FAILURE, failure);
				return;
			}
			long phaseStarted = System.nanoTime();
			RoutingStatic staticData = routingStatic.get();
			routingStaticNanos = System.nanoTime() - phaseStarted;
			phaseStarted = System.nanoTime();
			AStarRoutingSession.Lookup<SiteGraph> graph = session.graph(staticData, account);
			graphPrepareNanos = System.nanoTime() - phaseStarted;
			graphReused = graph.reused();
			AtomicBoolean timedOut = new AtomicBoolean();
			// A zero cutoff means no cutoff for the A* backend.
			SearchDeadline deadline = cutoffMillis > 0 ? new SearchDeadline(cutoffMillis) : null;
			int[] packedTargets = goals.tiles().stream().mapToInt(Integer::intValue).toArray();
			AStarForwardSearch.Result best = null;
			int bestTarget = firstTarget();
			int partialCost = PathfinderResult.NO_PATH_COST;
			aStarStats = AStarForwardSearch.Counters.empty();
			// When every target is a blocked tile that no transport lands on and
			// no walkable tile exists within the unreachable distance, no state can
			// satisfy the goal set — skip the search like the legacy backend does.
			if (!cancelled && goals.hasViableGoal() && packedTargets.length != 0)
			{
				// One search towards every target at once; it ends at the cheapest one to reach.
				phaseStarted = System.nanoTime();
				AStarRoutingSession.Lookup<PreparedTarget> prepared = session.target(graph.value(), collision,
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
				AStarForwardSearch.Result current = prepared.value().search(start,
					() ->
					{
						if (cancelled) return true;
						if (deadline != null && deadline.expired())
						{
							timedOut.set(true);
							return true;
						}
						return false;
					}, heuristicWeight, restrictions, deadline == null ? AStarForwardSearch.NO_PROGRESS : deadline::progressed,
					goals::accepts);
				forwardSearchNanos = System.nanoTime() - phaseStarted;
				stats.nodesChecked += current.counters().statesPopped();
				stats.transportsChecked += current.counters().transportCandidates();
				aStarStats = current.counters();
				AStarRoute found = null;
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
					// Publish only the final path, so the render thread never shows the raw one. First
					// pick each walking leg's canonical walk among the equally cheap ones, then turn it
					// into the clicks along it and the paths the game's own walking takes between them.
					phaseStarted = System.nanoTime();
					AStarWalkCanonicalizer.Result canonical = new AStarWalkCanonicalizer(collision, account,
						restrictions).canonicalize(found);
					walkCanonicalizeNanos = System.nanoTime() - phaseStarted;
					walkDiagnostics = canonical.diagnostics();
					phaseStarted = System.nanoTime();
					InGameWalkRewriter.Result walked = new InGameWalkRewriter(collision, restrictions).rewrite(canonical);
					walkRewriteNanos = System.nanoTime() - phaseStarted;
					clickPoints = walked.clickPoints();
					keptStepIndices = walked.keptStepIndices();
					path = walked.path();
					if (best != null) bestTarget = last(path);
				}
			}

			if (cancelled)
				result = new PathfinderResult(start, firstTarget(), false, List.of(new PathStep(start, BankVisitState.CARRIED)), start,
					PathfinderResult.NO_PATH_COST, stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
					PathTerminationReason.CANCELLED);
			else if (timedOut.get())
				result = new PathfinderResult(start, bestTarget, best != null, path,
					last(path), best == null ? partialCost : best.cost(), stats.nodesChecked,
					stats.transportsChecked, System.nanoTime() - started, PathTerminationReason.CUTOFF_REACHED, null,
					best == null ? null : arrival(path));
			else if (best != null)
				result = new PathfinderResult(start, bestTarget, true, path, last(path), best.cost(), stats.nodesChecked,
					stats.transportsChecked, System.nanoTime() - started, PathTerminationReason.TARGET_REACHED, null,
					arrival(path));
			else
			{
				result = new PathfinderResult(start, firstTarget(), false, path, last(path), PathfinderResult.NO_PATH_COST,
					stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
					PathTerminationReason.SEARCH_EXHAUSTED);
				if (unlocks != null && goals.hasViableGoal()) unreachableIn = staticData;
			}
		}
		// Errors too: the static build allocates the large search arrays, where OOM is the most
		// plausible fatal failure; an uncaught Error would leave result null and display as
		// "unreachable" rather than a backend failure. Matches runQuery's catch.
		catch (RuntimeException | Error error)
		{
			aStarStats = aStarStats == null ? AStarForwardSearch.Counters.empty() : aStarStats;
			result = new PathfinderResult(start, firstTarget(), false, path, last(path), PathfinderResult.NO_PATH_COST,
				stats.nodesChecked, stats.transportsChecked, System.nanoTime() - started,
				PathTerminationReason.BACKEND_FAILURE,
				"A* backend failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
		}
		finally
		{
			if (!cancelled) provisionalPath = null;
			done = !cancelled;
			stats.end();
			if (completionCallback != null) completionCallback.run();
		}
		// The unreachable result is already out; what to unlock follows on this thread, unless a
		// newer search cancels this one first.
		if (unreachableIn != null && !cancelled)
		{
			long phaseStarted = System.nanoTime();
			unlockSuggestions = unlocks.find(unreachableIn, collision, account, start, goals, restrictions,
				heuristicWeight, () -> cancelled);
			unlockSearchNanos = System.nanoTime() - phaseStarted;
		}
	}

	/** The arrival of a route that reached a goal, read from where and in which layer it ends. */
	private Arrival arrival(List<PathStep> route)
	{
		PathStep end = route.get(route.size() - 1);
		return goals.arrival(end.getPackedPosition(), end.getBankVisitState());
	}

	private int firstTarget()
	{
		return targets.points().stream().min(Integer::compareUnsigned).orElse(Integer.MIN_VALUE);
	}

	private static int last(List<PathStep> steps)
	{
		return steps.isEmpty() ? Integer.MIN_VALUE : steps.get(steps.size() - 1).getPackedPosition();
	}
}
