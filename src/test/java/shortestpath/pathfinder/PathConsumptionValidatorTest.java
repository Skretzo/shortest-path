package shortestpath.pathfinder;

import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.ItemVariations;
import shortestpath.WorldPointUtil;
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
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false, first),
			new PathStep(EDGE_DESTINATION, false, second));

		assertSame(second, PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void carriedCoveringBothFaresProducesNull()
	{
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.COINS, 6_000), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false, coinFare(3_000)),
			new PathStep(EDGE_DESTINATION, false, coinFare(3_000)));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void aBankVisitedStepBetweenFaresRefillsThePool()
	{
		// The first fare empties the carried pool; the bank-visited step between the
		// fares tops it back up to the bank-path pool, so the second fare is payable.
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.COINS, 3_000), Map.of(ItemID.COINS, 10_000), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false, coinFare(3_000)),
			new PathStep(EDGE_DESTINATION, true),
			new PathStep(FINAL_TILE, false, coinFare(3_000)));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void secondLawCastIsUnpayableAfterTheFirstSpends()
	{
		Transport first = lawSpell();
		Transport second = lawSpell();
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.LAWRUNE, 1), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false, first),
			new PathStep(EDGE_DESTINATION, false, second));

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
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false, dramenSpell()),
			new PathStep(EDGE_DESTINATION, false, dramenSpell()));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void secondConsumableWhistleUseIsUnpayable()
	{
		Transport first = quetzalWhistle();
		Transport second = quetzalWhistle();
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.HG_QUETZALWHISTLE_BASIC, 1), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false, first),
			new PathStep(EDGE_DESTINATION, false, second));

		assertSame(second, PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void aNonConsumableGateIsReusableAcrossEdges()
	{
		TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(ItemID.ROPE, 1), Map.of(), NO_POUCH, Map.of());

		List<PathStep> path = List.of(
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false, ropeGate()),
			new PathStep(EDGE_DESTINATION, false, ropeGate()));

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
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false, coinFare(3_000)),
			new PathStep(FINAL_TILE, false));

		assertNull(PathConsumptionValidator.firstUnpayable(eligibility, path));
	}

	@Test
	public void missingEligibilitySkipsValidation()
	{
		List<PathStep> path = List.of(
			new PathStep(START, false),
			new PathStep(EDGE_ORIGIN, false, coinFare(3_000)));

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
			List.of(new PathStep(START, false))));
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
