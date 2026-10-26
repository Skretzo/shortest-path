package shortestpath.settings;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import lombok.Getter;

import shortestpath.ShortestPathConfig;
import shortestpath.Util;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;
import shortestpath.TileCounter;
import shortestpath.transport.BalloonRoute;

/**
 * Registry of every config key: one row per persisted key name, recording
 * the payload key, how the base (configured) value is read, the coercion
 * discipline a payload value must satisfy to take effect, and the
 * {@link Effect}s a change to the key triggers.
 *
 * <p>Two row shapes exist:
 * <ul>
 * <li><b>Visible</b> keys are declared as {@code @ConfigItem} methods on
 * {@link ShortestPathConfig}; the row carries the getter whose stored value
 * the base read invokes.</li>
 * <li><b>Hidden</b> keys are raw {@code ConfigManager} keys — the row carries
 * the persisted type ({@code boolean.class}, an enum class, or {@code
 * Set.class} + element class) and the default literal (or a lambda over the
 * base resolver for flag-derived set defaults).</li>
 * </ul>
 *
 * <p>A keyed row may still be <i>declared</i> as a {@code @ConfigItem} so the
 * stock config panel can reach the setting: {@link #isKeyed} records the
 * read path (a raw {@code ConfigManager} read through the keyed base
 * resolver), not whether the key has an item declaration. The declaration
 * and the keyed read share one persisted key, so a declared keyed row's
 * annotation default must mirror the row's declared default literal.
 *
 * <p>The effect set is declared per row so the table is the single source
 * for both "is read by the routing refresh" and "invalidates a running
 * route": every row whose key the refresh path reads carries
 * {@link Effect#ROUTE_INVALIDATING}, while {@code drawDebugPanel} carries
 * only {@link Effect#SIDE_EFFECT_DEBUG_OVERLAY}.
 * Rows with no shell consequence beyond view republish carry
 * {@link Effect#DISPLAY_ONLY}.
 *
 * <p>Most rows read their own stored value as the payload pairing; the one
 * exception is {@code costQuetzalWhistle}, whose payload pairs with the
 * {@code costQuetzals} base value (declared as {@code baseKey}) while its
 * stored value reads through its own key.
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
	SHOW_SIDEBAR_PANEL,
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
	CLEAR_PATH_HOTKEY,
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
	;

	private static final Map<String, ConfigKey> BY_KEY;

	static
	{
		Map<String, ConfigKey> byKey = new HashMap<>();
		for (ConfigKey row : values())
		{
			byKey.put(row.getKey(), row);
		}
		BY_KEY = Map.copyOf(byKey);
		// The ctor fails loudly on a constant with no TSV row or getter
		// binding; these passes fail loudly on a TSV row or binding that
		// names no constant.
		for (String name : Rows.BY_NAME.keySet())
		{
			valueOf(name);
		}
		for (String name : VisibleGetters.BY_NAME.keySet())
		{
			valueOf(name);
		}
	}

	/**
	 * The coercion discipline applied to a payload value for this key;
	 * mirrors the legacy override() overloads one-for-one. NONE keys are
	 * inert: payload values never reach the getter.
	 */
	enum Coercion
	{
		NONE,
		BOOLEAN,
		INT,
		COLOR,
		TELEPORTATION_ITEM,
		JEWELLERY_BOX_TIER,
		TILE_COUNTER,
		TILE_STYLE
	}

	/** Payload/config key name — the persisted key for hidden rows. */
	private final String key;
	/** Persisted type of a hidden key; {@code null} on proxy-backed rows. */
	private final Class<?> type;
	/** Element class of a {@code Set}-typed hidden key. */
	private final Class<? extends Enum> element;
	/**
	 * The unwritten default: a literal, or a
	 * {@code Function<baseResolver, Object>} for flag-derived set defaults.
	 */
	private final Object def;
	/**
	 * The enum name of the row whose base value a payload on this key pairs
	 * with, where that pairing diverges from the key itself
	 * ({@code costQuetzalWhistle} pairs with {@code costQuetzals}).
	 * {@code null} means self. Stored as a name because the paired constant
	 * is not reliably constructed yet while rows initialise; resolved at
	 * {@link #baseRow()} call time.
	 */
	private final String baseKey;
	/** The config getter a visible key's value pairs with (hidden: null). */
	private final Function<ShortestPathConfig, Object> getter;
	/** The config getter a visible key's stored read invokes (hidden: null). */
	private final Function<ShortestPathConfig, Object> configuredGetter;
	private final Coercion coercion;
	/** The effects a change to this key triggers for the shell. */
	private final Set<Effect> effects;

	// ---- TSV-backed rows --------------------------------------------------

	/**
	 * Row backed by a {@code config-keys.tsv} line: every data column
	 * resolves out of {@link Rows}, with the getter binding for visible
	 * keys coming from {@link VisibleGetters}. Fails loudly on a missing
	 * row, a malformed literal, or a visible row with no binding; the
	 * enum's static block fails loudly on surplus rows and bindings.
	 */
	ConfigKey()
	{
		String[] cols = Rows.BY_NAME.get(name());
		if (cols == null)
		{
			throw new IllegalStateException("no config-keys.tsv row for " + name());
		}
		this.key = cols[1];
		this.type = parseType(name(), cols[2]);
		this.element = parseElement(name(), cols[3]);
		this.def = type == null ? null : parseDef(name(), type, element, cols[4]);
		this.baseKey = cols[5].isEmpty() ? null : cols[5];
		this.coercion = parseCoercion(name(), cols[6]);
		this.effects = parseEffects(name(), cols[7]);
		if (type == null)
		{
			Function<ShortestPathConfig, Object> bound = VisibleGetters.BY_NAME.get(name());
			if (bound == null)
			{
				throw new IllegalStateException("no VisibleGetters binding for visible key " + name());
			}
			this.getter = bound;
			this.configuredGetter = bound;
		}
		else
		{
			this.getter = null;
			this.configuredGetter = null;
		}
	}

	/** Column count every {@code config-keys.tsv} line must carry. */
	private static final int ROW_COLUMNS = 8;

	static Class<?> parseType(String name, String token)
	{
		if (token.isEmpty())
		{
			return null;
		}
		Class<?> type = Rows.TYPE_TOKENS.get(token);
		if (type == null)
		{
			throw new IllegalStateException("unrecognised type '" + token + "' for " + name);
		}
		return type;
	}

	static Class<?> parseElement(String name, String token)
	{
		if (token.isEmpty())
		{
			return null;
		}
		Class<?> element = Rows.ELEMENT_TOKENS.get(token);
		if (element == null)
		{
			throw new IllegalStateException("unrecognised element '" + token + "' for " + name);
		}
		return element;
	}

	/**
	 * The {@code def} column parsed into the same literal the tuple row
	 * carried: boolean/int/String literals, enum constants by name, or a
	 * {@code flag:<key>} token producing the flag-derived set-default
	 * lambda. Every mis-parse throws — a silently wrong default is worse
	 * than a loud load failure.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static Object parseDef(String name, Class<?> type, Class<?> element, String token)
	{
		if (token.startsWith("flag:"))
		{
			if (element == null)
			{
				throw new IllegalStateException("flag-derived def on a non-Set row for " + name);
			}
			String flagKey = token.substring("flag:".length());
			return (Function<Function<ConfigKey, Object>, Object>) b ->
				Boolean.TRUE.equals(b.apply(forKey(flagKey)))
					? Set.copyOf(elementValues(element)) : Set.of();
		}
		if (type == boolean.class)
		{
			if ("true".equals(token) || "false".equals(token))
			{
				return Boolean.valueOf(token);
			}
		}
		else if (type == int.class)
		{
			try
			{
				return Integer.valueOf(token);
			}
			catch (NumberFormatException e)
			{
				throw new IllegalStateException("unparseable int def '" + token + "' for " + name, e);
			}
		}
		else if (type == String.class)
		{
			return token;
		}
		else if (Enum.class.isAssignableFrom(type))
		{
			try
			{
				return Enum.valueOf((Class) type, token);
			}
			catch (IllegalArgumentException e)
			{
				throw new IllegalStateException("unparseable enum def '" + token + "' for " + name, e);
			}
		}
		throw new IllegalStateException("unparseable def '" + token + "' for " + name);
	}

	static Coercion parseCoercion(String name, String token)
	{
		try
		{
			return Coercion.valueOf(token);
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalStateException("unparseable coercion '" + token + "' for " + name, e);
		}
	}

	static Set<Effect> parseEffects(String name, String token)
	{
		try
		{
			Set<Effect> out = EnumSet.noneOf(Effect.class);
			for (String effect : token.split(","))
			{
				out.add(Effect.valueOf(effect));
			}
			return Set.copyOf(out);
		}
		catch (IllegalArgumentException e)
		{
			throw new IllegalStateException("unparseable effects '" + token + "' for " + name, e);
		}
	}

	/**
	 * Load a {@code config-keys.tsv}-shaped resource into its
	 * name-keyed row map — the same shape {@link Rows} binds at class
	 * init. Package-visible so the TSV test can exercise the load's loud
	 * posture against fixture resources.
	 */
	static java.util.Collection<?> elementValues(Class<?> element)
	{
		if (element == PohNexusPortal.class)
		{
			return PohNexusPortal.values();
		}
		if (element == PohMountedItem.class)
		{
			return PohMountedItem.values();
		}
		throw new IllegalStateException("no element values for " + element);
	}

	private static Object elementFromName(Class<?> element, String name)
	{
		if (element == PohNexusPortal.class)
		{
			return PohNexusPortal.fromName(name);
		}
		if (element == PohMountedItem.class)
		{
			return PohMountedItem.fromName(name);
		}
		throw new IllegalStateException("no element resolver for " + element);
	}

	static Map<String, String[]> loadRows(String path)
	{
		Map<String, String[]> rows = new HashMap<>();
		try
		{
			Util.each(path, (line, cols) ->
			{
				if (cols.length != ROW_COLUMNS)
				{
					throw new IllegalStateException("malformed config-keys.tsv row: " + line);
				}
				rows.put(cols[0], cols);
			});
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load " + path.substring(1), e);
		}
		return Collections.unmodifiableMap(rows);
	}

	/**
	 * Row table loaded from the bundled {@code config-keys.tsv} — a nested
	 * holder so the load happens on first constant construction; the
	 * enum's own static initializers would run too late (after every
	 * constant is already built).
	 */
	private static final class Rows
	{
		private static final Map<String, String[]> BY_NAME = loadRows("/config-keys.tsv");

		/**
		 * Persisted-type tokens the {@code type} column recognises.
		 * Explicit — no class-name lookup: a type not named here fails
		 * loudly rather than resolving anything the resource happened to
		 * contain. Nested so the maps initialise before the first constant
		 * constructor reads them (the enum's own static fields run too
		 * late, like every static on this enum).
		 */
		private static final Map<String, Class<?>> TYPE_TOKENS = Map.of(
			"boolean", boolean.class,
			"int", int.class,
			"String", String.class,
			"Set", Set.class,
			"TeleportationItem", TeleportationItem.class,
			"TileCounter", TileCounter.class,
			"JewelleryBoxTier", JewelleryBoxTier.class);

		/** Element-class tokens for {@code Set}-typed rows. */
		private static final Map<String, Class<?>> ELEMENT_TOKENS = Map.of(
			"PohNexusPortal", PohNexusPortal.class,
			"PohMountedItem", PohMountedItem.class);
	}

	/** Whether this row is a raw keyed read rather than a proxy-backed one. */
	public boolean isKeyed()
	{
		return type != null;
	}

	/** The row whose base value a payload on this key pairs with. */
	public ConfigKey baseRow()
	{
		return baseKey == null ? this : valueOf(baseKey);
	}

	/**
	 * The unwritten default for a hidden key; literal rows return the
	 * literal, derived rows (the flag-gated set defaults) evaluate against
	 * the given base resolver.
	 */
	@SuppressWarnings("unchecked")
	public Object resolveDefault(Function<ConfigKey, Object> base)
	{
		return def instanceof Function
			? ((Function<Function<ConfigKey, Object>, Object>) def).apply(base)
			: def;
	}

	/**
	 * Parses the persisted form of a {@code Set}-typed hidden key —
	 * {@code ["A","B"]} as {@code ConfigManager}'s gson write produces —
	 * into an {@code EnumSet}. {@code null} on unset or corrupt content,
	 * mirroring the proxy's fall-to-default behaviour.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Set<?> parseSet(String stored, Class<?> element)
	{
		if (stored == null || stored.isEmpty())
		{
			return null;
		}
		String inner = stored.trim();
		if (!inner.startsWith("[") || !inner.endsWith("]"))
		{
			return null;
		}
		inner = inner.substring(1, inner.length() - 1).trim();
		Set<Object> out = new LinkedHashSet<>();
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
				out.add(elementFromName(element, token.substring(1, token.length() - 1)));
			}
		}
		catch (RuntimeException e)
		{
			return null;
		}
		return out;
	}

	/** Whether a change to this key restarts pathfinding. */
	boolean isRouteInvalidating()
	{
		return effects.contains(Effect.ROUTE_INVALIDATING);
	}

	public static ConfigKey forKey(String key)
	{
		return BY_KEY.get(key);
	}
}
