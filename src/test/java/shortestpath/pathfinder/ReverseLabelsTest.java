package shortestpath.pathfinder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import org.junit.Test;
import shortestpath.pathfinder.exact.ExactCosts;
import shortestpath.pathfinder.exact.ExactMinHeap;
import shortestpath.pathfinder.exact.PreparedHeuristic;
import shortestpath.pathfinder.exact.PreparedRoutingAccount;
import shortestpath.pathfinder.exact.ReverseLabels;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.pathfinder.exact.RoutingStaticTestFixture;
import shortestpath.pathfinder.exact.SiteGraph;
import shortestpath.pathfinder.exact.TargetOverlay;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class ReverseLabelsTest
{
	@Test
	public void targetAttachmentsWalkingPlanesAndHeuristicContext() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.createT3();
		SiteGraph graph = new SiteGraph(stat, emptyAccount());
		TargetOverlay target = new TargetOverlay(graph,
			collision(RoutingStaticTestFixture.T3_TARGET, RoutingStaticTestFixture.T3_SEARCH_0,
				RoutingStaticTestFixture.T3_SEARCH_1),
			RoutingStaticTestFixture.T3_TARGET);

		assertTrue(target.synthetic());
		assertArrayEquals(new int[] {0, 1}, target.components());
		assertEquals(graph.nodeCount() + 1, target.queryNodeCount());
		assertEquals(5, target.attachmentCount());
		ReverseLabels reverse = ReverseLabels.compute(target);
		assertEquals(0, reverse.targetLabel(BankVisitState.CARRIED));
		assertEquals(1, reverse.label(graph.nodeForTile(RoutingStaticTestFixture.T3_A), BankVisitState.CARRIED));
		assertEquals(1, reverse.label(graph.nodeForTile(RoutingStaticTestFixture.T3_C), BankVisitState.CARRIED));
		assertEquals(ExactCosts.INF, reverse.label(graph.nodeForTile(RoutingStaticTestFixture.T3_D), BankVisitState.CARRIED));

		PreparedHeuristic heuristic = PreparedHeuristic.prepare(target, reverse);
		assertEquals(0, heuristic.estimate(RoutingStaticTestFixture.T3_TARGET, BankVisitState.CARRIED));
		assertEquals(1, heuristic.estimate(RoutingStaticTestFixture.T3_SEARCH_0, BankVisitState.CARRIED));
		assertEquals(1, heuristic.estimate(RoutingStaticTestFixture.T3_SEARCH_0, BankVisitState.CARRIED, new int[] {0}));
		assertEquals(ExactCosts.INF, heuristic.estimate(20000, BankVisitState.CARRIED, new int[0]));
	}

	@Test
	public void staticTargetAndDirectedReverseTransport() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.createT3();
		PreparedRoutingAccount account =
			account(local(RoutingStaticTestFixture.T3_B, RoutingStaticTestFixture.T3_D, 7));
		SiteGraph graph = new SiteGraph(stat, account);
		TargetOverlay staticTarget =
			new TargetOverlay(graph, collision(RoutingStaticTestFixture.T3_D), RoutingStaticTestFixture.T3_D);
		assertTrue(!staticTarget.synthetic());
		assertEquals(graph.nodeForTile(RoutingStaticTestFixture.T3_D), staticTarget.targetNode());
		ReverseLabels reverse = ReverseLabels.compute(staticTarget);
		assertEquals(7, reverse.label(graph.nodeForTile(RoutingStaticTestFixture.T3_B), BankVisitState.CARRIED));

		TargetOverlay reverseDirection =
			new TargetOverlay(graph, collision(RoutingStaticTestFixture.T3_B), RoutingStaticTestFixture.T3_B);
		reverse = ReverseLabels.compute(reverseDirection);
		assertEquals(ExactCosts.INF, reverse.label(graph.nodeForTile(RoutingStaticTestFixture.T3_D), BankVisitState.CARRIED));
	}

	@Test
	public void bankLayersGlobalHubAndStaleEntries() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.createT3();
		PreparedRoutingAccount account = account(local(RoutingStaticTestFixture.T3_B, RoutingStaticTestFixture.T3_D, 7),
			local(RoutingStaticTestFixture.T3_A, RoutingStaticTestFixture.T3_D, 10),
			local(RoutingStaticTestFixture.T3_A, RoutingStaticTestFixture.T3_B, 1));
		SiteGraph graph = new SiteGraph(stat, account);
		ReverseLabels stale = ReverseLabels.compute(
			new TargetOverlay(graph, collision(RoutingStaticTestFixture.T3_D), RoutingStaticTestFixture.T3_D));
		assertEquals(8, stale.label(graph.nodeForTile(RoutingStaticTestFixture.T3_A), BankVisitState.CARRIED));
		assertTrue(stale.staleCount() > 0);

		PreparedRoutingAccount bankAccount =
			account(new Transport[] {local(RoutingStaticTestFixture.T3_B, RoutingStaticTestFixture.T3_D, 9)},
				new Transport[] {local(RoutingStaticTestFixture.T3_B, RoutingStaticTestFixture.T3_D, 20),
					global(RoutingStaticTestFixture.T3_D, 6)},
				true);
		SiteGraph bankGraph = new SiteGraph(stat, bankAccount);
		assertTrue(bankGraph.hasBankedGlobalHub());
		TargetOverlay target =
			new TargetOverlay(bankGraph, collision(RoutingStaticTestFixture.T3_D), RoutingStaticTestFixture.T3_D);
		ReverseLabels reverse = ReverseLabels.compute(target);
		int bank = bankGraph.nodeForTile(RoutingStaticTestFixture.T3_B);
		assertEquals(6, reverse.label(bank, BankVisitState.CARRIED));
		assertEquals(20, reverse.label(bank, BankVisitState.BANKED));
		assertEquals(6, reverse.label(bankGraph.bankedGlobalHubNode(), BankVisitState.BANKED));
		assertEquals(ExactCosts.INF, reverse.label(bankGraph.bankedGlobalHubNode(), BankVisitState.CARRIED));
	}

	@Test
	public void heapOrderAndIdentityMismatch() throws Exception
	{
		ExactMinHeap heap = new ExactMinHeap(1);
		heap.push(1, 1, 2);
		heap.push(1, 1, 1);
		heap.push(3, 3, 0);
		heap.push(1, 2, 0);
		assertEquals(1, heap.poll() ? heap.state() : -1);
		assertEquals(2, heap.poll() ? heap.state() : -1);
		assertEquals(0, heap.poll() ? heap.state() : -1);
		assertEquals(0, heap.poll() ? heap.state() : -1);
		assertTrue(!heap.poll());

		RoutingStatic stat = RoutingStaticTestFixture.createT3();
		SiteGraph graph = new SiteGraph(stat, emptyAccount());
		TargetOverlay first =
			new TargetOverlay(graph, collision(RoutingStaticTestFixture.T3_TARGET), RoutingStaticTestFixture.T3_TARGET);
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(first, ReverseLabels.compute(first));
		TargetOverlay otherTarget =
			new TargetOverlay(new SiteGraph(RoutingStaticTestFixture.createT3(), emptyAccount()),
				collision(RoutingStaticTestFixture.T3_TARGET), RoutingStaticTestFixture.T3_TARGET);
		try
		{
			heuristic.estimate(otherTarget, RoutingStaticTestFixture.T3_TARGET, BankVisitState.CARRIED);
			fail("another static snapshot must be rejected");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	@Test
	public void reverseLabelsMatchSimpleRelaxationOracle() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.createT3();
		PreparedRoutingAccount account = account(
			new Transport[] {local(RoutingStaticTestFixture.T3_B, RoutingStaticTestFixture.T3_D, 9)},
			new Transport[] {local(RoutingStaticTestFixture.T3_A, RoutingStaticTestFixture.T3_B, 2),
				global(RoutingStaticTestFixture.T3_D, 6)},
			true);
		SiteGraph graph = new SiteGraph(stat, account);
		TargetOverlay target = new TargetOverlay(graph,
			collision(RoutingStaticTestFixture.T3_TARGET, RoutingStaticTestFixture.T3_SEARCH_0,
				RoutingStaticTestFixture.T3_SEARCH_1),
			RoutingStaticTestFixture.T3_TARGET);

		assertArrayEquals(simpleLabels(target), ReverseLabels.compute(target).labels());
	}

	private static int[] simpleLabels(TargetOverlay target)
	{
		SiteGraph graph = target.graph();
		RoutingStatic stat = target.routingStatic();
		int[] labels = new int[target.queryNodeCount() * 2];
		Arrays.fill(labels, ExactCosts.INF);
		labels[target.targetState(BankVisitState.CARRIED)] = 0;
		labels[target.targetState(BankVisitState.BANKED)] = 0;
		boolean changed;
		do
		{
			changed = false;
			for (int state = 0; state < graph.stateCount(); state++)
			{
				for (int edge = graph.reverseEdgeStart(state); edge < graph.reverseEdgeEnd(state); edge++)
				{
					changed |= relax(labels, graph.reverseFromState(edge), labels[state], graph.reverseCost(edge));
				}
			}
			for (BankVisitState banked : BankVisitState.values())
			{
				for (int from = 0; from < graph.spatialNodeCount(); from++)
				{
					for (int to = 0; to < graph.spatialNodeCount(); to++)
					{
						if (from != to && sharesComponent(stat.siteComponents(from), stat.siteComponents(to)))
						{
							changed |= relax(labels, SiteGraph.stateId(from, banked),
								labels[SiteGraph.stateId(to, banked)],
								shortestpath.WorldPointUtil.distanceBetween(stat.siteTile(from), stat.siteTile(to)));
						}
					}
					if (target.synthetic() && sharesComponent(stat.siteComponents(from), target.components()))
					{
						int siteState = SiteGraph.stateId(from, banked);
						int targetState = target.targetState(banked);
						int distance = shortestpath.WorldPointUtil.distanceBetween(
							stat.siteTile(from), target.packedTarget());
						changed |= relax(labels, siteState, labels[targetState], distance);
						changed |= relax(labels, targetState, labels[siteState], distance);
					}
				}
			}
		}
		while (changed);
		return labels;
	}

	private static boolean relax(int[] labels, int state, int base, int edge)
	{
		int candidate = ExactCosts.add(base, edge);
		if (candidate >= labels[state])
		{
			return false;
		}
		labels[state] = candidate;
		return true;
	}

	private static boolean sharesComponent(int[] left, int[] right)
	{
		for (int a : left)
		{
			for (int b : right)
			{
				if (a == b)
				{
					return true;
				}
			}
		}
		return false;
	}

	private static PreparedRoutingAccount emptyAccount()
	{
		return account(new Transport[0], new Transport[0], false);
	}

	private static PreparedRoutingAccount account(Transport... carried)
	{
		return account(carried, new Transport[0], false);
	}

	private static PreparedRoutingAccount account(Transport[] carried, Transport[] banked, boolean bankPath)
	{
		return PreparedRoutingAccount.compile(
			availability(carried), availability(banked), bankPath,
			java.util.Set.of(RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.T3_B), 0, true, ignored -> 0);
	}

	private static TransportAvailability availability(Transport[] transports)
	{
		TransportAvailability.Builder builder = new TransportAvailability.Builder(transports.length);
		for (Transport transport : transports)
			builder.add(transport);
		return builder.build();
	}

	private static Transport local(int origin, int destination, int duration)
	{
		return new Transport.TransportBuilder()
			.origin(origin)
			.destination(destination)
			.type(TransportType.TRANSPORT)
			.duration(duration)
			.build();
	}

	private static Transport global(int destination, int duration)
	{
		return new Transport.TransportBuilder()
			.destination(destination)
			.type(TransportType.TELEPORTATION_ITEM)
			.duration(duration)
			.maxWildernessLevel(0)
			.build();
	}

	private static CollisionMap collision(int target, int... neighbors)
	{
		return new CollisionMap(null)
		{
			@Override
			public int[] ordinaryWalkingNeighbors(int packedPoint)
			{
				return packedPoint == target ? neighbors.clone() : new int[0];
			}
		};
	}
}
