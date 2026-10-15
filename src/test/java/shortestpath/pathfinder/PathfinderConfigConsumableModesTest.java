package shortestpath.pathfinder;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.items.ItemStateService;
import shortestpath.settings.TeleportationItem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Adversarial coverage for the consumable gate the teleportation-item modes
 * apply inside {@code hasRequiredItems}: consumable item teleports must stay
 * unusable under every {@code *_NON_CONSUMABLE} mode — whether carried,
 * banked, or owned under an all-items mode — while the ownership modes keep
 * evaluating the item pool and non-consumable items survive. The necklace of
 * passage rows are consumable ({@code Consumable=T}); the eternal amulet of
 * glory rows are not.
 */
@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigConsumableModesTest
{
	private static final String NECKLACE_OF_PASSAGE = "Necklace of passage: Wizards' Tower";
	private static final String ETERNAL_GLORY = "Amulet of glory: Edgeville";
	private static final int NECKLACE_OF_PASSAGE_1 = 21146;
	private static final int AMULET_OF_ETERNAL_GLORY = 19707;

	@Mock
	private Client client;
	@Mock
	private ShortestPathConfig config;
	@Mock
	private ItemContainer inventory;
	@Mock
	private ItemContainer bank;

	private PathfinderConfig pathfinderConfig;

	@Test
	public void consumableRejectedInNonConsumableModesWhenCarried()
	{
		setupInventory(new Item(NECKLACE_OF_PASSAGE_1, 1));
		setupBank();
		for (TeleportationItem mode : new TeleportationItem[]{
			TeleportationItem.INVENTORY_NON_CONSUMABLE,
			TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE,
			TeleportationItem.ALL_NON_CONSUMABLE,
			TeleportationItem.UNLOCKED_NON_CONSUMABLE})
		{
			refresh(mode, true);
			assertFalse(mode + " must not surface a carried consumable",
				teleportUsable(NECKLACE_OF_PASSAGE, false));
			assertFalse(mode + " must not surface a carried consumable post-bank",
				teleportUsable(NECKLACE_OF_PASSAGE, true));
		}
	}

	@Test
	public void consumableRejectedInNonConsumableModesWhenOnlyBanked()
	{
		setupInventory();
		setupBank(new Item(NECKLACE_OF_PASSAGE_1, 1));
		refresh(TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE, true);
		assertFalse(teleportUsable(NECKLACE_OF_PASSAGE, false));
		assertFalse("banked consumable stays unusable under the banked non-consumable mode",
			teleportUsable(NECKLACE_OF_PASSAGE, true));
	}

	@Test
	public void consumableUsableInOwnershipModesWhenCarried()
	{
		setupInventory(new Item(NECKLACE_OF_PASSAGE_1, 1));
		setupBank();
		for (TeleportationItem mode : new TeleportationItem[]{
			TeleportationItem.INVENTORY,
			TeleportationItem.INVENTORY_AND_BANK})
		{
			refresh(mode, true);
			assertTrue(mode + " surfaces a carried consumable",
				teleportUsable(NECKLACE_OF_PASSAGE, false));
		}
	}

	@Test
	public void consumableUsableInAllModeWithoutOwnership()
	{
		setupInventory();
		setupBank();
		refresh(TeleportationItem.ALL, false);
		assertTrue(teleportUsable(NECKLACE_OF_PASSAGE, false));
		refresh(TeleportationItem.ALL_NON_CONSUMABLE, false);
		assertFalse("ALL_NON_CONSUMABLE rejects the consumable despite owning everything",
			teleportUsable(NECKLACE_OF_PASSAGE, false));
	}

	@Test
	public void nonConsumableSurvivesNonConsumableModes()
	{
		setupInventory(new Item(AMULET_OF_ETERNAL_GLORY, 1));
		setupBank();
		for (TeleportationItem mode : new TeleportationItem[]{
			TeleportationItem.INVENTORY_NON_CONSUMABLE,
			TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE})
		{
			refresh(mode, true);
			assertTrue(mode + " keeps a carried non-consumable usable",
				teleportUsable(ETERNAL_GLORY, false));
		}
		// ALL_NON_CONSUMABLE owns every item; the non-consumable survives on an empty inventory.
		setupInventory();
		setupBank();
		refresh(TeleportationItem.ALL_NON_CONSUMABLE, false);
		assertTrue(teleportUsable(ETERNAL_GLORY, false));
	}

	@Test
	public void noneModeRejectsItemTeleportsEntirely()
	{
		setupInventory(new Item(NECKLACE_OF_PASSAGE_1, 1), new Item(AMULET_OF_ETERNAL_GLORY, 1));
		setupBank();
		refresh(TeleportationItem.NONE, true);
		assertFalse(teleportUsable(NECKLACE_OF_PASSAGE, false));
		assertFalse(teleportUsable(ETERNAL_GLORY, false));
	}

	private void setupInventory(Item... items)
	{
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		when(inventory.getItems()).thenReturn(items);
	}

	private void setupBank(Item... items)
	{
		when(bank.getItems()).thenReturn(items);
	}

	private void refresh(TeleportationItem mode, boolean includeBankPath)
	{
		pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED, true, true);
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.useTeleportationSpells()).thenReturn(true);
		when(config.useTeleportationItems()).thenReturn(mode);
		when(config.includeBankPath()).thenReturn(includeBankPath);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		ItemStateService itemState = ItemStateService.forTesting();
		pathfinderConfig.setItemStateService(itemState);
		itemState.noteBankContainer(bank);
		pathfinderConfig.refresh();
	}

	private boolean teleportUsable(String displayInfo, boolean bankVisited)
	{
		return Arrays.stream(pathfinderConfig.getUsableTeleports(
				bankVisited ? BankVisitState.BANKED : BankVisitState.CARRIED))
			.anyMatch(t -> displayInfo.equals(t.getDisplayInfo()));
	}
}
