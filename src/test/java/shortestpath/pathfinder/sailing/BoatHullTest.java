package shortestpath.pathfinder.sailing;

import java.util.List;
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
import shortestpath.TestShortestPathConfig;
import shortestpath.settings.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TestPathfinderConfig;

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
	private TestShortestPathConfig config = new TestShortestPathConfig();
	private PathfinderConfig pathfinderConfig;
	private CollisionMap map;

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
		map = pathfinderConfig.getMap();
	}

	@Test
	public void testSloopFitsAThreeTileGapOnlyWhenLinedUp()
	{
		// Rocks at (2630, 2548) and (2634, 2548) south of Pest Control leave a gap three tiles wide, which a sloop
		// fits through sailing straight north with the boat on a tile centre, touching the rocks either side
		assertTrue(hull(SLOOP).canMove(map, 2632, 2538, 0, NORTH, 0, 12));
		assertFalse("Turned a heading", hull(SLOOP).canMove(map, 2632, 2538, 0, NORTH_NORTH_EAST, 0, 12));
	}

	@Test
	public void testOnlyARaftFitsAOneTileChannel()
	{
		// Rocks either side of x 2796 from y 2962 to 2967 leave a channel one tile wide
		assertTrue(hull(RAFT).canMove(map, 2796, 2963, 0, NORTH, 0, 5));
		assertFalse(hull(SKIFF).canMove(map, 2796, 2963, 0, NORTH, 0, 5));
	}

	@Test
	public void testHullCoversTheTilesUnderIt()
	{
		// A raft is a tile wide and 3 long, so sitting on a tile it covers that tile and the ones fore and aft
		assertTrue(hull(RAFT).covers(NORTH, 0, 1));
		assertTrue(hull(RAFT).covers(NORTH, 0, -1));
		assertFalse(hull(RAFT).covers(NORTH, 1, 0));
		assertTrue(hull(RAFT).covers(EAST, 1, 0));
		assertFalse(hull(RAFT).covers(EAST, 0, 1));
	}

	@Test
	public void testSloopRouteKeepsItsWholeHullClear()
	{
		// From open sea past the rocks off Mudskipper Point to the water off the Pandemonium
		SailingMoves moves = SailingMoves.forSpeed(3.0);
		int start = WorldPointUtil.packWorldPoint(2948, 3074, 0);
		int target = WorldPointUtil.packWorldPoint(3074, 2983, 0);

		List<PathStep> raftPath = findPath(start, target, moves, hull(RAFT));
		List<PathStep> sloopPath = findPath(start, target, moves, hull(SLOOP));

		assertNotNull("A raft's route takes a sloop over rocks", hullHit(raftPath, moves, 1.5, 3, 7));
		assertNull(hullHit(sloopPath, moves, 1.5, 3, 7));
	}

	@Test
	public void testSloopEndsWithItsHullOverAnOpenSeaTarget()
	{
		// Open sea between Rimmington and Karamja
		SailingMoves moves = SailingMoves.forSpeed(1.5);
		int target = WorldPointUtil.packWorldPoint(2963, 3109, 0);
		List<PathStep> path = findPath(WorldPointUtil.packWorldPoint(2948, 3074, 0), target, moves, hull(SLOOP));

		assertTrue(endsCovering(path, moves, hull(SLOOP), target));
	}

	@Test
	public void testRaftGetsPastTheElidDeltaIslands()
	{
		SailingMoves moves = SailingMoves.forSpeed(1.5);
		List<PathStep> path = findPath(UNKAH_HARBOUR, TOP_OF_ELID_DELTA, moves, hull(RAFT));

		assertTrue(endsCovering(path, moves, hull(RAFT), TOP_OF_ELID_DELTA));
		assertNull(hullHit(path, moves, 0.5, 1.5, 1.5));
	}

	@Test
	public void testSloopCantGetPastTheElidDeltaIslands()
	{
		// As in game, the channels between the islands are too narrow for a sloop
		assertNull(TestSail.sail(map, SailingMoves.forSpeed(1.5), hull(SLOOP), -1, UNKAH_HARBOUR,
			TOP_OF_ELID_DELTA));
	}

	@Test
	public void testABoatOverBlockedTilesCannotMove()
	{
		// On the 3x2 shipwreck at (2704-2706, 3050-3051) a raft's hull overlaps blocked tiles: no boat can be there,
		// so no move leaves it; a moored boat sets out from where it casts off instead
		BoatHull raft = hull(RAFT);
		assertFalse(raft.fits(map, 2705, 3050, 0));
		assertFalse("North over the wreck", raft.canMove(map, 2705, 3050, 0, NORTH, 0, 3));
		assertFalse("South out of the wreck", raft.canMove(map, 2705, 3050, 0, SOUTH, 0, -3));
		assertTrue("Clear of it", raft.fits(map, 2705, 3046, 0));
	}

	@Test
	public void testAStartTargetCountsOnlyOnTheBoatsHeading()
	{
		// A sloop reaches 7 tiles toward its bow but only 3 astern: a target 5 tiles north is under the
		// hull only facing north
		int here = WorldPointUtil.packWorldPoint(2948, 3074, 0);
		int target = WorldPointUtil.packWorldPoint(2948, 3079, 0);
		BoatHull sloop = hull(SLOOP);

		assertFalse("Facing south the target is astern, out of the hull", sloop.coversTile(target, here, SOUTH));
		assertTrue("Facing north it's under the bow", sloop.coversTile(target, here, NORTH));
		assertTrue("Unknown heading tries them all", sloop.coversTile(target, here, -1));
	}

	@Test
	public void testASailingSearchDoesNotArriveOnTheSpotBehindTheStern()
	{
		// Aboard in open water at (2705, 3038) facing south, the target at (2705, 3043) is under the hull only
		// facing north: the search must sail round to it rather than ending with the boat still on the spot, which
		// is what trying every heading at the start did
		int start = WorldPointUtil.packWorldPoint(2705, 3038, 0);
		int target = WorldPointUtil.packWorldPoint(2705, 3043, 0);

		SailingMoves moves = SailingMoves.forSpeed(1.5);
		List<PathStep> astern = TestSail.sail(map, moves, hull(SLOOP), SOUTH, start, target);
		assertNotNull("Still reached it by sailing", astern);
		assertTrue("Not on the spot facing away from the target", astern.size() > 1);

		List<PathStep> facing = TestSail.sail(map, moves, hull(SLOOP), NORTH, start, target);
		assertEquals("A target already under the bow still arrives on the spot", 1, facing.size());

		List<PathStep> noHeading = TestSail.sail(map, moves, hull(SLOOP), -1, start, target);
		assertEquals("An unknown heading still counts an under-hull target as arrived", 1, noHeading.size());
	}

	@Test
	public void testRaftSailsOutOfTheLumLagoon()
	{
		// West out of the Lum Lagoon through the channel past the stepping stone (see above)
		SailingMoves moves = SailingMoves.forSpeed(1.5);
		int west = WorldPointUtil.packWorldPoint(3196, 3125, 0);
		List<PathStep> path = findPath(WorldPointUtil.packWorldPoint(3229, 3124, 0), west, moves, hull(RAFT));

		assertTrue(endsCovering(path, moves, hull(RAFT), west));
		assertNull(hullHit(path, moves, 0.5, 1.5, 1.5));
	}

	private static BoatHull hull(int[] bounds)
	{
		return BoatHull.fromBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
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
		List<PathStep> path = TestSail.sail(map, moves, hull, -1, start, target);
		assertNotNull("Target should be reachable", path);
		return path;
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
