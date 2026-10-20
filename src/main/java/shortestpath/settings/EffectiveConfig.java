package shortestpath.settings;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import net.runelite.client.config.Keybind;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.ShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;

/**
 * Hand-written decorator over a base {@link ShortestPathConfig} that applies
 * the runtime override map. Every interface method is implemented explicitly:
 * methods whose return type has a coercion discipline consult the override map
 * under their own {@code @ConfigItem.keyName} (resolved through the
 * {@link ConfigKey} registry, never derived from the method name) and fall
 * back to the base value when the payload value is absent, the wrong type,
 * or — for the string-parsed enums — a string {@code fromType} does not
 * recognise. Methods of every other type delegate to the base unchanged;
 * payload values for those keys are inert by design.
 */
public final class EffectiveConfig implements ShortestPathConfig
{
	private final ShortestPathConfig base;
	private final Map<String, Object> overrides;

	EffectiveConfig(ShortestPathConfig base, Map<String, Object> overrides)
	{
		this.base = base;
		this.overrides = overrides;
	}

	@SuppressWarnings("unchecked")
	private <T> T override(ConfigKey key, T fallback)
	{
		Object value = overrides.get(key.getKey());
		return value != null && (fallback == null || fallback.getClass().isInstance(value)) ? (T) value : fallback;
	}

	@Override
	public PathfinderBackend pathfinderBackend()
	{
		return base.pathfinderBackend();
	}

	@Override
	public boolean avoidWilderness()
	{
		return override(ConfigKey.AVOID_WILDERNESS, base.avoidWilderness());
	}

	@Override
	public boolean useAgilityShortcuts()
	{
		return override(ConfigKey.USE_AGILITY_SHORTCUTS, base.useAgilityShortcuts());
	}

	@Override
	public boolean useGrappleShortcuts()
	{
		return override(ConfigKey.USE_GRAPPLE_SHORTCUTS, base.useGrappleShortcuts());
	}

	@Override
	public boolean useBoats()
	{
		return override(ConfigKey.USE_BOATS, base.useBoats());
	}

	@Override
	public boolean useCanoes()
	{
		return override(ConfigKey.USE_CANOES, base.useCanoes());
	}

	@Override
	public boolean useCharterShips()
	{
		return override(ConfigKey.USE_CHARTER_SHIPS, base.useCharterShips());
	}

	@Override
	public boolean useShips()
	{
		return override(ConfigKey.USE_SHIPS, base.useShips());
	}

	@Override
	public boolean useFairyRings()
	{
		return override(ConfigKey.USE_FAIRY_RINGS, base.useFairyRings());
	}

	@Override
	public boolean useGnomeGliders()
	{
		return override(ConfigKey.USE_GNOME_GLIDERS, base.useGnomeGliders());
	}

	@Override
	public boolean useHotAirBalloons()
	{
		return override(ConfigKey.USE_HOT_AIR_BALLOONS, base.useHotAirBalloons());
	}

	@Override
	public boolean useMagicCarpets()
	{
		return override(ConfigKey.USE_MAGIC_CARPETS, base.useMagicCarpets());
	}

	@Override
	public boolean useMagicMushtrees()
	{
		return override(ConfigKey.USE_MAGIC_MUSHTREES, base.useMagicMushtrees());
	}

	@Override
	public boolean useMinecarts()
	{
		return override(ConfigKey.USE_MINECARTS, base.useMinecarts());
	}

	@Override
	public boolean useQuetzals()
	{
		return override(ConfigKey.USE_QUETZALS, base.useQuetzals());
	}

	@Override
	public boolean useSpiritTrees()
	{
		return override(ConfigKey.USE_SPIRIT_TREES, base.useSpiritTrees());
	}

	@Override
	public TeleportationItem useTeleportationItems()
	{
		Object value = overrides.get(ConfigKey.USE_TELEPORTATION_ITEMS.getKey());
		if (value instanceof String)
		{
			TeleportationItem parsed = TeleportationItem.fromType((String) value);
			if (parsed != null)
			{
				return parsed;
			}
		}
		return base.useTeleportationItems();
	}

	@Override
	public boolean useTeleportationLevers()
	{
		return override(ConfigKey.USE_TELEPORTATION_LEVERS, base.useTeleportationLevers());
	}

	@Override
	public boolean useTeleportationPortals()
	{
		return override(ConfigKey.USE_TELEPORTATION_PORTALS, base.useTeleportationPortals());
	}

	@Override
	public boolean useTeleportationSpells()
	{
		return override(ConfigKey.USE_TELEPORTATION_SPELLS, base.useTeleportationSpells());
	}

	@Override
	public boolean useTeleportationSpellsHome()
	{
		return override(ConfigKey.USE_TELEPORTATION_SPELLS_HOME, base.useTeleportationSpellsHome());
	}

