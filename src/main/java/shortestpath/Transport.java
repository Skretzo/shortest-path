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

    /** The ids of items required to use this transport. If the player has **any** matching item, this transport is valid */
    @Getter
    private List<Integer> itemRequirements = new ArrayList<>();

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

    /** The additional travel time */
    @Getter
    private int wait;

    /** Info to display for this transport. For spirit trees, fairy rings, and others, this is the destination option to pick. */
    @Getter
    private String displayInfo;

    /** If this is an item transport, this tracks if it is consumable (as opposed to having infinite uses) */
    @Getter
    private final boolean isConsumable;

    /** If this is an item transport, this is the maximum wilderness level that it can be used in */
    @Getter
    private final int maxWildernessLevel;

    /** Any varbits to check for the transport to be valid. All must pass for a transport to be valid */
    @Getter
    private final List<TransportVarbit> varbits;

    Transport(final WorldPoint origin, final WorldPoint destination) {
        this.origin = origin;
        this.destination = destination;
        this.isConsumable = false;
        this.maxWildernessLevel = -1;
        this.varbits = new ArrayList<>();
    }

    Transport(final String line, TransportType transportType) {
        final String DELIM = " ";

        String[] parts = line.split("\t");

        String[] parts_origin = parts[0].split(DELIM);
        String[] parts_destination = parts[1].split(DELIM);

        if(!TransportType.PLAYER_ITEM.equals(transportType)){
            origin = new WorldPoint(
                Integer.parseInt(parts_origin[0]),
                Integer.parseInt(parts_origin[1]),
                Integer.parseInt(parts_origin[2]));
        } else {
            origin = null;
        }
        destination = new WorldPoint(
            Integer.parseInt(parts_destination[0]),
            Integer.parseInt(parts_destination[1]),
            Integer.parseInt(parts_destination[2]));

        // Skill requirements
        if (parts.length >= 4 && !parts[3].isEmpty()) {
            String[] skillRequirements = parts[3].split(";");

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

        itemRequirements = new ArrayList<>();
        //item requirements are currently only implemented for player-held item transports
        if(TransportType.PLAYER_ITEM.equals(transportType)){
            for (String item : parts[4].split(DELIM)) {
                int itemId = Integer.parseInt(item);
                itemRequirements.add(itemId);
            }
        }

        // Quest requirements
        if (parts.length >= 6 && !parts[5].isEmpty()) {
            this.quests = findQuests(parts[5]);
        }

        // Additional travel time
        if (parts.length >= 7 && !parts[6].isEmpty()) {
            this.wait = Integer.parseInt(parts[6]);
        } else if (TransportType.PLAYER_ITEM.equals(transportType)){
            //item transports should always have a non-zero wait, so the pathfinder doesn't calculate the cost by distance
            this.wait = 1;
        }

        // Destination
        if (parts.length >= 8 && !parts[7].isEmpty()) {
            this.displayInfo = parts[7];
        }

        //Consumable - for item transports
        this.isConsumable = parts.length >= 9 && parts[8].equals("T");

        //Wilderness level - for item transports
        if(parts.length >= 10 && !parts[9].isEmpty()){
            this.maxWildernessLevel = Integer.parseInt(parts[9]);
        } else {
            this.maxWildernessLevel = -1;
        }

        this.varbits = new ArrayList<>();
        //Varbit check - all must evaluate to true
        if(parts.length >= 11 && !parts[10].isEmpty()) {
            for (String varbitCheck : parts[10].split(DELIM)) {
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

    private static void addItemTransports(Map<WorldPoint, List<Transport>> transports) {
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream("/items.tsv")), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                Transport transport = new Transport(line, TransportType.PLAYER_ITEM);
                transports.computeIfAbsent(null, k -> new ArrayList<>()).add(transport);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addTransports(Map<WorldPoint, List<Transport>> transports, String path, TransportType transportType) {
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            List<String> fairyRingsQuestNames = new ArrayList<>();
            List<WorldPoint> fairyRings = new ArrayList<>();
            List<String> fairyRingCodes = new ArrayList<>();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }

                if (TransportType.FAIRY_RING.equals(transportType)) {
                    String[] p = line.split("\t");
                    fairyRings.add(new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                    fairyRingCodes.add(p.length >= 4 ? p[3].replaceAll("_", " ") : null);
                    fairyRingsQuestNames.add(p.length >= 7 ? p[6] : "");
                } else {
                    Transport transport = new Transport(line, transportType);
                    WorldPoint origin = transport.getOrigin();
                    transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);
                }
            }
            if (TransportType.FAIRY_RING.equals(transportType)) {
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
            }
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
        addTransports(transports, "/fairy_rings.tsv", TransportType.FAIRY_RING);
        addTransports(transports, "/gnome_gliders.tsv", TransportType.GNOME_GLIDER);
        addTransports(transports, "/spirit_trees.tsv", TransportType.SPIRIT_TREE);
        addTransports(transports, "/levers.tsv", TransportType.TELEPORTATION_LEVER);
        addTransports(transports, "/portals.tsv", TransportType.TELEPORTATION_PORTAL);

        addItemTransports(transports);

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
    }
}
