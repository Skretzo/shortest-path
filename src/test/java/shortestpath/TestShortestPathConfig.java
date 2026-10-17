package shortestpath;

import java.util.Set;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;
import shortestpath.settings.ConfigKey;
import shortestpath.settings.KeyedValues;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.BalloonRoute;

/**
 * Test stub over the config surface: the visible {@code @ConfigItem} methods
 * ride the interface defaults, while hidden-key values are supplied through
 * {@link KeyedValues}. Fields left {@code null} mean "the key was never
 * written" — the key's declared default (including the flag-derived set
 * defaults) then applies, exactly like an unwritten persisted key.
 */
public class TestShortestPathConfig implements ShortestPathConfig, KeyedValues
{
	private Integer calculationCutoff = 5;
	private Integer unreachableTargetDistance = 2;
	private Boolean collisionAwareBlockedTargets = true;
	private TeleportationItem useTeleportationItems = TeleportationItem.INVENTORY_NON_CONSUMABLE;
	private Boolean includeBankPath = false;
	private Integer costBankVisit = 0;
	private Boolean unlockCanoeAxe = false;
	private Boolean unlockXericsHonour = false;
	private Boolean unlockDragontoothPassage = false;
	private String blockedTeleportItems = "";
	private Boolean unlockBalloonLogBasket = false;
	private Set<BalloonRoute> balloonLogBasketRoutes = null;
	private Boolean usePoh = false;
	private Set<PohNexusPortal> pohNexusPortals = null;
	private Boolean showBankPickupInfo = false;
	private Boolean showTransportInfo = true;
	private final java.util.Map<String, Object> extras = new java.util.HashMap<>();

	@SuppressWarnings("unused")
	public void setCalculationCutoffValue(int calculationCutoff)
	{
		this.calculationCutoff = calculationCutoff;
	}

	public void setUnreachableTargetDistanceValue(int unreachableTargetDistance)
	{
		this.unreachableTargetDistance = unreachableTargetDistance;
	}

	@SuppressWarnings("unused")
	public void setCollisionAwareBlockedTargetsValue(boolean collisionAwareBlockedTargets)
	{
		this.collisionAwareBlockedTargets = collisionAwareBlockedTargets;
	}

	@SuppressWarnings("unused")
	public void setUseTeleportationItemsValue(TeleportationItem useTeleportationItems)
	{
		this.useTeleportationItems = useTeleportationItems;
	}

	@SuppressWarnings("unused")
	public void setIncludeBankPathValue(boolean includeBankPath)
	{
		this.includeBankPath = includeBankPath;
	}

	@SuppressWarnings("unused")
	public void setCostBankVisitValue(int costBankVisit)
	{
		this.costBankVisit = costBankVisit;
	}

	@SuppressWarnings("unused")
	public void setUnlockCanoeAxeValue(boolean unlockCanoeAxe)
	{
		this.unlockCanoeAxe = unlockCanoeAxe;
	}

	@SuppressWarnings("unused")
	public void setUnlockXericsHonourValue(boolean unlockXericsHonour)
	{
		this.unlockXericsHonour = unlockXericsHonour;
	}

	@SuppressWarnings("unused")
	public void setUnlockDragontoothPassageValue(boolean unlockDragontoothPassage)
	{
		this.unlockDragontoothPassage = unlockDragontoothPassage;
	}

	@SuppressWarnings("unused")
	public void setBlockedTeleportItemsValue(String blockedTeleportItems)
	{
		this.blockedTeleportItems = blockedTeleportItems;
	}

	@SuppressWarnings("unused")
	public void setUnlockBalloonLogBasketValue(boolean unlockBalloonLogBasket)
	{
		this.unlockBalloonLogBasket = unlockBalloonLogBasket;
	}

	@SuppressWarnings("unused")
	public void setBalloonLogBasketRoutesValue(Set<BalloonRoute> balloonLogBasketRoutes)
	{
		this.balloonLogBasketRoutes = balloonLogBasketRoutes;
	}

	@SuppressWarnings("unused")
	public void setUsePohValue(boolean usePoh)
	{
		this.usePoh = usePoh;
	}

	@SuppressWarnings("unused")
	public void setPohNexusPortalsValue(Set<PohNexusPortal> pohNexusPortals)
	{
		this.pohNexusPortals = pohNexusPortals;
	}

	@SuppressWarnings("unused")
	public void setShowBankPickupInfoValue(boolean showBankPickupInfo)
	{
		this.showBankPickupInfo = showBankPickupInfo;
	}

	@SuppressWarnings("unused")
	public void setShowTransportInfoValue(boolean showTransportInfo)
	{
		this.showTransportInfo = showTransportInfo;
	}

