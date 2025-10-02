package shortestpath.transport;

import org.junit.Test;
import org.junit.Assert;

import net.runelite.client.util.WorldUtil;
import shortestpath.WorldPointUtil;

import java.util.HashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unit tests for validating transport data integrity.
 * These tests validate the consistency and correctness of transport data files.
 */
public class TransportDataLintTest {

    @Test
    public void testNoDuplicateOriginDestinationPairs() {
        // Load all transport data from resources
        HashMap<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();

        // Track all origin-destination-type combinations to check for exact duplicates
        Set<String> transportSignatures = new HashSet<>();
        Map<String, String> duplicateInfo = new HashMap<>();
        List<String> duplicatesFound = new ArrayList<>();
        
        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            int origin = entry.getKey();
            Set<Transport> transportSet = entry.getValue();

            for (Transport transport : transportSet) {
                int destination = transport.getDestination();

                // Create a comprehensive signature that includes all transport properties
                // This helps distinguish between legitimate different transport variants vs
                // true duplicates
                // ObjectInfo is critical for distinguishing fairy rings with different item
                // requirements
                // (e.g., dramen staff vs lunar staff are represented by different objectIDs in
                // objectInfo)
                // The display info is there of the one edge case in the fairy ring system.
                // There are two options to go to Zanaris, one is with the code `B K S` and the
                // other is with the `zanaris` option.
                // only the display info is different between those two options so we need to
                // include it in the signature.
                String signature = String.format(
                        "%s -> %s [%s] '%s' Consumable:%s Items:%s Quests:%s Varbits:%s VarPlayers:%s ObjectInfo:%s",
                        WorldPointUtil.unpackWorldX(origin) + " " + WorldPointUtil.unpackWorldY(origin) + " "
                                + WorldPointUtil.unpackWorldPlane(origin),
                        WorldPointUtil.unpackWorldX(destination) + " " + WorldPointUtil.unpackWorldY(destination) + " "
                                + WorldPointUtil.unpackWorldPlane(destination),
                        transport.getType(),
                        transport.getDisplayInfo() != null ? transport.getDisplayInfo() : "",
                        transport.isConsumable(),
                        transport.getItemRequirements() != null ? transport.getItemRequirements().toString() : "none",
                        transport.getQuests().toString(),
                        transport.getVarbits().toString(),
                        transport.getVarPlayers().toString(),
                        transport.getObjectInfo() != null ? transport.getObjectInfo() : "none");

                // Check if this exact transport signature already exists
                if (transportSignatures.contains(signature)) {
                    String existingInfo = duplicateInfo.get(signature);
                    String newInfo = String.format("MaxWilderness: %d, Skills: %s",
                            transport.getMaxWildernessLevel(),
                            Arrays.toString(transport.getSkillLevels()));

                    duplicatesFound.add(String.format("Exact duplicate transport: %s\n" +
                            "First occurrence: %s\n" +
                            "Duplicate occurrence: %s\n",
                            signature, existingInfo, newInfo));
                } else {
                    // Add this signature to our tracking set
                    transportSignatures.add(signature);

                    // Store information about this transport for error reporting
                    String info = String.format("MaxWilderness: %d, Skills: %s",
                            transport.getMaxWildernessLevel(),
                            Arrays.toString(transport.getSkillLevels()));
                    duplicateInfo.put(signature, info);
                }
            }
        }

        // Report results
        if (!duplicatesFound.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append(String.format("Found %d duplicate transport entries in the data files:\n\n",
                    duplicatesFound.size()));
            for (String duplicate : duplicatesFound) {
                message.append(duplicate).append("\n");
            }
            Assert.fail(message.toString());
        }

        // If we get here, no exact duplicates were found
        System.out.printf("Successfully validated %d unique transport signatures across all transport data files.\n",
                transportSignatures.size());
    }
}
