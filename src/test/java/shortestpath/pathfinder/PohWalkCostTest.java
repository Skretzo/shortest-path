package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.*;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.poh.PohService;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/** The house is one tile, so its furniture's teleports pay the walk to them; its exit portal does not. */
public class PohWalkCostTest
{
	private static final int HOUSE = WorldPointUtil.packWorldPoint(PohService.POH_LANDING_X, PohService.POH_LANDING_Y, 0);

	@Test
	public void houseFurnitureChargesTheWalk()
	{
		PathfinderConfig config = config(settings -> { });
		assertEquals(5, config.getAdditionalTransportCost(fromHouse(TransportType.SPIRIT_TREE)));
		assertEquals(0, config.getAdditionalTransportCost(fromHouse(TransportType.TELEPORTATION_PORTAL)));
	}

	@Test
	public void theWalkIsASetting()
	{
		PathfinderConfig config = config(settings -> settings.set("costPohWalk", 12));
		assertEquals(12, config.getAdditionalTransportCost(fromHouse(TransportType.SPIRIT_TREE)));
	}

	private static Transport fromHouse(TransportType type)
	{
		return TestWorldData.transports().getOrDefault(HOUSE, Set.of()).stream()
			.filter(transport -> transport.getType() == type).findFirst()
			.orElseThrow(() -> new AssertionError("no " + type + " from the house"));
	}

	private static PathfinderConfig config(Consumer<TestShortestPathConfig> setUp)
	{
		Client client = mock(Client.class);
		ItemContainer empty = mock(ItemContainer.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		doReturn(new Item[0]).when(empty).getItems();
		doReturn(empty).when(client).getItemContainer(InventoryID.INV);
		doReturn(empty).when(client).getItemContainer(InventoryID.WORN);
		TestShortestPathConfig settings = new TestShortestPathConfig();
		setUp.accept(settings);
		PathfinderConfig config = new TestPathfinderConfig(client, settings);
		config.refresh();
		return config;
	}
}
