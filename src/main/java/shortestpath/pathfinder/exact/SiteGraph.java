package shortestpath.pathfinder.exact;

import java.util.Arrays;

/** Immutable two-layer account-specific site graph with reverse CSR edges. */
public final class SiteGraph
{
	public enum EdgeKind
	{
		LOCAL,
		BANK_TRANSITION,
		BANK_GLOBAL_ENTRY,
		BANK_GLOBAL_DESTINATION,
		SEPARATOR
	}

	private final RoutingStatic stat;
	private final PreparedRoutingAccount account;
	private final int nodeCount;
	private final boolean hasBankedGlobalHub;
	private final int[] reverseOffsets;
	private final int[] reverseFromStates;
	private final int[] reverseCosts;
	private final boolean[] reverseStartsGenerator;
	private final byte[] reverseKinds;

	public SiteGraph(RoutingStatic stat, PreparedRoutingAccount account)
	{
		this.stat = stat;
		this.account = account;
		int spatialCount = stat.siteCount();
		validateEndpoints(account, stat);
		int[] bankSites = bankSites(stat, account);
		int[] globalDestinations = globalDestinations(account, stat);
		hasBankedGlobalHub = account.allowTransports() && account.bankPathEnabled() && bankSites.length != 0
			&& globalDestinations.length != 0;
		nodeCount = spatialCount + (hasBankedGlobalHub ? 1 : 0);

		EdgeBuilder edges = new EdgeBuilder();
		addLocalEdges(edges, account.localView(false), false, stat);
		addLocalEdges(edges, account.localView(true), true, stat);
		if (account.bankPathEnabled())
		{
			for (int bankSite : bankSites)
			{
				edges.add(stateId(bankSite, false), stateId(bankSite, true), account.bankVisitCost(), true,
					EdgeKind.BANK_TRANSITION);
			}
		}
		if (hasBankedGlobalHub)
		{
			int hub = spatialCount;
			for (int bankSite : bankSites)
			{
				edges.add(stateId(bankSite, false), stateId(hub, true), account.bankVisitCost(), true,
					EdgeKind.BANK_GLOBAL_ENTRY);
			}
			for (int destination : globalDestinations)
			{
				int cost = minimumGlobalCost(account.globalView(true), destination);
				if (cost != ExactCosts.INF)
				{
					edges.add(stateId(hub, true), stateId(stat.siteIndex(destination), true), cost, false,
						EdgeKind.BANK_GLOBAL_DESTINATION);
				}
			}
		}
		for (int i = 0; i < stat.crossingCount(); i++)
		{
			int from = stat.crossingFromSite(i);
			int to = stat.crossingToSite(i);
			int cost = ExactCosts.validate(stat.crossingCost(i));
			for (boolean banked : new boolean[] {false, true})
			{
				edges.add(stateId(from, banked), stateId(to, banked), cost, true, EdgeKind.SEPARATOR);
				edges.add(stateId(to, banked), stateId(from, banked), cost, true, EdgeKind.SEPARATOR);
			}
		}
		Csr csr = edges.build(nodeCount * 2);
		reverseOffsets = csr.offsets;
		reverseFromStates = csr.fromStates;
		reverseCosts = csr.costs;
		reverseStartsGenerator = csr.startsGenerator;
		reverseKinds = csr.kinds;
	}

	public static SiteGraph compile(RoutingStatic stat, PreparedRoutingAccount account)
	{
		return new SiteGraph(stat, account);
	}

	public RoutingStatic routingStatic()
	{
		return stat;
	}
	public PreparedRoutingAccount preparedAccount()
	{
		return account;
	}
	public int spatialNodeCount()
	{
		return stat.siteCount();
	}
	public int nodeCount()
	{
		return nodeCount;
	}
	public int stateCount()
	{
		return nodeCount * 2;
	}
	public boolean hasBankedGlobalHub()
	{
		return hasBankedGlobalHub;
	}
	public int bankedGlobalHubNode()
	{
		return hasBankedGlobalHub ? stat.siteCount() : -1;
	}
	public int siteTile(int node)
	{
		return stat.siteTile(node);
	}
	public int nodeForTile(int tile)
	{
		return stat.siteIndex(tile);
	}

	public int reverseEdgeCount()
	{
		return reverseFromStates.length;
	}
	public int reverseEdgeStart(int state)
	{
		return reverseOffsets[state];
	}
	public int reverseEdgeEnd(int state)
	{
		return reverseOffsets[state + 1];
	}
	public int reverseFromState(int edge)
	{
		return reverseFromStates[edge];
	}
	public int reverseCost(int edge)
	{
		return reverseCosts[edge];
	}
	public boolean reverseStartsGenerator(int edge)
	{
		return reverseStartsGenerator[edge];
	}
	public EdgeKind reverseKind(int edge)
	{
		return EdgeKind.values()[reverseKinds[edge]];
	}

	public static int stateId(int node, boolean banked)
	{
		if (node < 0)
			throw new IllegalArgumentException("negative site node");
		return node * 2 + (banked ? 1 : 0);
	}

