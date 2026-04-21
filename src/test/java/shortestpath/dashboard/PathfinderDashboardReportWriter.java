package shortestpath.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.ItemVariations;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.PathStep;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

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

    /**
     * Same as {@link #createReport(String, String, long, List, List)} with optional reachability scenario metadata.
     */
    public PathfinderDashboardModels.Report createReport(
        String title,
        String subtitle,
        long elapsedMillis,
        List<PathfinderDashboardModels.RunRecord> runs,
        List<PathfinderDashboardModels.TransportLayerTransport> transportLayers,
        String scenarioId,
        PathfinderDashboardModels.WorldPointJson scenarioDefaultStart) {
        PathfinderDashboardModels.Report report = createReport(title, subtitle, elapsedMillis, runs, transportLayers);
        report.scenarioId = scenarioId;
        report.scenarioDefaultStart = scenarioDefaultStart;
        return report;
    }

    /** Packed world point for JSON (no {@code bankVisited} semantics). */
    public static PathfinderDashboardModels.WorldPointJson worldPointJsonPacked(int packedPoint) {
        return worldPoint(packedPoint);
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
        addBankPathMetadata(run, result.getPathSteps());
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
        // Per-step seen-set. Some transports (e.g. home-teleport spells) are defined as multiple
        // CSV rows that differ only in varbit guards for random cast-animation variants; tests
        // bypass varbit checks so every variant would be emitted. Collapse entries that agree on
        // everything the dashboard renders. We still keep genuinely distinct candidates (e.g.
        // Varrock tablet vs Varrock Teleport spell) because they differ by type / displayInfo.
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < path.size(); i++) {
            seen.clear();
            PathStep originStep = path.get(i - 1);
            PathStep destinationStep = path.get(i);
            int origin = originStep.getPackedPosition();
            int destination = destinationStep.getPackedPosition();
            boolean bankVisited = destinationStep.isBankVisited();

            // Physical transports at the origin tile — these are always shown.
            Set<Transport> physicalTransports = config.getTransportsPacked(bankVisited).getOrDefault(origin, Set.of());
            boolean physicalCoversDestination = false;
            for (Transport transport : physicalTransports) {
                if (transport.getDestination() == destination) {
                    physicalCoversDestination = true;
                    addTransportStep(steps, seen, i, origin, destination, transport);
                }
            }

            // Usable teleports (abstract-node transports) — skip if:
            //  (a) a physical transport at this origin already covers the destination (physical wins
            //      the dequeue race because usable teleports carry a differential-cost PQ penalty), or
            //  (b) the step is only 1 tile, which means it is a walking step and the matching
            //      teleport destination is a coincidence, not an actual teleport use.
            if (!physicalCoversDestination && WorldPointUtil.distanceBetween2D(origin, destination) > 1) {
                for (Transport transport : config.getUsableTeleports(bankVisited)) {
                    if (transport.getDestination() == destination) {
                        addTransportStep(steps, seen, i, origin, destination, transport);
                    }
                }
            }
        }
        return steps;
    }

    private static void addTransportStep(
            List<PathfinderDashboardModels.TransportStep> steps, Set<String> seen,
            int stepIndex, int origin, int destination, Transport transport) {
        String type = transport.getType() != null ? transport.getType().name() : "TRANSPORT";
        String key = type + "|" + transport.getDisplayInfo() + "|" + transport.getObjectInfo()
                + "|" + origin + "|" + destination;
        if (!seen.add(key)) {
            return;
        }
        PathfinderDashboardModels.TransportStep step = new PathfinderDashboardModels.TransportStep();
        step.stepIndex = stepIndex;
        step.type = type;
        step.displayInfo = transport.getDisplayInfo();
        step.objectInfo = transport.getObjectInfo();
        step.origin = worldPoint(origin);
        step.destination = worldPoint(destination);
        step.itemRequirements = itemLabelsForTransport(transport);
        steps.add(step);
    }

    /**
     * Human-readable item requirements for a transport, derived from the TSV item column plus
     * a synthesized staff entry for fairy rings (the Lumbridge-elite rule lives in
     * {@link shortestpath.pathfinder.PathfinderConfig}, not in the transport data itself).
     */
    static List<String> itemLabelsForTransport(Transport transport) {
        List<String> labels = new ArrayList<>();
        if (transport.getType() == TransportType.FAIRY_RING) {
            // Always synthesize: the reachability dashboard only surfaces pickups on bank runs,
            // where the Lumbridge elite diary is stubbed off, so a fairy-ring staff is needed.
            // Spell out both acceptable staves — the pathfinder's DRAMEN_STAFF check accepts the
            // Lunar Moonclan liminal staff too, and calling it out avoids confusion.
            labels.add("Dramen staff or Lunar staff");
        }
        TransportItems items = transport.getItemRequirements();
        if (items != null) {
            for (ItemRequirement req : items.getRequirements()) {
                String label = formatItemRequirement(req);
                if (label != null) {
                    labels.add(label);
                }
            }
        }
        return labels;
    }

    /**
     * Formats a single {@link ItemRequirement} as {@code "N× Label"} (quantity only when > 1).
     * Reverse-maps item IDs to {@link ItemVariations} names and joins multiple alternatives with
     * " or " (e.g. "Dramen staff or Lunar staff"). TSV entries that use a raw item id (e.g.
     * {@code 8007=1} for a Varrock teleport tablet) fall back to the {@code ItemID} constant's
     * humanised name.
     */
    private static String formatItemRequirement(ItemRequirement req) {
        int[] ids = req.getItemIds();
        if (ids == null || ids.length == 0) {
            return null;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (int id : ids) {
            String name = resolveItemName(id);
            if (name != null) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            names.add("item " + Arrays.toString(ids));
        }
        String joined = String.join(" or ", names);
        int qty = req.getQuantity();
        return qty > 1 ? (qty + "× " + joined) : joined;
    }

    /** Human-readable name for a single item id: {@link ItemVariations} first, then a cleaned
     * {@link net.runelite.api.gameval.ItemID} constant name, then {@code "Item <id>"}. */
    private static String resolveItemName(int id) {
        ItemVariations variation = ITEM_ID_TO_VARIATION.get(id);
        if (variation != null) {
            return humanizeVariation(variation);
        }
        String constant = ITEM_ID_CONSTANT_NAMES.get(id);
        if (constant != null) {
            return humanizeItemConstant(constant);
        }
        return "Item " + id;
    }

    private static String humanizeVariation(ItemVariations variation) {
        return capitalize(variation.name().toLowerCase().replace('_', ' '));
    }

    /** Cleans cryptic {@code ItemID} constant names (e.g. {@code POH_TABLET_VARROCKTELEPORT} →
     * "Varrock teleport tablet") while falling back to a plain lower-cased form otherwise. */
    private static String humanizeItemConstant(String constant) {
        if (constant.startsWith("POH_TABLET_") && constant.endsWith("TELEPORT")) {
            String place = constant.substring("POH_TABLET_".length(), constant.length() - "TELEPORT".length());
            return capitalize(place.toLowerCase()) + " teleport tablet";
        }
        if (constant.startsWith("TELETAB_")) {
            return capitalize(constant.substring("TELETAB_".length()).toLowerCase().replace('_', ' ')) + " teleport tablet";
        }
        if (constant.startsWith("NZONE_TELETAB_")) {
            return capitalize(constant.substring("NZONE_TELETAB_".length()).toLowerCase().replace('_', ' ')) + " teleport tablet";
        }
        if (constant.equals("SKILLCAPE_AD") || constant.equals("SKILLCAPE_AD_TRIMMED")) {
            return "Achievement diary cape";
        }
        if (constant.startsWith("RING_OF_DUELING")) {
            return "Ring of dueling";
        }
        if (constant.startsWith("GAMES_NECKLACE")) {
            return "Games necklace";
        }
        if (constant.startsWith("AMULET_OF_GLORY")) {
            return "Amulet of glory";
        }
        return capitalize(constant.toLowerCase().replace('_', ' '));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final Map<Integer, ItemVariations> ITEM_ID_TO_VARIATION = buildItemIdToVariation();
    private static final Map<Integer, String> ITEM_ID_CONSTANT_NAMES = buildItemIdConstantNames();

    private static Map<Integer, ItemVariations> buildItemIdToVariation() {
        Map<Integer, ItemVariations> map = new HashMap<>();
        for (ItemVariations v : ItemVariations.values()) {
            for (int id : v.getIds()) {
                // First-wins so the primary variation (declared first) owns the id.
                map.putIfAbsent(id, v);
            }
        }
        return map;
    }

    private static Map<Integer, String> buildItemIdConstantNames() {
        Map<Integer, String> map = new HashMap<>();
        try {
            Class<?> clazz = Class.forName("net.runelite.api.gameval.ItemID");
            for (java.lang.reflect.Field f : clazz.getFields()) {
                if (f.getType() != int.class || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                int id = f.getInt(null);
                // First-wins so the earliest-declared constant owns the id (item + placeholder share ids).
                map.putIfAbsent(id, f.getName());
            }
        } catch (ReflectiveOperationException e) {
            // Leave map empty — the resolver will fall back to "Item <id>".
        }
        return map;
    }

    private static List<PathfinderDashboardModels.Marker> markers(PathfinderResult result, PathfinderConfig config) {
        List<PathfinderDashboardModels.Marker> markers = new ArrayList<>();
        addMarker(markers, "start", "Start", result.getStart());
        addMarker(markers, "target", "Target", result.getTarget());
        addMarker(markers, "closest", "Closest reached", result.getClosestReachedPoint());

        List<PathStep> path = result.getPathSteps();
        for (BankTransition transition : collectBankTransitions(path)) {
            String bankName = BankDestinationLabels.labelForPackedNearest(transition.packedBankTile, 2);
            String label = bankName != null
                ? "Bank: " + bankName + " (step " + transition.transitionIndex + ")"
                : "Bank visited at step " + transition.transitionIndex;
            addMarker(markers, "bank", label, transition.packedBankTile);
        }
        return markers;
    }

    private static void addBankPathMetadata(PathfinderDashboardModels.RunRecord run, List<PathStep> path) {
        if (path == null || path.isEmpty()) {
            run.bankVisitedOnPath = false;
            run.bankEvents = null;
            return;
        }
        boolean any = path.stream().anyMatch(PathStep::isBankVisited);
        run.bankVisitedOnPath = any;
        List<BankTransition> transitions = collectBankTransitions(path);
        List<PathfinderDashboardModels.BankEvent> events = new ArrayList<>();
        for (int idx = 0; idx < transitions.size(); idx++) {
            BankTransition transition = transitions.get(idx);
            int segmentEndExclusive = idx + 1 < transitions.size()
                    ? transitions.get(idx + 1).transitionIndex
                    : Integer.MAX_VALUE;
            PathfinderDashboardModels.BankEvent ev = new PathfinderDashboardModels.BankEvent();
            ev.stepIndex = transition.transitionIndex;
            ev.location = worldPoint(transition.packedBankTile);
            ev.bankName = BankDestinationLabels.labelForPackedNearest(transition.packedBankTile, 2);
            ev.transportsAfterBank = transportLabelsInRange(
                    run.transports, transition.transitionIndex, segmentEndExclusive);
            ev.pickups = pickupsInRange(
                    run.transports, transition.transitionIndex, segmentEndExclusive);
            events.add(ev);
        }
        run.bankEvents = events.isEmpty() ? null : events;
    }

    /**
     * Returns distinct display labels for transports whose stepIndex falls in
     * {@code (afterStepExclusive, endStepExclusive)}. Preserves first-seen order so the caller
     * can answer "what was picked up from the bank, and in what order?". Transports in BANK-mode
     * tests all implicitly consume banked items (inventory starts empty).
     */
    private static List<String> transportLabelsInRange(
            List<PathfinderDashboardModels.TransportStep> transports,
            int afterStepExclusive, int endStepExclusive) {
        if (transports == null || transports.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (PathfinderDashboardModels.TransportStep step : transports) {
            if (step.stepIndex <= afterStepExclusive || step.stepIndex >= endStepExclusive) {
                continue;
            }
            String label = step.displayInfo != null ? step.displayInfo
                         : step.objectInfo != null  ? step.objectInfo
                         : step.type;
            if (label != null) {
                labels.add(label);
            }
        }
        return labels.isEmpty() ? null : new ArrayList<>(labels);
    }

    /**
     * Flattens and dedupes the item-requirement labels of every transport whose stepIndex falls
     * in {@code (afterStepExclusive, endStepExclusive)}. First-seen order is preserved so the
     * list mirrors the order in which items would be pulled from the bank.
     */
    private static List<String> pickupsInRange(
            List<PathfinderDashboardModels.TransportStep> transports,
            int afterStepExclusive, int endStepExclusive) {
        if (transports == null || transports.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (PathfinderDashboardModels.TransportStep step : transports) {
            if (step.stepIndex <= afterStepExclusive || step.stepIndex >= endStepExclusive) {
                continue;
            }
            if (step.itemRequirements != null) {
                items.addAll(step.itemRequirements);
            }
        }
        return items.isEmpty() ? null : new ArrayList<>(items);
    }

    private static final class BankTransition {
        final int transitionIndex;
        final int packedBankTile;

        BankTransition(int transitionIndex, int packedBankTile) {
            this.transitionIndex = transitionIndex;
            this.packedBankTile = packedBankTile;
        }
    }

    private static List<BankTransition> collectBankTransitions(List<PathStep> path) {
        List<BankTransition> out = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            PathStep step = path.get(i);
            boolean transitionedIntoBankState = step.isBankVisited() && (i == 0 || !path.get(i - 1).isBankVisited());
            if (transitionedIntoBankState) {
                int transitionIndex = Math.max(0, i - 1);
                out.add(new BankTransition(transitionIndex, path.get(transitionIndex).getPackedPosition()));
            }
        }
        return out;
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
