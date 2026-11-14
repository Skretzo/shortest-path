package shortestpath.poh;

import java.util.*;
import javax.inject.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;
import shortestpath.WorldPointUtil;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.*;
import shortestpath.widgets.GameWidgets;

/**
 * Fills in the logged-in character's house settings from the player's own
 * house: each time the house loads, the built fairy ring, spirit tree,
 * obelisk, jewellery box, mounted items and portal chamber portals are read
 * from the scene once; the Portal Nexus destinations are read from the
 * nexus's own interfaces when they are opened there. Nothing is read in
 * building mode, where empty hotspots stand in the scene, or in a house
 * that is not known to be the player's own ({@link OwnHouseTracker}).
 * <p>
 * Values are written per character through {@link Settings#writeForCharacter}
 * and only while the detect setting is on, so the settings stay the
 * player's to set by hand when it is off.
 */
@Slf4j
@Singleton
public class PohDetector
{
	static final String NEXUS_SEEN_KEY = "pohNexusSeen";
	static final String CHAMBER_SEEN_KEY = "pohChamberSeen";
	private static final String NEXUS_SEEN_VERSION = "1";

	private final Client client;
	private final Settings settings;
	private final ConfigManager configManager;
	private final GameWidgets gameWidgets;
	private final RefreshCoordinator coordinator;
	private final PohService pohService;
	private final OwnHouseTracker tracker = new OwnHouseTracker();

	/** Ticks after a house load before the first look: the furniture arrives after the rooms. */
	static final int SETTLE_TICKS = 2;
	/** Ticks with no object spawning in the house before it counts as loaded. */
	static final int QUIET_TICKS = 2;
	/** A look happens by then even if objects keep spawning (a door, a servant's work). */
	static final int MAX_WAIT_TICKS = 10;

	private boolean scanPending;
	/** A load happened since the last tick; one inside the house area may have changed houses. */
	private boolean reloadSeen;
	/** The tick of the last load; a house teleport is measured to it. */
	private int loadTick;
	private int pendingSince;
	private int lastSpawnTick;
	/** The last scan of the house the player stands in, or null. */
	private HouseScan lastScan;
	// What the debug panel shows about the last look.
	private int lastScanTick = -1;
	private int lastObjectCount;
	private List<Integer> lastHouseObjects = List.of();
	/** The house features of the last look, where they stand. */
	private List<HouseObject> lastFeatures = List.of();
	/** This character's nexus destinations as last read, or null when never read. */
	private Set<PohNexusPortal> nexusSeen;
	/** The destinations the last nexus menu opened in this house listed, or null; kept until the player leaves. */
	private Set<PohNexusPortal> visitNexus;
	/** This character's own portal chamber destinations, read from storage once. */
	private Set<PohNexusPortal> ownChamber;
	private boolean ownChamberLoaded;
	private String lastOutcome = "";
	/** The nexus interface being read, or -1 when none is open. */
	private int openNexusGroup = -1;
	private final Set<PohNexusPortal> nexusRead = EnumSet.noneOf(PohNexusPortal.class);
	/** The open configuration interface has listed its slots, so an empty list now means none are set. */
	private boolean slotsRead;
	/** Ticks the open teleport menu's list has been shown. */
	private int menuTicks;
	/** A menu shown this long has built its lines; with none it has no destinations. */
	static final int EMPTY_MENU_TICKS = 3;

	@Inject
	public PohDetector(Client client, Settings settings, ConfigManager configManager, GameWidgets gameWidgets,
		RefreshCoordinator coordinator, PohService pohService)
	{
		this.pohService = pohService;
		this.client = client;
		this.settings = settings;
		this.configManager = configManager;
		this.gameWidgets = gameWidgets;
		this.coordinator = coordinator;
	}

