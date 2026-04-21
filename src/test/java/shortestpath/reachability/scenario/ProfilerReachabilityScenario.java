package shortestpath.reachability.scenario;

import net.runelite.api.Client;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.reachability.mode.RouteMode;
import shortestpath.reachability.mode.RouteModes;

public final class ProfilerReachabilityScenario implements ReachabilityScenario {
    private static final RouteModes ROUTE_MODES = RouteModes.defaults();

    @Override
    public String id() {
        return "profiler";
    }

    @Override
    public int startPoint() {
        return WorldPointUtil.packWorldPoint(3222, 3218, 0);
    }

    @Override
    public RouteMode defaultMode() {
        return ROUTE_MODES.get("ALL");
    }

    @Override
    public String reportTitle() {
        return "Profiler";
    }

    @Override
    public String reportSubtitle() {
        return "Performance profiling scenarios";
    }

    @Override
    public void configureClient(Client client) {
        // No scenario-specific client setup yet.
    }

    @Override
    public void configurePathfinder(PathfinderConfig pathfinderConfig) {
        // No scenario-specific pathfinder setup yet.
    }
}
