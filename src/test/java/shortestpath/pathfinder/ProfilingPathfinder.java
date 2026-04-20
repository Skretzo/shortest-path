package shortestpath.pathfinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

/**
 * Test-only pathfinder that replicates the core search loop from {@link Pathfinder}
 * with full profiling instrumentation. This class never ships in the production build.
 *
 * <p>The search algorithm is kept structurally identical to
 * {@code Pathfinder.run()} so that the {@code profilingDoesNotAffectResults}
 * test can catch any drift.</p>
 */
public class ProfilingPathfinder {

    private final PathfinderConfig config;
    private final CollisionMap map;
    private final int start;
    private final Set<Integer> targets;
    private final boolean targetInWilderness;

    private final Deque<Node> boundary = new ArrayDeque<>(4096);
    private final Queue<TransportNode> pending = new PriorityQueue<>(256);
    private final VisitedTiles visited;

    private PathfinderProfile profile;
    private PathfinderResult result;

    private Node bestLastNode;
    private int bestRemainingDistance = Integer.MAX_VALUE;
    private int bestTravelledDistance = Integer.MAX_VALUE;
    private int bestX = Integer.MAX_VALUE;
    private int bestY = Integer.MAX_VALUE;
    private int reachedTarget = WorldPointUtil.UNDEFINED;
    private PathTerminationReason terminationReason;
    private int wildernessLevel;

    private int nodesChecked;
    private int transportsChecked;

    // Shared neighbor list, matching CollisionMap's single-threaded assumption
    private final List<Node> neighbors = new ArrayList<>(16);
    private final boolean[] traversable = new boolean[8];
    private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();

    public ProfilingPathfinder(PathfinderConfig config, int start, Set<Integer> targets) {
        this.config = config;
        this.map = config.getMap();
        this.start = start;
        this.targets = targets;
        this.visited = new VisitedTiles(map);
        this.targetInWilderness = WildernessChecker.isInWilderness(targets);
        this.wildernessLevel = 31;
        this.profile = new PathfinderProfile();
    }

    /**
     * Runs the pathfinding algorithm with full profiling. After this method
     * returns, {@link #getProfile()} and {@link #getResult()} are available.
     */
    public void run() {
        long startNanos = System.nanoTime();
        boundary.addFirst(new Node(start, null, 0, false));

        long cutoffDurationMillis = config.getCalculationCutoffMillis();
        long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
        int iteration = 0;

        while (!boundary.isEmpty() || !pending.isEmpty()) {
            // ── Queue selection phase ──
            long phaseStart = System.nanoTime();

            Node node = boundary.peekFirst();
            TransportNode p = pending.peek();

            if (p != null && (node == null || p.compareCost() < node.cost)) {
                node = pending.poll();

                // For delayed-visit nodes, check if the destination was already
                // reached by a cheaper path while this node was queued.
                if (node instanceof TransportNode && ((TransportNode) node).delayedVisit) {
                    if (visited.get(node.packedPosition, node.bankVisited)) {
                        profile.delayedVisitSkipped++;
                        profile.queueSelectionNanos += System.nanoTime() - phaseStart;
                        continue;
                    }
                    visited.set(node.packedPosition, node.bankVisited);
                }
            } else {
                node = boundary.removeFirst();
            }

            profile.queueSelectionNanos += System.nanoTime() - phaseStart;

            // ── Wilderness check phase ──
            if (node.isTile()) {
                phaseStart = System.nanoTime();
                updateWildernessLevel(node);
                profile.wildernessCheckNanos += System.nanoTime() - phaseStart;
            }

            // ── Target check phase ──
            phaseStart = System.nanoTime();

            if (node.isTile() && targets.contains(node.packedPosition)) {
                bestLastNode = node;
                reachedTarget = node.packedPosition;
                terminationReason = PathTerminationReason.TARGET_REACHED;
                profile.targetCheckNanos += System.nanoTime() - phaseStart;
                break;
            }

            if (node.isTile() && updateBestPathWhenUnreachable(node)) {
                cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
            }

            profile.targetCheckNanos += System.nanoTime() - phaseStart;

            // ── Cutoff check phase ──
            phaseStart = System.nanoTime();

            if (System.currentTimeMillis() > cutoffTimeMillis) {
                terminationReason = PathTerminationReason.CUTOFF_REACHED;
                profile.cutoffCheckNanos += System.nanoTime() - phaseStart;
                break;
            }

            profile.cutoffCheckNanos += System.nanoTime() - phaseStart;

            // ── addNeighbors phase ──
            phaseStart = System.nanoTime();
            addNeighbors(node);
            profile.addNeighborsNanos += System.nanoTime() - phaseStart;

            // ── Bookkeeping phase ──
            phaseStart = System.nanoTime();

            profile.updatePeakBoundarySize(boundary.size());
            profile.updatePeakPendingSize(pending.size());
            iteration++;
            if (profile.shouldSample(iteration)) {
                profile.recordSample(iteration, boundary.size(), pending.size(),
                    node.cost, System.nanoTime() - startNanos);
            }

            profile.bookkeepingNanos += System.nanoTime() - phaseStart;
        }

        if (terminationReason == null) {
            terminationReason = PathTerminationReason.SEARCH_EXHAUSTED;
        }

        long elapsedNanos = System.nanoTime() - startNanos;

        boundary.clear();
        visited.clear();
        pending.clear();

        boolean reached = reachedTarget != WorldPointUtil.UNDEFINED;
        int target = reached ? reachedTarget : (targets.isEmpty() ? WorldPointUtil.UNDEFINED : targets.iterator().next());
        int closestReached = bestLastNode != null ? bestLastNode.getClosestTilePosition() : start;
        List<PathStep> path = bestLastNode != null ? bestLastNode.getPathSteps() : List.of();

        result = new PathfinderResult(start, target, reached, path, closestReached,
            nodesChecked, transportsChecked, elapsedNanos, terminationReason);
    }

