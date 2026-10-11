package shortestpath;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.coords.LocalPoint;
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
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import shortestpath.api.MenuVerbs;
import shortestpath.api.PluginMessageHandler;
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
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.Arrival;
import shortestpath.poh.PohService;
import shortestpath.presentation.PathAnnotator;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.sailing.SailingService;
import shortestpath.scheduler.PathRenderState;
import shortestpath.scheduler.PathScheduler;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.ConfigChange;
import shortestpath.settings.Effect;
import shortestpath.settings.Settings;
import shortestpath.spirittree.SpiritTreeService;
import shortestpath.transport.Transport;
import shortestpath.widgets.WidgetGeometry;

@Slf4j
@SuppressWarnings("SameParameterValue")
@PluginDescriptor(name = "Shortest Path", description = "Draws the shortest path to a chosen destination on the map<br>"
	+
	"Right click on the world map or shift right click a tile to use", tags = {"pathfinder", "map", "waypoint",
	"navigation"})
public class ShortestPathPlugin extends Plugin
{
	protected static final String CONFIG_GROUP = "shortestpath";

	private static final int NEXUS_DIALOG_REFRESH_ATTEMPTS = 10;

	@Inject
	private Client client;
	@Getter
	@Inject
	private ClientThread clientThread;
	@Inject
	private ShortestPathConfig config;
	@Inject
	private ConfigManager configManager;
	private long activeConfigProfileId;
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
	private KeyManager keyManager;
	@Inject
	private PohService pohService;
	@Inject
	private SpiritTreeService spiritTrees;
	@Inject
	private ItemStateService itemState;
	@Inject
	private SailingService sailing;
	@Inject
	private Settings settings;
	@Inject
	private PathScheduler scheduler;
	@Inject
	private RefreshCoordinator coordinator;
	@Inject
	private PathRenderState renderState;
	@Inject
	private WidgetGeometry geometry;
	@Inject
	private PathAnnotator annotator;
	@Inject
	private PluginMessageHandler apiHandler;
	@Inject
	private MenuVerbs menuVerbs;
	// The missing-items label of missingItemsSearch, built on the client thread when first drawn.
	private ActiveSearch missingItemsSearch;
	private String missingItemsText;
	@Getter
	private PathfinderConfig pathfinderConfig;
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
				scheduler.setTarget(WorldPointUtil.UNDEFINED);
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}
	};
	public ActiveSearch getActiveSearch()
	{
		return scheduler.getActiveSearch();
	}

	/**
	 * Legacy source-compatible accessor. Plugin internals use {@link #getActiveSearch()}.
	 */
	public Pathfinder getPathfinder()
	{
		return scheduler.getPathfinder();
	}

	@Provides
	public ShortestPathConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ShortestPathConfig.class);
	}

	@Override
	protected void startUp()
	{
		activeConfigProfileId = configManager.getProfile().getId();

		pathfinderConfig = new PathfinderConfig(client, config, settings);
		pathfinderConfig.setSpiritTreeService(spiritTrees);
		pathfinderConfig.setItemStateService(itemState);
		pathfinderConfig.setSailingService(sailing);
		scheduler.attach(pathfinderConfig, sailing, apiHandler);
		annotator.attach(pathfinderConfig);
		if (GameState.LOGGED_IN.equals(client.getGameState()))
		{
			// The profile load touches the same HashMaps the
			// queued refresh() iterates, so it must run on the client thread
			// too — doing it here on the EDT would race that refresh.
			clientThread.invokeLater(() ->
			{
				spiritTrees.loadFromProfile();
				pathfinderConfig.refresh();
			});
		}
		else
		{
			// No refresh is queued when logged out, so loading here is safe;
			// RuneScapeProfileChanged reloads once a profile is active anyway.
			spiritTrees.loadFromProfile();
		}

		overlayManager.add(pathOverlay);
		overlayManager.add(pathMinimapOverlay);
		overlayManager.add(pathMapOverlay);
		overlayManager.add(pathMapTooltipOverlay);
		overlayManager.add(bankItemHighlightOverlay);
		overlayManager.add(spellbookHighlightOverlay);
		overlayManager.add(inventoryHighlightOverlay);

		if (settings.lifecycle().drawDebugPanel())
		{
			overlayManager.add(debugOverlayPanel);
		}

		keyManager.registerKeyListener(clearPathKeylistener);
		pohService.loadFromProfile();
		scheduler.prepareBackend();
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

		scheduler.shutdown();

		keyManager.unregisterKeyListener(clearPathKeylistener);

		// Flush pending observations so the last tick's sample is not lost.
		spiritTrees.persistIfDirty();

		// The singleton survives restarts; drop the live bank ref so session
		// state cannot leak into the next enable.
		itemState.reset();
		// Same lifetime for the sailing snapshot — a stale aboard flag must
		// not greet the next session before its first refresh publishes.
		sailing.reset();
	}

	public Color getPathColor()
	{
		return renderState.getPathColor();
	}


	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		ConfigChange change = settings.onConfigChanged(event);
		if (change == null)
		{
			return;
		}

		if (configManager.getProfile().getId() != activeConfigProfileId)
		{
			return;
		}

		Set<Effect> effects = change.getEffects();
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

		coordinator.configChanged(change);
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		long profileId = configManager.getProfile().getId();
		if (profileId == activeConfigProfileId)
		{
			return;
		}

		activeConfigProfileId = profileId;
		syncDebugPanelVisibility();
		if (pathfinderConfig == null)
		{
			return;
		}

		if (!coordinator.profileChanged())
		{
			clientThread.invokeLater(pathfinderConfig::refresh);
		}
	}

	private void syncDebugPanelVisibility()
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

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		coordinator.gameStateChanged(event.getGameState(), client.getTickCount());
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
		coordinator.worldChanged(client.getTickCount());
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		pohService.loadFromProfile();
		// The new profile may carry different persisted trees, so an in-flight
		// path computed against the old account's set must be redone — the
		// load declares that route-invalidating effect unconditionally.
		spiritTrees.loadFromProfile();
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		apiHandler.onPluginMessage(event);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		geometry.menuOpened(client.getMouseCanvasPosition());
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		pohService.refreshFromDialog(client);
		pohService.persistIfDirty();

		coordinator.gameTick(client.getTickCount());

		Player localPlayer = client.getLocalPlayer();

		// The service notes the player's region, samples the in-region patch
		// varbit once the region has settled, and flushes persistence; a
		// resolved change is declared to the coordinator, which owns the
		// restart decision.
		spiritTrees.onGameTick();

		if (localPlayer == null || scheduler.getActiveSearch() == null)
		{
			return;
		}

		LocalPoint boatPoint = WorldPointUtil.boatLocation(client, localPlayer);
		WorldEntity boatEntity = WorldPointUtil.boat(client, localPlayer);
		coordinator.offRouteTick(client.getTickCount(),
			WorldPointUtil.fromLocalInstance(client, localPlayer),
			boatPoint == null ? WorldPointUtil.UNDEFINED : WorldPointUtil.fromLocalInstance(client, boatPoint),
			boatEntity == null ? WorldPointUtil.UNDEFINED
				: Math.floorMod((boatEntity.getTargetOrientation() + 64) / 128, 16));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		menuVerbs.onMenuEntryAdded(event);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		itemState.onContainerChanged(event.getContainerId(), event.getItemContainer());
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Rune pouch contents and the Lumbridge Elite diary feed the eligibility
		// snapshot but change without firing a container event.
		itemState.onVarbitChanged(event.getVarbitId());
		// The boat varbits feed the sailing snapshot and may declare a
		// route-invalidating change through the service.
		sailing.onVarbitChanged(event.getVarbitId());
	}

	/**
	 * The label naming the items the displayed route's target needs but the player was not found to
	 * hold, or {@code null} when nothing is missing. Built once per search, on the client thread.
	 */
	public String getMissingTargetItemsText()
	{
		ActiveSearch search = scheduler.getActiveSearch();
		if (search != missingItemsSearch)
		{
			missingItemsSearch = search;
			missingItemsText = null;
		}
		PathfinderResult result = search == null ? null : search.getResult();
		if (missingItemsText == null && result != null)
		{
			Arrival arrival = result.getArrival();
			List<ItemRequirement> missing = arrival == null ? List.of() : arrival.getMissingItems();
			missingItemsText = missing.isEmpty() ? "" : missingItemsLabel(missing);
		}
		return missingItemsText == null || missingItemsText.isEmpty() ? null : missingItemsText;
	}

	/** "Not found in inventory or bank: Spade, Rope or Knife", one entry per missing item group. */
	private String missingItemsLabel(List<ItemRequirement> missing)
	{
		List<String> groups = new ArrayList<>(missing.size());
		for (ItemRequirement group : missing)
		{
			List<String> names = new ArrayList<>();
			for (ItemRequirement.Branch branch : group.getBranches())
			{
				for (int id : branch.getItemIds())
				{
					// Requirement ids come from other plugins' messages; an invalid or renamed
					// id may resolve to no name rather than crash every rendered frame.
					ItemComposition def = client.getItemDefinition(id);
					String name = def == null ? null : def.getName();
					names.add(name == null || name.isEmpty() || "null".equals(name) ? "Item " + id : name);
				}
			}
			groups.add(String.join(" or ", names));
		}
		return "Not found in inventory or bank: " + String.join(", ", groups)
			+ (pathfinderConfig.getHeldItems().isBankSeen() ? "" : " (bank not checked)");
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
			pohService.putFromDialogLine(widget.getText());
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		annotator.widgetLoaded(event.getGroupId(), scheduler.getActiveSearch());

		if (event.getGroupId() == InterfaceID.TELENEXUS_TELEPORT)
		{
			pohService.dialogOpened();
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
				boolean changed = pohService.refreshFromDialog(client);
				return changed
					|| attempts[0] >= NEXUS_DIALOG_REFRESH_ATTEMPTS;
			});
		}

		// Refresh the spirit tree cache on every menu open: the menu is
		// authoritative for the planted patches it lists, and other detection
		// sources (in-region varbits, persisted profile state) fill in the rest.
		switch (event.getGroupId())
		{
			case InterfaceID.MENU:
				clientThread.invokeLater(() -> spiritTrees.onMenuOpened(false));
				break;
			case InterfaceID.MENU_NEW:
				clientThread.invokeLater(() -> spiritTrees.onMenuOpened(true));
				break;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		annotator.widgetClosed(event.getGroupId());
	}

	@Subscribe
	public void onPostClientTick(PostClientTick event)
	{
		annotator.postClientTick(scheduler.getActiveSearch());
	}

	public CollisionMap getMap()
	{
		return annotator.getMap();
	}

	public PrimitiveIntHashMap<Transport[]> getTransports()
	{
		return annotator.getTransports();
	}

	public PrimitiveIntHashMap<Transport[]> getAllDisplayTransports()
	{
		return annotator.getAllDisplayTransports();
	}

	/**
	 * Delegates to the presentation annotator; kept public for the overlay
	 * callers and the shell's own serializers.
	 */
	public Set<Transport> transportsForEdge(PathStep currentStep, PathStep nextStep)
	{
		return annotator.transportsForEdge(currentStep, nextStep);
	}

	public PathStep nextPathStep(List<PathStep> path, int index)
	{
		return annotator.nextPathStep(path, index);
	}

	/**
	 * Delegates to the presentation annotator; kept public for the overlay callers.
	 */
	public String formatTransportDisplay(Transport transport)
	{
		return annotator.formatTransportDisplay(transport);
	}

	/**
	 * Checks if the destination is inside POH and looks ahead in the path to find the exit transport.
	 * If the immediate exit leads to a fairy ring or other notable transport shortly after,
	 * that information is included instead.
	 * <p>
	 * Delegates to the presentation annotator; kept public for the overlay callers.
	 *
	 * @param destination  The destination point to check
	 * @param path         The full path
	 * @param currentIndex The current index in the path
	 * @return The display info of the POH exit transport, or null if not applicable
	 */
	public String getPohExitInfo(int destination, List<PathStep> path, int currentIndex)
	{
		return annotator.getPohExitInfo(destination, path, currentIndex);
	}

	public int calculateMapPoint(int pointX, int pointY)
	{
		return geometry.calculateMapPoint(pointX, pointY);
	}

	public int mapWorldPointToGraphicsPointX(int packedWorldPoint)
	{
		return geometry.mapWorldPointToGraphicsPointX(packedWorldPoint);
	}

	public int mapWorldPointToGraphicsPointY(int packedWorldPoint)
	{
		return geometry.mapWorldPointToGraphicsPointY(packedWorldPoint);
	}

	public Shape getMinimapClipArea()
	{
		return geometry.getMinimapClipArea();
	}
}
