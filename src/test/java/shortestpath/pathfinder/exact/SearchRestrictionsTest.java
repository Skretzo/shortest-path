package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.pathfinder.WildernessChecker;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * {@link SearchRestrictions} mirrors the positional gates legacy checks per edge: neither walking
 * nor a local transport may land inside the wilderness or the league's always-blocked region from
 * outside it, while moving inside the gated area or out of it stays allowed. Global teleports are
 * barred from landing in the wilderness only when cast from outside it, matching the source-tile
 * check legacy runs on its abstract nodes.
 */
public class SearchRestrictionsTest
{
	// Fixture tiles: a short non-wilderness line leading to wilderness tiles. Masks join them in
	// array order, so geometry is irrelevant to the search but the coordinates are real, so the
	// wilderness and region predicates report genuine answers.
	private static final int SAFE2 = WorldPointUtil.packWorldPoint(2998, 3523, 0);
	private static final int SAFE = WorldPointUtil.packWorldPoint(2998, 3524, 0);
	private static final int WILD = WorldPointUtil.packWorldPoint(2995, 3540, 0);
	private static final int WILDT = WorldPointUtil.packWorldPoint(2995, 3541, 0);
	/** Level-25 wilderness: its teleport capability band is OVER_20. */
	private static final int WILD25 = WorldPointUtil.packWorldPoint(3200, 3712, 0);

	private static final SearchRestrictions AVOID_WILDERNESS = SearchRestrictions.of(true, null);
	private static final SearchRestrictions BLOCKED_WILD =
		SearchRestrictions.of(false, tile -> tile == WILD || tile == WILDT);

	@Test
	public void fixtureTilesHaveExpectedStatus()
	{
		assertFalse(WildernessChecker.isInWilderness(SAFE2));
		assertFalse(WildernessChecker.isInWilderness(SAFE));
		assertTrue(WildernessChecker.isInWilderness(WILD));
		assertTrue(WildernessChecker.isInWilderness(WILDT));
		assertEquals(TeleportCapability.OVER_20, TeleportCapability.at(WILD25));
	}

	@Test
	public void walkingIntoWildernessIsGated()
	{
		assertFalse(search(SAFE, WILD, AVOID_WILDERNESS).reached());
		assertTrue(search(SAFE, WILD, SearchRestrictions.none()).reached());
	}

	@Test
	public void leavingWildernessOnFootIsAllowed()
	{
		// The gate bars entering the wilderness, never walking out of it.
		assertTrue(search(WILD, SAFE, AVOID_WILDERNESS).reached());
		assertEquals(1, search(WILD, SAFE, AVOID_WILDERNESS).cost());
	}

	@Test
	public void localTransportIntoWildernessIsGated()
	{
		// Legacy flags transport destination nodes as tiles, so the same positional gate applies
		// to the jump's landing tile; a ditch-style crossing must not bypass it.
		PreparedRoutingAccount account = account(local(SAFE2, WILDT, 3));
		assertFalse(search(SAFE2, WILDT, AVOID_WILDERNESS, account).reached());
		assertTrue(search(SAFE2, WILDT, SearchRestrictions.none(), account).reached());
	}

	@Test
	public void localTransportOutOfWildernessIsAllowed()
	{
		PreparedRoutingAccount account = account(local(WILDT, SAFE2, 3));
		ExactForwardSearch.Result result = search(WILDT, SAFE2, AVOID_WILDERNESS, account);
		assertTrue(result.reached());
		assertEquals(3, result.cost());
	}

	@Test
	public void blockedRegionGatesWalkingAndTransports()
	{
		PreparedRoutingAccount account = account(local(SAFE2, WILDT, 3));
		assertFalse("walked into the blocked region", search(SAFE, WILD, BLOCKED_WILD, account).reached());
		assertFalse("transported into the blocked region",
			search(SAFE2, WILDT, BLOCKED_WILD, account).reached());
		assertTrue(search(SAFE, WILD, SearchRestrictions.none(), account).reached());
		assertTrue(search(SAFE2, WILDT, SearchRestrictions.none(), account).reached());
	}

	@Test
	public void leavingBlockedRegionByTransportIsAllowed()
	{
		PreparedRoutingAccount account = account(local(WILDT, SAFE2, 3));
		assertTrue(search(WILDT, SAFE2, BLOCKED_WILD, account).reached());
	}

	@Test
	public void globalTeleportIntoWildernessIsBarredFromOutside()
	{
		PreparedRoutingAccount account = account(teleport(WILDT, 0));
		assertFalse(search(SAFE2, WILDT, AVOID_WILDERNESS, account).reached());
		assertTrue(search(SAFE2, WILDT, SearchRestrictions.none(), account).reached());
	}

	@Test
	public void globalTeleportIntoWildernessIsAllowedFromInside()
	{
		// Legacy exempts wilderness landings when the cast tile is itself in the wilderness; the
		// capability band carries the same information. A limit-30 teleport is castable in level 25.
		PreparedRoutingAccount account = account(teleport(WILDT, 30));
		assertTrue(search(WILD25, WILDT, AVOID_WILDERNESS, account).reached());
	}

	private static ExactForwardSearch.Result search(int start, int target, SearchRestrictions restrictions)
	{
		return search(start, target, restrictions, account());
	}

	private static ExactForwardSearch.Result search(int start, int target, SearchRestrictions restrictions,
		PreparedRoutingAccount account)
	{
		TargetOverlay overlay = new TargetOverlay(new SiteGraph(routingStatic(), account), emptyCollision(), target);
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(overlay, ReverseLabels.compute(overlay));
		ExactForwardSearch.Result result = ExactForwardSearch.search(overlay, heuristic, start,
			() -> false, true, 1, restrictions);
		ExactForwardSearch.Result oracle = ExactForwardSearch.search(overlay, heuristic, start,
			() -> false, false, 1, restrictions);
		assertEquals("optimised search agrees with the unoptimised one", oracle.reached(), result.reached());
		assertEquals("optimised search agrees with the unoptimised one", oracle.cost(), result.cost());
		return result;
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
			.duration(2).maxWildernessLevel(maxWildernessLevel).build();
	}

	/**
	 * {@code SAFE2 - SAFE - WILD} walk each way, {@code WILDT} and {@code WILD25} reachable only by
	 * transport. Mask bit 2 steps to the next array element and bit 6 to the previous one, so the
	 * tiles need no real-world adjacency.
	 */
	private static RoutingStatic routingStatic()
	{
		int[] tiles = {SAFE2, SAFE, WILD, WILDT, WILD25};
		int n = tiles.length;
		int[] siteOffsets = new int[n + 1], componentMembers = new int[n], sparseOffsets = new int[n + 1];
		for (int i = 0; i < n; i++)
		{
			siteOffsets[i] = i;
			componentMembers[i] = i;
		}
		siteOffsets[n] = n;
		try
		{
			return RoutingStatic.create(tiles, new int[n], new byte[] {4, 68, 64, 0, 0},
				new int[] {-1, -1, -1, -1, -1}, new int[] {-1, -1, -1, -1, -1}, tiles, siteOffsets,
				new int[n], 1, new int[] {0, n}, componentMembers, new int[0], new int[0], new int[0],
				new int[0], n, n, 0, 0, 0, sparseOffsets, new int[0], new int[0]);
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
			{
				return new int[0];
			}

			@Override public boolean isBlocked(int x, int y, int z)
			{
				return false;
			}
		};
	}
}
