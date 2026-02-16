package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.Destination;
import shortestpath.ItemVariations;
import shortestpath.JewelleryBoxTier;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.ShortestPathConfig;
import shortestpath.ShortestPathPlugin;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;
import shortestpath.transport.TransportTypeConfig;
import shortestpath.transport.parser.VarRequirement;
import shortestpath.transport.requirement.TransportItems;

public class PathfinderConfig {
    private static final List<Integer> RUNE_POUCHES = Arrays.asList(
            ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
            ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
    );
    private static final int[] RUNE_POUCH_RUNE_VARBITS = {
            VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4,
            VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
    };
    private static final int[] RUNE_POUCH_AMOUNT_VARBITS = {
            VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4,
            VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
    };
    private static final Set<Integer> CURRENCIES = Set.of(
            ItemID.COINS, ItemID.VILLAGE_TRADE_STICKS, ItemID.ECTOTOKEN, ItemID.WARGUILD_TOKENS);
    private static final TransportItems DRAMEN_STAFF = new TransportItems(
            new int[][]{null},
            new int[][]{ItemVariations.DRAMEN_STAFF.getIds()},
            new int[][]{null},
            new int[]{1});

    private final SplitFlagMap mapData;
    private final ThreadLocal<CollisionMap> map;
    /**
     * All transports by origin. The WorldPointUtil.UNDEFINED key is used for transports centered on the player.
     */
    private final Map<Integer, Set<Transport>> allTransports;
    private final Set<Transport> usableTeleports;
    private final Map<String, Set<Integer>> allDestinations;
    private final Map<String, Set<Integer>> filteredDestinations;
    private final Map<Integer, Integer> itemsAndQuantities = new HashMap<>(28 + 11 + 500);
    private final List<Integer> filteredTargets = new ArrayList<>(4);

    @Getter
    private final Map<Integer, Set<Transport>> transports;
    // Copy of transports with packed positions for the hotpath; lists are not copied and are the same reference in both maps
    @Getter
    private final PrimitiveIntHashMap<Set<Transport>> transportsPacked;
    /**
     * Reference that points to either allDestinations or filteredDestinations
     */
    private Map<String, Set<Integer>> destinations;

    private final Client client;
    private final ShortestPathConfig config;

    @Getter
    private long calculationCutoffMillis;
    @Getter
    private boolean avoidWilderness;
    @Getter
    private boolean bankVisited;

    // Centralized transport type enable/disable config
    private final TransportTypeConfig transportTypeConfig;

    // POH-specific settings (not tied to a single TransportType)
    private boolean usePohFairyRing,
            usePohSpiritTree,
            usePohMountedItems,
            usePoh,
            usePohObelisk,
            includeBankPath;
    private JewelleryBoxTier pohJewelleryBoxTier;
    private int costConsumableTeleportationItems;
    private int currencyThreshold;
    private final int[] boostedSkillLevelsAndMore = new int[Skill.values().length + 3];
    private final Map<Quest, QuestState> questStates = new HashMap<>();
    private final Map<Integer, Integer> varbitValues = new HashMap<>();
    private final Map<Integer, Integer> varPlayerValues = new HashMap<>();

    public ItemContainer bank = null;

    public PathfinderConfig(Client client, ShortestPathConfig config) {
        this.client = client;
        this.config = config;
        this.transportTypeConfig = new TransportTypeConfig(config);
        this.mapData = SplitFlagMap.fromResources();
        this.map = ThreadLocal.withInitial(() -> new CollisionMap(mapData));
        this.allTransports = TransportLoader.loadAllFromResources();
        this.usableTeleports = new HashSet<>(allTransports.size() / 20);
        this.transports = new HashMap<>(allTransports.size() / 2);
        this.transportsPacked = new PrimitiveIntHashMap<>(allTransports.size() / 2);
        this.allDestinations = Destination.loadAllFromResources();
        this.filteredDestinations = filterDestinations(allDestinations);
        this.destinations = allDestinations;
    }

    public CollisionMap getMap() {
        return map.get();
    }

    public boolean hasDestination(String destinationType) {
        return destinations.containsKey(destinationType);
    }

    public Set<Integer> getDestinations(String destinationType) {
        return destinations.get(destinationType);
    }

