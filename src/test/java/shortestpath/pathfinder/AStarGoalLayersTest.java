package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;
import shortestpath.pathfinder.astar.AStarRoutingSession;
import shortestpath.pathfinder.astar.PreparedRoutingAccount;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.pathfinder.astar.RoutingStaticTestFixture;

/** Exercises resolved goal layers through the plugin adapter, including snapping and arrival. */
public class AStarGoalLayersTest
{
	private static final int START = RoutingStaticTestFixture.A;
	private static final int BANK = RoutingStaticTestFixture.BANK;
	private static final int FALLBACK = RoutingStaticTestFixture.C;
	private static final int BLOCKED = RoutingStaticTestFixture.D;
	private static final HeldItems CARRIED = new HeldItems(Map.of(ItemID.SPADE, 1), Map.of(ItemID.SPADE, 1), true);
	private static final HeldItems BANKED = new HeldItems(Map.of(), Map.of(ItemID.SPADE, 1), true);
	private PathfinderConfig config;
	private RoutingStatic stat;

	@Before
	public void world() throws Exception
	{
		stat = RoutingStaticTestFixture.createT4Walking();
		CollisionMap collision = mock(CollisionMap.class);
		when(collision.isBlocked(anyInt(), anyInt(), anyInt())).thenReturn(true);
		for (int x = 1000; x <= 1002; x++) when(collision.isBlocked(x, 1000, 0)).thenReturn(false);
		when(collision.ordinaryWalkingNeighbors(anyInt())).thenReturn(new int[0]);
		when(collision.ordinaryWalkingNeighbors(START)).thenReturn(new int[] {BANK});
		when(collision.ordinaryWalkingNeighbors(BANK)).thenReturn(new int[] {START, FALLBACK});
		when(collision.ordinaryWalkingNeighbors(FALLBACK)).thenReturn(new int[] {BANK});
		when(collision.ordinaryWalkingNeighbors(BLOCKED)).thenReturn(new int[] {FALLBACK});
		when(collision.ordinaryWalkingMask(START)).thenReturn((byte) 4);
		when(collision.ordinaryWalkingMask(BANK)).thenReturn((byte) 68);
		when(collision.ordinaryWalkingMask(FALLBACK)).thenReturn((byte) 64);
		config = mock(PathfinderConfig.class);
		when(config.getMap()).thenReturn(collision);
		when(config.isCollisionAwareBlockedTargets()).thenReturn(true);
		when(config.getUnreachableTargetDistance()).thenReturn(1);
		bankAccess(true);
	}

	@Test
	public void bankedFallbackRequiresABankAndPreservesArrival()
	{
		PathfinderResult result = search(BANKED, new AStarRoutingSession()).getResult();
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
		assertEquals(7, result.getPathCost());
		assertEquals(FALLBACK, result.getClosestReachedPoint());
		assertEquals(BLOCKED, result.getArrival().getTarget().getPoint());
		assertEquals(TargetStatus.BANK, result.getArrival().getStatus());
		assertEquals(BankVisitState.BANKED,
			result.getPathSteps().get(result.getPathSteps().size() - 1).getBankVisitState());
	}

	@Test
	public void bankedFallbackWithoutAnAccessibleBankSkipsTheTileSearch()
	{
		bankAccess(false);
		PathfinderResult result = search(BANKED, new AStarRoutingSession()).getResult();
		assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, result.getTerminationReason());
		assertEquals(0, result.getNodesChecked());
	}

	@Test
	public void unknownItemsStillRouteToFallbackAndReportMissingItems()
	{
		PathfinderResult result = search(HeldItems.NONE, new AStarRoutingSession()).getResult();
		assertTrue(result.isReached());
		assertEquals(2, result.getPathCost());
		assertEquals(TargetStatus.UNKNOWN, result.getArrival().getStatus());
		assertEquals(TargetsTest.items(ItemID.SPADE).getRequirements(), result.getArrival().getMissingItems());
	}

	@Test
	public void heldItemChangesReuseTheGraphButKeepSeparateTargetPreparations()
	{
		AStarRoutingSession session = new AStarRoutingSession();
		AStarPathfinder first = search(CARRIED, session);
		assertFalse(first.isTargetReused());
		assertEquals(2, first.getResult().getPathCost());
		assertEquals(TargetStatus.CARRIED, first.getResult().getArrival().getStatus());
		assertTrue(search(CARRIED, session).isTargetReused());
		AStarPathfinder banked = search(BANKED, session);
		assertTrue(banked.isGraphReused());
		assertFalse(banked.isTargetReused());
		assertEquals(7, banked.getResult().getPathCost());
		assertTrue(search(BANKED, session).isTargetReused());
		assertTrue(search(CARRIED, session).isTargetReused());
		assertEquals(2, session.cachedTargetCount());
	}

	private void bankAccess(boolean accessible)
	{
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
			TransportAvailabilityFixture.of(), true, accessible ? Set.of(BANK) : Set.of(), 5, true, ignored -> 0);
		when(config.prepareAStarRoutingAccount(true, false)).thenReturn(account);
		when(config.prepareAStarRoutingAccount(true, true)).thenReturn(account);
	}

	private AStarPathfinder search(HeldItems items, AStarRoutingSession session)
	{
		when(config.getHeldItems()).thenReturn(items);
		Targets targets = Targets.of(List.of(Target.requiring(BLOCKED, TargetsTest.items(ItemID.SPADE))));
		AStarPathfinder pathfinder = new AStarPathfinder(config, () -> stat, session, START, targets, null, 1);
		pathfinder.run();
		return pathfinder;
	}
}
