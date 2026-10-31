package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import shortestpath.transport.ResourceLoader;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * The component-layer path ({@link RoutingStaticBuilder#generate}, the serialized bytes,
 * {@link ComponentLayer#read}, {@link RoutingStaticLoader}) must produce the {@link RoutingStatic}
 * that the single-pass {@link RoutingStaticBuilder#build} derives, on real data and on worlds
 * covering the rules a whole map cannot isolate.
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

	private static void assertSameDerivation(CollisionMap world, Map<Integer, Set<Transport>> transports,
		Set<Integer> banks, int[] cuts, int seed) throws IOException
	{
		RoutingStatic built = RoutingStaticBuilder.build(world, transports, banks, cuts, seed).routingStatic;
		assertRoutingStaticEquals(built, viaLayer(world, transports, banks, cuts, seed), true);
	}

	@Test
	public void separatingCutAndItsReversedDuplicateMatchTheBuilder() throws Exception
	{
		assertSameDerivation(chainWorld(), Map.of(), Set.of(), cuts(pair(B, C), pair(C, B)), A);
	}

	@Test
	public void seasonalReachabilityMatchesTheBuilder() throws Exception
	{
		Map<Integer, Set<Transport>> transports = transports(transport(A2, B1, TransportType.SEASONAL_TRANSPORTS));
		assertSameDerivation(seasonalWorld(), transports, Set.of(B3), new int[0], A1);
		RoutingStatic loaded = viaLayer(seasonalWorld(), transports, Set.of(B3), new int[0], A1);
		assertTrue(loaded.searchIndex(B3) >= 0);
		assertEquals(-1, loaded.searchIndex(C1));
	}

	@Test
	public void transportBetweenUnreachableComponentsStillCountsItsComponents() throws Exception
	{
		Map<Integer, Set<Transport>> transports = transports(transport(B1, C1, TransportType.SEASONAL_TRANSPORTS));
		assertSameDerivation(seasonalWorld(), transports, Set.of(), new int[0], A1);
		RoutingStatic loaded = viaLayer(seasonalWorld(), transports, Set.of(), new int[0], A1);
		assertEquals(2, loaded.searchTileCount());
		assertEquals(4, loaded.routingComponentCount());
	}

	@Test
	public void cutOnUnreachableLandMatchesTheBuilder() throws Exception
	{
		assertSameDerivation(seasonalWorld(), Map.of(), Set.of(), cuts(pair(C1, C2)), A1);
		RoutingStatic loaded = viaLayer(seasonalWorld(), Map.of(), Set.of(), cuts(pair(C1, C2)), A1);
		assertEquals(1, loaded.crossingCount());
		assertEquals(4, loaded.routingComponentCount());
	}

	@Test
	public void blockedGlobalTeleportEndpointJoiningTwoIslandsMatchesTheBuilder() throws Exception
	{
		int blocked = tile(39, 20);
		Map<Integer, int[]> adjacency = SyntheticCollisionMap.symmetricAdjacency(
			new int[][] {{A1, A2}, {B1, B2}, {B2, B3}, {C1, C2}});
		adjacency.put(blocked, new int[] {B1, C1});
		SyntheticCollisionMap world = new SyntheticCollisionMap(Set.of(A1, A2, B1, B2, B3, C1, C2), adjacency, 1);
		Map<Integer, Set<Transport>> transports =
			transports(transport(WorldPointUtil.UNDEFINED, blocked, TransportType.SEASONAL_TRANSPORTS));
		assertSameDerivation(world, transports, Set.of(C2), new int[0], A1);
		assertEquals(7, viaLayer(world, transports, Set.of(C2), new int[0], A1).searchTileCount());
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
		assertSameDerivation(world, transports, Set.of(), new int[0], origin);

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
	public void deadCutEndpointsOnUnreachableLandAreTheOnlyDifference() throws Exception
	{
		// A cut that is not a walking edge is dropped when the layer is generated, so land that only that
		// cut's endpoint reaches is not a candidate there; the count of routing components is the one
		// thing that can differ.
		int[] cuts = cuts(pair(C1, tile(53, 20)));
		RoutingStatic built = RoutingStaticBuilder.build(seasonalWorld(), Map.of(), Set.of(), cuts, A1).routingStatic;
		RoutingStatic loaded = viaLayer(seasonalWorld(), Map.of(), Set.of(), cuts, A1);

		assertEquals(built.routingComponentCount(), loaded.routingComponentCount() + 1);
		assertRoutingStaticEquals(built, loaded, false);
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
			org.junit.Assert.fail("a walkable tile of the layer is blocked in the map");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains("stale"));
		}
	}

	@Test
	public void crossingOutsideTheLayerIsRejected() throws Exception
	{
		ComponentLayer layer = new ComponentLayer(new int[] {A}, new char[] {1}, new int[] {1}, new int[] {A, C});
		try
		{
			RoutingStaticLoader.build(chainWorld(), Map.of(), Set.of(), layer, A);
			org.junit.Assert.fail("the crossing endpoint is not covered");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains("stale"));
		}
	}

	@Test
	public void committedMapAndCutsMatchTheBuilderOnRealData() throws Exception
	{
		CollisionMap collision = new CollisionMap(SplitFlagMap.fromResources());
		Map<Integer, Set<Transport>> transports = ResourceLoader.transports();
		Set<Integer> banks = ResourceLoader.destinations().get("bank");
		RoutingCuts cuts = RoutingCuts.loadFromResources();
		assertTrue("the committed cuts must load", cuts.cutCount() > 0);

		RoutingStaticBuilder.Result built = RoutingStaticBuilder.build(collision, transports, banks, cuts.pairs());
		byte[] bytes = ComponentLayerWriter.write(RoutingStaticBuilder.generate(collision, cuts.pairs()));
		System.out.println("component layer: " + bytes.length + " bytes");
		ComponentLayer layer = ComponentLayer.read(new ByteArrayInputStream(bytes));
		RoutingStatic loaded = RoutingStaticLoader.build(collision, transports, banks, layer, RoutingStaticLoader.SEED_TILE);

		assertRoutingStaticEquals(built.routingStatic, loaded, true);
	}

	private static void assertRoutingStaticEquals(RoutingStatic left, RoutingStatic right, boolean componentCount)
	{
		assertEquals(left.searchTileCount(), right.searchTileCount());
		for (int i = 0; i < left.searchTileCount(); i++)
		{
			assertEquals(left.searchTile(i), right.searchTile(i));
			assertEquals(left.routingComponent(i), right.routingComponent(i));
			assertEquals(left.walkingMask(i), right.walkingMask(i));
			assertEquals(left.northNode(i), right.northNode(i));
			assertEquals(left.southNode(i), right.southNode(i));
		}
		assertEquals(left.siteCount(), right.siteCount());
		for (int i = 0; i < left.siteCount(); i++)
		{
			assertEquals(left.siteTile(i), right.siteTile(i));
			assertArrayEquals(left.siteComponents(i), right.siteComponents(i));
		}
		if (componentCount) assertEquals(left.routingComponentCount(), right.routingComponentCount());
		for (int c = 0; c < Math.min(left.routingComponentCount(), right.routingComponentCount()); c++)
			assertArrayEquals(left.componentSites(c), right.componentSites(c));
		assertEquals(left.reachableBankCount(), right.reachableBankCount());
		for (int i = 0; i < left.reachableBankCount(); i++)
			assertEquals(left.reachableBankTile(i), right.reachableBankTile(i));
		assertEquals(left.crossingCount(), right.crossingCount());
		for (int i = 0; i < left.crossingCount(); i++)
		{
			assertEquals(left.crossingFromSite(i), right.crossingFromSite(i));
			assertEquals(left.crossingToSite(i), right.crossingToSite(i));
			assertEquals(left.crossingCost(i), right.crossingCost(i));
		}
		assertEquals(left.sparseVertexCount(), right.sparseVertexCount());
		assertEquals(left.sparseOriginalCount(), right.sparseOriginalCount());
		assertEquals(left.sparseSteinerCount(), right.sparseSteinerCount());
		assertEquals(left.sparseAdjacencyCount(), right.sparseAdjacencyCount());
		for (int i = 0; i < left.sparseAdjacencyCount(); i++)
		{
			assertEquals(left.sparseDestination(i), right.sparseDestination(i));
			assertEquals(left.sparseWeight(i), right.sparseWeight(i));
		}
		assertEquals(left.exactWalks().exactComponentCount(), right.exactWalks().exactComponentCount());
		assertEquals(left.exactWalks().edgeCount(), right.exactWalks().edgeCount());
	}
}