    // ── addNeighbors: matches Pathfinder.addNeighbors exactly ───────────

    private void addNeighbors(Node node) {
        List<Node> nodes;
        if (node.isTile()) {
            nodes = getTileNeighbors(node);
        } else {
            nodes = getAbstractNodeNeighbors(node);
        }

        for (Node neighbor : nodes) {
            if (node.isTile() && neighbor.isTile()
                && config.avoidWilderness(node.packedPosition, neighbor.packedPosition, targetInWilderness)) {
                continue;
            }

            // For delayed-visit nodes (shared destinations), don't mark as visited on enqueue.
            // They will be checked and marked when dequeued from pending.
            if (!(neighbor instanceof TransportNode && ((TransportNode) neighbor).delayedVisit)) {
                visited.set(neighbor);
            } else {
                profile.delayedVisitEnqueued++;
            }
            if (neighbor instanceof TransportNode) {
                pending.add((TransportNode) neighbor);
                ++transportsChecked;
                profile.transportNeighborsAdded++;
            } else {
                boundary.addLast(neighbor);
                ++nodesChecked;
                profile.tileNeighborsAdded++;
            }
        }

        // Tile visit counting for tile nodes
        if (node.isTile()) {
            profile.incrementTileVisit(node.packedPosition);
        }
    }

    // ── getTileNeighbors: matches CollisionMap.getTileNeighbors ─────────
    // Only calls visited.get() (never visited.set()), returns the neighbor list.