	private static void addLocalEdges(
		EdgeBuilder edges, PreparedRoutingAccount.View view, boolean banked, RoutingStatic stat)
	{
		for (int i = 0; i < view.count; i++)
		{
			int from = stat.siteIndex(view.origins[i]);
			int to = stat.siteIndex(view.destinations[i]);
			if (view.costs[i] != ExactCosts.INF)
			{
				edges.add(stateId(from, banked), stateId(to, banked), view.costs[i], true, EdgeKind.LOCAL);
			}
		}
	}

	private static void validateEndpoints(PreparedRoutingAccount account, RoutingStatic stat)
	{
		for (boolean banked : new boolean[] {false, true})
		{
			PreparedRoutingAccount.View local = account.localView(banked);
			for (int i = 0; i < local.count; i++)
			{
				requireSite(stat, local.origins[i], "local origin");
				requireSite(stat, local.destinations[i], "local destination");
			}
			PreparedRoutingAccount.View global = account.globalView(banked);
			for (int i = 0; i < global.count; i++)
				requireSite(stat, global.destinations[i], "global destination");
		}
	}

	private static void requireSite(RoutingStatic stat, int tile, String label)
	{
		if (stat.siteIndex(tile) < 0)
		{
			throw new IllegalStateException(label + " is not a static site: " + Integer.toUnsignedString(tile));
		}
	}

	/** The static bank sites this account may use. */
	private static int[] bankSites(RoutingStatic stat, PreparedRoutingAccount account)
	{
		int[] result = new int[stat.reachableBankCount()];
		int count = 0;
		for (int i = 0; i < result.length; i++)
		{
			int tile = stat.reachableBankTile(i);
			int site = stat.siteIndex(tile);
			if (site < 0)
				throw new IllegalStateException("reachable bank is not a static site");
			if (account.bankAccessible(tile))
				result[count++] = site;
		}
		return Arrays.copyOf(result, count);
	}

	private static int[] globalDestinations(PreparedRoutingAccount account, RoutingStatic stat)
	{
		PreparedRoutingAccount.View view = account.globalView(true);
		int[] result = new int[view.count];
		int count = 0;
		for (int i = 0; i < view.count; i++)
		{
			int destination = view.destinations[i];
			boolean present = false;
			for (int j = 0; j < count; j++)
				present |= result[j] == destination;
			if (!present)
				result[count++] = destination;
		}
		result = Arrays.copyOf(result, count);
		for (int i = 1; i < count; i++)
		{
			int value = result[i];
			int j = i - 1;
			while (j >= 0 && Integer.compareUnsigned(result[j], value) > 0)
			{
				result[j + 1] = result[j--];
			}
			result[j + 1] = value;
		}
		for (int destination : result)
			requireSite(stat, destination, "global destination");
		return result;
	}

	private static int minimumGlobalCost(PreparedRoutingAccount.View view, int destination)
	{
		int minimum = ExactCosts.INF;
		for (int i = 0; i < view.count; i++)
		{
			if (view.destinations[i] == destination && view.costs[i] < minimum)
				minimum = view.costs[i];
		}
		return minimum;
	}

	private static final class EdgeBuilder
	{
		private int size;
		private int[] from = new int[64], to = new int[64], costs = new int[64];
		private boolean[] starts = new boolean[64];
		private byte[] kinds = new byte[64];

		void add(int fromState, int toState, int cost, boolean startsGenerator, EdgeKind kind)
		{
			ExactCosts.validate(cost);
			if (cost == ExactCosts.INF)
				return;
			if (size == from.length)
			{
				int capacity = size * 2;
				from = Arrays.copyOf(from, capacity);
				to = Arrays.copyOf(to, capacity);
				costs = Arrays.copyOf(costs, capacity);
				starts = Arrays.copyOf(starts, capacity);
				kinds = Arrays.copyOf(kinds, capacity);
			}
			from[size] = fromState;
			to[size] = toState;
			costs[size] = cost;
			starts[size] = startsGenerator;
			kinds[size++] = (byte) kind.ordinal();
		}

		Csr build(int stateCount)
		{
			int[] offsets = new int[stateCount + 1];
			for (int i = 0; i < size; i++)
				offsets[to[i] + 1]++;
			for (int i = 1; i < offsets.length; i++)
				offsets[i] += offsets[i - 1];
			int[] positions = Arrays.copyOfRange(offsets, 1, offsets.length);
			int[] sortedFrom = new int[size], sortedCosts = new int[size];
			boolean[] sortedStarts = new boolean[size];
			byte[] sortedKinds = new byte[size];
			for (int i = 0; i < size; i++)
			{
				int position = --positions[to[i]];
				sortedFrom[position] = from[i];
				sortedCosts[position] = costs[i];
				sortedStarts[position] = starts[i];
				sortedKinds[position] = kinds[i];
			}
			return new Csr(offsets, sortedFrom, sortedCosts, sortedStarts, sortedKinds);
		}
	}

	private static final class Csr
	{
		final int[] offsets, fromStates, costs;
		final boolean[] startsGenerator;
		final byte[] kinds;

		Csr(int[] offsets, int[] fromStates, int[] costs, boolean[] startsGenerator, byte[] kinds)
		{
			this.offsets = offsets;
			this.fromStates = fromStates;
			this.costs = costs;
			this.startsGenerator = startsGenerator;
			this.kinds = kinds;
		}
	}
}
