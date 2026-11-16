package shortestpath.pathfinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.poh.PohService;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Grouping (minigame) teleports "cannot be cast from ... player-owned house"
 * (https://oldschool.runescape.wiki/w/Grouping), so A* must leave the house before casting one.
 *
 * <p>From the POH landing to Rogues' Den, the cheapest route used to cast the Burthorpe Games Room
 * Minigame Teleport inside the house.
 */
public class AStarPohMinigameTeleportTest
{
	private static final int POH_LANDING = WorldPointUtil.packWorldPoint(PohService.POH_LANDING_X,
		PohService.POH_LANDING_Y, 0);
	private static final int ROGUES_DEN = WorldPointUtil.packWorldPoint(3040, 4969, 1);

	private static RoutingStatic routingStatic;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = TestWorldData.routingStatic();
	}

	@Test
	public void minigameTeleportsAreNotCastFromInsideThePoh()
	{
		AStarPathfinder aStar = new AStarPathfinder(config(), routingStatic, null, POH_LANDING, Set.of(ROGUES_DEN),
			null, 1);
		aStar.run();

		assertTrue(aStar.getResult().isReached());
		List<PathStep> path = aStar.getPath();
		boolean minigameTeleport = false;
		for (int i = 1; i < path.size(); i++)
		{
			Transport transport = path.get(i).getTransport();
			if (transport == null || transport.getType() != TransportType.TELEPORTATION_MINIGAME) continue;
			minigameTeleport = true;
			int from = path.get(i - 1).getPackedPosition();
			assertFalse(transport.getDisplayInfo() + " cast from inside the POH at " + WorldPointUtil.unpackWorldX(from)
				+ "," + WorldPointUtil.unpackWorldY(from),
				PohService.isInsidePoh(WorldPointUtil.unpackWorldX(from), WorldPointUtil.unpackWorldY(from)));
		}
		assertTrue("still casts a minigame teleport once out of the house", minigameTeleport);
	}

	/** Every quest and skill, no items; the POH and minigame teleports. */
	private static PathfinderConfig config()
	{
		Client client = mock(Client.class);
		TestShortestPathConfig settings = new TestShortestPathConfig();
		ItemContainer empty = mock(ItemContainer.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		doReturn(new Item[0]).when(empty).getItems();
		doReturn(empty).when(client).getItemContainer(InventoryID.INV);
		doReturn(empty).when(client).getItemContainer(InventoryID.WORN);
		settings.set("calculationCutoff", 500);
		settings.set("currencyThreshold", 10000000);
		settings.set("useTeleportationItems", TeleportationItem.NONE);
		settings.set("useTeleportationMinigames", true);
		settings.set("usePoh", true);

		PathfinderConfig config = new TestPathfinderConfig(client, settings, QuestState.FINISHED, true, true);
		config.refresh();
		return config;
	}
}
