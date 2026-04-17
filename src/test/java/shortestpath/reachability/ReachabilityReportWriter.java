package shortestpath.reachability;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathStep;

class ReachabilityReportWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    void writeReport(
        Path outputPath,
        String datasetName,
        int start,
        long elapsedMillis,
        List<ReachabilityTarget> targets,
        List<FailureRecord> failures
    ) throws IOException {
        Report report = new Report();
        report.generatedAt = Instant.now().toString();
        report.datasetName = datasetName;
        report.start = worldPoint(start);
        report.summary = new Summary();
        report.summary.totalTargets = targets.size();
        report.summary.unreachableTargets = failures.size();
        report.summary.reachableTargets = targets.size() - failures.size();
        report.summary.elapsedMillis = elapsedMillis;
        report.targets = new ArrayList<>(targets.size());
        for (ReachabilityTarget target : targets) {
            report.targets.add(new NamedPoint(target.getDescription(), worldPoint(target.getPackedPoint())));
        }
        report.failures = failures;

        Files.createDirectories(outputPath.getParent());
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            GSON.toJson(report, writer);
        }
    }

    FailureRecord failureRecord(ReachabilityTarget target, PathfinderResult result) {
        FailureRecord failure = new FailureRecord();
        failure.description = target.getDescription();
        failure.target = worldPoint(target.getPackedPoint());
        failure.reached = result.isReached();
        failure.terminationReason = result.getTerminationReason().name();
        failure.closestReachedPoint = worldPoint(result.getClosestReachedPoint());
        failure.path = path(result.getPathSteps());
        failure.stats = new Stats();
        failure.stats.nodesChecked = result.getNodesChecked();
        failure.stats.transportsChecked = result.getTransportsChecked();
        failure.stats.elapsedNanos = result.getElapsedNanos();
        return failure;
    }

    private static List<WorldPointJson> path(List<PathStep> path) {
        List<WorldPointJson> points = new ArrayList<>(path.size());
        for (PathStep step : path) {
            points.add(worldPoint(step.getPackedPosition()));
        }
        return points;
    }

    private static WorldPointJson worldPoint(int packedPoint) {
        WorldPointJson point = new WorldPointJson();
        point.x = WorldPointUtil.unpackWorldX(packedPoint);
        point.y = WorldPointUtil.unpackWorldY(packedPoint);
        point.plane = WorldPointUtil.unpackWorldPlane(packedPoint);
        return point;
    }

    static class FailureRecord {
        String description;
        WorldPointJson target;
        boolean reached;
        String terminationReason;
        WorldPointJson closestReachedPoint;
        List<WorldPointJson> path;
        Stats stats;
    }

    private static class Report {
        String generatedAt;
        String datasetName;
        WorldPointJson start;
        Summary summary;
        List<NamedPoint> targets;
        List<FailureRecord> failures;
    }

    private static class Summary {
        int totalTargets;
        int reachableTargets;
        int unreachableTargets;
        long elapsedMillis;
    }

    private static class NamedPoint {
        String description;
        WorldPointJson point;

        NamedPoint(String description, WorldPointJson point) {
            this.description = description;
            this.point = point;
        }
    }

    private static class Stats {
        int nodesChecked;
        int transportsChecked;
        long elapsedNanos;
    }

    private static class WorldPointJson {
        int x;
        int y;
        int plane;
    }
}
