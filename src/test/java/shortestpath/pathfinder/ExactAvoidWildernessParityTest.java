package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.ShortestPathConfig;
import shortestpath.requirement.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueRegion;
import shortestpath.pathfinder.exact.RoutingStatic;

/**
 * Exact honours {@code avoidWilderness} and the league's always-blocked region like legacy:
 * nothing may enter the gated area on foot, and a global teleport cast outside the wilderness
 * cannot land inside. The same exemptions apply in both backends: starting inside the gated area
 * may still leave it, and a target inside it lifts the gate entirely.
 */
public class ExactAvoidWildernessParityTest
{
	private static final int LUMBRIDGE = WorldPointUtil.packWorldPoint(3222, 3218, 0);
	private static final int VARROCK = WorldPointUtil.packWorldPoint(3210, 3424, 0);
	/** A safe pocket surrounded by wilderness: on foot it can only be entered through it. */
	private static final int FEROX_ENCLAVE = WorldPointUtil.packWorldPoint(3141, 3630, 0);
	private static final int CHAOS_TEMPLE = WorldPointUtil.packWorldPoint(3231, 3608, 0);
	/** Level-27 wilderness (benchmark early/quest-natural-0006's start). */
	private static final int START_WILDERNESS = WorldPointUtil.packWorldPoint(3051, 3736, 0);
	/** Asgarnia, away from the league's always-blocked Misthalin. */
	private static final int FALADOR = WorldPointUtil.packWorldPoint(2965, 3337, 0);
	/** Deep desert camp: from Falador unreachable without crossing Misthalin or a desert
	 *  transport with item requirements a bare inventory cannot meet. */
	private static final int BEDABIN = WorldPointUtil.packWorldPoint(3181, 3135, 0);
	/** Desert border town: unlocked even while Misthalin is, so transports there stay usable. */
	private static final int AL_KHARID = WorldPointUtil.packWorldPoint(3292, 3184, 0);