	@Override
	public boolean useTeleportationMinigames()
	{
		return override(ConfigKey.USE_TELEPORTATION_MINIGAMES, base.useTeleportationMinigames());
	}

	@Override
	public boolean useWildernessObelisks()
	{
		return override(ConfigKey.USE_WILDERNESS_OBELISKS, base.useWildernessObelisks());
	}

	@Override
	public boolean useSeasonalTransports()
	{
		return override(ConfigKey.USE_SEASONAL_TRANSPORTS, base.useSeasonalTransports());
	}

	@Override
	public boolean respawnPrifddinas()
	{
		return override(ConfigKey.RESPAWN_PRIFDDINAS, base.respawnPrifddinas());
	}

	@Override
	public boolean unlockCanoeAxe()
	{
		return override(ConfigKey.UNLOCK_CANOE_AXE, base.unlockCanoeAxe());
	}

	@Override
	public boolean unlockXericsHonour()
	{
		return override(ConfigKey.UNLOCK_XERICS_HONOUR, base.unlockXericsHonour());
	}

	@Override
	public boolean unlockDragontoothPassage()
	{
		return override(ConfigKey.UNLOCK_DRAGONTOOTH_PASSAGE, base.unlockDragontoothPassage());
	}

	@Override
	public boolean unlockMorUlRek()
	{
		return override(ConfigKey.UNLOCK_MOR_UL_REK, base.unlockMorUlRek());
	}

	@Override
	public boolean includeBankPath()
	{
		return override(ConfigKey.INCLUDE_BANK_PATH, base.includeBankPath());
	}

	@Override
	public int currencyThreshold()
	{
		return override(ConfigKey.CURRENCY_THRESHOLD, base.currencyThreshold());
	}

	@Override
	public boolean cancelInstead()
	{
		return override(ConfigKey.CANCEL_INSTEAD, base.cancelInstead());
	}

	@Override
	public int recalculateDistance()
	{
		return override(ConfigKey.RECALCULATE_DISTANCE, base.recalculateDistance());
	}

	@Override
	public int reachedDistance()
	{
		return override(ConfigKey.FINISH_DISTANCE, base.reachedDistance());
	}

	@Override
	public int unreachableTargetDistance()
	{
		return override(ConfigKey.UNREACHABLE_TARGET_DISTANCE_THRESHOLD, base.unreachableTargetDistance());
	}

	@Override
	public boolean showUnreachableText()
	{
		return override(ConfigKey.SHOW_UNREACHABLE_TEXT, base.showUnreachableText());
	}

	@Override
	public TileCounter showTileCounter()
	{
		Object value = overrides.get(ConfigKey.SHOW_TILE_COUNTER.getKey());
		if (value instanceof String)
		{
			TileCounter parsed = TileCounter.fromType((String) value);
			if (parsed != null)
			{
				return parsed;
			}
		}
		return base.showTileCounter();
	}

	@Override
	public int tileCounterStep()
	{
		return override(ConfigKey.TILE_COUNTER_STEP, base.tileCounterStep());
	}

	@Override
	public int calculationCutoff()
	{
		return override(ConfigKey.CALCULATION_CUTOFF, base.calculationCutoff());
	}

	@Override
	public int aStarHeuristicWeight()
	{
		return override(ConfigKey.A_STAR_HEURISTIC_WEIGHT, base.aStarHeuristicWeight());
	}

	@Override
	public boolean collisionAwareBlockedTargets()
	{
		return override(ConfigKey.COLLISION_AWARE_BLOCKED_TARGETS, base.collisionAwareBlockedTargets());
	}

	@Override
	public boolean useSailingMoves()
	{
		return override(ConfigKey.USE_SAILING_MOVES, base.useSailingMoves());
	}

	@Override
	public boolean showTransportInfo()
	{
		return override(ConfigKey.SHOW_TRANSPORT_INFO, base.showTransportInfo());
	}

	@Override
	public boolean showBankPickupInfo()
	{
		return override(ConfigKey.SHOW_BANK_PICKUP_INFO, base.showBankPickupInfo());
	}

	@Override
	public boolean highlightBankPickupItems()
	{
		return override(ConfigKey.HIGHLIGHT_BANK_PICKUP_ITEMS, base.highlightBankPickupItems());
	}

	@Override
	public boolean highlightSpellbookSpells()
	{
		return override(ConfigKey.HIGHLIGHT_SPELLBOOK_SPELLS, base.highlightSpellbookSpells());
	}

	@Override
	public boolean highlightInventoryItems()
	{
		return override(ConfigKey.HIGHLIGHT_INVENTORY_ITEMS, base.highlightInventoryItems());
	}

