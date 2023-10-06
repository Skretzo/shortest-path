package pathfinder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.ShortestPathPlugin;
import shortestpath.Transport;
import shortestpath.Util;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.SplitFlagMap;

@RunWith(MockitoJUnitRunner.class)
public class PathfinderTest {
    private static final SplitFlagMap map = SplitFlagMap.fromResources();
    private static final Map<WorldPoint, List<Transport>> transports = Transport.loadAllFromResources();
    private static PathfinderConfig pathfinderConfig;

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
        when(config.useGrappleShortcuts()).thenReturn(true);
        testPathLength(2, "agility_shortcuts.tsv", QuestState.FINISHED, 99);
    }

    @Test
    public void testBoats() {
        when(config.useBoats()).thenReturn(true);
        testPathLength(2, "boats.tsv", QuestState.FINISHED);
    }

    @Test
    public void testCanoes() {
        when(config.useCanoes()).thenReturn(true);
        testPathLength(2, "canoes.tsv", QuestState.NOT_STARTED, 99);
    }

    @Test
    public void testCharterShips() {
        when(config.useCharterShips()).thenReturn(true);
        testPathLength(2, "charter_ships.tsv", QuestState.FINISHED);
    }

    @Test
    public void testShips() {
        when(config.useShips()).thenReturn(true);
        testPathLength(2, "ships.tsv", QuestState.FINISHED);
    }

    @Test
    public void testGnomeGliders() {
        when(config.useGnomeGliders()).thenReturn(true);
        testPathLength(2, "gnome_gliders.tsv", QuestState.FINISHED);
    }

    @Test
    public void testSpiritTrees() {
        when(config.useSpiritTrees()).thenReturn(true);
        testPathLength(2, "spirit_trees.tsv", QuestState.FINISHED);
    }

    private void testPathLength(int expectedLength, String path, QuestState questState) {
        testPathLength(expectedLength, path, questState, -1);
    }

    private void testPathLength(int expectedLength, String path, QuestState questState, int skillLevel) {
        if (skillLevel > -1) {
            setupSkills(skillLevel);
        }
        setupQuests(questState);

        int counter = 0;
        for (int[] startAndTargetCoords : transportCoordinatesFromFile("/" + path)) {
            counter++;
            assertEquals(expectedLength, calculatePathLength(startAndTargetCoords));
        }
        System.out.println(String.format("Completed %d " + path + " path length tests successfully", counter));
    }

    private void setupSkills(int skillLevel) {
        when(client.getBoostedSkillLevel((Skill) any(Object.class))).thenReturn(skillLevel);
    }

    private void setupQuests(QuestState questState) {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        pathfinderConfig = spy(new PathfinderConfig(map, transports, client, config));
        doReturn(questState).when(pathfinderConfig).getQuestState((Quest) any(Object.class));
    }

    private int[][] transportCoordinatesFromFile(String path) {
        List<int[]> populateCoords = new ArrayList<>();
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t");
                final String DELIM = " ";
                String[] parts_origin = parts[0].split(DELIM);
                String[] parts_destination = parts[1].split(DELIM);
                populateCoords.add(new int[] {
                    Integer.parseInt(parts_origin[0]),
                    Integer.parseInt(parts_origin[1]),
                    Integer.parseInt(parts_origin[2]),
                    Integer.parseInt(parts_destination[0]),
                    Integer.parseInt(parts_destination[1]),
                    Integer.parseInt(parts_destination[2])});
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return populateCoords.toArray(new int[0][0]);
    }

    private int calculatePathLength(int[] coords) {
        return calculatePathLength(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
    }

    private int calculatePathLength(int startX, int startY, int startZ, int endX, int endY, int endZ) {
        pathfinderConfig.refresh();
        Pathfinder pathfinder = new Pathfinder(
            pathfinderConfig,
            new WorldPoint(startX, startY, startZ),
            new WorldPoint(endX, endY, endZ));

        pathfinder.run();
        return pathfinder.getPath().size();
    }
}
