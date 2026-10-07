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
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * The consumption validator's transport exclusions survive {@link
 * PathfinderConfig#refresh()} — a re-plan's refresh is exactly where they must
 * apply — and {@link PathfinderConfig#clearExcludedTransports()} restores the
 * excluded transport on the next refresh. Uses Varrock Teleport (3 air, 1 fire,
 * 1 law rune) as the excluded transport.
 */
@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigExclusionTest
{
	private static final String VARROCK_TELEPORT = "Varrock Teleport";

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
	public void anExcludedTransportStaysExcludedAcrossRefreshUntilCleared()
	{
		setupInventory(new Item(ItemID.AIRRUNE, 3), new Item(ItemID.FIRERUNE, 1),
			new Item(ItemID.LAWRUNE, 1));

		refresh();
		Transport varrockTeleport = Arrays.stream(pathfinderConfig.getUsableTeleports(false))
			.filter(t -> VARROCK_TELEPORT.equals(t.getDisplayInfo()))
			.findFirst()
			.orElseThrow(AssertionError::new);

		pathfinderConfig.excludeTransport(varrockTeleport);
		pathfinderConfig.refresh();

		assertFalse(varrockTeleportUsable());

		pathfinderConfig.clearExcludedTransports();
		pathfinderConfig.refresh();

		assertTrue(varrockTeleportUsable());
	}

	private void setupInventory(Item... items)
	{
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		when(inventory.getItems()).thenReturn(items);
	}

	private void refresh()
	{
		pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED, true, true);
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.useTeleportationSpells()).thenReturn(true);
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		pathfinderConfig.bank = bank;
		pathfinderConfig.refresh();
	}

	private boolean varrockTeleportUsable()
	{
		return Arrays.stream(pathfinderConfig.getUsableTeleports(false))
			.anyMatch(t -> VARROCK_TELEPORT.equals(t.getDisplayInfo()));
	}
}