	@Override
	public boolean usePoh()
	{
		return override(ConfigKey.USE_POH, base.usePoh());
	}

	@Override
	public boolean usePohFairyRing()
	{
		return override(ConfigKey.USE_POH_FAIRY_RING, base.usePohFairyRing());
	}

	@Override
	public boolean usePohSpiritTree()
	{
		return override(ConfigKey.USE_POH_SPIRIT_TREE, base.usePohSpiritTree());
	}

	@Override
	public boolean useTeleportationPortalsPoh()
	{
		return override(ConfigKey.USE_TELEPORTATION_PORTALS_POH, base.useTeleportationPortalsPoh());
	}

	@Override
	public Set<PohNexusPortal> pohNexusPortals()
	{
		return base.pohNexusPortals();
	}

	@Override
	public JewelleryBoxTier pohJewelleryBoxTier()
	{
		Object value = overrides.get(ConfigKey.POH_JEWELLERY_BOX_TIER.getKey());
		if (value instanceof String)
		{
			JewelleryBoxTier parsed = JewelleryBoxTier.fromType((String) value);
			if (parsed != null)
			{
				return parsed;
			}
		}
		return base.pohJewelleryBoxTier();
	}

	@Override
	public Set<PohMountedItem> pohMountedItems()
	{
		return base.pohMountedItems();
	}

	@Override
	public boolean usePohMountedItems()
	{
		return override(ConfigKey.USE_POH_MOUNTED_ITEMS, base.usePohMountedItems());
	}

	@Override
	public boolean usePohObelisk()
	{
		return override(ConfigKey.USE_POH_OBELISK, base.usePohObelisk());
	}

	@Override
	public int costAgilityShortcuts()
	{
		return override(ConfigKey.COST_AGILITY_SHORTCUTS, base.costAgilityShortcuts());
	}

	@Override
	public int costGrappleShortcuts()
	{
		return override(ConfigKey.COST_GRAPPLE_SHORTCUTS, base.costGrappleShortcuts());
	}

	@Override
	public int costBoats()
	{
		return override(ConfigKey.COST_BOATS, base.costBoats());
	}

	@Override
	public int costCanoes()
	{
		return override(ConfigKey.COST_CANOES, base.costCanoes());
	}

	@Override
	public int costCharterShips()
	{
		return override(ConfigKey.COST_CHARTER_SHIPS, base.costCharterShips());
	}

	@Override
	public int costShips()
	{
		return override(ConfigKey.COST_SHIPS, base.costShips());
	}

	@Override
	public int costFairyRings()
	{
		return override(ConfigKey.COST_FAIRY_RINGS, base.costFairyRings());
	}

	@Override
	public int costGnomeGliders()
	{
		return override(ConfigKey.COST_GNOME_GLIDERS, base.costGnomeGliders());
	}

	@Override
	public int costHotAirBalloons()
	{
		return override(ConfigKey.COST_HOT_AIR_BALLOONS, base.costHotAirBalloons());
	}

	@Override
	public int costMagicCarpets()
	{
		return override(ConfigKey.COST_MAGIC_CARPETS, base.costMagicCarpets());
	}

	@Override
	public int costMagicMushtrees()
	{
		return override(ConfigKey.COST_MAGIC_MUSHTREES, base.costMagicMushtrees());
	}

	@Override
	public int costMinecarts()
	{
		return override(ConfigKey.COST_MINECARTS, base.costMinecarts());
	}

	@Override
	public int costQuetzals()
	{
		return override(ConfigKey.COST_QUETZALS, base.costQuetzals());
	}

	@Override
	public int costQuetzalWhistle()
	{
		return override(ConfigKey.COST_QUETZAL_WHISTLE, base.costQuetzalWhistle());
	}

	@Override
	public int costSpiritTrees()
	{
		return override(ConfigKey.COST_SPIRIT_TREES, base.costSpiritTrees());
	}

	@Override
	public int costNonConsumableTeleportationItems()
	{
		return override(ConfigKey.COST_NON_CONSUMABLE_TELEPORTATION_ITEMS, base.costNonConsumableTeleportationItems());
	}

	@Override
	public int costConsumableTeleportationItems()
	{
		return override(ConfigKey.COST_CONSUMABLE_TELEPORTATION_ITEMS, base.costConsumableTeleportationItems());
	}

	@Override
	public int costTeleportationBoxes()
	{
		return override(ConfigKey.COST_TELEPORTATION_BOXES, base.costTeleportationBoxes());
	}

	@Override
	public int costTeleportationLevers()
	{
		return override(ConfigKey.COST_TELEPORTATION_LEVERS, base.costTeleportationLevers());
	}

	@Override
	public int costTeleportationPortals()
	{
		return override(ConfigKey.COST_TELEPORTATION_PORTALS, base.costTeleportationPortals());
	}

