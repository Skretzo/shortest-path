package shortestpath.pathfinder.astar;

import java.util.HashMap;
import java.util.Map;
import shortestpath.pathfinder.BankVisitState;

/**
 * Edges between tiles that only the search's estimate uses: a lower bound on getting from one tile to another through
 * a {@link SideGraph}, such as sailing from one mooring to another. The estimate sees through the side graph by them;
 * a route never takes them, as the search takes the side graph itself.
 */
public final class EstimateEdges
{
	public static final EstimateEdges NONE = new Builder().build();

	// Per bank layer, the edges in unsigned order of origin, then destination
	private final int[][] origins, destinations, costs;

	private EstimateEdges(int[][] origins, int[][] destinations, int[][] costs)
	{
		this.origins = origins;
		this.destinations = destinations;
		this.costs = costs;
	}

	public int count(BankVisitState banked)
	{
		return origins[layer(banked)].length;
	}

	public int origin(BankVisitState banked, int index)
	{
		return origins[layer(banked)][index];
	}

	public int destination(BankVisitState banked, int index)
	{
		return destinations[layer(banked)][index];
	}

	public int cost(BankVisitState banked, int index)
	{
		return costs[layer(banked)][index];
	}

	private static int layer(BankVisitState banked)
	{
		return banked == BankVisitState.BANKED ? 1 : 0;
	}

	/** Collects edges, keeping the least cost for each origin and destination in each bank layer. */
	public static final class Builder
	{
		@SuppressWarnings("unchecked")
		private final Map<Long, Integer>[] least = new Map[] {new HashMap<>(), new HashMap<>()};

		/** An edge from {@code origin} to {@code destination} in bank layer {@code banked}, costing at least {@code cost}. */
		public Builder add(BankVisitState banked, int origin, int destination, int cost)
		{
			least[layer(banked)].merge(((long) origin << 32) | (destination & 0xFFFFFFFFL), CostArithmetic.validate(cost),
				Math::min);
			return this;
		}

		public EstimateEdges build()
		{
			int[][] origins = new int[2][], destinations = new int[2][], costs = new int[2][];
			for (int layer = 0; layer < 2; layer++)
			{
				// Origin then destination, unsigned, is the order of the keys as unsigned longs
				long[] keys = least[layer].keySet().stream().mapToLong(Long::longValue)
					.map(key -> key ^ Long.MIN_VALUE).sorted().map(key -> key ^ Long.MIN_VALUE).toArray();
				origins[layer] = new int[keys.length];
				destinations[layer] = new int[keys.length];
				costs[layer] = new int[keys.length];
				for (int i = 0; i < keys.length; i++)
				{
					origins[layer][i] = (int) (keys[i] >>> 32);
					destinations[layer][i] = (int) keys[i];
					costs[layer][i] = least[layer].get(keys[i]);
				}
			}
			return new EstimateEdges(origins, destinations, costs);
		}
	}
}
