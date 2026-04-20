package shortestpath.reachability;

import java.io.IOException;
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
import shortestpath.dashboard.ProfilerReportWriter;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderProfile;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.ProfilingPathfinder;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.transport.Transport;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReachabilityDashboardTest {
    private static final String DATASET_PROPERTY = "reachability.dataset";
    private static final String REPORT_TITLE_PROPERTY = "reachability.reportTitle";
    private static final String REPORT_SUBTITLE_PROPERTY = "reachability.reportSubtitle";
    private static final String DEFAULT_DATASET = "/reachability/routes.csv";
    // A very large number of targets will take a long time to run, and sometimes during testing you
    // want to just test rendering etc on fewer targets.
    private static final int MAX_TARGETS = Integer.getInteger("reachability.maxTargets", 10000);
    private static final String SCENARIO_PROPERTY = "reachability.scenario";
    private static final String PROFILE_PROPERTY = "reachability.profile";
    private static final String BUNDLE_NAME_PROPERTY = DashboardBundlePublisher.BUNDLE_NAME_PROPERTY;
    private static final ReachabilityScenario DEFAULT_SCENARIO = ReachabilityScenario.DEFAULT;

    private final ReachabilityTargetLoader targetLoader = new ReachabilityTargetLoader();
    private final ProfilerReportWriter reportWriter = new ProfilerReportWriter();
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
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
        when(client.getTotalLevel()).thenReturn(2277);
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

    @Test
    public void allTargetsReachableFromBankStart() throws IOException {
        Assume.assumeTrue("Enable with -DrunReachabilityVerification=true",
            Boolean.getBoolean("runReachabilityVerification"));

        String dataset = System.getProperty(DATASET_PROPERTY, DEFAULT_DATASET);
        boolean profile = Boolean.parseBoolean(System.getProperty(PROFILE_PROPERTY, "true"));
        String bundleName = System.getProperty(BUNDLE_NAME_PROPERTY, "routes");
        List<ReachabilityTarget> allTargets = loadTargets(dataset);
        List<ReachabilityTarget> targets = allTargets.subList(0, Math.min(MAX_TARGETS, allTargets.size()));
        List<PathfinderDashboardModels.RunRecord> runs = new ArrayList<>();
        Map<String, String> routeSummary = new LinkedHashMap<>();
        long started = System.currentTimeMillis();
        Path siteRoot = bundlePublisher.getOutputRoot();
        String reportTitle = System.getProperty(REPORT_TITLE_PROPERTY, scenario.reportTitle);
        String reportSubtitle = System.getProperty(REPORT_SUBTITLE_PROPERTY, scenario.reportSubtitle);
        Path reportPath = siteRoot.resolve("bundles").resolve(bundleName).resolve("report.json");

        TeleportationItem currentTeleports = scenario.teleportationItemSetting;
        int targetIndex = 0;

        for (ReachabilityTarget target : targets) {
            targetIndex++;
            System.out.printf("[%d/%d] %s%n", targetIndex, targets.size(), target.getDescription());
            System.out.flush();
            // Reconfigure pathfinder if this target overrides teleport settings
            if (target.hasTeleportOverride() && target.getTeleportOverride() != currentTeleports) {
                currentTeleports = target.getTeleportOverride();
                config.setUseTeleportationItemsValue(currentTeleports);
                pathfinderConfig = new TestPathfinderConfig(client, config);
                scenario.configurePathfinder(pathfinderConfig);
                pathfinderConfig.refresh();
            }

            int start = target.hasStartPoint() ? target.getStartPoint() : scenario.start;
            String category = target.getCategory() != null ? target.getCategory() : "reachability";

            PathfinderResult result;
            PathfinderProfile profileData = null;
            if (profile) {
                ProfilingPathfinder profiler = new ProfilingPathfinder(
                    pathfinderConfig, start, Set.of(target.getPackedPoint()));
                profiler.run();
                result = profiler.getResult();
                profileData = profiler.getProfile();
            } else {
                Pathfinder pathfinder = new Pathfinder(
                    pathfinderConfig, start, Set.of(target.getPackedPoint()));
                pathfinder.run();
                result = pathfinder.getResult();
            }
            List<PathStep> path = result != null ? result.getPathSteps() : List.of();
            int pathLength = path.size();
            double elapsedMillis = result != null ? (result.getElapsedNanos() / 1_000_000.0) : 0.0;
            int nodesChecked = result != null ? result.getNodesChecked() : 0;
            int transportsChecked = result != null ? result.getTransportsChecked() : 0;
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
                    "Dataset: " + datasetLabel(dataset),
                    "Description: " + target.getDescription(),
                    "Expected reachable: true");
                PathfinderDashboardModels.RunRecord run = reportWriter.createRunRecord(
                    target.getDescription(),
                    category,
                    details,
                    result,
                    pathfinderConfig,
                    reached,
                    null,
                    null);
                if (profileData != null) {
                    reportWriter.populateProfilerData(run, profileData);
                }
                bundlePublisher.externalizeRunHeatmap(bundleName, runs.size(), run);
                runs.add(run);
            }
        }

        PathfinderDashboardModels.Report report = reportWriter.createReport(
            reportTitle,
            reportSubtitle,
            System.currentTimeMillis() - started,
            runs,
            reportWriter.createTransportLayerPoints(pathfinderConfig));

        bundlePublisher.publishBundle(bundleName, report);

        System.out.println("Reachability route summary:");
        System.out.println(" - scenario: " + scenario.id);
        System.out.println(" - tested targets: " + targets.size() + "/" + allTargets.size());
        System.out.println(" - dataset: " + dataset);
        for (Map.Entry<String, String> entry : routeSummary.entrySet()) {
            System.out.println(" - " + entry.getKey() + ": " + entry.getValue());
        }

        long unreachable = runs.stream().filter(run -> !run.reached).count();
        assertTrue("Unreachable targets found. See " + reportPath + " and " + siteRoot.resolve("index.html"),
            unreachable == 0);
    }

    private List<ReachabilityTarget> loadTargets(String dataset) throws IOException {
        if (dataset.startsWith("/")) {
            return targetLoader.loadFromResource(dataset);
        }
        return targetLoader.loadFromCsv(Paths.get(dataset));
    }

    private static String datasetLabel(String dataset) {
        if (dataset.startsWith("/")) {
            return dataset;
        }
        Path path = Paths.get(dataset);
        return path.getFileName() != null ? path.getFileName().toString() : dataset;
    }

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
    private enum ReachabilityScenario {
        DEFAULT(
            "default",
            WorldPointUtil.packWorldPoint(3185, 3436, 0),
            TeleportationItem.ALL,
            false,
            "Clue Steps Default",
            "Clue steps sweep from bank start"),
        PROFILER(
            "profiler",
            WorldPointUtil.packWorldPoint(3222, 3218, 0),
            TeleportationItem.ALL,
            false,
            "Profiler",
            "Performance profiling scenarios");

        private final String id;
        private final int start;
        private final TeleportationItem teleportationItemSetting;
        private final boolean includeBankPath;
        private final String reportTitle;
        private final String reportSubtitle;

        ReachabilityScenario(String id, int start, TeleportationItem teleportationItemSetting,
            boolean includeBankPath, String reportTitle, String reportSubtitle) {
            this.id = id;
            this.start = start;
            this.teleportationItemSetting = teleportationItemSetting;
            this.includeBankPath = includeBankPath;
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
