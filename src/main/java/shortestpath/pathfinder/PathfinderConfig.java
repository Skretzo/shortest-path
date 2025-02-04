package shortestpath.pathfinder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import shortestpath.TeleportationItem;
import shortestpath.ShortestPathConfig;
import shortestpath.ShortestPathPlugin;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.Transport;
import shortestpath.TransportType;
import shortestpath.TransportVarbit;
import shortestpath.TransportVarPlayer;
import shortestpath.WorldPointUtil;
import static shortestpath.TransportType.AGILITY_SHORTCUT;
import static shortestpath.TransportType.GRAPPLE_SHORTCUT;
import static shortestpath.TransportType.BOAT;
import static shortestpath.TransportType.CANOE;
import static shortestpath.TransportType.CHARTER_SHIP;
import static shortestpath.TransportType.SHIP;
import static shortestpath.TransportType.FAIRY_RING;
import static shortestpath.TransportType.GNOME_GLIDER;
import static shortestpath.TransportType.MINECART;
import static shortestpath.TransportType.QUETZAL;
import static shortestpath.TransportType.SPIRIT_TREE;
import static shortestpath.TransportType.TELEPORTATION_LEVER;
import static shortestpath.TransportType.TELEPORTATION_PORTAL;
import static shortestpath.TransportType.TELEPORTATION_ITEM;
import static shortestpath.TransportType.TELEPORTATION_SPELL;
import static shortestpath.TransportType.WILDERNESS_OBELISK;

