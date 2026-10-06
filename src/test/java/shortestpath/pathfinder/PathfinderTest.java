package shortestpath.pathfinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
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
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.requirement.TransportItems;

@SuppressWarnings("SameParameterValue")
@RunWith(MockitoJUnitRunner.class)
public class PathfinderTest
{
	private static final Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();
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
	private PathfinderConfig pathfinderConfig;

	@Before
	public void before()
	{
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.collisionAwareBlockedTargets()).thenReturn(true);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
	}

	@Test
	public void testAgilityShortcuts()
	{
		when(config.useAgilityShortcuts()).thenReturn(true);
		setupInventory(
			new Item(ItemID.ROPE, 1),
			new Item(ItemID.DEATH_CLIMBINGBOOTS, 1));
		testTransportLength(2, TransportType.AGILITY_SHORTCUT);
	}

	@Test
	public void testGrappleShortcuts()
	{
		when(config.useGrappleShortcuts()).thenReturn(true);
		setupInventory(
			new Item(ItemID.XBOWS_CROSSBOW_ADAMANTITE, 1),
			new Item(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE, 1));
		testTransportLength(2, TransportType.GRAPPLE_SHORTCUT);
	}

	@Test
	public void testGrappleBranchDoesNotLeakBankedMithGrapple()
	{
		// The crossbow is already on hand, but the mith grapple is only in the bank.
		// This should expose any branch leakage where one explored bank path makes the
		// grapple shortcut appear usable on a different branch that never banked.
		when(config.useGrappleShortcuts()).thenReturn(true);
		when(config.includeBankPath()).thenReturn(true);
		when(config.useAgilityShortcuts()).thenReturn(true);
		setupInventory(new Item(ItemID.XBOWS_CROSSBOW_ADAMANTITE, 1));
		setupEquipment();
		setupConfigWithBank(new Item(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE, 1));
		// Keep every skill below the barehanded wall-climb requirement (52
		// Agility) so the only way over the wall is still the grapple and the
		// scenario continues to exercise the banked-grapple branch.
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(40);
		pathfinderConfig.refresh();

		assertScenarioPathLength(
			"Banked mith grapple should not leak to non-bank grapple branch",
			66,
			WorldPointUtil.packWorldPoint(3025, 3365, 0),
			WorldPointUtil.packWorldPoint(3026, 3393, 0));
	}

	@Test
	public void testBoats()
	{
		when(config.useBoats()).thenReturn(true);
		setupInventory(
			new Item(ItemID.COINS, 10000),
			new Item(ItemID.ECTOTOKEN, 25));
		testTransportLength(2, TransportType.BOAT);
	}

	@Test
	public void testCanoes()
	{
		when(config.useCanoes()).thenReturn(true);
		setupInventory(new Item(ItemID.BRONZE_AXE, 1));
		testTransportLength(2, TransportType.CANOE);
	}

	@Test
	public void testCanoesWithStoredAxeUnlock()
	{
		// An axe stored at a canoe station relieves the carried-axe requirement.
		when(config.useCanoes()).thenReturn(true);
		when(config.unlockCanoeAxe()).thenReturn(true);
		setupInventory();
		testTransportLength(2, TransportType.CANOE);
	}

	@Test
	public void testCanoesNotUsedWithoutAxeOrDeclaredUnlock()
	{
		when(config.useCanoes()).thenReturn(true);
		setupInventory();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		for (Transport transport : activeTransportList())
		{
			assertNotEquals("No canoe may be usable without an axe or the declared stored axe",
				TransportType.CANOE, transport.getType());
		}
	}

	@Test
	public void testDragontoothBoatWithDeclaredFreePassage()
	{
		// The permanent free passage relieves the ecto-token toll on the
		// Port Phasmatys -> Dragontooth Island boat.
		when(config.useBoats()).thenReturn(true);
		when(config.unlockDragontoothPassage()).thenReturn(true);
		setupInventory();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		assertTrue("Dragontooth boat should be usable with the declared free passage",
			hasTransportTo(pathfinderConfig.getTransports(),
				WorldPointUtil.packWorldPoint(3703, 3487, 0),
				WorldPointUtil.packWorldPoint(3792, 3560, 0)));
	}

	@Test
	public void testDragontoothBoatNotUsedWithoutTokensOrDeclaredUnlock()
	{
		when(config.useBoats()).thenReturn(true);
		setupInventory();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		assertFalse("Dragontooth boat must stay gated without ecto-tokens or the declared unlock",
			hasTransportTo(pathfinderConfig.getTransports(),
				WorldPointUtil.packWorldPoint(3703, 3487, 0),
				WorldPointUtil.packWorldPoint(3792, 3560, 0)));
	}

	@Test
	public void testDragontoothBoatStillUsableWithEctoTokens()
	{
		// The item branch still covers the crossing: the unlock relief is additive, not exclusive.
		when(config.useBoats()).thenReturn(true);
		setupInventory(new Item(ItemID.ECTOTOKEN, 25));
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		assertTrue("Dragontooth boat should still be usable with 25 ecto-tokens",
			hasTransportTo(pathfinderConfig.getTransports(),
				WorldPointUtil.packWorldPoint(3703, 3487, 0),
				WorldPointUtil.packWorldPoint(3792, 3560, 0)));
	}

	@Test
	public void unlockOptionKeysMatchTransportOptionsRegex() throws Exception
	{
		// An unlock* config key must retrigger pathfinding like other transport options.
		java.lang.reflect.Field field = ShortestPathPlugin.class.getDeclaredField("TRANSPORT_OPTIONS_REGEX");
		field.setAccessible(true);
		java.util.regex.Pattern pattern = (java.util.regex.Pattern) field.get(null);
		assertTrue(pattern.matcher("unlockCanoeAxe").matches());
		assertTrue(pattern.matcher("unlockXericsHonour").matches());
		assertTrue(pattern.matcher("unlockDragontoothPassage").matches());
		assertFalse(pattern.matcher("unlock").matches());
		assertFalse(pattern.matcher("myUnlockCanoeAxe").matches());
	}

	private static final String XERICS_HONOUR = "Xeric's talisman: 5. Xeric's Honour";
	private static final String XERICS_LOOKOUT = "Xeric's talisman: 1. Xeric's Lookout";

	@Test
	public void xericsHonourIsGatedBehindTheDeclaredUnlockInEveryItemMode()
	{
		// The ancient-tablet unlock is a gate, not an item: modes that skip item
		// evaluation entirely (ALL/UNLOCKED early-return before hasRequiredItems)
		// must still refuse the Honour destination while it is undeclared. The
		// ungated sibling destinations stay usable as the control.
		for (TeleportationItem mode : new TeleportationItem[]{
			TeleportationItem.INVENTORY,
			TeleportationItem.INVENTORY_AND_BANK,
			TeleportationItem.UNLOCKED,
			TeleportationItem.ALL})
		{
			setupInventory(new Item(ItemID.XERIC_TALISMAN, 1));
			setupConfig(QuestState.FINISHED, 99, mode);
			assertFalse("Honour offered under " + mode + " without the declared unlock",
				hasUsableTeleport(XERICS_HONOUR));
			assertTrue("ungated Xeric's destination missing under " + mode,
				hasUsableTeleport(XERICS_LOOKOUT));
		}
	}

	@Test
	public void xericsHonourIsUsableWhenTheUnlockIsDeclared()
	{
		when(config.unlockXericsHonour()).thenReturn(true);
		setupInventory(new Item(ItemID.XERIC_TALISMAN, 1));
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);
		assertTrue(hasUsableTeleport(XERICS_HONOUR));
	}

	@Test
	public void xericsHonourStillNeedsTheTalismanWhenUnlocked()
	{
		// The unlock opens the destination; it does not conjure the talisman.
		when(config.unlockXericsHonour()).thenReturn(true);
		setupInventory();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);
		assertFalse(hasUsableTeleport(XERICS_HONOUR));
	}

	@Test
	public void xericsHonourUnderAllModeNeedsOnlyTheDeclaredUnlock()
	{
		// ALL presumes every item including the talisman, so the declared unlock
		// is the only remaining gate.
		when(config.unlockXericsHonour()).thenReturn(true);
		setupInventory();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.ALL);
		assertTrue(hasUsableTeleport(XERICS_HONOUR));
	}

	@Test
	public void pohHonourXericsTalismanIsGatedByTheDeclaredUnlock()
	{
		// The POH Honour mounted-talisman row has no Items column to carry the
		// unlock term, so the gate is checked in code — regardless of the
		// jewellery-box tier, which only covers actual jewellery boxes.
		when(config.usePoh()).thenReturn(true);
		when(config.pohNexusPortals()).thenReturn(Set.of());
		when(config.pohJewelleryBoxTier()).thenReturn(JewelleryBoxTier.NONE);
		when(config.pohMountedItems()).thenReturn(EnumSet.of(PohMountedItem.XERICS_TALISMAN));
		setupInventory();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

		assertFalse("POH Honour box offered without the declared unlock",
			hasActiveTeleportationBox("Honour"));
		assertTrue("ungated POH Xeric's destinations stay usable",
			hasActiveTeleportationBox("Lookout"));

		when(config.unlockXericsHonour()).thenReturn(true);
		pathfinderConfig.refresh();
		assertTrue("POH Honour box usable once the unlock is declared",
			hasActiveTeleportationBox("Honour"));
		assertTrue("ungated POH Xeric's destinations stay usable",
			hasActiveTeleportationBox("Lookout"));
	}

	private boolean hasActiveTeleportationBox(String displayInfo)
	{
		for (Transport t : activeTransportList())
		{
			if (t.isType(TransportType.TELEPORTATION_BOX) && t.hasDisplayInfo(displayInfo))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void testCharterShips()
	{
		when(config.useCharterShips()).thenReturn(true);
		setupInventory(new Item(ItemID.COINS, 100000));
		testTransportLength(2, TransportType.CHARTER_SHIP);
	}

	@Test
	public void testCatherbyCharterReusedAfterBankVisitWithBankedCoins()
	{
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
	public void testCatherbyBankBranchDoesNotLeakCoinsToCharterBranch()
	{
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
	public void testInventoryAndBankModeImpliesBankPath()
	{
		// A banked camulet with INVENTORY_AND_BANK item mode but includeBankPath off.
		// Bank contents only become usable in the bankVisited path state, so the
		// items mode must imply bank-path traversal: the route visits a bank, then
		// uses the banked camulet instead of the long foot detour into the temple.
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK, new Item(ItemID.CAMULET, 1));

		Pathfinder pathfinder = runPathfinder(
			WorldPointUtil.packWorldPoint(3160, 3486, 0),
			WorldPointUtil.packWorldPoint(3105, 9315, 0));

		assertTrue(
			"expected path to reach Enakhra's Temple",
			pathfinder.getResult() != null && pathfinder.getResult().isReached());
		assertTrue(
			"INVENTORY_AND_BANK mode should imply bank-path traversal",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		assertTrue(
			"banked camulet should be used after banking",
			usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Camulet: Enakhra's Temple"));
	}

	@Test
	public void testInventoryModeDoesNotImplyBankPath()
	{
		// The same banked camulet with plain INVENTORY item mode must NOT gain bank
		// access: no bankVisited state, the camulet stays unusable, and the route
		// falls back to the long foot detour into the temple.
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY, new Item(ItemID.CAMULET, 1));

		Pathfinder pathfinder = runPathfinder(
			WorldPointUtil.packWorldPoint(3160, 3486, 0),
			WorldPointUtil.packWorldPoint(3105, 9315, 0));

		assertFalse(
			"INVENTORY mode must not activate the bankVisited path state",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		assertFalse(
			"INVENTORY mode must not use a banked camulet",
			usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Camulet: Enakhra's Temple"));
		assertTrue(
			"expected the long foot detour without the banked camulet, got " + pathfinder.getPath().size(),
			pathfinder.getPath().size() >= 400);
	}

	@Test
	public void testIncludeBankPathTrueKeepsModeSemantics()
	{
		// With includeBankPath explicitly on, INVENTORY_AND_BANK already banked for
		// the camulet — the imply is a logical OR and must not change that path.
		when(config.includeBankPath()).thenReturn(true);
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK, new Item(ItemID.CAMULET, 1));

		Pathfinder pathfinder = runPathfinder(
			WorldPointUtil.packWorldPoint(3160, 3486, 0),
			WorldPointUtil.packWorldPoint(3105, 9315, 0));

		assertTrue(
			"expected path to reach Enakhra's Temple",
			pathfinder.getResult() != null && pathfinder.getResult().isReached());
		assertTrue(
			"includeBankPath=true should still bank for the camulet",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		assertTrue(
			"banked camulet should be used after banking",
			usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Camulet: Enakhra's Temple"));

		// INVENTORY mode with includeBankPath on still cannot use banked items:
		// the bankVisited state exists but bank contents are never collected.
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY, new Item(ItemID.CAMULET, 1));

		Pathfinder inventoryPathfinder = runPathfinder(
			WorldPointUtil.packWorldPoint(3160, 3486, 0),
			WorldPointUtil.packWorldPoint(3105, 9315, 0));

		assertFalse(
			"INVENTORY mode must not use a banked camulet even with includeBankPath=true",
			usedTransportWithDisplayInfo(inventoryPathfinder, TransportType.TELEPORTATION_ITEM, "Camulet: Enakhra's Temple"));
	}

	@Test
	public void testShips()
	{
		when(config.useShips()).thenReturn(true);
		setupInventory(new Item(ItemID.COINS, 10000));
		testTransportLength(2, TransportType.SHIP);
	}

	@Test
	public void testFairyRings()
	{
		when(config.useFairyRings()).thenReturn(true);
		when(config.usePoh()).thenReturn(true);
		when(config.usePohFairyRing()).thenReturn(true);
		setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

		// Verify ALL fairy ring transports are available, but only calculate one path
		testAllTransportsAvailableWithSinglePath(TransportType.FAIRY_RING);
	}

	@Test
	public void testLunarStaffFairyRings()
	{
		when(config.useFairyRings()).thenReturn(true);
		when(config.usePoh()).thenReturn(true);
		when(config.usePohFairyRing()).thenReturn(true);
		setupInventory(new Item(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF, 1));
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

		// Verify ALL fairy ring transports are available, but only calculate one path
		testAllTransportsAvailableWithSinglePath(TransportType.FAIRY_RING);
	}

	@Test
	public void testFairyRingsNotUsedWithoutDramenStaff()
	{
		when(config.useFairyRings()).thenReturn(true);
		setupInventory();
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
		when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0);

		// Refresh config which will populate usable transports
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		// Ensure none of the usable transports are of type FAIRY_RING
		for (Transport t : activeTransportList())
		{
			assertNotEquals("Fairy ring used unexpectedly: " + t, TransportType.FAIRY_RING, t.getType());
		}
	}

	@Test
	public void testFairyRingsNotUsedWithoutQuestProgressOrEliteDiary()
	{
		when(config.useFairyRings()).thenReturn(true);
		setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(0);
		when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(0);

		setupConfig(QuestState.NOT_STARTED, 99, TeleportationItem.NONE);

		for (Transport t : activeTransportList())
		{
			assertNotEquals("Fairy ring used unexpectedly without quest progress or diary: " + t, TransportType.FAIRY_RING, t.getType());
		}
	}

	@Test
	public void testFairyRingsUsedWithLumbridgeDiaryCompleteWithoutDramenStaff()
	{
		when(config.useFairyRings()).thenReturn(true);
		// No Dramen staff in inventory or equipment
		setupInventory();
		// Satisfy Fairy2 quest varbit and Lumbridge elite diary complete
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
		when(client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)).thenReturn(1);

		testSingleTransportScenario("Fairy ring with Lumbridge diary and no Dramen staff", 2, TransportType.FAIRY_RING);
	}

	@Test
	public void testFairyRingsUsedWithDramenStaffWornInHand()
	{
		when(config.useFairyRings()).thenReturn(true);
		setupInventory();
		setupEquipment(new Item(ItemID.DRAMEN_STAFF, 1));

		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

		testSingleTransportScenario("Fairy ring with Dramen staff worn", 2, TransportType.FAIRY_RING);
	}

	@Test
	public void testBlockedTargetExpandsToWalkableNeighbours()
	{
		// Issue #640: a blocked target tile can never be walked into. The goal set is
		// expanded to the walkable tiles within the unreachable distance so the search
		// terminates on a nearby tile instead of exhausting the whole map.
		when(config.unreachableTargetDistance()).thenReturn(2);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		setupInventory();
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(3203, 3178, 0);
		assertTrue("test requires a blocked target tile",
			pathfinderConfig.getMap().isBlocked(3203, 3178, 0));
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(blockedTarget));
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("expected path to a walkable tile near the blocked target", result.isReached());
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
		List<PathStep> steps = result.getPathSteps();
		int end = steps.get(steps.size() - 1).getPackedPosition();
		assertFalse("path must end on a walkable tile", pathfinderConfig.getMap().isBlocked(
			WorldPointUtil.unpackWorldX(end), WorldPointUtil.unpackWorldY(end),
			WorldPointUtil.unpackWorldPlane(end)));
		assertTrue("path must end within the unreachable distance of the blocked target",
			WorldPointUtil.distanceBetween(end, blockedTarget) <= 2);
	}

	@Test
	public void testFullyBlockedTargetShortCircuitsSearch()
	{
		// Issue #640: a blocked target that no transport lands on and that has no
		// walkable tile within the unreachable distance can never be reached, so the
		// search is skipped entirely instead of exhausting the map.
		when(config.unreachableTargetDistance()).thenReturn(2);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		setupInventory();
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		// Interior of a large blocked area: the whole 5x5 neighbourhood is blocked.
		int blockedTarget = WorldPointUtil.packWorldPoint(2114, 5506, 0);
		assertTrue("test requires a blocked target tile",
			pathfinderConfig.getMap().isBlocked(2114, 5506, 0));
		assertFalse("test requires that no transport lands on the target",
			pathfinderConfig.isTransportDestination(blockedTarget));
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(blockedTarget));
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertFalse(result.isReached());
		assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, result.getTerminationReason());
		assertEquals("a hopeless target must not explore any tile", 0, result.getNodesChecked());
	}

	@Test
	public void testBlockedTargetDoesNotSuppressOtherTargets()
	{
		// A hopeless blocked target in a multi-target set must not prevent the
		// remaining viable targets from being reached.
		when(config.unreachableTargetDistance()).thenReturn(2);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		setupInventory();
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(2114, 5506, 0);
		int reachableTarget = WorldPointUtil.packWorldPoint(3213, 3428, 0);
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(blockedTarget, reachableTarget));
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("the viable target should still be reached", result.isReached());
		assertEquals(reachableTarget, result.getTarget());
	}

	@Test
	public void testBlockedTransportDestinationStillReached()
	{
		// Fairy ring tiles are blocked for walking but a ring lands the player on
		// them, so they must stay reachable and must not be short-circuited.
		when(config.unreachableTargetDistance()).thenReturn(2);
		when(config.useFairyRings()).thenReturn(true);
		when(config.usePoh()).thenReturn(true);
		when(config.usePohFairyRing()).thenReturn(true);
		setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		int ringTile = WorldPointUtil.packWorldPoint(2700, 3247, 0);
		assertTrue("test requires the fairy ring tile to be blocked",
			pathfinderConfig.getMap().isBlocked(2700, 3247, 0));
		assertTrue("test requires a transport landing on the target",
			pathfinderConfig.isTransportDestination(ringTile));
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(ringTile));
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("fairy ring target should be reached despite the blocked tile",
			result.isReached());
	}

	@Test
	public void testBlockedTargetReachedViaAdjacentTransportDestination()
	{
		// Issue #640: the Jalsavrah teleport lands on (1934, 4428), a blocked tile
		// adjacent to the likewise blocked (1934, 4427). No walkable tile exists
		// within the unreachable distance, so the transport landing must count as
		// a goal instead of the target being treated as hopeless.
		when(config.unreachableTargetDistance()).thenReturn(2);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.ALL);
		setupInventory();
		int start = WorldPointUtil.packWorldPoint(3222, 3218, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(1934, 4427, 0);
		int jalsavrahLanding = WorldPointUtil.packWorldPoint(1934, 4428, 0);
		assertTrue("test requires a blocked target tile",
			pathfinderConfig.getMap().isBlocked(1934, 4427, 0));
		assertTrue("test requires the landing tile to be blocked",
			pathfinderConfig.getMap().isBlocked(1934, 4428, 0));
		assertTrue("test requires a transport landing adjacent to the target",
			pathfinderConfig.isTransportDestination(jalsavrahLanding));
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(blockedTarget));
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("target should be reached via the adjacent teleport landing",
			result.isReached());
		List<PathStep> steps = result.getPathSteps();
		assertEquals("path must end on the Jalsavrah landing tile",
			jalsavrahLanding, steps.get(steps.size() - 1).getPackedPosition());
	}

	private static Set<Integer> connectedTilesOracle(CollisionMap map, int target, int radius)
	{
		Set<Integer> connected = new HashSet<>();
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		connected.add(target);
		queue.add(target);
		while (!queue.isEmpty())
		{
			int current = queue.poll();
			for (int neighbour : map.ordinaryWalkingNeighbors(current))
			{
				if (connected.contains(neighbour)
					|| WorldPointUtil.distanceBetween(target, neighbour) > radius)
				{
					continue;
				}
				connected.add(neighbour);
				queue.add(neighbour);
			}
		}
		connected.remove(target);
		return connected;
	}

	@Test
	public void testBlockedTargetExpansionStaysOnTargetSideOfWalls()
	{
		// Issue #640: with collisionAwareBlockedTargets on (the default), a blocked
		// target's walkable fallback goals are limited to tiles connected to it
		// through the collision map, so the path cannot terminate on the far side
		// of a wall. Target (3258, 3351) is a blocked wall segment in southern
		// Varrock: the square radius also covers walkable tiles on the other side
		// of that wall, which must not become goals in this mode.
		final int radius = 6;
		when(config.unreachableTargetDistance()).thenReturn(radius);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		setupInventory();
		final CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3258, 3341, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(3258, 3351, 0);
		assertTrue("test requires a blocked target tile",
			map.isBlocked(3258, 3351, 0));
		assertFalse("test requires a walkable start tile",
			map.isBlocked(3258, 3341, 0));
		Set<Integer> connected = connectedTilesOracle(map, blockedTarget, radius);
		assertFalse("test requires at least one connected fallback goal",
			connected.isEmpty());
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(blockedTarget));
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("expected path to a tile connected to the blocked target",
			result.isReached());
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
		List<PathStep> steps = result.getPathSteps();
		int end = steps.get(steps.size() - 1).getPackedPosition();
		assertFalse("path must end on a walkable tile", map.isBlocked(
			WorldPointUtil.unpackWorldX(end), WorldPointUtil.unpackWorldY(end),
			WorldPointUtil.unpackWorldPlane(end)));
		assertTrue("path must end within the unreachable distance of the blocked target",
			WorldPointUtil.distanceBetween(end, blockedTarget) <= radius);
		assertTrue("end tile must be walk-connected to the target (same side of the wall)",
			connected.contains(end));
	}

	@Test
	public void testBlockedTargetExpansionOffKeepsSquareScan()
	{
		// With collisionAwareBlockedTargets off, fallback goals revert to every
		// walkable tile inside the square radius -- including tiles on the far side
		// of a wall. Same fixture as the ON-mode test; here the path is allowed to
		// end on a tile that is not walk-connected to the target.
		final int radius = 6;
		when(config.collisionAwareBlockedTargets()).thenReturn(false);
		when(config.unreachableTargetDistance()).thenReturn(radius);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		setupInventory();
		final CollisionMap map = pathfinderConfig.getMap();
		int start = WorldPointUtil.packWorldPoint(3258, 3341, 0);
		int blockedTarget = WorldPointUtil.packWorldPoint(3258, 3351, 0);
		assertTrue("test requires a blocked target tile",
			map.isBlocked(3258, 3351, 0));
		assertFalse("test requires a walkable start tile",
			map.isBlocked(3258, 3341, 0));
		Set<Integer> connected = connectedTilesOracle(map, blockedTarget, radius);
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, start, Set.of(blockedTarget));
		pathfinder.run();
		PathfinderResult result = pathfinder.getResult();
		assertNotNull(result);
		assertTrue("expected the square scan to still find a fallback goal",
			result.isReached());
		assertEquals(PathTerminationReason.TARGET_REACHED, result.getTerminationReason());
		List<PathStep> steps = result.getPathSteps();
		int end = steps.get(steps.size() - 1).getPackedPosition();
		assertFalse("path must end on a walkable tile", map.isBlocked(
			WorldPointUtil.unpackWorldX(end), WorldPointUtil.unpackWorldY(end),
			WorldPointUtil.unpackWorldPlane(end)));
		assertTrue("path must end within the unreachable distance of the blocked target",
			WorldPointUtil.distanceBetween(end, blockedTarget) <= radius);
		assertFalse("off mode must still accept a tile not connected to the target",
			connected.contains(end));
	}

	@Test
	public void testTeleportItemsAndFairyRingsAvailableAfterBankVisit()
	{
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
		for (Transport t : activeTransportList())
		{
			if (TransportType.FAIRY_RING.equals(t.getType()))
			{
				hasFairyRing = true;
				break;
			}
		}
		assertTrue("Fairy ring transports should be in map (filtered per-path)", hasFairyRing);

	}

	/**
	 * Debug test: Compare paths from Castle Wars to AKQ fairy ring
	 * with staff in inventory vs staff in bank.
	 * Both should use fairy rings if that's the optimal path.
	 */
	@Test
	public void testCastleWarsToAKQWithStaffInInventory()
	{
		int castleWars = WorldPointUtil.packWorldPoint(2442, 3083, 0);
		int akqFairyRing = WorldPointUtil.packWorldPoint(2324, 3619, 0);

		when(config.useFairyRings()).thenReturn(true);
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
		when(config.costConsumableTeleportationItems()).thenReturn(50);
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

		setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
		setupEquipment();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY_AND_BANK);

		Pathfinder pathfinderWithStaff = runScenario(
			castleWars, akqFairyRing);

		assertTrue("Fairy ring should be used when staff is in inventory",
			usedTransportType(pathfinderWithStaff, TransportType.FAIRY_RING));
	}

	@Test
	public void testCastleWarsToAKQWithStaffInBank()
	{
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

		Pathfinder pathfinderWithBankStaff = runScenario(
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
	public void testBankPathDJPvsAKQTarget()
	{
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

		Pathfinder pathfinderToDJP = runScenario(castleWars, djpFairyRing);

		// PathfinderConfig is not mutated between runs; reuse the same config for the second path
		Pathfinder pathfinderToAKQ = runScenario(castleWars, akqFairyRing);

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
	public void testArdougneCloakInInventoryWithStaffInBank()
	{
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

		Pathfinder pathfinder = runScenario(
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
	public void testFairyRingNotUsedAfterTeleportWithoutBankVisit()
	{
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
		for (Transport t : activeTransportList())
		{
			if (TransportType.FAIRY_RING.equals(t.getType()))
			{
				hasFairyRing = true;
				break;
			}
		}
		assertTrue("Fairy rings should be in transport map (filtered per-path)", hasFairyRing);

		runScenario(
			WorldPointUtil.packWorldPoint(2442, 3096, 0),
			WorldPointUtil.packWorldPoint(3162, 3489, 0));
	}

	@Test
	public void testTeleportItemInInventoryUsableBeforeFirstBankVisit()
	{
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

		Pathfinder pathfinder = runScenario(
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
	public void testFairyRingRequiresBankVisitWhenStaffInBank()
	{
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

		Pathfinder pathfinder = runScenario(
			alKharidMine, akqFairyRing);

		boolean usedFairyRing = usedTransportType(pathfinder, TransportType.FAIRY_RING);

		// If fairy rings are used, the path must have gone through a bank
		if (usedFairyRing)
		{
			assertTrue("If fairy ring is used, path must visit a bank first to pick up Dramen staff",
				pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		}
		// If no fairy ring was used, that's also acceptable (walking path)
	}

	@Test
	public void testGreatConchBankToMcGruborsWoodMaximumLength()
	{
		// Baseline bank-enabled Great Conch route: fairy rings and a combat bracelet are only
		// available from the bank, so the chosen path should reflect those post-bank unlocks.
		when(config.includeBankPath()).thenReturn(true);
		when(config.useAgilityShortcuts()).thenReturn(true);
		when(config.useFairyRings()).thenReturn(true);
		setupInventory();
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

		assertScenarioPathLengthWithBank(
			"Great Conch -> McGrubor's Wood with banked staff and bracelet",
			49,
			WorldPointUtil.packWorldPoint(3180, 2419, 0),
			WorldPointUtil.packWorldPoint(2652, 3485, 0),
			TeleportationItem.INVENTORY_AND_BANK,
			new Item(ItemID.DRAMEN_STAFF, 1),
			new Item(11118, 1));
	}

	@Test
	public void testGreatConchTileReuseToMcGruborsWoodWithBankedStaff()
	{
		// Target the tile-reuse case directly: the path re-enters the same corridor after banking,
		// and only the post-bank revisit has access to the banked Dramen staff route options.
		when(config.includeBankPath()).thenReturn(true);
		when(config.useAgilityShortcuts()).thenReturn(true);
		when(config.useFairyRings()).thenReturn(true);
		setupInventory();
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

		assertScenarioPathLengthWithBank(
			"Great Conch tile reuse -> McGrubor's Wood with banked Dramen staff",
			70,
			WorldPointUtil.packWorldPoint(3181, 2437, 0),
			WorldPointUtil.packWorldPoint(2652, 3485, 0),
			TeleportationItem.INVENTORY_AND_BANK,
			new Item(ItemID.DRAMEN_STAFF, 1),
			new Item(11118, 1));
	}

	@Test
	public void testGreatConchToMcGruborsWoodMaximumLengthWithoutBanking()
	{
		// Non-bank baseline for the same route family. The Dramen staff starts in inventory, so
		// the path can use fairy rings immediately without any bank visit or post-bank unlock.
		when(config.useAgilityShortcuts()).thenReturn(true);
		when(config.useFairyRings()).thenReturn(true);
		setupInventory(new Item(ItemID.DRAMEN_STAFF, 1));
		when(client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST)).thenReturn(100);

		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		assertScenarioPathLength(
			"Great Conch -> McGrubor's Wood with inventory Dramen staff",
			48,
			WorldPointUtil.packWorldPoint(3180, 2419, 0),
			WorldPointUtil.packWorldPoint(2652, 3485, 0));
	}

	@Test
	public void testFairyRingBranchDoesNotLeakBankedDramenStaff()
	{
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
	public void testCowbellAmuletInBankUsedAfterBankVisit()
	{
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
	public void testBankVisitCostPrefersReadyMinigameTeleport()
	{
		// GE -> Rogues' Den: a ready minigame teleport is the shorter route, but with a
		// free bank transition the pathfinder detours to a bank for a games necklace.
		// A nonzero bank visit cost must make the direct teleport win again.
		when(config.useTeleportationMinigames()).thenReturn(true);
		when(config.includeBankPath()).thenReturn(true);
		when(config.costBankVisit()).thenReturn(20);
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
			new Item(ItemID.NECKLACE_OF_MINIGAMES_8, 1));

		int grandExchangeBank = WorldPointUtil.packWorldPoint(3160, 3486, 0);
		int roguesDen = WorldPointUtil.packWorldPoint(3040, 4969, 1);

		Pathfinder pathfinder = assertScenarioPathLengthAndGet(
			"GE -> Rogues' Den with ready minigame teleport and banked games necklace",
			53,
			grandExchangeBank,
			roguesDen);

		assertFalse("Route should not visit a bank when the minigame teleport is ready",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
	}

	@Test
	public void testFreeBankVisitStillPrefersBankedTeleportDetour()
	{
		// costBankVisit = 0 must preserve the legacy free bank transition: the banked
		// games necklace detour still wins over the ready minigame teleport.
		when(config.useTeleportationMinigames()).thenReturn(true);
		when(config.includeBankPath()).thenReturn(true);
		when(config.costBankVisit()).thenReturn(0);
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
			new Item(ItemID.NECKLACE_OF_MINIGAMES_8, 1));

		int grandExchangeBank = WorldPointUtil.packWorldPoint(3160, 3486, 0);
		int roguesDen = WorldPointUtil.packWorldPoint(3040, 4969, 1);

		Pathfinder pathfinder = assertScenarioPathLengthAndGet(
			"GE -> Rogues' Den with free bank visit keeps the banked detour",
			56,
			grandExchangeBank,
			roguesDen);

		assertTrue("Route should visit a bank to fetch the games necklace",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));

		// Issue #492: the banked games necklace and the Burthorpe Games Room minigame
		// teleport share the destination tile 2899,3553,0. The step must carry the
		// transport the search actually used — the games necklace — so display and
		// pickup consumers never see the ambiguous minigame teleport.
		int sharedDestination = WorldPointUtil.packWorldPoint(2899, 3553, 0);
		PathStep teleportStep = pathfinder.getPath().stream()
			.filter(s -> s.getPackedPosition() == sharedDestination)
			.findFirst()
			.orElse(null);
		assertNotNull("Path should contain the shared teleport destination 2899,3553,0",
			teleportStep);
		assertNotNull("Teleport destination step should carry the transport that produced the edge",
			teleportStep.getTransport());
		assertTrue("Banked edge should use an item teleport, not the minigame teleport",
			teleportStep.getTransport().isType(TransportType.TELEPORTATION_ITEM));
		assertTrue("Expected the games necklace, got " + teleportStep.getTransport().getDisplayInfo(),
			teleportStep.getTransport().hasDisplayInfo("Games necklace"));
	}

	@Test
	public void testBankVisitCostStillBanksWhenTeleportItemRequiresIt()
	{
		// When the minigame teleport is unavailable (e.g. on cooldown) the route must
		// still bank to fetch the games necklace even when banking carries a cost.
		when(config.useTeleportationMinigames()).thenReturn(false);
		when(config.includeBankPath()).thenReturn(true);
		when(config.costBankVisit()).thenReturn(20);
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
			new Item(ItemID.NECKLACE_OF_MINIGAMES_8, 1));

		int grandExchangeBank = WorldPointUtil.packWorldPoint(3160, 3486, 0);
		int roguesDen = WorldPointUtil.packWorldPoint(3040, 4969, 1);

		Pathfinder pathfinder = runPathfinder(grandExchangeBank, roguesDen);
		List<PathStep> path = pathfinder.getPath();

		assertFalse("Expected a path to be found", path.isEmpty());
		assertEquals("Path should end at the Rogues' Den target",
			roguesDen, path.get(path.size() - 1).getPackedPosition());
		assertTrue("Route should still visit a bank to fetch the games necklace",
			path.stream().anyMatch(PathStep::isBankVisited));
	}

	@Test
	public void testBankersBriefcaseInBankUsedAfterBankVisit()
	{
		// The Banker's Briefcase is a SEASONAL_TRANSPORTS row, but its bank-pickup
		// behaviour goes through the same hasRequiredItems path as TELEPORTATION_ITEM
		// and QUETZAL_WHISTLE. With the briefcase only in the bank, the transport
		// should be unavailable until the path visits a bank tile.
		// Seasonal transports only exist on seasonal worlds: Varlamore and
		// Karamja are pre-unlocked, Kandarin (the briefcase destination) needs
		// an unlock slot.
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));
		when(client.getVarbitValue(10662)).thenReturn(21);
		when(client.getVarbitValue(10663)).thenReturn(2);
		when(client.getVarbitValue(10664)).thenReturn(4);
		when(config.useSeasonalTransports()).thenReturn(true);
		when(config.includeBankPath()).thenReturn(true);
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
			new Item(30361, 1));

		// Start one tile west of the Civitas Illa Fortis east bank chest at
		// (1781, 3100, 0); target the Catherby briefcase landing tile at
		// (2807, 3442, 0). Without the briefcase, no other transport will fire
		// (every other type is disabled), so the path must walk to a bank,
		// flip bankVisited, and only then teleport.
		int civitasEastApproach = WorldPointUtil.packWorldPoint(1735, 3093, 0);
		int catherbyBriefcase = WorldPointUtil.packWorldPoint(2807, 3442, 0);

		Pathfinder pathfinder = assertScenarioPathLengthAndGet(
			"Civitas approach -> Catherby briefcase with briefcase in bank",
			60,
			civitasEastApproach,
			catherbyBriefcase);

		assertTrue("Banker's Briefcase should be used after visiting a bank",
			usedTransportWithDisplayInfoAfterFirstBank(pathfinder, TransportType.SEASONAL_TRANSPORTS,
				"Banker's Briefcase: Kandarin - Catherby"));
		assertFalse("Banker's Briefcase should not be used before the first bank visit",
			usedTransportWithDisplayInfoBeforeFirstBank(pathfinder, TransportType.SEASONAL_TRANSPORTS,
				"Banker's Briefcase: Kandarin - Catherby"));
	}

	@Test
	public void testSeasonalTransportsNotUsableOnNormalWorld()
	{
		// Same setup as the briefcase test but on a normal world: the enabled
		// toggle must not leak seasonal transports into non-seasonal worlds.
		when(config.useSeasonalTransports()).thenReturn(true);
		when(config.includeBankPath()).thenReturn(true);
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
			new Item(30361, 1));

		int civitasEastApproach = WorldPointUtil.packWorldPoint(1735, 3093, 0);
		int catherbyBriefcase = WorldPointUtil.packWorldPoint(2807, 3442, 0);

		Pathfinder pathfinder = runPathfinder(civitasEastApproach, catherbyBriefcase);

		assertFalse("Seasonal transports must not be used on a non-seasonal world",
			usedTransportType(pathfinder, TransportType.SEASONAL_TRANSPORTS));
	}

	@Test
	public void testSeasonalTransportsNotUsableOnDeadmanWorld()
	{
		// Deadman worlds are permanent-mode worlds, not seasonal worlds.
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.DEADMAN));
		when(config.useSeasonalTransports()).thenReturn(true);
		when(config.includeBankPath()).thenReturn(true);
		setupInventory();
		setupEquipment();
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK,
			new Item(30361, 1));

		int civitasEastApproach = WorldPointUtil.packWorldPoint(1735, 3093, 0);
		int catherbyBriefcase = WorldPointUtil.packWorldPoint(2807, 3442, 0);

		Pathfinder pathfinder = runPathfinder(civitasEastApproach, catherbyBriefcase);

		assertFalse("Seasonal transports must not be used on a Deadman world",
			usedTransportType(pathfinder, TransportType.SEASONAL_TRANSPORTS));
	}

	@Test
	public void testGnomeGliders()
	{
		when(config.useGnomeGliders()).thenReturn(true);
		testTransportLength(2, TransportType.GNOME_GLIDER);
	}

	@Test
	public void testHotAirBalloons()
	{
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
	public void testMagicCarpets()
	{
		when(config.useMagicCarpets()).thenReturn(true);
		setupInventory(
			new Item(ItemID.COINS, 200));
		testTransportLength(2, TransportType.MAGIC_CARPET);
	}

	@Test
	public void testMagicMushtrees()
	{
		when(config.useMagicMushtrees()).thenReturn(true);
		testTransportLength(2, TransportType.MAGIC_MUSHTREE);
	}

	@Test
	public void testMinecarts()
	{
		when(config.useMinecarts()).thenReturn(true);
		setupInventory(new Item(ItemID.COINS, 1000));
		testTransportLength(2, TransportType.MINECART);
	}

	@Test
	public void testLovakenjMinecartNetworkPaidBeforeQuest()
	{
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
	public void testLovakenjMinecartNetworkFreeAfterQuest()
	{
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
	public void testLovakenjMinecartNetworkReverseNotUsableWithoutCoinsBeforeQuest()
	{
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
	public void testQuetzals()
	{
		when(config.useQuetzals()).thenReturn(true);
		testTransportLength(2, TransportType.QUETZAL);
	}

	/**
	 * Tests that the Primio quetzal (Varrock ↔ Civitas) works correctly.
	 * This is a fixed route in transports.tsv, NOT part of the quetzal platform system.
	 */
	@Test
	public void testPrimioQuetzal()
	{
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
	public void testQuetzalPlatformPreferredOverWhistle()
	{
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
	public void testWhistleNotUsedWhenNearPlatform()
	{
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
	public void testQuetzalDisabledDisablesWhistle()
	{
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
	public void testPlatformUsedWhenWhistleNotInInventory()
	{
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
	public void testQuetzalWhistleCostBoundary()
	{
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
	public void testWhistleUsedFromFarAway()
	{
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

	/**
	 * Issue #468: costConsumableTeleportationItems should penalise the Quetzal
	 * whistle as well as teleportation tabs, since both are consumable items.
	 * Without this fix the whistle bypassed the consumable-item threshold and could
	 * make a bank-detour route appear cheaper than a direct teleportation tab.
	 * <p>
	 * Verified via the Aldarin platform cost boundary: from 1 tile away the platform
	 * compareCost is 7 (1 walk + 6 flight). With costConsumableTeleportationItems=5
	 * the whistle's actual cost becomes 4+5=9, so the platform wins (path length 3).
	 * Before the fix the whistle paid no consumable penalty (cost 4) and won (path 2).
	 */
	@Test
	public void testConsumableCostPenaltyAppliedToQuetzalWhistle()
	{
		when(config.useQuetzals()).thenReturn(true);
		when(config.costConsumableTeleportationItems()).thenReturn(5);
		when(config.costQuetzalWhistle()).thenReturn(0);
		setupInventory(new Item(29271, 1)); // Quetzal whistle
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

		int nearAldarin = WorldPointUtil.packWorldPoint(1390, 2901, 0);
		int hunterGuild = WorldPointUtil.packWorldPoint(1585, 3053, 0);

		int pathLength = calculatePathLength(nearAldarin, hunterGuild);
		assertEquals(
			"Platform should win when costConsumableTeleportationItems tips the whistle past the platform cost",
			3, pathLength);
	}

	@Test
	public void testSpiritTrees()
	{
		when(config.useSpiritTrees()).thenReturn(true);
		when(client.getVarbitValue(any(Integer.class))).thenReturn(20);
		testTransportLength(2, TransportType.SPIRIT_TREE);
	}

	@Test
	public void testTeleportationLevers()
	{
		when(config.useTeleportationLevers()).thenReturn(true);
		testTransportLength(2, TransportType.TELEPORTATION_LEVER);
	}

	@Test
	public void testTeleportationMinigames()
	{
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
	public void testMinigameTeleportCooldownUsesRefreshTime()
	{
		// These are the Nature Spirit Grotto start and Fishing Trawler destination used by the
		// existing testTeleportationMinigames fixture above.
		int fishingTrawlerStart = WorldPointUtil.packWorldPoint(3440, 3334, 0);
		int fishingTrawlerDestination = WorldPointUtil.packWorldPoint(2658, 3157, 0);

		setupConfigAtTimes(100000000L, 99999990L, 99999979);
		Pathfinder ready = runPathfinder(fishingTrawlerStart, fishingTrawlerDestination);
		assertTrue(usedTransportWithDisplayInfo(
			ready, TransportType.TELEPORTATION_MINIGAME, "Fishing Trawler Minigame Teleport"));

		setupConfigAtTimes(99999990L, 100000000L, 99999979);
		Pathfinder onCooldown = runPathfinder(fishingTrawlerStart, fishingTrawlerDestination);
		assertFalse(usedTransportWithDisplayInfo(
			onCooldown, TransportType.TELEPORTATION_MINIGAME, "Fishing Trawler Minigame Teleport"));
	}

	@Test
	public void testPickaxeNotUsedWithoutPickaxe()
	{
		// Ensure transports requiring a pickaxe are not included when the player has no pickaxe
		setupInventory();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		assertFalse("No transports should be present that require a pickaxe", hasTransportWithRequiredItem(pathfinderConfig.getTransports(), ItemVariations.PICKAXE.getIds()));
	}

	@Test
	public void testPickaxeUsedWithPickaxe()
	{
		// Ensure transports requiring a pickaxe are included when the player has a pickaxe and sufficient mining level
		setupInventory(new Item(ItemID.BRONZE_PICKAXE, 1));
		setupConfig(QuestState.FINISHED, 50, TeleportationItem.NONE); // transport in data requires 50 Mining

		assertTrue("Transports requiring a pickaxe should be present",
			hasTransportWithRequiredItem(pathfinderConfig.getTransports(), ItemVariations.PICKAXE.getIds()));
	}

	@Test
	public void testAxeNotUsedWithoutAxe()
	{
		// Ensure transports requiring an axe are not included when the player has no axe
		setupInventory();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		assertFalse("No transports should be present that require an axe", hasTransportWithRequiredItem(pathfinderConfig.getTransports(), ItemVariations.AXE.getIds()));
	}

	@Test
	public void testAxeUsedWithAxe()
	{
		// Ensure transports requiring an axe are included when the player has an axe
		setupInventory(new Item(ItemID.BRONZE_AXE, 1));
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		assertTrue("Transports requiring an axe should be present",
			hasTransportWithRequiredItem(pathfinderConfig.getTransports(), ItemVariations.AXE.getIds()));
	}

	@Test
	public void testTeleportationPortals()
	{
		when(config.useTeleportationPortals()).thenReturn(true);
		testTransportLength(2, TransportType.TELEPORTATION_PORTAL);
	}

	@Test
	public void testWildernessObelisks()
	{
		when(config.useWildernessObelisks()).thenReturn(true);
		when(config.usePoh()).thenReturn(true);
		when(config.usePohObelisk()).thenReturn(true);
		testTransportLength(2, TransportType.WILDERNESS_OBELISK);
	}

	@Test
	public void testPohMountedItemSelectionChangesRoute()
	{
		// From Lumbridge to the Grand Exchange, a house tablet makes the POH exits viable:
		// Edgeville is the closest Glory exit; after Glory is disabled, Digsite is the best remaining exit.
		int grandExchange = WorldPointUtil.packWorldPoint(3164, 3487, 0);
		when(config.usePoh()).thenReturn(true);
		when(config.pohNexusPortals()).thenReturn(Set.of());
		when(config.pohJewelleryBoxTier()).thenReturn(JewelleryBoxTier.NONE);
		when(config.pohMountedItems()).thenReturn(EnumSet.of(
			PohMountedItem.GLORY, PohMountedItem.DIGSITE_PENDANT));
		setupInventory(new Item(8013, 1));
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

		Pathfinder withGlory = runScenario(WorldPointUtil.packWorldPoint(3200, 3200, 0), grandExchange);
		assertTrue(withGlory.getResult().isReached());
		assertTrue(usedTransportWithDisplayInfo(withGlory, TransportType.TELEPORTATION_ITEM, "Teleport to House tablet"));
		assertTrue(usedTransportWithDisplayInfo(withGlory, TransportType.TELEPORTATION_BOX, "Edgeville"));

		when(config.pohMountedItems()).thenReturn(EnumSet.of(PohMountedItem.DIGSITE_PENDANT));
		pathfinderConfig.refresh();
		Pathfinder withoutGlory = runScenario(WorldPointUtil.packWorldPoint(3200, 3200, 0), grandExchange);
		assertTrue(withoutGlory.getResult().isReached());
		assertTrue(usedTransportWithDisplayInfo(withoutGlory, TransportType.TELEPORTATION_ITEM, "Teleport to House tablet"));
		assertFalse(usedTransportWithDisplayInfo(withoutGlory, TransportType.TELEPORTATION_BOX, "Edgeville"));
		assertTrue(usedTransportWithDisplayInfo(withoutGlory, TransportType.TELEPORTATION_BOX, "Digsite"));
	}

	@Test
	public void testVarrockPalaceTrellisUsableWithGardenOfTranquillity()
	{
		testTransportLength(2,
			WorldPointUtil.packWorldPoint(3228, 3470, 0),
			WorldPointUtil.packWorldPoint(3228, 3472, 0));
		testTransportLength(2,
			WorldPointUtil.packWorldPoint(3228, 3472, 0),
			WorldPointUtil.packWorldPoint(3228, 3470, 0));
	}

	@Test
	public void testVarrockPalaceTrellisNotUsableWithoutGardenOfTranquillity()
	{
		setupConfig(QuestState.NOT_STARTED, 99, TeleportationItem.NONE);

		int south = WorldPointUtil.packWorldPoint(3228, 3470, 0);
		int north = WorldPointUtil.packWorldPoint(3228, 3472, 0);

		assertTrue("Varrock Palace trellis should not be directly usable without Garden of Tranquillity",
			calculatePathLength(south, north) > 2);
		assertTrue("Varrock Palace trellis should not be directly usable without Garden of Tranquillity",
			calculatePathLength(north, south) > 2);
	}

	@Test
	public void testAgilityShortcutAndTeleportItem()
	{
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
	public void testChronicle()
	{
		// South of river south of Champions Guild to Chronicle teleport destination
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.ALL);
		assertEquals(2, calculatePathLength(
			WorldPointUtil.packWorldPoint(3199, 3336, 0),
			WorldPointUtil.packWorldPoint(3200, 3355, 0)));
	}

	@Test
	public void testVarrockTeleport()
	{
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
	public void testQuestCapeTeleportRequiresCapeAndCompletedQuests()
	{
		setupQuestPointDatabase(343);
		setupInventory(new Item(ItemID.SKILLCAPE_QP, 1));
		setupEquipment();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);
		assertTrue("Quest cape in inventory with all quests finished should be usable",
			hasUsableTeleport("Quest point cape: Teleport"));

		setupQuestPointDatabase(342);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);
		assertFalse("Quest cape should not teleport when quests are incomplete",
			hasUsableTeleport("Quest point cape: Teleport"));
	}

	@Test
	public void testQuestCapeInBankRequiresCompletedQuests()
	{
		setupQuestPointDatabase(343);
		when(config.includeBankPath()).thenReturn(true);
		setupInventory();
		setupEquipment();
		setupConfigWithBank(QuestState.FINISHED, TeleportationItem.INVENTORY_AND_BANK,
			new Item(ItemID.SKILLCAPE_QP, 1));
		assertTrue("Quest cape in bank with all quests finished should be usable after banking",
			hasUsableTeleport("Quest point cape: Teleport", true));

		setupQuestPointDatabase(342);
		setupConfigWithBank(QuestState.FINISHED, TeleportationItem.INVENTORY_AND_BANK,
			new Item(ItemID.SKILLCAPE_QP, 1));
		assertFalse("Quest cape in bank should not be suggested when quests are incomplete",
			hasUsableTeleport("Quest point cape: Teleport", true));
	}

	@Test
	public void testFaladorTeleportWithMistStaff()
	{
		when(config.useTeleportationSpells()).thenReturn(true);
		setupInventory(
			new Item(ItemID.MIST_BATTLESTAFF, 1),
			new Item(ItemID.LAWRUNE, 1));
		setupEquipment();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

		assertTrue("Mist battlestaff should supply both air and water for Falador Teleport",
			hasUsableTeleport("Falador Teleport"));
	}

	@Test
	public void testHouseTeleportWithDustStaff()
	{
		when(config.useTeleportationSpells()).thenReturn(true);
		when(config.useTeleportationSpellsHome()).thenReturn(true);
		setupInventory(
			new Item(ItemID.DUST_BATTLESTAFF, 1),
			new Item(ItemID.LAWRUNE, 1));
		setupEquipment();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);

		assertTrue("Dust battlestaff should supply both air and earth for Teleport to House",
			hasUsableTeleport("Teleport to House") || hasUsableTeleport("Teleport to House (Inside)"));
	}

	@Test
	public void testWildernessRouteWithoutTeleportsWalksOut()
	{
		int deepWilderness = WorldPointUtil.packWorldPoint(3340, 3828, 0);
		int grandExchange = WorldPointUtil.packWorldPoint(3158, 3509, 0);

		when(config.useAgilityShortcuts()).thenReturn(true);
		setupInventory();
		setupEquipment();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		Pathfinder pathfinder = runScenario(deepWilderness, grandExchange);

		assertFalse("No teleportation item should be used when none are available",
			usedTransportType(pathfinder, TransportType.TELEPORTATION_ITEM));
		assertFalse("No teleportation spell should be used when none are available",
			usedTransportType(pathfinder, TransportType.TELEPORTATION_SPELL));
		assertTrue("Walking route should still reach the destination", pathfinder.getResult().isReached());

		assertEquals(328, pathfinder.getPath().size());
	}

	@Test
	public void testWildernessRouteUsesGloryAfterLeavingLevel30()
	{
		int deepWilderness = WorldPointUtil.packWorldPoint(3340, 3828, 0);
		int grandExchange = WorldPointUtil.packWorldPoint(3158, 3509, 0);

		when(config.useAgilityShortcuts()).thenReturn(true);

		setupInventory(new Item(ItemID.AMULET_OF_GLORY_6, 1));
		setupEquipment();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.INVENTORY);
		Pathfinder withGlory = runScenario(deepWilderness, grandExchange);

		assertTrue("Charged glory should be used once the route reaches a legal wilderness level",
			usedTransportWithDisplayInfo(withGlory, TransportType.TELEPORTATION_ITEM, "Amulet of glory"));

		assertEquals(139, withGlory.getPath().size());
	}

	@Test
	public void testWildernessRouteUsesGrandExchangeVarrockTeleportAfterLeavingLevel20()
	{
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
		Pathfinder withVarrockTeleport = runScenario(deepWilderness, grandExchange);

		assertEquals(181, withVarrockTeleport.getPath().size());
		assertTrue("GE Varrock Teleport should be used on the route to Grand Exchange",
			usedTransportWithDisplayInfo(withVarrockTeleport, TransportType.TELEPORTATION_SPELL, "Varrock Teleport: Grand Exchange"));
	}

	@Test
	public void testWildernessRouteWithGloryAndRunesDoesNotUseSpellTooEarly()
	{
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

		Pathfinder pathfinder = runScenario(deepWilderness, grandExchange);

		assertEquals(102, pathfinder.getPath().size());
		assertTrue("Glory should be used when both glory and GE runes are available but the spell is still wilderness-locked",
			usedTransportWithDisplayInfo(pathfinder, TransportType.TELEPORTATION_ITEM, "Amulet of glory"));
	}

	@Test
	public void testRespawnTeleportPrifddinasGatedOnConfigOption()
	{
		// All *_SPAWN varbits at 0 is the signature for both the Lumbridge default and the
		// Prifddinas respawn - the config option is the only way to tell them apart.
		int prifddinasRespawn = WorldPointUtil.packWorldPoint(3265, 6077, 0);
		int lumbridgeRespawn = WorldPointUtil.packWorldPoint(3221, 3218, 0);
		Map<Integer, Integer> varbits = new HashMap<>();
		varbits.put(4070, 3); // Arceuus spellbook

		setupInventory(new Item(ItemID.LAWRUNE, 1), new Item(ItemID.SOULRUNE, 1));
		when(config.useTeleportationSpells()).thenReturn(true);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE, varbits);

		assertTrue("Lumbridge Respawn Teleport should be usable when no spawn varbit is set",
			hasUsableTeleportTo(lumbridgeRespawn));
		assertFalse("Prifddinas Respawn Teleport should stay gated without the config option",
			hasUsableTeleportTo(prifddinasRespawn));
	}

	@Test
	public void testRespawnTeleportPrifddinasConfigSuppressesLumbridgeDefault()
	{
		int prifddinasRespawn = WorldPointUtil.packWorldPoint(3265, 6077, 0);
		int lumbridgeRespawn = WorldPointUtil.packWorldPoint(3221, 3218, 0);
		Map<Integer, Integer> varbits = new HashMap<>();
		varbits.put(4070, 3); // Arceuus spellbook

		when(config.respawnPrifddinas()).thenReturn(true);
		setupInventory(new Item(ItemID.LAWRUNE, 1), new Item(ItemID.SOULRUNE, 1));
		when(config.useTeleportationSpells()).thenReturn(true);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE, varbits);

		assertTrue("Prifddinas Respawn Teleport should be usable with the config option set",
			hasUsableTeleportTo(prifddinasRespawn));
		assertFalse("Lumbridge Respawn Teleport should be suppressed when the respawn is Prifddinas",
			hasUsableTeleportTo(lumbridgeRespawn));
	}

	@Test
	public void testRespawnPortalPrifddinasGatedOnConfigOption()
	{
		int prifddinasRespawn = WorldPointUtil.packWorldPoint(3265, 6077, 0);
		int lumbridgeRespawn = WorldPointUtil.packWorldPoint(3221, 3218, 0);
		int pohOrigin = WorldPointUtil.packWorldPoint(1858, 7051, 0);

		when(config.usePoh()).thenReturn(true);
		when(config.pohNexusPortals()).thenReturn(EnumSet.of(PohNexusPortal.RESPAWN));
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		PrimitiveIntHashMap<Transport[]> transports = pathfinderConfig.getTransports();
		assertTrue("Lumbridge respawn portal should be usable when no spawn varbit is set",
			hasTransportTo(transports, pohOrigin, lumbridgeRespawn));
		assertFalse("Prifddinas respawn portal should stay gated without the config option",
			hasTransportTo(transports, pohOrigin, prifddinasRespawn));
	}

	@Test
	public void testMosLeHarmlessSteppingStonesStayOutOfTheSea()
	{
		// The stones (3810,3050 and 3810,3055) stand in open water, which the collision map
		// leaves walkable. Landing on a water tile between them would connect the search to
		// about a million tiles of sea, so the crossing goes straight from shore to island.
		int shore = WorldPointUtil.packWorldPoint(3810, 3048, 0);
		int island = WorldPointUtil.packWorldPoint(3811, 3056, 0);

		when(config.useAgilityShortcuts()).thenReturn(true);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		PrimitiveIntHashMap<Transport[]> transports = pathfinderConfig.getTransports();
		assertTrue("Stepping stones should cross from Mos Le'Harmless to the island",
			hasTransportTo(transports, shore, island));
		assertTrue("Stepping stones should cross from the island to Mos Le'Harmless",
			hasTransportTo(transports, island, shore));
		for (int y = 3049; y <= 3055; y++)
		{
			for (int x = 3810; x <= 3811; x++)
			{
				int water = WorldPointUtil.packWorldPoint(x, y, 0);
				assertNull("No transport may start in the water at " + x + "," + y, transports.get(water));
				assertFalse("No transport may land in the water at " + x + "," + y,
					hasTransportTo(transports, shore, water) || hasTransportTo(transports, island, water));
			}
		}
	}

	private boolean hasUsableTeleportTo(int packedDestination)
	{
		for (Transport transport : pathfinderConfig.getUsableTeleports(false))
		{
			if (transport.getDestination() == packedDestination
				&& transport.hasDisplayInfo("Respawn"))
			{
				return true;
			}
		}
		return false;
	}

	private boolean hasTransportTo(PrimitiveIntHashMap<Transport[]> transports, int origin, int packedDestination)
	{
		Transport[] transportsFromOrigin = transports.get(origin);
		if (transportsFromOrigin == null)
		{
			return false;
		}
		for (Transport transport : transportsFromOrigin)
		{
			if (transport.getDestination() == packedDestination)
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void testAvoidWildernessSuppressesBurningAmuletRoute()
	{
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
	public void testArdougneLeverNotUsedWithoutSlashItem()
	{
		int origin = WorldPointUtil.packWorldPoint(2485, 3080, 0);
		int destination = WorldPointUtil.packWorldPoint(3087, 3492, 0);

		when(config.avoidWilderness()).thenReturn(false);
		when(config.useTeleportationLevers()).thenReturn(true);
		setupInventory();
		setupEquipment();
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		Pathfinder pathfinder = assertScenarioPathLengthAndGet(
			"Wizards' Guild -> Edgeville with no items and wilderness allowed",
			876,
			origin,
			destination);

		assertTrue("Route should still reach the destination when wilderness is allowed", pathfinder.getResult().isReached());
		assertFalse("Ardougne lever lands inside a web-fenced compound and should not be used without a slash item",
			usedTransportType(pathfinder, TransportType.TELEPORTATION_LEVER));
	}

	@Test
	public void testBurningAmuletRouteAllowedWhenNotAvoidingWilderness()
	{
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
	public void testCaves()
	{
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
	public void testPathViaOtherPlane()
	{
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
	public void testImpossibleCharterShips()
	{
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

		testTransportMinimumLength(3,
			WorldPointUtil.packWorldPoint(3058, 2975, 0), // The Pandemonium
			WorldPointUtil.packWorldPoint(2954, 3158, 0)); // Musa Point

		testTransportMinimumLength(3,
			WorldPointUtil.packWorldPoint(3058, 2975, 0), // The Pandemonium
			WorldPointUtil.packWorldPoint(3038, 3192, 0)); // Port Sarim
	}

	@Test
	public void testLumbridgeDesertSteppingStoneCannotCrossOcean()
	{
		when(config.useAgilityShortcuts()).thenReturn(true);
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		Pathfinder pathfinder = runPathfinder(
			WorldPointUtil.packWorldPoint(3212, 3137, 0),
			WorldPointUtil.packWorldPoint(3214, 3132, 0));

		assertFalse("Stepping stone must not connect ocean tiles", pathfinder.getResult().isReached());
	}

	@Test
	public void testTransportItems()
	{
		// Varrock Teleport
		TransportItems actual = null;
		for (Transport transport : transports.get(Transport.UNDEFINED_ORIGIN))
		{
			if ("Varrock Teleport".equals(transport.getDisplayInfo()))
			{
				actual = transport.getItemRequirements();
				break;
			}
		}
		TransportItems expected;
		if (actual != null)
		{
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
		for (Transport transport : transports.get(Transport.UNDEFINED_ORIGIN))
		{
			if ("Trollheim Teleport".equals(transport.getDisplayInfo()))
			{
				actual = transport.getItemRequirements();
				break;
			}
		}
		if (actual != null)
		{
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
	private void setupConfig(QuestState questState, int skillLevel, TeleportationItem useTeleportationItems)
	{
		// NOTE: Not mocked since PathfinderConfig is repeatedly queried in the hot loop.
		pathfinderConfig = new TestPathfinderConfig(
			client,
			config,
			questState,
			true,  // Ignore Varbit checks
			true  // Ignore Varplayer checks
		);

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(skillLevel);
		when(config.useTeleportationItems()).thenReturn(useTeleportationItems);

		pathfinderConfig.refresh();
	}

	private void setupConfigAtTimes(long refreshTimeMinutes, long laterTimeMinutes, int storedTimestamp)
	{
		pathfinderConfig = new ChangingTimePathfinderConfig(
			client, config, QuestState.FINISHED, false, false, refreshTimeMinutes, laterTimeMinutes);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getTotalLevel()).thenReturn(2376);
		when(client.getVarbitValue(any(Integer.class))).thenReturn(0);
		when(client.getVarpValue(any(Integer.class))).thenReturn(0);
		when(client.getVarpValue(888)).thenReturn(storedTimestamp);
		when(config.calculationCutoff()).thenReturn(500);
		when(config.useTeleportationMinigames()).thenReturn(true);
		when(config.useTeleportationSpells()).thenReturn(false);
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);

		pathfinderConfig.refresh();
	}

	// Setup a configuration with
	// * A fixed QuestState for all quests
	// * A fixed skill level for all skills
	// * A toggle about wheher to use teleportation items.
	// * Fixed set of varbit values
	private void setupConfig(QuestState questState, int skillLevel, TeleportationItem useTeleportationItems, Map<Integer, Integer> varbitValues)
	{
		// NOTE: Not mocked since PathfinderConfig is repeatedly queried in the hot loop.
		pathfinderConfig = new TestPathfinderConfig(
			client, config,
			questState,
			false,  // Use proper varbit checking
			true  // Ignore varplayer checks
		);

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(skillLevel);
		when(config.useTeleportationItems()).thenReturn(useTeleportationItems);
		for (Map.Entry<Integer, Integer> entry : varbitValues.entrySet())
		{
			when(client.getVarbitValue(entry.getKey())).thenReturn(entry.getValue());
		}

		pathfinderConfig.refresh();
	}

	private void setupInventory(Item... items)
	{
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		doReturn(items).when(inventory).getItems();
	}

	private void setupQuestPointDatabase(int currentQuestPoints)
	{
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(Arrays.asList(1, 2, 3));
		when(client.getDBTableField(1, DBTableID.Quest.COL_RELEASE_TYPE, 0)).thenReturn(new Object[]{1});
		when(client.getDBTableField(2, DBTableID.Quest.COL_RELEASE_TYPE, 0)).thenReturn(new Object[]{1});
		when(client.getDBTableField(3, DBTableID.Quest.COL_RELEASE_TYPE, 0)).thenReturn(new Object[]{0});
		when(client.getDBTableField(1, DBTableID.Quest.COL_QUESTPOINTS, 0)).thenReturn(new Object[]{300});
		when(client.getDBTableField(2, DBTableID.Quest.COL_QUESTPOINTS, 0)).thenReturn(new Object[]{43});
		when(client.getDBTableField(3, DBTableID.Quest.COL_QUESTPOINTS, 0)).thenReturn(new Object[]{4});
		when(client.getVarpValue(VarPlayer.QUEST_POINTS)).thenReturn(currentQuestPoints);
	}

	private void setupEquipment(Item... items)
	{
		doReturn(equipment).when(client).getItemContainer(InventoryID.WORN);
		doReturn(items).when(equipment).getItems();
	}

	private void testTransportLength(int expectedLength, int origin, int destination)
	{
		testTransportLength(expectedLength, origin, destination, TeleportationItem.NONE, 99);
	}

	private void testTransportLength(
		int expectedLength,
		int origin,
		int destination,
		TeleportationItem useTeleportationItems,
		int skillLevel)
	{
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

	private void testTransportLength(int expectedLength, TransportType transportType)
	{
		testTransportLength(expectedLength, transportType, QuestState.FINISHED, 99, TeleportationItem.NONE);
	}

	private void testTransportLength(
		int expectedLength,
		TransportType transportType,
		QuestState questState,
		int skillLevel,
		TeleportationItem useTeleportationItems)
	{
		setupConfig(questState, skillLevel, useTeleportationItems);

		int counter = 0;
		PrimitiveIntHashMap<Transport[]> activeTransports = pathfinderConfig.getTransports();
		for (int origin : activeTransports.keys())
		{
			for (Transport transport : activeTransports.get(origin))
			{
				if (transportType.equals(transport.getType()))
				{
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
	private void testAllTransportsAvailableWithSinglePath(TransportType transportType)
	{
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);

		// Count expected transports from the full transport list
		int expectedCount = 0;
		for (int origin : transports.keySet())
		{
			for (Transport transport : transports.get(origin))
			{
				if (transportType.equals(transport.getType()))
				{
					expectedCount++;
				}
			}
		}

		// Count actual transports in the configured (usable) transports
		int actualCount = 0;
		for (Transport t : activeTransportList())
		{
			if (transportType.equals(t.getType()))
			{
				actualCount++;
			}
		}

		assertEquals("All " + transportType + " transports should be available", expectedCount, actualCount);
		assertTrue("At least one transport should exist", expectedCount > 0);

		// Test path calculation on just one transport to verify pathfinding works
		Transport sampleTransport = findSampleTransport(transportType);
		assertEquals(sampleTransport.toString(), 2, calculateTransportLength(sampleTransport));
	}

	private void testTransportMinimumLength(int minimumLength, int origin, int destination)
	{
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

	private int calculateTransportLength(Transport transport)
	{
		return calculatePathLength(transport.getOrigin(), transport.getDestination());
	}

	private void testSingleTransportScenario(String label, int expectedLength, TransportType transportType)
	{
		setupConfig(QuestState.FINISHED, 99, TeleportationItem.NONE);
		Transport transport = findSampleTransport(transportType);
		Assert.assertNotNull(transport);
		assertScenarioPathLength(label, expectedLength, transport.getOrigin(), transport.getDestination());
	}

	// A "scenario" is a single, named pathfinding example with a fixed origin and
	// destination that demonstrates a meaningful routing behaviour or regression.
	// Scenarios are intended for the debugging dashboard, so they should be cases
	// worth visualising: bank-state transitions, branch-leak regressions,
	// transport-vs-walking choices, tile/plane reuse, or other route-selection
	// behaviours. Broad transport coverage, static connectivity smoke tests, and
	// feature-availability checks are not scenarios even if they use coordinates.
	private void assertScenarioPathLength(String label, int expectedLength, int origin, int destination)
	{
		assertScenarioPathLengthAndGet(label, expectedLength, origin, destination);
	}

	private Pathfinder assertScenarioPathLengthAndGet(String label, int expectedLength, int origin, int destination)
	{
		Pathfinder pathfinder = runPathfinder(origin, destination);
		int actualLength = pathfinder.getPath().size();
		assertEquals(label, expectedLength, actualLength);
		return pathfinder;
	}

	private void assertScenarioMinimumPathLength(String label, int minimumLength, int origin, int destination)
	{
		Pathfinder pathfinder = runPathfinder(origin, destination);
		int actualLength = pathfinder.getPath().size();
		assertTrue("Scenario " + label + " had length " + actualLength + " < " + minimumLength, actualLength >= minimumLength);
	}

	private void assertScenarioPathLengthWithBank(
		String label,
		int expectedLength,
		int origin,
		int destination,
		TeleportationItem useTeleportationItems,
		Item... bankItems)
	{
		setupConfigWithBank(useTeleportationItems, bankItems);
		assertScenarioPathLength(label, expectedLength, origin, destination);
	}

	private Transport findSampleTransport(TransportType transportType)
	{
		for (int origin : transports.keySet())
		{
			for (Transport transport : transports.get(origin))
			{
				if (transportType.equals(transport.getType()))
				{
					int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
					int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
					int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
					int destY = WorldPointUtil.unpackWorldY(transport.getDestination());
					// Skip transports touching the POH interior: they are rejected by
					// PathfinderConfig unless usePoh is enabled, and the POH ring
					// destination is unreachable by walking, so a picked sample would
					// produce an arbitrarily long exhausted-search path instead of 2.
					if (ShortestPathPlugin.isInsidePoh(originX, originY)
						|| ShortestPathPlugin.isInsidePoh(destX, destY))
					{
						continue;
					}
					return transport;
				}
			}
		}
		fail("No transport of type " + transportType + " found");
		return null;
	}

	private int calculatePathLength(int origin, int destination)
	{
		Pathfinder pathfinder = runPathfinder(origin, destination);
		return pathfinder.getPath().size();
	}

	private Pathfinder runPathfinder(int origin, int destination)
	{
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, origin, Set.of(destination));
		pathfinder.run();
		return pathfinder;
	}

	private Pathfinder runScenario(int origin, int destination)
	{
		return runPathfinder(origin, destination);
	}

	private List<Transport> activeTransportList()
	{
		PrimitiveIntHashMap<Transport[]> active = pathfinderConfig.getTransports();
		List<Transport> all = new ArrayList<>();
		for (int origin : active.keys())
		{
			all.addAll(Arrays.asList(active.get(origin)));
		}
		return all;
	}

	private boolean hasTransportWithRequiredItem(PrimitiveIntHashMap<Transport[]> transports, int[] variationIds)
	{
		for (int origin : transports.keys())
		{
			for (Transport t : transports.get(origin))
			{
				TransportItems items = t.getItemRequirements();
				if (items == null)
				{
					continue;
				}
				int[][] reqs = items.getItems();
				for (int[] inner : reqs)
				{
					if (inner == null)
					{
						continue;
					}
					for (int id : inner)
					{
						for (int vid : variationIds)
						{
							if (id == vid)
							{
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
	 * Counts the number of Lovakengj Minecart Network transports in the active transport set.
	 * These are identified by having varbit 7796 among their requirements.
	 */
	private int countLovakenjMinecarts()
	{
		int count = 0;
		for (Transport t : activeTransportList())
		{
			if (t.isType(TransportType.MINECART) && t.hasVarbit(7796))
			{
				count++;
			}
		}
		return count;
	}

	private void setupConfigWithBank(Item... bankItems)
	{
		setupConfigWithBank(TeleportationItem.INVENTORY_AND_BANK, bankItems);
	}

	private void setupConfigWithBank(TeleportationItem useTeleportationItems, Item... bankItems)
	{
		setupConfigWithBank(QuestState.FINISHED, useTeleportationItems, bankItems);
	}

	private void setupConfigWithBank(QuestState questState, TeleportationItem useTeleportationItems, Item... bankItems)
	{
		pathfinderConfig = new TestPathfinderConfig(client, config, questState, true, true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(config.useTeleportationItems()).thenReturn(useTeleportationItems);
		when(config.usePoh()).thenReturn(false);
		doReturn(bankItems).when(bank).getItems();
		pathfinderConfig.bank = bank;
		pathfinderConfig.refresh();
	}

	/**
	 * Returns true if the given transport type was used anywhere along the path.
	 */
	private boolean usedTransportType(Pathfinder pathfinder, TransportType type)
	{
		for (int i = 1; i < pathfinder.getPath().size(); i++)
		{
			PathStep originStep = pathfinder.getPath().get(i - 1);
			int origin = originStep.getPackedPosition();
			int dest = pathfinder.getPath().get(i).getPackedPosition();

			Set<Transport> stepTransports = transportsForStep(origin, originStep.isBankVisited());
			for (Transport t : stepTransports)
			{
				if (t.getDestination() == dest && t.isType(type))
				{
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
	private boolean usedTransportWithDisplayInfo(Pathfinder pathfinder, TransportType type, String displayInfoSubstring)
	{
		return usedTransportWithDisplayInfo(pathfinder, type, displayInfoSubstring, false, false);
	}

	private boolean usedTransportWithDisplayInfoAfterFirstBank(Pathfinder pathfinder, TransportType type, String displayInfoSubstring)
	{
		return usedTransportWithDisplayInfo(pathfinder, type, displayInfoSubstring, false, true);
	}

	private boolean usedTransportWithDisplayInfoBeforeFirstBank(Pathfinder pathfinder, TransportType type, String displayInfoSubstring)
	{
		return usedTransportWithDisplayInfo(pathfinder, type, displayInfoSubstring, true, false);
	}

	private boolean usedTransportWithDisplayInfo(Pathfinder pathfinder, TransportType type, String displayInfoSubstring,
		boolean stopAtFirstBank, boolean startAfterFirstBank)
	{
		for (int i = 1; i < pathfinder.getPath().size(); i++)
		{
			PathStep originStep = pathfinder.getPath().get(i - 1);
			PathStep destStep = pathfinder.getPath().get(i);
			boolean bankVisited = destStep.isBankVisited();
			int origin = originStep.getPackedPosition();
			if (stopAtFirstBank && bankVisited)
			{
				return false;
			}
			if (startAfterFirstBank && !bankVisited)
			{
				continue;
			}

			for (Transport t : transportsForStep(origin, bankVisited))
			{
				if (t.getDestination() == destStep.getPackedPosition() && t.isType(type) && t.hasDisplayInfo(displayInfoSubstring))
				{
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasUsableTeleport(String displayInfo)
	{
		return hasUsableTeleport(displayInfo, false);
	}

	private boolean hasUsableTeleport(String displayInfo, boolean bankVisited)
	{
		for (Transport transport : pathfinderConfig.getUsableTeleports(bankVisited))
		{
			if (displayInfo.equals(transport.getDisplayInfo()))
			{
				return true;
			}
		}
		return false;
	}

	private Set<Transport> transportsForStep(int origin, boolean bankVisited)
	{
		Set<Transport> stepTransports = new java.util.HashSet<>(Arrays.asList(
			pathfinderConfig.getTransportsPacked(bankVisited).getOrDefault(origin, TransportAvailability.EMPTY_TRANSPORTS)));
		stepTransports.addAll(Arrays.asList(pathfinderConfig.getUsableTeleports(bankVisited)));
		return stepTransports;
	}

	/**
	 * With Fortis Colosseum bank gated by glory, the path from Auburnvale should bank at local booths
	 * (not the colosseum lobby chest) before using a ring of dueling to Ferox.
	 */
	@Test
	public void auburnvaleToFeroxBanksAtAuburnvaleWhenColosseumGloryNotMet()
	{
		when(config.calculationCutoff()).thenReturn(500);
		when(config.includeBankPath()).thenReturn(true);
		// Bank chest at Fortis Colosseum requires COLOSSEUM_GLORY &gt; 1999
		when(client.getVarpValue(eq(VarPlayerID.COLOSSEUM_GLORY))).thenReturn(0);
		setupConfigWithBank(new Item(ItemID.RING_OF_DUELING_8, 1));

		int auburnvaleStart = WorldPointUtil.packWorldPoint(1411, 3361, 0);
		int ferox = WorldPointUtil.packWorldPoint(3134, 3629, 0);
		Set<Integer> auburnvaleBankTiles = Set.of(
			WorldPointUtil.packWorldPoint(1416, 3350, 0),
			WorldPointUtil.packWorldPoint(1413, 3353, 0),
			WorldPointUtil.packWorldPoint(1419, 3353, 0),
			WorldPointUtil.packWorldPoint(1416, 3356, 0));
		int fortisColosseumBank = WorldPointUtil.packWorldPoint(1804, 9501, 0);

		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, auburnvaleStart, Set.of(ferox));
		pathfinder.run();
		assertTrue("expected path to Ferox", pathfinder.getResult() != null && pathfinder.getResult().isReached());

		// PathStep.bankVisited flips true on tiles *after* using bank inventory; the bank booth tile itself
		// may still carry bankVisited=false — assert we pass through Auburnvale booths and later use bank state.
		assertTrue(
			"path should walk onto an Auburnvale bank tile",
			pathfinder.getPath().stream().anyMatch(s -> auburnvaleBankTiles.contains(s.getPackedPosition())));
		assertTrue(
			"path should enter post-bank inventory state (for ring of dueling from bank)",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		assertFalse(
			"Fortis Colosseum bank tile should not appear on the shortest path when glory gates it off",
			pathfinder.getPath().stream().anyMatch(s -> s.getPackedPosition() == fortisColosseumBank));
	}

	// -----------------------------------------------------------------------
	// PathfinderConfig.computeCombatLevel
	// -----------------------------------------------------------------------

	/**
	 * Level 3: all combat stats at minimum.
	 */
	@Test
	public void combatLevelMinStats()
	{
		// base = 0.25 * (1 + 10 + 1/2) = 2.75, melee = 13*2/40 = 0.65 → floor(3.4) = 3
		assertEquals(3, PathfinderConfig.computeCombatLevel(1, 1, 1, 10, 1, 1, 1));
	}

	/**
	 * Level 126
	 */
	@Test
	public void combatLevelMaxMelee()
	{
		// base = 0.25*(99+99+49) = 61.75, melee = 13*198/40 = 64.35 → floor(126.1) = 126
		assertEquals(126, PathfinderConfig.computeCombatLevel(99, 99, 99, 99, 1, 1, 99));
	}

	/**
	 * Magic as the dominant combat style (99 magic, minimal else).
	 */
	@Test
	public void combatLevelMageDominant()
	{
		// mage = 13*(3*99/2)/40 = 48.1 → floor(2.75 + 48.1) = 50
		assertEquals(50, PathfinderConfig.computeCombatLevel(1, 1, 1, 10, 99, 1, 1));
	}

	/**
	 * Ranged as the dominant combat style (99 ranged, minimal else) — mirrors mage formula.
	 */
	@Test
	public void combatLevelRangeDominant()
	{
		assertEquals(50, PathfinderConfig.computeCombatLevel(1, 1, 1, 10, 1, 99, 1));
	}

	/**
	 * Typical mid-level account: 70 attack/strength/defence/hp, 43 prayer → level 85.
	 */
	@Test
	public void combatLevelTypicalMeleeAccount()
	{
		// base = 0.25*(70+70+21) = 40.25, melee = 13*140/40 = 45.5 → floor(85.75) = 85
		assertEquals(85, PathfinderConfig.computeCombatLevel(70, 70, 70, 70, 1, 1, 43));
	}

	/**
	 * Higher prayer must not lower the combat level.
	 */
	@Test
	public void combatLevelHigherPrayerRaisesLevel()
	{
		int withLowPrayer = PathfinderConfig.computeCombatLevel(60, 60, 60, 60, 1, 1, 1);
		int withHighPrayer = PathfinderConfig.computeCombatLevel(60, 60, 60, 60, 1, 1, 99);
		assertTrue("Higher prayer should yield a higher or equal combat level",
			withHighPrayer >= withLowPrayer);
	}

	private static final class ChangingTimePathfinderConfig extends TestPathfinderConfig
	{
		private final long refreshTimeMinutes;
		private final long laterTimeMinutes;
		private boolean timeRead;

		private ChangingTimePathfinderConfig(Client client, ShortestPathConfig config,
			QuestState questState, boolean bypassVarbitChecks, boolean bypassVarPlayerChecks,
			long refreshTimeMinutes, long laterTimeMinutes)
		{
			super(client, config, questState, bypassVarbitChecks, bypassVarPlayerChecks);
			this.refreshTimeMinutes = refreshTimeMinutes;
			this.laterTimeMinutes = laterTimeMinutes;
		}

		@Override
		protected long currentTimeMinutes()
		{
			if (!timeRead)
			{
				timeRead = true;
				return refreshTimeMinutes;
			}
			return laterTimeMinutes;
		}
	}

}
