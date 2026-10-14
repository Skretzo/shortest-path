package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CostArithmeticTest
{
	@Test
	public void addsFiniteCosts()
	{
		assertEquals(7, CostArithmetic.add(3, 4));
		assertEquals(0, CostArithmetic.add(0, 0));
		assertEquals(CostArithmetic.INF - 1, CostArithmetic.add(CostArithmetic.INF - 1, 0));
	}

	@Test
	public void infinitySaturates()
	{
		assertEquals(CostArithmetic.INF, CostArithmetic.add(CostArithmetic.INF, 1));
		assertEquals(CostArithmetic.INF, CostArithmetic.add(1, CostArithmetic.INF));
		assertEquals(CostArithmetic.INF, CostArithmetic.add(CostArithmetic.INF - 1, 1));
	}

	@Test
	public void rejectsNegativeCosts()
	{
		try
		{
			CostArithmetic.add(-1, 0);
			fail("negative costs must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	@Test
	public void weightsSearchPrioritySafely()
	{
		assertEquals(13, AStarForwardSearch.priority(3, 5, 2));
		assertEquals(CostArithmetic.INF - 1,
			AStarForwardSearch.priority(3, CostArithmetic.INF - 1, Double.MAX_VALUE));
		try
		{
			AStarForwardSearch.validateHeuristicWeight(0);
			fail("zero heuristic weight must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}
}
