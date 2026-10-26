package shortestpath.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.TransportEligibility.BankPickupPlan;
import shortestpath.settings.TeleportationItem;

/**
 * The {@link BalloonRoute} enum against the {@code hot_air_balloons.tsv}
 * destination rows, and the destination-scoped
 * {@code UNLOCK_BALLOON_LOG_BASKET} grant: an eligibility snapshot grants the
 * unlock only to a {@link TransportType#HOT_AIR_BALLOON} transport whose
 * destination is one of the configured basket destinations.
 */
public class BalloonRouteTest
{
	private static Transport balloonTo(BalloonRoute route)
	{
		return new Transport.TransportBuilder()
			.type(TransportType.HOT_AIR_BALLOON)
			.destination(route.getPackedDestination())
			.itemRequirements(route.getLogItemId() + "=1|UNLOCK_BALLOON_LOG_BASKET=1")
			.build();
	}

	private static TransportEligibility eligibilityWith(Map<Integer, Integer> carried,
		Map<Integer, Integer> bankHas, Set<Integer> balloonDests)
	{
		return new TransportEligibility(carried, carried, bankHas, -1, Map.of(),
			false, TeleportationItem.NONE, Integer.MAX_VALUE, Set.of(), false,
			balloonDests);
	}

	private static void assertRoute(BalloonRoute route, String label, int x, int y,
		int logItemId, String logLabel)
	{
		assertEquals(label, route.getLabel());
		assertEquals(WorldPointUtil.packWorldPoint(x, y, 0), route.getPackedDestination());
		assertEquals(logItemId, route.getLogItemId());
		assertEquals(logLabel, route.getLogLabel());
	}

	@Test
	public void enumCoversExactlyTheSixTsvRoutes()
	{
		assertEquals(6, BalloonRoute.values().length);
		assertRoute(BalloonRoute.ENTRANA, "Entrana", 2808, 3354, 1511, "logs");
		assertRoute(BalloonRoute.TAVERLEY, "Taverley", 2941, 3420, 1511, "logs");
		assertRoute(BalloonRoute.CRAFTING_GUILD, "Crafting Guild", 2923, 3302, 1521, "oak logs");
		assertRoute(BalloonRoute.VARROCK, "Varrock", 3299, 3482, 1519, "willow logs");
		assertRoute(BalloonRoute.CASTLE_WARS, "Castle Wars", 2459, 3110, 1515, "yew logs");
		assertRoute(BalloonRoute.GRAND_TREE, "Grand Tree", 2478, 3456, 1513, "magic logs");
	}

	@Test
	public void packedDestinationsAreDistinct()
	{
		Set<Integer> dests = new HashSet<>();
		for (BalloonRoute route : BalloonRoute.values())
		{
			dests.add(route.getPackedDestination());
		}
		assertEquals(BalloonRoute.values().length, dests.size());
	}

	@Test
	public void displayLabelNamesRouteAndLog()
	{
		assertEquals("Grand Tree (magic logs)", BalloonRoute.GRAND_TREE.displayLabel());
		assertEquals("Castle Wars (yew logs)", BalloonRoute.CASTLE_WARS.displayLabel());
	}

	@Test
	public void selectedRouteWaivesItsLogRequirement()
	{
		Transport grandTree = balloonTo(BalloonRoute.GRAND_TREE);
		TransportEligibility eligibility = eligibilityWith(Map.of(), Map.of(),
			Set.of(BalloonRoute.GRAND_TREE.getPackedDestination()));

		assertTrue("stocked Grand Tree basket waives the magic log",
			eligibility.usable(grandTree, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(grandTree, BankVisitState.BANKED));
		assertTrue(eligibility.satisfiedByPlayer(grandTree));
	}

	@Test
	public void unselectedRouteStillDemandsItsLog()
	{
		Transport grandTree = balloonTo(BalloonRoute.GRAND_TREE);
		Set<Integer> varrockOnly = Set.of(BalloonRoute.VARROCK.getPackedDestination());

		assertFalse("Varrock's stocked basket does not waive Grand Tree's magic log",
			eligibilityWith(Map.of(), Map.of(), varrockOnly)
				.usable(grandTree, BankVisitState.CARRIED));
		assertFalse(eligibilityWith(Map.of(), Map.of(), varrockOnly)
			.satisfiedByPlayer(grandTree));

		// The route's log itself still satisfies the row when carried.
		assertTrue(eligibilityWith(Map.of(1513, 1), Map.of(), varrockOnly)
			.usable(grandTree, BankVisitState.CARRIED));
	}

	@Test
	public void bankPickupPlanFollowsTheSameGrant()
	{
		Transport grandTree = balloonTo(BalloonRoute.GRAND_TREE);
		Map<Integer, Integer> bankWithLogs = Map.of(1513, 1);

		BankPickupPlan unlocked = eligibilityWith(Map.of(), bankWithLogs,
			Set.of(BalloonRoute.GRAND_TREE.getPackedDestination()))
			.bankPickupPlan(grandTree);
		assertTrue("a stocked basket needs no bank pickup", unlocked.items.isEmpty());

		BankPickupPlan locked = eligibilityWith(Map.of(), bankWithLogs,
			Set.of(BalloonRoute.VARROCK.getPackedDestination()))
			.bankPickupPlan(grandTree);
		assertEquals("an unstocked basket picks up the route's log",
			Map.of(1513, 1L), locked.items);
	}

	@Test
	public void nonBalloonTransportNeverInheritsTheGrant()
	{
		Transport notABalloon = new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.destination(BalloonRoute.GRAND_TREE.getPackedDestination())
			.itemRequirements("1513=1|UNLOCK_BALLOON_LOG_BASKET=1")
			.build();
		TransportEligibility eligibility = eligibilityWith(Map.of(), Map.of(),
			Set.of(BalloonRoute.GRAND_TREE.getPackedDestination()));

		assertFalse("the basket unlock is balloon-only",
			eligibility.usable(notABalloon, BankVisitState.CARRIED));
		assertFalse(eligibility.satisfiedByPlayer(notABalloon));
	}

	@Test
	public void collectDefaultsToNoUnlockedRoutes()
	{
		Transport grandTree = balloonTo(BalloonRoute.GRAND_TREE);
		TransportEligibility eligibility = TransportEligibility.collect(
			mock(PlayerStateSource.class), null, TeleportationItem.NONE,
			Integer.MAX_VALUE, false, Set.of(), Set.of());

		assertFalse("an empty basket-destination set captures no unlocked balloon routes",
			eligibility.usable(grandTree, BankVisitState.CARRIED));
	}

	@Test
	public void destinationSetIsCapturedImmutably()
	{
		Transport grandTree = balloonTo(BalloonRoute.GRAND_TREE);
		Set<Integer> dests = new HashSet<>();
		TransportEligibility eligibility = eligibilityWith(Map.of(), Map.of(), dests);

		dests.add(BalloonRoute.GRAND_TREE.getPackedDestination());

		assertFalse("a post-construction write must not reach the snapshot",
			eligibility.usable(grandTree, BankVisitState.CARRIED));
	}
}
