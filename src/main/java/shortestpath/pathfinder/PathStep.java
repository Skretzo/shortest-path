package shortestpath.pathfinder;

import lombok.Getter;

@Getter
public final class PathStep {
    private final int packedPosition;
    private final boolean bankVisited;

    public PathStep(int packedPosition, boolean bankVisited) {
        this.packedPosition = packedPosition;
        this.bankVisited = bankVisited;
    }
}
