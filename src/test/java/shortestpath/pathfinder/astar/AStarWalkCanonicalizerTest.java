package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class AStarWalkCanonicalizerTest
{
	// Away from the wilderness, so every global teleport is castable.
	private static final int BASE_X = 3200, BASE_Y = 3200;
	private static final PreparedRoutingAccount NO_TRANSPORTS = PreparedRoutingAccount.compile(null, null, false,
		Set.of(), 0, false, ignored -> 0);

	@Test
	public void walkTheGameAlreadyTakesIsKept()
	{
		Grid grid = new Grid();
		List<PathStep> route = steps(0, 0, 1, 0, 2, 0, 3, 0, 4, 0);

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 4);

		assertEquals(describe(route), describe(result.path()));
		assertEquals(List.of(4), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
	}

	@Test
	public void equallyShortGamePathReplacesTheSearchsWalk()
	{
		Grid grid = new Grid();
		// The search went NE then E; the game expands E before NE, so it walks E then NE.
		List<PathStep> route = steps(0, 0, 1, 1, 2, 1);

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 2);

		assertEquals(describe(steps(0, 0, 1, 0, 2, 1)), describe(result.path()));
		assertEquals(List.of(2), result.clickPoints());
	}

	@Test
	public void gamePathMayLeaveTheSearchsWalkBetweenClicks()
	{
		Grid grid = new Grid();
		// SE SE S S S S to (2,-6): as short as the game's S S S S SE SE, which it walks instead.
		List<PathStep> route = steps(0, 0, 1, -1, 2, -2, 2, -3, 2, -4, 2, -5, 2, -6);

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 6);

		assertEquals(List.of(6), result.clickPoints());
		assertEquals(gamePath(grid, pack(0, 0), pack(2, -6)), tiles(result.path(), 0, 6));
		assertEquals(describe(steps(0, 0, 0, -1, 0, -2, 0, -3, 0, -4, 1, -5, 2, -6)), describe(result.path()));
		assertEquals(route.size(), result.path().size());
		assertEquals(describe(route.subList(6, 7)), describe(result.path().subList(6, 7)));
		Set<Integer> searched = new HashSet<>(tiles(route, 0, 6));
		assertTrue("the game path should leave the search's walk",
			tiles(result.path(), 0, 6).stream().anyMatch(tile -> !searched.contains(tile)));
	}

	@Test
	public void longLegsAreSplitIntoSeveralClicks()
	{
		Grid grid = new Grid();
		// 30 east, then 12 north-east, then 10 north.
		List<PathStep> route = new ArrayList<>(steps(0, 0));
		for (int x = 1; x <= 30; x++) route.add(step(x, 0));
		for (int d = 1; d <= 12; d++) route.add(step(30 + d, d));
		for (int y = 13; y <= 22; y++) route.add(step(42, y));
		int end = route.size() - 1;

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, end);

		assertTrue("expected several clicks: " + result.clickPoints(), result.clickPoints().size() > 1);
		assertEquals(end, (int) result.clickPoints().get(result.clickPoints().size() - 1));
		assertEquals(route.size(), result.path().size());
		assertEquals(describe(route.subList(end, end + 1)), describe(result.path().subList(end, end + 1)));
		assertClicksFollowTheGame(grid, route, result, 0);
		assertEquals(List.of(), result.keptStepIndices());
	}

	@Test
	public void furtherClickWithAShorterGamePathIsRefused()
	{
		Grid grid = new Grid();
		// 5 ticks to (3,0), which the game reaches in 3: clicking it would make the route cheaper.
		List<PathStep> route = steps(0, 0, 0, 1, 1, 1, 2, 1, 2, 0, 3, 0);

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 5);

		assertEquals(describe(route), describe(result.path()));
		assertEquals(List.of(1, 3, 4, 5), result.clickPoints());
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void stepTheGameDoesNotWalkKeepsTheSearchsStep()
	{
		// A blocked transport origin at (6,0), stepped onto by the A* search to take the transport.
		Grid grid = new Grid(Set.of(pack(6, 0)));
		List<PathStep> route = steps(0, 0, 1, 1, 2, 1, 3, 1, 4, 1, 5, 0, 6, 0);

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 6);

		// (6,0) is unreachable in game, so the click falls back to (5,0), and the last step is
		// kept; it is recorded as a kept step, not a click, as no click lands on it.
		assertEquals(List.of(5), result.clickPoints());
		assertEquals(List.of(6), result.keptStepIndices());
		assertEquals(gamePath(grid, pack(0, 0), pack(5, 0)), tiles(result.path(), 0, 5));
		assertEquals(describe(route.subList(5, 7)), describe(result.path().subList(5, 7)));
	}

	@Test
	public void legsAreRewrittenSeparatelyAndOtherStepsAreKept()
	{
		Grid grid = new Grid();
		// Walk, bank, teleport, walk: only the walking legs are rewritten.
		List<PathStep> route = new ArrayList<>(steps(0, 0, 1, 1, 2, 1));
		route.add(new PathStep(pack(2, 1), BankVisitState.BANKED));
		route.add(new PathStep(pack(100, 100), BankVisitState.BANKED));
		route.add(new PathStep(pack(101, 101), BankVisitState.BANKED));
		route.add(new PathStep(pack(102, 101), BankVisitState.BANKED));
		int[] costs = {0, 1, 2, 2, 5, 6, 7};

		AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS)
			.canonicalize(AStarRoute.of(route, costs));

		List<PathStep> expected = new ArrayList<>(steps(0, 0, 1, 0, 2, 1));
		expected.add(new PathStep(pack(2, 1), BankVisitState.BANKED));
		expected.add(new PathStep(pack(100, 100), BankVisitState.BANKED));
		expected.add(new PathStep(pack(101, 100), BankVisitState.BANKED));
		expected.add(new PathStep(pack(102, 101), BankVisitState.BANKED));
		assertEquals(describe(expected), describe(result.path()));
		assertEquals(List.of(2, 6), result.clickPoints());
	}

	@Test
	public void clicksNeverCrossALegBoundary()
	{
		Grid grid = new Grid();
		// Step 2 to 3 is a transport between adjacent tiles: not part of either leg.
		List<PathStep> route = steps(0, 0, 1, 0, 2, 0, 3, 0, 4, 1, 5, 1);

		AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS)
			.rewrite(route, new int[]{0, 2, 3, 5});

		assertEquals(List.of(2, 5), result.clickPoints());
		assertEquals(describe(steps(0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 1)), describe(result.path()));
	}

	@Test
	public void routesKeepTheirLengthAndEndsOnAMaze()
	{
		// Walls with gaps, so the search's walks bend and the game's walks differ from them.
		Set<Integer> blocked = new HashSet<>();
		for (int y = -20; y <= 20; y++)
		{
			if (y != 15) blocked.add(pack(8, y));
			if (y != -12) blocked.add(pack(20, y));
		}
		Grid grid = new Grid(blocked);
		int[][] ends = {{0, 0, 30, 0}, {0, -10, 30, 10}, {2, 18, 27, -18}, {5, 5, 12, -3}};
		for (int[] e : ends)
		{
			// The game's own walk as the route: every step is one it takes, so none is kept.
			List<Integer> walk = gamePath(grid, pack(e[0], e[1]), pack(e[2], e[3]));
			List<PathStep> raw = new ArrayList<>(steps(e[0], e[1]));
			for (int tile : walk) raw.add(new PathStep(tile, BankVisitState.CARRIED));

			AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS)
				.canonicalize(AStarRoute.of(raw, costs(raw.size())));

			assertEquals(raw.size(), result.path().size());
			assertEquals(describe(raw.subList(0, 1)), describe(result.path().subList(0, 1)));
			assertEquals(describe(raw.subList(raw.size() - 1, raw.size())),
				describe(result.path().subList(raw.size() - 1, raw.size())));
			// Already the game's own walk, so rewriting it must hand the same tiles back, as clicks.
			assertEquals(describe(raw), describe(result.path()));
			assertFalse(result.clickPoints().isEmpty());
			assertClicksFollowTheGame(grid, raw, result, 0);
			assertEquals(List.of(), result.keptStepIndices());
		}
	}

	@Test
	public void stepIntoAGatedTileIsKeptRatherThanClicked()
	{
		// (1,0) is walkable but gated by the league's blocked region; no in-game path lands on
		// it without crossing the gate, so the step is kept rather than drawn as a click.
		Grid grid = new Grid();
		List<PathStep> route = steps(0, 0, 1, 0);
		SearchRestrictions restrictions = SearchRestrictions.of(false, tile -> tile == pack(1, 0));

		AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS, restrictions)
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
		Grid grid = new Grid();
		List<PathStep> route = steps(0, 0, 1, 1, 2, 1, 3, 1);
		SearchRestrictions restrictions = SearchRestrictions.of(false, tile -> tile == pack(2, 0));

		AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS, restrictions)
			.rewrite(route, new int[]{0, 3});

		assertEquals(describe(steps(0, 0, 1, 0, 2, 1, 3, 1)), describe(result.path()));
		assertEquals(List.of(2, 3), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
		assertFalse("the splice stepped into a gated tile", tiles(result.path(), 0, 3).contains(pack(2, 0)));

		// Without the restriction the longer click through (2,0) is spliced in.
		AStarWalkCanonicalizer.Result free = rewrite(grid, route, 0, 3);
		assertEquals(List.of(3), free.clickPoints());
		assertTrue(tiles(free.path(), 0, 3).contains(pack(2, 0)));
	}

	@Test
	public void legOnBlockedTilesKeepsEveryStep()
	{
		// The A* search steps onto and off blocked tiles it reaches by transport; the game walks
		// onto none of them, so a leg lying on them keeps every step.
		Grid grid = new Grid(Set.of(pack(0, 0), pack(1, 0), pack(2, 0), pack(3, 0)));
		List<PathStep> route = steps(0, 0, 1, 0, 2, 0, 3, 0);

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 3);

		assertEquals(List.of(), result.clickPoints());
		assertEquals(List.of(1, 2, 3), result.keptStepIndices());
		assertEquals(describe(route), describe(result.path()));
	}

	@Test
	public void consecutiveStepsTheGameDoesNotWalkAreAllKept()
	{
		// A two-tile-thick wall mid-leg: the detour around it is longer than the search's walk,
		// so the clicks on the far side are refused, and the game cannot step on or off the wall
		// tiles either; three steps in a row are kept before real clicks resume.
		Set<Integer> blocked = new HashSet<>();
		for (int x = 6; x <= 7; x++)
			for (int y = -1; y <= 1; y++) blocked.add(pack(x, y));
		Grid grid = new Grid(blocked);
		List<PathStep> route = steps(0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 8, 0, 9, 0);

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 9);

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
		// tiles along the top, and descends another staircase south-east, 27 checkpoints to
		// the end, so the far click is refused and the leg decomposes at step 27, the last
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
		Grid grid = new Grid(blocked);
		List<PathStep> route = new ArrayList<>();
		for (int tile : corridor) route.add(new PathStep(tile, BankVisitState.CARRIED));

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, route.size() - 1);

		assertEquals(describe(route), describe(result.path()));
		assertEquals(List.of(27, route.size() - 1), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void clickReachIsEuclideanUpToTheRadiusBoundary()
	{
		Grid grid = new Grid();
		// (35,0) is exactly at a click's reach (35^2 = 1225): one click covers the whole leg.
		List<PathStep> inside = new ArrayList<>(steps(0, 0));
		for (int x = 1; x <= 35; x++) inside.add(step(x, 0));

		AStarWalkCanonicalizer.Result reached = rewrite(grid, inside, 0, 35);

		assertEquals(List.of(35), reached.clickPoints());
		assertClicksFollowTheGame(grid, inside, reached, 0);

		// (35,1) is 35 ticks away too (east 34, then north-east), so the game's walk to it is as long as
		// the search's and nothing but its reach (35^2 + 1 = 1226) stops a single click there: the click
		// falls back to the previous tile of the game's walk, and the last one lands on (35,1).
		List<PathStep> beyond = new ArrayList<>(steps(0, 0));
		for (int x = 1; x <= 34; x++) beyond.add(step(x, 0));
		beyond.add(step(35, 1));

		AStarWalkCanonicalizer.Result result = rewrite(grid, beyond, 0, 35);

		assertEquals(List.of(34, 35), result.clickPoints());
		assertEquals(gamePath(grid, pack(0, 0), pack(35, 1)), tiles(result.path(), 0, 35));
		assertClicksFollowTheGame(grid, beyond, result, 0);
	}

	@Test
	public void legEndsStraightWhereTheGameWalksStraightRatherThanOnTheSearchsDogleg()
	{
		// A wall along y = 20 with a gap at x = 0: past the gap the game walks straight north to
		// (0,35). The search took the equally short dogleg (0,33) NE (1,34) NW (0,35), and (1,34) is
		// the furthest tile of its walk within reach of (4,0), (0,35) being just beyond it. A click
		// there would draw the dogleg; the player clicks on the game's way, which ends straight.
		Set<Integer> wall = new HashSet<>();
		for (int x = -40; x <= 40; x++)
			if (x != 0) wall.add(pack(x, 20));
		Grid grid = new Grid(wall);
		List<PathStep> route = new ArrayList<>(steps(4, 0, 3, 1, 2, 2, 1, 3, 0, 4));
		for (int y = 5; y <= 33; y++) route.add(step(0, y));
		route.addAll(steps(1, 34, 0, 35));

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 35);

		assertEquals(describe(steps(0, 33, 0, 34, 0, 35)), describe(result.path().subList(33, 36)));
		assertEquals(gamePath(grid, pack(4, 0), pack(0, 35)), tiles(result.path(), 0, 35));
		assertEquals(List.of(34, 35), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void clicksLieOnTheWayTheGameWalksToTheLegsEnd()
	{
		// Open ground, 40 ticks from (6,0) to (0,40): the game walks north, then north-west at the
		// end. The search went north-west first, so its furthest tile within reach, (0,34), is off
		// the game's way; the click is the game's own tile there, and the leg is the game's walk.
		Grid grid = new Grid();
		List<PathStep> route = new ArrayList<>(steps(6, 0, 5, 1, 4, 2, 3, 3, 2, 4, 1, 5, 0, 6));
		for (int y = 7; y <= 40; y++) route.add(step(0, y));

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 40);

		assertEquals(gamePath(grid, pack(6, 0), pack(0, 40)), tiles(result.path(), 0, 40));
		assertEquals(pack(6, 34), result.path().get(34).getPackedPosition());
		assertEquals(List.of(34, 40), result.clickPoints());
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void stepOffABlockedLandingIsTheGamesFirstStepOffIt()
	{
		// A transport landed on the blocked tile (0,0). No click is drawn from it, so its first step
		// is kept. The search stepped south-west and turned north-west, a dogleg the game does not
		// walk: it steps west off the tile, on its way to (-12,10).
		Grid grid = new Grid(Set.of(pack(0, 0)));
		List<PathStep> route = new ArrayList<>(steps(0, 0, -1, -1));
		for (int x = -2; x >= -12; x--) route.add(step(x, -2 - x));

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 12);

		assertEquals(List.of(1), result.keptStepIndices());
		assertEquals(pack(-1, 0), result.path().get(1).getPackedPosition());
		assertEquals(List.of(12), result.clickPoints());
		assertEquals(gamePath(grid, pack(-1, 0), pack(-12, 10)), tiles(result.path(), 1, 12));
		assertClicksFollowTheGame(grid, route, result, 0);
	}

	@Test
	public void legBeforeAGlobalTeleportEndsOnTheFirstCastableTileTheGameFinds()
	{
		Grid grid = new Grid();
		// Level 31 starts at y = 3760: the search walked south-east then south to y = 3759, the
		// first level 30 tile, 20 ticks away, and cast the level 30 teleports from there.
		int start = WorldPointUtil.packWorldPoint(3000, 3779, 0);
		List<PathStep> route = new ArrayList<>();
		route.add(new PathStep(start, BankVisitState.CARRIED));
		for (int i = 1; i <= 10; i++)
			route.add(new PathStep(WorldPointUtil.packWorldPoint(3000 + i, 3779 - i, 0), BankVisitState.CARRIED));
		for (int i = 11; i <= 20; i++)
			route.add(new PathStep(WorldPointUtil.packWorldPoint(3010, 3779 - i, 0), BankVisitState.CARRIED));
		int searchEnd = route.get(20).getPackedPosition();
		int destination = WorldPointUtil.packWorldPoint(3213, 3424, 0);
		route.add(new PathStep(destination, BankVisitState.CARRIED));
		byte[] arrivals = new byte[route.size()];
		arrivals[route.size() - 1] = AStarRoute.fromHub(TeleportCapability.OVER_20);
		int[] costs = new int[route.size()];
		for (int i = 0; i < 21; i++) costs[i] = i;
		costs[21] = 20 + 4;
		IntPredicate castable = tile -> TeleportCapability.at(tile).allows(TeleportCapability.OVER_20);

		AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS)
			.canonicalize(new AStarRoute(route, arrivals, costs));

		int end = result.path().get(20).getPackedPosition();
		assertEquals(firstDiscovered(grid, start, 20, castable), end);
		assertNotEquals("the leg should end elsewhere than the search's tile", searchEnd, end);
		assertTrue(castable.test(end));
		assertEquals(route.size(), result.path().size());
		assertEquals(destination, result.path().get(21).getPackedPosition());
		assertEquals(gamePath(grid, start, end), tiles(result.path(), 0, 20));
		assertEquals(List.of(20), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
	}

	@Test
	public void castableTileNearerThanTheRemainingDistanceIsNeverTaken()
	{
		Grid grid = new Grid();
		// Straight south from level 31 into level 30: the first castable tile is 11 ticks away
		// but the leg is 14 ticks long, so it ends where the game finds a castable tile at 14.
		int start = WorldPointUtil.packWorldPoint(3000, 3770, 0);
		List<PathStep> route = new ArrayList<>();
		for (int i = 0; i <= 14; i++)
			route.add(new PathStep(WorldPointUtil.packWorldPoint(3000, 3770 - i, 0), BankVisitState.CARRIED));
		route.add(new PathStep(WorldPointUtil.packWorldPoint(3213, 3424, 0), BankVisitState.CARRIED));
		byte[] arrivals = new byte[route.size()];
		arrivals[route.size() - 1] = AStarRoute.fromHub(TeleportCapability.OVER_20);
		int[] costs = new int[route.size()];
		for (int i = 0; i < 15; i++) costs[i] = i;
		costs[15] = 14 + 4;
		IntPredicate castable = tile -> TeleportCapability.at(tile).allows(TeleportCapability.OVER_20);
		assertNotEquals("a castable tile exists 11 ticks away", -1, firstDiscovered(grid, start, 11, castable));

		AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS)
			.canonicalize(new AStarRoute(route, arrivals, costs));

		int end = result.path().get(14).getPackedPosition();
		assertEquals(firstDiscovered(grid, start, 14, castable), end);
		assertEquals(14, gamePath(grid, start, end).size());
		assertEquals(gamePath(grid, start, end), tiles(result.path(), 0, 14));
		assertEquals(route.size(), result.path().size());
	}

	@Test
	public void legBeforeATransportEndsOnTheFirstOriginOfTheSameActionTheGameFinds()
	{
		Grid grid = new Grid();
		int destination = pack(50, 50);
		PreparedRoutingAccount account = account(
			transport(pack(0, 10), destination, "Climb-up Ladder 1"),
			transport(pack(5, 10), destination, "Climb-up Ladder 1"));
		// Ten ticks to either origin; the search went to the far one.
		List<PathStep> route = steps(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5, 6, 5, 7, 5, 8, 5, 9, 5, 10, 50, 50);

		AStarWalkCanonicalizer.Result result = canonicalize(grid, account, route);

		int expected = firstDiscovered(grid, pack(0, 0), 10, tile -> tile == pack(0, 10) || tile == pack(5, 10));
		assertEquals(expected, result.path().get(10).getPackedPosition());
		assertEquals(pack(0, 10), expected);
		assertEquals(gamePath(grid, pack(0, 0), expected), tiles(result.path(), 0, 10));
		assertEquals(destination, last(result.path()));
		assertEquals(List.of(10), result.clickPoints());
	}

	@Test
	public void originNearerThanTheRemainingDistanceIsNeverTaken()
	{
		Grid grid = new Grid();
		int destination = pack(50, 50);
		PreparedRoutingAccount account = account(
			transport(pack(0, 6), destination, "Climb-up Ladder 1"),
			transport(pack(5, 10), destination, "Climb-up Ladder 1"));
		List<PathStep> route = steps(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5, 6, 5, 7, 5, 8, 5, 9, 5, 10, 50, 50);

		AStarWalkCanonicalizer.Result result = canonicalize(grid, account, route);

		// The origin 6 ticks away does not qualify for a 10 tick leg; the 10 tick one does.
		assertEquals(pack(5, 10), result.path().get(10).getPackedPosition());
		assertEquals(gamePath(grid, pack(0, 0), pack(5, 10)), tiles(result.path(), 0, 10));
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

		AStarWalkCanonicalizer.Result result = canonicalize(grid, account, route);

		assertEquals(pack(5, 10), result.path().get(10).getPackedPosition());
		assertEquals(gamePath(grid, pack(0, 0), pack(5, 10)), tiles(result.path(), 0, 10));
		assertEquals(destination, last(result.path()));
	}

	@Test
	public void legOntoABlockedTransportOriginStillEndsThere()
	{
		int door = pack(0, 5);
		Grid grid = new Grid(Set.of(door, pack(-1, 5), pack(1, 5)));
		Transport open = transport(door, pack(0, 6), "Open Door 1");
		PreparedRoutingAccount account = account(open);
		// Stepping onto the blocked door tile to open it, as the A* search does, then through it.
		List<PathStep> route = new ArrayList<>(steps(0, 0, 1, 1, 1, 2, 0, 3, 0, 4, 0, 5));
		route.add(new PathStep(pack(0, 6), BankVisitState.CARRIED, open));

		AStarWalkCanonicalizer.Result result = canonicalize(grid, account, route);

		// The game never walks onto the door: the last step is kept and the leg still ends there.
		assertEquals(door, result.path().get(5).getPackedPosition());
		assertEquals(List.of(5), result.keptStepIndices());
		assertEquals(List.of(4), result.clickPoints());
		assertEquals(pack(0, 6), last(result.path()));
		assertEquals(gamePath(grid, pack(0, 0), pack(0, 4)), tiles(result.path(), 0, 4));
	}

	@Test
	public void bankVisitsKeepTheirTile()
	{
		Grid grid = new Grid();
		List<PathStep> route = new ArrayList<>(steps(0, 0, 1, 1, 0, 2));
		route.add(new PathStep(pack(0, 2), BankVisitState.BANKED));
		route.add(new PathStep(pack(1, 3), BankVisitState.BANKED));
		route.add(new PathStep(pack(0, 4), BankVisitState.BANKED));

		AStarWalkCanonicalizer.Result result = canonicalize(grid, NO_TRANSPORTS, route);

		List<PathStep> expected = new ArrayList<>(steps(0, 0, 0, 1, 0, 2));
		expected.add(new PathStep(pack(0, 2), BankVisitState.BANKED));
		expected.add(new PathStep(pack(0, 3), BankVisitState.BANKED));
		expected.add(new PathStep(pack(0, 4), BankVisitState.BANKED));
		assertEquals(describe(expected), describe(result.path()));
		assertEquals(List.of(2, 5), result.clickPoints());
	}

	@Test
	public void legBeforeABankVisitKeepsItsTileEvenWhenAnotherOriginExists()
	{
		Grid grid = new Grid();
		int destination = pack(50, 50);
		PreparedRoutingAccount account = account(
			transport(pack(0, 10), destination, "Climb-up Ladder 1"),
			transport(pack(5, 10), destination, "Climb-up Ladder 1"));
		// The bank visit at (5,10) comes straight after the walk, so the leg must end there.
		List<PathStep> route = new ArrayList<>(steps(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5, 6, 5, 7, 5, 8, 5, 9, 5, 10));
		route.add(new PathStep(pack(5, 10), BankVisitState.BANKED));
		route.add(new PathStep(pack(50, 50), BankVisitState.BANKED));

		AStarWalkCanonicalizer.Result result = canonicalize(grid, account, route);

		assertEquals(pack(5, 10), result.path().get(10).getPackedPosition());
		assertEquals(pack(5, 10), result.path().get(11).getPackedPosition());
	}

	@Test
	public void equalRoutesAreCanonicalisedIdentically()
	{
		Grid grid = new Grid(Set.of(pack(3, 3)));
		AStarWalkCanonicalizer canonicalizer = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS);
		AStarRoute route = AStarRoute.of(steps(0, 0, 1, 1, 2, 2, 2, 3, 3, 4, 4, 5, 4, 6, 4, 7, 4, 8), costs(9));
		AStarRoute other = AStarRoute.of(steps(2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0), costs(6));

		AStarWalkCanonicalizer.Result first = canonicalizer.canonicalize(route);
		AStarWalkCanonicalizer.Result again = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS).canonicalize(route);
		canonicalizer.canonicalize(other);
		AStarWalkCanonicalizer.Result reused = canonicalizer.canonicalize(route);

		assertEquals(describe(first.path()), describe(again.path()));
		assertEquals(describe(first.path()), describe(reused.path()));
		assertEquals(first.clickPoints(), again.clickPoints());
		assertEquals(first.clickPoints(), reused.clickPoints());
		assertEquals(first.keptStepIndices(), again.keptStepIndices());
		assertEquals(first.keptStepIndices(), reused.keptStepIndices());
	}

	@Test
	public void randomGridsMatchTheReferenceOrdering()
	{
		Random random = new Random(364);
		int checked = 0;
		for (int trial = 0; trial < 300; trial++)
		{
			// A walled box of random obstacles, so both the game's and the reference walk stay inside it.
			Set<Integer> blocked = new HashSet<>();
			for (int x = -40; x <= 40; x++)
				for (int y = -40; y <= 40; y++)
					if (Math.abs(x) == 40 || Math.abs(y) == 40 || random.nextInt(100) < 20) blocked.add(pack(x, y));
			int from = pack(random.nextInt(61) - 30, random.nextInt(61) - 30);
			int to = pack(random.nextInt(61) - 30, random.nextInt(61) - 30);
			blocked.remove(from);
			blocked.remove(to);
			Grid grid = new Grid(blocked);
			// A shortest route found in the opposite expansion order, so it differs from the game's walk.
			List<Integer> walk = reversedOrderPath(grid, from, to);
			if (walk.size() < 2) continue;
			List<PathStep> raw = new ArrayList<>();
			raw.add(new PathStep(from, BankVisitState.CARRIED));
			for (int tile : walk) raw.add(new PathStep(tile, BankVisitState.CARRIED));

			AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS)
				.canonicalize(AStarRoute.of(raw, costs(raw.size())));

			assertEquals(raw.size(), result.path().size());
			assertEquals(from, result.path().get(0).getPackedPosition());
			assertEquals(to, last(result.path()));
			assertEquals(List.of(), result.keptStepIndices());
			assertClicksFollowTheGame(grid, raw, result, 0);
			for (int i = 1; i < result.path().size(); i++)
			{
				int previous = result.path().get(i - 1).getPackedPosition(), tile = result.path().get(i).getPackedPosition();
				assertTrue("step " + i + " is not a walking move",
					Arrays.stream(grid.ordinaryWalkingNeighbors(previous)).anyMatch(next -> next == tile));
			}
			checked++;
		}
		assertTrue("too few connected pairs: " + checked, checked > 100);
	}

	@Test
	public void legsOnAnotherPlaneAreRewrittenOnThatPlane()
	{
		Grid grid = new Grid();
		// The search went NE then E; the game expands E before NE, so it walks E then NE, on plane 2.
		List<PathStep> route = List.of(plane2(0, 0), plane2(1, 1), plane2(2, 1));

		AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS)
			.rewrite(route, new int[]{0, 2});

		assertEquals(describe(List.of(plane2(0, 0), plane2(1, 0), plane2(2, 1))), describe(result.path()));
		for (PathStep step : result.path())
			assertEquals(2, WorldPointUtil.unpackWorldPlane(step.getPackedPosition()));
		assertEquals(List.of(2), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
	}

	@Test
	public void legsLongerThanTheSearchWindowAreWalkedInSeveralClicks()
	{
		Grid grid = new Grid();
		// 100 tiles east: tiles past 63 are outside the 128x128 window of the first click's source.
		List<PathStep> route = new ArrayList<>(steps(0, 0));
		for (int x = 1; x <= 100; x++) route.add(step(x, 0));

		AStarWalkCanonicalizer.Result result = rewrite(grid, route, 0, 100);

		assertEquals(describe(route), describe(result.path()));
		assertEquals(List.of(35, 70, 100), result.clickPoints());
		assertEquals(List.of(), result.keptStepIndices());
	}

	@Test
	public void legBeforeAGlobalTeleportSkipsACastableTileTheRestrictionsGateOff()
	{
		Grid grid = new Grid();
		int start = WorldPointUtil.packWorldPoint(3000, 3779, 0);
		List<PathStep> route = new ArrayList<>();
		route.add(new PathStep(start, BankVisitState.CARRIED));
		for (int i = 1; i <= 10; i++)
			route.add(new PathStep(WorldPointUtil.packWorldPoint(3000 + i, 3779 - i, 0), BankVisitState.CARRIED));
		for (int i = 11; i <= 20; i++)
			route.add(new PathStep(WorldPointUtil.packWorldPoint(3010, 3779 - i, 0), BankVisitState.CARRIED));
		route.add(new PathStep(WorldPointUtil.packWorldPoint(3213, 3424, 0), BankVisitState.CARRIED));
		byte[] arrivals = new byte[route.size()];
		arrivals[route.size() - 1] = AStarRoute.fromHub(TeleportCapability.OVER_20);
		int[] costs = new int[route.size()];
		for (int i = 0; i < 21; i++) costs[i] = i;
		costs[21] = 20 + 4;
		IntPredicate castable = tile -> TeleportCapability.at(tile).allows(TeleportCapability.OVER_20);
		// The tile the unrestricted walk ends on, which the game's search finds first: gate it off.
		int preferred = firstDiscovered(grid, start, 20, castable);
		SearchRestrictions restrictions = SearchRestrictions.of(false, tile -> tile == preferred);

		AStarWalkCanonicalizer.Result result = new AStarWalkCanonicalizer(grid, NO_TRANSPORTS, restrictions)
			.canonicalize(new AStarRoute(route, arrivals, costs));

		int end = result.path().get(20).getPackedPosition();
		assertNotEquals("the gated tile must not be walked onto", preferred, end);
		assertTrue(castable.test(end));
		assertFalse(tiles(result.path(), 0, 20).contains(preferred));
		assertEquals(route.size(), result.path().size());
		assertEquals(List.of(), result.keptStepIndices());
		assertEquals(List.of(20), result.clickPoints());
		assertEquals(gamePath(grid, start, end), tiles(result.path(), 0, 20));
	}

	@Test
	public void legBeforeABankedTransportUsesTheBankedActions()
	{
		Grid grid = new Grid();
		int destination = pack(50, 50);
		Transport near = transport(pack(0, 10), destination, "Climb-up Ladder 1");
		Transport far = transport(pack(5, 10), destination, "Climb-up Ladder 1");
		// Only the far ladder is open to a player carrying items; with banked items both are.
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(far),
			TransportAvailabilityFixture.of(near, far), false, Set.of(), 0, true, ignored -> 0);
		List<PathStep> carried = new ArrayList<>(), banked = new ArrayList<>();
		int[][] tiles = {{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 4}, {5, 5}, {5, 6}, {5, 7}, {5, 8}, {5, 9}, {5, 10}, {50, 50}};
		for (int[] tile : tiles)
		{
			carried.add(step(tile[0], tile[1]));
			banked.add(new PathStep(pack(tile[0], tile[1]), BankVisitState.BANKED));
		}

		AStarWalkCanonicalizer.Result carriedResult = canonicalize(grid, account, carried);
		AStarWalkCanonicalizer.Result bankedResult = canonicalize(grid, account, banked);

		assertEquals(pack(5, 10), carriedResult.path().get(10).getPackedPosition());
		assertEquals(pack(0, 10), bankedResult.path().get(10).getPackedPosition());
		assertEquals(gamePath(grid, pack(0, 0), pack(0, 10)), tiles(bankedResult.path(), 0, 10));
		assertEquals(BankVisitState.BANKED, bankedResult.path().get(10).getBankVisitState());
		assertEquals(destination, last(bankedResult.path()));
	}

	@Test
	public void onlyTheAStarPathfinderUsesTheCanonicalizer() throws IOException
	{
		Path sourceRoot = Paths.get("src/main/java/shortestpath");
		if (!Files.isDirectory(sourceRoot))
		{
			// Composite builds run tests with the module nested one level down.
			sourceRoot = Paths.get("shortest-path/src/main/java/shortestpath");
		}
		assertTrue("cannot find the plugin sources; run tests from the module or composite "
			+ "project directory (tried src/main/java/shortestpath and "
			+ sourceRoot.toAbsolutePath() + ")", Files.isDirectory(sourceRoot));
		Path testSourceRoot = Paths.get("src/test/java/shortestpath");
		if (!Files.isDirectory(testSourceRoot))
		{
			// Composite builds run tests with the module nested one level down.
			testSourceRoot = Paths.get("shortest-path/src/test/java/shortestpath");
		}
		assertTrue("cannot find the plugin test sources; run tests from the module or composite "
			+ "project directory (tried src/test/java/shortestpath and "
			+ testSourceRoot.toAbsolutePath() + ")", Files.isDirectory(testSourceRoot));
		List<String> users;
		try (Stream<Path> files = Files.walk(sourceRoot))
		{
			users = files.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !path.getFileName().toString().equals("AStarWalkCanonicalizer.java"))
				.filter(path -> usesCanonicalizer(read(path)))
				.map(path -> path.getFileName().toString())
				.sorted()
				.collect(Collectors.toList());
		}
		assertEquals(List.of("AStarPathfinder.java"), users);
		assertFalse(usesCanonicalizer(read(testSourceRoot.resolve("pathfinder").resolve("Pathfinder.java"))));
	}

	// ---- helpers ----

	private static AStarWalkCanonicalizer.Result rewrite(CollisionMap grid, List<PathStep> route, int from, int to)
	{
		return new AStarWalkCanonicalizer(grid, NO_TRANSPORTS).rewrite(route, new int[]{from, to});
	}

	private static AStarWalkCanonicalizer.Result canonicalize(Grid grid, PreparedRoutingAccount account,
		List<PathStep> route)
	{
		return new AStarWalkCanonicalizer(grid, account).canonicalize(AStarRoute.of(route, costs(route.size())));
	}

	/**
	 * Checks every stretch between segment boundaries (clicks and kept steps interleaved) is the
	 * game's path between them, as long as the search's walk between the same indices. A click need
	 * not be a tile of the search's walk: it lies on the way the game walks. A kept step
	 * bounds a segment too (it sits between clicks) but is a single step the search may take rather
	 * than a walked-to tile, so it has no game path to check. The leg is the whole route, so the
	 * last boundary must be its final step: a dropped trailing click, or no boundary at all, fails.
	 */
	private static void assertClicksFollowTheGame(CollisionMap grid, List<PathStep> searched,
		AStarWalkCanonicalizer.Result result, int legStart)
	{
		Set<Integer> kept = new HashSet<>(result.keptStepIndices());
		List<Integer> boundaries = new ArrayList<>(result.clickPoints());
		boundaries.addAll(kept);
		Collections.sort(boundaries);
		int legEnd = searched.size() - 1;
		assertEquals("the leg is not covered by clicks and kept steps: " + boundaries,
			legEnd > legStart, !boundaries.isEmpty());
		if (!boundaries.isEmpty())
			assertEquals("the last boundary is not the leg's end", legEnd, (int) boundaries.get(boundaries.size() - 1));
		int previous = legStart;
		for (int boundary : boundaries)
		{
			if (kept.contains(boundary))
			{
				// A kept step is the single step after the previous boundary, one the search may take.
				assertEquals("kept step " + boundary + " does not follow the previous boundary",
					previous + 1, boundary);
				int tile = result.path().get(boundary).getPackedPosition();
				assertTrue("kept step " + boundary + " is not a step the search may take",
					tile == searched.get(boundary).getPackedPosition() || Arrays.stream(grid.ordinaryWalkingNeighbors(
						result.path().get(previous).getPackedPosition())).anyMatch(next -> next == tile));
			}
			else
			{
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
				int next = WorldPointUtil.packWorldPoint(x(at) + dx[i], y(at) + dy[i], 0);
				if (parent.putIfAbsent(next, at) == null) queue.add(next);
			}
		}
		List<Integer> path = new ArrayList<>();
		if (!parent.containsKey(to)) return path;
		for (int at = to; at != from; at = parent.get(at)) path.add(0, at);
		return path;
	}

	/**
	 * The first tile, in the order the game's walking BFS discovers tiles (no window), that is
	 * exactly {@code distance} steps from {@code from} and accepted; -1 if there is none. Re-derived
	 * like {@link #gamePath} so the end-tile rule is checked against an independent search.
	 */
	private static int firstDiscovered(CollisionMap grid, int from, int distance, IntPredicate accepted)
	{
		int[] dx = {-1, 1, 0, 0, -1, 1, -1, 1}, dy = {0, 0, -1, 1, -1, -1, 1, 1};
		int[] bits = {6, 2, 4, 0, 5, 3, 7, 1};
		Map<Integer, Integer> steps = new HashMap<>();
		List<Integer> order = new ArrayList<>();
		steps.put(from, 0);
		order.add(from);
		for (int head = 0; head < order.size() && steps.get(order.get(head)) < distance; head++)
		{
			int at = order.get(head);
			int mask = grid.ordinaryWalkingMask(at);
			for (int i = 0; i < 8; i++)
			{
				if ((mask & 1 << bits[i]) == 0) continue;
				int next = WorldPointUtil.packWorldPoint(x(at) + dx[i], y(at) + dy[i], 0);
				if (steps.putIfAbsent(next, steps.get(at) + 1) == null) order.add(next);
			}
		}
		for (int tile : order)
			if (steps.get(tile) == distance && accepted.test(tile)) return tile;
		return -1;
	}

	/**
	 * A shortest path, tiles after {@code from} up to {@code to}, found by a BFS expanding in the
	 * reverse of the game's order, so it is as short as the game's but usually not the same tiles.
	 */
	private static List<Integer> reversedOrderPath(CollisionMap grid, int from, int to)
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
			for (int i = 7; i >= 0; i--)
			{
				if ((mask & 1 << bits[i]) == 0) continue;
				int next = WorldPointUtil.packWorldPoint(x(at) + dx[i], y(at) + dy[i], 0);
				if (parent.putIfAbsent(next, at) == null) queue.add(next);
			}
		}
		List<Integer> path = new ArrayList<>();
		if (!parent.containsKey(to)) return path;
		for (int at = to; at != from; at = parent.get(at)) path.add(0, at);
		return path;
	}

	private static PathStep plane2(int x, int y)
	{
		return new PathStep(WorldPointUtil.packWorldPoint(BASE_X + x, BASE_Y + y, 2), BankVisitState.CARRIED);
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

	/**
	 * Whether {@code source} actually uses the canonicalizer (imports or constructs it), ignoring
	 * line comments, so a comment or javadoc mention does not count.
	 */
	private static boolean usesCanonicalizer(String source)
	{
		String code = source.lines()
			.map(line -> line.contains("//") ? line.substring(0, line.indexOf("//")) : line)
			.collect(Collectors.joining("\n"));
		return code.contains("import shortestpath.pathfinder.astar.AStarWalkCanonicalizer")
			|| code.contains("new AStarWalkCanonicalizer(");
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

	private static PreparedRoutingAccount account(Transport... transports)
	{
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(transports),
			TransportAvailabilityFixture.of(transports), false, Set.of(), 0, true, ignored -> 0);
	}

	private static Transport transport(int origin, int destination, String objectInfo)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination).type(TransportType.TRANSPORT)
			.duration(1).objectInfo(objectInfo).build();
	}

	private static List<String> describe(List<PathStep> path)
	{
		List<String> tiles = new ArrayList<>();
		for (PathStep step : path)
			tiles.add((x(step.getPackedPosition()) - BASE_X) + "," + (y(step.getPackedPosition()) - BASE_Y)
				+ (step.getBankVisitState() == BankVisitState.BANKED ? " banked" : ""));
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
		return new PathStep(pack(x, y), BankVisitState.CARRIED);
	}

	private static int last(List<PathStep> path)
	{
		return path.get(path.size() - 1).getPackedPosition();
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

		/**
		 * As {@link CollisionMap}: the mask's moves, but from a blocked tile (one a transport lands
		 * on) every open neighbour, a diagonal one when both flanking tiles are open too.
		 */
		@Override
		public int[] ordinaryWalkingNeighbors(int packedPoint)
		{
			int[] dx = {0, 1, 1, 1, 0, -1, -1, -1}, dy = {1, 1, 0, -1, -1, -1, 0, 1};
			int x = x(packedPoint), y = y(packedPoint), z = WorldPointUtil.unpackWorldPlane(packedPoint);
			int mask = Byte.toUnsignedInt(ordinaryWalkingMask(packedPoint));
			int[] result = new int[8];
			int count = 0;
			for (int bit = 0; bit < 8; bit++)
			{
				boolean open = isBlocked(x, y, z)
					? !isBlocked(x + dx[bit], y + dy[bit], z) && (dx[bit] == 0 || dy[bit] == 0
						|| !isBlocked(x + dx[bit], y, z) && !isBlocked(x, y + dy[bit], z))
					: (mask & 1 << bit) != 0;
				if (open) result[count++] = WorldPointUtil.packWorldPoint(x + dx[bit], y + dy[bit], z);
			}
			return Arrays.copyOf(result, count);
		}
	}
}
