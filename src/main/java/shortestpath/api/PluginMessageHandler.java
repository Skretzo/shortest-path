package shortestpath.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.scheduler.QueryResponder;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Settings;
import shortestpath.transport.Transport;

/**
 * Owns the plugin's external plugin-message surface: the dispatch of incoming
 * {@code path}/{@code query}/{@code clear}/{@code getTarget} messages and the
 * serialization of the outgoing {@code result}/{@code currentTarget}/
 * {@code transports} responses. Implements the scheduler's narrow
 * {@link QueryResponder} callback seam so query completions can be answered
 * without the scheduler knowing the wire contract.
 *
 * <p>The annotator/serializer seam is the documented injection point for any
 * future verdict channel — none is wired today; the payload shapes are the
 * live external contract and stay byte-identical.
 *
 * <p>Thread affinity is load-bearing: the serializers that answer scheduler
 * callbacks hop to the client thread via {@code clientThread.invokeLater},
 * while {@link #postPluginMessages()} posts directly because it already runs
 * on the pathfinder completion path.
 *
 * <p>This package is a leaf: nothing here may reference the plugin type.
 */
@Singleton
public class PluginMessageHandler implements QueryResponder
{
	private static final String PLUGIN_MESSAGE_PATH = "path";
	private static final String PLUGIN_MESSAGE_CLEAR = "clear";
	private static final String PLUGIN_MESSAGE_START = "start";
	private static final String PLUGIN_MESSAGE_TARGET = "target";
	private static final String PLUGIN_MESSAGE_CONFIG_OVERRIDE = "config";
	private static final String PLUGIN_MESSAGE_TRANSPORTS = "transports";
	private static final String PLUGIN_MESSAGE_QUERY = "query";
	private static final String PLUGIN_MESSAGE_RESULT = "result";
	private static final String PLUGIN_MESSAGE_GET_TARGET = "getTarget";
	private static final String PLUGIN_MESSAGE_CURRENT_TARGET = "currentTarget";
	private static final String PLUGIN_MESSAGE_ID = "id";

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final PathScheduler scheduler;
	private final RefreshCoordinator coordinator;
	private final Settings settings;
	private final PathAnnotator annotator;

