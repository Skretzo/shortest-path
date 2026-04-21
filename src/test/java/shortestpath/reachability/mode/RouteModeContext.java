package shortestpath.reachability.mode;

import static org.mockito.Mockito.when;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import shortestpath.TestShortestPathConfig;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.reachability.scenario.ReachabilityScenario;

/**
 * Holds mutable pathfinder state and helpers used while applying a {@link RouteMode}.
 */
public final class RouteModeContext {
    private final Client client;
    private final TestShortestPathConfig config;
    private final ReachabilityScenario scenario;
    private final ItemContainer universalBankContainer;
    private PathfinderConfig pathfinderConfig;

    public RouteModeContext(
        Client client,
        TestShortestPathConfig config,
        ReachabilityScenario scenario,
        ItemContainer universalBankContainer) {
        this.client = client;
        this.config = config;
        this.scenario = scenario;
        this.universalBankContainer = universalBankContainer;
    }

    public TestShortestPathConfig config() {
        return config;
    }

    public Client client() {
        return client;
    }

    public ReachabilityScenario scenario() {
        return scenario;
    }

    public ItemContainer universalBankContainer() {
        return universalBankContainer;
    }

    public void stubVarbit(int varbitId, int value) {
        when(client.getVarbitValue(varbitId)).thenReturn(value);
    }

    public void rebuildPathfinder() {
        pathfinderConfig = new TestPathfinderConfig(client, config);
        scenario.configurePathfinder(pathfinderConfig);
    }

    public void setPathfinderBank(ItemContainer bank) {
        pathfinderConfig.bank = bank;
    }

    public PathfinderConfig pathfinder() {
        return pathfinderConfig;
    }
}
