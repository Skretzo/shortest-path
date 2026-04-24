package shortestpath.dashboard;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathfinderProfile;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.ProfilingPathfinder;
import shortestpath.pathfinder.Pathfinder;

/**
 * Generic dashboard test harness.
 *
 * <p>Run with {@code -DrunDashboardVerification=true} to enable assertions.
 * Without the flag all tests are skipped; the task still produces the report.</p>
 *
 * <h3>Gradle invocation</h3>
 * <pre>
 * ./gradlew dashboard
 *   -PdashboardDataset=/dashboard/unit-tests.csv
 *   -PdashboardBundle=unit-tests
 * </pre>
 *
 * <h3>System properties</h3>
 * <table>
 *   <tr><th>Property</th><th>Default</th></tr>
 *   <tr><td>{@code dashboard.dataset}</td><td>{@code /dashboard/routes.csv}</td></tr>
 *   <tr><td>{@code dashboard.bundleName}</td><td>{@code routes}</td></tr>
 *   <tr><td>{@code dashboard.title}</td><td>{@code Dashboard}</td></tr>
 *   <tr><td>{@code dashboard.subtitle}</td><td>dataset label</td></tr>
 *   <tr><td>{@code dashboard.profile}</td><td>{@code true}</td></tr>
 *   <tr><td>{@code reachability.maxTargets}</td><td>{@code 10000}</td></tr>
 * </table>
 */
public class DashboardTest {

    // Universal bank: every item id 0..24999 in qty 1000 – used for BANK preset runs.
    private static final Item[] UNIVERSAL_BANK_ITEMS;

    static {
        UNIVERSAL_BANK_ITEMS = new Item[25000];
        for (int i = 0; i < 25000; i++) {
            UNIVERSAL_BANK_ITEMS[i] = new Item(i, 1000);
        }
    }

    private static final String DATASET_PROPERTY = "dashboard.dataset";
    private static final String BUNDLE_NAME_PROPERTY = DashboardBundlePublisher.BUNDLE_NAME_PROPERTY;
    private static final String DEFAULT_DATASET = "/dashboard/routes.csv";
    private static final int MAX_SCENARIOS = Integer.getInteger("reachability.maxTargets", 10000);

    private final DashboardScenarioLoader loader = new DashboardScenarioLoader();
    private final ProfilerReportWriter profilerReportWriter = new ProfilerReportWriter();
    private final PathfinderDashboardReportWriter reportWriter = new PathfinderDashboardReportWriter();
    private final DashboardBundlePublisher bundlePublisher = new DashboardBundlePublisher();

    private Client client;
    private ItemContainer universalBankContainer;
    private Runnable clientBaseline;

