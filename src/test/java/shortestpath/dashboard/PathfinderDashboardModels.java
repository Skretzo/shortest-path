package shortestpath.dashboard;

import java.util.List;

public final class PathfinderDashboardModels {
    private PathfinderDashboardModels() {
    }

    public static class Report {
        public String generatedAt;
        public String title;
        public String subtitle;
        /** Reachability / scenario dashboards: scenario id (e.g. {@code default}). */
        public String scenarioId;
        /** Default start world point for the scenario when not overridden per target. */
        public WorldPointJson scenarioDefaultStart;
        /**
         * Distinct bank location names from {@code /destinations/game_features/bank.tsv} (Info column), when the report
         * generator populates it (e.g. reachability dashboard).
         */
        public List<String> bankNamesFromData;
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

        /** Reachability: route mode id from the route CSV / scenario (e.g. {@code ALL}, {@code BANK}). */
        public String routeModeId;
        /** Teleportation item policy name (matches {@code TeleportationItem} enum). */
        public String teleportationItems;
        /** Whether the pathfinder was configured to allow routing via a bank first. */
        public Boolean includeBankPath;
        /** Stub value for elite Lumbridge diary varbit in tests (affects fairy-ring staff rules). */
        public Integer lumbridgeDiaryEliteStub;
        /** Whether minigame teleports were enabled in this run (false for BANK mode). */
        public Boolean useTeleportationMinigames;

        /** True if any path step has {@code bankVisited} (post-bank inventory state). */
        public Boolean bankVisitedOnPath;
        /** Each transition from unbanked to banked state along the path (bank tile + label when known). */
        public List<BankEvent> bankEvents;

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
        /**
         * Human-readable item requirements for this transport (e.g. "Dramen staff",
         * "3× Air rune or Dust rune"). Empty when the transport has no item requirements.
         * Fairy rings synthesize a "Dramen staff" entry since the rule lives in the pathfinder
         * and not in the TSV data.
         */
        public List<String> itemRequirements;
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

    /** A point where the path transitions into the "bank visited" inventory state. */
    public static class BankEvent {
        /** Index of the path step at the bank tile (before leaving with banked items). */
        public int stepIndex;
        /** Bank tile world position. */
        public WorldPointJson location;
        /** Name from {@code bank.tsv} when the tile matches, else null. */
        public String bankName;
        /**
         * Distinct transport labels used on the path after this bank event (in order, deduped).
         * Each transport in this segment required items that came from the bank, since the test
         * inventory is empty. Useful for answering "what did this run pick up from the bank?".
         * Null if no transport follows the bank event on the path.
         */
        public List<String> transportsAfterBank;
        /**
         * Distinct human-readable items picked up from this bank event (in order, deduped).
         * Aggregated from the {@code itemRequirements} of every transport in this segment.
         * Null when the segment has no transports or no item requirements.
         */
        public List<String> pickups;
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
