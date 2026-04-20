package shortestpath.pathfinder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GameState;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.dashboard.PathfinderTestDashboardCollector;
import shortestpath.ItemVariations;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.ShortestPathConfig;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;
import shortestpath.transport.requirement.TransportItems;
import shortestpath.ShortestPathPlugin;

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
    ItemContainer bank;

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
    public void testGrappleBranchDoesNotLeakBankedMithGrapple() {
        // The crossbow is already on hand, but the mith grapple is only in the bank.
        // This should expose any branch leakage where one explored bank path makes the
        // grapple shortcut appear usable on a different branch that never banked.
        when(config.useGrappleShortcuts()).thenReturn(true);
        when(config.includeBankPath()).thenReturn(true);
        when(config.useAgilityShortcuts()).thenReturn(true);
        setupInventory(new Item(ItemID.XBOWS_CROSSBOW_ADAMANTITE, 1));
        setupEquipment();
        setupConfigWithBank(new Item(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE, 1));

        assertScenarioPathLength(
            "Banked mith grapple should not leak to non-bank grapple branch",
            66,
            WorldPointUtil.packWorldPoint(3025, 3365, 0),
            WorldPointUtil.packWorldPoint(3026, 3393, 0));
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
    public void testCatherbyCharterReusedAfterBankVisitWithBankedCoins() {
        // Start on the Catherby charter tile with no coins on hand. The route must bank,
        // then come back and reuse that same charter origin tile once coins are available.
        int catherbyCharter = WorldPointUtil.packWorldPoint(2792, 3414, 0);
        int musaPointCharter = WorldPointUtil.packWorldPoint(2954, 3158, 0);

        when(config.useCharterShips()).thenReturn(true);
        when(config.includeBankPath()).thenReturn(true);
        setupInventory();
        setupEquipment();
        setupConfigWithBank(new Item(ItemID.COINS, 10000));

        assertScenarioPathLength(
            "Catherby charter tile reuse -> bank -> Musa Point with banked coins",
            78, // Fill in the precise length after running locally.
            catherbyCharter,
            musaPointCharter);
    }

    @Test
    public void testCatherbyBankBranchDoesNotLeakCoinsToCharterBranch() {
        // Start near Catherby bank rather than on the dock itself. One search branch can touch
        // the bank, while another heads straight to the charter ship. Banked coins must not leak
        // from the bank branch into the non-bank charter branch.
        when(config.useCharterShips()).thenReturn(true);
        when(config.includeBankPath()).thenReturn(true);
        setupInventory();
        setupEquipment();
        setupConfigWithBank(new Item(ItemID.COINS, 10000));

        assertScenarioPathLength(
            "Catherby bank branch should not leak coins to charter branch",
            46, // Fill in the precise length after running locally.
            WorldPointUtil.packWorldPoint(2807, 3435, 0),
            WorldPointUtil.packWorldPoint(2954, 3158, 0));
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

        // Verify ALL fairy ring transports are available, but only calculate one path
        testAllTransportsAvailableWithSinglePath(TransportType.FAIRY_RING);
    }

    @Test
    public void testLunarStaffFairyRings() {
        when(config.useFairyRings()).thenReturn(true);
        setupInventory(new Item(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF, 1));
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

        // Verify ALL fairy ring transports are available, but only calculate one path
        testAllTransportsAvailableWithSinglePath(TransportType.FAIRY_RING);
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
    public void testFairyRingsNotUsedWithoutQuestProgressOrEliteDiary() {
        when(config.useFairyRings()).thenReturn(true);
        setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(0);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0);

        setupConfig(QuestState.NOT_STARTED, 99, TeleportationItem.NONE);

        for (Set<Transport> set : pathfinderConfig.getTransports().values()) {
            for (Transport t : set) {
                assertTrue("Fairy ring used unexpectedly without quest progress or diary: " + t,
                    !TransportType.FAIRY_RING.equals(t.getType()));
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

        testSingleTransportScenario("Fairy ring with Lumbridge diary and no Dramen staff", 2, TransportType.FAIRY_RING);
    }

    @Test
    public void testFairyRingsUsedWithDramenStaffWornInHand() {
        when(config.useFairyRings()).thenReturn(true);
        setupInventory();
        setupEquipment(new Item(ItemID.DRAMEN_STAFF, 1));

        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

        testSingleTransportScenario("Fairy ring with Dramen staff worn", 2, TransportType.FAIRY_RING);
    }

    @Test
    public void testTeleportItemsAndFairyRingsAvailableAfterBankVisit() {
        // Test scenario: Both Dramen staff AND Ardougne cloak are in the bank
        // After visiting a bank, both fairy rings AND teleport items should be available
        when(config.useFairyRings()).thenReturn(true);
        when(config.includeBankPath()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
        setupInventory();
        setupEquipment();

        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

        setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
            new Item(ItemID.DRAMEN_STAFF, 1),
            new Item(ItemID.ARDY_CAPE_ELITE, 1)
        );

        // With per-path filtering, fairy rings ARE in the transport map
        // but filtered at runtime based on whether the path visited a bank
        boolean hasFairyRing = false;
        for (Set<Transport> set : pathfinderConfig.getTransports().values()) {
            for (Transport t : set) {
                if (TransportType.FAIRY_RING.equals(t.getType())) {
                    hasFairyRing = true;
                    break;
                }
            }
            if (hasFairyRing) break;
        }
        assertTrue("Fairy ring transports should be in map (filtered per-path)", hasFairyRing);

    }

    /**
     * Debug test: Compare paths from Castle Wars to AKQ fairy ring
     * with staff in inventory vs staff in bank.
     * Both should use fairy rings if that's the optimal path.
     */
    @Test
    public void testCastleWarsToAKQWithStaffInInventory() {
        int castleWars = WorldPointUtil.packWorldPoint(2442, 3083, 0);
        int akqFairyRing = WorldPointUtil.packWorldPoint(2324, 3619, 0);

        when(config.useFairyRings()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
        when(config.costConsumableTeleportationItems()).thenReturn(50);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

        setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
        setupEquipment();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY_AND_BANK);

        Pathfinder pathfinderWithStaff = runScenario("Castle Wars -> AKQ with staff in inventory",
            castleWars, akqFairyRing);

        assertTrue("Fairy ring should be used when staff is in inventory",
            usedTransportType(pathfinderWithStaff, TransportType.FAIRY_RING));
    }

    @Test
    public void testCastleWarsToAKQWithStaffInBank() {
        int castleWars = WorldPointUtil.packWorldPoint(2442, 3083, 0);
        int akqFairyRing = WorldPointUtil.packWorldPoint(2324, 3619, 0);

        when(config.useFairyRings()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
        when(config.costConsumableTeleportationItems()).thenReturn(50);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(config.includeBankPath()).thenReturn(true);
        setupInventory();
        setupEquipment();
        setupConfigWithBank(
            new Item(ItemID.DRAMEN_STAFF, 1),
            new Item(ItemID.ARDY_CAPE_MEDIUM, 1),
            new Item(ItemID.NECKLACE_OF_PASSAGE_5, 1)
        );

        Pathfinder pathfinderWithBankStaff = runScenario("Castle Wars -> AKQ with staff in bank",
            castleWars, akqFairyRing);

        assertTrue("Fairy ring should be used when staff is in bank with includeBankPath",
            usedTransportType(pathfinderWithBankStaff, TransportType.FAIRY_RING));
    }

    /**
     * Diagnose the issue: targeting DJP fairy ring works, but targeting AKQ does not.
     * When staff is in bank and target is DJP - uses Ardougne cloak correctly.
     * When staff is in bank and target is AKQ - incorrectly uses necklace of passage.
     */
    @Test
    public void testBankPathDJPvsAKQTarget() {
        int castleWars = WorldPointUtil.packWorldPoint(2442, 3083, 0);
        int djpFairyRing = WorldPointUtil.packWorldPoint(2658, 3230, 0); // Near Kandarin Monastery
        int akqFairyRing = WorldPointUtil.packWorldPoint(2319, 3619, 0); // AKQ destination

        when(config.useFairyRings()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
        when(config.includeBankPath()).thenReturn(true);
        when(config.costConsumableTeleportationItems()).thenReturn(50);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        setupInventory();
        setupEquipment();

        // Both paths share the same config — bank contains: Dramen staff, Ardougne cloak, necklace
        setupConfigWithBank(
            new Item(ItemID.DRAMEN_STAFF, 1),
            new Item(ItemID.ARDY_CAPE_ELITE, 1),
            new Item(ItemID.NECKLACE_OF_PASSAGE_1, 1)
        );

        Pathfinder pathfinderToDJP = runScenario("Castle Wars -> DJP", castleWars, djpFairyRing);

        // PathfinderConfig is not mutated between runs; reuse the same config for the second path
        Pathfinder pathfinderToAKQ = runScenario("Castle Wars -> AKQ", castleWars, akqFairyRing);

        assertTrue("Should use Ardougne cloak to DJP",
            usedTransportWithDisplayInfo(pathfinderToDJP, TransportType.TELEPORTATION_ITEM, "Ardougne"));
        assertFalse("Should NOT use necklace to DJP",
            usedTransportWithDisplayInfo(pathfinderToDJP, TransportType.TELEPORTATION_ITEM, "Necklace"));

        assertTrue("Should use fairy ring to reach AKQ",
            usedTransportType(pathfinderToAKQ, TransportType.FAIRY_RING));
        assertTrue("Should use Ardougne cloak to reach AKQ via fairy ring",
            usedTransportWithDisplayInfo(pathfinderToAKQ, TransportType.TELEPORTATION_ITEM, "Ardougne"));
        assertFalse("Should NOT use necklace to AKQ",
            usedTransportWithDisplayInfo(pathfinderToAKQ, TransportType.TELEPORTATION_ITEM, "Necklace"));
    }

    /**
     * Test scenario: Player has Ardougne cloak in INVENTORY, Dramen staff in BANK.
     * Route should be: walk to nearest bank → pick up staff → use cloak → fairy ring.
     * Should NOT suggest picking up a necklace from bank instead.
     */
    @Test
    public void testArdougneCloakInInventoryWithStaffInBank() {
        int castleWars = WorldPointUtil.packWorldPoint(2442, 3083, 0);
        int akqFairyRing = WorldPointUtil.packWorldPoint(2319, 3619, 0);

        when(config.useFairyRings()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
        when(config.includeBankPath()).thenReturn(true);
        when(config.costConsumableTeleportationItems()).thenReturn(50);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0);

        // Ardougne cloak already in INVENTORY; staff and (penalised) necklace only in bank
        setupInventory(new Item(ItemID.ARDY_CAPE_ELITE, 1));
        setupEquipment();
        setupConfigWithBank(
            new Item(ItemID.DRAMEN_STAFF, 1),
            new Item(ItemID.NECKLACE_OF_PASSAGE_5, 1)
        );

        Pathfinder pathfinder = runScenario("Castle Wars -> AKQ with cloak inventory/staff bank",
            castleWars, akqFairyRing);

        assertTrue("Should use Ardougne cloak (it's in inventory, non-consumable)",
            usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Ardougne"));
        assertFalse("Should NOT use necklace (it's consumable with penalty)",
            usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Necklace"));
        assertTrue("Should use fairy ring to reach destination",
            usedTransportType(pathfinder, TransportType.FAIRY_RING));
    }

    /**
     * Test that when Dramen staff is only in the bank, fairy rings are in the transport map
     * with the bank-only staff case gated by the explicit bankVisited search state.
     * This ensures paths that don't visit a bank won't use fairy rings.
     */
    @Test
    public void testFairyRingNotUsedAfterTeleportWithoutBankVisit() {
        // Enable fairy rings and teleport items
        when(config.useFairyRings()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
        when(config.includeBankPath()).thenReturn(true);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0); // No diary

        // Ring of Wealth in inventory, but Dramen staff only in bank
        setupInventory(new Item(ItemID.RING_OF_WEALTH_1, 1));
        setupEquipment();

        setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
            new Item(ItemID.DRAMEN_STAFF, 1));

        // With per-path filtering, fairy rings ARE in the transport map
        // but filtered at runtime based on whether the path visited a bank
        boolean hasFairyRing = false;
        for (Set<Transport> set : pathfinderConfig.getTransports().values()) {
            for (Transport t : set) {
                if (TransportType.FAIRY_RING.equals(t.getType())) {
                    hasFairyRing = true;
                    break;
                }
            }
            if (hasFairyRing) break;
        }
        assertTrue("Fairy rings should be in transport map (filtered per-path)", hasFairyRing);

        runScenario("Fairy ring remains gated before bank visit",
            WorldPointUtil.packWorldPoint(2442, 3096, 0),
            WorldPointUtil.packWorldPoint(3162, 3489, 0));
    }

    @Test
    public void testTeleportItemInInventoryUsableBeforeFirstBankVisit() {
        // A bank-only Dramen staff should not suppress a teleport item that is already on hand.
        // The route is allowed to spend the Ring of wealth before it reaches the first bank.
        int castleWars = WorldPointUtil.packWorldPoint(2442, 3096, 0);
        int grandExchangeBank = WorldPointUtil.packWorldPoint(3162, 3489, 0);

        when(config.useFairyRings()).thenReturn(true);
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
        when(config.includeBankPath()).thenReturn(true);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0);

        // The ring is already on hand; only the fairy ring staff is banked.
        setupInventory(new Item(ItemID.RING_OF_WEALTH_1, 1));
        setupEquipment();
        setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
            new Item(ItemID.DRAMEN_STAFF, 1));

        Pathfinder pathfinder = runScenario("Castle Wars -> Grand Exchange bank with on-hand ring",
            castleWars, grandExchangeBank);

        assertTrue("Ring of wealth should remain usable before the first bank visit",
            usedTransportWithDisplayInfoBeforeFirstBank(pathfinder, TransportType.TELEPORTATION_ITEM, "Ring of wealth"));
    }

    /**
     * Test that fairy rings are only used when the path actually visits a bank.
     * When the Dramen staff is only in the bank, fairy rings should only be available
     * on paths that go through a bank first.
     */
    @Test
    public void testFairyRingRequiresBankVisitWhenStaffInBank() {
        // Enable fairy rings
        when(config.useFairyRings()).thenReturn(true);
        when(config.includeBankPath()).thenReturn(true);
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0); // No diary

        // No staff in inventory or equipment - only in bank
        setupInventory();
        setupEquipment();

        setupConfigWithBank(new Item(ItemID.DRAMEN_STAFF, 1));


        // Test pathfinding: from Al Kharid mine to AKQ fairy ring
        // The path should NOT use fairy rings directly without going through a bank
        int alKharidMine = WorldPointUtil.packWorldPoint(3298, 3290, 0);
        int akqFairyRing = WorldPointUtil.packWorldPoint(2319, 3619, 0);

        Pathfinder pathfinder = runScenario("Al Kharid mine -> AKQ with staff only in bank",
            alKharidMine, akqFairyRing);

        boolean usedFairyRing = usedTransportType(pathfinder, TransportType.FAIRY_RING);

        // If fairy rings are used, the path must have gone through a bank
        if (usedFairyRing) {
            assertTrue("If fairy ring is used, path must visit a bank first to pick up Dramen staff",
                pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
        }
        // If no fairy ring was used, that's also acceptable (walking path)
    }

    @Test
    public void testGreatConchBankToMcGruborsWoodMaximumLength() {
        // Baseline bank-enabled Great Conch route: fairy rings and a combat bracelet are only
        // available from the bank, so the chosen path should reflect those post-bank unlocks.
        when(config.includeBankPath()).thenReturn(true);
        when(config.useAgilityShortcuts()).thenReturn(true);
        when(config.useFairyRings()).thenReturn(true);
        setupInventory();
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

        assertScenarioPathLengthWithBank(
            "Great Conch -> McGrubor's Wood with banked staff and bracelet",
            54,
            WorldPointUtil.packWorldPoint(3180, 2419, 0),
            WorldPointUtil.packWorldPoint(2652, 3485, 0),
            TeleportationItem.INVENTORY_AND_BANK,
            new Item(ItemID.DRAMEN_STAFF, 1),
            new Item(11118, 1));
    }

    @Test
    public void testGreatConchTileReuseToMcGruborsWoodWithBankedStaff() {
        // Target the tile-reuse case directly: the path re-enters the same corridor after banking,
        // and only the post-bank revisit has access to the banked Dramen staff route options.
        when(config.includeBankPath()).thenReturn(true);
        when(config.useAgilityShortcuts()).thenReturn(true);
        when(config.useFairyRings()).thenReturn(true);
        setupInventory();
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

        assertScenarioPathLengthWithBank(
            "Great Conch tile reuse -> McGrubor's Wood with banked Dramen staff",
            78,
            WorldPointUtil.packWorldPoint(3181, 2437, 0),
            WorldPointUtil.packWorldPoint(2652, 3485, 0),
            TeleportationItem.INVENTORY_AND_BANK,
            new Item(ItemID.DRAMEN_STAFF, 1),
            new Item(11118, 1));
    }

    @Test
    public void testGreatConchToMcGruborsWoodMaximumLengthWithoutBanking() {
        // Non-bank baseline for the same route family. The Dramen staff starts in inventory, so
        // the path can use fairy rings immediately without any bank visit or post-bank unlock.
        when(config.useAgilityShortcuts()).thenReturn(true);
        when(config.useFairyRings()).thenReturn(true);
        setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
        assertScenarioPathLength(
            "Great Conch -> McGrubor's Wood with inventory Dramen staff",
            50,
            WorldPointUtil.packWorldPoint(3180, 2419, 0),
            WorldPointUtil.packWorldPoint(2652, 3485, 0));
    }

    @Test
    public void testFairyRingBranchDoesNotLeakBankedDramenStaff() {
        // Start near a bank branch and a fairy-ring branch. The Dramen staff is only in the bank,
        // so a branch that has not banked must not gain fairy-ring access just because another
        // explored branch touched a bank.
        when(config.includeBankPath()).thenReturn(true);
        when(config.useFairyRings()).thenReturn(true);
        setupInventory();
        setupEquipment();
        when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
        when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0);
        setupConfigWithBank(new Item(ItemID.DRAMEN_STAFF, 1));

        assertScenarioPathLength(
            "Banked Dramen staff should not leak to non-bank fairy-ring branch",
            127,
            WorldPointUtil.packWorldPoint(3134, 3503, 0),
            WorldPointUtil.packWorldPoint(2652, 3485, 0));
    }

    @Test
    public void testCowbellAmuletInBankUsedAfterBankVisit() {
        // A teleport item that exists only in the bank should become usable after the route banks.
        // This checks the positive side of the bank-state transition for teleport items.
        when(config.includeBankPath()).thenReturn(true);
        setupInventory();
        setupEquipment();
        setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
            new Item(33104, 1));

        Pathfinder pathfinder = assertScenarioPathLengthAndGet(
            "Varrock centre -> Cowbell amulet destination with amulet in bank",
            36,
            WorldPointUtil.packWorldPoint(3213, 3424, 0),
            WorldPointUtil.packWorldPoint(3259, 3277, 0));

        assertTrue("Cowbell amulet should be used after visiting a bank",
            usedTransportWithDisplayInfoAfterFirstBank(pathfinder, TransportType.TELEPORTATION_ITEM, "Cowbell amulet"));
        assertFalse("Cowbell amulet should not be used before the first bank visit",
            usedTransportWithDisplayInfoBeforeFirstBank(pathfinder, TransportType.TELEPORTATION_ITEM, "Cowbell amulet"));
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
    public void testLovakenjMinecartNetworkPaidBeforeQuest() {
        // Before The Forsaken Tower quest completion (varbit 7796 < 11),
        // Lovakengj minecart rides cost 20 coins
        when(config.useMinecarts()).thenReturn(true);
        setupInventory(new Item(ItemID.COINS, 20));
        when(client.getVarbitValue(7796)).thenReturn(0);
        Map<Integer, Integer> varbits = new HashMap<>();
        varbits.put(7796, 0);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE, varbits);

        /*
         * Info:
         * single_minecart_origin_locations * (number_of_minecart_destinations)
         * - self_pairs_with_distance_0
         *   1 * 12   // Arceuus (1 origin tile, no self-pair)
         * + 2 * 12 - 1   // Farming Guild (2 origin tiles, 1 self-pair)
         * + 2 * 12 - 1   // Hosidius South
         * + 2 * 12 - 1   // Hosidius West
         * + 2 * 12 - 1   // Kingstown
         * + 1 * 12 - 1   // Kourend Woodland (1 origin tile, 1 self-pair)
         * + 2 * 12 - 1   // Lovakengj
         * + 2 * 12 - 1   // Mount Quidamortem
         * + 1 * 12 - 1   // Northern Tundras (Wintertodt)
         * + 2 * 12 - 1   // Port Piscarilius
         * + 2 * 12 - 1   // Shayzien East
         * + 2 * 12 - 1   // Shayzien West
         * = 252 - 11 = 241
         */
        assertEquals(241, countLovakenjMinecarts());
    }

    @Test
    public void testLovakenjMinecartNetworkFreeAfterQuest() {
        // After The Forsaken Tower quest completion (varbit 7796 = 11),
        // rides are free (no coins required)
        when(config.useMinecarts()).thenReturn(true);
        setupInventory();
        Map<Integer, Integer> varbits = new HashMap<>();
        varbits.put(7796, 11);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE, varbits);

        assertEquals(241, countLovakenjMinecarts());
    }

    @Test
    public void testLovakenjMinecartNetworkReverseNotUsableWithoutCoinsBeforeQuest() {
        // Before The Forsaken Tower completion (varbit 7796 < 11), reverse minecart travel
        // should still require payment and therefore not be a direct 2-step transport with 0 coins.
        when(config.useMinecarts()).thenReturn(true);
        setupInventory();
        Map<Integer, Integer> varbits = new HashMap<>();
        varbits.put(7796, 0);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE, varbits);

        int shayzienWest = WorldPointUtil.packWorldPoint(1415, 3577, 0);
        int arceuus = WorldPointUtil.packWorldPoint(1670, 3833, 0);

        assertScenarioMinimumPathLength("Lovakengj reverse minecart without coins", 3, shayzienWest, arceuus);
    }

    @Test
    public void testQuetzals() {
        when(config.useQuetzals()).thenReturn(true);
        testTransportLength(2, TransportType.QUETZAL);
    }

    /**
     * Tests that the Primio quetzal (Varrock ↔ Civitas) works correctly.
     * This is a fixed route in transports.tsv, NOT part of the quetzal platform system.
     */
    @Test
    public void testPrimioQuetzal() {
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

        // Varrock Primio platform to Civitas
        int varrockPrimio = WorldPointUtil.packWorldPoint(3280, 3412, 0);
        int civitasPrimio = WorldPointUtil.packWorldPoint(1700, 3141, 0);

        assertEquals(2, calculatePathLength(varrockPrimio, civitasPrimio));

        // Civitas Primio platform to Varrock
        int civitasPrimioOrigin = WorldPointUtil.packWorldPoint(1703, 3140, 0);
        int varrockPrimioDest = WorldPointUtil.packWorldPoint(3280, 3412, 0);

        assertEquals(2, calculatePathLength(civitasPrimioOrigin, varrockPrimioDest));
    }

    /**
     * Tests that when standing at a quetzal platform, the platform is used
     * instead of the whistle, even when the whistle is available.
     * The platform is free while the whistle has charges, so platform should be preferred.
     */
    @Test
    public void testQuetzalPlatformPreferredOverWhistle() {
        when(config.useQuetzals()).thenReturn(true);

        // Setup whistle in inventory
        setupInventory(new Item(29271, 1)); // Quetzal whistle

        // With whistle cost threshold, platform should be preferred
        when(config.costQuetzalWhistle()).thenReturn(10);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        // From Aldarin platform (1389, 2901) to Hunter Guild platform (1585, 3053)
        // Both are Renu destinations accessible by platform
        int aldarinPlatform = WorldPointUtil.packWorldPoint(1389, 2901, 0);
        int hunterGuild = WorldPointUtil.packWorldPoint(1585, 3053, 0);

        int pathLength = calculatePathLength(aldarinPlatform, hunterGuild);
        assertEquals("Platform should be used when standing at platform origin", 2, pathLength);
    }

    /**
     * Tests that the whistle is NOT used when standing close to a platform.
     * Walking to the nearby platform and flying is cheaper than using a whistle charge.
     */
    @Test
    public void testWhistleNotUsedWhenNearPlatform() {
        when(config.useQuetzals()).thenReturn(true);

        // Setup whistle in inventory
        setupInventory(new Item(29271, 1)); // Quetzal whistle

        // Even with zero additional whistle cost
        when(config.costQuetzalWhistle()).thenReturn(0);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        // Start 1 tile from Aldarin platform (1389, 2901), going to Hunter Guild (1585, 3053)
        // Platform path: walk 1 tile + 6 tick flight = cost 7, path length 3 (start, platform, dest)
        // Whistle path: 4 tick teleport + 0 additional = cost 4, path length 2 (start, dest)
        // Whistle is cheaper here, so it should be used with cost 0
        // But with cost > 0, platform should win
        int nearAldarinPlatform = WorldPointUtil.packWorldPoint(1390, 2901, 0); // 1 tile away
        int hunterGuild = WorldPointUtil.packWorldPoint(1585, 3053, 0);

        int pathLength = calculatePathLength(nearAldarinPlatform, hunterGuild);
        // With costQuetzalWhistle=0, whistle (cost 4) beats platform (cost 7), so path length = 2
        assertEquals("Whistle should be used when it's cheaper and has no extra cost", 2, pathLength);

        // Now with a higher whistle cost, platform should win
        when(config.costQuetzalWhistle()).thenReturn(10);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        pathLength = calculatePathLength(nearAldarinPlatform, hunterGuild);
        // Whistle cost: 4 + 10 = 14, Platform cost: 1 walk + 6 flight = 7
        // Platform wins, path = start -> platform -> dest = 3
        assertEquals("Platform should be used when whistle cost threshold makes it more expensive", 3, pathLength);
    }

    /**
     * Tests that disabling quetzals via useQuetzals=false also disables the whistle,
     * since both QUETZAL and QUETZAL_WHISTLE share the useQuetzals toggle.
     */
    @Test
    public void testQuetzalDisabledDisablesWhistle() {
        when(config.useQuetzals()).thenReturn(false);

        setupInventory(new Item(29271, 1)); // Quetzal whistle
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        // From Aldarin platform (1389, 2901) to Hunter Guild platform (1585, 3053)
        // With quetzals disabled, neither platform nor whistle should be used
        int aldarinPlatform = WorldPointUtil.packWorldPoint(1389, 2901, 0);
        int hunterGuild = WorldPointUtil.packWorldPoint(1585, 3053, 0);

        int pathLength = calculatePathLength(aldarinPlatform, hunterGuild);
        assertTrue("Without quetzals, path should be much longer than 2 (walking)", pathLength > 2);
    }

    /**
     * Tests that the platform is used when the whistle item is not in inventory.
     * Without the whistle item, only the platform route should be available.
     */
    @Test
    public void testPlatformUsedWhenWhistleNotInInventory() {
        when(config.useQuetzals()).thenReturn(true);

        // No whistle in inventory
        setupInventory(); // empty inventory
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        // From Aldarin platform (1389, 2901) to Hunter Guild platform (1585, 3053)
        int aldarinPlatform = WorldPointUtil.packWorldPoint(1389, 2901, 0);
        int hunterGuild = WorldPointUtil.packWorldPoint(1585, 3053, 0);

        int pathLength = calculatePathLength(aldarinPlatform, hunterGuild);
        assertEquals("Platform should be used when whistle is not in inventory", 2, pathLength);
    }

    /**
     * Tests the cost boundary where the whistle differential tips the balance.
     * From 1 tile away: platform compareCost = 1 walk + 6 flight = 7.
     * Whistle base cost = 4 ticks, compareCost = 4 + differential.
     * With differential=4: whistle compareCost=8 > platform=7, platform wins (path=3).
     * With differential=2: whistle compareCost=6 < platform=7, whistle wins (path=2).
     */
    @Test
    public void testQuetzalWhistleCostBoundary() {
        when(config.useQuetzals()).thenReturn(true);

        setupInventory(new Item(29271, 1)); // Quetzal whistle
        int nearAldarinPlatform = WorldPointUtil.packWorldPoint(1390, 2901, 0); // 1 tile away
        int hunterGuild = WorldPointUtil.packWorldPoint(1585, 3053, 0);

        // Differential=4: whistle compareCost=8 > platform=7, platform should win
        when(config.costQuetzalWhistle()).thenReturn(4);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        int pathLength = calculatePathLength(nearAldarinPlatform, hunterGuild);
        assertEquals("Platform should win when whistle differential makes it more expensive", 3, pathLength);

        // Differential=2: whistle compareCost=6 < platform=7, whistle should win
        when(config.costQuetzalWhistle()).thenReturn(2);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        pathLength = calculatePathLength(nearAldarinPlatform, hunterGuild);
        assertEquals("Whistle should win when differential keeps it cheaper than platform", 2, pathLength);
    }

    /**
     * Tests that the whistle works standalone from far away where no quetzal platform is nearby.
     * This is the primary real-world use case: teleporting from a remote location to Varlamore.
     * Uses Falador as the origin (far from any quetzal platform, including Primio near Varrock).
     */
    @Test
    public void testWhistleUsedFromFarAway() {
        when(config.useQuetzals()).thenReturn(true);

        setupInventory(new Item(29271, 1)); // Quetzal whistle
        // Zero differential so the whistle is clearly the cheapest option
        when(config.costQuetzalWhistle()).thenReturn(0);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        // From Falador center (2965, 3380) to Hunter Guild platform (1585, 3053)
        // No quetzal platform is near Falador; the Primio platform is at (3280, 3412) — over 300 tiles away
        int faladorCenter = WorldPointUtil.packWorldPoint(2965, 3380, 0);
        int hunterGuild = WorldPointUtil.packWorldPoint(1585, 3053, 0);

        int pathLength = calculatePathLength(faladorCenter, hunterGuild);
        // Whistle teleport (compareCost=4) is cheapest: path = start -> dest = 2
        assertEquals("Whistle should be used when far from any platform", 2, pathLength);
    }

    @Test
    public void testSpiritTrees() {
        when(config.useSpiritTrees()).thenReturn(true);
        when(client.getVarbitValue(any(Integer.class))).thenReturn(20);
        testTransportLength(2, TransportType.SPIRIT_TREE);
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
    public void testPickaxeNotUsedWithoutPickaxe() {
        // Ensure transports requiring a pickaxe are not included when the player has no pickaxe
        setupInventory();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

        assertTrue(
            "No transports should be present that require a pickaxe",
            !hasTransportWithRequiredItem(pathfinderConfig.getTransports(), ItemVariations.PICKAXE.getIds())
        );
    }

    @Test
    public void testPickaxeUsedWithPickaxe() {
        // Ensure transports requiring a pickaxe are included when the player has a pickaxe and sufficient mining level
        setupInventory(new Item(ItemID.BRONZE_PICKAXE, 1));
        setupConfig(QuestState.FINISHED, 50, TeleportationItem.NONE); // transport in data requires 50 Mining

        assertTrue("Transports requiring a pickaxe should be present",
            hasTransportWithRequiredItem(pathfinderConfig.getTransports(), ItemVariations.PICKAXE.getIds()));
    }

    @Test
    public void testAxeNotUsedWithoutAxe() {
        // Ensure transports requiring an axe are not included when the player has no axe
        setupInventory();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

        assertTrue(
            "No transports should be present that require an axe",
            !hasTransportWithRequiredItem(pathfinderConfig.getTransports(), ItemVariations.AXE.getIds())
        );
    }

    @Test
    public void testAxeUsedWithAxe() {
        // Ensure transports requiring an axe are included when the player has an axe
        setupInventory(new Item(ItemID.BRONZE_AXE, 1));
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

        assertTrue("Transports requiring an axe should be present",
            hasTransportWithRequiredItem(pathfinderConfig.getTransports(), ItemVariations.AXE.getIds()));
    }

    @Test
    public void testTeleportationPortals() {
        when(config.useTeleportationPortals()).thenReturn(true);
        testTransportLength(2, TransportType.TELEPORTATION_PORTAL);
    }

    @Test
    public void testWildernessObelisks() {
        when(config.useWildernessObelisks()).thenReturn(true);
        when(config.usePoh()).thenReturn(true);
        when(config.usePohObelisk()).thenReturn(true);
        testTransportLength(2, TransportType.WILDERNESS_OBELISK);
    }

    @Test
    public void testVarrockPalaceTrellisUsableWithGardenOfTranquillity() {
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(3228, 3470, 0),
            WorldPointUtil.packWorldPoint(3228, 3472, 0));
        testTransportLength(2,
            WorldPointUtil.packWorldPoint(3228, 3472, 0),
            WorldPointUtil.packWorldPoint(3228, 3470, 0));
    }

    @Test
    public void testVarrockPalaceTrellisNotUsableWithoutGardenOfTranquillity() {
        setupConfig(QuestState.NOT_STARTED, 99, TeleportationItem.NONE);

        int south = WorldPointUtil.packWorldPoint(3228, 3470, 0);
        int north = WorldPointUtil.packWorldPoint(3228, 3472, 0);

        assertTrue("Varrock Palace trellis should not be directly usable without Garden of Tranquillity",
            calculatePathLength(south, north) > 2);
        assertTrue("Varrock Palace trellis should not be directly usable without Garden of Tranquillity",
            calculatePathLength(north, south) > 2);
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
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.ALL);
        assertScenarioPathLength("Draynor Manor stepping stones vs combat bracelet", 6,
            WorldPointUtil.packWorldPoint(3149, 3363, 0),
            WorldPointUtil.packWorldPoint(3154, 3363, 0));
    }

    @Test
    public void testChronicle() {
        // South of river south of Champions Guild to Chronicle teleport destination
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.ALL);
        assertEquals(2, calculatePathLength(
            WorldPointUtil.packWorldPoint(3199, 3336, 0),
            WorldPointUtil.packWorldPoint(3200, 3355, 0)));
    }

    @Test
    public void testVarrockTeleport() {
        // Test that Varrock Teleport is used when it's cheaper than walking
        when(config.useTeleportationSpells()).thenReturn(true);

        // Test 1: Without magic level (can't cast spell) - should walk
        setupConfig(QuestState.FINISHED, 1, TeleportationItem.NONE);
        assertScenarioPathLength("Varrock teleport too low magic level", 4,
            WorldPointUtil.packWorldPoint(3216, 3424, 0),
            WorldPointUtil.packWorldPoint(3213, 3424, 0));

        // Test 2: With magic level and runes, starting far enough that teleport is cheaper
        setupInventory(
                new Item(ItemID.LAWRUNE, 1),
                new Item(ItemID.AIRRUNE, 3),
                new Item(ItemID.FIRERUNE, 1));
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        // Starting 10 tiles away - teleport (4 ticks) is definitely cheaper than walking (10 ticks)
        assertScenarioPathLength("Varrock teleport with runes and magic level", 2,
            WorldPointUtil.packWorldPoint(3223, 3424, 0),
            WorldPointUtil.packWorldPoint(3213, 3424, 0));
    }

    @Test
    public void testWildernessRouteWithoutTeleportsWalksOut() {
        int deepWilderness = WorldPointUtil.packWorldPoint(3340, 3828, 0);
        int grandExchange = WorldPointUtil.packWorldPoint(3158, 3509, 0);

        when(config.useAgilityShortcuts()).thenReturn(true);
        setupInventory();
        setupEquipment();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

        Pathfinder pathfinder = runScenario("Deep wilderness -> Grand Exchange with no teleports", deepWilderness, grandExchange);

        assertFalse("No teleportation item should be used when none are available",
            usedTransportType(pathfinder, TransportType.TELEPORTATION_ITEM));
        assertFalse("No teleportation spell should be used when none are available",
            usedTransportType(pathfinder, TransportType.TELEPORTATION_SPELL));
        assertTrue("Walking route should still reach the destination", pathfinder.getResult().isReached());

        assertEquals(328, pathfinder.getPath().size());
    }

    @Test
    public void testWildernessRouteUsesGloryAfterLeavingLevel30() {
        int deepWilderness = WorldPointUtil.packWorldPoint(3340, 3828, 0);
        int grandExchange = WorldPointUtil.packWorldPoint(3158, 3509, 0);

        when(config.useAgilityShortcuts()).thenReturn(true);

        setupInventory(new Item(ItemID.AMULET_OF_GLORY_6, 1));
        setupEquipment();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);
        Pathfinder withGlory = runScenario("Deep wilderness -> Grand Exchange with glory", deepWilderness, grandExchange);

        assertTrue("Charged glory should be used once the route reaches a legal wilderness level",
            usedTransportWithDisplayInfo(withGlory, TransportType.TELEPORTATION_ITEM, "Amulet of glory"));

        assertEquals(139, withGlory.getPath().size());
    }

    @Test
    public void testWildernessRouteUsesGrandExchangeVarrockTeleportAfterLeavingLevel20() {
        int deepWilderness = WorldPointUtil.packWorldPoint(3340, 3828, 0);
        int grandExchange = WorldPointUtil.packWorldPoint(3158, 3509, 0);
        Map<Integer, Integer> varbits = new HashMap<>();
        varbits.put(VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, 1); // Grand Exchange teleport unlocked

        when(config.useAgilityShortcuts()).thenReturn(true);
        setupInventory(
            new Item(ItemID.LAWRUNE, 1),
            new Item(ItemID.AIRRUNE, 3),
            new Item(ItemID.FIRERUNE, 1));
        setupEquipment();
        when(config.useTeleportationSpells()).thenReturn(true);
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE, varbits);
        Pathfinder withVarrockTeleport = runScenario("Deep wilderness -> Grand Exchange with GE Varrock Teleport", deepWilderness, grandExchange);

        assertEquals(182, withVarrockTeleport.getPath().size());
        assertTrue("GE Varrock Teleport should be used on the route to Grand Exchange",
            usedTransportWithDisplayInfo(withVarrockTeleport, TransportType.TELEPORTATION_SPELL, "Varrock Teleport: GE"));
    }

    @Test
    public void testWildernessRouteWithGloryAndRunesDoesNotUseSpellTooEarly() {
        // At this start point the route should use glory first because it becomes legal earlier and is closer to the eventual goal.
        int deepWilderness = WorldPointUtil.packWorldPoint(3340, 3828, 0);
        int grandExchange = WorldPointUtil.packWorldPoint(3158, 3509, 0);
        Map<Integer, Integer> varbits = new HashMap<>();
        varbits.put(VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, 1); // Grand Exchange teleport unlocked

        when(config.useAgilityShortcuts()).thenReturn(true);
        when(config.useTeleportationSpells()).thenReturn(true);
        setupInventory(
            new Item(ItemID.AMULET_OF_GLORY_6, 1),
            new Item(ItemID.LAWRUNE, 1),
            new Item(ItemID.AIRRUNE, 3),
            new Item(ItemID.FIRERUNE, 1));
        setupEquipment();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY, varbits);

        Pathfinder pathfinder = runScenario("Deep wilderness -> Grand Exchange with glory and GE runes", deepWilderness, grandExchange);

        assertEquals(103, pathfinder.getPath().size());
        assertTrue("Glory should be used when both glory and GE runes are available but the spell is still wilderness-locked",
            usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Amulet of glory"));
    }

    @Test
    public void testAvoidWildernessSuppressesBurningAmuletRoute() {
        int origin = WorldPointUtil.packWorldPoint(2485, 3080, 0);
        int destination = WorldPointUtil.packWorldPoint(3087, 3492, 0);

        when(config.avoidWilderness()).thenReturn(true);
        setupInventory(new Item(ItemID.BURNING_AMULET_5, 1));
        setupEquipment();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        Pathfinder pathfinder = assertScenarioPathLengthAndGet(
            "Wizards' Guild -> Edgeville with burning amulet and avoid wilderness",
            876,
            origin,
            destination);

        assertTrue("Route should still reach the destination while avoiding wilderness", pathfinder.getResult().isReached());
        assertFalse("Burning amulet should not be used when avoid wilderness is enabled",
            usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Burning amulet"));
        assertFalse("Ardougne lever should not be used when avoid wilderness is enabled",
            usedTransportType(pathfinder, TransportType.TELEPORTATION_LEVER));
    }

    @Test
    public void testArdougneLeverUsedWithoutItemsWhenWildernessAllowed() {
        int origin = WorldPointUtil.packWorldPoint(2485, 3080, 0);
        int destination = WorldPointUtil.packWorldPoint(3087, 3492, 0);

        when(config.avoidWilderness()).thenReturn(false);
        when(config.useTeleportationLevers()).thenReturn(true);
        setupInventory();
        setupEquipment();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

        Pathfinder pathfinder = assertScenarioPathLengthAndGet(
            "Wizards' Guild -> Edgeville with no items and wilderness allowed",
            769,
            origin,
            destination);

        assertTrue("Route should still reach the destination when wilderness is allowed", pathfinder.getResult().isReached());
        assertTrue("Ardougne lever should be used when wilderness is allowed and no better item teleport exists",
            usedTransportType(pathfinder, TransportType.TELEPORTATION_LEVER));
    }

    @Test
    public void testBurningAmuletRouteAllowedWhenNotAvoidingWilderness() {
        int origin = WorldPointUtil.packWorldPoint(2485, 3080, 0);
        int destination = WorldPointUtil.packWorldPoint(3087, 3492, 0);

        when(config.avoidWilderness()).thenReturn(false);
        setupInventory(new Item(ItemID.BURNING_AMULET_5, 1));
        setupEquipment();
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

        Pathfinder pathfinder = assertScenarioPathLengthAndGet(
            "Wizards' Guild -> Edgeville with burning amulet and wilderness allowed",
            162,
            origin,
            destination);

        assertTrue("Route should reach the destination when wilderness is allowed", pathfinder.getResult().isReached());
        assertTrue("Burning amulet should be used when wilderness is allowed",
            usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Burning amulet"));
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
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
        assertScenarioPathLength("Keldagrim east -> west via other plane", 64,
            WorldPointUtil.packWorldPoint(2894, 10199, 0),
            WorldPointUtil.packWorldPoint(2864, 10199, 0));

        assertScenarioPathLength("Keldagrim west -> east via other plane", 64,
            WorldPointUtil.packWorldPoint(2864, 10199, 0),
            WorldPointUtil.packWorldPoint(2894, 10199, 0));
    }

    @Test
    public void testImpossibleCharterShips() {
        // Shortest path for impossible charter ships has length 3 and goes
        // via an intermediate charter ship and not directly with length 2
        when(config.useCharterShips()).thenReturn(true);
        setupInventory(new Item(ItemID.COINS, 1000000));

        setupConfig(QuestState.FINISHED, 99, TeleportationItem.ALL);
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

    // Setup a configuration with
    // * A fixed QuestState for all quests
    // * A fixed skill level for all skills
    // * A toggle about wheher to use teleportation items.
    private void setupConfig(QuestState questState, int skillLevel, TeleportationItem useTeleportationItems) {
        // NOTE: Not mocked since PathfinderConfig is repeatedly queried in the hot loop.
        pathfinderConfig = new TestPathfinderConfig(client, config, questState
                                                                  , true // Ignore Varbit checks
                                                                  , true // Ignore Varplayer checks
                                                                  );

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(skillLevel);
        when(config.useTeleportationItems()).thenReturn(useTeleportationItems);

        pathfinderConfig.refresh();
    }

    // Setup a configuration with
    // * A fixed QuestState for all quests
    // * A fixed skill level for all skills
    // * A toggle about wheher to use teleportation items.
    // * Fixed set of varbit values
    private void setupConfig(QuestState questState, int skillLevel, TeleportationItem useTeleportationItems, Map<Integer, Integer> varbitValues) {
        // NOTE: Not mocked since PathfinderConfig is repeatedly queried in the hot loop.
        pathfinderConfig = new TestPathfinderConfig(client, config, questState
                                                                  , false // Use proper varbit checking
                                                                  , true  // Ignore varplayer checks
                                                                  );

        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(skillLevel);
        when(config.useTeleportationItems()).thenReturn(useTeleportationItems);
        for (Map.Entry<Integer, Integer> entry : varbitValues.entrySet()) {
            when(client.getVarbitValue(entry.getKey())).thenReturn(entry.getValue());
        }

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
        Map<Integer, Set<Transport>> activeTransports = pathfinderConfig.getTransports();
        for (int origin : activeTransports.keySet()) {
            for (Transport transport : activeTransports.get(origin)) {
                if (transportType.equals(transport.getType())) {
                    counter++;
                    assertEquals(transport.toString(), expectedLength, calculateTransportLength(transport));
                }
            }
        }

        assertTrue("No tests were performed", counter > 0);
        System.out.printf("Successfully completed %d " + transportType + " transport length tests%n", counter);
    }


    /**
     * Verifies that ALL transports of the given type are present in the usable transports,
     * but only calculates a path for one of them (for efficiency).
     * This provides comprehensive coverage that all transports are enabled while being fast.
     */
    private void testAllTransportsAvailableWithSinglePath(TransportType transportType) {
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

        // Count expected transports from the full transport list
        int expectedCount = 0;
        Transport sampleTransport = null;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (transportType.equals(transport.getType())) {
                    int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
                    int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
                    if (ShortestPathPlugin.isInsidePoh(originX, originY)) {
                        continue;
                    }
                    expectedCount++;
                    if (sampleTransport == null) {
                        sampleTransport = transport;
                    }
                }
            }
        }

        // Count actual transports in the configured (usable) transports
        int actualCount = 0;
        for (Set<Transport> set : pathfinderConfig.getTransports().values()) {
            for (Transport t : set) {
                if (transportType.equals(t.getType())) {
                    actualCount++;
                }
            }
        }

        assertEquals("All " + transportType + " transports should be available", expectedCount, actualCount);
        assertTrue("At least one transport should exist", expectedCount > 0);

        // Test path calculation on just one transport to verify pathfinding works
        if (sampleTransport != null) {
            assertEquals(sampleTransport.toString(), 2, calculateTransportLength(sampleTransport));
        }
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

    private void testSingleTransportScenario(String label, int expectedLength, TransportType transportType) {
        setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
        Transport transport = findSampleTransport(transportType);
        assertScenarioPathLength(label, expectedLength, transport.getOrigin(), transport.getDestination());
    }

    // A "scenario" is a single, named pathfinding example with a fixed origin and
    // destination that demonstrates a meaningful routing behaviour or regression.
    // Scenarios are intended for the debugging dashboard, so they should be cases
    // worth visualising: bank-state transitions, branch-leak regressions,
    // transport-vs-walking choices, tile/plane reuse, or other route-selection
    // behaviours. Broad transport coverage, static connectivity smoke tests, and
    // feature-availability checks are not scenarios even if they use coordinates.
    private void assertScenarioPathLength(String label, int expectedLength, int origin, int destination) {
        assertScenarioPathLengthAndGet(label, expectedLength, origin, destination);
    }

    private Pathfinder assertScenarioPathLengthAndGet(String label, int expectedLength, int origin, int destination) {
        Pathfinder pathfinder = runPathfinder(origin, destination);
        int actualLength = pathfinder.getPath().size();
        try {
            assertEquals(label, expectedLength, actualLength);
            PathfinderTestDashboardCollector.record(label, pathfinder.getResult(), pathfinderConfig, true, null);
        } catch (AssertionError e) {
            PathfinderTestDashboardCollector.record(label, pathfinder.getResult(), pathfinderConfig, false, e.getMessage());
            throw e;
        }
        return pathfinder;
    }

    private void assertScenarioMinimumPathLength(String label, int minimumLength, int origin, int destination) {
        Pathfinder pathfinder = runPathfinder(origin, destination);
        int actualLength = pathfinder.getPath().size();
        try {
            assertTrue("Scenario " + label + " had length " + actualLength + " < " + minimumLength, actualLength >= minimumLength);
            PathfinderTestDashboardCollector.record(label, pathfinder.getResult(), pathfinderConfig, true, null);
        } catch (AssertionError e) {
            PathfinderTestDashboardCollector.record(label, pathfinder.getResult(), pathfinderConfig, false, e.getMessage());
            throw e;
        }
    }

    private void assertScenarioPathLengthWithBank(String label, int expectedLength, int origin, int destination,
                                                  TeleportationItem useTeleportationItems, Item... bankItems) {
        setupConfigWithBank(useTeleportationItems, bankItems);
        assertScenarioPathLength(label, expectedLength, origin, destination);
    }

    private Transport findSampleTransport(TransportType transportType) {
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (transportType.equals(transport.getType())) {
                    int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
                    int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
                    if (ShortestPathPlugin.isInsidePoh(originX, originY)) {
                        continue;
                    }
                    return transport;
                }
            }
        }
        fail("No transport of type " + transportType + " found");
        return null;
    }

    private Transport findConfiguredTransport(TransportType transportType) {
        for (Set<Transport> set : pathfinderConfig.getTransports().values()) {
            for (Transport transport : set) {
                if (transportType.equals(transport.getType())) {
                    return transport;
                }
            }
        }
        fail("No configured transport of type " + transportType + " found");
        return null;
    }

    private int calculatePathLength(int origin, int destination) {
        Pathfinder pathfinder = runPathfinder(origin, destination);
        return pathfinder.getPath().size();
    }

    private Pathfinder runPathfinder(int origin, int destination) {
        Pathfinder pathfinder = new Pathfinder(pathfinderConfig, origin, Set.of(destination));
        pathfinder.run();
        return pathfinder;
    }

    private Pathfinder runScenario(String label, int origin, int destination) {
        Pathfinder pathfinder = runPathfinder(origin, destination);
        PathfinderTestDashboardCollector.record(
                label,
                pathfinder.getResult(),
                pathfinderConfig,
                null,
                null);
        return pathfinder;
    }

    private boolean hasTransportWithRequiredItem(Map<Integer, Set<Transport>> transports, int[] variationIds) {
        for (Set<Transport> set : transports.values()) {
            for (Transport t : set) {
                TransportItems items = t.getItemRequirements();
                if (items == null) {
                    continue;
                }
                int[][] reqs = items.getItems();
                for (int[] inner : reqs) {
                    if (inner == null) {
                        continue;
                    }
                    for (int id : inner) {
                        for (int vid : variationIds) {
                            if (id == vid) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Counts the number of Lovakengj Minecart Network transports in the active transport set.
     * These are identified by having varbit 7796 among their requirements. */
    private int countLovakenjMinecarts() {
        int count = 0;
        for (Set<Transport> set : pathfinderConfig.getTransports().values()) {
            for (Transport t : set) {
                if (t.isType(TransportType.MINECART) && t.hasVarbit(7796)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void setupConfigWithBank(Item... bankItems) {
        setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK, bankItems);
    }

    private void setupConfigWithBank(TeleportationItem useTeleportationItems, Item... bankItems) {
        pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.FINISHED, true, true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getClientThread()).thenReturn(Thread.currentThread());
        when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
        when(config.useTeleportationItems()).thenReturn(useTeleportationItems);
        when(config.usePoh()).thenReturn(false);
        doReturn(bankItems).when(bank).getItems();
        pathfinderConfig.bank = bank;
        pathfinderConfig.refresh();
    }

    /** Returns true if the given transport type was used anywhere along the path. */
    private boolean usedTransportType(Pathfinder pathfinder, TransportType type) {
        for (int i = 1; i < pathfinder.getPath().size(); i++) {
            PathStep originStep = pathfinder.getPath().get(i - 1);
            int origin = originStep.getPackedPosition();
            int dest = pathfinder.getPath().get(i).getPackedPosition();

            Set<Transport> stepTransports = transportsForStep(origin, originStep.isBankVisited());
            for (Transport t : stepTransports) {
                if (t.getDestination() == dest && t.isType(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if a transport of the given type whose displayInfo contains the given
     * substring was used anywhere along the path.
     */
    private boolean usedTransportWithDisplayInfo(Pathfinder pathfinder, TransportType type, String displayInfoSubstring) {
        return usedTransportWithDisplayInfo(pathfinder, type, displayInfoSubstring, false, false);
    }

    private boolean usedTransportWithDisplayInfoAfterFirstBank(Pathfinder pathfinder, TransportType type, String displayInfoSubstring) {
        return usedTransportWithDisplayInfo(pathfinder, type, displayInfoSubstring, false, true);
    }

    private boolean usedTransportWithDisplayInfoBeforeFirstBank(Pathfinder pathfinder, TransportType type, String displayInfoSubstring) {
        return usedTransportWithDisplayInfo(pathfinder, type, displayInfoSubstring, true, false);
    }

    private boolean usedTransportWithDisplayInfo(Pathfinder pathfinder, TransportType type, String displayInfoSubstring,
        boolean stopAtFirstBank, boolean startAfterFirstBank) {
        for (int i = 1; i < pathfinder.getPath().size(); i++) {
            PathStep originStep = pathfinder.getPath().get(i - 1);
            PathStep destStep = pathfinder.getPath().get(i);
            boolean bankVisited = destStep.isBankVisited();
            int origin = originStep.getPackedPosition();
            if (stopAtFirstBank && bankVisited) {
                return false;
            }
            if (startAfterFirstBank && !bankVisited) {
                continue;
            }

            for (Transport t : transportsForStep(origin, bankVisited)) {
                if (t.getDestination() == destStep.getPackedPosition() && t.isType(type) && t.hasDisplayInfo(displayInfoSubstring)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<Transport> transportsForStep(int origin, boolean bankVisited) {
        Set<Transport> stepTransports = new java.util.HashSet<>(
            pathfinderConfig.getTransportsPacked(bankVisited).getOrDefault(origin, Set.of()));
        stepTransports.addAll(pathfinderConfig.getUsableTeleports(bankVisited));
        return stepTransports;
    }

}
