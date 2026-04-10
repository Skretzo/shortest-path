package shortestpath.dashboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;

public final class PathfinderTestDashboardCollector {
    private static final String BUNDLE_ID = "pathfinder-tests";
    private static final String OUTPUT_ROOT_PROPERTY = DashboardBundlePublisher.OUTPUT_ROOT_PROPERTY;
    private static final PathfinderDashboardReportWriter REPORT_WRITER = new PathfinderDashboardReportWriter();
    private static final DashboardBundlePublisher BUNDLE_PUBLISHER = new DashboardBundlePublisher();
    // Scenario tests append run records incrementally as assertions execute, then rewrite the same bundle so the
    // dashboard can be inspected even partway through a test run.
    private static final List<PathfinderDashboardModels.RunRecord> RUNS = new ArrayList<>();
    private static final long STARTED = System.currentTimeMillis();

    private PathfinderTestDashboardCollector() {
    }

    public static synchronized void record(
        String scenarioLabel,
        PathfinderResult result,
        PathfinderConfig config,
        Boolean assertionPassed,
        String assertionMessage
    ) {
        if (result == null || config == null || !isEnabled()) {
            return;
        }

        List<String> details = new ArrayList<>();
        if (scenarioLabel != null && !scenarioLabel.isBlank()) {
            details.add("Scenario: " + scenarioLabel);
        }

        RUNS.add(REPORT_WRITER.createRunRecord(
            scenarioLabel,
            "pathfinder-test",
            details,
            result,
            config,
            assertionPassed,
            assertionMessage));

        writeReport();
    }

    private static boolean isEnabled() {
        String outputRoot = System.getProperty(OUTPUT_ROOT_PROPERTY);
        return outputRoot != null && !outputRoot.isBlank();
    }

    private static void writeReport() {
        try {
            // PathfinderTest has many different scenario configurations, so its transport overlay is a generic
            // "show everything" layer rather than one availability snapshot from a single config state.
            BUNDLE_PUBLISHER.publishBundle(
                BUNDLE_ID,
                "Pathfinder Dashboard",
                "Routes captured from PathfinderTest",
                REPORT_WRITER.createReport(
                    "Pathfinder Dashboard",
                    "Routes captured from PathfinderTest",
                    System.currentTimeMillis() - STARTED,
                    RUNS,
                    REPORT_WRITER.createTransportLayerPointsAlwaysAvailable()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write pathfinder test dashboard", e);
        }
    }
}
