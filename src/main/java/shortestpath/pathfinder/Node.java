package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.List;
import shortestpath.WorldPointUtil;

public class Node {
    public enum Type {
        // A concrete world tile that can appear in the rendered path.
        // Search starts on a TILE node, walking/transport expansion stays on TILE nodes,
        // and bank state is still tracked on each tile node because it affects which
        // transports are legal from that point onward.
        TILE,
        // An abstract search-state node with no world position.
        // The search reaches one of these from a TILE node when it wants to consider a
        // global action set such as teleports. That abstract node is keyed by
        // AbstractNodeKind plus bankVisited, so it is only expanded once for each
        // relevant search state. Expanding the ABSTRACT node emits the legal teleports
        // for that state back into concrete TILE destination nodes. This avoids scanning
        // the full global teleport list from every visited tile while still allowing the
        // search to reconsider teleports when the state meaningfully changes
        // (for example when wilderness level drops into a new bucket, or when a future
        // abstract-node family is added).
        ABSTRACT
    }

    public final int packedPosition;
    public final Node previous;
    public final int cost;
    public final boolean bankVisited;
    public final Type type;
    // Only set for ABSTRACT nodes. TILE nodes leave this null.
    public final AbstractNodeKind abstractKind;

    // A constructor which propagates the previous Node's banked state at the new position.
    public Node(int packedPosition, Node previous, int cost) {
        this(packedPosition, previous, cost, previous != null && previous.bankVisited);
    }

    public Node(int packedPosition, Node previous, int cost, boolean bankVisited) {
        this(packedPosition, previous, cost, bankVisited, Type.TILE, null);
    }

    private Node(int packedPosition, Node previous, int cost, boolean bankVisited, Type type, AbstractNodeKind abstractKind) {
        this.packedPosition = packedPosition;
        this.previous = previous;
        this.cost = cost;
        this.bankVisited = bankVisited;
        this.type = type;
        this.abstractKind = abstractKind;
    }

    public static Node abstractNode(AbstractNodeKind abstractKind, Node previous, boolean bankVisited) {
        // Abstract nodes model global search states, not physical positions, so they carry no packed world point.
        return new Node(WorldPointUtil.UNDEFINED, previous, previous != null ? previous.cost : 0, bankVisited, Type.ABSTRACT, abstractKind);
    }

    public List<PathStep> getPathSteps() {
        Node node = this;
        int n = 0;

        while (node != null) {
            if (node.isTile()) {
                n++;
            }
            node = node.previous;
        }

        List<PathStep> pathSteps = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pathSteps.add(null);
        }

        node = this;
        while (node != null) {
            if (node.isTile()) {
                pathSteps.set(--n, new PathStep(node.packedPosition, node.bankVisited));
            }
            node = node.previous;
        }

        return pathSteps;
    }

    public boolean isTile() {
        return type == Type.TILE;
    }

    public int getClosestTilePosition() {
        Node node = this;
        while (node != null && !node.isTile()) {
            node = node.previous;
        }
        return node != null ? node.packedPosition : WorldPointUtil.UNDEFINED;
    }

    public static int cost(int packedPosition, Node previous) {
        int previousCost = 0;
        int travelTime = 0;

        if (previous != null) {
            previousCost = previous.cost;
            if (previous.isTile()) {
                // Travel wait time in TransportNode and distance is compared as if the player is walking 1 tile/tick.
                // TODO: reduce the distance if the player is currently running and has enough run energy for the distance?
                travelTime = WorldPointUtil.distanceBetween(previous.packedPosition, packedPosition);
            }
        }

        return previousCost + travelTime;
    }
}
