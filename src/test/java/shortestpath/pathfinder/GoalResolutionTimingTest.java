package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.sailing.SailingMoves;
import shortestpath.pathfinder.sailing.SailingPathfinder;

/**
 * Every search resolves its goals when it runs, on the pathfinding thread, not when it is created
 * on the client thread. Resolving a blocked target always asks whether a transport lands on it, so
 * the count of those questions shows when resolution happens.
 */
public class GoalResolutionTimingTest
{
	private static final int START = WorldPointUtil.packWorldPoint(3093, 3245, 0);
	// A Draynor bank booth: blocked, so resolving it asks about transports.
	private static final int BOOTH = WorldPointUtil.packWorldPoint(3091, 3242, 0);

	private int transportDestinationChecks;
	private PathfinderConfig config;

	@Before
	public void before()
	{
		Client client = mock(Client.class);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		config = new TestPathfinderConfig(client, new TestShortestPathConfig())
		{
			@Override
			public boolean isTransportDestination(int packedPosition)
			{
				transportDestinationChecks++;
				return super.isTransportDestination(packedPosition);
			}
		};
		config.refresh();
		transportDestinationChecks = 0;
	}

	@Test
	public void legacyResolvesGoalsWhenItRuns()
	{
		assertResolvesGoalsWhenItRuns(new Pathfinder(config, START, Set.of(BOOTH)));
	}

	@Test
	public void sailingResolvesGoalsWhenItRuns()
	{
		assertResolvesGoalsWhenItRuns(new SailingPathfinder(config, START, Set.of(BOOTH), null,
			SailingMoves.forSpeed(1.5), null));
	}

	@Test
	public void aStarResolvesGoalsWhenItRuns()
	{
		assertResolvesGoalsWhenItRuns(new AStarPathfinder(config, TestWorldData.routingStatic(), START,
			Set.of(BOOTH), null));
	}

	private void assertResolvesGoalsWhenItRuns(ActiveSearch search)
	{
		assertEquals("creating a search must not resolve its goals", 0, transportDestinationChecks);
		search.run();
		assertTrue("running it resolves them", transportDestinationChecks > 0);
	}
}
