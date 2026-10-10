package shortestpath.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;

import shortestpath.pathfinder.ActiveSearch;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.sailing.SailingService;
import shortestpath.sailing.SailingState;
import shortestpath.settings.Settings;

/**
 * Pins the render seam's delegation contract: every read flows through to
 * the published source — the scheduler's active search and per-dispatch
 * pivot volatiles, and the sailing service's snapshot — with no caching or
 * reinterpretation in between.
 */
public class PathRenderStateTest
{
	private PathScheduler scheduler;
	private SailingService sailing;
	private Settings settings;
	private PathRenderState renderState;
	private ActiveSearch search;

	@Before
	public void setUp()
	{
		scheduler = mock(PathScheduler.class);
		sailing = mock(SailingService.class);
		settings = mock(Settings.class);
		renderState = new PathRenderState(scheduler, sailing, settings);
		search = mock(ActiveSearch.class);
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
}
