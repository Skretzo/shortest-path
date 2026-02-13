package shortestpath.pathfinder;

import lombok.Getter;

@Getter
public class TransportNode extends Node implements Comparable<TransportNode> {
    /**
     * If true, don't mark destination as visited when added to queue.
     * Used for teleports where a cheaper path might be discovered later.
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
