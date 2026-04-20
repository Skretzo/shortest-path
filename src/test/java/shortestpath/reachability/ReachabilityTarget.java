package shortestpath.reachability;

import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;

class ReachabilityTarget {
    private final String description;
    private final int packedPoint;
    private final String category;
    private final int startPoint;
    private final TeleportationItem teleportOverride;

    ReachabilityTarget(String description, int packedPoint) {
        this(description, packedPoint, null, WorldPointUtil.UNDEFINED, null);
    }

    ReachabilityTarget(String description, int packedPoint, String category, int startPoint, TeleportationItem teleportOverride) {
        this.description = description;
        this.packedPoint = packedPoint;
        this.category = category;
        this.startPoint = startPoint;
        this.teleportOverride = teleportOverride;
    }

    String getDescription() {
        return description;
    }

    int getPackedPoint() {
        return packedPoint;
    }

    String getCategory() {
        return category;
    }

    int getStartPoint() {
        return startPoint;
    }

    boolean hasStartPoint() {
        return startPoint != WorldPointUtil.UNDEFINED;
    }

    TeleportationItem getTeleportOverride() {
        return teleportOverride;
    }

    boolean hasTeleportOverride() {
        return teleportOverride != null;
    }
}
