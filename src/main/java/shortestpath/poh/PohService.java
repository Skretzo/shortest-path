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
import shortestpath.settings.Effect;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * The single public boundary for POH (Player Owned House) domain behavior:
 * geometry (the POH bounds and the canonical landing tile), transport
 * destination remapping, feature enablement, portal nexus keybinds, and
 * POH exit-info display.
 * <p>
 * This class owns the geometry contract verbatim — the bounds and landing
 * constants plus the inclusive-bounds {@link #isInsidePoh} predicate that
 * every POH geometry read crosses. The pure domain functions are statics so
 * they are reachable from construction-time and static-init call sites (the
 * transport load path remaps destinations before any service instance could
 * be injected). The portal nexus keybind machinery is instance state: it
 * persists through {@link ConfigManager} (RSProfile) and so needs injection
 * plus a detached-test seam.
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
	 * Slots whose rendered label has no readable prefix have no keybind — the
	 * key is drawn as a sprite and cannot be recovered from the line position.
	 */
	public static final int TELENEXUS_CREATE_TELELINE = 2675;

	// In-game labels look like "<col=ffffff>1 : Harmony Island" (space before the colon).
	private static final Pattern KEYED_LINE = Pattern.compile("^([0-9A-Za-z]|F(?:[1-9]|1[0-2]))\\s*:\\s*(.+)$");
	private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^()]*)\\)");
	private static final Map<String, String> NAME_ALIASES;

	static
	{
		Map<String, String> aliases = new HashMap<>();
		aliases.put("east ardougne", "ardougne");
		aliases.put("kourend castle", "kourend");
		aliases.put("great kourend", "kourend");
		aliases.put("ourania cave", "ourania");
		aliases.put("forgotten cemetery", "cemetery");
		aliases.put("mooring point", "boat");
		aliases.put("teleport to boat", "boat");
		aliases.put("frozen waste plateau", "ghorrock");
		aliases.put("demonic ruins", "annakarl");
		aliases.put("graveyard of shadows", "carrallanger");
		aliases.put("canifis", "kharyrll");
		aliases.put("exam centre", "senntisten");
		aliases.put("exam center", "senntisten");
		aliases.put("digsite", "senntisten");
		aliases.put("edgeville dungeon", "paddewwa");
		aliases.put("ice mountain", "lassar");
		aliases.put("crazy archaeologist", "dareeyak");
		aliases.put("ruins west", "dareeyak");
		aliases.put("ape atoll", "marim");
		// The teleport menu truncates "Fenkenstrain's Castle" to "Fenken' Castle".
		aliases.put("fenken castle", "fenkenstrain s castle");
		NAME_ALIASES = Map.copyOf(aliases);
	}
	private static final String[][] SHARED_SLOTS = {
		{"varrock", "grand exchange"},
		{"camelot", "seers village"},
		{"watchtower", "yanille"},
	};
	static final String CONFIG_KEY = "portalNexusKeybinds";

	private final ConfigManager configManager;
	private final Map<String, String> keysByNormalizedName = new HashMap<>();
	private String lastSaved = "";
	private boolean dirty;

	@Inject
	public PohService(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	PohService()
	{
		this(null);
	}

	/**
	 * A detached service for harnesses and tests: no persistence, so every
	 * RSProfile read/write takes its detached arm instead of touching
	 * storage.
	 */
	public static PohService forTesting()
	{
		return new PohService();
	}

	/**
	 * Reads the current keybind map from the open Portal Nexus dialog.
	 * Returns a fact when at least one mapping was found, null otherwise.
	 */
	public PohChange refreshFromDialog(Client client)
	{
		PohChange change = refreshFromTeleportMenu(client);
		if (change != null)
		{
			return change;
		}
		return refreshFromConfigurationSlots(client);
	}

	public String apply(String displayInfo)
	{
		if (displayInfo == null || displayInfo.isEmpty() || keysByNormalizedName.isEmpty())
		{
			return displayInfo;
		}

		String base = stripKeyPrefix(displayInfo);
		String key = keysByNormalizedName.get(normalize(base));
		if (key == null)
		{
			return base;
		}
		return key + ": " + base;
	}

	/**
	 * Records one teleport menu line as it is created by the client. Only an
	 * explicit "key : name" prefix in the line text produces a mapping; a
	 * line without a prefix carries no keybind information and is ignored.
	 */
	public PohChange putFromDialogLine(String rawText)
	{
		String cleaned = rawText == null ? "" : Text.removeTags(rawText).trim();
		Matcher matcher = KEYED_LINE.matcher(cleaned);
		if (!matcher.matches())
		{
			return null;
		}
		String key = matcher.group(1).toUpperCase(Locale.ROOT);
		String name = matcher.group(2);
		putMapping(keysByNormalizedName, name, key);
		dirty = true;
		return new PohChange("dialogLine", Set.of(Effect.DISPLAY_ONLY));
	}

	/**
	 * Reloads the persisted keybind map for the active profile. A profile load
	 * always emits a {@code DISPLAY_ONLY} fact once storage was consulted —
	 * the map it republished may differ from the previous account's — and a
	 * null fact when running detached (no ConfigManager).
	 */
	public PohChange loadFromProfile()
	{
		dirty = false;
		keysByNormalizedName.clear();
		lastSaved = "";
		if (configManager == null)
		{
			return null;
		}
		String stored = configManager.getRSProfileConfiguration("shortestpath", CONFIG_KEY);
		deserialize(stored, keysByNormalizedName);
		lastSaved = serialize(keysByNormalizedName);
		return new PohChange("profile", Set.of(Effect.DISPLAY_ONLY));
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
		if (keysByNormalizedName.isEmpty() || parsed.size() >= keysByNormalizedName.size())
		{
			Set<Effect> effects = keysByNormalizedName.equals(parsed)
				? Set.of()
				: Set.of(Effect.DISPLAY_ONLY);
			keysByNormalizedName.clear();
			keysByNormalizedName.putAll(parsed);
			dirty = false;
			saveToProfile();
			return new PohChange("dialog", effects);
		}

		// A partially-built dialog can contain only some rows. Keep cached keys for
		// destinations that are not in this snapshot and do not persist a shrink.
		boolean changed = false;
		for (Map.Entry<String, String> entry : parsed.entrySet())
		{
			if (!entry.getValue().equals(keysByNormalizedName.get(entry.getKey())))
			{
				changed = true;
				break;
			}
		}
		keysByNormalizedName.putAll(parsed);
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
		StringBuilder sb = new StringBuilder();
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
		for (String entry : stored.split("\\|"))
		{
			int split = entry.indexOf('=');
			if (split <= 0 || split == entry.length() - 1)
			{
				continue;
			}
			String name = entry.substring(0, split);
			String key = entry.substring(split + 1).toUpperCase(Locale.ROOT);
			// Only single-character keys are admitted. Persisted function-key
			// entries are dropped on load, so profiles poisoned by earlier
			// position-derived bindings self-heal.
			if (key.matches("[0-9A-Z]"))
			{
				keys.put(name, key);
			}
		}
	}

	private void saveToProfile()
	{
		if (configManager == null || keysByNormalizedName.isEmpty())
		{
			return;
		}
		String serialized = serialize(keysByNormalizedName);
		if (serialized.equals(lastSaved))
		{
			return;
		}
		lastSaved = serialized;
		configManager.setRSProfileConfiguration("shortestpath", CONFIG_KEY, serialized);
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
		if (normalized.isEmpty())
		{
			return;
		}
		parsed.put(normalized, key);
		for (String[] shared : SHARED_SLOTS)
		{
			for (String alias : shared)
			{
				if (alias.equals(normalized))
				{
					for (String sharedName : shared)
					{
						parsed.put(sharedName, key);
					}
					return;
				}
			}
		}
	}
}