    public void refresh() {
        calculationCutoffMillis = config.calculationCutoff() * Constants.GAME_TICK_LENGTH;
        avoidWilderness = ShortestPathPlugin.override("avoidWilderness", config.avoidWilderness());
        usePoh = ShortestPathPlugin.override("usePoh", config.usePoh());

        // Refresh transport type enabled states
        transportTypeConfig.refresh();
        // POH-specific settings
        usePohFairyRing = ShortestPathPlugin.override("usePohFairyRing", config.usePohFairyRing());
        usePohSpiritTree = ShortestPathPlugin.override("usePohSpiritTree", config.usePohSpiritTree());
        usePohMountedItems = ShortestPathPlugin.override("usePohMountedItems", config.usePohMountedItems());
        usePohObelisk = ShortestPathPlugin.override("usePohObelisk", config.usePohObelisk());
        pohJewelleryBoxTier = ShortestPathPlugin.override("pohJewelleryBoxTier", config.pohJewelleryBoxTier());

        // Other settings (useTeleportationItems is now managed by transportTypeConfig)
        currencyThreshold = ShortestPathPlugin.override("currencyThreshold", config.currencyThreshold());
        includeBankPath = ShortestPathPlugin.override("includeBankPath", config.includeBankPath());
        bankVisited = !includeBankPath;

        // Note: Transport type costs are now managed by transportTypeConfig.getCost()
        costConsumableTeleportationItems = ShortestPathPlugin.override("costConsumableTeleportationItems", config.costConsumableTeleportationItems());

        if (GameState.LOGGED_IN.equals(client.getGameState())) {
            int i = 0;
            for (; i < Skill.values().length; i++) {
                boostedSkillLevelsAndMore[i] = client.getBoostedSkillLevel(Skill.values()[i]);
            }
            boostedSkillLevelsAndMore[i++] = client.getTotalLevel(); // skill total level
            boostedSkillLevelsAndMore[i++] = getCombatLevel(); // combat level
            boostedSkillLevelsAndMore[i++] = client.getVarpValue(VarPlayerID.QP); // quest points

            refreshTransports();
        }

        refreshDestinations();
    }

    /**
     * Specialized method for only updating player-held item and spell transports
     */
    public void refreshTeleports(int packedLocation, int wildernessLevel) {
        Set<Transport> usableWildyTeleports = new HashSet<>(usableTeleports.size());

        for (Transport teleport : usableTeleports) {
            if (wildernessLevel <= teleport.getMaxWildernessLevel()) {
                usableWildyTeleports.add(teleport);
            }
        }

        if (!usableWildyTeleports.isEmpty()) {
            Set<Transport> oldTransports = transports.getOrDefault(packedLocation, new HashSet<>());
            oldTransports.addAll(usableWildyTeleports);
            transports.put(packedLocation, oldTransports);
            transportsPacked.put(packedLocation, usableWildyTeleports); // appends for collections
        }
    }

    private void refreshDestinations() {
        destinations = avoidWilderness ? filteredDestinations : allDestinations;
    }

    /**
     * Changes to the config might have invalidated some locations, e.g. those in the wilderness
     */
    public void filterLocations(Set<Integer> locations, boolean canReviveFiltered) {
        if (avoidWilderness) {
            locations.removeIf(location -> {
                boolean inWilderness = WildernessChecker.isInWilderness(location);
                if (inWilderness) {
                    filteredTargets.add(location);
                }
                return inWilderness;
            });
            // If we ended up with no valid locations we re-include the filtered locations
            if (locations.isEmpty()) {
                locations.addAll(filteredTargets);
                filteredTargets.clear();
            }
        } else if (canReviveFiltered) { // Re-include previously filtered locations
            locations.addAll(filteredTargets);
            filteredTargets.clear();
        }
    }

    /**
     * Returns the user-configured additional cost for a given transport
     */
    public int getAdditionalTransportCost(Transport transport) {
        if (transport.isConsumable() && TransportType.TELEPORTATION_ITEM.equals(transport.getType())) {
            return costConsumableTeleportationItems;
        }
        int cost = transportTypeConfig.getCost(transport.getType());
        return cost;
    }

