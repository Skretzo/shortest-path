package shortestpath.transport;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.ItemVariations;
import shortestpath.Util;
import shortestpath.WorldPointUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * This class represents a travel point between two WorldPoints.
 */
@Slf4j
public class Transport {
    public static final int UNDEFINED_ORIGIN = WorldPointUtil.UNDEFINED;
    public static final int UNDEFINED_DESTINATION = WorldPointUtil.UNDEFINED;
    /** A location placeholder different from null to use for permutation transports */
    public static final int LOCATION_PERMUTATION = WorldPointUtil.packWorldPoint(-1, -1, 1);
    private static final String DELIM_SPACE = " ";
    private static final String DELIM_MULTI = ";";
    private static final String DELIM_STATE = "=";
    private static final String DELIM_AND = "&";
    private static final String DELIM_OR = "|";

    /** The starting point of this transport */
    @Getter
    private int origin = UNDEFINED_ORIGIN;

    /** The ending point of this transport */
    @Getter
    private int destination = UNDEFINED_DESTINATION;

    /** The skill levels, total level, combat level and quest points required to use this transport */
    @Getter
    private final int[] skillLevels = new int[Skill.values().length + 3];

    /** The quests required to use this transport */
    @Getter
    private Set<Quest> quests = new HashSet<>();

    /** The item requirements to use this transport */
    @Getter
    private TransportItems itemRequirements;

    /** The type of transport */
    @Getter
    private TransportType type;

    /** The travel waiting time in number of ticks */
    @Getter
    private int duration;

    /** Info to display for this transport. For spirit trees, fairy rings,
     * and others, this is the destination option to pick. */
    @Getter
    private String displayInfo = null;

    /** If this is an item transport, this tracks if it is consumable (as opposed to having infinite uses) */
    @Getter
    private boolean isConsumable = false;

    /** The maximum wilderness level that the transport can be used in */
    @Getter
    private int maxWildernessLevel = -1;

    /** Object information for this transport */
    @Getter
    private String objectInfo = null;

    /** Any varbits to check for the transport to be valid. All must pass for a transport to be valid */
    @Getter
    private final Set<TransportVarbit> varbits = new HashSet<>();

    /** Any varplayers to check for the transport to be valid. All must pass for a transport to be valid */
    @Getter
    private final Set<TransportVarPlayer> varPlayers = new HashSet<>();

    /** Creates a new transport from an origin-only transport
     * and a destination-only transport, and merges requirements */
    Transport(Transport origin, Transport destination) {
        this.origin = origin.origin;
        this.destination = destination.destination;

        for (int i = 0; i < skillLevels.length; i++) {
            this.skillLevels[i] = Math.max(
                origin.skillLevels[i],
                destination.skillLevels[i]);
        }

        this.quests.addAll(origin.quests);
        this.quests.addAll(destination.quests);

        this.itemRequirements = mergeItemRequirements(origin.itemRequirements, destination.itemRequirements);

        this.type = origin.type;

        this.duration = Math.max(
            origin.duration,
            destination.duration);

        this.displayInfo = destination.displayInfo;

        this.isConsumable |= origin.isConsumable;
        this.isConsumable |= destination.isConsumable;

        this.maxWildernessLevel = Math.max(
            origin.maxWildernessLevel,
            destination.maxWildernessLevel);

        this.objectInfo = origin.objectInfo;

        this.varbits.addAll(origin.varbits);
        this.varbits.addAll(destination.varbits);

        this.varPlayers.addAll(origin.varPlayers);
        this.varPlayers.addAll(destination.varPlayers);
    }

