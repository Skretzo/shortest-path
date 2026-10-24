package shortestpath.pathfinder.astar;

import java.util.Arrays;
import shortestpath.WorldPointUtil;

/**
 * Exact walking distances between the sites of each small routing component.
 *
 * <p>The sparse walking network joins a component's sites by Chebyshev distance, which a winding
 * component (a dungeon corridor, a walled town) can undercut several times over. Reverse labels
 * then make a site at one end look almost as close to the target as one at the other, and the
 * forward search floods everything reachable through transports before the gap is closed. For a
 * component with at most {@link #MAX_SITES} sites this class walks from every site over the
 * component's own tiles and keeps the site-pair distances instead; the network joins those
 * components' sites by them in place of a Steiner tree.
 *
 * <p>Why a walk inside the component is a lower bound: routing components are only left by
 * crossing a cut, whose endpoints are sites, so a walk between two sites either stays in the
 * component or passes through further sites, whose own edges price the rest. Each distance is
 * also kept at least the Chebyshev distance it replaces, and the shorter of the two directions.
 *
 * <p>Larger components (the Wilderness, the open surface) keep their trees: their pair counts grow
 * quadratically, and they wind less. An edge is left out when a path through a third site of the
 * component is no longer, so the network's distances equal the full pair table's.
 */
final class ExactWalks
{
	/** The most sites a component may have for its sites to be joined by exact walks. */
	static final int MAX_SITES = 256;
	private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
	private static final int[] DY = {1, 1, 0, -1, -1, -1, 0, 1};

	private final boolean[] exact;
	/** Per component: its kept edges as (site, site, distance) triples, or {@code null}. */
	private final int[][] edges;
	private final int edgeCount;

	private ExactWalks(boolean[] exact, int[][] edges, int edgeCount)
	{
		this.exact = exact;
		this.edges = edges;
		this.edgeCount = edgeCount;
	}

	static ExactWalks build(RoutingStatic stat)
	{
		return build(stat, MAX_SITES);
	}

	static ExactWalks build(RoutingStatic stat, int maxSites)
	{
		int componentCount = stat.routingComponentCount();
		boolean[] exact = new boolean[componentCount];
		for (int component = 0; component < componentCount; component++)
		{
			int sites = stat.componentSiteCount(component);
			exact[component] = sites >= 2 && sites <= maxSites;
		}
		int[][] tiles = componentTiles(stat, exact);
		int[] local = new int[stat.searchTileCount()];
		Arrays.fill(local, -1);
		int[][] edges = new int[componentCount][];
		int edgeCount = 0;
		for (int component = 0; component < componentCount; component++)
		{
			if (!exact[component]) continue;
			int[] sites = stat.componentSites(component);
			int[][] distance = pairDistances(stat, sites, tiles[component], local);
			edges[component] = keptEdges(sites, distance);
			edgeCount += edges[component].length / 3;
		}
		return new ExactWalks(exact, edges, edgeCount);
	}

	/** Whether the component's sites are joined by exact walks rather than a Steiner tree. */
	boolean isExact(int component)
	{
		return exact[component];
	}

	/** The component's kept edges as (site, site, distance) triples. */
	int[] edges(int component)
	{
		return edges[component];
	}

	int edgeCount()
	{
		return edgeCount;
	}

	int exactComponentCount()
	{
		int count = 0;
		for (boolean value : exact)
			if (value) count++;
		return count;
	}

	/**
	 * The full site-pair table of one component, indexed like {@link RoutingStatic#componentSites},
	 * with {@link CostArithmetic#INF} for pairs that cannot walk to each other inside it.
	 */
	static int[][] pairDistances(RoutingStatic stat, int component)
	{
		boolean[] only = new boolean[stat.routingComponentCount()];
		only[component] = true;
		int[] local = new int[stat.searchTileCount()];
		Arrays.fill(local, -1);
		return pairDistances(stat, stat.componentSites(component), componentTiles(stat, only)[component], local);
	}

	/** Every chosen component's search tiles, ascending. */
	private static int[][] componentTiles(RoutingStatic stat, boolean[] chosen)
	{
		int[] counts = new int[chosen.length];
		for (int node = 0; node < stat.searchTileCount(); node++)
			if (chosen[stat.routingComponent(node)]) counts[stat.routingComponent(node)]++;
		int[][] tiles = new int[chosen.length][];
		for (int component = 0; component < chosen.length; component++)
			if (chosen[component]) tiles[component] = new int[counts[component]];
		int[] fill = new int[chosen.length];
		for (int node = 0; node < stat.searchTileCount(); node++)
		{
			int component = stat.routingComponent(node);
			if (chosen[component]) tiles[component][fill[component]++] = node;
		}
		return tiles;
	}