    private List<Node> getTileNeighbors(Node node) {
        final int x = WorldPointUtil.unpackWorldX(node.packedPosition);
        final int y = WorldPointUtil.unpackWorldY(node.packedPosition);
        final int z = WorldPointUtil.unpackWorldPlane(node.packedPosition);

        neighbors.clear();

        // ── Bank check sub-phase ──
        long subStart = System.nanoTime();

        boolean pathBankVisited = node.bankVisited
            || (config.isBankPathEnabled() && config.getDestinations("bank").contains(node.packedPosition));

        profile.bankCheckNanos += System.nanoTime() - subStart;
        if (pathBankVisited && !node.bankVisited) {
            profile.bankTransitions++;
        }

        // ── Transport lookup sub-phase ──
        subStart = System.nanoTime();

        Set<Transport> transports = config.getTransportsPacked(pathBankVisited).getOrDefault(node.packedPosition, Set.of());
        int inheritedDifferential = (node instanceof TransportNode && ((TransportNode) node).delayedVisit)
            ? ((TransportNode) node).differentialCost
            : 0;
        for (Transport transport : transports) {
            profile.transportEvaluations++;
            boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
            if (!delayedVisit && visited.get(transport.getDestination(), pathBankVisited)) {
                profile.visitedSkipped++;
                continue;
            }
            int chainPenalty = (delayedVisit && inheritedDifferential > 0) ? inheritedDifferential : 0;
            neighbors.add(new TransportNode(
                transport.getDestination(), node,
                transport.getDuration(), config.getAdditionalTransportCost(transport) + chainPenalty,
                pathBankVisited,
                delayedVisit,
                delayedVisit ? config.getDifferentialCost(transport) : 0));
        }

        profile.transportLookupNanos += System.nanoTime() - subStart;

        // ── Abstract node sub-phase ──
        subStart = System.nanoTime();

        Node globalTeleports = Node.abstractNode(AbstractNodeKind.fromWildernessLevel(wildernessLevel), node, pathBankVisited);
        if (!visited.get(globalTeleports)) {
            neighbors.add(globalTeleports);
            profile.abstractNodesExpanded++;
        }

        profile.abstractNodeNanos += System.nanoTime() - subStart;

        // ── Collision check sub-phase ──
        subStart = System.nanoTime();

        if (map.isBlocked(x, y, z)) {
            boolean westBlocked = map.isBlocked(x - 1, y, z);
            boolean eastBlocked = map.isBlocked(x + 1, y, z);
            boolean southBlocked = map.isBlocked(x, y - 1, z);
            boolean northBlocked = map.isBlocked(x, y + 1, z);
            boolean southWestBlocked = map.isBlocked(x - 1, y - 1, z);
            boolean southEastBlocked = map.isBlocked(x + 1, y - 1, z);
            boolean northWestBlocked = map.isBlocked(x - 1, y + 1, z);
            boolean northEastBlocked = map.isBlocked(x + 1, y + 1, z);
            traversable[0] = !westBlocked;
            traversable[1] = !eastBlocked;
            traversable[2] = !southBlocked;
            traversable[3] = !northBlocked;
            traversable[4] = !southWestBlocked && !westBlocked && !southBlocked;
            traversable[5] = !southEastBlocked && !eastBlocked && !southBlocked;
            traversable[6] = !northWestBlocked && !westBlocked && !northBlocked;
            traversable[7] = !northEastBlocked && !eastBlocked && !northBlocked;
        } else {
            traversable[0] = map.w(x, y, z);
            traversable[1] = map.e(x, y, z);
            traversable[2] = map.s(x, y, z);
            traversable[3] = map.n(x, y, z);
            // Diagonals: same logic as CollisionMap's private sw/se/nw/ne methods
            traversable[4] = map.s(x, y, z) && map.w(x, y - 1, z) && map.w(x, y, z) && map.s(x - 1, y, z);
            traversable[5] = map.s(x, y, z) && map.e(x, y - 1, z) && map.e(x, y, z) && map.s(x + 1, y, z);
            traversable[6] = map.n(x, y, z) && map.w(x, y + 1, z) && map.w(x, y, z) && map.n(x - 1, y, z);
            traversable[7] = map.n(x, y, z) && map.e(x, y + 1, z) && map.e(x, y, z) && map.n(x + 1, y, z);
        }

        profile.collisionCheckNanos += System.nanoTime() - subStart;

        // ── Walkable tile iteration sub-phase ──
        subStart = System.nanoTime();

        for (int i = 0; i < traversable.length; i++) {
            OrdinalDirection d = ORDINAL_VALUES[i];
            int neighborPacked = WorldPointUtil.packWorldPoint(x + d.x, y + d.y, z);
            if (visited.get(neighborPacked, pathBankVisited)) continue;

            if (traversable[i]) {
                neighbors.add(new Node(neighborPacked, node, Node.cost(neighborPacked, node), pathBankVisited));
            } else if (Math.abs(d.x + d.y) == 1 && map.isBlocked(x + d.x, y + d.y, z)) {
                // Blocked-tile transport fallback
                profile.walkableTileNanos += System.nanoTime() - subStart;
                subStart = System.nanoTime();

                Set<Transport> neighborTransports = config.getTransportsPacked(pathBankVisited).getOrDefault(neighborPacked, Set.of());
                for (Transport transport : neighborTransports) {
                    profile.blockedTileTransportChecks++;
                    if (transport.getOrigin() == Transport.UNDEFINED_ORIGIN
                        || !transport.isUsableAtWildernessLevel(wildernessLevel)
                        || visited.get(transport.getOrigin(), pathBankVisited)) {
                        continue;
                    }
                    neighbors.add(new Node(transport.getOrigin(), node, Node.cost(transport.getOrigin(), node), pathBankVisited));
                }

                profile.blockedTileTransportNanos += System.nanoTime() - subStart;
                subStart = System.nanoTime();
            }
        }

        profile.walkableTileNanos += System.nanoTime() - subStart;

        return neighbors;
    }

