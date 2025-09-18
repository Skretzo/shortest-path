package shortestpath.pathfinder;

import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import shortestpath.ItemVariations;
import shortestpath.ShortestPathConfig;
import shortestpath.ShortestPathPlugin;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportItems;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PathfinderTest {
    private static final Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();

    private PathfinderConfig pathfinderConfig;

    @Mock
    Client client;

    @Mock
    ItemContainer inventory;

    @Mock
    ItemContainer equipment;

    @Mock
    ShortestPathPlugin plugin;

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
        setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        testTransportLength(2, TransportType.FAIRY_RING);
    }

    @Test
    public void testFairyRingsNotUsedWithoutDramenStaff() {
        when(config.useFairyRings()).thenReturn(true);
        setupInventory();
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0);

        // Refresh config which will populate usable transports
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

        // Ensure none of the usable transports are of type FAIRY_RING
        for (Set<Transport> set : pathfinderConfig.getTransports().values()) {
            for (Transport t : set) {
                assertTrue("Fairy ring used unexpectedly: " + t, !TransportType.FAIRY_RING.equals(t.getType()));
            }
        }
    }

    @Test
    public void testFairyRingsUsedWithLumbridgeDiaryCompleteWithoutDramenStaff() {
        when(config.useFairyRings()).thenReturn(true);
        // No Dramen staff in inventory or equipment
        setupInventory();
        // Satisfy Fairy2 quest varbit and Lumbridge elite diary complete
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);

        // Use the existing test helper which will refresh config and run checks for all fairy ring transports
        testTransportLength(2, TransportType.FAIRY_RING);
    }

    @Test
    public void testFairyRingsUsedWithDramenStaffWornInHand() {
        when(config.useFairyRings()).thenReturn(true);
        setupInventory();
        setupEquipment(new Item(ItemID.DRAMEN_STAFF, 1));

        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

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
    public void testMagicCarpets() {
        when(config.useMagicCarpets()).thenReturn(true);
        setupInventory(
            new Item(ItemID.COINS, 200));
        testTransportLength(2, TransportType.MAGIC_CARPET);
    }

    @Test
    public void testMagicMushtrees() {
        when(config.useMagicMushtrees()).thenReturn(true);
        testTransportLength(2, TransportType.MAGIC_MUSHTREE);
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
        // enabling Combat bracelet teleport should not prioritize over stepping stones
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
    public void testCaves() {
        // Eadgar's Cave
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(2892, 3671, 0),
            WorldPointUtil.packWorldPoint(2893, 10074, 2));
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(2893, 3671, 0),
            WorldPointUtil.packWorldPoint(2893, 10074, 2));
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(2894, 3671, 0),
            WorldPointUtil.packWorldPoint(2893, 10074, 2));
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(2895, 3672, 0),
            WorldPointUtil.packWorldPoint(2893, 10074, 2));
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(2892, 10074, 2),
            WorldPointUtil.packWorldPoint(2893, 3671, 0));
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(2893, 10074, 2),
            WorldPointUtil.packWorldPoint(2893, 3671, 0));
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(2894, 10074, 2),
            WorldPointUtil.packWorldPoint(2893, 3671, 0));
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
    public void testTransportItems() {
        // Varrock Teleport
        TransportItems actual = null;
        for (Transport transport : transports.get(Transport.UNDEFINED_ORIGIN)) {
            if ("Varrock Teleport".equals(transport.getDisplayInfo())) {
                actual = transport.getItemRequirements();
                break;
            }
        }
        TransportItems expected = null;
        if (actual != null) {
            expected = new TransportItems(
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

        // Trollheim Teleport
        actual = null;
        for (Transport transport : transports.get(Transport.UNDEFINED_ORIGIN)) {
            if ("Trollheim Teleport".equals(transport.getDisplayInfo())) {
                actual = transport.getItemRequirements();
                break;
            }
        }
        expected = null;
        if (actual != null) {
            expected = new TransportItems(
                new int[][]{
                    ItemVariations.FIRE_RUNE.getIds(),
                    ItemVariations.LAW_RUNE.getIds()},
                new int[][]{
                    ItemVariations.STAFF_OF_FIRE.getIds(),
                    null},
                new int[][]{
                    ItemVariations.TOME_OF_FIRE.getIds(),
                    null},
                new int[]{2, 2});
            assertEquals(expected, actual);
        }
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
        doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
        doReturn(items).when(inventory).getItems();
    }

    private void setupEquipment(Item... items) {
        doReturn(equipment).when(client).getItemContainer(InventoryID.WORN);
        doReturn(items).when(equipment).getItems();
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
        Pathfinder pathfinder = new Pathfinder(plugin, pathfinderConfig, origin, Set.of(destination));
        pathfinder.run();
        return pathfinder.getPath().size();
    }
}
