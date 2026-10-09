package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/** Global teleports are castable only where their own wilderness limit allows, as in legacy. */
public class WildernessGlobalTeleportTest
{
	private static final int OUTSIDE = WorldPointUtil.packWorldPoint(3200, 3400, 0);
	private static final int LEVEL_13 = WorldPointUtil.packWorldPoint(3156, 3620, 0);
	private static final int LEVEL_25 = WorldPointUtil.packWorldPoint(3200, 3712, 0);
	private static final int LEVEL_31 = WorldPointUtil.packWorldPoint(3200, 3760, 0);
	private static final int TARGET = WorldPointUtil.packWorldPoint(2658, 3157, 0);
	private static final int WALK = 50;
	private static final int TELEPORT = 2;

	@Test
	public void preparedGlobalsFollowEachTeleportsLimit()
	{
		Transport limit0 = teleport(WorldPointUtil.packWorldPoint(1000, 1000, 0), 0);
		Transport limit20 = teleport(WorldPointUtil.packWorldPoint(1001, 1000, 0), 20);
		Transport limit30 = teleport(WorldPointUtil.packWorldPoint(1002, 1000, 0), 30);
		Transport seasonal = new Transport.TransportBuilder().destination(WorldPointUtil.packWorldPoint(1003, 1000, 0))
			.type(TransportType.SEASONAL_TRANSPORTS).duration(TELEPORT).maxWildernessLevel(60).build();
		PreparedRoutingAccount account = account(limit0, limit20, limit30, seasonal);

		// location -> {limit 0, limit 20, limit 30, seasonal (not a teleport type)}
		assertCastable(account, OUTSIDE, true, true, true, true);
		assertCastable(account, LEVEL_13, false, true, true, true);
		assertCastable(account, LEVEL_25, false, false, true, true);
		assertCastable(account, LEVEL_31, false, false, false, true);
	}

	@Test
	public void levelZeroTeleportIsNotCastAfterAnObeliskToLevel13()
	{
		// Deep wilderness start, obelisk to the level-13 landing: Fishing Trawler (level 0) is not castable there.
		Transport obelisk = local(LEVEL_31, LEVEL_13, 1);
		ExactForwardSearch.Result result = search(LEVEL_31, obelisk, local(LEVEL_13, TARGET, WALK), teleport(TARGET, 0));

		assertEquals(1 + WALK, result.cost());
		assertEquals(List.of(LEVEL_31, LEVEL_13, TARGET), tiles(result));
	}

	@Test
	public void level20TeleportIsCastAfterAnObeliskToLevel13()
	{
		ExactForwardSearch.Result result = search(LEVEL_31, local(LEVEL_31, LEVEL_13, 1), local(LEVEL_13, TARGET, WALK),
			teleport(TARGET, 20));

		assertEquals(1 + TELEPORT, result.cost());
	}

	@Test
	public void startingAtLevel13CastsOnlyTeleportsUsableThere()
	{
		assertEquals(WALK, search(LEVEL_13, local(LEVEL_13, TARGET, WALK), teleport(TARGET, 0)).cost());
		assertEquals(TELEPORT, search(LEVEL_13, local(LEVEL_13, TARGET, WALK), teleport(TARGET, 20)).cost());
		assertEquals(TELEPORT, search(LEVEL_13, local(LEVEL_13, TARGET, WALK), teleport(TARGET, 30)).cost());
	}

	@Test
	public void level25CastsOnlyLevel30Teleports()
	{
		assertEquals(WALK, search(LEVEL_25, local(LEVEL_25, TARGET, WALK), teleport(TARGET, 0)).cost());
		assertEquals(WALK, search(LEVEL_25, local(LEVEL_25, TARGET, WALK), teleport(TARGET, 20)).cost());
		assertEquals(TELEPORT, search(LEVEL_25, local(LEVEL_25, TARGET, WALK), teleport(TARGET, 30)).cost());
		// Reached rather than started at.
		assertEquals(1 + WALK, search(LEVEL_31, local(LEVEL_31, LEVEL_25, 1), local(LEVEL_25, TARGET, WALK),
			teleport(TARGET, 20)).cost());
		assertEquals(1 + TELEPORT, search(LEVEL_31, local(LEVEL_31, LEVEL_25, 1), local(LEVEL_25, TARGET, WALK),
			teleport(TARGET, 30)).cost());
	}