public class PathfinderConfig {
    private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3523, 448, 448, 0);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_20 = new WorldArea(2944, 3680, 448, 448, 0);
    private static final WorldArea WILDERNESS_ABOVE_GROUND_LEVEL_30 = new WorldArea(2944, 3760, 448, 448, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND = new WorldArea(2944, 9918, 320, 442, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_20 = new WorldArea(2944, 10075, 320, 442, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND_LEVEL_30 = new WorldArea(2944, 10155, 320, 442, 0);
    private static final List<Integer> RUNE_POUCHES = Arrays.asList(
        ItemID.RUNE_POUCH, ItemID.RUNE_POUCH_L,
        ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_L
    );
    private static final int[] RUNE_POUCH_RUNE_VARBITS = {
        Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3, Varbits.RUNE_POUCH_RUNE4,
        Varbits.RUNE_POUCH_RUNE5, Varbits.RUNE_POUCH_RUNE6
	};

    private final SplitFlagMap mapData;
    private final ThreadLocal<CollisionMap> map;
    /** All transports by origin. The WorldPointUtil.UNDEFINED key is used for transports centered on the player. */
    private final Map<Integer, Set<Transport>> allTransports;
    private final Set<Transport> usableTeleports;

    @Getter
    private Map<Integer, Set<Transport>> transports;
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
        useMinecarts,
        useQuetzals,
        useSpiritTrees,
        useTeleportationLevers,
        useTeleportationPortals,
        useTeleportationSpells,
        useWildernessObelisks;
    private TeleportationItem useTeleportationItems;
    private final int[] boostedLevels = new int[Skill.values().length];
    private Map<Quest, QuestState> questStates = new HashMap<>();
    private Map<Integer, Integer> varbitValues = new HashMap<>();
    private Map<Integer, Integer> varPlayerValues = new HashMap<>();

    public PathfinderConfig(SplitFlagMap mapData, Map<Integer, Set<Transport>> transports,
                            Client client, ShortestPathConfig config) {
        this.mapData = mapData;
        this.map = ThreadLocal.withInitial(() -> new CollisionMap(this.mapData));
        this.allTransports = transports;
        this.usableTeleports = new HashSet<>(allTransports.size() / 20);
        this.transports = new HashMap<>(allTransports.size() / 2);
        this.transportsPacked = new PrimitiveIntHashMap<>(allTransports.size() / 2);
        this.client = client;
        this.config = config;
    }

    public CollisionMap getMap() {
        return map.get();
    }

    public void refresh() {
        calculationCutoffMillis = config.calculationCutoff() * Constants.GAME_TICK_LENGTH;
        avoidWilderness = ShortestPathPlugin.override("avoidWilderness", config.avoidWilderness());
        useAgilityShortcuts = ShortestPathPlugin.override("useAgilityShortcuts", config.useAgilityShortcuts());
        useGrappleShortcuts = ShortestPathPlugin.override("useGrappleShortcuts", config.useGrappleShortcuts());
        useBoats = ShortestPathPlugin.override("useBoats", config.useBoats());
        useCanoes = ShortestPathPlugin.override("useCanoes", config.useCanoes());
        useCharterShips = ShortestPathPlugin.override("useCharterShips", config.useCharterShips());
        useShips = ShortestPathPlugin.override("useShips", config.useShips());
        useFairyRings = ShortestPathPlugin.override("useFairyRings", config.useFairyRings());
        useGnomeGliders = ShortestPathPlugin.override("useGnomeGliders", config.useGnomeGliders());
        useMinecarts = ShortestPathPlugin.override("useMinecarts", config.useMinecarts());
        useQuetzals = ShortestPathPlugin.override("useQuetzals", config.useQuetzals());
        useSpiritTrees = ShortestPathPlugin.override("useSpiritTrees", config.useSpiritTrees());
        useTeleportationItems = ShortestPathPlugin.override("useTeleportationItems", config.useTeleportationItems());
        useTeleportationLevers = ShortestPathPlugin.override("useTeleportationLevers", config.useTeleportationLevers());
        useTeleportationPortals = ShortestPathPlugin.override("useTeleportationPortals", config.useTeleportationPortals());
        useTeleportationSpells = ShortestPathPlugin.override("useTeleportationSpells", config.useTeleportationSpells());
        useWildernessObelisks = ShortestPathPlugin.override("useWildernessObelisks", config.useWildernessObelisks());

        if (GameState.LOGGED_IN.equals(client.getGameState())) {
            for (int i = 0; i < Skill.values().length; i++) {
                boostedLevels[i] = client.getBoostedSkillLevel(Skill.values()[i]);
            }

            refreshTransports();
        }
    }

    /** Specialized method for only updating player-held item and spell transports */
    public void refreshTeleports(int packedLocation, int wildernessLevel) {
        Set<Transport> usableWildyTeleports = new HashSet<>(usableTeleports.size());

        for (Transport teleport : usableTeleports) {
            if (wildernessLevel <= teleport.getMaxWildernessLevel()) {
                usableWildyTeleports.add(teleport);
            }
        }

        if (!usableWildyTeleports.isEmpty()) {
            transports.put(packedLocation, usableWildyTeleports);
            transportsPacked.put(packedLocation, usableWildyTeleports);
        }
    }

    private void refreshTransports() {
        if (!Thread.currentThread().equals(client.getClientThread())) {
            return; // Has to run on the client thread; data will be refreshed when path finding commences
        }

        useFairyRings &= !QuestState.NOT_STARTED.equals(getQuestState(Quest.FAIRYTALE_II__CURE_A_QUEEN));
        useGnomeGliders &= QuestState.FINISHED.equals(getQuestState(Quest.THE_GRAND_TREE));
        useSpiritTrees &= QuestState.FINISHED.equals(getQuestState(Quest.TREE_GNOME_VILLAGE));

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

                for (TransportVarbit varbitRequirement : transport.getVarbits()) {
                    varbitValues.put(varbitRequirement.getId(), client.getVarbitValue(varbitRequirement.getId()));
                }
                for (TransportVarPlayer varPlayerRequirement : transport.getVarPlayers()) {
                    varPlayerValues.put(varPlayerRequirement.getId(), client.getVarpValue(varPlayerRequirement.getId()));
                }

                if (point == WorldPointUtil.UNDEFINED && hasRequiredItems(transport) && useTransport(transport)) {
                    usableTeleports.add(transport);
                } else if (useTransport(transport)) {
                    usableTransports.add(transport);
                }
            }

            if (point != WorldPointUtil.UNDEFINED && !usableTransports.isEmpty()) {
                transports.put(point, usableTransports);
                transportsPacked.put(point, usableTransports);
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

    public static boolean isInWilderness(Set<Integer> packedPoints) {
        for (int packedPoint : packedPoints) {
            if (WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_ABOVE_GROUND) == 0 ||
                WorldPointUtil.distanceToArea(packedPoint, WILDERNESS_UNDERGROUND) == 0) {
                return true;
            }
        }
        return false;
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

    public boolean varbitChecks(Transport transport) {
        for (TransportVarbit varbitRequirement : transport.getVarbits()) {
            if (!varbitRequirement.check(varbitValues)) {
                return false;
            }
        }
        return true;
    }

    public boolean varPlayerChecks(Transport transport) {
        for (TransportVarPlayer varPlayerRequirement : transport.getVarPlayers()) {
            if (!varPlayerRequirement.check(varPlayerValues)) {
                return false;
            }
        }
        return true;
    }

    private boolean useTransport(Transport transport) {
        final boolean isQuestLocked = transport.isQuestLocked();

        if (!hasRequiredLevels(transport)) {
            return false;
        }

        TransportType type = transport.getType();

        if (AGILITY_SHORTCUT.equals(type) && !useAgilityShortcuts) {
            return false;
        } else if (GRAPPLE_SHORTCUT.equals(type) && !useGrappleShortcuts) {
            return false;
        } else if (BOAT.equals(type) && !useBoats) {
            return false;
        } else if (CANOE.equals(type) && !useCanoes) {
            return false;
        } else if (CHARTER_SHIP.equals(type) && !useCharterShips) {
            return false;
        } else if (SHIP.equals(type) && !useShips) {
            return false;
        } else if (FAIRY_RING.equals(type) && !useFairyRings) {
            return false;
        } else if (GNOME_GLIDER.equals(type) && !useGnomeGliders) {
            return false;
        } else if (MINECART.equals(type) && !useMinecarts) {
            return false;
        } else if (QUETZAL.equals(type) && !useQuetzals) { 
            return false;
        } else if (SPIRIT_TREE.equals(type) && !useSpiritTrees) {
            return false;
        } else if (TELEPORTATION_ITEM.equals(type)) {
            switch (useTeleportationItems) {
                case ALL:
                case INVENTORY:
                    break;
                case NONE:
                    return false;
                case INVENTORY_NON_CONSUMABLE:
                case ALL_NON_CONSUMABLE:
                    if (transport.isConsumable()) {
                        return false;
                    }
                    break;
            }
        } else if (TELEPORTATION_LEVER.equals(type) && !useTeleportationLevers) {
            return false;
        } else if (TELEPORTATION_PORTAL.equals(type) && !useTeleportationPortals) {
            return false;
        } else if (TELEPORTATION_SPELL.equals(type) && !useTeleportationSpells) {
            return false;
        } else if (WILDERNESS_OBELISK.equals(type) && !useWildernessObelisks) {
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

    /** Checks if the player has all the required skill levels for the transport */
    private boolean hasRequiredLevels(Transport transport) {
        int[] requiredLevels = transport.getSkillLevels();
        for (int i = 0; i < boostedLevels.length; i++) {
            int boostedLevel = boostedLevels[i];
            int requiredLevel = requiredLevels[i];
            if (boostedLevel < requiredLevel) {
                return false;
            }
        }
        return true;
    }

    /** Checks if the player has all the required equipment and inventory items for the transport */
    private boolean hasRequiredItems(Transport transport) {
        if ((TeleportationItem.ALL.equals(useTeleportationItems) ||
            TeleportationItem.ALL_NON_CONSUMABLE.equals(useTeleportationItems)) &&
            TransportType.TELEPORTATION_ITEM.equals(transport.getType())) {
            return true;
        }
        if (TeleportationItem.NONE.equals(useTeleportationItems) &&
            TransportType.TELEPORTATION_ITEM.equals(transport.getType())) {
            return false;
        }
        List<Integer> inventoryItems = Arrays.stream(new InventoryID[]{InventoryID.INVENTORY, InventoryID.EQUIPMENT})
            .map(client::getItemContainer)
            .filter(Objects::nonNull)
            .map(ItemContainer::getItems)
            .flatMap(Arrays::stream)
            .map(Item::getId)
            .filter(itemId -> itemId != -1)
            .collect(Collectors.toList());
        if (RUNE_POUCHES.stream().anyMatch(runePouch -> inventoryItems.contains(runePouch))) {
            EnumComposition runePouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
            for (int i = 0; i < RUNE_POUCH_RUNE_VARBITS.length; i++) {
                int runeEnumId = client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]);
                if (runeEnumId > 0) {
                    inventoryItems.add(runePouchEnum.getIntValue(runeEnumId));
                }
            }
        }
        // TODO: this does not check quantity
        return transport.getItemIdRequirements().stream().anyMatch(requirements -> requirements.stream().allMatch(inventoryItems::contains));
    }
}
