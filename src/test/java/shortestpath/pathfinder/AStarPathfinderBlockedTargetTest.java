package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.settings.TeleportationItem;
import shortestpath.pathfinder.astar.RoutingStatic;

/**
 * Issue #640 coverage for the A* backend: it terminates on the goal set
 * {@link TargetGoals} resolves from the requested targets, so a blocked target
 * ends the search on a walkable fallback tile on the target's side of any
 * wall instead of exhausting the map.
 */
@RunWith(MockitoJUnitRunner.class)
public class AStarPathfinderBlockedTargetTest
{
	private static RoutingStatic routingStatic;

	@Mock
	Client client;
	private TestShortestPathConfig config = new TestShortestPathConfig();
	private PathfinderConfig pathfinderConfig;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = TestWorldData.routingStatic();
	}

	@Before
	public void before()
	{
		config.set("calculationCutoff", 30);
		config.set("collisionAwareBlockedTargets", true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		config.set("useTeleportationItems", TeleportationItem.NONE);
	}

	private void setup(int unreachableDistance)
	{
		config.set("unreachableTargetDistanceThreshold", unreachableDistance);
		pathfinderConfig = new TestPathfinderConfig(client, config, net.runelite.api.QuestState.FINISHED, true, true);
		pathfinderConfig.refresh();
	}

	private AStarPathfinder search(int start, Set<Integer> targets)
	{
		AStarPathfinder pathfinder = new AStarPathfinder(pathfinderConfig, routingStatic,
			start, targets, null);
		pathfinder.run();
		return pathfinder;
	}

	// The same connectivity oracle PathfinderTest uses: every tile walk-connected
	// to the target within the radius, regardless of walls or footprints.
	private static Set<Integer> connectedTilesOracle(CollisionMap map, int target, int radius)
	{
		Set<Integer> connected = new HashSet<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		connected.add(target);
		queue.add(target);
		while (!queue.isEmpty())
		{
			int current = queue.poll();
			for (int neighbour : map.ordinaryWalkingNeighbors(current))
			{
				if (connected.contains(neighbour)
					|| WorldPointUtil.distanceBetween(target, neighbour) > radius)
				{
					continue;
				}
				connected.add(neighbour);
				queue.add(neighbour);
			}
		}
		connected.remove(target);
		return connected;
	}

	private static int end(PathfinderResult result)
	{
		List<PathStep> steps = result.getPathSteps();
		return steps.get(steps.size() - 1).getPackedPosition();
	}

	@Test
	public void blockedTargetExpandsToWalkableNeighbours()
	{
		setup(2);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(3203, 3178, 0);
		assertTrue("test requires a blocked target tile", map.isBlocked(3203, 3178, 0));

		AStarPathfinder pathfinder = search(start, Set.of(blockedTarget));
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("expected a fallback tile near the blocked target", result.isReached());
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
		int end = end(result);
		assertFalse("path must end on a walkable tile", map.isBlocked(
			WorldPointUtil.unpackWorldX(end), WorldPointUtil.unpackWorldY(end),
			WorldPointUtil.unpackWorldPlane(end)));
		assertTrue("path must end within the unreachable distance",
			WorldPointUtil.distanceBetween(end, blockedTarget) <= 2);
	}

	@Test
	public void fullyBlockedTargetShortCircuitsSearch()
	{
		// (2114, 5506) sits inside a fully blocked 5x5: no walkable tile within
		// the unreachable distance and no transport lands on it, so the aStar
		// search must skip the map walk exactly like the legacy one.
		setup(2);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(2114, 5506, 0);
		assertTrue("test requires a blocked target tile", map.isBlocked(2114, 5506, 0));
		assertFalse("test requires that no transport lands on the target",
			pathfinderConfig.isTransportDestination(blockedTarget));

		AStarPathfinder pathfinder = search(start, Set.of(blockedTarget));
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertFalse(result.isReached());
		assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, result.getTerminationReason());
		assertEquals("a hopeless target must not explore any tile", 0, result.getNodesChecked());
	}

	@Test
	public void blockedTargetDoesNotSuppressOtherTargets()
	{
		setup(2);
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(2114, 5506, 0);
		int reachableTarget = WorldPointUtil.packWorldPoint(3213, 3428, 0);

		AStarPathfinder pathfinder = search(start, Set.of(blockedTarget, reachableTarget));
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("the viable target should still be reached", result.isReached());
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
		assertEquals(reachableTarget, end(result));
	}

	@Test
	public void blockedTargetExpansionStaysOnTargetSideOfWalls()
	{
		// Same Varrock wall fixture as the legacy test: (3258, 3351) is a blocked
		// wall segment whose expansion square covers walkable tiles on the far
		// side of the wall; the path must end on the target's connected side.
		final int radius = 6;
		setup(radius);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3258, 3341, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(3258, 3351, 0);
		assertTrue("test requires a blocked target tile", map.isBlocked(3258, 3351, 0));
		assertFalse("test requires a walkable start tile", map.isBlocked(3258, 3341, 0));
		Set<Integer> connected = connectedTilesOracle(map, blockedTarget, radius);
		assertFalse("test requires at least one connected fallback goal", connected.isEmpty());

		AStarPathfinder pathfinder = search(start, Set.of(blockedTarget));
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue(result.isReached());
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
		int end = end(result);
		assertFalse("path must end on a walkable tile", map.isBlocked(
			WorldPointUtil.unpackWorldX(end), WorldPointUtil.unpackWorldY(end),
			WorldPointUtil.unpackWorldPlane(end)));
		assertTrue("end tile must be walk-connected to the target (same side of the wall)",
			connected.contains(end));
	}

	@Test
	public void blockedTargetDoesNotTerminateOnStartTile()
	{
		// The player's own tile is excluded from the goal set: the search must
		// not terminate on the first popped state and draw an invisible path.
		final int radius = 6;
		setup(radius);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3256, 3345, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(3258, 3351, 0);
		Set<Integer> connected = connectedTilesOracle(map, blockedTarget, radius);
		assertTrue("test requires the start tile to be a connected fallback goal",
			connected.contains(start));

		AStarPathfinder pathfinder = search(start, Set.of(blockedTarget));
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue(result.isReached());
		List<PathStep> steps = result.getPathSteps();
		assertTrue("a path of a single start tile is meaningless to draw", steps.size() > 1);
		assertNotEquals("the search must not terminate on the start tile", start, end(result));
	}

	@Test
	public void blockedTargetExpansionOffIgnoresConnectivity()
	{
		// With collisionAwareBlockedTargets off the innermost walkable ring is
		// the goal set: the Port Sarim jetty target's ring 1 holds only tiles on
		// the far side, which legacy mode accepts and aware mode rejects.
		final int radius = 6;
		config.set("collisionAwareBlockedTargets", false);
		setup(radius);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3200, 3330, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(3200, 3340, 0);
		assertTrue("test requires a blocked target tile", map.isBlocked(3200, 3340, 0));
		Set<Integer> connected = connectedTilesOracle(map, blockedTarget, radius);

		AStarPathfinder pathfinder = search(start, Set.of(blockedTarget));
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("expected the ring scan to find a fallback goal", result.isReached());
		int end = end(result);
		assertTrue("path must end within the unreachable distance",
			WorldPointUtil.distanceBetween(end, blockedTarget) <= radius);
		assertFalse("off mode must accept a tile not connected to the target",
			connected.contains(end));
	}

	@Test
	public void blockedWallFaceTargetEndsOnAdjacentFace()
	{
		// Same Lovakengj blast-mine fixture as the legacy test: the rock at
		// (1504, 3859) is the wall object itself, so every walkable neighbour
		// lies behind one of its boundary edges. They must still resolve as
		// goals -- the cardinally adjacent face specifically -- rather than
		// expanding to the pocket behind the wall's west end.
		setup(6);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(1504, 3866, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(1504, 3859, 0);
		assertTrue("test requires a blocked target tile", map.isBlocked(1504, 3859, 0));
		assertTrue("test requires the north edge to be a wall boundary",
			map.wallN(1504, 3859, 0));
		assertFalse("test requires a walkable start tile", map.isBlocked(1504, 3866, 0));

		AStarPathfinder pathfinder = search(start, Set.of(blockedTarget));
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue(result.isReached());
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
		assertEquals("the rock face is only usable from its cardinal neighbour",
			WorldPointUtil.packWorldPoint(1504, 3860, 0), end(result));
	}

	// A blocked target must snap to a tile the player can use: one cardinally
	// adjacent to it, reached for no more than the route to goodTile, a tile
	// adjacent to the target on the side the player approaches from.
	private void assertSnapsNoWorseThan(int start, int target, int goodTile)
	{
		CollisionMap map = pathfinderConfig.getMap();
		assertTrue("test requires a blocked target tile", map.isBlocked(
			WorldPointUtil.unpackWorldX(target), WorldPointUtil.unpackWorldY(target),
			WorldPointUtil.unpackWorldPlane(target)));
		PathfinderResult good = search(start, Set.of(goodTile)).getResult();
		assertNotNull(good);
		assertTrue("test requires the good tile to be reachable", good.isReached());

		PathfinderResult snapped = search(start, Set.of(target)).getResult();
		assertNotNull(snapped);
		assertTrue("the blocked target must snap to a reachable tile, not to one the player cannot get to",
			snapped.isReached());
		assertTrue("snapped route ends on " + WorldPointUtil.unpackWorldPoint(end(snapped))
				+ " at cost " + snapped.getPathCost() + "; " + WorldPointUtil.unpackWorldPoint(goodTile)
				+ " costs " + good.getPathCost(),
			snapped.getPathCost() <= good.getPathCost());
		int end = end(snapped);
		int dx = Math.abs(WorldPointUtil.unpackWorldX(end) - WorldPointUtil.unpackWorldX(target));
		int dy = Math.abs(WorldPointUtil.unpackWorldY(end) - WorldPointUtil.unpackWorldY(target));
		assertEquals("snapped route must end cardinally adjacent to the target, not on "
			+ WorldPointUtil.unpackWorldPoint(end), 1, dx + dy);
	}

	@Test
	public void bankBoothSnapsToPlayerSideOfCounter()
	{
		// Draynor bank booth: the tile behind the counter is the most enclosed
		// candidate, so it anchors the side and the search can never reach it.
		setup(2);
		assertSnapsNoWorseThan(WorldPointUtil.packWorldPoint(3093, 3245, 0),
			WorldPointUtil.packWorldPoint(3091, 3242, 0), WorldPointUtil.packWorldPoint(3092, 3242, 0));
	}

	@Test
	public void bankBoothSnapsToPlayerSideOfBoothRow()
	{
		// Varrock East bank: same failure with the booths in a row along y=3419.
		setup(2);
		assertSnapsNoWorseThan(WorldPointUtil.packWorldPoint(3251, 3421, 0),
			WorldPointUtil.packWorldPoint(3251, 3419, 0), WorldPointUtil.packWorldPoint(3251, 3420, 0));
	}

	@Test
	public void barPumpsSnapToCustomerSideOfBar()
	{
		// Rising Sun Inn, Falador: the bar pumps snap behind the bar.
		setup(2);
		assertSnapsNoWorseThan(WorldPointUtil.packWorldPoint(2965, 3380, 0),
			WorldPointUtil.packWorldPoint(2955, 3374, 0), WorldPointUtil.packWorldPoint(2956, 3374, 0));
	}

	@Test
	public void treeDoesNotSnapIntoFencedPocket()
	{
		// Seers' Village 2x2 tree (2734, 3479): the tile north of it is a small
		// fenced pocket, the most enclosed candidate, and unreachable.
		setup(2);
		assertSnapsNoWorseThan(WorldPointUtil.packWorldPoint(2725, 3485, 0),
			WorldPointUtil.packWorldPoint(2735, 3480, 0), WorldPointUtil.packWorldPoint(2736, 3480, 0));
	}

	@Test
	public void wallTargetSnapsToNearSide()
	{
		// Diagonal wall south-east of Lumbridge castle: the enclosed far side
		// wins, so the route walks round through two doors instead of stopping
		// on the open tile north of the wall.
		setup(2);
		assertSnapsNoWorseThan(WorldPointUtil.packWorldPoint(3222, 3218, 0),
			WorldPointUtil.packWorldPoint(3224, 3206, 0), WorldPointUtil.packWorldPoint(3224, 3207, 0));
	}

	@Test
	public void disconnectedTargetShortCircuitsSearch()
	{
		// Open sea off Draynor is walkable in the collision map but no route
		// reaches it, nor any tile within the unreachable distance: the search
		// must give up without exploring the map.
		setup(2);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3093, 3245, 0);
		int target = WorldPointUtil.packWorldPoint(3074, 3221, 0);
		assertFalse("test requires a walkable target tile", map.isBlocked(3074, 3221, 0));

		PathfinderResult result = search(start, Set.of(target)).getResult();
		assertNotNull(result);
		assertFalse(result.isReached());
		assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, result.getTerminationReason());
		assertEquals("an unreachable target must not explore any tile", 0, result.getNodesChecked());
	}
}
