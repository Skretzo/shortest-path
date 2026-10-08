package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;

public class InGameWalkRewriterTest
{
	private static final int BASE_X = 3200, BASE_Y = 3200;
	private static final PreparedRoutingAccount NO_TRANSPORTS = PreparedRoutingAccount.compile(null, null, false,
		Set.of(), 0, false, ignored -> 0);

	@Test
	public void walkTheGameAlreadyTakesIsKept()
	{
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		List<PathStep> route = steps(0, 0, 1, 0, 2, 0, 3, 0, 4, 0);

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, 4);

		assertEquals(describe(route), describe(result.path()));
		assertEquals(List.of(4), result.clickPoints());
		assertEquals(0, result.keptSteps());
	}

	@Test
	public void equallyShortGamePathReplacesTheCanonicalWalk()
	{
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		// Canonical NE then E; the game expands E before NE, so it walks E then NE.
		List<PathStep> route = steps(0, 0, 1, 1, 2, 1);

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, 2);

		assertEquals(describe(steps(0, 0, 1, 0, 2, 1)), describe(result.path()));
		assertEquals(List.of(2), result.clickPoints());
	}

	@Test
	public void gamePathMayLeaveTheCanonicalWalkBetweenClicks()
	{
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		// SE SE S S S S to (2,-6): as short and with as few turns as the game's S S S S SE SE.
		List<PathStep> route = steps(0, 0, 1, -1, 2, -2, 2, -3, 2, -4, 2, -5, 2, -6);

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, 6);

		assertEquals(List.of(6), result.clickPoints());
		assertEquals(gamePath(grid, pack(0, 0), pack(2, -6)), tiles(result.path(), 0, 6));
		assertEquals(describe(steps(0, 0, 0, -1, 0, -2, 0, -3, 0, -4, 1, -5, 2, -6)), describe(result.path()));
		assertEquals(route.size(), result.path().size());
		assertEquals(describe(route.subList(6, 7)), describe(result.path().subList(6, 7)));
		Set<Integer> canonical = new HashSet<>(tiles(route, 0, 6));
		assertTrue("the game path should leave the canonical walk",
			tiles(result.path(), 0, 6).stream().anyMatch(tile -> !canonical.contains(tile)));
	}

	@Test
	public void longLegsAreSplitIntoClicksAlongTheCanonicalWalk()
	{
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		// 30 east, then 12 north-east, then 10 north.
		List<PathStep> route = new ArrayList<>(steps(0, 0));
		for (int x = 1; x <= 30; x++) route.add(step(x, 0));
		for (int d = 1; d <= 12; d++) route.add(step(30 + d, d));
		for (int y = 13; y <= 22; y++) route.add(step(42, y));
		int end = route.size() - 1;

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, end);

		assertTrue("expected several clicks: " + result.clickPoints(), result.clickPoints().size() > 2);
		assertEquals(end, (int) result.clickPoints().get(result.clickPoints().size() - 1));
		assertEquals(route.size(), result.path().size());
		assertEquals(describe(route.subList(end, end + 1)), describe(result.path().subList(end, end + 1)));
		assertClicksFollowTheGame(grid, route, result, 0);
		assertEquals(0, result.keptSteps());
	}

	@Test
	public void furtherClickWithAShorterGamePathIsRefused()
	{
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		// 5 ticks to (3,0), which the game reaches in 3: clicking it would make the route cheaper.
		List<PathStep> route = steps(0, 0, 0, 1, 1, 1, 2, 1, 2, 0, 3, 0);

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, 5);

		assertEquals(describe(route), describe(result.path()));
		assertEquals(List.of(1, 3, 4, 5), result.clickPoints());
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void stepTheGameDoesNotWalkKeepsTheCanonicalStep()
	{
		// A blocked transport origin at (6,0), stepped onto by the exact search to take the transport.
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid(Set.of(pack(6, 0)));
		List<PathStep> route = steps(0, 0, 1, 1, 2, 1, 3, 1, 4, 1, 5, 0, 6, 0);

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, 6);

		// (6,0) is unreachable in game, so the click falls back to (5,0), and the last step is
		// kept — recorded as a kept step, not a click: no click lands on it.
		assertEquals(List.of(5), result.clickPoints());
		assertEquals(List.of(6), result.keptStepIndices());
		assertEquals(1, result.keptSteps());
		assertEquals(gamePath(grid, pack(0, 0), pack(5, 0)), tiles(result.path(), 0, 5));
		assertEquals(describe(route.subList(5, 7)), describe(result.path().subList(5, 7)));
	}

	@Test
	public void legsAreRewrittenSeparatelyAndOtherStepsAreKept()
	{
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		// Walk, bank, teleport, walk: the canonicaliser finds the legs, the rewriter keeps the rest.
		List<PathStep> route = new ArrayList<>(steps(0, 0, 1, 1, 2, 1));
		route.add(new PathStep(pack(2, 1), true));
		route.add(new PathStep(pack(100, 100), true));
		route.add(new PathStep(pack(101, 101), true));
		route.add(new PathStep(pack(102, 101), true));
		int[] costs = {0, 1, 2, 2, 5, 6, 7};
		ExactWalkCanonicalizer.Result canonical = new ExactWalkCanonicalizer(grid, NO_TRANSPORTS)
			.canonicalize(ExactRoute.of(route, costs));
		assertEquals(2, canonical.legs());

		InGameWalkRewriter.Result result = new InGameWalkRewriter(grid).rewrite(canonical);

		List<PathStep> expected = new ArrayList<>(steps(0, 0, 1, 0, 2, 1));
		expected.add(new PathStep(pack(2, 1), true));
		expected.add(new PathStep(pack(100, 100), true));
		expected.add(new PathStep(pack(101, 100), true));
		expected.add(new PathStep(pack(102, 101), true));
		assertEquals(describe(expected), describe(result.path()));
		assertEquals(List.of(2, 6), result.clickPoints());
	}

	@Test
	public void clicksNeverCrossALegBoundary()
	{
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		// Step 2 to 3 is a transport between adjacent tiles: not part of either leg.
		List<PathStep> route = steps(0, 0, 1, 0, 2, 0, 3, 0, 4, 1, 5, 1);

		InGameWalkRewriter.Result result = new InGameWalkRewriter(grid).rewrite(route, new int[]{0, 2, 3, 5});

		assertEquals(List.of(2, 5), result.clickPoints());
		assertEquals(describe(steps(0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 1)), describe(result.path()));
	}

	@Test
	public void canonicalRoutesKeepTheirLengthAndEndsOnAMaze()
	{
		// Walls with gaps, so the canonical walks bend and the game's walks differ from them.
		Set<Integer> blocked = new HashSet<>();
		for (int y = -20; y <= 20; y++)
		{
			if (y != 15) blocked.add(pack(8, y));
			if (y != -12) blocked.add(pack(20, y));
		}
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid(blocked);
		int[][] ends = {{0, 0, 30, 0}, {0, -10, 30, 10}, {2, 18, 27, -18}, {5, 5, 12, -3}};
		for (int[] e : ends)
		{
			List<Integer> walk = gamePath(grid, pack(e[0], e[1]), pack(e[2], e[3]));
			List<PathStep> raw = new ArrayList<>(steps(e[0], e[1]));
			for (int tile : walk) raw.add(new PathStep(tile, false));
			ExactWalkCanonicalizer.Result canonical = new ExactWalkCanonicalizer(grid, NO_TRANSPORTS)
				.canonicalize(ExactRoute.of(raw, costs(raw.size())));
			assertEquals(List.of(), canonical.diagnostics());

			InGameWalkRewriter.Result result = new InGameWalkRewriter(grid).rewrite(canonical);

			assertEquals(raw.size(), result.path().size());
			assertEquals(describe(raw.subList(0, 1)), describe(result.path().subList(0, 1)));
			assertEquals(describe(raw.subList(raw.size() - 1, raw.size())),
				describe(result.path().subList(raw.size() - 1, raw.size())));
			assertClicksFollowTheGame(grid, canonical.path(), result, 0);
			assertEquals(0, result.keptSteps());
		}
	}

	@Test
	public void stepIntoAGatedTileIsKeptRatherThanClicked()
	{
		// (1,0) is walkable but gated by the league's blocked region; no in-game path lands on
		// it without crossing the gate, so the step is kept rather than drawn as a click.
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		List<PathStep> route = steps(0, 0, 1, 0);
		SearchRestrictions restrictions = SearchRestrictions.of(false, tile -> tile == pack(1, 0));

		InGameWalkRewriter.Result result = new InGameWalkRewriter(grid, restrictions)
			.rewrite(route, new int[]{0, 1});

		assertEquals(describe(route), describe(result.path()));
		assertEquals(List.of(), result.clickPoints());
		assertEquals(List.of(1), result.keptStepIndices());
	}

	@Test
	public void restrictedCandidateFallsBackToANearerClick()
	{
		// (2,0) is gated: clicking (3,1) would splice the game path (1,0),(2,0),(3,1) through
		// it, so the click falls back to (2,1), whose game path only crosses (1,0).
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		List<PathStep> route = steps(0, 0, 1, 1, 2, 1, 3, 1);
		SearchRestrictions restrictions = SearchRestrictions.of(false, tile -> tile == pack(2, 0));

		InGameWalkRewriter.Result result = new InGameWalkRewriter(grid, restrictions)
			.rewrite(route, new int[]{0, 3});

		assertEquals(describe(steps(0, 0, 1, 0, 2, 1, 3, 1)), describe(result.path()));
		assertEquals(List.of(2, 3), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
		assertFalse("the splice stepped into a gated tile",
			tiles(result.path(), 0, 3).contains(pack(2, 0)));

		// Without the restriction the longer click through (2,0) is spliced in.
		InGameWalkRewriter.Result free = rewrite(grid, route, 0, 3);
		assertEquals(List.of(3), free.clickPoints());
		assertTrue(tiles(free.path(), 0, 3).contains(pack(2, 0)));
	}

	@Test
	public void legOnBlockedTilesKeepsEveryStep()
	{
		// The exact search steps onto and off blocked tiles it reaches by transport; the game's
		// BFS expands nothing from a blocked tile, so a leg lying on them keeps every step.
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid(
			Set.of(pack(0, 0), pack(1, 0), pack(2, 0), pack(3, 0)));
		List<PathStep> route = steps(0, 0, 1, 0, 2, 0, 3, 0);

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, 3);

		assertEquals(List.of(), result.clickPoints());
		assertEquals(List.of(1, 2, 3), result.keptStepIndices());
		assertEquals(describe(route), describe(result.path()));
	}

	@Test
	public void consecutiveStepsTheGameDoesNotWalkAreAllKept()
	{
		// A two-tile-thick wall mid-leg: the detour around it is longer than the canonical
		// walk, so the clicks on the far side are refused, and the game cannot step on or off
		// the wall tiles either — three steps in a row are kept before real clicks resume.
		Set<Integer> blocked = new HashSet<>();
		for (int x = 6; x <= 7; x++)
			for (int y = -1; y <= 1; y++) blocked.add(pack(x, y));
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid(blocked);
		List<PathStep> route = steps(0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 8, 0, 9, 0);

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, 9);

		assertEquals(List.of(5, 9), result.clickPoints());
		assertEquals(List.of(6, 7, 8), result.keptStepIndices());
		assertEquals(describe(route.subList(5, 10)), describe(result.path().subList(5, 10)));
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void clicksBeyondTheCheckpointBudgetAreDecomposed()
	{
		// The client walks a click only up to its 25th checkpoint tile (the direction-change
		// corners plus the destination). This corridor climbs a staircase north-east, runs two
		// tiles along the top, and descends another staircase south-east — 27 checkpoints to
		// the end — so the far click is refused and the leg decomposes at step 27, the last
		// tile whose traced path fits the budget, rather than drawing a walk the game would
		// cut short. The steps are cardinal: a diagonal zig-zag through a walled channel is
		// not walkable at all.
		List<Integer> corridor = new ArrayList<>(List.of(pack(0, 0)));
		for (int k = 0; k <= 6; k++)
		{
			corridor.add(pack(k, k + 1));
			corridor.add(pack(k + 1, k + 1));
		}
		corridor.add(pack(8, 7));
		corridor.add(pack(9, 7));
		for (int k = 0; k <= 5; k++)
		{
			corridor.add(pack(9 + k, 6 - k));
			corridor.add(pack(10 + k, 6 - k));
		}
		corridor.add(pack(15, 0));
		Set<Integer> open = new HashSet<>(corridor);
		Set<Integer> blocked = new HashSet<>();
		for (int x = -2; x <= 17; x++)
			for (int y = -2; y <= 9; y++)
				if (!open.contains(pack(x, y))) blocked.add(pack(x, y));
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid(blocked);
		List<PathStep> route = new ArrayList<>();
		for (int tile : corridor) route.add(new PathStep(tile, false));

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, route.size() - 1);

		assertEquals(describe(route), describe(result.path()));
		assertEquals(List.of(27, route.size() - 1), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void clickReachIsEuclideanUpToTheRadiusBoundary()
	{
		ExactWalkCanonicalizerTest.Grid grid = new ExactWalkCanonicalizerTest.Grid();
		// (15,0) is exactly at a click's reach (15^2 = 225); (15,1) is just beyond it (226).
		List<PathStep> route = new ArrayList<>(steps(0, 0));
		for (int x = 1; x <= 15; x++) route.add(step(x, 0));
		route.add(step(15, 1));

		InGameWalkRewriter.Result result = rewrite(grid, route, 0, 16);

		assertEquals(List.of(15, 16), result.clickPoints());
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void legacySearchDoesNotRewriteWalks() throws IOException
	{
		Path sourceRoot = Paths.get("src/main/java/shortestpath");
		List<String> users;
		try (Stream<Path> files = Files.walk(sourceRoot))
		{
			users = files.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.getFileName().toString().equals("InGameWalkRewriter.java"))
				.filter(path -> read(path).contains("InGameWalkRewriter"))
				.map(path -> path.getFileName().toString())
				.sorted()
				.collect(Collectors.toList());
		}
		assertEquals(List.of("ExactPathfinder.java"), users);
		assertFalse(read(Paths.get("src/main/java/shortestpath/pathfinder/Pathfinder.java"))
			.contains("InGameWalkRewriter"));
	}

	// ---- helpers ----

	private static InGameWalkRewriter.Result rewrite(CollisionMap grid, List<PathStep> route, int from, int to)
	{
		return new InGameWalkRewriter(grid).rewrite(route, new int[]{from, to});
	}

	/**
	 * Checks every click is a tile of the canonical walk, every stretch between segment boundaries
	 * (clicks and kept steps interleaved) is the game's path between them, and the clicks keep the
	 * canonical indices. A kept step bounds a segment too — it sits between clicks — but ends the
	 * segment on the canonical tile rather than a walked-to one, so it has no game path to check.
	 */
	private static void assertClicksFollowTheGame(CollisionMap grid, List<PathStep> canonical,
		InGameWalkRewriter.Result result, int legStart)
	{
		Set<Integer> kept = new HashSet<>(result.keptStepIndices());
		List<Integer> boundaries = new ArrayList<>(result.clickPoints());
		boundaries.addAll(kept);
		Collections.sort(boundaries);
		int previous = legStart;
		for (int boundary : boundaries)
		{
			if (kept.contains(boundary))
			{
				// A kept step is the single step after the previous boundary and stays canonical.
				assertEquals("kept step " + boundary + " does not follow the previous boundary",
					previous + 1, boundary);
				assertEquals("kept step " + boundary + " is not the canonical tile",
					canonical.get(boundary).getPackedPosition(),
					result.path().get(boundary).getPackedPosition());
			}
			else
			{
				assertEquals("click " + boundary + " is not on the canonical walk",
					canonical.get(boundary).getPackedPosition(),
					result.path().get(boundary).getPackedPosition());
				List<Integer> game = gamePath(grid, result.path().get(previous).getPackedPosition(),
					result.path().get(boundary).getPackedPosition());
				assertEquals("the game walks " + (boundary - previous) + " ticks",
					boundary - previous, game.size());
				assertEquals(game, tiles(result.path(), previous, boundary));
			}
			previous = boundary;
		}
	}

	/**
	 * A reference of the game's walking BFS (no window): the tiles after {@code from} up to
	 * {@code to}. The expansion-order tables are deliberately re-derived here rather than read
	 * from GameWalkOrder, so the test keeps an independent implementation to check against.
	 */
	private static List<Integer> gamePath(CollisionMap grid, int from, int to)
	{
		int[] dx = {-1, 1, 0, 0, -1, 1, -1, 1}, dy = {0, 0, -1, 1, -1, -1, 1, 1};
		int[] bits = {6, 2, 4, 0, 5, 3, 7, 1};
		Map<Integer, Integer> parent = new HashMap<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		parent.put(from, from);
		queue.add(from);
		while (!queue.isEmpty() && !parent.containsKey(to))
		{
			int at = queue.poll();
			int mask = grid.ordinaryWalkingMask(at);
			for (int i = 0; i < 8; i++)
			{
				if ((mask & 1 << bits[i]) == 0) continue;
				int next = WorldPointUtil.packWorldPoint(WorldPointUtil.unpackWorldX(at) + dx[i],
					WorldPointUtil.unpackWorldY(at) + dy[i], 0);
				if (parent.putIfAbsent(next, at) == null) queue.add(next);
			}
		}
		List<Integer> path = new ArrayList<>();
		if (!parent.containsKey(to)) return path;
		for (int at = to; at != from; at = parent.get(at)) path.add(0, at);
		return path;
	}

	private static List<Integer> tiles(List<PathStep> path, int from, int to)
	{
		List<Integer> tiles = new ArrayList<>();
		for (int i = from + 1; i <= to; i++) tiles.add(path.get(i).getPackedPosition());
		return tiles;
	}

	private static int[] costs(int size)
	{
		int[] costs = new int[size];
		for (int i = 1; i < size; i++) costs[i] = i;
		return costs;
	}

	private static String read(Path path)
	{
		try
		{
			return Files.readString(path);
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}

	private static List<String> describe(List<PathStep> path)
	{
		List<String> tiles = new ArrayList<>();
		for (PathStep step : path)
		{
			int packed = step.getPackedPosition();
			tiles.add((WorldPointUtil.unpackWorldX(packed) - BASE_X) + "," + (WorldPointUtil.unpackWorldY(packed) - BASE_Y)
				+ (step.isBankVisited() ? " banked" : ""));
		}
		return tiles;
	}

	private static List<PathStep> steps(int... xy)
	{
		List<PathStep> steps = new ArrayList<>();
		for (int i = 0; i < xy.length; i += 2) steps.add(step(xy[i], xy[i + 1]));
		return steps;
	}

	private static PathStep step(int x, int y)
	{
		return new PathStep(pack(x, y), false);
	}

	private static int pack(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(BASE_X + x, BASE_Y + y, 0);
	}
}
