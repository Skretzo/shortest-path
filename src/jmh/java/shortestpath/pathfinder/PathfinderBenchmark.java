package shortestpath.pathfinder;

import java.util.Set;
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
 * JMH benchmarks for end-to-end pathfinding.
 *
 * <p>Run with: {@code ./gradlew jmh}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PathfinderBenchmark {

    private PathfinderConfig configWithTeleports;
    private PathfinderConfig configWalkOnly;

    @Setup(Level.Trial)
    public void setUp() {
        Client client = Mockito.mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);

        // Config with teleports
        TestShortestPathConfig configAll = new TestShortestPathConfig();
        configAll.setCalculationCutoffValue(500);
        configAll.setUseTeleportationItemsValue(TeleportationItem.ALL);
        configAll.setIncludeBankPathValue(false);
        configWithTeleports = new TestPathfinderConfig(client, configAll, QuestState.FINISHED, true, true);
        configWithTeleports.refresh();

        // Config without teleports
        TestShortestPathConfig configNone = new TestShortestPathConfig();
        configNone.setCalculationCutoffValue(500);
        configNone.setUseTeleportationItemsValue(TeleportationItem.NONE);
        configNone.setIncludeBankPathValue(false);
        configWalkOnly = new TestPathfinderConfig(client, configNone, QuestState.FINISHED, true, true);
        configWalkOnly.refresh();
    }

    @Benchmark
    public void shortWalk_LumbridgeToVarrock(Blackhole bh) {
        int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
        int target = WorldPointUtil.packWorldPoint(3213, 3428, 0);
        Pathfinder pf = new Pathfinder(configWithTeleports, start, Set.of(target));
        pf.run();
        bh.consume(pf.getResult());
    }

    @Benchmark
    public void crossMap_LumbridgeToArdougne(Blackhole bh) {
        int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
        int target = WorldPointUtil.packWorldPoint(2662, 3305, 0);
        Pathfinder pf = new Pathfinder(configWithTeleports, start, Set.of(target));
        pf.run();
        bh.consume(pf.getResult());
    }

    @Benchmark
    public void longWalk_LumbridgeToRellekka(Blackhole bh) {
        int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
        int target = WorldPointUtil.packWorldPoint(2660, 3657, 0);
        Pathfinder pf = new Pathfinder(configWalkOnly, start, Set.of(target));
        pf.run();
        bh.consume(pf.getResult());
    }

    @Benchmark
    public void teleportHeavy_GEToZulAndra(Blackhole bh) {
        int start = WorldPointUtil.packWorldPoint(3165, 3487, 0);
        int target = WorldPointUtil.packWorldPoint(2200, 3056, 0);
        Pathfinder pf = new Pathfinder(configWithTeleports, start, Set.of(target));
        pf.run();
        bh.consume(pf.getResult());
    }

    @Benchmark
    public void wilderness_EdgevilleToMageArena(Blackhole bh) {
        int start = WorldPointUtil.packWorldPoint(3087, 3496, 0);
        int target = WorldPointUtil.packWorldPoint(3105, 3951, 0);
        Pathfinder pf = new Pathfinder(configWithTeleports, start, Set.of(target));
        pf.run();
        bh.consume(pf.getResult());
    }

    @Benchmark
    public void multiPlane_FaladorToWhiteKnight2F(Blackhole bh) {
        int start = WorldPointUtil.packWorldPoint(2964, 3378, 0);
        int target = WorldPointUtil.packWorldPoint(2961, 3339, 2);
        Pathfinder pf = new Pathfinder(configWithTeleports, start, Set.of(target));
        pf.run();
        bh.consume(pf.getResult());
    }
}
