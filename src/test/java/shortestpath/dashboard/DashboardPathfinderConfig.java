package shortestpath.dashboard;

import shortestpath.JewelleryBoxTier;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;

/**
 * Mutable POJO implementation of {@link ShortestPathConfig} for the dashboard test harness.
 * <p>
 * Every functionally relevant setting has a field and a setter so that presets and per-row
 * {@code config_overrides} can configure any combination without touching
 * {@link shortestpath.TestShortestPathConfig} or the unit-test {@link org.mockito.Mock} pattern.
 * <p>
 * Display and UI methods (drawMap, drawMinimap, colours, hotkeys …) are not overridden and
 * fall through to the {@link ShortestPathConfig} interface defaults, which are never called
 * during pathfinding.
 * <p>
 * Defaults are chosen to match the {@link ShortestPathConfig} interface defaults so that
 * {@code routes.csv} routes continue to work without any per-row overrides, with three
 * intentional exceptions:
 * <ul>
 *   <li>{@code calculationCutoff} = 500 (interface default is 5 — far too small for dashboard runs)</li>
 *   <li>{@code useTeleportationItems} = {@code NONE} (presets always set this explicitly)</li>
 *   <li>{@code currencyThreshold} = 10 000 000 (generous; ensures currency-gated routes are never
 *       blocked by a threshold)</li>
 * </ul>
 */
public class DashboardPathfinderConfig implements ShortestPathConfig {

    // -------------------------------------------------------------------------
    // Transport toggles
    // -------------------------------------------------------------------------
    private boolean avoidWilderness = true;
    private boolean useAgilityShortcuts = true;
    private boolean useGrappleShortcuts = false;
    private boolean useBoats = true;
    private boolean useCanoes = false;
    private boolean useCharterShips = false;
    private boolean useShips = true;
    private boolean useFairyRings = true;
    private boolean useGnomeGliders = true;
    private boolean useHotAirBalloons = false;
    private boolean useMagicCarpets = true;
    private boolean useMagicMushtrees = true;
    private boolean useMinecarts = true;
    private boolean useQuetzals = true;
    private boolean useSpiritTrees = true;
    private TeleportationItem useTeleportationItems = TeleportationItem.NONE;
    private boolean useTeleportationLevers = true;
    private boolean useTeleportationPortals = true;
    private boolean useTeleportationSpells = true;
    private boolean useTeleportationMinigames = true;
    private boolean useWildernessObelisks = true;
    private boolean useSeasonalTransports = false;

    // -------------------------------------------------------------------------
    // Pathfinder behaviour
    // -------------------------------------------------------------------------
    private boolean includeBankPath = false;
    private int currencyThreshold = 10_000_000;
    private int calculationCutoff = 500;

    // -------------------------------------------------------------------------
    // POH settings
    // -------------------------------------------------------------------------
    private boolean usePoh = false;
    private boolean usePohFairyRing = false;
    private boolean usePohSpiritTree = false;
    private boolean useTeleportationPortalsPoh = false;
    private JewelleryBoxTier pohJewelleryBoxTier = JewelleryBoxTier.ORNATE;
    private boolean usePohMountedItems = true;
    private boolean usePohObelisk = false;

    // -------------------------------------------------------------------------
    // Built-item state (stored as serialised strings)
    // -------------------------------------------------------------------------
    private String builtTeleportationBoxes = "";
    private String builtTeleportationPortalsPoh = "";

    // -------------------------------------------------------------------------
    // Cost thresholds
    // -------------------------------------------------------------------------
    private int costAgilityShortcuts = 0;
    private int costGrappleShortcuts = 0;
    private int costBoats = 0;
    private int costCanoes = 0;
    private int costCharterShips = 0;
    private int costShips = 0;
    private int costFairyRings = 0;
    private int costGnomeGliders = 0;
    private int costHotAirBalloons = 0;
    private int costMagicCarpets = 0;
    private int costMagicMushtrees = 0;
    private int costMinecarts = 0;
    private int costQuetzals = 0;
    private int costQuetzalWhistle = 0;
    private int costSpiritTrees = 0;
    private int costNonConsumableTeleportationItems = 0;
    private int costConsumableTeleportationItems = 0;
    private int costTeleportationBoxes = 0;
    private int costTeleportationLevers = 0;
    private int costTeleportationPortals = 0;
    private int costTeleportationSpells = 0;
    private int costTeleportationMinigames = 0;
    private int costWildernessObelisks = 0;
    private int costSeasonalTransports = 0;

    // =========================================================================
    // Getters (implements ShortestPathConfig)
    // =========================================================================

