package shortestpath.pathfinder;

import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.TestShortestPathConfig;
import shortestpath.requirement.model.Unlock;
import shortestpath.settings.Settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The injected {@link Settings} override map must reach every overridable read
 * in {@link PathfinderConfig#refresh()}, while the deliberately raw reads
 * (backend, cutoff, POH set copies) keep ignoring payloads.
 */
@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigOverrideTest
{
	@Mock
	private Client client;
		private TestShortestPathConfig config = new TestShortestPathConfig();

	private Settings settings;
	private PathfinderConfig pathfinderConfig;

	@Before
	public void setup()
	{
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		settings = Settings.wrap(config);
		pathfinderConfig = new PathfinderConfig(client, config, settings);
	}

	@Test
	public void overridePayloadReachesRefreshFields()
	{
		settings.applyOverrides(Map.of(
			"unreachableTargetDistanceThreshold", 250,
			"aStarHeuristicWeight", 250,
			"costBankVisit", 9,
			"includeBankPath", true,
			"unlockCanoeAxe", true));

		pathfinderConfig.refresh();

		assertEquals(250, pathfinderConfig.getUnreachableTargetDistance());
		assertEquals(2.5, pathfinderConfig.getAStarHeuristicWeight(), 0.0);
		assertEquals(9, pathfinderConfig.getBankVisitCost());
		assertTrue(pathfinderConfig.isBankPathEnabled());
		assertTrue(pathfinderConfig.getUnlocks().contains(Unlock.CANOE_AXE));
	}

	@Test
	public void overridePayloadIsIgnoredByRawReads()
	{
		config.set("calculationCutoff", 5);
		config.set("pathfinderAlgorithm", PathfinderBackend.ASTAR);
		settings.applyOverrides(Map.of(
			"calculationCutoff", 99,
			"pathfinderAlgorithm", "Legacy"));

		pathfinderConfig.refresh();

		assertEquals(5L * Constants.GAME_TICK_LENGTH, pathfinderConfig.getCalculationCutoffMillis());
		assertEquals(PathfinderBackend.ASTAR, pathfinderConfig.getPathfinderBackend());
	}

	@Test
	public void clearedOverridesReturnToConfiguredValues()
	{
		settings.applyOverrides(Map.of("unreachableTargetDistanceThreshold", 250));
		settings.clearOverrides();
		config.set("unreachableTargetDistanceThreshold", 42);

		pathfinderConfig.refresh();

		assertEquals(42, pathfinderConfig.getUnreachableTargetDistance());
		assertFalse(pathfinderConfig.isBankPathEnabled());
	}
}
