package shortestpath.pathfinder;

public class TransportNode extends Node implements Comparable<TransportNode> {
    final boolean delayedVisit;
    /** Extra cost used only for priority queue ordering, not propagated to children */
    final int differentialCost;

    public TransportNode(int packedPosition, Node previous, int travelTime, int additionalCost, boolean bankVisited, boolean delayedVisit) {
        this(packedPosition, previous, travelTime, additionalCost, bankVisited, delayedVisit, 0);
    }

    public TransportNode(int packedPosition, Node previous, int travelTime, int additionalCost, boolean bankVisited, boolean delayedVisit, int differentialCost) {
        super(packedPosition, previous, cost(previous, travelTime + additionalCost), bankVisited);
        this.delayedVisit = delayedVisit;
        this.differentialCost = differentialCost;
    }

    /** The cost used for priority queue ordering, includes the differential */
    public int compareCost() {
        return cost + differentialCost;
    }

    private static int cost(Node previous, int travelTime) {
        return (previous != null ? previous.cost : 0) + travelTime;
    }

    @Override
    public int compareTo(TransportNode other) {
        return Integer.compare(this.compareCost(), other.compareCost());
    }
}
