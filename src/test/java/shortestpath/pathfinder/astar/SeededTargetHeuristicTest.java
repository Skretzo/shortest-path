package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.TransportAvailabilityFixture;

/** A heuristic towards several targets each seeded with a cost is the cheapest of the targets' own plus their seed. */
public class SeededTargetHeuristicTest
{
	@Test
	public void aSeededHeuristicIsTheCheapestSeedPlusDistance() throws Exception
	{
		// Two rooms joined by a corridor, a tile at each end and one in the middle as targets
		Set<Integer> walkable = new HashSet<>();
		List<int[]> edges = new ArrayList<>();
		for (int x = 10; x <= 40; x++)
		{
			walkable.add(tile(x));
			if (x > 10) edges.add(new int[] {tile(x - 1), tile(x)});
		}
		SyntheticCollisionMap collision = new SyntheticCollisionMap(walkable,
			SyntheticCollisionMap.symmetricAdjacency(edges.toArray(new int[0][])), 1);
		RoutingStatic stat = RoutingStaticBuilder.build(collision, Map.of(), Set.of(), new int[0], tile(10)).routingStatic;
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
			TransportAvailabilityFixture.of(), false, Set.of(), 0, true, ignored -> 0);
		SiteGraph graph = new SiteGraph(stat, account);
		int[] targets = {tile(10), tile(25), tile(40)};
		int[] seeds = {30, 2, 7};
		TargetOverlay overlay = TargetOverlay.withSeedCosts(graph, collision, targets, seeds);
		PreparedHeuristic seeded = PreparedHeuristic.prepare(overlay, ReverseLabels.compute(overlay));
		for (int x = 10; x <= 40; x++)
		{
			int expected = CostArithmetic.INF;
			for (int i = 0; i < targets.length; i++)
			{
				PreparedTarget single = PreparedTarget.prepare(graph, collision, targets[i]);
				expected = Math.min(expected, CostArithmetic.add(seeds[i],
					single.heuristic().estimate(tile(x), BankVisitState.CARRIED)));
			}
			assertEquals("at x=" + x, expected, seeded.estimate(tile(x), BankVisitState.CARRIED));
		}
		assertEquals(2, seeded.estimate(tile(25), BankVisitState.CARRIED));
	}

	private static int tile(int x)
	{
		return WorldPointUtil.packWorldPoint(x, 10, 0);
	}
}
