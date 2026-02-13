package shortestpath.pathfinder;

import lombok.Getter;
import shortestpath.PrimitiveIntList;
import shortestpath.ShortestPathPlugin;
import shortestpath.WorldPointUtil;

import java.util.*;

public class Pathfinder implements Runnable {
    private final PathfinderStats stats;
    @Getter
    private volatile boolean done = false;
    private volatile boolean cancelled = false;

    @Getter
    private final int start;
    @Getter
    private final Set<Integer> targets;

    private final ShortestPathPlugin plugin;
    private final PathfinderConfig config;
    private final CollisionMap map;
    private final boolean targetInWilderness;

    // Capacities should be enough to store all nodes without requiring the queue to grow
    // They were found by checking the max queue size
    private final Deque<Node> boundary = new ArrayDeque<>(4096);
    private final Queue<Node> pending = new PriorityQueue<>(256);
    private final VisitedTiles visited;

    private PrimitiveIntList path = new PrimitiveIntList();
    private boolean pathNeedsUpdate = false;
    private Node bestLastNode;
    /**
     * Teleportation transports are updated when this changes.
     * Can be either:
     * 0 = all teleports can be used (e.g. Chronicle)
     * 20 = most teleports can be used (e.g. Varrock Teleport)
     * 30 = some teleports can be used (e.g. Amulet of Glory)
     * 31 = no teleports can be used
     */
    private int wildernessLevel;

    public Pathfinder(ShortestPathPlugin plugin, PathfinderConfig config, int start, Set<Integer> targets) {
        stats = new PathfinderStats();
        this.plugin = plugin;
        this.config = config;
        this.map = config.getMap();
        this.start = start;
        this.targets = targets;
        visited = new VisitedTiles(map);
        targetInWilderness = WildernessChecker.isInWilderness(targets);
        wildernessLevel = 31;
    }

    public void cancel() {
        cancelled = true;
    }

    public PathfinderStats getStats() {
        if (stats.started && stats.ended) {
            return stats;
        }

        // Don't give incomplete results
        return null;
    }

    public PrimitiveIntList getPath() {
        Node lastNode = bestLastNode; // For thread safety, read bestLastNode once
        if (lastNode == null) {
            return path;
        }

        if (pathNeedsUpdate) {
            path = lastNode.getPath();
            pathNeedsUpdate = false;
        }

        return path;
    }

    private void addNeighbors(Node node) {
        List<Node> nodes = map.getNeighbors(node, visited, config, wildernessLevel);
        for (Node neighbor : nodes) {
            if (config.avoidWilderness(node.packedPosition, neighbor.packedPosition, targetInWilderness)) {
                continue;
            }

            if (neighbor instanceof TransportNode) {
                // Don't mark transport destinations as visited immediately
                // This ensures lower-cost paths are not blocked by higher-cost transports
                // that happen to be discovered first (e.g., quetzal whistle vs platform)
                // The destination will be marked visited when the node is actually processed
                pending.add(neighbor);
                ++stats.transportsChecked;
            } else {
                visited.set(neighbor.packedPosition);
                boundary.addLast(neighbor);
                ++stats.nodesChecked;
            }
        }
    }

    @Override
    public void run() {
        stats.start();
        boundary.addFirst(new Node(start, null));

        int bestDistance = Integer.MAX_VALUE;
        long bestHeuristic = Integer.MAX_VALUE;
        long cutoffDurationMillis = config.getCalculationCutoffMillis();
        long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;

        while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty())) {
            Node node = boundary.peekFirst();
            Node p = pending.peek();

            if (p != null && (node == null || p.cost < node.cost)) {
                node = pending.poll();
                // Check if destination was already reached via cheaper path
                // This is necessary because we don't mark transport destinations as visited
                // when adding them to the queue (to allow lower-cost paths to be discovered)
                if (visited.get(node.packedPosition)) {
                    continue; // Skip - already visited via cheaper path
                }
                visited.set(node.packedPosition);
            } else {
                node = boundary.removeFirst();
            }

            if (wildernessLevel > 0) {
                // We don't need to remove teleports when going from 20 to 21 or higher,
                // because the teleport is either used at the very start of the
                // path or when going from 31 or higher to 30, or from 21 or higher to 20.

                boolean update = false;

                // These are overlapping boundaries, so if the node isn't in level 30, it's in 0-29
                // likewise, if the node isn't in level 20, it's in 0-19
                if (wildernessLevel > 30 && !WildernessChecker.isInLevel30Wilderness(node.packedPosition)) {
                    wildernessLevel = 30;
                    update = true;
                }
                if (wildernessLevel > 20 && !WildernessChecker.isInLevel20Wilderness(node.packedPosition)) {
                    wildernessLevel = 20;
                    update = true;
                }
                if (wildernessLevel > 0 && !WildernessChecker.isInWilderness(node.packedPosition)) {
                    wildernessLevel = 0;
                    update = true;
                }
                if (update) {
                    config.refreshTeleports(node.packedPosition, wildernessLevel);
                }
            }

            if (targets.contains(node.packedPosition)) {
                bestLastNode = node;
                pathNeedsUpdate = true;
                break;
            }

            for (int target : targets) {
                int distance = WorldPointUtil.distanceBetween(node.packedPosition, target);
                long heuristic = distance + (long) WorldPointUtil.distanceBetween(node.packedPosition, target, 2);
                if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                    bestLastNode = node;
                    pathNeedsUpdate = true;
                    bestDistance = distance;
                    bestHeuristic = heuristic;
                    cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
                }
            }

            if (System.currentTimeMillis() > cutoffTimeMillis) {
                break;
            }

            addNeighbors(node);
        }

        done = !cancelled;

        boundary.clear();
        visited.clear();
        pending.clear();

        stats.end(); // Include cleanup in stats to get the total cost of pathfinding

        plugin.postPluginMessages();
    }

    public static class PathfinderStats {
        @Getter
        private int nodesChecked = 0, transportsChecked = 0;
        private long startNanos, endNanos;
        private volatile boolean started = false, ended = false;

        public int getTotalNodesChecked() {
            return nodesChecked + transportsChecked;
        }

        public long getElapsedTimeNanos() {
            return endNanos - startNanos;
        }

        private void start() {
            started = true;
            nodesChecked = 0;
            transportsChecked = 0;
            startNanos = System.nanoTime();
        }

        private void end() {
            endNanos = System.nanoTime();
            ended = true;
        }
    }
}
