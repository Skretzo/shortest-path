package shortestpath.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;

import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.sailing.SailingService;
import shortestpath.sailing.SailingState;
import shortestpath.settings.Settings;

/**
 * Pins the render seam's delegation contract: every read flows through to
 * the published source — the scheduler's active search and per-dispatch
 * pivot volatiles, and the sailing service's snapshot — with no caching or
 * reinterpretation in between. The unreachable-verdict cases pin the full
 * truth table: the null/not-done/null-path/empty-target guards, the sailing
 * reached branch, the walking distance rule, and the empty-path start
 * fallback.
 */
public class PathRenderStateTest
{
	/**
	 * The display threshold the unreachable verdict compares against —
	 * {@link TestShortestPathConfig} ships it at 2 tiles.
	 */
	private static final int UNREACHABLE_DISTANCE = 2;

	private PathScheduler scheduler;
	private SailingService sailing;
	private Settings settings;
	private PathRenderState renderState;
	private PathRenderState unreachableState;
	private ActiveSearch search;

	@Before
	public void setUp()
	{
		scheduler = mock(PathScheduler.class);
		sailing = mock(SailingService.class);
		settings = mock(Settings.class);
		renderState = new PathRenderState(scheduler, sailing, settings);
		// DisplayView is an immutable final value, so the unreachable cases
		// run against a real settings view rather than a mock.
		unreachableState = new PathRenderState(scheduler, sailing,
			Settings.wrap(new TestShortestPathConfig()));
		search = mock(ActiveSearch.class);
	}

	private static int packed(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	private static PathStep step(int x, int y)
	{
		return new PathStep(packed(x, y), null);
	}

	/**
	 * Stubs a done search with the given path, targets, and sailing flag —
	 * the state the unreachable verdict reads past its guards.
	 */
	private void doneSearch(List<PathStep> path, Set<Integer> targets, boolean sailingSearch)
	{
		when(scheduler.getActiveSearch()).thenReturn(search);
		when(search.isDone()).thenReturn(true);
		when(search.getPath()).thenReturn(path);
		when(search.getTargets()).thenReturn(targets);
		when(search.isSailing()).thenReturn(sailingSearch);
	}

	@Test
	public void isSailingIsFalseWithNoActiveSearch()
	{
		assertFalse(renderState.isSailing());
	}

	@Test
	public void isSailingDelegatesToTheActiveSearch()
	{
		when(scheduler.getActiveSearch()).thenReturn(search);
		when(search.isSailing()).thenReturn(false);
		assertFalse(renderState.isSailing());

		when(search.isSailing()).thenReturn(true);
		assertTrue(renderState.isSailing());
	}

	@Test
	public void sailingPivotsDelegateToTheScheduler()
	{
		when(scheduler.getSailingPivotX()).thenReturn(-64);
		when(scheduler.getSailingPivotY()).thenReturn(32);

		assertEquals(-64, renderState.getSailingPivotX());
		assertEquals(32, renderState.getSailingPivotY());
	}

	@Test
	public void isOnSailingBoatReadsThePublishedSnapshot()
	{
		// The never-refreshed snapshot is EMPTY: not aboard.
		when(sailing.getState()).thenReturn(SailingState.EMPTY);

		assertFalse(renderState.isOnSailingBoat());
	}

	@Test
	public void isOnSailingBoatReflectsARefresh()
	{
		// A detached service still captures the varbit side of a refresh, so
		// it can publish an aboard snapshot without a client — the seam then
		// reads that same publication.
		SailingService live = SailingService.forTesting();
		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.gameState()).thenReturn(GameState.LOGGED_IN);
		when(source.varbit(VarbitID.SAILING_BOARDED_BOAT)).thenReturn(1);
		live.refresh(source);

		assertTrue(new PathRenderState(scheduler, live, settings).isOnSailingBoat());
	}

	// ---------------------------------------------------------------
	// isPathUnreachable — guards
	// ---------------------------------------------------------------

	@Test
	public void unreachableIsFalseWithNoActiveSearch()
	{
		assertFalse(unreachableState.isPathUnreachable());
	}

	@Test
	public void unreachableIsFalseWhileTheSearchRuns()
	{
		when(scheduler.getActiveSearch()).thenReturn(search);
		when(search.isDone()).thenReturn(false);

		assertFalse(unreachableState.isPathUnreachable());
	}

	@Test
	public void unreachableIsFalseWithNoPath()
	{
		doneSearch(null, Set.of(packed(9, 9)), false);

		assertFalse(unreachableState.isPathUnreachable());
	}

	@Test
	public void unreachableIsFalseWithNoTargets()
	{
		doneSearch(List.of(step(5, 5)), Set.of(), false);

		assertFalse(unreachableState.isPathUnreachable());
	}

	// ---------------------------------------------------------------
	// isPathUnreachable — the sailing reached verdict
	// ---------------------------------------------------------------

	@Test
	public void sailingSearchIsReachableWhenTheResultReached()
	{
		doneSearch(List.of(step(5, 5)), Set.of(packed(20, 20)), true);
		PathfinderResult result = mock(PathfinderResult.class);
		when(search.getResult()).thenReturn(result);
		when(result.isReached()).thenReturn(true);

		assertFalse(unreachableState.isPathUnreachable());
	}

	@Test
	public void sailingSearchIsUnreachableWhenTheResultDidNotReach()
	{
		doneSearch(List.of(step(5, 5)), Set.of(packed(20, 20)), true);
		PathfinderResult result = mock(PathfinderResult.class);
		when(search.getResult()).thenReturn(result);
		when(result.isReached()).thenReturn(false);

		assertTrue(unreachableState.isPathUnreachable());
	}

	@Test
	public void sailingSearchIsNotUnreachableWithoutAResult()
	{
		doneSearch(List.of(step(5, 5)), Set.of(packed(20, 20)), true);
		when(search.getResult()).thenReturn(null);

		assertFalse(unreachableState.isPathUnreachable());
	}

	// ---------------------------------------------------------------
	// isPathUnreachable — the walking distance rule
	// ---------------------------------------------------------------

	@Test
	public void walkingSearchIsReachableWithinTheDistanceThreshold()
	{
		// The path ends exactly at the threshold — only farther is unreachable.
		doneSearch(List.of(step(10, 10)), Set.of(packed(10 + UNREACHABLE_DISTANCE, 10)), false);

		assertFalse(unreachableState.isPathUnreachable());
	}

	@Test
	public void walkingSearchIsUnreachableBeyondTheDistanceThreshold()
	{
		doneSearch(List.of(step(10, 10)), Set.of(packed(10 + UNREACHABLE_DISTANCE + 1, 10)), false);

		assertTrue(unreachableState.isPathUnreachable());
	}

	@Test
	public void walkingSearchFallsBackToTheStartForAnEmptyPath()
	{
		// No path steps, so the endpoint is the search's start — placed far
		// from the target so the verdict flips only when the fallback fires.
		doneSearch(List.of(), Set.of(packed(20, 20)), false);
		when(search.getStart()).thenReturn(packed(0, 0));

		assertTrue(unreachableState.isPathUnreachable());
		verify(search).getStart();
	}
}
