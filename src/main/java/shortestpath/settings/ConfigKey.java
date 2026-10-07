package shortestpath.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.Getter;

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
	PATHFINDER_BACKEND("pathfinderBackend", "pathfinderBackend", Coercion.NONE, Effect.ROUTE_INVALIDATING, Effect.SIDE_EFFECT_BACKEND_PREP),
	AVOID_WILDERNESS("avoidWilderness", "avoidWilderness", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_AGILITY_SHORTCUTS("useAgilityShortcuts", "useAgilityShortcuts", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_GRAPPLE_SHORTCUTS("useGrappleShortcuts", "useGrappleShortcuts", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_BOATS("useBoats", "useBoats", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_CANOES("useCanoes", "useCanoes", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_CHARTER_SHIPS("useCharterShips", "useCharterShips", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_SHIPS("useShips", "useShips", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_FAIRY_RINGS("useFairyRings", "useFairyRings", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_GNOME_GLIDERS("useGnomeGliders", "useGnomeGliders", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_HOT_AIR_BALLOONS("useHotAirBalloons", "useHotAirBalloons", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_MAGIC_CARPETS("useMagicCarpets", "useMagicCarpets", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_MAGIC_MUSHTREES("useMagicMushtrees", "useMagicMushtrees", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_MINECARTS("useMinecarts", "useMinecarts", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_QUETZALS("useQuetzals", "useQuetzals", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_SPIRIT_TREES("useSpiritTrees", "useSpiritTrees", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_ITEMS("useTeleportationItems", "useTeleportationItems", Coercion.TELEPORTATION_ITEM, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_LEVERS("useTeleportationLevers", "useTeleportationLevers", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_PORTALS("useTeleportationPortals", "useTeleportationPortals", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_SPELLS("useTeleportationSpells", "useTeleportationSpells", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_SPELLS_HOME("useTeleportationSpellsHome", "useTeleportationSpellsHome", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_MINIGAMES("useTeleportationMinigames", "useTeleportationMinigames", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_WILDERNESS_OBELISKS("useWildernessObelisks", "useWildernessObelisks", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_SEASONAL_TRANSPORTS("useSeasonalTransports", "useSeasonalTransports", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	RESPAWN_PRIFDDINAS("respawnPrifddinas", "respawnPrifddinas", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	UNLOCK_CANOE_AXE("unlockCanoeAxe", "unlockCanoeAxe", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	UNLOCK_XERICS_HONOUR("unlockXericsHonour", "unlockXericsHonour", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	UNLOCK_DRAGONTOOTH_PASSAGE("unlockDragontoothPassage", "unlockDragontoothPassage", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	INCLUDE_BANK_PATH("includeBankPath", "includeBankPath", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	CURRENCY_THRESHOLD("currencyThreshold", "currencyThreshold", Coercion.INT, Effect.ROUTE_INVALIDATING),
	CANCEL_INSTEAD("cancelInstead", "cancelInstead", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	RECALCULATE_DISTANCE("recalculateDistance", "recalculateDistance", Coercion.INT, Effect.DISPLAY_ONLY),
	FINISH_DISTANCE("finishDistance", "reachedDistance", Coercion.INT, Effect.DISPLAY_ONLY), // keyName differs from method name
	UNREACHABLE_TARGET_DISTANCE_THRESHOLD("unreachableTargetDistanceThreshold", "unreachableTargetDistance", Coercion.INT, Effect.ROUTE_INVALIDATING), // keyName differs from method name
	SHOW_UNREACHABLE_TEXT("showUnreachableText", "showUnreachableText", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	SHOW_TILE_COUNTER("showTileCounter", "showTileCounter", Coercion.TILE_COUNTER, Effect.DISPLAY_ONLY),
	TILE_COUNTER_STEP("tileCounterStep", "tileCounterStep", Coercion.INT, Effect.DISPLAY_ONLY),
	CALCULATION_CUTOFF("calculationCutoff", "calculationCutoff", Coercion.INT, Effect.ROUTE_INVALIDATING),
	EXACT_HEURISTIC_WEIGHT("exactHeuristicWeight", "exactHeuristicWeight", Coercion.INT, Effect.ROUTE_INVALIDATING),
	SHOW_TRANSPORT_INFO("showTransportInfo", "showTransportInfo", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	SHOW_BANK_PICKUP_INFO("showBankPickupInfo", "showBankPickupInfo", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	HIGHLIGHT_BANK_PICKUP_ITEMS("highlightBankPickupItems", "highlightBankPickupItems", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	HIGHLIGHT_SPELLBOOK_SPELLS("highlightSpellbookSpells", "highlightSpellbookSpells", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	HIGHLIGHT_INVENTORY_ITEMS("highlightInventoryItems", "highlightInventoryItems", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	USE_POH("usePoh", "usePoh", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_POH_FAIRY_RING("usePohFairyRing", "usePohFairyRing", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_POH_SPIRIT_TREE("usePohSpiritTree", "usePohSpiritTree", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_TELEPORTATION_PORTALS_POH("useTeleportationPortalsPoh", "useTeleportationPortalsPoh", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	POH_NEXUS_PORTALS("pohNexusPortals", "pohNexusPortals", Coercion.NONE, Effect.ROUTE_INVALIDATING),
	POH_JEWELLERY_BOX_TIER("pohJewelleryBoxTier", "pohJewelleryBoxTier", Coercion.JEWELLERY_BOX_TIER, Effect.ROUTE_INVALIDATING),
	POH_MOUNTED_ITEMS("pohMountedItems", "pohMountedItems", Coercion.NONE, Effect.ROUTE_INVALIDATING),
	USE_POH_MOUNTED_ITEMS("usePohMountedItems", "usePohMountedItems", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	USE_POH_OBELISK("usePohObelisk", "usePohObelisk", Coercion.BOOLEAN, Effect.ROUTE_INVALIDATING),
	COST_AGILITY_SHORTCUTS("costAgilityShortcuts", "costAgilityShortcuts", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_GRAPPLE_SHORTCUTS("costGrappleShortcuts", "costGrappleShortcuts", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_BOATS("costBoats", "costBoats", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_CANOES("costCanoes", "costCanoes", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_CHARTER_SHIPS("costCharterShips", "costCharterShips", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_SHIPS("costShips", "costShips", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_FAIRY_RINGS("costFairyRings", "costFairyRings", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_GNOME_GLIDERS("costGnomeGliders", "costGnomeGliders", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_HOT_AIR_BALLOONS("costHotAirBalloons", "costHotAirBalloons", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_MAGIC_CARPETS("costMagicCarpets", "costMagicCarpets", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_MAGIC_MUSHTREES("costMagicMushtrees", "costMagicMushtrees", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_MINECARTS("costMinecarts", "costMinecarts", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_QUETZALS("costQuetzals", "costQuetzals", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_QUETZAL_WHISTLE("costQuetzalWhistle", "costQuetzals", Coercion.INT, Effect.ROUTE_INVALIDATING), // override key pairs with costQuetzals() rather than costQuetzalWhistle()
	COST_SPIRIT_TREES("costSpiritTrees", "costSpiritTrees", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_NON_CONSUMABLE_TELEPORTATION_ITEMS("costNonConsumableTeleportationItems", "costNonConsumableTeleportationItems", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_CONSUMABLE_TELEPORTATION_ITEMS("costConsumableTeleportationItems", "costConsumableTeleportationItems", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_BOXES("costTeleportationBoxes", "costTeleportationBoxes", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_LEVERS("costTeleportationLevers", "costTeleportationLevers", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_PORTALS("costTeleportationPortals", "costTeleportationPortals", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_SPELLS("costTeleportationSpells", "costTeleportationSpells", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_SPELLS_HOME("costTeleportationSpellsHome", "costTeleportationSpellsHome", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_TELEPORTATION_MINIGAMES("costTeleportationMinigames", "costTeleportationMinigames", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_WILDERNESS_OBELISKS("costWildernessObelisks", "costWildernessObelisks", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_SEASONAL_TRANSPORTS("costSeasonalTransports", "costSeasonalTransports", Coercion.INT, Effect.ROUTE_INVALIDATING),
	COST_BANK_VISIT("costBankVisit", "costBankVisit", Coercion.INT, Effect.ROUTE_INVALIDATING),
	DRAW_MAP("drawMap", "drawMap", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_MINIMAP("drawMinimap", "drawMinimap", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_TILES("drawTiles", "drawTiles", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	PATH_STYLE("pathStyle", "pathStyle", Coercion.TILE_STYLE, Effect.DISPLAY_ONLY),
	SHOW_TELEPORT_PULSE("showTeleportPulse", "showTeleportPulse", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	COLOUR_PATH("colourPath", "colourPath", Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_PATH_CALCULATING("colourPathCalculating", "colourPathCalculating", Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_PATH_UNREACHABLE("colourPathUnreachable", "colourPathUnreachable", Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_TRANSPORTS("colourTransports", "colourTransports", Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_COLLISION_MAP("colourCollisionMap", "colourCollisionMap", Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_TEXT("colourText", "colourText", Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_TELEPORT_PULSE("colourTeleportPulse", "colourTeleportPulse", Coercion.COLOR, Effect.DISPLAY_ONLY),
	COLOUR_BANK_PICKUP_HIGHLIGHT("colourBankPickupHighlight", "colourBankPickupHighlight", Coercion.COLOR, Effect.DISPLAY_ONLY),
	CLEAR_PATH_HOTKEY("clearPathHotkey", "clearPathHotkey", Coercion.NONE, Effect.DISPLAY_ONLY),
	DRAW_TRANSPORTS("drawTransports", "drawTransports", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_COLLISION_MAP("drawCollisionMap", "drawCollisionMap", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	DRAW_DEBUG_PANEL("drawDebugPanel", "drawDebugPanel", Coercion.BOOLEAN, Effect.SIDE_EFFECT_DEBUG_OVERLAY),
	POST_TRANSPORTS("postTransports", "postTransports", Coercion.BOOLEAN, Effect.DISPLAY_ONLY),
	UNREACHABLE_TEXT("unreachableText", "unreachableText", Coercion.NONE, Effect.DISPLAY_ONLY),
	BUILT_TELEPORTATION_BOXES("builtTeleportationBoxes", "builtTeleportationBoxes", Coercion.NONE, Effect.DISPLAY_ONLY),
	BUILT_TELEPORTATION_PORTALS_POH("builtTeleportationPortalsPoh", "builtTeleportationPortalsPoh", Coercion.NONE, Effect.DISPLAY_ONLY),
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
	/** Interface getter whose base value this key's payload value pairs with. */
	private final String getter;
	private final Coercion coercion;
	/** The effects a change to this key triggers for the shell. */
	private final Set<Effect> effects;

	ConfigKey(String key, String getter, Coercion coercion, Effect... effects)
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