	/**
	 * Routes with another player's house while the player stands in it and
	 * it has been read, and with the player's own settings otherwise; a
	 * switch either way reroutes. Cheap, so it runs every tick.
	 */
	private void syncVisit()
	{
		Map<ConfigKey, Object> values = Map.of();
		// Only a house known to be someone else's: one entered some unknown way may be the
		// player's own, whose stored settings (and saved nexus list) are then the better guess.
		if (tracker.isInOthersHouse() && lastScan != null && settings.bool(ConfigKey.DETECT_POH_SETTINGS))
		{
			values = visitValues(lastScan, visitNexus);
		}
		pohService.setOwnKeysApply(values.isEmpty());
		pohService.setChamberPortals(tracker.isInHouse() && lastScan != null && (tracker.isInOwnHouse() || !values.isEmpty())
			? lastScan.chamberPortals() : ownChamber());
		ConfigChange change = settings.visitHouse(values);
		if (change != null)
		{
			log.debug("routing with {}", values.isEmpty() ? "your own house" : "the house you are in");
			Player player = client.getLocalPlayer();
			coordinator.houseChanged(change, player == null ? WorldPointUtil.UNDEFINED
				: WorldPointUtil.fromLocalInstance(client, player));
		}
	}

	/**
	 * The house settings another player's house gives: what its read found,
	 * and its nexus destinations once its menu has been read here. Until
	 * then its nexus may hold any destination, so every one counts: a route
	 * that would gain from one leads to the nexus, whose menu is then read
	 * and the route found again with what it really has.
	 */
	static Map<ConfigKey, Object> visitValues(HouseScan scan, Set<PohNexusPortal> nexus)
	{
		Map<ConfigKey, Object> values = new HashMap<>();
		// Standing in the house, its teleports are there to use whatever the player's own house setting is.
		values.put(ConfigKey.USE_POH, true);
		values.put(ConfigKey.USE_POH_FAIRY_RING, scan.fairyRing());
		values.put(ConfigKey.USE_POH_SPIRIT_TREE, scan.spiritTree());
		values.put(ConfigKey.USE_POH_OBELISK, scan.obelisk());
		values.put(ConfigKey.POH_JEWELLERY_BOX_TIER, scan.boxTier());
		Set<PohMountedItem> mounted = EnumSet.noneOf(PohMountedItem.class);
		mounted.addAll(scan.mountedItems());
		values.put(ConfigKey.POH_MOUNTED_ITEMS, mounted);
		Set<PohNexusPortal> portals = EnumSet.noneOf(PohNexusPortal.class);
		portals.addAll(scan.chamberPortals());
		if (scan.nexus())
		{
			portals.addAll(nexus != null ? nexus : EnumSet.allOf(PohNexusPortal.class));
		}
		values.put(ConfigKey.POH_NEXUS_PORTALS, portals);
		return values;
	}

	public void menuClicked(MenuOptionClicked event)
	{
		String target = event.getMenuTarget();
		if (event.getMenuAction() == MenuAction.WIDGET_CONTINUE)
		{
			Widget widget = event.getWidget();
			target = widget == null ? "" : widget.getText();
		}
		tracker.menuClicked(event.getMenuOption(), target, client.getTickCount());
	}

	/** The game's answer to Configuration on another player's Portal Nexus. */
	static final String NOT_YOUR_PORTAL = "This is not your portal to configure.";

	/** A game message; the nexus refusing to be configured proves the house is someone else's. */
	public void gameMessage(String message)
	{
		if (message != null && NOT_YOUR_PORTAL.equals(Text.removeTags(message).trim()))
		{
			tracker.ownerOnlyActionRefused();
		}
	}

	/** A loading screen rebuilds the house, so it is looked at again once loaded. */
	public void gameStateChanged(GameState state)
	{
		if (state == GameState.LOADING)
		{
			// The old scene's objects are gone. What the house was found to hold (and a nexus read here)
			// stays: a reload in place is the same house, and a teleport into another one clears it.
			dropObjects();
			requestScan();
			reloadSeen = true;
			loadTick = client.getTickCount();
		}
		else if (state == GameState.CONNECTION_LOST)
		{
			tracker.connectionLost();
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// Leaving the world leaves any house: a route found with another player's house is found again.
			ConfigChange change = forget();
			if (change != null)
			{
				coordinator.configChanged(change);
			}
		}
	}

	/**
	 * An object spawned. The house's furniture is added after the rooms
	 * load, so spawns hold the look back until they stop; a house feature
	 * spawning after the look (built or loaded late) asks for another.
	 */
	public void objectSpawned(int objectId)
	{
		// Noted everywhere: the house's first spawns can come before the tick that sees the player inside.
		lastSpawnTick = client.getTickCount();
		if (tracker.isInHouse() && !scanPending && lastScan != null && HouseScan.isHouseFeature(objectId))
		{
			requestScan();
		}
	}

