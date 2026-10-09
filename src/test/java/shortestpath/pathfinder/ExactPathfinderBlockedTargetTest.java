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
import java.util.Map;
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
import shortestpath.Destination;
import shortestpath.ShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.exact.RoutingCuts;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.pathfinder.exact.RoutingStaticBuilder;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

/**
 * Issue #640 coverage for the exact backend: it terminates on the goal set
 * {@link TargetGoals} resolves from the requested targets, so a blocked target
 * ends the search on a walkable fallback tile on the target's side of any
 * wall instead of exhausting the map.
 */
@RunWith(MockitoJUnitRunner.class)
public class ExactPathfinderBlockedTargetTest
{
	private static RoutingStatic routingStatic;

	@Mock
	Client client;
	@Mock
	ShortestPathConfig config;
	private PathfinderConfig pathfinderConfig;

	@BeforeClass
	public static void buildRoutingStatic() throws Exception
	{
		CollisionMap collision = new CollisionMap(SplitFlagMap.fromResources());
		Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();
		Set<Integer> banks = Destination.loadAllFromResources().get("bank");
		routingStatic = RoutingStaticBuilder.build(collision, transports,
			banks == null ? Set.of() : banks, RoutingCuts.loadFromResources().pairs()).routingStatic;
	}

	@Before
	public void before()
	{
		when(config.calculationCutoff()).thenReturn(30);
		when(config.collisionAwareBlockedTargets()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
	}

	private void setup(int unreachableDistance)
	{
		when(config.unreachableTargetDistance()).thenReturn(unreachableDistance);
		pathfinderConfig = new TestPathfinderConfig(client, config, net.runelite.api.QuestState.FINISHED, true, true);
		pathfinderConfig.refresh();
	}

	private ExactPathfinder search(int start, Set<Integer> targets)
	{
		ExactPathfinder pathfinder = new ExactPathfinder(pathfinderConfig, routingStatic,
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

		ExactPathfinder pathfinder = search(start, Set.of(blockedTarget));
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
		// the unreachable distance and no transport lands on it, so the exact
		// search must skip the map walk exactly like the legacy one.
		setup(2);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(2114, 5506, 0);
		assertTrue("test requires a blocked target tile", map.isBlocked(2114, 5506, 0));
		assertFalse("test requires that no transport lands on the target",
			pathfinderConfig.isTransportDestination(blockedTarget));

		ExactPathfinder pathfinder = search(start, Set.of(blockedTarget));
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

		ExactPathfinder pathfinder = search(start, Set.of(blockedTarget, reachableTarget));
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

		ExactPathfinder pathfinder = search(start, Set.of(blockedTarget));
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

		ExactPathfinder pathfinder = search(start, Set.of(blockedTarget));
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
		when(config.collisionAwareBlockedTargets()).thenReturn(false);
		setup(radius);
		CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3200, 3330, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(3200, 3340, 0);
		assertTrue("test requires a blocked target tile", map.isBlocked(3200, 3340, 0));
		Set<Integer> connected = connectedTilesOracle(map, blockedTarget, radius);

		ExactPathfinder pathfinder = search(start, Set.of(blockedTarget));
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("expected the ring scan to find a fallback goal", result.isReached());
		int end = end(result);
		assertTrue("path must end within the unreachable distance",
			WorldPointUtil.distanceBetween(end, blockedTarget) <= radius);
		assertFalse("off mode must accept a tile not connected to the target",
			connected.contains(end));
	}
}
