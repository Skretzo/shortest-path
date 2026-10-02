package shortestpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

/**
 * Live Portal Nexus Teleport Menu keys. Slot order is player-configurable, so
 * keybinds are read from the teleport dialog whenever it is built rather than
 * assumed from the default destination list.
 */
@Singleton
public class PortalNexusKeybinds
{
	static final int TELENEXUS_CREATE_TELELINE = 2675;

	// In-game labels look like "<col=ffffff>1 : Harmony Island" (space before the colon).
	private static final Pattern KEYED_LINE = Pattern.compile("^([1-9A-Za-z])\\s*:\\s*(.+)$");
	private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^()]*)\\)");
	private static final String KEYBIND_KEYS = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
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
	private final Map<String, Character> keysByNormalizedName = new HashMap<>();
	private String lastSaved = "";
	private boolean dirty;

	@Inject
	public PortalNexusKeybinds(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	PortalNexusKeybinds()
	{
		this(null);
	}

	/**
	 * Reads the current keybind map from the open Portal Nexus dialog.
	 * Returns true when at least one mapping was found.
	 */
	public boolean refreshFromDialog(Client client)
	{
		if (refreshFromTeleportMenu(client))
		{
			return true;
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
		Character key = keysByNormalizedName.get(normalize(base));
		if (key == null)
		{
			return base;
		}
		return key + ": " + base;
	}

	void putFromDialogLine(String rawText)
	{
		parseKeyedText(rawText, keysByNormalizedName);
		dirty = true;
	}

	void loadFromProfile()
	{
		dirty = false;
		keysByNormalizedName.clear();
		lastSaved = "";
		if (configManager == null)
		{
			return;
		}
		String stored = configManager.getRSProfileConfiguration(ShortestPathPlugin.CONFIG_GROUP, CONFIG_KEY);
		deserialize(stored, keysByNormalizedName);
		lastSaved = serialize(keysByNormalizedName);
	}

	void persistIfDirty()
	{
		if (!dirty)
		{
			return;
		}
		dirty = false;
		saveToProfile();
	}

	boolean refreshFromTeleportMenu(Client client)
	{
		Map<String, Character> parsed = new HashMap<>();
		// Primary labels are TEXT1; diary/alternate names (GE, Seers, Yanille) are EXTRAS.
		collectKeyedWidgets(client.getWidget(InterfaceID.TelenexusTeleport.TEXT1), parsed);
		collectKeyedWidgets(client.getWidget(InterfaceID.TelenexusTeleport.EXTRAS), parsed);
		return replaceIfPresent(parsed);
	}

	boolean refreshFromConfigurationSlots(Client client)
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

	boolean replaceIfPresent(Map<String, Character> parsed)
	{
		if (parsed.isEmpty())
		{
			return false;
		}
		if (keysByNormalizedName.isEmpty() || parsed.size() >= keysByNormalizedName.size())
		{
			keysByNormalizedName.clear();
			keysByNormalizedName.putAll(parsed);
			dirty = false;
			saveToProfile();
			return true;
		}

		// A partially-built dialog can contain only some rows. Keep cached keys for
		// destinations that are not in this snapshot and do not persist a shrink.
		keysByNormalizedName.putAll(parsed);
		return true;
	}

	static Map<String, Character> parseSlotLabels(List<String> names)
	{
		Map<String, Character> parsed = new HashMap<>();
		if (names == null || names.isEmpty())
		{
			return parsed;
		}

		boolean anyKeyed = false;
		for (String name : names)
		{
			if (KEYED_LINE.matcher(name).matches())
			{
				anyKeyed = true;
				break;
			}
		}

		if (anyKeyed)
		{
			for (String name : names)
			{
				parseKeyedText(name, parsed);
			}
			return parsed;
		}

		for (int i = 0; i < names.size() && i < KEYBIND_KEYS.length(); i++)
		{
			putMapping(parsed, names.get(i), KEYBIND_KEYS.charAt(i));
		}
		return parsed;
	}

	static String serialize(Map<String, Character> keys)
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

	static void deserialize(String stored, Map<String, Character> keys)
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
			String keyText = entry.substring(split + 1);
			if (keyText.length() != 1)
			{
				continue;
			}
			char key = Character.toUpperCase(keyText.charAt(0));
			if ((key >= '1' && key <= '9') || (key >= 'A' && key <= 'Z'))
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
		configManager.setRSProfileConfiguration(ShortestPathPlugin.CONFIG_GROUP, CONFIG_KEY, serialized);
	}

	private static void collectKeyedWidgets(Widget widget, Map<String, Character> parsed)
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

	private static void parseKeyedText(String rawText, Map<String, Character> parsed)
	{
		if (rawText == null || rawText.isEmpty())
		{
			return;
		}
		Matcher matcher = KEYED_LINE.matcher(Text.removeTags(rawText).trim());
		if (matcher.matches())
		{
			putMapping(parsed, matcher.group(2), Character.toUpperCase(matcher.group(1).charAt(0)));
		}
	}

	private static void putMapping(Map<String, Character> parsed, String destinationName, char key)
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

	private static void putNormalized(Map<String, Character> parsed, String destinationName, char key)
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
