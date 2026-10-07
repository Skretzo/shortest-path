package shortestpath.settings;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import net.runelite.client.config.Keybind;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.ShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;

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

	@Override
	public PathfinderBackend pathfinderBackend()
	{
		return base.pathfinderBackend();
	}

	@Override
	public boolean avoidWilderness()
	{
		Object value = overrides.get(ConfigKey.AVOID_WILDERNESS.getKey());
		return value instanceof Boolean ? (boolean) value : base.avoidWilderness();
	}

	@Override
	public boolean useAgilityShortcuts()
	{
		Object value = overrides.get(ConfigKey.USE_AGILITY_SHORTCUTS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useAgilityShortcuts();
	}

	@Override
	public boolean useGrappleShortcuts()
	{
		Object value = overrides.get(ConfigKey.USE_GRAPPLE_SHORTCUTS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useGrappleShortcuts();
	}

	@Override
	public boolean useBoats()
	{
		Object value = overrides.get(ConfigKey.USE_BOATS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useBoats();
	}

	@Override
	public boolean useCanoes()
	{
		Object value = overrides.get(ConfigKey.USE_CANOES.getKey());
		return value instanceof Boolean ? (boolean) value : base.useCanoes();
	}

	@Override
	public boolean useCharterShips()
	{
		Object value = overrides.get(ConfigKey.USE_CHARTER_SHIPS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useCharterShips();
	}

	@Override
	public boolean useShips()
	{
		Object value = overrides.get(ConfigKey.USE_SHIPS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useShips();
	}

	@Override
	public boolean useFairyRings()
	{
		Object value = overrides.get(ConfigKey.USE_FAIRY_RINGS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useFairyRings();
	}

	@Override
	public boolean useGnomeGliders()
	{
		Object value = overrides.get(ConfigKey.USE_GNOME_GLIDERS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useGnomeGliders();
	}

	@Override
	public boolean useHotAirBalloons()
	{
		Object value = overrides.get(ConfigKey.USE_HOT_AIR_BALLOONS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useHotAirBalloons();
	}

	@Override
	public boolean useMagicCarpets()
	{
		Object value = overrides.get(ConfigKey.USE_MAGIC_CARPETS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useMagicCarpets();
	}

	@Override
	public boolean useMagicMushtrees()
	{
		Object value = overrides.get(ConfigKey.USE_MAGIC_MUSHTREES.getKey());
		return value instanceof Boolean ? (boolean) value : base.useMagicMushtrees();
	}

	@Override
	public boolean useMinecarts()
	{
		Object value = overrides.get(ConfigKey.USE_MINECARTS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useMinecarts();
	}

	@Override
	public boolean useQuetzals()
	{
		Object value = overrides.get(ConfigKey.USE_QUETZALS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useQuetzals();
	}

	@Override
	public boolean useSpiritTrees()
	{
		Object value = overrides.get(ConfigKey.USE_SPIRIT_TREES.getKey());
		return value instanceof Boolean ? (boolean) value : base.useSpiritTrees();
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
		Object value = overrides.get(ConfigKey.USE_TELEPORTATION_LEVERS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useTeleportationLevers();
	}

	@Override
	public boolean useTeleportationPortals()
	{
		Object value = overrides.get(ConfigKey.USE_TELEPORTATION_PORTALS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useTeleportationPortals();
	}

	@Override
	public boolean useTeleportationSpells()
	{
		Object value = overrides.get(ConfigKey.USE_TELEPORTATION_SPELLS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useTeleportationSpells();
	}

	@Override
	public boolean useTeleportationSpellsHome()
	{
		Object value = overrides.get(ConfigKey.USE_TELEPORTATION_SPELLS_HOME.getKey());
		return value instanceof Boolean ? (boolean) value : base.useTeleportationSpellsHome();
	}

	@Override
	public boolean useTeleportationMinigames()
	{
		Object value = overrides.get(ConfigKey.USE_TELEPORTATION_MINIGAMES.getKey());
		return value instanceof Boolean ? (boolean) value : base.useTeleportationMinigames();
	}

	@Override
	public boolean useWildernessObelisks()
	{
		Object value = overrides.get(ConfigKey.USE_WILDERNESS_OBELISKS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useWildernessObelisks();
	}

	@Override
	public boolean useSeasonalTransports()
	{
		Object value = overrides.get(ConfigKey.USE_SEASONAL_TRANSPORTS.getKey());
		return value instanceof Boolean ? (boolean) value : base.useSeasonalTransports();
	}

	@Override
	public boolean respawnPrifddinas()
	{
		Object value = overrides.get(ConfigKey.RESPAWN_PRIFDDINAS.getKey());
		return value instanceof Boolean ? (boolean) value : base.respawnPrifddinas();
	}

	@Override
	public boolean unlockCanoeAxe()
	{
		Object value = overrides.get(ConfigKey.UNLOCK_CANOE_AXE.getKey());
		return value instanceof Boolean ? (boolean) value : base.unlockCanoeAxe();
	}

	@Override
	public boolean unlockXericsHonour()
	{
		Object value = overrides.get(ConfigKey.UNLOCK_XERICS_HONOUR.getKey());
		return value instanceof Boolean ? (boolean) value : base.unlockXericsHonour();
	}

	@Override
	public boolean unlockDragontoothPassage()
	{
		Object value = overrides.get(ConfigKey.UNLOCK_DRAGONTOOTH_PASSAGE.getKey());
		return value instanceof Boolean ? (boolean) value : base.unlockDragontoothPassage();
	}

	@Override
	public boolean includeBankPath()
	{
		Object value = overrides.get(ConfigKey.INCLUDE_BANK_PATH.getKey());
		return value instanceof Boolean ? (boolean) value : base.includeBankPath();
	}

	@Override
	public int currencyThreshold()
	{
		Object value = overrides.get(ConfigKey.CURRENCY_THRESHOLD.getKey());
		return value instanceof Integer ? (int) value : base.currencyThreshold();
	}

	@Override
	public boolean cancelInstead()
	{
		Object value = overrides.get(ConfigKey.CANCEL_INSTEAD.getKey());
		return value instanceof Boolean ? (boolean) value : base.cancelInstead();
	}

	@Override
	public int recalculateDistance()
	{
		Object value = overrides.get(ConfigKey.RECALCULATE_DISTANCE.getKey());
		return value instanceof Integer ? (int) value : base.recalculateDistance();
	}

	@Override
	public int reachedDistance()
	{
		Object value = overrides.get(ConfigKey.FINISH_DISTANCE.getKey());
		return value instanceof Integer ? (int) value : base.reachedDistance();
	}

	@Override
	public int unreachableTargetDistance()
	{
		Object value = overrides.get(ConfigKey.UNREACHABLE_TARGET_DISTANCE_THRESHOLD.getKey());
		return value instanceof Integer ? (int) value : base.unreachableTargetDistance();
	}

	@Override
	public boolean showUnreachableText()
	{
		Object value = overrides.get(ConfigKey.SHOW_UNREACHABLE_TEXT.getKey());
		return value instanceof Boolean ? (boolean) value : base.showUnreachableText();
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
		Object value = overrides.get(ConfigKey.TILE_COUNTER_STEP.getKey());
		return value instanceof Integer ? (int) value : base.tileCounterStep();
	}

	@Override
	public int calculationCutoff()
	{
		Object value = overrides.get(ConfigKey.CALCULATION_CUTOFF.getKey());
		return value instanceof Integer ? (int) value : base.calculationCutoff();
	}

	@Override
	public int exactHeuristicWeight()
	{
		Object value = overrides.get(ConfigKey.EXACT_HEURISTIC_WEIGHT.getKey());
		return value instanceof Integer ? (int) value : base.exactHeuristicWeight();
	}

	@Override
	public boolean showTransportInfo()
	{
		Object value = overrides.get(ConfigKey.SHOW_TRANSPORT_INFO.getKey());
		return value instanceof Boolean ? (boolean) value : base.showTransportInfo();
	}

	@Override
	public boolean showBankPickupInfo()
	{
		Object value = overrides.get(ConfigKey.SHOW_BANK_PICKUP_INFO.getKey());
		return value instanceof Boolean ? (boolean) value : base.showBankPickupInfo();
	}

	@Override
	public boolean highlightBankPickupItems()
	{
		Object value = overrides.get(ConfigKey.HIGHLIGHT_BANK_PICKUP_ITEMS.getKey());
		return value instanceof Boolean ? (boolean) value : base.highlightBankPickupItems();
	}

	@Override
	public boolean highlightSpellbookSpells()
	{
		Object value = overrides.get(ConfigKey.HIGHLIGHT_SPELLBOOK_SPELLS.getKey());
		return value instanceof Boolean ? (boolean) value : base.highlightSpellbookSpells();
	}

	@Override
	public boolean highlightInventoryItems()
	{
		Object value = overrides.get(ConfigKey.HIGHLIGHT_INVENTORY_ITEMS.getKey());
		return value instanceof Boolean ? (boolean) value : base.highlightInventoryItems();
	}

	@Override
	public boolean usePoh()
	{
		Object value = overrides.get(ConfigKey.USE_POH.getKey());
		return value instanceof Boolean ? (boolean) value : base.usePoh();
	}

	@Override
	public boolean usePohFairyRing()
	{
		Object value = overrides.get(ConfigKey.USE_POH_FAIRY_RING.getKey());
		return value instanceof Boolean ? (boolean) value : base.usePohFairyRing();
	}

	@Override
	public boolean usePohSpiritTree()
	{
		Object value = overrides.get(ConfigKey.USE_POH_SPIRIT_TREE.getKey());
		return value instanceof Boolean ? (boolean) value : base.usePohSpiritTree();
	}

	@Override
	public boolean useTeleportationPortalsPoh()
	{
		Object value = overrides.get(ConfigKey.USE_TELEPORTATION_PORTALS_POH.getKey());
		return value instanceof Boolean ? (boolean) value : base.useTeleportationPortalsPoh();
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
		Object value = overrides.get(ConfigKey.USE_POH_MOUNTED_ITEMS.getKey());
		return value instanceof Boolean ? (boolean) value : base.usePohMountedItems();
	}

	@Override
	public boolean usePohObelisk()
	{
		Object value = overrides.get(ConfigKey.USE_POH_OBELISK.getKey());
		return value instanceof Boolean ? (boolean) value : base.usePohObelisk();
	}

	@Override
	public int costAgilityShortcuts()
	{
		Object value = overrides.get(ConfigKey.COST_AGILITY_SHORTCUTS.getKey());
		return value instanceof Integer ? (int) value : base.costAgilityShortcuts();
	}

	@Override
	public int costGrappleShortcuts()
	{
		Object value = overrides.get(ConfigKey.COST_GRAPPLE_SHORTCUTS.getKey());
		return value instanceof Integer ? (int) value : base.costGrappleShortcuts();
	}

	@Override
	public int costBoats()
	{
		Object value = overrides.get(ConfigKey.COST_BOATS.getKey());
		return value instanceof Integer ? (int) value : base.costBoats();
	}

	@Override
	public int costCanoes()
	{
		Object value = overrides.get(ConfigKey.COST_CANOES.getKey());
		return value instanceof Integer ? (int) value : base.costCanoes();
	}

	@Override
	public int costCharterShips()
	{
		Object value = overrides.get(ConfigKey.COST_CHARTER_SHIPS.getKey());
		return value instanceof Integer ? (int) value : base.costCharterShips();
	}

	@Override
	public int costShips()
	{
		Object value = overrides.get(ConfigKey.COST_SHIPS.getKey());
		return value instanceof Integer ? (int) value : base.costShips();
	}

	@Override
	public int costFairyRings()
	{
		Object value = overrides.get(ConfigKey.COST_FAIRY_RINGS.getKey());
		return value instanceof Integer ? (int) value : base.costFairyRings();
	}

	@Override
	public int costGnomeGliders()
	{
		Object value = overrides.get(ConfigKey.COST_GNOME_GLIDERS.getKey());
		return value instanceof Integer ? (int) value : base.costGnomeGliders();
	}

	@Override
	public int costHotAirBalloons()
	{
		Object value = overrides.get(ConfigKey.COST_HOT_AIR_BALLOONS.getKey());
		return value instanceof Integer ? (int) value : base.costHotAirBalloons();
	}

	@Override
	public int costMagicCarpets()
	{
		Object value = overrides.get(ConfigKey.COST_MAGIC_CARPETS.getKey());
		return value instanceof Integer ? (int) value : base.costMagicCarpets();
	}

	@Override
	public int costMagicMushtrees()
	{
		Object value = overrides.get(ConfigKey.COST_MAGIC_MUSHTREES.getKey());
		return value instanceof Integer ? (int) value : base.costMagicMushtrees();
	}

	@Override
	public int costMinecarts()
	{
		Object value = overrides.get(ConfigKey.COST_MINECARTS.getKey());
		return value instanceof Integer ? (int) value : base.costMinecarts();
	}

	@Override
	public int costQuetzals()
	{
		Object value = overrides.get(ConfigKey.COST_QUETZALS.getKey());
		return value instanceof Integer ? (int) value : base.costQuetzals();
	}

	@Override
	public int costQuetzalWhistle()
	{
		Object value = overrides.get(ConfigKey.COST_QUETZAL_WHISTLE.getKey());
		return value instanceof Integer ? (int) value : base.costQuetzalWhistle();
	}

	@Override
	public int costSpiritTrees()
	{
		Object value = overrides.get(ConfigKey.COST_SPIRIT_TREES.getKey());
		return value instanceof Integer ? (int) value : base.costSpiritTrees();
	}

	@Override
	public int costNonConsumableTeleportationItems()
	{
		Object value = overrides.get(ConfigKey.COST_NON_CONSUMABLE_TELEPORTATION_ITEMS.getKey());
		return value instanceof Integer ? (int) value : base.costNonConsumableTeleportationItems();
	}

	@Override
	public int costConsumableTeleportationItems()
	{
		Object value = overrides.get(ConfigKey.COST_CONSUMABLE_TELEPORTATION_ITEMS.getKey());
		return value instanceof Integer ? (int) value : base.costConsumableTeleportationItems();
	}

	@Override
	public int costTeleportationBoxes()
	{
		Object value = overrides.get(ConfigKey.COST_TELEPORTATION_BOXES.getKey());
		return value instanceof Integer ? (int) value : base.costTeleportationBoxes();
	}

	@Override
	public int costTeleportationLevers()
	{
		Object value = overrides.get(ConfigKey.COST_TELEPORTATION_LEVERS.getKey());
		return value instanceof Integer ? (int) value : base.costTeleportationLevers();
	}

	@Override
	public int costTeleportationPortals()
	{
		Object value = overrides.get(ConfigKey.COST_TELEPORTATION_PORTALS.getKey());
		return value instanceof Integer ? (int) value : base.costTeleportationPortals();
	}

	@Override
	public int costTeleportationSpells()
	{
		Object value = overrides.get(ConfigKey.COST_TELEPORTATION_SPELLS.getKey());
		return value instanceof Integer ? (int) value : base.costTeleportationSpells();
	}

	@Override
	public int costTeleportationSpellsHome()
	{
		Object value = overrides.get(ConfigKey.COST_TELEPORTATION_SPELLS_HOME.getKey());
		return value instanceof Integer ? (int) value : base.costTeleportationSpellsHome();
	}

	@Override
	public int costTeleportationMinigames()
	{
		Object value = overrides.get(ConfigKey.COST_TELEPORTATION_MINIGAMES.getKey());
		return value instanceof Integer ? (int) value : base.costTeleportationMinigames();
	}

	@Override
	public int costWildernessObelisks()
	{
		Object value = overrides.get(ConfigKey.COST_WILDERNESS_OBELISKS.getKey());
		return value instanceof Integer ? (int) value : base.costWildernessObelisks();
	}

	@Override
	public int costSeasonalTransports()
	{
		Object value = overrides.get(ConfigKey.COST_SEASONAL_TRANSPORTS.getKey());
		return value instanceof Integer ? (int) value : base.costSeasonalTransports();
	}

	@Override
	public int costBankVisit()
	{
		Object value = overrides.get(ConfigKey.COST_BANK_VISIT.getKey());
		return value instanceof Integer ? (int) value : base.costBankVisit();
	}

	@Override
	public boolean drawMap()
	{
		Object value = overrides.get(ConfigKey.DRAW_MAP.getKey());
		return value instanceof Boolean ? (boolean) value : base.drawMap();
	}

	@Override
	public boolean drawMinimap()
	{
		Object value = overrides.get(ConfigKey.DRAW_MINIMAP.getKey());
		return value instanceof Boolean ? (boolean) value : base.drawMinimap();
	}

	@Override
	public boolean drawTiles()
	{
		Object value = overrides.get(ConfigKey.DRAW_TILES.getKey());
		return value instanceof Boolean ? (boolean) value : base.drawTiles();
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
		Object value = overrides.get(ConfigKey.SHOW_TELEPORT_PULSE.getKey());
		return value instanceof Boolean ? (boolean) value : base.showTeleportPulse();
	}

	@Override
	public Color colourPath()
	{
		Object value = overrides.get(ConfigKey.COLOUR_PATH.getKey());
		return value instanceof Color ? (Color) value : base.colourPath();
	}

	@Override
	public Color colourPathCalculating()
	{
		Object value = overrides.get(ConfigKey.COLOUR_PATH_CALCULATING.getKey());
		return value instanceof Color ? (Color) value : base.colourPathCalculating();
	}

	@Override
	public Color colourPathUnreachable()
	{
		Object value = overrides.get(ConfigKey.COLOUR_PATH_UNREACHABLE.getKey());
		return value instanceof Color ? (Color) value : base.colourPathUnreachable();
	}

	@Override
	public Color colourTransports()
	{
		Object value = overrides.get(ConfigKey.COLOUR_TRANSPORTS.getKey());
		return value instanceof Color ? (Color) value : base.colourTransports();
	}

	@Override
	public Color colourCollisionMap()
	{
		Object value = overrides.get(ConfigKey.COLOUR_COLLISION_MAP.getKey());
		return value instanceof Color ? (Color) value : base.colourCollisionMap();
	}

	@Override
	public Color colourText()
	{
		Object value = overrides.get(ConfigKey.COLOUR_TEXT.getKey());
		return value instanceof Color ? (Color) value : base.colourText();
	}

	@Override
	public Color colourTeleportPulse()
	{
		Object value = overrides.get(ConfigKey.COLOUR_TELEPORT_PULSE.getKey());
		return value instanceof Color ? (Color) value : base.colourTeleportPulse();
	}

	@Override
	public Color colourBankPickupHighlight()
	{
		Object value = overrides.get(ConfigKey.COLOUR_BANK_PICKUP_HIGHLIGHT.getKey());
		return value instanceof Color ? (Color) value : base.colourBankPickupHighlight();
	}

	@Override
	public Keybind clearPathHotkey()
	{
		return base.clearPathHotkey();
	}

	@Override
	public boolean drawTransports()
	{
		Object value = overrides.get(ConfigKey.DRAW_TRANSPORTS.getKey());
		return value instanceof Boolean ? (boolean) value : base.drawTransports();
	}

	@Override
	public boolean drawCollisionMap()
	{
		Object value = overrides.get(ConfigKey.DRAW_COLLISION_MAP.getKey());
		return value instanceof Boolean ? (boolean) value : base.drawCollisionMap();
	}

	@Override
	public boolean drawDebugPanel()
	{
		Object value = overrides.get(ConfigKey.DRAW_DEBUG_PANEL.getKey());
		return value instanceof Boolean ? (boolean) value : base.drawDebugPanel();
	}

	@Override
	public boolean postTransports()
	{
		Object value = overrides.get(ConfigKey.POST_TRANSPORTS.getKey());
		return value instanceof Boolean ? (boolean) value : base.postTransports();
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

	@Override
	public String blockedTeleportItems()
	{
		Object value = overrides.get(ConfigKey.BLOCKED_TELEPORT_ITEMS.getKey());
		return value instanceof String ? (String) value : base.blockedTeleportItems();
	}

	@Override
	public boolean unlockBalloonLogBasket()
	{
		Object value = overrides.get(ConfigKey.UNLOCK_BALLOON_LOG_BASKET.getKey());
		return value instanceof Boolean ? (boolean) value : base.unlockBalloonLogBasket();
	}

}
