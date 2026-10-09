package shortestpath.pathfinder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.ItemVariations;
import shortestpath.WorldPointUtil;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.requirement.TransportEligibility;
import shortestpath.transport.TransportType;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Tests {@link PathConsumptionValidator#firstUnpayable}: a post-search replay of the
 * path's recorded transports against a consumption ledger seeded from the same
 * eligibility snapshot the search used.
 */
public class PathConsumptionValidatorTest
{
	private static final int NO_POUCH = -1;
	private static final int[] NO_SUBSTITUTES = new int[0];
	private static final int START = WorldPointUtil.packWorldPoint(3100, 3500, 0);
	private static final int EDGE_ORIGIN = WorldPointUtil.packWorldPoint(3101, 3500, 0);
	private static final int EDGE_DESTINATION = WorldPointUtil.packWorldPoint(3102, 3500, 0);
	private static final int FINAL_TILE = WorldPointUtil.packWorldPoint(3103, 3500, 0);

	@Test
	public void secondFareIsUnpayableAfterTheFirstSpends()
	{
		Transport first = coinFare(3_000);
		Transport second = coinFare(3_000);
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.COINS, 3_000), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, first),
			new PathStep(EDGE_DESTINATION, BankVisitState.CARRIED, second));

		assertSame(second, PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void carriedCoveringBothFaresProducesNull()
	{
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.COINS, 6_000), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, coinFare(3_000)),
			new PathStep(EDGE_DESTINATION, BankVisitState.CARRIED, coinFare(3_000)));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void aBankVisitedStepBetweenFaresRefillsThePool()
	{
		// The first fare empties the carried pool; banking between the fares tops
		// it back up to the bank-path pool, so the second fare is payable. The
		// banked state is monotone — real paths never un-bank — so both steps
		// after the banking point carry the flag.
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.COINS, 3_000), Map.of(ItemID.COINS, 10_000), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, coinFare(3_000)),
			new PathStep(EDGE_DESTINATION, BankVisitState.BANKED),
			new PathStep(FINAL_TILE, BankVisitState.BANKED, coinFare(3_000)));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void bankVisitedStepsRefillOnceNotPerStep()
	{
		// bankVisited is a state flag: consecutive banked steps must not each
		// refund the spend that preceded them. Two 3,000 fares after banking
		// exhaust a 3,000 bank-path pool, so the second is unpayable.
		Transport second = coinFare(3_000);
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(), Map.of(ItemID.COINS, 3_000), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.BANKED),
			new PathStep(EDGE_DESTINATION, BankVisitState.BANKED, coinFare(3_000)),
			new PathStep(FINAL_TILE, BankVisitState.BANKED, second));

		assertSame(second, PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void aTransportOnTheBankTransitionStepPaysFromTheBankPool()
	{
		// The first banked step departs after the bank visit, so a transport
		// recorded on it is paid from the refilled bank-path pool — nothing is
		// carried here, so only the banked coins can cover the fare.
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(), Map.of(ItemID.COINS, 3_000), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_DESTINATION, BankVisitState.BANKED, coinFare(3_000)),
			new PathStep(FINAL_TILE, BankVisitState.BANKED));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void aTransportIntoTheBankedStateStillPaysFromTheCarriedPool()
	{
		// The symmetric edge: a transport landing on the bank tile is an
		// unbanked step — the player has not banked yet — so a fare only the
		// bank could cover is genuinely unpayable there.
		Transport fare = coinFare(3_000);
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(), Map.of(ItemID.COINS, 3_000), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, fare),
			new PathStep(EDGE_DESTINATION, BankVisitState.BANKED));

		assertSame(fare, PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void secondLawCastIsUnpayableAfterTheFirstSpends()
	{
		Transport first = lawSpell();
		Transport second = lawSpell();
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.LAWRUNE, 1), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, first),
			new PathStep(EDGE_DESTINATION, BankVisitState.CARRIED, second));

		assertSame(second, PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void aCarriedStaffSatisfiesBothCastsWithoutConsumption()
	{
		// A Dramen-class staff substitutes for the rune requirement on both edges;
		// it is not consumed, so neither cast is flagged.
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.DRAMEN_STAFF, 1), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, dramenSpell()),
			new PathStep(EDGE_DESTINATION, BankVisitState.CARRIED, dramenSpell()));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void secondConsumableWhistleUseIsUnpayable()
	{
		Transport first = quetzalWhistle();
		Transport second = quetzalWhistle();
		// An item-checking teleportation-item setting: under a bypassing one the
		// whistle would be routed without items and owe nothing.
		TransportEligibility eligibility = eligibilityWithSetting(
			Map.of(ItemID.HG_QUETZALWHISTLE_BASIC, 1), TeleportationItem.INVENTORY);

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, first),
			new PathStep(EDGE_DESTINATION, BankVisitState.CARRIED, second));

		assertSame(second, PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void aBypassedTeleportItemTypeIsNotFlaggedWithoutItems()
	{
		// Under a bypassing teleportation-item setting the search routes these
		// transports without any items, so the replay must apply the same
		// type-level verdict — otherwise every routed one reads as unpayable.
		TransportEligibility eligibility = eligibilityWithSetting(Map.of(), TeleportationItem.ALL);

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, quetzalWhistle()),
			new PathStep(EDGE_DESTINATION, BankVisitState.CARRIED, quetzalWhistle()));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void aNonConsumableGateIsReusableAcrossEdges()
	{
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.ROPE, 1), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, ropeGate()),
			new PathStep(EDGE_DESTINATION, BankVisitState.CARRIED, ropeGate()));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void stepsWithoutRecordedTransportsAreSkipped()
	{
		// Walk edges carry no transport and must not trip the ledger; the single
		// recorded fare is payable from the carried coins.
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.COINS, 3_000), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED),
			new PathStep(EDGE_DESTINATION, BankVisitState.CARRIED, coinFare(3_000)),
			new PathStep(FINAL_TILE, BankVisitState.CARRIED));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void missingEligibilitySkipsValidation()
	{
		List<PathStep> path = List.of(
			new PathStep(START, BankVisitState.CARRIED),
			new PathStep(EDGE_ORIGIN, BankVisitState.CARRIED, coinFare(3_000)));

		assertNull(PathConsumptionValidator.firstUnpayable(null, path));
	}

	@Test
	public void nullOrEmptyPathsProduceNull()
	{
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(), Map.of(), NO_POUCH, Map.of());

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, null));
		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, List.of()));
		assertNull(PathConsumptionValidator.firstUnpayable(eligibility,
			List.of(new PathStep(START, BankVisitState.CARRIED))));
	}

	/**
	 * A snapshot with the given carried pool (and an equal bank-path pool, no
	 * bank stock) under an explicit teleportation-item setting.
	 */
	private static TransportEligibility eligibilityWithSetting(
		Map<Integer, Integer> carriedItems, TeleportationItem setting)
	{
		return new TransportEligibility(carriedItems, carriedItems, Map.of(), NO_POUCH, Map.of(),
			false, setting, Integer.MAX_VALUE, Set.of());
	}

	private static Transport coinFare(int coins)
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.origin(EDGE_ORIGIN)
			.destination(EDGE_DESTINATION)
			.itemRequirements(singleItem(ItemID.COINS, coins))
			.build();
	}

	private static Transport lawSpell()
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_SPELL)
			.itemRequirements(singleItem(ItemID.LAWRUNE, 1))
			.build();
	}

	private static Transport dramenSpell()
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_SPELL)
			.itemRequirements(new TransportItems(List.of(
				new ItemRequirement(ItemVariations.FIRE_RUNE.getIds(),
					ItemVariations.DRAMEN_STAFF.getIds(), NO_SUBSTITUTES, 3))))
			.build();
	}

	private static Transport quetzalWhistle()
	{
		return new Transport.TransportBuilder()
			.type(TransportType.QUETZAL_WHISTLE)
			.origin(EDGE_ORIGIN)
			.destination(EDGE_DESTINATION)
			.isConsumable(true)
			.itemRequirements(singleItem(ItemID.HG_QUETZALWHISTLE_BASIC, 1))
			.build();
	}

	private static Transport ropeGate()
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.origin(EDGE_ORIGIN)
			.destination(EDGE_DESTINATION)
			.itemRequirements(singleItem(ItemID.ROPE, 1))
			.build();
	}

	private static TransportItems singleItem(int itemId, int quantity)
	{
		return new TransportItems(List.of(
			new ItemRequirement(new int[]{itemId}, NO_SUBSTITUTES, NO_SUBSTITUTES, quantity)));
	}
}