	private void requestScan()
	{
		scanPending = true;
		pendingSince = client.getTickCount();
	}

	public void gameTick()
	{
		int tick = client.getTickCount();
		boolean inHouse = isPlayerInHouse();
		boolean entered = tracker.tick(inHouse, tick);
		if (entered)
		{
			requestScan();
		}
		else if (reloadSeen && inHouse && tracker.reloaded(loadTick))
		{
			// Teleported from one house straight into another: nothing found in the last one holds.
			clearLook();
		}
		reloadSeen = false;
		if (!inHouse)
		{
			clearLook();
			scanPending = false;
			syncVisit();
			return;
		}
		if (openNexusGroup != -1)
		{
			readNexus();
		}
		// Every house is looked at once per load so the debug panel can show it; only one's own is applied.
		if (scanPending && !isBuildingMode() && readyToScan(tick, pendingSince, lastSpawnTick))
		{
			scanPending = false;
			List<HouseObject> features = new ArrayList<>();
			Set<Integer> ids = sceneObjectIds(features);
			lastFeatures = List.copyOf(features);
			nexusSeen = loadNexusSeen();
			HouseScan previous = lastScan;
			lastScan = HouseScan.of(ids);
			if (previous != null && !previous.sameHouse(lastScan))
			{
				// A different house than before the load, whatever the way in suggested: its nexus is unread.
				visitNexus = null;
			}
			lastScanTick = tick;
			lastObjectCount = ids.size();
			lastHouseObjects = houseObjects(ids);
			log.debug("house scan of {} objects, own {}: fairy ring {}, spirit tree {}, obelisk {}, nexus {}, box {},"
					+ " mounted {}, portals {}; house objects {}", ids.size(), tracker.isInOwnHouse(),
				lastScan.fairyRing(), lastScan.spiritTree(), lastScan.obelisk(), lastScan.nexus(),
				lastScan.boxTier(), lastScan.mountedItems(), lastScan.chamberPortals(), lastHouseObjects);
			if (tracker.isInOwnHouse())
			{
				apply(lastScan);
			}
			else
			{
				lastOutcome = "not applied (not yours)";
			}
		}
		syncVisit();
	}

	private boolean isBuildingMode()
	{
		return client.getVarbitValue(VarbitID.POH_BUILDING_MODE) != 0;
	}

	private void clearLook()
	{
		lastScan = null;
		lastScanTick = -1;
		lastObjectCount = 0;
		lastOutcome = "";
		visitNexus = null;
		dropObjects();
	}

	/** Lets go of the scene's objects, which a load unloads. */
	private void dropObjects()
	{
		lastHouseObjects = List.of();
		lastFeatures = List.of();
	}

	/**
	 * Whether the house has loaded: a few ticks have passed since the load
	 * and objects have stopped spawning, or the wait has run out.
	 */
	static boolean readyToScan(int tick, int pendingSince, int lastSpawnTick)
	{
		int waited = tick - pendingSince;
		if (waited >= MAX_WAIT_TICKS)
		{
			return true;
		}
		return waited >= SETTLE_TICKS && tick - lastSpawnTick >= QUIET_TICKS;
	}

	private static List<Integer> houseObjects(Set<Integer> ids)
	{
		List<Integer> found = new ArrayList<>();
		for (int id : ids)
		{
			if (HouseScan.isHouseFeature(id))
			{
				found.add(id);
			}
		}
		Collections.sort(found);
		return found;
	}

	public void widgetLoaded(int groupId)
	{
		if (groupId == InterfaceID.TELENEXUS)
		{
			tracker.ownerOnlyInterfaceOpened();
		}
		if (groupId == InterfaceID.TELENEXUS || groupId == InterfaceID.TELENEXUS_TELEPORT)
		{
			openNexusGroup = groupId;
			nexusRead.clear();
			slotsRead = false;
			menuTicks = 0;
		}
	}

