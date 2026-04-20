package shortestpath.pathfinder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * JMH benchmarks isolating CollisionMap.getNeighbors() performance.
 *
 * <p>Run with: {@code ./gradlew jmh -Pjmh.includes='CollisionMapBenchmark'}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class CollisionMapBenchmark {

    private CollisionMap map;
    private VisitedTiles visited;
    private PathfinderConfig config;
    private Node openFieldNode;
    private Node wallAdjacentNode;

    @Setup(Level.Trial)
    public void setUp() {
        Client client = Mockito.mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);

        TestShortestPathConfig testConfig = new TestShortestPathConfig();
        testConfig.setCalculationCutoffValue(500);
        testConfig.setUseTeleportationItemsValue(TeleportationItem.ALL);
        testConfig.setIncludeBankPathValue(false);

        config = new TestPathfinderConfig(client, testConfig, QuestState.FINISHED, true, true);
        config.refresh();

        map = config.getMap();
        visited = new VisitedTiles(map);

        // Open field in Lumbridge
        int openPacked = WorldPointUtil.packWorldPoint(3222, 3218, 0);
        openFieldNode = new Node(openPacked, null, 0, false);

        // Near Varrock castle wall
        int wallPacked = WorldPointUtil.packWorldPoint(3213, 3428, 0);
        wallAdjacentNode = new Node(wallPacked, null, 0, false);
    }

    @Benchmark
    public void neighborsOpenField(Blackhole bh) {
        List<Node> neighbors = map.getNeighbors(openFieldNode, visited, config, 0, false);
        bh.consume(neighbors);
    }

    @Benchmark
    public void neighborsWallAdjacent(Blackhole bh) {
        List<Node> neighbors = map.getNeighbors(wallAdjacentNode, visited, config, 0, false);
        bh.consume(neighbors);
    }
}
