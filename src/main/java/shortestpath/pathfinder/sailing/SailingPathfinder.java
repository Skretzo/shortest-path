package shortestpath.pathfinder.sailing;

import java.util.List;
import java.util.Set;

import lombok.Getter;
import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.IntDeque;
import shortestpath.pathfinder.IntMinHeap;
import shortestpath.pathfinder.NodeGraph;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathfinderStats;
import shortestpath.pathfinder.SearchDeadline;
import shortestpath.pathfinder.TargetGoals;
import shortestpath.pathfinder.Targets;
import shortestpath.pathfinder.VisitedTiles;
import shortestpath.pathfinder.WildernessChecker;

/**
 * The sailing search: a Dijkstra over the boat's 16 headings (see {@link SailingMoves}) that keeps the
 * boat's whole hull clear along every move and arrives once the hull covers a target, rather than on
 * a tile. A self-contained search sharing only the public machinery of {@code pathfinder} (NodeGraph,
 * VisitedTiles, TargetGoals, SplitFlagMap) — it is an {@link ActiveSearch}, not a {@code Pathfinder},
 * so a walking search and a sailing search never share a class.
 */
public class SailingPathfinder implements ActiveSearch
{
	private final Stats stats;
	@Getter
	private final int start;
	// The targets keep their required items so a restart after landing honours them, but a
	// sailing search ignores them: a boat can't visit a bank.
	@Getter
	private final Targets targets;
	// Termination set resolved by TargetGoals: the requested targets plus, for
	// each blocked target, its fallback tiles (issue #640).
	private final Set<Integer> goals;
	// Whether any requested target could possibly terminate the search: non-blocked,
	// a transport destination, or resolved to at least one walkable nearby tile.
	// When false the search is skipped entirely, since no goal can ever be visited.
	private final boolean hasViableGoal;
	private final SailingSearch sailing;
	private final PathfinderConfig config;
	private final CollisionMap map;
	private final boolean targetInWilderness;
	private final boolean targetInBlockedRegion;
	private final Runnable completionCallback;
	// Nodes are stored structure-of-arrays style: each node is an int id into the graph, instead of
	// an object per explored tile. This keeps a whole search to a handful of arrays (issue #491).
	private final NodeGraph graph = new NodeGraph(1 << 14);
	// Capacities should be enough to store all nodes without requiring the queue to grow
	// They were found by checking the max queue size
	private final IntDeque boundary = new IntDeque(4096);
	private final IntMinHeap pending = new IntMinHeap(graph, 256);
	private final VisitedTiles visited;
	// The reusable emission buffer the neighbour enumeration fills; a sailing search emits only
	// weighted tiles into it.
	private final PrimitiveIntList neighbors = new PrimitiveIntList(16);
	@Getter
	private volatile boolean done = false;
	private volatile boolean cancelled = false;
	// Read by the render thread during the search to draw the partial path; written by the worker.
	private volatile int bestLastNode = NodeGraph.NO_NODE;
	// The path the render thread builds progressively while the search runs.
	private List<PathStep> pathSteps = List.of();
	private boolean pathNeedsUpdate = false;
	// Built once on the worker thread when the search finishes, then served to the render thread so
	// it never walks the node chain (which is released) after the search is done.
	private volatile List<PathStep> finalPath = null;
	private volatile int closestReachedPoint = WorldPointUtil.UNDEFINED;
	private volatile int finalPathCost = PathfinderResult.NO_PATH_COST;
	private int bestRemainingDistance = Integer.MAX_VALUE;
	private int bestTravelledDistance = Integer.MAX_VALUE;
	private int bestX = Integer.MAX_VALUE;
	private int bestY = Integer.MAX_VALUE;
	private int reachedTarget = WorldPointUtil.UNDEFINED;
	private PathTerminationReason terminationReason;
	/**
	 * Teleportation transports are updated when this changes.
	 * Can be either:
	 * 0 = all teleports can be used (e.g. Chronicle)
	 * 20 = most teleports can be used (e.g. Varrock Teleport)
	 * 30 = some teleports can be used (e.g. Amulet of Glory)
	 * 31 = no teleports can be used
	 */
	private int wildernessLevel;