	/**
	 * Seed or override a hidden key's value — the {@code when(...).thenReturn}
	 * replacement now that hidden keys are not mockable interface methods.
	 */
	public void set(String keyName, Object v)
	{
		extras.put(keyName, v);
	}


	/**
	 * Baseline-seed every opt-in routing flag to {@code false} — the state
	 * the old {@code @Mock} config presented (unstubbed booleans read
	 * {@code false}). Scenario tests that assume transports are off unless
	 * explicitly enabled call this before their own {@code set}s. Display
	 * and lifecycle flags are untouched so their declared defaults stay.
	 */
	public void disableTransportToggles()
	{
		for (String key : new String[]{
			"avoidWilderness", "includeBankPath", "respawnPrifddinas",
			"useAgilityShortcuts", "useGrappleShortcuts", "useBoats", "useCanoes",
			"useCharterShips", "useShips", "useFairyRings", "useGnomeGliders",
			"useHotAirBalloons", "useMagicCarpets", "useMagicMushtrees",
			"useMinecarts", "useQuetzals", "useSpiritTrees",
			"useTeleportationLevers", "useTeleportationPortals",
			"useTeleportationSpells", "useTeleportationSpellsHome",
			"useTeleportationMinigames", "useWildernessObelisks",
			"useSeasonalTransports", "useSailingMoves",
			"usePoh", "usePohFairyRing", "usePohSpiritTree",
			"useTeleportationPortalsPoh", "usePohMountedItems", "usePohObelisk",
			"unlockCanoeAxe", "unlockXericsHonour", "unlockDragontoothPassage",
			"unlockMorUlRek", "unlockBalloonLogBasket"})
		{
			set(key, false);
		}
		// Numeric keys whose mock default was 0 rather than the declared
		// default — reached-expansion and the whistle differential priced
		// routes differently under the old suite.
		set("unreachableTargetDistanceThreshold", 0);
		set("costQuetzalWhistle", 0);
	}

	public Object value(String keyName)
	{
		if (extras.containsKey(keyName))
		{
			return extras.get(keyName);
		}
		switch (keyName)
		{
			case "calculationCutoff":
				return calculationCutoff;
			case "unreachableTargetDistanceThreshold":
				return unreachableTargetDistance;
			case "collisionAwareBlockedTargets":
				return collisionAwareBlockedTargets;
			case "useTeleportationItems":
				return useTeleportationItems;
			case "includeBankPath":
				return includeBankPath;
			case "costBankVisit":
				return costBankVisit;
			case "unlockCanoeAxe":
				return unlockCanoeAxe;
			case "unlockXericsHonour":
				return unlockXericsHonour;
			case "unlockDragontoothPassage":
				return unlockDragontoothPassage;
			case "blockedTeleportItems":
				return blockedTeleportItems;
			case "unlockBalloonLogBasket":
				return unlockBalloonLogBasket;
			case "balloonLogBasketRoutes":
				return balloonLogBasketRoutes;
			case "usePoh":
				return usePoh;
			case "pohNexusPortals":
				return pohNexusPortals;
			case "showBankPickupInfo":
				return showBankPickupInfo;
			case "showTransportInfo":
				return showTransportInfo;
			default:
				return null;
		}
	}

	/**
	 * The value a stub read reports for a hidden key — the field value when
	 * set, else the key's declared default (flag-derived defaults included).
	 */
	private Object def(String keyName)
	{
		// Mirrors Settings.keyedBase: the declared default runs on the same
		// resolver, so flag-derived defaults see the flag's own default.
		Object value = value(keyName);
		return value != null ? value
			: ConfigKey.forKey(keyName).resolveDefault(row -> def(row.getKey()));
	}

	public TeleportationItem useTeleportationItems()
	{
		return (TeleportationItem) def("useTeleportationItems");
	}

	public boolean useTeleportationMinigames()
	{
		return Boolean.TRUE.equals(def("useTeleportationMinigames"));
	}

	public boolean includeBankPath()
	{
		return Boolean.TRUE.equals(def("includeBankPath"));
	}

	public int costBankVisit()
	{
		return ((Number) def("costBankVisit")).intValue();
	}

	public boolean unlockCanoeAxe()
	{
		return Boolean.TRUE.equals(def("unlockCanoeAxe"));
	}

	public boolean unlockXericsHonour()
	{
		return Boolean.TRUE.equals(def("unlockXericsHonour"));
	}

	public boolean unlockDragontoothPassage()
	{
		return Boolean.TRUE.equals(def("unlockDragontoothPassage"));
	}

	public String blockedTeleportItems()
	{
		return (String) def("blockedTeleportItems");
	}

	public boolean unlockBalloonLogBasket()
	{
		return Boolean.TRUE.equals(def("unlockBalloonLogBasket"));
	}

