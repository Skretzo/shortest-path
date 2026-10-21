package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.TransportAvailabilityFixture;

public class BankedTargetTest
{
	private static final int START = tile(10), BANK = tile(11), TARGET = tile(12), FAR = tile(15);
	private SyntheticCollisionMap collision;
	private RoutingStatic stat;

	@Before
	public void world() throws Exception
	{
		Set<Integer> tiles = new HashSet<>();
		int[][] edges = new int[5][2];
		for (int x = 10; x <= 15; x++)
		{
			tiles.add(tile(x));
			if (x < 15) edges[x - 10] = new int[] {tile(x), tile(x + 1)};
		}
		collision = new SyntheticCollisionMap(tiles, SyntheticCollisionMap.symmetricAdjacency(edges), 1);
		stat = RoutingStaticBuilder.build(collision, Map.of(), Set.of(BANK), new int[0], START).routingStatic;
	}

	@Test
	public void bankOnlyGoalWithNoAccessibleBankPrunesTheCarriedStart()
	{
		PreparedTarget target = prepare(new int[] {TARGET}, new int[] {2}, false, 5);
		assertEquals(CostArithmetic.INF, target.heuristic().estimate(START, BankVisitState.CARRIED));
		assertEquals(0, target.heuristic().reverseLabels().targetLabel(BankVisitState.BANKED));
		AStarForwardSearch.Result result = target.search(START, () -> false, 1);
		assertFalse(result.reached());
		assertEquals(0, result.counters().statesPopped());
		assertEquals(0, result.counters().uniqueStatesReached());
		assertEquals(1, result.counters().heuristicUnreachable());
	}

	@Test
	public void carriedStartCanReachTheBankAndReturnToItsOwnTargetTile()
	{
		PreparedTarget target = prepare(new int[] {TARGET}, new int[] {2}, true, 5);
		assertEquals(7, target.heuristic().estimate(TARGET, BankVisitState.CARRIED));
		for (double weight : new double[] {1, 1.5})
		{
			AStarForwardSearch.Result result = target.search(TARGET, () -> false, weight);
			assertTrue(result.reached());
			assertEquals(7, result.cost());
			assertTrue(result.path().stream().anyMatch(step -> step.getPackedPosition() == BANK
				&& step.getBankVisitState() == BankVisitState.BANKED));
			assertEquals(BankVisitState.BANKED, result.path().get(result.path().size() - 1).getBankVisitState());
		}
	}

	@Test
	public void eitherLayerGoalSeedsBothLayersAndNeedsNoBank()
	{
		PreparedTarget target = prepare(new int[] {TARGET}, new int[] {3}, true, 5);
		for (BankVisitState layer : BankVisitState.values())
			assertEquals(0, target.heuristic().reverseLabels().targetLabel(layer));
		AStarForwardSearch.Result result = target.search(START, () -> false, 1);
		assertTrue(result.reached());
		assertEquals(2, result.cost());
		assertTrue(result.path().stream().allMatch(step -> step.getBankVisitState() == BankVisitState.CARRIED));
	}

	@Test
	public void mixedTargetsChooseTheCheapestValidLayer()
	{
		for (int bankCost : new int[] {1, 5})
		{
			PreparedTarget target = prepare(new int[] {TARGET, FAR}, new int[] {2, 3}, true, bankCost);
			ReverseLabels labels = target.heuristic().reverseLabels();
			assertEquals(0, labels.targetLabel(0, BankVisitState.BANKED));
			assertTrue(labels.targetLabel(0, BankVisitState.CARRIED) != 0);
			assertEquals(0, labels.targetLabel(1, BankVisitState.CARRIED));
			AStarForwardSearch.Result result = target.search(START, () -> false, 1);
			assertTrue(result.reached());
			assertEquals(bankCost == 1 ? 3 : 5, result.cost());
			assertEquals(bankCost == 1 ? TARGET : FAR,
				result.path().get(result.path().size() - 1).getPackedPosition());
		}
	}

	@Test
	public void sparseCliqueAndReducedSeedScansAgreeForAsymmetricLayers()
	{
		for (boolean accessible : new boolean[] {false, true})
		{
			for (int[] masks : new int[][] {{2}, {2, 3}, {1, 2}})
			{
				int[] tiles = masks.length == 1 ? new int[] {TARGET} : new int[] {TARGET, FAR};
				TargetOverlay overlay = prepare(tiles, masks, accessible, 5).overlay();
				ReverseLabels sparse = ReverseLabels.computeSparse(overlay);
				ReverseLabels clique = ReverseLabels.computeClique(overlay);
				assertTrue(sparse.sparse());
				assertArrayEquals(clique.labels(), sparse.labels());
				PreparedHeuristic heuristic = PreparedHeuristic.prepare(overlay, sparse);
				for (int node = 0; node < stat.searchTileCount(); node++)
				{
					for (BankVisitState layer : BankVisitState.values())
					{
						int packed = stat.searchTile(node);
						int[] components = {stat.routingComponent(node)};
						assertEquals(heuristic.estimateRaw(packed, layer, components),
							heuristic.estimate(packed, layer, components));
					}
				}
			}
		}
	}

	private PreparedTarget prepare(int[] tiles, int[] masks, boolean accessible, int bankCost)
	{
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(),
			TransportAvailabilityFixture.of(), true, accessible ? Set.of(BANK) : Set.of(), bankCost, true, ignored -> 0);
		return PreparedTarget.prepare(new SiteGraph(stat, account), collision, tiles, masks);
	}

	private static int tile(int x)
	{
		return WorldPointUtil.packWorldPoint(x, 10, 0);
	}
}
