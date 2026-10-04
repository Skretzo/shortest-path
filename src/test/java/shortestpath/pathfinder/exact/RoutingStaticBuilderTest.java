package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Synthetic-world unit tests for {@link RoutingStaticBuilder}, covering rules a whole-map test
 * can't isolate: cut validity, non-separating cuts, transport-only reachability,
 * seasonal reachability and pruning, the empty-cut-list case, and determinism.
 */
public class RoutingStaticBuilderTest
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
		{
			map.computeIfAbsent(transport.getOrigin(), k -> new HashSet<>()).add(transport);
		}
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

	// ---- A three-tile chain A-B-C, used by several cut-handling tests ----

	private static final int A = tile(10, 10);
	private static final int B = tile(11, 10);
	private static final int C = tile(12, 10);

	private static SyntheticCollisionMap chainWorld()
	{
		Set<Integer> walkable = Set.of(A, B, C);
		Map<Integer, int[]> adjacency =
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{A, B}, {B, C}});
		return new SyntheticCollisionMap(walkable, adjacency, 1);
	}

	@Test
	public void noCutsProducesOneNaturalAndOneRoutingComponent() throws Exception
	{
		RoutingStaticBuilder.Result result =
			RoutingStaticBuilder.build(chainWorld(), Map.of(), Set.of(), new int[0], A);

		assertEquals(1, result.diagnostics.naturalComponentCount);
		assertEquals(1, result.diagnostics.routingComponentCount);
		assertEquals(0, result.diagnostics.crossingCount);
		assertEquals(3, result.routingStatic.searchTileCount());
		// With zero cuts, routing ids must exactly track natural ids (same flood fill, no blocking).
		assertEquals(result.diagnostics.naturalComponentCount, result.diagnostics.routingComponentCount);
	}

	@Test
	public void validSeparatingCutSplitsIntoTwoRoutingComponentsAndOneCrossing() throws Exception
	{
		// A duplicate, reversed copy of the same cut must collapse (deduplicated, not double-counted).
		int[] cuts = cuts(pair(B, C), pair(C, B));
		RoutingStaticBuilder.Result result =
			RoutingStaticBuilder.build(chainWorld(), Map.of(), Set.of(), cuts, A);

		assertEquals(1, result.diagnostics.naturalComponentCount);
		assertEquals(2, result.diagnostics.routingComponentCount);
		assertEquals(1, result.diagnostics.duplicateCutCount);
		assertEquals(0, result.diagnostics.invalidCutCount);
		assertEquals(0, result.diagnostics.nonSeparatingCutCount);
		assertEquals(1, result.diagnostics.crossingCount);
		// Reachability is by NATURAL id, so the cut subdivides but never excludes: all 3 tiles remain.
		assertEquals(3, result.routingStatic.searchTileCount());

		RoutingStatic mine = result.routingStatic;
		assertEquals(1, mine.crossingCount());
		int from = mine.siteTile(mine.crossingFromSite(0));
		int to = mine.siteTile(mine.crossingToSite(0));
		assertEquals(B, from);
		assertEquals(C, to);
		assertEquals(1, mine.crossingCost(0));
	}

	@Test
	public void cutThatIsNotAWalkingEdgeIsIgnored() throws Exception
	{
		// A and C are not declared neighbours of each other (only A-B and B-C are).
		RoutingStaticBuilder.Result result =
			RoutingStaticBuilder.build(chainWorld(), Map.of(), Set.of(), cuts(pair(A, C)), A);

		assertEquals(1, result.diagnostics.invalidCutCount);
		assertEquals(0, result.diagnostics.crossingCount);
		assertEquals(1, result.diagnostics.routingComponentCount);
		assertEquals(3, result.routingStatic.searchTileCount());
	}

	@Test
	public void cutThatNoLongerSeparatesIsDroppedAsNonSeparating() throws Exception
	{
		// A 2x2 square walked as a 4-cycle: cutting one edge leaves every tile connected via the
		// other three.
		int e = tile(11, 11);
		int d = tile(10, 11);
		Set<Integer> walkable = Set.of(A, B, e, d);
		Map<Integer, int[]> adjacency =
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{A, B}, {B, e}, {e, d}, {d, A}});
		SyntheticCollisionMap world = new SyntheticCollisionMap(walkable, adjacency, 1);

		RoutingStaticBuilder.Result result = RoutingStaticBuilder.build(world, Map.of(), Set.of(), cuts(pair(A, B)), A);

		assertEquals(0, result.diagnostics.invalidCutCount);
		assertEquals(1, result.diagnostics.nonSeparatingCutCount);
		assertEquals(0, result.diagnostics.crossingCount);
		assertEquals(1, result.diagnostics.naturalComponentCount);
		assertEquals(1, result.diagnostics.routingComponentCount);
	}

	@Test
	public void transportOnlyReachableComponentBecomesReachable() throws Exception
	{
		int x1 = tile(30, 10);
		int y1 = tile(40, 10);
		Set<Integer> walkable = Set.of(x1, y1);
		SyntheticCollisionMap world = new SyntheticCollisionMap(walkable, Map.of(), 1);
		Map<Integer, Set<Transport>> transports = transports(transport(x1, y1, TransportType.TRANSPORT));

		RoutingStaticBuilder.Result result =
			RoutingStaticBuilder.build(world, transports, Set.of(y1), new int[0], x1);

		assertEquals(2, result.diagnostics.naturalComponentCount);
		assertEquals(2, result.diagnostics.reachableNaturalComponentCount);
		assertEquals(2, result.routingStatic.searchTileCount());
		assertEquals(2, result.routingStatic.siteCount());
		assertEquals(1, result.routingStatic.reachableBankCount());
		assertEquals(y1, result.routingStatic.reachableBankTile(0));
	}

	// ---- Seasonal reachability: A1-A2 holds the seed, B1-B2-B3 is reached only by a seasonal
	// transport from A2, and C1-C2 is reached by nothing. ----

	private static final int A1 = tile(30, 20), A2 = tile(31, 20);
	private static final int B1 = tile(40, 20), B2 = tile(41, 20), B3 = tile(42, 20);
	private static final int C1 = tile(50, 20), C2 = tile(51, 20);
	private static final Transport SEASONAL = transport(A2, B1, TransportType.SEASONAL_TRANSPORTS);

	private static SyntheticCollisionMap seasonalWorld()
	{
		return new SyntheticCollisionMap(Set.of(A1, A2, B1, B2, B3, C1, C2),
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{A1, A2}, {B1, B2}, {B2, B3}, {C1, C2}}), 1);
	}

	private static RoutingStatic seasonalStatic() throws Exception
	{
		return RoutingStaticBuilder.build(seasonalWorld(), transports(SEASONAL), Set.of(), new int[0], A1)
			.routingStatic;
	}

	@Test
	public void seasonalTransportKeepsItsDestinationComponent() throws Exception
	{
		RoutingStaticBuilder.Result result =
			RoutingStaticBuilder.build(seasonalWorld(), transports(SEASONAL), Set.of(), new int[0], A1);

		assertEquals(3, result.diagnostics.naturalComponentCount);
		assertEquals(2, result.diagnostics.reachableNaturalComponentCount);
		for (int tile : new int[] {A1, A2, B1, B2, B3})
			assertTrue(result.routingStatic.searchIndex(tile) >= 0);
	}

	@Test
	public void componentNothingReachesIsStillPruned() throws Exception
	{
		RoutingStatic mine = seasonalStatic();

		assertEquals(5, mine.searchTileCount());
		assertEquals(-1, mine.searchIndex(C1));
		assertEquals(-1, mine.searchIndex(C2));
	}

	@Test
	public void keptSeasonalComponentIsOnlyReachableWhenTheAccountHasTheTransport() throws Exception
	{
		RoutingStatic mine = seasonalStatic();

		ExactForwardSearch.Result without = search(mine, TransportAvailabilityFixture.of(), B3);
		ExactForwardSearch.Result with = search(mine, TransportAvailabilityFixture.of(SEASONAL), B3);

		assertFalse(without.reached());
		assertTrue(with.reached());
		// Walk to A2, take the transport, then walk on through B2 to B3.
		assertEquals(4, with.cost());
		assertEquals(java.util.List.of(A1, A2, B1, B2, B3), with.path().stream()
			.map(shortestpath.pathfinder.PathStep::getPackedPosition).collect(java.util.stream.Collectors.toList()));
	}

	private static ExactForwardSearch.Result search(RoutingStatic stat, TransportAvailability availability, int target)
	{
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(availability, TransportAvailabilityFixture.of(),
			false, Set.of(), 0, true, ignored -> 0);
		TargetOverlay overlay = new TargetOverlay(new SiteGraph(stat, account), seasonalWorld(), target);
		return ExactForwardSearch.search(overlay, PreparedHeuristic.prepare(overlay, ReverseLabels.compute(overlay)), A1);
	}

	@Test
	public void emptyCutListProducesNaturalComponentsAndValidates() throws Exception
	{
		RoutingStaticBuilder.Result result =
			RoutingStaticBuilder.build(chainWorld(), Map.of(), Set.of(), new int[0], A);

		// RoutingStatic.create() already ran full validation inside build(); reaching here means it
		// passed. Cross-check the natural/routing equivalence explicitly too.
		assertEquals(result.diagnostics.naturalComponentCount, result.diagnostics.routingComponentCount);
		assertEquals(0, result.diagnostics.crossingCount);
	}

	@Test(expected = IllegalStateException.class)
	public void missingStructuralReachabilitySeedIsAHardError() throws Exception
	{
		int unattachedSeed = tile(999, 999);
		RoutingStaticBuilder.build(chainWorld(), Map.of(), Set.of(), new int[0], unattachedSeed);
	}

	@Test
	public void derivationIsDeterministicAcrossTwoBuilds() throws Exception
	{
		int x1 = tile(30, 10);
		int y1 = tile(40, 10);
		Set<Integer> walkable = Set.of(A, B, C, x1, y1);
		Map<Integer, int[]> adjacency =
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{A, B}, {B, C}});

		Map<Integer, Set<Transport>> transports = transports(transport(x1, y1, TransportType.TRANSPORT));
		int[] cuts = cuts(pair(B, C));

		RoutingStaticBuilder.Result first = RoutingStaticBuilder.build(
			new SyntheticCollisionMap(walkable, adjacency, 1), transports, Set.of(y1), cuts, A);
		RoutingStaticBuilder.Result second = RoutingStaticBuilder.build(
			new SyntheticCollisionMap(walkable, adjacency, 1), transports, Set.of(y1), cuts, A);

		assertRoutingStaticEquals(first.routingStatic, second.routingStatic);
	}

	private static void assertRoutingStaticEquals(RoutingStatic left, RoutingStatic right)
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
			org.junit.Assert.assertArrayEquals(left.siteComponents(i), right.siteComponents(i));
		}
		assertEquals(left.routingComponentCount(), right.routingComponentCount());
		for (int c = 0; c < left.routingComponentCount(); c++)
		{
			org.junit.Assert.assertArrayEquals(left.componentSites(c), right.componentSites(c));
		}
		assertEquals(left.reachableBankCount(), right.reachableBankCount());
		for (int i = 0; i < left.reachableBankCount(); i++)
		{
			assertEquals(left.reachableBankTile(i), right.reachableBankTile(i));
		}
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
	}

	@Test
	public void walkingGraphExposesNaturalComponentsAndReachability()
	{
		int island = tile(20, 20);
		Set<Integer> walkable = Set.of(A, B, C, island);
		Map<Integer, int[]> adjacency = SyntheticCollisionMap.symmetricAdjacency(new int[][] {{A, B}, {B, C}});
		RoutingStaticBuilder.WalkingGraph graph =
			RoutingStaticBuilder.walkingGraph(new SyntheticCollisionMap(walkable, adjacency, 1), Map.of(), A);

		assertEquals(4, graph.tileCount());
		assertEquals(2, graph.naturalComponentCount());
		int a = graph.indexOf(A), b = graph.indexOf(B), c = graph.indexOf(C), i = graph.indexOf(island);
		assertEquals(-1, graph.indexOf(tile(30, 30)));
		assertEquals(graph.naturalComponent(a), graph.naturalComponent(c));
		assertTrue(graph.naturalComponent(a) != graph.naturalComponent(i));
		assertTrue(graph.isStructurallyReachable(graph.naturalComponent(a)));
		assertTrue(!graph.isStructurallyReachable(graph.naturalComponent(i)));
		int[] neighbours = graph.neighbours(b);
		java.util.Arrays.sort(neighbours);
		int[] expected = {a, c};
		java.util.Arrays.sort(expected);
		org.junit.Assert.assertArrayEquals(expected, neighbours);
		assertEquals(0, graph.neighbours(i).length);
	}
}
