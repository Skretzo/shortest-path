package shortestpath.reachability.scenario;

import net.runelite.api.Client;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.reachability.mode.RouteMode;

/**
 * Scenario-specific defaults (start point, report labels, default route mode) and optional hooks.
 */
public interface ReachabilityScenario {
    String id();

    int startPoint();

    RouteMode defaultMode();

    String reportTitle();

    String reportSubtitle();

    void configureClient(Client client);

    void configurePathfinder(PathfinderConfig pathfinderConfig);
}
