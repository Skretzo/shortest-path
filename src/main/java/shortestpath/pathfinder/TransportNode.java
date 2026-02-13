package shortestpath.pathfinder;

import lombok.Getter;

@Getter
public class TransportNode extends Node implements Comparable<TransportNode> {
    /**
     * If true, this transport destination should not be marked as visited when added to queue.
     * This allows cheaper paths to the same destination to be discovered later.
     * Used for teleports with cost thresholds (e.g., quetzal whistle).
     */
    private final boolean delayedVisit;

    public TransportNode(int packedPosition, Node previous, int travelTime, int additionalCost, boolean delayedVisit) {
        super(packedPosition, previous, cost(previous, travelTime + additionalCost));
        this.delayedVisit = delayedVisit;
    }

    private static int cost(Node previous, int travelTime) {
        return (previous != null ? previous.cost : 0) + travelTime;
    }

    @Override
    public int compareTo(TransportNode other) {
        return Integer.compare(cost, other.cost);
    }
}
