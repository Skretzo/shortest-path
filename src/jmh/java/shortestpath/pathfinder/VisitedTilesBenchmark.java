package shortestpath.pathfinder;

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
 * JMH benchmarks for VisitedTiles set/get performance.
 *
 * <p>Run with: {@code ./gradlew jmh -Pjmh.includes='VisitedTilesBenchmark'}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class VisitedTilesBenchmark {

    private CollisionMap map;
    private VisitedTiles visited;
    private int counter;

    // Pre-computed valid tile coordinates for set/get
    private static final int BASE_X = 3136; // Region 50
    private static final int BASE_Y = 3136;

    @Setup(Level.Invocation)
    public void setUp() {
        Client client = Mockito.mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);

        TestShortestPathConfig config = new TestShortestPathConfig();
        config.setCalculationCutoffValue(500);
        config.setUseTeleportationItemsValue(TeleportationItem.NONE);
        config.setIncludeBankPathValue(false);

        PathfinderConfig pfConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED, true, true);
        pfConfig.refresh();

        map = pfConfig.getMap();
        visited = new VisitedTiles(map);
        counter = 0;
    }

    @Benchmark
    public void setAndGet(Blackhole bh) {
        int x = BASE_X + (counter % 64);
        int y = BASE_Y + ((counter / 64) % 64);
        counter++;
        visited.set(x, y, 0, false);
        bh.consume(visited.get(x, y, 0, false));
    }

    @Benchmark
    public void getMiss(Blackhole bh) {
        int x = BASE_X + (counter % 64);
        int y = BASE_Y + ((counter / 64) % 64);
        counter++;
        bh.consume(visited.get(x, y, 0, false));
    }
}
