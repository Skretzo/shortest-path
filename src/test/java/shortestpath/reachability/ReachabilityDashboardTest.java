package shortestpath.reachability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assume;
import shortestpath.TeleportationItem;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.dashboard.DashboardBundlePublisher;
import shortestpath.dashboard.PathfinderDashboardModels;
import shortestpath.dashboard.PathfinderDashboardReportWriter;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.transport.Transport;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReachabilityDashboardTest {
    // Overrides the input dataset path. Resource paths start with `/`; other values are filesystem paths.
    private static final String DATASET_PROPERTY = "reachability.dataset";
    // Overrides the bundle directory name written under `build/reports/pathfinder-dashboard/bundles/`.
    private static final String BUNDLE_ID_PROPERTY = "reachability.bundleId";
    // Overrides the human-readable title shown for this dashboard bundle in the UI.
    private static final String REPORT_TITLE_PROPERTY = "reachability.reportTitle";
    // Overrides the shorter descriptive subtitle shown for this dashboard bundle in the UI.
    private static final String REPORT_SUBTITLE_PROPERTY = "reachability.reportSubtitle";
    private static final String DEFAULT_DATASET = "/reachability/clue_locations_full.csv";
    // A very large number of targets will take a long time to run, and sometimes during testing you
    // want to just test rendering etc on fewer targets.
    private static final int MAX_TARGETS = Integer.getInteger("reachability.maxTargets", 10000);
    // Selects a predefined reachability scenario, which controls the start point and pathfinder setup.
    private static final String SCENARIO_PROPERTY = "reachability.scenario";
    private static final ReachabilityScenario DEFAULT_SCENARIO = ReachabilityScenario.DEFAULT;

    private final ReachabilityTargetLoader targetLoader = new ReachabilityTargetLoader();
    private final PathfinderDashboardReportWriter reportWriter = new PathfinderDashboardReportWriter();
    private final DashboardBundlePublisher bundlePublisher = new DashboardBundlePublisher();

    private Client client;
    private TestShortestPathConfig config;
    private PathfinderConfig pathfinderConfig;
    private ReachabilityScenario scenario;

    @Before
    public void setUp() {
        client = mock(Client.class);
        config = new TestShortestPathConfig();
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        // A maxed account
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
        when(client.getTotalLevel()).thenReturn(2277);
        // With access to fairy rings (this should not be necessary but fairy ring support is globally disabled if
        // these are not set).
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        scenario = ReachabilityScenario.fromSystemProperty(System.getProperty(SCENARIO_PROPERTY));
        config.setCalculationCutoffValue(500);
        config.setUseTeleportationItemsValue(scenario.teleportationItemSetting);
        config.setIncludeBankPathValue(scenario.includeBankPath);
        scenario.configureClient(client);

        pathfinderConfig = new TestPathfinderConfig(client, config);
        scenario.configurePathfinder(pathfinderConfig);
        pathfinderConfig.refresh();
    }

    // Check for each target in the dataset, can we reach it?
    @Test
    public void allTargetsReachableFromBankStart() throws IOException {
        Assume.assumeTrue("Enable with -DrunReachabilityVerification=true",
            Boolean.getBoolean("runReachabilityVerification"));

        String dataset = System.getProperty(DATASET_PROPERTY, DEFAULT_DATASET);
        List<ReachabilityTarget> allTargets = loadTargets(dataset);
        List<ReachabilityTarget> targets = allTargets.subList(0, Math.min(MAX_TARGETS, allTargets.size()));
        List<PathfinderDashboardModels.RunRecord> runs = new ArrayList<>();
        Map<String, String> routeSummary = new LinkedHashMap<>();
        long started = System.currentTimeMillis();
        Path siteRoot = bundlePublisher.getOutputRoot();
        String bundleId = System.getProperty(BUNDLE_ID_PROPERTY, scenario.bundleId);
        String reportTitle = System.getProperty(REPORT_TITLE_PROPERTY, scenario.reportTitle);
        String reportSubtitle = System.getProperty(REPORT_SUBTITLE_PROPERTY, scenario.reportSubtitle);
        Path outputDirectory = siteRoot.resolve("bundles").resolve(bundleId);
        Path reportPath = outputDirectory.resolve("report.json");

        // Reachability is a batch producer: it computes all run records first, then publishes one complete bundle
        // at the end of the sweep rather than incrementally rewriting the bundle after each target.
        for (ReachabilityTarget target : targets) {
            Pathfinder pathfinder = new Pathfinder(
                pathfinderConfig,
                scenario.start,
                Set.of(target.getPackedPoint()),
                null);
            pathfinder.run();
            PathfinderResult result = pathfinder.getResult();
            List<PathStep> path = result != null ? result.getPathSteps() : List.of();
            int pathLength = path.size();
            double elapsedMillis = result != null ? (result.getElapsedNanos() / 1_000_000.0) : 0.0;
            int nodesChecked = result != null ? result.getNodesChecked() : 0;
            int transportsChecked = result != null ? result.getTransportsChecked() : 0;
            // TODO: We should really have this logic in the main pathfinder to help with routing to adjacent but
            // blocked tiles.
            boolean reached = isReachedOrAdjacent(result, target);

            routeSummary.put(
                target.getDescription(),
                String.format(
                    "%.2f ms, %d steps, %d nodes, %d transports, %s%s | start: %s | end: %s",
                    elapsedMillis,
                    pathLength,
                    nodesChecked,
                    transportsChecked,
                    result != null ? result.getTerminationReason() : "NO_RESULT",
                    reached ? "" : " [UNREACHABLE: " + (result != null ? result.getTerminationReason() : "NO_RESULT") + "]",
                    formatRouteSnippet(result, true),
                    formatRouteSnippet(result, false)));

            if (result != null) {
                List<String> details = List.of(
                    "Dataset: " + dataset,
                    "Description: " + target.getDescription(),
                    "Expected reachable: true");
                runs.add(reportWriter.createRunRecord(
                    target.getDescription(),
                    "reachability",
                    details,
                    result,
                    pathfinderConfig,
                    reached,
                    null,
                    null));
            }
        }

        Files.createDirectories(outputDirectory);
        // Publishing updates both `bundles/<bundleId>/report.json` and the shared `bundles/index.json` registry,
        // which is what makes this report show up in the common dashboard frontend.
        bundlePublisher.publishBundle(
            bundleId,
            reportTitle,
            reportSubtitle,
            reportWriter.createReport(
                "Pathfinder Dashboard",
                reportSubtitle,
                System.currentTimeMillis() - started,
                runs,
                reportWriter.createTransportLayerPoints(pathfinderConfig)));

        System.out.println("Reachability route summary:");
        System.out.println(" - scenario: " + scenario.id);
        System.out.println(" - tested targets: " + targets.size() + "/" + allTargets.size());
        System.out.println(" - dataset: " + dataset);

        long unreachable = runs.stream().filter(run -> !run.reached).count();
        assertTrue("Unreachable targets found. See " + reportPath + " and " + siteRoot.resolve("index.html"),
            unreachable == 0);
    }

    private List<ReachabilityTarget> loadTargets(String dataset) throws IOException {
        if (dataset.startsWith("/")) {
            return targetLoader.loadFromResource(dataset);
        }
        return targetLoader.loadFromFile(Paths.get(dataset));
    }

    // This accounts for when the target tile is adjacent to a walkable tile (for instance, a searchable crate).
    static boolean isReachedOrAdjacent(PathfinderResult result, ReachabilityTarget target) {
        if (result == null || result.getPathSteps().isEmpty()) {
            return false;
        }

        List<PathStep> path = result.getPathSteps();
        return WorldPointUtil.distanceBetween(
            path.get(path.size() - 1).getPackedPosition(),
            target.getPackedPoint()) <= 1;
    }

    private String formatRouteSnippet(PathfinderResult result, boolean fromStart) {
        if (result == null || result.getPathSteps().isEmpty()) {
            return "n/a";
        }

        List<PathStep> path = result.getPathSteps();
        List<String> segments = new ArrayList<>();
        int pathSize = path.size();
        int snippetLength = Math.min(3, pathSize);
        int startIndex = fromStart ? 0 : Math.max(0, pathSize - snippetLength);
        int endIndex = fromStart ? snippetLength : pathSize;

        for (int i = startIndex; i < endIndex; i++) {
            int point = path.get(i).getPackedPosition();
            StringBuilder entry = new StringBuilder(formatPoint(point));
            if (i + 1 < pathSize && i + 1 < endIndex + (fromStart ? 1 : 0)) {
                String transport = describeTransport(point, path.get(i + 1).getPackedPosition());
                if (transport != null) {
                    entry.append(" -> ").append(transport);
                }
            }
            segments.add(entry.toString());
        }

        return String.join(" | ", segments);
    }

    private String describeTransport(int origin, int destination) {
        for (Transport transport : pathfinderConfig.getTransports().getOrDefault(origin, Set.of())) {
            if (transport.getDestination() == destination) {
                return describeTransport(transport);
            }
        }

        return null;
    }

    private String describeTransport(Transport transport) {
        String displayInfo = transport.getDisplayInfo();
        if (displayInfo != null && !displayInfo.isBlank()) {
            return displayInfo;
        }

        String objectInfo = transport.getObjectInfo();
        if (objectInfo != null && !objectInfo.isBlank()) {
            return objectInfo;
        }

        return transport.getType() != null ? transport.getType().name() : "transport";
    }

    private static String formatPoint(int packedPoint) {
        return String.format("(%d,%d,%d)",
            WorldPointUtil.unpackWorldX(packedPoint),
            WorldPointUtil.unpackWorldY(packedPoint),
            WorldPointUtil.unpackWorldPlane(packedPoint));
    }
    
    // Default scenario, a maxed account with all teleports able to be used instantly. Useful for a global reachability check.
    private enum ReachabilityScenario {
        DEFAULT(
            "default",
            WorldPointUtil.packWorldPoint(3185, 3436, 0),
            TeleportationItem.ALL,
            false,
            "clue-steps-default",
            "Clue Steps Default",
            "Clue steps sweep from bank start");

        private final String id;
        private final int start;
        private final TeleportationItem teleportationItemSetting;
        private final boolean includeBankPath;
        private final String bundleId;
        private final String reportTitle;
        private final String reportSubtitle;

        ReachabilityScenario(String id, int start, TeleportationItem teleportationItemSetting,
            boolean includeBankPath, String bundleId, String reportTitle, String reportSubtitle) {
            this.id = id;
            this.start = start;
            this.teleportationItemSetting = teleportationItemSetting;
            this.includeBankPath = includeBankPath;
            this.bundleId = bundleId;
            this.reportTitle = reportTitle;
            this.reportSubtitle = reportSubtitle;
        }

        private static ReachabilityScenario fromSystemProperty(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT_SCENARIO;
            }
            for (ReachabilityScenario scenario : values()) {
                if (scenario.id.equalsIgnoreCase(value)) {
                    return scenario;
                }
            }
            throw new IllegalArgumentException("Unknown reachability scenario: " + value);
        }

        private void configureClient(Client client) {
            // No scenario-specific client setup yet.
        }

        private void configurePathfinder(PathfinderConfig pathfinderConfig) {
            // No scenario-specific pathfinder setup yet.
        }
    }
}
