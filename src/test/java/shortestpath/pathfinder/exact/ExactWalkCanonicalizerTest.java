package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class ExactWalkCanonicalizerTest
{
	// Away from the wilderness, so every global teleport is castable.
	private static final int BASE_X = 3200, BASE_Y = 3200;
	private static final PreparedRoutingAccount NO_TRANSPORTS = PreparedRoutingAccount.compile(null, null, false,
		java.util.Set.of(), false, ignored -> 0);

	@Test
	public void straightWalkStaysStraight()
	{
		Shape shape = walk(new Grid(), 0, 0, 40, WalkGoal.tile(pack(0, 40)));

		assertEquals(40, shape.ticks);
		assertEquals(40, shape.axis);
		assertEquals(0, shape.turns);
	}

	@Test
	public void diagonalWalkStaysDiagonal()
	{
		Shape shape = walk(new Grid(), 0, 0, 40, WalkGoal.tile(pack(40, 40)));

		assertEquals(40, shape.ticks);
		assertEquals(80, shape.axis);
		assertEquals(0, shape.turns);
		assertEquals("NE40", shape.runs);
	}

	@Test
	public void unequalDeltasTakeOneDiagonalAndNoAlternation()
	{
		Shape shape = walk(new Grid(), 0, 0, 40, WalkGoal.tile(pack(1, 40)));

		assertEquals(40, shape.ticks);
		assertEquals(41, shape.axis);
		assertEquals(1, shape.turns);
		assertEquals(1, shape.diagonals);
	}

	@Test
	public void sidewaysDetoursThatCancelAreRemoved()
	{
		Grid grid = new Grid();
		// Same ticks, but west then back east: 2 more axis travel than walking straight north.
		List<PathStep> route = steps(0, 0, -1, 1, -1, 2, 0, 3, 0, 4);
		assertEquals(6, shape(route, 0, 4).axis);

		ExactWalkCanonicalizer.Result result = canonicalize(grid, NO_TRANSPORTS, route);

		assertEquals(List.of(), result.diagnostics());
		assertEquals(describe(steps(0, 0, 0, 1, 0, 2, 0, 3, 0, 4)), describe(result.path()));
	}

	@Test
	public void fewerTurnsWinAmongEquallyDirectWalks()
	{
		Grid grid = new Grid();
		// N NE N NE N N: as few ticks and as little axis travel as possible, but four turns.
		List<PathStep> route = steps(0, 0, 0, 1, 1, 2, 1, 3, 2, 4, 2, 5, 2, 6);
		Shape before = shape(route, 0, 6);
		assertEquals(8, before.axis);
		assertEquals(4, before.turns);

		ExactWalkCanonicalizer.Result result = canonicalize(grid, NO_TRANSPORTS, route);

		Shape after = shape(result.path(), 0, 6);
		assertEquals(6, after.ticks);
		assertEquals(8, after.axis);
		assertEquals(1, after.turns);
		assertEquals(last(route), last(result.path()));
	}

	@Test
	public void wallsAreWalkedAroundWithTheReferenceOrdering()
	{
		Set<Integer> blocked = new HashSet<>();
		for (int x = -3; x <= 4; x++) blocked.add(pack(x, 5));
		for (int y = 5; y <= 9; y++) blocked.add(pack(4, y));
		Grid grid = new Grid(blocked);
		WalkGoal goal = WalkGoal.tile(pack(1, 12));
		int[] reference = Reference.best(grid, pack(0, 0), goal, 30);
		assertNotNull(reference);

		Shape shape = walk(grid, 0, 0, reference[0], goal);

		assertArrayEquals(reference, new int[] {shape.ticks, shape.axis, shape.turns});
		assertLegal(grid, shape.tiles);
	}

	@Test
	public void randomGridsMatchTheReferenceOrdering()
	{
		Random random = new Random(20261002);
		int checked = 0;
		for (int trial = 0; trial < 300; trial++)
		{
			Set<Integer> blocked = new HashSet<>();
			for (int x = -1; x <= 13; x++)
				for (int y = -1; y <= 13; y++)
					if (x < 0 || y < 0 || x > 12 || y > 12 || random.nextInt(100) < 22) blocked.add(pack(x, y));
			int start = pack(random.nextInt(13), random.nextInt(13));
			blocked.remove(start);
			int goalCount = 1 + random.nextInt(3);
			int[] goals = new int[goalCount];
			for (int i = 0; i < goalCount; i++)
			{
				goals[i] = pack(random.nextInt(13), random.nextInt(13));
				blocked.remove(goals[i]);
			}
			Grid grid = new Grid(blocked);
			WalkGoal goal = WalkGoal.tiles(goals, 0);
			int[] reference = Reference.best(grid, start, goal, 60);
			ExactWalkCanonicalizer canonicalizer = new ExactWalkCanonicalizer(grid, NO_TRANSPORTS);
			if (reference == null || reference[0] == 0) continue;
			ExactWalkCanonicalizer.Walk walk = canonicalizer.walk(start, reference[0], goal);
			assertEquals("trial " + trial, ExactWalkCanonicalizer.Outcome.CANONICAL, walk.outcome);
			Shape shape = Shape.of(start, walk.tiles);
			assertArrayEquals("trial " + trial, reference, new int[] {shape.ticks, shape.axis, shape.turns});
			assertTrue(goal.contains(x(walk.tiles[walk.tiles.length - 1]), y(walk.tiles[walk.tiles.length - 1])));
			assertLegal(grid, shape.tiles);
			// Asked for a longer leg than the shortest, it reports the route as too long.
			assertEquals(ExactWalkCanonicalizer.Outcome.SHORTER_ROUTE,
				canonicalizer.walk(start, reference[0] + 1, goal).outcome);
			checked++;
		}
		assertTrue("too few reachable trials: " + checked, checked > 150);
	}

	@Test
	public void teleportAreaIsLeftStraightAhead()
	{
		// Level 30 wilderness above-ground ends below y = 3760; level 20 below y = 3680.
		Shape wilderness = walk(new Grid(), 3000 - BASE_X, 3800 - BASE_Y, 41,
			WalkGoal.teleportArea(TeleportCapability.WILDERNESS));
		assertEquals("S41", wilderness.runs);
		assertEquals(41, wilderness.axis);

		Shape all = walk(new Grid(), 3000 - BASE_X, 3700 - BASE_Y, 21, WalkGoal.teleportArea(TeleportCapability.ALL));
		assertEquals("S21", all.runs);
	}

	@Test
	public void teleportAreaAroundAWallTakesTheSmallestDetour()
	{
		Set<Integer> blocked = new HashSet<>();
		for (int x = 2995; x <= 3005; x++) blocked.add(WorldPointUtil.packWorldPoint(x, 3790, 0));
		Grid grid = new Grid(blocked);
		WalkGoal goal = WalkGoal.teleportArea(TeleportCapability.WILDERNESS);
		int start = WorldPointUtil.packWorldPoint(3000, 3800, 0);
		int[] reference = Reference.best(grid, start, goal, 45);
		assertNotNull(reference);

		ExactWalkCanonicalizer.Walk walk = new ExactWalkCanonicalizer(grid, NO_TRANSPORTS).walk(start, 41, goal);

		Shape shape = Shape.of(start, walk.tiles);
		assertEquals(41, reference[0]);
		assertArrayEquals(reference, new int[] {shape.ticks, shape.axis, shape.turns});
		// Six tiles sideways to pass the wall and no further, and no coming back.
		assertEquals(41 + 6, shape.axis);
		assertEquals(3759, y(last(walk.tiles)));
		assertLegal(grid, shape.tiles);
	}

	@Test
	public void legBeforeAGlobalTeleportEndsAnywhereItCanBeCast()
	{
		Grid grid = new Grid();
		// The search drifted east while walking south to the level 30 line, then teleported.
		List<PathStep> route = new ArrayList<>();
		route.add(new PathStep(WorldPointUtil.packWorldPoint(3000, 3800, 0), false));
		for (int i = 1; i <= 41; i++)
			route.add(new PathStep(WorldPointUtil.packWorldPoint(3000 + (i <= 10 ? i : 10), 3800 - i, 0), false));
		int destination = WorldPointUtil.packWorldPoint(3213, 3424, 0);
		route.add(new PathStep(destination, false));
		byte[] arrivals = new byte[route.size()];
		arrivals[route.size() - 1] = ExactRoute.FROM_WILDERNESS_HUB;
		int[] costs = new int[route.size()];
		for (int i = 0; i < 42; i++) costs[i] = i;
		costs[42] = 41 + 4;

		ExactWalkCanonicalizer.Result result = new ExactWalkCanonicalizer(grid, NO_TRANSPORTS)
			.canonicalize(new ExactRoute(route, arrivals, costs));

		assertEquals(List.of(), result.diagnostics());
		assertEquals(route.size(), result.path().size());
		assertEquals(WorldPointUtil.packWorldPoint(3000, 3759, 0), result.path().get(41).getPackedPosition());
		assertEquals(destination, result.path().get(42).getPackedPosition());
		assertEquals("S41", shape(result.path(), 0, 41).runs);
	}

	@Test
	public void legBeforeATransportEndsAtAnyOriginOfTheSameAction()
	{
		Grid grid = new Grid();
		int destination = pack(50, 50);
		PreparedRoutingAccount account = account(
			transport(pack(0, 10), destination, "Climb-up Ladder 1"),
			transport(pack(5, 10), destination, "Climb-up Ladder 1"));
		// Ten ticks to either origin; the search went to the far one.
		List<PathStep> route = steps(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5, 6, 5, 7, 5, 8, 5, 9, 5, 10, 50, 50);

		ExactWalkCanonicalizer.Result result = canonicalize(grid, account, route);

		assertEquals(List.of(), result.diagnostics());
		assertEquals(pack(0, 10), result.path().get(10).getPackedPosition());
		assertEquals("N10", shape(result.path(), 0, 10).runs);
		assertEquals(destination, last(result.path()));
	}

	@Test
	public void legBeforeATransportKeepsItsOriginWhenTheActionDiffers()
	{
		Grid grid = new Grid();
		int destination = pack(50, 50);
		PreparedRoutingAccount account = account(
			transport(pack(0, 10), destination, "Climb-up Ladder 1"),
			transport(pack(5, 10), destination, "Climb-up Ladder 2"));
		List<PathStep> route = steps(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5, 6, 5, 7, 5, 8, 5, 9, 5, 10, 50, 50);

		ExactWalkCanonicalizer.Result result = canonicalize(grid, account, route);

		assertEquals(List.of(), result.diagnostics());
		assertEquals(pack(5, 10), result.path().get(10).getPackedPosition());
		Shape shape = shape(result.path(), 0, 10);
		assertEquals(15, shape.axis);
		assertEquals(1, shape.turns);
	}

	@Test
	public void legOntoABlockedTransportOriginStillEndsThere()
	{
		int door = pack(0, 5);
		Grid grid = new Grid(Set.of(door, pack(-1, 5), pack(1, 5)));
		PreparedRoutingAccount account = account(transport(door, pack(0, 6), "Open Door 1"));
		// Stepping onto the blocked door tile to open it, as the exact search does.
		List<PathStep> route = steps(0, 0, 1, 1, 1, 2, 0, 3, 0, 4, 0, 5, 0, 6);

		ExactWalkCanonicalizer.Result result = canonicalize(grid, account, route);

		assertEquals(List.of(), result.diagnostics());
		assertEquals(describe(steps(0, 0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6)), describe(result.path()));
	}

	@Test
	public void bankVisitsKeepTheirTile()
	{
		Grid grid = new Grid();
		List<PathStep> route = new ArrayList<>(steps(0, 0, 1, 1, 0, 2));
		route.add(new PathStep(pack(0, 2), true));
		route.add(new PathStep(pack(1, 3), true));
		route.add(new PathStep(pack(0, 4), true));

		ExactWalkCanonicalizer.Result result = canonicalize(grid, NO_TRANSPORTS, route);

		List<PathStep> expected = new ArrayList<>(steps(0, 0, 0, 1, 0, 2));
		expected.add(new PathStep(pack(0, 2), true));
		expected.add(new PathStep(pack(0, 3), true));
		expected.add(new PathStep(pack(0, 4), true));
		assertEquals(describe(expected), describe(result.path()));
		assertEquals(2, result.legs());
	}

	@Test
	public void aLegLongerThanItsShortestWalkIsReportedAndKept()
	{
		Grid grid = new Grid();
		// Six ticks to a tile three ticks away: the route is not optimal, so nothing is changed.
		List<PathStep> route = steps(0, 0, -1, 1, -1, 2, 0, 3, 1, 3, 2, 3, 3, 2);

		ExactWalkCanonicalizer.Result result = canonicalize(grid, NO_TRANSPORTS, route);

		assertEquals(1, result.diagnostics().size());
		assertEquals(ExactWalkCanonicalizer.Outcome.SHORTER_ROUTE, result.diagnostics().get(0).outcome());
		assertEquals(describe(route), describe(result.path()));
	}

	@Test
	public void aGoalOutOfReachIsReported()
	{
		Set<Integer> blocked = new HashSet<>();
		for (int x = -1; x <= 1; x++)
			for (int y = 9; y <= 11; y++)
				if (x != 0 || y != 10) blocked.add(pack(x, y));
		ExactWalkCanonicalizer canonicalizer = new ExactWalkCanonicalizer(new Grid(blocked), NO_TRANSPORTS);

		assertEquals(ExactWalkCanonicalizer.Outcome.NO_ROUTE,
			canonicalizer.walk(pack(0, 0), 10, WalkGoal.tile(pack(0, 10))).outcome);
		assertEquals(ExactWalkCanonicalizer.Outcome.NO_ROUTE,
			canonicalizer.walk(pack(0, 0), 3, WalkGoal.tile(pack(0, 10))).outcome);
	}

	@Test
	public void equalWalksAreChosenDeterministically()
	{
		Grid grid = new Grid(Set.of(pack(3, 3)));
		ExactWalkCanonicalizer canonicalizer = new ExactWalkCanonicalizer(grid, NO_TRANSPORTS);
		int[] first = canonicalizer.walk(pack(0, 0), 8, WalkGoal.tile(pack(4, 8))).tiles;
		int[] again = new ExactWalkCanonicalizer(grid, NO_TRANSPORTS).walk(pack(0, 0), 8, WalkGoal.tile(pack(4, 8))).tiles;
		canonicalizer.walk(pack(2, 0), 5, WalkGoal.tile(pack(7, 0)));
		int[] reused = canonicalizer.walk(pack(0, 0), 8, WalkGoal.tile(pack(4, 8))).tiles;

		assertArrayEquals(first, again);
		assertArrayEquals(first, reused);
	}

	// ---- helpers ----

	private static ExactWalkCanonicalizer.Result canonicalize(Grid grid, PreparedRoutingAccount account,
		List<PathStep> route)
	{
		int[] costs = new int[route.size()];
		for (int i = 1; i < costs.length; i++) costs[i] = costs[i - 1] + 1;
		return new ExactWalkCanonicalizer(grid, account).canonicalize(ExactRoute.of(route, costs));
	}

	private static Shape walk(Grid grid, int x, int y, int ticks, WalkGoal goal)
	{
		int start = pack(x, y);
		ExactWalkCanonicalizer.Walk walk = new ExactWalkCanonicalizer(grid, NO_TRANSPORTS).walk(start, ticks, goal);
		assertEquals(ExactWalkCanonicalizer.Outcome.CANONICAL, walk.outcome);
		Shape shape = Shape.of(start, walk.tiles);
		assertLegal(grid, shape.tiles);
		return shape;
	}

	private static PreparedRoutingAccount account(Transport... transports)
	{
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(transports),
			TransportAvailabilityFixture.of(transports), false, java.util.Set.of(), true, ignored -> 0);
	}

	private static Transport transport(int origin, int destination, String objectInfo)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination).type(TransportType.TRANSPORT)
			.duration(1).objectInfo(objectInfo).build();
	}

	private static void assertLegal(Grid grid, int[] tiles)
	{
		for (int i = 1; i < tiles.length; i++)
		{
			boolean found = false;
			for (int next : grid.ordinaryWalkingNeighbors(tiles[i - 1])) found |= next == tiles[i];
			assertTrue("step " + i + " is not a walk", found);
		}
	}

	private static Shape shape(List<PathStep> path, int from, int to)
	{
		int[] tiles = new int[to - from];
		for (int i = from + 1; i <= to; i++) tiles[i - from - 1] = path.get(i).getPackedPosition();
		return Shape.of(path.get(from).getPackedPosition(), tiles);
	}

	private static List<String> describe(List<PathStep> path)
	{
		List<String> tiles = new ArrayList<>();
		for (PathStep step : path)
			tiles.add((x(step.getPackedPosition()) - BASE_X) + "," + (y(step.getPackedPosition()) - BASE_Y)
				+ (step.isBankVisited() ? " banked" : ""));
		return tiles;
	}

	private static List<PathStep> steps(int... xy)
	{
		List<PathStep> steps = new ArrayList<>();
		for (int i = 0; i < xy.length; i += 2) steps.add(new PathStep(pack(xy[i], xy[i + 1]), false));
		return steps;
	}

	private static int last(List<PathStep> path)
	{
		return path.get(path.size() - 1).getPackedPosition();
	}

	private static int last(int[] tiles)
	{
		return tiles[tiles.length - 1];
	}

	private static int pack(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(BASE_X + x, BASE_Y + y, 0);
	}

	private static int x(int packed)
	{
		return WorldPointUtil.unpackWorldX(packed);
	}

	private static int y(int packed)
	{
		return WorldPointUtil.unpackWorldY(packed);
	}

	/** Ticks, axis travel and heading changes of a walk, and its run-length directions. */
	private static final class Shape
	{
		int ticks, axis, turns, diagonals;
		String runs;
		int[] tiles;

		static Shape of(int start, int[] walk)
		{
			Shape shape = new Shape();
			shape.tiles = new int[walk.length + 1];
			shape.tiles[0] = start;
			System.arraycopy(walk, 0, shape.tiles, 1, walk.length);
			StringBuilder runs = new StringBuilder();
			String previous = null;
			int length = 0;
			for (int i = 1; i < shape.tiles.length; i++)
			{
				int dx = x(shape.tiles[i]) - x(shape.tiles[i - 1]), dy = y(shape.tiles[i]) - y(shape.tiles[i - 1]);
				shape.ticks++;
				shape.axis += Math.abs(dx) + Math.abs(dy);
				if (dx != 0 && dy != 0) shape.diagonals++;
				String direction = (dy > 0 ? "N" : dy < 0 ? "S" : "") + (dx > 0 ? "E" : dx < 0 ? "W" : "");
				if (!direction.equals(previous))
				{
					if (previous != null)
					{
						shape.turns++;
						runs.append(previous).append(length).append(' ');
					}
					previous = direction;
					length = 0;
				}
				length++;
			}
			if (previous != null) runs.append(previous).append(length);
			shape.runs = runs.toString();
			return shape;
		}
	}

	/**
	 * The best (ticks, axis travel, turns) by brute force: the least ticks at which any goal tile is
	 * reached, then a layer-by-layer table of the best (axis travel, turns) per (tile, direction)
	 * over walks of exactly that many moves. Independent of the A* it checks.
	 */
	static final class Reference
	{
		private static final int[] DX = {-1, 1, 0, 0, -1, 1, -1, 1};
		private static final int[] DY = {0, 0, -1, 1, -1, -1, 1, 1};

		static int[] best(CollisionMap map, int start, WalkGoal goal, int maxTicks)
		{
			IntPredicate inGoal = tile -> goal.contains(x(tile), y(tile));
			if (inGoal.test(start)) return new int[] {0, 0, 0};
			// Layer t maps (tile, direction) to (axis, turns); direction 8 is the start's none.
			Map<Long, int[]> layer = new HashMap<>();
			layer.put(key(start, 8), new int[] {0, 0});
			for (int ticks = 1; ticks <= maxTicks; ticks++)
			{
				Map<Long, int[]> next = new HashMap<>();
				for (Map.Entry<Long, int[]> entry : layer.entrySet())
				{
					int tile = (int) (entry.getKey() >> 4), incoming = (int) (entry.getKey() & 15);
					// Goal tiles end a walk; walks never pass through them.
					if (inGoal.test(tile)) continue;
					for (int neighbor : map.ordinaryWalkingNeighbors(tile))
					{
						int dx = x(neighbor) - x(tile), dy = y(neighbor) - y(tile), direction = 0;
						while (DX[direction] != dx || DY[direction] != dy) direction++;
						int axis = entry.getValue()[0] + Math.abs(dx) + Math.abs(dy);
						int turns = entry.getValue()[1] + (incoming == 8 || incoming == direction ? 0 : 1);
						next.merge(key(neighbor, direction), new int[] {axis, turns},
							(a, b) -> a[0] != b[0] ? (a[0] < b[0] ? a : b) : a[1] <= b[1] ? a : b);
					}
				}
				int[] best = null;
				for (Map.Entry<Long, int[]> entry : next.entrySet())
				{
					if (!inGoal.test((int) (entry.getKey() >> 4))) continue;
					int[] value = entry.getValue();
					if (best == null || value[0] < best[0] || value[0] == best[0] && value[1] < best[1]) best = value;
				}
				if (best != null) return new int[] {ticks, best[0], best[1]};
				layer = next;
			}
			return null;
		}

		private static long key(int tile, int direction)
		{
			return (long) tile << 4 | direction;
		}
	}

	/** Open ground except for fully blocked tiles; diagonals need both flanking tiles open. */
	static final class Grid extends CollisionMap
	{
		private final Set<Integer> blocked;

		Grid()
		{
			this(Set.of());
		}

		Grid(Set<Integer> blocked)
		{
			super(null);
			this.blocked = blocked;
		}

		@Override
		public boolean isBlocked(int x, int y, int z)
		{
			return blocked.contains(WorldPointUtil.packWorldPoint(x, y, z));
		}

		@Override
		public byte ordinaryWalkingMask(int packedPoint)
		{
			int x = x(packedPoint), y = y(packedPoint), z = WorldPointUtil.unpackWorldPlane(packedPoint);
			if (isBlocked(x, y, z)) return 0;
			boolean n = !isBlocked(x, y + 1, z), e = !isBlocked(x + 1, y, z);
			boolean s = !isBlocked(x, y - 1, z), w = !isBlocked(x - 1, y, z);
			int mask = 0;
			if (n) mask |= 1;
			if (n && e && !isBlocked(x + 1, y + 1, z)) mask |= 1 << 1;
			if (e) mask |= 1 << 2;
			if (s && e && !isBlocked(x + 1, y - 1, z)) mask |= 1 << 3;
			if (s) mask |= 1 << 4;
			if (s && w && !isBlocked(x - 1, y - 1, z)) mask |= 1 << 5;
			if (w) mask |= 1 << 6;
			if (n && w && !isBlocked(x - 1, y + 1, z)) mask |= 1 << 7;
			return (byte) mask;
		}

		@Override
		public int[] ordinaryWalkingNeighbors(int packedPoint)
		{
			int[] dx = {0, 1, 1, 1, 0, -1, -1, -1}, dy = {1, 1, 0, -1, -1, -1, 0, 1};
			int x = x(packedPoint), y = y(packedPoint), z = WorldPointUtil.unpackWorldPlane(packedPoint);
			int mask = Byte.toUnsignedInt(ordinaryWalkingMask(packedPoint));
			int[] result = new int[8];
			int count = 0;
			for (int bit = 0; bit < 8; bit++)
				if ((mask & 1 << bit) != 0) result[count++] = WorldPointUtil.packWorldPoint(x + dx[bit], y + dy[bit], z);
			return Arrays.copyOf(result, count);
		}
	}
}
