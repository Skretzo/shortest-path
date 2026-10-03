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
import shortestpath.pathfinder.AStarPathfinder;
import shortestpath.pathfinder.AStarRoutingStaticProvider;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.sailing.BoatHull;
import shortestpath.pathfinder.sailing.SailingMoves;
import shortestpath.pathfinder.sailing.SailingPathfinder;
import shortestpath.pathfinder.PathConsumptionValidator;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.Targets;
import shortestpath.pathfinder.astar.AStarRoutingSession;
import shortestpath.requirement.TransportEligibility;
import shortestpath.sailing.SailingService;
import shortestpath.sailing.SailingState;
import shortestpath.transport.Transport;

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
	/**
	 * Bound on the consumption re-plan loop: a path that still fails validation
	 * after this many re-plans is shown as-is rather than searched again.
	 */
	private static final int MAX_REPLAN_ATTEMPTS = 5;
	private final List<PendingTask> pendingTasks = new ArrayList<>(3);
	private final Object pathfinderMutex = new Object();

	private final Client client;
	private final ClientThread clientThread;
	private final ShortestPathConfig config;
	private final WorldMapPointManager worldMapPointManager;
	private final DebugState debugState;

	// Late-bound by attach(): the shell still owns PathfinderConfig construction and the
	// plugin-message surface, so they cannot ride the constructor. The sailing service
	// joins them for the same reason — injecting it here would close the
	// SailingService -> RefreshCoordinator -> PathScheduler Guice cycle.
	private PathfinderConfig pathfinderConfig;
	private ItemStateService itemState;
	private SailingService sailing;
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
	private AStarRoutingStaticProvider aStarRoutingStatic;
	private final AStarRoutingSession aStarRoutingSession = new AStarRoutingSession();
	private boolean startPointSet = false;
	// Where the boat sat within its tile when the sailing search started, in local units from the tile centre
	private volatile int sailingPivotX;
	private volatile int sailingPivotY;
	/**
	 * Re-plans triggered by the post-search consumption validator for the current
	 * (start, targets) search context. Reset whenever pathfinding restarts for a
	 * new context — exclusions are per-context state held on the config. Only
	 * touched under {@link #pathfinderMutex}: incremented on the pathfinder
	 * worker inside {@link #onPathComplete}, reset on the client thread.
	 */
	private int replanAttempts = 0;
	/**
	 * Monotonic search counter bumped by every {@link #schedulePathfinding} call
	 * under {@link #pathfinderMutex}. Each submitted search captures the
	 * generation it was scheduled under, and its completion callback re-checks
	 * it under the same mutex — the {@link #pathfinder} field alone cannot flag
	 * a stale callback because reassignment lags the schedule call by up to a
	 * client tick (it happens inside the queued invokeLater).
	 */
	private int pathGeneration;
	/**
	 * The generation the currently assigned {@link #pathfinder} was scheduled
	 * under, so idle readers can tell a settled search apart from one a pending
	 * schedule is about to replace.
	 */
	private int pathfinderGeneration;
	/**
	 * Set by {@link #shutdown()} so a finishing search's callback or a stray
	 * restart cannot resurrect the executor that shutdown tore down.
	 */
	private volatile boolean shutdown;

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
	 * startUp, the item-state and sailing services, and the plugin-message responder. Called
	 * before anything can elicit a search.
	 */
	public void attach(PathfinderConfig pathfinderConfig, ItemStateService itemState, SailingService sailing, QueryResponder responder)
	{
		shutdown = false;
		this.pathfinderConfig = pathfinderConfig;
		this.itemState = itemState;
		this.sailing = sailing;
		this.responder = responder;
	}

	void restart(String reason, int start, Targets requestedEnds, boolean canReviveFiltered)
	{
		// A new search context starts a fresh consumption accounting: the
		// validator's exclusions and the re-plan budget only belong to the search
		// that produced them.
		synchronized (pathfinderMutex)
		{
			replanAttempts = 0;
			if (pathfinderConfig != null)
			{
				pathfinderConfig.clearExcludedTransports();
			}
		}
		schedulePathfinding(reason, start, requestedEnds, canReviveFiltered);
	}

	/**
	 * Shared restart body for public restarts and the validator's re-plans. The
	 * re-plan variant re-enters here without clearing exclusions or resetting the
	 * attempt counter.
	 */
	private void schedulePathfinding(String reason, int start, Targets requestedEnds, boolean canReviveFiltered)
	{
		debugState.restartRequested(reason, client.getTickCount());
		List<PathStep> previousPath;
		Targets previousTargets;
		final int generation;
		synchronized (pathfinderMutex)
		{
			if (shutdown)
			{
				// Never resurrect the executor from a finishing search's
				// callback or a stray restart after shutdown.
				return;
			}
			// Every scheduled search gets its own generation so a completion
			// callback can tell it is stale while `pathfinder` still points at
			// it — the reassignment only happens in the queued invokeLater.
			generation = ++pathGeneration;
			previousPath = pathfinder == null ? null : pathfinder.getPath();
			previousTargets = pathfinder == null ? null : pathfinder.getTargets();
			// Cancel the current search only when it is still running. A re-plan
			// re-enters here from the finished search's own callback, where the
			// future is already done — cancelling it would self-interrupt the
			// worker and leak the interrupt flag into the next executor task.
			if (pathfinder != null && !pathfinder.isDone())
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
				startPathfinding(start, requestedEnds, canReviveFiltered, previousPath, previousTargets, generation);
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
		List<PathStep> previousPath, Targets previousTargets, int generation)
	{
		pathfinderConfig.refresh();
		Targets ends = pathfinderConfig.filterLocations(requestedEnds, canReviveFiltered);
		synchronized (pathfinderMutex)
		{
			if (shutdown || generation != pathGeneration)
			{
				// A newer schedule call (or shutdown) superseded this one
				// between the cancel above and this client tick.
				return;
			}
			if (ends.isEmpty())
			{
				debugState.restartOutcome("no targets left after filtering");
				setTarget(WorldPointUtil.UNDEFINED);
			}
			else
			{
				itemState.markBankPickupDirty();
				// Snapshot the eligibility the refreshed availability was
				// built from so the completion callback replays this
				// search's path against the exact snapshot the search
				// consumed — never a later rebuild observed off the
				// client thread.
				TransportEligibility eligibility = pathfinderConfig.getEligibility();
				// The boarded-boat varbit can lag the world entity (stale across a teleport or
				// login), so only sail when the boat is actually in the scene — otherwise the
				// search would sail its headings across land
				if (pathfinderConfig.isSailingMoves() && sailing.getState().hasBoat())
				{
					// A SailingPathfinder isn't a Pathfinder, so it can't be held by
					// legacyPathfinder — getPathfinder() reports null while sailing
					legacyPathfinder = null;
					pathfinder = sailingPathfinder(start, ends);
				}
				else if (pathfinderConfig.getPathfinderBackend() == PathfinderBackend.ASTAR)
				{
					try
					{
						legacyPathfinder = null;
						// The callback is bound to this search instance and generation
						// so a stale completion can never validate or re-plan over a
						// newer pathfinder's context.
						AStarPathfinder[] created = new AStarPathfinder[1];
						created[0] = new AStarPathfinder(pathfinderConfig, aStarRoutingStatic(),
							aStarRoutingSession, start, ends,
							() -> onPathComplete(created[0], start, ends, canReviveFiltered,
								generation, eligibility));
						// Recalculating towards the same targets: keep the old route drawn until
						// the new one is ready.
						if (ends.equals(previousTargets))
							created[0].showUntilDone(previousPath);
						pathfinder = created[0];
					}
					catch (RuntimeException error)
					{
						debugState.clientError(error, client.getTickCount());
						legacyPathfinder = null;
						pathfinder = AStarPathfinder.failed(start, ends, responder::postPluginMessages, error);
					}
				}
				else
				{
					// The callback is bound to this search instance and generation
					// so a stale completion can never validate or re-plan over a
					// newer pathfinder's context.
					Pathfinder[] created = new Pathfinder[1];
					created[0] = new Pathfinder(pathfinderConfig, start, ends,
						() -> onPathComplete(created[0], start, ends, canReviveFiltered,
							generation, eligibility));
					legacyPathfinder = created[0];
					pathfinder = legacyPathfinder;
				}
				pathfinderGeneration = generation;
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

	/**
	 * Runs on the pathfinder worker thread when a search finishes: replays the
	 * path's recorded transports against the consumption ledger and, when the
	 * first unpayable transport exists, excludes it and re-plans the same
	 * (start, targets) — bounded by {@link #MAX_REPLAN_ATTEMPTS}. Validation is
	 * deliberately post-search: keeping resource state out of the search graph
	 * avoids multiplying the state space, so the worst case is a few extra full
	 * searches per displayed path and zero when the path is clean.
	 * <p>
	 * {@code eligibility} is the snapshot the search's availability was built
	 * from, captured on the client thread at schedule time — {@link
	 * PathfinderConfig#getEligibility()} is a client-thread reader and may
	 * already have been rebuilt by an interim refresh. The whole body runs
	 * under {@link #pathfinderMutex} so the staleness check and the re-plan
	 * decision are atomic: a newer schedule bumps {@link #pathGeneration} under
	 * the same mutex before its invokeLater reassigns {@link #pathfinder}, so
	 * the generation catches callbacks the field check would miss.
	 */
	private void onPathComplete(ActiveSearch finished, int start, Targets ends, boolean canReviveFiltered,
		int generation, TransportEligibility eligibility)
	{
		synchronized (pathfinderMutex)
		{
			// Only the search this callback belongs to may be validated; a newer
			// context's own callback handles it. A cancelled search reports
			// done == false, and its partial path is meaningless to the ledger.
			if (pathfinder != finished || generation != pathGeneration || !finished.isDone())
			{
				return;
			}
			Transport unpayable = PathConsumptionValidator.firstUnpayable(eligibility, finished.getPath());
			if (unpayable == null || replanAttempts >= MAX_REPLAN_ATTEMPTS)
			{
				responder.postPluginMessages();
				return;
			}
			replanAttempts++;
			pathfinderConfig.excludeTransport(unpayable);
			schedulePathfinding("consumption re-plan", start, ends, canReviveFiltered);
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
	 * Builds the A* backend's static routing data in the background when the A* backend is
	 * selected, so the first route does not pay for it. The build runs on the pathfinding thread,
	 * so a search submitted meanwhile simply queues behind it; a failed build is remembered by the
	 * provider and reported by that search.
	 */
	public void prepareBackend()
	{
		if (pathfinderConfig == null || config.pathfinderBackend() != PathfinderBackend.ASTAR)
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
		if (pathfinder instanceof AStarPathfinder && ((AStarPathfinder) pathfinder).isShowingProvisionalPath())
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

		// A sailing path has a point at the end of each move, up to a dozen tiles apart, so the boat can be between them
		if (isSailing())
		{
			for (int i = 1; i < path.size(); i++)
			{
				if (distanceToLine(location, path.get(i - 1).getPackedPosition(), path.get(i).getPackedPosition())
					< config.recalculateDistance())
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
			shutdown = true;
			cancelQueries("SHUTDOWN");
			if (pathfindingExecutor != null)
			{
				pathfindingExecutor.shutdownNow();
				pathfindingExecutor = null;
			}
			// A finishing search's callback must see the context as gone so it
			// can neither post messages nor re-plan onto a torn-down executor.
			pathfinder = null;
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
		pendingTasks.add(new PendingTask(tick, pathfinderConfig::refresh));
	}

	/**
	 * Runs each pending task whose tick has arrived, in submission order.
	 */
	void drainDueTasks(int tick)
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
	 * The legacy-backend search last submitted, for source-compatible callers of the shell's
	 * {@code getPathfinder()}.
	 */
	public Pathfinder getPathfinder()
	{
		return legacyPathfinder;
	}

	public int getSailingPivotX()
	{
		return sailingPivotX;
	}

	public int getSailingPivotY()
	{
		return sailingPivotY;
	}

	boolean isStartPointSet()
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
