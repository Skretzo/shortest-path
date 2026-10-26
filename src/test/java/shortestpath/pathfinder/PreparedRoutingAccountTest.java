package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.astar.PreparedRoutingAccount;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.pathfinder.astar.RoutingStaticTestFixture;
import shortestpath.pathfinder.astar.SiteGraph;
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
	public void pohOriginGetsLandingAlias()
	{
		Transport poh = local(RoutingStaticTestFixture.POH_ORIGIN, RoutingStaticTestFixture.C, 1);
		TransportAvailability.Builder builder = new TransportAvailability.Builder(1);
		builder.add(poh);
		builder.remapPohTransports();
		PreparedRoutingAccount account =
			PreparedRoutingAccount.compile(builder.build(), availability(), false, Set.of(), 0, true, ignored -> 0);
		SiteGraph graph = new SiteGraph(stat, account);
		int destination = graph.nodeForTile(RoutingStaticTestFixture.C);

		assertTrue(has(graph, SiteGraph.stateId(graph.nodeForTile(RoutingStaticTestFixture.POH_ORIGIN), BankVisitState.CARRIED),
			SiteGraph.stateId(destination, BankVisitState.CARRIED), 1, SiteGraph.EdgeKind.LOCAL));
		assertTrue(has(graph, SiteGraph.stateId(graph.nodeForTile(RoutingStaticTestFixture.POH_LANDING), BankVisitState.CARRIED),
			SiteGraph.stateId(destination, BankVisitState.CARRIED), 1, SiteGraph.EdgeKind.LOCAL));
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
		assertEquals(bank * 2, SiteGraph.stateId(bank, BankVisitState.CARRIED));
		assertEquals(bank * 2 + 1, SiteGraph.stateId(bank, BankVisitState.BANKED));
		assertTrue(graph.hasBankedGlobalHub());
		assertTrue(has(graph, SiteGraph.stateId(bank, BankVisitState.CARRIED), SiteGraph.stateId(hub, BankVisitState.BANKED), 0,
			SiteGraph.EdgeKind.BANK_GLOBAL_ENTRY));
		assertTrue(has(graph, SiteGraph.stateId(hub, BankVisitState.BANKED), SiteGraph.stateId(destination, BankVisitState.BANKED), 6,
			SiteGraph.EdgeKind.BANK_GLOBAL_DESTINATION));
		assertFalse(has(graph, SiteGraph.stateId(bank, BankVisitState.BANKED), SiteGraph.stateId(bank, BankVisitState.CARRIED), 0, null));
		assertTrue(has(graph, SiteGraph.stateId(bank, BankVisitState.CARRIED), SiteGraph.stateId(bank, BankVisitState.BANKED), 0,
			SiteGraph.EdgeKind.BANK_TRANSITION));
		assertTrue(
			has(graph, SiteGraph.stateId(0, BankVisitState.CARRIED), SiteGraph.stateId(2, BankVisitState.CARRIED), 1, SiteGraph.EdgeKind.SEPARATOR));
		assertTrue(
			has(graph, SiteGraph.stateId(2, BankVisitState.CARRIED), SiteGraph.stateId(0, BankVisitState.CARRIED), 1, SiteGraph.EdgeKind.SEPARATOR));
		assertTrue(
			has(graph, SiteGraph.stateId(0, BankVisitState.BANKED), SiteGraph.stateId(2, BankVisitState.BANKED), 1, SiteGraph.EdgeKind.SEPARATOR));
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
		assertTrue(has(graph, SiteGraph.stateId(bank, BankVisitState.CARRIED), SiteGraph.stateId(bank, BankVisitState.BANKED), 7,
			SiteGraph.EdgeKind.BANK_TRANSITION));
		assertTrue(has(graph, SiteGraph.stateId(bank, BankVisitState.CARRIED), SiteGraph.stateId(graph.bankedGlobalHubNode(), BankVisitState.BANKED), 7,
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
		assertFalse(has(inaccessible, SiteGraph.stateId(bank, BankVisitState.CARRIED), SiteGraph.stateId(bank, BankVisitState.BANKED), 0,
			SiteGraph.EdgeKind.BANK_TRANSITION));
		assertTrue(accessible.hasBankedGlobalHub());
		assertTrue(has(accessible, SiteGraph.stateId(bank, BankVisitState.CARRIED), SiteGraph.stateId(bank, BankVisitState.BANKED), 0,
			SiteGraph.EdgeKind.BANK_TRANSITION));
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