	/**
	 * @param moves the boat's moves; required — a walking search is a plain {@code Pathfinder}
	 */
	public SailingPathfinder(PathfinderConfig config, int start, Set<Integer> targets, Runnable completionCallback,
		SailingMoves moves)
	{
		this(config, start, targets, completionCallback, moves, null);
	}

	/**
	 * @param moves the boat's moves; required — a walking search is a plain {@code Pathfinder}
	 * @param boatHull the boat's hull, which must fit along every sailing move, or {@code null} for just its centre
	 */
	public SailingPathfinder(PathfinderConfig config, int start, Set<Integer> targets, Runnable completionCallback,
		SailingMoves moves, BoatHull boatHull)
	{
		this(config, start, targets, completionCallback, moves, boatHull, WorldPointUtil.UNDEFINED);
	}

	/**
	 * @param moves          the boat's moves; required — a walking search is a plain {@code Pathfinder}
	 * @param boatHull       the boat's hull, which must fit along every sailing move, or {@code null} for just its centre
	 * @param sailingHeading the heading the boat is facing where the route starts (0 south, 8 north), or -1 when it
	 *                       isn't known: a target the hull already covers then counts as arrived facing any heading
	 */
	public SailingPathfinder(PathfinderConfig config, int start, Set<Integer> targets, Runnable completionCallback,
		SailingMoves moves, BoatHull boatHull, int sailingHeading)
	{
		this(config, start, Targets.at(targets), completionCallback, moves, boatHull, sailingHeading);
	}

	/** As {@link #SailingPathfinder(PathfinderConfig, int, Set, Runnable, SailingMoves, BoatHull, int)}. */
	public SailingPathfinder(PathfinderConfig config, int start, Targets targets, Runnable completionCallback,
		SailingMoves moves, BoatHull boatHull, int sailingHeading)
	{
		stats = new Stats();
		this.config = config;
		this.map = config.getMap();
		this.start = start;
		this.targets = targets;
		Set<Integer> points = targets.points();
		TargetGoals resolved = TargetGoals.resolve(config, start, points);
		this.goals = resolved.goals();
		this.hasViableGoal = resolved.hasViableGoal();
		this.sailing = new SailingSearch(moves, boatHull, points.stream().mapToInt(Integer::intValue).toArray(),
			goals.stream().mapToInt(Integer::intValue).toArray(), sailingHeading);
		this.completionCallback = completionCallback;
		visited = new VisitedTiles(map, config.getBankVisitCost());
		targetInWilderness = WildernessChecker.isInWilderness(points);
		targetInBlockedRegion = anyInBlockedRegion(config.getLeagueModeState(), points);
		wildernessLevel = 31;
	}

	/**
	 * The target the boat, on the tile at {@code packedPosition} and facing {@code heading} (one of the
	 * 16 headings, or -1 when unknown), has arrived at — see {@link SailingSearch#arrivedTarget}. Lets
	 * the plugin ask where the live boat is rather than trusting the search's verdict, which says a
	 * route was found, not that the boat sailed it. {@link WorldPointUtil#UNDEFINED} if it hasn't
	 * arrived.
	 */
	public int arrivedTarget(int packedPosition, int heading)
	{
		return sailing.arrivedTarget(packedPosition, heading);
	}

	private static boolean anyInBlockedRegion(LeagueModeState league, Set<Integer> packed)
	{
		if (!league.isSeasonal() || packed == null || packed.isEmpty())
		{
			return false;
		}
		for (Integer point : packed)
		{
			if (league.isInBlockedRegion(point))
			{
				return true;
			}
		}
		return false;
	}

	public void cancel()
	{
		cancelled = true;
	}

	public PathfinderStats getStats()
	{
		if (stats.started && stats.ended)
		{
			return stats;
		}

		// Don't give incomplete results
		return null;
	}

