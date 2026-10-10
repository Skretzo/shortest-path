package shortestpath.pathfinder;

import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
public class BoatHullTest
{
	// The game's bounds for each boat, in local units: across, along (negative is toward the bow), width, length
	private static final int[] RAFT = {0, 0, 128, 384};
	private static final int[] SKIFF = {0, 0, 256, 640};
	private static final int[] SLOOP = {0, -256, 384, 1280};
	private static final int SOUTH = 0;
	private static final int WEST = 4;
	private static final int NORTH = 8;
	private static final int NORTH_NORTH_EAST = 9;
	private static final int EAST = 12;
	// From the harbour at the Ruins of Unkah to the top of the Elid Delta, past the small islands south of Sophanem
	private static final int UNKAH_HARBOUR = WorldPointUtil.packWorldPoint(3143, 2847, 0);
	private static final int TOP_OF_ELID_DELTA = WorldPointUtil.packWorldPoint(3273, 2738, 0);

	@Mock
	Client client;
	@Mock
	ShortestPathConfig config;
	private PathfinderConfig pathfinderConfig;
	private CollisionMap map;

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
		map = pathfinderConfig.getMap();
	}

	@Test
	public void testSloopFitsAThreeTileGapOnlyWhenLinedUp()
	{
		// Rocks at (2630, 2548) and (2634, 2548) south of Pest Control leave a gap three tiles wide, which a sloop
		// fits through sailing straight north with the boat on a tile centre, touching the rocks either side. In game
		// it only gets through from that spot: from the 3 others across its tile it stops at the rocks
		assertTrue(hull(SLOOP, 0, 0).canMove(map, 2632, 2538, 0, NORTH, 0, 12));
		assertFalse("A quarter tile off centre", hull(SLOOP, -32, 0).canMove(map, 2632, 2538, 0, NORTH, 0, 12));
		assertFalse("A quarter tile the other way", hull(SLOOP, 32, 0).canMove(map, 2632, 2538, 0, NORTH, 0, 12));
		assertFalse("On the tile's west edge", hull(SLOOP, -64, 0).canMove(map, 2632, 2538, 0, NORTH, 0, 12));
		assertFalse("Turned a heading", hull(SLOOP, 0, 0).canMove(map, 2632, 2538, 0, NORTH_NORTH_EAST, 0, 12));
	}

	@Test
	public void testOnlyARaftFitsAOneTileChannel()
	{
		// Rocks either side of x 2796 from y 2962 to 2967 leave a channel one tile wide
		assertTrue(hull(RAFT, 0, 0).canMove(map, 2796, 2963, 0, NORTH, 0, 5));
		assertFalse(hull(SKIFF, 0, 0).canMove(map, 2796, 2963, 0, NORTH, 0, 5));
	}

	@Test
	public void testSkiffFitsPastTheLumSteppingStoneOnlyOnItsTilesSouthEdge()
	{
		// The channel between the Lum Lagoon and the Lumbridge Basin has an Agility shortcut's stepping stone in it at
		// (3214, 3135), with 2 tiles of water between it and the bank to the south. A skiff sailing east along the row
		// just south of the stone fits between them only sitting on its tile's south edge, as in game
		assertTrue(hull(SKIFF, 0, -64).canMove(map, 3207, 3134, 0, EAST, 13, 0));
		assertFalse(hull(SKIFF, 0, -32).canMove(map, 3207, 3134, 0, EAST, 13, 0));
		assertFalse(hull(SKIFF, 0, 0).canMove(map, 3207, 3134, 0, EAST, 13, 0));
		assertFalse(hull(SKIFF, 0, 32).canMove(map, 3207, 3134, 0, EAST, 13, 0));
	}

	@Test
	public void testHullCoversTheTilesUnderIt()
	{
		// A raft is a tile wide and 3 long, so sitting on a tile it covers that tile and the ones fore and aft
		assertTrue(hull(RAFT, 0, 0).covers(NORTH, 0, 1));
		assertTrue(hull(RAFT, 0, 0).covers(NORTH, 0, -1));
		assertFalse(hull(RAFT, 0, 0).covers(NORTH, 1, 0));
		assertTrue(hull(RAFT, 0, 0).covers(EAST, 1, 0));
		assertFalse(hull(RAFT, 0, 0).covers(EAST, 0, 1));
	}

	@Test
	public void testSloopRouteKeepsItsWholeHullClear()
	{
		// From open sea past the rocks off Mudskipper Point to the water off the Pandemonium
		SailingMoves moves = SailingMoves.forSpeed(3.0);
		int start = WorldPointUtil.packWorldPoint(2948, 3074, 0);
		int target = WorldPointUtil.packWorldPoint(3074, 2983, 0);

		List<PathStep> pointPath = findPath(start, target, moves, null);
		List<PathStep> sloopPath = findPath(start, target, moves, hull(SLOOP, 0, 0));

		assertNotNull("Steering only the boat's centre clear takes a sloop over rocks", hullHit(pointPath, moves, 1.5, 3, 7));
		assertNull(hullHit(sloopPath, moves, 1.5, 3, 7));
	}

	@Test
	public void testSloopEndsWithItsHullOverAnOpenSeaTarget()
	{
		// Open sea between Rimmington and Karamja
		SailingMoves moves = SailingMoves.forSpeed(1.5);
		int target = WorldPointUtil.packWorldPoint(2963, 3109, 0);
		List<PathStep> path = findPath(WorldPointUtil.packWorldPoint(2948, 3074, 0), target, moves, hull(SLOOP, 0, 0));

		assertTrue(endsCovering(path, moves, hull(SLOOP, 0, 0), target));
	}

	@Test
	public void testRaftGetsPastTheElidDeltaIslands()
	{
		SailingMoves moves = SailingMoves.forSpeed(1.5);
		List<PathStep> path = findPath(UNKAH_HARBOUR, TOP_OF_ELID_DELTA, moves, hull(RAFT, 0, 0));

		assertTrue(endsCovering(path, moves, hull(RAFT, 0, 0), TOP_OF_ELID_DELTA));
		assertNull(hullHit(path, moves, 0.5, 1.5, 1.5));
	}

	@Test
	public void testSloopCantGetPastTheElidDeltaIslands()
	{
		// As in game, the channels between the islands are too narrow for a sloop
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, UNKAH_HARBOUR, Set.of(TOP_OF_ELID_DELTA), null,
			SailingMoves.forSpeed(1.5), hull(SLOOP, 0, 0));
		pathfinder.run();

		assertFalse(pathfinder.getResult().isReached());
	}

	@Test
	public void testBoatSailsOutOfTilesItsHullSitsOn()
	{
		// Moored on the 3x2 shipwreck at (2704-2706, 3050-3051), a raft's hull overlaps blocked tiles
		// where it starts; like a boat on a blocked tile it can sail out of them with its first move,
		// but not over other blocked tiles its hull doesn't already cover
		BoatHull raft = hull(RAFT, 0, 0);
		assertTrue("North over the wreck row its hull sits on", raft.canMove(map, 2705, 3050, 0, NORTH, 0, 3, true));
		assertTrue("South out of the wreck", raft.canMove(map, 2705, 3050, 0, SOUTH, 0, -3, true));
		assertFalse("Only the first move gets the escape", raft.canMove(map, 2705, 3050, 0, NORTH, 0, 3));
		assertFalse("Facing along the wreck, its hull doesn't cover the other row",
			raft.canMove(map, 2705, 3050, 0, WEST, 0, 3, true));

		int start = WorldPointUtil.packWorldPoint(2705, 3050, 0);
		int target = WorldPointUtil.packWorldPoint(2705, 3042, 0);
		findPath(start, target, SailingMoves.forSpeed(1.5), raft);
	}

	@Test
	public void testSailingSearchesWhenNoGoalIsViable()
	{
		// A target on the shipwreck is blocked, and with the unreachable distance at 0 it resolves to
		// no goal tiles: a walking search is skipped outright, but a sailing search still runs — its
		// hull covering the target doesn't need a walkable goal tile
		when(config.unreachableTargetDistance()).thenReturn(0);
		pathfinderConfig.refresh();
		int start = WorldPointUtil.packWorldPoint(2705, 3044, 0);
		int target = WorldPointUtil.packWorldPoint(2705, 3050, 0);

		Pathfinder walking = new Pathfinder(pathfinderConfig, start, Set.of(target));
		walking.run();
		assertEquals(0, walking.getResult().getNodesChecked());

		Pathfinder sailing = new Pathfinder(pathfinderConfig, start, Set.of(target), null,
			SailingMoves.forSpeed(1.5), hull(RAFT, 0, 0));
		sailing.run();
		assertTrue(sailing.getResult().getNodesChecked() > 0);
	}

	@Test
	public void testACostlierMoveStillQueuesWhenItWouldArrive()
	{
		// With the target a tile east, a raft only covers it facing east: if the cheapest route to
		// that tile faces another way, a costlier move facing east must still be queued
		SailingMoves moves = SailingMoves.forSpeed(1.5);
		int target = WorldPointUtil.packWorldPoint(2706, 3049, 0);
		SailingSearch search = new SailingSearch(moves, hull(RAFT, 0, 0), new int[]{target}, new int[]{target});

		int tile = WorldPointUtil.packWorldPoint(2705, 3049, 0);
		int eastMove = moves.indexOf(3, 0);
		int northMove = moves.indexOf(0, 3);
		assertTrue(search.queueArrival(tile, eastMove, 2705, 3049, 0, false));
		assertFalse("A move is only let through once", search.queueArrival(tile, eastMove, 2705, 3049, 0, false));
		assertFalse("Facing north doesn't cover the target", search.queueArrival(tile, northMove, 2705, 3049, 0, false));
		assertFalse("Without a hull, facing doesn't matter", new SailingSearch(moves, null, new int[]{target},
			new int[]{target}).queueArrival(tile, eastMove, 2705, 3049, 0, false));
	}

	@Test
	public void testRaftSailsOutOfTheLumLagoon()
	{
		// West out of the Lum Lagoon through the channel past the stepping stone (see above)
		SailingMoves moves = SailingMoves.forSpeed(1.5);
		int west = WorldPointUtil.packWorldPoint(3196, 3125, 0);
		List<PathStep> path = findPath(WorldPointUtil.packWorldPoint(3229, 3124, 0), west, moves, hull(RAFT, 0, 0));

		assertTrue(endsCovering(path, moves, hull(RAFT, 0, 0), west));
		assertNull(hullHit(path, moves, 0.5, 1.5, 1.5));
	}

	// The boat sitting (pivotX, pivotY) local units from the centre of its tile
	private static BoatHull hull(int[] bounds, int pivotX, int pivotY)
	{
		return BoatHull.fromBounds(bounds[0], bounds[1], bounds[2], bounds[3], pivotX, pivotY);
	}

	// Whether the boat's hull, where the path ends and facing the way it sailed there, covers the target
	private static boolean endsCovering(List<PathStep> path, SailingMoves moves, BoatHull hull, int target)
	{
		int from = path.get(path.size() - 2).getPackedPosition();
		int end = path.get(path.size() - 1).getPackedPosition();
		int heading = moves.headingOf(WorldPointUtil.unpackWorldX(end) - WorldPointUtil.unpackWorldX(from),
			WorldPointUtil.unpackWorldY(end) - WorldPointUtil.unpackWorldY(from));
		return hull.covers(heading, WorldPointUtil.unpackWorldX(target) - WorldPointUtil.unpackWorldX(end),
			WorldPointUtil.unpackWorldY(target) - WorldPointUtil.unpackWorldY(end));
	}

	private List<PathStep> findPath(int start, int target, SailingMoves moves, BoatHull hull)
	{
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(target), null, moves, hull);
		pathfinder.run();
		assertTrue("Target should be reachable", pathfinder.getResult().isReached());
		return pathfinder.getPath();
	}

	// Checks every move independently of BoatHull: samples the hull's rectangle every quarter tile along the move and
	// tests it against each nearby tile's square, allowing edges to touch. Describes the first blocked tile the hull runs
	// over, or returns null if there are none.
	private String hullHit(List<PathStep> path, SailingMoves moves, double halfWidth, double stern, double bow)
	{
		for (int i = 1; i < path.size(); i++)
		{
			int from = path.get(i - 1).getPackedPosition();
			int to = path.get(i).getPackedPosition();
			int x = WorldPointUtil.unpackWorldX(from);
			int y = WorldPointUtil.unpackWorldY(from);
			int dx = WorldPointUtil.unpackWorldX(to) - x;
			int dy = WorldPointUtil.unpackWorldY(to) - y;
			double angle = moves.headingOf(dx, dy) * Math.PI / 8;
			double forwardX = -Math.sin(angle);
			double forwardY = -Math.cos(angle);
			double halfLength = (stern + bow) / 2;
			int samples = (int) Math.ceil(Math.hypot(dx, dy) * 4);
			for (int s = 0; s <= samples; s++)
			{
				double centreX = x + dx * (double) s / samples + forwardX * (bow - stern) / 2;
				double centreY = y + dy * (double) s / samples + forwardY * (bow - stern) / 2;
				for (int tileX = (int) centreX - 8; tileX <= (int) centreX + 8; tileX++)
				{
					for (int tileY = (int) centreY - 8; tileY <= (int) centreY + 8; tileY++)
					{
						double ex = centreX - tileX;
						double ey = centreY - tileY;
						boolean overlaps = Math.abs(ex) < 0.5 + halfLength * Math.abs(forwardX) + halfWidth * Math.abs(forwardY) - 1e-9
							&& Math.abs(ey) < 0.5 + halfLength * Math.abs(forwardY) + halfWidth * Math.abs(forwardX) - 1e-9
							&& Math.abs(ex * forwardX + ey * forwardY) < halfLength + 0.5 * (Math.abs(forwardX) + Math.abs(forwardY)) - 1e-9
							&& Math.abs(ey * forwardX - ex * forwardY) < halfWidth + 0.5 * (Math.abs(forwardX) + Math.abs(forwardY)) - 1e-9;
						if (overlaps && map.isBlocked(tileX, tileY, 0))
						{
							return "Move " + i + " from (" + x + ", " + y + ") runs the hull over blocked tile (" + tileX + ", " + tileY + ")";
						}
					}
				}
			}
		}
		return null;
	}
}
