package shortestpath.transport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.ItemVariations;
import shortestpath.requirement.TeleportationItem;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Snapshot-level coverage for {@link TransportEligibility}: usable() verdicts for
 * pathfinding, satisfiedByPlayer() for the overlays, and the bank pickup plans plus
 * bank item highlighting the display layer renders.
 */
public class TransportEligibilityTest
{
	private static final int NO_POUCH = -1;
	private static final int[] NO_SUBSTITUTES = new int[0];
	// Falador Teleport: 1 law, 3 air, 1 water
	private static final Transport FALADOR_TELEPORT = teleport(
		rune(ItemVariations.LAW_RUNE, 1), rune(ItemVariations.AIR_RUNE, 3), rune(ItemVariations.WATER_RUNE, 1));
	private static final Transport THREE_AIR = teleport(rune(ItemVariations.AIR_RUNE, 3));
	private static final Transport SHANTAY_GATE = teleport(new ItemRequirement(List.of(
		new ItemRequirement.Branch(ItemVariations.SHANTAY_PASS.getIds(), null, null, 1),
		new ItemRequirement.Branch(ItemVariations.COINS.getIds(), null, null, 5))));

	private final Map<Integer, Integer> playerHas = new HashMap<>();
	private final Map<Integer, Integer> bankHas = new HashMap<>();
	private final Map<Integer, Integer> bankPouchRunes = new HashMap<>();

	@Test
	public void pickupAsksOnlyForTheShortfall()
	{
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 2);
		bankHas.put(ItemID.AIRRUNE, 1000);
		bankHas.put(ItemID.WATERRUNE, 1000);

