package shortestpath.dashboard;

import java.util.List;

public final class PathfinderDashboardModels {
    private PathfinderDashboardModels() {
    }

    public static class BundleIndex {
        public List<BundleManifest> bundles;
    }

    public static class BundleManifest {
        public String id;
        public String title;
        public String subtitle;
        public String reportPath;
        public String generatedAt;
    }

    public static class Report {
        public String generatedAt;
        public String title;
        public String subtitle;
        public Summary summary;
        public List<TransportLayerTransport> transportLayers;
        public List<RunRecord> runs;
    }

    public static class Summary {
        public int totalRuns;
        public int successfulRuns;
        public int failedRuns;
        public long elapsedMillis;
    }

    public static class RunRecord {
        public String name;
        public String category;
        public Boolean assertionPassed;
        public String assertionMessage;
        public boolean reached;
        public String terminationReason;
        public WorldPointJson start;
        public WorldPointJson target;
        public WorldPointJson closestReachedPoint;
        public List<WorldPointJson> path;
        public Stats stats;
        public List<TransportStep> transports;
        public List<Marker> markers;
        public List<String> details;
    }

    public static class Stats {
        public int nodesChecked;
        public int transportsChecked;
        public long elapsedNanos;
    }

    public static class TransportStep {
        public int stepIndex;
        public String type;
        public String displayInfo;
        public String objectInfo;
        public WorldPointJson origin;
        public WorldPointJson destination;
    }

    public static class Marker {
        public String kind;
        public String label;
        public WorldPointJson point;
    }

    public static class TransportLayerTransport {
        public String type;
        public String validity;
        public String displayInfo;
        public String objectInfo;
        public WorldPointJson origin;
        public WorldPointJson destination;
    }

    public static class WorldPointJson {
        public int x;
        public int y;
        public int plane;
        public boolean bankVisited;
    }
}