    // ── getAbstractNodeNeighbors: matches CollisionMap.getAbstractNodeNeighbors ──

    private List<Node> getAbstractNodeNeighbors(Node node) {
        neighbors.clear();
        int sourceTile = node.getClosestTilePosition();
        for (Transport transport : config.getUsableTeleports(node.bankVisited)) {
            profile.transportEvaluations++;
            boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
            if (!delayedVisit && visited.get(transport.getDestination(), node.bankVisited)) {
                profile.visitedSkipped++;
                continue;
            }
            if (!transport.isUsableAtWildernessLevel(node.abstractKind.maxWildernessLevel())) {
                continue;
            }
            if (config.avoidWilderness(sourceTile, transport.getDestination(), targetInWilderness)) {
                continue;
            }
            int differentialCost = delayedVisit ? config.getDifferentialCost(transport) : 0;
            neighbors.add(new TransportNode(
                transport.getDestination(), node,
                transport.getDuration(), config.getAdditionalTransportCost(transport),
                node.bankVisited,
                delayedVisit,
                differentialCost));
        }
        return neighbors;
    }

    private boolean updateBestPathWhenUnreachable(Node node) {
        boolean update = false;
        for (int target : targets) {
            int remainingDistance = WorldPointUtil.distanceBetween(target, node.packedPosition, WorldPointUtil.EUCLIDEAN_SQUARED_DISTANCE_METRIC);
            int travelledDistance = node.cost;
            int x = WorldPointUtil.unpackWorldX(node.packedPosition);
            int y = WorldPointUtil.unpackWorldY(node.packedPosition);
            if ((remainingDistance < bestRemainingDistance) ||
                (remainingDistance == bestRemainingDistance && travelledDistance < bestTravelledDistance) ||
                (remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x < bestX) ||
                (remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x == bestX && y < bestY)) {
                bestRemainingDistance = remainingDistance;
                bestTravelledDistance = travelledDistance;
                bestX = x;
                bestY = y;
                bestLastNode = node;
                update = true;
            }
        }
        return update;
    }

    private void updateWildernessLevel(Node node) {
        int previousLevel = wildernessLevel;
        if (wildernessLevel > 0) {
            if (wildernessLevel > 30 && !WildernessChecker.isInLevel30Wilderness(node.packedPosition)) {
                wildernessLevel = 30;
            }
            if (wildernessLevel > 20 && !WildernessChecker.isInLevel20Wilderness(node.packedPosition)) {
                wildernessLevel = 20;
            }
            if (wildernessLevel > 0 && !WildernessChecker.isInWilderness(node.packedPosition)) {
                wildernessLevel = 0;
            }
        }
        if (wildernessLevel != previousLevel) {
            profile.wildernessLevelChanges++;
        }
    }

    public int getStart() { return start; }
    public Set<Integer> getTargets() { return targets; }
    public PathfinderProfile getProfile() { return profile; }
    public PathfinderResult getResult() { return result; }
}
