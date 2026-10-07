package shortestpath.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import lombok.Getter;

import shortestpath.ShortestPathConfig;

/**
 * Registry of every {@link shortestpath.ShortestPathConfig} key: one row per
 * {@code @ConfigItem.keyName}, recording the payload key, the getter whose
 * base value that key pairs with at driven call sites, the coercion
 * discipline a payload value must satisfy to take effect, and the
 * {@link Effect}s a change to the key triggers.
 *
 * <p>The effect set is declared per row so the table is the single source
 * for both "is read by the routing refresh" and "invalidates a running
 * route": every row whose key the refresh path reads carries
 * {@link Effect#ROUTE_INVALIDATING} — including keys the reads reach through
 * {@code ShortestPathConfig} default methods ({@code useTeleportationPortalsPoh}
 * and {@code usePohMountedItems}, read inside {@code pohNexusPortals()}/
 * {@code pohMountedItems()}) — while {@code pathfinderBackend} additionally
 * carries {@link Effect#SIDE_EFFECT_BACKEND_PREP} and {@code drawDebugPanel}
 * carries only {@link Effect#SIDE_EFFECT_DEBUG_OVERLAY}. Rows with no shell
 * consequence beyond view republish carry {@link Effect#DISPLAY_ONLY}.
 *
 * <p>Rows are explicit rather than derived from getter names because a few
 * pairings genuinely diverge (e.g. the {@code costQuetzalWhistle} override
 * key pairs with the {@code costQuetzals} base getter, and
 * {@code finishDistance}/{@code unreachableTargetDistanceThreshold} key names
 * do not match their method names).
 */
@Getter
enum ConfigKey
{
	PATHFINDER_BACKEND("pathfinderBackend", ShortestPathConfig::pathfinderBackend, Coercion.NONE, Effect.ROUTE_INVALIDATING, Effect.SIDE_EFFECT_BACKEND_PREP),
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
	EXACT_HEURISTIC_WEIGHT("exactHeuristicWeight", ShortestPathConfig::exactHeuristicWeight, Coercion.INT, Effect.ROUTE_INVALIDATING),
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

	/** Payload/config key name ({@code @ConfigItem.keyName}). */
	private final String key;
	/**
	 * The config getter whose base value this key's payload value pairs with,
	 * and the read method keyed configured-value lookups invoke. Declared as
	 * a method reference so reads need no reflection.
	 */
	private final Function<ShortestPathConfig, Object> getter;
	private final Coercion coercion;
	/** The effects a change to this key triggers for the shell. */
	private final Set<Effect> effects;

	ConfigKey(String key, Function<ShortestPathConfig, Object> getter, Coercion coercion, Effect... effects)
	{
		this.key = key;
		this.getter = getter;
		this.coercion = coercion;
		this.effects = Set.of(effects);
	}

	/** Whether a change to this key restarts pathfinding. */
	boolean isRouteInvalidating()
	{
		return effects.contains(Effect.ROUTE_INVALIDATING);
	}

	static ConfigKey forKey(String key)
	{
		return BY_KEY.get(key);
	}
}
