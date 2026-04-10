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

        // Check what transports are used after this bank step
        boolean usesFairyRing = false;
        boolean usesTeleportItem = false;
        Transport teleportTransport = null;

        for (int i = pathIndex; i < path.size() - 1; i++) {
            int stepPoint = path.get(i).getPackedPosition();
            int nextPoint = path.get(i + 1).getPackedPosition();
            boolean banked = path.get(i + 1).isBankVisited();
            Set<Transport> stepTransports = pathfinderConfig.getTransportAvailability(banked)
                    .getTransportsByOrigin().get(stepPoint);
            if (stepTransports != null) {
                for (Transport t : stepTransports) {
                    if (t.getDestination() == nextPoint) {
                        if (TransportType.FAIRY_RING.equals(t.getType())) {
                            usesFairyRing = true;
                        }
                        if (TransportType.TELEPORTATION_ITEM.equals(t.getType()) && teleportTransport == null) {
                            usesTeleportItem = true;
                            teleportTransport = t;
                        }
                    }
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

        // Check if teleport item needs to be picked up
        if (usesTeleportItem && teleportTransport != null) {
            String teleportPickup = checkTeleportItemInBank(client, bank, teleportTransport);
            if (teleportPickup != null) {
                requiredItems.add(teleportPickup);
            }
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
     * Checks if a teleport item is in the bank and not in inventory/equipment.
     */
    private static String checkTeleportItemInBank(Client client, ItemContainer bank, Transport transport) {
        if (transport.getItemRequirements() == null || transport.getItemRequirements().size() == 0) {
            return null;
        }

        // Get the first item requirement (the teleport item itself)
        int[] teleportItemIds = transport.getItemRequirements().getRequirements().get(0).getItemIds();
        if (teleportItemIds == null || teleportItemIds.length == 0) {
            return null;
        }

        // Check if already in inventory or equipment
        if (hasItemInInventoryOrEquipment(client, teleportItemIds)) {
            return null;
        }

        // Check if in bank
        if (hasItemInBank(bank, teleportItemIds)) {
            String displayInfo = transport.getDisplayInfo();
            if (displayInfo != null) {
                // Extract just the item name (before the colon)
                int colonIndex = displayInfo.indexOf(':');
                if (colonIndex > 0) {
                    return displayInfo.substring(0, colonIndex).trim();
                }
                return displayInfo;
            }
        }

        return null;
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
