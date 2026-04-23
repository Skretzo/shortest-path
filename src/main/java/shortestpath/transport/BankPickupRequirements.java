package shortestpath.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailability;

/**
 * Determines what items need to be picked up from the bank for a given path.
 * This handles transport-specific requirements like the Dramen staff for fairy rings.
 */
public class BankPickupRequirements {

    /**
     * Gets a list of items that need to be picked up from the bank at a given path step.
     *
     * @param client The game client
     * @param bank The bank ItemContainer
     * @param pathfinderConfig The pathfinder config for bank-aware transport lookups
     * @param bankLocations Set of bank location coordinates
     * @param path The current path
     * @param pathIndex The current step index in the path
     * @return List of item names to pick up, or empty list if none needed
     */
    public static List<String> getRequiredBankItems(
            Client client,
            ItemContainer bank,
            PathfinderConfig pathfinderConfig,
            Set<Integer> bankLocations,
            List<PathStep> path,
            int pathIndex) {

        List<String> requiredItems = new ArrayList<>();

        if (bank == null || path == null || pathIndex < 0 || pathIndex >= path.size()) {
            return requiredItems;
        }

        // Check if this is a bank step
        int currentPoint = path.get(pathIndex).getPackedPosition();
        if (!bankLocations.contains(currentPoint)) {
            return requiredItems;
        }

        // Check what transports are used after this bank step, up to (but not including) the next bank
        boolean usesFairyRing = false;
        List<Transport> teleportTransports = new ArrayList<>();

        for (int i = pathIndex; i < path.size() - 1; i++) {
            int stepPoint = path.get(i).getPackedPosition();
            int nextPoint = path.get(i + 1).getPackedPosition();
            boolean banked = path.get(i + 1).isBankVisited();
            TransportAvailability availability = pathfinderConfig.getTransportAvailability(banked);
            Set<Transport> stepTransports = availability.getTransportsByOrigin().get(stepPoint);
            if (stepTransports != null) {
                for (Transport t : stepTransports) {
                    if (t.getDestination() == nextPoint) {
                        if (TransportType.FAIRY_RING.equals(t.getType())) {
                            usesFairyRing = true;
                        }
                        if (TransportType.TELEPORTATION_ITEM.equals(t.getType())) {
                            teleportTransports.add(t);
                        }
                    }
                }
            }
            // Teleportation items with no fixed origin (e.g. Royal seed pod) live in usableTeleports
            for (Transport t : availability.getUsableTeleports()) {
                if (t.getDestination() == nextPoint && TransportType.TELEPORTATION_ITEM.equals(t.getType())) {
                    teleportTransports.add(t);
                }
            }
        }

        // Check if Dramen/Lunar staff needs to be picked up for fairy rings
        if (usesFairyRing) {
            String staffPickup = checkDramenStaffInBank(client, bank);
            if (staffPickup != null) {
                requiredItems.add(staffPickup);
            }
        }

        // Check if teleport items need to be picked up (all teleport transports along the path)
        for (Transport teleportTransport : teleportTransports) {
            List<String> teleportPickups = checkTeleportItemsInBank(client, bank, teleportTransport);
            requiredItems.addAll(teleportPickups);
        }

        return requiredItems;
    }

    /**
     * Checks if the Dramen/Lunar staff is in the bank and not in inventory/equipment.
     */
    private static String checkDramenStaffInBank(Client client, ItemContainer bank) {
        int[] staffIds = ItemVariations.DRAMEN_STAFF.getIds();

        // Check if already in inventory or equipment
        if (hasItemInInventoryOrEquipment(client, staffIds)) {
            return null;
        }

        // Check if in bank
        if (hasItemInBank(bank, staffIds)) {
            return "Dramen/Lunar staff";
        }

        return null;
    }

    /**
     * Checks which item requirements for a teleport transport are in the bank but not yet in
     * inventory/equipment. Returns a display name for each such item.
     */
    private static List<String> checkTeleportItemsInBank(Client client, ItemContainer bank, Transport transport) {
        List<String> result = new ArrayList<>();
        if (transport.getItemRequirements() == null || transport.getItemRequirements().size() == 0) {
            return result;
        }

        for (shortestpath.transport.requirement.ItemRequirement req : transport.getItemRequirements().getRequirements()) {
            int[] itemIds = req.getItemIds();
            if (itemIds == null || itemIds.length == 0) {
                continue;
            }

            // Already carried — no need to pick it up
            if (hasItemInInventoryOrEquipment(client, itemIds)) {
                continue;
            }

            // In bank — player needs to withdraw it
            if (hasItemInBank(bank, itemIds)) {
                String displayInfo = transport.getDisplayInfo();
                if (displayInfo != null) {
                    // Extract just the item name (the part before the colon, e.g. "Amulet of glory: Edgeville")
                    int colonIndex = displayInfo.indexOf(':');
                    result.add(colonIndex > 0 ? displayInfo.substring(0, colonIndex).trim() : displayInfo);
                }
            }
        }

        return result;
    }

    private static boolean hasItemInInventoryOrEquipment(Client client, int[] itemIds) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                for (int itemId : itemIds) {
                    if (item.getId() == itemId) {
                        return true;
                    }
                }
            }
        }

        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        if (equipment != null) {
            for (Item item : equipment.getItems()) {
                for (int itemId : itemIds) {
                    if (item.getId() == itemId) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean hasItemInBank(ItemContainer bank, int[] itemIds) {
        for (Item bankItem : bank.getItems()) {
            for (int itemId : itemIds) {
                if (bankItem.getId() == itemId && bankItem.getQuantity() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
