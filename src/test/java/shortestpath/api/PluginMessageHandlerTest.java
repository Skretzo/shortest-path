package shortestpath.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.pathfinder.Targets;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Settings;
import shortestpath.transport.Transport;

/**
 * Pins the handler's dispatch and wire contract without a client: the
 * namespace gate, each inbound action's service call, the parsers'
 * valid/invalid edges, and both outbound payload shapes. Settings is a real
 * wrapped stub config so the override write/echo paths run for real;
 * {@code clientThread.invokeLater} is answered inline so posted messages can
 * be captured synchronously.
 */
public class PluginMessageHandlerTest
{
	private Client client;
	private ClientThread clientThread;
	private EventBus eventBus;
	private PathScheduler scheduler;
	private RefreshCoordinator coordinator;
	private Settings settings;
	private PathAnnotator annotator;
	private PluginMessageHandler handler;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		eventBus = mock(EventBus.class);
		scheduler = mock(PathScheduler.class);
		coordinator = mock(RefreshCoordinator.class);
		settings = Settings.wrap(new TestShortestPathConfig());
		annotator = mock(PathAnnotator.class);
		handler = new PluginMessageHandler(client, clientThread, eventBus, scheduler,
			coordinator, settings, annotator);

		// Run the client-thread hop inline so the event-bus post happens inside the test call.
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		// Execute the locked body inline — the assertion is on what the body did.
		when(scheduler.withSchedulerLock(any())).thenAnswer(invocation ->
			((Supplier<?>) invocation.getArgument(0)).get());
	}

	private static int packed(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	private PluginMessage singlePost()
	{
		ArgumentCaptor<PluginMessage> captor = ArgumentCaptor.forClass(PluginMessage.class);
		verify(eventBus).post(captor.capture());
		return captor.getValue();
	}

	@Test
	public void outsideNamespaceIsIgnored()
	{
		handler.onPluginMessage(new PluginMessage("other-plugin", "path",
			Map.of("start", packed(1, 1), "target", Set.of(packed(2, 2)))));

		verifyNoInteractions(coordinator, scheduler, eventBus);
	}

	@Test
	public void pathDispatchRequestsTarget()
	{
		int start = packed(3200, 3200);
		int target = packed(3300, 3300);

		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "path",
			Map.of("start", start, "target", Set.of(target))));

		verify(coordinator).targetRequest(start, Targets.at(Set.of(target)));
	}

	@Test
	public void pathDispatchAppliesConfigOverrides()
	{
		int start = packed(3200, 3200);
		int target = packed(3300, 3300);
		Map<String, Object> overrides = Map.of("colourPath", "#ff0000");

		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "path",
			Map.of("start", start, "target", Set.of(target), "config", overrides)));

		assertEquals(overrides, settings.rawOverrides());
		verify(coordinator).targetRequest(start, Targets.at(Set.of(target)));
	}

	@Test
	public void pathDispatchWithoutStartOrTargetDoesNothing()
	{
		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "path", Map.of()));

		verify(coordinator, never()).targetRequest(anyInt(), any());
	}

	@Test
	public void pathDispatchDefaultsStartToLocalPlayer()
	{
		int target = packed(3300, 3300);
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3100, 3500, 0));
		when(client.getLocalPlayer()).thenReturn(player);

		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "path",
			Map.of("target", Set.of(target))));

		verify(coordinator).targetRequest(packed(3100, 3500), Targets.at(Set.of(target)));
	}

	@Test
	public void queryDispatchRunsQuery()
	{
		int start = packed(3200, 3200);
		int target = packed(3300, 3300);

		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "query",
			Map.of("id", "q1", "start", start, "target", Set.of(target))));

		verify(scheduler).queryPath("q1", start, Targets.at(Set.of(target)));
	}

	@Test
	public void queryDispatchRejectsInvalidTargets()
	{
		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "query",
			Map.of("id", "q1", "start", packed(3200, 3200),
				"target", Set.of(WorldPointUtil.UNDEFINED))));

		verify(scheduler, never()).queryPath(any(), anyInt(), any());
		PluginMessage posted = singlePost();
		assertEquals("result", posted.getName());
		assertEquals("q1", posted.getData().get("id"));
		assertEquals(false, posted.getData().get("reached"));
		assertEquals("INVALID", posted.getData().get("reason"));
	}

	@Test
	public void clearDispatchClearsOverridesAndTarget()
	{
		settings.applyOverrides(Map.of("colourPath", "#ff0000"));

		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "clear", Map.of()));

		assertTrue(settings.rawOverrides().isEmpty());
		verify(scheduler).withSchedulerLock(any());
		verify(scheduler).setTarget(WorldPointUtil.UNDEFINED);
	}

	@Test
	public void getTargetPostsCurrentTargetSnapshot()
	{
		int start = packed(3000, 3000);
		Set<Integer> targets = Set.of(packed(3100, 3100), packed(3200, 3200));
		ActiveSearch search = mock(ActiveSearch.class);
		when(search.getStart()).thenReturn(start);
		when(search.getTargets()).thenReturn(Targets.at(targets));
		when(scheduler.getActiveSearch()).thenReturn(search);
		Map<String, Object> overrides = Map.of("colourPath", "#ff0000");
		settings.applyOverrides(overrides);

		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "getTarget",
			Map.of("id", "g7")));

		PluginMessage posted = singlePost();
		assertEquals("currentTarget", posted.getName());
		assertEquals("g7", posted.getData().get("id"));
		assertEquals(true, posted.getData().get("set"));
		assertEquals(new WorldPoint(3000, 3000, 0), posted.getData().get("start"));
		assertEquals(2, ((List<?>) posted.getData().get("target")).size());
		assertEquals(overrides, posted.getData().get("config"));
	}

	@Test
	public void getTargetWithoutPathPostsUnset()
	{
		handler.onPluginMessage(new PluginMessage(Settings.CONFIG_GROUP, "getTarget",
			Map.of("id", "g8")));

		PluginMessage posted = singlePost();
		assertEquals("currentTarget", posted.getName());
		assertEquals("g8", posted.getData().get("id"));
		assertEquals(false, posted.getData().get("set"));
		assertFalse(posted.getData().containsKey("start"));
		assertFalse(posted.getData().containsKey("target"));
		assertFalse(posted.getData().containsKey("config"));
	}

	@Test
	public void postQueryResultSerializesResultShape()
	{
		PathStep first = new PathStep(packed(1, 1), null);
		PathStep second = new PathStep(packed(2, 2), null);
		PathfinderResult result = new PathfinderResult(packed(1, 1), packed(9, 9), true,
			List.of(first, second), packed(9, 9), 42, 7, 3, 5L,
			PathTerminationReason.TARGET_REACHED);
		Pathfinder query = mock(Pathfinder.class);
		when(query.getResult()).thenReturn(result);
		Transport transport = mock(Transport.class);
		when(transport.getObjectInfo()).thenReturn("obj-info");
		when(annotator.transportsForEdge(first, second)).thenReturn(Set.of(transport));
		when(annotator.formatTransportDisplay(transport)).thenReturn("display-info");

		handler.postQueryResult("r1", query);

		PluginMessage posted = singlePost();
		assertEquals("result", posted.getName());
		assertEquals("r1", posted.getData().get("id"));
		assertEquals(true, posted.getData().get("reached"));
		assertEquals(2, ((List<?>) posted.getData().get("path")).size());
		assertEquals(42, posted.getData().get("cost"));
		assertEquals("TARGET_REACHED", posted.getData().get("reason"));
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> transports = (List<Map<String, Object>>) posted.getData().get("transports");
		assertEquals(1, transports.size());
		Map<String, Object> edge = transports.get(0);
		assertEquals(new WorldPoint(1, 1, 0), edge.get("origin"));
		assertEquals(new WorldPoint(2, 2, 0), edge.get("destination"));
		assertEquals("obj-info", edge.get("objectInfo"));
		assertEquals("display-info", edge.get("displayInfo"));
	}

	@Test
	public void postQueryResultWithoutResultPostsError()
	{
		Pathfinder query = mock(Pathfinder.class);
		when(query.getResult()).thenReturn(null);

		handler.postQueryResult("r2", query);

		PluginMessage posted = singlePost();
		assertEquals("result", posted.getName());
		assertEquals("r2", posted.getData().get("id"));
		assertEquals(false, posted.getData().get("reached"));
		assertEquals("ERROR", posted.getData().get("reason"));
		assertTrue(((List<?>) posted.getData().get("path")).isEmpty());
		assertTrue(((List<?>) posted.getData().get("transports")).isEmpty());
	}

	@Test
	public void postQueryFailurePostsReason()
	{
		handler.postQueryFailure("f1", "SHUTDOWN");

		PluginMessage posted = singlePost();
		assertEquals("result", posted.getName());
		assertEquals("f1", posted.getData().get("id"));
		assertEquals(false, posted.getData().get("reached"));
		assertEquals("SHUTDOWN", posted.getData().get("reason"));
	}

	@Test
	public void postPluginMessagesPostsTransportsParallelArrays()
	{
		PathStep first = new PathStep(packed(1, 1), null);
		PathStep second = new PathStep(packed(2, 2), null);
		PathStep third = new PathStep(packed(3, 3), null);
		ActiveSearch search = mock(ActiveSearch.class);
		when(search.getPath()).thenReturn(List.of(first, second, third));
		when(scheduler.getActiveSearch()).thenReturn(search);
		settings.applyOverrides(Map.of("postTransports", true));
		Transport transport = mock(Transport.class);
		when(transport.getObjectInfo()).thenReturn("obj");
		when(annotator.transportsForEdge(any(), any())).thenReturn(Set.of(transport));
		when(annotator.formatTransportDisplay(transport)).thenReturn("disp");

		handler.postPluginMessages();

		PluginMessage posted = singlePost();
		assertEquals("transports", posted.getName());
		assertEquals(List.of(new WorldPoint(1, 1, 0), new WorldPoint(2, 2, 0)),
			posted.getData().get("origin"));
		assertEquals(List.of(new WorldPoint(2, 2, 0), new WorldPoint(3, 3, 0)),
			posted.getData().get("destination"));
		assertEquals(List.of("obj", "obj"), posted.getData().get("objectInfo"));
		assertEquals(List.of("disp", "disp"), posted.getData().get("displayInfo"));
	}

	@Test
	public void postPluginMessagesHonoursPostTransportsToggle()
	{
		ActiveSearch search = mock(ActiveSearch.class);
		when(scheduler.getActiveSearch()).thenReturn(search);

		handler.postPluginMessages();

		verify(eventBus, never()).post(any());
	}

	@Test
	public void postPluginMessagesWithoutSearchIsNoOp()
	{
		handler.postPluginMessages();

		verify(eventBus, never()).post(any());
	}
}
