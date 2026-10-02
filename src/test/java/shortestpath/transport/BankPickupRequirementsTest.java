package shortestpath.transport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ItemVariations;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BankPickupRequirementsTest
{
	private static final int POUCH_AIR = 1;
	private static final int NO_POUCH = -1;
	private static final int[] NO_SUBSTITUTES = new int[0];
	// Falador Teleport: 1 law, 3 air, 1 water
	private static final Transport FALADOR_TELEPORT = teleport(
		rune(ItemVariations.LAW_RUNE, 1), rune(ItemVariations.AIR_RUNE, 3), rune(ItemVariations.WATER_RUNE, 1));
	private static final Transport THREE_AIR = teleport(rune(ItemVariations.AIR_RUNE, 3));

	@Mock
	private Client client;
	@Mock
	private ItemContainer inventory;
	@Mock
	private ItemContainer equipment;
	@Mock
	private EnumComposition runePouchEnum;
	@Mock
	private ItemComposition airRune;
	@Mock
	private ItemComposition waterRune;

	private final Map<Integer, Integer> playerHas = new HashMap<>();
	private final Map<Integer, Integer> bankHas = new HashMap<>();
	private final Map<Integer, Integer> bankPouchRunes = new HashMap<>();

	@Test
	public void collectPlayerItemsCountsInventoryEquipmentAndRunePouch()
	{
		setupPlayerItems();
		when(client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(runePouchEnum);
		when(runePouchEnum.getIntValue(POUCH_AIR)).thenReturn(ItemID.AIRRUNE);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)).thenReturn(POUCH_AIR);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(5);

		Map<Integer, Integer> collected = BankPickupRequirements.collectPlayerItems(client);

		assertEquals(Integer.valueOf(15), collected.get(ItemID.AIRRUNE));
		assertEquals(Integer.valueOf(1), collected.get(ItemID.STAFF_OF_FIRE));
		assertEquals(Integer.valueOf(1), collected.get(ItemID.BH_RUNE_POUCH));
	}

	@Test
	public void pickupAsksOnlyForTheShortfall()
	{
		when(client.getItemDefinition(ItemID.AIRRUNE)).thenReturn(airRune);
		when(client.getItemDefinition(ItemID.WATERRUNE)).thenReturn(waterRune);
		when(airRune.getName()).thenReturn("Air rune");
		when(waterRune.getName()).thenReturn("Water rune");
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 2);
		bankHas.put(ItemID.AIRRUNE, 1000);
		bankHas.put(ItemID.WATERRUNE, 1000);

		Map<Integer, Long> pickups = pickups(FALADOR_TELEPORT, NO_POUCH);

		assertEquals("1 Air rune, 1 Water rune", BankPickupRequirements.formatPickups(client, pickups));
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

	private Map<Integer, Long> pickups(Transport transport, int bankPouchId)
	{
		return BankPickupRequirements.computeBankPickups(transport, playerHas, bankHas, bankPouchId, bankPouchRunes);
	}

	private Set<Integer> highlighted(Transport transport, int bankPouchId)
	{
		Set<Integer> itemIds = new HashSet<>();
		BankPickupRequirements.collectPartialBankItemIds(
			transport, playerHas, bankHas, bankPouchId, bankPouchRunes, itemIds);
		return itemIds;
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

	private void setupPlayerItems()
	{
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		doReturn(equipment).when(client).getItemContainer(InventoryID.WORN);
		when(inventory.getItems()).thenReturn(new Item[]{
			new Item(ItemID.AIRRUNE, 10), new Item(ItemID.BH_RUNE_POUCH, 1)});
		when(equipment.getItems()).thenReturn(new Item[]{new Item(ItemID.STAFF_OF_FIRE, 1)});
	}
}
