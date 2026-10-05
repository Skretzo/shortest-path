package shortestpath;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import shortestpath.items.ItemChange;
import shortestpath.items.ItemStateService;
import shortestpath.overlay.BankItemHighlightOverlay;
import shortestpath.overlay.DebugOverlayPanel;
import shortestpath.overlay.InventoryHighlightOverlay;
import shortestpath.overlay.PathMapOverlay;
import shortestpath.overlay.PathMapTooltipOverlay;
import shortestpath.overlay.PathMinimapOverlay;
import shortestpath.overlay.PathTileOverlay;
import shortestpath.overlay.SpellbookHighlightOverlay;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.ExactPathfinder;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.pathfinder.ExactRoutingStaticProvider;
import shortestpath.pathfinder.exact.ExactRoutingSession;
import shortestpath.poh.PohChange;
import shortestpath.poh.PohService;
import shortestpath.settings.ConfigChange;
import shortestpath.settings.Effect;
import shortestpath.settings.Settings;
import shortestpath.spirittree.SpiritTreeService;
import shortestpath.spirittree.TreeChange;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

@Slf4j
@SuppressWarnings("SameParameterValue")
@PluginDescriptor(name = "Shortest Path", description = "Draws the shortest path to a chosen destination on the map<br>"
	+
	"Right click on the world map or shift right click a tile to use", tags = {"pathfinder", "map", "waypoint",
	"navigation"})
public class ShortestPathPlugin extends Plugin
{
	protected static final String CONFIG_GROUP = "shortestpath";

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
	private static final String CLEAR = "Clear";
	private static final String PATH = ColorUtil.wrapWithColorTag("Path", JagexColors.MENU_TARGET);
	private static final String SET = "Set";
	private static final String FIND_CLOSEST = "Find closest";
	private static final String FLASH_ICONS = "Flash icons";
	private static final String START = ColorUtil.wrapWithColorTag("Start", JagexColors.MENU_TARGET);
	private static final String TARGET = ColorUtil.wrapWithColorTag("Target", JagexColors.MENU_TARGET);
	private static final BufferedImage MARKER_IMAGE = ImageUtil.loadImageResource(ShortestPathPlugin.class, "/marker.png");
	private static final int NEXUS_DIALOG_REFRESH_ATTEMPTS = 10;
	private final List<PendingTask> pendingTasks = new ArrayList<>(3);
	private final Object pathfinderMutex = new Object();

	@Inject
	private Client client;
	@Getter
	@Inject
	private ClientThread clientThread;
	@Inject
	private ShortestPathConfig config;
	@Inject
	private EventBus eventBus;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private PathTileOverlay pathOverlay;
	@Inject
	private PathMinimapOverlay pathMinimapOverlay;
	@Inject
	private PathMapOverlay pathMapOverlay;
	@Inject
	private PathMapTooltipOverlay pathMapTooltipOverlay;
	@Inject
	private BankItemHighlightOverlay bankItemHighlightOverlay;
	@Inject
	private SpellbookHighlightOverlay spellbookHighlightOverlay;
	@Inject
	private InventoryHighlightOverlay inventoryHighlightOverlay;
	@Inject
	private DebugOverlayPanel debugOverlayPanel;
	@Inject
	private SpriteManager spriteManager;
	@Inject
	private WorldMapPointManager worldMapPointManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private PohService pohService;
	@Inject
	private SpiritTreeService spiritTrees;
	@Inject
	private ItemStateService itemState;
	@Inject
	private Settings settings;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ConfigManager configManager;
	@Inject
	private ItemManager itemManager;
	private ShortestPathPanel panel;
	private NavigationButton navButton;
	private Point lastMenuOpenedPoint;
	private WorldMapPoint marker;
	private int lastLocation = WorldPointUtil.packWorldPoint(0, 0, 0);
	private Shape minimapClipFixed;
	private Shape minimapClipResizeable;
	private BufferedImage minimapSpriteFixed;
	private BufferedImage minimapSpriteResizeable;
	private Rectangle minimapRectangle = new Rectangle();
	private GameState lastGameState = null;
	private GameState lastLastGameState = null;
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
	@Getter
	private PathfinderConfig pathfinderConfig;
	@Getter
	private boolean startPointSet = false;
	@Getter
	private final DebugState debugState = new DebugState();
	private final KeyListener clearPathKeylistener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (config.clearPathHotkey().matches(e))
			{
				setTarget(WorldPointUtil.UNDEFINED);
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};
	private boolean fairyRingPanelOpen = false;

	public ActiveSearch getActiveSearch()
	{
		return pathfinder;
	}

	/**
	 * Legacy source-compatible accessor. Plugin internals use {@link #getActiveSearch()}.
	 */
	public Pathfinder getPathfinder()
	{
		return legacyPathfinder;
	}

