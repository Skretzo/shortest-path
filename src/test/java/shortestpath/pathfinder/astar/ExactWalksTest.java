package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.TransportAvailabilityFixture;

/**
 * Covers {@link ExactWalks} on a U-shaped corridor whose ends are 2 tiles apart in a straight line
 * but 10 on foot, with banks (sites) at both ends and at the bend:
 *
 * <pre>
 *   y=13: P (blocked, off the corridor, used from C)
 *   y=12:  C --+--+--+--+
 *   y=11:              B
 *   y=10:  A --+--+--+--+
 * </pre>
 */
public class ExactWalksTest
{
	private static final int A = tile(10, 10);
	private static final int B = tile(14, 11);
	private static final int C = tile(10, 12);
	private static final int POCKET = tile(9, 13);
	private static final int[] CORRIDOR =
	{
		tile(10, 12), tile(11, 12), tile(12, 12), tile(13, 12), tile(14, 12),
		tile(14, 11),
		tile(14, 10), tile(13, 10), tile(12, 10), tile(11, 10), tile(10, 10)
	};

	private static int tile(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	private static SyntheticCollisionMap collision()
	{
		int[][] edges = new int[CORRIDOR.length - 1][];
		for (int i = 1; i < CORRIDOR.length; i++)
			edges[i - 1] = new int[] {CORRIDOR[i - 1], CORRIDOR[i]};
		Map<Integer, int[]> adjacency = SyntheticCollisionMap.symmetricAdjacency(edges);
		adjacency.put(POCKET, new int[] {C});
		return new SyntheticCollisionMap(new HashSet<>(Arrays.asList(
			Arrays.stream(CORRIDOR).boxed().toArray(Integer[]::new))), adjacency, 1);
	}

	private static RoutingStatic corridor() throws Exception
	{
		return RoutingStaticBuilder.build(collision(), Map.of(), Set.of(A, B, C, POCKET), new int[0], A).routingStatic;
	}

	private static int component(RoutingStatic stat)
	{
		return stat.routingComponent(stat.searchIndex(A));
	}

	/** The full pair table's entry for two site tiles. */
	private static int pair(RoutingStatic stat, int[][] table, int left, int right)
	{
		int[] sites = stat.componentSites(component(stat));
		return table[Arrays.binarySearch(sites, stat.siteIndex(left))][Arrays.binarySearch(sites, stat.siteIndex(right))];
	}

	@Test
	public void pairDistancesAreWalksNotChebyshev() throws Exception
	{
		RoutingStatic stat = corridor();
		int[][] table = ExactWalks.pairDistances(stat, component(stat));
		assertEquals(10, pair(stat, table, A, C));
		assertEquals(5, pair(stat, table, A, B));
		assertEquals(5, pair(stat, table, B, C));
		// The blocked pocket is used from C, its only neighbour on the corridor.
		assertEquals(10, pair(stat, table, A, POCKET));
		assertEquals(5, pair(stat, table, B, POCKET));
	}

	@Test
	public void edgesThroughAThirdSiteAreLeftOut() throws Exception
	{
		RoutingStatic stat = corridor();
		ExactWalks walks = stat.exactWalks();
		assertTrue(walks.isExact(component(stat)));
		int[] edges = walks.edges(component(stat));
		for (int i = 0; i < edges.length; i += 3)
		{
			Set<Integer> ends = Set.of(stat.siteTile(edges[i]), stat.siteTile(edges[i + 1]));
			assertFalse("A-C is A-B plus B-C", ends.equals(Set.of(A, C)));
		}
		// The component's only sites are joined without Steiner vertices.
		assertEquals(0, stat.sparseSteinerCount());
	}

	@Test
	public void componentsWithTooManySitesKeepTheirTree() throws Exception
	{
		RoutingStatic stat = corridor();
		assertFalse(ExactWalks.build(stat, 3).isExact(component(stat)));
		assertTrue(ExactWalks.build(stat, 4).isExact(component(stat)));
	}

	@Test
	public void reverseLabelsWalkTheCorridor() throws Exception
	{
		RoutingStatic stat = corridor();
		TargetOverlay overlay = new TargetOverlay(new SiteGraph(stat, account()), collision(), C);
		ReverseLabels sparse = ReverseLabels.compute(overlay);
		assertTrue(sparse.sparse());
		assertEquals(10, sparse.label(stat.siteIndex(A), BankVisitState.CARRIED));
		assertArrayEquals(ReverseLabels.computeClique(overlay).labels(), sparse.labels());
	}

	private static PreparedRoutingAccount account()
	{
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
			TransportAvailabilityFixture.of(), false, Set.of(), 0, true, ignored -> 0);
	}
}