    @Override public boolean avoidWilderness() { return avoidWilderness; }
    @Override public boolean useAgilityShortcuts() { return useAgilityShortcuts; }
    @Override public boolean useGrappleShortcuts() { return useGrappleShortcuts; }
    @Override public boolean useBoats() { return useBoats; }
    @Override public boolean useCanoes() { return useCanoes; }
    @Override public boolean useCharterShips() { return useCharterShips; }
    @Override public boolean useShips() { return useShips; }
    @Override public boolean useFairyRings() { return useFairyRings; }
    @Override public boolean useGnomeGliders() { return useGnomeGliders; }
    @Override public boolean useHotAirBalloons() { return useHotAirBalloons; }
    @Override public boolean useMagicCarpets() { return useMagicCarpets; }
    @Override public boolean useMagicMushtrees() { return useMagicMushtrees; }
    @Override public boolean useMinecarts() { return useMinecarts; }
    @Override public boolean useQuetzals() { return useQuetzals; }
    @Override public boolean useSpiritTrees() { return useSpiritTrees; }
    @Override public TeleportationItem useTeleportationItems() { return useTeleportationItems; }
    @Override public boolean useTeleportationLevers() { return useTeleportationLevers; }
    @Override public boolean useTeleportationPortals() { return useTeleportationPortals; }
    @Override public boolean useTeleportationSpells() { return useTeleportationSpells; }
    @Override public boolean useTeleportationMinigames() { return useTeleportationMinigames; }
    @Override public boolean useWildernessObelisks() { return useWildernessObelisks; }
    @Override public boolean useSeasonalTransports() { return useSeasonalTransports; }

    @Override public boolean includeBankPath() { return includeBankPath; }
    @Override public int currencyThreshold() { return currencyThreshold; }
    @Override public int calculationCutoff() { return calculationCutoff; }

    @Override public boolean usePoh() { return usePoh; }
    @Override public boolean usePohFairyRing() { return usePohFairyRing; }
    @Override public boolean usePohSpiritTree() { return usePohSpiritTree; }
    @Override public boolean useTeleportationPortalsPoh() { return useTeleportationPortalsPoh; }
    @Override public JewelleryBoxTier pohJewelleryBoxTier() { return pohJewelleryBoxTier; }
    @Override public boolean usePohMountedItems() { return usePohMountedItems; }
    @Override public boolean usePohObelisk() { return usePohObelisk; }

    @Override public String builtTeleportationBoxes() { return builtTeleportationBoxes; }
    @Override public String builtTeleportationPortalsPoh() { return builtTeleportationPortalsPoh; }

    @Override public int costAgilityShortcuts() { return costAgilityShortcuts; }
    @Override public int costGrappleShortcuts() { return costGrappleShortcuts; }
    @Override public int costBoats() { return costBoats; }
    @Override public int costCanoes() { return costCanoes; }
    @Override public int costCharterShips() { return costCharterShips; }
    @Override public int costShips() { return costShips; }
    @Override public int costFairyRings() { return costFairyRings; }
    @Override public int costGnomeGliders() { return costGnomeGliders; }
    @Override public int costHotAirBalloons() { return costHotAirBalloons; }
    @Override public int costMagicCarpets() { return costMagicCarpets; }
    @Override public int costMagicMushtrees() { return costMagicMushtrees; }
    @Override public int costMinecarts() { return costMinecarts; }
    @Override public int costQuetzals() { return costQuetzals; }
    @Override public int costQuetzalWhistle() { return costQuetzalWhistle; }
    @Override public int costSpiritTrees() { return costSpiritTrees; }
    @Override public int costNonConsumableTeleportationItems() { return costNonConsumableTeleportationItems; }
    @Override public int costConsumableTeleportationItems() { return costConsumableTeleportationItems; }
    @Override public int costTeleportationBoxes() { return costTeleportationBoxes; }
    @Override public int costTeleportationLevers() { return costTeleportationLevers; }
    @Override public int costTeleportationPortals() { return costTeleportationPortals; }
    @Override public int costTeleportationSpells() { return costTeleportationSpells; }
    @Override public int costTeleportationMinigames() { return costTeleportationMinigames; }
    @Override public int costWildernessObelisks() { return costWildernessObelisks; }
    @Override public int costSeasonalTransports() { return costSeasonalTransports; }

    // =========================================================================
    // Abstract void setters required by the interface
    // =========================================================================

    @Override
    public void setBuiltTeleportationBoxes(String content) {
        this.builtTeleportationBoxes = content != null ? content : "";
    }

    @Override
    public void setBuiltTeleportationPortalsPoh(String content) {
        this.builtTeleportationPortalsPoh = content != null ? content : "";
    }