	private static RoutingStatic routingStatic;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = new ExactRoutingStaticProvider(
			() -> new CollisionMap(SplitFlagMap.fromResources())).get();
	}

	@Test
	public void wildernessCannotBeEnteredOnFootWhileAvoidingIt()
	{
		// Every walking route to Ferox crosses the wilderness; with no teleports enabled both
		// backends must give up.
		PathfinderConfig config = config(true, false);
		PathfinderResult legacy = legacy(config, LUMBRIDGE, FEROX_ENCLAVE);
		ExactPathfinder exact = exact(config, LUMBRIDGE, FEROX_ENCLAVE);
		assertFalse("legacy reached Ferox without entering the wilderness", legacy.isReached());
		assertFalse("exact entered the wilderness while avoiding it", exact.getResult().isReached());

		// Without the gate the same search walks straight through, and pays the same as legacy.
		config = config(false, false);
		legacy = legacy(config, LUMBRIDGE, FEROX_ENCLAVE);
		exact = exact(config, LUMBRIDGE, FEROX_ENCLAVE);
		assertTrue(legacy.isReached());
		assertTrue(exact.getResult().isReached());
		assertEquals(legacy.getPathCost(), exact.getResult().getPathCost());

		// The route crosses the Wilderness Ditch by transport; the landing step must report
		// which transport produced it, not just the tile it landed on.
		assertTrue("the ditch hop does not report its transport",
			exact.getResult().getPathSteps().stream().anyMatch(
				s -> s.getTransport() != null && s.getTransport().getDestination() == s.getPackedPosition()));
	}

	@Test
	public void wildernessTargetLiftsTheGateLikeLegacy()
	{
		// A target inside the wilderness exempts the search: entering it is the point.
		PathfinderConfig config = config(true, true);
		PathfinderResult legacy = legacy(config, LUMBRIDGE, CHAOS_TEMPLE);
		ExactPathfinder exact = exact(config, LUMBRIDGE, CHAOS_TEMPLE);
		assertTrue(legacy.isReached());
		assertTrue(exact.getResult().isReached());
		assertEquals(legacy.getPathCost(), exact.getResult().getPathCost());
	}

	@Test
	public void startingInWildernessMayLeaveWhileAvoidingIt()
	{
		// The gate only bars entering the wilderness, not walking out of it.
		PathfinderConfig config = config(true, true);
		PathfinderResult legacy = legacy(config, START_WILDERNESS, LUMBRIDGE);
		ExactPathfinder exact = exact(config, START_WILDERNESS, LUMBRIDGE);
		assertTrue(legacy.isReached());
		assertTrue(exact.getResult().isReached());
		assertEquals(legacy.getPathCost(), exact.getResult().getPathCost());
	}

	@Test
	public void ordinaryRoutesNeverTouchTheWildernessWhileAvoidingIt()
	{
		PathfinderConfig config = config(true, true);
		PathfinderResult legacy = legacy(config, LUMBRIDGE, VARROCK);
		ExactPathfinder exact = exact(config, LUMBRIDGE, VARROCK);
		assertTrue(legacy.isReached());
		assertTrue(exact.getResult().isReached());
		for (PathStep step : exact.getPath())
		{
			assertFalse("route enters the wilderness", WildernessChecker.isInWilderness(step.getPackedPosition()));
		}
		assertEquals(legacy.getPathCost(), exact.getResult().getPathCost());
	}

	@Test
	public void leagueBlockedRegionIsGatedLikeLegacy()
	{
		// Falador to the Bedabin camp cannot be routed while Misthalin is the always-blocked
		// region: the walk crosses Misthalin and the desert transports need items this account
		// does not carry, so both backends must give up.
		PathfinderConfig config = config(false, false);
		config.getLeagueModeState().setForTest(true, EnumSet.complementOf(EnumSet.of(LeagueRegion.MISTHALIN)));
		PathfinderResult legacy = legacy(config, FALADOR, BEDABIN);
		ExactPathfinder exact = exact(config, FALADOR, BEDABIN);
		assertFalse("legacy walked into the blocked region", legacy.isReached());
		assertFalse("exact entered the blocked region", exact.getResult().isReached());

		// Transports landing in an unlocked region are unaffected: Lumbridge to Al Kharid
		// crosses the Misthalin/desert gate inside Misthalin-free destination tiles.
		legacy = legacy(config, LUMBRIDGE, AL_KHARID);
		exact = exact(config, LUMBRIDGE, AL_KHARID);
		assertTrue(legacy.isReached());
		assertTrue(exact.getResult().isReached());
		assertEquals(legacy.getPathCost(), exact.getResult().getPathCost());

		// A target inside the blocked region lifts the gate, as in legacy.
		legacy = legacy(config, FALADOR, LUMBRIDGE);
		exact = exact(config, FALADOR, LUMBRIDGE);
		assertTrue(legacy.isReached());
		assertTrue(exact.getResult().isReached());
		assertEquals(legacy.getPathCost(), exact.getResult().getPathCost());
	}

	private static PathfinderResult legacy(PathfinderConfig config, int start, int target)
	{
		Pathfinder pathfinder = new Pathfinder(config, start, Set.of(target));
		pathfinder.run();
		return pathfinder.getResult();
	}

	private static ExactPathfinder exact(PathfinderConfig config, int start, int target)
	{
		ExactPathfinder pathfinder = new ExactPathfinder(config, routingStatic, null, start,
			Set.of(target), null, 1);
		pathfinder.run();
		return pathfinder;
	}

	/** A maxed account with no items and, optionally, wilderness obelisks and minigame teleports. */
	private static PathfinderConfig config(boolean avoidWilderness, boolean teleports)
	{
		Client client = mock(Client.class);
		ShortestPathConfig settings = mock(ShortestPathConfig.class);
		ItemContainer empty = mock(ItemContainer.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		doReturn(new Item[0]).when(empty).getItems();
		doReturn(empty).when(client).getItemContainer(InventoryID.INV);
		doReturn(empty).when(client).getItemContainer(InventoryID.WORN);
		when(settings.calculationCutoff()).thenReturn(500);
		when(settings.currencyThreshold()).thenReturn(10000000);
		when(settings.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(settings.avoidWilderness()).thenReturn(avoidWilderness);
		when(settings.useWildernessObelisks()).thenReturn(teleports);
		when(settings.useTeleportationMinigames()).thenReturn(teleports);

		PathfinderConfig config = new TestPathfinderConfig(client, settings, QuestState.FINISHED, true, true);
		config.refresh();
		return config;
	}
}
