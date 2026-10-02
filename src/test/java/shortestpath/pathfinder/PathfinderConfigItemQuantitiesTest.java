package shortestpath.pathfinder;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Quantities of an item held in several places are summed when checking
 * teleport requirements. Uses Varrock Teleport (3 air, 1 fire, 1 law rune).
 */
@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigItemQuantitiesTest
{
	private static final String VARROCK_TELEPORT = "Varrock Teleport";
	private static final int POUCH_AIR = 1;

	@Mock
	private Client client;
	@Mock
	private ShortestPathConfig config;
	@Mock
	private ItemContainer inventory;
	@Mock
	private ItemContainer bank;
	@Mock
	private EnumComposition runePouchEnum;

	private PathfinderConfig pathfinderConfig;

	@Test
	public void runePouchRunesAddToLooseRunes()
	{
		// 1 loose air rune plus 2 in the pouch makes the 3 air runes Varrock Teleport needs
		setupInventory(new Item(ItemID.AIRRUNE, 1), new Item(ItemID.FIRERUNE, 1), new Item(ItemID.LAWRUNE, 1),
			new Item(ItemID.BH_RUNE_POUCH, 1));
		when(client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(runePouchEnum);
		when(runePouchEnum.getIntValue(POUCH_AIR)).thenReturn(ItemID.AIRRUNE);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)).thenReturn(POUCH_AIR);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(2);

		refresh(false, TeleportationItem.NONE);

		assertTrue(varrockTeleportUsable(false));
	}

	@Test
	public void bankedRunesAddToCarriedRunes()
	{
		// 2 carried air runes plus 1 in the bank makes 3 on the bank path, not 1
		setupInventory(new Item(ItemID.AIRRUNE, 2), new Item(ItemID.FIRERUNE, 1), new Item(ItemID.LAWRUNE, 1));
		when(bank.getItems()).thenReturn(new Item[]{new Item(ItemID.AIRRUNE, 1)});

		refresh(true, TeleportationItem.INVENTORY_AND_BANK);

		assertFalse(varrockTeleportUsable(false));
		assertTrue(varrockTeleportUsable(true));
	}

	@Test
	public void bankedRunePouchRunesAddToLooseBankedRunes()
	{
		// A banked pouch with 2 air plus 1 loose banked air makes 3 on the bank path
		setupInventory(new Item(ItemID.FIRERUNE, 1), new Item(ItemID.LAWRUNE, 1));
		when(bank.getItems()).thenReturn(new Item[]{new Item(ItemID.BH_RUNE_POUCH, 1), new Item(ItemID.AIRRUNE, 1)});
		when(client.getEnum(EnumID.RUNEPOUCH_RUNE)).thenReturn(runePouchEnum);
		when(runePouchEnum.getIntValue(POUCH_AIR)).thenReturn(ItemID.AIRRUNE);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)).thenReturn(POUCH_AIR);
		when(client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(2);

		refresh(true, TeleportationItem.INVENTORY_AND_BANK);

		assertFalse(varrockTeleportUsable(false));
		assertTrue(varrockTeleportUsable(true));
	}

	private void setupInventory(Item... items)
	{
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		when(inventory.getItems()).thenReturn(items);
	}

	private void refresh(boolean includeBankPath, TeleportationItem useTeleportationItems)
	{
		pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED, true, true);
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.useTeleportationSpells()).thenReturn(true);
		when(config.useTeleportationItems()).thenReturn(useTeleportationItems);
		when(config.includeBankPath()).thenReturn(includeBankPath);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		pathfinderConfig.bank = bank;
		pathfinderConfig.refresh();
	}

	private boolean varrockTeleportUsable(boolean bankVisited)
	{
		return Arrays.stream(pathfinderConfig.getUsableTeleports(bankVisited))
			.anyMatch(t -> VARROCK_TELEPORT.equals(t.getDisplayInfo()));
	}
}