    Transport(Map<String, String> fieldMap, TransportType transportType) {
        String value;

        // If the origin field is null the transport is a teleportation item or spell
        // If the origin field has 3 elements it is a coordinate of a transport
        // Otherwise it is a transport that needs to be expanded into all permutations (e.g. fairy ring)
        if ((value = fieldMap.get("Origin")) != null) {
            String[] originArray = value.split(DELIM_SPACE);
            origin = originArray.length == 3 ? WorldPointUtil.packWorldPoint(
                Integer.parseInt(originArray[0]),
                Integer.parseInt(originArray[1]),
                Integer.parseInt(originArray[2])) : LOCATION_PERMUTATION;
        }

        if ((value = fieldMap.get("Destination")) != null) {
            String[] destinationArray = value.split(DELIM_SPACE);
            destination = destinationArray.length == 3 ? WorldPointUtil.packWorldPoint(
                Integer.parseInt(destinationArray[0]),
                Integer.parseInt(destinationArray[1]),
                Integer.parseInt(destinationArray[2])) : LOCATION_PERMUTATION;
        }

        if ((value = fieldMap.get("Skills")) != null) {
            String[] skillRequirements = value.split(DELIM_MULTI);

            try {
                for (String requirement : skillRequirements) {
                    if (requirement.isEmpty()) {
                        continue;
                    }
                    String[] levelAndSkill = requirement.split(DELIM_SPACE);
                    assert levelAndSkill.length == 2 : "Invalid level and skill: '" + requirement + "'";

                    int level = Integer.parseInt(levelAndSkill[0]);
                    String skillName = levelAndSkill[1] == null ? "" : levelAndSkill[1];

                    Skill[] skills = Skill.values();
                    int i = 0;
                    for (; i < skills.length; i++) {
                        if (skills[i].getName().equals(skillName)) {
                            skillLevels[i] = level;
                        }
                    }
                    if (skillName.toLowerCase().startsWith("total")) {
                        skillLevels[i] = level;
                    }
                    i++;
                    if (skillName.toLowerCase().startsWith("combat")) {
                        skillLevels[i] = level;
                    }
                    i++;
                    if (skillName.toLowerCase().startsWith("quest")) {
                        skillLevels[i] = level;
                    }
                }
            } catch (NumberFormatException e) {
                log.error("Invalid level and skill: " + value);
            }
        }

        if ((value = fieldMap.get("Items")) != null && !value.isEmpty()) {
            value = value.replace(DELIM_SPACE, "");
            value = value.replace(DELIM_AND + DELIM_AND, DELIM_AND);
            value = value.replace(DELIM_OR + DELIM_OR, DELIM_OR);
            value = value.toUpperCase();
            String[] itemVariationAndQuantityList = value.split(DELIM_AND);
            try {
                int n = itemVariationAndQuantityList.length;
                int[][] items = new int[n][];
                int[][] staves = new int[n][];
                int[][] offhands = new int[n][];
                int[] quantities = new int[n];
                for (int i = 0; i < n; i++) {
                    int maxQuantity = -1;
                    String[] itemVariationsAndQuantities = itemVariationAndQuantityList[i].split("\\" + DELIM_OR);
                    int[][] multipleItems = new int[itemVariationsAndQuantities.length][];
                    int[][] multipleStaves = new int[itemVariationsAndQuantities.length][];
                    int[][] multipleOffhands = new int[itemVariationsAndQuantities.length][];
                    for (int k = 0; k < itemVariationsAndQuantities.length; k++) {
                        String[] itemVariationAndQuantity = itemVariationsAndQuantities[k].split(DELIM_STATE);
                        if (itemVariationAndQuantity.length == 2) {
                            ItemVariations itemVariations = ItemVariations.fromName(itemVariationAndQuantity[0]);
                            multipleItems[k] = itemVariations == null ? new int[]{Integer.parseInt(itemVariationAndQuantity[0])} : itemVariations.getIds();
                            multipleStaves[k] = ItemVariations.staves(itemVariations);
                            multipleOffhands[k] = ItemVariations.offhands(itemVariations);
                            maxQuantity = Math.max(maxQuantity, Integer.parseInt(itemVariationAndQuantity[1]));
                        } else {
                            throw new NumberFormatException(itemVariationAndQuantityList[i]);
                        }
                    }
                    items[i] = Util.concatenate(multipleItems);
                    staves[i] = Util.concatenate(multipleStaves);
                    offhands[i] = Util.concatenate(multipleOffhands);
                    quantities[i] = maxQuantity;
                }
                itemRequirements = new TransportItems(items, staves, offhands, quantities);
            } catch (NumberFormatException e) {
                log.error("Invalid item or quantity: " + value);
            }
        }

        if ((value = fieldMap.get("Quests")) != null) {
            this.quests = findQuests(value);
        }

        if ((value = fieldMap.get("Duration")) != null && !value.isEmpty()) {
            try {
                this.duration = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.error("Invalid tick duration: " + value);
            }
        }
        if (TransportType.isTeleport(transportType)) {
            // Teleports should always have a non-zero wait,
            // so the pathfinder doesn't calculate the cost by distance
            this.duration = Math.max(this.duration, 1);
        }

        if ((value = fieldMap.get("Display info")) != null) {
            this.displayInfo = value;
        }

        if ((value = fieldMap.get("Consumable")) != null) {
            this.isConsumable = "T".equals(value) || "yes".equals(value.toLowerCase());
        }

        if ((value = fieldMap.get("Wilderness level")) != null && !value.isEmpty()) {
            try {
                this.maxWildernessLevel =  Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.error("Invalid wilderness level: " + value);
            }
        }

        if ((value = fieldMap.get("menuOption menuTarget objectID")) != null) {
            this.objectInfo = value;
        }

        if ((value = fieldMap.get("Varbits")) != null) {
            try {
                for (String varbitRequirement : value.split(DELIM_MULTI)) {
                    if (varbitRequirement.isEmpty()) {
                        continue;
                    }
                    String[] varbitParts = null;
                    for (TransportVarCheck check : TransportVarCheck.values()) {
                        varbitParts = varbitRequirement.split(check.getCode());
                        if (varbitParts.length == 2) {
                            int varbitId = Integer.parseInt(varbitParts[0]);
                            int varbitValue = Integer.parseInt(varbitParts[1]);
                            varbits.add(new TransportVarbit(varbitId, varbitValue, check));
                            break;
                        }
                    }
                    assert varbitParts.length == 2 : "Invalid varbit id and value: '" + varbitRequirement + "'";
                }
            } catch (NumberFormatException e) {
                log.error("Invalid varbit id and value: " + value);
            }
        }

        if ((value = fieldMap.get("VarPlayers")) != null) {
            try {
                for (String varPlayerRequirement : value.split(DELIM_MULTI)) {
                    if (varPlayerRequirement.isEmpty()) {
                        continue;
                    }
                    String[] varPlayerParts = null;
                    for (TransportVarCheck check : TransportVarCheck.values()) {
                        varPlayerParts = varPlayerRequirement.split(check.getCode());
                        if (varPlayerParts.length == 2) {
                            int varPlayerId = Integer.parseInt(varPlayerParts[0]);
                            int varPlayerValue = Integer.parseInt(varPlayerParts[1]);
                            varPlayers.add(new TransportVarPlayer(varPlayerId, varPlayerValue, check));
                            break;
                        }
                    }
                    assert varPlayerParts.length == 2 : "Invalid VarPlayer id and value: '" + varPlayerRequirement + "'";
                }
            } catch (NumberFormatException e) {
                log.error("Invalid VarPlayer id and value: " + value);
            }
        }

        this.type = transportType;
        if (TransportType.AGILITY_SHORTCUT.equals(transportType) &&
            (getRequiredLevel(Skill.RANGED) > 1 || getRequiredLevel(Skill.STRENGTH) > 1)) {
            this.type = TransportType.GRAPPLE_SHORTCUT;
        }
    }

