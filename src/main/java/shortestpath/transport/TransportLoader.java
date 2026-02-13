package shortestpath.transport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import shortestpath.ShortestPathPlugin;
import shortestpath.Util;
import shortestpath.WorldPointUtil;

@Slf4j
public class TransportLoader {
    private static final String DELIM_COLUMN = "\t";
    private static final String PREFIX_COMMENT = "#";

    private static void addTransports(Map<Integer, Set<Transport>> transports, String path, TransportType transportType) {
        addTransports(transports, path, transportType, 0);
    }

    private static void addTransports(Map<Integer, Set<Transport>> transports, String path, TransportType transportType, int radiusThreshold) {
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            addTransportsFromContents(transports, s, transportType, radiusThreshold);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void addTransportsFromContents(Map<Integer, Set<Transport>> transports, String contents, TransportType transportType, int radiusThreshold) {
        Scanner scanner = new Scanner(contents);

        // Header line is the first line in the file and will start with either '#' or '# '
        String headerLine = scanner.nextLine();
        headerLine = headerLine.startsWith(PREFIX_COMMENT + " ") ? headerLine.replace(PREFIX_COMMENT + " ", PREFIX_COMMENT) : headerLine;
        headerLine = headerLine.startsWith(PREFIX_COMMENT) ? headerLine.replace(PREFIX_COMMENT, "") : headerLine;
        String[] headers = headerLine.split(DELIM_COLUMN);

        Set<Transport> newTransports = new HashSet<>();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (line.startsWith(PREFIX_COMMENT) || line.isBlank()) {
                continue;
            }

            String[] fields = line.split(DELIM_COLUMN);
            Map<String, String> fieldMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                if (i < fields.length) {
                    fieldMap.put(headers[i], fields[i]);
                }
            }

            Transport transport = new Transport(fieldMap, transportType);
            newTransports.add(transport);

        }
        scanner.close();

        /*
        * A transport with origin A and destination B is one-way and must
        * be duplicated as origin B and destination A to become two-way.
        * Example: key-locked doors
        * 
        * A transport with origin A and a missing destination is one-way,
        * but can go from origin A to all destinations with a missing origin.
        * Example: fairy ring AIQ -> <blank>
        * 
        * A transport with a missing origin and destination B is one-way,
        * but can go from all origins with a missing destination to destination B.
        * Example: fairy ring <blank> -> AIQ
        * 
        * Identical transports from origin A to destination A are skipped, and
        * non-identical transports from origin A to destination A can be skipped
        * by specifying a radius threshold to ignore almost identical coordinates.
        * Example: fairy ring AIQ -> AIQ
        */
        Set<Transport> transportOrigins = new HashSet<>();
        Set<Transport> transportDestinations = new HashSet<>();
        for (Transport transport : newTransports) {
            int origin = transport.getOrigin();
            int destination = transport.getDestination();
            // Logic to determine ordinary transport vs teleport vs permutation (e.g. fairy ring)
            if (
                ( origin == Transport.UNDEFINED_ORIGIN && destination == Transport.UNDEFINED_DESTINATION)
                || (origin == Transport.LOCATION_PERMUTATION && destination == Transport.LOCATION_PERMUTATION)) {
                continue;
            } else if (origin != Transport.LOCATION_PERMUTATION && origin != Transport.UNDEFINED_ORIGIN
                && destination == Transport.LOCATION_PERMUTATION) {
                transportOrigins.add(transport);
            } else if (origin == Transport.LOCATION_PERMUTATION
                && destination != Transport.LOCATION_PERMUTATION && destination != Transport.UNDEFINED_DESTINATION) {
                transportDestinations.add(transport);
            }
            if (origin != Transport.LOCATION_PERMUTATION
                && destination != Transport.UNDEFINED_DESTINATION && destination != Transport.LOCATION_PERMUTATION
                && (origin == Transport.UNDEFINED_ORIGIN || origin != destination)) {
                transports.computeIfAbsent(origin, k -> new HashSet<>()).add(transport);
            }
        }
        for (Transport origin : transportOrigins) {
            for (Transport destination : transportDestinations) {
                // The radius threshold prevents transport permutations from including (almost) same origin and destination
                if (WorldPointUtil.distanceBetween2D(origin.getOrigin(), destination.getDestination()) > radiusThreshold) {
                    transports
                        .computeIfAbsent(origin.getOrigin(), k -> new HashSet<>())
                        .add(new Transport(origin, destination));
                }
            }
        }
    }

    public static HashMap<Integer, Set<Transport>> loadAllFromResources() {
        HashMap<Integer, Set<Transport>> transports = new HashMap<>();

        for (TransportType type : TransportType.values()) {
            if (type.hasResourcePath()) {
                addTransports(transports, type.getResourcePath(), type, type.hasRadiusThreshold() ? type.getRadiusThreshold() : 0);
            }
        }

        return transports;
    }
}
