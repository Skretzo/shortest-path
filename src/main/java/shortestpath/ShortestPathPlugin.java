package shortestpath;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
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
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
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
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TransportAvailability;
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

	// POH (Player Owned House) bounds for detecting when path goes through POH
	// Note: POH_MIN_X is 1856 to exclude the Daddy's Home miniquest area
	private static final int POH_MIN_X = 1856;
	private static final int POH_MAX_X = 2047;
	private static final int POH_MIN_Y = 5696;
	private static final int POH_MAX_Y = 5767;
	private static final String PLUGIN_MESSAGE_PATH = "path";
	private static final String PLUGIN_MESSAGE_CLEAR = "clear";
	private static final String PLUGIN_MESSAGE_START = "start";
	private static final String PLUGIN_MESSAGE_TARGET = "target";
	private static final String PLUGIN_MESSAGE_CONFIG_OVERRIDE = "config";
	private static final String PLUGIN_MESSAGE_TRANSPORTS = "transports";
	private static final String CLEAR = "Clear";
	private static final String PATH = ColorUtil.wrapWithColorTag("Path", JagexColors.MENU_TARGET);
	private static final String SET = "Set";
	private static final String FIND_CLOSEST = "Find closest";
	private static final String FLASH_ICONS = "Flash icons";
	private static final String START = ColorUtil.wrapWithColorTag("Start", JagexColors.MENU_TARGET);
	private static final String TARGET = ColorUtil.wrapWithColorTag("Target", JagexColors.MENU_TARGET);
	private static final BufferedImage MARKER_IMAGE = ImageUtil.loadImageResource(ShortestPathPlugin.class, "/marker.png");
	private static final Pattern TRANSPORT_OPTIONS_REGEX = Pattern.compile("^(avoidWilderness|includeBankPath|currencyThreshold|use\\w+|cost\\w+)$");
	private static final Map<String, Object> configOverride = new HashMap<>(50);
	private static final Pattern SPIRIT_TREE_LABEL_PATTERN_MENU = Pattern.compile("<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)");
	private static final Pattern SPIRIT_TREE_LABEL_PATTERN_MENU_NEW = Pattern.compile("<col=ffffff>(.+)</col>: (<col=5f5f5f>)?(.+)");
	private final List<PendingTask> pendingTasks = new ArrayList<>(3);
	private final Object pathfinderMutex = new Object();
	boolean drawCollisionMap;
	boolean drawMap;
	boolean drawMinimap;
	boolean drawTiles;
	boolean drawTransports;
	boolean showTransportInfo;
	boolean showBankPickupInfo;
	Color colourCollisionMap;
	Color colourPath;
	Color colourPathCalculating;
	Color colourPathUnreachable;
	Color colourText;
	Color colourTransports;
	Color colourTeleportPulse;
	boolean showTeleportPulse;
	int tileCounterStep;
	int unreachableTargetDistance;
	String unreachableText;
	TileCounter showTileCounter;
	TileStyle pathStyle;
	@Inject
	private Client client;
	@Getter
	@Inject
	private ClientThread clientThread;
	@Inject
	private ShortestPathConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Gson gson;
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
	private DebugOverlayPanel debugOverlayPanel;
	@Inject
	private SpriteManager spriteManager;
	@Inject
	private WorldMapPointManager worldMapPointManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private ClientToolbar clientToolbar;
	// Alternative-routes feature: panel, async route generator, the methods the user has excluded, the
	// generated routes, and which one is currently shown on the map.
	private ShortestPathPanel altPanel;
	private NavigationButton navButton;
	private AlternativeRoutesService altRoutesService;
	private static final String CONFIG_KEY_EXCLUSIONS = "alternativeRoutesExclusions";
	private static final String CONFIG_KEY_MODE = "alternativeRoutesMode";
	private final Set<TeleportMethod> userExclusions = ConcurrentHashMap.newKeySet();
	// Which methods the alternatives consider: carried (default), carried + bank, or every teleport.
	private volatile AlternativeRoutesMode routesMode = AlternativeRoutesMode.OWNED_INVENTORY;
	// How many alternative routes to generate; grows when the user asks for more.
	// Initialised from config in startUp (config is not injected at field-init time).
	private int routeLimit = AlternativeRoutesService.MAX_ROUTES;
	// Whether the last generation hit the limit (so more routes may exist).
	private volatile boolean moreRoutesLikely = false;
	private volatile List<RouteOption> alternativeRoutes = new ArrayList<>();
	private volatile List<TeleportMethod> teleportCatalog = new ArrayList<>();
	// Catalog methods the player can't use in the current mode, mapped to why (for the panel markers).
	private volatile Map<TeleportMethod, MethodAvailability> unavailableMethods = Map.of();
	private volatile RouteOption selectedRoute;
	// Start/targets the alternatives were last generated from, reused by exclusion/mode/show-more edits
	// so they re-run against the same destination. Volatile: read/written from client thread + Swing EDT.
	private volatile int lastAltStart = WorldPointUtil.UNDEFINED;
	private volatile Set<Integer> lastAltTargets = Set.of();
	// Whether the client knows the bank's contents this session (the bank container is only populated
	// once the bank has been opened). Used by the panel to explain why Bank mode finds nothing.
	private volatile boolean bankContentsKnown = false;
	// True while a generation is computing for the current target. Used to suppress the classic-path
	// fallback on the map until the first alternative streams in, so the displayed path never flashes
	// a route that the mode's list won't contain (the classic path follows the SP config, not the mode).
	private volatile boolean altGenerationInFlight = false;
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
	@Getter
	private Pathfinder pathfinder;
	@Getter
	private PathfinderConfig pathfinderConfig;
	@Getter
	private boolean startPointSet = false;
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

	/**
	 * Checks if the given coordinates are inside the POH (Player Owned House) area.
	 *
	 * @param x The world X coordinate
	 * @param y The world Y coordinate
	 * @return true if inside POH, false otherwise
	 */
	public static boolean isInsidePoh(int x, int y)
	{
		return x >= POH_MIN_X && x <= POH_MAX_X && y >= POH_MIN_Y && y <= POH_MAX_Y;
	}

	public static boolean override(String configOverrideKey, boolean defaultValue)
	{
		if (!configOverride.isEmpty())
		{
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof Boolean)
			{
				return (boolean) value;
			}
		}
		return defaultValue;
	}

	/**
	 * Override for TransportType enabled state using the config key name stored in the enum.
	 */
	public static boolean override(TransportType type, boolean defaultValue)
	{
		String key = type.getEnabledKey();
		return key != null ? override(key, defaultValue) : defaultValue;
	}

	/**
	 * Override for TransportType cost threshold using the config key name stored in the enum.
	 */
	public static int override(TransportType type, int defaultValue)
	{
		String key = type.getCostKey();
		return key != null ? override(key, defaultValue) : defaultValue;
	}

	public static int override(String configOverrideKey, int defaultValue)
	{
		if (!configOverride.isEmpty())
		{
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof Integer)
			{
				return (int) value;
			}
		}
		return defaultValue;
	}

	public static TeleportationItem override(String configOverrideKey, TeleportationItem defaultValue)
	{
		if (!configOverride.isEmpty())
		{
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof String)
			{
				TeleportationItem teleportationItem = TeleportationItem.fromType((String) value);
				if (teleportationItem != null)
				{
					return teleportationItem;
				}
			}
		}
		return defaultValue;
	}

	public static JewelleryBoxTier override(String configOverrideKey, JewelleryBoxTier defaultValue)
	{
		if (!configOverride.isEmpty())
		{
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof String)
			{
				JewelleryBoxTier tier = JewelleryBoxTier.fromType((String) value);
				if (tier != null)
				{
					return tier;
				}
			}
		}
		return defaultValue;
	}

	@Provides
	public ShortestPathConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ShortestPathConfig.class);
	}

	@Override
	protected void startUp()
	{
		cacheConfigValues();

		pathfinderConfig = new PathfinderConfig(client, config);
		if (GameState.LOGGED_IN.equals(client.getGameState()))
		{
			clientThread.invokeLater(pathfinderConfig::refresh);
		}

		overlayManager.add(pathOverlay);
		overlayManager.add(pathMinimapOverlay);
		overlayManager.add(pathMapOverlay);
		overlayManager.add(pathMapTooltipOverlay);

		if (config.drawDebugPanel())
		{
			overlayManager.add(debugOverlayPanel);
		}

		loadExclusions();
		loadRoutesMode();
		routeLimit = defaultRouteLimit();
		altPanel = new ShortestPathPanel(this);
		altRoutesService = new AlternativeRoutesService(clientThread, pathfinderConfig.copyForPlanning());
		navButton = NavigationButton.builder()
			.tooltip("Alternative routes")
			.icon(MARKER_IMAGE)
			.priority(70)
			.panel(altPanel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Populate the teleport-methods catalog so it's visible before any target is set, and check
		// whether the bank contents are already known this session.
		if (GameState.LOGGED_IN.equals(client.getGameState()))
		{
			clientThread.invokeLater(() ->
			{
				ItemContainer liveBank = client.getItemContainer(InventoryID.BANK);
				if (liveBank != null && liveBank.getItems().length > 0)
				{
					pathfinderConfig.bank = liveBank;
					pathfinderConfig.setBankSnapshot(liveBank.getItems());
					bankContentsKnown = true;
				}
			});
			triggerAlternatives(WorldPointUtil.UNDEFINED, new HashSet<>());
		}

		keyManager.registerKeyListener(clearPathKeylistener);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(pathOverlay);
		overlayManager.remove(pathMinimapOverlay);
		overlayManager.remove(pathMapOverlay);
		overlayManager.remove(pathMapTooltipOverlay);
		overlayManager.remove(debugOverlayPanel);

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (altRoutesService != null)
		{
			altRoutesService.shutdown();
			altRoutesService = null;
		}

		if (pathfindingExecutor != null)
		{
			pathfindingExecutor.shutdownNow();
			pathfindingExecutor = null;
		}

		keyManager.unregisterKeyListener(clearPathKeylistener);
	}

	public void restartPathfinding(int start, Set<Integer> ends, boolean canReviveFiltered)
	{
		synchronized (pathfinderMutex)
		{
			if (pathfinder != null)
			{
				pathfinder.cancel();
				pathfinderFuture.cancel(true);
			}

			if (pathfindingExecutor == null)
			{
				ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
				pathfindingExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
			}
		}

		getClientThread().invokeLater(() ->
		{
			// The panel's method catalog is the single customization surface: methods the user has
			// excluded there are also excluded from the classic path.
			pathfinderConfig.setExcludedMethods(getUserExclusions());
			pathfinderConfig.refresh();
			pathfinderConfig.filterLocations(ends, canReviveFiltered);
			synchronized (pathfinderMutex)
			{
				if (ends.isEmpty())
				{
					setTarget(WorldPointUtil.UNDEFINED);
				}
				else
				{
					pathfinder = new Pathfinder(pathfinderConfig, start, ends, this::postPluginMessages);
					pathfinderFuture = pathfindingExecutor.submit(pathfinder);
				}
			}
		});
	}

	public void restartPathfinding(int start, Set<Integer> ends)
	{
		restartPathfinding(start, ends, true);
	}

	public boolean isNearPath(int location)
	{
		List<PathStep> path;
		if (pathfinder == null || (path = pathfinder.getPath()) == null || path.isEmpty() ||
			config.recalculateDistance() < 0 || lastLocation == (lastLocation = location))
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
		// A displayed alternative route is a static snapshot: colour it from its own endpoint,
		// never by the classic pathfinder's background re-anchoring (which would otherwise flash the drawn
		// route blue every time you walk far enough to trigger a recalculation).
		RouteOption displayed = getDisplayedRoute();
		if (displayed != null)
		{
			return isRouteEndTooFar(displayed) ? colourPathUnreachable : colourPath;
		}

		if (pathfinder == null || !pathfinder.isDone())
		{
			return colourPathCalculating;
		}

		List<PathStep> path = pathfinder.getPath();
		if (path == null || path.isEmpty() || pathfinder.getTargets().isEmpty())
		{
			return colourPath;
		}

		if (isPathUnreachable())
		{
			return colourPathUnreachable;
		}

		return colourPath;
	}

	/**
	 * Mirrors {@link #isPathUnreachable()}'s tolerance for a displayed alternative route: a route that
	 * stops at the closest reachable tile (e.g. because the exact target tile is an NPC/object spot)
	 * still counts as reached for colouring while its endpoint is within the configured
	 * unreachable-distance threshold — only genuinely far endpoints get the unreachable colour.
	 */
	private boolean isRouteEndTooFar(RouteOption route)
	{
		if (route.isReached())
		{
			return false;
		}
		List<PathStep> path = route.getPath();
		Set<Integer> targets = lastAltTargets;
		if (path == null || path.isEmpty() || targets.isEmpty())
		{
			return false;
		}
		int endPoint = path.get(path.size() - 1).getPackedPosition();
		int closestTargetDistance = Integer.MAX_VALUE;
		for (int target : targets)
		{
			closestTargetDistance = Math.min(closestTargetDistance, WorldPointUtil.distanceBetween(target, endPoint));
		}
		return closestTargetDistance > unreachableTargetDistance;
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

		return closestTargetDistance > unreachableTargetDistance;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		cacheConfigValues();

		if ("drawDebugPanel".equals(event.getKey()))
		{
			if (config.drawDebugPanel())
			{
				overlayManager.add(debugOverlayPanel);
			}
			else
			{
				overlayManager.remove(debugOverlayPanel);
			}
			return;
		}

		// Transport option changed; rerun pathfinding
		if ("defaultRouteCount".equals(event.getKey()))
		{
			routeLimit = defaultRouteLimit();
		}

		if (TRANSPORT_OPTIONS_REGEX.matcher(event.getKey()).find())
		{
			if (pathfinder != null)
			{
				restartPathfinding(pathfinder.getStart(), pathfinder.getTargets());
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (pathfinderConfig == null
			|| !GameState.LOGGING_IN.equals(lastLastGameState)
			|| !GameState.LOADING.equals(lastLastGameState = lastGameState)
			|| !GameState.LOGGED_IN.equals(lastGameState = event.getGameState()))
		{
			lastLastGameState = lastGameState;
			lastGameState = event.getGameState();
			return;
		}

		pendingTasks.add(new PendingTask(client.getTickCount() + 1, pathfinderConfig::refresh));
		// Refresh the teleport-methods catalog (and any current routes) now that game state is available.
		pendingTasks.add(new PendingTask(client.getTickCount() + 1, this::recomputeAlternatives));
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
			Map<String, Object> configOverride = (objConfigOverride instanceof Map<?, ?>) ? ((Map<String, Object>) objConfigOverride) : null;
			if (configOverride != null && !configOverride.isEmpty())
			{
				ShortestPathPlugin.configOverride.clear();
				for (String key : configOverride.keySet())
				{
					ShortestPathPlugin.configOverride.put(key, configOverride.get(key));
				}
				cacheConfigValues();
			}

			if (objStart == null && objTarget == null)
			{
				return;
			}

			int start = (objStart instanceof WorldPoint) ? WorldPointUtil.packWorldPoint((WorldPoint) objStart)
				: ((objStart instanceof Integer) ? ((int) objStart) : WorldPointUtil.UNDEFINED);
			if (start == WorldPointUtil.UNDEFINED)
			{
				if (client.getLocalPlayer() == null)
				{
					return;
				}
				start = WorldPointUtil.packWorldPoint(client.getLocalPlayer().getWorldLocation());
			}

			Set<Integer> targets = new HashSet<>();
			if (objTarget instanceof Integer)
			{
				int packedPoint = (Integer) objTarget;
				if (packedPoint == WorldPointUtil.UNDEFINED)
				{
					return;
				}
				targets.add(packedPoint);
			}
			else if (objTarget instanceof WorldPoint)
			{
				int packedPoint = WorldPointUtil.packWorldPoint((WorldPoint) objTarget);
				if (packedPoint == WorldPointUtil.UNDEFINED)
				{
					return;
				}
				targets.add(packedPoint);
			}
			else if (objTarget instanceof Set<?>)
			{
				@SuppressWarnings("unchecked")
				Set<Object> objTargets = (Set<Object>) objTarget;
				for (Object obj : objTargets)
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
						return;
					}
					targets.add(packedPoint);
				}
			}

			boolean useOld = targets.isEmpty() && pathfinder != null;
			Set<Integer> ends = useOld ? new HashSet<>(pathfinder.getTargets()) : targets;
			// Alternatives are computed manually (the panel's "Find routes" button reads the current
			// target), so other-plugin requests like Quest Helper just set the path here.
			restartPathfinding(start, ends, useOld);
		}
		else if (PLUGIN_MESSAGE_CLEAR.equals(action))
		{
			configOverride.clear();
			cacheConfigValues();
			setTarget(WorldPointUtil.UNDEFINED);
		}
	}

	public void postPluginMessages()
	{
		if (pathfinder == null)
		{
			return;
		}
		if (override("postTransports", config.postTransports()))
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
					transportDisplayInfos.add(transport.getDisplayInfo());
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
		for (int i = 0; i < pendingTasks.size(); i++)
		{
			if (pendingTasks.get(i).check(client.getTickCount()))
			{
				pendingTasks.remove(i--).run();
			}
		}

		maybeAutoComputeAlternatives();

		Player localPlayer = client.getLocalPlayer();
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
			restartPathfinding(currentLocation, pathfinder.getTargets());
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
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		pathfinderConfig.bank = event.getItemContainer();
		// Snapshot the items now, while the bank is open: the client may empty the live container
		// (and thereby every reference to it) once the interface closes.
		pathfinderConfig.setBankSnapshot(event.getItemContainer().getItems());
		bankContentsKnown = true;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (pathfinder != null && event.getGroupId() == InterfaceID.FAIRYRINGS_LOG)
		{
			fairyRingPanelOpen = true;
		}

		// Populate spirit tree cache, but only once.
		// The values here almost never change, we only need to load it once.
		if (pathfinderConfig.availableSpiritTrees == null)
		{
			switch (event.getGroupId())
			{
				case InterfaceID.MENU:
					clientThread.invokeLater(() -> parseSpiritTreeWidget(false));
					break;
				case InterfaceID.MENU_NEW:
					clientThread.invokeLater(() -> parseSpiritTreeWidget(true));
					break;
			}
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

	private void parseSpiritTreeWidget(boolean useNewMenu)
	{
		// Referencing
		// https://github.com/trs/runelite-teleport-maps/blob/e006270494500ab8e4826903b377bb945ca9fc96/src/main/java/com/mjhylkema/TeleportMaps/components/adventureLog/SpiritTreeMap.java#L141

		Widget container;
		if (useNewMenu)
		{
			container = client.getWidget(InterfaceID.MENU_NEW, 9);
		}
		else
		{
			container = client.getWidget(InterfaceID.MENU, 3);
		}

		if (container == null)
		{
			return;
		}

		Widget[] children = container.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		// Tree Gnome Village is always the first row and always available;
		// quick length check before running the regex
		// Expected (old): "<col=735a28>1</col>: Tree Gnome Village" (length 39)
		// Expected (new): "<col=ffffff>1</col>: Tree Gnome Village" (length 39)
		String firstText = children[0].getText();
		if (firstText == null || firstText.length() != 39)
		{
			return;
		}

		Pattern pattern = useNewMenu ? SPIRIT_TREE_LABEL_PATTERN_MENU_NEW : SPIRIT_TREE_LABEL_PATTERN_MENU;

		Set<String> available = new HashSet<>();

		for (Widget child : children)
		{
			Matcher matcher = pattern.matcher(child.getText());
			if (!matcher.matches())
			{
				continue;
			}

			// Group 2 is the disabled color tag; if present, the tree is unavailable
			if (matcher.group(2) != null)
			{
				continue;
			}

			// Group 3 is spirit tree name
			available.add(matcher.group(3));
		}

		pathfinderConfig.availableSpiritTrees = available;

		if (pathfinder != null)
		{
			restartPathfinding(pathfinder.getStart(), pathfinder.getTargets());
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
	 * This is still only a fallback for display code and remains inherently ambiguous when multiple
	 * valid transports share the same origin/destination pair under the same edge state. The more
	 * structural fix would be to model reconstructed paths in terms of explicit edges, or otherwise
	 * carry richer per-edge metadata, instead of repeatedly re-deriving transport candidates from
	 * adjacent path steps.
	 * <p>
	 * Note that this function also performs filtering by the transport target, so callers of this
	 * function can directly iterate over the returned transports.
	 */
	public Set<Transport> transportsForEdge(PathStep currentStep, PathStep nextStep)
	{
		if (currentStep == null || nextStep == null)
		{
			return Set.of();
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
	 * Checks if the destination is inside POH and looks ahead in the path to find the exit transport.
	 * If the immediate exit leads to a fairy ring or other notable transport shortly after,
	 * that information is included instead.
	 *
	 * @param destination  The destination point to check
	 * @param path         The full path
	 * @param currentIndex The current index in the path
	 * @return The display info of the POH exit transport, or null if not applicable
	 */
	public String getPohExitInfo(int destination, List<PathStep> path, int currentIndex)
	{
		if (path == null || currentIndex < 0)
		{
			return null;
		}

		int destX = WorldPointUtil.unpackWorldX(destination);
		int destY = WorldPointUtil.unpackWorldY(destination);

		// Check if destination is inside POH
		if (!isInsidePoh(destX, destY))
		{
			return null;
		}

		String immediateExitInfo = null;

		// Look ahead in the path to find the next transport that exits POH
		for (int i = currentIndex + 1; i < path.size() - 1; i++)
		{
			int stepLocation = path.get(i).getPackedPosition();
			int nextLocation = path.get(i + 1).getPackedPosition();

			int stepX = WorldPointUtil.unpackWorldX(stepLocation);
			int stepY = WorldPointUtil.unpackWorldY(stepLocation);
			int nextX = WorldPointUtil.unpackWorldX(nextLocation);
			int nextY = WorldPointUtil.unpackWorldY(nextLocation);

			// Check if this step is inside POH but next step is outside (exit transport)
			boolean stepInsidePoh = isInsidePoh(stepX, stepY);
			boolean nextInsidePoh = isInsidePoh(nextX, nextY);

			if (stepInsidePoh && !nextInsidePoh)
			{
				// Found the exit transport - get its display info using bank-aware lookup
				PathStep currentStep = path.get(i);
				PathStep nextStep = path.get(i + 1);
				for (Transport transport : transportsForEdge(currentStep, nextStep))
				{
					String exitInfo = transport.getDisplayInfo();
					if (exitInfo != null && !exitInfo.isEmpty())
					{
						TransportType exitType = transport.getType();
						if (TransportType.TELEPORTATION_BOX.equals(exitType))
						{
							String objInfo = transport.getObjectInfo();
							if (objInfo != null && objInfo.contains("Amulet of Glory"))
							{
								immediateExitInfo = "Mounted Glory: " + exitInfo;
							}
							else if (objInfo != null && objInfo.contains("Mythical cape"))
							{
								immediateExitInfo = "Mythical Cape: " + exitInfo;
							}
							else if (objInfo != null && objInfo.contains("Xeric's Talisman"))
							{
								immediateExitInfo = "Xeric's Talisman: " + exitInfo;
							}
							else if (objInfo != null && objInfo.contains("Digsite"))
							{
								immediateExitInfo = "Digsite Pendant: " + exitInfo;
							}
							else
							{
								immediateExitInfo = "Jewelry Box: " + exitInfo;
							}
						}
						else if (TransportType.TELEPORTATION_PORTAL_POH.equals(exitType))
						{
							immediateExitInfo = "Nexus: " + exitInfo;
						}
						else if (TransportType.FAIRY_RING.equals(exitType))
						{
							immediateExitInfo = "Fairy Ring " + exitInfo;
						}
						else if (TransportType.SPIRIT_TREE.equals(exitType))
						{
							immediateExitInfo = "Spirit Tree: " + exitInfo;
						}
						else if (TransportType.WILDERNESS_OBELISK.equals(exitType))
						{
							immediateExitInfo = "Obelisk: " + exitInfo;
						}
						else
						{
							immediateExitInfo = exitInfo;
						}
					}
					break;
				}
				break;
			}

			// If we've left POH without finding a transport, stop looking
			if (!stepInsidePoh)
			{
				break;
			}
		}

		return immediateExitInfo;
	}

	private Color override(String configOverrideKey, Color defaultValue)
	{
		if (!configOverride.isEmpty())
		{
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof Color)
			{
				return (Color) value;
			}
		}
		return defaultValue;
	}

	private TileCounter override(String configOverrideKey, TileCounter defaultValue)
	{
		if (!configOverride.isEmpty())
		{
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof String)
			{
				TileCounter tileCounter = TileCounter.fromType((String) value);
				if (tileCounter != null)
				{
					return tileCounter;
				}
			}
		}
		return defaultValue;
	}

	private TileStyle override(String configOverrideKey, TileStyle defaultValue)
	{
		if (!configOverride.isEmpty())
		{
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof String)
			{
				TileStyle tileStyle = TileStyle.fromType((String) value);
				if (tileStyle != null)
				{
					return tileStyle;
				}
			}
		}
		return defaultValue;
	}

	private void cacheConfigValues()
	{
		drawCollisionMap = override("drawCollisionMap", config.drawCollisionMap());
		drawMap = override("drawMap", config.drawMap());
		drawMinimap = override("drawMinimap", config.drawMinimap());
		drawTiles = override("drawTiles", config.drawTiles());
		drawTransports = override("drawTransports", config.drawTransports());
		showTransportInfo = override("showTransportInfo", config.showTransportInfo());
		showBankPickupInfo = override("showBankPickupInfo", config.showBankPickupInfo());

		colourCollisionMap = override("colourCollisionMap", config.colourCollisionMap());
		colourPath = override("colourPath", config.colourPath());
		colourPathCalculating = override("colourPathCalculating", config.colourPathCalculating());
		colourPathUnreachable = override("colourPathUnreachable", config.colourPathUnreachable());
		colourText = override("colourText", config.colourText());
		colourTransports = override("colourTransports", config.colourTransports());
		colourTeleportPulse = override("colourTeleportPulse", config.colourTeleportPulse());

		tileCounterStep = override("tileCounterStep", config.tileCounterStep());
		unreachableTargetDistance = override("unreachableTargetDistanceThreshold", config.unreachableTargetDistance());
		unreachableText = config.unreachableText();

		showTileCounter = override("showTileCounter", config.showTileCounter());
		pathStyle = override("pathStyle", config.pathStyle());
		showTeleportPulse = override("showTeleportPulse", config.showTeleportPulse());
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
			return client.isMenuOpen()
				? calculateMapPoint(lastMenuOpenedPoint.getX(), lastMenuOpenedPoint.getY())
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
			}

			worldMapPointManager.removeIf(x -> x == marker);
			marker = null;
			startPointSet = false;
			selectedRoute = null;
			routeLimit = defaultRouteLimit();
			// Keep the teleport-methods catalog visible with no target selected.
			triggerAlternatives(WorldPointUtil.UNDEFINED, new HashSet<>());
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

			int start = WorldPointUtil.fromLocalInstance(client, localPlayer);
			lastLocation = start;
			if (startPointSet && pathfinder != null)
			{
				start = pathfinder.getStart();
			}
			Set<Integer> destinations = new HashSet<>(targets);
			if (pathfinder != null && append)
			{
				destinations.addAll(pathfinder.getTargets());
			}
			// Alternatives are computed manually via the panel's "Find routes" button.
			restartPathfinding(start, destinations, append);
		}
	}

	private void setStart(int start)
	{
		if (pathfinder == null)
		{
			return;
		}
		startPointSet = true;
		restartPathfinding(start, pathfinder.getTargets());
	}

	// --- Alternative-routes feature (driven by ShortestPathPanel) ---

	public RouteOption getSelectedRoute()
	{
		return selectedRoute;
	}

	/**
	 * The route currently being displayed: the explicitly selected one, or — when the alternatives
	 * were computed for the pathfinder's current destination — the first (best) route of the list
	 * under the currently selected mode. Null when neither applies (falls back to the classic path).
	 */
	public RouteOption getDisplayedRoute()
	{
		RouteOption route = selectedRoute;
		if (route != null)
		{
			return route;
		}
		List<RouteOption> routes = alternativeRoutes;
		if (routes.isEmpty())
		{
			return null;
		}
		// Only substitute the first alternative when it was computed for the current destination;
		// a stale list (target changed since "Find routes") must not override the live path.
		Pathfinder current = pathfinder;
		if (current == null || !lastAltTargets.equals(current.getTargets()))
		{
			return null;
		}
		return routes.get(0);
	}

	/**
	 * The path the overlays should draw: the displayed alternative route if any (the selected one,
	 * or by default the first route of the current alternatives list, so the drawn path reflects the
	 * chosen mode/exclusions), otherwise the classic live pathfinder path.
	 */
	public List<PathStep> getDisplayPath()
	{
		RouteOption route = getDisplayedRoute();
		if (route != null)
		{
			return route.getPath();
		}
		// While the alternatives for the current destination are still computing, draw nothing rather
		// than the classic path: the classic path follows the SP config (not the panel's mode) and can
		// use methods the list will never contain. The first streamed route replaces it in <1s.
		Pathfinder current = pathfinder;
		if (altGenerationInFlight && current != null && lastAltTargets.equals(current.getTargets()))
		{
			return List.of();
		}
		return current != null ? current.getPath() : List.of();
	}

	public List<RouteOption> getAlternativeRoutes()
	{
		return alternativeRoutes;
	}

	public Set<TeleportMethod> getUserExclusions()
	{
		return new HashSet<>(userExclusions);
	}

	public void selectRoute(int index)
	{
		List<RouteOption> routes = alternativeRoutes;
		if (index >= 0 && index < routes.size())
		{
			RouteOption route = routes.get(index);
			// Toggle: clicking the route that's already shown hides it.
			selectedRoute = (selectedRoute == route) ? null : route;
			refreshPanel(false);
		}
	}

	public void excludeMethod(TeleportMethod method)
	{
		if (method != null && userExclusions.add(method))
		{
			saveExclusions();
			triggerAlternatives(lastAltStart, new HashSet<>(lastAltTargets));
		}
	}

	public void includeMethod(TeleportMethod method)
	{
		if (method != null && userExclusions.remove(method))
		{
			saveExclusions();
			triggerAlternatives(lastAltStart, new HashSet<>(lastAltTargets));
		}
	}

	public void excludeMethods(Collection<TeleportMethod> methods)
	{
		boolean changed = false;
		if (methods != null)
		{
			for (TeleportMethod method : methods)
			{
				changed |= userExclusions.add(method);
			}
		}
		if (changed)
		{
			saveExclusions();
			triggerAlternatives(lastAltStart, new HashSet<>(lastAltTargets));
		}
	}

	public void includeMethods(Collection<TeleportMethod> methods)
	{
		boolean changed = false;
		if (methods != null)
		{
			for (TeleportMethod method : methods)
			{
				changed |= userExclusions.remove(method);
			}
		}
		if (changed)
		{
			saveExclusions();
			triggerAlternatives(lastAltStart, new HashSet<>(lastAltTargets));
		}
	}

	public void clearExclusions()
	{
		if (!userExclusions.isEmpty())
		{
			userExclusions.clear();
			saveExclusions();
			triggerAlternatives(lastAltStart, new HashSet<>(lastAltTargets));
		}
	}

	/**
	 * Manually (re)compute the alternative routes for whatever destination Shortest Path currently has
	 * set — read live from the active pathfinder. With no target set, just refreshes the methods catalog.
	 */
	public void recomputeAlternatives()
	{
		getClientThread().invokeLater(() ->
		{
			Pathfinder current = pathfinder;
			if (current != null && !current.getTargets().isEmpty())
			{
				int start = altStart(current);
				log.debug("[alt-routes] Find routes: SP target set, spStart={}, searchStart={}, target={}",
					WorldPointUtil.unpackWorldPoint(current.getStart()),
					WorldPointUtil.unpackWorldPoint(start),
					WorldPointUtil.unpackWorldPoint(current.getTargets().iterator().next()));
				routeLimit = defaultRouteLimit();
				triggerAlternatives(start, new HashSet<>(current.getTargets()));
			}
			else
			{
				log.debug("[alt-routes] Find routes: no SP target (pathfinder={})",
					current == null ? "null" : "targets-empty");
				triggerAlternatives(WorldPointUtil.UNDEFINED, new HashSet<>());
			}
		});
	}

	/**
	 * The start tile to search alternatives from: the player's current (instance-correct) location,
	 * matching what Shortest Path itself uses for recalculation, falling back to the pathfinder's own
	 * start. Must be called on the client thread.
	 */
	private int altStart(Pathfinder current)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			return WorldPointUtil.fromLocalInstance(client, localPlayer);
		}
		return current.getStart();
	}

	/**
	 * The configured number of routes to search for per query (clamped to the service's hard cap).
	 */
	private int defaultRouteLimit()
	{
		int configured = override("defaultRouteCount", config.defaultRouteCount());
		return Math.max(1, Math.min(configured, 25));
	}

	public boolean canLoadMoreRoutes()
	{
		return moreRoutesLikely;
	}

	public void loadMoreRoutes()
	{
		if (lastAltTargets.isEmpty() || !moreRoutesLikely)
		{
			return;
		}
		int step = Math.max(1, Math.min(override("routeCountStep", config.routeCountStep()), 25));
		routeLimit += step;
		triggerAlternatives(lastAltStart, new HashSet<>(lastAltTargets));
	}

	public AlternativeRoutesMode getRoutesMode()
	{
		return routesMode;
	}

	/**
	 * Whether the bank's contents are known this session (false until the bank has been opened once).
	 * Bank mode cannot see banked teleports until this is true — same constraint as Shortest Path's
	 * own INVENTORY_AND_BANK setting.
	 */
	public boolean isBankContentsKnown()
	{
		return bankContentsKnown;
	}

	public void setRoutesMode(AlternativeRoutesMode mode)
	{
		if (mode == null || this.routesMode == mode)
		{
			return;
		}
		this.routesMode = mode;
		saveRoutesMode();
		triggerAlternatives(lastAltStart, new HashSet<>(lastAltTargets));
	}

	/**
	 * Light auto-detect, run each game tick: when Shortest Path's destination changes (a new target set
	 * manually, by Quest Helper, on reaching the previous one, etc.) compute the alternatives once.
	 * Deliberately keyed on the target SET only — never on start/movement — so the live path recalcs
	 * that thrashed the old approach are ignored. If it ever misses, the panel's "Find routes" button
	 * forces a recompute.
	 */
	private void maybeAutoComputeAlternatives()
	{
		if (altRoutesService == null)
		{
			return;
		}
		Pathfinder current = pathfinder;
		Set<Integer> targets = (current != null) ? current.getTargets() : Set.of();
		if (targets.isEmpty() || targets.equals(lastAltTargets))
		{
			return;
		}
		routeLimit = defaultRouteLimit();
		triggerAlternatives(altStart(current), new HashSet<>(targets));
	}

	private void triggerAlternatives(int start, Set<Integer> targets)
	{
		if (altRoutesService == null)
		{
			return;
		}
		Set<Integer> ends = (targets == null) ? new HashSet<>() : new HashSet<>(targets);
		lastAltStart = start;
		lastAltTargets = Set.copyOf(ends);

		// Clear the previous routes immediately (the catalog stays); the new routes stream in one by
		// one as they are found. With no target this still streams just the teleport-methods catalog.
		alternativeRoutes = new ArrayList<>();
		moreRoutesLikely = false;
		altGenerationInFlight = !ends.isEmpty();
		final List<TeleportMethod> catalog = teleportCatalog;
		final boolean hasTarget = !ends.isEmpty();
		if (altPanel != null)
		{
			final Map<TeleportMethod, MethodAvailability> unavailable = unavailableMethods;
			SwingUtilities.invokeLater(() ->
				altPanel.displayRoutes(List.of(), catalog, unavailable, getUserExclusions(), true, hasTarget));
		}
		altRoutesService.generate(start, ends, userExclusions, routesMode, routeLimit, this::onAlternativeRoutesUpdate);
	}

	private void onAlternativeRoutesUpdate(List<RouteOption> routes, List<TeleportMethod> catalog,
		Map<TeleportMethod, MethodAvailability> unavailable, boolean done)
	{
		alternativeRoutes = routes;
		teleportCatalog = catalog;
		unavailableMethods = unavailable;
		if (done)
		{
			// If walking there is already on the list we've stopped at it, so there's nothing more to
			// find; otherwise "more likely" when the generation filled every requested slot.
			boolean stoppedAtWalk = routes.stream().anyMatch(RouteOption::isWalkOnly);
			moreRoutesLikely = !stoppedAtWalk && !routes.isEmpty() && routes.size() >= routeLimit;
			// Generation finished; if it produced nothing the classic path may draw again as fallback.
			altGenerationInFlight = false;
		}
		final boolean hasTarget = !lastAltTargets.isEmpty();
		SwingUtilities.invokeLater(() ->
		{
			if (selectedRoute != null && !routes.contains(selectedRoute))
			{
				selectedRoute = null;
			}
			if (altPanel != null)
			{
				altPanel.displayRoutes(routes, catalog, unavailable, getUserExclusions(), !done, hasTarget);
			}
		});
	}


	private void refreshPanel(boolean calculating)
	{
		final boolean hasTarget = !lastAltTargets.isEmpty();
		if (altPanel != null)
		{
			SwingUtilities.invokeLater(() ->
				altPanel.displayRoutes(alternativeRoutes, teleportCatalog, unavailableMethods,
					getUserExclusions(), calculating, hasTarget));
		}
	}

	private void saveExclusions()
	{
		try
		{
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_EXCLUSIONS,
				gson.toJson(new ArrayList<>(userExclusions)));
		}
		catch (Exception e)
		{
			log.warn("Failed to save alternative-route exclusions", e);
		}
	}

	private void loadExclusions()
	{
		try
		{
			String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_EXCLUSIONS);
			if (json == null || json.isEmpty())
			{
				return;
			}
			TeleportMethod[] saved = gson.fromJson(json, TeleportMethod[].class);
			if (saved != null)
			{
				for (TeleportMethod method : saved)
				{
					if (method != null && method.getType() != null)
					{
						userExclusions.add(method);
					}
				}
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load alternative-route exclusions", e);
		}
	}

	private void saveRoutesMode()
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MODE, routesMode.name());
	}

	private void loadRoutesMode()
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_MODE);
		if (value == null || value.isEmpty())
		{
			return;
		}
		try
		{
			routesMode = AlternativeRoutesMode.valueOf(value);
		}
		catch (IllegalArgumentException e)
		{
			// Legacy 3-mode names from before the Owned/All split.
			switch (value)
			{
				case "AVAILABLE":
					routesMode = AlternativeRoutesMode.OWNED_INVENTORY;
					break;
				case "AVAILABLE_WITH_BANK":
					routesMode = AlternativeRoutesMode.OWNED_WITH_BANK;
					break;
				case "ALL_TELEPORTS":
					routesMode = AlternativeRoutesMode.ALL_UNLOCKED;
					break;
				default:
					log.warn("Unknown alternative-routes mode '{}'", value);
					break;
			}
		}
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