	public void widgetClosed(int groupId)
	{
		if (groupId != openNexusGroup)
		{
			return;
		}
		// A teleport menu shown for a few ticks without a destination has none set up.
		boolean read = !nexusRead.isEmpty() || slotsRead || menuTicks >= EMPTY_MENU_TICKS;
		if (read)
		{
			visitNexus = EnumSet.noneOf(PohNexusPortal.class);
			visitNexus.addAll(nexusRead);
		}
		if (read && tracker.isInOwnHouse() && enabled())
		{
			Set<PohNexusPortal> seen = EnumSet.noneOf(PohNexusPortal.class);
			seen.addAll(nexusRead);
			if (!seen.equals(loadNexusSeen()))
			{
				saveNexusSeen(seen);
				nexusSeen = seen;
			}
		}
		// Opening the configuration can prove the house is one's own after the look, so apply it now.
		if (lastScan != null && tracker.isInOwnHouse())
		{
			apply(lastScan);
		}
		closeNexus();
	}

	/** What the detector knows about the house the player stands in, for the debug panel; null outside a house. */
	public HouseDebug debug()
	{
		if (!tracker.isInHouse())
		{
			return null;
		}
		String state;
		if (isBuildingMode())
		{
			state = "skipped in building mode";
		}
		else if (scanPending)
		{
			state = "waiting for furniture";
		}
		else if (lastScan == null)
		{
			state = "not read yet";
		}
		else
		{
			state = (client.getTickCount() - lastScanTick) + " ticks ago, " + lastOutcome;
		}
		return new HouseDebug(tracker.isInOwnHouse(), tracker.reason(), state, lastScan, lastObjectCount,
			lastFeatures, openNexusGroup != -1 ? sorted(nexusRead) : visitNexus,
			tracker.isInOwnHouse() ? nexusSeen : null, settings.isVisitingHouse(), isVisitedNexusUnread());
	}

	/**
	 * Whether routing uses another player's house whose nexus has not been
	 * opened here, so every nexus destination is assumed until it is.
	 */
	public boolean isVisitedNexusUnread()
	{
		return settings.isVisitingHouse() && lastScan != null && lastScan.nexus() && visitNexus == null;
	}

	/**
	 * The house features of the house the player stands in, where they
	 * stand, for highlighting; empty outside a house, in building mode
	 * (hotspots replace the furniture) or before the house is read.
	 */
	public List<HouseObject> houseObjects()
	{
		if (!tracker.isInHouse() || isBuildingMode())
		{
			return List.of();
		}
		return lastFeatures;
	}

	/** A copy in the destinations' own order, so a list shown from it keeps one order. */
	static Set<PohNexusPortal> sorted(Set<PohNexusPortal> portals)
	{
		Set<PohNexusPortal> copy = EnumSet.noneOf(PohNexusPortal.class);
		copy.addAll(portals);
		return copy;
	}

	/** Forgets the session: the plugin is stopping or the account changed. */
	public void reset()
	{
		forget();
	}

	/**
	 * Forgets the session and returns the routing change that leaving any
	 * visited house makes, or null; {@link #reset} drops it (the plugin is
	 * stopping, or the account change reroutes by itself).
	 */
	private ConfigChange forget()
	{
		tracker.reset();
		clearLook();
		scanPending = false;
		reloadSeen = false;
		nexusSeen = null;
		ownChamber = null;
		ownChamberLoaded = false;
		ConfigChange change = settings.visitHouse(Map.of());
		pohService.setOwnKeysApply(true);
		closeNexus();
		return change;
	}

	private void closeNexus()
	{
		openNexusGroup = -1;
		nexusRead.clear();
		slotsRead = false;
		menuTicks = 0;
	}

	/**
	 * Collects the destinations the open nexus interface lists. The lines
	 * are built over a few ticks, so each tick adds what has appeared; the
	 * set is used once the interface closes. The configuration interface
	 * is read from its slotted list only, never the list of teleports
	 * still available to add.
	 */
	private void readNexus()
	{
		List<String> lines = new ArrayList<>();
		if (openNexusGroup == InterfaceID.TELENEXUS_TELEPORT)
		{
			if (gameWidgets.isWidgetOpen(InterfaceID.TelenexusTeleport.TEXT1))
			{
				menuTicks++;
			}
			lines.addAll(gameWidgets.selfAndChildTexts(InterfaceID.TelenexusTeleport.TEXT1));
			lines.addAll(gameWidgets.selfAndChildTexts(InterfaceID.TelenexusTeleport.EXTRAS));
		}
		else
		{
			// Slots can be removed while the configuration is open, so its latest read replaces the last.
			// An empty list before the first read is one not built yet; after it, the last slot was removed.
			Set<PohNexusPortal> slots = NexusNames.portals(gameWidgets.selfAndChildTexts(InterfaceID.Telenexus.SLOTTED_LIST));
			if (!slots.isEmpty() || slotsRead)
			{
				nexusRead.clear();
				nexusRead.addAll(slots);
				slotsRead = true;
			}
			return;
		}
		nexusRead.addAll(NexusNames.portals(lines));
	}

