package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class ExactForwardSearchHotPathTest
{
	private static final int OUTSIDE = WorldPointUtil.packWorldPoint(3200, 3679, 0);
	private static final int TARGET = WorldPointUtil.packWorldPoint(3202, 3679, 0);
	private static final int DEEP_WILDERNESS = WorldPointUtil.packWorldPoint(3200, 3760, 0);

	@Test
	public void nodeStatesAndReachableBankIndexesMatchTileData() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.createT4Walking();
		TargetOverlay target = target(stat, account(), RoutingStaticTestFixture.C);
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(target, ReverseLabels.compute(target));
		for (int node = 0; node < stat.searchTileCount(); node++)
		{
			assertEquals(stat.searchIndex(stat.searchTile(node)) * 2,
				ExactForwardSearch.stateForNode(node, false));
			assertEquals(stat.searchIndex(stat.searchTile(node)) * 2 + 1,
				ExactForwardSearch.stateForNode(node, true));
			assertEquals(isBankTile(stat, stat.searchTile(node)), stat.isBankNode(node));
			for (boolean banked : new boolean[] {false, true})
				assertEquals(heuristic.estimate(stat.searchTile(node), banked,
					new int[] {stat.routingComponent(node)}), heuristic.estimateBaseNode(stat.searchTile(node),
					banked, stat.routingComponent(node)));
		}
		for (int site = 0; site < stat.siteCount(); site++)
			assertEquals(isBankTile(stat, stat.siteTile(site)), stat.isBankSite(site));
	}

	@Test
	public void walkingAndLocalTransportKeepExactStatesCostsAndRoute() throws Exception
	{
		RoutingStatic walking = RoutingStaticTestFixture.createT4Walking();
		ExactForwardSearch.Result walked = search(walking, account(), RoutingStaticTestFixture.A,
			RoutingStaticTestFixture.C);
		assertEquals(2, walked.cost());
		assertEquals(List.of(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK,
			RoutingStaticTestFixture.C), tiles(walked.path()));
		assertTrue(walked.counters().walkingPqPushes() > 0);

		RoutingStatic transported = RoutingStaticTestFixture.create();
		ExactForwardSearch.Result moved = search(transported,
			account(local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.D, 7)),
			RoutingStaticTestFixture.A, RoutingStaticTestFixture.D);
		assertEquals(7, moved.cost());
		assertEquals(List.of(RoutingStaticTestFixture.A, RoutingStaticTestFixture.D), tiles(moved.path()));
		assertEquals(1, moved.counters().localTransportPqPushes());
	}

	@Test
	public void cancelledSearchKeepsTheRouteToTheClosestPoppedTile() throws Exception
	{
		TargetOverlay target = target(RoutingStaticTestFixture.createT4Walking(), account(), RoutingStaticTestFixture.C);
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(target, ReverseLabels.compute(target));
		int[] checks = {0};

		ExactForwardSearch.Result result = ExactForwardSearch.search(target, heuristic, RoutingStaticTestFixture.A,
			() -> ++checks[0] >= 4);

		assertTrue(result.cancelled());
		assertEquals(List.of(RoutingStaticTestFixture.A), tiles(result.path()));
		assertEquals(List.of(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK), tiles(result.closestPath()));
		assertEquals(1, result.closestCost());
	}

	@Test
	public void unreachableTargetKeepsTheRouteToTheClosestReachableTile() throws Exception
	{
		ExactForwardSearch.Result result = search(RoutingStaticTestFixture.createT4Walking(), account(),
			RoutingStaticTestFixture.A, RoutingStaticTestFixture.D);

		assertFalse(result.reached());
		assertEquals(List.of(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.C),
			tiles(result.closestPath()));
		assertEquals(2, result.closestCost());
	}

	@Test
	public void restrictedStartUsesCapThenNormalHeuristicAfterGlobalActivation() throws Exception
	{
		RoutingStatic stat = wildernessStatic();
		PreparedRoutingAccount account = account(local(DEEP_WILDERNESS, OUTSIDE, 5), global(TARGET, 1));
		TargetOverlay target = target(stat, account, TARGET);
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(target, ReverseLabels.compute(target));

		ExactForwardSearch.Result oracle = ExactForwardSearch.search(target, heuristic, DEEP_WILDERNESS, false);
		ExactForwardSearch.Result result = ExactForwardSearch.search(target, heuristic, DEEP_WILDERNESS, true);

		assertEquals(oracle.cost(), result.cost());
		assertEquals(6, result.cost());
		assertEquals(List.of(DEEP_WILDERNESS, OUTSIDE, TARGET), tiles(result.path()));
		assertEquals("NONE", result.counters().initialCapability());
		assertFalse(result.counters().normalHeuristicEnabledAtStart());
		assertTrue(result.counters().restrictedHeuristicStates() > 0);
		assertTrue(result.counters().normalHeuristicStates() > 0);
	}

	@Test
	public void wildernessPositionDoesNotDisableHeuristicWhenAccountHasNoGlobals() throws Exception
	{
		RoutingStatic stat = wildernessStatic();
		ExactForwardSearch.Result result = search(stat, account(local(DEEP_WILDERNESS, TARGET, 4)),
			DEEP_WILDERNESS, TARGET);

		assertEquals(4, result.cost());
		assertTrue(result.counters().normalHeuristicEnabledAtStart());
		assertEquals(0, result.counters().restrictedHeuristicStates());
		assertTrue(result.counters().normalHeuristicStates() > 0);
	}

	private static boolean isBankTile(RoutingStatic stat, int tile)
	{
		for (int i = 0; i < stat.reachableBankCount(); i++)
			if (stat.reachableBankTile(i) == tile) return true;
		return false;
	}

	private static List<Integer> tiles(List<PathStep> path)
	{
		return path.stream().map(PathStep::getPackedPosition).collect(java.util.stream.Collectors.toList());
	}

	private static ExactForwardSearch.Result search(RoutingStatic stat, PreparedRoutingAccount account,
		int start, int destination)
	{
		TargetOverlay target = target(stat, account, destination);
		return ExactForwardSearch.search(target, PreparedHeuristic.prepare(target, ReverseLabels.compute(target)), start);
	}

	private static TargetOverlay target(RoutingStatic stat, PreparedRoutingAccount account, int destination)
	{
		return new TargetOverlay(new SiteGraph(stat, account), emptyCollision(), destination);
	}

	private static PreparedRoutingAccount account(Transport... transports)
	{
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(transports),
			TransportAvailabilityFixture.of(), false, java.util.Set.of(), true, ignored -> 0);
	}

	private static Transport local(int origin, int destination, int cost)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination)
			.type(TransportType.TRANSPORT).duration(cost).build();
	}

	private static Transport global(int destination, int cost)
	{
		return new Transport.TransportBuilder().destination(destination)
			.type(TransportType.TELEPORTATION_ITEM).duration(cost).build();
	}

	private static RoutingStatic wildernessStatic() throws Exception
	{
		return RoutingStatic.create(new int[0], new int[0], new byte[0], new int[0], new int[0],
			new int[] {OUTSIDE, TARGET, DEEP_WILDERNESS}, new int[] {0, 1, 2, 3}, new int[] {0, 0, 0},
			1, new int[] {0, 3}, new int[] {0, 1, 2}, new int[0], new int[0], new int[0], new int[0],
			3, 3, 0, 0, 0, new int[] {0, 0, 0, 0}, new int[0], new int[0]);
	}

	private static CollisionMap emptyCollision()
	{
		return new CollisionMap(null)
		{
			@Override public int[] ordinaryWalkingNeighbors(int packedPoint)
			{ return new int[0];
			}
			@Override public boolean isBlocked(int x, int y, int z)
			{ return false;
			}
		};
	}
}