    @Before
    public void setUp() {
        client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
        when(client.getTotalLevel()).thenReturn(2277);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(null);

        universalBankContainer = mock(ItemContainer.class);
        when(universalBankContainer.getItems()).thenReturn(UNIVERSAL_BANK_ITEMS);

        // Capture current stub state as the per-scenario baseline Runnable
        clientBaseline = () -> {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getClientThread()).thenReturn(Thread.currentThread());
            when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
            when(client.getTotalLevel()).thenReturn(2277);
            when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);
            when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
            when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
            when(client.getItemContainer(InventoryID.WORN)).thenReturn(null);
        };
    }

    @Test
    public void allTargetsReachableFromBankStart() throws IOException {
        String dataset = System.getProperty(DATASET_PROPERTY, DEFAULT_DATASET);
        boolean profile = Boolean.parseBoolean(System.getProperty("dashboard.profile", "true"));
        String bundleName = System.getProperty(BUNDLE_NAME_PROPERTY, "routes");
        String reportTitle = System.getProperty("dashboard.title", "Dashboard");
        String reportSubtitle = System.getProperty("dashboard.subtitle", datasetLabel(dataset));
        Path siteRoot = bundlePublisher.getOutputRoot();
        Path reportPath = siteRoot.resolve("bundles").resolve(bundleName).resolve("report.json");

        List<DashboardScenario> allScenarios = loadScenarios(dataset);
        List<DashboardScenario> scenarios = allScenarios.subList(0, Math.min(MAX_SCENARIOS, allScenarios.size()));

        List<PathfinderDashboardModels.RunRecord> runs = new ArrayList<>();
        Map<String, String> routeSummary = new LinkedHashMap<>();
        long started = System.currentTimeMillis();

        int scenarioIndex = 0;
        for (DashboardScenario scenario : scenarios) {
            scenarioIndex++;
            System.out.printf("[%d/%d] %s%n", scenarioIndex, scenarios.size(), scenario.getName());
            System.out.flush();

            DashboardScenarioRunner.ApplyResult applied = DashboardScenarioRunner.apply(
                scenario, client, clientBaseline, universalBankContainer);

            // Default to the Grand Exchange bank when no explicit start is set (e.g. clue-step CSV rows)
            int start = scenario.getStartPoint() != WorldPointUtil.UNDEFINED
                ? scenario.getStartPoint()
                : WorldPointUtil.packWorldPoint(3185, 3436, 0);
            int end = scenario.getEndPoint();
            String category = scenario.getCategory() != null && !scenario.getCategory().isEmpty()
                ? scenario.getCategory()
                : "dashboard";

            PathfinderResult result;
            PathfinderProfile profileData = null;
            if (profile) {
                ProfilingPathfinder profiler = new ProfilingPathfinder(
                    applied.pathfinderConfig, start, Set.of(end));
                profiler.run();
                result = profiler.getResult();
                profileData = profiler.getProfile();
            } else {
                Pathfinder pathfinder = new Pathfinder(applied.pathfinderConfig, start, Set.of(end));
                pathfinder.run();
                result = pathfinder.getResult();
            }

            if (result == null) {
                routeSummary.put(scenario.getName(), "NO_RESULT");
                continue;
            }

            List<PathStep> path = result.getPathSteps();
            int pathLength = path.size();
            boolean reached = isReachedOrAdjacent(result, end);

            // Evaluate assertions
            Boolean assertionPassed = null;
            String assertionMessage = null;
            OptionalInt expectedLength = scenario.getExpectedLength();
            OptionalInt minimumLength = scenario.getMinimumLength();
            if (expectedLength.isPresent()) {
                int expected = expectedLength.getAsInt();
                if (pathLength == expected) {
                    assertionPassed = true;
                } else {
                    assertionPassed = false;
                    assertionMessage = "Expected path length " + expected + " but got " + pathLength;
                }
            } else if (minimumLength.isPresent()) {
                int minimum = minimumLength.getAsInt();
                if (pathLength >= minimum) {
                    assertionPassed = true;
                } else {
                    assertionPassed = false;
                    assertionMessage = "Expected minimum path length " + minimum + " but got " + pathLength;
                }
            }

            List<String> details = List.of(
                "Dataset: " + datasetLabel(dataset),
                "Scenario: " + scenario.getName(),
                "Preset: " + scenario.getPreset(),
                "Expected reachable: true");

            PathfinderDashboardModels.RunRecord run = reportWriter.createRunRecord(
                scenario.getName(),
                category,
                details,
                result,
                applied.pathfinderConfig,
                reached,
                assertionPassed,
                assertionMessage);

            DashboardRunMetadata.apply(run, scenario.getPreset(), applied.dashboardConfig,
                applied.lumbridgeDiaryEliteStub);

            if (profileData != null) {
                profilerReportWriter.populateProfilerData(run, profileData);
            }
            bundlePublisher.externalizeRunHeatmap(bundleName, runs.size(), run);
            runs.add(run);

            routeSummary.put(scenario.getName(), String.format(
                "%.2f ms, %d steps, %s%s | preset: %s | bankVisited: %s | banks: %s",
                result.getElapsedNanos() / 1_000_000.0,
                pathLength,
                result.getTerminationReason(),
                reached ? "" : " [UNREACHABLE]",
                scenario.getPreset(),
                run.bankVisitedOnPath,
                formatBankEventsSummary(run)));
        }

        PathfinderDashboardModels.Report report = reportWriter.createReport(
            reportTitle,
            reportSubtitle,
            System.currentTimeMillis() - started,
            runs,
            reportWriter.createTransportLayerPointsAlwaysAvailable());
        report.bankNamesFromData = BankDestinationLabels.uniqueSortedBankNames();

        bundlePublisher.publishBundle(bundleName, report);

        System.out.println("Dashboard run summary:");
        System.out.println(" - tested: " + scenarios.size() + "/" + allScenarios.size());
        System.out.println(" - dataset: " + dataset);
        for (Map.Entry<String, String> entry : routeSummary.entrySet()) {
            System.out.println(" - " + entry.getKey() + ": " + entry.getValue());
        }

        long unreachable = runs.stream().filter(run -> !run.reached).count();
        assertTrue("Unreachable targets found. See " + reportPath + " and " + siteRoot.resolve("index.html"),
            unreachable == 0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<DashboardScenario> loadScenarios(String dataset) throws IOException {
        if (dataset.startsWith("/")) {
            return loader.loadFromResource(dataset);
        }
        return loader.loadFromCsv(Paths.get(dataset));
    }

    private static String datasetLabel(String dataset) {
        if (dataset.startsWith("/")) {
            return dataset;
        }
        Path path = Paths.get(dataset);
        return path.getFileName() != null ? path.getFileName().toString() : dataset;
    }

    private static boolean isReachedOrAdjacent(PathfinderResult result, int target) {
        if (result == null || result.getPathSteps().isEmpty()) {
            return false;
        }
        List<PathStep> path = result.getPathSteps();
        return WorldPointUtil.distanceBetween(
            path.get(path.size() - 1).getPackedPosition(), target) <= 1;
    }

    private static String formatBankEventsSummary(PathfinderDashboardModels.RunRecord run) {
        if (run.bankEvents == null || run.bankEvents.isEmpty()) {
            return "none";
        }
        return run.bankEvents.stream()
            .map(ev -> {
                String name = ev.bankName != null ? ev.bankName : "unknown";
                return name + " (step " + ev.stepIndex + ")";
            })
            .collect(Collectors.joining(", "));
    }
}
