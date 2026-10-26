package shortestpath.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.AStarRoutingStaticProvider;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.pathfinder.Targets;
import shortestpath.sailing.SailingService;
import shortestpath.settings.Settings;

/**
 * Pins the engine that serves plugin-message {@code query} requests and the
 * result a caller receives when the engine cannot even be constructed: an
 * external caller must never see an uncaught client-thread exception, so a
 * construction-time failure has to surface as a finished search carrying
 * {@link PathTerminationReason#BACKEND_FAILURE}.
 *
 * <p>{@link #ctorFailureAnswersBackendFailure} drives with an unstubbed
 * {@link PathfinderConfig} mock on purpose: its {@code getAStarHeuristicWeight()}
 * answers {@code 0}, which the session-aware A* constructor rejects outright — a
 * deterministic constructor failure with a still-valid target set, exactly the
 * edge the {@code failed()} arm exists for. {@link #queriesRunOnTheAStarEngine}
 * stubs just enough config to get the constructor through instead.
 */
public class PathSchedulerQueryTest
{
	private PathScheduler scheduler;
	private QueryResponder responder;
	private PathfinderConfig pathfinderConfig;

	private static int packed(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	@Before
	public void setUp()
	{
		Client client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		// The scheduler defers query submission onto the client thread; run it inline.
		doAnswer(call ->
		{
			((Runnable) call.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(org.mockito.ArgumentMatchers.any(Runnable.class));

		scheduler = new PathScheduler(client, clientThread,
			Settings.wrap(new TestShortestPathConfig()), mock(WorldMapPointManager.class),
			new DebugState());
		responder = mock(QueryResponder.class);
		pathfinderConfig = mock(PathfinderConfig.class);
		scheduler.attach(pathfinderConfig, mock(SailingService.class), responder);
	}

	@After
	public void tearDown()
	{
		scheduler.shutdown();
	}

	private void issueQuery(String id)
	{
		try
		{
			scheduler.queryPath(id, packed(3222, 3218), Targets.at(Set.of(packed(3240, 3225))));
		}
		catch (RuntimeException escaped)
		{
			// An engine constructor must never throw past the client-thread hop —
			// but if it does, the missing result is what the verifies below flag.
		}
	}

	private ActiveSearch awaitQueryResult(String id)
	{
		ArgumentCaptor<ActiveSearch> captor = ArgumentCaptor.forClass(ActiveSearch.class);
		verify(responder, timeout(5000)).postQueryResult(eq(id), captor.capture());
		return captor.getValue();
	}

	@Test
	public void ctorFailureAnswersBackendFailure()
	{
		issueQuery("q1");

		PathfinderResult result = awaitQueryResult("q1").getResult();
		assertNotNull(result);
		assertEquals(PathTerminationReason.BACKEND_FAILURE, result.getTerminationReason());
	}

	/**
	 * Replaces the scheduler's lazily-built routing-statics provider, the same
	 * private seam {@code PluginApiHarness} uses to inject the shared one.
	 */
	private void injectRoutingStatic(AStarRoutingStaticProvider provider)
	{
		try
		{
			Field field = PathScheduler.class.getDeclaredField("aStarRoutingStatic");
			field.setAccessible(true);
			field.set(scheduler, provider);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	@Test
	public void queriesRunOnTheAStarEngine()
	{
		// Just enough config for the A* constructor to run: a positive finite
		// heuristic weight and a map. A failed() stub consults neither, so the
		// verifies below separate a real engine from the stub. The statics
		// provider throws a marker error on first use so the constructed
		// search's run() fails fast — this unit test does not build world data.
		when(pathfinderConfig.getAStarHeuristicWeight()).thenReturn(1.0);
		when(pathfinderConfig.getMap()).thenReturn(mock(CollisionMap.class));
		injectRoutingStatic(new AStarRoutingStaticProvider(() ->
		{
			throw new IllegalStateException("no routing statics in this test");
		}));

		issueQuery("q2");

		// The constructor runs inside the inline client-thread hop, so these
		// already happened: the real engine resolved goals against the map and
		// compiled the routing account — calls the failed() stub never makes.
		verify(pathfinderConfig, atLeastOnce()).getMap();
		verify(pathfinderConfig).prepareAStarRoutingAccount(true, false);

		// A real engine's run() reached the statics provider and folded the
		// marker into the result's "A* backend failed:" message — the failed()
		// stub reports "A* backend unavailable:" from its own ctor failure and
		// never touches the provider.
		PathfinderResult result = awaitQueryResult("q2").getResult();
		assertNotNull(result);
		assertEquals(PathTerminationReason.BACKEND_FAILURE, result.getTerminationReason());
		assertTrue(result.getMessage().contains("no routing statics in this test"));
	}
}
