package shortestpath.scheduler;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import net.runelite.api.GameState;
import net.runelite.client.events.ConfigChanged;

import shortestpath.ShortestPathConfig;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.items.TestItemChange;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.poh.TestPohChange;
import shortestpath.settings.ConfigChange;
import shortestpath.settings.Effect;
import shortestpath.settings.Settings;
import shortestpath.spirittree.TestTreeChange;

/**
 * Pins the coordinator's channel contract: every declared fact maps to the
 * scheduler verbs the shell used to apply, null facts and missing searches
 * admit nothing, and the restart reasons keep the literals the debug panel's
 * restart log is read against. The {@link PathScheduler} is a Mockito mock —
 * the class stays non-final for exactly this — so the assertions are on the
 * verb surface, not on search behavior.
 */
public class RefreshCoordinatorTest
{
	private PathScheduler scheduler;
	private ShortestPathConfig config;
	private RefreshCoordinator coordinator;
	private ActiveSearch search;

	@Before
	public void setUp()
	{
		scheduler = mock(PathScheduler.class);
		config = mock(ShortestPathConfig.class);
		coordinator = new RefreshCoordinator(scheduler, config);
		search = mock(ActiveSearch.class);
	}

	private void runningSearch(int start, Set<Integer> targets)
	{
		when(scheduler.getActiveSearch()).thenReturn(search);
		when(search.getStart()).thenReturn(start);
		when(search.getTargets()).thenReturn(targets);
	}

