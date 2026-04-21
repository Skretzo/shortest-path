package shortestpath.reachability;

import shortestpath.TestShortestPathConfig;
import shortestpath.dashboard.PathfinderDashboardModels;
import shortestpath.reachability.mode.RouteMode;

/**
 * Fills reachability-specific fields on dashboard {@link PathfinderDashboardModels.RunRecord}s.
 */
public final class ReachabilityRunMetadata {
    private ReachabilityRunMetadata() {
    }

    public static void apply(
        PathfinderDashboardModels.RunRecord run,
        RouteMode mode,
        TestShortestPathConfig config) {
        run.routeModeId = mode.id();
        run.teleportationItems = config.useTeleportationItems().name();
        run.includeBankPath = config.includeBankPath();
        run.lumbridgeDiaryEliteStub = mode.lumbridgeDiaryEliteStub();
    }
}
