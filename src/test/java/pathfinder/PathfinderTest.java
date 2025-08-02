package pathfinder;

import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.TeleportationItem;
import shortestpath.ItemVariations;
import shortestpath.ShortestPathConfig;
import shortestpath.Transport;
import shortestpath.TransportItems;
import shortestpath.TransportType;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PathfinderTest {
    private static final Map<Integer, Set<Transport>> transports = Transport.loadAllFromResources();

    private PathfinderConfig pathfinderConfig;

    @Mock
    Client client;

    @Mock
    ItemContainer inventory;

    @Mock
    ShortestPathConfig config;

    @Before
    public void before() {
        when(config.calculationCutoff()).thenReturn(30);
        when(config.currencyThreshold()).thenReturn(10000000);
    }

    @Test
    public void testAgilityShortcuts() {
        when(config.useAgilityShortcuts()).thenReturn(true);
        setupInventory(
            new Item(ItemID.ROPE, 1),
            new Item(ItemID.DEATH_CLIMBINGBOOTS, 1));
        testTransportLength(2, TransportType.AGILITY_SHORTCUT);
    }

    @Test
    public void testGrappleShortcuts() {
        when(config.useGrappleShortcuts()).thenReturn(true);
        setupInventory(
            new Item(ItemID.XBOWS_CROSSBOW_ADAMANTITE, 1),
            new Item(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE, 1));
        testTransportLength(2, TransportType.GRAPPLE_SHORTCUT);
    }

    @Test
    public void testBoats() {
        when(config.useBoats()).thenReturn(true);
        setupInventory(
            new Item(ItemID.COINS, 10000),
            new Item(ItemID.ECTOTOKEN, 25));
        testTransportLength(2, TransportType.BOAT);
    }

    @Test
    public void testCanoes() {
        when(config.useCanoes()).thenReturn(true);
        setupInventory(new Item(ItemID.BRONZE_AXE, 1));
        testTransportLength(2, TransportType.CANOE);
    }

    @Test
    public void testCharterShips() {
        when(config.useCharterShips()).thenReturn(true);
        setupInventory(new Item(ItemID.COINS, 100000));
        testTransportLength(2, TransportType.CHARTER_SHIP);
    }

    @Test
    public void testShips() {
        when(config.useShips()).thenReturn(true);
        setupInventory(new Item(ItemID.COINS, 10000));
        testTransportLength(2, TransportType.SHIP);
    }

    @Test
    public void testFairyRings() {
        when(config.useFairyRings()).thenReturn(true);
        testTransportLength(2, TransportType.FAIRY_RING);
    }

    @Test
    public void testGnomeGliders() {
        when(config.useGnomeGliders()).thenReturn(true);
        testTransportLength(2, TransportType.GNOME_GLIDER);
    }

    @Test
    public void testHotAirBalloons() {
        when(config.useHotAirBalloons()).thenReturn(true);
        setupInventory(
            new Item(ItemID.LOGS, 2),
            new Item(ItemID.OAK_LOGS, 1),
            new Item(ItemID.WILLOW_LOGS, 1),
            new Item(ItemID.YEW_LOGS, 1),
            new Item(ItemID.MAGIC_LOGS, 1));
        testTransportLength(2, TransportType.HOT_AIR_BALLOON);
    }

    @Test
    public void testMinecarts() {
        when(config.useMinecarts()).thenReturn(true);
        setupInventory(new Item(ItemID.COINS, 1000));
        testTransportLength(2, TransportType.MINECART);
    }

    @Test
    public void testQuetzals() {
        when(config.useQuetzals()).thenReturn(true);
        testTransportLength(2, TransportType.QUETZAL);
    }

    @Test
    public void testSpiritTrees() {
        when(config.useSpiritTrees()).thenReturn(true);
        when(client.getVarbitValue(any(Integer.class))).thenReturn(20);
        testTransportLength(2, TransportType.SPIRIT_TREE);
    }

    @Test
    public void testTeleportationBoxes() {
        when(config.useTeleportationBoxes()).thenReturn(true);
        testTransportLength(2, TransportType.TELEPORTATION_BOX);
    }

    @Test
    public void testTeleportationLevers() {
        when(config.useTeleportationLevers()).thenReturn(true);
        testTransportLength(2, TransportType.TELEPORTATION_LEVER);
    }

    @Test
    public void testTeleportationMinigames() {
        when(config.useTeleportationMinigames()).thenReturn(true);
        when(config.useTeleportationSpells()).thenReturn(false);
        when(client.getVarbitValue(any(Integer.class))).thenReturn(0);
        when(client.getVarpValue(any(Integer.class))).thenReturn(0);
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(3440, 3334, 0),  // Nature Spirit Grotto
            WorldPointUtil.packWorldPoint(2658, 3157, 0)); // Fishing Trawler
        testTransportLength(3,
            WorldPointUtil.packWorldPoint(3136, 3525, 0),  // In wilderness level 1
            WorldPointUtil.packWorldPoint(2658, 3157, 0)); // Fishing Trawler
    }

    @Test
    public void testTeleportationPortals() {
        when(config.useTeleportationPortals()).thenReturn(true);
        testTransportLength(2, TransportType.TELEPORTATION_PORTAL);
    }

    @Test
    public void testWildernessObelisks() {
        when(config.useWildernessObelisks()).thenReturn(true);
        testTransportLength(2, TransportType.WILDERNESS_OBELISK);
    }

    @Test
    public void testAgilityShortcutAndTeleportItem() {
        when(config.useAgilityShortcuts()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.ALL);
        // Draynor Manor to Champions Guild via several stepping stones, but
        // enabling Combat bracelet teleport should not priotize over stepping stones
        // 5 tiles is using the stepping stones
        // ~40 tiles is using the combat bracelet teleport to Champions Guild
        // >100 tiles is walking around the river via Barbarian Village
        testTransportLength(6,
            WorldPointUtil.packWorldPoint(3149, 3363, 0),
            WorldPointUtil.packWorldPoint(3154, 3363, 0));
    }

    @Test
    public void testChronicle() {
        // South of river south of Champions Guild to Chronicle teleport destination
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(3199, 3336, 0),
            WorldPointUtil.packWorldPoint(3200, 3355, 0),
            TeleportationItem.ALL);
    }

    @Test
    public void testVarrockTeleport() {
        // West of Varrock teleport destination to Varrock teleport destination
        when(config.useTeleportationSpells()).thenReturn(true);

        // With magic level 1 and no item requirements
        testTransportLength(4,
            WorldPointUtil.packWorldPoint(3216, 3424, 0),
            WorldPointUtil.packWorldPoint(3213, 3424, 0),
            TeleportationItem.NONE,
            1);

        // With magic level 99 and magic runes
        setupInventory(
            new Item(ItemID.LAWRUNE, 1),
            new Item(ItemID.AIRRUNE, 3),
            new Item(ItemID.FIRERUNE, 1));
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(3216, 3424, 0),
            WorldPointUtil.packWorldPoint(3213, 3424, 0),
            TeleportationItem.INVENTORY,
            99);
    }

    @Test
    public void testPathViaOtherPlane() {
        // Shortest path from east to west Keldagrim is via the first floor
        // of the Keldagrim Palace, and not via the bridge to the north
        testTransportLength(64,
            WorldPointUtil.packWorldPoint(2894, 10199, 0), // east
            WorldPointUtil.packWorldPoint(2864, 10199, 0)); // west

        testTransportLength(64,
            WorldPointUtil.packWorldPoint(2864, 10199, 0), // west
            WorldPointUtil.packWorldPoint(2894, 10199, 0)); // east
    }

    @Test
    public void testImpossibleCharterShips() {
        // Shortest path for impossible charter ships has length 3 and goes
        // via an intermediate charter ship and not directly with length 2
        when(config.useCharterShips()).thenReturn(true);
        setupInventory(new Item(ItemID.COINS, 1000000));

        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(1455, 2968, 0), // Aldarin
            WorldPointUtil.packWorldPoint(1514, 2971, 0)); // Sunset Coast
        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(1514, 2971, 0), // Sunset Coast
            WorldPointUtil.packWorldPoint(1455, 2968, 0)); // Aldarin

        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(3702, 3503, 0), // Port Phasmatys
            WorldPointUtil.packWorldPoint(3671, 2931, 0)); // Mos Le'Harmless
        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(3671, 2931, 0), // Mos Le'Harmless
            WorldPointUtil.packWorldPoint(3702, 3503, 0)); // Port Phasmatys

        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(1808, 3679, 0), // Port Piscarilius
            WorldPointUtil.packWorldPoint(1496, 3403, 0)); // Land's End
        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(1496, 3403, 0), // Land's End
            WorldPointUtil.packWorldPoint(1808, 3679, 0)); // Port Piscarilius

        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(3038, 3192, 0), // Port Sarim
            WorldPointUtil.packWorldPoint(1496, 3403, 0)); // Land's End
        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(1496, 3403, 0), // Land's End
            WorldPointUtil.packWorldPoint(3038, 3192, 0)); // Port Sarim

        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(3038, 3192, 0), // Port Sarim
            WorldPointUtil.packWorldPoint(2954, 3158, 0)); // Musa Point
        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(2954, 3158, 0), // Musa Point
            WorldPointUtil.packWorldPoint(3038, 3192, 0)); // Port Sarim

        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(3038, 3192, 0), // Port Sarim
            WorldPointUtil.packWorldPoint(1808, 3679, 0)); // Port Piscarilius
        testTransportMinimumLength(3,
            WorldPointUtil.packWorldPoint(1808, 3679, 0), // Port Piscarilius
            WorldPointUtil.packWorldPoint(3038, 3192, 0)); // Port Sarim
    }

    @Test
    public void testNumberOfGnomeGliders() {
        // All permutations of gnome glider transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.GNOME_GLIDER.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /* 
         * Info:
         * NB: Lemanto Andra (Digsite) can only be destination and not origin
         * single_glider_origin_locations * (number_of_gnome_gliders - 1)
         *   1 * 6   // Ta Quir Priw (Gnome Stronghold)
         * + 3 * 6   // Gandius (Karamja)
         * + 3 * 6   // Kar-Hewo (Al-Kharid)
         * + 2 * 6   // Sindarpos (White Wolf Mountain)
         * + 3 * 6   // Lemantolly Undri (Feldip Hills)
         * + 3 * 6   // Ookookolly Undri (Ape Atoll)
         * = 90
         */
        assertEquals(90, actualCount);
    }

    @Test
    public void testNumberOfHotAirBalloons() {
        // All permutations of hot air balloon transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.HOT_AIR_BALLOON.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /* 
         * Info:
         * single_hot_air_balloon_origin_locations * (number_of_hot_air_balloons - 1)
         *   6 * 5   // Entrana
         * + 8 * 5   // Taverley
         * + 8 * 5   // Crafting Guild
         * + 8 * 5   // Varrock
         * + 7 * 5   // Castle Wars
         * + 8 * 5   // Grand Tree
         * = 225
         */
        assertEquals(225, actualCount);
    }

    @Test
    public void testNumberOfQuetzals() {
        // All but 2 permutations of quetzal transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.QUETZAL.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /*
         * Info:
         * NB: Primio can only be used between Varrock and Civitas illa Fortis
         * single_quetzal_origin_locations * (number_of_quetzals - 1) + 2
         *   1 * 10 // Aldarin
         * + 1 * 10 // Civitas illa Fortis
         * + 1 * 10 // Hunter Guild
         * + 1 * 10 // Quetzacalli Gorge
         * + 1 * 10 // Sunset Coast
         * + 1 * 10 // The Teomat
         * + 1 * 10 // Fortis Colosseum
         * + 1 * 10 // Outer Fortis
         * + 1 * 10 // Colossal Wyrm Remains
         * + 1 * 10 // Cam Torum
         * + 1 * 10 // Salvager Overlook
         * + 1 // Varrock -> Civitas illa Fortis
         * + 1 // Civitas illa Fortis -> Varrock
         * = 110 + 2
         * = 112
         */
        assertEquals(112, actualCount);
    }

    @Test
    public void testNumberOfSpiritTrees() {
        // All permutations of spirit tree transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.SPIRIT_TREE.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /*
         * Info:
         * single_tree_origin_locations * (number_of_spirit_trees - 1)
         *   15 * 11   // Tree Gnome Village
         * + 14 * 11   // Gnome Stronghold
         * +  8 * 11   // Battlefield of Khazard
         * +  8 * 11   // Grand Exchange
         * +  8 * 11   // Feldip Hills
         * +  7 * 11   // Prifddinas
         * + 12 * 11   // Port Sarim
         * + 12 * 11   // Etceteria
         * + 12 * 11   // Brimhaven
         * + 12 * 11   // Hosidius
         * + 12 * 11   // Farming Guild
         * +  0 * 11   // Player-owned house
         * + 12 * 11   // Poison Waste
         * = 1452
         */
        assertEquals(1452, actualCount);
    }

    @Test
    public void testNumberOfCharterShips() {
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.CHARTER_SHIP.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /*
         * Info:
         * There are currently 16 unique charter ship origin/destinations.
         * If every combination was possible then it would be 16^2 = 256.
         * It is impossible to travel from and to the same place, so subtract 16.
         * It is also impossible to travel between certain places, presumably
         * because the distance between them is too small. Currently 12 of these.
         */
        assertEquals(16 * 16 - 16 - 12, actualCount);
    }

    @Test
    public void testTransportItems() {
        TransportItems actual = null;
        for (Transport transport : transports.get(Transport.UNDEFINED_ORIGIN)) {
            if ("Varrock Teleport".equals(transport.getDisplayInfo())) {
                actual = transport.getItemRequirements();
                break;
            }
        }
        if (actual != null) {
            TransportItems expected = new TransportItems(
                new int[][]{
                    ItemVariations.AIR_RUNE.getIds(),
                    ItemVariations.FIRE_RUNE.getIds(),
                    ItemVariations.LAW_RUNE.getIds()},
                new int[][]{
                    ItemVariations.STAFF_OF_AIR.getIds(),
                    ItemVariations.STAFF_OF_FIRE.getIds(), null},
                new int[][]{null, ItemVariations.TOME_OF_FIRE.getIds(), null},
                new int[]{3, 1, 1});
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testIsInWilderness() {
        assertEquals(true, PathfinderConfig.isInWilderness(
            WorldPointUtil.packWorldPoint(3339, 3696, 0))); // Green dragons
        assertEquals(false, PathfinderConfig.isInWilderness(
            WorldPointUtil.packWorldPoint(3134, 3629, 0))); // Ferox Enclave
        assertEquals(true, PathfinderConfig.isInWilderness(
            WorldPointUtil.packWorldPoint(3089, 9957, 0))); // Edgeville Dungeon
        assertEquals(true, PathfinderConfig.isInWilderness(
            WorldPointUtil.packWorldPoint(3039, 10260, 0))); // Lava Maze Dungeon
        assertEquals(false, PathfinderConfig.isInWilderness(
            WorldPointUtil.packWorldPoint(3009, 3531, 0))); // Non-wildy peninsula
        assertEquals(true, PathfinderConfig.isInWilderness(
            WorldPointUtil.packWorldPoint(3023, 3626, 1))); // Upstairs in Dark Warriors' Fortress
    }

    private void setupConfig(QuestState questState, int skillLevel, TeleportationItem useTeleportationItems) {
        pathfinderConfig = spy(new PathfinderConfig(client, config));

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(skillLevel);
        when(config.useTeleportationItems()).thenReturn(useTeleportationItems);
        doReturn(true).when(pathfinderConfig).varbitChecks(any(Transport.class));
        doReturn(true).when(pathfinderConfig).varPlayerChecks(any(Transport.class));
        doReturn(questState).when(pathfinderConfig).getQuestState(any(Quest.class));

        pathfinderConfig.refresh();
    }

    private void setupInventory(Item... items) {
        doReturn(inventory).when(client).getItemContainer(InventoryID.INVENTORY);
        doReturn(items).when(inventory).getItems();
    }

    private void testTransportLength(int expectedLength, int origin, int destination) {
        testTransportLength(expectedLength, origin, destination, TeleportationItem.NONE, 99);
    }

    private void testTransportLength(int expectedLength, int origin, int destination,
        TeleportationItem useTeleportationItems) {
        testTransportLength(expectedLength, origin, destination, useTeleportationItems, 99);
    }

    private void testTransportLength(int expectedLength, int origin, int destination,
        TeleportationItem useTeleportationItems, int skillLevel) {
        setupConfig(QuestState.FINISHED, skillLevel, useTeleportationItems);
        assertEquals(expectedLength, calculatePathLength(origin, destination));
        System.out.println("Successfully completed transport length test from " +
            "(" + WorldPointUtil.unpackWorldX(origin) +
            ", " + WorldPointUtil.unpackWorldY(origin) +
            ", " + WorldPointUtil.unpackWorldPlane(origin) + ") to " +
            "(" + WorldPointUtil.unpackWorldX(destination) +
            ", " + WorldPointUtil.unpackWorldY(destination) +
            ", " + WorldPointUtil.unpackWorldPlane(destination) + ")");
    }

    private void testTransportLength(int expectedLength, TransportType transportType) {
        testTransportLength(expectedLength, transportType, QuestState.FINISHED, 99, TeleportationItem.NONE);
    }

    private void testTransportLength(int expectedLength, TransportType transportType, QuestState questState, int skillLevel,
        TeleportationItem useTeleportationItems) {
        setupConfig(questState, skillLevel, useTeleportationItems);

        int counter = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (transportType.equals(transport.getType())) {
                    counter++;
                    assertEquals(transport.toString(), expectedLength, calculateTransportLength(transport));
                }
            }
        }

        assertTrue("No tests were performed", counter > 0);
        System.out.println(String.format("Successfully completed %d " + transportType + " transport length tests", counter));
    }

    private void testTransportMinimumLength(int minimumLength, int origin, int destination) {
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.ALL);
        int actualLength = calculatePathLength(origin, destination);
        assertTrue("An impossible transport was used with length " + actualLength, actualLength >= minimumLength);
        System.out.println("Successfully completed transport length test from " +
            "(" + WorldPointUtil.unpackWorldX(origin) +
            ", " + WorldPointUtil.unpackWorldY(origin) +
            ", " + WorldPointUtil.unpackWorldPlane(origin) + ") to " +
            "(" + WorldPointUtil.unpackWorldX(destination) +
            ", " + WorldPointUtil.unpackWorldY(destination) +
            ", " + WorldPointUtil.unpackWorldPlane(destination) + ")" +
            " with actual length = " + actualLength + " >= minimum length = " + minimumLength);
    }

    private int calculateTransportLength(Transport transport) {
        return calculatePathLength(transport.getOrigin(), transport.getDestination());
    }

    private int calculatePathLength(int origin, int destination) {
        Pathfinder pathfinder = new Pathfinder(pathfinderConfig, origin, Set.of(destination));
        pathfinder.run();
        return pathfinder.getPath().size();
    }
}
