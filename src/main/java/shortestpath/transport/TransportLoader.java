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
        addTransports(transports, "/transports/transports.tsv", TransportType.TRANSPORT);
        addTransports(transports, "/transports/agility_shortcuts.tsv", TransportType.AGILITY_SHORTCUT);
        addTransports(transports, "/transports/boats.tsv", TransportType.BOAT);
        addTransports(transports, "/transports/canoes.tsv", TransportType.CANOE);
        addTransports(transports, "/transports/charter_ships.tsv", TransportType.CHARTER_SHIP);
        addTransports(transports, "/transports/ships.tsv", TransportType.SHIP);
        addTransports(transports, "/transports/fairy_rings.tsv", TransportType.FAIRY_RING);
        addTransports(transports, "/transports/gnome_gliders.tsv", TransportType.GNOME_GLIDER, 6);
        addTransports(transports, "/transports/hot_air_balloons.tsv", TransportType.HOT_AIR_BALLOON, 7);
        addTransports(transports, "/transports/magic_carpets.tsv", TransportType.MAGIC_CARPET);
        addTransports(transports, "/transports/magic_mushtrees.tsv", TransportType.MAGIC_MUSHTREE, 5);
        addTransports(transports, "/transports/minecarts.tsv", TransportType.MINECART);
        addTransports(transports, "/transports/quetzals.tsv", TransportType.QUETZAL);
        addTransports(transports, "/transports/seasonal_transports.tsv", TransportType.SEASONAL_TRANSPORTS);
        addTransports(transports, "/transports/spirit_trees.tsv", TransportType.SPIRIT_TREE, 5);
        addTransports(transports, "/transports/teleportation_items.tsv", TransportType.TELEPORTATION_ITEM);
        addTransports(transports, "/transports/teleportation_boxes.tsv", TransportType.TELEPORTATION_BOX);
        addTransports(transports, "/transports/teleportation_levers.tsv", TransportType.TELEPORTATION_LEVER);
        addTransports(transports, "/transports/teleportation_minigames.tsv", TransportType.TELEPORTATION_MINIGAME);
        addTransports(transports, "/transports/teleportation_portals.tsv", TransportType.TELEPORTATION_PORTAL);
        addTransports(transports, "/transports/teleportation_spells.tsv", TransportType.TELEPORTATION_SPELL);
        addTransports(transports, "/transports/wilderness_obelisks.tsv", TransportType.WILDERNESS_OBELISK);
        return transports;
    }
}
