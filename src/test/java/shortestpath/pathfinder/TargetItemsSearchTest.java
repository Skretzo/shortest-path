package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.requirement.TeleportationItem;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.astar.RoutingStatic;

/**
 * Both searches routing to targets that need a spade, on the real map, from the Lumbridge Castle
 * courtyard. {@link #NEAR} is 19 to walk to, or 61 through the castle's top-floor bank;
 * {@link #FARTHER} is 39 to walk to.
 */
public class TargetItemsSearchTest
{
	private static final int START = WorldPointUtil.packWorldPoint(3222, 3218, 0);
	private static final int NEAR = WorldPointUtil.packWorldPoint(3240, 3225, 0);
	private static final int FARTHER = WorldPointUtil.packWorldPoint(3260, 3230, 0);

	private static RoutingStatic routingStatic;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = new AStarRoutingStaticProvider(
			() -> new CollisionMap(SplitFlagMap.fromResources())).get();
	}

	private static Target spadeAt(int point)
	{
		return Target.requiring(point, TargetsTest.items(ItemID.SPADE));
	}

	/** A logged-in player carrying {@code carried} with {@code banked} in the bank. */
	private static PathfinderConfig config(Item[] carried, Item[] banked)
	{
		Client client = mock(Client.class);
		ItemContainer inventory = mock(ItemContainer.class);
		ItemContainer worn = mock(ItemContainer.class);
		ItemContainer bank = mock(ItemContainer.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		doReturn(carried).when(inventory).getItems();
		doReturn(new Item[0]).when(worn).getItems();
		doReturn(banked).when(bank).getItems();
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		doReturn(worn).when(client).getItemContainer(InventoryID.WORN);
		TestShortestPathConfig settings = new TestShortestPathConfig();
		settings.setUseTeleportationItemsValue(TeleportationItem.NONE);
		settings.setCalculationCutoffValue(50);

		PathfinderConfig config = new TestPathfinderConfig(client, settings, QuestState.FINISHED, true, true);
		config.bank = bank;
		config.refresh();
		return config;
	}

	private static Item[] spade()
	{
		return new Item[] {new Item(ItemID.SPADE, 1)};
	}

	private static PathfinderResult legacy(PathfinderConfig config, Target... targets)
	{
		Pathfinder pathfinder = new Pathfinder(config, START, Targets.of(List.of(targets)));
		pathfinder.run();
		return pathfinder.getResult();
	}

	private static PathfinderResult aStar(PathfinderConfig config, Target... targets)
	{
		AStarPathfinder pathfinder = new AStarPathfinder(config, () -> routingStatic, null, START,
			Targets.of(List.of(targets)), null, 1);
		pathfinder.run();
		return pathfinder.getResult();
	}

	private static boolean banked(PathfinderResult result)
	{
		List<PathStep> path = result.getPathSteps();
		return path.get(path.size() - 1).isBankVisited();
	}

	@Test
	public void carriedItemsRouteDirectly()
	{
		PathfinderConfig config = config(spade(), spade());
		for (PathfinderResult result : List.of(legacy(config, spadeAt(NEAR)), aStar(config, spadeAt(NEAR))))
		{
			assertEquals(19, result.getPathCost());
			assertFalse(banked(result));
			assertEquals(TargetStatus.CARRIED, result.getArrival().getStatus());
		}
	}

	@Test
	public void bankedItemsRouteThroughABankAtTheSameCostInBothSearches()
	{
		PathfinderConfig config = config(new Item[0], spade());
		assertFalse("bank visits are off in the settings", config.isBankPathEnabled());
		for (PathfinderResult result : List.of(legacy(config, spadeAt(NEAR)), aStar(config, spadeAt(NEAR))))
		{
			assertTrue(result.isReached());
			assertEquals(61, result.getPathCost());
			assertTrue(banked(result));
			assertEquals(NEAR, result.getArrival().getTarget().getPoint());
			assertEquals(TargetStatus.BANK, result.getArrival().getStatus());
		}
	}

	@Test
	public void fartherTargetWinsOverACloserOneThatNeedsABankTrip()
	{
		PathfinderConfig config = config(new Item[0], spade());
		for (PathfinderResult result : List.of(legacy(config, spadeAt(NEAR), Target.at(FARTHER)),
			aStar(config, spadeAt(NEAR), Target.at(FARTHER))))
		{
			assertEquals(FARTHER, result.getArrival().getTarget().getPoint());
			assertFalse(banked(result));
		}
	}

	@Test
	public void itemsNotFoundRouteDirectlyAndAreReported()
	{
		PathfinderConfig config = config(new Item[0], new Item[0]);
		for (PathfinderResult result : List.of(legacy(config, spadeAt(NEAR)), aStar(config, spadeAt(NEAR))))
		{
			assertEquals(19, result.getPathCost());
			assertEquals(TargetStatus.UNKNOWN, result.getArrival().getStatus());
			assertEquals(TargetsTest.items(ItemID.SPADE).getRequirements(), result.getArrival().getMissingItems());
		}
	}
}
