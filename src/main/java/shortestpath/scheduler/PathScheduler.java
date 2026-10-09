package shortestpath.scheduler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;
import shortestpath.ShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.items.ItemStateService;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.ExactRoutingStaticProvider;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.exact.ExactRoutingSession;

/**
 * Owns the plugin's pathfinding concurrency: the single-worker executor, the mutex that pairs
 * the displayed search with its future and the queued queries, the deferred-tick work queue,
 * and the target/start marker state. The plugin shell forwards user and plugin-message intents
 * here and answers callers through the narrow {@link QueryResponder} seam. This package is a
 * leaf: nothing here may reference the plugin type.
 */
@Slf4j
@Singleton
public class PathScheduler
{
	private static final BufferedImage MARKER_IMAGE = ImageUtil.loadImageResource(PathScheduler.class, "/marker.png");
	private final List<PendingTask> pendingTasks = new ArrayList<>(3);
	private final Object pathfinderMutex = new Object();

	private final Client client;
	private final ClientThread clientThread;
	private final ShortestPathConfig config;
	private final WorldMapPointManager worldMapPointManager;
	private final DebugState debugState;

	// Late-bound by attach(): the shell still owns PathfinderConfig construction and the
	// plugin-message surface, so they cannot ride the constructor.
	private PathfinderConfig pathfinderConfig;
	private ItemStateService itemState;
	private QueryResponder responder;

	private WorldMapPoint marker;
	private int lastLocation = WorldPointUtil.packWorldPoint(0, 0, 0);
	private ExecutorService pathfindingExecutor = Executors.newSingleThreadExecutor();
	private Future<?> pathfinderFuture;
	// Queries in flight or still queued, guarded by pathfinderMutex. Used both to answer callers
	// whose query never ran and to know whether a search is reading the transport availability.
	private final Map<Pathfinder, QueryTask> queries = new LinkedHashMap<>();
	// Volatile because the render thread reads it (through getActiveSearch() and friends)
	// while restarts write it on the client thread under pathfinderMutex.
	private volatile ActiveSearch pathfinder;
	private volatile Pathfinder legacyPathfinder;
	private ExactRoutingStaticProvider exactRoutingStatic;
	private final ExactRoutingSession exactRoutingSession = new ExactRoutingSession();
	private boolean startPointSet = false;

