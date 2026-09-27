package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import shortestpath.ShortestPathConfig;
import shortestpath.pathfinder.exact.ExactForwardSearch;
import shortestpath.pathfinder.exact.PreparedHeuristic;
import shortestpath.pathfinder.exact.PreparedRoutingAccount;
import shortestpath.pathfinder.exact.ReverseLabels;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.pathfinder.exact.RoutingStaticTestFixture;
import shortestpath.pathfinder.exact.SiteGraph;
import shortestpath.pathfinder.exact.TargetOverlay;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class ExactPathfinderTest
{
	@Test
	public void sourceGlobalReconstructsAnExactTransportPath()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account(global(RoutingStaticTestFixture.D, 2), new Transport[0], false);
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D);

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.A);

		assertTrue(result.reached());
		assertEquals(2, result.cost());
		assertEquals(2, result.path().size());
		assertEquals(RoutingStaticTestFixture.A, result.path().get(0).getPackedPosition());
		assertEquals(RoutingStaticTestFixture.D, result.path().get(1).getPackedPosition());
		assertTrue(result.counters().heuristicEvaluations() > 0);
	}

	@Test
	public void globalsUseSearchNodesRatherThanSiteIndices()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.createT3();
		PreparedRoutingAccount account = account(global(RoutingStaticTestFixture.T3_B, 2),
			new Transport[0], false);
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.T3_B);

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.T3_A);

		assertTrue(result.reached());
		assertEquals(2, result.cost());
		assertEquals(RoutingStaticTestFixture.T3_B,
			result.path().get(result.path().size() - 1).getPackedPosition());
	}

	@Test
	public void bankTransitionKeepsThePostBankLayerAndUsesBankedGlobal()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account(
			local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, 1),
			new Transport[] {global(RoutingStaticTestFixture.D, 3)}, true);
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D);

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.A);

		assertTrue(result.reached());
		assertEquals(4, result.cost());
		assertEquals(3, result.path().size());
		assertTrue(result.path().get(2).isBankVisited());
		assertEquals(RoutingStaticTestFixture.D, result.path().get(2).getPackedPosition());
	}

	@Test
	public void cancellationDoesNotPublishACompletedExactRoute()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account();
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D);

		ExactForwardSearch.Result result = ExactForwardSearch.search(target,
			PreparedHeuristic.prepare(target, ReverseLabels.compute(target)), RoutingStaticTestFixture.A, () -> true);

		assertTrue(result.cancelled());
		assertFalse(result.reached());
		assertEquals(1, result.path().size());
		assertEquals(RoutingStaticTestFixture.A, result.path().get(0).getPackedPosition());
	}

	@Test
	public void legacyIsTheDefaultAndExactIsExplicit()
	{
		ShortestPathConfig config = mock(ShortestPathConfig.class);
		when(config.pathfinderBackend()).thenReturn(PathfinderBackend.LEGACY, PathfinderBackend.EXACT);
		assertEquals(PathfinderBackend.LEGACY, config.pathfinderBackend());
		assertEquals(PathfinderBackend.EXACT, config.pathfinderBackend());
		assertTrue(ActiveSearch.class.isAssignableFrom(ExactPathfinder.class));
	}

	private static ExactForwardSearch.Result search(TargetOverlay target, int start)
	{
		return ExactForwardSearch.search(target, PreparedHeuristic.prepare(target, ReverseLabels.compute(target)), start);
	}

	private static TargetOverlay target(RoutingStatic stat, PreparedRoutingAccount account, int target)
	{
		return new TargetOverlay(new SiteGraph(stat, account), emptyCollision(), target);
	}

	private static CollisionMap emptyCollision()
	{
		return new CollisionMap(null)
		{
			@Override public int[] ordinaryWalkingNeighbors(int packedPoint)
	{ return new int[0];
	}
			@Override public boolean isBlocked(int x, int y, int z)
	{ return false;
	}
		};
	}

	private static PreparedRoutingAccount account()
	{
		return account(new Transport[0], new Transport[0], false);
	}

	private static PreparedRoutingAccount account(Transport carried, Transport[] banked, boolean bankPath)
	{
		return account(new Transport[] {carried}, banked, bankPath);
	}

	private static PreparedRoutingAccount account(Transport[] carried, Transport[] banked, boolean bankPath)
	{
		return PreparedRoutingAccount.compile(availability(carried), availability(banked), bankPath, true, ignored -> 0);
	}

	private static TransportAvailability availability(Transport... transports)
	{
		TransportAvailability.Builder builder = new TransportAvailability.Builder(transports.length);
		for (Transport transport : transports) builder.add(transport);
		return builder.build();
	}

	private static Transport local(int origin, int destination, int duration)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination)
			.type(TransportType.TRANSPORT).duration(duration).build();
	}

	private static Transport global(int destination, int duration)
	{
		return new Transport.TransportBuilder().destination(destination)
			.type(TransportType.TELEPORTATION_ITEM).duration(duration).build();
	}
}
