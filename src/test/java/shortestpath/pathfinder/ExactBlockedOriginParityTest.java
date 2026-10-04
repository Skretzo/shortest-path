package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
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
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.exact.RoutingStatic;

/**
 * Legacy can chain across consecutive blocked tiles that host a transport, the
 * "stepping stone" pattern where each stone is the origin of the hop to the next.
 * Exact must offer the same step-on and hop-off moves, and only while a transport
 * leaving the stone is usable at the tile's wilderness level.
 */
public class ExactBlockedOriginParityTest
{
	/** West and east banks of the river at Draynor Manor, joined by stepping stones. */
	private static final int DRAYNOR_WEST = WorldPointUtil.packWorldPoint(3149, 3363, 0);
	private static final int DRAYNOR_EAST = WorldPointUtil.packWorldPoint(3154, 3363, 0);

	private static RoutingStatic routingStatic;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = new ExactRoutingStaticProvider(
			() -> new CollisionMap(SplitFlagMap.fromResources())).get();
	}

	@Test
	public void steppingStonesChainLikeLegacy()
	{
		PathfinderConfig config = config();
		Pathfinder legacy = new Pathfinder(config, DRAYNOR_WEST, Set.of(DRAYNOR_EAST));
		legacy.run();
		ExactPathfinder exact = new ExactPathfinder(config, routingStatic, null,
			DRAYNOR_WEST, Set.of(DRAYNOR_EAST), null, 1);
		exact.run();
		assertTrue(legacy.getResult().isReached());
		assertTrue("exact could not chain the stepping stones", exact.getResult().isReached());
		// Legacy crosses in six steps; exact must route over the stones, not around them.
		assertEquals(6, exact.getResult().getPathSteps().size());
		assertEquals(legacy.getResult().getPathCost(), exact.getResult().getPathCost());
	}

	private static PathfinderConfig config()
	{
		Client client = mock(Client.class);
		ShortestPathConfig settings = mock(ShortestPathConfig.class);
		ItemContainer empty = mock(ItemContainer.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		doReturn(new Item[0]).when(empty).getItems();
		doReturn(empty).when(client).getItemContainer(InventoryID.INV);
		doReturn(empty).when(client).getItemContainer(InventoryID.WORN);
		when(settings.calculationCutoff()).thenReturn(500);
		when(settings.currencyThreshold()).thenReturn(10000000);
		when(settings.useAgilityShortcuts()).thenReturn(true);
		when(settings.useTeleportationItems()).thenReturn(TeleportationItem.NONE);

		PathfinderConfig config = new TestPathfinderConfig(client, settings, QuestState.FINISHED, true, true);
		config.refresh();
		return config;
	}
}
