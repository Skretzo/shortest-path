package shortestpath;

import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * This class represents a travel point between two WorldPoints.
 */
public class Transport {

    /** The starting point of this transport */
    @Getter
    private final WorldPoint origin;

    /** The ending point of this transport */
    @Getter
    private final WorldPoint destination;

    /** The skill levels required to use this transport */
    private final int[] skillLevels = new int[Skill.values().length];

    /** The quests required to use this transport */
    @Getter
    private List<Quest> quests = new ArrayList<>();

    /** The ids of items required to use this transport. If the player has **any** of the matching list of items, this transport is valid */
    @Getter
    private List<List<Integer>> itemIdRequirements = new ArrayList<>();

    /** Whether the transport is an agility shortcut */
    @Getter
    private boolean isAgilityShortcut;

    /** Whether the transport is a crossbow grapple shortcut */
    @Getter
    private boolean isGrappleShortcut;

    /** Whether the transport is a boat */
    @Getter
    private boolean isBoat;

    /** Whether the transport is a canoe */
    @Getter
    private boolean isCanoe;

    /** Whether the transport is a charter ship */
    @Getter
    private boolean isCharterShip;

    /** Whether the transport is a ship */
    @Getter
    private boolean isShip;

    /** Whether the transport is a fairy ring */
    @Getter
    private boolean isFairyRing;

    /** Whether the transport is a gnome glider */
    @Getter
    private boolean isGnomeGlider;

    /** Whether the transport is a spirit tree */
    @Getter
    private boolean isSpiritTree;

    /** Whether the transport is a teleportation lever */
    @Getter
    private boolean isTeleportationLever;

    /** Whether the transport is a teleportation portal */
    @Getter
    private boolean isTeleportationPortal;

    /** Whether the transport is a player-held item */
    @Getter
    private boolean isPlayerItem;

    /** Whether the transport is a spell teleport */
    @Getter
    private boolean isSpellTeleport;

    /** The additional travel time */
    @Getter
    private int wait;

    /** Info to display for this transport. For spirit trees, fairy rings, and others, this is the destination option to pick. */
    @Getter
    private String displayInfo;

    /** If this is an item transport, this tracks if it is consumable (as opposed to having infinite uses) */
    @Getter
    private boolean isConsumable = false;

    /** If this is an item transport, this is the maximum wilderness level that it can be used in */
    @Getter
    private int maxWildernessLevel = -1;

    /** Any varbits to check for the transport to be valid. All must pass for a transport to be valid */
    @Getter
    private final List<TransportVarbit> varbits = new ArrayList<>();

    Transport(final WorldPoint origin, final WorldPoint destination) {
        this.origin = origin;
        this.destination = destination;
    }

