package shortestpath;

import net.runelite.client.game.AgilityShortcut;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This test checks every AgilityShortcut enum constant against the list of all transports
 * in the resource files, to ensure that our list is complete.
 * <p>
 * This test should only fail when a new AgilityShortcut enum constant is added
 * without a corresponding entry in the TSV file.
 * <p>
 * There are some known exceptions where an AgilityShortcut cannot be matched to a TSV entry,
 * for example when the Object ID changes as the result of "unlocking" the shortcut via a quest.
 * These exceptions can be added to the {@link #createExcludedIds()} method. Use this sparingly.
 */
public class AgilityShortcutTest {
    private static final Pattern OBJECT_ID_PATTERN = Pattern.compile("\\d+");

    /**
     * IDs that are known exceptions and cannot be matched to TSV entries.
     * These typically represent shortcuts that change based on quest completion or other game state.
     */
    private static final Set<Integer> EXCLUDED_OBJECT_IDS = createExcludedIds();

    private static Set<Integer> createExcludedIds() {
        Set<Integer> excluded = new HashSet<>();
        
        // These two IDs represent the same shortcut before and after a step in the 'Making Friends with My Arm' quest
        // See: https://oldschool.runescape.wiki/w/Broken_fence_(Weiss)
        excluded.add(46815);
        excluded.add(46817);
        
        // somehow not found in tsv
        excluded.add(23644);

        // somehow not found in tsv
        excluded.add(11948);

        // Somehow not found in tsv
        excluded.add(23568);
        excluded.add(23569);
        
        return excluded;
    }

    @Test
    public void everyEnumHasTsvEntry() {
        Map<Integer, Set<Transport>> allTransports = TransportLoader.loadAllFromResources();
        TransportData transportData = extractTransportData(allTransports);
        
        List<String> missingShortcuts = findMissingShortcuts(transportData);
        
        if (!missingShortcuts.isEmpty()) {
            String errorMessage = String.join("\n", missingShortcuts);
            System.out.println("Missing TSV entries for the following AgilityShortcut enum constants:\n" + errorMessage);
            Assert.fail("Missing TSV entries for AgilityShortcut enum constants (see stdout for list)");
        }
    }

    /**
     * Extracts coordinate and object ID data from all transports.
     */
    private TransportData extractTransportData(Map<Integer, Set<Transport>> allTransports) {
        Set<String> coordinates = new HashSet<>();
        Set<Integer> objectIds = new HashSet<>();

        for (Set<Transport> transportSet : allTransports.values()) {
            for (Transport transport : transportSet) {
                addCoordinatesFromTransport(transport, coordinates);
                addObjectIdsFromTransport(transport, objectIds);
            }
        }

        return new TransportData(coordinates, objectIds);
    }

    /**
     * Adds origin and destination coordinates from a transport to the coordinate set.
     */
    private void addCoordinatesFromTransport(Transport transport, Set<String> coordinates) {
        addCoordinateIfValid(transport.getOrigin(), coordinates);
        addCoordinateIfValid(transport.getDestination(), coordinates);
    }

    /**
     * Adds a coordinate to the set if it's valid (not undefined or a permutation).
     */
    private void addCoordinateIfValid(int packedCoordinate, Set<String> coordinates) {
        if (packedCoordinate != Transport.UNDEFINED_ORIGIN && 
            packedCoordinate != Transport.UNDEFINED_DESTINATION &&
            packedCoordinate != Transport.LOCATION_PERMUTATION) {
            
            String coordinate = formatCoordinate(packedCoordinate);
            coordinates.add(coordinate);
        }
    }

    /**
     * Formats a packed coordinate as "x y plane".
     */
    private String formatCoordinate(int packed) {
        int x = WorldPointUtil.unpackWorldX(packed);
        int y = WorldPointUtil.unpackWorldY(packed);
        int plane = WorldPointUtil.unpackWorldPlane(packed);
        return x + " " + y + " " + plane;
    }

    /**
     * Extracts object IDs from the transport's object info string.
     */
    private void addObjectIdsFromTransport(Transport transport, Set<Integer> objectIds) {
        String objectInfo = transport.getObjectInfo();
        if (objectInfo == null || objectInfo.isEmpty()) {
            return;
        }

        Matcher matcher = OBJECT_ID_PATTERN.matcher(objectInfo);
        while (matcher.find()) {
            try {
                objectIds.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
                // Skip invalid numbers
            }
        }
    }

    /**
     * Finds all AgilityShortcut enum constants that don't have matching TSV entries.
     */
    private List<String> findMissingShortcuts(TransportData transportData) {
        List<String> missing = new ArrayList<>();

        for (AgilityShortcut shortcut : AgilityShortcut.values()) {
            if (!isShortcutMatched(shortcut, transportData)) {
                missing.add(shortcut.name());
            }
        }

        return missing;
    }

    /**
     * Checks if an AgilityShortcut matches any entry in the transport data.
     */
    private boolean isShortcutMatched(AgilityShortcut shortcut, TransportData transportData) {
        // Check by world location
        if (isMatchedByLocation(shortcut, transportData.coordinates)) {
            return true;
        }

        // Check by obstacle IDs
        return isMatchedByObstacleIds(shortcut, transportData.objectIds);
    }

    /**
     * Checks if the shortcut's world location matches any coordinate in the transport data.
     */
    private boolean isMatchedByLocation(AgilityShortcut shortcut, Set<String> coordinates) {
        if (shortcut.getWorldLocation() == null) {
            return false;
        }

        String coordinate = shortcut.getWorldLocation().getX() + " " 
                          + shortcut.getWorldLocation().getY() + " " 
                          + shortcut.getWorldLocation().getPlane();
        
        return coordinates.contains(coordinate);
    }

    /**
     * Checks if any of the shortcut's obstacle IDs match object IDs in the transport data.
     * Returns true if any non-excluded ID matches, or if all IDs are excluded.
     */
    private boolean isMatchedByObstacleIds(AgilityShortcut shortcut, Set<Integer> objectIds) {
        if (shortcut.getObstacleIds() == null) {
            return false;
        }

        boolean hasExcludedId = false;
        for (int obstacleId : shortcut.getObstacleIds()) {
            if (EXCLUDED_OBJECT_IDS.contains(obstacleId)) {
                System.out.println("Skipping excluded ID " + obstacleId + " for " + shortcut.name());
                hasExcludedId = true;
                continue;
            }

            if (objectIds.contains(obstacleId)) {
                return true;
            }
        }

        // If all IDs were excluded, consider it matched to avoid false failures
        return hasExcludedId;
    }

    /**
     * Holds coordinate and object ID data extracted from transports.
     */
    private static class TransportData {
        final Set<String> coordinates;
        final Set<Integer> objectIds;

        TransportData(Set<String> coordinates, Set<Integer> objectIds) {
            this.coordinates = coordinates;
            this.objectIds = objectIds;
        }
    }
}
