package shortestpath.dashboard;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.QuestState;
import shortestpath.TeleportationItem;
import shortestpath.pathfinder.TestPathfinderConfig;

/**
 * Applies a {@link DashboardScenario} to a Mockito {@link Client} and a fresh
 * {@link DashboardPathfinderConfig}, producing a ready-to-use {@link TestPathfinderConfig}.
 *
 * <p>The sequence of operations matches the old {@code AbstractRouteMode.apply()} contract:
 * <ol>
 *   <li>Run the client baseline (resets all per-call stubs to defaults).</li>
 *   <li>Create a fresh {@link DashboardPathfinderConfig} and apply the preset.</li>
 *   <li>Apply preset-driven varbit stub (BANK preset → diary=0).</li>
 *   <li>Apply per-scenario varbit overrides.</li>
 *   <li>Apply per-scenario varplayer overrides.</li>
 *   <li>Stub inventory and equipment containers.</li>
 *   <li>Apply per-scenario skill level overrides.</li>
 *   <li>Apply {@code config_overrides}.</li>
 *   <li>Create {@link TestPathfinderConfig} and assign bank container.</li>
 *   <li>Call {@code refresh()} on the config.</li>
 * </ol>
 */
public final class DashboardScenarioRunner {

    /**
     * The result of applying a scenario.  Carries both the pathfinder config (for running the
     * algorithm) and the dashboard config snapshot (for metadata like
     * {@link DashboardRunMetadata#apply}).
     */
    public static final class ApplyResult {
        public final TestPathfinderConfig pathfinderConfig;
        public final DashboardPathfinderConfig dashboardConfig;
        public final int lumbridgeDiaryEliteStub;

        private ApplyResult(
                TestPathfinderConfig pathfinderConfig,
                DashboardPathfinderConfig dashboardConfig,
                int lumbridgeDiaryEliteStub) {
            this.pathfinderConfig = pathfinderConfig;
            this.dashboardConfig = dashboardConfig;
            this.lumbridgeDiaryEliteStub = lumbridgeDiaryEliteStub;
        }
    }

    private DashboardScenarioRunner() {
    }

    /**
     * Apply {@code scenario} to produce a fresh {@link ApplyResult}.
     *
     * @param scenario               the scenario to run
     * @param client                 the Mockito-mocked client
     * @param clientBaseline         resets all client stubs to their default values (called first)
     * @param universalBankContainer all-items bank container used for BANK preset runs
     * @return a fully-initialised {@link ApplyResult} ready for pathfinding
     */
    public static ApplyResult apply(
            DashboardScenario scenario,
            Client client,
            Runnable clientBaseline,
            ItemContainer universalBankContainer) {

        // Step 1: reset client stubs to baseline
        clientBaseline.run();

        // Step 2: create fresh config and apply preset
        DashboardPathfinderConfig config = new DashboardPathfinderConfig();
        DashboardPresets.apply(scenario.getPreset(), config);

        // Step 3: preset-driven Lumbridge diary varbit
        int diaryStub = DashboardPresets.lumbridgeDiaryEliteStub(scenario.getPreset());
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(diaryStub);

        // Step 4: per-scenario varbit overrides
        for (Map.Entry<Integer, Integer> entry : scenario.getVarbits().entrySet()) {
            when(client.getVarbitValue(entry.getKey())).thenReturn(entry.getValue());
        }

        // Step 5: per-scenario varplayer overrides
        for (Map.Entry<Integer, Integer> entry : scenario.getVarplayers().entrySet()) {
            when(client.getVarpValue(entry.getKey())).thenReturn(entry.getValue());
        }

        // Step 6: inventory and equipment containers
        stubItemContainer(client, InventoryID.INV, scenario.getInventory());
        stubItemContainer(client, InventoryID.WORN, scenario.getEquipment());

        // Step 7: skill level overrides
        for (Map.Entry<String, Integer> entry : scenario.getSkillLevels().entrySet()) {
            when(client.getBoostedSkillLevel(Skill.valueOf(entry.getKey())))
                .thenReturn(entry.getValue());
        }

        // Step 8: config_overrides dispatch
        applyConfigOverrides(scenario.getConfigOverrides(), config);

        // Step 9: build TestPathfinderConfig
        TestPathfinderConfig pfConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED,
            config.isBypassVarbitChecks(), true);

        // Assign bank container
        boolean isBankPreset = "BANK".equalsIgnoreCase(scenario.getPreset());
        if (isBankPreset) {
            pfConfig.bank = universalBankContainer;
        } else if (!scenario.getBank().isEmpty()) {
            pfConfig.bank = buildItemContainer(scenario.getBank());
        }

        // Step 10: refresh
        pfConfig.refresh();

        return new ApplyResult(pfConfig, config, diaryStub);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void stubItemContainer(Client client, int containerId, List<DashboardScenario.ItemQuantity> items) {
        if (items.isEmpty()) {
            // Return null → PathfinderConfig treats null container as empty
            when(client.getItemContainer(containerId)).thenReturn(null);
        } else {
            ItemContainer container = buildItemContainer(items);
            doReturn(container).when(client).getItemContainer(containerId);
        }
    }

    private static ItemContainer buildItemContainer(List<DashboardScenario.ItemQuantity> items) {
        Item[] itemArray = items.stream()
            .map(iq -> new Item(iq.itemId, iq.quantity))
            .toArray(Item[]::new);
        ItemContainer container = mock(ItemContainer.class);
        when(container.getItems()).thenReturn(itemArray);
        return container;
    }