	@Provides
	public ShortestPathConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ShortestPathConfig.class);
	}

	@Override
	protected void startUp()
	{
		pathfinderConfig = new PathfinderConfig(client, config, settings);
		pathfinderConfig.setSpiritTreeService(spiritTrees);
		pathfinderConfig.setItemStateService(itemState);
		if (GameState.LOGGED_IN.equals(client.getGameState()))
		{
			// The profile load touches the same HashMaps the
			// queued refresh() iterates, so it must run on the client thread
			// too — doing it here on the EDT would race that refresh.
			clientThread.invokeLater(() ->
			{
				applyTreeChange(spiritTrees.loadFromProfile(), "profile change");
				pathfinderConfig.refresh();
			});
		}
		else
		{
			// No refresh is queued when logged out, so loading here is safe;
			// RuneScapeProfileChanged reloads once a profile is active anyway.
			applyTreeChange(spiritTrees.loadFromProfile(), "profile change");
		}

		overlayManager.add(pathOverlay);
		overlayManager.add(pathMinimapOverlay);
		overlayManager.add(pathMapOverlay);
		overlayManager.add(pathMapTooltipOverlay);
		overlayManager.add(bankItemHighlightOverlay);
		overlayManager.add(spellbookHighlightOverlay);
		overlayManager.add(inventoryHighlightOverlay);

		if (config.drawDebugPanel())
		{
			overlayManager.add(debugOverlayPanel);
		}

		keyManager.registerKeyListener(clearPathKeylistener);
		applyPohChange(pohService.loadFromProfile(), "profile load");
		prepareExactBackend();

		panel = new ShortestPathPanel(config, configManager, client, clientThread, itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Shortest Path")
			.icon(ImageUtil.loadImageResource(ShortestPathPlugin.class, "/panel_icon.png"))
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(pathOverlay);
		overlayManager.remove(pathMinimapOverlay);
		overlayManager.remove(pathMapOverlay);
		overlayManager.remove(pathMapTooltipOverlay);
		overlayManager.remove(bankItemHighlightOverlay);
		overlayManager.remove(spellbookHighlightOverlay);
		overlayManager.remove(inventoryHighlightOverlay);
		overlayManager.remove(debugOverlayPanel);

		synchronized (pathfinderMutex)
		{
			cancelQueries("SHUTDOWN");
			if (pathfindingExecutor != null)
			{
				pathfindingExecutor.shutdownNow();
				pathfindingExecutor = null;
			}
		}

		keyManager.unregisterKeyListener(clearPathKeylistener);

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
			panel = null;
		}

		// Flush pending observations so the last tick's sample is not lost.
		spiritTrees.persistIfDirty();

		// The singleton survives restarts; drop the live bank ref and pickup
		// cache so session state cannot leak into the next enable.
		itemState.reset();
	}

	public void restartPathfinding(String reason, int start, Set<Integer> requestedEnds, boolean canReviveFiltered)
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

		getClientThread().invokeLater(() ->
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
							exactRoutingSession, start, ends, this::postPluginMessages);
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
						pathfinder = ExactPathfinder.failed(start, ends, this::postPluginMessages, error);
					}
				}
				else
				{
					legacyPathfinder = new Pathfinder(pathfinderConfig, start, ends, this::postPluginMessages);
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
	private void prepareExactBackend()
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

	public void restartPathfinding(String reason, int start, Set<Integer> ends)
	{
		restartPathfinding(reason, start, ends, true);
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

	public Color getPathColor()
	{
		if (pathfinder == null || !pathfinder.isDone())
		{
			return settings.display().colourPathCalculating();
		}

		List<PathStep> path = pathfinder.getPath();
		if (path == null || path.isEmpty() || pathfinder.getTargets().isEmpty())
		{
			return settings.display().colourPath();
		}

		if (isPathUnreachable())
		{
			return settings.display().colourPathUnreachable();
		}

		return settings.display().colourPath();
	}

	public boolean isPathUnreachable()
	{
		if (pathfinder == null || !pathfinder.isDone())
		{
			return false;
		}

		List<PathStep> path = pathfinder.getPath();
		if (path == null || path.isEmpty() || pathfinder.getTargets().isEmpty())
		{
			return false;
		}

		int endPoint = path.get(path.size() - 1).getPackedPosition();
		int closestTargetDistance = Integer.MAX_VALUE;
		for (int target : pathfinder.getTargets())
		{
			closestTargetDistance = Math.min(closestTargetDistance, WorldPointUtil.distanceBetween(target, endPoint));
		}

		return closestTargetDistance > settings.display().unreachableTargetDistance();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		ConfigChange change = settings.onConfigChanged(event);
		if (change == null)
		{
			return;
		}

		Set<Effect> effects = change.getEffects();

		if (panel != null)
		{
			panel.onExternalConfigChanged(event);
		}

		if (effects.contains(Effect.SIDE_EFFECT_DEBUG_OVERLAY))
		{
			if (settings.lifecycle().drawDebugPanel())
			{
				overlayManager.add(debugOverlayPanel);
			}
			else
			{
				overlayManager.remove(debugOverlayPanel);
			}
		}

		if (effects.contains(Effect.SIDE_EFFECT_BACKEND_PREP))
		{
			prepareExactBackend();
		}

		// A routing input changed; rerun pathfinding
		if (effects.contains(Effect.ROUTE_INVALIDATING) && pathfinder != null)
		{
			restartPathfinding("config: " + change.getKey(), pathfinder.getStart(), pathfinder.getTargets());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState previousGameState = lastGameState;
		GameState previousPreviousGameState = lastLastGameState;
		lastLastGameState = lastGameState;
		lastGameState = event.getGameState();

		if (pathfinderConfig == null
			|| !GameState.LOGGING_IN.equals(previousPreviousGameState)
			|| !GameState.LOADING.equals(previousGameState)
			|| !GameState.LOGGED_IN.equals(lastGameState))
		{
			return;
		}

		pendingTasks.add(new PendingTask(client.getTickCount() + 1, pathfinderConfig::refresh));
	}

	/**
	 * Refresh the pathfinder when the player hops worlds. The new world's type
	 * (e.g. seasonal) is what drives league-mode auto-detection in
	 * {@link shortestpath.leagues.LeagueModeState}, so we need a fresh
	 * {@code PathfinderConfig.refresh()} pass after every hop.
	 */
	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		if (pathfinderConfig == null)
		{
			return;
		}
		pendingTasks.add(new PendingTask(client.getTickCount() + 1, pathfinderConfig::refresh));
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		applyPohChange(pohService.loadFromProfile(), "profile load");
		// The new profile may carry different persisted trees, so an in-flight
		// path computed against the old account's set must be redone — the
		// load's fact carries that route-invalidating effect unconditionally.
		applyTreeChange(spiritTrees.loadFromProfile(), "profile change");
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (!CONFIG_GROUP.equals(event.getNamespace()))
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
				synchronized (pathfinderMutex)
				{
					settings.applyOverrides(payloadOverrides);
				}
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

			boolean useOld = targets.isEmpty() && pathfinder != null;
			restartPathfinding("plugin message", start, useOld ? pathfinder.getTargets() : targets, useOld);
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
			queryPath(id, start, targets);
		}
		else if (PLUGIN_MESSAGE_GET_TARGET.equals(action))
		{
			Map<String, Object> data = event.getData();
			postCurrentTarget(data == null ? null : data.get(PLUGIN_MESSAGE_ID));
		}
		else if (PLUGIN_MESSAGE_CLEAR.equals(action))
		{
			synchronized (pathfinderMutex)
			{
				settings.clearOverrides();
			}
			setTarget(WorldPointUtil.UNDEFINED);
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

	/**
	 * Finds a path for another plugin without touching the displayed one, and answers with a
	 * {@code result} plugin message carrying the caller's {@code id}.
	 *
	 * <p>Queries share the single pathfinding thread with the displayed path, so the two never
	 * search at the same time. The transport availability is only refreshed when no search is
	 * queued or running, since a search reads it while it runs; otherwise the query uses what the
	 * previous search was started with. A new displayed path cancels pending queries, so a query
	 * never runs concurrently with a refresh triggered by {@code restartPathfinding} either.
	 */
	private void queryPath(Object id, int start, Set<Integer> targets)
	{
		clientThread.invokeLater(() ->
		{
			synchronized (pathfinderMutex)
			{
				if (pathfindingExecutor == null)
				{
					postQueryFailure(id, "SHUTDOWN");
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
			postQueryResult(task.id, query);
		}
		catch (RuntimeException | Error e)
		{
			postQueryFailure(task.id, "ERROR");
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
				postQueryFailure(task.id, reason);
			}
			else
			{
				entry.getKey().cancel();
			}
		}
	}

	private void postQueryResult(Object id, Pathfinder query)
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
				for (Transport transport : transportsForEdge(steps.get(i - 1), steps.get(i)))
				{
					Map<String, Object> edge = new HashMap<>();
					edge.put("origin", WorldPointUtil.unpackWorldPoint(steps.get(i - 1).getPackedPosition()));
					edge.put("destination", WorldPointUtil.unpackWorldPoint(steps.get(i).getPackedPosition()));
					edge.put("objectInfo", transport.getObjectInfo());
					edge.put("displayInfo", formatTransportDisplay(transport));
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
		clientThread.invokeLater(() -> eventBus.post(new PluginMessage(CONFIG_GROUP, PLUGIN_MESSAGE_RESULT, data)));
	}

	/**
	 * Answers a query that produced no result: a malformed message, a plugin shutdown, a
	 * cancelled search, or a failure while running.
	 */
	private void postQueryFailure(Object id, String reason)
	{
		Map<String, Object> data = new HashMap<>();
		data.put(PLUGIN_MESSAGE_ID, id);
		data.put("reached", false);
		data.put("path", List.of());
		data.put(PLUGIN_MESSAGE_TRANSPORTS, List.of());
		data.put("reason", reason);
		clientThread.invokeLater(() -> eventBus.post(new PluginMessage(CONFIG_GROUP, PLUGIN_MESSAGE_RESULT, data)));
	}

	/**
	 * Answers a {@code getTarget} plugin message with a {@code currentTarget} message carrying
	 * the caller's {@code id} and a snapshot of the displayed path's start and targets. This is
	 * a read-only request: it launches no pathfinding work and mutates no state.
	 *
	 * <p>The start/targets pair is read while holding {@link #pathfinderMutex} so a request
	 * arriving mid-search sees one consistent snapshot rather than a torn pair; the mutex is
	 * released before the response hops to the client thread. When no path is set the response
	 * carries {@code set=false} and no start or target fields.
	 */
	private void postCurrentTarget(Object id)
	{
		int start;
		Set<Integer> targets;
		Map<String, Object> overrides;
		synchronized (pathfinderMutex)
		{
			start = (pathfinder == null) ? WorldPointUtil.UNDEFINED : pathfinder.getStart();
			targets = (pathfinder == null) ? Set.of() : new HashSet<>(pathfinder.getTargets());
			overrides = settings.rawOverrides().isEmpty() ? null : new HashMap<>(settings.rawOverrides());
		}

		Map<String, Object> data = new HashMap<>();
		data.put(PLUGIN_MESSAGE_ID, id);
		data.put("set", !targets.isEmpty());
		if (!targets.isEmpty())
		{
			data.put(PLUGIN_MESSAGE_START,
				start == WorldPointUtil.UNDEFINED ? null : WorldPointUtil.unpackWorldPoint(start));
			List<WorldPoint> unpackedTargets = new ArrayList<>(targets.size());
			for (int target : targets)
			{
				unpackedTargets.add(WorldPointUtil.unpackWorldPoint(target));
			}
			data.put(PLUGIN_MESSAGE_TARGET, unpackedTargets);
		}
		if (overrides != null)
		{
			data.put(PLUGIN_MESSAGE_CONFIG_OVERRIDE, overrides);
		}
		clientThread.invokeLater(() -> eventBus.post(new PluginMessage(CONFIG_GROUP, PLUGIN_MESSAGE_CURRENT_TARGET, data)));
	}

	/**
	 * A query that has been submitted to the executor: the caller's {@code id} plus the task's
	 * future, so it can be answered or cancelled when the displayed path or plugin shutdown
	 * takes precedence.
	 */
	private static class QueryTask
	{
		final Object id;
		Future<?> future;

		QueryTask(Object id)
		{
			this.id = id;
		}
	}

	public void postPluginMessages()
	{
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
				for (Transport transport : transportsForEdge(currentStep, nextStep))
				{
					transportOrigins.add(WorldPointUtil.unpackWorldPoint(currentStep.getPackedPosition()));
					transportDestinations.add(WorldPointUtil.unpackWorldPoint(nextStep.getPackedPosition()));
					transportObjectInfos.add(transport.getObjectInfo());
					transportDisplayInfos.add(formatTransportDisplay(transport));
				}
			}
			data.put("origin", transportOrigins);
			data.put("destination", transportDestinations);
			data.put("objectInfo", transportObjectInfos);
			data.put("displayInfo", transportDisplayInfos);
			eventBus.post(new PluginMessage(CONFIG_GROUP, PLUGIN_MESSAGE_TRANSPORTS, data));
		}
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		lastMenuOpenedPoint = client.getMouseCanvasPosition();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		applyPohChange(pohService.refreshFromDialog(client), "nexus dialog");
		pohService.persistIfDirty();

		for (int i = 0; i < pendingTasks.size(); i++)
		{
			if (pendingTasks.get(i).check(client.getTickCount()))
			{
				pendingTasks.remove(i--).run();
			}
		}

		Player localPlayer = client.getLocalPlayer();

		// The service notes the player's region, samples the in-region patch
		// varbit once the region has settled, and flushes persistence; the
		// returned fact carries the restart decision.
		applyTreeChange(spiritTrees.onGameTick(), "spirit tree varbit");

		if (localPlayer == null || pathfinder == null)
		{
			return;
		}

		int currentLocation = WorldPointUtil.fromLocalInstance(client, localPlayer);
		for (int target : pathfinder.getTargets())
		{
			if (WorldPointUtil.distanceBetween(currentLocation, target) < config.reachedDistance())
			{
				setTarget(WorldPointUtil.UNDEFINED);
				return;
			}
		}

		if (!startPointSet && !isNearPath(currentLocation))
		{
			if (config.cancelInstead())
			{
				setTarget(WorldPointUtil.UNDEFINED);
				return;
			}
			restartPathfinding("off route", currentLocation, pathfinder.getTargets());
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (client.isKeyPressed(KeyCode.KC_SHIFT)
			&& event.getType() == MenuAction.WALK.getId())
		{
			addMenuEntry(event, SET, TARGET, 1);
			if (pathfinder != null)
			{
				if (!pathfinder.getTargets().isEmpty())
				{
					addMenuEntry(event, SET, TARGET + ColorUtil.wrapWithColorTag(" " +
						(pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET), 1);
				}
				for (int target : pathfinder.getTargets())
				{
					if (target != WorldPointUtil.UNDEFINED)
					{
						addMenuEntry(event, SET, START, 1);
						break;
					}
				}
				int selectedTile = getSelectedWorldPoint();
				List<PathStep> path;
				if ((path = pathfinder.getPath()) != null)
				{
					for (PathStep pathStep : path)
					{
						if (pathStep.getPackedPosition() == selectedTile)
						{
							addMenuEntry(event, CLEAR, PATH, 1);
							break;
						}
					}
				}
			}
		}

		final Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);

		if (map != null)
		{
			if (map.getBounds().contains(
				client.getMouseCanvasPosition().getX(),
				client.getMouseCanvasPosition().getY()))
			{
				addMenuEntry(event, SET, TARGET, 0);
				if (pathfinder != null)
				{
					if (!pathfinder.getTargets().isEmpty())
					{
						addMenuEntry(event, SET, TARGET + ColorUtil.wrapWithColorTag(" " +
							(pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET), 0);
					}
					for (int target : pathfinder.getTargets())
					{
						if (target != WorldPointUtil.UNDEFINED)
						{
							addMenuEntry(event, SET, START, 0);
							addMenuEntry(event, CLEAR, PATH, 0);
						}
					}
				}
			}
			if (event.getOption().equals(FLASH_ICONS) && pathfinderConfig.hasDestination(simplify(event.getTarget())))
			{
				addMenuEntry(event, FIND_CLOSEST, event.getTarget(), 1);
			}
		}

		final Shape minimap = getMinimapClipArea();

		if (minimap != null && pathfinder != null
			&& minimap.contains(
			client.getMouseCanvasPosition().getX(),
			client.getMouseCanvasPosition().getY()))
		{
			addMenuEntry(event, CLEAR, PATH, 0);
		}

		if (minimap != null && pathfinder != null
			&& ("Floating World Map".equals(Text.removeTags(event.getOption()))
			|| "Close Floating panel".equals(Text.removeTags(event.getOption()))))
		{
			addMenuEntry(event, CLEAR, PATH, 1);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemChange change = itemState.onContainerChanged(event.getContainerId(), event.getItemContainer());
		applyItemChange(change);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Rune pouch contents and the Lumbridge Elite diary feed the eligibility
		// snapshot but change without firing a container event.
		ItemChange change = itemState.onVarbitChanged(event.getVarbitId());
		applyItemChange(change);
	}

	/**
	 * Maps an item-state change fact to its shell follow-up actions: the
	 * declared {@link Effect#ELIGIBILITY_STALE} effect lazily rebuilds the
	 * eligibility snapshot. {@code null} facts admit nothing and map to no
	 * action.
	 */
	private void applyItemChange(ItemChange change)
	{
		if (change != null && change.getEffects().contains(Effect.ELIGIBILITY_STALE))
		{
			pathfinderConfig.invalidateEligibility();
		}
	}

	/**
	 * Maps a spirit-tree change fact to its shell follow-up actions: the
	 * declared {@link Effect#ROUTE_INVALIDATING} effect restarts pathfinding
	 * with the running search's own start and targets. {@code null} facts
	 * admit nothing and map to no action.
	 */
	private void applyTreeChange(TreeChange change, String reason)
	{
		if (change != null
			&& change.getEffects().contains(Effect.ROUTE_INVALIDATING)
			&& pathfinder != null)
		{
			restartPathfinding(reason, pathfinder.getStart(), pathfinder.getTargets());
		}
	}

	/**
	 * Maps a POH change fact to its shell follow-up actions. {@link
	 * Effect#DISPLAY_ONLY} carries no shell action today — the keybind map is
	 * read lazily by the display path, which sees it hot by reference — so it
	 * is logged and nothing else runs. The mapper exists so the fact stream is
	 * consumed symmetrically until the refresh coordinator owns the policy;
	 * any other effect violates the service contract and is surfaced rather
	 * than silently swallowed.
	 */
	private void applyPohChange(PohChange change, String why)
	{
		if (change == null || change.getEffects().isEmpty())
		{
			return;
		}
		for (Effect effect : change.getEffects())
		{
			if (Effect.DISPLAY_ONLY.equals(effect))
			{
				log.debug("POH display refresh: {}", why);
			}
			else
			{
				log.warn("Unhandled POH change effect {} ({})", effect, why);
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != PohService.TELENEXUS_CREATE_TELELINE)
		{
			return;
		}
		Widget widget = client.getScriptActiveWidget();
		if (widget != null)
		{
			applyPohChange(pohService.putFromDialogLine(widget.getText()), "nexus dialog line");
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (pathfinder != null && event.getGroupId() == InterfaceID.FAIRYRINGS_LOG)
		{
			fairyRingPanelOpen = true;
		}

		if (event.getGroupId() == InterfaceID.TELENEXUS_TELEPORT
			|| event.getGroupId() == InterfaceID.TELENEXUS)
		{
			final int[] attempts = {0};
			clientThread.invokeLater(() ->
			{
				attempts[0]++;
				boolean dialogOpen = client.getWidget(InterfaceID.TelenexusTeleport.UNIVERSE) != null
					|| client.getWidget(InterfaceID.Telenexus.UNIVERSE) != null;
				if (!dialogOpen)
				{
					return attempts[0] >= NEXUS_DIALOG_REFRESH_ATTEMPTS;
				}
				PohChange change = pohService.refreshFromDialog(client);
				applyPohChange(change, "nexus dialog retry");
				return change != null
					|| attempts[0] >= NEXUS_DIALOG_REFRESH_ATTEMPTS;
			});
		}

		// Refresh the spirit tree cache on every menu open: the menu is
		// authoritative for the planted patches it lists, and other detection
		// sources (in-region varbits, persisted profile state) fill in the rest.
		switch (event.getGroupId())
		{
			case InterfaceID.MENU:
				clientThread.invokeLater(() -> applyTreeChange(spiritTrees.onMenuOpened(false), "spirit trees"));
				break;
			case InterfaceID.MENU_NEW:
				clientThread.invokeLater(() -> applyTreeChange(spiritTrees.onMenuOpened(true), "spirit trees"));
				break;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.FAIRYRINGS_LOG)
		{
			fairyRingPanelOpen = false;
		}
	}

	@Subscribe
	public void onPostClientTick(PostClientTick event)
	{
		if (fairyRingPanelOpen && pathfinder != null)
		{
			scrollFairyRingPanel();
		}
	}

	private void scrollFairyRingPanel()
	{
		List<PathStep> path;

		if (pathfinder == null
			|| (path = pathfinder.getPath()) == null)
		{
			return;
		}

		String fairyRingCode = null;

		for (int i = 1; i < path.size(); i++)
		{
			PathStep currentStep = path.get(i - 1);
			PathStep nextStep = path.get(i);
			for (Transport transport : transportsForEdge(currentStep, nextStep))
			{
				if (TransportType.FAIRY_RING.equals(transport.getType()))
				{
					fairyRingCode = transport.getDisplayInfo();
				}
			}
		}
		if (fairyRingCode == null)
		{
			return;
		}

		Widget codeWidget = null;

		Widget favesPanel = client.getWidget(InterfaceID.FairyringsLog.FAVES);
		if (favesPanel != null)
		{
			for (Widget widget : favesPanel.getStaticChildren())
			{
				if (widget != null)
				{
					String widgetText = widget.getText();
					if ((fairyRingCode.equals(widgetText)
						|| ("(Shortest Path) " + fairyRingCode).equals(widgetText)))
					{
						codeWidget = widget;
						break;
					}
				}
			}
		}

		Widget contentsList = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
		if (contentsList != null && codeWidget == null)
		{
			for (Widget widget : contentsList.getDynamicChildren())
			{
				if (widget != null)
				{
					String widgetText = widget.getText();
					if ((fairyRingCode.equals(widgetText)
						|| ("(Shortest Path) " + fairyRingCode).equals(widgetText)))
					{
						codeWidget = widget;
						break;
					}
				}
			}
		}

		if (codeWidget == null)
		{
			return;
		}

		codeWidget.setTextColor(0x00FF00);
		String codeWidgetText = codeWidget.getText();
		if (codeWidgetText != null && !codeWidgetText.contains("(Shortest Path)"))
		{
			codeWidget.setText("(Shortest Path) " + codeWidgetText);
		}

		if (contentsList == null)
		{
			return;
		}

		int panelScrollY = Math.min(
			codeWidget.getRelativeY(),
			contentsList.getScrollHeight() - contentsList.getHeight()
		);

		contentsList.setScrollY(panelScrollY);
		contentsList.revalidateScroll();

		client.runScript(
			ScriptID.UPDATE_SCROLLBAR,
			InterfaceID.FairyringsLog.SCROLLBAR,
			InterfaceID.FairyringsLog.CONTENTS,
			panelScrollY
		);
	}

	public CollisionMap getMap()
	{
		return pathfinderConfig.getMap();
	}

	/**
	 * WARNING: This is a legacy wrapper for coarse display-oriented callers only.
	 * <p>
	 * It collapses banked/unbanked transport availability into a single view via
	 * PathfinderConfig.getTransports(), which is not valid for path-state-sensitive logic.
	 * <p>
	 * Do not use this for reasoning about which transports are available at a specific
	 * step of a path. Use PathfinderConfig.getTransportAvailability(boolean) and the
	 * path's PathStep state instead.
	 */
	public PrimitiveIntHashMap<Transport[]> getTransports()
	{
		return pathfinderConfig.getTransports();
	}

	/**
	 * Every loaded transport grouped by origin for display, including transports the
	 * player cannot currently use. Same layout as {@link #getTransports()}.
	 */
	public PrimitiveIntHashMap<Transport[]> getAllDisplayTransports()
	{
		return pathfinderConfig.getAllDisplayTransports();
	}

	/**
	 * This reconstructs the candidate transports for a rendered path edge from the current path state.
	 * <p>
	 * The important detail is that path display logic is edge-based, not node-based:
	 * - origin position comes from currentStep
	 * - destination position comes from nextStep
	 * - the applicable transport set may depend on whether the edge transitions into banked state
	 * <p>
	 * That last point is the awkward one. Banking is not represented as its own explicit path edge;
	 * instead the "becomes banked" state change is conflated into the movement/transport edge that
	 * reaches the banked destination step. As a result, callers cannot safely resolve transports from
	 * a single PathStep alone: using only currentStep can miss bank-gated transports, while using only
	 * nextStep loses the origin tile of the edge. This helper therefore takes both steps and resolves
	 * transports for the edge between them.
	 * <p>
	 * When the destination step carries the transport the search actually chose (steps built by
	 * {@link NodeGraph#getPathSteps} do), that transport is the answer and this function returns
	 * it directly as a singleton — reconstruction is only a fallback for identity-less steps
	 * (e.g. steps built via the two-argument {@link PathStep} constructor). The destination-based
	 * fallback remains inherently ambiguous when multiple valid transports share the same
	 * origin/destination pair under the same edge state.
	 * <p>
	 * Note that the fallback also performs filtering by the transport target, so callers of this
	 * function can directly iterate over the returned transports.
	 */
	public Set<Transport> transportsForEdge(PathStep currentStep, PathStep nextStep)
	{
		if (currentStep == null || nextStep == null)
		{
			return Set.of();
		}
		// The step knows which transport produced the edge into it: no reconstruction needed.
		if (nextStep.getTransport() != null)
		{
			return Set.of(nextStep.getTransport());
		}
		boolean bankVisited = currentStep.isBankVisited() || nextStep.isBankVisited();
		// Get the transports which start from the position of starting step.
		Set<Transport> stepTransports = new HashSet<>(Arrays.asList(
			pathfinderConfig.getTransportsPacked(bankVisited)
				.getOrDefault(currentStep.getPackedPosition(), TransportAvailability.EMPTY_TRANSPORTS)));
		// Add the teleports, which might be used from anywhere.
		stepTransports.addAll(Arrays.asList(pathfinderConfig.getUsableTeleports(bankVisited)));
		// Remove transports which do not target the correct location.
		stepTransports.removeIf(transport -> transport.getDestination() != nextStep.getPackedPosition());
		// Remove teleports that share destinations with a local transport type on this edge.
		// For example, if the path uses a QUETZAL (local) transport, suppress QUETZAL_WHISTLE hints.
		// Also suppress them when the edge distance is within the shared type's radius threshold,
		// which occurs when the path is simply walking to a landing site (not teleporting to it).
		Set<TransportType> localTypes = EnumSet.noneOf(TransportType.class);
		for (Transport t : stepTransports)
		{
			if (t.getOrigin() != Transport.UNDEFINED_ORIGIN && t.getType() != null)
			{
				localTypes.add(t.getType());
			}
		}
		int edgeDistance = WorldPointUtil.distanceBetween2D(currentStep.getPackedPosition(), nextStep.getPackedPosition());
		stepTransports.removeIf(t ->
		{
			if (t.getOrigin() != Transport.UNDEFINED_ORIGIN || t.getType() == null)
			{
				return false; // keep local transports
			}
			TransportType sharedType = t.getType().sharesDestinationsWith();
			if (sharedType == null)
			{
				return false; // not a shared-destination teleport, keep it
			}
			// Suppress if a local transport of the shared type is present on this edge (Issue 1),
			// or if the edge is within the shared type's radius threshold, meaning the path is
			// walking to the landing site rather than teleporting there (Issue 2).
			return localTypes.contains(sharedType)
				|| (sharedType.getRadiusThreshold() != null && edgeDistance <= sharedType.getRadiusThreshold());
		});
		return stepTransports;
	}

	public PathStep nextPathStep(List<PathStep> path, int index)
	{
		if (path == null || index < 0 || index + 1 >= path.size())
		{
			return null;
		}
		return path.get(index + 1);
	}

	/**
	 * Delegates to the POH service; kept public for the overlay callers.
	 */
	public String formatTransportDisplay(Transport transport)
	{
		return pohService.formatTransportDisplay(transport);
	}

	/**
	 * Checks if the destination is inside POH and looks ahead in the path to find the exit transport.
	 * If the immediate exit leads to a fairy ring or other notable transport shortly after,
	 * that information is included instead.
	 * <p>
	 * Delegates to the POH service, bridging the edge-transport lookup across
	 * the boundary; kept public for the overlay callers.
	 *
	 * @param destination  The destination point to check
	 * @param path         The full path
	 * @param currentIndex The current index in the path
	 * @return The display info of the POH exit transport, or null if not applicable
	 */
	public String getPohExitInfo(int destination, List<PathStep> path, int currentIndex)
	{
		return pohService.getPohExitInfo(destination, path, currentIndex, this::transportsForEdge);
	}

	private String simplify(String text)
	{
		return Text.removeTags(text).toLowerCase()
			.replaceAll("[^a-zA-Z ]", "")
			.replace(" ", "_")
			.replace("__", "_");
	}

	private void onMenuOptionClicked(MenuEntry entry)
	{
		if (entry.getOption().equals(SET) && entry.getTarget().equals(TARGET))
		{
			setTarget(getSelectedWorldPoint());
		}
		else if (entry.getOption().equals(SET) && pathfinder != null && entry.getTarget().equals(TARGET +
			ColorUtil.wrapWithColorTag(" " + (pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET)))
		{
			setTarget(getSelectedWorldPoint(), true);
		}
		else if (entry.getOption().equals(SET) && entry.getTarget().equals(START))
		{
			setStart(getSelectedWorldPoint());
		}
		else if (entry.getOption().equals(CLEAR) && entry.getTarget().equals(PATH))
		{
			setTarget(WorldPointUtil.UNDEFINED);
		}
		else if (entry.getOption().equals(FIND_CLOSEST))
		{
			setTargets(pathfinderConfig.getDestinations(simplify(entry.getTarget())), true);
		}
	}

	private int getSelectedWorldPoint()
	{
		if (client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null)
		{
			if (client.getTopLevelWorldView().getSelectedSceneTile() != null)
			{
				return WorldPointUtil.fromLocalInstance(client, client.getTopLevelWorldView().getSelectedSceneTile().getLocalLocation());
			}
		}
		else
		{
			Point menuPoint = lastMenuOpenedPoint;
			return client.isMenuOpen() && menuPoint != null
				? calculateMapPoint(menuPoint.getX(), menuPoint.getY())
				: calculateMapPoint(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());
		}
		return WorldPointUtil.UNDEFINED;
	}

	private void setTarget(int target)
	{
		setTarget(target, false);
	}

	private void setTarget(int target, boolean append)
	{
		Set<Integer> targets = new HashSet<>();
		if (target != WorldPointUtil.UNDEFINED)
		{
			targets.add(target);
		}
		setTargets(targets, append);
	}

	private void setTargets(Set<Integer> targets, boolean append)
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
			restartPathfinding(append ? "add target" : "set target", start, destinations, append);
		}
	}

	private void setStart(int start)
	{
		if (pathfinder == null)
		{
			return;
		}
		startPointSet = true;
		restartPathfinding("set start", start, pathfinder.getTargets());
	}

	public int calculateMapPoint(int pointX, int pointY)
	{
		WorldMap worldMap = client.getWorldMap();
		float zoom = worldMap.getWorldMapZoom();
		int mapPoint = WorldPointUtil.packWorldPoint(worldMap.getWorldMapPosition().getX(), worldMap.getWorldMapPosition().getY(), 0);
		int middleX = mapWorldPointToGraphicsPointX(mapPoint);
		int middleY = mapWorldPointToGraphicsPointY(mapPoint);

		if (pointX == Integer.MIN_VALUE || pointY == Integer.MIN_VALUE ||
			middleX == Integer.MIN_VALUE || middleY == Integer.MIN_VALUE)
		{
			return WorldPointUtil.UNDEFINED;
		}

		final int dx = (int) ((pointX - middleX) / zoom);
		final int dy = (int) ((-(pointY - middleY)) / zoom);

		return WorldPointUtil.dxdy(mapPoint, dx, dy);
	}

	public int mapWorldPointToGraphicsPointX(int packedWorldPoint)
	{
		WorldMap worldMap = client.getWorldMap();

		float pixelsPerTile = worldMap.getWorldMapZoom();

		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map != null)
		{
			Rectangle worldMapRect = map.getBounds();

			int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);

			Point worldMapPosition = worldMap.getWorldMapPosition();

			int xTileOffset = WorldPointUtil.unpackWorldX(packedWorldPoint) + widthInTiles / 2 - worldMapPosition.getX();

			int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
			xGraphDiff += (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
			xGraphDiff += (int) worldMapRect.getX();

			return xGraphDiff;
		}
		return Integer.MIN_VALUE;
	}

	public int mapWorldPointToGraphicsPointY(int packedWorldPoint)
	{
		WorldMap worldMap = client.getWorldMap();

		float pixelsPerTile = worldMap.getWorldMapZoom();

		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map != null)
		{
			Rectangle worldMapRect = map.getBounds();

			int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

			Point worldMapPosition = worldMap.getWorldMapPosition();

			int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
			int yTileOffset = (yTileMax - WorldPointUtil.unpackWorldY(packedWorldPoint) - 1) * -1;

			int yGraphDiff = (int) (yTileOffset * pixelsPerTile);
			yGraphDiff -= (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
			yGraphDiff = worldMapRect.height - yGraphDiff;
			yGraphDiff += (int) worldMapRect.getY();

			return yGraphDiff;
		}
		return Integer.MIN_VALUE;
	}

	private void addMenuEntry(MenuEntryAdded event, String option, String target, int position)
	{
		List<MenuEntry> entries = new LinkedList<>(Arrays.asList(client.getMenu().getMenuEntries()));

		if (entries.stream().anyMatch(e -> e.getOption().equals(option) && e.getTarget().equals(target)))
		{
			return;
		}

		client.getMenu().createMenuEntry(position)
			.setOption(option)
			.setTarget(target)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setIdentifier(event.getIdentifier())
			.setType(MenuAction.RUNELITE)
			.onClick(this::onMenuOptionClicked);
	}

	private Widget getMinimapDrawWidget()
	{
		if (client.isResized())
		{
			if (client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1)
			{
				return client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
			}
			return client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
		}
		return client.getWidget(InterfaceID.Toplevel.MINIMAP);
	}

	private Shape getMinimapClipAreaSimple()
	{
		Widget minimapDrawArea = getMinimapDrawWidget();

		if (minimapDrawArea == null || minimapDrawArea.isHidden())
		{
			return null;
		}

		Rectangle bounds = minimapDrawArea.getBounds();

		return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
	}

	public Shape getMinimapClipArea()
	{
		Widget minimapWidget = getMinimapDrawWidget();

		if (minimapWidget == null || minimapWidget.isHidden() || !minimapRectangle.equals(minimapRectangle = minimapWidget.getBounds()))
		{
			minimapClipFixed = null;
			minimapClipResizeable = null;
			minimapSpriteFixed = null;
			minimapSpriteResizeable = null;
		}

		if (minimapWidget == null || minimapWidget.isHidden())
		{
			return null;
		}

		if (client.isResized())
		{
			if (minimapClipResizeable != null)
			{
				return minimapClipResizeable;
			}
			if (minimapSpriteResizeable == null)
			{
				minimapSpriteResizeable = spriteManager.getSprite(SpriteID.RESIZE_MAP_MASK, 0);
			}
			if (minimapSpriteResizeable != null)
			{
				minimapClipResizeable = bufferedImageToPolygon(minimapSpriteResizeable);
				return minimapClipResizeable;
			}
			return getMinimapClipAreaSimple();
		}
		if (minimapClipFixed != null)
		{
			return minimapClipFixed;
		}
		if (minimapSpriteFixed == null)
		{
			minimapSpriteFixed = spriteManager.getSprite(SpriteID.FIXED_MAP_MASK, 0);
		}
		if (minimapSpriteFixed != null)
		{
			minimapClipFixed = bufferedImageToPolygon(minimapSpriteFixed);
			return minimapClipFixed;
		}
		return getMinimapClipAreaSimple();
	}

	private Polygon bufferedImageToPolygon(BufferedImage image)
	{
		Color outsideColour = null;
		Color previousColour;
		final int width = image.getWidth();
		final int height = image.getHeight();
		List<java.awt.Point> points = new ArrayList<>();
		for (int y = 0; y < height; y++)
		{
			previousColour = outsideColour;
			for (int x = 0; x < width; x++)
			{
				int rgb = image.getRGB(x, y);
				int a = (rgb & 0xff000000) >>> 24;
				int r = (rgb & 0x00ff0000) >> 16;
				int g = (rgb & 0x0000ff00) >> 8;
				int b = (rgb & 0x000000ff);
				Color colour = new Color(r, g, b, a);
				if (x == 0 && y == 0)
				{
					outsideColour = colour;
					previousColour = colour;
				}
				if (!colour.equals(outsideColour) && previousColour.equals(outsideColour))
				{
					points.add(new java.awt.Point(x, y));
				}
				if ((colour.equals(outsideColour) || x == (width - 1)) && !previousColour.equals(outsideColour))
				{
					points.add(0, new java.awt.Point(x, y));
				}
				previousColour = colour;
			}
		}
		int offsetX = minimapRectangle.x;
		int offsetY = minimapRectangle.y;
		Polygon polygon = new Polygon();
		for (java.awt.Point point : points)
		{
			polygon.addPoint(point.x + offsetX, point.y + offsetY);
		}
		return polygon;
	}
}
