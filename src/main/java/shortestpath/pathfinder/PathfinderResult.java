package shortestpath.pathfinder;

import lombok.Getter;
import shortestpath.PrimitiveIntList;

@Getter
public class PathfinderResult {
    private final int start;
    private final int target;
    private final boolean reached;
    private final PrimitiveIntList path;
    private final int closestReachedPoint;
    private final int nodesChecked;
    private final int transportsChecked;
    private final long elapsedNanos;
    private final PathTerminationReason terminationReason;

    public PathfinderResult(
        int start,
        int target,
        boolean reached,
        PrimitiveIntList path,
        int closestReachedPoint,
        int nodesChecked,
        int transportsChecked,
        long elapsedNanos,
        PathTerminationReason terminationReason
    ) {
        this.start = start;
        this.target = target;
        this.reached = reached;
        this.path = path;
        this.closestReachedPoint = closestReachedPoint;
        this.nodesChecked = nodesChecked;
        this.transportsChecked = transportsChecked;
        this.elapsedNanos = elapsedNanos;
        this.terminationReason = terminationReason;
    }
}
