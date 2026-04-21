package shortestpath.reachability.target;

import shortestpath.WorldPointUtil;
import shortestpath.reachability.mode.RouteMode;

public final class ReachabilityTarget {
    private final String description;
    private final int packedPoint;
    private final String category;
    private final int startPoint;
    private final RouteMode modeOverride;

    private ReachabilityTarget(String description, int packedPoint, String category, int startPoint, RouteMode modeOverride) {
        this.description = description;
        this.packedPoint = packedPoint;
        this.category = category;
        this.startPoint = startPoint;
        this.modeOverride = modeOverride;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getDescription() {
        return description;
    }

    public int getPackedPoint() {
        return packedPoint;
    }

    public String getCategory() {
        return category;
    }

    public int getStartPoint() {
        return startPoint;
    }

    public boolean hasStartPoint() {
        return startPoint != WorldPointUtil.UNDEFINED;
    }

    public RouteMode getModeOverride() {
        return modeOverride;
    }

    public boolean hasModeOverride() {
        return modeOverride != null;
    }

    public static final class Builder {
        private String description;
        private int packedPoint;
        private String category;
        private int startPoint = WorldPointUtil.UNDEFINED;
        private RouteMode modeOverride;

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder packedPoint(int packedPoint) {
            this.packedPoint = packedPoint;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder startPoint(int startPoint) {
            this.startPoint = startPoint;
            return this;
        }

        public Builder modeOverride(RouteMode modeOverride) {
            this.modeOverride = modeOverride;
            return this;
        }

        public ReachabilityTarget build() {
            return new ReachabilityTarget(description, packedPoint, category, startPoint, modeOverride);
        }
    }
}
