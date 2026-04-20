package shortestpath.reachability;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import shortestpath.TeleportationItem;
import shortestpath.Util;
import shortestpath.WorldPointUtil;

class ReachabilityTargetLoader {
    List<ReachabilityTarget> loadFromResource(String resourcePath) throws IOException {
        try (InputStream in = ReachabilityTargetLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing resource: " + resourcePath);
            }

            String contents = new String(Util.readAllBytes(in), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(contents);

            if (!scanner.hasNextLine()) {
                scanner.close();
                return new ArrayList<>();
            }

            String header = scanner.nextLine();
            List<ReachabilityTarget> targets;
            if (header.contains("start_x") && header.contains("teleports")) {
                targets = parseRouteCsv(scanner, header, resourcePath);
            } else if (header.contains("clue_type") && header.contains("x") && header.contains("y") && header.contains("plane")) {
                targets = parseCsv(scanner, header, resourcePath);
            } else {
                targets = parseTsv(scanner, header, resourcePath);
            }

            scanner.close();
            return targets;
        }
    }

    List<ReachabilityTarget> loadFromCsv(Path csvPath) throws IOException {
        Map<Integer, ReachabilityTarget> dedupedTargets = new LinkedHashMap<>();
        try (Scanner scanner = new Scanner(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
            if (!scanner.hasNextLine()) {
                return new ArrayList<>();
            }

            String[] headers = scanner.nextLine().split(",", -1);
            int clueTypeIndex = indexOf(headers, "clue_type");
            int xIndex = indexOf(headers, "x");
            int yIndex = indexOf(headers, "y");
            int planeIndex = indexOf(headers, "plane");
            if (clueTypeIndex < 0 || xIndex < 0 || yIndex < 0 || planeIndex < 0) {
                throw new IOException("Invalid clue CSV header in " + csvPath);
            }

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.isBlank()) {
                    continue;
                }

                String[] fields = line.split(",", 6);
                int packedPoint = WorldPointUtil.packWorldPoint(
                    Integer.parseInt(fields[xIndex]),
                    Integer.parseInt(fields[yIndex]),
                    Integer.parseInt(fields[planeIndex]));
                dedupedTargets.putIfAbsent(
                    packedPoint,
                    new ReachabilityTarget(
                        fields[clueTypeIndex] + " " + formatPoint(packedPoint),
                        packedPoint));
            }
        }

        return new ArrayList<>(dedupedTargets.values());
    }

    private List<ReachabilityTarget> parseCsv(Scanner scanner, String header, String source) throws IOException {
        String[] headers = header.split(",", -1);
        int clueTypeIndex = indexOf(headers, "clue_type");
        int xIndex = indexOf(headers, "x");
        int yIndex = indexOf(headers, "y");
        int planeIndex = indexOf(headers, "plane");
        if (clueTypeIndex < 0 || xIndex < 0 || yIndex < 0 || planeIndex < 0) {
            throw new IOException("Invalid clue CSV header in " + source);
        }

        Map<Integer, ReachabilityTarget> dedupedTargets = new LinkedHashMap<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                continue;
            }

            String[] fields = line.split(",", 6);
            int packedPoint = WorldPointUtil.packWorldPoint(
                Integer.parseInt(fields[xIndex]),
                Integer.parseInt(fields[yIndex]),
                Integer.parseInt(fields[planeIndex]));
            dedupedTargets.putIfAbsent(
                packedPoint,
                new ReachabilityTarget(
                    fields[clueTypeIndex] + " " + formatPoint(packedPoint),
                    packedPoint));
        }

        return new ArrayList<>(dedupedTargets.values());
    }

    private List<ReachabilityTarget> parseRouteCsv(Scanner scanner, String header, String source) throws IOException {
        String[] headers = header.split(",", -1);
        int nameIndex = indexOf(headers, "name");
        int categoryIndex = indexOf(headers, "category");
        int startXIndex = indexOf(headers, "start_x");
        int startYIndex = indexOf(headers, "start_y");
        int startPlaneIndex = indexOf(headers, "start_plane");
        int xIndex = indexOf(headers, "x");
        int yIndex = indexOf(headers, "y");
        int planeIndex = indexOf(headers, "plane");
        int teleportsIndex = indexOf(headers, "teleports");
        if (nameIndex < 0 || xIndex < 0 || yIndex < 0 || planeIndex < 0) {
            throw new IOException("Invalid route CSV header in " + source);
        }

        List<ReachabilityTarget> targets = new ArrayList<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                continue;
            }

            String[] fields = line.split(",", -1);
            int packedPoint = WorldPointUtil.packWorldPoint(
                Integer.parseInt(fields[xIndex]),
                Integer.parseInt(fields[yIndex]),
                Integer.parseInt(fields[planeIndex]));

            int startPoint = WorldPointUtil.UNDEFINED;
            if (startXIndex >= 0 && startYIndex >= 0 && startPlaneIndex >= 0
                && !fields[startXIndex].isEmpty()) {
                startPoint = WorldPointUtil.packWorldPoint(
                    Integer.parseInt(fields[startXIndex]),
                    Integer.parseInt(fields[startYIndex]),
                    Integer.parseInt(fields[startPlaneIndex]));
            }

            String category = categoryIndex >= 0 ? fields[categoryIndex] : null;

            TeleportationItem teleportOverride = null;
            if (teleportsIndex >= 0 && !fields[teleportsIndex].isEmpty()) {
                teleportOverride = TeleportationItem.valueOf(fields[teleportsIndex]);
            }

            targets.add(new ReachabilityTarget(
                fields[nameIndex], packedPoint, category, startPoint, teleportOverride));
        }

        return targets;
    }

    private List<ReachabilityTarget> parseTsv(Scanner scanner, String header, String source) throws IOException {
        List<ReachabilityTarget> targets = new ArrayList<>();
        String[] headers = normalizeHeader(header).split("\t");
        int descriptionIndex = indexOf(headers, "Description");
        int xIndex = indexOf(headers, "X");
        int yIndex = indexOf(headers, "Y");
        int planeIndex = indexOf(headers, "Plane");
        if (descriptionIndex < 0 || xIndex < 0 || yIndex < 0 || planeIndex < 0) {
            throw new IOException("Invalid target TSV header in " + source);
        }

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            String[] fields = line.split("\t", -1);
            targets.add(new ReachabilityTarget(
                fields[descriptionIndex],
                WorldPointUtil.packWorldPoint(
                    Integer.parseInt(fields[xIndex]),
                    Integer.parseInt(fields[yIndex]),
                    Integer.parseInt(fields[planeIndex]))));
        }

        return targets;
    }

    private static String normalizeHeader(String header) {
        if (header.startsWith("# ")) {
            return header.substring(2);
        }
        if (header.startsWith("#")) {
            return header.substring(1);
        }
        return header;
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (name.equals(headers[i])) {
                return i;
            }
        }
        return -1;
    }

    private static String formatPoint(int packedPoint) {
        return String.format("(%d,%d,%d)",
            WorldPointUtil.unpackWorldX(packedPoint),
            WorldPointUtil.unpackWorldY(packedPoint),
            WorldPointUtil.unpackWorldPlane(packedPoint));
    }
}
