package shortestpath.settings;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

/**
 * Registry of every {@link shortestpath.ShortestPathConfig} key: one row per
 * {@code @ConfigItem.keyName}, recording the payload key, the getter whose
 * base value that key pairs with at driven call sites, the coercion
 * discipline a payload value must satisfy to take effect, and whether a
 * change to the key invalidates a running search.
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
	PATHFINDER_BACKEND("pathfinderBackend", "pathfinderBackend", Coercion.NONE, true),
	AVOID_WILDERNESS("avoidWilderness", "avoidWilderness", Coercion.BOOLEAN, true),
	USE_AGILITY_SHORTCUTS("useAgilityShortcuts", "useAgilityShortcuts", Coercion.BOOLEAN, true),
	USE_GRAPPLE_SHORTCUTS("useGrappleShortcuts", "useGrappleShortcuts", Coercion.BOOLEAN, true),
	USE_BOATS("useBoats", "useBoats", Coercion.BOOLEAN, true),
	USE_CANOES("useCanoes", "useCanoes", Coercion.BOOLEAN, true),
	USE_CHARTER_SHIPS("useCharterShips", "useCharterShips", Coercion.BOOLEAN, true),
	USE_SHIPS("useShips", "useShips", Coercion.BOOLEAN, true),
	USE_FAIRY_RINGS("useFairyRings", "useFairyRings", Coercion.BOOLEAN, true),
	USE_GNOME_GLIDERS("useGnomeGliders", "useGnomeGliders", Coercion.BOOLEAN, true),
	USE_HOT_AIR_BALLOONS("useHotAirBalloons", "useHotAirBalloons", Coercion.BOOLEAN, true),
	USE_MAGIC_CARPETS("useMagicCarpets", "useMagicCarpets", Coercion.BOOLEAN, true),
	USE_MAGIC_MUSHTREES("useMagicMushtrees", "useMagicMushtrees", Coercion.BOOLEAN, true),
	USE_MINECARTS("useMinecarts", "useMinecarts", Coercion.BOOLEAN, true),
	USE_QUETZALS("useQuetzals", "useQuetzals", Coercion.BOOLEAN, true),
	USE_SPIRIT_TREES("useSpiritTrees", "useSpiritTrees", Coercion.BOOLEAN, true),
	USE_TELEPORTATION_ITEMS("useTeleportationItems", "useTeleportationItems", Coercion.TELEPORTATION_ITEM, true),
	USE_TELEPORTATION_LEVERS("useTeleportationLevers", "useTeleportationLevers", Coercion.BOOLEAN, true),
	USE_TELEPORTATION_PORTALS("useTeleportationPortals", "useTeleportationPortals", Coercion.BOOLEAN, true),
	USE_TELEPORTATION_SPELLS("useTeleportationSpells", "useTeleportationSpells", Coercion.BOOLEAN, true),
	USE_TELEPORTATION_SPELLS_HOME("useTeleportationSpellsHome", "useTeleportationSpellsHome", Coercion.BOOLEAN, true),
	USE_TELEPORTATION_MINIGAMES("useTeleportationMinigames", "useTeleportationMinigames", Coercion.BOOLEAN, true),
	USE_WILDERNESS_OBELISKS("useWildernessObelisks", "useWildernessObelisks", Coercion.BOOLEAN, true),
	USE_SEASONAL_TRANSPORTS("useSeasonalTransports", "useSeasonalTransports", Coercion.BOOLEAN, true),
	RESPAWN_PRIFDDINAS("respawnPrifddinas", "respawnPrifddinas", Coercion.BOOLEAN, false),
	UNLOCK_CANOE_AXE("unlockCanoeAxe", "unlockCanoeAxe", Coercion.BOOLEAN, true),
	UNLOCK_XERICS_HONOUR("unlockXericsHonour", "unlockXericsHonour", Coercion.BOOLEAN, true),
	UNLOCK_DRAGONTOOTH_PASSAGE("unlockDragontoothPassage", "unlockDragontoothPassage", Coercion.BOOLEAN, true),
	INCLUDE_BANK_PATH("includeBankPath", "includeBankPath", Coercion.BOOLEAN, true),
	CURRENCY_THRESHOLD("currencyThreshold", "currencyThreshold", Coercion.INT, true),
	CANCEL_INSTEAD("cancelInstead", "cancelInstead", Coercion.BOOLEAN, false),
	RECALCULATE_DISTANCE("recalculateDistance", "recalculateDistance", Coercion.INT, false),
	FINISH_DISTANCE("finishDistance", "reachedDistance", Coercion.INT, false), // keyName differs from method name
	UNREACHABLE_TARGET_DISTANCE_THRESHOLD("unreachableTargetDistanceThreshold", "unreachableTargetDistance", Coercion.INT, false), // keyName differs from method name
	SHOW_UNREACHABLE_TEXT("showUnreachableText", "showUnreachableText", Coercion.BOOLEAN, false),
	SHOW_TILE_COUNTER("showTileCounter", "showTileCounter", Coercion.TILE_COUNTER, false),
	TILE_COUNTER_STEP("tileCounterStep", "tileCounterStep", Coercion.INT, false),
	CALCULATION_CUTOFF("calculationCutoff", "calculationCutoff", Coercion.INT, false),
	EXACT_HEURISTIC_WEIGHT("exactHeuristicWeight", "exactHeuristicWeight", Coercion.INT, true),
	SHOW_TRANSPORT_INFO("showTransportInfo", "showTransportInfo", Coercion.BOOLEAN, false),
	SHOW_BANK_PICKUP_INFO("showBankPickupInfo", "showBankPickupInfo", Coercion.BOOLEAN, false),
	HIGHLIGHT_BANK_PICKUP_ITEMS("highlightBankPickupItems", "highlightBankPickupItems", Coercion.BOOLEAN, false),
	HIGHLIGHT_SPELLBOOK_SPELLS("highlightSpellbookSpells", "highlightSpellbookSpells", Coercion.BOOLEAN, false),
	HIGHLIGHT_INVENTORY_ITEMS("highlightInventoryItems", "highlightInventoryItems", Coercion.BOOLEAN, false),
	USE_POH("usePoh", "usePoh", Coercion.BOOLEAN, true),
	USE_POH_FAIRY_RING("usePohFairyRing", "usePohFairyRing", Coercion.BOOLEAN, true),
	USE_POH_SPIRIT_TREE("usePohSpiritTree", "usePohSpiritTree", Coercion.BOOLEAN, true),
	USE_TELEPORTATION_PORTALS_POH("useTeleportationPortalsPoh", "useTeleportationPortalsPoh", Coercion.BOOLEAN, true),
	POH_NEXUS_PORTALS("pohNexusPortals", "pohNexusPortals", Coercion.NONE, false),
	POH_JEWELLERY_BOX_TIER("pohJewelleryBoxTier", "pohJewelleryBoxTier", Coercion.JEWELLERY_BOX_TIER, false),
	POH_MOUNTED_ITEMS("pohMountedItems", "pohMountedItems", Coercion.NONE, false),
	USE_POH_MOUNTED_ITEMS("usePohMountedItems", "usePohMountedItems", Coercion.BOOLEAN, true),
	USE_POH_OBELISK("usePohObelisk", "usePohObelisk", Coercion.BOOLEAN, true),
	COST_AGILITY_SHORTCUTS("costAgilityShortcuts", "costAgilityShortcuts", Coercion.INT, true),
	COST_GRAPPLE_SHORTCUTS("costGrappleShortcuts", "costGrappleShortcuts", Coercion.INT, true),
	COST_BOATS("costBoats", "costBoats", Coercion.INT, true),
	COST_CANOES("costCanoes", "costCanoes", Coercion.INT, true),
	COST_CHARTER_SHIPS("costCharterShips", "costCharterShips", Coercion.INT, true),
	COST_SHIPS("costShips", "costShips", Coercion.INT, true),
	COST_FAIRY_RINGS("costFairyRings", "costFairyRings", Coercion.INT, true),
	COST_GNOME_GLIDERS("costGnomeGliders", "costGnomeGliders", Coercion.INT, true),
	COST_HOT_AIR_BALLOONS("costHotAirBalloons", "costHotAirBalloons", Coercion.INT, true),
	COST_MAGIC_CARPETS("costMagicCarpets", "costMagicCarpets", Coercion.INT, true),
	COST_MAGIC_MUSHTREES("costMagicMushtrees", "costMagicMushtrees", Coercion.INT, true),
	COST_MINECARTS("costMinecarts", "costMinecarts", Coercion.INT, true),
	COST_QUETZALS("costQuetzals", "costQuetzals", Coercion.INT, true),
	COST_QUETZAL_WHISTLE("costQuetzalWhistle", "costQuetzals", Coercion.INT, true), // override key pairs with costQuetzals() rather than costQuetzalWhistle()
	COST_SPIRIT_TREES("costSpiritTrees", "costSpiritTrees", Coercion.INT, true),
	COST_NON_CONSUMABLE_TELEPORTATION_ITEMS("costNonConsumableTeleportationItems", "costNonConsumableTeleportationItems", Coercion.INT, true),
	COST_CONSUMABLE_TELEPORTATION_ITEMS("costConsumableTeleportationItems", "costConsumableTeleportationItems", Coercion.INT, true),
	COST_TELEPORTATION_BOXES("costTeleportationBoxes", "costTeleportationBoxes", Coercion.INT, true),
	COST_TELEPORTATION_LEVERS("costTeleportationLevers", "costTeleportationLevers", Coercion.INT, true),
	COST_TELEPORTATION_PORTALS("costTeleportationPortals", "costTeleportationPortals", Coercion.INT, true),
	COST_TELEPORTATION_SPELLS("costTeleportationSpells", "costTeleportationSpells", Coercion.INT, true),
	COST_TELEPORTATION_SPELLS_HOME("costTeleportationSpellsHome", "costTeleportationSpellsHome", Coercion.INT, true),
	COST_TELEPORTATION_MINIGAMES("costTeleportationMinigames", "costTeleportationMinigames", Coercion.INT, true),
	COST_WILDERNESS_OBELISKS("costWildernessObelisks", "costWildernessObelisks", Coercion.INT, true),
	COST_SEASONAL_TRANSPORTS("costSeasonalTransports", "costSeasonalTransports", Coercion.INT, true),
	COST_BANK_VISIT("costBankVisit", "costBankVisit", Coercion.INT, true),
	DRAW_MAP("drawMap", "drawMap", Coercion.BOOLEAN, false),
	DRAW_MINIMAP("drawMinimap", "drawMinimap", Coercion.BOOLEAN, false),
	DRAW_TILES("drawTiles", "drawTiles", Coercion.BOOLEAN, false),
	PATH_STYLE("pathStyle", "pathStyle", Coercion.TILE_STYLE, false),
	SHOW_TELEPORT_PULSE("showTeleportPulse", "showTeleportPulse", Coercion.BOOLEAN, false),
	COLOUR_PATH("colourPath", "colourPath", Coercion.COLOR, false),
	COLOUR_PATH_CALCULATING("colourPathCalculating", "colourPathCalculating", Coercion.COLOR, false),
	COLOUR_PATH_UNREACHABLE("colourPathUnreachable", "colourPathUnreachable", Coercion.COLOR, false),
	COLOUR_TRANSPORTS("colourTransports", "colourTransports", Coercion.COLOR, false),
	COLOUR_COLLISION_MAP("colourCollisionMap", "colourCollisionMap", Coercion.COLOR, false),
	COLOUR_TEXT("colourText", "colourText", Coercion.COLOR, false),
	COLOUR_TELEPORT_PULSE("colourTeleportPulse", "colourTeleportPulse", Coercion.COLOR, false),
	COLOUR_BANK_PICKUP_HIGHLIGHT("colourBankPickupHighlight", "colourBankPickupHighlight", Coercion.COLOR, false),
	CLEAR_PATH_HOTKEY("clearPathHotkey", "clearPathHotkey", Coercion.NONE, false),
	DRAW_TRANSPORTS("drawTransports", "drawTransports", Coercion.BOOLEAN, false),
	DRAW_COLLISION_MAP("drawCollisionMap", "drawCollisionMap", Coercion.BOOLEAN, false),
	DRAW_DEBUG_PANEL("drawDebugPanel", "drawDebugPanel", Coercion.BOOLEAN, false),
	POST_TRANSPORTS("postTransports", "postTransports", Coercion.BOOLEAN, false),
	UNREACHABLE_TEXT("unreachableText", "unreachableText", Coercion.NONE, false),
	BUILT_TELEPORTATION_BOXES("builtTeleportationBoxes", "builtTeleportationBoxes", Coercion.NONE, false),
	BUILT_TELEPORTATION_PORTALS_POH("builtTeleportationPortalsPoh", "builtTeleportationPortalsPoh", Coercion.NONE, false),
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
	/** Whether a change to this key restarts pathfinding. */
	private final boolean routeInvalidating;

	ConfigKey(String key, String getter, Coercion coercion, boolean routeInvalidating)
	{
		this.key = key;
		this.getter = getter;
		this.coercion = coercion;
		this.routeInvalidating = routeInvalidating;
	}

	static ConfigKey forKey(String key)
	{
		return BY_KEY.get(key);
	}
}
