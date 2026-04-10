package shortestpath.reachability;

class ReachabilityTarget {
    private final String description;
    private final int packedPoint;

    ReachabilityTarget(String description, int packedPoint) {
        this.description = description;
        this.packedPoint = packedPoint;
    }

    String getDescription() {
        return description;
    }

    int getPackedPoint() {
        return packedPoint;
    }
}
