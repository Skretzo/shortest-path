package pathfinder;

import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.TeleportationItem;
import shortestpath.ShortestPathConfig;
import shortestpath.Transport;
import shortestpath.TransportType;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.SplitFlagMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PathfinderTest {
    private static final SplitFlagMap map = SplitFlagMap.fromResources();
    private static final Map<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();

    private PathfinderConfig pathfinderConfig;

    @Mock
    Client client;

    @Mock
    ShortestPathConfig config;

    @Before
    public void before() {
        when(config.calculationCutoff()).thenReturn(30);
    }

    @Test
    public void testAgilityShortcuts() {
        when(config.useAgilityShortcuts()).thenReturn(true);
        testTransportLength(2, TransportType.AGILITY_SHORTCUT);
    }

    @Test
    public void testGrappleShortcuts() {
        when(config.useGrappleShortcuts()).thenReturn(true);
        testTransportLength(2, TransportType.GRAPPLE_SHORTCUT);
    }

    @Test
    public void testBoats() {
        when(config.useBoats()).thenReturn(true);
        testTransportLength(2, TransportType.BOAT);
    }

    @Test
    public void testCanoes() {
        when(config.useCanoes()).thenReturn(true);
        testTransportLength(2, TransportType.CANOE);
    }

    @Test
    public void testCharterShips() {
        when(config.useCharterShips()).thenReturn(true);
        testTransportLength(2, TransportType.CHARTER_SHIP);
    }

    @Test
    public void testShips() {
        when(config.useShips()).thenReturn(true);
        testTransportLength(2, TransportType.SHIP);
    }

    @Test
    public void testGnomeGliders() {
        when(config.useGnomeGliders()).thenReturn(true);
        testTransportLength(2, TransportType.GNOME_GLIDER);
    }

    @Test
    public void testSpiritTrees() {
        when(config.useSpiritTrees()).thenReturn(true);
        testTransportLength(2, TransportType.SPIRIT_TREE);
    }

    @Test
    public void testTeleportationLevers() {
        when(config.useTeleportationLevers()).thenReturn(true);
        testTransportLength(2, TransportType.TELEPORTATION_LEVER);
    }

    @Test
    public void testAgilityShortcutAndTeleportItem() {
        when(config.useAgilityShortcuts()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.ALL);
        // Draynor Manor to Champions Guild via several stepping stones, but
        // enabling Combat bracelet teleport should not priotize over stepping stones
        // 5 tiles is using the stepping stones
        // ~40 tiles is using the combat bracelet teleport to Champions Guild
        // >100 tiles is walking around the river via Barbarian Village
        testTransportLength(5, new WorldPoint(3149, 3363, 0), new WorldPoint(3154, 3363, 0));
    }

    @Test
    public void testChronicle() {
        // South of river south of Champions Guild to Chronicle teleport destination
        testTransportLength(2,
            new WorldPoint(3199, 3336, 0),
            new WorldPoint(3200, 3355, 0),
            TeleportationItem.ALL);
    }

    private void setupConfig(QuestState questState, int skillLevel, TeleportationItem useTeleportationItems) {
        pathfinderConfig = spy(new PathfinderConfig(map, transports, client, config));

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(skillLevel);
        when(config.useTeleportationItems()).thenReturn(useTeleportationItems);
        doReturn(questState).when(pathfinderConfig).getQuestState(any(Quest.class));

        pathfinderConfig.refresh();
    }

    private void testTransportLength(int expectedLength, WorldPoint origin, WorldPoint destination) {
        testTransportLength(expectedLength, origin, destination, TeleportationItem.NONE);
    }

    private void testTransportLength(int expectedLength, WorldPoint origin, WorldPoint destination,
        TeleportationItem useTeleportationItems) {
        setupConfig(QuestState.FINISHED, 99, useTeleportationItems);
        assertEquals(expectedLength, calculatePathLength(origin, destination));
        System.out.println("Successfully completed transport length test from " +
            "(" + origin.getX() + ", " + origin.getY() + ", " + origin.getPlane() + ") to " +
            "(" + destination.getX() + ", " + destination.getY() + ", " + destination.getPlane() + ")");
    }

    private void testTransportLength(int expectedLength, TransportType transportType) {
        testTransportLength(expectedLength, transportType, QuestState.FINISHED, 99, TeleportationItem.NONE);
    }

    private void testTransportLength(int expectedLength, TransportType transportType, QuestState questState, int skillLevel,
        TeleportationItem useTeleportationItems) {
        setupConfig(questState, skillLevel, useTeleportationItems);

        int counter = 0;
        for (WorldPoint origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (transportType.equals(transport.getType())) {
                    counter++;
                    assertEquals(expectedLength, calculateTransportLength(transport));
                }
            }
        }

        assertTrue("No tests were performed", counter > 0);
        System.out.println(String.format("Completed %d " + transportType + " transport length tests successfully", counter));
    }

    private int calculateTransportLength(Transport transport) {
        return calculatePathLength(transport.getOrigin(), transport.getDestination());
    }

    private int calculatePathLength(WorldPoint origin, WorldPoint destination) {
        Pathfinder pathfinder = new Pathfinder(pathfinderConfig, origin, destination);
        pathfinder.run();
        return pathfinder.getPath().size();
    }
}
