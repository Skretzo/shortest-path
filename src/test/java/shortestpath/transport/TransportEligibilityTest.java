package shortestpath.transport;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.settings.TeleportationItem;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.transport.parser.ItemRequirementParser;

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
		ItemRequirement.Branch.item(ItemVariations.SHANTAY_PASS.getIds(), null, null, 1),
		ItemRequirement.Branch.item(ItemVariations.COINS.getIds(), null, null, 5))));
	// The ANDed gate shape: the Xeric's talisman AND the declared Honour unlock.
	private static final TransportItems TALISMAN_AND_GATE =
		new ItemRequirementParser().parse("13393=1&UNLOCK_XERICS_HONOUR=1");
	private static final Transport HONOUR_ITEM_TELEPORT = teleportItem(TALISMAN_AND_GATE);
	private static final Transport HONOUR_SPELL_TELEPORT = new Transport.TransportBuilder()
		.type(TransportType.TELEPORTATION_SPELL)
		.itemRequirements(TALISMAN_AND_GATE)
		.build();

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

		assertFalse(eligibility.usable(SHANTAY_GATE, BankVisitState.CARRIED));
		assertFalse(eligibility.usable(SHANTAY_GATE, BankVisitState.BANKED));
		assertTrue(eligibility.satisfiedByPlayer(SHANTAY_GATE));
	}

	@Test
	public void bankPathPoolUnlocksUsableOnlyWhenBanked()
	{
		bankHas.put(ItemID.SHANTAY_PASS, 1);

		TransportEligibility eligibility = eligibility(NO_POUCH);
		assertFalse(eligibility.usable(SHANTAY_GATE, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(SHANTAY_GATE, BankVisitState.BANKED));
		assertFalse(eligibility.satisfiedByPlayer(SHANTAY_GATE));
	}

	@Test
	public void fairyRingNeedsDramenStaffWhenDiaryIncomplete()
	{
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);

		assertFalse(eligibility.usable(fairyRing(), BankVisitState.CARRIED));
		assertFalse(eligibility.usable(fairyRing(), BankVisitState.BANKED));
	}

	@Test
	public void fairyRingUsableWithBankedDramenStaffOnlyOnBankPath()
	{
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);

		assertFalse(eligibility.usable(fairyRing(), BankVisitState.CARRIED));
		assertTrue(eligibility.usable(fairyRing(), BankVisitState.BANKED));
	}

	@Test
	public void fairyRingNeedsNoStaffWhenDiaryComplete()
	{
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, Integer.MAX_VALUE);

		assertTrue(eligibility.usable(fairyRing(), BankVisitState.CARRIED));
		assertTrue(eligibility.usable(fairyRing(), BankVisitState.BANKED));
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

		assertTrue(eligibility.usable(teleportItem(FALADOR_TELEPORT.getItemRequirements()), BankVisitState.CARRIED));
		assertTrue(eligibility.usable(teleportItem(FALADOR_TELEPORT.getItemRequirements()), BankVisitState.BANKED));
	}

	@Test
	public void teleportItemSettingNoneBlocksRegardlessOfItems()
	{
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 3);
		playerHas.put(ItemID.WATERRUNE, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, Integer.MAX_VALUE);

		assertFalse(eligibility.usable(teleportItem(FALADOR_TELEPORT.getItemRequirements()), BankVisitState.CARRIED));
	}

	@Test
	public void teleportItemSettingInventoryFallsThroughToRequirements()
	{
		Transport teleport = teleportItem(FALADOR_TELEPORT.getItemRequirements());
		TransportEligibility lacking = eligibility(TeleportationItem.INVENTORY, false, Integer.MAX_VALUE);

		assertFalse(lacking.usable(teleport, BankVisitState.CARRIED));

		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 3);
		playerHas.put(ItemID.WATERRUNE, 1);
		TransportEligibility carrying = eligibility(TeleportationItem.INVENTORY, false, Integer.MAX_VALUE);

		assertTrue(carrying.usable(teleport, BankVisitState.CARRIED));
	}

	@Test
	public void noRequirementsAreUsableAndNeedNoPickup()
	{
		Transport free = new Transport.TransportBuilder().type(TransportType.TRANSPORT).build();
		TransportEligibility eligibility = eligibility(NO_POUCH);

		assertTrue(eligibility.usable(free, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(free, BankVisitState.BANKED));
		assertTrue(eligibility.satisfiedByPlayer(free));
		assertNotNull(eligibility.bankPickupPlan(free).items);
		assertTrue(eligibility.bankPickupPlan(free).items.isEmpty());
		assertTrue(eligibility.bankPickupPlan(free).bankItemIds.isEmpty());
	}

	/**
	 * The item-question side of the unlock gate under every item mode: the
	 * talisman is carried, the gate is not declared. {@code usable()} only
	 * answers the item question — modes that bypass item evaluation entirely
	 * report the transport usable even without the declared unlock, and the
	 * admissibility gate that actually keeps it out of the route set lives in
	 * the Requirements chain (pinned per mode there). This matrix pins the
	 * semantics this layer owns, per mode, so adjacent modes can never merge
	 * or swap outcomes.
	 */
	@Test
	public void unlockGatedItemTeleportFollowsEachModesItemSemantics()
	{
		playerHas.put(ItemID.XERIC_TALISMAN, 1);

		Map<TeleportationItem, Boolean> expected = new EnumMap<>(TeleportationItem.class);
		expected.put(TeleportationItem.NONE, false);
		expected.put(TeleportationItem.INVENTORY, false);
		expected.put(TeleportationItem.INVENTORY_NON_CONSUMABLE, false);
		expected.put(TeleportationItem.INVENTORY_AND_BANK, false);
		expected.put(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, false);
		// The item-bypass modes return before item evaluation runs; their
		// usable() says nothing about the gate — Requirements.check does.
		expected.put(TeleportationItem.UNLOCKED, true);
		expected.put(TeleportationItem.UNLOCKED_NON_CONSUMABLE, true);
		expected.put(TeleportationItem.ALL, true);
		expected.put(TeleportationItem.ALL_NON_CONSUMABLE, true);
		assertEquals("the matrix must cover every mode",
			TeleportationItem.values().length, expected.size());

		for (TeleportationItem mode : TeleportationItem.values())
		{
			TransportEligibility eligibility = eligibility(mode, false, Integer.MAX_VALUE, Set.of());
			assertEquals("usable() under " + mode + " without the declared unlock",
				expected.get(mode), eligibility.usable(HONOUR_ITEM_TELEPORT, BankVisitState.CARRIED));
			// The overlay check has no mode bypass: an undeclared unlock is
			// never satisfied, whatever the teleportation-item mode.
			assertFalse("satisfiedByPlayer under " + mode + " without the declared unlock",
				eligibility.satisfiedByPlayer(HONOUR_ITEM_TELEPORT));
		}
	}

	/**
	 * The declared side of the same matrix: with the Honour unlock declared
	 * and the talisman carried, every mode but NONE offers the transport —
	 * the declared unlock satisfies the gate term, never the item term.
	 */
	@Test
	public void unlockGatedItemTeleportIsUsableWithTheDeclaredUnlockPerItsMode()
	{
		playerHas.put(ItemID.XERIC_TALISMAN, 1);

		for (TeleportationItem mode : TeleportationItem.values())
		{
			TransportEligibility eligibility = eligibility(mode, false, Integer.MAX_VALUE,
				Set.of(Unlock.XERICS_HONOUR));
			assertEquals("usable() under " + mode + " with the declared unlock",
				mode != TeleportationItem.NONE,
				eligibility.usable(HONOUR_ITEM_TELEPORT, BankVisitState.CARRIED));
			assertTrue("satisfiedByPlayer under " + mode + " with the declared unlock",
				eligibility.satisfiedByPlayer(HONOUR_ITEM_TELEPORT));
		}
	}

	/**
	 * A teleportation spell is outside the item-mode family entirely: its
	 * unlock gate must be honoured by item evaluation under every mode, so
	 * the undeclared gate is never usable and the declared one always is.
	 */
	@Test
	public void unlockGatedSpellTeleportIgnoresTheItemModeSetting()
	{
		playerHas.put(ItemID.XERIC_TALISMAN, 1);

		for (TeleportationItem mode : TeleportationItem.values())
		{
			TransportEligibility undeclared = eligibility(mode, false, Integer.MAX_VALUE, Set.of());
			assertFalse("spell gated under " + mode + " without the declared unlock",
				undeclared.usable(HONOUR_SPELL_TELEPORT, BankVisitState.CARRIED));

			TransportEligibility declared = eligibility(mode, false, Integer.MAX_VALUE,
				Set.of(Unlock.XERICS_HONOUR));
			assertTrue("spell usable under " + mode + " with the declared unlock",
				declared.usable(HONOUR_SPELL_TELEPORT, BankVisitState.CARRIED));
		}
	}

	private TransportEligibility eligibility(int bankPouchId)
	{
		return TransportEligibility.forPlayerAndBank(playerHas, bankHas, bankPouchId, bankPouchRunes);
	}

	private TransportEligibility eligibility(TeleportationItem setting, boolean staffRequired, int threshold)
	{
		return eligibility(setting, staffRequired, threshold, Set.of());
	}

	private TransportEligibility eligibility(TeleportationItem setting, boolean staffRequired, int threshold,
		Set<Unlock> unlocks)
	{
		Map<Integer, Integer> bankPathItems = new HashMap<>(playerHas);
		bankHas.forEach((itemId, quantity) -> bankPathItems.merge(itemId, quantity, Integer::sum));
		return new TransportEligibility(playerHas, bankPathItems, bankHas, NO_POUCH, bankPouchRunes,
			staffRequired, setting, threshold, unlocks, true);
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
