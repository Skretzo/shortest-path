package shortestpath.dashboard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import shortestpath.Util;
import shortestpath.WorldPointUtil;

/**
 * Loads {@link DashboardScenario} objects from CSV/TSV resources or files.
 * <p>
 * Supported formats (auto-detected from the header row):
 * <ol>
 *   <li><b>Extended routes CSV</b> — has {@code start_x} and {@code preset} (or the legacy
 *       {@code teleports} column alias). Supports the full set of optional columns:
 *       {@code inventory}, {@code equipment}, {@code bank}, {@code varbits}, {@code varplayers},
 *       {@code skill_levels}, {@code config_overrides}, {@code expected_length},
 *       {@code minimum_length}.</li>
 *   <li><b>Clue-step CSV</b> — has {@code clue_type}, {@code x}, {@code y}, {@code plane}.</li>
 *   <li><b>TSV</b> — tab-separated with {@code Description}, {@code X}, {@code Y}, {@code Plane}.</li>
 * </ol>
 *
 * <h3>Column grammar for optional columns</h3>
 * <pre>
 * inventory / equipment / bank : itemId:qty;itemId:qty   (qty defaults to 1)
 * varbits / varplayers         : id=value;id=value
 * skill_levels                 : SKILL_NAME=level;…      (e.g. AGILITY=70)
 * config_overrides             : settingName=value;…
 * expected_length              : integer
 * minimum_length               : integer
 * </pre>
 */
public final class DashboardScenarioLoader {

    public List<DashboardScenario> loadFromResource(String resourcePath) throws IOException {
        try (InputStream in = DashboardScenarioLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing resource: " + resourcePath);
            }
            String contents = new String(Util.readAllBytes(in), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(contents);
            List<DashboardScenario> result = parseAny(scanner, resourcePath);
            scanner.close();
            return result;
        }
    }

