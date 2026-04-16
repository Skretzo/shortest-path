package shortestpath.pathfinder;

public class TransportNode extends Node implements Comparable<TransportNode> {
    public TransportNode(int packedPosition, Node previous, int travelTime, int additionalCost) {
        this(packedPosition, previous, travelTime, additionalCost, previous != null && previous.bankVisited);
    }

    public TransportNode(int packedPosition, Node previous, int travelTime, int additionalCost, boolean bankVisited) {
        super(packedPosition, previous, cost(previous, travelTime + additionalCost), bankVisited);
    }

    private static int cost(Node previous, int travelTime) {
        return (previous != null ? previous.cost : 0) + travelTime;
    }

    @Override
    public int compareTo(TransportNode other) {
        return Integer.compare(cost, other.cost);
    }
}