	/**
	 * One forward walk from each site over the component's tiles, stopping once every site is
	 * reached. A site off the search tiles (a blocked transport origin) is walked from and to
	 * through its neighbours in the component. {@code local} must be all -1, and is again after.
	 */
	private static int[][] pairDistances(RoutingStatic stat, int[] sites, int[] tiles, int[] local)
	{
		for (int i = 0; i < tiles.length; i++) local[tiles[i]] = i;
		int k = sites.length;
		int[][] ends = new int[k][];
		boolean[] target = new boolean[tiles.length];
		int targetCount = 0;
		for (int i = 0; i < k; i++)
		{
			ends[i] = siteTiles(stat, stat.siteTile(sites[i]), local);
			for (int end : ends[i])
			{
				if (!target[end]) targetCount++;
				target[end] = true;
			}
		}
		int[] distance = new int[tiles.length], seen = new int[tiles.length], queue = new int[tiles.length];
		int[][] walk = new int[k][k];
		for (int i = 0; i < k; i++)
		{
			int mark = i + 1, head = 0, tail = 0, remaining = targetCount;
			for (int end : ends[i])
			{
				if (seen[end] == mark) continue;
				seen[end] = mark;
				distance[end] = 0;
				queue[tail++] = end;
				if (target[end]) remaining--;
			}
			while (head < tail && remaining > 0)
			{
				int current = queue[head++], node = tiles[current];
				int mask = stat.walkingMask(node), next = distance[current] + 1;
				for (int bit = 0; bit < 8; bit++)
				{
					if ((mask & (1 << bit)) == 0) continue;
					int to = stat.walkSuccessor(node, bit);
					if (to < 0 || to >= local.length || local[to] < 0 || seen[local[to]] == mark) continue;
					seen[local[to]] = mark;
					distance[local[to]] = next;
					queue[tail++] = local[to];
					if (target[local[to]]) remaining--;
				}
			}
			for (int j = 0; j < k; j++)
			{
				int best = CostArithmetic.INF;
				for (int end : ends[j])
					if (seen[end] == mark) best = Math.min(best, distance[end]);
				walk[i][j] = best;
			}
		}
		for (int tile : tiles) local[tile] = -1;

		int[][] result = new int[k][k];
		for (int i = 0; i < k; i++)
		{
			for (int j = 0; j < k; j++)
			{
				int best = Math.min(walk[i][j], walk[j][i]);
				result[i][j] = i == j ? 0 : best == CostArithmetic.INF ? best
					: Math.max(best, WorldPointUtil.distanceBetween(stat.siteTile(sites[i]), stat.siteTile(sites[j])));
			}
		}
		return result;
	}

	/** The component tiles a site is walked from and to: its own, else its neighbours'. */
	private static int[] siteTiles(RoutingStatic stat, int packed, int[] local)
	{
		int node = stat.searchIndex(packed);
		if (node >= 0 && local[node] >= 0) return new int[] {local[node]};
		int x = WorldPointUtil.unpackWorldX(packed), y = WorldPointUtil.unpackWorldY(packed);
		int plane = WorldPointUtil.unpackWorldPlane(packed);
		int[] result = new int[8];
		int count = 0;
		for (int bit = 0; bit < 8; bit++)
		{
			int neighbour = stat.searchIndex(WorldPointUtil.packWorldPoint(x + DX[bit], y + DY[bit], plane));
			if (neighbour >= 0 && local[neighbour] >= 0) result[count++] = local[neighbour];
		}
		return Arrays.copyOf(result, count);
	}

	/**
	 * The pairs to keep as edges: every finite pair but those a path through a third site matches.
	 * Distances between distinct sites are positive, so each dropped edge is replaced by strictly
	 * shorter kept ones, and shortest paths over the kept edges equal the table.
	 */
	private static int[] keptEdges(int[] sites, int[][] distance)
	{
		int k = sites.length;
		int[] result = new int[3 * k];
		int size = 0;
		for (int i = 0; i < k; i++)
		{
			for (int j = i + 1; j < k; j++)
			{
				if (distance[i][j] == CostArithmetic.INF) continue;
				boolean redundant = false;
				for (int m = 0; m < k && !redundant; m++)
					redundant = m != i && m != j && CostArithmetic.add(distance[i][m], distance[m][j]) <= distance[i][j];
				if (redundant) continue;
				if (size + 3 > result.length) result = Arrays.copyOf(result, result.length * 2);
				result[size++] = sites[i];
				result[size++] = sites[j];
				result[size++] = distance[i][j];
			}
		}
		return Arrays.copyOf(result, size);
	}
}