	@Test
	public void aboveLevel30NoTeleportIsCast()
	{
		for (int limit : new int[] {0, 20, 30})
			assertEquals("limit " + limit, WALK,
				search(LEVEL_31, local(LEVEL_31, TARGET, WALK), teleport(TARGET, limit)).cost());
	}

	@Test
	public void outsideTheWildernessEveryTeleportIsCast()
	{
		for (int limit : new int[] {0, 20, 30})
			assertEquals("limit " + limit, TELEPORT,
				search(OUTSIDE, local(OUTSIDE, TARGET, WALK), teleport(TARGET, limit)).cost());
	}

	private static void assertCastable(PreparedRoutingAccount account, int location, boolean... expected)
	{
		TeleportCapability capability = TeleportCapability.at(location);
		Set<Integer> castable = new HashSet<>();
		for (int i = 0; i < account.globalCount(capability, BankVisitState.CARRIED); i++)
			castable.add(account.globalDestination(capability, BankVisitState.CARRIED, i));
		for (int i = 0; i < expected.length; i++)
			assertEquals(capability + " destination " + i, expected[i],
				castable.contains(WorldPointUtil.packWorldPoint(1000 + i, 1000, 0)));
	}

	private static ExactForwardSearch.Result search(int start, Transport... transports)
	{
		PreparedRoutingAccount account = account(transports);
		TargetOverlay target = new TargetOverlay(new SiteGraph(routingStatic(), account), emptyCollision(), TARGET);
		ExactForwardSearch.Result result = ExactForwardSearch.search(target,
			PreparedHeuristic.prepare(target, ReverseLabels.compute(target)), start);
		assertTrue(result.reached());
		ExactForwardSearch.Result oracle = ExactForwardSearch.search(target,
			PreparedHeuristic.prepare(target, ReverseLabels.compute(target)), start, false);
		assertEquals("optimised search agrees with the unoptimised one", oracle.cost(), result.cost());
		return result;
	}

	private static List<Integer> tiles(ExactForwardSearch.Result result)
	{
		return result.path().stream().map(PathStep::getPackedPosition).collect(java.util.stream.Collectors.toList());
	}

	private static PreparedRoutingAccount account(Transport... transports)
	{
		return PreparedRoutingAccount.compile(TransportAvailabilityFixture.of(transports),
			TransportAvailabilityFixture.of(), false, Set.of(), 0, true, ignored -> 0);
	}

	private static Transport local(int origin, int destination, int cost)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination)
			.type(TransportType.TRANSPORT).duration(cost).build();
	}

	private static Transport teleport(int destination, int maxWildernessLevel)
	{
		return new Transport.TransportBuilder().destination(destination).type(TransportType.TELEPORTATION_MINIGAME)
			.duration(TELEPORT).maxWildernessLevel(maxWildernessLevel).build();
	}

	/** One site per tile, no walking: every move is a transport. */
	private static RoutingStatic routingStatic()
	{
		int[] sites = {OUTSIDE, LEVEL_13, LEVEL_25, LEVEL_31, TARGET};
		sites = Arrays.stream(sites).map(t -> t ^ Integer.MIN_VALUE).sorted().map(t -> t ^ Integer.MIN_VALUE).toArray();
		int n = sites.length;
		int[] offsets = new int[n + 1], ids = new int[n], members = new int[n];
		for (int i = 0; i <= n; i++) offsets[i] = i;
		for (int i = 0; i < n; i++) members[i] = i;
		try
		{
			return RoutingStatic.create(new int[0], new int[0], new byte[0], new int[0], new int[0], sites, offsets,
				ids, 1, new int[] {0, n}, members, new int[0], new int[0], new int[0], new int[0], n, n, 0, 0, 0,
				new int[n + 1], new int[0], new int[0]);
		}
		catch (java.io.IOException e)
		{
			throw new AssertionError(e);
		}
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
