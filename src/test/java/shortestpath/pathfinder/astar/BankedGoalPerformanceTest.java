package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.TransportAvailabilityFixture;

/** Repeatable query timings on a 4,096-tile world; correctness, not wall-clock time, is asserted. */
public class BankedGoalPerformanceTest
{
	@Test
	public void measureBankDependentQueries() throws Exception
	{
		Set<Integer> tiles = new HashSet<>();
		int[][] edges = new int[64 * 63 * 2][2];
		int edge = 0;
		for (int y = 0; y < 64; y++)
		{
			for (int x = 0; x < 64; x++)
			{
				tiles.add(tile(x, y));
				if (x < 63) edges[edge++] = new int[] {tile(x, y), tile(x + 1, y)};
				if (y < 63) edges[edge++] = new int[] {tile(x, y), tile(x, y + 1)};
			}
		}
		SyntheticCollisionMap collision = new SyntheticCollisionMap(tiles,
			SyntheticCollisionMap.symmetricAdjacency(edges), 1);
		int start = tile(0, 0), bank = tile(1, 1), destination = tile(63, 63);
		RoutingStatic stat = RoutingStaticBuilder.build(collision, Map.of(), Set.of(bank), new int[0], start).routingStatic;
		for (String scenario : new String[] {"bank-unreachable", "bank-reachable", "carried", "cached-carried"})
		{
			boolean bankedOnly = scenario.startsWith("bank-");
			boolean accessible = scenario.equals("bank-reachable");
			PreparedRoutingAccount account = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
				TransportAvailabilityFixture.of(), true, accessible ? Set.of(bank) : Set.of(), 5, true, ignored -> 0);
			AStarRoutingSession cached = new AStarRoutingSession();
			long[][] measurements = new long[6][200];
			AStarForwardSearch.Result result = null;
			for (int repetition = -200; repetition < 200; repetition++)
			{
				long started = System.nanoTime();
				AStarRoutingSession session = scenario.equals("cached-carried") ? cached : new AStarRoutingSession();
				SiteGraph graph = session.graph(stat, account).value();
				AStarRoutingSession.Lookup<PreparedTarget> lookup = session.target(graph, collision,
					new int[] {destination}, new int[] {bankedOnly ? 2 : 3});
				PreparedTarget target = lookup.value();
				long forwardStarted = System.nanoTime();
				result = target.search(start, () -> false, 1, SearchRestrictions.none(), AStarForwardSearch.NO_PROGRESS,
					(tile, banked) -> !bankedOnly || banked == BankVisitState.BANKED);
				long finished = System.nanoTime();
				assertEquals(!scenario.equals("bank-unreachable"), result.reached());
				if (bankedOnly && !accessible) assertEquals(0, result.counters().statesPopped());
				if (result.reached()) assertEquals(accessible ? 131 : 126, result.cost());
				if (repetition >= 0)
				{
					measurements[0][repetition] = lookup.reused() ? 0 : target.reverseSearchNanos() + target.heuristicPrepareNanos();
					measurements[1][repetition] = finished - forwardStarted;
					measurements[2][repetition] = finished - started;
					measurements[3][repetition] = lookup.reused() ? 0 : target.reverseSearchNanos();
					measurements[4][repetition] = result.counters().statesPopped();
					measurements[5][repetition] = result.counters().uniqueStatesReached();
				}
			}
			for (long[] values : measurements) Arrays.sort(values);
			System.out.println(scenario + " median_ns: preparation=" + measurements[0][100]
				+ " reverse=" + measurements[3][100] + " forward=" + measurements[1][100]
				+ " total=" + measurements[2][100] + " statesPopped=" + measurements[4][100]
				+ " uniqueStatesReached=" + measurements[5][100]
				+ " heuristicUnreachable=" + result.counters().heuristicUnreachable());
		}
	}

	private static int tile(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}
}