	private void apply(HouseScan scan)
	{
		// Only labels use it, so it is kept current even when the settings are set by hand.
		saveOwnChamber(scan.chamberPortals());
		if (!enabled())
		{
			lastOutcome = "not applied (detect off)";
			return;
		}
		lastOutcome = settings.bool(ConfigKey.DETECT_POH_KEEP_EXTRA) ? "applied, kept extras" : "applied";
		boolean keep = settings.bool(ConfigKey.DETECT_POH_KEEP_EXTRA);
		write(ConfigKey.USE_POH_FAIRY_RING, scan.fairyRing(), keep);
		write(ConfigKey.USE_POH_SPIRIT_TREE, scan.spiritTree(), keep);
		write(ConfigKey.USE_POH_OBELISK, scan.obelisk(), keep);
		settings.writeForCharacter(ConfigKey.POH_JEWELLERY_BOX_TIER,
			boxTier(scan.boxTier(), settings.configured(ConfigKey.POH_JEWELLERY_BOX_TIER.getKey()), keep));
		Set<PohMountedItem> currentMounted = settings.configured(ConfigKey.POH_MOUNTED_ITEMS.getKey());
		settings.writeForCharacter(ConfigKey.POH_MOUNTED_ITEMS,
			union(PohMountedItem.class, scan.mountedItems(), keep ? currentMounted : Set.of()));
		Set<PohNexusPortal> current = settings.configured(ConfigKey.POH_NEXUS_PORTALS.getKey());
		settings.writeForCharacter(ConfigKey.POH_NEXUS_PORTALS,
			union(PohNexusPortal.class, housePortals(scan, loadNexusSeen(), current), keep ? current : Set.of()));
	}

	private void write(ConfigKey key, boolean detected, boolean keep)
	{
		settings.writeForCharacter(key, flag(detected, Boolean.TRUE.equals(settings.configured(key.getKey())), keep));
	}

	/**
	 * A feature is on when the house has it; with {@code keep}, a feature
	 * already on stays on although the house lacks it (the player uses one
	 * elsewhere, such as a friend's house).
	 */
	static boolean flag(boolean detected, boolean current, boolean keep)
	{
		return detected || (keep && current);
	}

	/** The house's box tier; with {@code keep}, a higher tier already set stays. */
	static JewelleryBoxTier boxTier(JewelleryBoxTier detected, JewelleryBoxTier current, boolean keep)
	{
		return keep && current != null && current.ordinal() > detected.ordinal() ? current : detected;
	}

	static <E extends Enum<E>> Set<E> union(Class<E> type, Set<E> detected, Set<E> kept)
	{
		Set<E> all = EnumSet.noneOf(type);
		all.addAll(detected);
		if (kept != null)
		{
			all.addAll(kept);
		}
		return all;
	}

	/**
	 * The house's portal destinations: the chamber's portals plus the
	 * nexus's. Until the nexus has been read for this character, the
	 * destinations already set stand in for it, so the first visit never
	 * drops a hand-picked nexus list.
	 */
	static Set<PohNexusPortal> housePortals(HouseScan scan, Set<PohNexusPortal> nexusSeen,
		Set<PohNexusPortal> current)
	{
		Set<PohNexusPortal> portals = EnumSet.noneOf(PohNexusPortal.class);
		portals.addAll(scan.chamberPortals());
		if (scan.nexus())
		{
			portals.addAll(nexusSeen != null ? nexusSeen : current);
		}
		return portals;
	}

	private boolean enabled()
	{
		return settings.bool(ConfigKey.DETECT_POH_SETTINGS);
	}

