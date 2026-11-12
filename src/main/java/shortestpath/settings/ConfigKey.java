package shortestpath.settings;

import java.awt.Color;
import java.util.*;

import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ColorUtil;
import shortestpath.*;
import shortestpath.poh.*;
import shortestpath.transport.BalloonRoute;

/**
 * Every setting the plugin reads. Each constant is one row of
 * {@code config-keys.tsv}: the persisted key name, its type, its default and
 * the {@link Effect}s a change to it triggers. {@link Settings#get} resolves
 * a key's value.
 *
 * <p>A {@code Set} default may be {@code flag:<key>}: every element while that
 * boolean key is on, none while it is off. Those sets replaced on/off
 * toggles, so a profile that never wrote the set keeps the toggle's meaning.
 */
@Getter
public enum ConfigKey
{
	AVOID_WILDERNESS,
	USE_AGILITY_SHORTCUTS,
	USE_GRAPPLE_SHORTCUTS,
	USE_BOATS,
	USE_CANOES,
	USE_CHARTER_SHIPS,
	USE_SHIPS,
	USE_FAIRY_RINGS,
	USE_GNOME_GLIDERS,
	USE_HOT_AIR_BALLOONS,
	USE_MAGIC_CARPETS,
	USE_MAGIC_MUSHTREES,
	USE_MINECARTS,
	USE_QUETZALS,
	USE_SPIRIT_TREES,
	USE_TELEPORTATION_ITEMS,
	USE_TELEPORTATION_LEVERS,
	USE_TELEPORTATION_PORTALS,
	USE_TELEPORTATION_SPELLS,
	USE_TELEPORTATION_SPELLS_HOME,
	USE_TELEPORTATION_MINIGAMES,
	USE_WILDERNESS_OBELISKS,
	USE_SEASONAL_TRANSPORTS,
	RESPAWN_PRIFDDINAS,
	UNLOCK_CANOE_AXE,
	UNLOCK_XERICS_HONOUR,
	UNLOCK_DRAGONTOOTH_PASSAGE,
	UNLOCK_MOR_UL_REK,
	INCLUDE_BANK_PATH,
	CURRENCY_THRESHOLD,
	CANCEL_INSTEAD,
	RECALCULATE_DISTANCE,
	FINISH_DISTANCE,
	UNREACHABLE_TARGET_DISTANCE_THRESHOLD,
	SHOW_UNREACHABLE_TEXT,
	SHOW_TILE_COUNTER,
	TILE_COUNTER_STEP,
	CALCULATION_CUTOFF,
	A_STAR_HEURISTIC_WEIGHT,
	COLLISION_AWARE_BLOCKED_TARGETS,
	USE_SAILING_MOVES,
	SHOW_TRANSPORT_INFO,
	SHOW_BANK_PICKUP_INFO,
	HIGHLIGHT_BANK_PICKUP_ITEMS,
	HIGHLIGHT_SPELLBOOK_SPELLS,
	HIGHLIGHT_INVENTORY_ITEMS,
	USE_POH,
	USE_POH_FAIRY_RING,
	USE_POH_SPIRIT_TREE,
	USE_TELEPORTATION_PORTALS_POH,
	POH_NEXUS_PORTALS,
	POH_JEWELLERY_BOX_TIER,
	POH_MOUNTED_ITEMS,
	USE_POH_MOUNTED_ITEMS,
	USE_POH_OBELISK,
	COST_AGILITY_SHORTCUTS,
	COST_GRAPPLE_SHORTCUTS,
	COST_BOATS,
	COST_CANOES,
	COST_CHARTER_SHIPS,
	COST_SHIPS,
	COST_FAIRY_RINGS,
	COST_GNOME_GLIDERS,
	COST_HOT_AIR_BALLOONS,
	COST_MAGIC_CARPETS,
	COST_MAGIC_MUSHTREES,
	COST_MINECARTS,
	COST_QUETZALS,
	COST_QUETZAL_WHISTLE,
	COST_SPIRIT_TREES,
	COST_NON_CONSUMABLE_TELEPORTATION_ITEMS,
	COST_CONSUMABLE_TELEPORTATION_ITEMS,
	COST_TELEPORTATION_BOXES,
	COST_TELEPORTATION_LEVERS,
	COST_TELEPORTATION_PORTALS,
	COST_TELEPORTATION_SPELLS,
	COST_TELEPORTATION_SPELLS_HOME,
	COST_TELEPORTATION_MINIGAMES,
	COST_WILDERNESS_OBELISKS,
	COST_SEASONAL_TRANSPORTS,
	COST_BANK_VISIT,
	DRAW_MAP,
	DRAW_MINIMAP,
	DRAW_TILES,
	DRAW_CLICK_POINTS,
	PATH_STYLE,
	SHOW_TELEPORT_PULSE,
	COLOUR_PATH,
	COLOUR_PATH_CALCULATING,
	COLOUR_PATH_UNREACHABLE,
	COLOUR_TRANSPORTS,
	COLOUR_COLLISION_MAP,
	COLOUR_TEXT,
	COLOUR_TELEPORT_PULSE,
	COLOUR_BANK_PICKUP_HIGHLIGHT,
	DRAW_TRANSPORTS,
	DRAW_COLLISION_MAP,
	DRAW_DEBUG_PANEL,
	POST_TRANSPORTS,
	UNREACHABLE_TEXT,
	BUILT_TELEPORTATION_BOXES,
	BUILT_TELEPORTATION_PORTALS_POH,
	BLOCKED_TELEPORT_ITEMS,
	UNLOCK_BALLOON_LOG_BASKET,
	BALLOON_LOG_BASKET_ROUTES;

