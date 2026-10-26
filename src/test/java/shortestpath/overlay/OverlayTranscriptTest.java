package shortestpath.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.runelite.api.CameraFocusableEntity;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

import shortestpath.ItemVariations;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.BankPickupAnalysis;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.Targets;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.poh.PohService;
import shortestpath.presentation.BankPickupRequirements;
import shortestpath.presentation.PathAnnotator;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.sailing.SailingService;
import shortestpath.sailing.SailingState;
import shortestpath.scheduler.PathRenderState;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.widgets.WidgetGeometry;

/**
 * Golden-master transcripts over every overlay render path this phase touches:
 * the five {@code PathTileOverlay} style arms, {@code drawTransportInfo} arms,
 * {@code renderTransports}, the map/minimap/tooltip overlays, and the highlight
 * trio — each recorded through {@link RecordingGraphics2D} on the pre-change
 * tree so the consolidation proves call-for-call equality.
 * <p>
 * {@code Perspective} runs real against camera/viewport/tile-height stubs
 * (Mockito 3.4.6 cannot mock statics); the {@code @Before} calibration assert
 * guarantees a canned tile projects, so an empty transcript can never read as
 * a pass. Regenerate goldens for a deliberate draw change with
 * {@code TRANSCRIPT_REGEN=1 ./gradlew -p shortest-path test --tests '*OverlayTranscriptTest*'}
 * — see {@link TranscriptGolden}.
 */
public class OverlayTranscriptTest
{
	// Scene: base (3200,3200), 104x104 tiles. Canned path sits mid-scene.
	private static final int BASE = 3200;
	// Camera calibration (frozen — verified on-screen by the @Before assert):
	// level camera (pitch 0, yaw 0) 8 tiles south of the path start, 800 scale
	// on a 765x503 viewport puts every canned tile past the z1 >= 50 guard.
	private static final int CAM_X = (20 << 7) + 64;
	private static final int CAM_Y = ((20 << 7) + 64) - 1024;

