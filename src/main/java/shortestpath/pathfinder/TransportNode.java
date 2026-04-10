package shortestpath.pathfinder;

public class TransportNode extends Node implements Comparable<TransportNode> {
    final boolean delayedVisit;

    public TransportNode(int packedPosition, Node previous, int travelTime, int additionalCost) {
        this(packedPosition, previous, travelTime, additionalCost, previous != null && previous.bankVisited, false);
    }

    public TransportNode(int packedPosition, Node previous, int travelTime, int additionalCost, boolean bankVisited) {
        this(packedPosition, previous, travelTime, additionalCost, bankVisited, false);
    }

    public TransportNode(int packedPosition, Node previous, int travelTime, int additionalCost, boolean bankVisited, boolean delayedVisit) {
        super(packedPosition, previous, cost(previous, travelTime + additionalCost), bankVisited);
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
