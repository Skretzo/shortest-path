package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/** Correctness and diagnostic counts for unreachable goals the relaxed graph cannot reject. */
public class UnreachableSearchTest
{
	@Test
	public void unavailableBlockedTransportOriginDoesNotConnectItsNeighbouringRooms() throws Exception
	{
		int start = tile(10), left = tile(11), blocked = tile(12), right = tile(13), target = tile(14);
		SyntheticCollisionMap collision = new SyntheticCollisionMap(Set.of(start, left, right, target),
			Map.of(start, new int[] {left}, left, new int[] {start}, blocked, new int[] {left, right},
				right, new int[] {target}, target, new int[] {right}), 1);
		Transport transport = new Transport.TransportBuilder().origin(blocked).destination(right)
			.type(TransportType.TRANSPORT).duration(1).build();
		RoutingStatic stat = RoutingStaticBuilder.build(collision, Map.of(blocked, Set.of(transport)),
			Set.of(), new int[0], start).routingStatic;
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
			TransportAvailabilityFixture.of(), false, Set.of(), 0, true, ignored -> 0);
		PreparedTarget prepared = PreparedTarget.prepare(new SiteGraph(stat, account), collision, target);
		AStarForwardSearch.Result result = prepared.search(start, () -> false, 1);

		assertFalse("the account has no transport through the blocked origin", result.reached());
		System.out.println("Unavailable blocked origin: popped=" + result.counters().statesPopped()
			+ ", discovered=" + result.counters().uniqueStatesReached());
		PreparedRoutingAccount enabled = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(transport),
			TransportAvailabilityFixture.of(transport), false, Set.of(), 0, true, ignored -> 0);
		assertTrue(PreparedTarget.prepare(new SiteGraph(stat, enabled), collision, target)
			.search(start, () -> false, 1).reached());
	}

	@Test
	public void bankOnlyGoalCannotFinishWhenNoBankIsReachable() throws Exception
	{
		int start = tile(10), middle = tile(11), target = tile(12);
		SyntheticCollisionMap collision = new SyntheticCollisionMap(Set.of(start, middle, target),
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{start, middle}, {middle, target}}), 1);
		RoutingStatic stat = RoutingStaticBuilder.build(collision, Map.of(), Set.of(middle), new int[0], start).routingStatic;
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
			TransportAvailabilityFixture.of(), true, Set.of(), 0, true, ignored -> 0);
		PreparedTarget prepared = PreparedTarget.prepare(new SiteGraph(stat, account), collision,
			new int[] {target}, new int[] {2});
		AStarForwardSearch.Result result = prepared.search(start, () -> false, 1, SearchRestrictions.none(),
			AStarForwardSearch.NO_PROGRESS, (tile, banked) -> banked == BankVisitState.BANKED);

		assertFalse("the goal requires a bank visit the account cannot make", result.reached());
		assertEquals(CostArithmetic.INF, prepared.heuristic().estimate(start, BankVisitState.CARRIED));
		assertEquals(0, result.counters().statesPopped());
		System.out.println("Bank-only goal: popped=" + result.counters().statesPopped()
			+ ", discovered=" + result.counters().uniqueStatesReached());
		PreparedRoutingAccount enabled = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
			TransportAvailabilityFixture.of(), true, Set.of(middle), 0, true, ignored -> 0);
		assertTrue(PreparedTarget.prepare(new SiteGraph(stat, enabled), collision, new int[] {target}, new int[] {2})
			.search(start, () -> false, 1, SearchRestrictions.none(), AStarForwardSearch.NO_PROGRESS,
				(tile, banked) -> banked == BankVisitState.BANKED).reached());
	}

	private static int tile(int x)
	{
		return WorldPointUtil.packWorldPoint(x, 10, 0);
	}
}