	private static int packed(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	private static PathStep step(int x, int y)
	{
		return new PathStep(packed(x, y), null);
	}

	/** The shared five-step path: three walking tiles, a jump, one more tile. */
	private static List<PathStep> defaultPath()
	{
		return List.of(step(BASE + 20, BASE + 20), step(BASE + 21, BASE + 20),
			step(BASE + 22, BASE + 20), step(BASE + 35, BASE + 22), step(BASE + 36, BASE + 22));
	}

	private Client client;
	private WorldView wv;
	private PathScheduler scheduler;
	private ActiveSearch search;
	private PathAnnotator annotator;
	private ItemManager itemManager;
	private SailingService sailing;
	private SpriteManager spriteManager;
	private WidgetGeometry geometry;
	private TestShortestPathConfig config;
	private Settings settings;
	private PathRenderState renderState;
	private Map<Integer, Widget> widgets;
	private PathTileOverlay tileOverlay;
	private Injector injector;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		wv = mock(WorldView.class);
		Scene scene = mock(Scene.class);
		when(client.getTopLevelWorldView()).thenReturn(wv);
		when(client.getWorldView(anyInt())).thenReturn(wv);
		when(wv.getId()).thenReturn(WorldView.TOPLEVEL);
		when(wv.isTopLevel()).thenReturn(true);
		when(wv.isInstance()).thenReturn(false);
		when(wv.getPlane()).thenReturn(0);
		when(wv.getSizeX()).thenReturn(104);
		when(wv.getSizeY()).thenReturn(104);
		when(wv.getBaseX()).thenReturn(BASE);
		when(wv.getBaseY()).thenReturn(BASE);
		when(wv.getTileHeights()).thenReturn(new int[4][106][106]);
		when(wv.getTileSettings()).thenReturn(new byte[4][106][106]);
		when(wv.getScene()).thenReturn(scene);
		when(scene.getExtendedTileSettings()).thenReturn(new byte[4][184][184]);
		when(wv.getTileHeight(anyInt(), anyInt(), anyInt())).thenReturn(0);

		when(client.getScale()).thenReturn(800);
		when(client.getViewportWidth()).thenReturn(765);
		when(client.getViewportHeight()).thenReturn(503);
		when(client.getCameraX()).thenReturn(CAM_X);
		when(client.getCameraY()).thenReturn(CAM_Y);
		when(client.getCameraZ()).thenReturn(0);
		when(client.getCameraPitch()).thenReturn(0);
		when(client.getCameraYaw()).thenReturn(0);
		when(client.getMinimapZoom()).thenReturn(4.0);
		when(client.getCameraYawTarget()).thenReturn(0);
		when(client.isResized()).thenReturn(false);
		when(client.getMouseCanvasPosition()).thenReturn(new Point(-1, -1));

		CameraFocusableEntity focus = mock(CameraFocusableEntity.class);
		when(focus.getCameraFocus()).thenReturn(new LocalPoint(CAM_X, CAM_Y + 1024));
		when(client.getCameraFocusEntity()).thenReturn(focus);

		Player player = mock(Player.class);
		when(player.getLocalLocation()).thenReturn(new LocalPoint(CAM_X, CAM_Y + 1024));
		when(player.getWorldLocation()).thenReturn(new WorldPoint(BASE + 20, BASE + 20, 0));
		when(client.getLocalPlayer()).thenReturn(player);

		widgets = new HashMap<>();
		when(client.getWidget(anyInt())).thenAnswer(inv -> widgets.get(inv.getArgument(0)));
		when(client.getWidget(any(WidgetInfo.class)))
			.thenAnswer(inv -> widgets.get(((WidgetInfo) inv.getArgument(0)).getId()));

		search = mock(ActiveSearch.class);
		when(search.getPath()).thenReturn(defaultPath());
		when(search.getTargets()).thenReturn(Targets.at(Set.of(packed(BASE + 36, BASE + 22))));
		when(search.isDone()).thenReturn(true);
		when(search.isSailing()).thenReturn(false);
		// Mock default returns null here; render() iterates it at the
		// click-points gate — the stub is mandatory.
		when(search.getClickPoints()).thenReturn(List.of());

		scheduler = mock(PathScheduler.class);
		when(scheduler.getActiveSearch()).thenReturn(search);

		sailing = mock(SailingService.class);
		when(sailing.getState()).thenReturn(SailingState.EMPTY);

		config = new TestShortestPathConfig();
		settings = Settings.wrap(config);
		renderState = new PathRenderState(scheduler, sailing, settings);

		annotator = mock(PathAnnotator.class);
		when(annotator.getAllDisplayTransports()).thenReturn(new PrimitiveIntHashMap<>(8));
		when(annotator.getTransports()).thenReturn(new PrimitiveIntHashMap<>(8));
		when(annotator.nextPathStep(any(), anyInt())).thenAnswer(inv -> {
			List<PathStep> path = inv.getArgument(0);
			int i = inv.getArgument(1);
			return i >= 0 && i + 1 < path.size() ? path.get(i + 1) : null;
		});
		when(annotator.transportsForEdge(any(), any())).thenReturn(Set.of());

		itemManager = mock(ItemManager.class);
		// A real AsyncBufferedImage — a mock's null internals NPE the real
		// drawImage pipeline (BufImgSurfaceData) mid-render.
		AsyncBufferedImage img = new AsyncBufferedImage(mock(ClientThread.class), 8, 8,
			BufferedImage.TYPE_INT_ARGB);
		when(itemManager.getImage(anyInt())).thenReturn(img);
		when(itemManager.getItemOutline(anyInt(), anyInt(), any(Color.class)))
			.thenReturn(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
		ItemComposition def = mock(ItemComposition.class);
		when(def.getName()).thenReturn("Rune scimitar");
		when(client.getItemDefinition(anyInt())).thenReturn(def);

		spriteManager = mock(SpriteManager.class);
		geometry = WidgetGeometry.forTesting(client, spriteManager);

		injector = Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(client);
				bind(Settings.class).toInstance(settings);
				bind(PathRenderState.class).toInstance(renderState);
				bind(PathScheduler.class).toInstance(scheduler);
				bind(PathAnnotator.class).toInstance(annotator);
				bind(ItemManager.class).toInstance(itemManager);
				bind(WidgetGeometry.class).toInstance(geometry);
			}
		});
		tileOverlay = injector.getInstance(PathTileOverlay.class);

		// A canned tile must project — otherwise every transcript is empty and
		// the goldens pass trivially.
		assertNotNull("fixture calibration: canned tile must project",
			Perspective.getCanvasTilePoly(client, new LocalPoint(CAM_X, CAM_Y + 1024)));
	}

	private void override(String key, Object value)
	{
		settings.applyOverrides(Map.of(key, value));
	}

	private void check(String scenario, net.runelite.client.ui.overlay.Overlay overlay)
	{
		List<String> transcript = new ArrayList<>();
		BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = new RecordingGraphics2D(image.createGraphics(), transcript);
		overlay.render(g);
		TranscriptGolden.compareOrRegen(scenario, transcript);
	}

	private void checkTileOverlay(String scenario)
	{
		check(scenario, tileOverlay);
	}

	private static Transport transport(int origin, int destination, TransportType type, String info)
	{
		return new Transport.TransportBuilder()
			.origin(origin)
			.destination(destination)
			.type(type)
			.displayInfo(info)
			.build();
	}

	// ---------------------------------------------------------------
	// PathTileOverlay — the five style arms
	// ---------------------------------------------------------------

	@Test
	public void tilesDefault()
	{
		override("showTileCounter", "Travelled");
		checkTileOverlay("tiles-default");
	}

	@Test
	public void tilesInstanced()
	{
		// Two instance chunks mapping the same template chunk -> every canned
		// tile maps twice, pinning the all-mapped-points iteration (not the
		// first-point shortcut drawLine/tileCenter take).
		WorldView iwv = mock(WorldView.class);
		Scene scene = mock(Scene.class);
		int chunkData = (412 << 14) | (412 << 3); // template tiles [3296..3303]^2, plane 0, rot 0
		int[][][] chunks = new int[4][13][13];
		chunks[0][10][10] = chunkData;
		chunks[0][12][12] = chunkData;
		when(client.getTopLevelWorldView()).thenReturn(iwv);
		when(client.getWorldView(anyInt())).thenReturn(iwv);
		when(iwv.getId()).thenReturn(WorldView.TOPLEVEL);
		when(iwv.isTopLevel()).thenReturn(true);
		when(iwv.isInstance()).thenReturn(true);
		when(iwv.getPlane()).thenReturn(0);
		when(iwv.getSizeX()).thenReturn(104);
		when(iwv.getSizeY()).thenReturn(104);
		when(iwv.getBaseX()).thenReturn(BASE);
		when(iwv.getBaseY()).thenReturn(BASE);
		when(iwv.getTileHeights()).thenReturn(new int[4][106][106]);
		when(iwv.getTileSettings()).thenReturn(new byte[4][106][106]);
		when(iwv.getScene()).thenReturn(scene);
		when(scene.getExtendedTileSettings()).thenReturn(new byte[4][184][184]);
		when(iwv.getTileHeight(anyInt(), anyInt(), anyInt())).thenReturn(0);
		when(iwv.getInstanceTemplateChunks()).thenReturn(chunks);

		when(search.getPath()).thenReturn(List.of(step(3300, 3300), step(3301, 3300)));
		when(search.getTargets()).thenReturn(Targets.at(Set.of(packed(3301, 3300))));
		checkTileOverlay("tiles-instanced");
	}

	@Test
	public void lines()
	{
		override("pathStyle", "Lines");
		checkTileOverlay("lines");
	}

	@Test
	public void arrowLine()
	{
		// ArrowHead.draw's graphics.create()/dispose() tunnel lands here.
		override("pathStyle", "Arrow line");
		checkTileOverlay("arrow-line");
	}

	@Test
	public void turnMarkers()
	{
		override("pathStyle", "Turn markers");
		checkTileOverlay("turn-markers");
	}

	@Test
	public void tracer()
	{
		// The moving marker's index is wall-clock phased: tracerWindow caps at
		// 60, so a 61-step path makes every index have a successor (the
		// arrowhead always draws), and identical steps pin its coordinates.
		override("pathStyle", "Tracer");
		when(search.getPath()).thenReturn(Collections.nCopies(61, step(BASE + 20, BASE + 20)));
		checkTileOverlay("tracer");
	}

	@Test
	public void spriteMarkers()
	{
		override("pathStyle", "Sprite markers");
		checkTileOverlay("sprite-markers");
	}

	// ---------------------------------------------------------------
	// drawTransportInfo arms
	// ---------------------------------------------------------------

	@Test
	public void infoUnreachable()
	{
		// Target far past the unreachable threshold -> the banner label draws
		// and every step's drawTransportInfo early-returns.
		when(search.getTargets()).thenReturn(Targets.at(Set.of(packed(BASE + 95, BASE + 95))));
		checkTileOverlay("info-unreachable");
	}

	@Test
	public void infoMissingItems()
	{
		when(annotator.getMissingTargetItemsText(search))
			.thenReturn("Not found in inventory or bank: Spade");
		checkTileOverlay("info-missing-items");
	}

	@Test
	public void infoPohExit()
	{
		when(search.getPath()).thenReturn(List.of(
			step(1860, 7050), step(1861, 7050), step(1862, 7050)));
		when(search.getTargets()).thenReturn(Targets.at(Set.of(packed(1862, 7050))));
		when(client.getLocalPlayer().getWorldLocation()).thenReturn(new WorldPoint(1861, 7050, 0));
		when(annotator.getPohExitInfo(anyInt(), any(), anyInt()))
			.thenReturn("Nexus: Lumbridge Home Teleport");
		checkTileOverlay("info-poh-exit");
	}

	@Test
	public void infoBankPickup()
	{
		BankPickupRequirements.BankPickupResult pickup = BankPickupRequirements.BankPickupResult.format(
			client, new BankPickupAnalysis(List.of(List.of(Map.of(4151, 2L))), Set.of(4151)));
		when(annotator.getBankPickup(any(), anyInt(), any())).thenReturn(pickup);
		checkTileOverlay("info-bank-pickup");
	}

	@Test
	public void infoTransportLabels()
	{
		Set<Transport> transports = new LinkedHashSet<>(List.of(
			transport(packed(BASE + 20, BASE + 20), packed(BASE + 21, BASE + 20),
				TransportType.FAIRY_RING, "Fairy ring: DIQ"),
			transport(packed(BASE + 20, BASE + 20), packed(BASE + 21, BASE + 20),
				TransportType.SPIRIT_TREE, "Spirit tree: Gnome Stronghold")));
		when(annotator.transportsForEdge(any(), any())).thenReturn(transports);
		when(annotator.formatTransportDisplay(any()))
			.thenAnswer(inv -> ((Transport) inv.getArgument(0)).getDisplayInfo());
		checkTileOverlay("info-transport-labels");
	}

	@Test
	public void infoSailingDisembark()
	{
		SailingService live = SailingService.forTesting();
		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.gameState()).thenReturn(GameState.LOGGED_IN);
		when(source.varbit(VarbitID.SAILING_BOARDED_BOAT)).thenReturn(1);
		live.refresh(source);
		renderState = new PathRenderState(scheduler, live, settings);
		// PathTileOverlay's ctor is public — no rebinding needed.
		tileOverlay = new PathTileOverlay(client, itemManager, settings, renderState, scheduler, annotator);

		when(search.isSailing()).thenReturn(true);
		PathfinderResult result = mock(PathfinderResult.class);
		when(result.isReached()).thenReturn(false);
		when(search.getResult()).thenReturn(result);
		checkTileOverlay("info-sailing-disembark");
	}

	@Test
	public void infoTeleportPulse()
	{
		override("showTeleportPulse", true);
		Set<Transport> teleports = Set.of(
			transport(Transport.UNDEFINED_ORIGIN, packed(BASE + 35, BASE + 22),
				TransportType.TELEPORTATION_ITEM, "Amulet of glory"));
		when(annotator.transportsForEdge(any(), any())).thenReturn(teleports);
		checkTileOverlay("info-teleport-pulse");
	}

	// ---------------------------------------------------------------
	// renderTransports + the empty probe
	// ---------------------------------------------------------------

	@Test
	public void renderTransports()
	{
		override("drawTransports", true);
		PrimitiveIntHashMap<Transport[]> all = new PrimitiveIntHashMap<>(8);
		PrimitiveIntHashMap<Transport[]> available = new PrimitiveIntHashMap<>(8);
		Transport shuttle = transport(packed(BASE + 20, BASE + 20), packed(BASE + 30, BASE + 24),
			TransportType.FAIRY_RING, "Fairy ring");
		Transport upper = transport(packed(BASE + 20, BASE + 20),
			WorldPointUtil.packWorldPoint(BASE + 30, BASE + 24, 1), TransportType.SPIRIT_TREE, "Up");
		Transport noDest = transport(packed(BASE + 20, BASE + 20), Transport.UNDEFINED_DESTINATION,
			TransportType.SHIP, "No destination");
		Transport teleport = transport(Transport.UNDEFINED_ORIGIN, packed(BASE + 30, BASE + 24),
			TransportType.TELEPORTATION_ITEM, "Teleport");
		all.put(packed(BASE + 20, BASE + 20), new Transport[]{shuttle, upper, noDest});
		all.put(Transport.UNDEFINED_ORIGIN, new Transport[]{teleport});
		available.put(packed(BASE + 20, BASE + 20), new Transport[]{shuttle});
		when(annotator.getAllDisplayTransports()).thenReturn(all);
		when(annotator.getTransports()).thenReturn(available);
		checkTileOverlay("render-transports");
	}

	@Test
	public void emptyPath()
	{
		// Empty path, no targets, empty transport maps -> zero draw calls, no
		// NPE, no orphan labels.
		when(search.getPath()).thenReturn(List.of());
		when(search.getTargets()).thenReturn(Targets.NONE);
		checkTileOverlay("empty-path");
	}

	// ---------------------------------------------------------------
	// Map / minimap / tooltip overlays
	// ---------------------------------------------------------------

	private void withWorldMap()
	{
		WorldMap worldMap = mock(WorldMap.class);
		when(worldMap.getWorldMapZoom()).thenReturn(4f);
		when(worldMap.getWorldMapPosition()).thenReturn(new Point(BASE + 28, BASE + 21));
		when(client.getWorldMap()).thenReturn(worldMap);
		Widget map = mock(Widget.class);
		when(map.isHidden()).thenReturn(false);
		when(map.getBounds()).thenReturn(new Rectangle(0, 0, 500, 300));
		widgets.put(InterfaceID.Worldmap.MAP_CONTAINER, map);
	}

	@Test
	public void mapOverlay()
	{
		withWorldMap();
		check("map-overlay", injector.getInstance(PathMapOverlay.class));
	}

	@Test
	public void mapOverlayTransports()
	{
		override("drawTransports", true);
		withWorldMap();
		PrimitiveIntHashMap<Transport[]> all = new PrimitiveIntHashMap<>(8);
		PrimitiveIntHashMap<Transport[]> available = new PrimitiveIntHashMap<>(8);
		// Origin at the map-centre tile so the clip-containment gates pass.
		Transport shuttle = transport(packed(BASE + 28, BASE + 21), packed(BASE + 30, BASE + 22),
			TransportType.FAIRY_RING, "Fairy ring");
		Transport offMap = transport(packed(BASE + 28, BASE + 21), packed(BASE + 90, BASE + 90),
			TransportType.SPIRIT_TREE, "Far away");
		all.put(packed(BASE + 28, BASE + 21), new Transport[]{shuttle, offMap});
		available.put(packed(BASE + 28, BASE + 21), new Transport[]{shuttle});
		when(annotator.getAllDisplayTransports()).thenReturn(all);
		when(annotator.getTransports()).thenReturn(available);
		check("map-overlay-transports", injector.getInstance(PathMapOverlay.class));
	}

	@Test
	public void mapOverlayAbsent()
	{
		// No MAP_CONTAINER widget -> the early-return transcript.
		check("map-overlay-absent", injector.getInstance(PathMapOverlay.class));
	}

	@Test
	public void minimap()
	{
		Widget minimap = mock(Widget.class);
		when(minimap.isHidden()).thenReturn(false);
		when(minimap.getBounds()).thenReturn(new Rectangle(600, 40, 140, 140));
		when(minimap.getCanvasLocation()).thenReturn(new Point(600, 40));
		when(minimap.getWidth()).thenReturn(140);
		when(minimap.getHeight()).thenReturn(140);
		widgets.put(InterfaceID.Toplevel.MINIMAP, minimap);
		check("minimap", injector.getInstance(PathMinimapOverlay.class));
	}

	@Test
	public void mapTooltip()
	{
		withWorldMap();
		// Cursor inside the first step's map rect -> the tooltip draws with the
		// POH-exit suffix row.
		int cx = geometry.mapWorldPointToGraphicsPointX(packed(BASE + 20, BASE + 20));
		int cy = geometry.mapWorldPointToGraphicsPointY(packed(BASE + 20, BASE + 20));
		when(client.getMouseCanvasPosition()).thenReturn(new Point(cx, cy));
		Set<Transport> transports = Set.of(
			transport(packed(BASE + 20, BASE + 20), packed(BASE + 21, BASE + 20),
				TransportType.TELEPORTATION_PORTAL_POH, "Portal chamber"));
		when(annotator.transportsForEdge(any(), any())).thenReturn(transports);
		when(annotator.formatTransportDisplay(any()))
			.thenAnswer(inv -> ((Transport) inv.getArgument(0)).getDisplayInfo());
		when(annotator.getPohExitInfo(anyInt(), any(), anyInt())).thenReturn("Port Sarim");
		check("map-tooltip", injector.getInstance(PathMapTooltipOverlay.class));
	}

	// ---------------------------------------------------------------
	// Highlight trio — gated (zero draw calls) + populated
	// ---------------------------------------------------------------

	@Test
	public void highlightInventoryOff()
	{
		override("highlightInventoryItems", false);
		check("highlight-inventory-off", injector.getInstance(InventoryHighlightOverlay.class));
	}

	@Test
	public void highlightInventoryOn()
	{
		// A fairy ring ahead on the path pulls the dramen-staff ids into the
		// highlight set (Lumbridge Elite not done); one inventory child carries
		// a matching item id.
		int dramen = ItemVariations.fromName("DRAMEN_STAFF").getIds()[0];
		when(annotator.transportsForEdge(any(), any())).thenReturn(Set.of(
			transport(packed(BASE + 20, BASE + 20), packed(BASE + 21, BASE + 20),
				TransportType.FAIRY_RING, "Fairy ring")));
		Widget item = mock(Widget.class);
		when(item.isHidden()).thenReturn(false);
		when(item.getItemId()).thenReturn(dramen);
		when(item.getItemQuantity()).thenReturn(1);
		when(item.getBounds()).thenReturn(new Rectangle(10, 10, 30, 30));
		Widget inventory = mock(Widget.class);
		when(inventory.isHidden()).thenReturn(false);
		when(inventory.getChildren()).thenReturn(new Widget[]{item});
		widgets.put(InterfaceID.Inventory.ITEMS, inventory);
		check("highlight-inventory-on", injector.getInstance(InventoryHighlightOverlay.class));
	}

	@Test
	public void highlightBankOff()
	{
		override("highlightBankPickupItems", false);
		check("highlight-bank-off", injector.getInstance(BankItemHighlightOverlay.class));
	}

	@Test
	public void highlightBankOn()
	{
		BankPickupRequirements.BankPickupResult pickup = BankPickupRequirements.BankPickupResult.format(
			client, new BankPickupAnalysis(List.of(List.of(Map.of(4151, 1L))), Set.of(4151)));
		when(annotator.getBankPickup(any(), anyInt(), any())).thenReturn(pickup);
		when(annotator.isBankOpen()).thenReturn(true);
		when(annotator.getDestinations(eq("bank"))).thenReturn(Set.of(packed(BASE + 20, BASE + 20)));
		Widget item = mock(Widget.class);
		when(item.isHidden()).thenReturn(false);
		when(item.getItemId()).thenReturn(4151);
		when(item.getItemQuantity()).thenReturn(1);
		when(item.getBounds()).thenReturn(new Rectangle(20, 20, 30, 30));
		Widget bank = mock(Widget.class);
		when(bank.isHidden()).thenReturn(false);
		when(bank.getChildren()).thenReturn(new Widget[]{item});
		widgets.put(InterfaceID.Bankmain.ITEMS, bank);
		check("highlight-bank-on", injector.getInstance(BankItemHighlightOverlay.class));
	}

	@Test
	public void highlightSpellbookOff()
	{
		override("highlightSpellbookSpells", false);
		check("highlight-spellbook-off", injector.getInstance(SpellbookHighlightOverlay.class));
	}

	@Test
	public void highlightSpellbookOn()
	{
		when(annotator.transportsForEdge(any(), any())).thenReturn(Set.of(
			transport(Transport.UNDEFINED_ORIGIN, packed(BASE + 21, BASE + 20),
				TransportType.TELEPORTATION_SPELL, "Varrock Teleport")));
		Widget layer = mock(Widget.class);
		when(layer.isHidden()).thenReturn(false);
		widgets.put(InterfaceID.MagicSpellbook.SPELLLAYER, layer);
		Widget spell = mock(Widget.class);
		when(spell.isHidden()).thenReturn(false);
		when(spell.getBounds()).thenReturn(new Rectangle(30, 30, 24, 24));
		widgets.put(14286874, spell); // spell-widget-ids.tsv: Varrock Teleport
		check("highlight-spellbook-on", injector.getInstance(SpellbookHighlightOverlay.class));
	}
}
