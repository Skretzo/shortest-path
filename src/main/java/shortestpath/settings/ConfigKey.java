package shortestpath.settings;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import lombok.Getter;

import shortestpath.ShortestPathConfig;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;
import shortestpath.TileCounter;

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
 * <p>The effect set is declared per row so the table is the single source
 * for both "is read by the routing refresh" and "invalidates a running
 * route": every row whose key the refresh path reads carries
 * {@link Effect#ROUTE_INVALIDATING}, while {@code pathfinderAlgorithm}
 * additionally carries {@link Effect#SIDE_EFFECT_BACKEND_PREP} and
 * {@code drawDebugPanel} carries only {@link Effect#SIDE_EFFECT_DEBUG_OVERLAY}.
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
	PATHFINDER_ALGORITHM("pathfinderAlgorithm", ShortestPathConfig::pathfinderBackend, Coercion.NONE, Effect.ROUTE_INVALIDATING, Effect.SIDE_EFFECT_BACKEND_PREP),
	AVOID_WILDERNESS("avoidWilderness", ShortestPathConfig::avoidWilderness, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_AGILITY_SHORTCUTS("useAgilityShortcuts", ShortestPathConfig::useAgilityShortcuts, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_GRAPPLE_SHORTCUTS("useGrappleShortcuts", ShortestPathConfig::useGrappleShortcuts, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_BOATS("useBoats", ShortestPathConfig::useBoats, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_CANOES("useCanoes", ShortestPathConfig::useCanoes, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_CHARTER_SHIPS("useCharterShips", ShortestPathConfig::useCharterShips, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_SHIPS("useShips", ShortestPathConfig::useShips, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_FAIRY_RINGS("useFairyRings", ShortestPathConfig::useFairyRings, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_GNOME_GLIDERS("useGnomeGliders", ShortestPathConfig::useGnomeGliders, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_HOT_AIR_BALLOONS("useHotAirBalloons", ShortestPathConfig::useHotAirBalloons, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_MAGIC_CARPETS("useMagicCarpets", ShortestPathConfig::useMagicCarpets, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_MAGIC_MUSHTREES("useMagicMushtrees", ShortestPathConfig::useMagicMushtrees, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_MINECARTS("useMinecarts", ShortestPathConfig::useMinecarts, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_QUETZALS("useQuetzals", ShortestPathConfig::useQuetzals, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_SPIRIT_TREES("useSpiritTrees", ShortestPathConfig::useSpiritTrees, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_ITEMS("useTeleportationItems", ShortestPathConfig::useTeleportationItems, Coercion.TELEPORTATION_ITEM, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_LEVERS("useTeleportationLevers", ShortestPathConfig::useTeleportationLevers, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_PORTALS("useTeleportationPortals", ShortestPathConfig::useTeleportationPortals, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_SPELLS("useTeleportationSpells", ShortestPathConfig::useTeleportationSpells, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_SPELLS_HOME("useTeleportationSpellsHome", ShortestPathConfig::useTeleportationSpellsHome, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_MINIGAMES("useTeleportationMinigames", ShortestPathConfig::useTeleportationMinigames, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_WILDERNESS_OBELISKS("useWildernessObelisks", ShortestPathConfig::useWildernessObelisks, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_SEASONAL_TRANSPORTS("useSeasonalTransports", ShortestPathConfig::useSeasonalTransports, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	RESPAWN_PRIFDDINAS("respawnPrifddinas", ShortestPathConfig::respawnPrifddinas, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	UNLOCK_CANOE_AXE("unlockCanoeAxe", ShortestPathConfig::unlockCanoeAxe, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	UNLOCK_XERICS_HONOUR("unlockXericsHonour", ShortestPathConfig::unlockXericsHonour, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	UNLOCK_DRAGONTOOTH_PASSAGE("unlockDragontoothPassage", ShortestPathConfig::unlockDragontoothPassage, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	UNLOCK_MOR_UL_REK("unlockMorUlRek", ShortestPathConfig::unlockMorUlRek, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	INCLUDE_BANK_PATH("includeBankPath", ShortestPathConfig::includeBankPath, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	CURRENCY_THRESHOLD("currencyThreshold", ShortestPathConfig::currencyThreshold, Coercion.INT, Effect.ROUTE_INVALIDATING),
	CANCEL_INSTEAD("cancelInstead", ShortestPathConfig::cancelInstead, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	RECALCULATE_DISTANCE("recalculateDistance", ShortestPathConfig::recalculateDistance, Coercion.INT, Effect.DISPLAY_ONLY),
	FINISH_DISTANCE("finishDistance", ShortestPathConfig::reachedDistance, Coercion.INT, Effect.DISPLAY_ONLY), // keyName differs from method name
	UNREACHABLE_TARGET_DISTANCE_THRESHOLD("unreachableTargetDistanceThreshold", ShortestPathConfig::unreachableTargetDistance, Coercion.INT, Effect.ROUTE_INVALIDATING), // keyName differs from method name
	SHOW_UNREACHABLE_TEXT("showUnreachableText", ShortestPathConfig::showUnreachableText, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	SHOW_TILE_COUNTER("showTileCounter", ShortestPathConfig::showTileCounter, Coercion.TILE_COUNTER, Effect.DISPLAY_ONLY),
	TILE_COUNTER_STEP("tileCounterStep", ShortestPathConfig::tileCounterStep, Coercion.INT, Effect.DISPLAY_ONLY),
	CALCULATION_CUTOFF("calculationCutoff", ShortestPathConfig::calculationCutoff, Coercion.INT, Effect.ROUTE_INVALIDATING),
	A_STAR_HEURISTIC_WEIGHT("aStarHeuristicWeight", ShortestPathConfig::aStarHeuristicWeight, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COLLISION_AWARE_BLOCKED_TARGETS("collisionAwareBlockedTargets", ShortestPathConfig::collisionAwareBlockedTargets, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_SAILING_MOVES("useSailingMoves", ShortestPathConfig::useSailingMoves, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	SHOW_TRANSPORT_INFO("showTransportInfo", ShortestPathConfig::showTransportInfo, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	SHOW_BANK_PICKUP_INFO("showBankPickupInfo", ShortestPathConfig::showBankPickupInfo, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	HIGHLIGHT_BANK_PICKUP_ITEMS("highlightBankPickupItems", ShortestPathConfig::highlightBankPickupItems, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	HIGHLIGHT_SPELLBOOK_SPELLS("highlightSpellbookSpells", ShortestPathConfig::highlightSpellbookSpells, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	HIGHLIGHT_INVENTORY_ITEMS("highlightInventoryItems", ShortestPathConfig::highlightInventoryItems, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	USE_POH("usePoh", ShortestPathConfig::usePoh, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_POH_FAIRY_RING("usePohFairyRing", ShortestPathConfig::usePohFairyRing, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_POH_SPIRIT_TREE("usePohSpiritTree", ShortestPathConfig::usePohSpiritTree, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_PORTALS_POH("useTeleportationPortalsPoh", ShortestPathConfig::useTeleportationPortalsPoh, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	POH_NEXUS_PORTALS("pohNexusPortals", ShortestPathConfig::pohNexusPortals, Coercion.NONE, Effect.ROUTE_INVALIDATING),
	POH_JEWELLERY_BOX_TIER("pohJewelleryBoxTier", ShortestPathConfig::pohJewelleryBoxTier, Coercion.JEWELLERY_BOX_TIER, Effect.ROUTE_INVALIDATING),
	POH_MOUNTED_ITEMS("pohMountedItems", ShortestPathConfig::pohMountedItems, Coercion.NONE, Effect.ROUTE_INVALIDATING),
	USE_POH_MOUNTED_ITEMS("usePohMountedItems", ShortestPathConfig::usePohMountedItems, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_POH_OBELISK("usePohObelisk", ShortestPathConfig::usePohObelisk, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	COST_AGILITY_SHORTCUTS("costAgilityShortcuts", ShortestPathConfig::costAgilityShortcuts, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_GRAPPLE_SHORTCUTS("costGrappleShortcuts", ShortestPathConfig::costGrappleShortcuts, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_BOATS("costBoats", ShortestPathConfig::costBoats, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_CANOES("costCanoes", ShortestPathConfig::costCanoes, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_CHARTER_SHIPS("costCharterShips", ShortestPathConfig::costCharterShips, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_SHIPS("costShips", ShortestPathConfig::costShips, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_FAIRY_RINGS("costFairyRings", ShortestPathConfig::costFairyRings, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_GNOME_GLIDERS("costGnomeGliders", ShortestPathConfig::costGnomeGliders, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_HOT_AIR_BALLOONS("costHotAirBalloons", ShortestPathConfig::costHotAirBalloons, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_MAGIC_CARPETS("costMagicCarpets", ShortestPathConfig::costMagicCarpets, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_MAGIC_MUSHTREES("costMagicMushtrees", ShortestPathConfig::costMagicMushtrees, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_MINECARTS("costMinecarts", ShortestPathConfig::costMinecarts, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_QUETZALS("costQuetzals", ShortestPathConfig::costQuetzals, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_QUETZAL_WHISTLE("costQuetzalWhistle", ShortestPathConfig::costQuetzals, Coercion.INT, Effect.ROUTE_INVALIDATING), // override key pairs with costQuetzals() rather than costQuetzalWhistle()
	COST_SPIRIT_TREES("costSpiritTrees", ShortestPathConfig::costSpiritTrees, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_NON_CONSUMABLE_TELEPORTATION_ITEMS("costNonConsumableTeleportationItems", ShortestPathConfig::costNonConsumableTeleportationItems, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_CONSUMABLE_TELEPORTATION_ITEMS("costConsumableTeleportationItems", ShortestPathConfig::costConsumableTeleportationItems, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_BOXES("costTeleportationBoxes", ShortestPathConfig::costTeleportationBoxes, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_LEVERS("costTeleportationLevers", ShortestPathConfig::costTeleportationLevers, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_PORTALS("costTeleportationPortals", ShortestPathConfig::costTeleportationPortals, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_SPELLS("costTeleportationSpells", ShortestPathConfig::costTeleportationSpells, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_SPELLS_HOME("costTeleportationSpellsHome", ShortestPathConfig::costTeleportationSpellsHome, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_MINIGAMES("costTeleportationMinigames", ShortestPathConfig::costTeleportationMinigames, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_WILDERNESS_OBELISKS("costWildernessObelisks", ShortestPathConfig::costWildernessObelisks, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_SEASONAL_TRANSPORTS("costSeasonalTransports", ShortestPathConfig::costSeasonalTransports, Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_BANK_VISIT("costBankVisit", ShortestPathConfig::costBankVisit, Coercion.INT, Effect.ROUTE_INVALIDATING),
	DRAW_MAP("drawMap", ShortestPathConfig::drawMap, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_MINIMAP("drawMinimap", ShortestPathConfig::drawMinimap, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_TILES("drawTiles", ShortestPathConfig::drawTiles, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_CLICK_POINTS("drawClickPoints", ShortestPathConfig::drawClickPoints, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	PATH_STYLE("pathStyle", ShortestPathConfig::pathStyle, Coercion.TILE_STYLE, Effect.DISPLAY_ONLY),
	SHOW_TELEPORT_PULSE("showTeleportPulse", ShortestPathConfig::showTeleportPulse, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	COLOUR_PATH("colourPath", ShortestPathConfig::colourPath, Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_PATH_CALCULATING("colourPathCalculating", ShortestPathConfig::colourPathCalculating, Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_PATH_UNREACHABLE("colourPathUnreachable", ShortestPathConfig::colourPathUnreachable, Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_TRANSPORTS("colourTransports", ShortestPathConfig::colourTransports, Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_COLLISION_MAP("colourCollisionMap", ShortestPathConfig::colourCollisionMap, Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_TEXT("colourText", ShortestPathConfig::colourText, Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_TELEPORT_PULSE("colourTeleportPulse", ShortestPathConfig::colourTeleportPulse, Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_BANK_PICKUP_HIGHLIGHT("colourBankPickupHighlight", ShortestPathConfig::colourBankPickupHighlight, Coercion.COLOR, Effect.DISPLAY_ONLY),
	CLEAR_PATH_HOTKEY("clearPathHotkey", ShortestPathConfig::clearPathHotkey, Coercion.NONE, Effect.DISPLAY_ONLY),
	DRAW_TRANSPORTS("drawTransports", ShortestPathConfig::drawTransports, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_COLLISION_MAP("drawCollisionMap", ShortestPathConfig::drawCollisionMap, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_DEBUG_PANEL("drawDebugPanel", ShortestPathConfig::drawDebugPanel, Coercion.BOOLEAN, Effect.SIDE_EFFECT_DEBUG_OVERLAY),
	POST_TRANSPORTS("postTransports", ShortestPathConfig::postTransports, Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	UNREACHABLE_TEXT("unreachableText", ShortestPathConfig::unreachableText, Coercion.NONE, Effect.DISPLAY_ONLY),
	BUILT_TELEPORTATION_BOXES("builtTeleportationBoxes", ShortestPathConfig::builtTeleportationBoxes, Coercion.NONE, Effect.DISPLAY_ONLY),
	BUILT_TELEPORTATION_PORTALS_POH("builtTeleportationPortalsPoh", ShortestPathConfig::builtTeleportationPortalsPoh, Coercion.NONE, Effect.DISPLAY_ONLY),
	BLOCKED_TELEPORT_ITEMS("blockedTeleportItems", ShortestPathConfig::blockedTeleportItems, Coercion.NONE, Effect.ROUTE_INVALIDATING),
	UNLOCK_BALLOON_LOG_BASKET("unlockBalloonLogBasket", ShortestPathConfig::unlockBalloonLogBasket, Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
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
	private final Class<?> element;
	/**
	 * The unwritten default: a literal, or a
	 * {@code Function<baseResolver, Object>} for flag-derived set defaults.
	 */
	private final Object def;
	/**
	 * The key whose base value a payload on this key pairs with, where that
	 * pairing diverges from the key itself ({@code costQuetzalWhistle}
	 * pairs with {@code costQuetzals}). {@code null} means self.
	 */
	private final ConfigKey baseKey;
	/** The config getter a visible key's value pairs with (hidden: null). */
	private final Function<ShortestPathConfig, Object> getter;
	/** The config getter a visible key's stored read invokes (hidden: null). */
	private final Function<ShortestPathConfig, Object> configuredGetter;
	private final Coercion coercion;
	/** The effects a change to this key triggers for the shell. */
	private final Set<Effect> effects;

	// ---- hidden rows ------------------------------------------------------

	ConfigKey(String key, Class<?> type, Object def, Coercion coercion, Effect... effects)
	{
		this(key, type, null, def, null, coercion, effects);
	}

	ConfigKey(String key, Class<?> type, ConfigKey baseKey, Object def, Coercion coercion, Effect... effects)
	{
		this(key, type, null, def, baseKey, coercion, effects);
	}

	ConfigKey(String key, Class<?> type, Class<?> element, Object def, Coercion coercion, Effect... effects)
	{
		this(key, type, element, def, null, coercion, effects);
	}

	ConfigKey(String key, Class<?> type, Class<?> element,
		Function<Function<ConfigKey, Object>, Object> def, Coercion coercion, Effect... effects)
	{
		this(key, type, element, def, null, coercion, effects);
	}

	private ConfigKey(String key, Class<?> type, Class<?> element, Object def,
		ConfigKey baseKey, Coercion coercion, Effect... effects)
	{
		this.key = key;
		this.type = type;
		this.element = element;
		this.def = def;
		this.baseKey = baseKey;
		this.getter = null;
		this.configuredGetter = null;
		this.coercion = coercion;
		this.effects = Set.of(effects);
	}

	// ---- visible rows -----------------------------------------------------

	ConfigKey(String key, Function<ShortestPathConfig, Object> getter, Coercion coercion, Effect... effects)
	{
		this(key, getter, getter, coercion, effects);
	}

	ConfigKey(String key, Function<ShortestPathConfig, Object> getter,
		Function<ShortestPathConfig, Object> configuredGetter, Coercion coercion, Effect... effects)
	{
		this.key = key;
		this.type = null;
		this.element = null;
		this.def = null;
		this.baseKey = null;
		this.getter = getter;
		this.configuredGetter = configuredGetter;
		this.coercion = coercion;
		this.effects = Set.of(effects);
	}

	/** Whether this row is a raw keyed read rather than a proxy-backed one. */
	public boolean isKeyed()
	{
		return type != null;
	}

	/** The row whose base value a payload on this key pairs with. */
	public ConfigKey baseRow()
	{
		return baseKey == null ? this : baseKey;
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
	 * {@code Set<element>} as a {@link java.lang.reflect.Type} for the raw
	 * config read — the element class is not required to be an enum.
	 */
	public java.lang.reflect.Type getSetType()
	{
		return com.google.gson.reflect.TypeToken.getParameterized(Set.class, element).getType();
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