	private boolean isPlayerInHouse()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}
		int point = WorldPointUtil.fromLocalInstance(client, player);
		return point != WorldPointUtil.UNDEFINED
			&& PohService.isInsidePoh(WorldPointUtil.unpackWorldX(point), WorldPointUtil.unpackWorldY(point));
	}

	/**
	 * Every object id in the loaded scene, with the id each multiloc shows
	 * now. Run once per house load, never per tick.
	 */
	private Set<Integer> sceneObjectIds(List<HouseObject> features)
	{
		Set<Integer> ids = new HashSet<>();
		Scene scene = client.getTopLevelWorldView().getScene();
		for (Tile[][] plane : scene.getTiles())
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					for (GameObject object : tile.getGameObjects())
					{
						addObject(ids, features, object);
					}
					addObject(ids, features, tile.getWallObject());
					addObject(ids, features, tile.getDecorativeObject());
					addObject(ids, features, tile.getGroundObject());
				}
			}
		}
		return ids;
	}

	/**
	 * Notes an object's id and the id it shows now (a multiloc's current
	 * form), and keeps it with its position when it is a house feature.
	 * A game object larger than one tile sits on each of its tiles; it is
	 * kept once.
	 */
	private void addObject(Set<Integer> ids, List<HouseObject> features, TileObject object)
	{
		if (object == null)
		{
			return;
		}
		int id = object.getId();
		int shown = id;
		ObjectComposition definition = client.getObjectDefinition(id);
		if (definition != null && definition.getImpostorIds() != null)
		{
			ObjectComposition impostor = definition.getImpostor();
			if (impostor != null)
			{
				shown = impostor.getId();
			}
		}
		ids.add(id);
		ids.add(shown);
		Set<String> serves = new LinkedHashSet<>(HouseScan.serves(id));
		serves.addAll(HouseScan.serves(shown));
		if (serves.isEmpty())
		{
			return;
		}
		for (HouseObject kept : features)
		{
			if (kept.object() == object)
			{
				return;
			}
		}
		features.add(new HouseObject(object, HouseScan.isHouseFeature(shown) ? shown : id, serves));
	}

	/** This character's own portal chamber destinations as last read, or null when never read. */
	private Set<PohNexusPortal> ownChamber()
	{
		if (!ownChamberLoaded)
		{
			ownChamber = parseNexusSeen(configManager.getRSProfileConfiguration(Settings.CONFIG_GROUP, CHAMBER_SEEN_KEY));
			ownChamberLoaded = true;
		}
		return ownChamber;
	}

	private void saveOwnChamber(Set<PohNexusPortal> portals)
	{
		Set<PohNexusPortal> copy = sorted(portals);
		if (!copy.equals(ownChamber()))
		{
			ownChamber = copy;
			configManager.setRSProfileConfiguration(Settings.CONFIG_GROUP, CHAMBER_SEEN_KEY, serializeNexusSeen(copy));
		}
	}

	/** The nexus destinations last read for this character, or null when never read. */
	Set<PohNexusPortal> loadNexusSeen()
	{
		return parseNexusSeen(configManager.getRSProfileConfiguration(Settings.CONFIG_GROUP, NEXUS_SEEN_KEY));
	}

	private void saveNexusSeen(Set<PohNexusPortal> seen)
	{
		// Makes the character's profile if it has none yet; does nothing without a logged-in player.
		configManager.setRSProfileConfiguration(Settings.CONFIG_GROUP, NEXUS_SEEN_KEY, serializeNexusSeen(seen));
	}

	static String serializeNexusSeen(Set<PohNexusPortal> seen)
	{
		StringBuilder sb = new StringBuilder(NEXUS_SEEN_VERSION).append(':');
		boolean first = true;
		for (PohNexusPortal portal : seen)
		{
			if (!first)
			{
				sb.append(',');
			}
			sb.append(portal.name());
			first = false;
		}
		return sb.toString();
	}

	static Set<PohNexusPortal> parseNexusSeen(String stored)
	{
		String prefix = NEXUS_SEEN_VERSION + ":";
		if (stored == null || !stored.startsWith(prefix))
		{
			return null;
		}
		Set<PohNexusPortal> seen = EnumSet.noneOf(PohNexusPortal.class);
		for (String name : stored.substring(prefix.length()).split(","))
		{
			try
			{
				if (!name.isEmpty())
				{
					seen.add(PohNexusPortal.valueOf(name));
				}
			}
			catch (IllegalArgumentException e)
			{
				// A destination this version does not know is skipped.
			}
		}
		return seen;
	}
}
