package shortestpath.reachability;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import shortestpath.WorldPointUtil;

class ReachabilityTargetLoader {
    List<ReachabilityTarget> loadFromResource(String resourcePath) throws IOException {
        try (InputStream in = ReachabilityTargetLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing resource: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return load(reader, resourcePath);
            }
        }
    }

    List<ReachabilityTarget> loadFromFile(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(reader, path.toString());
        }
    }

    private List<ReachabilityTarget> load(BufferedReader reader, String source) throws IOException {
        Map<Integer, ReachabilityTarget> dedupedTargets = new LinkedHashMap<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .build();

        try (CSVParser parser = new CSVParser(reader, format)) {
            Map<String, Integer> headers = parser.getHeaderMap();
            requireHeaders(headers, source, "description", "x", "y", "plane");

            for (CSVRecord record : parser) {
                int packedPoint = WorldPointUtil.packWorldPoint(
                    Integer.parseInt(record.get("x")),
                    Integer.parseInt(record.get("y")),
                    Integer.parseInt(record.get("plane")));
                dedupedTargets.putIfAbsent(
                    packedPoint,
                    new ReachabilityTarget(
                        formatDescription(record.get("description"), packedPoint),
                        packedPoint));
            }
        }

        return new ArrayList<>(dedupedTargets.values());
    }

    private static void requireHeaders(Map<String, Integer> headers, String source, String... names) throws IOException {
        for (String name : names) {
            if (!headers.containsKey(name)) {
                throw new IOException("Invalid dataset header in " + source + ": missing " + name);
            }
        }
    }
    
    private static String formatPoint(int packedPoint) {
        return String.format("(%d,%d,%d)",
            WorldPointUtil.unpackWorldX(packedPoint),
            WorldPointUtil.unpackWorldY(packedPoint),
            WorldPointUtil.unpackWorldPlane(packedPoint));
    }

    private static String formatDescription(String description, int packedPoint) {
        return description + " " + formatPoint(packedPoint);
    }
}