	public Set<BalloonRoute> balloonLogBasketRoutes()
	{
		return (Set<BalloonRoute>) def("balloonLogBasketRoutes");
	}

	public boolean usePoh()
	{
		return Boolean.TRUE.equals(def("usePoh"));
	}

	public Set<PohNexusPortal> pohNexusPortals()
	{
		return (Set<PohNexusPortal>) def("pohNexusPortals");
	}

	public int calculationCutoff()
	{
		return ((Number) def("calculationCutoff")).intValue();
	}

	public int unreachableTargetDistance()
	{
		return ((Number) def("unreachableTargetDistanceThreshold")).intValue();
	}

	public boolean collisionAwareBlockedTargets()
	{
		return Boolean.TRUE.equals(def("collisionAwareBlockedTargets"));
	}

	public boolean showBankPickupInfo()
	{
		return Boolean.TRUE.equals(def("showBankPickupInfo"));
	}

	public boolean showTransportInfo()
	{
		return Boolean.TRUE.equals(def("showTransportInfo"));
	}
	public int aStarHeuristicWeight()
	{
		return ((Number) def("aStarHeuristicWeight")).intValue();
	}
	public boolean avoidWilderness()
	{
		return Boolean.TRUE.equals(def("avoidWilderness"));
	}
	public String builtTeleportationBoxes()
	{
		return (String) def("builtTeleportationBoxes");
	}
	public String builtTeleportationPortalsPoh()
	{
		return (String) def("builtTeleportationPortalsPoh");
	}
	public boolean cancelInstead()
	{
		return Boolean.TRUE.equals(def("cancelInstead"));
	}
	public int costAgilityShortcuts()
	{
		return ((Number) def("costAgilityShortcuts")).intValue();
	}
	public int costBoats()
	{
		return ((Number) def("costBoats")).intValue();
	}
	public int costCanoes()
	{
		return ((Number) def("costCanoes")).intValue();
	}
	public int costCharterShips()
	{
		return ((Number) def("costCharterShips")).intValue();
	}
	public int costConsumableTeleportationItems()
	{
		return ((Number) def("costConsumableTeleportationItems")).intValue();
	}
	public int costFairyRings()
	{
		return ((Number) def("costFairyRings")).intValue();
	}
	public int costGnomeGliders()
	{
		return ((Number) def("costGnomeGliders")).intValue();
	}
	public int costGrappleShortcuts()
	{
		return ((Number) def("costGrappleShortcuts")).intValue();
	}
	public int costHotAirBalloons()
	{
		return ((Number) def("costHotAirBalloons")).intValue();
	}
	public int costMagicCarpets()
	{
		return ((Number) def("costMagicCarpets")).intValue();
	}
	public int costMagicMushtrees()
	{
		return ((Number) def("costMagicMushtrees")).intValue();
	}
	public int costMinecarts()
	{
		return ((Number) def("costMinecarts")).intValue();
	}
	public int costNonConsumableTeleportationItems()
	{
		return ((Number) def("costNonConsumableTeleportationItems")).intValue();
	}
	public int costQuetzalWhistle()
	{
		return ((Number) def("costQuetzalWhistle")).intValue();
	}
	public int costQuetzals()
	{
		return ((Number) def("costQuetzals")).intValue();
	}
	public int costSeasonalTransports()
	{
		return ((Number) def("costSeasonalTransports")).intValue();
	}
	public int costShips()
	{
		return ((Number) def("costShips")).intValue();
	}
	public int costSpiritTrees()
	{
		return ((Number) def("costSpiritTrees")).intValue();
	}
	public int costTeleportationBoxes()
	{
		return ((Number) def("costTeleportationBoxes")).intValue();
	}
	public int costTeleportationLevers()
	{
		return ((Number) def("costTeleportationLevers")).intValue();
	}
	public int costTeleportationMinigames()
	{
		return ((Number) def("costTeleportationMinigames")).intValue();
	}
	public int costTeleportationPortals()
	{
		return ((Number) def("costTeleportationPortals")).intValue();
	}
	public int costTeleportationSpells()
	{
		return ((Number) def("costTeleportationSpells")).intValue();
	}
	public int costTeleportationSpellsHome()
	{
		return ((Number) def("costTeleportationSpellsHome")).intValue();
	}
	public int costWildernessObelisks()
	{
		return ((Number) def("costWildernessObelisks")).intValue();
	}
	public int currencyThreshold()
	{
		return ((Number) def("currencyThreshold")).intValue();
	}
	public boolean highlightBankPickupItems()
	{
		return Boolean.TRUE.equals(def("highlightBankPickupItems"));
	}
	public boolean highlightInventoryItems()
	{
		return Boolean.TRUE.equals(def("highlightInventoryItems"));
	}
	public boolean highlightSpellbookSpells()
	{
		return Boolean.TRUE.equals(def("highlightSpellbookSpells"));
	}
	public PathfinderBackend pathfinderBackend()
	{
		return (PathfinderBackend) def("pathfinderAlgorithm");
	}
	public JewelleryBoxTier pohJewelleryBoxTier()
	{
		return (JewelleryBoxTier) def("pohJewelleryBoxTier");
	}
	public Set<PohMountedItem> pohMountedItems()
	{
		return (Set<PohMountedItem>) def("pohMountedItems");
	}
	public int reachedDistance()
	{
		return ((Number) def("finishDistance")).intValue();
	}
	public int recalculateDistance()
	{
		return ((Number) def("recalculateDistance")).intValue();
	}
	public boolean respawnPrifddinas()
	{
		return Boolean.TRUE.equals(def("respawnPrifddinas"));
	}
	public TileCounter showTileCounter()
	{
		return (TileCounter) def("showTileCounter");
	}
	public boolean showUnreachableText()
	{
		return Boolean.TRUE.equals(def("showUnreachableText"));
	}
	public int tileCounterStep()
	{
		return ((Number) def("tileCounterStep")).intValue();
	}
	public boolean unlockMorUlRek()
	{
		return Boolean.TRUE.equals(def("unlockMorUlRek"));
	}
	public String unreachableText()
	{
		return (String) def("unreachableText");
	}
	public boolean useAgilityShortcuts()
	{
		return Boolean.TRUE.equals(def("useAgilityShortcuts"));
	}
	public boolean useBoats()
	{
		return Boolean.TRUE.equals(def("useBoats"));
	}
	public boolean useCanoes()
	{
		return Boolean.TRUE.equals(def("useCanoes"));
	}
	public boolean useCharterShips()
	{
		return Boolean.TRUE.equals(def("useCharterShips"));
	}
	public boolean useFairyRings()
	{
		return Boolean.TRUE.equals(def("useFairyRings"));
	}
	public boolean useGnomeGliders()
	{
		return Boolean.TRUE.equals(def("useGnomeGliders"));
	}
	public boolean useGrappleShortcuts()
	{
		return Boolean.TRUE.equals(def("useGrappleShortcuts"));
	}
	public boolean useHotAirBalloons()
	{
		return Boolean.TRUE.equals(def("useHotAirBalloons"));
	}
	public boolean useMagicCarpets()
	{
		return Boolean.TRUE.equals(def("useMagicCarpets"));
	}
	public boolean useMagicMushtrees()
	{
		return Boolean.TRUE.equals(def("useMagicMushtrees"));
	}
	public boolean useMinecarts()
	{
		return Boolean.TRUE.equals(def("useMinecarts"));
	}
	public boolean usePohFairyRing()
	{
		return Boolean.TRUE.equals(def("usePohFairyRing"));
	}
	public boolean usePohMountedItems()
	{
		return Boolean.TRUE.equals(def("usePohMountedItems"));
	}
	public boolean usePohObelisk()
	{
		return Boolean.TRUE.equals(def("usePohObelisk"));
	}
	public boolean usePohSpiritTree()
	{
		return Boolean.TRUE.equals(def("usePohSpiritTree"));
	}
	public boolean useQuetzals()
	{
		return Boolean.TRUE.equals(def("useQuetzals"));
	}
	public boolean useSailingMoves()
	{
		return Boolean.TRUE.equals(def("useSailingMoves"));
	}
	public boolean useSeasonalTransports()
	{
		return Boolean.TRUE.equals(def("useSeasonalTransports"));
	}
	public boolean useShips()
	{
		return Boolean.TRUE.equals(def("useShips"));
	}
	public boolean useSpiritTrees()
	{
		return Boolean.TRUE.equals(def("useSpiritTrees"));
	}
	public boolean useTeleportationLevers()
	{
		return Boolean.TRUE.equals(def("useTeleportationLevers"));
	}
	public boolean useTeleportationPortals()
	{
		return Boolean.TRUE.equals(def("useTeleportationPortals"));
	}
	public boolean useTeleportationPortalsPoh()
	{
		return Boolean.TRUE.equals(def("useTeleportationPortalsPoh"));
	}
	public boolean useTeleportationSpells()
	{
		return Boolean.TRUE.equals(def("useTeleportationSpells"));
	}
	public boolean useTeleportationSpellsHome()
	{
		return Boolean.TRUE.equals(def("useTeleportationSpellsHome"));
	}
	public boolean useWildernessObelisks()
	{
		return Boolean.TRUE.equals(def("useWildernessObelisks"));
	}
}
