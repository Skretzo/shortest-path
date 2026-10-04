package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.exact.ExactCosts;
import shortestpath.pathfinder.exact.PreparedRoutingAccount;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.pathfinder.exact.RoutingStaticTestFixture;
import shortestpath.pathfinder.exact.SiteGraph;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class PreparedRoutingAccountTest
{
	private RoutingStatic stat;
	private int penalty;

	@Before public void setUp() throws Exception
	{
		stat = RoutingStaticTestFixture.create();
		penalty = 4;
	}

	@Test
	public void compilesSixViewsAndStableSnapshot()
	{
		Transport carriedLocal = local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, 2);
		Transport sharedLocal = local(RoutingStaticTestFixture.C, RoutingStaticTestFixture.D, 1);
		Transport bankedLocal = local(RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.C, 3);
		Transport wilderness = global(RoutingStaticTestFixture.D, 2, 30);
		Transport ordinary = global(RoutingStaticTestFixture.D, 5, 29);

		PreparedRoutingAccount first =
			PreparedRoutingAccount.compile(availability(carriedLocal, sharedLocal, wilderness, ordinary),
				availability(bankedLocal, sharedLocal, wilderness, ordinary), true, Set.of(RoutingStaticTestFixture.BANK), 0, true, ignored -> penalty);
		PreparedRoutingAccount second =
			PreparedRoutingAccount.compile(availability(ordinary, wilderness, sharedLocal, carriedLocal),
				availability(ordinary, wilderness, sharedLocal, bankedLocal), true, Set.of(RoutingStaticTestFixture.BANK), 0, true, ignored -> penalty);

		assertEquals(2, first.localCount(false));
		assertEquals(2, first.localCount(true));
		assertEquals(2, first.globalCount(false));
		assertEquals(2, first.globalCount(true));
		assertEquals(1, first.wildernessGlobalCount(false));
		assertEquals(1, first.wildernessGlobalCount(true));
		assertEquals(6, first.localCost(false, 0));
		assertEquals(7, first.localCost(true, 0));
		assertEquals(6, first.wildernessGlobalCost(true, 0));
		assertEquals(first.fingerprint(), second.fingerprint());

		// The prepared arrays are query-local; later mutation of a source transport cannot alter them.
		carriedLocal.setDestination(RoutingStaticTestFixture.D);
		assertEquals(RoutingStaticTestFixture.BANK, first.localDestination(false, 0));
	}

	@Test
	public void classifiesWildernessAtTheDeepWildernessThreshold()
	{
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(
			availability(global(RoutingStaticTestFixture.C, 1, 29), global(RoutingStaticTestFixture.D, 1, 30)),
			availability(), false, Set.of(), 0, true, ignored -> 0);
		assertEquals(2, account.globalCount(false));
		assertEquals(1, account.wildernessGlobalCount(false));
		assertEquals(30, account.wildernessGlobalMaxWilderness(false, 0));
	}

	@Test
	public void pohOriginGetsLandingAlias()
	{
		Transport poh = local(RoutingStaticTestFixture.POH_ORIGIN, RoutingStaticTestFixture.C, 1);
		TransportAvailability.Builder builder = new TransportAvailability.Builder(1);
		builder.add(poh);
		builder.remapPohTransports();
		PreparedRoutingAccount account =
			PreparedRoutingAccount.compile(builder.build(), availability(), false, Set.of(), 0, true, ignored -> 0);
		Set<Integer> origins = new HashSet<>();
		for (int i = 0; i < account.localCount(false); i++)
			origins.add(account.localOrigin(false, i));
		assertTrue(origins.contains(RoutingStaticTestFixture.POH_ORIGIN));
		assertTrue(origins.contains(RoutingStaticTestFixture.POH_LANDING));

		PreparedRoutingAccount unavailable = PreparedRoutingAccount.compile(
			availability(), availability(), false, Set.of(), 0, true, ignored -> 0);
		assertEquals(0, unavailable.localCount(false));
	}

	@Test
	public void graphHasTwoLayersDirectedEdgesBankHubAndSeparators()
	{
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(
			availability(local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, 2)),
			availability(local(RoutingStaticTestFixture.BANK, RoutingStaticTestFixture.C, 3),
				global(RoutingStaticTestFixture.D, 5, 29), global(RoutingStaticTestFixture.D, 2, 30)),
			true, Set.of(RoutingStaticTestFixture.BANK), 0, true, ignored -> penalty);
		SiteGraph graph = new SiteGraph(stat, account);
		int bank = graph.nodeForTile(RoutingStaticTestFixture.BANK);
		int destination = graph.nodeForTile(RoutingStaticTestFixture.D);
		int hub = graph.bankedGlobalHubNode();

		assertEquals(7, graph.nodeCount());
		assertEquals(14, graph.stateCount());
		assertEquals(bank * 2, SiteGraph.stateId(bank, false));
		assertEquals(bank * 2 + 1, SiteGraph.stateId(bank, true));
		assertTrue(graph.hasBankedGlobalHub());
		assertTrue(has(graph, SiteGraph.stateId(bank, false), SiteGraph.stateId(hub, true), 0,
			SiteGraph.EdgeKind.BANK_GLOBAL_ENTRY));
		assertTrue(has(graph, SiteGraph.stateId(hub, true), SiteGraph.stateId(destination, true), 6,
			SiteGraph.EdgeKind.BANK_GLOBAL_DESTINATION));
		assertFalse(has(graph, SiteGraph.stateId(bank, true), SiteGraph.stateId(bank, false), 0, null));
		assertTrue(has(graph, SiteGraph.stateId(bank, false), SiteGraph.stateId(bank, true), 0,
			SiteGraph.EdgeKind.BANK_TRANSITION));
		assertTrue(
			has(graph, SiteGraph.stateId(0, false), SiteGraph.stateId(2, false), 1, SiteGraph.EdgeKind.SEPARATOR));
		assertTrue(
			has(graph, SiteGraph.stateId(2, false), SiteGraph.stateId(0, false), 1, SiteGraph.EdgeKind.SEPARATOR));
		assertTrue(has(graph, SiteGraph.stateId(0, true), SiteGraph.stateId(2, true), 1, SiteGraph.EdgeKind.SEPARATOR));
	}

	@Test
	public void accessibleBanksAreAnAccountPropertyInTheFingerprint()
	{
		int bank = RoutingStaticTestFixture.BANK;
		int highPlane = WorldPointUtil.packWorldPoint(1000, 1000, 3);
		PreparedRoutingAccount none = PreparedRoutingAccount.compile(
			availability(), availability(), true, Set.of(), 0, true, ignored -> 0);
		PreparedRoutingAccount some = PreparedRoutingAccount.compile(
			availability(), availability(), true, java.util.List.of(highPlane, bank, highPlane), 0, true, ignored -> 0);
		PreparedRoutingAccount reordered = PreparedRoutingAccount.compile(
			availability(), availability(), true, java.util.List.of(bank, highPlane), 0, true, ignored -> 0);

		assertFalse(none.bankAccessible(bank));
		assertTrue(some.bankAccessible(bank));
		assertTrue(some.bankAccessible(highPlane));
		assertFalse(some.bankAccessible(RoutingStaticTestFixture.C));
		assertEquals(2, some.accessibleBankCount());
		assertEquals(bank, some.accessibleBankTile(0));
		assertEquals(highPlane, some.accessibleBankTile(1));
		assertTrue(none.fingerprint() != some.fingerprint());
		assertEquals(some.fingerprint(), reordered.fingerprint());
	}

	@Test
	public void bankVisitCostIsInTheFingerprintAndOnTheBankEdges()
	{
		Transport bankedGlobal = global(RoutingStaticTestFixture.D, 5, 30);
		PreparedRoutingAccount free = PreparedRoutingAccount.compile(availability(), availability(bankedGlobal), true,
			Set.of(RoutingStaticTestFixture.BANK), 0, true, ignored -> 0);
		PreparedRoutingAccount costed = PreparedRoutingAccount.compile(availability(), availability(bankedGlobal),
			true, Set.of(RoutingStaticTestFixture.BANK), 7, true, ignored -> 0);
		SiteGraph graph = new SiteGraph(stat, costed);
		int bank = graph.nodeForTile(RoutingStaticTestFixture.BANK);

		assertEquals(7, costed.bankVisitCost());
		assertTrue(free.fingerprint() != costed.fingerprint());
		assertTrue(has(graph, SiteGraph.stateId(bank, false), SiteGraph.stateId(bank, true), 7,
			SiteGraph.EdgeKind.BANK_TRANSITION));
		assertTrue(has(graph, SiteGraph.stateId(bank, false), SiteGraph.stateId(graph.bankedGlobalHubNode(), true), 7,
			SiteGraph.EdgeKind.BANK_GLOBAL_ENTRY));
		try
		{
			PreparedRoutingAccount.compile(availability(), availability(), true, Set.of(), -1, true, ignored -> 0);
			fail("negative bank visit cost must fail");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	@Test
	public void siteGraphOnlyBanksWhereTheAccountMay()
	{
		Transport bankedGlobal = global(RoutingStaticTestFixture.D, 5, 30);
		SiteGraph inaccessible = new SiteGraph(stat, PreparedRoutingAccount.compile(
			availability(), availability(bankedGlobal), true, Set.of(), 0, true, ignored -> 0));
		SiteGraph accessible = new SiteGraph(stat, PreparedRoutingAccount.compile(
			availability(), availability(bankedGlobal), true, Set.of(RoutingStaticTestFixture.BANK), 0, true,
			ignored -> 0));
		int bank = inaccessible.nodeForTile(RoutingStaticTestFixture.BANK);

		assertTrue("the static data still has the bank", stat.isBankSite(stat.siteIndex(RoutingStaticTestFixture.BANK)));
		assertFalse(inaccessible.hasBankedGlobalHub());
		assertFalse(has(inaccessible, SiteGraph.stateId(bank, false), SiteGraph.stateId(bank, true), 0,
			SiteGraph.EdgeKind.BANK_TRANSITION));
		assertTrue(accessible.hasBankedGlobalHub());
		assertTrue(has(accessible, SiteGraph.stateId(bank, false), SiteGraph.stateId(bank, true), 0,
			SiteGraph.EdgeKind.BANK_TRANSITION));
	}

	@Test
	public void rejectsMissingEndpointAndSaturatesOverflow()
	{
		try
		{
			new SiteGraph(stat,
				PreparedRoutingAccount.compile(availability(local(RoutingStaticTestFixture.A, 12345, 1)),
					availability(), false, Set.of(), 0, true, ignored -> 0));
			fail("missing endpoint must fail");
		}
		catch (IllegalStateException expected)
		{
		}

		PreparedRoutingAccount overflow = PreparedRoutingAccount.compile(
			availability(local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, Integer.MAX_VALUE)),
			availability(), false, Set.of(), 0, true, ignored -> 1);
		assertEquals(ExactCosts.INF, overflow.localCost(false, 0));
		try
		{
			PreparedRoutingAccount.compile(
				availability(local(RoutingStaticTestFixture.A, RoutingStaticTestFixture.BANK, 1)), availability(),
				false, Set.of(), 0, true, ignored -> - 1);
			fail("negative cost must fail");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	private static boolean has(SiteGraph graph, int from, int to, int cost, SiteGraph.EdgeKind kind)
	{
		for (int edge = graph.reverseEdgeStart(to); edge < graph.reverseEdgeEnd(to); edge++)
		{
			if (graph.reverseFromState(edge) == from && graph.reverseCost(edge) == cost
				&& (kind == null || graph.reverseKind(edge) == kind))
				return true;
		}
		return false;
	}

	private static TransportAvailability availability(Transport... transports)
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

	private static Transport global(int destination, int duration, int wilderness)
	{
		return new Transport.TransportBuilder()
			.destination(destination)
			.type(TransportType.TELEPORTATION_ITEM)
			.duration(duration)
			.maxWildernessLevel(wilderness)
			.build();
	}
}
