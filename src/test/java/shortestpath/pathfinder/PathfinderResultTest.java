package shortestpath.pathfinder;

import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;

public class PathfinderResultTest
{
	private static PathfinderConfig configWithCutoff(int cutoffTicks)
	{
		return configWithCutoff(cutoffTicks, 2);
	}

	private static PathfinderConfig configWithCutoff(int cutoffTicks, int unreachableTargetDistance)
	{
		Client client = mock(Client.class);
		TestShortestPathConfig config = new TestShortestPathConfig();
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getTotalLevel()).thenReturn(2277);
		config.setCalculationCutoffValue(cutoffTicks);
		config.setUnreachableTargetDistanceValue(unreachableTargetDistance);
		config.setUseTeleportationItemsValue(TeleportationItem.ALL);

		PathfinderConfig pathfinderConfig = new TestPathfinderConfig(client, config);
		pathfinderConfig.refresh();
		return pathfinderConfig;
	}

	private static int point(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	@Test
	public void reachedTargetProducesReachedResult()
	{
		Pathfinder pathfinder = new Pathfinder(configWithCutoff(100), point(3200, 3200), Set.of(point(3201, 3200)));

		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();

		assertTrue(result.isReached());
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
	}

	@Test
	public void zeroCutoffProducesCutoffResult()
	{
		Pathfinder pathfinder = new Pathfinder(configWithCutoff(0), point(3200, 3200), Set.of(point(3300, 3300)));

		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();

		assertEquals(PathTerminationReason.CUTOFF_REACHED, result.getTerminationReason());
	}

	@Test
	public void prefersShorterPathWithinUnreachableThreshold()
	{
		int start = point(3139, 3445);
		int nearbyPieDish = point(3142, 3447);
		int distantTarget = WorldPointUtil.packWorldPoint(2813, 3449, 1);
		PathfinderConfig config = configWithCutoff(100, 3);

		Pathfinder pathfinder = new Pathfinder(config, start, Set.of(nearbyPieDish, distantTarget));
		pathfinder.run();

		PathfinderResult result = pathfinder.getResult();
		assertEquals(nearbyPieDish, result.getTarget());
		assertTrue(result.isReached());
		assertTrue(result.getClosestReachedPoint() != nearbyPieDish);
		assertTrue(WorldPointUtil.distanceBetween(nearbyPieDish, result.getClosestReachedPoint()) <= config.getUnreachableTargetDistance());
	}
}
