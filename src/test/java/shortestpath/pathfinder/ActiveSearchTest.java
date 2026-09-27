package shortestpath.pathfinder;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ActiveSearchTest
{
	@Test
	public void legacyPathfinderImplementsActiveSearch()
	{
		assertTrue(ActiveSearch.class.isAssignableFrom(Pathfinder.class));
	}

	private ActiveSearch compileContract(Pathfinder pathfinder)
	{
		return pathfinder;
	}
}
