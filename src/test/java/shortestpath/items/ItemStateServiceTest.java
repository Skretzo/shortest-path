package shortestpath.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.requirement.BankPickupRequirements.BankPickupResult;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Pins the item-state producer seam contract: which game inputs the service
 * admits, which {@link ItemChange} fact each admitted input declares to the
 * coordinator, and the ownership rules for the live open-bank reference. The
 * coordinator relies on a declared fact meaning "a tracked input changed" and
 * on the declared {@link Effect}s to decide follow-up actions, so silent
 * inputs and declares the old return-null paths guarded are the failure
 * modes these tests guard against. The adversarial half of this file covers
 * the collection boundary (null/empty containers, non-positive quantities,
 * banked-pouch selection, bank-path pooling) and the bank-pickup cache's
 * (path identity, index, dirty) contract.
 */
public class ItemStateServiceTest
{
	private static void assertEligibilityStale(ItemChange change, String what)
	{
		assertNotNull(what + " produces a change fact", change);
		assertTrue(what + " declares ELIGIBILITY_STALE",
			change.getEffects().contains(Effect.ELIGIBILITY_STALE));
	}

	/**
	 * The fact the service most recently declared on the coordinator,
	 * asserting the channel saw exactly {@code declared} calls so a dropped
	 * or duplicated declare fails here rather than downstream.
	 */
	private static ItemChange lastDeclared(RefreshCoordinator coordinator, int declared)
	{
		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(declared)).itemsChanged(captor.capture());
		return captor.getValue();
	}

	@Test
	public void bankContainerIsStoredAsLiveRefAndDeclaresEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);
		ItemContainer bank = mock(ItemContainer.class);

		service.onContainerChanged(InventoryID.BANK, bank);

		assertEligibilityStale(lastDeclared(coordinator, 1), "bank container event");
		assertSame("the open bank stays a live reference, never copied", bank, service.getBank());
	}

	@Test
	public void inventoryContainerDeclaresEligibilityStaleButKeepsBankRef()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);
		ItemContainer bank = mock(ItemContainer.class);
		ItemContainer inv = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		service.onContainerChanged(InventoryID.INV, inv);

		assertEligibilityStale(lastDeclared(coordinator, 2), "inventory container event");
		assertSame("inventory events never overwrite the bank ref", bank, service.getBank());
	}

	@Test
	public void wornContainerDeclaresEligibilityStaleButKeepsBankRef()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);
		ItemContainer bank = mock(ItemContainer.class);
		ItemContainer worn = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		service.onContainerChanged(InventoryID.WORN, worn);

		assertEligibilityStale(lastDeclared(coordinator, 2), "equipment container event");
		assertSame("equipment events never overwrite the bank ref", bank, service.getBank());
	}

	@Test
	public void unrelatedContainerDeclaresNothingAndLeavesTheBankRef()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);
		ItemContainer bank = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		service.onContainerChanged(InventoryID.LOOTING_BAG, mock(ItemContainer.class));

		verify(coordinator, times(1)).itemsChanged(any());
		assertSame("an untracked container never overwrites the bank ref", bank, service.getBank());
	}

	@Test
	public void lumbridgeDiaryVarbitDeclaresEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		service.onVarbitChanged(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);

		assertEligibilityStale(lastDeclared(coordinator, 1), "Lumbridge diary varbit");
	}

	@Test
	public void runePouchRuneVarbitsDeclareEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		for (int varbitId : OwnedItems.RUNE_POUCH_RUNE_VARBITS)
		{
			service.onVarbitChanged(varbitId);
		}

		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(OwnedItems.RUNE_POUCH_RUNE_VARBITS.length))
			.itemsChanged(captor.capture());
		for (ItemChange change : captor.getAllValues())
		{
			assertEligibilityStale(change, "rune pouch rune varbit fact " + change.getKey());
		}
	}

	@Test
	public void runePouchAmountVarbitsDeclareEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		for (int varbitId : OwnedItems.RUNE_POUCH_AMOUNT_VARBITS)
		{
			service.onVarbitChanged(varbitId);
		}

		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(OwnedItems.RUNE_POUCH_AMOUNT_VARBITS.length))
			.itemsChanged(captor.capture());
		for (ItemChange change : captor.getAllValues())
		{
			assertEligibilityStale(change, "rune pouch amount varbit fact " + change.getKey());
		}
	}

	@Test
	public void unrelatedVarbitDeclaresNothing()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		service.onVarbitChanged(VarbitID.FAIRY2_QUEENCURE_QUEST);

		verify(coordinator, never()).itemsChanged(any());
	}

	@Test
	public void noteBankContainerSeedsTheBankRefForHarnesses()
	{
		ItemStateService service = ItemStateService.forTesting();
		ItemContainer bank = mock(ItemContainer.class);

		service.noteBankContainer(bank);

		assertSame(bank, service.getBank());
	}

	@Test
	public void changeFactExposesKeyAndEffectsAsAValue()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		service.onContainerChanged(InventoryID.BANK, mock(ItemContainer.class));
		service.onContainerChanged(InventoryID.BANK, mock(ItemContainer.class));

		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(2)).itemsChanged(captor.capture());
		ItemChange first = captor.getAllValues().get(0);
		ItemChange second = captor.getAllValues().get(1);

		assertEquals("container:" + InventoryID.BANK, first.getKey());
		assertNotSame("each observed change is its own fact", first, second);
		try
		{
			first.getEffects().add(Effect.DISPLAY_ONLY);
			fail("the fact's effect set must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
		}
	}

	// ------------------------------------------------------------------
	// collectEligibility — adversarial container and pouch cases
	// ------------------------------------------------------------------

	private static final Transport THREE_AIR = needsItem(ItemVariations.AIR_RUNE.getIds(), 3);
	private static final Transport SHANTAY_GATE = needsItem(ItemVariations.SHANTAY_PASS.getIds(), 1);

	private static ItemContainer containerOf(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		return container;
	}

	/**
	 * A {@code PlayerStateSource} Mockito stub: inventory/worn containers and
	 * the pouch runes are fixed; {@code addRunePouchContents} behaves like the
	 * real source — the pouch runes land in {@code owned} only when a pouch is
	 * already there. The Lumbridge diary varbit reads 0 (incomplete).
	 */
	private static PlayerStateSource sourceWith(ItemContainer inventory, ItemContainer worn,
		Map<Integer, Integer> pouchRunes)
	{
		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.itemContainer(InventoryID.INV)).thenReturn(inventory);
		when(source.itemContainer(InventoryID.WORN)).thenReturn(worn);
		when(source.runePouchContents()).thenReturn(pouchRunes);
		doAnswer(invocation ->
		{
			Map<Integer, Integer> owned = invocation.getArgument(0);
			if (OwnedItems.RUNE_POUCHES.stream().anyMatch(owned::containsKey))
			{
				pouchRunes.forEach((runeId, amount) -> owned.merge(runeId, amount, Integer::sum));
			}
			return null;
		}).when(source).addRunePouchContents(any());
		return source;
	}

	private static TransportEligibility collect(PlayerStateSource source, ItemContainer bank,
		TeleportationItem setting, boolean includeBankPath)
	{
		return ItemStateService.collectEligibility(source, bank, setting,
			Integer.MAX_VALUE, includeBankPath, Set.<Unlock>of());
	}

	private static Transport needsItem(int[] itemIds, int quantity)
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TRANSPORT)
			.itemRequirements(new TransportItems(List.of(
				new ItemRequirement(itemIds, new int[0], new int[0], quantity))))
			.build();
	}

	@Test
	public void collectEligibilityToleratesNullContainers()
	{
		PlayerStateSource source = sourceWith(null, null, Map.of());

		TransportEligibility eligibility = collect(source, null,
			TeleportationItem.INVENTORY_AND_BANK, true);

		assertNotNull(eligibility);
		assertFalse("no pool satisfies an item requirement without containers",
			eligibility.usable(THREE_AIR, BankVisitState.CARRIED));
		assertFalse(eligibility.usable(THREE_AIR, BankVisitState.BANKED));
	}

	@Test
	public void collectEligibilityToleratesEmptyContainers()
	{
		PlayerStateSource source = sourceWith(containerOf(), containerOf(), Map.of());

		TransportEligibility eligibility = collect(source, containerOf(),
			TeleportationItem.INVENTORY_AND_BANK, true);

		assertNotNull(eligibility);
		assertFalse(eligibility.usable(THREE_AIR, BankVisitState.CARRIED));
		assertFalse(eligibility.usable(THREE_AIR, BankVisitState.BANKED));
	}

	@Test
	public void nonPositiveQuantitiesAreSkippedByCollection()
	{
		// A zero-quantity stack and a placeholder (negative id) slot must not
		// count as owned items — the bank effectively holds nothing usable.
		ItemContainer bank = containerOf(new Item(ItemID.AIRRUNE, 0), new Item(-1, 5));
		PlayerStateSource source = sourceWith(null, null, Map.of());

		TransportEligibility eligibility = collect(source, bank,
			TeleportationItem.INVENTORY_AND_BANK, true);

		assertFalse("a zero-quantity air rune cannot satisfy the requirement",
			eligibility.usable(needsItem(ItemVariations.AIR_RUNE.getIds(), 1), BankVisitState.BANKED));
	}

	@Test
	public void bankSeededViaNoteBankContainerFeedsTheBankedPool()
	{
		ItemStateService service = ItemStateService.forTesting();
		service.noteBankContainer(containerOf(new Item(ItemID.SHANTAY_PASS, 1)));
		PlayerStateSource source = sourceWith(null, null, Map.of());

		TransportEligibility eligibility = collect(source, service.getBank(),
			TeleportationItem.INVENTORY_AND_BANK, true);

		assertFalse("the bank item is not carried", eligibility.usable(SHANTAY_GATE, BankVisitState.CARRIED));
		assertTrue("the bank item enters the bank-path pool", eligibility.usable(SHANTAY_GATE, BankVisitState.BANKED));
	}

	@Test
	public void includeBankPathOffLeavesBankItemsOutOfTheBankedPool()
	{
		ItemStateService service = ItemStateService.forTesting();
		service.noteBankContainer(containerOf(new Item(ItemID.SHANTAY_PASS, 1)));
		PlayerStateSource source = sourceWith(null, null, Map.of());

		TransportEligibility eligibility = collect(source, service.getBank(),
			TeleportationItem.INVENTORY_AND_BANK, false);

		assertFalse("with bank paths off the banked pool is the carried pool",
			eligibility.usable(SHANTAY_GATE, BankVisitState.BANKED));
	}

	@Test
	public void bankedRunePouchSuppliesItsRunesOnTheBankPath()
	{
		ItemStateService service = ItemStateService.forTesting();
		service.noteBankContainer(containerOf(new Item(ItemID.BH_RUNE_POUCH, 1)));
		PlayerStateSource source = sourceWith(null, null, Map.of(ItemID.AIRRUNE, 3));

		TransportEligibility eligibility = collect(source, service.getBank(),
			TeleportationItem.INVENTORY_AND_BANK, true);

		assertFalse("pouch runes are not carried", eligibility.usable(THREE_AIR, BankVisitState.CARRIED));
		assertTrue("a banked pouch's runes feed the bank-path pool",
			eligibility.usable(THREE_AIR, BankVisitState.BANKED));
	}

	@Test
	public void firstPouchInDeclaredOrderIsSelected()
	{
		// The bank holds two pouch variants; the selector must pick the one
		// earliest in the declared pouch order (BH before DIVINE).
		ItemStateService service = ItemStateService.forTesting();
		service.noteBankContainer(containerOf(
			new Item(ItemID.DIVINE_RUNE_POUCH, 1), new Item(ItemID.BH_RUNE_POUCH, 1)));
		PlayerStateSource source = sourceWith(null, null, Map.of(ItemID.AIRRUNE, 3));

		TransportEligibility eligibility = collect(source, service.getBank(),
			TeleportationItem.INVENTORY_AND_BANK, true);
		TransportEligibility.BankPickupPlan plan = eligibility.bankPickupPlan(THREE_AIR);

		assertTrue("the declared-first pouch supplies the runes",
			plan.bankItemIds.contains(ItemID.BH_RUNE_POUCH));
		assertFalse("a later pouch is never the selected one",
			plan.bankItemIds.contains(ItemID.DIVINE_RUNE_POUCH));
	}

	@Test
	public void bankWithoutPouchNeverConsultsPouchRunes()
	{
		// The source reports pouch runes, but without a pouch in the bank the
		// bank-pouch rune pool must stay empty and the pickup unsatisfiable.
		ItemStateService service = ItemStateService.forTesting();
		service.noteBankContainer(containerOf());
		PlayerStateSource source = sourceWith(null, null, Map.of(ItemID.AIRRUNE, 3));

		TransportEligibility eligibility = collect(source, service.getBank(),
			TeleportationItem.INVENTORY_AND_BANK, true);

		assertFalse(eligibility.usable(THREE_AIR, BankVisitState.BANKED));
		assertNull("the bank cannot supply the runes", eligibility.bankPickupPlan(THREE_AIR).items);
	}

	// ------------------------------------------------------------------
	// Bank-pickup projection — (path, index) identity cache and dirty flag
	// ------------------------------------------------------------------

	private static PathfinderConfig pickupConfig()
	{
		PathfinderConfig config = mock(PathfinderConfig.class);
		// The bank-location set is only consulted to gate "is this a bank
		// step"; a real set keeps the compute seam reachable while the test
		// path deliberately misses it, yielding the empty result.
		when(config.getDestinations("bank")).thenReturn(Set.of(12345));
		return config;
	}

	private static ItemStateService serviceWithBank()
	{
		ItemStateService service = ItemStateService.forTesting();
		service.noteBankContainer(mock(ItemContainer.class));
		return service;
	}

	@Test
	public void getBankPickupGuardsRejectBadInputs()
	{
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED));

		assertNull("no open bank means no projection",
			ItemStateService.forTesting().getBankPickup(path, 0, config));

		ItemStateService service = serviceWithBank();
		assertNull("null path is rejected", service.getBankPickup(null, 0, config));
		assertNull("negative index is rejected", service.getBankPickup(path, -1, config));
		assertNull("out-of-range index is rejected",
			service.getBankPickup(path, path.size(), config));

		when(config.getDestinations("bank")).thenReturn(null);
		assertNull("a missing bank-destination set is rejected",
			service.getBankPickup(path, 0, config));
	}

	@Test
	public void bankPickupCacheHitsOnSamePathAndIndexIdentity()
	{
		ItemStateService service = serviceWithBank();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED), new PathStep(888, BankVisitState.CARRIED));

		BankPickupResult first = service.getBankPickup(path, 0, config);
		BankPickupResult second = service.getBankPickup(path, 0, config);

		assertSame("same (path identity, index) reuses the cached result", first, second);
	}

	@Test
	public void bankPickupCacheMissesOnDifferentIndexOrPathInstance()
	{
		ItemStateService service = serviceWithBank();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED), new PathStep(888, BankVisitState.CARRIED));

		BankPickupResult first = service.getBankPickup(path, 0, config);

		assertNotSame("a different index recomputes", first, service.getBankPickup(path, 1, config));

		List<PathStep> equalButSeparate = List.of(new PathStep(999, BankVisitState.CARRIED), new PathStep(888, BankVisitState.CARRIED));
		assertNotSame("an equal-but-separate path list recomputes — the key is identity",
			first, service.getBankPickup(equalButSeparate, 0, config));
	}

	@Test
	public void markBankPickupDirtyForcesRecompute()
	{
		ItemStateService service = serviceWithBank();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED));

		BankPickupResult first = service.getBankPickup(path, 0, config);
		service.markBankPickupDirty();

		assertNotSame("the explicit dirty mark defeats the cache",
			first, service.getBankPickup(path, 0, config));
	}

	@Test
	public void containerAndVarbitEventsDirtyThePickupCache()
	{
		ItemStateService service = serviceWithBank();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED));

		BankPickupResult first = service.getBankPickup(path, 0, config);
		service.onContainerChanged(InventoryID.INV, mock(ItemContainer.class));
		BankPickupResult afterContainer = service.getBankPickup(path, 0, config);
		assertNotSame("a tracked container event recomputes", first, afterContainer);

		service.onVarbitChanged(OwnedItems.RUNE_POUCH_RUNE_VARBITS[0]);
		assertNotSame("a tracked varbit event recomputes",
			afterContainer, service.getBankPickup(path, 0, config));
	}
}
