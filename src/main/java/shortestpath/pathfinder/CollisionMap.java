package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;


public class CollisionMap {
    // Enum.values() makes copies every time which hurts performance in the hotpath
    private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();

    private final SplitFlagMap collisionData;

    public byte[] getPlanes() {
        return collisionData.getRegionMapPlaneCounts();
    }

    public CollisionMap(SplitFlagMap collisionData) {
        this.collisionData = collisionData;
    }

    private boolean get(int x, int y, int z, int flag) {
        return collisionData.get(x, y, z, flag);
    }

    public boolean n(int x, int y, int z) {
        return get(x, y, z, 0);
    }

    public boolean s(int x, int y, int z) {
        return n(x, y - 1, z);
    }

    public boolean e(int x, int y, int z) {
        return get(x, y, z, 1);
    }

    public boolean w(int x, int y, int z) {
        return e(x - 1, y, z);
    }

    private boolean ne(int x, int y, int z) {
        return n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z);
    }

    private boolean nw(int x, int y, int z) {
        return n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z);
    }

    private boolean se(int x, int y, int z) {
        return s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z);
    }

    private boolean sw(int x, int y, int z) {
        return s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z);
    }

    public boolean isBlocked(int x, int y, int z) {
        return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z);
    }

    private static int packedPointFromOrdinal(int startPacked, OrdinalDirection direction) {
        final int x = WorldPointUtil.unpackWorldX(startPacked);
        final int y = WorldPointUtil.unpackWorldY(startPacked);
        final int plane = WorldPointUtil.unpackWorldPlane(startPacked);
        return WorldPointUtil.packWorldPoint(x + direction.x, y + direction.y, plane);
    }

    // This is only safe if pathfinding is single-threaded
    private final List<Node> neighbors = new ArrayList<>(16);
    private final boolean[] traversable = new boolean[8];

    public List<Node> getNeighbors(Node node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel,
        boolean targetInWilderness) {
        if (node.isTile()) {
            return getTileNeighbors(node, visited, config, wildernessLevel);
        } else {
            return getAbstractNodeNeighbors(node, visited, config, targetInWilderness);
        }
    }

    // Get neighbours for a walkable tile: 
    //      * Neighbouring tiles we can walk to
    //      * A transition into banked state, if the current tile is a bank.
    //      * Transition into abstract global teleport nodes, if we haven't tried that yet.
    private List<Node> getTileNeighbors(Node node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel) {
        final int x = WorldPointUtil.unpackWorldX(node.packedPosition);
        final int y = WorldPointUtil.unpackWorldY(node.packedPosition);
        final int z = WorldPointUtil.unpackWorldPlane(node.packedPosition);

        neighbors.clear();

        // Either we have already visited a bank, if the current tile is a bank switch into the bankVisited state for the
        // rest of the path.
        boolean pathBankVisited = node.bankVisited
            || (config.isBankPathEnabled() && config.getDestinations("bank").contains(node.packedPosition));

        // Firstly check if there are any transports or teleports which are applicable from the current tile.
        Set<Transport> transports = config.getTransportsPacked(pathBankVisited).getOrDefault(node.packedPosition, Set.of());
        for (Transport transport : transports) {
            // Do not consider a transport if we have already visited its target tile.
            if (visited.get(transport.getDestination(), pathBankVisited)) {
                continue;
            }
            // NB: Do not need to check for wilderness level for transports, since transports have specific origin tile.
            neighbors.add(new TransportNode(
                transport.getDestination(),
                node,
                transport.getDuration(),
                config.getAdditionalTransportCost(transport),
                pathBankVisited));
        }

        // Global teleports are only considered from an abstract node, so each wilderness/bank state expands them once.
        Node globalTeleports = Node.abstractNode(AbstractNodeKind.fromWildernessLevel(wildernessLevel), node, pathBankVisited);
        if (!visited.get(globalTeleports)) {
            neighbors.add(globalTeleports);
        }

        // Then add tiles which we can walk to, which go into the FIFO boundary queue.
        if (isBlocked(x, y, z)) {
            boolean westBlocked = isBlocked(x - 1, y, z);
            boolean eastBlocked = isBlocked(x + 1, y, z);
            boolean southBlocked = isBlocked(x, y - 1, z);
            boolean northBlocked = isBlocked(x, y + 1, z);
            boolean southWestBlocked = isBlocked(x - 1, y - 1, z);
            boolean southEastBlocked = isBlocked(x + 1, y - 1, z);
            boolean northWestBlocked = isBlocked(x - 1, y + 1, z);
            boolean northEastBlocked = isBlocked(x + 1, y + 1, z);
            traversable[0] = !westBlocked;
            traversable[1] = !eastBlocked;
            traversable[2] = !southBlocked;
            traversable[3] = !northBlocked;
            traversable[4] = !southWestBlocked && !westBlocked && !southBlocked;
            traversable[5] = !southEastBlocked && !eastBlocked && !southBlocked;
            traversable[6] = !northWestBlocked && !westBlocked && !northBlocked;
            traversable[7] = !northEastBlocked && !eastBlocked && !northBlocked;
        } else {
            traversable[0] = w(x, y, z);
            traversable[1] = e(x, y, z);
            traversable[2] = s(x, y, z);
            traversable[3] = n(x, y, z);
            traversable[4] = sw(x, y, z);
            traversable[5] = se(x, y, z);
            traversable[6] = nw(x, y, z);
            traversable[7] = ne(x, y, z);
        }

        for (int i = 0; i < traversable.length; i++) {
            OrdinalDirection d = ORDINAL_VALUES[i];
            int neighborPacked = packedPointFromOrdinal(node.packedPosition, d);
            if (visited.get(neighborPacked, pathBankVisited)) continue;

            if (traversable[i]) {
                neighbors.add(new Node(neighborPacked, node, Node.cost(neighborPacked, node), pathBankVisited));
            } else if (Math.abs(d.x + d.y) == 1 && isBlocked(x + d.x, y + d.y, z)) {
                // The transport starts from a blocked adjacent tile, e.g. fairy ring
                // Only checks non-teleport transports (includes portals and levers, but not items and spells)
                Set<Transport> neighborTransports = config.getTransportsPacked(pathBankVisited).getOrDefault(neighborPacked, Set.of());
                for (Transport transport : neighborTransports) {
                    if (transport.getOrigin() == Transport.UNDEFINED_ORIGIN
                        || !(transport.isUsableAtWildernessLevel(wildernessLevel))
                        || visited.get(transport.getOrigin(), pathBankVisited)) {
                        continue;
                    }
                    neighbors.add(new Node(transport.getOrigin(), node, Node.cost(transport.getOrigin(), node), pathBankVisited));
                }
            }
        }

        return neighbors;
    }

    // The only abstract nodes are currently for global teleports
    private List<Node> getAbstractNodeNeighbors(Node node, VisitedTiles visited, PathfinderConfig config,
        boolean targetInWilderness) {
        neighbors.clear();
        int sourceTile = node.getClosestTilePosition();
        for (Transport transport : config.getUsableTeleports(node.bankVisited)) {
            if (visited.get(transport.getDestination(), node.bankVisited)) {
                continue;
            }
            if (!transport.isUsableAtWildernessLevel(node.abstractKind.maxWildernessLevel())) {
                continue;
            }
            if (config.avoidWilderness(sourceTile, transport.getDestination(), targetInWilderness)) {
                continue;
            }
            neighbors.add(new TransportNode(
                transport.getDestination(),
                node,
                transport.getDuration(),
                config.getAdditionalTransportCost(transport),
                node.bankVisited));
        }
        return neighbors;
    }
}