    Transport(final Map<String, String> fieldMap, TransportType transportType) {
        final String DELIM = " ";

        if (fieldMap.containsKey("Origin")) {
            String[] originArray = fieldMap.get("Origin").split(DELIM);
            origin = new WorldPoint(
                Integer.parseInt(originArray[0]),
                Integer.parseInt(originArray[1]),
                Integer.parseInt(originArray[2]));
        } else {
            origin = null;
        }

        if (fieldMap.containsKey("Destination")) {
            String[] destinationArray = fieldMap.get("Destination").split(DELIM);
            destination = new WorldPoint(
                Integer.parseInt(destinationArray[0]),
                Integer.parseInt(destinationArray[1]),
                Integer.parseInt(destinationArray[2]));
        } else {
            destination = null;
        }

        if (fieldMap.containsKey("Skill Requirements")) {
            String[] skillRequirements = fieldMap.get("Skill Requirements").split(";");

            for (String requirement : skillRequirements) {
                String[] levelAndSkill = requirement.split(DELIM);

                int level = Integer.parseInt(levelAndSkill[0]);
                String skillName = levelAndSkill[1];

                Skill[] skills = Skill.values();
                for (int i = 0; i < skills.length; i++) {
                    if (skills[i].getName().equals(skillName)) {
                        skillLevels[i] = level;
                        break;
                    }
                }
            }
        }

        if (fieldMap.containsKey("Item ID Requirements")) {
            String[] itemIdsList = fieldMap.get("Item ID Requirements").split(";");
            for (String listIds : itemIdsList)
            {
                List<Integer> multiitemList = new ArrayList<>();
                String[] itemIds = listIds.split(",");
                for (String item : itemIds) {
                    int itemId = Integer.parseInt(item);
                    multiitemList.add(itemId);
                }
                itemIdRequirements.add(multiitemList);
            }
        }

        if (fieldMap.containsKey("Quest")) {
            this.quests = findQuests(fieldMap.get("Quest"));
        }

        if (fieldMap.containsKey("Wait")) {
            this.wait = Integer.parseInt(fieldMap.get("Wait"));
        }
        if (TransportType.PLAYER_ITEM.equals(transportType)) {
            // Item transports should always have a non-zero wait, so the pathfinder doesn't calculate the cost by distance
            this.wait = Math.max(this.wait, 1);
        }
        if (TransportType.SPELL_TELEPORT.equals(transportType)) {
            // Spell transports should always have a non-zero wait, so the pathfinder doesn't calculate the cost by distance
            this.wait = Math.max(this.wait, 1);
        }

        if (fieldMap.containsKey("Display Info")) {
            this.displayInfo = fieldMap.get("Display Info");
        }

        if (fieldMap.containsKey("Consumable")) {
            this.isConsumable = fieldMap.get("Consumable").equals("T");
        }

        if (fieldMap.containsKey("Wilderness Level")) {
            this.maxWildernessLevel =  Integer.parseInt(fieldMap.get("Wilderness Level"));
        } else {
            this.maxWildernessLevel = -1;
        }

        if (fieldMap.containsKey("Varbits")) {
            for (String varbitCheck : fieldMap.get("Varbits").split(";")) {
                var varbitParts = varbitCheck.split("=");
                int varbitId = Integer.parseInt(varbitParts[0]);
                int varbitValue = Integer.parseInt(varbitParts[1]);
                varbits.add(new TransportVarbit(varbitId, varbitValue));
            }
        }

        isAgilityShortcut = TransportType.AGILITY_SHORTCUT.equals(transportType);
        isGrappleShortcut = isAgilityShortcut && (getRequiredLevel(Skill.RANGED) > 1 || getRequiredLevel(Skill.STRENGTH) > 1);
        isBoat = TransportType.BOAT.equals(transportType);
        isCanoe = TransportType.CANOE.equals(transportType);
        isCharterShip = TransportType.CHARTER_SHIP.equals(transportType);
        isShip = TransportType.SHIP.equals(transportType);
        isGnomeGlider = TransportType.GNOME_GLIDER.equals(transportType);
        isSpiritTree = TransportType.SPIRIT_TREE.equals(transportType);
        isTeleportationLever = TransportType.TELEPORTATION_LEVER.equals(transportType);
        isTeleportationPortal = TransportType.TELEPORTATION_PORTAL.equals(transportType);
        isPlayerItem = TransportType.PLAYER_ITEM.equals(transportType);
        isSpellTeleport = TransportType.SPELL_TELEPORT.equals(transportType);
    }

    /** The skill level required to use this transport */
    public int getRequiredLevel(Skill skill) {
        return skillLevels[skill.ordinal()];
    }

    /** Whether the transport has one or more quest requirements */
    public boolean isQuestLocked() {
        return !quests.isEmpty();
    }

