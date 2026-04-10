package shortestpath.dashboard;

import java.util.List;

/**
 * Serializable model types for the static pathfinder dashboard JSON payloads.
 */
public final class PathfinderDashboardModels {
    private PathfinderDashboardModels() {
    }

    /**
     * Top-level bundle registry consumed by the shared dashboard frontend.
     */
    public static class BundleIndex {
        /** All published dashboard bundles available for selection. */
        public List<BundleManifest> bundles;
    }

    /**
     * Metadata for one published dashboard bundle.
     */
    public static class BundleManifest {
        /** Stable bundle identifier used as the directory name under `bundles/`. */
        public String id;
        /** Human-readable bundle title shown in the dashboard UI. */
        public String title;
        /** Short secondary description shown alongside the bundle title. */
        public String subtitle;
        /** Relative path from the site root to the bundle's `report.json`. */
        public String reportPath;
        /** ISO-8601 timestamp for when this bundle was last generated. */
        public String generatedAt;
    }

    /**
     * Full report payload for one dashboard bundle.
     */
    public static class Report {
        /** ISO-8601 timestamp for when this report was generated. */
        public String generatedAt;
        /** Report title displayed at the top of the dashboard view. */
        public String title;
        /** Report subtitle displayed under the main title. */
        public String subtitle;
        /** Aggregate counts and timing information for the report. */
        public Summary summary;
        /** Optional transport overlay data rendered across the whole map. */
        public List<TransportLayerTransport> transportLayers;
        /** Per-run records shown in the run list and detail views. */
        public List<RunRecord> runs;
    }

    /**
     * Aggregate summary for all runs within a report.
     */
    public static class Summary {
        /** Total number of recorded runs in the report. */
        public int totalRuns;
        /** Number of runs that reached their target. */
        public int successfulRuns;
        /** Number of runs that did not reach their target. */
        public int failedRuns;
        /** Wall-clock time spent generating the report, in milliseconds. */
        public long elapsedMillis;
    }

    /**
     * Serialized record for one pathfinder execution.
     */
    public static class RunRecord {
        /** Human-readable scenario or dataset entry name. */
        public String name;
        /** Run grouping label such as `scenario` or `reachability`. */
        public String category;
        /** Assertion result when the source test had an explicit pass/fail assertion. */
        public Boolean assertionPassed;
        /** Assertion failure message when `assertionPassed` is false. */
        public String assertionMessage;
        /** Whether the run reached its intended destination. */
        public boolean reached;
        /** Pathfinder termination reason enum name. */
        public String terminationReason;
        /** Start point for the run. */
        public WorldPointJson start;
        /** Intended target point for the run. */
        public WorldPointJson target;
        /** Closest point reached when the target was not reached exactly. */
        public WorldPointJson closestReachedPoint;
        /** Ordered list of path tiles returned by the pathfinder. */
        public List<WorldPointJson> path;
        /** Pathfinder performance counters for this run. */
        public Stats stats;
        /** Transport steps inferred from transitions along the path. */
        public List<TransportStep> transports;
        /** Important map markers such as start, target, and bank transition. */
        public List<Marker> markers;
        /** Free-form detail strings displayed in the run detail panel. */
        public List<String> details;
    }

    /**
     * Performance counters captured for a single run.
     */
    public static class Stats {
        /** Number of nodes explored during pathfinding. */
        public int nodesChecked;
        /** Number of transports considered during pathfinding. */
        public int transportsChecked;
        /** Pathfinder execution time in nanoseconds. */
        public long elapsedNanos;
    }

    /**
     * One inferred transport edge used within a path.
     */
    public static class TransportStep {
        /** Path index where this transport step lands. */
        public int stepIndex;
        /** Transport type enum name, or `TRANSPORT` when unspecified. */
        public String type;
        /** User-facing transport description, when available. */
        public String displayInfo;
        /** Backing object or interaction description, when available. */
        public String objectInfo;
        /** Transport origin point. */
        public WorldPointJson origin;
        /** Transport destination point. */
        public WorldPointJson destination;
    }

    /**
     * A labeled point rendered as a marker on the dashboard map.
     */
    public static class Marker {
        /** Marker kind identifier used for styling and filtering. */
        public String kind;
        /** Human-readable marker label shown in the UI. */
        public String label;
        /** Marker location on the world map. */
        public WorldPointJson point;
    }

    /**
     * One transport entry for the report-wide transport overlay layer.
     */
    public static class TransportLayerTransport {
        /** Transport type enum name, or `TRANSPORT` when unspecified. */
        public String type;
        /** Availability classification such as `INVENTORY_VALID`, `BANK_VALID`, or `INVALID`. */
        public String validity;
        /** User-facing transport description, when available. */
        public String displayInfo;
        /** Backing object or interaction description, when available. */
        public String objectInfo;
        /** Transport origin point, or null for teleports without a concrete origin tile. */
        public WorldPointJson origin;
        /** Transport destination point. */
        public WorldPointJson destination;
    }

    /**
     * Serialized world point used across all dashboard payloads.
     */
    public static class WorldPointJson {
        /** World X coordinate. */
        public int x;
        /** World Y coordinate. */
        public int y;
        /** Plane level. */
        public int plane;
        /** Whether the path had already visited a bank at this point. */
        public boolean bankVisited;
    }
}