	public List<PathStep> getPath()
	{
		int lastNode = bestLastNode; // For thread safety, read bestLastNode once
		if (lastNode == NodeGraph.NO_NODE)
		{
			List<PathStep> finalised = finalPath;
			return finalised != null ? finalised : pathSteps;
		}

		// Once the search is finished the node graph is released, so serve the pre-built snapshot.
		if (done)
		{
			List<PathStep> finalised = finalPath;
			if (finalised != null)
			{
				return finalised;
			}
		}

		if (pathNeedsUpdate)
		{
			List<PathStep> walked = graph.getPathSteps(lastNode);
			// An empty result means the graph was released mid-walk; keep the last good path.
			if (!walked.isEmpty())
			{
				pathSteps = walked;
				pathNeedsUpdate = false;
			}
		}

		return pathSteps;
	}

	public PathfinderResult getResult()
	{
		PathfinderStats currentStats = getStats();
		if (currentStats == null)
		{
			return null;
		}

		List<PathStep> currentPath = getPath();
		boolean reached = reachedTarget != WorldPointUtil.UNDEFINED;
		int target = reached ? reachedTarget : (targets.isEmpty() ? WorldPointUtil.UNDEFINED : targets.points().iterator().next());
		// getStats() only returns non-null once the search has ended, so the snapshot is set.
		return new PathfinderResult(
			start,
			target,
			reached,
			currentPath,
			closestReachedPoint,
			finalPathCost,
			currentStats.getNodesChecked(),
			currentStats.getTransportsChecked(),
			currentStats.getElapsedTimeNanos(),
			terminationReason
		);
	}

	private void addNeighbors(int node, int nodePacked)
	{
		final int x = WorldPointUtil.unpackWorldX(nodePacked);
		final int y = WorldPointUtil.unpackWorldY(nodePacked);
		final int z = WorldPointUtil.unpackWorldPlane(nodePacked);
		neighbors.clear();
		sailing.addNeighbors(map, neighbors, node, x, y, z, graph.bankVisited(node), visited, graph);
		final int count = neighbors.size();
		for (int i = 0; i < count; i++)
		{
			int neighbor = neighbors.get(i);
			final int neighborPacked = graph.packedPosition(neighbor);
			if (config.avoidWilderness(nodePacked, neighborPacked, targetInWilderness))
			{
				continue;
			}
			if (config.avoidBlockedRegion(nodePacked, neighborPacked, targetInBlockedRegion))
			{
				continue;
			}
			// Every emitted node is a weighted tile: a sailing move crosses its whole span at once, so
			// check its midpoint too, so a leg can't clip a wilderness or league boundary inside its reach
			final int midPacked = WorldPointUtil.packWorldPoint(
				(WorldPointUtil.unpackWorldX(nodePacked) + WorldPointUtil.unpackWorldX(neighborPacked)) / 2,
				(WorldPointUtil.unpackWorldY(nodePacked) + WorldPointUtil.unpackWorldY(neighborPacked)) / 2,
				WorldPointUtil.unpackWorldPlane(nodePacked));
			if (config.avoidWilderness(nodePacked, midPacked, targetInWilderness)
				|| config.avoidWilderness(midPacked, neighborPacked, targetInWilderness)
				|| config.avoidBlockedRegion(nodePacked, midPacked, targetInBlockedRegion)
				|| config.avoidBlockedRegion(midPacked, neighborPacked, targetInBlockedRegion))
			{
				continue;
			}

			pending.add(neighbor);
			// Only now, past the checks above, is the sailing tile queued at this cost, so a move they turn
			// down doesn't stop another route from queuing the tile
			sailing.setQueuedCost(WorldPointUtil.unpackWorldX(neighborPacked), WorldPointUtil.unpackWorldY(neighborPacked),
				WorldPointUtil.unpackWorldPlane(neighborPacked), graph.bankVisited(neighbor), graph.cost(neighbor));
			++stats.nodesChecked;
		}
	}