    private static List<Quest> findQuests(String questNamesCombined) {
        String[] questNames = questNamesCombined.split(";");
        List<Quest> quests = new ArrayList<>();
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

    private static void addFairyRingTransports(Map<WorldPoint, List<Transport>> transports, String path) {
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            List<String> fairyRingsQuestNames = new ArrayList<>();
            List<WorldPoint> fairyRings = new ArrayList<>();
            List<String> fairyRingCodes = new ArrayList<>();

            // Header line is the first line in the file and must start with `# ` so trim off the first two chars
            String headerLine = scanner.nextLine();
            String[] headers = headerLine.substring(2).split("\t");

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }

                String[] fields = line.split("\t");
                Map<String, String> fieldMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    if (i < fields.length && !fields[i].isEmpty()) {
                        fieldMap.put(headers[i], fields[i]);
                    }
                }

                fairyRings.add(new WorldPoint(Integer.parseInt(fieldMap.get("X")),
                                              Integer.parseInt(fieldMap.get("Y")),
                                              Integer.parseInt(fieldMap.get("Z"))));
                fairyRingCodes.add(fieldMap.getOrDefault("Fairy Code", null));
                fairyRingsQuestNames.add(fieldMap.getOrDefault("Fairy Code", ""));
            }

            for (WorldPoint origin : fairyRings) {
                for (int i = 0; i < fairyRings.size(); i++) {
                    WorldPoint destination = fairyRings.get(i);
                    String questName = fairyRingsQuestNames.get(i);
                    if (origin.equals(destination)) {
                        continue;
                    }

                    Transport transport = new Transport(origin, destination);
                    transport.isFairyRing = true;
                    transport.wait = 5;
                    transport.displayInfo = fairyRingCodes.get(i);
                    transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);
                    if (!Strings.isNullOrEmpty(questName)) {
                        transport.quests = findQuests(questName);
                    }
                }
            }
            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addTransports(Map<WorldPoint, List<Transport>> transports, String path, TransportType transportType) {
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);

            // Header line is the first line in the file and must start with `# ` so trim off the first two chars
            String headerLine = scanner.nextLine();
            String[] headers = headerLine.substring(2).split("\t");

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }

                String[] fields = line.split("\t");
                Map<String, String> fieldMap = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    if (i < fields.length && !fields[i].isEmpty()) {
                        fieldMap.put(headers[i], fields[i]);
                    }
                }

                Transport transport = new Transport(fieldMap, transportType);
                WorldPoint origin = transport.getOrigin();
                transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);

            }
            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static HashMap<WorldPoint, List<Transport>> loadAllFromResources() {
        HashMap<WorldPoint, List<Transport>> transports = new HashMap<>();
        addTransports(transports, "/transports.tsv", TransportType.TRANSPORT);
        addTransports(transports, "/agility_shortcuts.tsv", TransportType.AGILITY_SHORTCUT);
        addTransports(transports, "/boats.tsv", TransportType.BOAT);
        addTransports(transports, "/canoes.tsv", TransportType.CANOE);
        addTransports(transports, "/charter_ships.tsv", TransportType.CHARTER_SHIP);
        addTransports(transports, "/ships.tsv", TransportType.SHIP);
        addFairyRingTransports(transports, "/fairy_rings.tsv");
        addTransports(transports, "/gnome_gliders.tsv", TransportType.GNOME_GLIDER);
        addTransports(transports, "/spirit_trees.tsv", TransportType.SPIRIT_TREE);
        addTransports(transports, "/levers.tsv", TransportType.TELEPORTATION_LEVER);
        addTransports(transports, "/portals.tsv", TransportType.TELEPORTATION_PORTAL);
        addTransports(transports, "/items.tsv", TransportType.PLAYER_ITEM);
        addTransports(transports, "/spell_teleports.tsv", TransportType.SPELL_TELEPORT);

        return transports;
    }

    private enum TransportType {
        TRANSPORT,
        AGILITY_SHORTCUT,
        BOAT,
        CANOE,
        CHARTER_SHIP,
        SHIP,
        FAIRY_RING,
        GNOME_GLIDER,
        SPIRIT_TREE,
        TELEPORTATION_LEVER,
        TELEPORTATION_PORTAL,
        PLAYER_ITEM,
        SPELL_TELEPORT,
    }
}
