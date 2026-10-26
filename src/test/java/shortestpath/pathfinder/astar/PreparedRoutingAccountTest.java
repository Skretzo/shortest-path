package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.pathfinder.TransportAvailabilityFixture;
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
	public void localTransportsAreOrderedUnsignedAcrossPlanes()
	{
		int[] origins = {
			WorldPointUtil.packWorldPoint(3200, 3200, 3),
			WorldPointUtil.packWorldPoint(3200, 3200, 0),
			WorldPointUtil.packWorldPoint(3201, 3200, 2),
			WorldPointUtil.packWorldPoint(3200, 3201, 1),
		};
		Transport[] transports = new Transport[origins.length];
		for (int i = 0; i < origins.length; i++)
			transports[i] = new Transport.TransportBuilder().origin(origins[i]).destination(origins[i] + 1)
				.type(TransportType.TRANSPORT).duration(1).build();
		TransportAvailability availability = TransportAvailabilityFixture.of(transports);

		PreparedRoutingAccount account = PreparedRoutingAccount.compile(availability, availability, false, java.util.Set.of(), 0, true,
			ignored -> 0);

		PreparedRoutingAccount.View view = account.localView(BankVisitState.CARRIED);
		assertEquals(origins.length, view.destinations.length);
		for (int i = 1; i < view.destinations.length; i++)
			assertTrue(Integer.compareUnsigned(view.origins[i - 1], view.origins[i]) <= 0);
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

		assertEquals(2, first.localView(BankVisitState.CARRIED).destinations.length);
		assertEquals(2, first.localView(BankVisitState.BANKED).destinations.length);
		assertEquals(2, first.globalCount(BankVisitState.CARRIED));
		assertEquals(2, first.globalCount(BankVisitState.BANKED));
		assertEquals(1, first.globalCount(TeleportCapability.OVER_20, BankVisitState.CARRIED));
		assertEquals(1, first.globalCount(TeleportCapability.OVER_20, BankVisitState.BANKED));
		assertEquals(6, first.localView(BankVisitState.CARRIED).costs[0]);
		assertEquals(7, first.localView(BankVisitState.BANKED).costs[0]);
		assertEquals(6, first.globalCost(TeleportCapability.OVER_20, BankVisitState.BANKED, 0));
		assertEquals(first.fingerprint(), second.fingerprint());

		// The prepared arrays are query-local; later mutation of a source transport cannot alter them.
		carriedLocal.setDestination(RoutingStaticTestFixture.D);
		assertEquals(RoutingStaticTestFixture.BANK, first.localView(BankVisitState.CARRIED).destinations[0]);
	}

	@Test
	public void classifiesWildernessAtTheDeepWildernessThreshold()
	{
		PreparedRoutingAccount account = PreparedRoutingAccount.compile(
			availability(global(RoutingStaticTestFixture.C, 1, 29), global(RoutingStaticTestFixture.D, 1, 30)),
			availability(), false, Set.of(), 0, true, ignored -> 0);
		assertEquals(2, account.globalCount(BankVisitState.CARRIED));
		assertEquals(1, account.globalCount(TeleportCapability.OVER_20, BankVisitState.CARRIED));
		assertEquals(30, account.globalView(TeleportCapability.OVER_20, BankVisitState.CARRIED).maxWilderness[0]);
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
		assertEquals(CostArithmetic.INF, overflow.localView(BankVisitState.CARRIED).costs[0]);
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

	private static TransportAvailability availability(Transport... transports)
	{
		return TransportAvailabilityFixture.of(transports);
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
