package shortestpath.transport;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.ItemVariations;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.settings.TeleportationItem;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
		rune(ItemVariations.fromName("LAW_RUNE"), 1), rune(ItemVariations.fromName("AIR_RUNE"), 3), rune(ItemVariations.fromName("WATER_RUNE"), 1));
	private static final Transport THREE_AIR = teleport(rune(ItemVariations.fromName("AIR_RUNE"), 3));
	private static final Transport SHANTAY_GATE = teleport(new ItemRequirement(List.of(
		new ItemRequirement.Branch(ItemVariations.fromName("SHANTAY_PASS").getIds(), null, null, 1),
		new ItemRequirement.Branch(ItemVariations.fromName("COINS").getIds(), null, null, 5))));

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

		assertFalse(eligibility.usable(eligibility.wrapFairyRingStaff(fairyRingAt(ZANARIS_RING)),
			BankVisitState.CARRIED));
		assertFalse(eligibility.usable(eligibility.wrapFairyRingStaff(fairyRingAt(ZANARIS_RING)),
			BankVisitState.BANKED));
	}

	@Test
	public void fairyRingUsableWithBankedDramenStaffOnlyOnBankPath()
	{
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);
		Transport wrapped = eligibility.wrapFairyRingStaff(fairyRingAt(ZANARIS_RING));

		assertFalse(eligibility.usable(wrapped, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(wrapped, BankVisitState.BANKED));
	}

	@Test
	public void fairyRingNeedsNoStaffWhenDiaryComplete()
	{
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, Integer.MAX_VALUE);
		Transport wrapped = eligibility.wrapFairyRingStaff(fairyRingAt(ZANARIS_RING));

		assertTrue(eligibility.usable(wrapped, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(wrapped, BankVisitState.BANKED));
	}

	@Test
	public void pohRingNeedsNoStaffEvenWhenDiaryIncomplete()
	{
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, true, Integer.MAX_VALUE);
		Transport pohRing = fairyRingAt(POH_RING);
		Transport wrapped = eligibility.wrapFairyRingStaff(pohRing);

		assertSame("the POH ring passes through unwrapped", pohRing, wrapped);
		assertTrue(eligibility.usable(wrapped, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(wrapped, BankVisitState.BANKED));
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

	// ------------------------------------------------------------------
	// Usable-side teleportation-item mode matrix — the twin of the
	// check-side matrix: every mode x consumable cell asserts the
	// usable(CARRIED) verdict on a carried-satisfying pool.
	// ------------------------------------------------------------------

	@Test
	public void everyTeleportationItemModeIsUsableOnACarriedConsumable()
	{
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 3);
		playerHas.put(ItemID.WATERRUNE, 1);
		Transport consumable = teleportItem(FALADOR_TELEPORT.getItemRequirements(), true);

		Map<TeleportationItem, Boolean> expected = new EnumMap<>(TeleportationItem.class);
		expected.put(TeleportationItem.NONE, false);
		expected.put(TeleportationItem.INVENTORY, true);
		expected.put(TeleportationItem.INVENTORY_NON_CONSUMABLE, false);
		expected.put(TeleportationItem.INVENTORY_AND_BANK, true);
		expected.put(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, false);
		expected.put(TeleportationItem.UNLOCKED, true);
		expected.put(TeleportationItem.UNLOCKED_NON_CONSUMABLE, false);
		expected.put(TeleportationItem.ALL, true);
		expected.put(TeleportationItem.ALL_NON_CONSUMABLE, false);
		assertEquals("the matrix must cover every mode",
			TeleportationItem.values().length, expected.size());

		for (Map.Entry<TeleportationItem, Boolean> entry : expected.entrySet())
		{
			TeleportationItem mode = entry.getKey();
			assertEquals(mode + " usable(carried) on a carried consumable",
				entry.getValue(),
				eligibility(mode, false, Integer.MAX_VALUE).usable(consumable, BankVisitState.CARRIED));
		}
	}

	@Test
	public void everyTeleportationItemModeIsUsableOnACarriedNonConsumable()
	{
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 3);
		playerHas.put(ItemID.WATERRUNE, 1);
		Transport nonConsumable = teleportItem(FALADOR_TELEPORT.getItemRequirements(), false);

		Map<TeleportationItem, Boolean> expected = new EnumMap<>(TeleportationItem.class);
		expected.put(TeleportationItem.NONE, false);
		expected.put(TeleportationItem.INVENTORY, true);
		expected.put(TeleportationItem.INVENTORY_NON_CONSUMABLE, true);
		expected.put(TeleportationItem.INVENTORY_AND_BANK, true);
		expected.put(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, true);
		expected.put(TeleportationItem.UNLOCKED, true);
		expected.put(TeleportationItem.UNLOCKED_NON_CONSUMABLE, true);
		expected.put(TeleportationItem.ALL, true);
		expected.put(TeleportationItem.ALL_NON_CONSUMABLE, true);
		assertEquals("the matrix must cover every mode",
			TeleportationItem.values().length, expected.size());

		for (Map.Entry<TeleportationItem, Boolean> entry : expected.entrySet())
		{
			TeleportationItem mode = entry.getKey();
			assertEquals(mode + " usable(carried) on a carried non-consumable",
				entry.getValue(),
				eligibility(mode, false, Integer.MAX_VALUE).usable(nonConsumable, BankVisitState.CARRIED));
		}
	}

	// ------------------------------------------------------------------
	// Named divergent-branch cases — the cells that distinguish a bypass
	// verdict from a deferral that happens to pass.
	// ------------------------------------------------------------------

	@Test
	public void unlockedModeBypassesItemPoolsEntirely()
	{
		// UNLOCKED returns true on both pools even when they are empty —
		// items are never evaluated. A deferred verdict would read false on
		// these pools, so this is the bypass/deferral boundary.
		Transport teleport = teleportItem(FALADOR_TELEPORT.getItemRequirements(), true);
		TransportEligibility eligibility = eligibility(TeleportationItem.UNLOCKED, false, Integer.MAX_VALUE);

		assertTrue(eligibility.usable(teleport, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(teleport, BankVisitState.BANKED));
	}

	@Test
	public void nonConsumableModesSplitBypassFromDeferral()
	{
		// On a non-consumable with EMPTY pools the table splits: ALL/UNLOCKED
		// non-consumable bypass the pools (true), while INVENTORY
		// non-consumable modes defer to them and fail. A mode merged into the
		// wrong arm is caught here and nowhere else.
		Transport nonConsumable = teleportItem(FALADOR_TELEPORT.getItemRequirements(), false);

		assertTrue(eligibility(TeleportationItem.ALL_NON_CONSUMABLE, false, Integer.MAX_VALUE)
			.usable(nonConsumable, BankVisitState.CARRIED));
		assertTrue(eligibility(TeleportationItem.UNLOCKED_NON_CONSUMABLE, false, Integer.MAX_VALUE)
			.usable(nonConsumable, BankVisitState.CARRIED));
		assertFalse(eligibility(TeleportationItem.INVENTORY_NON_CONSUMABLE, false, Integer.MAX_VALUE)
			.usable(nonConsumable, BankVisitState.CARRIED));
		assertFalse(eligibility(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, false, Integer.MAX_VALUE)
			.usable(nonConsumable, BankVisitState.CARRIED));
	}

	@Test
	public void inventoryAndBankReadsTheBankedPool()
	{
		// The item sits only in the bank: carried fails, banked passes.
		bankHas.put(ItemID.LAWRUNE, 1);
		bankHas.put(ItemID.AIRRUNE, 3);
		bankHas.put(ItemID.WATERRUNE, 1);
		Transport teleport = teleportItem(FALADOR_TELEPORT.getItemRequirements(), true);
		TransportEligibility eligibility = eligibility(TeleportationItem.INVENTORY_AND_BANK, false, Integer.MAX_VALUE);

		assertFalse(eligibility.usable(teleport, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(teleport, BankVisitState.BANKED));
	}

	@Test
	public void noneModeRejectsOnBothPools()
	{
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 3);
		playerHas.put(ItemID.WATERRUNE, 1);
		Transport teleport = teleportItem(FALADOR_TELEPORT.getItemRequirements(), true);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, Integer.MAX_VALUE);

		assertFalse(eligibility.usable(teleport, BankVisitState.CARRIED));
		assertFalse(eligibility.usable(teleport, BankVisitState.BANKED));
	}

	@Test
	public void nonItemTeleportTypesAreNotModeRejected()
	{
		// A non-teleport-item type under NONE mode still reads the pools —
		// the mode dispatch never reaches it.
		playerHas.put(ItemID.SHANTAY_PASS, 1);
		TransportEligibility eligibility = eligibility(TeleportationItem.NONE, false, Integer.MAX_VALUE);

		assertTrue(eligibility.usable(SHANTAY_GATE, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(SHANTAY_GATE, BankVisitState.BANKED));
	}

	// ------------------------------------------------------------------
	// Fairy-staff injection — the availability-build wrap carries the
	// staff rule as a real item requirement on non-POH rings.
	// ------------------------------------------------------------------

	private static final int ZANARIS_RING = WorldPointUtil.packWorldPoint(2412, 4434, 0);
	private static final int POH_RING = WorldPointUtil.packWorldPoint(1858, 7051, 0);

	private static Transport fairyRingAt(int origin)
	{
		return new Transport.TransportBuilder().type(TransportType.FAIRY_RING).origin(origin).build();
	}

	private TransportEligibility staffEligibility(boolean staffRequired)
	{
		return eligibility(TeleportationItem.NONE, staffRequired, Integer.MAX_VALUE);
	}

	private static boolean contains(int[] ids, int id)
	{
		for (int candidate : ids)
		{
			if (candidate == id)
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void wrapInjectsTheStaffRequirementOnANonPohRing()
	{
		Transport ring = fairyRingAt(ZANARIS_RING);
		Transport wrapped = staffEligibility(true).wrapFairyRingStaff(ring);

		assertTrue(wrapped != ring);
		assertNull("the shared instance must not be mutated", ring.getItemRequirements());
		TransportItems merged = wrapped.getItemRequirements();
		assertNotNull(merged);
		// The injected requirement is a single OR group over every staff variant.
		assertEquals(1, merged.size());
		for (int id : ItemVariations.fromName("DRAMEN_STAFF").getIds())
		{
			assertTrue("merged requirement missing staff variant " + id,
				contains(merged.getRequirements().get(0).getStaffIds(), id));
		}
	}

	@Test
	public void wrapPassesThroughWhenDiaryComplete()
	{
		Transport ring = fairyRingAt(ZANARIS_RING);
		assertSame(ring, staffEligibility(false).wrapFairyRingStaff(ring));
	}

	@Test
	public void wrapPassesThroughNonFairyTransports()
	{
		assertSame(FALADOR_TELEPORT, staffEligibility(true).wrapFairyRingStaff(FALADOR_TELEPORT));
	}

	@Test
	public void wrapExcludesThePohRing()
	{
		Transport pohRing = fairyRingAt(POH_RING);
		assertSame(pohRing, staffEligibility(true).wrapFairyRingStaff(pohRing));
		assertNull("an unwrapped POH ring stays staff-free", pohRing.getItemRequirements());
	}

	@Test
	public void wrapMergesExistingRequirementsAsAnd()
	{
		// A ring that also declared an item requirement would need both —
		// the merge ANDs the injected staff onto the existing OR groups.
		TransportItems law = new TransportItems(
			new int[][]{ItemVariations.fromName("LAW_RUNE").getIds()},
			new int[][]{null}, new int[][]{null}, new int[]{1});
		Transport ring = new Transport.TransportBuilder().type(TransportType.FAIRY_RING)
			.origin(ZANARIS_RING).itemRequirements(law).build();

		Transport wrapped = staffEligibility(true).wrapFairyRingStaff(ring);
		assertEquals(2, wrapped.getItemRequirements().size());
	}

	@Test
	public void wrappedRingReadsTheStaffFromTheGenericPath()
	{
		Transport wrapped = staffEligibility(true).wrapFairyRingStaff(fairyRingAt(ZANARIS_RING));
		assertFalse(staffEligibility(true).usable(wrapped, BankVisitState.CARRIED));

		playerHas.put(ItemID.DRAMEN_STAFF, 1);
		assertTrue(staffEligibility(true).usable(wrapped, BankVisitState.CARRIED));
	}

	@Test
	public void wrappedRingUsableWithBankedStaffOnlyOnBankPath()
	{
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		TransportEligibility eligibility = staffEligibility(true);
		Transport wrapped = eligibility.wrapFairyRingStaff(fairyRingAt(ZANARIS_RING));
		assertFalse(eligibility.usable(wrapped, BankVisitState.CARRIED));
		assertTrue(eligibility.usable(wrapped, BankVisitState.BANKED));
	}

	@Test
	public void everyTeleportationItemModeLeavesTheWrappedRingEvaluatingRequirements()
	{
		// The verdict dispatch never reaches fairy rings: NONE must not
		// reject the ring outright, and ALL must not bypass the staff.
		Transport wrapped = staffEligibility(true).wrapFairyRingStaff(fairyRingAt(ZANARIS_RING));
		for (TeleportationItem mode : TeleportationItem.values())
		{
			TransportEligibility lacking = eligibility(mode, true, Integer.MAX_VALUE);
			assertFalse(mode + " must not bypass the staff requirement",
				lacking.usable(wrapped, BankVisitState.CARRIED));

			playerHas.put(ItemID.DRAMEN_STAFF, 1);
			TransportEligibility carrying = eligibility(mode, true, Integer.MAX_VALUE);
			assertTrue(mode + " must satisfy the staff requirement",
				carrying.usable(wrapped, BankVisitState.CARRIED));
			playerHas.clear();
		}
	}

	@Test
	public void wrappedRingStaffRequirementHonoursBlockedItems()
	{
		Transport wrapped = staffEligibility(true).wrapFairyRingStaff(fairyRingAt(ZANARIS_RING));
		Set<Integer> allStaffIds = new HashSet<>();
		for (int id : ItemVariations.fromName("DRAMEN_STAFF").getIds())
		{
			allStaffIds.add(id);
		}
		assertFalse("blocking every staff variant disables the ring",
			wrapped.getItemRequirements().survivesBlockedItems(allStaffIds));
		assertTrue("a partial block leaves an unblocked branch",
			wrapped.getItemRequirements().survivesBlockedItems(Set.of(ItemID.DRAMEN_STAFF)));
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
			staffRequired, setting, threshold, Set.of(), true, Set.of());
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
		return teleportItem(requirements, false);
	}

	private static Transport teleportItem(TransportItems requirements, boolean consumable)
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_ITEM)
			.isConsumable(consumable)
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