    @Override
    public String toString() {
        return ("(" +
            WorldPointUtil.unpackWorldX(origin) + ", " +
            WorldPointUtil.unpackWorldY(origin) + ", " +
            WorldPointUtil.unpackWorldPlane(origin) + ") to ("+
            WorldPointUtil.unpackWorldX(destination) + ", " +
            WorldPointUtil.unpackWorldY(destination) + ", " +
            WorldPointUtil.unpackWorldPlane(destination) + ")");
    }

    /** The skill level required to use this transport */
    private int getRequiredLevel(Skill skill) {
        return skillLevels[skill.ordinal()];
    }

    /** Whether the transport has one or more quest requirements */
    public boolean isQuestLocked() {
        return !quests.isEmpty();
    }

    private static Set<Quest> findQuests(String questNamesCombined) {
        String[] questNames = questNamesCombined.split(";");
        Set<Quest> quests = new HashSet<>();
        for (String questName : questNames) {
            for (Quest quest : Quest.values()) {
                if (quest.getName().equals(questName)) {
                    quests.add(quest);
                    break;
                }
            }
        }
        return quests;
    }

    private static TransportItems mergeItemRequirements(TransportItems originItems, TransportItems destinationItems) {
        if (originItems == null) {
            return destinationItems;
        }
        if (destinationItems == null) {
            return originItems;
        }

        int[][] items = concatenate2D(originItems.getItems(), destinationItems.getItems());
        int[][] staves = concatenate2D(originItems.getStaves(), destinationItems.getStaves());
        int[][] offhands = concatenate2D(originItems.getOffhands(), destinationItems.getOffhands());

        int[] quantities = Arrays.copyOf(originItems.getQuantities(),
            originItems.getQuantities().length + destinationItems.getQuantities().length);
        System.arraycopy(destinationItems.getQuantities(), 0,
            quantities, originItems.getQuantities().length,
            destinationItems.getQuantities().length);

        return new TransportItems(items, staves, offhands, quantities);
    }

    private static int[][] concatenate2D(int[][] first, int[][] second) {
        int[][] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
