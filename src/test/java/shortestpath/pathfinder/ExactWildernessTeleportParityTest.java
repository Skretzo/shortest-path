package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
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
import shortestpath.ShortestPathConfig;
import shortestpath.settings.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.exact.RoutingStatic;

/**
 * Exact casts a global teleport only where its own wilderness limit allows, like legacy.
 *
 * <p>From the level-27 obelisk (benchmark early/quest-natural-0006's start), the cheapest obelisk
 * lands at level 13 (3156,3620). The Fishing Trawler Minigame Teleport is limited to level 0, so it
 * may only be cast after leaving the wilderness, for example into Ferox Enclave a few tiles north.
 */
public class ExactWildernessTeleportParityTest
{
	private static final int START = WorldPointUtil.packWorldPoint(3051, 3736, 0);
	private static final int FISHING_TRAWLER = WorldPointUtil.packWorldPoint(2658, 3157, 0);

	private static RoutingStatic routingStatic;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = new ExactRoutingStaticProvider(
			() -> new CollisionMap(SplitFlagMap.fromResources())).get();
	}

	@Test
	public void levelZeroTeleportIsOnlyCastOutsideTheWildernessLikeLegacy()
	{
		PathfinderConfig config = config();
		Pathfinder legacy = new Pathfinder(config, START, Set.of(FISHING_TRAWLER));
		legacy.run();
		ExactPathfinder exact = new ExactPathfinder(config, routingStatic, null, START, Set.of(FISHING_TRAWLER),
			null, 1);
		exact.run();

		assertTrue(legacy.getResult().isReached());
		assertTrue(exact.getResult().isReached());
		List<PathStep> path = exact.getPath();
		int castFrom = path.get(path.size() - 2).getPackedPosition();
		assertFalse("cast from wilderness tile " + WorldPointUtil.unpackWorldX(castFrom) + ","
			+ WorldPointUtil.unpackWorldY(castFrom), WildernessChecker.isInWilderness(castFrom));
		assertEquals(legacy.getResult().getPathCost(), exact.getResult().getPathCost());
	}

	/** Every quest and skill, no items; wilderness obelisks and minigame teleports only. */
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
		when(settings.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(settings.useWildernessObelisks()).thenReturn(true);
		when(settings.useTeleportationMinigames()).thenReturn(true);

		PathfinderConfig config = new TestPathfinderConfig(client, settings, QuestState.FINISHED, true, true);
		config.refresh();
		return config;
	}
}