	/**
	 * Pathfinding to an unreachable target is slightly different from normal pathfinding.
	 * Straight-line movement before diagonal movement is no longer prioritized, because the
	 * original target is moved to the closest reachable tile. To avoid having to move the
	 * original target we instead do the following to favour the closest reachable tile:
	 * - 1) Pick the path with the minimum Euclidean distance (no need to use square root though)
	 * - 2) If a tie occurs, pick the path with minimum travelled distance
	 * - 3) If another tie occurs, pick the path with minimum x-coordinate
	 * - 4) If another tie occurs, pick the path with minimum y-coordinate
	 */
	private boolean updateBestPathWhenUnreachable(int node, int packedPosition)
	{
		boolean update = false;

		final int travelledDistance = graph.cost(node);
		for (int target : targets.points())
		{
			int remainingDistance = WorldPointUtil.distanceBetween(target, packedPosition, WorldPointUtil.EUCLIDEAN_SQUARED_DISTANCE_METRIC);
			int x = WorldPointUtil.unpackWorldX(packedPosition);
			int y = WorldPointUtil.unpackWorldY(packedPosition);
			if ((remainingDistance < bestRemainingDistance) ||
				(remainingDistance == bestRemainingDistance && travelledDistance < bestTravelledDistance) ||
				(remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x < bestX) ||
				(remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x == bestX && y < bestY))
			{
				bestRemainingDistance = remainingDistance;
				bestTravelledDistance = travelledDistance;
				bestX = x;
				bestY = y;
				bestLastNode = node;
				pathNeedsUpdate = true;
				update = true;
			}
		}

		return update;
	}

	/**
	 * Update wilderness level based on the current node position.
	 */
	private void updateWildernessLevel(int packedPosition)
	{
		if (wildernessLevel > 0)
		{
			// These are overlapping boundaries, so if the node isn't in level 30, it's in 0-29
			// likewise, if the node isn't in level 20, it's in 0-19
			if (wildernessLevel > 30 && !WildernessChecker.isInLevel30Wilderness(packedPosition))
			{
				wildernessLevel = 30;
			}
			if (wildernessLevel > 20 && !WildernessChecker.isInLevel20Wilderness(packedPosition))
			{
				wildernessLevel = 20;
			}
			if (wildernessLevel > 0 && !WildernessChecker.isInWilderness(packedPosition))
			{
				wildernessLevel = 0;
			}
		}
	}

	/**
	 * Always {@code true}: this search sails the boat's headings.
	 */
	@Override
	public boolean isSailing()
	{
		return true;
	}

