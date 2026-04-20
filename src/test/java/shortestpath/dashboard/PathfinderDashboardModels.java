package shortestpath.dashboard;

import java.util.List;

public final class PathfinderDashboardModels {
    private PathfinderDashboardModels() {
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

        // Optional profiler fields (null when not profiled)
        public PhaseBreakdown phases;
        public SubPhaseBreakdown subPhases;
        public ProfilerCounters counters;
        public List<TimeSeriesSample> timeSeries;
        public TileHeatmap tileHeatmap;
        /** Relative path to a separate heatmap JSON file (set when heatmap is externalised) */
        public String heatmapFile;
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

    // ── Profiler models (optional per-run data) ─────────────────────

    public static class PhaseBreakdown {
        public long addNeighborsNanos;
        public long queueSelectionNanos;
        public long targetCheckNanos;
        public long wildernessCheckNanos;
        public long cutoffCheckNanos;
        public long bookkeepingNanos;
        public long otherNanos;
    }

    public static class SubPhaseBreakdown {
        public long bankCheckNanos;
        public long transportLookupNanos;
        public long collisionCheckNanos;
        public long walkableTileNanos;
        public long blockedTileTransportNanos;
        public long abstractNodeNanos;
    }

    public static class ProfilerCounters {
        public int tileNeighborsAdded;
        public int transportNeighborsAdded;
        public int visitedSkipped;
        public int abstractNodesExpanded;
        public int transportEvaluations;
        public int blockedTileTransportChecks;
        public int bankTransitions;
        public int wildernessLevelChanges;
        public int delayedVisitEnqueued;
        public int delayedVisitSkipped;
        public int peakBoundarySize;
        public int peakPendingSize;
    }

    public static class TimeSeriesSample {
        public int iteration;
        public int boundarySize;
        public int pendingSize;
        public int currentCost;
        public double elapsedMs;
    }

    public static class TileHeatmap {
        public List<TileVisit> tiles;
    }

    public static class TileVisit {
        public int x;
        public int y;
        public int count;
    }

    // ── Bundle index (bundles/index.json) ───────────────────────────

    public static class BundleIndex {
        public List<BundleEntry> bundles;
    }

    public static class BundleEntry {
        public String name;
        public String title;
        public String generatedAt;
        public String reportPath;
    }
}