    private Map<String, Set<Integer>> filterDestinations(Map<String, Set<Integer>> allDestinations) {
        Map<String, Set<Integer>> filteredDestinations = new HashMap<>(allDestinations.size());
        for (Map.Entry<String, Set<Integer>> entry : allDestinations.entrySet()) {
            String destinationType = entry.getKey();
            Set<Integer> usableDestinations = new HashSet<>(entry.getValue().size());
            boolean isDifferent = false;
            for (Integer destination : entry.getValue()) {
                // We filter based on whether the destination is inside or outside wilderness
                if (!WildernessChecker.isInWilderness(destination)) {
                    usableDestinations.add(destination);
                    isDifferent = true;
                }
            }
            // If all destinations of a destination type have been filtered away then we don't add the entry
            if (!usableDestinations.isEmpty()) {
                // If no destinations of a destination type have been filtered away then we re-use the same set reference
                filteredDestinations.put(destinationType, isDifferent ? usableDestinations : entry.getValue());
            }
        }
        return filteredDestinations;
    }

    private void refreshTransports() {
        if (!Thread.currentThread().equals(client.getClientThread())) {
            return; // Has to run on the client thread; data will be refreshed when path finding commences
        }

        // Apply runtime restrictions based on quests/items
        // Cache the varbits needed for fairy ring checks so they can be used by refreshTransportsForBankVisit
        varbitValues.put(VarbitID.FAIRY2_QUEENCURE_QUEST, client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST));
        varbitValues.put(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE));

        // For fairy rings, only enable the type if staff is in inventory/equipment.
        // If staff is only in bank, the type will be enabled later when bank is visited.
        // This prevents fairy ring routes that don't go through a bank.
        boolean hasFairyRingQuest = varbitValues.get(VarbitID.FAIRY2_QUEENCURE_QUEST) > 39;
        boolean hasLumbridgeDiary = varbitValues.get(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;
        boolean hasStaffInInventoryOrEquipment = hasRequiredItems(DRAMEN_STAFF, true, true, false, false);
        transportTypeConfig.disableUnless(TransportType.FAIRY_RING,
                hasFairyRingQuest && (hasLumbridgeDiary || hasStaffInInventoryOrEquipment));
        transportTypeConfig.disableUnless(TransportType.GNOME_GLIDER,
                QuestState.FINISHED.equals(getQuestState(Quest.THE_GRAND_TREE)));
        transportTypeConfig.disableUnless(TransportType.MAGIC_MUSHTREE,
                QuestState.FINISHED.equals(getQuestState(Quest.BONE_VOYAGE)));
        transportTypeConfig.disableUnless(TransportType.SPIRIT_TREE,
                QuestState.FINISHED.equals(getQuestState(Quest.TREE_GNOME_VILLAGE)));

        transports.clear();
        transportsPacked.clear();
        usableTeleports.clear();
        for (Map.Entry<Integer, Set<Transport>> entry : allTransports.entrySet()) {
            int point = entry.getKey();
            Set<Transport> usableTransports = new HashSet<>(entry.getValue().size());
            for (Transport transport : entry.getValue()) {
                for (Quest quest : transport.getQuests()) {
                    try {
                        questStates.put(quest, getQuestState(quest));
                    } catch (NullPointerException ignored) {
                    }
                }

                for (VarRequirement varRequirement : transport.getVarRequirements()) {
                    if (varRequirement.isVarbit()) {
                        varbitValues.put(varRequirement.getId(), client.getVarbitValue(varRequirement.getId()));
                    } else {
                        varPlayerValues.put(varRequirement.getId(), client.getVarpValue(varRequirement.getId()));
                    }
                }

                if (useTransport(transport) && hasRequiredItems(transport)) {
                    if (point == WorldPointUtil.UNDEFINED) {
                        usableTeleports.add(transport);
                    } else {
                        usableTransports.add(transport);
                    }
                }
            }

            if (point != WorldPointUtil.UNDEFINED && !usableTransports.isEmpty()) {
                transports.put(point, usableTransports);
                transportsPacked.put(point, usableTransports);
            }
        }

        // Remap all POH transport origins to the house landing tile
        remapPohTransports();
    }

    private void refreshUsableTeleports() {
        // Only for appending and not for removing teleports
        for (Map.Entry<Integer, Set<Transport>> entry : allTransports.entrySet()) {
            if (entry.getKey() == WorldPointUtil.UNDEFINED) { // is a teleport
                for (Transport transport : entry.getValue()) {
                    if (useTransport(transport)
                            && hasRequiredItems(transport, false, false, true, false)) {
                        usableTeleports.add(transport);
                    }
                }
            }
        }
    }

    public boolean avoidWilderness(int packedPosition, int packedNeighborPosition, boolean targetInWilderness) {
        return avoidWilderness
                && !targetInWilderness
                && !WildernessChecker.isInWilderness(packedPosition)
                && WildernessChecker.isInWilderness(packedNeighborPosition);
    }

    public void setBankVisited(boolean visited, int packedLocation, int wildernessLevel) {
        bankVisited = visited;
        if (bankVisited) {
            // Re-run transport refresh to add transports that require bank items (like fairy rings with staff in bank)
            // Use the non-client-thread version since we're called from the pathfinder thread
            refreshTransportsForBankVisit();
            refreshUsableTeleports();
            refreshTeleports(packedLocation, wildernessLevel);
        }
    }

    /**
     * Refreshes transports when bank is visited during pathfinding.
     * This is called from the pathfinder thread.
     * It only needs to add transports that require bank items - inventory/equipment
     * transports were already added during the initial refreshTransports() on client thread.
     */
    private void refreshTransportsForBankVisit() {
        // Re-evaluate fairy ring type based on bank items now being available
        boolean hasFairyRingQuest = varbitValues.getOrDefault(VarbitID.FAIRY2_QUEENCURE_QUEST, 0) > 39;
        boolean hasLumbridgeDiary = varbitValues.getOrDefault(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0) == 1;
        boolean hasStaffInBank = hasStaffInBank();

        // Only enable fairy rings if we have the staff in bank (inventory/equipment was already checked)
        if (hasFairyRingQuest && (hasLumbridgeDiary || hasStaffInBank)) {
            transportTypeConfig.disableUnless(TransportType.FAIRY_RING, true);

            // Add fairy ring transports that weren't added before because staff was in bank
            for (Map.Entry<Integer, Set<Transport>> entry : allTransports.entrySet()) {
                int point = entry.getKey();
                if (point == WorldPointUtil.UNDEFINED) {
                    continue; // Teleports handled separately
                }

                for (Transport transport : entry.getValue()) {
                    if (TransportType.FAIRY_RING.equals(transport.getType())) {
                        if (useTransport(transport)) {
                            // Add to existing transports at this point
                            Set<Transport> existing = transports.getOrDefault(point, new HashSet<>());
                            existing.add(transport);
                            transports.put(point, existing);
                            transportsPacked.put(point, existing);
                        }
                    }
                }
            }

            // Remap any POH fairy rings
            remapPohTransports();
        }
    }

    /**
     * Checks if the Dramen/Lunar staff is in the bank.
     */
    private boolean hasStaffInBank() {
        if (bank == null) {
            return false;
        }
        for (int[] staffIds : DRAMEN_STAFF.getStaves()) {
            if (staffIds != null) {
                for (int staffId : staffIds) {
                    for (Item bankItem : bank.getItems()) {
                        if (bankItem.getId() == staffId && bankItem.getQuantity() > 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Remaps POH transport origins to the house landing tile.
     * Extracted to be reusable by both refreshTransports and refreshTransportsForBankVisit.
     */
    private void remapPohTransports() {
        int pohLanding = WorldPointUtil.packWorldPoint(1923, 5709, 0);
        Set<Transport> pohTransports = new HashSet<>();
        Set<Integer> pohOriginsToRemove = new HashSet<>();

        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            int origin = entry.getKey();
            int originX = WorldPointUtil.unpackWorldX(origin);
            int originY = WorldPointUtil.unpackWorldY(origin);
            if (ShortestPathPlugin.isInsidePoh(originX, originY)) {
                pohTransports.addAll(entry.getValue());
                pohOriginsToRemove.add(origin);
            }
        }

        // Remove POH origins from transports map (PrimitiveIntHashMap doesn't support remove)
        for (Integer origin : pohOriginsToRemove) {
            transports.remove(origin);
        }

        if (!pohTransports.isEmpty()) {
            Set<Transport> existingPohTransports = transports.getOrDefault(pohLanding, new HashSet<>());
            existingPohTransports.addAll(pohTransports);
            transports.put(pohLanding, existingPohTransports);

            // Also update transportsPacked for the landing tile
            transportsPacked.put(pohLanding, existingPohTransports);
        }
    }

    public QuestState getQuestState(Quest quest) {
        return quest.getState(client);
    }

    private boolean completedQuests(Transport transport) {
        for (Quest quest : transport.getQuests()) {
            if (!QuestState.FINISHED.equals(questStates.getOrDefault(quest, QuestState.NOT_STARTED))) {
                return false;
            }
        }
        return true;
    }

    public boolean varbitChecks(Transport transport) {
        for (VarRequirement varRequirement : transport.getVarbits()) {
            if (!varRequirement.check(varbitValues)) {
                return false;
            }
        }
        return true;
    }

    public boolean varPlayerChecks(Transport transport) {
        for (VarRequirement varRequirement : transport.getVarPlayers()) {
            if (!varRequirement.check(varPlayerValues)) {
                return false;
            }
        }
        return true;
    }

    private boolean useTransport(Transport transport) {
        // Master POH gate - if POH is disabled, reject all POH transports
        if (!usePoh) {
            int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
            int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
            if (ShortestPathPlugin.isInsidePoh(originX, originY)) {
                return false;
            }
        }

        final boolean isQuestLocked = transport.isQuestLocked();
        TransportType type = transport.getType();

        // Check if transport type is enabled in config
        if (!transportTypeConfig.isEnabled(type)) {
            return false;
        }

        // Handle POH variants for types that have them
        if (!checkPohVariant(transport, type)) {
            return false;
        }

        // Handle special cases for teleportation items and seasonal transports
        if (!checkTeleportationItemRules(transport, type)) {
            return false;
        }

        // Handle jewellery box tier filtering
        if (TransportType.TELEPORTATION_BOX.equals(type)) {
            if (!checkJewelleryBoxTier(transport)) {
                return false;
            }
        }

        if (!hasRequiredLevels(transport)) {
            return false;
        }

        if (isQuestLocked && !completedQuests(transport)) {
            return false;
        }

        if (!varbitChecks(transport)) {
            return false;
        }

        if (!varPlayerChecks(transport)) {
            return false;
        }

        return true;
    }

    /**
     * Checks POH-specific transport variants (fairy ring, spirit tree, obelisk inside POH).
     * Returns false if the transport is a POH variant and that variant is disabled.
     */
    private boolean checkPohVariant(Transport transport, TransportType type) {
        int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
        int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());

        if (!ShortestPathPlugin.isInsidePoh(originX, originY)) {
            return true; // Not a POH transport
        }

        // POH fairy ring
        if (TransportType.FAIRY_RING.equals(type)) {
            return usePohFairyRing;
        }
        // POH spirit tree
        if (TransportType.SPIRIT_TREE.equals(type)) {
            return usePohSpiritTree;
        }
        // POH obelisk
        if (TransportType.WILDERNESS_OBELISK.equals(type)) {
            return usePohObelisk;
        }

        return true;
    }

    /**
     * Checks teleportation item rules (consumable vs non-consumable, inventory settings).
     * Returns false if the transport should be filtered out based on teleportation item settings.
     */
    private boolean checkTeleportationItemRules(Transport transport, TransportType type) {
        if (!TransportType.TELEPORTATION_ITEM.equals(type) && !TransportType.SEASONAL_TRANSPORTS.equals(type)) {
            return true; // Not a teleportation item type
        }

        switch (transportTypeConfig.getTeleportationItemSetting()) {
            case ALL:
                return true;
            case ALL_NON_CONSUMABLE:
                return !transport.isConsumable();
            case UNLOCKED:
            case INVENTORY:
            case INVENTORY_AND_BANK:
                return true; // Will be checked later by hasRequiredItems
            case NONE:
                return false;
            case UNLOCKED_NON_CONSUMABLE:
            case INVENTORY_NON_CONSUMABLE:
            case INVENTORY_AND_BANK_NON_CONSUMABLE:
                return !transport.isConsumable();
        }
        return true;
    }

    /**
     * Checks if a TELEPORTATION_BOX transport should be used based on POH settings.
     * Handles jewellery box tiers and mounted items.
     */
    private boolean checkJewelleryBoxTier(Transport transport) {
        String objectInfo = transport.getObjectInfo();
        if (objectInfo == null) {
            return false;
        }

        // Check if this is a mounted item (glory, xeric's, digsite, mythical cape)
        boolean isMountedGlory = objectInfo.contains("Amulet of Glory");
        boolean isMountedItem = isMountedGlory ||
                objectInfo.contains("Xeric's Talisman") ||
                objectInfo.contains("Digsite") ||
                objectInfo.contains("Mythical cape");

        if (isMountedItem) {
            // If mounted glory and ornate jewellery box is enabled, skip the glory
            // because the ornate box already covers all 4 destinations with correct prefixes
            if (isMountedGlory && JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier)) {
                return false;
            }
            return usePohMountedItems;
        }

        // Filter jewellery boxes by tier
        if (JewelleryBoxTier.NONE.equals(pohJewelleryBoxTier)) {
            return false;
        }

        // Basic box (37492): destinations 1-9
        if (objectInfo.contains("Basic Jewellery Box 37492")) {
            return true; // All tiers include basic
        }

        // Fancy box (37501): destinations A-J
        if (objectInfo.contains("Fancy Jewellery Box 37501")) {
            return JewelleryBoxTier.FANCY.equals(pohJewelleryBoxTier) ||
                    JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier);
        }

        // Ornate box (37520): destinations K-R
        if (objectInfo.contains("Ornate Jewellery Box 37520")) {
            return JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier);
        }

        return false;
    }

    /**
     * Checks if the player has all the required skill levels for the transport
     */
    private boolean hasRequiredLevels(Transport transport) {
        int[] requiredLevels = transport.getSkillLevels();
        for (int i = 0; i < boostedSkillLevelsAndMore.length; i++) {
            int boostedLevel = boostedSkillLevelsAndMore[i];
            int requiredLevel = requiredLevels[i];
            if (boostedLevel < requiredLevel) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRequiredItems(Transport transport) {
        return hasRequiredItems(transport, true, true, true, true);
    }

    /**
     * Checks if the player has all the required equipment and inventory items for the transport
     */
    private boolean hasRequiredItems(Transport transport,
                                     boolean checkInventory,
                                     boolean checkEquipment,
                                     boolean checkBank,
                                     boolean checkRunePouch) {
        if (TransportType.TELEPORTATION_ITEM.equals(transport.getType()) ||
                TransportType.SEASONAL_TRANSPORTS.equals(transport.getType())) {
            switch (transportTypeConfig.getTeleportationItemSetting()) {
                case ALL:
                case ALL_NON_CONSUMABLE:
                case UNLOCKED:
                case UNLOCKED_NON_CONSUMABLE:
                    return true;
                case NONE:
                    return false;
                default:
                    break;
            }
        }

        // Fairy rings require Dramen/Lunar staff unless Lumbridge Elite diary is complete
        if (TransportType.FAIRY_RING.equals(transport.getType())) {
            // Use cached varbit value to support being called from pathfinder thread
            int lumbridgeDiaryComplete = varbitValues.getOrDefault(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0);
            if (lumbridgeDiaryComplete != 1) {
                if (!hasRequiredItems(DRAMEN_STAFF, checkInventory, checkEquipment, checkBank, checkRunePouch)) {
                    return false;
                }
            }
        }

        return hasRequiredItems(transport.getItemRequirements(),
                checkInventory, checkEquipment, checkBank, checkRunePouch);
    }

    private boolean hasRequiredItems(TransportItems transportItems) {
        return hasRequiredItems(transportItems, true, true, true, true);
    }

    /**
     * Checks if the player has all the required equipment and inventory items for the transport
     */
    private boolean hasRequiredItems(TransportItems transportItems,
                                     boolean checkInventory,
                                     boolean checkEquipment,
                                     boolean checkBank,
                                     boolean checkRunePouch) {
        if (transportItems == null) {
            return true;
        }
        itemsAndQuantities.clear();

        if (checkInventory) {
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            if (inventory != null) {
                for (Item item : inventory.getItems()) {
                    if (item.getId() >= 0 && item.getQuantity() > 0) {
                        itemsAndQuantities.put(item.getId(), item.getQuantity());
                    }
                }
            }
        }

        if (checkEquipment) {
            ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
            if (equipment != null) {
                for (Item item : equipment.getItems()) {
                    if (item.getId() >= 0 && item.getQuantity() > 0) {
                        itemsAndQuantities.put(item.getId(), item.getQuantity());
                    }
                }
            }
        }

        if (checkBank) {
            TeleportationItem teleportSetting = transportTypeConfig.getTeleportationItemSetting();
            // Check bank items only when bankVisited is true AND either:
            // 1. includeBankPath is enabled, OR
            // 2. teleport setting allows bank items
            if (bank != null && bankVisited
                    && (includeBankPath
                    || TeleportationItem.INVENTORY_AND_BANK.equals(teleportSetting)
                    || TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE.equals(teleportSetting))) {
                for (Item item : bank.getItems()) {
                    if (item.getId() >= 0 && item.getQuantity() > 0) {
                        itemsAndQuantities.put(item.getId(), item.getQuantity());
                    }
                }
            }
        }

        if (checkRunePouch) {
            if (RUNE_POUCHES.stream().anyMatch(runePouch -> itemsAndQuantities.containsKey(runePouch))) {
                EnumComposition runePouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
                for (int i = 0; i < RUNE_POUCH_RUNE_VARBITS.length; i++) {
                    int runeEnumId = client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]);
                    int runeId = runeEnumId > 0 ? runePouchEnum.getIntValue(runeEnumId) : 0;
                    int runeAmount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
                    if (runeId > 0 && runeAmount > 0) {
                        itemsAndQuantities.put(runeId, runeAmount);
                    }
                }
            }
        }

        boolean usingStaff = false;
        boolean usingOffhand = false;
        for (int i = 0; i < transportItems.getItems().length; i++) {
            boolean missing = true;
            if (transportItems.getItems()[i] != null) {
                for (int itemId : transportItems.getItems()[i]) {
                    int quantity = itemsAndQuantities.getOrDefault(itemId, 0);
                    int requiredQuantity = transportItems.getQuantities()[i];
                    if (requiredQuantity > 0 && quantity >= requiredQuantity || requiredQuantity == 0 && quantity == 0) {
                        if (CURRENCIES.contains(itemId) && requiredQuantity > currencyThreshold) {
                            return false;
                        }
                        missing = false;
                        break;
                    }
                }
            }
            if (missing && !usingStaff && transportItems.getStaves()[i] != null) {
                for (int itemId : transportItems.getStaves()[i]) {
                    int quantity = itemsAndQuantities.getOrDefault(itemId, 0);
                    int requiredQuantity = transportItems.getQuantities()[i];
                    if (requiredQuantity > 0 && quantity >= 1 || requiredQuantity == 0 && quantity == 0) {
                        usingStaff = true;
                        missing = false;
                        break;
                    }
                }
            }
            if (missing && !usingOffhand && transportItems.getOffhands()[i] != null) {
                for (int itemId : transportItems.getOffhands()[i]) {
                    int quantity = itemsAndQuantities.getOrDefault(itemId, 0);
                    int requiredQuantity = transportItems.getQuantities()[i];
                    if (requiredQuantity > 0 && quantity >= 1 || requiredQuantity == 0 && quantity == 0) {
                        usingOffhand = true;
                        missing = false;
                        break;
                    }
                }
            }
            if (missing) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if required items are available in inventory, equipment, or bank.
     * Used for transport TYPE eligibility when includeBankPath is enabled.
     */
    private boolean hasItemsInInventoryEquipmentOrBank(TransportItems transportItems) {
        if (transportItems == null) {
            return true;
        }
        // Check inventory and equipment first
        if (hasRequiredItems(transportItems, true, true, false, false)) {
            return true;
        }
        // If includeBankPath is enabled, also check if item is in bank
        if (includeBankPath && bank != null) {
            for (int[] itemGroup : transportItems.getStaves()) {
                if (itemGroup != null) {
                    for (int itemId : itemGroup) {
                        for (Item bankItem : bank.getItems()) {
                            if (bankItem.getId() == itemId && bankItem.getQuantity() > 0) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Calculates the combat level of the player
     */
    private int getCombatLevel() {
        int attack = client.getRealSkillLevel(Skill.ATTACK);
        int strength = client.getRealSkillLevel(Skill.STRENGTH);
        int defence = client.getRealSkillLevel(Skill.DEFENCE);
        int hitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
        int magic = client.getRealSkillLevel(Skill.MAGIC);
        int ranged = client.getRealSkillLevel(Skill.RANGED);
        int prayer = client.getRealSkillLevel(Skill.PRAYER);
        double base = 0.25 * (defence + hitpoints + (prayer) / 2);
        double melee = (13 * (attack + strength)) / 40.0;
        double range = (13 * ((3 * ranged) / 2)) / 40.0;
        double mage = (13 * ((3 * magic) / 2)) / 40.0;
        int combatLevel = (int) Math.floor(base + Math.max(Math.max(melee, range), Math.max(melee, mage)));
        return combatLevel;
    }
}

