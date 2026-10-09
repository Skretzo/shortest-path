package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.TransportAvailabilityFixture;

public class SparseManhattanTest
{
	private static final int A = WorldPointUtil.packWorldPoint(1000, 1000, 0);
	private static final int B = WorldPointUtil.packWorldPoint(1003, 1000, 0);
	private static final int C = WorldPointUtil.packWorldPoint(1003, 1004, 0);
	private static final int OTHER_PLANE = WorldPointUtil.packWorldPoint(1000, 1000, 1);
	private static final int TARGET = WorldPointUtil.packWorldPoint(1001, 1001, 0);

	@Test
	public void sparseSteinerLabelsMatchCliqueForSyntheticTarget() throws Exception
	{
		RoutingStatic stat = syntheticSparse();
		TargetOverlay target = new TargetOverlay(new SiteGraph(stat, emptyAccount()),
			attachments(A, OTHER_PLANE), TARGET);
		ReverseLabels sparse = ReverseLabels.computeSparse(target);
		ReverseLabels clique = ReverseLabels.computeClique(target);
		assertArrayEquals(clique.labels(), sparse.labels());
		assertTrue(sparse.sparse());
		assertTrue(sparse.sparseRelaxationCount() > 0);
		assertTrue(sparse.attachmentRelaxationCount() > 0);
		assertEquals(0, sparse.targetLabel(BankVisitState.CARRIED));
	}

	@Test
	public void reducedGeneratorsMatchRawSeedsAndRetainOracle() throws Exception
	{
		RoutingStatic stat = syntheticSparse();
		TargetOverlay target = new TargetOverlay(new SiteGraph(stat, emptyAccount()),
			attachments(A, OTHER_PLANE), TARGET);
		ReverseLabels reverse = ReverseLabels.compute(target);
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(target, reverse);
		for (int packed : new int[] {A, B, C, TARGET, OTHER_PLANE,
			WorldPointUtil.packWorldPoint(1002, 1002, 0)})
		{
			assertEquals(heuristic.estimateRaw(packed, BankVisitState.CARRIED, new int[] {0}),
				heuristic.estimate(packed, BankVisitState.CARRIED, new int[] {0}));
			assertEquals(heuristic.estimateRaw(packed, BankVisitState.BANKED, new int[] {0}),
				heuristic.estimate(packed, BankVisitState.BANKED, new int[] {0}));
		}
		assertEquals(heuristic.rawSeedCount(), heuristic.seedCount(0, BankVisitState.CARRIED) + heuristic.seedCount(0, BankVisitState.BANKED)
			+ heuristic.seedCount(1, BankVisitState.CARRIED) + heuristic.seedCount(1, BankVisitState.BANKED));
		assertEquals(10, heuristic.rawSeedCount());
		assertEquals(4, heuristic.generatorCount());
		assertEquals(0.4, heuristic.generatorRatio(), 0.0);
		assertTrue(heuristic.generatorCount() <= heuristic.rawSeedCount());
		assertTrue(heuristic.generatorCount() > 0);
		assertTrue(heuristic.preparationNanos() >= 0);
	}

	private static RoutingStatic syntheticSparse() throws Exception
	{
		return RoutingStatic.create(new int[] {A, OTHER_PLANE}, new int[] {0, 1}, new byte[] {0, 0},
			new int[] {-1, -1}, new int[] {-1, -1}, new int[] {A, B, C, OTHER_PLANE},
			new int[] {0, 1, 2, 3, 4}, new int[] {0, 0, 0, 1}, 2, new int[] {0, 3, 4},
			new int[] {0, 1, 2, 3}, new int[0], new int[0], new int[0], new int[0], 4, 5, 1, 3, 6,
			new int[] {0, 1, 2, 3, 3, 6}, new int[] {4, 4, 4, 0, 1, 2}, new int[] {6, 0, 8, 6, 0, 8});
	}

	private static CollisionMap attachments(int... sites)
	{
		return new CollisionMap(null)
		{
			@Override public int[] ordinaryWalkingNeighbors(int packedPoint)
			{
				return sites.clone();
			}

			@Override public boolean isBlocked(int x, int y, int z)
			{
				return false;
			}
		};
	}

	private static PreparedRoutingAccount emptyAccount()
	{
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(), TransportAvailabilityFixture.of(),
			false, java.util.Set.of(), 0, true, ignored -> 0);
	}
}
