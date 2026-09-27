package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ExactCostsTest
{
	@Test
	public void addsFiniteCosts()
	{
		assertEquals(7, ExactCosts.add(3, 4));
		assertEquals(0, ExactCosts.add(0, 0));
		assertEquals(ExactCosts.INF - 1, ExactCosts.add(ExactCosts.INF - 1, 0));
	}

	@Test
	public void infinitySaturates()
	{
		assertEquals(ExactCosts.INF, ExactCosts.add(ExactCosts.INF, 1));
		assertEquals(ExactCosts.INF, ExactCosts.add(1, ExactCosts.INF));
		assertEquals(ExactCosts.INF, ExactCosts.add(ExactCosts.INF - 1, 1));
	}

	@Test
	public void rejectsNegativeCosts()
	{
		try
		{
			ExactCosts.add(-1, 0);
			fail("negative costs must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}
}
