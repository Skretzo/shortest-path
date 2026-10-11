package shortestpath.pathfinder;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HardwoodGroveTest
{
	@Test
	public void eliteDiaryReplacesTradingStickPaymentAtBothDoors()
	{
		Client client = mock(Client.class);
		ShortestPathConfig config = mock(ShortestPathConfig.class);
		ItemContainer inventory = mock(ItemContainer.class);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(config.currencyThreshold()).thenReturn(100);
		PathfinderConfig pathfinder = new TestPathfinderConfig(client, config, QuestState.FINISHED, false, true);
		for (int diary : new int[]{0, 1})
		{
			when(client.getVarbitValue(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE)).thenReturn(diary);
			for (int sticks : new int[]{0, 99, 100})
			{
				when(inventory.getItems()).thenReturn(new Item[]{new Item(6306, sticks)});
				pathfinder.refresh();
				for (int y : new int[]{3083, 3084})
				{
					Transport[] entries = Arrays.stream(pathfinder.getTransportsPacked(false)
						.getOrDefault(WorldPointUtil.packWorldPoint(2816, y, 0), TransportAvailability.EMPTY_TRANSPORTS))
						.filter(t -> t.getDestination() == WorldPointUtil.packWorldPoint(2817, y, 0))
						.toArray(Transport[]::new);
					assertEquals(diary == 1 || sticks == 100 ? 1 : 0, entries.length);
					if (diary == 1)
					{
						assertTrue(entries[0].getItemRequirements() == null || entries[0].getItemRequirements().size() == 0);
					}
					assertTrue(Arrays.stream(pathfinder.getTransportsPacked(false)
						.getOrDefault(WorldPointUtil.packWorldPoint(2817, y, 0), TransportAvailability.EMPTY_TRANSPORTS))
						.anyMatch(t -> t.getDestination() == WorldPointUtil.packWorldPoint(2816, y, 0)));
				}
			}
		}
	}
}
