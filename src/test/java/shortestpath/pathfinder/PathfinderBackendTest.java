package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PathfinderBackendTest
{
	@Test
	public void configurationUsesAStarEnumValueAndLabel()
	{
		assertEquals("ASTAR", PathfinderBackend.ASTAR.name());
		assertEquals("A*", PathfinderBackend.ASTAR.toString());
	}
}
