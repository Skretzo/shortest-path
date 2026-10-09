package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
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
		assertTrue(result.path().get(2).getBankVisitState() == BankVisitState.BANKED);
		assertEquals(RoutingStaticTestFixture.D, result.path().get(2).getPackedPosition());
	}

	@Test
	public void inaccessibleBankIsNotEnteredEvenThoughTheStaticDataHasIt()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account(
			new Transport[] {local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, 1)},
			new Transport[] {global(RoutingStaticTestFixture.D, 3)}, true, java.util.Set.of());
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D);

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.A);

		assertFalse("only the banked teleport reaches D, and this account cannot bank", result.reached());
		assertFalse(result.path().stream().anyMatch(step -> step.getBankVisitState() == BankVisitState.BANKED));
	}

	@Test
	public void bankVisitCostIsChargedAndCanMakeBankingNotWorthIt()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		Transport[] carried = {local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, 1),
			local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.D, 10)};
		Transport[] banked = {global(RoutingStaticTestFixture.D, 3)};

		ExactForwardSearch.Result cheapVisit = search(target(stat, account(carried, banked, true,
			java.util.Set.of(RoutingStaticTestFixture.BANK), 5), RoutingStaticTestFixture.D), RoutingStaticTestFixture.A);
		ExactForwardSearch.Result dearVisit = search(target(stat, account(carried, banked, true,
			java.util.Set.of(RoutingStaticTestFixture.BANK), 20), RoutingStaticTestFixture.D), RoutingStaticTestFixture.A);

		assertEquals("walk 1 + visit 5 + banked teleport 3", 9, cheapVisit.cost());
		assertTrue(cheapVisit.path().stream().anyMatch(step -> step.getBankVisitState() == BankVisitState.BANKED));
		assertEquals("the direct transport beats 1 + 20 + 3", 10, dearVisit.cost());
		assertFalse(dearVisit.path().stream().anyMatch(step -> step.getBankVisitState() == BankVisitState.BANKED));
	}

	@Test
	public void disabledBankPathIgnoresAnAccessibleBank()
		throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PreparedRoutingAccount account = account(
			new Transport[] {local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, 1)},
			new Transport[] {global(RoutingStaticTestFixture.D, 3)}, false,
			java.util.Set.of(RoutingStaticTestFixture.BANK));
		TargetOverlay target = target(stat, account, RoutingStaticTestFixture.D);

		ExactForwardSearch.Result result = search(target, RoutingStaticTestFixture.A);

		assertTrue(account.bankAccessible(RoutingStaticTestFixture.BANK));
		assertFalse(result.reached());
		assertFalse(result.path().stream().anyMatch(step -> step.getBankVisitState() == BankVisitState.BANKED));
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

	@Test
	public void previousRouteIsShownUntilTheSearchCompletes()
		throws Exception
	{
		List<PathStep> previous = List.of(new PathStep(RoutingStaticTestFixture.C, BankVisitState.CARRIED),
			new PathStep(RoutingStaticTestFixture.D, BankVisitState.CARRIED));
		ExactPathfinder search = pathfinder(0);
		search.showUntilDone(previous);

		assertTrue(search.isShowingProvisionalPath());
		assertEquals(previous, search.getPath());
		search.run();

		assertFalse(search.isShowingProvisionalPath());
		assertTrue(search.getResult().isReached());
		assertEquals(RoutingStaticTestFixture.D, search.getPath().get(search.getPath().size() - 1).getPackedPosition());
		assertEquals(RoutingStaticTestFixture.A, search.getPath().get(0).getPackedPosition());
	}

	@Test
	public void cancelledSearchKeepsShowingThePreviousRoute()
		throws Exception
	{
		List<PathStep> previous = List.of(new PathStep(RoutingStaticTestFixture.C, BankVisitState.CARRIED),
			new PathStep(RoutingStaticTestFixture.D, BankVisitState.CARRIED));
		ExactPathfinder search = pathfinder(0);
		search.showUntilDone(previous);
		search.cancel();

		search.run();

		assertTrue(search.isShowingProvisionalPath());
		assertEquals(previous, search.getPath());
	}

	@Test
	public void trivialPreviousRouteIsNotShown()
		throws Exception
	{
		ExactPathfinder search = pathfinder(0);
		search.showUntilDone(List.of(new PathStep(RoutingStaticTestFixture.C, BankVisitState.CARRIED)));

		assertFalse(search.isShowingProvisionalPath());
		assertEquals(1, search.getPath().size());
	}

	private static ExactPathfinder pathfinder(long cutoffMillis) throws Exception
	{
		RoutingStatic stat = RoutingStaticTestFixture.create();
		PathfinderConfig config = mock(PathfinderConfig.class);
		CollisionMap collision = emptyCollision();
		when(config.getMap()).thenReturn(collision);
		when(config.prepareExactRoutingAccount(true))
			.thenReturn(account(global(RoutingStaticTestFixture.D, 2), new Transport[0], false));
		when(config.getCalculationCutoffMillis()).thenReturn(cutoffMillis);
		return new ExactPathfinder(config, stat, null, RoutingStaticTestFixture.A,
			java.util.Set.of(RoutingStaticTestFixture.D), null, 1);
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
		return account(carried, banked, bankPath,
			java.util.Set.of(RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.T3_B));
	}

	private static PreparedRoutingAccount account(Transport[] carried, Transport[] banked, boolean bankPath,
		java.util.Set<Integer> accessibleBanks)
	{
		return account(carried, banked, bankPath, accessibleBanks, 0);
	}

	private static PreparedRoutingAccount account(Transport[] carried, Transport[] banked, boolean bankPath,
		java.util.Set<Integer> accessibleBanks, int bankVisitCost)
	{
		return PreparedRoutingAccount.compile(availability(carried), availability(banked), bankPath,
			accessibleBanks, bankVisitCost, true, ignored -> 0);
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
			.type(TransportType.TELEPORTATION_ITEM).duration(duration).maxWildernessLevel(0).build();
	}
}