    /**
     * Dispatches {@code config_overrides} entries to setters on {@code config}.
     * Keys are the camelCase setter name without the "set" prefix (e.g. {@code "useFairyRings"}).
     */
    private static void applyConfigOverrides(Map<String, String> overrides, DashboardPathfinderConfig config) {
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            switch (key) {
                case "avoidWilderness": config.setAvoidWilderness(parseBoolean(value)); break;
                case "useAgilityShortcuts": config.setUseAgilityShortcuts(parseBoolean(value)); break;
                case "useGrappleShortcuts": config.setUseGrappleShortcuts(parseBoolean(value)); break;
                case "useBoats": config.setUseBoats(parseBoolean(value)); break;
                case "useCanoes": config.setUseCanoes(parseBoolean(value)); break;
                case "useCharterShips": config.setUseCharterShips(parseBoolean(value)); break;
                case "useShips": config.setUseShips(parseBoolean(value)); break;
                case "useFairyRings": config.setUseFairyRings(parseBoolean(value)); break;
                case "useGnomeGliders": config.setUseGnomeGliders(parseBoolean(value)); break;
                case "useHotAirBalloons": config.setUseHotAirBalloons(parseBoolean(value)); break;
                case "useMagicCarpets": config.setUseMagicCarpets(parseBoolean(value)); break;
                case "useMagicMushtrees": config.setUseMagicMushtrees(parseBoolean(value)); break;
                case "useMinecarts": config.setUseMinecarts(parseBoolean(value)); break;
                case "useQuetzals": config.setUseQuetzals(parseBoolean(value)); break;
                case "useSpiritTrees": config.setUseSpiritTrees(parseBoolean(value)); break;
                case "useTeleportationItems": config.setUseTeleportationItems(TeleportationItem.valueOf(value)); break;
                case "useTeleportationLevers": config.setUseTeleportationLevers(parseBoolean(value)); break;
                case "useTeleportationPortals": config.setUseTeleportationPortals(parseBoolean(value)); break;
                case "useTeleportationSpells": config.setUseTeleportationSpells(parseBoolean(value)); break;
                case "useTeleportationMinigames": config.setUseTeleportationMinigames(parseBoolean(value)); break;
                case "useWildernessObelisks": config.setUseWildernessObelisks(parseBoolean(value)); break;
                case "useSeasonalTransports": config.setUseSeasonalTransports(parseBoolean(value)); break;
                case "includeBankPath": config.setIncludeBankPath(parseBoolean(value)); break;
                case "bypassVarbitChecks": config.setBypassVarbitChecks(parseBoolean(value)); break;
                case "currencyThreshold": config.setCurrencyThreshold(Integer.parseInt(value)); break;
                case "calculationCutoff": config.setCalculationCutoff(Integer.parseInt(value)); break;
                case "usePoh": config.setUsePoh(parseBoolean(value)); break;
                case "usePohFairyRing": config.setUsePohFairyRing(parseBoolean(value)); break;
                case "usePohSpiritTree": config.setUsePohSpiritTree(parseBoolean(value)); break;
                case "useTeleportationPortalsPoh": config.setUseTeleportationPortalsPoh(parseBoolean(value)); break;
                case "usePohMountedItems": config.setUsePohMountedItems(parseBoolean(value)); break;
                case "usePohObelisk": config.setUsePohObelisk(parseBoolean(value)); break;
                case "costConsumableTeleportationItems": config.setCostConsumableTeleportationItems(Integer.parseInt(value)); break;
                case "costNonConsumableTeleportationItems": config.setCostNonConsumableTeleportationItems(Integer.parseInt(value)); break;
                case "costAgilityShortcuts": config.setCostAgilityShortcuts(Integer.parseInt(value)); break;
                case "costGrappleShortcuts": config.setCostGrappleShortcuts(Integer.parseInt(value)); break;
                case "costFairyRings": config.setCostFairyRings(Integer.parseInt(value)); break;
                case "costBoats": config.setCostBoats(Integer.parseInt(value)); break;
                case "costCanoes": config.setCostCanoes(Integer.parseInt(value)); break;
                case "costCharterShips": config.setCostCharterShips(Integer.parseInt(value)); break;
                case "costShips": config.setCostShips(Integer.parseInt(value)); break;
                case "costGnomeGliders": config.setCostGnomeGliders(Integer.parseInt(value)); break;
                case "costHotAirBalloons": config.setCostHotAirBalloons(Integer.parseInt(value)); break;
                case "costMagicCarpets": config.setCostMagicCarpets(Integer.parseInt(value)); break;
                case "costMagicMushtrees": config.setCostMagicMushtrees(Integer.parseInt(value)); break;
                case "costMinecarts": config.setCostMinecarts(Integer.parseInt(value)); break;
                case "costQuetzals": config.setCostQuetzals(Integer.parseInt(value)); break;
                case "costSpiritTrees": config.setCostSpiritTrees(Integer.parseInt(value)); break;
                case "costTeleportationLevers": config.setCostTeleportationLevers(Integer.parseInt(value)); break;
                case "costTeleportationPortals": config.setCostTeleportationPortals(Integer.parseInt(value)); break;
                case "costTeleportationSpells": config.setCostTeleportationSpells(Integer.parseInt(value)); break;
                case "costTeleportationMinigames": config.setCostTeleportationMinigames(Integer.parseInt(value)); break;
                case "costWildernessObelisks": config.setCostWildernessObelisks(Integer.parseInt(value)); break;
                case "costSeasonalTransports": config.setCostSeasonalTransports(Integer.parseInt(value)); break;
                default:
                    throw new IllegalArgumentException("Unknown config_override key: '" + key + "'");
            }
        }
    }

    private static boolean parseBoolean(String value) {
        return Boolean.parseBoolean(value.trim());
    }
}