	/**
	 * A real {@link ConfigChange} fact, classified by the production
	 * settings table — the coordinator reads the declared effects, so the
	 * test feeds it the same facts the shell would.
	 */
	private static ConfigChange configChange(String key)
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		ConfigChanged event = new ConfigChanged();
		event.setGroup("shortestpath");
		event.setKey(key);
		return settings.onConfigChanged(event);
	}

	// ---------------------------------------------------------------
	// Effect → verb mapping
	// ---------------------------------------------------------------

	@Test
	public void itemsChangedIsInvalidateOnly()
	{
		runningSearch(7, Set.of(9));

		coordinator.itemsChanged(
			TestItemChange.of("container:95", Set.of(Effect.ELIGIBILITY_STALE)));

		verify(scheduler).invalidateEligibility();
		verify(scheduler, never()).restart(anyString(), anyInt(), anySet());
		verify(scheduler, never()).restart(anyString(), anyInt(), anySet(), anyBoolean());
	}

	@Test
	public void itemsChangedWithoutEligibilityStaleDoesNothing()
	{
		coordinator.itemsChanged(TestItemChange.of("x", Set.of(Effect.DISPLAY_ONLY)));

		verifyNoInteractions(scheduler);
	}

	@Test
	public void treeSetChangedRestartsTheRunningSearch()
	{
		runningSearch(42, Set.of(7, 8));

		coordinator.treeSetChanged(
			TestTreeChange.of("varbit:Port Sarim", Set.of(Effect.ROUTE_INVALIDATING)));

		verify(scheduler).restart("spirit tree varbit", 42, Set.of(7, 8));
	}

	@Test
	public void treeSetChangedWithoutRouteInvalidatingDoesNothing()
	{
		coordinator.treeSetChanged(TestTreeChange.of("profile", Set.of(Effect.DISPLAY_ONLY)));

		verifyNoInteractions(scheduler);
	}

	@Test
	public void configChangedRestartsRoutingKeysAndPrepsTheBackend()
	{
		runningSearch(11, Set.of(13));

		coordinator.configChanged(configChange("pathfinderBackend"));

		verify(scheduler).prepareBackend();
		verify(scheduler).restart("config: pathfinderBackend", 11, Set.of(13));
	}

	@Test
	public void configChangedRestartsWithoutBackendPrepForPlainRoutingKeys()
	{
		runningSearch(11, Set.of(13));

		coordinator.configChanged(configChange("avoidWilderness"));

		verify(scheduler).restart("config: avoidWilderness", 11, Set.of(13));
		verify(scheduler, never()).prepareBackend();
	}

	@Test
	public void configChangedDisplayOnlyDoesNothing()
	{
		coordinator.configChanged(configChange("cancelInstead"));

		verifyNoInteractions(scheduler);
	}

	// ---------------------------------------------------------------
	// Null facts and the benign-early guard
	// ---------------------------------------------------------------

	@Test
	public void nullFactsAreIgnoredByEveryProducerChannel()
	{
		coordinator.itemsChanged(null);
		coordinator.treeSetChanged(null);
		coordinator.pohChanged(null);

		verifyNoInteractions(scheduler);
	}

	@Test
	public void restartCapableChannelsNoOpWithoutARunningSearch()
	{
		coordinator.treeSetChanged(
			TestTreeChange.of("profile", Set.of(Effect.ROUTE_INVALIDATING)));
		coordinator.configChanged(configChange("avoidWilderness"));
		coordinator.offRouteTick(5, WorldPointUtil.packWorldPoint(3100, 3200, 0));

		verify(scheduler, never()).restart(anyString(), anyInt(), anySet());
		verify(scheduler, never()).restart(anyString(), anyInt(), anySet(), anyBoolean());
		verify(scheduler, never()).setTarget(anyInt());
	}

	// ---------------------------------------------------------------
	// Reason literals — the restart-log text the fact keys map to
	// ---------------------------------------------------------------

	@Test
	public void treeRestartReasonsKeepTheirLiterals()
	{
		runningSearch(1, Set.of(2));

		coordinator.treeSetChanged(TestTreeChange.of("profile", Set.of(Effect.ROUTE_INVALIDATING)));
		coordinator.treeSetChanged(TestTreeChange.of("menu", Set.of(Effect.ROUTE_INVALIDATING)));
		coordinator.treeSetChanged(TestTreeChange.of("varbit:Etceteria", Set.of(Effect.ROUTE_INVALIDATING)));
		coordinator.treeSetChanged(TestTreeChange.of("unlisted-key", Set.of(Effect.ROUTE_INVALIDATING)));

		verify(scheduler).restart("profile change", 1, Set.of(2));
		verify(scheduler).restart("spirit trees", 1, Set.of(2));
		verify(scheduler).restart("spirit tree varbit", 1, Set.of(2));
		verify(scheduler).restart("unlisted-key", 1, Set.of(2));
	}

	@Test
	public void pohChangedIsLogOnlyAndNeverTouchesTheScheduler()
	{
		// Every POH label feeds the debug/warn log lines, not a scheduler
		// verb — including the contract-violating unhandled effect and the
		// empty-effects fact an identical re-read produces.
		coordinator.pohChanged(TestPohChange.of("profile", Set.of(Effect.DISPLAY_ONLY)));
		coordinator.pohChanged(TestPohChange.of("dialog", Set.of(Effect.DISPLAY_ONLY)));
		coordinator.pohChanged(TestPohChange.of("dialogLine", Set.of(Effect.DISPLAY_ONLY)));
		coordinator.pohChanged(TestPohChange.of("unlisted-key", Set.of(Effect.DISPLAY_ONLY)));
		coordinator.pohChanged(TestPohChange.of("dialog", Set.of(Effect.ELIGIBILITY_STALE)));
		coordinator.pohChanged(TestPohChange.of("dialog", Set.of()));

		verifyNoInteractions(scheduler);
	}

	// ---------------------------------------------------------------
	// Deferred work and tick ordering
	// ---------------------------------------------------------------

	@Test
	public void worldChangedDefersRefreshToTheNextTick()
	{
		coordinator.worldChanged(100);

		verify(scheduler).deferRefresh(101);
		verifyNoMoreInteractions(scheduler);
	}

	@Test
	public void gameTickDrainsDueTasks()
	{
		coordinator.gameTick(55);

		verify(scheduler).drainDueTasks(55);
		verifyNoMoreInteractions(scheduler);
	}

	@Test
	public void loginTransitionDefersRefreshToTheNextTick()
	{
		coordinator.gameStateChanged(GameState.LOGGING_IN, 10);
		coordinator.gameStateChanged(GameState.LOADING, 11);
		coordinator.gameStateChanged(GameState.LOGGED_IN, 12);

		verify(scheduler).deferRefresh(13);
	}

	@Test
	public void otherTransitionsOnlyUpdateTheMemory()
	{
		// Not LOGGING_IN -> LOADING -> LOGGED_IN, so no defer — and the
		// memory update means a following LOGGED_IN is still not a login.
		coordinator.gameStateChanged(GameState.LOGIN_SCREEN, 10);
		coordinator.gameStateChanged(GameState.LOADING, 11);
		coordinator.gameStateChanged(GameState.LOGGED_IN, 12);

		verify(scheduler, never()).deferRefresh(anyInt());
	}

	@Test
	public void offRouteTickDrainsNothingButKeepsTheOffRouteVerbs()
	{
		// The player is off the displayed route and cancel-instead is off:
		// the search restarts from the current position.
		int target = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int location = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		runningSearch(50, Set.of(target));
		when(scheduler.isStartPointSet()).thenReturn(false);
		when(scheduler.isNearPath(location)).thenReturn(false);
		when(config.cancelInstead()).thenReturn(false);

		coordinator.offRouteTick(20, location);

		verify(scheduler).restart("off route", location, Set.of(target));
		verify(scheduler, never()).drainDueTasks(anyInt());
	}

	@Test
	public void drainRunsBeforeTheOffRouteDecision()
	{
		// The tick's drain position precedes the off-route decision, matching
		// the shell's call order — the spirit-tree observation between them is
		// the producer's own declare, not part of either verb.
		int target = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int location = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		runningSearch(50, Set.of(target));
		when(scheduler.isStartPointSet()).thenReturn(false);
		when(scheduler.isNearPath(location)).thenReturn(false);
		when(config.cancelInstead()).thenReturn(false);

		coordinator.gameTick(20);
		coordinator.offRouteTick(20, location);

		InOrder order = inOrder(scheduler);
		order.verify(scheduler).drainDueTasks(20);
		order.verify(scheduler).isNearPath(location);
		order.verify(scheduler).restart("off route", location, Set.of(target));
	}

	@Test
	public void reachingATargetTileClearsThePath()
	{
		int target = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int location = WorldPointUtil.packWorldPoint(3200, 3201, 0);
		runningSearch(50, Set.of(target));
		when(config.reachedDistance()).thenReturn(10);

		coordinator.offRouteTick(20, location);

		verify(scheduler).setTarget(WorldPointUtil.UNDEFINED);
		verify(scheduler, never()).restart(anyString(), anyInt(), anySet(), anyBoolean());
	}

	@Test
	public void cancelInsteadClearsRatherThanRestarts()
	{
		int target = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int location = WorldPointUtil.packWorldPoint(1000, 1000, 0);
		runningSearch(50, Set.of(target));
		when(scheduler.isStartPointSet()).thenReturn(false);
		when(scheduler.isNearPath(location)).thenReturn(false);
		when(config.cancelInstead()).thenReturn(true);

		coordinator.offRouteTick(20, location);

		verify(scheduler).setTarget(WorldPointUtil.UNDEFINED);
		verify(scheduler, never()).restart(anyString(), anyInt(), anySet(), anyBoolean());
	}

	// ---------------------------------------------------------------
	// Plugin-message target requests
	// ---------------------------------------------------------------

	@Test
	public void targetRequestRestartsFromTheMessageStart()
	{
		coordinator.targetRequest(5, Set.of(9));

		verify(scheduler).restart("plugin message", 5, Set.of(9), false);
	}

	@Test
	public void targetRequestWithEmptyTargetsReusesTheRunningOnes()
	{
		runningSearch(5, Set.of(3, 4));

		coordinator.targetRequest(5, Set.of());

		verify(scheduler).restart("plugin message", 5, Set.of(3, 4), true);
	}
}
