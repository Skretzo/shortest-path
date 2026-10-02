package shortestpath.transport;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OwnedItemsTest
{
	private static final int POUCH_AIR = 1;
	private static final int POUCH_LAW = 2;

	@Mock
	private Client client;
	@Mock
	private ItemContainer inventory;
	@Mock
	private ItemContainer equipment;
	@Mock
	private EnumComposition runePouchEnum;

	private final Map<Integer, Integer> owned = new HashMap<>();

	@Test
	public void quantitiesAreSummedAcrossContainers()
	{
		when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.COINS, 100)});
		when(equipment.getItems()).thenReturn(new Item[]{new Item(ItemID.COINS, 5)});

		OwnedItems.addContainer(owned, inventory);
		OwnedItems.addContainer(owned, equipment);

		assertEquals(Integer.valueOf(105), owned.get(ItemID.COINS));
	}

	@Test
	public void emptySlotsAndMissingContainersAreIgnored()
	{
		when(inventory.getItems()).thenReturn(new Item[]{new Item(-1, 0), new Item(ItemID.COINS, 0)});

		OwnedItems.addContainer(owned, inventory);
		OwnedItems.addContainer(owned, null);

		assertTrue(owned.isEmpty());
	}

	@Test
	public void runePouchRunesAddToLooseRunes()
	{
		// 1000 loose air runes plus 2 in the pouch is 1002, not 2
		when(inventory.getItems()).thenReturn(new Item[]{
			new Item(ItemID.AIRRUNE, 1000), new Item(ItemID.BH_RUNE_POUCH, 1)});
		setupRunePouch(2, 5);

		OwnedItems.addContainer(owned, inventory);
		OwnedItems.addRunePouchContents(client, owned);

		assertEquals(Integer.valueOf(1002), owned.get(ItemID.AIRRUNE));
		assertEquals(Integer.valueOf(5), owned.get(ItemID.LAWRUNE));
	}

	@Test
	public void runePouchIsOnlyReadWhenOwned()
	{
		OwnedItems.addRunePouchContents(client, owned);

		assertTrue(owned.isEmpty());
		verifyNoInteractions(client);
	}

	@Test
	public void runePouchContentsAreDecodedAsRuneIdToAmount()
	{
		setupRunePouch(2, 5);

		assertEquals(Map.of(ItemID.AIRRUNE, 2, ItemID.LAWRUNE, 5), OwnedItems.runePouchContents(client));
	}

	private void setupRunePouch(int airRunes, int lawRunes)
	{
		when(client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(runePouchEnum);
		when(runePouchEnum.getIntValue(POUCH_AIR)).thenReturn(ItemID.AIRRUNE);
		when(runePouchEnum.getIntValue(POUCH_LAW)).thenReturn(ItemID.LAWRUNE);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)).thenReturn(POUCH_AIR);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(airRunes);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_2)).thenReturn(POUCH_LAW);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_2)).thenReturn(lawRunes);
	}
}
