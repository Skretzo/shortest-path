package shortestpath.transport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import shortestpath.Util;
import shortestpath.WorldPointUtil;
import shortestpath.transport.parser.TransportRecord;
import shortestpath.transport.parser.TsvParser;
import shortestpath.transport.parser.WorldPointParser;

/*
* The TransportCoordinateExporter exports a structure representation of all coordinates known by the plugin so
* it can be consumed by external tools (such as the coordinate preview workflow).
 */

public class TransportCoordinateExporter {
    // Separator to use when combining together descriptions of locations which share the same coordinate.
    private static final String MERGED_VALUE_SEPARATOR = "; ";
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final WorldPointParser WORLD_POINT_PARSER = new WorldPointParser();
    private final TsvParser tsvParser = new TsvParser();

    public List<TransportCoordinateItem> exportAllFromResources() {
        LinkedHashMap<String, TransportCoordinateItem> items = new LinkedHashMap<>();
        for (TransportType type : TransportType.values()) {
            if (!type.hasResourcePath()) {
                continue;
            }
            String path = type.getResourcePath();
            String resourceContents = readResource(path);
            addCoordinatesFromContents(items, path.substring(1), resourceContents, type);
        }
        return new ArrayList<>(items.values());
    }

    List<TransportCoordinateItem> exportFromContents(String sourcePath, String contents, TransportType transportType) {
        LinkedHashMap<String, TransportCoordinateItem> items = new LinkedHashMap<>();
        addCoordinatesFromContents(items, sourcePath, contents, transportType);
        return new ArrayList<>(items.values());
    }

    static String toJson(List<TransportCoordinateItem> items, boolean pretty) {
        return pretty ? PRETTY_GSON.toJson(items) : GSON.toJson(items);
    }

    private static void addCoordinate(Map<String, TransportCoordinateItem> items, TransportRecord record, TransportType type, Integer packedCoordinate, String role) {
        if (packedCoordinate == null) {
            return;
        }
        String coordinate = toCoordinateString(packedCoordinate);
        String label = buildLabel(record, type, role);
        String source = record.getSourceReference();
        TransportCoordinateItem existing = items.get(coordinate);
        if (existing == null) {
            items.put(coordinate, new TransportCoordinateItem(buildId(packedCoordinate), label, coordinate, source));
        }
        // If the coordinate already exists, merge the label.
        else {
            items.put(coordinate, new TransportCoordinateItem(
                    existing.getId(),
                    mergeValues(existing.getLabel(), label),
                    coordinate,
                    mergeValues(existing.getSource(), source)
            ));
        }
    }

    private void addCoordinatesFromContents(Map<String, TransportCoordinateItem> items, String sourcePath, String contents, TransportType transportType) {
        for (TransportRecord record : tsvParser.parse(contents, sourcePath)) {
            addCoordinate(items, record, transportType, parseCoordinate(record.getOrigin(), Transport.UNDEFINED_ORIGIN), "origin");
            addCoordinate(items, record, transportType, parseCoordinate(record.getDestination(), Transport.UNDEFINED_DESTINATION), "destination");
        }
    }

    private static Integer parseCoordinate(String value, int undefinedValue) {
        if (value == null) {
            return null;
        }
        int coordinate = WORLD_POINT_PARSER.parse(value);
        if (coordinate == undefinedValue || coordinate == Transport.LOCATION_PERMUTATION) {
            return null;
        }
        return coordinate;
    }

    // Create a unique identifier for each coordinate.
    private static String buildId(int packedCoordinate) {
        return "coord-" + WorldPointUtil.unpackWorldX(packedCoordinate)
            + "-" + WorldPointUtil.unpackWorldY(packedCoordinate)
            + "-" + WorldPointUtil.unpackWorldPlane(packedCoordinate);
    }

    // Create a human-readable label in the form "<Transport Type>: <role> - <display/object info>".
    private static String buildLabel(TransportRecord record, TransportType type, String role) {
        List<String> parts = new ArrayList<>();
        parts.add(toDisplayName(type));
        parts.add(role);

        String displayInfo = record.getDisplayInfo();
        String objectInfo = record.getObjectInfo();
        LinkedHashSet<String> contextValues = new LinkedHashSet<>();
        if (displayInfo != null && !displayInfo.isBlank()) {
            contextValues.add(displayInfo.trim());
        }
        if (objectInfo != null && !objectInfo.isBlank()) {
            contextValues.add(objectInfo.trim());
        }
        if (!contextValues.isEmpty()) {
            parts.add(String.join(" / ", contextValues));
        }

        return String.join(": ", parts.subList(0, 2))
            + (parts.size() > 2 ? " - " + parts.get(2) : "");
    }

    private static String toDisplayName(TransportType transportType) {
        String lower = transportType.name().toLowerCase();
        String[] words = lower.split("_");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(words[i].charAt(0)));
            builder.append(words[i].substring(1));
        }
        return builder.toString();
    }

    // Render a coordinate as X/Y/P
    private static String toCoordinateString(int packedCoordinate) {
        return WorldPointUtil.unpackWorldX(packedCoordinate)
            + "/" + WorldPointUtil.unpackWorldY(packedCoordinate)
            + "/" + WorldPointUtil.unpackWorldPlane(packedCoordinate);
    }

    private static String readResource(String path) {
        try {
            return new String(Util.readAllBytes(Objects.requireNonNull(TransportCoordinateExporter.class.getResourceAsStream(path))), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read transport resource " + path, e);
        }
    }

    // Merge together the description of shared coordinates by inserting the separator. 
    private static String mergeValues(String existing, String next) {
        if (existing == null || existing.isEmpty()) {
            return next;
        }
        if (next == null || next.isEmpty() || existing.equals(next)) {
            return existing;
        }
        return existing + MERGED_VALUE_SEPARATOR + next;
    }
}