	private final String key;
	private final Class<?> type;
	private final Class<? extends Enum> element;
	private final Object def;
	/** The boolean key a {@code flag:} set default follows, or null. */
	@Getter(lombok.AccessLevel.NONE)
	private final String flagKey;
	private final Set<Effect> effects;

	@SuppressWarnings({"unchecked", "rawtypes"})
	ConfigKey()
	{
		String[] cols = Rows.BY_NAME.get(name());
		if (cols == null)
		{
			throw new IllegalStateException("no config-keys.tsv row for " + name());
		}
		key = cols[1];
		type = Rows.TYPES.get(cols[2]);
		element = cols[3].isEmpty() ? null : Rows.ELEMENTS.get(cols[3]);
		if (type == null || (type == Set.class) != (element != null))
		{
			throw new IllegalStateException("bad type for " + name());
		}
		String def = cols[4];
		flagKey = def.startsWith("flag:") ? def.substring("flag:".length()) : null;
		if (flagKey != null || type == String.class)
		{
			this.def = flagKey != null ? null : def;
		}
		else if (type == Boolean.class)
		{
			this.def = Boolean.valueOf(def);
		}
		else if (type == Integer.class)
		{
			this.def = Integer.valueOf(def);
		}
		else if (type == Color.class)
		{
			this.def = ColorUtil.fromHex(def);
		}
		else
		{
			this.def = Enum.valueOf((Class) type, def);
		}
		Set<Effect> effects = EnumSet.noneOf(Effect.class);
		for (String effect : cols[5].split(","))
		{
			effects.add(Effect.valueOf(effect));
		}
		this.effects = Collections.unmodifiableSet(effects);
	}

	public static ConfigKey forKey(String key)
	{
		return Rows.NAMES.containsKey(key) ? valueOf(Rows.NAMES.get(key)) : null;
	}

	/** The value stored for this key, or null when it was never written or does not parse. */
	Object read(ConfigManager configManager)
	{
		return type == Set.class
			? parseSet(configManager.getConfiguration(Settings.CONFIG_GROUP, key), element)
			: configManager.getConfiguration(Settings.CONFIG_GROUP, key, type);
	}

	/** {@code value} if it has this key's type (enum keys also take their display name), otherwise null. */
	Object coerce(Object value)
	{
		if (value instanceof String && type.isEnum())
		{
			for (Object constant : type.getEnumConstants())
			{
				if (constant.toString().equals(value))
				{
					return constant;
				}
			}
		}
		return type.isInstance(value) ? value : null;
	}

	/** The boolean key a {@code flag:} set default follows, or null. */
	public ConfigKey getFlag()
	{
		return flagKey == null ? null : forKey(flagKey);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	Set<?> flagDefault(boolean on)
	{
		return on ? EnumSet.allOf(element) : EnumSet.noneOf(element);
	}

	/** Parses the {@code ["A", "B"]} form a stored enum set takes; null when unset or malformed. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Set<?> parseSet(String stored, Class<? extends Enum> element)
	{
		if (stored == null || !stored.trim().startsWith("[") || !stored.trim().endsWith("]"))
		{
			return null;
		}
		String inner = stored.trim();
		inner = inner.substring(1, inner.length() - 1).trim();
		EnumSet out = EnumSet.noneOf(element);
		if (inner.isEmpty())
		{
			return out;
		}
		try
		{
			for (String token : inner.split(","))
			{
				token = token.trim();
				if (token.length() < 2 || token.charAt(0) != '"' || token.charAt(token.length() - 1) != '"')
				{
					return null;
				}
				out.add(Enum.valueOf(element, token.substring(1, token.length() - 1)));
			}
		}
		catch (RuntimeException e)
		{
			return null;
		}
		return out;
	}

	private static final class Rows
	{
		private static final Map<String, String[]> BY_NAME = new HashMap<>();
		/** Persisted key name to enum constant name. */
		private static final Map<String, String> NAMES = new HashMap<>();
		private static final Map<String, Class<?>> TYPES = Map.of(
			"boolean", Boolean.class,
			"int", Integer.class,
			"String", String.class,
			"Color", Color.class,
			"Set", Set.class,
			"TeleportationItem", TeleportationItem.class,
			"TileCounter", TileCounter.class,
			"TileStyle", TileStyle.class,
			"JewelleryBoxTier", JewelleryBoxTier.class);
		private static final Map<String, Class<? extends Enum>> ELEMENTS = Map.of(
			"PohNexusPortal", PohNexusPortal.class,
			"PohMountedItem", PohMountedItem.class,
			"BalloonRoute", BalloonRoute.class);

		static
		{
			try
			{
				Util.each("/config-keys.tsv", (line, cols) ->
				{
					if (cols.length != 6 || BY_NAME.put(cols[0], cols) != null || NAMES.put(cols[1], cols[0]) != null)
					{
						throw new IllegalStateException("bad config-keys.tsv row: " + line);
					}
				});
			}
			catch (Exception e)
			{
				throw new IllegalStateException("failed to load config-keys.tsv", e);
			}
		}
	}
}
