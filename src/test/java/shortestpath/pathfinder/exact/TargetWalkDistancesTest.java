package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailabilityFixture;

/**
 * Covers {@link TargetWalkDistances}: the reverse-BFS walking bound a synthetic target's
 * attachment edges use instead of a bare Chebyshev distance. Earlier fixtures never ran it —
 * their synthetic targets were not search tiles ({@code search()} returned null) or their targets
 * were static sites — so these tests use a U-shaped corridor whose dead end is far closer in a
 * straight line than on foot:
 *
 * <pre>
 *   y=12:  T --+--+--+--+
 *   y=11:              |
 *   y=10:  B +--+--+--+
 * </pre>
 */
public class TargetWalkDistancesTest
{
	private static final int TARGET = tile(10, 12);
	private static final int BANK = tile(10, 10);
	/** A blocked nook east of the corridor, a stand-in for a site on a non-search tile. */
	private static final int POCKET_BANK = tile(15, 10);
	/** A blocked tile inside the bend, a stand-in for a non-search-tile target. */
	private static final int BLOCKED_TARGET = tile(11, 11);
	/** The corridor in walking-distance order from TARGET, so CORRIDOR[i] is i steps away. */
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

	private static SyntheticCollisionMap corridorCollision()
	{
		int[][] edges = new int[CORRIDOR.length - 1][];
		for (int i = 1; i < CORRIDOR.length; i++)
		{
			edges[i - 1] = new int[] {CORRIDOR[i - 1], CORRIDOR[i]};
		}
		Map<Integer, int[]> adjacency = SyntheticCollisionMap.symmetricAdjacency(edges);
		// One-way declarations for the blocked tiles: attachment lookups see the corridor as their
		// neighbour, without corridor tiles gaining an edge into a blocked tile.
		adjacency.put(POCKET_BANK, new int[] {tile(14, 10)});
		adjacency.put(BLOCKED_TARGET, new int[] {tile(11, 12), tile(12, 12)});
		return new SyntheticCollisionMap(new HashSet<>(Arrays.asList(
			Arrays.stream(CORRIDOR).boxed().toArray(Integer[]::new))), adjacency, 1);
	}

	private static RoutingStatic corridorStatic(boolean withCut) throws Exception
	{
		int[] cuts = withCut ? new int[] {tile(14, 10), tile(14, 11)} : new int[0];
		return RoutingStaticBuilder.build(corridorCollision(), Map.of(), Set.of(BANK, POCKET_BANK), cuts, BANK)
			.routingStatic;
	}

	private static int[] targetComponents(RoutingStatic stat)
	{
		return new int[] {stat.routingComponent(stat.searchIndex(TARGET))};
	}

	private static TargetOverlay overlay(RoutingStatic stat, int target)
	{
		return new TargetOverlay(new SiteGraph(stat, account()), corridorCollision(), target);
	}

	private static PreparedRoutingAccount account()
	{
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
			TransportAvailabilityFixture.of(), false, Set.of(), 0, true, ignored -> 0);
	}

	@Test
	public void searchRunsOnlyForSearchTileTargets() throws Exception
	{
		RoutingStatic stat = corridorStatic(false);

		assertNotNull(TargetWalkDistances.search(stat, TARGET, targetComponents(stat)));
		assertNull(TargetWalkDistances.search(stat, BLOCKED_TARGET, targetComponents(stat)));
	}

	@Test
	public void windingCorridorSitesGetWalkDistanceNotChebyshev() throws Exception
	{
		RoutingStatic stat = corridorStatic(false);
		TargetWalkDistances walk = TargetWalkDistances.search(stat, TARGET, targetComponents(stat));

		assertNotNull(walk);
		for (int i = 0; i < CORRIDOR.length; i++)
		{
			int chebyshev = WorldPointUtil.distanceBetween(TARGET, CORRIDOR[i]);
			assertEquals(Math.max(chebyshev, i), walk.bound(CORRIDOR[i], chebyshev));
		}
		// The far end of the corridor: straight-line distance 2, walking distance 10.
		assertEquals(10, walk.bound(BANK, 2));
		// A site on a blocked tile steps to its nearest reached search-tile neighbour first:
		// (14,11) at distance 5 bounds it at 6, above the Chebyshev 5.
		assertEquals(6, walk.bound(POCKET_BANK, WorldPointUtil.distanceBetween(TARGET, POCKET_BANK)));
	}

	@Test
	public void overlayAttachmentsUseTheWalkingBound() throws Exception
	{
		RoutingStatic stat = corridorStatic(false);
		TargetOverlay overlay = overlay(stat, TARGET);

		assertTrue(overlay.synthetic());
		assertEquals(2, overlay.attachmentCount());
		for (int i = 0; i < overlay.attachmentCount(); i++)
		{
			int site = stat.siteTile(overlay.attachmentSite(i));
			if (site == BANK)
			{
				assertEquals(10, overlay.attachmentCost(i));
			}
			else if (site == POCKET_BANK)
			{
				assertEquals(6, overlay.attachmentCost(i));
			}
			else
			{
				fail("unexpected attachment site " + site);
			}
		}
	}

	@Test
	public void blockedTargetKeepsChebyshevAttachments() throws Exception
	{
		RoutingStatic stat = corridorStatic(false);
		TargetOverlay overlay = overlay(stat, BLOCKED_TARGET);

		assertTrue(overlay.synthetic());
		assertEquals(2, overlay.attachmentCount());
		for (int i = 0; i < overlay.attachmentCount(); i++)
		{
			assertEquals(WorldPointUtil.distanceBetween(BLOCKED_TARGET, stat.siteTile(overlay.attachmentSite(i))),
				overlay.attachmentCost(i));
		}
	}

	@Test
	public void walkBoundStaysInsideTheTargetRoutingComponent() throws Exception
	{
		RoutingStatic stat = corridorStatic(true);
		TargetOverlay overlay = overlay(stat, TARGET);

		// The cut makes the bottom row a second routing component: only the (14,11) crossing
		// endpoint attaches to the target's component, at its real walking distance 5.
		assertEquals(1, overlay.attachmentCount());
		assertEquals(tile(14, 11), stat.siteTile(overlay.attachmentSite(0)));
		assertEquals(5, overlay.attachmentCost(0));

		// Tiles beyond the cut are never reached: their bound is the exhausted frontier's
		// distance (last reached distance 5, plus one).
		TargetWalkDistances walk = TargetWalkDistances.search(stat, TARGET, targetComponents(stat));
		assertEquals(6, walk.bound(BANK, WorldPointUtil.distanceBetween(TARGET, BANK)));
		assertEquals(6, walk.bound(POCKET_BANK, WorldPointUtil.distanceBetween(TARGET, POCKET_BANK)));
	}

	@Test
	public void raisedBoundsKeepTheSearchExact() throws Exception
	{
		List<Integer> corridor = Arrays.stream(CORRIDOR).boxed().collect(Collectors.toList());
		java.util.Collections.reverse(corridor);
		for (boolean cut : new boolean[] {false, true})
		{
			RoutingStatic stat = corridorStatic(cut);
			TargetOverlay overlay = overlay(stat, TARGET);

			ExactForwardSearch.Result result = ExactForwardSearch.search(overlay,
				PreparedHeuristic.prepare(overlay, ReverseLabels.compute(overlay)), BANK);

			assertTrue(result.reached());
			assertEquals(10, result.cost());
			assertEquals(corridor, result.path().stream()
				.map(PathStep::getPackedPosition).collect(Collectors.toList()));
			assertTrue(result.counters().heuristicCacheHits() > 0);
		}
	}
}