	@Inject
	public PathScheduler(Client client, ClientThread clientThread, ShortestPathConfig config,
		WorldMapPointManager worldMapPointManager, DebugState debugState)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.worldMapPointManager = worldMapPointManager;
		this.debugState = debugState;
	}

	/**
	 * Binds the shell-owned pieces the scheduler needs: the pathfinder config the shell builds in
	 * startUp, the item-state service, and the plugin-message responder. Called before anything
	 * can elicit a search.
	 */
	public void attach(PathfinderConfig pathfinderConfig, ItemStateService itemState, QueryResponder responder)
	{
		this.pathfinderConfig = pathfinderConfig;
		this.itemState = itemState;
		this.responder = responder;
	}

	public void restart(String reason, int start, Set<Integer> requestedEnds, boolean canReviveFiltered)
	{
		// filterLocations edits the set in place, and callers often pass a search's own targets,
		// which the exact backend holds immutable.
		Set<Integer> ends = new HashSet<>(requestedEnds);
		debugState.restartRequested(reason, client.getTickCount());
		List<PathStep> previousPath;
		Set<Integer> previousTargets;
		synchronized (pathfinderMutex)
		{
			previousPath = pathfinder == null ? null : pathfinder.getPath();
			previousTargets = pathfinder == null ? null : Set.copyOf(pathfinder.getTargets());
			if (pathfinder != null)
			{
				pathfinder.cancel();
				// pathfinderFuture is null when a submit threw before the
				// assignment (e.g. executor shutting down mid-restart).
				if (pathfinderFuture != null)
				{
					pathfinderFuture.cancel(true);
				}
				debugState.searchCancelled(pathfinder);
			}
			// The displayed path wins over pending queries: it refreshes the transport
			// availability, which a running query would otherwise read mid-search, and queued
			// queries would delay it on the shared single thread.
			cancelQueries("CANCELLED");

			ensurePathfindingExecutor();
		}

		clientThread.invokeLater(() ->
		{
			try
			{
				startPathfinding(start, ends, canReviveFiltered, previousPath, previousTargets);
			}
			catch (RuntimeException error)
			{
				debugState.clientError(error, client.getTickCount());
				debugState.restartOutcome("failed: " + DebugState.describe(error));
				throw error;
			}
		});
	}

	private void startPathfinding(int start, Set<Integer> ends, boolean canReviveFiltered,
		List<PathStep> previousPath, Set<Integer> previousTargets)
	{
		pathfinderConfig.refresh();
		pathfinderConfig.filterLocations(ends, canReviveFiltered);
		synchronized (pathfinderMutex)
		{
			if (ends.isEmpty())
			{
				debugState.restartOutcome("no targets left after filtering");
				setTarget(WorldPointUtil.UNDEFINED);
			}
			else
			{
				itemState.markBankPickupDirty();
				if (pathfinderConfig.getPathfinderBackend() == PathfinderBackend.EXACT)
				{
					try
					{
						legacyPathfinder = null;
						ExactPathfinder exact = new ExactPathfinder(pathfinderConfig, exactRoutingStatic(),
							exactRoutingSession, start, ends, responder::postPluginMessages);
						// Recalculating towards the same targets: keep the old route drawn until
						// the new one is ready.
						if (ends.equals(previousTargets))
							exact.showUntilDone(previousPath);
						pathfinder = exact;
					}
					catch (RuntimeException error)
					{
						debugState.clientError(error, client.getTickCount());
						legacyPathfinder = null;
						pathfinder = ExactPathfinder.failed(start, ends, responder::postPluginMessages, error);
					}
				}
				else
				{
					legacyPathfinder = new Pathfinder(pathfinderConfig, start, ends, responder::postPluginMessages);
					pathfinder = legacyPathfinder;
				}
				ActiveSearch search = pathfinder;
				debugState.searchStarted(search);
				pathfinderFuture = pathfindingExecutor.submit(() ->
				{
					try
					{
						search.run();
					}
					catch (RuntimeException error)
					{
						debugState.searchError(error, client.getTickCount());
						throw error;
					}
				});
			}
		}
	}

	private void ensurePathfindingExecutor()
	{
		synchronized (pathfinderMutex)
		{
			if (pathfindingExecutor == null)
			{
				ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
				pathfindingExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
			}
		}
	}

	private ExactRoutingStaticProvider exactRoutingStatic()
	{
		synchronized (pathfinderMutex)
		{
			if (exactRoutingStatic == null)
				exactRoutingStatic = new ExactRoutingStaticProvider(pathfinderConfig::getMap);
			return exactRoutingStatic;
		}
	}

	/**
	 * Builds the exact backend's static routing data in the background when the exact backend is
	 * selected, so the first route does not pay for it. The build runs on the pathfinding thread,
	 * so a search submitted meanwhile simply queues behind it; a failed build is remembered by the
	 * provider and reported by that search.
	 */
	public void prepareBackend()
	{
		if (config.pathfinderBackend() != PathfinderBackend.EXACT)
		{
			return;
		}
		ExactRoutingStaticProvider provider = exactRoutingStatic();
		ensurePathfindingExecutor();
		synchronized (pathfinderMutex)
		{
			pathfindingExecutor.submit(() ->
			{
				try
				{
					provider.get();
				}
				catch (RuntimeException error)
				{
					log.warn("Failed to build exact routing data", error);
				}
			});
		}
	}

	public void restart(String reason, int start, Set<Integer> ends)
	{
		restart(reason, start, ends, true);
	}

	public boolean isNearPath(int location)
	{
		List<PathStep> path;
		// The previous route is only on screen until its recalculation finishes; the player is
		// expected to be off it, so it must not trigger yet another recalculation.
		if (pathfinder instanceof ExactPathfinder && ((ExactPathfinder) pathfinder).isShowingProvisionalPath())
		{
			return true;
		}
		boolean sameLocation = lastLocation == location;
		lastLocation = location;
		if (pathfinder == null || (path = pathfinder.getPath()) == null || path.isEmpty() ||
			config.recalculateDistance() < 0 || sameLocation)
		{
			return true;
		}

		for (PathStep pathStep : path)
		{
			if (WorldPointUtil.distanceBetween(location, pathStep.getPackedPosition()) < config.recalculateDistance())
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Finds a path for another plugin without touching the displayed one, and answers with a
	 * {@code result} plugin message carrying the caller's {@code id}.
	 *
	 * <p>Queries share the single pathfinding thread with the displayed path, so the two never
	 * search at the same time. The transport availability is only refreshed when no search is
	 * queued or running, since a search reads it while it runs; otherwise the query uses what the
	 * previous search was started with. A new displayed path cancels pending queries, so a query
	 * never runs concurrently with a refresh triggered by {@code restart} either.
	 */
	public void queryPath(Object id, int start, Set<Integer> targets)
	{
		clientThread.invokeLater(() ->
		{
			synchronized (pathfinderMutex)
			{
				if (pathfindingExecutor == null)
				{
					responder.postQueryFailure(id, "SHUTDOWN");
					return;
				}
				if (queries.isEmpty() && (pathfinder == null || pathfinder.isDone()))
				{
					pathfinderConfig.refresh();
				}
				// Plugin queries always run on the legacy backend, even when the user-facing
				// search opted into a different one: external callers get the proven engine's
				// routes regardless of the experiment the user has enabled.
				Pathfinder query = new Pathfinder(pathfinderConfig, start, targets);
				QueryTask task = new QueryTask(id);
				queries.put(query, task);
				task.future = pathfindingExecutor.submit(() -> runQuery(query, task));
			}
		});
	}

	private void runQuery(Pathfinder query, QueryTask task)
	{
		try
		{
			query.run();
			responder.postQueryResult(task.id, query);
		}
		catch (RuntimeException | Error e)
		{
			responder.postQueryFailure(task.id, "ERROR");
			throw e;
		}
		finally
		{
			synchronized (pathfinderMutex)
			{
				queries.remove(query);
			}
		}
	}

	/**
	 * Cancels every pending query while holding {@link #pathfinderMutex}. Queries still in the
	 * executor queue are dequeued and answered here since their runnable will never execute;
	 * queries already running are signalled and answer for themselves once they stop.
	 */
	private void cancelQueries(String reason)
	{
		for (Iterator<Map.Entry<Pathfinder, QueryTask>> it = queries.entrySet().iterator(); it.hasNext();)
		{
			Map.Entry<Pathfinder, QueryTask> entry = it.next();
			QueryTask task = entry.getValue();
			if (task.future != null && task.future.cancel(false))
			{
				it.remove();
				responder.postQueryFailure(task.id, reason);
			}
			else
			{
				entry.getKey().cancel();
			}
		}
	}

	/**
	 * Cancels pending queries and tears the executor down for plugin shutdown. The executor is
	 * nulled rather than left stopped so a late restart lazily recreates it.
	 */
	public void shutdown()
	{
		synchronized (pathfinderMutex)
		{
			cancelQueries("SHUTDOWN");
			if (pathfindingExecutor != null)
			{
				pathfindingExecutor.shutdownNow();
				pathfindingExecutor = null;
			}
		}
	}

	/**
	 * Queues a config refresh to run on a later client tick, after the triggering state change
	 * has fully settled.
	 */
	public void deferRefresh(int tick)
	{
		pendingTasks.add(new PendingTask(tick, pathfinderConfig::refresh));
	}

	/**
	 * Runs each pending task whose tick has arrived, in submission order.
	 */
	public void drainDueTasks(int tick)
	{
		for (int i = 0; i < pendingTasks.size(); i++)
		{
			if (pendingTasks.get(i).check(tick))
			{
				pendingTasks.remove(i--).run();
			}
		}
	}

	/**
	 * Marks the eligibility snapshot stale; the next config refresh rebuilds it.
	 */
	public void invalidateEligibility()
	{
		pathfinderConfig.invalidateEligibility();
	}

	public void setTarget(int target)
	{
		setTarget(target, false);
	}

	public void setTarget(int target, boolean append)
	{
		Set<Integer> targets = new HashSet<>();
		if (target != WorldPointUtil.UNDEFINED)
		{
			targets.add(target);
		}
		setTargets(targets, append);
	}

	public void setTargets(Set<Integer> targets, boolean append)
	{
		if (targets == null || targets.isEmpty())
		{
			synchronized (pathfinderMutex)
			{
				if (pathfinder != null)
				{
					pathfinder.cancel();
				}
				pathfinder = null;
				legacyPathfinder = null;
			}

			worldMapPointManager.removeIf(x -> x == marker);
			marker = null;
			startPointSet = false;
		}
		else
		{
			Player localPlayer = client.getLocalPlayer();
			if (!startPointSet && localPlayer == null)
			{
				return;
			}
			worldMapPointManager.removeIf(x -> x == marker);
			if (targets.size() == 1)
			{
				marker = new WorldMapPoint(WorldPointUtil.unpackWorldPoint(targets.iterator().next()), MARKER_IMAGE);
				marker.setName("Target");
				marker.setTarget(marker.getWorldPoint());
				marker.setJumpOnClick(true);
				worldMapPointManager.add(marker);
			}

			// fromLocalInstance needs a live player; during a world hop it is
			// null, so fall back to the running search's start and bail when
			// there is nothing to anchor to.
			int start;
			if (localPlayer != null)
			{
				start = WorldPointUtil.fromLocalInstance(client, localPlayer);
				lastLocation = start;
			}
			else if (startPointSet && pathfinder != null)
			{
				start = pathfinder.getStart();
			}
			else
			{
				return;
			}
			if (startPointSet && pathfinder != null)
			{
				start = pathfinder.getStart();
			}
			Set<Integer> destinations = new HashSet<>(targets);
			if (pathfinder != null && append)
			{
				destinations.addAll(pathfinder.getTargets());
			}
			restart(append ? "add target" : "set target", start, destinations, append);
		}
	}

	public void setStart(int start)
	{
		if (pathfinder == null)
		{
			return;
		}
		startPointSet = true;
		restart("set start", start, pathfinder.getTargets());
	}

	public ActiveSearch getActiveSearch()
	{
		return pathfinder;
	}

	/**
	 * The legacy-backend search last submitted, for source-compatible callers of the shell's
	 * {@code getPathfinder()}.
	 */
	public Pathfinder getPathfinder()
	{
		return legacyPathfinder;
	}

	public boolean isStartPointSet()
	{
		return startPointSet;
	}

	/**
	 * Runs {@code action} while holding the scheduler mutex, for shell callers that must pair
	 * their own state writes with the search snapshot.
	 */
	public <T> T withSchedulerLock(Supplier<T> action)
	{
		synchronized (pathfinderMutex)
		{
			return action.get();
		}
	}
}
