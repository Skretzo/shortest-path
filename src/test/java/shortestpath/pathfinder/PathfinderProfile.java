package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects detailed profiling data during a pathfinding run.
 * This class lives in test only — it is never included in the production build.
 */
public class PathfinderProfile {
    static final int SAMPLE_INTERVAL = 2000;

    // ── Top-level phase timing (accumulated nanos) ──────────────────────
    long addNeighborsNanos;
    long queueSelectionNanos;
    long targetCheckNanos;
    long wildernessCheckNanos;
    long cutoffCheckNanos;
    long bookkeepingNanos;

    // ── Sub-phase timing within addNeighbors / CollisionMap ─────────────
    long bankCheckNanos;
    long transportLookupNanos;
    long collisionCheckNanos;
    long walkableTileNanos;
    long blockedTileTransportNanos;
    long abstractNodeNanos;

    // ── Counters ────────────────────────────────────────────────────────
    int tileNeighborsAdded;
    int transportNeighborsAdded;
    int visitedSkipped;
    int abstractNodesExpanded;
    int transportEvaluations;
    int blockedTileTransportChecks;
    int bankTransitions;
    int wildernessLevelChanges;
    int delayedVisitEnqueued;
    int delayedVisitSkipped;
    int peakBoundarySize;
    int peakPendingSize;

    // ── Time series ─────────────────────────────────────────────────────
    private final List<ProfileSample> samples = new ArrayList<>(400);

    // ── Tile visit heatmap (sparse: packedPosition -> visit count) ────
    private final Map<Integer, int[]> tileVisitCounts = new HashMap<>();

    public PathfinderProfile() {
    }

    // ── Sampling ────────────────────────────────────────────────────────

    boolean shouldSample(int iteration) {
        return iteration % SAMPLE_INTERVAL == 0;
    }

    void recordSample(int iteration, int boundarySize, int pendingSize, int currentCost, long elapsedNanos) {
        samples.add(new ProfileSample(iteration, boundarySize, pendingSize, currentCost, elapsedNanos));
    }

    void updatePeakBoundarySize(int size) {
        if (size > peakBoundarySize) {
            peakBoundarySize = size;
        }
    }

    void updatePeakPendingSize(int size) {
        if (size > peakPendingSize) {
            peakPendingSize = size;
        }
    }

    void incrementTileVisit(int packedPosition) {
        tileVisitCounts.computeIfAbsent(packedPosition, k -> new int[1])[0]++;
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public long getAddNeighborsNanos() { return addNeighborsNanos; }
    public long getQueueSelectionNanos() { return queueSelectionNanos; }
    public long getTargetCheckNanos() { return targetCheckNanos; }
    public long getWildernessCheckNanos() { return wildernessCheckNanos; }
    public long getCutoffCheckNanos() { return cutoffCheckNanos; }
    public long getBookkeepingNanos() { return bookkeepingNanos; }
    public long getBankCheckNanos() { return bankCheckNanos; }
    public long getTransportLookupNanos() { return transportLookupNanos; }
    public long getCollisionCheckNanos() { return collisionCheckNanos; }
    public long getWalkableTileNanos() { return walkableTileNanos; }
    public long getBlockedTileTransportNanos() { return blockedTileTransportNanos; }
    public long getAbstractNodeNanos() { return abstractNodeNanos; }
    public int getTileNeighborsAdded() { return tileNeighborsAdded; }
    public int getTransportNeighborsAdded() { return transportNeighborsAdded; }
    public int getVisitedSkipped() { return visitedSkipped; }
    public int getAbstractNodesExpanded() { return abstractNodesExpanded; }
    public int getTransportEvaluations() { return transportEvaluations; }
    public int getBlockedTileTransportChecks() { return blockedTileTransportChecks; }
    public int getBankTransitions() { return bankTransitions; }
    public int getWildernessLevelChanges() { return wildernessLevelChanges; }
    public int getDelayedVisitEnqueued() { return delayedVisitEnqueued; }
    public int getDelayedVisitSkipped() { return delayedVisitSkipped; }
    public int getPeakBoundarySize() { return peakBoundarySize; }
    public int getPeakPendingSize() { return peakPendingSize; }
    public List<ProfileSample> getSamples() { return samples; }
    public Map<Integer, int[]> getTileVisitCounts() { return tileVisitCounts; }

    public static class ProfileSample {
        private final int iteration;
        private final int boundarySize;
        private final int pendingSize;
        private final int currentCost;
        private final long elapsedNanos;

        public ProfileSample(int iteration, int boundarySize, int pendingSize, int currentCost, long elapsedNanos) {
            this.iteration = iteration;
            this.boundarySize = boundarySize;
            this.pendingSize = pendingSize;
            this.currentCost = currentCost;
            this.elapsedNanos = elapsedNanos;
        }

        public int getIteration() { return iteration; }
        public int getBoundarySize() { return boundarySize; }
        public int getPendingSize() { return pendingSize; }
        public int getCurrentCost() { return currentCost; }
        public long getElapsedNanos() { return elapsedNanos; }
    }
}
