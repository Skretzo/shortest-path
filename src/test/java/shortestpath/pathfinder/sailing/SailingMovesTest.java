package shortestpath.pathfinder.sailing;

import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.TestShortestPathConfig;
import shortestpath.settings.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.sailing.BoatSpeed;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TestPathfinderConfig;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SailingMovesTest
{
	// (2946, 3072) to (2971, 3117) is open sea between Rimmington and Karamja
	private static final int OPEN_SEA_START = WorldPointUtil.packWorldPoint(2948, 3074, 0);

	@Mock
	Client client;
	private TestShortestPathConfig config = new TestShortestPathConfig();
	private PathfinderConfig pathfinderConfig;

	@Before
	public void before()
	{
		config.set("calculationCutoff", 30);
		config.set("currencyThreshold", 10000000);
		config.set("useTeleportationItems", TeleportationItem.NONE);
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
		assertEquals(192, SailingFleets.speed(192));
		assertEquals("Falls back to the estimate", BoatSpeed.ESTIMATED, SailingFleets.speed(0));
	}

	@Test
	public void testAVeryLowSpeedFallsBackToTheEstimate()
	{
		// Below about 0.125 tiles per tick every heading rounds to no movement; a garbage or stale
		// base-speed reading falls back to the estimate rather than sailing nowhere
		assertSame(SailingMoves.ESTIMATE, SailingMoves.forSpeed(0.01));
	}

	@Test
	public void testAnImpossibleSpeedFallsBackToTheEstimate()
	{
		// The moves a far-too-fast reading builds are so long every leg clips a blocked tile and the
		// search ends at once, so like a negative or NaN one it falls back to the estimate
		assertSame(SailingMoves.ESTIMATE, SailingMoves.forSpeed(512));
		assertSame(SailingMoves.ESTIMATE, SailingMoves.forSpeed(SailingMoves.MAX_SPEED + 1));
		assertSame(SailingMoves.ESTIMATE, SailingMoves.forSpeed(-1));
		assertSame(SailingMoves.ESTIMATE, SailingMoves.forSpeed(Double.NaN));
		assertSame(SailingMoves.ESTIMATE, SailingMoves.forSpeed(0));
		assertTrue(SailingMoves.forSpeed(SailingMoves.MAX_SPEED).size() > 0);
	}

	@Test
	public void testAStaleOrMisScaledBaseSpeedFallsBackToTheEstimate()
	{
		// The side-panel varbit is in 1/128ths of a tile per tick: a stale or mis-scaled read far above
		// a real boat's speed is not a faster boat
		int fastest = (int) (SailingMoves.MAX_SPEED * BoatSpeed.UNITS);
		assertEquals(BoatSpeed.ESTIMATED, SailingFleets.speed(65536));
		assertEquals("The fastest real speed still counts", fastest, SailingFleets.speed(fastest));
		assertEquals("Just above it does not", BoatSpeed.ESTIMATED, SailingFleets.speed(fastest + 1));
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
		int start = OPEN_SEA_START;
		int target = WorldPointUtil.packWorldPoint(2968, 3114, 0);
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(target));
		pathfinder.run();
		assertTrue("Target should be reachable", pathfinder.getResult().isReached());
		List<PathStep> path = pathfinder.getPath();

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

	// A raft's leg from start to target
	private List<PathStep> findPath(int start, int target, SailingMoves sailingMoves)
	{
		List<PathStep> path = TestSail.sail(pathfinderConfig.getMap(), sailingMoves,
			SailingVessel.of(SailingVessel.Kind.RAFT, BoatSpeed.ESTIMATED).hull(), -1, start, target);
		assertNotNull("Target should be reachable", path);
		return path;
	}

	// Checks move i independently of the search: samples the line between the tile centres every
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

	private static int dx(List<PathStep> path, int i)
	{
		return WorldPointUtil.unpackWorldX(path.get(i).getPackedPosition()) - WorldPointUtil.unpackWorldX(path.get(i - 1).getPackedPosition());
	}

	private static int dy(List<PathStep> path, int i)
	{
		return WorldPointUtil.unpackWorldY(path.get(i).getPackedPosition()) - WorldPointUtil.unpackWorldY(path.get(i - 1).getPackedPosition());
	}

	@Test
	public void aBoatCanAlwaysSailSlower()
	{
		SailingMoves moves = SailingMoves.forSpeed(2.0), both = moves.withSlow();
		// A tile a tick north, slowly, as well as its own moves
		assertTrue(both.indexOf(0, 1) >= 0);
		assertTrue(both.size() > moves.size());
		for (int move = 0; move < moves.size(); move++) assertTrue(both.indexOf(moves.dx(move), moves.dy(move)) >= 0);
		// Slowing never sails faster
		assertEquals(moves.openWaterTicks(30, 40), both.openWaterTicks(30, 40), 1e-9);
	}
}
