package shortestpath.transport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.transport.requirement.ItemRequirement;

/**
 * Determines what items need to be picked up from the bank for a given path.
 * This handles transport-specific requirements like the Dramen staff for fairy rings.
 *
 * Multiple transports that connect the same edge are treated as alternatives (OR):
 * if the player can satisfy any one of them, no pickup is needed; otherwise the
 * cheapest single alternative that can be filled from the bank is chosen.
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

        // Snapshot what the player already has on them (inventory + equipment + rune pouch).
        Map<Integer, Integer> playerHas = collectPlayerItems(client);

        // Snapshot bank contents.
        Map<Integer, Integer> bankHas = new HashMap<>();
        for (Item bankItem : bank.getItems()) {
            if (bankItem.getId() >= 0 && bankItem.getQuantity() > 0) {
                bankHas.merge(bankItem.getId(), bankItem.getQuantity(), Integer::sum);
            }
        }

        // Each entry is one edge's pickup phrase, e.g. "Air rune (3), Law rune or Varrock teleport".
        // A LinkedHashSet preserves order while deduplicating identical phrases across edges.
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        boolean usesFairyRing = false;

        // Walk each edge in the remaining path and collect alternative transports per edge.
        for (int i = pathIndex; i < path.size() - 1; i++) {
            int stepPoint = path.get(i).getPackedPosition();
            int nextPoint = path.get(i + 1).getPackedPosition();
            boolean banked = path.get(i + 1).isBankVisited();
            TransportAvailability availability = pathfinderConfig.getTransportAvailability(banked);

            List<Transport> edgeAlternatives = new ArrayList<>();
            Set<Transport> originSet = availability.getTransportsByOrigin().get(stepPoint);
            if (originSet != null) {
                for (Transport t : originSet) {
                    if (t.getDestination() == nextPoint) {
                        edgeAlternatives.add(t);
                    }
                }
            }
            for (Transport t : availability.getUsableTeleports()) {
                if (t.getDestination() == nextPoint) {
                    edgeAlternatives.add(t);
                }
            }
            if (edgeAlternatives.isEmpty()) {
                continue;
            }

            // Fairy rings handled once globally via the staff requirement below.
            List<Transport> nonFairy = new ArrayList<>();
            for (Transport t : edgeAlternatives) {
                if (TransportType.FAIRY_RING.equals(t.getType())) {
                    usesFairyRing = true;
                } else {
                    nonFairy.add(t);
                }
            }
            if (nonFairy.isEmpty()) {
                continue;
            }

            // If at least one alternative requires no items, nothing to pick up for this edge.
            boolean anyAlternativeIsFree = false;
            for (Transport t : nonFairy) {
                if (t.getItemRequirements() == null || t.getItemRequirements().size() == 0) {
                    anyAlternativeIsFree = true;
                    break;
                }
            }
            if (anyAlternativeIsFree) {
                continue;
            }

            // If any alternative is fully satisfied by the player already, no pickup needed.
            boolean satisfied = false;
            for (Transport t : nonFairy) {
                if (transportSatisfiedBy(t, playerHas)) {
                    satisfied = true;
                    break;
                }
            }
            if (satisfied) {
                continue;
            }

            // Otherwise, surface every alternative the bank can fully supply, joined by " or ".
            // Deduplicate alternatives that resolve to the exact same set of bank items.
            LinkedHashSet<String> altStrings = new LinkedHashSet<>();
            for (Transport t : nonFairy) {
                Map<Integer, Long> pickups = computeBankPickups(t, playerHas, bankHas);
                if (pickups == null || pickups.isEmpty()) {
                    continue; // bank can't satisfy this alternative
                }
                altStrings.add(formatPickups(client, pickups));
            }
            if (altStrings.isEmpty()) {
                continue;
            }
            phrases.add(String.join(" or ", altStrings));
        }

        // Fairy ring staff (Dramen / Lunar) is a single OR requirement across the whole trip.
        if (usesFairyRing) {
            int[] staffIds = ItemVariations.DRAMEN_STAFF.getIds();
            if (!hasAnyItem(playerHas, staffIds, 1)) {
                int foundId = findItemIdInBank(bankHas, staffIds, 1);
                if (foundId != -1) {
                    Map<Integer, Long> single = new LinkedHashMap<>();
                    single.put(foundId, 1L);
                    phrases.add(formatPickups(client, single));
                }
            }
        }

        requiredItems.addAll(phrases);
        return requiredItems;
    }

    /**
     * Formats a pickup map (item id → quantity) as a comma-separated, human-readable string.
     */
    private static String formatPickups(Client client, Map<Integer, Long> pickups) {
        List<String> parts = new ArrayList<>(pickups.size());
        for (Map.Entry<Integer, Long> entry : pickups.entrySet()) {
            int itemId = entry.getKey();
            long qty = entry.getValue();
            String itemName = client.getItemDefinition(itemId).getName();
            if (itemName == null || itemName.isEmpty() || "null".equals(itemName)) {
                itemName = "Unknown item";
            }
            boolean isCurrency = PathfinderConfig.CURRENCIES.contains(itemId);
            if (isCurrency && qty > 1) {
                itemName += " (" + String.format("%,d", qty) + ")";
            }
            parts.add(itemName);
        }
        return String.join(", ", parts);
    }

    /**
     * Returns true if the player has at least {@code requiredQty} of any id in {@code itemIds}
     * across inventory/equipment/rune-pouch.
     */
    private static boolean hasAnyItem(Map<Integer, Integer> playerHas, int[] itemIds, int requiredQty) {
        if (itemIds == null) {
            return false;
        }
        for (int id : itemIds) {
            if (playerHas.getOrDefault(id, 0) >= requiredQty) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the item ID from {@code itemIds} present in the bank with at least
     * {@code requiredQty}, or -1 if none qualifies.
     */
    private static int findItemIdInBank(Map<Integer, Integer> bankHas, int[] itemIds, int requiredQty) {
        if (itemIds == null) {
            return -1;
        }
        for (int id : itemIds) {
            if (bankHas.getOrDefault(id, 0) >= requiredQty) {
                return id;
            }
        }
        return -1;
    }

    /**
     * Returns true if every requirement of the transport is already met by the player's
     * inventory/equipment/rune-pouch (taking item-id, staff and offhand variations into account).
     */
    private static boolean transportSatisfiedBy(Transport transport, Map<Integer, Integer> playerHas) {
        if (transport.getItemRequirements() == null) {
            return true;
        }
        for (ItemRequirement req : transport.getItemRequirements().getRequirements()) {
            int qty = req.getQuantity() > 0 ? req.getQuantity() : 1;
            if (hasAnyItem(playerHas, req.getItemIds(), qty)
                    || hasAnyItem(playerHas, req.getStaffIds(), 1)
                    || hasAnyItem(playerHas, req.getOffhandIds(), 1)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * For an unsatisfied transport, returns the items (id → qty) that need to be picked up
     * from the bank to satisfy it, or null if the bank can't supply them.
     */
    private static Map<Integer, Long> computeBankPickups(Transport transport,
                                                         Map<Integer, Integer> playerHas,
                                                         Map<Integer, Integer> bankHas) {
        Map<Integer, Long> pickups = new LinkedHashMap<>();
        if (transport.getItemRequirements() == null) {
            return pickups;
        }
        for (ItemRequirement req : transport.getItemRequirements().getRequirements()) {
            int qty = req.getQuantity() > 0 ? req.getQuantity() : 1;
            // Already satisfied by player?
            if (hasAnyItem(playerHas, req.getItemIds(), qty)
                    || hasAnyItem(playerHas, req.getStaffIds(), 1)
                    || hasAnyItem(playerHas, req.getOffhandIds(), 1)) {
                continue;
            }
            // Try to satisfy from bank: prefer the primary item ids, then staves, then offhands.
            int foundId = findItemIdInBank(bankHas, req.getItemIds(), qty);
            int foundQty = qty;
            if (foundId == -1) {
                foundId = findItemIdInBank(bankHas, req.getStaffIds(), 1);
                foundQty = 1;
            }
            if (foundId == -1) {
                foundId = findItemIdInBank(bankHas, req.getOffhandIds(), 1);
                foundQty = 1;
            }
            if (foundId == -1) {
                return null; // bank can't satisfy this requirement
            }
            pickups.merge(foundId, (long) foundQty, Long::sum);
        }
        return pickups;
    }

    /**
     * Collects the items the player currently has accessible (inventory + equipment + rune pouch).
     */
    private static Map<Integer, Integer> collectPlayerItems(Client client) {
        Map<Integer, Integer> totals = new HashMap<>();

        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item.getId() >= 0 && item.getQuantity() > 0) {
                    totals.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        if (equipment != null) {
            for (Item item : equipment.getItems()) {
                if (item.getId() >= 0 && item.getQuantity() > 0) {
                    totals.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        // Rune pouch contents (only readable when a pouch is in inventory).
        boolean hasPouch = false;
        for (Integer pouchId : PathfinderConfig.RUNE_POUCHES) {
            if (totals.containsKey(pouchId)) {
                hasPouch = true;
                break;
            }
        }
        if (hasPouch) {
            EnumComposition runePouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
            if (runePouchEnum != null) {
                for (int i = 0; i < PathfinderConfig.RUNE_POUCH_RUNE_VARBITS.length; i++) {
                    int runeEnumId = client.getVarbitValue(PathfinderConfig.RUNE_POUCH_RUNE_VARBITS[i]);
                    int runeId = runeEnumId > 0 ? runePouchEnum.getIntValue(runeEnumId) : 0;
                    int runeAmount = client.getVarbitValue(PathfinderConfig.RUNE_POUCH_AMOUNT_VARBITS[i]);
                    if (runeId > 0 && runeAmount > 0) {
                        totals.merge(runeId, runeAmount, Integer::sum);
                    }
                }
            }
        }

        return totals;
    }
}
