package shortestpath.pathfinder;

import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SailingMovesTest
{
	// (2946, 3072) to (2971, 3117) is open sea between Rimmington and Karamja
	private static final int OPEN_SEA_START = WorldPointUtil.packWorldPoint(2948, 3074, 0);

	@Mock
	Client client;
	@Mock
	ShortestPathConfig config;
	private PathfinderConfig pathfinderConfig;

	@Before
	public void before()
	{
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		pathfinderConfig = new TestPathfinderConfig(client, config);
		pathfinderConfig.refresh();
	}

	@Test
	public void testEstimateUsesTheGameHeadingsAtSpeedTwo()
	{
		SailingMoves moves = SailingMoves.ESTIMATE;
		assertEquals(16, moves.size());
		// Per tick at speed 2 the game moves N (0,8), NNE (3,7), NE (6,6), ENE (7,3), E (8,0) quarter tiles
		assertMove(moves, "N", 0, 2, 1);
		assertMove(moves, "NNE", 3, 7, 4);
		assertMove(moves, "NE", 3, 3, 2);
		assertMove(moves, "ENE", 7, 3, 4);
		assertMove(moves, "E", 2, 0, 1);
		assertMove(moves, "SSW", -3, -7, 4);
	}

	@Test
	public void testHeadingsDependOnSpeed()
	{
		// NNE is (2,6) quarter tiles per tick at speed 1.5 and (5,13) at 3.5; NE is (9,9) at 3
		assertMove(SailingMoves.forSpeed(1.5), "NNE", 1, 3, 2);
		assertMove(SailingMoves.forSpeed(3.5), "NNE", 5, 13, 4);
		assertMove(SailingMoves.forSpeed(3.0), "NE", 9, 9, 4);
	}

	@Test
	public void testSailingSpeedIsTheBoatsBaseSpeed()
	{
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED)).thenReturn(192);
		pathfinderConfig.refresh();
		assertEquals(1.5, pathfinderConfig.getSailingSpeed(), 0);

		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED)).thenReturn(0);
		pathfinderConfig.refresh();
		assertEquals("Falls back to the estimate", SailingMoves.ESTIMATED_SPEED, pathfinderConfig.getSailingSpeed(), 0);
	}

	@Test
	public void testOpenSeaTakesTheDirectHeading()
	{
		// 15 across and 35 up is 5 NNE moves (38 tiles); N, N, NE five times takes as long but sails 41 tiles
		List<PathStep> path = findPath(OPEN_SEA_START, WorldPointUtil.packWorldPoint(2963, 3109, 0), SailingMoves.ESTIMATE);

		assertEquals(6, path.size());
		for (int i = 1; i < path.size(); i++)
		{
			assertEquals("Move " + i + " should be NNE", "NNE", SailingMoves.ESTIMATE.name(moveIndex(path, i)));
		}
	}

	@Test
	public void testRouteAroundLandOnlyUsesBoatHeadings()
	{
		// Port Sarim to The Pandemonium, around Mudskipper Point
		List<PathStep> path = findPath(WorldPointUtil.packWorldPoint(3048, 3184, 0), WorldPointUtil.packWorldPoint(3069, 2983, 0),
			SailingMoves.ESTIMATE);

		for (int i = 1; i < path.size(); i++)
		{
			assertNotEquals("Move " + i + " should be a boat heading", -1, SailingMoves.ESTIMATE.headingOf(dx(path, i), dy(path, i)));
			assertNull("Move " + i + " should not pass over a blocked tile", blockedOnLine(path, i));
		}
	}

	@Test
	public void testSearchWithoutSailingMovesStillWalks()
	{
		List<PathStep> path = findPath(OPEN_SEA_START, WorldPointUtil.packWorldPoint(2968, 3114, 0), null);

		assertEquals("40 walking steps plus the start tile", 41, path.size());
		for (int i = 1; i < path.size(); i++)
		{
			assertEquals("Step " + i + " should move one tile", 1, Math.max(Math.abs(dx(path, i)), Math.abs(dy(path, i))));
		}
	}

	@Test
	public void testRouteGoesAroundObstacles()
	{
		// A 3x2 shipwreck at (2704-2706, 3050-3051) in open sea blocks the straight line between these points
		int start = WorldPointUtil.packWorldPoint(2705, 3044, 0);
		int target = WorldPointUtil.packWorldPoint(2705, 3057, 0);
		SailingMoves moves = SailingMoves.forSpeed(1.5);

		List<PathStep> path = findPath(start, target, moves);

		assertTrue("Should leave the straight line north, which runs into the wreck",
			path.stream().anyMatch(step -> WorldPointUtil.unpackWorldX(step.getPackedPosition()) != 2705));
		for (int i = 1; i < path.size(); i++)
		{
			assertNotEquals("Move " + i + " should be a boat heading", -1, moves.headingOf(dx(path, i), dy(path, i)));
			assertNull("Move " + i + " should not pass over the shipwreck", blockedOnLine(path, i));
		}
	}

	@Test
	public void testArrivesNextToATargetTheBoatCantStopOn()
	{
		// At speed 2 every move goes an even number of tiles across and up together, so 15 across and 16 up is out of reach
		int target = WorldPointUtil.packWorldPoint(2963, 3090, 0);
		List<PathStep> path = findPath(OPEN_SEA_START, target, SailingMoves.ESTIMATE);

		assertEquals("Ends next to the target", 1, WorldPointUtil.distanceBetween(target, last(path)));
		for (int i = 1; i < path.size(); i++)
		{
			assertNotEquals("Move " + i + " should be a boat heading", -1, SailingMoves.ESTIMATE.headingOf(dx(path, i), dy(path, i)));
		}
	}

	@Test
	public void testLinesDontCrossBlockedTiles()
	{
		// A 3x2 shipwreck at (2704-2706, 3050-3051) in open sea
		CollisionMap map = pathfinderConfig.getMap();
		assertTrue(map.isBlocked(2705, 3050, 0));
		assertTrue("Stops short of the wreck", map.canSailLine(2705, 3044, 0, 0, 5));
		assertFalse("Runs into the wreck", map.canSailLine(2705, 3044, 0, 0, 13));
		assertFalse("Clips the wreck's corner", map.canSailLine(2702, 3048, 0, 3, 3));
		// A boat that starts on a blocked tile can sail out of it, but not further into the blocked tiles
		assertTrue("Leaves the wreck", map.canSailLine(2705, 3050, 0, 0, -3));
		assertFalse("Stays in the wreck", map.canSailLine(2705, 3050, 0, 0, 3));
	}

	@Test
	public void testSailingSkipsBankVisitsAndTransports()
	{
		// The Bank Boat at (2280, 2544) is a bank out at sea. With paths through a bank turned on, a walking search
		// passing it would consider visiting it; a sailing search only sails, since bank visits and transports cost
		// ticks rather than the distance sailed
		when(config.includeBankPath()).thenReturn(true);
		pathfinderConfig.refresh();
		assertTrue(pathfinderConfig.bankAccessible(WorldPointUtil.packWorldPoint(2280, 2544, 0)));

		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, WorldPointUtil.packWorldPoint(2260, 2544, 0),
			Set.of(WorldPointUtil.packWorldPoint(2300, 2548, 0)), null, SailingMoves.ESTIMATE);
		pathfinder.run();

		assertTrue(pathfinder.getResult().isReached());
		assertEquals("No bank visits or transports queued", 0, pathfinder.getResult().getTransportsChecked());
	}

	private List<PathStep> findPath(int start, int target, SailingMoves sailingMoves)
	{
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(target), null, sailingMoves);
		pathfinder.run();
		assertTrue("Target should be reachable", pathfinder.getResult().isReached());
		return pathfinder.getPath();
	}

	// Checks move i independently of CollisionMap#canSailLine: samples the line between the tile centres every
	// twentieth of a tile, and describes the first blocked tile it passes over after the one it starts on, or returns
	// null if there are none
	private String blockedOnLine(List<PathStep> path, int i)
	{
		CollisionMap map = pathfinderConfig.getMap();
		int from = path.get(i - 1).getPackedPosition();
		int x = WorldPointUtil.unpackWorldX(from);
		int y = WorldPointUtil.unpackWorldY(from);
		int z = WorldPointUtil.unpackWorldPlane(from);
		int samples = (int) Math.ceil(Math.hypot(dx(path, i), dy(path, i)) * 20);
		for (int s = 1; s <= samples; s++)
		{
			// Nudged so that a sample never sits exactly on a tile edge
			int tileX = (int) Math.floor(x + dx(path, i) * (double) s / samples + 0.5 + 1e-6);
			int tileY = (int) Math.floor(y + dy(path, i) * (double) s / samples + 0.5 + 1e-7);
			if ((tileX != x || tileY != y) && map.isBlocked(tileX, tileY, z))
			{
				return "(" + tileX + ", " + tileY + ")";
			}
		}
		return null;
	}

	private static void assertMove(SailingMoves moves, String name, int dx, int dy, int ticks)
	{
		int index = moves.indexOf(dx, dy);
		assertNotEquals(name + " should move (" + dx + ", " + dy + ")", -1, index);
		assertEquals(name, moves.name(index));
		assertEquals(name + " ticks", ticks, moves.ticks(index));
	}

	private static int last(List<PathStep> path)
	{
		return path.get(path.size() - 1).getPackedPosition();
	}

	private static int moveIndex(List<PathStep> path, int i)
	{
		return SailingMoves.ESTIMATE.indexOf(dx(path, i), dy(path, i));
	}

	private static int dx(List<PathStep> path, int i)
	{
		return WorldPointUtil.unpackWorldX(path.get(i).getPackedPosition()) - WorldPointUtil.unpackWorldX(path.get(i - 1).getPackedPosition());
	}

	private static int dy(List<PathStep> path, int i)
	{
		return WorldPointUtil.unpackWorldY(path.get(i).getPackedPosition()) - WorldPointUtil.unpackWorldY(path.get(i - 1).getPackedPosition());
	}
}
