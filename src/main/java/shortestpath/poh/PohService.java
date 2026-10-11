package shortestpath.poh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

import shortestpath.WorldPointUtil;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;
import shortestpath.settings.Settings;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The single public boundary for POH (Player Owned House) domain behavior:
 * geometry (the POH bounds and the canonical landing tile), transport
 * destination remapping, feature enablement, and portal nexus keybinds.
 * <p>
 * This class owns the geometry contract verbatim — the bounds and landing
 * constants plus the inclusive-bounds {@link #isInsidePoh} predicate that
 * every POH geometry read crosses. The pure domain functions are statics so
 * they are reachable from construction-time and static-init call sites (the
 * transport load path remaps destinations before any service instance could
 * be injected). The portal nexus keybind machinery is instance state: it
 * persists through {@link ConfigManager} (RSProfile) and so needs injection
 * plus a detached-test seam. POH exit-info display — the label composition
 * over the exit transports — lives on the presentation annotator; this
 * service exposes only the narrow reads it consumes ({@link #apply} for
 * nexus-keybind label rewriting and the {@link #isInsidePoh} bounds
 * predicate).
 */
@Singleton
public class PohService
{
	// POH (Player Owned House) bounds for detecting when path goes through POH
	// Note: POH_MIN_X is 1856 to exclude the Daddy's Home miniquest area
	private static final int POH_MIN_X = 1856;
	private static final int POH_MAX_X = 2047;
	private static final int POH_MIN_Y = 7040;
	private static final int POH_MAX_Y = 7111;
	// Co-ordinates for Basic theme landing tile
	public static final int POH_LANDING_X = 1858;
	public static final int POH_LANDING_Y = 7051;

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

	/**
	 * Remaps POH transport destinations to the house landing tile.
	 * Transports that arrive inside the POH (e.g., fairy ring DIQ, spirit tree "Your house")
	 * are remapped so chaining with other POH transports is possible.
	 * Called once at load time since Transport objects in allTransports are shared references.
	 */
	public static void remapPohDestinations(Map<Integer, Set<Transport>> transports)
	{
		int pohLanding = WorldPointUtil.packWorldPoint(POH_LANDING_X, POH_LANDING_Y, 0);
		for (Set<Transport> transportSet : transports.values())
		{
			for (Transport transport : transportSet)
			{
				int destination = transport.getDestination();
				int destX = WorldPointUtil.unpackWorldX(destination);
				int destY = WorldPointUtil.unpackWorldY(destination);
				if (destination != pohLanding && isInsidePoh(destX, destY))
				{
					transport.setDestination(pohLanding);
				}
			}
		}
	}

	public static boolean isNexusPortalEnabled(Set<PohNexusPortal> enabledPortals, String displayInfo)
	{
		PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(displayInfo);
		return portal == null || enabledPortals.contains(portal);
	}

	public static boolean isMountedItemEnabled(Set<PohMountedItem> enabledItems, String objectInfo)
	{
		PohMountedItem item = PohMountedItem.fromObjectInfo(objectInfo);
		return item == null || enabledItems.contains(item);
	}

	/**
	 * Checks if a TELEPORTATION_BOX transport should be used based on POH settings.
	 * Handles jewellery box tiers and mounted items.
	 */
	public static boolean jewelleryBoxSatisfied(JewelleryBoxTier tier, Set<PohMountedItem> enabledItems, Transport transport)
	{
		String objectInfo = transport.getObjectInfo();
		if (objectInfo == null)
		{
			return false;
		}

		PohMountedItem mountedItem = PohMountedItem.fromObjectInfo(objectInfo);
		if (mountedItem != null)
		{
			// If mounted glory and ornate jewellery box is enabled, skip the glory
			// because the ornate box already covers all 4 destinations with correct prefixes
			if (PohMountedItem.GLORY.equals(mountedItem) && JewelleryBoxTier.ORNATE.equals(tier))
			{
				return false;
			}
			return isMountedItemEnabled(enabledItems, objectInfo);
		}

		// Filter jewellery boxes by tier
		if (JewelleryBoxTier.NONE.equals(tier))
		{
			return false;
		}

		// Basic box (37492): destinations 1-9
		if (objectInfo.contains("Basic Jewellery Box 37492"))
		{
			return true; // All tiers include basic
		}

		// Fancy box (37501): destinations A-J
		if (objectInfo.contains("Fancy Jewellery Box 37501"))
		{
			return JewelleryBoxTier.FANCY.equals(tier) ||
				JewelleryBoxTier.ORNATE.equals(tier);
		}

		// Ornate box (37520): destinations K-R
		if (objectInfo.contains("Ornate Jewellery Box 37520"))
		{
			return JewelleryBoxTier.ORNATE.equals(tier);
		}

		return false;
	}

	/**
	 * Per-type POH variant enablement: each POH-capable transport type reads
	 * its own toggle, and a nexus portal checks the enabled-portal set.
	 * Transports without a POH variant pass.
	 */
	public static boolean variantEnabled(Transport transport, boolean usePohFairyRing,
		boolean usePohSpiritTree, boolean usePohObelisk, Set<PohNexusPortal> enabledPortals)
	{
		TransportType type = transport.getType();

		// POH fairy ring
		if (TransportType.FAIRY_RING.equals(type))
		{
			return usePohFairyRing;
		}
		// POH spirit tree
		if (TransportType.SPIRIT_TREE.equals(type))
		{
			return usePohSpiritTree;
		}
		// POH obelisk
		if (TransportType.WILDERNESS_OBELISK.equals(type))
		{
			return usePohObelisk;
		}
		if (TransportType.TELEPORTATION_PORTAL_POH.equals(type))
		{
			return isNexusPortalEnabled(enabledPortals, transport.getDisplayInfo());
		}

		return true;
	}

	/*
	 * Live Portal Nexus Teleport Menu keys. Slot order is player-configurable, so
	 * keybinds are read from the teleport dialog whenever it is built rather than
	 * assumed from the default destination list.
	 *
	 * Only lines carrying an explicit "key : name" prefix produce a mapping.
	 * Lines whose key hint is rendered as a sprite carry no readable prefix, and
	 * lines the game does not key at all render no hint; neither can be assigned
	 * a key from their position — other plugins (e.g. Better Teleport Menu) can
	 * rebind or strip keys, so a position-derived key may not exist in game.
	 */
	public static final int TELENEXUS_CREATE_TELELINE = 2675;

	// In-game labels look like "<col=ffffff>1 : Harmony Island" (space before the colon).
	private static final Pattern KEYED_LINE = Pattern.compile("^([1-9A-Za-z]|F(?:[1-9]|1[0-2]))\\s*:\\s*(.+)$");
	private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^()]*)\\)");
	private static final String SERIAL_VERSION = "2";
	private static Map<String, String> nameAliases()
	{
		Map<String, String> aliases = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			PohService.class.getResourceAsStream("/poh-name-aliases.tsv"), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] c = line.split("\t", -1);
				aliases.put(c[0], c[1]);
			}
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load poh-name-aliases.tsv", e);
		}
		return Map.copyOf(aliases);
	}

	private static final Map<String, String> NAME_ALIASES = nameAliases();
	static final String CONFIG_KEY = "portalNexusKeybinds";

	private final ConfigManager configManager;
	private final RefreshCoordinator coordinator;
	private volatile Map<String, String> keysByNormalizedName = Map.of();
	private String lastSaved = "";
	private boolean dirty;

	@Inject
	public PohService(ConfigManager configManager, RefreshCoordinator coordinator)
	{
		this.configManager = configManager;
		this.coordinator = coordinator;
	}

	/**
	 * Detached-coordinator seam for tests that exercise persistence without
	 * asserting declares — the declare no-ops on the missing coordinator.
	 */
	PohService(ConfigManager configManager)
	{
		this(configManager, null);
	}

	PohService()
	{
		this(null, null);
	}

	/**
	 * A detached service for harnesses and tests: no persistence, so every
	 * RSProfile read/write takes its detached arm instead of touching
	 * storage, and no coordinator, so change declares no-op.
	 */
	public static PohService forTesting()
	{
		return new PohService();
	}

	/**
	 * Test seam for asserting change declares: the returned instance hands
	 * admitted facts to the given (typically mocked) coordinator while
	 * staying detached from persistence.
	 */
	public static PohService forTesting(RefreshCoordinator coordinator)
	{
		return new PohService(null, coordinator);
	}

	/**
	 * Reads the current keybind map from the open Portal Nexus dialog and
	 * declares the resulting fact to the coordinator. Returns whether at
	 * least one mapping was found — the dialog-retry predicate consumes the
	 * signal as "something parsed", so an identical re-read still answers
	 * {@code true} even when its fact carries no effects.
	 */
	public boolean refreshFromDialog(Client client)
	{
		PohChange change = refreshFromTeleportMenu(client);
		if (change == null)
		{
			change = refreshFromConfigurationSlots(client);
		}
		declare(change);
		return change != null;
	}

	public String apply(String displayInfo)
	{
		Map<String, String> keys = keysByNormalizedName;
		if (displayInfo == null || displayInfo.isEmpty() || keys.isEmpty())
		{
			return displayInfo;
		}

		String base = stripKeyPrefix(displayInfo);
		String key = keys.get(normalize(base));
		if (key == null)
		{
			return base;
		}
		return key + ": " + base;
	}

	/**
	 * Marks that the teleport menu is (re)building. The dialog is authoritative
	 * for its hotkeys: keys are relearned from its lines and widget text on
	 * every open, so cached mappings for slots the player unassigned must be
	 * dropped before the new build is observed.
	 */
	public void dialogOpened()
	{
		boolean changed = !keysByNormalizedName.isEmpty();
		keysByNormalizedName = Map.of();
		dirty = true;
		declare(new PohChange("dialogOpen", changed ? Set.of(Effect.DISPLAY_ONLY) : Set.of()));
	}

	/**
	 * Records one teleport menu line as it is created by the client and
	 * declares the learned-keybind fact to the coordinator. Only an
	 * explicit "key : name" prefix in the line text produces a mapping; a
	 * line without a prefix carries no keybind information and is ignored.
	 */
	public void putFromDialogLine(String rawText)
	{
		String cleaned = rawText == null ? "" : Text.removeTags(rawText).trim();
		Matcher matcher = KEYED_LINE.matcher(cleaned);
		if (!matcher.matches())
		{
			return;
		}
		Map<String, String> learned = new HashMap<>(keysByNormalizedName);
		putMapping(learned, matcher.group(2), matcher.group(1).toUpperCase(Locale.ROOT));
		keysByNormalizedName = learned;
		dirty = true;
		declare(new PohChange("dialogLine", Set.of(Effect.DISPLAY_ONLY)));
	}

	/**
	 * Reloads the persisted keybind map for the active profile. A profile load
	 * always declares a {@code DISPLAY_ONLY} fact once storage was consulted —
	 * the map it republished may differ from the previous account's — and
	 * declares nothing when running detached (no ConfigManager).
	 */
	public void loadFromProfile()
	{
		dirty = false;
		keysByNormalizedName = Map.of();
		lastSaved = "";
		if (configManager == null)
		{
			return;
		}
		String stored = configManager.getRSProfileConfiguration(Settings.CONFIG_GROUP, CONFIG_KEY);
		Map<String, String> loaded = new HashMap<>();
		deserialize(stored, loaded);
		keysByNormalizedName = loaded;
		lastSaved = serialize(loaded);
		declare(new PohChange("profile", Set.of(Effect.DISPLAY_ONLY)));
	}

	public void persistIfDirty()
	{
		if (!dirty)
		{
			return;
		}
		dirty = false;
		saveToProfile();
	}

	/**
	 * Hands an admitted fact to the refresh coordinator. Detached
	 * {@code forTesting} instances carry no coordinator, so the declare
	 * no-ops there; a null fact declares nothing, mirroring the return-null
	 * convention the shell's mapper used to guard.
	 */
	private void declare(PohChange change)
	{
		if (change != null && coordinator != null)
		{
			coordinator.pohChanged(change);
		}
	}

	PohChange refreshFromTeleportMenu(Client client)
	{
		Map<String, String> parsed = new HashMap<>();
		// Primary labels are TEXT1; diary/alternate names (GE, Seers, Yanille) are EXTRAS.
		collectKeyedWidgets(client.getWidget(InterfaceID.TelenexusTeleport.TEXT1), parsed);
		collectKeyedWidgets(client.getWidget(InterfaceID.TelenexusTeleport.EXTRAS), parsed);
		return replaceIfPresent(parsed);
	}

	PohChange refreshFromConfigurationSlots(Client client)
	{
		List<String> names = new ArrayList<>();
		collectSlotNames(client.getWidget(InterfaceID.Telenexus.SLOTTED_LIST), names);
		if (names.isEmpty())
		{
			collectSlotNames(client.getWidget(InterfaceID.Telenexus.ROWS2), names);
		}
		return replaceIfPresent(parseSlotLabels(names));
	}

	static String stripKeyPrefix(String displayInfo)
	{
		Matcher matcher = KEYED_LINE.matcher(displayInfo);
		if (matcher.matches())
		{
			return matcher.group(2);
		}
		return displayInfo;
	}

	static String normalize(String name)
	{
		String stripped = Text.removeTags(name).toLowerCase(Locale.ROOT).trim();
		if (stripped.startsWith("respawn"))
		{
			return "respawn";
		}

		stripped = stripped.replace("portal", " ");
		stripped = stripped.replace("teleport to", " ");
		stripped = stripped.replace("the ", " ");
		stripped = stripped.replaceAll("\\([^)]*\\)", " ");
		stripped = stripped.replaceAll("[^a-z0-9]+", " ").trim().replaceAll(" +", " ");

		return NAME_ALIASES.getOrDefault(stripped, stripped);
	}

	PohChange replaceIfPresent(Map<String, String> parsed)
	{
		if (parsed.isEmpty())
		{
			return null;
		}
		Map<String, String> current = keysByNormalizedName;
		if (current.isEmpty() || parsed.size() >= current.size())
		{
			Set<Effect> effects = current.equals(parsed)
				? Set.of()
				: Set.of(Effect.DISPLAY_ONLY);
			keysByNormalizedName = Map.copyOf(parsed);
			dirty = false;
			saveToProfile();
			return new PohChange("dialog", effects);
		}

		// A partially-built dialog can contain only some rows. Keep cached keys for
		// destinations that are not in this snapshot and do not persist a shrink.
		boolean changed = false;
		for (Map.Entry<String, String> entry : parsed.entrySet())
		{
			if (!entry.getValue().equals(current.get(entry.getKey())))
			{
				changed = true;
				break;
			}
		}
		Map<String, String> merged = new HashMap<>(current);
		merged.putAll(parsed);
		keysByNormalizedName = merged;
		return new PohChange("dialog", changed ? Set.of(Effect.DISPLAY_ONLY) : Set.of());
	}

	static Map<String, String> parseSlotLabels(List<String> names)
	{
		Map<String, String> parsed = new HashMap<>();
		if (names == null || names.isEmpty())
		{
			return parsed;
		}

		for (String name : names)
		{
			parseKeyedText(name, parsed);
		}
		return parsed;
	}

	static String serialize(Map<String, String> keys)
	{
		if (keys.isEmpty())
		{
			return "";
		}
		List<String> names = new ArrayList<>(keys.keySet());
		Collections.sort(names);
		StringBuilder sb = new StringBuilder(SERIAL_VERSION);
		for (String name : names)
		{
			if (sb.length() > 0)
			{
				sb.append('|');
			}
			sb.append(name).append('=').append(keys.get(name));
		}
		return sb.toString();
	}

	static void deserialize(String stored, Map<String, String> keys)
	{
		if (stored == null || stored.isEmpty())
		{
			return;
		}
		String[] entries = stored.split("\\|");
		// Entries persisted by older versions could contain keys derived from
		// line position rather than an observed binding, and cannot be told
		// apart from real ones; they are relearned from the dialog on next open.
		if (!SERIAL_VERSION.equals(entries[0]))
		{
			return;
		}
		for (int i = 1; i < entries.length; i++)
		{
			String entry = entries[i];
			int split = entry.indexOf('=');
			if (split <= 0 || split == entry.length() - 1)
			{
				continue;
			}
			String name = entry.substring(0, split);
			String key = entry.substring(split + 1).toUpperCase(Locale.ROOT);
			if (key.matches("[1-9A-Z]|F(?:[1-9]|1[0-2])"))
			{
				keys.put(name, key);
			}
		}
	}

	private void saveToProfile()
	{
		Map<String, String> keys = keysByNormalizedName;
		if (configManager == null || keys.isEmpty())
		{
			return;
		}
		String serialized = serialize(keys);
		if (serialized.equals(lastSaved))
		{
			return;
		}
		lastSaved = serialized;
		configManager.setRSProfileConfiguration(Settings.CONFIG_GROUP, CONFIG_KEY, serialized);
	}

	private static void collectKeyedWidgets(Widget widget, Map<String, String> parsed)
	{
		if (widget == null)
		{
			return;
		}
		parseKeyedText(widget.getText(), parsed);
		Widget[] children = widget.getDynamicChildren();
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			if (child != null)
			{
				parseKeyedText(child.getText(), parsed);
			}
		}
	}

	private static void collectSlotNames(Widget widget, List<String> names)
	{
		if (widget == null)
		{
			return;
		}
		collectSlotName(widget.getText(), names);
		Widget[] children = widget.getDynamicChildren();
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			if (child != null)
			{
				collectSlotName(child.getText(), names);
			}
		}
	}

	private static void collectSlotName(String rawText, List<String> names)
	{
		if (rawText == null || rawText.isEmpty())
		{
			return;
		}
		String cleaned = Text.removeTags(rawText).trim();
		if (cleaned.isEmpty() || "Slots".equalsIgnoreCase(cleaned) || "Available".equalsIgnoreCase(cleaned))
		{
			return;
		}
		Matcher matcher = KEYED_LINE.matcher(cleaned);
		if (!normalize(matcher.matches() ? matcher.group(2) : cleaned).isEmpty() && !names.contains(cleaned))
		{
			names.add(cleaned);
		}
	}

	private static void parseKeyedText(String rawText, Map<String, String> parsed)
	{
		if (rawText == null || rawText.isEmpty())
		{
			return;
		}
		Matcher matcher = KEYED_LINE.matcher(Text.removeTags(rawText).trim());
		if (matcher.matches())
		{
			putMapping(parsed, matcher.group(2), matcher.group(1).toUpperCase(Locale.ROOT));
		}
	}

	private static void putMapping(Map<String, String> parsed, String destinationName, String key)
	{
		putNormalized(parsed, destinationName, key);

		// "Frozen Waste Plateau (Ghorrock)" must also match TSV "Ghorrock Portal".
		Matcher matcher = PARENTHETICAL.matcher(destinationName);
		while (matcher.find())
		{
			putNormalized(parsed, matcher.group(1), key);
		}
		int split = destinationName.indexOf('(');
		if (split > 0)
		{
			putNormalized(parsed, destinationName.substring(0, split), key);
		}
	}

	private static void putNormalized(Map<String, String> parsed, String destinationName, String key)
	{
		String normalized = normalize(destinationName);
		if (!normalized.isEmpty())
		{
			parsed.put(normalized, key);
		}
	}

}
