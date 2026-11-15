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
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.pathfinder.astar.StartOverlay;
import shortestpath.settings.TeleportationItem;

/**
 * Starts on land no known transport leads into walk their own pocket, as legacy did, while targets
 * in left-out land still fail before the search.
 */
public class AStarStartOverlayTest
{
	// Maze random event: the outer ring to the shrine in the centre.
	private static final int MAZE_START = WorldPointUtil.packWorldPoint(2901, 4551, 0);
	private static final int MAZE_SHRINE = WorldPointUtil.packWorldPoint(2912, 4577, 0);
	// Song of the Elves' warped library, upper floor, corner to corner.
	private static final int LIBRARY_START = WorldPointUtil.packWorldPoint(2565, 6101, 1);
	private static final int LIBRARY_FAR = WorldPointUtil.packWorldPoint(2681, 6185, 1);
	// A dungeon pocket whose exit ladder is mapped but whose entrance is not.
	private static final int POCKET_START = WorldPointUtil.packWorldPoint(2526, 9843, 0);
	private static final int POCKET_EXIT = WorldPointUtil.packWorldPoint(2557, 9844, 0);
	private static final int LUMBRIDGE = WorldPointUtil.packWorldPoint(3222, 3218, 0);

	private static RoutingStatic routingStatic;
	private static PathfinderConfig config;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = TestWorldData.routingStatic();
		config = config();
	}

	@Test
	public void mazeWalksToTheShrineLikeLegacy()
	{
		assertSameCostAsLegacy(MAZE_START, MAZE_SHRINE);
	}

	@Test
	public void warpedLibraryWalksAcrossTheFloorLikeLegacy()
	{
		assertSameCostAsLegacy(LIBRARY_START, LIBRARY_FAR);
	}

	@Test
	public void pocketWalksToItsMappedExitLikeLegacy()
	{
		assertSameCostAsLegacy(POCKET_START, POCKET_EXIT);
	}

	@Test
	public void searchInsideAPocketNeverLeavesIt()
	{
		int pocket = pocketSize(LIBRARY_START);
		AStarPathfinder aStar = search(LIBRARY_START, LIBRARY_FAR);
		assertTrue(aStar.getResult().isReached());
		assertTrue("popped " + aStar.getAStarStats().statesPopped() + " states for a pocket of " + pocket,
			aStar.getAStarStats().statesPopped() <= 2 * pocket);
	}

	@Test
	public void targetsInLeftOutLandStillFailBeforeTheSearch()
	{
		// From the main land, and from another pocket.
		for (int start : new int[] {LUMBRIDGE, MAZE_START})
		{
			AStarPathfinder aStar = search(start, LIBRARY_FAR);
			assertFalse(aStar.getResult().isReached());
			assertEquals(0, aStar.getAStarStats().statesPopped());
		}
	}

	@Test
	public void onlyLeftOutStartsHaveAPocket()
	{
		assertEquals(0, pocketSize(LUMBRIDGE));
		StartOverlay maze = StartOverlay.of(routingStatic, config.getMap(), MAZE_START);
		assertTrue(maze.contains(MAZE_START));
		assertTrue("the tile north of the shrine", maze.contains(WorldPointUtil.packWorldPoint(2912, 4578, 0)));
		assertFalse(maze.reachable(LIBRARY_FAR));
		assertTrue(maze.reachable(LUMBRIDGE));
	}

	private static int pocketSize(int start)
	{
		return StartOverlay.of(routingStatic, config.getMap(), start).size();
	}

	private static void assertSameCostAsLegacy(int start, int target)
	{
		Pathfinder legacy = new Pathfinder(config, start, Set.of(target));
		legacy.run();
		AStarPathfinder aStar = search(start, target);
		assertTrue(legacy.getResult().isReached());
		assertTrue(aStar.getResult().isReached());
		assertEquals(legacy.getResult().getPathCost(), aStar.getResult().getPathCost());
		List<PathStep> path = aStar.getPath();
		assertEquals(start, path.get(0).getPackedPosition());
	}

	private static AStarPathfinder search(int start, int target)
	{
		AStarPathfinder aStar = new AStarPathfinder(config, routingStatic, null, start, Set.of(target), null, 1);
		aStar.run();
		return aStar;
	}

	/** Every quest and skill, no items or teleports beyond the free home teleport. */
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
		settings.set("collisionAwareBlockedTargets", true);
		settings.set("useTeleportationItems", TeleportationItem.NONE);
		settings.set("useTeleportationSpells", false);
		settings.set("useTeleportationMinigames", false);

		PathfinderConfig config = new TestPathfinderConfig(client, settings, QuestState.FINISHED, true, true);
		config.refresh();
		return config;
	}
}