	@Override
	public void run()
	{
		stats.start();
		boundary.addFirst(graph.createStart(start));

		// The cutoff counts time without progress towards the target.
		SearchDeadline deadline = new SearchDeadline(config.getCalculationCutoffMillis());

		// When every target is a blocked tile that no transport lands on and no
		// walkable tile exists within the unreachable distance, no node can ever
		// satisfy the goal set — skip the search instead of exhausting the map.
		// A sailing search still runs: its hull can cover a target the walking
		// goals can't reach, which is what it arrives on rather than the goal set.
		while (!cancelled && (hasViableGoal || !targets.isEmpty())
			&& (!boundary.isEmpty() || !pending.isEmpty()))
		{
			int boundaryHead = boundary.peekFirst();
			int pendingHead = pending.peek();

			int node;
			// On a cost tie the pending heap wins: a queued transport that is strictly
			// cheaper than the walking route must claim its destination before an
			// equal-cost boundary node can expand and emit a competing walking edge.
			if (pendingHead != NodeGraph.NO_NODE
				&& (boundaryHead == NodeGraph.NO_NODE || graph.compareCost(pendingHead) <= graph.cost(boundaryHead)))
			{
				node = pending.poll();

				// Every node in pending is a sailing tile; none of them claimed their
				// destination at enqueue. The first, cheapest, dequeue wins the tile; later
				// queued duplicates for the same tile are dropped here — unless the duplicate
				// is a sailing move that arrives on its own heading, queued for it because the
				// settled heading didn't cover a target.
				int packed = graph.packedPosition(node);
				BankVisitState bank = graph.bankVisited(node);
				if (visited.get(packed, bank)
					&& !(graph.isWeighted(node)
						&& sailing.arrivedTarget(packed, sailing.arrivalHeading(graph, node)) != WorldPointUtil.UNDEFINED))
				{
					continue;
				}
				visited.set(packed, bank);
			}
			else
			{
				node = boundary.pollFirst();
			}
			if (node == NodeGraph.NO_NODE)
			{
				continue;
			}
			// A sailing search explores only tiles: the start node and the weighted tiles its
			// moves emit, so there is no nodeIsTile branch to keep. The walking search's
			// nearest-tile acceptance (shortestAcceptedNode/updateCustomPathWhenUnreachable) is
			// likewise dropped: accepting the first tile near a target could truncate the path
			// before the hull reaches a heading that covers one.
			final int nodePacked = graph.packedPosition(node);
			updateWildernessLevel(nodePacked);

			final int arrivedTarget = sailing.arrivedTarget(nodePacked, sailing.arrivalHeading(graph, node));
			if (goals.contains(nodePacked) || arrivedTarget != WorldPointUtil.UNDEFINED)
			{
				bestLastNode = node;
				// A sailing arrival reports the target the hull covers, which can sit a
				// boat length from the boat's tile rather than on it
				reachedTarget = goals.contains(nodePacked) ? nodePacked : arrivedTarget;
				pathNeedsUpdate = true;
				terminationReason = PathTerminationReason.TARGET_REACHED;
				break;
			}

			// Unreachable sailing routes still draw toward the target; isPathUnreachable
			// reads result.isReached()
			if (updateBestPathWhenUnreachable(node, nodePacked))
			{
				deadline.progressed();
			}

			if (deadline.expired())
			{
				terminationReason = PathTerminationReason.CUTOFF_REACHED;
				break;
			}

			addNeighbors(node, nodePacked);
		}

		if (cancelled)
		{
			terminationReason = PathTerminationReason.CANCELLED;
		}
		else if (terminationReason == null)
		{
			terminationReason = PathTerminationReason.SEARCH_EXHAUSTED;
		}

		// Materialise the final path and closest reached tile on the worker thread, publish them,
		// then release the large node graph. Once done is set the render thread serves finalPath
		// and never touches the released graph, so this is race-free with progressive rendering.
		int lastNode = bestLastNode;
		if (lastNode != NodeGraph.NO_NODE)
		{
			finalPath = graph.getPathSteps(lastNode);
			closestReachedPoint = graph.getClosestTilePosition(lastNode);
			finalPathCost = graph.cost(lastNode);
		}
		else
		{
			finalPath = pathSteps;
			closestReachedPoint = start;
			finalPathCost = PathfinderResult.NO_PATH_COST;
		}

		done = !cancelled;

		boundary.clear();
		visited.clear();
		pending.clear();
		graph.release();
		sailing.release();

		stats.end(); // Include cleanup in stats to get the total cost of pathfinding

		if (completionCallback != null)
		{
			completionCallback.run();
		}
	}

	// PathfinderStats' counters are package-private in pathfinder, so this keeps its own: the
	// nodes it checked (a sailing search never checks transports, so getTransportsChecked()
	// inherits the base's constant 0) and its own start/end clock.
	private static final class Stats extends PathfinderStats
	{
		@Getter private int nodesChecked;
		private long startNanos, endNanos;
		private volatile boolean started, ended;

		void start()
		{
			started = true;
			nodesChecked = 0;
			startNanos = System.nanoTime();
		}

		void end()
		{
			endNanos = System.nanoTime();
			ended = true;
		}

		@Override
		public int getTotalNodesChecked()
		{
			return nodesChecked;
		}

		@Override
		public long getElapsedTimeNanos()
		{
			return endNanos - startNanos;
		}
	}
}
