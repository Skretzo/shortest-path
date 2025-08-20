package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import shortestpath.WorldPointUtil;

public class Node {
    public final int packedPosition;
    public final Node previous;
    public final int cost;

    public Node(int packedPosition, Node previous, int cost) {
        this.packedPosition = packedPosition;
        this.previous = previous;
        this.cost = cost;
    }

    public Node(int packedPosition, Node previous) {
        this(packedPosition, previous, cost(packedPosition, previous));
    }

    public List<Integer> getPath() {
        List<Integer> path = new LinkedList<>();
        Node node = this;

        while (node != null) {
            path.add(0, node.packedPosition);
            node = node.previous;
        }

        return new ArrayList<>(path);
    }

    public List<Integer> getObjectIDs(){
        List<Integer> path = new LinkedList<>();
        Node node = this;

        while (node != null) {
            if (node instanceof TransportNode){
                int id = ((TransportNode)node).getObjectID();
                if (id > 0)
                    path.add(id);
            }
            node = node.previous;
        }

        return new ArrayList<>(path);
    }

    public List<Integer> getPathPacked() {
        List<Integer> path = new LinkedList<>();
        Node node = this;

        while (node != null) {
            path.add(0, node.packedPosition);
            node = node.previous;
        }

        return new ArrayList<>(path);
    }

    private static int cost(int packedPosition, Node previous) {
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
