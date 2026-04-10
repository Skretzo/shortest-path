package shortestpath.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathStep;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

public class PathfinderDashboardReportWriter {
    // This class is the translation layer from pathfinder/test domain objects into the JSON model consumed by the
    // static dashboard frontend. Producers build run records here, then hand the finished report to the publisher.
    public PathfinderDashboardModels.Report createReport(
        String title,
        String subtitle,
        long elapsedMillis,
        List<PathfinderDashboardModels.RunRecord> runs,
        List<PathfinderDashboardModels.TransportLayerTransport> transportLayers
    ) {
        PathfinderDashboardModels.Report report = new PathfinderDashboardModels.Report();
        report.generatedAt = Instant.now().toString();
        report.title = title;
        report.subtitle = subtitle;
        report.summary = new PathfinderDashboardModels.Summary();
        report.summary.totalRuns = runs.size();
        report.summary.successfulRuns = (int) runs.stream().filter(run -> run.reached).count();
        report.summary.failedRuns = runs.size() - report.summary.successfulRuns;
        report.summary.elapsedMillis = elapsedMillis;
        report.transportLayers = transportLayers;
        report.runs = runs;
        return report;
    }

    public PathfinderDashboardModels.RunRecord createRunRecord(
        String name,
        String category,
        List<String> details,
        PathfinderResult result,
        PathfinderConfig config,
        Boolean assertionPassed,
        String assertionMessage
    ) {
        return createRunRecord(
            name,
            category,
            details,
            result,
            config,
            result.isReached(),
            assertionPassed,
            assertionMessage);
    }

    public PathfinderDashboardModels.RunRecord createRunRecord(
        String name,
        String category,
        List<String> details,
        PathfinderResult result,
        PathfinderConfig config,
        boolean reached,
        Boolean assertionPassed,
        String assertionMessage
    ) {
        PathfinderDashboardModels.RunRecord run = new PathfinderDashboardModels.RunRecord();
        run.name = name;
        run.category = category;
        run.assertionPassed = assertionPassed;
        run.assertionMessage = assertionMessage;
        run.reached = reached;
        run.terminationReason = result.getTerminationReason().name();
        run.start = worldPoint(result.getStart());
        run.target = worldPoint(result.getTarget());
        run.closestReachedPoint = worldPoint(result.getClosestReachedPoint());
        run.path = path(result.getPathSteps());
        run.stats = stats(result);
        run.transports = transportSteps(result.getPathSteps(), config);
        run.markers = markers(result, config);
        run.details = details;
        return run;
    }

    // Reachability dashboards use one concrete PathfinderConfig, so the overlay can classify transports by whether
    // they are available before banking, after banking, or not available in that config at all.
    public List<PathfinderDashboardModels.TransportLayerTransport> createTransportLayerPoints(PathfinderConfig config) {
        Map<TransportKey, Transport> allTransports = indexTransports(TransportLoader.loadAllFromResources());
        Set<TransportKey> withoutBank = collectAvailableTransportKeys(config, false);
        Set<TransportKey> withBank = collectAvailableTransportKeys(config, true);
        return createTransportLayerPoints(allTransports, withoutBank, withBank);
    }

    // Scenario dashboards mix many bespoke test setups together, so the most useful overlay is the full transport
    // graph rendered as available everywhere rather than a misleading snapshot from one scenario's config.
    public List<PathfinderDashboardModels.TransportLayerTransport> createTransportLayerPointsAlwaysAvailable() {
        Map<TransportKey, Transport> allTransports = indexTransports(TransportLoader.loadAllFromResources());
        return createTransportLayerPoints(allTransports, allTransports.keySet(), allTransports.keySet());
    }

    private List<PathfinderDashboardModels.TransportLayerTransport> createTransportLayerPoints(
        Map<TransportKey, Transport> allTransports,
        Set<TransportKey> withoutBank,
        Set<TransportKey> withBank
    ) {
        List<PathfinderDashboardModels.TransportLayerTransport> points = new ArrayList<>();
        for (Map.Entry<TransportKey, Transport> entry : allTransports.entrySet()) {
            Transport transport = entry.getValue();
            String validity = withoutBank.contains(entry.getKey())
                ? "INVENTORY_VALID"
                : withBank.contains(entry.getKey())
                    ? "BANK_VALID"
                    : "INVALID";
            PathfinderDashboardModels.TransportLayerTransport transportJson = new PathfinderDashboardModels.TransportLayerTransport();
            transportJson.type = transport.getType() != null ? transport.getType().name() : "TRANSPORT";
            transportJson.validity = validity;
            transportJson.displayInfo = transport.getDisplayInfo();
            transportJson.objectInfo = transport.getObjectInfo();
            transportJson.origin = transport.getOrigin() == shortestpath.WorldPointUtil.UNDEFINED ? null : worldPoint(transport.getOrigin());
            transportJson.destination = worldPoint(transport.getDestination());
            points.add(transportJson);
        }
        return points;
    }

    private static Map<TransportKey, Transport> indexTransports(Map<Integer, Set<Transport>> transportsByOrigin) {
        Map<TransportKey, Transport> indexed = new HashMap<>();
        for (Set<Transport> transports : transportsByOrigin.values()) {
            for (Transport transport : transports) {
                indexed.put(new TransportKey(transport), transport);
            }
        }
        return indexed;
    }

