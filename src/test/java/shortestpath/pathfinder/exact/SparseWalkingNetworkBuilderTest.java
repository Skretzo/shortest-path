package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;
import shortestpath.WorldPointUtil;

/** Synthetic correctness tests for {@link SparseWalkingNetworkBuilder}. */
public class SparseWalkingNetworkBuilderTest
{
	private static final int INF = Integer.MAX_VALUE;

	@Test
	public void emptyInputProducesEmptyNetwork()
	{
		SparseWalkingNetworkBuilder.Result result =
			SparseWalkingNetworkBuilder.build(new int[0], new int[] {0}, new int[0]);
		assertEquals(0, result.originalCount);
		assertEquals(0, result.vertexCount);
		assertEquals(0, result.steinerCount);
		assertEquals(0, result.undirectedEdgeCount);
		assertArrayEquals(new int[] {0}, result.offsets);
		assertArrayEquals(new int[0], result.destinations);
		assertArrayEquals(new int[0], result.weights);
	}

	@Test
	public void singleSiteProducesSingleIsolatedVertex()
	{
		int tile = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		SparseWalkingNetworkBuilder.Result result =
			SparseWalkingNetworkBuilder.build(new int[] {tile}, new int[] {0, 1}, new int[] {0});
		assertEquals(1, result.originalCount);
		assertEquals(1, result.vertexCount);
		assertEquals(0, result.steinerCount);
		assertEquals(0, result.undirectedEdgeCount);
		assertArrayEquals(new int[] {0, 0}, result.offsets);
	}

	@Test
	public void siteWithNoComponentAttachmentIsIsolated()
	{
		int a = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		int b = WorldPointUtil.packWorldPoint(1010, 1000, 0);
		// Site 0 has no component attachments (empty range); site 1 attaches to component 0 alone.
		SparseWalkingNetworkBuilder.Result result = SparseWalkingNetworkBuilder.build(
			new int[] {a, b}, new int[] {0, 0, 1}, new int[] {0});
		assertEquals(2, result.originalCount);
		assertEquals(2, result.vertexCount);
		assertEquals(0, result.steinerCount);
		assertEquals(0, result.undirectedEdgeCount);
	}

	@Test
	public void distancesEqualDoubledChebyshevWithinOneComponent()
	{
		int[] xs = {1000, 1003, 1003, 998, 1050, 1000, 1200};
		int[] ys = {1000, 1000, 1004, 990, 1050, 1200, 900};
		int[] tiles = new int[xs.length];
		for (int i = 0; i < xs.length; i++)
		{
			tiles[i] = WorldPointUtil.packWorldPoint(xs[i], ys[i], 0);
		}
		int[] offsets = new int[tiles.length + 1];
		int[] ids = new int[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			offsets[i + 1] = i + 1;
			ids[i] = 0;
		}
		SparseWalkingNetworkBuilder.Result result = SparseWalkingNetworkBuilder.build(tiles, offsets, ids);
		assertEquals(tiles.length, result.originalCount);
		assertTrue(result.vertexCount >= result.originalCount);
		assertEquals(result.vertexCount - result.originalCount, result.steinerCount);
		assertEquals(result.undirectedEdgeCount * 2, result.destinations.length);

		for (int i = 0; i < tiles.length; i++)
		{
			for (int j = 0; j < tiles.length; j++)
			{
				if (i == j)
				{
					continue;
				}
				int expected = 2 * WorldPointUtil.distanceBetween(tiles[i], tiles[j]);
				int actual = dijkstra(result, i, j);
				assertEquals("distance " + i + "->" + j, expected, actual);
			}
		}
	}