	@Inject
	public PluginMessageHandler(Client client, ClientThread clientThread, EventBus eventBus,
		PathScheduler scheduler, RefreshCoordinator coordinator, Settings settings,
		PathAnnotator annotator)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.scheduler = scheduler;
		this.coordinator = coordinator;
		this.settings = settings;
		this.annotator = annotator;
	}

	public void onPluginMessage(PluginMessage event)
	{
		if (!Settings.CONFIG_GROUP.equals(event.getNamespace()))
		{
			return;
		}

		String action = event.getName();
		if (PLUGIN_MESSAGE_PATH.equals(action))
		{
			Map<String, Object> data = event.getData();
			Object objStart = data.getOrDefault(PLUGIN_MESSAGE_START, null);
			Object objTarget = data.getOrDefault(PLUGIN_MESSAGE_TARGET, null);
			Object objConfigOverride = data.getOrDefault(PLUGIN_MESSAGE_CONFIG_OVERRIDE, null);

			@SuppressWarnings("unchecked")
			Map<String, Object> payloadOverrides = (objConfigOverride instanceof Map<?, ?>) ? ((Map<String, Object>) objConfigOverride) : null;
			if (payloadOverrides != null && !payloadOverrides.isEmpty())
			{
				scheduler.withSchedulerLock(() ->
				{
					settings.applyOverrides(payloadOverrides);
					return null;
				});
			}

			if (objStart == null && objTarget == null)
			{
				return;
			}

			int start = parseStart(objStart);
			Set<Integer> targets = parseTargets(objTarget);
			if (start == WorldPointUtil.UNDEFINED || targets == null)
			{
				return;
			}

			coordinator.targetRequest(start, targets);
		}
		else if (PLUGIN_MESSAGE_QUERY.equals(action))
		{
			Map<String, Object> data = event.getData();
			if (data == null)
			{
				return;
			}
			Object id = data.get(PLUGIN_MESSAGE_ID);
			int start = parseStart(data.get(PLUGIN_MESSAGE_START));
			Set<Integer> targets = parseTargets(data.get(PLUGIN_MESSAGE_TARGET));
			if (start == WorldPointUtil.UNDEFINED || targets == null || targets.isEmpty())
			{
				postQueryFailure(id, "INVALID");
				return;
			}
			scheduler.queryPath(id, start, targets);
		}
		else if (PLUGIN_MESSAGE_GET_TARGET.equals(action))
		{
			Map<String, Object> data = event.getData();
			postCurrentTarget(data == null ? null : data.get(PLUGIN_MESSAGE_ID));
		}
		else if (PLUGIN_MESSAGE_CLEAR.equals(action))
		{
			scheduler.withSchedulerLock(() ->
			{
				settings.clearOverrides();
				return null;
			});
			scheduler.setTarget(WorldPointUtil.UNDEFINED);
		}
	}

	/**
	 * The packed start of a plugin message path, defaulting to the player's location.
	 * {@link WorldPointUtil#UNDEFINED} when there is neither.
	 */
	private int parseStart(Object objStart)
	{
		int start = (objStart instanceof WorldPoint) ? WorldPointUtil.packWorldPoint((WorldPoint) objStart)
			: ((objStart instanceof Integer) ? ((int) objStart) : WorldPointUtil.UNDEFINED);
		if (start == WorldPointUtil.UNDEFINED && client.getLocalPlayer() != null)
		{
			start = WorldPointUtil.packWorldPoint(client.getLocalPlayer().getWorldLocation());
		}
		return start;
	}

	/**
	 * The packed targets of a plugin message: a WorldPoint, a packed Integer, or a Set of either.
	 * Empty when none were given, and null when any of them is invalid.
	 */
	private static Set<Integer> parseTargets(Object objTarget)
	{
		Set<Integer> targets = new HashSet<>();
		if (objTarget instanceof Integer)
		{
			int packedPoint = (Integer) objTarget;
			if (packedPoint == WorldPointUtil.UNDEFINED)
			{
				return null;
			}
			targets.add(packedPoint);
		}
		else if (objTarget instanceof WorldPoint)
		{
			int packedPoint = WorldPointUtil.packWorldPoint((WorldPoint) objTarget);
			if (packedPoint == WorldPointUtil.UNDEFINED)
			{
				return null;
			}
			targets.add(packedPoint);
		}
		else if (objTarget instanceof Set<?>)
		{
			for (Object obj : (Set<?>) objTarget)
			{
				int packedPoint = WorldPointUtil.UNDEFINED;
				if (obj instanceof Integer)
				{
					packedPoint = (Integer) obj;
				}
				else if (obj instanceof WorldPoint)
				{
					packedPoint = WorldPointUtil.packWorldPoint((WorldPoint) obj);
				}
				if (packedPoint == WorldPointUtil.UNDEFINED)
				{
					return null;
				}
				targets.add(packedPoint);
			}
		}
		return targets;
	}

	@Override
	public void postQueryResult(Object id, Pathfinder query)
	{
		PathfinderResult result = query.getResult();
		if (result == null)
		{
			postQueryFailure(id, "ERROR");
			return;
		}

		List<PathStep> steps = result.getPathSteps();
		List<WorldPoint> path = new ArrayList<>(steps.size());
		List<Map<String, Object>> transports = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++)
		{
			path.add(WorldPointUtil.unpackWorldPoint(steps.get(i).getPackedPosition()));
			if (i > 0)
			{
				for (Transport transport : annotator.transportsForEdge(steps.get(i - 1), steps.get(i)))
				{
					Map<String, Object> edge = new HashMap<>();
					edge.put("origin", WorldPointUtil.unpackWorldPoint(steps.get(i - 1).getPackedPosition()));
					edge.put("destination", WorldPointUtil.unpackWorldPoint(steps.get(i).getPackedPosition()));
					edge.put("objectInfo", transport.getObjectInfo());
					edge.put("displayInfo", annotator.formatTransportDisplay(transport));
					transports.add(edge);
				}
			}
		}

		Map<String, Object> data = new HashMap<>();
		data.put(PLUGIN_MESSAGE_ID, id);
		data.put("reached", result.isReached());
		data.put(PLUGIN_MESSAGE_START, WorldPointUtil.unpackWorldPoint(result.getStart()));
		data.put(PLUGIN_MESSAGE_TARGET, WorldPointUtil.unpackWorldPoint(result.getTarget()));
		data.put("path", path);
		data.put(PLUGIN_MESSAGE_TRANSPORTS, transports);
		data.put("closest", WorldPointUtil.unpackWorldPoint(result.getClosestReachedPoint()));
		data.put("cost", result.getPathCost());
		PathTerminationReason reason = result.getTerminationReason();
		data.put("reason", reason == null ? null : reason.name());
		clientThread.invokeLater(() -> eventBus.post(new PluginMessage(Settings.CONFIG_GROUP, PLUGIN_MESSAGE_RESULT, data)));
	}

	/**
	 * Answers a query that produced no result: a malformed message, a plugin shutdown, a
	 * cancelled search, or a failure while running.
	 */
	@Override
	public void postQueryFailure(Object id, String reason)
	{
		Map<String, Object> data = new HashMap<>();
		data.put(PLUGIN_MESSAGE_ID, id);
		data.put("reached", false);
		data.put("path", List.of());
		data.put(PLUGIN_MESSAGE_TRANSPORTS, List.of());
		data.put("reason", reason);
		clientThread.invokeLater(() -> eventBus.post(new PluginMessage(Settings.CONFIG_GROUP, PLUGIN_MESSAGE_RESULT, data)));
	}

	/**
	 * Answers a {@code getTarget} plugin message with a {@code currentTarget} message carrying
	 * the caller's {@code id} and a snapshot of the displayed path's start and targets. This is
	 * a read-only request: it launches no pathfinding work and mutates no state.
	 *
	 * <p>The start/targets/overrides triple is read while holding the scheduler's lock so a
	 * request arriving mid-search sees one consistent snapshot rather than a torn pair; the
	 * lock is released before the response hops to the client thread. When no path is set the
	 * response carries {@code set=false} and no start or target fields.
	 */
	private void postCurrentTarget(Object id)
	{
		Map<String, Object> data = scheduler.withSchedulerLock(() ->
		{
			ActiveSearch pathfinder = scheduler.getActiveSearch();
			int start = (pathfinder == null) ? WorldPointUtil.UNDEFINED : pathfinder.getStart();
			Set<Integer> targets = (pathfinder == null) ? Set.of() : new HashSet<>(pathfinder.getTargets());
			Map<String, Object> overrides =
				settings.rawOverrides().isEmpty() ? null : new HashMap<>(settings.rawOverrides());

			Map<String, Object> message = new HashMap<>();
			message.put(PLUGIN_MESSAGE_ID, id);
			message.put("set", !targets.isEmpty());
			if (!targets.isEmpty())
			{
				message.put(PLUGIN_MESSAGE_START,
					start == WorldPointUtil.UNDEFINED ? null : WorldPointUtil.unpackWorldPoint(start));
				List<WorldPoint> unpackedTargets = new ArrayList<>(targets.size());
				for (int target : targets)
				{
					unpackedTargets.add(WorldPointUtil.unpackWorldPoint(target));
				}
				message.put(PLUGIN_MESSAGE_TARGET, unpackedTargets);
			}
			if (overrides != null)
			{
				message.put(PLUGIN_MESSAGE_CONFIG_OVERRIDE, overrides);
			}
			return message;
		});
		clientThread.invokeLater(() -> eventBus.post(new PluginMessage(Settings.CONFIG_GROUP, PLUGIN_MESSAGE_CURRENT_TARGET, data)));
	}

	@Override
	public void postPluginMessages()
	{
		ActiveSearch pathfinder = scheduler.getActiveSearch();
		if (pathfinder == null)
		{
			return;
		}
		if (settings.effective().postTransports())
		{
			Map<String, Object> data = new HashMap<>();
			List<WorldPoint> transportOrigins = new ArrayList<>();
			List<WorldPoint> transportDestinations = new ArrayList<>();
			List<String> transportObjectInfos = new ArrayList<>();
			List<String> transportDisplayInfos = new ArrayList<>();
			List<PathStep> currentPath = pathfinder.getPath();
			for (int i = 1; i < currentPath.size(); i++)
			{
				PathStep currentStep = currentPath.get(i - 1);
				PathStep nextStep = currentPath.get(i);
				for (Transport transport : annotator.transportsForEdge(currentStep, nextStep))
				{
					transportOrigins.add(WorldPointUtil.unpackWorldPoint(currentStep.getPackedPosition()));
					transportDestinations.add(WorldPointUtil.unpackWorldPoint(nextStep.getPackedPosition()));
					transportObjectInfos.add(transport.getObjectInfo());
					transportDisplayInfos.add(annotator.formatTransportDisplay(transport));
				}
			}
			data.put("origin", transportOrigins);
			data.put("destination", transportDestinations);
			data.put("objectInfo", transportObjectInfos);
			data.put("displayInfo", transportDisplayInfos);
			eventBus.post(new PluginMessage(Settings.CONFIG_GROUP, PLUGIN_MESSAGE_TRANSPORTS, data));
		}
	}
}
