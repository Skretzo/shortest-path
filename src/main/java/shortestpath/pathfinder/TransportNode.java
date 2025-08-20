package shortestpath.pathfinder;

import lombok.Getter;

public class TransportNode extends Node implements Comparable<TransportNode> {
    @Getter
    private final int objectID;

    public TransportNode(int packedPosition, Node previous, int travelTime, int objectID) {
        super(packedPosition, previous, cost(previous, travelTime));

        this.objectID = objectID;
    }

    private static int cost(Node previous, int travelTime) {
        return (previous != null ? previous.cost : 0) + travelTime;
    }

    @Override
    public int compareTo(TransportNode other) {
        return Integer.compare(cost, other.cost);
    }
}