	@Test
	public void coincidentSitesAreConnectedByZeroWeightEdges()
	{
		int tile = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		int[] tiles = {tile, tile, tile};
		int[] offsets = {0, 1, 2, 3};
		int[] ids = {0, 0, 0};
		SparseWalkingNetworkBuilder.Result result = SparseWalkingNetworkBuilder.build(tiles, offsets, ids);
		assertEquals(3, result.originalCount);
		assertEquals(3, result.vertexCount);
		assertEquals(0, result.steinerCount);
		assertEquals(2, result.undirectedEdgeCount);
		for (int i = 0; i < 3; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				if (i != j)
				{
					assertEquals(0, dijkstra(result, i, j));
				}
			}
		}
	}

	@Test
	public void differentComponentsDoNotConnect()
	{
		int a = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		int b = WorldPointUtil.packWorldPoint(1005, 1000, 0);
		int c = WorldPointUtil.packWorldPoint(1000, 1000, 1);
		int d = WorldPointUtil.packWorldPoint(1005, 1000, 1);
		// Sites 0,1 -> component 0 (plane 0). Sites 2,3 -> component 1 (plane 1, same x/y).
		int[] tiles = {a, b, c, d};
		int[] offsets = {0, 1, 2, 3, 4};
		int[] ids = {0, 0, 1, 1};
		SparseWalkingNetworkBuilder.Result result = SparseWalkingNetworkBuilder.build(tiles, offsets, ids);
		assertEquals(4, result.originalCount);
		assertEquals(INF, dijkstra(result, 0, 2));
		assertEquals(INF, dijkstra(result, 0, 3));
		assertEquals(INF, dijkstra(result, 1, 2));
		assertEquals(INF, dijkstra(result, 1, 3));
		assertEquals(2 * WorldPointUtil.distanceBetween(a, b), dijkstra(result, 0, 1));
		assertEquals(2 * WorldPointUtil.distanceBetween(c, d), dijkstra(result, 2, 3));
	}

	@Test
	public void multiComponentSiteBridgesBothTrees()
	{
		int a = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		int bridge = WorldPointUtil.packWorldPoint(1010, 1000, 0);
		int c = WorldPointUtil.packWorldPoint(1020, 1000, 0);
		int[] tiles = {a, bridge, c};
		// site 1 (bridge) attaches to both component 0 and component 1; sites 0 and 2 attach to
		// only one of them each, mirroring a site straddling two routing components.
		int[] offsets = {0, 1, 3, 4};
		int[] ids = {0, 0, 1, 1};
		SparseWalkingNetworkBuilder.Result result = SparseWalkingNetworkBuilder.build(tiles, offsets, ids);
		assertEquals(3, result.originalCount);
		assertEquals(2 * WorldPointUtil.distanceBetween(a, bridge), dijkstra(result, 0, 1));
		assertEquals(2 * WorldPointUtil.distanceBetween(bridge, c), dijkstra(result, 1, 2));
		// 0 and 2 are never members of the same component's tree, but the bridge site is a
		// vertex shared by both trees, so a path can still transit through it.
		assertEquals(2 * WorldPointUtil.distanceBetween(a, c), dijkstra(result, 0, 2));
	}

	@Test
	public void buildIsDeterministic()
	{
		int[] xs = {1000, 1003, 1003, 998, 1050, 1000, 1200, 1007, 995, 1300};
		int[] ys = {1000, 1000, 1004, 990, 1050, 1200, 900, 1002, 1500, 700};
		int[] tiles = new int[xs.length];
		for (int i = 0; i < xs.length; i++)
		{
			tiles[i] = WorldPointUtil.packWorldPoint(xs[i], ys[i], 0);
		}
		int[] offsets = new int[tiles.length + 1];
		int[] ids = new int[tiles.length];
		for (int i = 0; i < tiles.length; i++)
		{
			offsets[i + 1] = i + 1;
			ids[i] = 0;
		}
		SparseWalkingNetworkBuilder.Result first = SparseWalkingNetworkBuilder.build(tiles, offsets, ids);
		SparseWalkingNetworkBuilder.Result second = SparseWalkingNetworkBuilder.build(tiles, offsets, ids);
		assertEquals(first.originalCount, second.originalCount);
		assertEquals(first.vertexCount, second.vertexCount);
		assertEquals(first.steinerCount, second.steinerCount);
		assertEquals(first.undirectedEdgeCount, second.undirectedEdgeCount);
		assertArrayEquals(first.offsets, second.offsets);
		assertArrayEquals(first.destinations, second.destinations);
		assertArrayEquals(first.weights, second.weights);
	}

	private static void assertArrayEquals(int[] expected, int[] actual)
	{
		assertTrue("expected " + Arrays.toString(expected) + " but was " + Arrays.toString(actual),
			Arrays.equals(expected, actual));
	}

	/** Plain O(V^2) Dijkstra over the built CSR graph, for small test graphs only. */
	private static int dijkstra(SparseWalkingNetworkBuilder.Result net, int source, int target)
	{
		int[] dist = new int[net.vertexCount];
		Arrays.fill(dist, INF);
		dist[source] = 0;
		boolean[] settled = new boolean[net.vertexCount];
		for (int iteration = 0; iteration < net.vertexCount; iteration++)
		{
			int best = -1;
			int bestDist = INF;
			for (int v = 0; v < net.vertexCount; v++)
			{
				if (!settled[v] && dist[v] < bestDist)
				{
					bestDist = dist[v];
					best = v;
				}
			}
			if (best < 0)
			{
				break;
			}
			settled[best] = true;
			for (int edge = net.offsets[best]; edge < net.offsets[best + 1]; edge++)
			{
				int next = net.destinations[edge];
				int candidate = dist[best] + net.weights[edge];
				if (candidate < dist[next])
				{
					dist[next] = candidate;
				}
			}
		}
		return dist[target];
	}
}