    public List<DashboardScenario> loadFromCsv(Path csvPath) throws IOException {
        try (Scanner scanner = new Scanner(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
            return parseAny(scanner, csvPath.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Format dispatch
    // -------------------------------------------------------------------------

    private List<DashboardScenario> parseAny(Scanner scanner, String source) throws IOException {
        if (!scanner.hasNextLine()) {
            return new ArrayList<>();
        }
        String header = scanner.nextLine();
        if (header.contains("start_x") && (header.contains("preset") || header.contains("teleports"))) {
            return parseRoutesCsv(scanner, header, source);
        }
        if (header.contains("clue_type") && header.contains("x") && header.contains("y")) {
            return parseClueCsv(scanner, header, source);
        }
        return parseTsv(scanner, header, source);
    }

    // -------------------------------------------------------------------------
    // Extended routes CSV
    // -------------------------------------------------------------------------

    private List<DashboardScenario> parseRoutesCsv(Scanner scanner, String header, String source) throws IOException {
        String[] headers = header.split(",", -1);

        int nameIdx        = indexOf(headers, "name");
        int categoryIdx    = indexOf(headers, "category");
        int startXIdx      = indexOf(headers, "start_x");
        int startYIdx      = indexOf(headers, "start_y");
        int startPlaneIdx  = indexOf(headers, "start_plane");
        int xIdx           = indexOf(headers, "x");
        int yIdx           = indexOf(headers, "y");
        int planeIdx       = indexOf(headers, "plane");
        // "preset" is the new name; "teleports" is the old alias
        int presetIdx      = indexOf(headers, "preset");
        if (presetIdx < 0) {
            presetIdx = indexOf(headers, "teleports");
        }
        // Optional extended columns
        int inventoryIdx        = indexOf(headers, "inventory");
        int equipmentIdx        = indexOf(headers, "equipment");
        int bankIdx             = indexOf(headers, "bank");
        int varbitsIdx          = indexOf(headers, "varbits");
        int varplayersIdx       = indexOf(headers, "varplayers");
        int skillLevelsIdx      = indexOf(headers, "skill_levels");
        int configOverridesIdx  = indexOf(headers, "config_overrides");
        int expectedLengthIdx   = indexOf(headers, "expected_length");
        int minimumLengthIdx    = indexOf(headers, "minimum_length");

        if (xIdx < 0 || yIdx < 0 || planeIdx < 0) {
            throw new IOException("Routes CSV missing x/y/plane columns in " + source);
        }

        List<DashboardScenario> result = new ArrayList<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);

            int endPoint = WorldPointUtil.packWorldPoint(
                parseInt(f, xIdx, 0), parseInt(f, yIdx, 0), parseInt(f, planeIdx, 0));

            int startPoint = WorldPointUtil.UNDEFINED;
            if (startXIdx >= 0 && startYIdx >= 0 && startPlaneIdx >= 0
                    && startXIdx < f.length && !f[startXIdx].isEmpty()) {
                startPoint = WorldPointUtil.packWorldPoint(
                    parseInt(f, startXIdx, 0), parseInt(f, startYIdx, 0), parseInt(f, startPlaneIdx, 0));
            }

            DashboardScenario.Builder b = DashboardScenario.builder()
                .name(get(f, nameIdx, ""))
                .category(get(f, categoryIdx, ""))
                .startPoint(startPoint)
                .endPoint(endPoint)
                .preset(get(f, presetIdx, "NONE"))
                .inventory(parseItems(get(f, inventoryIdx, "")))
                .equipment(parseItems(get(f, equipmentIdx, "")))
                .bank(parseItems(get(f, bankIdx, "")))
                .varbits(parseIntMap(get(f, varbitsIdx, "")))
                .varplayers(parseIntMap(get(f, varplayersIdx, "")))
                .skillLevels(parseStringIntMap(get(f, skillLevelsIdx, "")))
                .configOverrides(parseStringStringMap(get(f, configOverridesIdx, "")));

            String expLen = get(f, expectedLengthIdx, "");
            if (!expLen.isEmpty()) {
                b.expectedLength(Integer.parseInt(expLen.trim()));
            }
            String minLen = get(f, minimumLengthIdx, "");
            if (!minLen.isEmpty()) {
                b.minimumLength(Integer.parseInt(minLen.trim()));
            }

            result.add(b.build());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Clue-step CSV
    // -------------------------------------------------------------------------

    private List<DashboardScenario> parseClueCsv(Scanner scanner, String header, String source) throws IOException {
        String[] headers = header.split(",", -1);
        int clueTypeIdx = indexOf(headers, "clue_type");
        int xIdx        = indexOf(headers, "x");
        int yIdx        = indexOf(headers, "y");
        int planeIdx    = indexOf(headers, "plane");
        if (clueTypeIdx < 0 || xIdx < 0 || yIdx < 0 || planeIdx < 0) {
            throw new IOException("Invalid clue CSV header in " + source);
        }

        Map<Integer, DashboardScenario> deduped = new LinkedHashMap<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            int packed = WorldPointUtil.packWorldPoint(
                parseInt(f, xIdx, 0), parseInt(f, yIdx, 0), parseInt(f, planeIdx, 0));
            String name = get(f, clueTypeIdx, "") + " " + formatPoint(packed);
            deduped.putIfAbsent(packed, DashboardScenario.builder()
                .name(name)
                .category(get(f, clueTypeIdx, ""))
                .startPoint(WorldPointUtil.UNDEFINED)
                .endPoint(packed)
                .preset("ALL")
                .build());
        }
        return new ArrayList<>(deduped.values());
    }

    // -------------------------------------------------------------------------
    // Legacy TSV (targets.tsv etc.)
    // -------------------------------------------------------------------------

    private List<DashboardScenario> parseTsv(Scanner scanner, String header, String source) throws IOException {
        String[] headers = normalizeHeader(header).split("\t");
        int descIdx  = indexOf(headers, "Description");
        int xIdx     = indexOf(headers, "X");
        int yIdx     = indexOf(headers, "Y");
        int planeIdx = indexOf(headers, "Plane");
        if (descIdx < 0 || xIdx < 0 || yIdx < 0 || planeIdx < 0) {
            throw new IOException("Invalid TSV header in " + source);
        }

        List<DashboardScenario> result = new ArrayList<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length <= Math.max(Math.max(descIdx, xIdx), Math.max(yIdx, planeIdx))) {
                continue;
            }
            int packed = WorldPointUtil.packWorldPoint(
                parseInt(f, xIdx, 0), parseInt(f, yIdx, 0), parseInt(f, planeIdx, 0));
            result.add(DashboardScenario.builder()
                .name(get(f, descIdx, ""))
                .category("")
                .startPoint(WorldPointUtil.UNDEFINED)
                .endPoint(packed)
                .preset("ALL")
                .build());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Parses {@code itemId:qty;itemId:qty} into a list.  Quantity defaults to 1 when omitted.
     */
    private static List<DashboardScenario.ItemQuantity> parseItems(String raw) {
        List<DashboardScenario.ItemQuantity> items = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return items;
        }
        for (String token : raw.split(";")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            String[] parts = token.split(":", 2);
            int itemId = Integer.parseInt(parts[0].trim());
            int qty = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            items.add(new DashboardScenario.ItemQuantity(itemId, qty));
        }
        return items;
    }

    /** Parses {@code key=value;key=value} where both key and value are integers. */
    private static Map<Integer, Integer> parseIntMap(String raw) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String token : raw.split(";")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            String[] parts = token.split("=", 2);
            map.put(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        }
        return map;
    }

    /** Parses {@code KEY=value;…} where keys are strings and values are integers. */
    private static Map<String, Integer> parseStringIntMap(String raw) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String token : raw.split(";")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            String[] parts = token.split("=", 2);
            map.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
        }
        return map;
    }

    /** Parses {@code key=value;…} where both key and value are strings. */
    private static Map<String, String> parseStringStringMap(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String token : raw.split(";")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            String[] parts = token.split("=", 2);
            map.put(parts[0].trim(), parts.length > 1 ? parts[1].trim() : "");
        }
        return map;
    }

    private static int parseInt(String[] fields, int index, int defaultValue) {
        if (index < 0 || index >= fields.length || fields[index].isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(fields[index].trim());
    }

    private static String get(String[] fields, int index, String defaultValue) {
        if (index < 0 || index >= fields.length) {
            return defaultValue;
        }
        String v = fields[index].trim();
        return v.isEmpty() ? defaultValue : v;
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeHeader(String header) {
        // Remove UTF-8 BOM if present
        if (header.startsWith("\uFEFF")) {
            return header.substring(1);
        }
        return header;
    }

    private static String formatPoint(int packed) {
        return String.format("(%d,%d,%d)",
            WorldPointUtil.unpackWorldX(packed),
            WorldPointUtil.unpackWorldY(packed),
            WorldPointUtil.unpackWorldPlane(packed));
    }
}
