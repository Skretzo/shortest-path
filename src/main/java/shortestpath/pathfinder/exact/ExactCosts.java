package shortestpath.pathfinder.exact;

/**
 * Shared cost arithmetic for the exact pathfinder.
 */
public final class ExactCosts
{
	public static final int INF = Integer.MAX_VALUE;

	private ExactCosts()
	{
	}

	public static int validate(int cost)
	{
		if (cost < 0)
		{
			throw new IllegalArgumentException("Cost must not be negative: " + cost);
		}
		return cost;
	}

	public static int add(int left, int right)
	{
		validate(left);
		validate(right);
		if (left == INF || right == INF || left > INF - right)
		{
			return INF;
		}
		return left + right;
	}

	public static int twice(int cost)
	{
		validate(cost);
		return cost == INF || cost > INF / 2 ? INF : cost * 2;
	}

	public static int halve(int cost)
	{
		validate(cost);
		return cost == INF ? INF : cost / 2;
	}
}