    private static Set<TransportKey> collectAvailableTransportKeys(PathfinderConfig config, boolean bankVisited) {
        Set<TransportKey> keys = new HashSet<>();

        for (int origin : config.getTransportsPacked(bankVisited).keys()) {
            for (Transport transport : config.getTransportsPacked(bankVisited).getOrDefault(origin, Set.of())) {
                keys.add(new TransportKey(transport));
            }
        }

        for (Transport transport : config.getUsableTeleports(bankVisited)) {
            keys.add(new TransportKey(transport));
        }

        return keys;
    }

    private static PathfinderDashboardModels.Stats stats(PathfinderResult result) {
        PathfinderDashboardModels.Stats stats = new PathfinderDashboardModels.Stats();
        stats.nodesChecked = result.getNodesChecked();
        stats.transportsChecked = result.getTransportsChecked();
        stats.elapsedNanos = result.getElapsedNanos();
        return stats;
    }

    private static List<PathfinderDashboardModels.WorldPointJson> path(List<PathStep> path) {
        List<PathfinderDashboardModels.WorldPointJson> points = new ArrayList<>(path.size());
        for (PathStep step : path) {
            points.add(worldPoint(step));
        }
        return points;
    }

    private static List<PathfinderDashboardModels.TransportStep> transportSteps(List<PathStep> path, PathfinderConfig config) {
        List<PathfinderDashboardModels.TransportStep> steps = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            PathStep originStep = path.get(i - 1);
            PathStep destinationStep = path.get(i);
            int origin = originStep.getPackedPosition();
            int destination = destinationStep.getPackedPosition();
            boolean bankVisited = destinationStep.isBankVisited();

            Set<Transport> originTransports = new java.util.HashSet<>(
                config.getTransportsPacked(bankVisited).getOrDefault(origin, Set.of()));
            originTransports.addAll(config.getUsableTeleports(bankVisited));

            for (Transport transport : originTransports) {
                if (transport.getDestination() == destination) {
                    PathfinderDashboardModels.TransportStep step = new PathfinderDashboardModels.TransportStep();
                    step.stepIndex = i;
                    step.type = transport.getType() != null ? transport.getType().name() : "TRANSPORT";
                    step.displayInfo = transport.getDisplayInfo();
                    step.objectInfo = transport.getObjectInfo();
                    step.origin = worldPoint(origin);
                    step.destination = worldPoint(destination);
                    steps.add(step);
                }
            }
        }
        return steps;
    }

    private static List<PathfinderDashboardModels.Marker> markers(PathfinderResult result, PathfinderConfig config) {
        List<PathfinderDashboardModels.Marker> markers = new ArrayList<>();
        addMarker(markers, "start", "Start", result.getStart());
        addMarker(markers, "target", "Target", result.getTarget());
        addMarker(markers, "closest", "Closest reached", result.getClosestReachedPoint());

        List<PathStep> path = result.getPathSteps();
        for (int i = 0; i < path.size(); i++) {
            PathStep step = path.get(i);
            boolean transitionedIntoBankState = step.isBankVisited() && (i == 0 || !path.get(i - 1).isBankVisited());
            if (transitionedIntoBankState) {
                int transitionIndex = Math.max(0, i - 1);
                addMarker(markers, "bank", "Bank visited at step " + transitionIndex, path.get(transitionIndex).getPackedPosition());
            }
        }
        return markers;
    }

    private static void addMarker(List<PathfinderDashboardModels.Marker> markers, String kind, String label, int packedPoint) {
        PathfinderDashboardModels.Marker marker = new PathfinderDashboardModels.Marker();
        marker.kind = kind;
        marker.label = label;
        marker.point = worldPoint(packedPoint);
        markers.add(marker);
    }

    private static PathfinderDashboardModels.WorldPointJson worldPoint(int packedPoint) {
        return worldPoint(new PathStep(packedPoint, false));
    }

    private static PathfinderDashboardModels.WorldPointJson worldPoint(PathStep step) {
        PathfinderDashboardModels.WorldPointJson point = new PathfinderDashboardModels.WorldPointJson();
        point.x = WorldPointUtil.unpackWorldX(step.getPackedPosition());
        point.y = WorldPointUtil.unpackWorldY(step.getPackedPosition());
        point.plane = WorldPointUtil.unpackWorldPlane(step.getPackedPosition());
        point.bankVisited = step.isBankVisited();
        return point;
    }

    private static final class TransportKey {
        private final int origin;
        private final int destination;
        private final String type;
        private final String displayInfo;
        private final String objectInfo;

        private TransportKey(Transport transport) {
            this.origin = transport.getOrigin();
            this.destination = transport.getDestination();
            this.type = transport.getType() != null ? transport.getType().name() : "TRANSPORT";
            this.displayInfo = transport.getDisplayInfo();
            this.objectInfo = transport.getObjectInfo();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TransportKey)) {
                return false;
            }
            TransportKey that = (TransportKey) other;
            return origin == that.origin
                && destination == that.destination
                && java.util.Objects.equals(type, that.type)
                && java.util.Objects.equals(displayInfo, that.displayInfo)
                && java.util.Objects.equals(objectInfo, that.objectInfo);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(origin, destination, type, displayInfo, objectInfo);
        }
    }
}
