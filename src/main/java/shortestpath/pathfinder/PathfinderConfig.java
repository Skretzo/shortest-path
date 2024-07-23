package shortestpath.pathfinder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import shortestpath.TeleportationItem;
import shortestpath.ShortestPathConfig;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.Transport;
import shortestpath.TransportType;
import shortestpath.TransportVarbit;
import shortestpath.WorldPointUtil;
import static shortestpath.TransportType.AGILITY_SHORTCUT;
import static shortestpath.TransportType.GRAPPLE_SHORTCUT;
import static shortestpath.TransportType.BOAT;
import static shortestpath.TransportType.CANOE;
import static shortestpath.TransportType.CHARTER_SHIP;
import static shortestpath.TransportType.SHIP;
import static shortestpath.TransportType.FAIRY_RING;
import static shortestpath.TransportType.GNOME_GLIDER;
import static shortestpath.TransportType.SPIRIT_TREE;
import static shortestpath.TransportType.TELEPORTATION_LEVER;
import static shortestpath.TransportType.TELEPORTATION_PORTAL;
import static shortestpath.TransportType.TELEPORTATION_ITEM;
import static shortestpath.TransportType.TELEPORTATION_SPELL;

public class PathfinderConfig {
    private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3523, 448, 448, 0);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_20 = new WorldArea(2944, 3680, 448, 448, 0);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_30 = new WorldArea(2944, 3760, 448, 448, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND = new WorldArea(2944, 9918, 320, 442, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_20 = new WorldArea(2944, 10075, 320, 442, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_30 = new WorldArea(2944, 10155, 320, 442, 0);

    private final SplitFlagMap mapData;
    private final ThreadLocal<CollisionMap> map;
    /** All transports by origin {@link WorldPoint}. The null key is used for transports centered on the player. */
    private final Map<WorldPoint, Set<Transport>> allTransports;
    @Getter
    private Map<WorldPoint, Set<Transport>> transports;

    // Copy of transports with packed positions for the hotpath; lists are not copied and are the same reference in both maps
    @Getter
    private PrimitiveIntHashMap<Set<Transport>> transportsPacked;

    private final Client client;
    private final ShortestPathConfig config;

    @Getter
    private long calculationCutoffMillis;
    @Getter
    private boolean avoidWilderness;
    private boolean useAgilityShortcuts,
        useGrappleShortcuts,
        useBoats,
        useCanoes,
        useCharterShips,
        useShips,
        useFairyRings,
        useGnomeGliders,
        useSpiritTrees,
        useTeleportationLevers,
        useTeleportationPortals,
        useTeleportationSpells;
    private TeleportationItem useTeleportationItems;
    private int agilityLevel;
    private int rangedLevel;
    private int strengthLevel;
    private int prayerLevel;
    private int woodcuttingLevel;
    private Map<Quest, QuestState> questStates = new HashMap<>();
    private Map<Integer, Integer> varbitValues = new HashMap<>();

    public PathfinderConfig(SplitFlagMap mapData, Map<WorldPoint, Set<Transport>> transports,
                            Client client, ShortestPathConfig config) {
        this.mapData = mapData;
        this.map = ThreadLocal.withInitial(() -> new CollisionMap(this.mapData));
        this.allTransports = transports;
        this.transports = new HashMap<>(allTransports.size());
        this.transportsPacked = new PrimitiveIntHashMap<>(allTransports.size());
        this.client = client;
        this.config = config;
    }

    public CollisionMap getMap() {
        return map.get();
    }

    public void refresh() {
        calculationCutoffMillis = config.calculationCutoff() * Constants.GAME_TICK_LENGTH;
        avoidWilderness = config.avoidWilderness();
        useAgilityShortcuts = config.useAgilityShortcuts();
        useGrappleShortcuts = config.useGrappleShortcuts();
        useBoats = config.useBoats();
        useCanoes = config.useCanoes();
        useCharterShips = config.useCharterShips();
        useShips = config.useShips();
        useFairyRings = config.useFairyRings();
        useSpiritTrees = config.useSpiritTrees();
        useGnomeGliders = config.useGnomeGliders();
        useTeleportationLevers = config.useTeleportationLevers();
        useTeleportationPortals = config.useTeleportationPortals();
        useTeleportationItems = config.useTeleportationItems();
        useTeleportationSpells = config.useTeleportationSpells();

        if (GameState.LOGGED_IN.equals(client.getGameState())) {
            agilityLevel = client.getBoostedSkillLevel(Skill.AGILITY);
            rangedLevel = client.getBoostedSkillLevel(Skill.RANGED);
            strengthLevel = client.getBoostedSkillLevel(Skill.STRENGTH);
            prayerLevel = client.getBoostedSkillLevel(Skill.PRAYER);
            woodcuttingLevel = client.getBoostedSkillLevel(Skill.WOODCUTTING);

            refreshTransportData();
        }
    }

    /** Specialized method for only updating player-held item and spell transports */
    public void refreshPlayerTransportData(@Nonnull WorldPoint location, int wildernessLevel) {
        //TODO: This just checks the player's inventory and equipment. Later, bank items could be included, but the player will probably need to configure which items are considered
        List<Integer> inventoryItems = Arrays.stream(new InventoryID[]{InventoryID.INVENTORY, InventoryID.EQUIPMENT})
            .map(client::getItemContainer)
            .filter(Objects::nonNull)
            .map(ItemContainer::getItems)
            .flatMap(Arrays::stream)
            .map(Item::getId)
            .filter(itemId -> itemId != -1)
            .collect(Collectors.toList());

        boolean skipInventoryCheck = TeleportationItem.ALL.equals(useTeleportationItems);

        Set<Transport> playerItemTransports = allTransports.getOrDefault(null, new HashSet<>());
        Set<Transport> usableTransports = new HashSet<>(playerItemTransports.size());
        for (Transport transport : playerItemTransports) {
            boolean itemInInventory = skipInventoryCheck ||
                (!transport.getItemIdRequirements().isEmpty() && transport.getItemIdRequirements().stream().anyMatch(requirements -> requirements.stream().allMatch(inventoryItems::contains)));
            // questStates and varbits cannot be checked in a non-main thread, so item transports' quests and varbits are cached in `refreshTransportData`
            if (useTransport(transport) && itemInInventory && transport.getMaxWildernessLevel() >= wildernessLevel) {
                usableTransports.add(transport);
            }
        }

        if (!usableTransports.isEmpty()) {
            transports.put(location, usableTransports);
            transportsPacked.put(WorldPointUtil.packWorldPoint(location), usableTransports);
        }
    }

    private void refreshTransportData() {
        if (!Thread.currentThread().equals(client.getClientThread())) {
            return; // Has to run on the client thread; data will be refreshed when path finding commences
        }

        useFairyRings &= !QuestState.NOT_STARTED.equals(getQuestState(Quest.FAIRYTALE_II__CURE_A_QUEEN));
        useGnomeGliders &= QuestState.FINISHED.equals(getQuestState(Quest.THE_GRAND_TREE));
        useSpiritTrees &= QuestState.FINISHED.equals(getQuestState(Quest.TREE_GNOME_VILLAGE));

        transports.clear();
        transportsPacked.clear();
        for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet()) {
            Set<Transport> usableTransports = new HashSet<>(entry.getValue().size());
            for (Transport transport : entry.getValue()) {
                for (Quest quest : transport.getQuests()) {
                    try {
                        questStates.put(quest, getQuestState(quest));
                    } catch (NullPointerException ignored) {
                    }
                }

                for (TransportVarbit varbitCheck : transport.getVarbits()) {
                    varbitValues.put(varbitCheck.getVarbitId(), client.getVarbitValue(varbitCheck.getVarbitId()));
                }

                if (entry.getKey() == null) {
                    // null keys are for player-centered transports. They are added in refreshPlayerTransportData at pathfinding time.
                    // still need to get quest states for these transports while we're in the client thread though
                    continue;
                }

                if (useTransport(transport)) {
                    usableTransports.add(transport);
                }
            }

            WorldPoint point = entry.getKey();

            if (point != null && !usableTransports.isEmpty()) {
                transports.put(point, usableTransports);
                transportsPacked.put(WorldPointUtil.packWorldPoint(point), usableTransports);
            }
        }
    }

    public static boolean isInWilderness(WorldPoint p) {
        return WILDERNESS_ABOVE_GROUND.distanceTo(p) == 0 || WILDERNESS_UNDERGROUND.distanceTo(p) == 0;
    }

    public static boolean isInWilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND) == 0
            || WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND) == 0;
    }

    public boolean avoidWilderness(int packedPosition, int packedNeightborPosition, boolean targetInWilderness) {
        return avoidWilderness && !targetInWilderness
            && !isInWilderness(packedPosition) && isInWilderness(packedNeightborPosition);
    }

    public boolean isInLevel20Wilderness(int packedPoint) {
        return WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_20) == 0
            || WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_20) == 0;
    }

    public boolean isInLevel30Wilderness(int packedPoint){
        return WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND_LEVEL_30) == 0
            || WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND_LEVEL_30) == 0;

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

    private boolean varbitChecks(Transport transport) {
        for (TransportVarbit varbitCheck : transport.getVarbits()) {
            if (!varbitValues.get(varbitCheck.getVarbitId()).equals(varbitCheck.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean useTransport(Transport transport) {
        final int transportAgilityLevel = transport.getRequiredLevel(Skill.AGILITY);
        final int transportRangedLevel = transport.getRequiredLevel(Skill.RANGED);
        final int transportStrengthLevel = transport.getRequiredLevel(Skill.STRENGTH);
        final int transportPrayerLevel = transport.getRequiredLevel(Skill.PRAYER);
        final int transportWoodcuttingLevel = transport.getRequiredLevel(Skill.WOODCUTTING);

        final boolean isPrayerLocked = transportPrayerLevel > 1;
        final boolean isQuestLocked = transport.isQuestLocked();

        TransportType type = transport.getType();

        if (AGILITY_SHORTCUT.equals(type)
            && (!useAgilityShortcuts || agilityLevel < transportAgilityLevel)) {
            return false;
        } else if (GRAPPLE_SHORTCUT.equals(transport.getType())
            && (!useGrappleShortcuts
            || rangedLevel < transportRangedLevel
            || strengthLevel < transportStrengthLevel)) {
            return false;
        } else if (BOAT.equals(transport.getType()) && !useBoats) {
            return false;
        } else if (CANOE.equals(transport.getType())
            && (!useCanoes || woodcuttingLevel < transportWoodcuttingLevel)) {
            return false;
        } else if (CHARTER_SHIP.equals(transport.getType()) && !useCharterShips) {
            return false;
        } else if (SHIP.equals(transport.getType()) && !useShips) {
            return false;
        } else if (FAIRY_RING.equals(transport.getType()) && !useFairyRings) {
            return false;
        } else if (GNOME_GLIDER.equals(transport.getType()) && !useGnomeGliders) {
            return false;
        } else if (SPIRIT_TREE.equals(transport.getType()) && !useSpiritTrees) {
            return false;
        } else if (TELEPORTATION_LEVER.equals(transport.getType()) && !useTeleportationLevers) {
            return false;
        } else if (TELEPORTATION_PORTAL.equals(transport.getType()) && !useTeleportationPortals) {
            return false;
        } else if (TELEPORTATION_ITEM.equals(transport.getType())) {
            switch (useTeleportationItems) {
                case NONE:
                    return false;
                case INVENTORY_NON_CONSUMABLE:
                case ALL_NON_CONSUMABLE:
                    if (transport.isConsumable()) {
                        return false;
                    }
            }
        } else if (TELEPORTATION_SPELL.equals(transport.getType()) && !useTeleportationSpells) {
            return false;
        }

        if (isPrayerLocked && prayerLevel < transportPrayerLevel) {
            return false;
        }

        if (isQuestLocked && !completedQuests(transport)) {
            return false;
        }

        if (!varbitChecks(transport)) {
            return false;
        }

        return true;
    }
}
