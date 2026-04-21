package shortestpath.dashboard;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;
import shortestpath.ShortestPathPlugin;
import shortestpath.Util;
import shortestpath.WorldPointUtil;

/**
 * Resolves human-readable bank names from packed coordinates using {@code /destinations/game_features/bank.tsv}.
 */
public final class BankDestinationLabels {
    private static final Map<Integer, String> PACKED_TO_LABEL = load();

    private BankDestinationLabels() {
    }

    /**
     * @return the Info column for this exact tile, or {@code null} if unknown
     */
    public static String labelForPacked(int packedPoint) {
        return PACKED_TO_LABEL.get(packedPoint);
    }

    /**
     * Same as {@link #labelForPacked(int)}, or the nearest bank tile on the same plane within Chebyshev distance
     * (useful when the path stops on an adjacent tile).
     */
    public static String labelForPackedNearest(int packedPoint, int maxChebyshev) {
        String exact = labelForPacked(packedPoint);
        if (exact != null) {
            return exact;
        }
        int px = WorldPointUtil.unpackWorldX(packedPoint);
        int py = WorldPointUtil.unpackWorldY(packedPoint);
        int plane = WorldPointUtil.unpackWorldPlane(packedPoint);
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Map.Entry<Integer, String> e : PACKED_TO_LABEL.entrySet()) {
            int q = e.getKey();
            if (WorldPointUtil.unpackWorldPlane(q) != plane) {
                continue;
            }
            int qx = WorldPointUtil.unpackWorldX(q);
            int qy = WorldPointUtil.unpackWorldY(q);
            int d = Math.max(Math.abs(qx - px), Math.abs(qy - py));
            if (d <= maxChebyshev && d < bestDist) {
                bestDist = d;
                best = e.getValue();
            }
        }
        return best;
    }

    /** Distinct values from the {@code Info} column in {@code bank.tsv}, sorted for display. */
    public static List<String> uniqueSortedBankNames() {
        return new ArrayList<>(new TreeSet<>(PACKED_TO_LABEL.values()));
    }

    private static Map<Integer, String> load() {
        Map<Integer, String> map = new HashMap<>();
        try {
            String s = new String(
                Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream("/destinations/game_features/bank.tsv")),
                StandardCharsets.UTF_8);
            try (Scanner scanner = new Scanner(s)) {
                String headerLine = scanner.nextLine();
                headerLine = headerLine.startsWith("# ") ? headerLine.substring(2) : headerLine.startsWith("#") ? headerLine.substring(1) : headerLine;
                String[] headers = headerLine.split("\t");
                int destCol = -1;
                int infoCol = -1;
                for (int i = 0; i < headers.length; i++) {
                    if ("Destination".equals(headers[i].trim())) {
                        destCol = i;
                    }
                    if ("Info".equals(headers[i].trim())) {
                        infoCol = i;
                    }
                }
                if (destCol < 0 || infoCol < 0) {
                    return map;
                }
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("#") || line.isBlank()) {
                        continue;
                    }
                    String[] fields = line.split("\t", -1);
                    if (fields.length <= Math.max(destCol, infoCol)) {
                        continue;
                    }
                    String[] triple = fields[destCol].trim().split(" ");
                    if (triple.length != 3) {
                        continue;
                    }
                    try {
                        int packed = WorldPointUtil.packWorldPoint(
                            Integer.parseInt(triple[0]),
                            Integer.parseInt(triple[1]),
                            Integer.parseInt(triple[2]));
                        map.putIfAbsent(packed, fields[infoCol].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}
