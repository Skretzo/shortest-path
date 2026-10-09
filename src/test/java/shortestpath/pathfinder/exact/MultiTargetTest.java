package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class MultiTargetTest
{
	private static final int T3_TARGET = RoutingStaticTestFixture.T3_TARGET;
	private static final int T3_B = RoutingStaticTestFixture.T3_B;
	private static final int T3_D = RoutingStaticTestFixture.T3_D;
	private static final int T3_F = RoutingStaticTestFixture.T3_F;
	private static final int SECOND_SYNTHETIC = WorldPointUtil.packWorldPoint(1005, 1000, 0);

	@Test
	public void overlayOrdersTargetsAndGivesEachSyntheticTargetItsOwnNode() throws Exception
	{
		SiteGraph graph = new SiteGraph(RoutingStaticTestFixture.createT3(), account());
		TargetOverlay overlay = new TargetOverlay(graph, t3Collision(),
			new int[] {T3_D, SECOND_SYNTHETIC, T3_TARGET, T3_B, T3_D});

		assertEquals(4, overlay.targetCount());
		assertEquals(2, overlay.syntheticCount());
		assertEquals(graph.nodeCount() + 2, overlay.queryNodeCount());
		for (int i = 1; i < overlay.targetCount(); i++)
			assertTrue(Integer.compareUnsigned(overlay.packedTarget(i - 1), overlay.packedTarget(i)) < 0);
		for (int i = 0; i < overlay.targetCount(); i++)
		{
			assertEquals(i, overlay.targetIndex(overlay.packedTarget(i)));
			assertEquals(overlay.packedTarget(i), overlay.nodeTile(overlay.targetNode(i)));
		}
		assertEquals(graph.nodeForTile(T3_B), overlay.targetNode(overlay.targetIndex(T3_B)));
		assertTrue(overlay.synthetic(overlay.targetIndex(T3_TARGET)));
		assertTrue(overlay.synthetic(overlay.targetIndex(SECOND_SYNTHETIC)));
		assertFalse(overlay.isTarget(T3_F));
	}

	@Test
	public void cliqueLabelsAreTheMinimumOverSingleTargets() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.createT3();
		SiteGraph graph = new SiteGraph(stat, account(local(T3_B, T3_D, 7)));
		int[] targets = {T3_TARGET, T3_D, SECOND_SYNTHETIC};
		TargetOverlay multi = new TargetOverlay(graph, t3Collision(), targets);

		ReverseLabels labels = ReverseLabels.computeClique(multi);

		assertMinimumOverSingles(graph, t3Collision(), targets, labels, false);
		for (int i = 0; i < multi.targetCount(); i++)
		{
			assertEquals(0, labels.targetLabel(i, BankVisitState.CARRIED));
			assertEquals(0, labels.targetLabel(i, BankVisitState.BANKED));
		}
	}

	@Test
	public void sparseLabelsMatchCliqueAndSingleTargetsWithTwoTargets() throws Exception
	{
		RoutingStatic stat = SparseFixture.create();
		SiteGraph graph = new SiteGraph(stat, account());
		CollisionMap collision = SparseFixture.collision();
		int[] targets = {SparseFixture.TARGET, SparseFixture.C};
		TargetOverlay multi = new TargetOverlay(graph, collision, targets);

		ReverseLabels sparse = ReverseLabels.computeSparse(multi);

		assertArrayEquals(ReverseLabels.computeClique(multi).labels(), sparse.labels());
		assertMinimumOverSingles(graph, collision, targets, sparse, true);
	}

	@Test
	public void heuristicIsTheMinimumOverSingleTargets() throws Exception
	{
		RoutingStatic stat = SparseFixture.create();
		SiteGraph graph = new SiteGraph(stat, account());
		CollisionMap collision = SparseFixture.collision();
		int[] targets = {SparseFixture.TARGET, SparseFixture.C};
		PreparedTarget multi = PreparedTarget.prepare(graph, collision, targets);
		PreparedTarget first = PreparedTarget.prepare(graph, collision, targets[0]);
		PreparedTarget second = PreparedTarget.prepare(graph, collision, targets[1]);

		for (int packed : SparseFixture.PROBES)
		{
			for (BankVisitState banked : BankVisitState.values())
			{
				for (int[] components : new int[][] {{0}, {1}, {0, 1}})
				{
					int expected = Math.min(first.heuristic().estimateRaw(packed, banked, components),
						second.heuristic().estimateRaw(packed, banked, components));
					assertEquals(expected, multi.heuristic().estimateRaw(packed, banked, components));
					assertEquals(expected, multi.heuristic().estimate(packed, banked, components));
				}
			}
		}
	}

	@Test
	public void forwardSearchEndsAtTheCheapestTarget() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		SiteGraph graph = new SiteGraph(stat, account(new Transport[] {
			local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.C, 5),
			local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, 1),
			local(RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.D, 2)}));
		CollisionMap collision = emptyCollision();
		int[] targets = {RoutingStaticTestFixture.C, RoutingStaticTestFixture.D};

		ExactForwardSearch.Result multi = PreparedTarget.prepare(graph, collision, targets)
			.search(RoutingStaticTestFixture.A, () -> false, 1);

		assertTrue(multi.reached());
		assertEquals(3, multi.cost());
		assertEquals(RoutingStaticTestFixture.D, multi.path().get(multi.path().size() - 1).getPackedPosition());
		for (int target : targets)
		{
			ExactForwardSearch.Result single = PreparedTarget.prepare(graph, collision, target)
				.search(RoutingStaticTestFixture.A, () -> false, 1);
			assertTrue(single.cost() >= multi.cost());
		}
	}

	@Test
	public void unreachableTargetsDoNotHideAReachableOne() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		SiteGraph graph = new SiteGraph(stat, account(local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.C, 4)));
		int unreachable = WorldPointUtil.packWorldPoint(3000, 3000, 0);

		ExactForwardSearch.Result result = PreparedTarget.prepare(graph, emptyCollision(),
			new int[] {unreachable, RoutingStaticTestFixture.C}).search(RoutingStaticTestFixture.A, () -> false, 1);

		assertTrue(result.reached());
		assertEquals(4, result.cost());
	}

	@Test
	public void sessionCachesTargetSetsRegardlessOfOrder() throws Exception
	{
		ExactRoutingSession session = new ExactRoutingSession();
		CollisionMap collision = emptyCollision();
		SiteGraph graph = session.graph(RoutingStaticTestFixture.create(), account()).value();
		int a = RoutingStaticTestFixture.C, b = RoutingStaticTestFixture.D;

		ExactRoutingSession.Lookup<PreparedTarget> first = session.target(graph, collision, new int[] {a, b});
		ExactRoutingSession.Lookup<PreparedTarget> reordered = session.target(graph, collision, new int[] {b, a, b});

		assertFalse(first.reused());
		assertTrue(reordered.reused());
		assertSame(first.value(), reordered.value());
		assertFalse(session.target(graph, collision, a).reused());
	}

	private static void assertMinimumOverSingles(SiteGraph graph, CollisionMap collision, int[] targets,
		ReverseLabels multi, boolean sparse)
	{
		for (int state = 0; state < graph.stateCount(); state++)
		{
			int expected = ExactCosts.INF;
			for (int target : targets)
			{
				TargetOverlay single = new TargetOverlay(graph, collision, target);
				ReverseLabels labels = sparse ? ReverseLabels.computeSparse(single) : ReverseLabels.computeClique(single);
				expected = Math.min(expected, labels.labels()[state]);
			}
			assertEquals("state " + state, expected, multi.labels()[state]);
		}
	}

	private static CollisionMap t3Collision()
	{
		return neighbours(Map.of(
			T3_TARGET, new int[] {RoutingStaticTestFixture.T3_SEARCH_0, RoutingStaticTestFixture.T3_SEARCH_1},
			SECOND_SYNTHETIC, new int[] {RoutingStaticTestFixture.T3_SEARCH_0}));
	}

	private static CollisionMap neighbours(Map<Integer, int[]> neighbours)
	{
		return new CollisionMap(null)
		{
			@Override public int[] ordinaryWalkingNeighbors(int packedPoint)
			{
				int[] result = neighbours.get(packedPoint);
				return result == null ? new int[0] : result.clone();
			}
			@Override public boolean isBlocked(int x, int y, int z)
			{
				return false;
			}
		};
	}

	private static CollisionMap emptyCollision()
	{
		return neighbours(Map.of());
	}

	private static PreparedRoutingAccount account(Transport... local)
	{
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(local),
			TransportAvailabilityFixture.of(), false, java.util.Set.of(), 0, true, ignored -> 0);
	}

	private static Transport local(int origin, int destination, int duration)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination)
			.type(TransportType.TRANSPORT).duration(duration).build();
	}

	/** The sparse-network world from {@link SparseManhattanTest}. */
	private static final class SparseFixture
	{
		static final int A = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		static final int B = WorldPointUtil.packWorldPoint(1003, 1000, 0);
		static final int C = WorldPointUtil.packWorldPoint(1003, 1004, 0);
		static final int OTHER_PLANE = WorldPointUtil.packWorldPoint(1000, 1000, 1);
		static final int TARGET = WorldPointUtil.packWorldPoint(1001, 1001, 0);
		static final int[] PROBES = {A, B, C, TARGET, OTHER_PLANE, WorldPointUtil.packWorldPoint(1002, 1002, 0)};

		static RoutingStatic create() throws Exception
		{
			return RoutingStatic.create(new int[] {A, OTHER_PLANE}, new int[] {0, 1}, new byte[] {0, 0},
				new int[] {-1, -1}, new int[] {-1, -1}, new int[] {A, B, C, OTHER_PLANE},
				new int[] {0, 1, 2, 3, 4}, new int[] {0, 0, 0, 1}, 2, new int[] {0, 3, 4},
				new int[] {0, 1, 2, 3}, new int[0], new int[0], new int[0], new int[0], 4, 5, 1, 3, 6,
				new int[] {0, 1, 2, 3, 3, 6}, new int[] {4, 4, 4, 0, 1, 2}, new int[] {6, 0, 8, 6, 0, 8});
		}

		static CollisionMap collision()
		{
			return neighbours(Map.of(TARGET, new int[] {A, OTHER_PLANE}));
		}
	}
}