    // =========================================================================
    // Setters (called by DashboardPresets and DashboardScenarioRunner)
    // =========================================================================

    public void setAvoidWilderness(boolean v) { avoidWilderness = v; }
    public void setUseAgilityShortcuts(boolean v) { useAgilityShortcuts = v; }
    public void setUseGrappleShortcuts(boolean v) { useGrappleShortcuts = v; }
    public void setUseBoats(boolean v) { useBoats = v; }
    public void setUseCanoes(boolean v) { useCanoes = v; }
    public void setUseCharterShips(boolean v) { useCharterShips = v; }
    public void setUseShips(boolean v) { useShips = v; }
    public void setUseFairyRings(boolean v) { useFairyRings = v; }
    public void setUseGnomeGliders(boolean v) { useGnomeGliders = v; }
    public void setUseHotAirBalloons(boolean v) { useHotAirBalloons = v; }
    public void setUseMagicCarpets(boolean v) { useMagicCarpets = v; }
    public void setUseMagicMushtrees(boolean v) { useMagicMushtrees = v; }
    public void setUseMinecarts(boolean v) { useMinecarts = v; }
    public void setUseQuetzals(boolean v) { useQuetzals = v; }
    public void setUseSpiritTrees(boolean v) { useSpiritTrees = v; }
    public void setUseTeleportationItems(TeleportationItem v) { useTeleportationItems = v; }
    public void setUseTeleportationLevers(boolean v) { useTeleportationLevers = v; }
    public void setUseTeleportationPortals(boolean v) { useTeleportationPortals = v; }
    public void setUseTeleportationSpells(boolean v) { useTeleportationSpells = v; }
    public void setUseTeleportationMinigames(boolean v) { useTeleportationMinigames = v; }
    public void setUseWildernessObelisks(boolean v) { useWildernessObelisks = v; }
    public void setUseSeasonalTransports(boolean v) { useSeasonalTransports = v; }

    public void setIncludeBankPath(boolean v) { includeBankPath = v; }
    public void setCurrencyThreshold(int v) { currencyThreshold = v; }
    public void setCalculationCutoff(int v) { calculationCutoff = v; }

    public void setUsePoh(boolean v) { usePoh = v; }
    public void setUsePohFairyRing(boolean v) { usePohFairyRing = v; }
    public void setUsePohSpiritTree(boolean v) { usePohSpiritTree = v; }
    public void setUseTeleportationPortalsPoh(boolean v) { useTeleportationPortalsPoh = v; }
    public void setPohJewelleryBoxTier(JewelleryBoxTier v) { pohJewelleryBoxTier = v; }
    public void setUsePohMountedItems(boolean v) { usePohMountedItems = v; }
    public void setUsePohObelisk(boolean v) { usePohObelisk = v; }

    public void setCostAgilityShortcuts(int v) { costAgilityShortcuts = v; }
    public void setCostGrappleShortcuts(int v) { costGrappleShortcuts = v; }
    public void setCostBoats(int v) { costBoats = v; }
    public void setCostCanoes(int v) { costCanoes = v; }
    public void setCostCharterShips(int v) { costCharterShips = v; }
    public void setCostShips(int v) { costShips = v; }
    public void setCostFairyRings(int v) { costFairyRings = v; }
    public void setCostGnomeGliders(int v) { costGnomeGliders = v; }
    public void setCostHotAirBalloons(int v) { costHotAirBalloons = v; }
    public void setCostMagicCarpets(int v) { costMagicCarpets = v; }
    public void setCostMagicMushtrees(int v) { costMagicMushtrees = v; }
    public void setCostMinecarts(int v) { costMinecarts = v; }
    public void setCostQuetzals(int v) { costQuetzals = v; }
    public void setCostQuetzalWhistle(int v) { costQuetzalWhistle = v; }
    public void setCostSpiritTrees(int v) { costSpiritTrees = v; }
    public void setCostNonConsumableTeleportationItems(int v) { costNonConsumableTeleportationItems = v; }
    public void setCostConsumableTeleportationItems(int v) { costConsumableTeleportationItems = v; }
    public void setCostTeleportationBoxes(int v) { costTeleportationBoxes = v; }
    public void setCostTeleportationLevers(int v) { costTeleportationLevers = v; }
    public void setCostTeleportationPortals(int v) { costTeleportationPortals = v; }
    public void setCostTeleportationSpells(int v) { costTeleportationSpells = v; }
    public void setCostTeleportationMinigames(int v) { costTeleportationMinigames = v; }
    public void setCostWildernessObelisks(int v) { costWildernessObelisks = v; }
    public void setCostSeasonalTransports(int v) { costSeasonalTransports = v; }
}
