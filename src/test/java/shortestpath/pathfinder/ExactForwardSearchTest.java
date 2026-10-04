package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import shortestpath.pathfinder.exact.ExactCosts;
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

public class ExactForwardSearchTest
{
	@Test
	public void walksLocallyAndKeepsTheTerminalState()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.createT4Walking();
		PreparedRoutingAccount account = account();
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.C, neighbours());

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.A);

		assertTrue(result.reached());
		assertFalse(result.cancelled());
		assertEquals(2, result.cost());
		assertTrue((result.terminalState() & 1) == 0);
		assertTrue(result.counters().walkingRelaxations() > 0);
	}

	@Test
	public void usesThePostBankLayerAndLocalTransport()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account(null,
			local(RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.D, 1), true);
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D,
			neighbours(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK));

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.A);

		assertTrue(result.reached());
		assertEquals(2, result.cost());
		assertTrue((result.terminalState() & 1) != 0);
		assertTrue(result.counters().transportRelaxations() > 0);
	}

	@Test
	public void entersABlockedLocalTransportOrigin()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account(local(RoutingStaticTestFixture.BANK,
			RoutingStaticTestFixture.D, 1));
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D, neighbours(),
			RoutingStaticTestFixture.BANK);

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.A);

		assertTrue(result.reached());
		assertEquals(2, result.cost());
	}

	@Test
	public void staleEntriesAndUnreachableHeuristicPrunesAreSafe()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account(
			local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.C, 5),
			local(RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.C, 1));
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D,
			neighbours(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK,
				RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.C));

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.A);

		assertFalse(result.reached());
		assertEquals(ExactCosts.INF, result.cost());
		assertTrue(result.counters().staleEntries() > 0);
	}

	@Test
	public void cancellationDoesNotPublishACompletedRoute()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account();
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D,
			neighbours(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK));
		BooleanSupplier cancelled = () -> true;

		ExactForwardSearch.Result result = ExactForwardSearch.search(target,
			PreparedHeuristic.prepare(target, ReverseLabels.compute(target)), RoutingStaticTestFixture.A, cancelled);

		assertTrue(result.cancelled());
		assertFalse(result.reached());
		assertEquals(ExactCosts.INF, result.cost());
	}

	private static ExactForwardSearch.Result search(TargetOverlay target, int start)
	{
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(target, ReverseLabels.compute(target));
		return ExactForwardSearch.search(target, heuristic, start);
	}

	private static TargetOverlay target(RoutingStatic stat, PreparedRoutingAccount account, int target,
		Map<Integer, int[]> neighbours)
	{
		return target(stat, account, target, neighbours, -1);
	}

	private static TargetOverlay target(RoutingStatic stat, PreparedRoutingAccount account, int target,
		Map<Integer, int[]> neighbours, int blocked)
	{
		SiteGraph graph = new SiteGraph(stat, account);
		return new TargetOverlay(graph, collision(neighbours, blocked), target);
	}

	private static CollisionMap collision(Map<Integer, int[]> neighbours)
	{
		return collision(neighbours, -1);
	}

	private static CollisionMap collision(Map<Integer, int[]> neighbours, int blocked)
	{
		return new CollisionMap(null)
		{
			@Override
			public int[] ordinaryWalkingNeighbors(int packedPoint)
			{
				int[] values = neighbours.get(packedPoint);
				return values == null ? new int[0] : values.clone();
			}

			@Override
			public boolean isBlocked(int x, int y, int z)
			{
				return shortestpath.WorldPointUtil.packWorldPoint(x, y, z) == blocked;
			}
		};
	}

	private static Map<Integer, int[]> neighbours(int... values)
	{
		Map<Integer, int[]> result = new HashMap<>();
		for (int i = 0; i < values.length; i += 2)
			result.put(values[i], new int[] {values[i + 1]});
		return result;
	}

	private static PreparedRoutingAccount account(Transport... carried)
	{
		return account(carried, new Transport[0], false);
	}

	private static PreparedRoutingAccount account(Transport carried, Transport banked, boolean bankPath)
	{
		return account(carried == null ? new Transport[0] : new Transport[] {carried},
			banked == null ? new Transport[0] : new Transport[] {banked}, bankPath);
	}

	private static PreparedRoutingAccount account(Transport[] carried, Transport[] banked, boolean bankPath)
	{
		return PreparedRoutingAccount.compile(availability(carried), availability(banked), bankPath,
			java.util.Set.of(RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.T3_B), true, ignored -> 0);
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
		return new Transport.TransportBuilder().origin(origin).destination(destination)
			.type(TransportType.TRANSPORT).duration(duration).build();
	}
}
