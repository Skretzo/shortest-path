package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class ExactRoutingSessionTest
{
	private static final int A = RoutingStaticTestFixture.A;
	private static final int BANK = RoutingStaticTestFixture.BANK;
	private static final int C = RoutingStaticTestFixture.C;
	private static final int D = RoutingStaticTestFixture.D;

	@Test
	public void graphIsReusedForAnEqualFingerprintFromAFreshCompile() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		ExactRoutingSession session = new ExactRoutingSession();

		ExactRoutingSession.Lookup<SiteGraph> first = session.graph(stat, account(global(D, 2)));
		ExactRoutingSession.Lookup<SiteGraph> second = session.graph(stat, account(global(D, 2)));

		assertFalse(first.reused());
		assertTrue(second.reused());
		assertSame(first.value(), second.value());
	}

	@Test
	public void changedAccountRebuildsTheGraphAndDropsPreparedTargets() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		CollisionMap collision = emptyCollision();
		ExactRoutingSession session = new ExactRoutingSession();
		SiteGraph before = session.graph(stat, account(global(D, 2))).value();
		session.target(before, collision, D);

		ExactRoutingSession.Lookup<SiteGraph> after = session.graph(stat, account(global(D, 3)));

		assertFalse(after.reused());
		assertNotSame(before, after.value());
		assertEquals(0, session.cachedTargetCount());
		assertFalse(session.target(after.value(), collision, D).reused());
	}

	@Test
	public void changedBankAccessRebuildsTheGraphInBothDirections() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		CollisionMap collision = emptyCollision();
		ExactRoutingSession session = new ExactRoutingSession();

		SiteGraph inaccessible = session.graph(stat, bankingAccount(java.util.Set.of())).value();
		ExactRoutingSession.Lookup<SiteGraph> accessible = session.graph(stat, bankingAccount(java.util.Set.of(BANK)));
		assertFalse(accessible.reused());
		assertTrue(search(accessible.value(), collision).reached());

		ExactRoutingSession.Lookup<SiteGraph> again = session.graph(stat, bankingAccount(java.util.Set.of()));
		assertFalse("a graph that may bank must not be reused for an account that may not", again.reused());
		assertNotSame(inaccessible, again.value());
		ExactForwardSearch.Result result = search(again.value(), collision);
		assertFalse(result.reached());
		assertFalse(result.path().stream().anyMatch(step -> step.getBankVisitState() == BankVisitState.BANKED));
	}

	@Test
	public void differentStaticDataRebuildsTheGraph() throws Exception
	{
		ExactRoutingSession session = new ExactRoutingSession();
		session.graph(RoutingStaticTestFixture.create(), account(global(D, 2)));

		assertFalse(session.graph(RoutingStaticTestFixture.create(), account(global(D, 2))).reused());
	}

	@Test
	public void preparedTargetIsReusedForNewStartsWithIdenticalResults() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		CollisionMap collision = emptyCollision();
		PreparedRoutingAccount account = account(global(D, 2));
		ExactRoutingSession session = new ExactRoutingSession();
		SiteGraph graph = session.graph(stat, account).value();

		ExactRoutingSession.Lookup<PreparedTarget> first = session.target(graph, collision, D);
		ExactRoutingSession.Lookup<PreparedTarget> second = session.target(graph, collision, D);

		assertFalse(first.reused());
		assertTrue(second.reused());
		assertSame(first.value(), second.value());
		for (int start : new int[] {A, BANK, C})
		{
			TargetOverlay fresh = new TargetOverlay(new SiteGraph(stat, account), collision, D);
			ExactForwardSearch.Result expected = ExactForwardSearch.search(fresh,
				PreparedHeuristic.prepare(fresh, ReverseLabels.compute(fresh)), start);
			ExactForwardSearch.Result actual = second.value().search(start, () -> false, 1);
			assertEquals(expected.reached(), actual.reached());
			assertEquals(expected.cost(), actual.cost());
			assertEquals(positions(expected), positions(actual));
		}
	}

	@Test
	public void leastRecentlyUsedTargetIsEvicted() throws Exception
	{
		CollisionMap collision = emptyCollision();
		ExactRoutingSession session = new ExactRoutingSession(2);
		SiteGraph graph = session.graph(RoutingStaticTestFixture.create(), account(global(D, 2))).value();
		session.target(graph, collision, A);
		session.target(graph, collision, C);
		assertTrue(session.target(graph, collision, A).reused());

		session.target(graph, collision, D);

		assertEquals(2, session.cachedTargetCount());
		assertTrue(session.target(graph, collision, A).reused());
		assertFalse(session.target(graph, collision, C).reused());
	}

	@Test
	public void clearingTargetsKeepsTheGraph() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		CollisionMap collision = emptyCollision();
		ExactRoutingSession session = new ExactRoutingSession();
		SiteGraph graph = session.graph(stat, account(global(D, 2))).value();
		session.target(graph, collision, D);

		session.clearTargets();

		assertTrue(session.graph(stat, account(global(D, 2))).reused());
		assertFalse(session.target(graph, collision, D).reused());
	}

	@Test
	public void targetsAreNotSharedAcrossCollisionMaps() throws Exception
	{
		ExactRoutingSession session = new ExactRoutingSession();
		SiteGraph graph = session.graph(RoutingStaticTestFixture.create(), account(global(D, 2))).value();
		session.target(graph, emptyCollision(), D);

		assertFalse(session.target(graph, emptyCollision(), D).reused());
	}

	@Test
	public void graphsFromOutsideTheSessionAreNotCached() throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		CollisionMap collision = emptyCollision();
		ExactRoutingSession session = new ExactRoutingSession();
		SiteGraph outside = new SiteGraph(stat, account(global(D, 2)));

		session.target(outside, collision, D);

		assertFalse(session.target(outside, collision, D).reused());
		assertEquals(0, session.cachedTargetCount());
	}

	private static List<Integer> positions(ExactForwardSearch.Result result)
	{
		return result.path().stream().map(PathStep::getPackedPosition).collect(Collectors.toList());
	}

	private static PreparedRoutingAccount account(Transport global)
	{
		TransportAvailability availability = TransportAvailabilityFixture.of(global);
		return PreparedRoutingAccount.compile(availability, availability, false, java.util.Set.of(), 0, true,
			ignored -> 0);
	}

	/** Banks at BANK (reached by a local transport from A) to take the only teleport to D. */
	private static PreparedRoutingAccount bankingAccount(java.util.Set<Integer> accessibleBanks)
	{
		Transport toBank = new Transport.TransportBuilder().origin(A).destination(BANK)
			.type(TransportType.TRANSPORT).duration(1).build();
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(toBank),
			TransportAvailabilityFixture.of(global(D, 3)), true, accessibleBanks, 0, true, ignored -> 0);
	}

	private static ExactForwardSearch.Result search(SiteGraph graph, CollisionMap collision)
	{
		TargetOverlay target = new TargetOverlay(graph, collision, D);
		return ExactForwardSearch.search(target, PreparedHeuristic.prepare(target, ReverseLabels.compute(target)), A);
	}

	private static Transport global(int destination, int duration)
	{
		return new Transport.TransportBuilder().destination(destination)
			.type(TransportType.TELEPORTATION_ITEM).duration(duration).maxWildernessLevel(0).build();
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
}
