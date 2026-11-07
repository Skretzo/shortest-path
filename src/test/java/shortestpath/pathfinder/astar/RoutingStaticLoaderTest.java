package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * The contract of {@link RoutingStaticLoader}: what it derives from a serialized component layer
 * ({@link RoutingStaticBuilder#generate}, {@link ComponentLayerWriter#write}, {@link ComponentLayer#read})
 * and the loaded collision map, and when it refuses the layer. The full-world identity is pinned by the
 * freshness test, the real-data builder test and the routing static digest.
 */
public class RoutingStaticLoaderTest
{
	private static int tile(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	private static Transport transport(int origin, int destination, TransportType type)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination).type(type).duration(1).build();
	}

	private static Map<Integer, Set<Transport>> transports(Transport... transports)
	{
		Map<Integer, Set<Transport>> map = new HashMap<>();
		for (Transport transport : transports)
			map.computeIfAbsent(transport.getOrigin(), k -> new HashSet<>()).add(transport);
		return map;
	}

	private static int[] cuts(int[]... pairs)
	{
		int[] result = new int[pairs.length * 2];
		for (int i = 0; i < pairs.length; i++)
		{
			result[2 * i] = pairs[i][0];
			result[2 * i + 1] = pairs[i][1];
		}
		return result;
	}

	private static int[] pair(int a, int b)
	{
		return new int[] {a, b};
	}

	private static final int A = tile(10, 10);
	private static final int B = tile(11, 10);
	private static final int C = tile(12, 10);

	private static SyntheticCollisionMap chainWorld()
	{
		return new SyntheticCollisionMap(Set.of(A, B, C),
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{A, B}, {B, C}}), 1);
	}

	// A1-A2 holds the seed, B1-B2-B3 is reached only by a seasonal transport from A2, C1-C2 by nothing.
	private static final int A1 = tile(30, 20), A2 = tile(31, 20);
	private static final int B1 = tile(40, 20), B2 = tile(41, 20), B3 = tile(42, 20);
	private static final int C1 = tile(50, 20), C2 = tile(51, 20);

	private static SyntheticCollisionMap seasonalWorld()
	{
		return new SyntheticCollisionMap(Set.of(A1, A2, B1, B2, B3, C1, C2),
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{A1, A2}, {B1, B2}, {B2, B3}, {C1, C2}}), 1);
	}

	private static ComponentLayer layerOf(CollisionMap world, int[] cuts) throws IOException
	{
		return ComponentLayer.read(new ByteArrayInputStream(ComponentLayerWriter.write(RoutingStaticBuilder.generate(world, cuts))));
	}

	private static RoutingStatic viaLayer(CollisionMap world, Map<Integer, Set<Transport>> transports,
		Set<Integer> banks, int[] cuts, int seed) throws IOException
	{
		return RoutingStaticLoader.build(world, transports, banks, layerOf(world, cuts), seed);
	}

	@Test
	public void separatingCutAndItsReversedDuplicateBecomeOneCrossing() throws Exception
	{
		RoutingStatic loaded = viaLayer(chainWorld(), Map.of(), Set.of(), cuts(pair(B, C), pair(C, B)), A);

		assertEquals(3, loaded.searchTileCount());
		assertEquals(1, loaded.crossingCount());
		assertEquals(3, loaded.routingComponentCount());
		assertTrue(loaded.routingComponent(loaded.searchIndex(A)) == loaded.routingComponent(loaded.searchIndex(B)));
		assertTrue(loaded.routingComponent(loaded.searchIndex(B)) != loaded.routingComponent(loaded.searchIndex(C)));
	}

	@Test
	public void seasonalTransportMakesItsDestinationIslandSearchable() throws Exception
	{
		Map<Integer, Set<Transport>> transports = transports(transport(A2, B1, TransportType.SEASONAL_TRANSPORTS));
		RoutingStatic loaded = viaLayer(seasonalWorld(), transports, Set.of(B3), new int[0], A1);

		assertEquals(5, loaded.searchTileCount());
		assertTrue(loaded.searchIndex(B3) >= 0);
		assertEquals(-1, loaded.searchIndex(C1));
		assertEquals(1, loaded.reachableBankCount());
	}

	@Test
	public void transportBetweenUnreachableIslandsKeepsTheirComponentsButNotTheirTiles() throws Exception
	{
		Map<Integer, Set<Transport>> transports = transports(transport(B1, C1, TransportType.SEASONAL_TRANSPORTS));
		RoutingStaticBuilder.Result result =
			RoutingStaticBuilder.build(seasonalWorld(), transports, Set.of(), new int[0], A1);

		assertEquals(3, result.diagnostics.routingComponentCount);
		assertEquals(4, result.routingStatic.routingComponentCount());
		assertEquals(2, result.routingStatic.searchTileCount());
		assertEquals(-1, result.routingStatic.searchIndex(B1));
		assertEquals(-1, result.routingStatic.searchIndex(C1));
	}

	@Test
	public void cutOnUnreachableLandKeepsItsCrossing() throws Exception
	{
		RoutingStatic loaded = viaLayer(seasonalWorld(), Map.of(), Set.of(), cuts(pair(C1, C2)), A1);

		assertEquals(1, loaded.crossingCount());
		assertEquals(4, loaded.routingComponentCount());
		assertEquals(2, loaded.searchTileCount());
	}

	@Test
	public void blockedGlobalTeleportEndpointAttachesBothIslandsItBorders() throws Exception
	{
		int blocked = tile(39, 20);
		Map<Integer, int[]> adjacency = SyntheticCollisionMap.symmetricAdjacency(
			new int[][] {{A1, A2}, {B1, B2}, {B2, B3}, {C1, C2}});
		adjacency.put(blocked, new int[] {B1, C1});
		SyntheticCollisionMap world = new SyntheticCollisionMap(Set.of(A1, A2, B1, B2, B3, C1, C2), adjacency, 1);
		Map<Integer, Set<Transport>> transports =
			transports(transport(WorldPointUtil.UNDEFINED, blocked, TransportType.SEASONAL_TRANSPORTS));

		RoutingStatic loaded = viaLayer(world, transports, Set.of(C2), new int[0], A1);

		assertEquals(7, loaded.searchTileCount());
		assertEquals(1, loaded.reachableBankCount());
	}

	@Test
	public void verticalNeighboursDoNotWrapAtTheEdgesOfAPlaneOrBetweenPlanes() throws Exception
	{
		int top = WorldPointUtil.packWorldPoint(10, 32767, 0);
		int bottom = WorldPointUtil.packWorldPoint(10, 0, 1);
		int origin = WorldPointUtil.packWorldPoint(10, 0, 0);
		int lowerTwo = WorldPointUtil.packWorldPoint(10, 5, 2);
		int upperTwo = WorldPointUtil.packWorldPoint(10, 6, 2);
		int highest = WorldPointUtil.packWorldPoint(10, 32767, 3);
		// Every tile gets an eastern partner, since a tile with no walking edge has no walking mask.
		Set<Integer> tiles = new HashSet<>(Set.of(top, bottom, origin, lowerTwo, upperTwo, highest));
		int[][] edges = new int[5][];
		int edge = 0;
		for (int tile : new int[] {top, bottom, origin, highest})
		{
			int partner = WorldPointUtil.packWorldPoint(11, WorldPointUtil.unpackWorldY(tile),
				WorldPointUtil.unpackWorldPlane(tile));
			tiles.add(partner);
			edges[edge++] = pair(tile, partner);
		}
		edges[edge] = pair(lowerTwo, upperTwo);
		SyntheticCollisionMap world = new SyntheticCollisionMap(tiles, SyntheticCollisionMap.symmetricAdjacency(edges), 4)
		{
			@Override
			public SplitFlagMap.RegionExtent regionExtent()
			{
				return new SplitFlagMap.RegionExtent(0, 0, 0, 511);
			}

			@Override
			public byte getRegionPlaneCounts(int regionIndex)
			{
				return 4;
			}
		};
		Transport[] globals = new Transport[tiles.size()];
		int next = 0;
		for (int tile : tiles)
			globals[next++] = transport(WorldPointUtil.UNDEFINED, tile, TransportType.SEASONAL_TRANSPORTS);
		Map<Integer, Set<Transport>> transports = transports(globals);

		RoutingStatic loaded = viaLayer(world, transports, Set.of(), new int[0], origin);
		assertEquals(tiles.size(), loaded.searchTileCount());
		assertEquals(-1, loaded.northNode(loaded.searchIndex(top)));
		assertEquals(-1, loaded.southNode(loaded.searchIndex(bottom)));
		assertEquals(-1, loaded.northNode(loaded.searchIndex(highest)));
		assertEquals(-1, loaded.southNode(loaded.searchIndex(origin)));
		assertEquals(loaded.searchIndex(upperTwo), loaded.northNode(loaded.searchIndex(lowerTwo)));
		assertEquals(loaded.searchIndex(lowerTwo), loaded.southNode(loaded.searchIndex(upperTwo)));
	}

	@Test
	public void cutThatIsNotAWalkingEdgeOnUnattachedLandAddsNoRoutingComponent() throws Exception
	{
		// Routing components count the ones a transport, the seed or a crossing attaches to, so the endpoint
		// of a cut that is dropped when the layer is generated does not make its island a candidate.
		RoutingStatic without = viaLayer(seasonalWorld(), Map.of(), Set.of(), new int[0], A1);
		RoutingStatic dead = viaLayer(seasonalWorld(), Map.of(), Set.of(), cuts(pair(C1, tile(53, 20))), A1);

		assertEquals(2, without.routingComponentCount());
		assertEquals(without.routingComponentCount(), dead.routingComponentCount());
		assertEquals(0, dead.crossingCount());
		assertEquals(2, dead.searchTileCount());
	}

	@Test(expected = IllegalStateException.class)
	public void missingStructuralReachabilitySeedIsAHardError() throws Exception
	{
		viaLayer(chainWorld(), Map.of(), Set.of(), new int[0], tile(999, 999));
	}

	@Test
	public void layerThatNoLongerFitsTheCollisionMapIsRejected() throws Exception
	{
		Map<Integer, Set<Transport>> transports = transports(transport(A2, B1, TransportType.SEASONAL_TRANSPORTS));
		ComponentLayer layer = layerOf(seasonalWorld(), new int[0]);
		SyntheticCollisionMap shrunk = new SyntheticCollisionMap(Set.of(A1, A2, B1, B2, C1, C2),
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{A1, A2}, {B1, B2}, {C1, C2}}), 1);
		try
		{
			RoutingStaticLoader.build(shrunk, transports, Set.of(), layer, A1);
			fail("a walkable tile of the layer is blocked in the map");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains("stale"));
		}
	}

	@Test
	public void layerWhoseRunLengthsOverflowTheTileCountIsRejected() throws Exception
	{
		// 40,000 maximal runs hold more tiles than an int can count; none of them has to match the map.
		int runs = 40000;
		int[] starts = new int[runs];
		char[] lengths = new char[runs];
		int[] ids = new int[runs];
		for (int i = 0; i < runs; i++)
		{
			starts[i] = i * Character.MAX_VALUE;
			lengths[i] = Character.MAX_VALUE;
			ids[i] = 1;
		}
		ComponentLayer layer = new ComponentLayer(starts, lengths, ids, new int[0]);
		try
		{
			RoutingStaticLoader.build(chainWorld(), Map.of(), Set.of(), layer, 0);
			fail("the run lengths add up to more search tiles than the loader can hold");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains("stale"));
		}
	}

	@Test
	public void crossingThatIsNotAWalkingEdgeIsRejected() throws Exception
	{
		ComponentLayer covered = layerOf(chainWorld(), new int[0]);
		// A and C are both in the layer, but B lies between them, so no walking edge joins them.
		ComponentLayer layer = new ComponentLayer(covered.starts, covered.lengths, covered.ids, new int[] {A, C});
		try
		{
			RoutingStaticLoader.build(chainWorld(), Map.of(), Set.of(), layer, A);
			fail("the crossing is not a walking edge of the collision map");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains("walking edge"));
		}
	}

	@Test
	public void crossingOutsideTheLayerIsRejected() throws Exception
	{
		ComponentLayer layer = new ComponentLayer(new int[] {A}, new char[] {1}, new int[] {1}, new int[] {A, C});
		try
		{
			RoutingStaticLoader.build(chainWorld(), Map.of(), Set.of(), layer, A);
			fail("the crossing endpoint is not covered");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains("stale"));
		}
	}
}
