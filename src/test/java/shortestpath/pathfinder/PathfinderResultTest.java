package shortestpath.pathfinder;

import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import org.junit.Test;
import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathfinderResultTest {
    @Test
    public void reachedTargetProducesReachedResult() {
        Pathfinder pathfinder = new Pathfinder(configWithCutoff(100), point(3200, 3200), Set.of(point(3201, 3200)));

        pathfinder.run();
        PathfinderResult result = pathfinder.getResult();

        assertTrue(result.isReached());
        assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
    }

    @Test
    public void zeroCutoffProducesCutoffResult() {
        Pathfinder pathfinder = new Pathfinder(configWithCutoff(0), point(3200, 3200), Set.of(point(3300, 3300)));

        pathfinder.run();
        PathfinderResult result = pathfinder.getResult();

        assertEquals(PathTerminationReason.CUTOFF_REACHED, result.getTerminationReason());
    }

    private static PathfinderConfig configWithCutoff(int cutoffTicks) {
        Client client = mock(Client.class);
        TestShortestPathConfig config = new TestShortestPathConfig();
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
        when(client.getTotalLevel()).thenReturn(2277);
        config.setCalculationCutoffValue(cutoffTicks);
        config.setUseTeleportationItemsValue(TeleportationItem.ALL);

        PathfinderConfig pathfinderConfig = new TestPathfinderConfig(client, config);
        pathfinderConfig.refresh();
        return pathfinderConfig;
    }

    private static int point(int x, int y) {
        return WorldPointUtil.packWorldPoint(x, y, 0);
    }
}
