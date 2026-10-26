package shortestpath.requirement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import shortestpath.ItemVariations;
import shortestpath.items.ItemStateService;
import shortestpath.items.OwnedItems;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Adversarial container and pouch cases for {@link TransportEligibility#collect}
 * — the one eligibility collection path shared by {@code RequirementContext.capture}
 * and {@code PathfinderConfig#getEligibility()}.
 */
public class TransportEligibilityCollectTest
{
	// ------------------------------------------------------------------
	// TransportEligibility.collect — adversarial container and pouch cases
	// ------------------------------------------------------------------

	private static final Transport THREE_AIR = needsItem(ItemVariations.fromName("AIR_RUNE").getIds(), 3);
	private static final Transport SHANTAY_GATE = needsItem(ItemVariations.fromName("SHANTAY_PASS").getIds(), 1);

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
		return TransportEligibility.collect(source, bank, setting,
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
			eligibility.usable(needsItem(ItemVariations.fromName("AIR_RUNE").getIds(), 1), BankVisitState.BANKED));
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
}
