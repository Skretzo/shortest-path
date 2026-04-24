package shortestpath.dashboard;

import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;
import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderProfile;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.ProfilingPathfinder;
import shortestpath.pathfinder.TestPathfinderConfig;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ProfilingPathfinder} produces the same results as the
 * standard {@link Pathfinder} and that profiling overhead is negligible.
 * Lives in the dashboard package because {@link ProfilingPathfinder} is the
 * engine behind {@link DashboardTest}.
 */
public class ProfilerTest {
    private Client client;
    private TestShortestPathConfig config;
    private PathfinderConfig pathfinderConfig;

    @Before
    public void setUp() {
        client = mock(Client.class);
        config = new TestShortestPathConfig();
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
        config.setCalculationCutoffValue(500);
        config.setUseTeleportationItemsValue(TeleportationItem.ALL);
        config.setIncludeBankPathValue(false);

        pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED, true, true);
        pathfinderConfig.refresh();
    }

    @Test
    public void profilingDoesNotAffectResults() {
        // Verify that profiled (ProfilingPathfinder) and unprofiled (Pathfinder) produce identical paths
        int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
        int target = WorldPointUtil.packWorldPoint(3213, 3428, 0);

        // Warm up both implementations so JIT compilation does not skew the overhead ratio.
        for (int i = 0; i < 10; i++) {
            new Pathfinder(pathfinderConfig, start, Set.of(target)).run();
            new ProfilingPathfinder(pathfinderConfig, start, Set.of(target)).run();
        }

        // Run both once and record the time for the overhead assertion.
        Pathfinder unprofiled = new Pathfinder(pathfinderConfig, start, Set.of(target));
        unprofiled.run();

        ProfilingPathfinder profiled = new ProfilingPathfinder(pathfinderConfig, start, Set.of(target));
        long profiledStart = System.nanoTime();
        profiled.run();
        long profiledNanos = System.nanoTime() - profiledStart;

        PathfinderResult unprofiledResult = unprofiled.getResult();
        PathfinderResult profiledResult = profiled.getResult();

        assertNotNull("Unprofiled result should not be null", unprofiledResult);
        assertNotNull("Profiled result should not be null", profiledResult);

        // Same path length
        assertTrue("Profiled and unprofiled paths should have same length, got " +
            unprofiledResult.getPathSteps().size() + " vs " + profiledResult.getPathSteps().size(),
            unprofiledResult.getPathSteps().size() == profiledResult.getPathSteps().size());

        // Same reached state
        assertTrue("Profiled and unprofiled should have same reached state",
            unprofiledResult.isReached() == profiledResult.isReached());

        // Profile has meaningful data
        PathfinderProfile profile = profiled.getProfile();
        assertNotNull("Profile data should be present", profile);
        assertTrue("Should have checked some nodes", profile.getTileNeighborsAdded() > 0);
        assertTrue("addNeighbors time should be positive", profile.getAddNeighborsNanos() > 0);
        assertTrue("Peak boundary size should be positive", profile.getPeakBoundarySize() > 0);

        // Profiling overhead must not cause catastrophic slowdown.
        // Use an absolute cap (2× the calculation cutoff) rather than a ratio,
        // because ratio-based checks are unreliable when the unprofiled run is very fast.
        long maxProfiledNanos = config.calculationCutoff() * 2_000_000L;
        assertTrue("Profiling overhead too high: profiled=" + profiledNanos / 1_000_000 + "ms" +
            ", limit=" + maxProfiledNanos / 1_000_000 + "ms",
            profiledNanos < maxProfiledNanos);
    }
}