		assertEquals(Map.of(ItemID.AIRRUNE, 1L, ItemID.WATERRUNE, 1L), pickups(FALADOR_TELEPORT, NO_POUCH));
	}

	@Test
	public void bankHoldingOnlyTheShortfallCanSupplyIt()
	{
		// The pathfinder routes via the bank for 2 carried + 1 banked air, so the hint must too
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 2);
		bankHas.put(ItemID.AIRRUNE, 1);
		bankHas.put(ItemID.WATERRUNE, 1);

		assertEquals(Map.of(ItemID.AIRRUNE, 1L, ItemID.WATERRUNE, 1L), pickups(FALADOR_TELEPORT, NO_POUCH));
	}

	@Test
	public void pureRuneIsPreferredAmongCarriedVariants()
	{
		// Both carried variants can be topped up; air comes first in the variations
		playerHas.put(ItemID.AIRRUNE, 1);
		playerHas.put(ItemID.DUSTRUNE, 1);
		bankHas.put(ItemID.AIRRUNE, 1000);
		bankHas.put(ItemID.DUSTRUNE, 1000);

		assertEquals(Map.of(ItemID.AIRRUNE, 2L), pickups(THREE_AIR, NO_POUCH));
	}

	@Test
	public void carriedCombinationRuneIsToppedUpBeforePureRune()
	{
		// Topping up the 2 carried dust saves a slot over bringing 3 air
		playerHas.put(ItemID.DUSTRUNE, 2);
		bankHas.put(ItemID.AIRRUNE, 1000);
		bankHas.put(ItemID.DUSTRUNE, 1000);

		assertEquals(Map.of(ItemID.DUSTRUNE, 1L), pickups(THREE_AIR, NO_POUCH));
	}

	@Test
	public void pureRuneIsUsedWhenCarriedVariantCannotBeToppedUp()
	{
		playerHas.put(ItemID.DUSTRUNE, 2);
		bankHas.put(ItemID.AIRRUNE, 1000);

		assertEquals(Map.of(ItemID.AIRRUNE, 3L), pickups(THREE_AIR, NO_POUCH));
	}

	@Test
	public void combinationRuneIsUsedWhenNoPureRuneCovers()
	{
		// The player carries 2 dust, so only dust tops them up: show dust, not air
		playerHas.put(ItemID.DUSTRUNE, 2);
		bankHas.put(ItemID.DUSTRUNE, 1);

		assertEquals(Map.of(ItemID.DUSTRUNE, 1L), pickups(THREE_AIR, NO_POUCH));
	}

	@Test
	public void combinationRuneNotCarriedIsShownAsThePureRune()
	{
		bankHas.put(ItemID.DUSTRUNE, 3);

		assertEquals(Map.of(ItemID.AIRRUNE, 3L), pickups(THREE_AIR, NO_POUCH));
	}

	@Test
	public void bankHoldingLessThanTheShortfallCannotSupplyIt()
	{
		playerHas.put(ItemID.AIRRUNE, 1);
		bankHas.put(ItemID.AIRRUNE, 1);

		assertNull(pickups(THREE_AIR, NO_POUCH));
	}

	@Test
	public void bankPouchShortOfTheShortfallFallsBackToLooseRunes()
	{
		bankHas.put(ItemID.BH_RUNE_POUCH, 1);
		bankHas.put(ItemID.AIRRUNE, 3);
		bankPouchRunes.put(ItemID.AIRRUNE, 2);

		assertEquals(Map.of(ItemID.AIRRUNE, 3L), pickups(THREE_AIR, ItemID.BH_RUNE_POUCH));
		assertEquals(Set.of(ItemID.AIRRUNE), highlighted(THREE_AIR, ItemID.BH_RUNE_POUCH));
	}

	@Test
	public void bankPouchAndLooseRunesCombineForOneShortfall()
	{
		// Neither the pouch's 2 air nor the 1 loose air covers 3 alone, but together they do,
		// as the pathfinder's bank path already counts them
		bankHas.put(ItemID.BH_RUNE_POUCH, 1);
		bankHas.put(ItemID.AIRRUNE, 1);
		bankPouchRunes.put(ItemID.AIRRUNE, 2);

		assertEquals(Map.of(ItemID.BH_RUNE_POUCH, 1L, ItemID.AIRRUNE, 1L), pickups(THREE_AIR, ItemID.BH_RUNE_POUCH));
		assertEquals(Set.of(ItemID.BH_RUNE_POUCH, ItemID.AIRRUNE), highlighted(THREE_AIR, ItemID.BH_RUNE_POUCH));
	}

	@Test
	public void bankPouchAndLooseRunesStillShortGiveNoPickup()
	{
		bankHas.put(ItemID.BH_RUNE_POUCH, 1);
		bankHas.put(ItemID.AIRRUNE, 1);
		bankPouchRunes.put(ItemID.AIRRUNE, 1);

		assertNull(pickups(THREE_AIR, ItemID.BH_RUNE_POUCH));
	}

	@Test
	public void bankPouchCoveringTheShortfallIsPreferredOnce()
	{
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 2);
		bankHas.put(ItemID.BH_RUNE_POUCH, 1);
		bankHas.put(ItemID.AIRRUNE, 5);
		bankHas.put(ItemID.WATERRUNE, 5);
		bankPouchRunes.put(ItemID.AIRRUNE, 1);
		bankPouchRunes.put(ItemID.WATERRUNE, 1);

		// One pouch covers both air and water
		assertEquals(Map.of(ItemID.BH_RUNE_POUCH, 1L), pickups(FALADOR_TELEPORT, ItemID.BH_RUNE_POUCH));
	}

	@Test
	public void bankPouchRunesCountTowardOtherShortfallsOnceTaken()
	{
		// The pouch is taken for law and water, so its 2 air leave a shortfall of 1 air
		bankHas.put(ItemID.BH_RUNE_POUCH, 1);
		bankHas.put(ItemID.AIRRUNE, 1000);
		bankPouchRunes.put(ItemID.LAWRUNE, 1);
		bankPouchRunes.put(ItemID.AIRRUNE, 2);
		bankPouchRunes.put(ItemID.WATERRUNE, 1);

		assertEquals(Map.of(ItemID.BH_RUNE_POUCH, 1L, ItemID.AIRRUNE, 1L),
			pickups(FALADOR_TELEPORT, ItemID.BH_RUNE_POUCH));
		assertEquals(Set.of(ItemID.BH_RUNE_POUCH, ItemID.AIRRUNE),
			highlighted(FALADOR_TELEPORT, ItemID.BH_RUNE_POUCH));
	}

	@Test
	public void highlightingUsesTheShortfall()
	{
		// 1 banked air covers the shortfall; 2 banked dust do not (none carried, so 3 needed).
		// No water in the bank, so the bank can't supply the teleport, but the air is still highlighted.
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 2);
		bankHas.put(ItemID.AIRRUNE, 1);
		bankHas.put(ItemID.DUSTRUNE, 2);

		assertNull(pickups(FALADOR_TELEPORT, NO_POUCH));
		assertEquals(Set.of(ItemID.AIRRUNE), highlighted(FALADOR_TELEPORT, NO_POUCH));
	}

	@Test
	public void carriedShantayPassNeedsNoBankPickup()
	{
		playerHas.put(ItemID.SHANTAY_PASS, 1);
		bankHas.put(ItemID.COINS, 100);

		assertEquals(Map.of(), pickups(SHANTAY_GATE, NO_POUCH));
		assertEquals(Set.of(), highlighted(SHANTAY_GATE, NO_POUCH));
		assertTrue(eligibility(NO_POUCH).satisfiedByPlayer(SHANTAY_GATE));
	}

	@Test
	public void bankCoinsAloneArePickedUpAsFiveCoins()
	{
		bankHas.put(ItemID.COINS, 100);

		assertEquals(Map.of(ItemID.COINS, 5L), pickups(SHANTAY_GATE, NO_POUCH));
	}

	@Test
	public void bankShantayPassPreferredOverCoins()
	{
		bankHas.put(ItemID.SHANTAY_PASS, 1);
		bankHas.put(ItemID.COINS, 100);

		assertEquals(Map.of(ItemID.SHANTAY_PASS, 1L), pickups(SHANTAY_GATE, NO_POUCH));
	}

	@Test
	public void carriedCoinsAreToppedUpBeforeTakingAPass()
	{
		playerHas.put(ItemID.COINS, 4);
		bankHas.put(ItemID.SHANTAY_PASS, 1);
		bankHas.put(ItemID.COINS, 100);

		assertEquals(Map.of(ItemID.COINS, 1L), pickups(SHANTAY_GATE, NO_POUCH));
	}

	@Test
	public void highlightingUsesEachBranchQuantity()
	{
		bankHas.put(ItemID.SHANTAY_PASS, 1);
		bankHas.put(ItemID.COINS, 3);

		assertEquals(Set.of(ItemID.SHANTAY_PASS), highlighted(SHANTAY_GATE, NO_POUCH));
		assertEquals(Map.of(ItemID.SHANTAY_PASS, 1L), pickups(SHANTAY_GATE, NO_POUCH));
	}

	@Test
	public void currencyThresholdBlocksUsableButNotSatisfiedByPlayer()
	{
		// The COINS=5 branch exceeds a threshold of 4, so the bank/carry pools cannot
		// satisfy the transport for pathfinding; the player-side display check ignores
		// the threshold and reports the coins as sufficient.
		playerHas.put(ItemID.COINS, 100);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, 4);

		assertFalse(eligibility.usable(SHANTAY_GATE, false));
		assertFalse(eligibility.usable(SHANTAY_GATE, true));
		assertTrue(eligibility.satisfiedByPlayer(SHANTAY_GATE));
	}

	@Test
	public void bankPathPoolUnlocksUsableOnlyWhenBanked()
	{
		bankHas.put(ItemID.SHANTAY_PASS, 1);

		TransportEligibility eligibility = eligibility(NO_POUCH);
		assertFalse(eligibility.usable(SHANTAY_GATE, false));
		assertTrue(eligibility.usable(SHANTAY_GATE, true));
		assertFalse(eligibility.satisfiedByPlayer(SHANTAY_GATE));
	}

	@Test
	public void fairyRingNeedsDramenStaffWhenDiaryIncomplete()
	{
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);

		assertFalse(eligibility.usable(fairyRing(), false));
		assertFalse(eligibility.usable(fairyRing(), true));
	}

	@Test
	public void fairyRingUsableWithBankedDramenStaffOnlyOnBankPath()
	{
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);

		assertFalse(eligibility.usable(fairyRing(), false));
		assertTrue(eligibility.usable(fairyRing(), true));
	}

	@Test
	public void fairyRingNeedsNoStaffWhenDiaryComplete()
	{
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, Integer.MAX_VALUE);

		assertTrue(eligibility.usable(fairyRing(), false));
		assertTrue(eligibility.usable(fairyRing(), true));
	}

	@Test
	public void fairyStaffPickupReturnsTheDramenStaffInTheBank()
	{
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		bankHas.put(ItemID.DRAMEN_STAFF_AIR, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);

		TransportEligibility.BankPickupPlan plan = eligibility.fairyStaffPickup();

		assertNotNull(plan);
		assertEquals(Map.of(ItemID.DRAMEN_STAFF, 1L), plan.items);
		assertEquals(Set.of(ItemID.DRAMEN_STAFF, ItemID.DRAMEN_STAFF_AIR), plan.bankItemIds);
	}

	@Test
	public void fairyStaffPickupIsNullWhenThePlayerCarriesAStaff()
	{
		playerHas.put(ItemID.DRAMEN_STAFF_FIRE, 1);
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);

		assertNull(eligibility.fairyStaffPickup());
	}

	@Test
	public void fairyStaffPickupIsNullWhenTheDiaryIsComplete()
	{
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, Integer.MAX_VALUE);

		assertNull(eligibility.fairyStaffPickup());
	}

	@Test
	public void fairyStaffPickupIsNullWhenTheBankLacksAStaff()
	{
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);

		assertNull(eligibility.fairyStaffPickup());
	}

	@Test
	public void teleportItemSettingAllBypassesItemRequirements()
	{
		TransportEligibility eligibility = eligibility(TeleportationItem.ALL, false, Integer.MAX_VALUE);

		assertTrue(eligibility.usable(teleportItem(FALADOR_TELEPORT.getItemRequirements()), false));
		assertTrue(eligibility.usable(teleportItem(FALADOR_TELEPORT.getItemRequirements()), true));
	}

	@Test
	public void teleportItemSettingNoneBlocksRegardlessOfItems()
	{
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 3);
		playerHas.put(ItemID.WATERRUNE, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, Integer.MAX_VALUE);

		assertFalse(eligibility.usable(teleportItem(FALADOR_TELEPORT.getItemRequirements()), false));
	}

	@Test
	public void teleportItemSettingInventoryFallsThroughToRequirements()
	{
		Transport teleport = teleportItem(FALADOR_TELEPORT.getItemRequirements());
		TransportEligibility lacking = eligibility(TeleportationItem.INVENTORY, false, Integer.MAX_VALUE);

		assertFalse(lacking.usable(teleport, false));

		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 3);
		playerHas.put(ItemID.WATERRUNE, 1);
		TransportEligibility carrying = eligibility(TeleportationItem.INVENTORY, false, Integer.MAX_VALUE);

		assertTrue(carrying.usable(teleport, false));
	}

	@Test
	public void usableAsRoutedBypassesUnownedTeleportItems()
	{
		// A teleport item routed under a bypassing setting stays usable on the
		// ledger even though the pool owns none of its items; satisfied() stays
		// the item-level question for the pickup hint.
		TransportEligibility eligibility = eligibility(TeleportationItem.ALL, false, Integer.MAX_VALUE);
		TransportEligibility.ConsumptionLedger ledger = eligibility.consumptionLedger();
		Transport teleport = teleportItem(FALADOR_TELEPORT.getItemRequirements());

		assertFalse(ledger.satisfied(teleport));
		assertTrue(ledger.usableAsRouted(teleport));
	}

	@Test
	public void usableAsRoutedAppliesTheFairyStaffGate()
	{
		// Fairy rings carry no item requirements, so the item-level check passes;
		// the routed check still enforces the Dramen staff gate.
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);
		TransportEligibility.ConsumptionLedger ledger = eligibility.consumptionLedger();
		Transport fairy = fairyRing();

		assertTrue(ledger.satisfied(fairy));
		assertFalse(ledger.usableAsRouted(fairy));

		playerHas.put(ItemID.DRAMEN_STAFF, 1);
		assertTrue(eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE)
			.consumptionLedger().usableAsRouted(fairy));
	}

	@Test
	public void noRequirementsAreUsableAndNeedNoPickup()
	{
		Transport free = new Transport.TransportBuilder().type(TransportType.TRANSPORT).build();
		TransportEligibility eligibility = eligibility(NO_POUCH);

		assertTrue(eligibility.usable(free, false));
		assertTrue(eligibility.usable(free, true));
		assertTrue(eligibility.satisfiedByPlayer(free));
		assertNotNull(eligibility.bankPickupPlan(free).items);
		assertTrue(eligibility.bankPickupPlan(free).items.isEmpty());
		assertTrue(eligibility.bankPickupPlan(free).bankItemIds.isEmpty());
	}

	private TransportEligibility eligibility(int bankPouchId)
	{
		return TransportEligibility.forPlayerAndBank(playerHas, bankHas, bankPouchId, bankPouchRunes);
	}

	private TransportEligibility eligibility(TeleportationItem setting, boolean staffRequired, int threshold)
	{
		Map<Integer, Integer> bankPathItems = new HashMap<>(playerHas);
		bankHas.forEach((itemId, quantity) -> bankPathItems.merge(itemId, quantity, Integer::sum));
		return new TransportEligibility(playerHas, bankPathItems, bankHas, NO_POUCH, bankPouchRunes,
			staffRequired, setting, threshold, Set.of());
	}

	private Map<Integer, Long> pickups(Transport transport, int bankPouchId)
	{
		return eligibility(bankPouchId).bankPickupPlan(transport).items;
	}

	private Set<Integer> highlighted(Transport transport, int bankPouchId)
	{
		return eligibility(bankPouchId).bankPickupPlan(transport).bankItemIds;
	}

	private static ItemRequirement rune(ItemVariations rune, int quantity)
	{
		return new ItemRequirement(rune.getIds(), NO_SUBSTITUTES, NO_SUBSTITUTES, quantity);
	}

	private static Transport teleport(ItemRequirement... requirements)
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_SPELL)
			.itemRequirements(new TransportItems(List.of(requirements)))
			.build();
	}

	private static Transport teleportItem(TransportItems requirements)
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(requirements)
			.build();
	}

	private static Transport fairyRing()
	{
		return new Transport.TransportBuilder()
			.type(TransportType.FAIRY_RING)
			.build();
	}
}