	@Override
	public int costTeleportationSpells()
	{
		return override(ConfigKey.COST_TELEPORTATION_SPELLS, base.costTeleportationSpells());
	}

	@Override
	public int costTeleportationSpellsHome()
	{
		return override(ConfigKey.COST_TELEPORTATION_SPELLS_HOME, base.costTeleportationSpellsHome());
	}

	@Override
	public int costTeleportationMinigames()
	{
		return override(ConfigKey.COST_TELEPORTATION_MINIGAMES, base.costTeleportationMinigames());
	}

	@Override
	public int costWildernessObelisks()
	{
		return override(ConfigKey.COST_WILDERNESS_OBELISKS, base.costWildernessObelisks());
	}

	@Override
	public int costSeasonalTransports()
	{
		return override(ConfigKey.COST_SEASONAL_TRANSPORTS, base.costSeasonalTransports());
	}

	@Override
	public int costBankVisit()
	{
		return override(ConfigKey.COST_BANK_VISIT, base.costBankVisit());
	}

	@Override
	public boolean drawMap()
	{
		return override(ConfigKey.DRAW_MAP, base.drawMap());
	}

	@Override
	public boolean drawMinimap()
	{
		return override(ConfigKey.DRAW_MINIMAP, base.drawMinimap());
	}

	@Override
	public boolean drawTiles()
	{
		return override(ConfigKey.DRAW_TILES, base.drawTiles());
	}

	@Override
	public boolean drawClickPoints()
	{
		return override(ConfigKey.DRAW_CLICK_POINTS, base.drawClickPoints());
	}

	@Override
	public TileStyle pathStyle()
	{
		Object value = overrides.get(ConfigKey.PATH_STYLE.getKey());
		if (value instanceof String)
		{
			TileStyle parsed = TileStyle.fromType((String) value);
			if (parsed != null)
			{
				return parsed;
			}
		}
		return base.pathStyle();
	}

	@Override
	public boolean showTeleportPulse()
	{
		return override(ConfigKey.SHOW_TELEPORT_PULSE, base.showTeleportPulse());
	}

	@Override
	public Color colourPath()
	{
		return override(ConfigKey.COLOUR_PATH, base.colourPath());
	}

	@Override
	public Color colourPathCalculating()
	{
		return override(ConfigKey.COLOUR_PATH_CALCULATING, base.colourPathCalculating());
	}

	@Override
	public Color colourPathUnreachable()
	{
		return override(ConfigKey.COLOUR_PATH_UNREACHABLE, base.colourPathUnreachable());
	}

	@Override
	public Color colourTransports()
	{
		return override(ConfigKey.COLOUR_TRANSPORTS, base.colourTransports());
	}

	@Override
	public Color colourCollisionMap()
	{
		return override(ConfigKey.COLOUR_COLLISION_MAP, base.colourCollisionMap());
	}

	@Override
	public Color colourText()
	{
		return override(ConfigKey.COLOUR_TEXT, base.colourText());
	}

	@Override
	public Color colourTeleportPulse()
	{
		return override(ConfigKey.COLOUR_TELEPORT_PULSE, base.colourTeleportPulse());
	}

	@Override
	public Color colourBankPickupHighlight()
	{
		return override(ConfigKey.COLOUR_BANK_PICKUP_HIGHLIGHT, base.colourBankPickupHighlight());
	}

	@Override
	public Keybind clearPathHotkey()
	{
		return base.clearPathHotkey();
	}

	@Override
	public boolean drawTransports()
	{
		return override(ConfigKey.DRAW_TRANSPORTS, base.drawTransports());
	}

	@Override
	public boolean drawCollisionMap()
	{
		return override(ConfigKey.DRAW_COLLISION_MAP, base.drawCollisionMap());
	}

	@Override
	public boolean drawDebugPanel()
	{
		return override(ConfigKey.DRAW_DEBUG_PANEL, base.drawDebugPanel());
	}

	@Override
	public boolean postTransports()
	{
		return override(ConfigKey.POST_TRANSPORTS, base.postTransports());
	}

	@Override
	public String unreachableText()
	{
		return base.unreachableText();
	}

	@Override
	public String builtTeleportationBoxes()
	{
		return base.builtTeleportationBoxes();
	}

	@Override
	public void setBuiltTeleportationBoxes(String content)
	{
		base.setBuiltTeleportationBoxes(content);
	}

	@Override
	public String builtTeleportationPortalsPoh()
	{
		return base.builtTeleportationPortalsPoh();
	}

	@Override
	public void setBuiltTeleportationPortalsPoh(String content)
	{
		base.setBuiltTeleportationPortalsPoh(content);
	}

}
