package shortestpath.reachability.scenario;

import net.runelite.api.Client;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.reachability.mode.RouteMode;
import shortestpath.reachability.mode.RouteModes;

public final class DefaultReachabilityScenario implements ReachabilityScenario {
    private static final RouteModes ROUTE_MODES = RouteModes.defaults();

    @Override
    public String id() {
        return "default";
    }

    @Override
    public int startPoint() {
        return WorldPointUtil.packWorldPoint(3185, 3436, 0);
    }

    @Override
    public RouteMode defaultMode() {
        return ROUTE_MODES.get("ALL");
    }

    @Override
    public String reportTitle() {
        return "Clue Steps Default";
    }

    @Override
    public String reportSubtitle() {
        return "Clue steps sweep from bank start";
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
