package shortestpath.pathfinder;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Members rune sources (rune pouch contents, combination runes) don't count toward
 * teleport requirements on free-to-play worlds with members-only filtering on.
 * Uses Varrock Teleport (3 air, 1 fire, 1 law rune), which is free-to-play.
 */
@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigMembersItemsTest
{
	private static final String VARROCK_TELEPORT = "Varrock Teleport";
	private static final int POUCH_AIR = 1;
	private static final int POUCH_FIRE = 2;
	private static final int POUCH_LAW = 3;

	@Mock
	private Client client;
	@Mock
	private ShortestPathConfig config;
	@Mock
	private ItemContainer inventory;
	@Mock
	private EnumComposition runePouchEnum;
	@Mock
	private ItemComposition f2pItem;
	@Mock
	private ItemComposition membersItem;

	private PathfinderConfig pathfinderConfig;

	@Test
	public void runePouchDoesNotCountOnFreeToPlayWorld()
	{
		setupInventory(new Item(ItemID.BH_RUNE_POUCH, 1));
		setupMembersItems(ItemID.BH_RUNE_POUCH);
		// Lenient: the pouch contents must exist to show they are not read
		setupRunePouch(true, 100, 100, 100);

		refresh(true, false);

		assertFalse(varrockTeleportUsable());
	}

	@Test
	public void looseRunesStillCountOnFreeToPlayWorld()
	{
		setupInventory(new Item(ItemID.AIRRUNE, 3), new Item(ItemID.FIRERUNE, 1), new Item(ItemID.LAWRUNE, 1),
			new Item(ItemID.BH_RUNE_POUCH, 1));
		setupFreeToPlayItems(ItemID.AIRRUNE, ItemID.FIRERUNE, ItemID.LAWRUNE);
		setupMembersItems(ItemID.BH_RUNE_POUCH);
		// Lenient: the pouch contents must exist to show they are not read
		setupRunePouch(true, 100, 100, 100);

		refresh(true, false);

		assertTrue(varrockTeleportUsable());
	}

	@Test
	public void combinationRunesDoNotCountOnFreeToPlayWorld()
	{
		// Dust runes would otherwise stand in for air runes
		setupInventory(new Item(ItemID.DUSTRUNE, 100), new Item(ItemID.FIRERUNE, 1), new Item(ItemID.LAWRUNE, 1));
		setupFreeToPlayItems(ItemID.FIRERUNE, ItemID.LAWRUNE);
		setupMembersItems(ItemID.DUSTRUNE);

		refresh(true, false);

		assertFalse(varrockTeleportUsable());
	}

	@Test
	public void runePouchCountsOnMembersWorld()
	{
		setupInventory(new Item(ItemID.BH_RUNE_POUCH, 1));
		setupRunePouch(false, 100, 100, 100);

		refresh(true, true);

		assertTrue(varrockTeleportUsable());
	}

	@Test
	public void runePouchCountsWhenFilteringIsOff()
	{
		setupInventory(new Item(ItemID.BH_RUNE_POUCH, 1));
		setupRunePouch(false, 100, 100, 100);

		refresh(false, false);

		assertTrue(varrockTeleportUsable());
	}

	@Test
	public void membersFlagIsLookedUpOncePerItem()
	{
		setupInventory(new Item(ItemID.DUSTRUNE, 100), new Item(ItemID.FIRERUNE, 1), new Item(ItemID.LAWRUNE, 1));
		setupFreeToPlayItems(ItemID.FIRERUNE, ItemID.LAWRUNE);
		setupMembersItems(ItemID.DUSTRUNE);

		refresh(true, false);
		pathfinderConfig.refresh();

		verify(client, times(1)).getItemDefinition(ItemID.DUSTRUNE);
		verify(client, times(1)).getItemDefinition(ItemID.FIRERUNE);
	}

	@Test
	public void noItemLookupsWhenMembersItemsAreUsable()
	{
		setupInventory(new Item(ItemID.DUSTRUNE, 100), new Item(ItemID.FIRERUNE, 1), new Item(ItemID.LAWRUNE, 1));

		refresh(false, false);
		refresh(true, true);

		verify(client, never()).getItemDefinition(anyInt());
	}

	private void setupInventory(Item... items)
	{
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		when(inventory.getItems()).thenReturn(items);
	}

	private void setupFreeToPlayItems(int... itemIds)
	{
		when(f2pItem.isMembers()).thenReturn(false);
		for (int itemId : itemIds)
		{
			when(client.getItemDefinition(itemId)).thenReturn(f2pItem);
		}
	}

	private void setupMembersItems(int... itemIds)
	{
		when(membersItem.isMembers()).thenReturn(true);
		for (int itemId : itemIds)
		{
			when(client.getItemDefinition(itemId)).thenReturn(membersItem);
		}
	}

	private void setupRunePouch(boolean lenient, int airRunes, int fireRunes, int lawRunes)
	{
		stub(lenient, client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(runePouchEnum);
		stub(lenient, runePouchEnum.getIntValue(POUCH_AIR)).thenReturn(ItemID.AIRRUNE);
		stub(lenient, runePouchEnum.getIntValue(POUCH_FIRE)).thenReturn(ItemID.FIRERUNE);
		stub(lenient, runePouchEnum.getIntValue(POUCH_LAW)).thenReturn(ItemID.LAWRUNE);
		stub(lenient, client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)).thenReturn(POUCH_AIR);
		stub(lenient, client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(airRunes);
		stub(lenient, client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_2)).thenReturn(POUCH_FIRE);
		stub(lenient, client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_2)).thenReturn(fireRunes);
		stub(lenient, client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_3)).thenReturn(POUCH_LAW);
		stub(lenient, client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_3)).thenReturn(lawRunes);
	}

	private static <T> OngoingStubbing<T> stub(boolean lenient, T methodCall)
	{
		return lenient ? lenient().when(methodCall) : when(methodCall);
	}

	private void refresh(boolean filterEnabled, boolean membersWorld)
	{
		pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED, true, true);
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.useTeleportationSpells()).thenReturn(true);
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(config.filterMembersTransportsOnF2p()).thenReturn(filterEnabled);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getWorldType()).thenReturn(membersWorld
			? EnumSet.of(WorldType.MEMBERS)
			: EnumSet.noneOf(WorldType.class));
		pathfinderConfig.refresh();
	}

	private boolean varrockTeleportUsable()
	{
		return Arrays.stream(pathfinderConfig.getUsableTeleports(false))
			.anyMatch(t -> VARROCK_TELEPORT.equals(t.getDisplayInfo()));
	}
}
