package shortestpath.pathfinder;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import net.runelite.api.Client;
import org.junit.Test;
import shortestpath.ShortestPathConfig;

public class PathfinderConfigTimeTest
{
	@Test
	public void defaultTimeUsesWallClockMinutes()
	{
		PathfinderConfig config = new TestPathfinderConfig(mock(Client.class), mock(ShortestPathConfig.class));
		long expected = System.currentTimeMillis() / 60_000L;

		assertTrue(Math.abs(config.currentTimeMinutes() - expected) <= 1);
	}
}
