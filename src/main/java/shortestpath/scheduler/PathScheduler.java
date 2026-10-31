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
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;
import shortestpath.settings.Settings;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.AStarPathfinder;
import shortestpath.pathfinder.AStarRoutingStaticProvider;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.sailing.BoatHull;
import shortestpath.pathfinder.sailing.SailingMoves;
import shortestpath.pathfinder.sailing.SailingPathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.Targets;
import shortestpath.pathfinder.astar.AStarRoutingSession;
import shortestpath.sailing.SailingService;
import shortestpath.sailing.SailingState;

/**
 * Owns the plugin's pathfinding concurrency: the single-worker executor, the mutex that pairs
 * the displayed search with its future and the queued queries, the deferred-tick work queue,
 * and the target/start marker state. The plugin shell forwards user and plugin-message intents
 * here and answers callers through the narrow {@link QueryResponder} seam. This package is a
 * leaf: nothing here may reference the plugin type.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PathScheduler
{
	private static final BufferedImage MARKER_IMAGE = ImageUtil.loadImageResource(PathScheduler.class, "/shortestpath/marker.png");
	private final List<Integer> pendingTasks = new ArrayList<>(3);
	private final Object pathfinderMutex = new Object();

	private final Client client;
	private final ClientThread clientThread;
	private final Settings settings;
	private final WorldMapPointManager worldMapPointManager;
	private final DebugState debugState;

	// Late-bound by attach(): the shell still owns PathfinderConfig construction and the
	// plugin-message surface, so they cannot ride the constructor. The sailing service
	// joins them for the same reason — injecting it here would close the
	// SailingService -> RefreshCoordinator -> PathScheduler Guice cycle.
	private PathfinderConfig pathfinderConfig;
	private SailingService sailing;
	private QueryResponder responder;

	private WorldMapPoint marker;
	// Where the open world map draws a tile; the plugin sets it, as the scheduler stays clear of widgets
	@Setter
	private IntUnaryOperator mapPosition = IntUnaryOperator.identity();
	private int lastLocation = WorldPointUtil.packWorldPoint(0, 0, 0);
	private ExecutorService pathfindingExecutor = Executors.newSingleThreadExecutor();
	private Future<?> pathfinderFuture;
	// Queries in flight or still queued, guarded by pathfinderMutex. Used both to answer callers
	// whose query never ran and to know whether a search is reading the transport availability.
	private final Map<ActiveSearch, QueryTask> queries = new LinkedHashMap<>();
	// Volatile because the render thread reads it (through getActiveSearch() and friends)
	// while restarts write it on the client thread under pathfinderMutex.
	private volatile ActiveSearch pathfinder;
	private AStarRoutingStaticProvider aStarRoutingStatic;
	private final AStarRoutingSession aStarRoutingSession = new AStarRoutingSession();
	@Getter(AccessLevel.PACKAGE) private boolean startPointSet = false;
	// Where the boat sat within its tile when the sailing search started, in local units from the tile centre
	@Getter private volatile int sailingPivotX;
	@Getter private volatile int sailingPivotY;

	/**
	 * Binds the shell-owned pieces the scheduler needs: the pathfinder config the shell builds in
	 * startUp, the sailing service, and the plugin-message responder. Called
	 * before anything can elicit a search.
	 */
	public void attach(PathfinderConfig pathfinderConfig, SailingService sailing, QueryResponder responder)
	{
		this.pathfinderConfig = pathfinderConfig;
		this.sailing = sailing;
		this.responder = responder;
	}

	void restart(String reason, int start, Targets ends, boolean canReviveFiltered)
	{
		debugState.restartRequested(reason, client.getTickCount());
		List<PathStep> previousPath;
		Targets previousTargets;
		synchronized (pathfinderMutex)
		{
			previousPath = pathfinder == null ? null : pathfinder.getPath();
			previousTargets = pathfinder == null ? null : pathfinder.getTargets();
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

	private void startPathfinding(int start, Targets requestedEnds, boolean canReviveFiltered,
		List<PathStep> previousPath, Targets previousTargets)
	{
		pathfinderConfig.refresh();
		Targets ends = pathfinderConfig.filterLocations(requestedEnds, canReviveFiltered);
		synchronized (pathfinderMutex)
		{
			if (ends.isEmpty())
			{
				debugState.restartOutcome("no targets left after filtering");
				setTarget(WorldPointUtil.UNDEFINED);
			}
			else
			{
				// The boarded-boat varbit can lag the world entity (stale across a teleport or
				// login), so only sail when the boat is actually in the scene — otherwise the
				// search would sail its headings across land
				if (pathfinderConfig.isSailingMoves() && sailing.getState().hasBoat())
				{
					pathfinder = sailingPathfinder(start, ends);
				}
				else
				{
					try
					{
						AStarPathfinder aStar = new AStarPathfinder(pathfinderConfig, aStarRoutingStatic(),
							aStarRoutingSession, start, ends, responder::postPluginMessages);
						// Recalculating towards the same targets: keep the old route drawn until
						// the new one is ready.
						if (ends.equals(previousTargets))
							aStar.showUntilDone(previousPath);
						pathfinder = aStar;
					}
					catch (RuntimeException error)
					{
						debugState.clientError(error, client.getTickCount());
						pathfinder = AStarPathfinder.failed(start, ends, responder::postPluginMessages, error);
					}
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

	/**
	 * On a boat, a search that sails the boat's 16 headings at its base speed instead of walking. It starts from the
	 * boat's exact tile and remembers where the boat sits within it: every sailing move lands whole tiles away, so the
	 * boat stays at that spot at each turn of the path. The boat's whole hull, from its bounds, must fit along every move
	 * there.
	 */
	private SailingPathfinder sailingPathfinder(int start, Targets ends)
	{
		int sailingStart = start;
		sailingPivotX = 0;
		sailingPivotY = 0;
		SailingState state = sailing.getState();
		if (state.getBoatPoint() != WorldPointUtil.UNDEFINED && !startPointSet)
		{
			sailingStart = state.getBoatPoint();
			sailingPivotX = state.getPivotX();
			sailingPivotY = state.getPivotY();
		}
		BoatHull hull = !state.hasBounds() ? null : BoatHull.fromBounds(state.getBoundsX(), state.getBoundsY(),
			state.getBoundsWidth(), state.getBoundsHeight(), sailingPivotX, sailingPivotY);
		// The heading the boat faces where the route starts, as one of the 16 headings 128 angle units
		// apart: a target under its hull only counts as reached facing that way — berthed, the boat hasn't
		// arrived at a target astern of it. The target orientation pairs with the target tile the route
		// starts from
		int sailingHeading = state.getHeading() < 0 ? WorldPointUtil.UNDEFINED
			: Math.floorMod((state.getHeading() + 64) / 128, 16);
		return new SailingPathfinder(pathfinderConfig, sailingStart, ends, responder::postPluginMessages,
			SailingMoves.forSpeed(pathfinderConfig.getSailingSpeed()), hull, sailingHeading);
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

	private AStarRoutingStaticProvider aStarRoutingStatic()
	{
		synchronized (pathfinderMutex)
		{
			if (aStarRoutingStatic == null)
				aStarRoutingStatic = new AStarRoutingStaticProvider(pathfinderConfig::getMap);
			return aStarRoutingStatic;
		}
	}

	/**
	 * Builds the A* backend's static routing data in the background, so the first route does
	 * not pay for it. The build runs on the pathfinding thread, so a search submitted
	 * meanwhile simply queues behind it; a failed build is remembered by the provider and
	 * reported by that search.
	 */
	public void prepareBackend()
	{
		if (pathfinderConfig == null)
		{
			return;
		}
		AStarRoutingStaticProvider provider = aStarRoutingStatic();
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
					log.warn("Failed to build A* routing data", error);
				}
			});
		}
	}

	void restart(String reason, int start, Targets ends)
	{
		restart(reason, start, ends, true);
	}

	boolean isNearPath(int location)
	{
		List<PathStep> path;
		// The previous route is only on screen until its recalculation finishes; the player is
		// expected to be off it, so it must not trigger yet another recalculation.
		if (pathfinder.isShowingProvisionalPath())
		{
			return true;
		}
		boolean sameLocation = lastLocation == location;
		lastLocation = location;
		if (pathfinder == null || (path = pathfinder.getPath()) == null || path.isEmpty() ||
			settings.lifecycle().recalculateDistance() < 0 || sameLocation)
		{
			return true;
		}

		for (PathStep pathStep : path)
		{
			if (WorldPointUtil.distanceBetween(location, pathStep.getPackedPosition()) < settings.lifecycle().recalculateDistance())
			{
				return true;
			}
		}

		// A sailing path has a point at the end of each move, up to a dozen tiles apart, so the boat can be between them
		if (isSailing())
		{
			for (int i = 1; i < path.size(); i++)
			{
				if (distanceToLine(location, path.get(i - 1).getPackedPosition(), path.get(i).getPackedPosition())
					< settings.lifecycle().recalculateDistance())
				{
					return true;
				}
			}
		}

		return false;
	}

	// How far a point is from the closest point on the straight line between two others, in tiles counted like
	// WorldPointUtil#distanceBetween, or Integer.MAX_VALUE on another plane
	private static int distanceToLine(int point, int from, int to)
	{
		if (WorldPointUtil.unpackWorldPlane(point) != WorldPointUtil.unpackWorldPlane(from))
		{
			return Integer.MAX_VALUE;
		}
		final double px = WorldPointUtil.unpackWorldX(point);
		final double py = WorldPointUtil.unpackWorldY(point);
		final double ax = WorldPointUtil.unpackWorldX(from);
		final double ay = WorldPointUtil.unpackWorldY(from);
		final double dx = WorldPointUtil.unpackWorldX(to) - ax;
		final double dy = WorldPointUtil.unpackWorldY(to) - ay;
		final double lengthSquared = dx * dx + dy * dy;
		final double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
		return (int) Math.round(Math.max(Math.abs(px - (ax + t * dx)), Math.abs(py - (ay + t * dy))));
	}

	private boolean isSailing()
	{
		return pathfinder != null && pathfinder.isSailing();
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
	public void queryPath(Object id, int start, Targets targets)
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
				// The plugin's only engine serves external callers too: queries run on the
				// same A* backend as the displayed path and reuse its session cache.
				ActiveSearch query = querySearch(start, targets);
				QueryTask task = new QueryTask(id);
				queries.put(query, task);
				task.future = pathfindingExecutor.submit(() -> runQuery(query, task));
			}
		});
	}

	/**
	 * The engine a plugin-message query runs. A constructor failure still owes the caller a
	 * result, so it becomes a finished {@code failed()} search carrying BACKEND_FAILURE
	 * rather than an uncaught exception thrown across the client-thread hop.
	 */
	private ActiveSearch querySearch(int start, Targets targets)
	{
		try
		{
			return new AStarPathfinder(pathfinderConfig, aStarRoutingStatic(), aStarRoutingSession,
				start, targets, null);
		}
		catch (RuntimeException error)
		{
			return AStarPathfinder.failed(start, targets, null, error);
		}
	}

	private void runQuery(ActiveSearch query, QueryTask task)
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
		for (Iterator<Map.Entry<ActiveSearch, QueryTask>> it = queries.entrySet().iterator(); it.hasNext();)
		{
			Map.Entry<ActiveSearch, QueryTask> entry = it.next();
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
	 * has fully settled. No-ops before {@code startUp} attaches the config — the pre-init
	 * guard lives here so callers may declare events unconditionally.
	 */
	void deferRefresh(int tick)
	{
		if (pathfinderConfig == null)
		{
			return;
		}
		pendingTasks.add(tick);
	}

	/**
	 * Runs each pending task whose tick has arrived, in submission order.
	 */
	void drainDueTasks(int tick)
	{
		for (int i = 0; i < pendingTasks.size(); i++)
		{
			if (tick >= pendingTasks.get(i))
			{
				pendingTasks.remove(i--);
				pathfinderConfig.refresh();
			}
		}
	}

	/**
	 * Marks the eligibility snapshot stale; the next config refresh rebuilds it.
	 */
	void invalidateEligibility()
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
				int target = targets.iterator().next();
				marker = new WorldMapPoint(WorldPointUtil.unpackWorldPoint(target), MARKER_IMAGE)
				{
					// RuneLite draws the marker, and jumps to it on click, at a spot of the open world map
					@Override
					public WorldPoint getWorldPoint()
					{
						return WorldPointUtil.unpackWorldPoint(mapPosition.applyAsInt(target));
					}

					@Override
					public WorldPoint getTarget()
					{
						return getWorldPoint();
					}
				};
				marker.setName("Target");
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
			// Targets the player picks need no items; adding one keeps the items of those already set.
			Targets destinations = Targets.at(targets);
			if (pathfinder != null && append)
			{
				destinations = pathfinder.getTargets().plus(destinations);
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

	/**
	 * {@link #withSchedulerLock(Supplier)} for bodies that have nothing to return; the same
	 * monitor guards both overloads.
	 */
	public void withSchedulerLock(Runnable action)
	{
		withSchedulerLock(() ->
		{
			action.run();
			return null;
		});
	}
}
