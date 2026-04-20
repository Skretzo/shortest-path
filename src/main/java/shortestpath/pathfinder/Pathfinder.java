package shortestpath.pathfinder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import lombok.Getter;
import shortestpath.WorldPointUtil;

public class Pathfinder implements Runnable
{
	private final PathfinderStats stats;
	@Getter
	private volatile boolean done = false;
	private volatile boolean cancelled = false;

	@Getter
	private final int start;
	@Getter
	private final Set<Integer> targets;

	private final PathfinderConfig config;
	private final CollisionMap map;
	private final boolean targetInWilderness;
	private final Runnable completionCallback;

	// Capacities should be enough to store all nodes without requiring the queue to grow
	// They were found by checking the max queue size
	private final Deque<Node> boundary = new ArrayDeque<>(4096);
	private final Queue<TransportNode> pending = new PriorityQueue<>(256);
	private final VisitedTiles visited;

	private List<PathStep> pathSteps = List.of();
	private boolean pathNeedsUpdate = false;
	private Node bestLastNode;
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

	public Pathfinder(PathfinderConfig config, int start, Set<Integer> targets, Runnable completionCallback)
	{
		stats = new PathfinderStats();
		this.config = config;
		this.map = config.getMap();
		this.start = start;
		this.targets = targets;
		this.completionCallback = completionCallback;
		visited = new VisitedTiles(map);
		targetInWilderness = WildernessChecker.isInWilderness(targets);
		wildernessLevel = 31;
	}

	public Pathfinder(PathfinderConfig config, int start, Set<Integer> targets)
	{
		this(config, start, targets, null);
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
		Node lastNode = bestLastNode; // For thread safety, read bestLastNode once
		if (lastNode == null)
		{
			return pathSteps;
		}

		if (pathNeedsUpdate)
		{
			pathSteps = lastNode.getPathSteps();
			pathNeedsUpdate = false;
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
		int target = reached ? reachedTarget : (targets.isEmpty() ? WorldPointUtil.UNDEFINED : targets.iterator().next());
		int closestReachedPoint = bestLastNode != null ? bestLastNode.getClosestTilePosition() : start;
		return new PathfinderResult(
			start,
			target,
			reached,
			currentPath,
			closestReachedPoint,
			currentStats.getNodesChecked(),
			currentStats.getTransportsChecked(),
			currentStats.getElapsedTimeNanos(),
			terminationReason
		);
	}

	private void addNeighbors(Node node)
	{
		List<Node> nodes = map.getNeighbors(node, visited, config, wildernessLevel, targetInWilderness);
		for (Node neighbor : nodes)
		{
			if (node.isTile() && neighbor.isTile()
				&& config.avoidWilderness(node.packedPosition, neighbor.packedPosition, targetInWilderness))
			{
				continue;
			}

			// For delayed-visit nodes (shared destinations), don't mark as visited on enqueue.
			// They will be checked and marked when dequeued from pending.
			if (!(neighbor instanceof TransportNode && ((TransportNode) neighbor).delayedVisit))
			{
				visited.set(neighbor);
			}
			if (neighbor instanceof TransportNode)
			{
				pending.add((TransportNode) neighbor);
				++stats.transportsChecked;
			}
			else
			{
				boundary.addLast(neighbor);
				++stats.nodesChecked;
			}
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
	private boolean updateBestPathWhenUnreachable(Node node)
	{
		boolean update = false;

		for (int target : targets)
		{
			int remainingDistance = WorldPointUtil.distanceBetween(target, node.packedPosition, WorldPointUtil.EUCLIDEAN_SQUARED_DISTANCE_METRIC);
			int travelledDistance = node.cost;
			int x = WorldPointUtil.unpackWorldX(node.packedPosition);
			int y = WorldPointUtil.unpackWorldY(node.packedPosition);
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
	private void updateWildernessLevel(Node node)
	{
		if (wildernessLevel > 0)
		{
			// These are overlapping boundaries, so if the node isn't in level 30, it's in 0-29
			// likewise, if the node isn't in level 20, it's in 0-19
			if (wildernessLevel > 30 && !WildernessChecker.isInLevel30Wilderness(node.packedPosition))
			{
				wildernessLevel = 30;
			}
			if (wildernessLevel > 20 && !WildernessChecker.isInLevel20Wilderness(node.packedPosition))
			{
				wildernessLevel = 20;
			}
			if (wildernessLevel > 0 && !WildernessChecker.isInWilderness(node.packedPosition))
			{
				wildernessLevel = 0;
			}
		}
	}

	@Override
	public void run()
	{
		stats.start();
		boundary.addFirst(new Node(start, null, 0, false));

		long cutoffDurationMillis = config.getCalculationCutoffMillis();
		long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;

		while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty()))
		{
			Node node = boundary.peekFirst();
			TransportNode p = pending.peek();


			if (p != null && (node == null || p.compareCost() < node.cost))
			{
				node = pending.poll();

				// For delayed-visit nodes, check if the destination was already
				// reached by a cheaper path while this node was queued.
				if (node instanceof TransportNode && ((TransportNode) node).delayedVisit)
				{
					if (visited.get(node.packedPosition, node.bankVisited))
					{
						continue;
					}
					visited.set(node.packedPosition, node.bankVisited);
				}
			}
			else
			{
				node = boundary.removeFirst();
			}

			if (node.isTile())
			{
				updateWildernessLevel(node);
			}

			if (node.isTile() && targets.contains(node.packedPosition))
			{
				bestLastNode = node;
				pathNeedsUpdate = true;
				reachedTarget = node.packedPosition;
				terminationReason = PathTerminationReason.TARGET_REACHED;
				break;
			}

			if (node.isTile() && updateBestPathWhenUnreachable(node))
			{
				cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
			}

			if (System.currentTimeMillis() > cutoffTimeMillis)
			{
				terminationReason = PathTerminationReason.CUTOFF_REACHED;
				break;
			}

			addNeighbors(node);
		}

		if (cancelled)
		{
			terminationReason = PathTerminationReason.CANCELLED;
		}
		else if (terminationReason == null)
		{
			terminationReason = PathTerminationReason.SEARCH_EXHAUSTED;
		}

		done = !cancelled;

		boundary.clear();
		visited.clear();
		pending.clear();

		stats.end(); // Include cleanup in stats to get the total cost of pathfinding

		if (completionCallback != null)
		{
			completionCallback.run();
		}
	}

	public static class PathfinderStats
	{
		@Getter
		private int nodesChecked = 0, transportsChecked = 0;
		private long startNanos, endNanos;
		private volatile boolean started = false, ended = false;

		public int getTotalNodesChecked()
		{
			return nodesChecked + transportsChecked;
		}

		public long getElapsedTimeNanos()
		{
			return endNanos - startNanos;
		}

		private void start()
		{
			started = true;
			nodesChecked = 0;
			transportsChecked = 0;
			startNanos = System.nanoTime();
		}

		private void end()
		{
			endNanos = System.nanoTime();
			ended = true;
		}
	}
}
