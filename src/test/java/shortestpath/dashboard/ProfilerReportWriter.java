package shortestpath.dashboard;

import java.util.ArrayList;
import java.util.Map;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderProfile;

public class ProfilerReportWriter extends PathfinderDashboardReportWriter {

    public void populateProfilerData(PathfinderDashboardModels.RunRecord run, PathfinderProfile profile) {
        if (profile == null) {
            return;
        }

        // Phase breakdown
        run.phases = new PathfinderDashboardModels.PhaseBreakdown();
        run.phases.addNeighborsNanos = profile.getAddNeighborsNanos();
        run.phases.queueSelectionNanos = profile.getQueueSelectionNanos();
        run.phases.targetCheckNanos = profile.getTargetCheckNanos();
        run.phases.wildernessCheckNanos = profile.getWildernessCheckNanos();
        run.phases.cutoffCheckNanos = profile.getCutoffCheckNanos();
        run.phases.bookkeepingNanos = profile.getBookkeepingNanos();
        long accountedPhaseNanos = profile.getAddNeighborsNanos()
            + profile.getQueueSelectionNanos()
            + profile.getTargetCheckNanos()
            + profile.getWildernessCheckNanos()
            + profile.getCutoffCheckNanos()
            + profile.getBookkeepingNanos();
        run.phases.otherNanos = Math.max(0, run.stats.elapsedNanos - accountedPhaseNanos);

        // Sub-phase breakdown
        run.subPhases = new PathfinderDashboardModels.SubPhaseBreakdown();
        run.subPhases.bankCheckNanos = profile.getBankCheckNanos();
        run.subPhases.transportLookupNanos = profile.getTransportLookupNanos();
        run.subPhases.collisionCheckNanos = profile.getCollisionCheckNanos();
        run.subPhases.walkableTileNanos = profile.getWalkableTileNanos();
        run.subPhases.blockedTileTransportNanos = profile.getBlockedTileTransportNanos();
        run.subPhases.abstractNodeNanos = profile.getAbstractNodeNanos();

        // Counters
        run.counters = new PathfinderDashboardModels.ProfilerCounters();
        run.counters.tileNeighborsAdded = profile.getTileNeighborsAdded();
        run.counters.transportNeighborsAdded = profile.getTransportNeighborsAdded();
        run.counters.visitedSkipped = profile.getVisitedSkipped();
        run.counters.abstractNodesExpanded = profile.getAbstractNodesExpanded();
        run.counters.transportEvaluations = profile.getTransportEvaluations();
        run.counters.blockedTileTransportChecks = profile.getBlockedTileTransportChecks();
        run.counters.bankTransitions = profile.getBankTransitions();
        run.counters.wildernessLevelChanges = profile.getWildernessLevelChanges();
        run.counters.delayedVisitEnqueued = profile.getDelayedVisitEnqueued();
        run.counters.delayedVisitSkipped = profile.getDelayedVisitSkipped();
        run.counters.peakBoundarySize = profile.getPeakBoundarySize();
        run.counters.peakPendingSize = profile.getPeakPendingSize();

        // Time series
        run.timeSeries = new ArrayList<>();
        for (PathfinderProfile.ProfileSample sample : profile.getSamples()) {
            PathfinderDashboardModels.TimeSeriesSample s = new PathfinderDashboardModels.TimeSeriesSample();
            s.iteration = sample.getIteration();
            s.boundarySize = sample.getBoundarySize();
            s.pendingSize = sample.getPendingSize();
            s.currentCost = sample.getCurrentCost();
            s.elapsedMs = sample.getElapsedNanos() / 1_000_000.0;
            run.timeSeries.add(s);
        }

        // Tile heatmap
        run.tileHeatmap = new PathfinderDashboardModels.TileHeatmap();
        run.tileHeatmap.tiles = new ArrayList<>();
        for (Map.Entry<Integer, int[]> entry : profile.getTileVisitCounts().entrySet()) {
            PathfinderDashboardModels.TileVisit tv = new PathfinderDashboardModels.TileVisit();
            tv.x = WorldPointUtil.unpackWorldX(entry.getKey());
            tv.y = WorldPointUtil.unpackWorldY(entry.getKey());
            tv.count = entry.getValue()[0];
            run.tileHeatmap.tiles.add(tv);
        }
    }
}
