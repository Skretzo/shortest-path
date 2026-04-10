package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.List;
import shortestpath.WorldPointUtil;

public class Node {
    public final int packedPosition;
    public final Node previous;
    public final int cost;
    // bankVisited records whether we have already visited a bank on the current path.
    public final boolean bankVisited;

    // A constructor which propagates the previous Node's banked state at the new position.
    public Node(int packedPosition, Node previous, int cost) {
        this(packedPosition, previous, cost, previous != null && previous.bankVisited);
    }

    public Node(int packedPosition, Node previous, int cost, boolean bankVisited) {
        this.packedPosition = packedPosition;
        this.previous = previous;
        this.cost = cost;
        this.bankVisited = bankVisited;
    }

    public Node(int packedPosition, Node previous) {
        this(packedPosition, previous, cost(packedPosition, previous));
    }

    public List<PathStep> getPathSteps() {
        Node node = this;
        int n = 0;

        while (node != null) {
            node = node.previous;
            n++;
        }

        List<PathStep> pathSteps = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pathSteps.add(null);
        }

        node = this;
        while (node != null) {
            pathSteps.set(--n, new PathStep(node.packedPosition, node.bankVisited));
            node = node.previous;
        }

        return pathSteps;
    }

    public static int cost(int packedPosition, Node previous) {
        int previousCost = 0;
        int travelTime = 0;

        if (previous != null) {
            previousCost = previous.cost;
            // Travel wait time in TransportNode and distance is compared as if the player is walking 1 tile/tick.
            // TODO: reduce the distance if the player is currently running and has enough run energy for the distance?
            travelTime = WorldPointUtil.distanceBetween(previous.packedPosition, packedPosition);
        }

        return previousCost + travelTime;
    }
}
