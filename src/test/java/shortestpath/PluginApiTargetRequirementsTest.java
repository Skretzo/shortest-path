package shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.AStarPathfinder;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.TargetStatus;
import shortestpath.presentation.BankPickupRequirements.BankPickupResult;
import shortestpath.settings.TeleportationItem;

/**
 * The public plugin-message API for item requirements on targets ({@code path}, {@code query},
 * {@code getTarget}, {@code clear}), driven through a real plugin on the real map.
 *
 * <p>The geography, all from the Lumbridge Castle courtyard: {@link #NEAR} is 19 to walk to, or 61
 * through the castle's top-floor bank; {@link #FARTHER} is 39 and {@link #FAR} 77 to walk to.
 * {@link #BLOCKED} is a blocked tile, so a route to it ends on a walkable tile beside it.
 */
public class PluginApiTargetRequirementsTest
{
	private static final WorldPoint START = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint NEAR = new WorldPoint(3240, 3225, 0);
	private static final WorldPoint FARTHER = new WorldPoint(3260, 3230, 0);
	private static final WorldPoint FAR = new WorldPoint(3180, 3260, 0);
	private static final WorldPoint BLOCKED = new WorldPoint(3255, 3225, 0);
	private static final WorldPoint EMIRS_ARENA = new WorldPoint(3315, 3235, 0);

	private static Map<String, Object> target(Object point, Object requirements)
	{
		Map<String, Object> target = new HashMap<>();
		target.put("point", point);
		if (requirements != null)
		{
			target.put("requirements", requirements);
		}
		return target;
	}

	private static Set<Object> targets(Object... targets)
	{
		return new LinkedHashSet<>(Arrays.asList(targets));
	}

	private static int cost(Map<String, Object> result)
	{
		return (Integer) result.get("cost");
	}

	/**
	 * The requested target a result reached. With several targets legacy stops on the first tile
	 * within {@code unreachableTargetDistance} of one, so the path's last tile may sit beside it.
	 */
	private static WorldPoint reached(Map<String, Object> result)
	{
		assertEquals(true, result.get("reached"));
		WorldPoint target = (WorldPoint) result.get("target");
		assertTrue(lastStep(result).distanceTo(target) <= 2);
		return target;
	}

	private static WorldPoint lastStep(Map<String, Object> result)
	{
		List<?> path = (List<?>) result.get("path");
		return (WorldPoint) path.get(path.size() - 1);
	}

	private static List<String> transportsUsed(Map<String, Object> result)
	{
		List<String> used = new ArrayList<>();
		for (Object edge : (List<?>) result.get("transports"))
		{
			used.add(String.valueOf(((Map<?, ?>) edge).get("displayInfo")));
		}
		return used;
	}

	private static void assertDirect(Map<String, Object> result, int plainCost)
	{
		assertEquals(true, result.get("reached"));
		assertEquals(false, result.get("bankVisited"));
		assertEquals("routes exactly as a target without requirements", plainCost, cost(result));
	}

	// ---- Targets without requirements keep their behaviour ----

	@Test
	public void plainTargetReportsNoRequirementStatus()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Map<String, Object> result = api.query(START, NEAR);
			assertEquals(true, result.get("reached"));
			assertEquals(19, cost(result));
			assertNull(result.get("targetStatus"));
			assertEquals(List.of(), result.get("missingItems"));
			assertEquals(false, result.get("bankVisited"));
		}
	}

	@Test
	public void targetMapWithoutRequirementsIsAPlainTarget()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			assertDirect(api.query(START, target(NEAR, null)), 19);
			assertNull(api.query(START, target(NEAR, null)).get("targetStatus"));
		}
	}

	@Test
	public void targetMapAcceptsPackedPoints()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.carry(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START,
				target(WorldPointUtil.packWorldPoint(NEAR), ItemID.SPADE));
			assertDirect(result, 19);
			assertEquals("CARRIED", result.get("targetStatus"));
		}
	}

	@Test
	public void targetsMayBeAnyCollectionMixingPointsAndMaps()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Map<String, Object> result = api.query(START, List.of(FAR, target(NEAR, null)));
			assertEquals(NEAR, reached(result));
		}
	}

	// ---- Where the items are decides the route ----

	@Test
	public void carriedItemsRouteDirectly()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.carry(ItemID.SPADE, 1).bank(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START, target(NEAR, ItemID.SPADE));
			assertDirect(result, 19);
			assertEquals("CARRIED", result.get("targetStatus"));
			assertEquals(List.of(), result.get("missingItems"));
		}
	}

	@Test
	public void wornItemsCountAsCarried()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.wear(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START, target(NEAR, ItemID.SPADE));
			assertDirect(result, 19);
			assertEquals("CARRIED", result.get("targetStatus"));
		}
	}

	@Test
	public void bankedItemsRouteThroughABankFirst()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			assertFalse("bank visits are off in the settings", api.settings.includeBankPath);
			api.bank(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START, target(NEAR, ItemID.SPADE));
			assertEquals(true, result.get("reached"));
			assertEquals("BANK", result.get("targetStatus"));
			assertEquals(true, result.get("bankVisited"));
			assertEquals(NEAR, lastStep(result));
			assertEquals(61, cost(result));
			assertEquals(List.of(), result.get("missingItems"));
		}
	}

	@Test
	public void bankVisitCostIsPaidForTheDetour()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			int free = cost(api.query(START, target(NEAR, ItemID.SPADE)));
			api.settings.costBankVisit = 25;
			assertEquals(free + 25, cost(api.query(START, target(NEAR, ItemID.SPADE))));
		}
	}

	@Test
	public void itemsInABankNotSeenThisSessionAreUnknown()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bankNotSeen();
			Map<String, Object> result = api.query(START, target(NEAR, ItemID.SPADE));
			assertDirect(result, 19);
			assertEquals("UNKNOWN", result.get("targetStatus"));
			assertEquals(List.of(List.of(ItemID.SPADE)), result.get("missingItems"));
		}
	}

	@Test
	public void itemsOwnedNowhereAreUnknownAndStillRouted()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.KNIFE, 1);
			Map<String, Object> result = api.query(START, target(NEAR, ItemID.SPADE));
			assertDirect(result, 19);
			assertEquals("UNKNOWN", result.get("targetStatus"));
			assertEquals(List.of(List.of(ItemID.SPADE)), result.get("missingItems"));
		}
	}

	@Test
	public void bankIsCountedWhateverTheTeleportItemSetting()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			for (TeleportationItem setting : TeleportationItem.values())
			{
				api.settings.teleportationItems = setting;
				api.bank(ItemID.SPADE, 1);
				assertEquals(setting.name(), "BANK",
					api.query(START, target(NEAR, ItemID.SPADE)).get("targetStatus"));
			}
		}
	}

	// ---- Several targets: one search picks the cheapest one that can be completed ----

	@Test
	public void fartherTargetWinsOverACloserOneThatNeedsABankTrip()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			assertEquals("without requirements the closer target wins", NEAR,
				reached(api.query(START, targets(NEAR, FARTHER))));

			api.bank(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START, targets(target(NEAR, ItemID.SPADE), FARTHER));
			assertEquals("39 to walk beats 61 through the bank", FARTHER, reached(result));
			assertEquals(false, result.get("bankVisited"));
			assertNull(result.get("targetStatus"));
		}
	}

	@Test
	public void bankTripWinsWhenItIsStillCheapest()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START, targets(target(NEAR, ItemID.SPADE), FAR));
			assertEquals("61 through the bank beats 77 to walk", NEAR, reached(result));
			assertEquals(true, result.get("bankVisited"));
			assertEquals("BANK", result.get("targetStatus"));
		}
	}

	@Test
	public void unknownTargetIsDroppedWhileAnotherCanBeCompleted()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Map<String, Object> result = api.query(START, targets(target(NEAR, ItemID.SPADE), FAR));
			assertEquals(FAR, reached(result));
			assertNull(result.get("targetStatus"));
			assertEquals(List.of(), result.get("missingItems"));
		}
	}

	@Test
	public void everyTargetUnknownRoutesToTheClosest()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Map<String, Object> result = api.query(START,
				targets(target(FARTHER, ItemID.SPADE), target(NEAR, ItemID.KNIFE)));
			assertEquals(NEAR, reached(result));
			assertEquals("UNKNOWN", result.get("targetStatus"));
			assertEquals("the reached target's own missing items",
				List.of(List.of(ItemID.KNIFE)), result.get("missingItems"));
		}
	}

	@Test
	public void eachTargetIsJudgedByItsOwnRequirement()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.carry(ItemID.KNIFE, 1).bank(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START,
				targets(target(NEAR, ItemID.SPADE), target(FARTHER, ItemID.KNIFE)));
			assertEquals(FARTHER, reached(result));
			assertEquals("CARRIED", result.get("targetStatus"));
		}
	}

	@Test
	public void blockedTargetEndsBesideItAfterTheBank()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Map<String, Object> plain = api.query(START, BLOCKED);
			assertEquals(true, plain.get("reached"));
			assertFalse("the target tile itself is blocked", BLOCKED.equals(lastStep(plain)));

			api.bank(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START, target(BLOCKED, ItemID.SPADE));
			assertEquals(true, result.get("reached"));
			assertEquals(true, result.get("bankVisited"));
			assertEquals("the fallback tile counts for the target", "BANK", result.get("targetStatus"));
			assertTrue(lastStep(result).distanceTo(BLOCKED) <= 2);
		}
	}

	// ---- Requirement shapes ----

	@Test
	public void setIsAnyOneOfTheItems()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.carry(ItemID.KNIFE, 1);
			assertEquals("CARRIED", api.query(START,
				target(NEAR, new HashSet<>(List.of(ItemID.SPADE, ItemID.KNIFE)))).get("targetStatus"));
		}
	}

	@Test
	public void quantityMapCountsCarriedAndBankedTogether()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Object thousandCoins = Map.of(ItemID.COINS, 1000);
			api.carry(ItemID.COINS, 500).bankNotSeen();
			assertEquals("UNKNOWN", api.query(START, target(NEAR, thousandCoins)).get("targetStatus"));
			api.bank(ItemID.COINS, 600);
			assertEquals("BANK", api.query(START, target(NEAR, thousandCoins)).get("targetStatus"));
			api.carry(ItemID.COINS, 1000);
			assertEquals("CARRIED", api.query(START, target(NEAR, thousandCoins)).get("targetStatus"));
		}
	}

	@Test
	public void quantityMapAlternativesEachHaveTheirOwnQuantity()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.carry(ItemID.KNIFE, 2);
			Object needs = Map.of(ItemID.SPADE, 1, ItemID.KNIFE, 3);
			assertEquals("UNKNOWN", api.query(START, target(NEAR, needs)).get("targetStatus"));
			api.carry(ItemID.KNIFE, 3);
			assertEquals("CARRIED", api.query(START, target(NEAR, needs)).get("targetStatus"));
		}
	}

	@Test
	public void listNeedsEveryGroupAndReportsOnlyTheMissingOnes()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Object needs = List.of(ItemID.SPADE, Set.of(ItemID.ROPE), Map.of(ItemID.TINDERBOX, 2));
			api.carry(ItemID.SPADE, 1).bank(ItemID.ROPE, 1);
			Map<String, Object> result = api.query(START, target(NEAR, needs));
			assertEquals("UNKNOWN", result.get("targetStatus"));
			assertEquals(List.of(List.of(ItemID.TINDERBOX)), result.get("missingItems"));

			api.bank(ItemID.ROPE, 1, ItemID.TINDERBOX, 2);
			result = api.query(START, target(NEAR, needs));
			assertEquals("one group from the bank makes the whole target need it", "BANK",
				result.get("targetStatus"));
			assertEquals(true, result.get("bankVisited"));
		}
	}

	@Test
	public void listElementsMayThemselvesBeListsOfAlternatives()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.carry(ItemID.KNIFE, 1, ItemID.ROPE, 1);
			Object needs = List.of(List.of(ItemID.SPADE, ItemID.KNIFE), ItemID.ROPE);
			assertEquals("CARRIED", api.query(START, target(NEAR, needs)).get("targetStatus"));
		}
	}

	@Test
	public void sameTargetTwiceIsFineWhenTheyAgree()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START,
				List.of(target(NEAR, ItemID.SPADE), target(WorldPointUtil.packWorldPoint(NEAR), ItemID.SPADE)));
			assertEquals("BANK", result.get("targetStatus"));
		}
	}

	// ---- Malformed requirements are rejected ----

	@Test
	public void malformedTargetsAreInvalidQueries()
	{
		Map<String, Object> missingPoint = new HashMap<>();
		missingPoint.put("requirements", ItemID.SPADE);
		List<Object> malformed = List.of(
			target(NEAR, -1),
			target(NEAR, "SPADE=1"),
			target(NEAR, Map.of(ItemID.SPADE, 0)),
			target(NEAR, Map.of(ItemID.SPADE, "1")),
			target(NEAR, Map.of()),
			target(NEAR, List.of()),
			target(NEAR, Set.of()),
			target(NEAR, List.of(ItemID.SPADE, "rope")),
			target(NEAR, 952L),
			target("3240 3225 0", ItemID.SPADE),
			missingPoint,
			List.of(target(NEAR, ItemID.SPADE), NEAR),
			List.of(target(NEAR, ItemID.SPADE), target(NEAR, ItemID.KNIFE)),
			"not a target");
		try (PluginApiHarness api = new PluginApiHarness())
		{
			for (Object target : malformed)
			{
				Map<String, Object> result = api.query(START, target);
				assertEquals(String.valueOf(target), "INVALID", result.get("reason"));
				assertEquals(false, result.get("reached"));
				assertNull(result.get("targetStatus"));
				assertEquals(List.of(), result.get("missingItems"));
				assertEquals(false, result.get("bankVisited"));
			}
		}
	}

	@Test
	public void malformedPathMessageLeavesTheDisplayedPathAlone()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			ActiveSearch shown = api.path(START, NEAR);
			api.path(START, target(NEAR, "spade"));
			assertSame(shown, api.activeSearch());
		}
	}

	@Test
	public void pathMessageWithoutStartOrTargetIsIgnored()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			ActiveSearch shown = api.path(START, NEAR);
			api.send("path", new HashMap<>());
			assertSame(shown, api.plugin.getActiveSearch());
		}
	}

	@Test
	public void blockedTargetWithUnfoundItemsStillReportsTheTarget()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Map<String, Object> result = api.query(START, target(BLOCKED, ItemID.SPADE));
			assertEquals(true, result.get("reached"));
			assertEquals("UNKNOWN", result.get("targetStatus"));
			assertEquals(List.of(List.of(ItemID.SPADE)), result.get("missingItems"));
			assertEquals(false, result.get("bankVisited"));
			assertTrue(lastStep(result).distanceTo(BLOCKED) <= 2);
		}
	}

	// The Soul Wars lobby bank tile routes through in bankedItemsRouteThroughABankFirst use.
	private static final WorldPoint SOUL_WARS_BANK = new WorldPoint(2020, 5931, 0);

	@Test
	public void bankTileTargetReportsTheBankVisit()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			Map<String, Object> result = api.query(START, target(SOUL_WARS_BANK, ItemID.SPADE));
			assertEquals(true, result.get("reached"));
			assertEquals("BANK", result.get("targetStatus"));
			assertEquals("the route ends inside the bank", true, result.get("bankVisited"));
			assertEquals(SOUL_WARS_BANK, lastStep(result));
		}
	}

	@Test
	public void bankTileTargetListsThePickupAtTheFinalStep()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			ActiveSearch search = api.path(START, target(SOUL_WARS_BANK, ItemID.SPADE));
			List<PathStep> path = search.getPath();
			assertTrue(last(path).isBankVisited());
			assertEquals(SOUL_WARS_BANK, WorldPointUtil.unpackWorldPoint(last(path).getPackedPosition()));
			BankPickupResult pickup = api.plugin.getBankPickup(path, path.size() - 1);
			assertNotNull(pickup);
			assertEquals(List.of("1 Item " + ItemID.SPADE), pickup.phrases);
		}
	}

	// ---- The displayed path ----

	@Test
	public void displayedPathRoutesThroughTheBank()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			ActiveSearch search = api.path(START, target(NEAR, ItemID.SPADE));
			PathfinderResult result = search.getResult();
			assertTrue(result.isReached());
			assertEquals(WorldPointUtil.packWorldPoint(NEAR), result.getTarget());
			assertTrue(last(search.getPath()).getBankVisitState() == BankVisitState.BANKED);
			assertEquals(TargetStatus.BANK, result.getArrival().getStatus());
			assertEquals(WorldPointUtil.packWorldPoint(NEAR), result.getArrival().getTarget().getPoint());
			assertNull("nothing is missing", api.missingItemsLabel());
		}
	}

	@Test
	public void displayedPathListsTargetItemsAtTheBankStep()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			ActiveSearch search = api.path(START, target(NEAR, ItemID.SPADE));
			List<PathStep> path = search.getPath();
			int lastUnbanked = -1;
			for (int i = 0; i < path.size(); i++)
			{
				if (!path.get(i).isBankVisited())
				{
					lastUnbanked = i;
				}
			}
			assertTrue("the route visits a bank", lastUnbanked >= 0 && lastUnbanked < path.size() - 1);

			String pickup = "1 Item " + ItemID.SPADE;
			BankPickupResult atBank = api.plugin.getBankPickup(path, lastUnbanked);
			assertNotNull(atBank);
			assertEquals(List.of(pickup), atBank.phrases);
			assertEquals(Set.of(ItemID.SPADE), atBank.bankItemIds);

			assertTrue("the target step itself lists no pickup",
				api.plugin.getBankPickup(path, path.size() - 1).phrases.isEmpty());
		}
	}

	@Test
	public void bankStepListsTransportAndTargetPickupsTogether()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.settings.teleportationItems = TeleportationItem.INVENTORY_AND_BANK;
			api.bank(ItemID.SPADE, 1, ItemID.RING_OF_DUELING_8, 1);
			ActiveSearch search = api.path(START, target(EMIRS_ARENA, ItemID.SPADE));
			assertTrue("the route uses the banked ring",
				transportsUsed(search).stream().anyMatch(t -> t.contains("Ring of dueling")));

			List<PathStep> path = search.getPath();
			int lastUnbanked = -1;
			for (int i = 0; i < path.size(); i++)
			{
				if (!path.get(i).isBankVisited())
				{
					lastUnbanked = i;
				}
			}
			assertTrue("the route visits a bank", lastUnbanked >= 0 && lastUnbanked < path.size() - 1);

			List<String> pickups = api.plugin.getBankPickup(path, lastUnbanked).phrases;
			assertTrue("the spade is listed: " + pickups,
				pickups.contains("1 Item " + ItemID.SPADE));
			assertTrue("the ring is listed: " + pickups,
				pickups.stream().anyMatch(p -> p.contains("Item " + ItemID.RING_OF_DUELING_8)));
		}
	}

	private static List<String> transportsUsed(ActiveSearch search)
	{
		List<String> used = new ArrayList<>();
		for (PathStep step : search.getPath())
		{
			if (step.getTransport() != null)
			{
				used.add(String.valueOf(step.getTransport().getDisplayInfo()));
			}
		}
		return used;
	}

	@Test
	public void displayedPathNamesItemsThatWereNotFound()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.KNIFE, 1);
			api.path(START, target(NEAR, List.of(List.of(ItemID.SPADE, ItemID.ROPE), ItemID.KNIFE)));
			assertEquals("Not found in inventory or bank: Item 952 or Item 954", api.missingItemsLabel());

			api.bankNotSeen();
			api.path(START, target(NEAR, ItemID.SPADE));
			assertEquals("Not found in inventory or bank: Item 952 (bank not checked)", api.missingItemsLabel());
		}
	}

	@Test
	public void currentTargetReturnsRequirementsInTheirCanonicalShape()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			Object needs = List.of(ItemID.SPADE, Set.of(ItemID.ROPE, ItemID.KNIFE), Map.of(ItemID.COINS, 50));
			api.path(START, targets(target(NEAR, needs), FAR));
			Map<String, Object> current = api.currentTarget();
			assertEquals(true, current.get("set"));
			Object canonical = List.of(Map.of(ItemID.SPADE, 1), Map.of(ItemID.ROPE, 1, ItemID.KNIFE, 1),
				Map.of(ItemID.COINS, 50));
			assertEquals(Set.of(FAR, Map.of("point", NEAR, "requirements", canonical)),
				new HashSet<>((List<?>) current.get("target")));
		}
	}

	@Test
	public void currentTargetCanBeSentBackUnchanged()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			api.path(START, targets(target(NEAR, List.of(ItemID.SPADE, Map.of(ItemID.COINS, 50))), FAR));
			Object returned = api.currentTarget().get("target");
			api.send("clear", new HashMap<>());
			api.path(START, returned);
			assertEquals(returned, api.currentTarget().get("target"));
		}
	}

	@Test
	public void currentTargetIsUnchangedForCallersWithoutRequirements()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.path(START, targets(NEAR, FAR));
			assertEquals(Set.of(NEAR, FAR), new HashSet<>((List<?>) api.currentTarget().get("target")));
		}
	}

	@Test
	public void pathMessageWithoutTargetsKeepsTargetsAndRequirements()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			api.path(START, target(NEAR, ItemID.SPADE));
			ActiveSearch restarted = api.path(new WorldPoint(3223, 3218, 0), null);
			assertTrue("the restart still banks for the spade", last(restarted.getPath()).getBankVisitState() == BankVisitState.BANKED);
			assertEquals(Set.of(Map.of("point", NEAR, "requirements", List.of(Map.of(ItemID.SPADE, 1)))),
				new HashSet<>((List<?>) api.currentTarget().get("target")));
		}
	}

	@Test
	public void newTargetsReplaceRequirements()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			api.path(START, target(NEAR, ItemID.SPADE));
			ActiveSearch replaced = api.path(START, NEAR);
			assertFalse(last(replaced.getPath()).getBankVisitState() == BankVisitState.BANKED);
			assertEquals(List.of(NEAR), api.currentTarget().get("target"));
		}
	}

	@Test
	public void clearDropsTargetsAndRequirements()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			api.path(START, target(NEAR, ItemID.SPADE));
			api.send("clear", new HashMap<>());
			assertEquals(false, api.currentTarget().get("set"));
			assertFalse(last(api.path(START, NEAR).getPath()).getBankVisitState() == BankVisitState.BANKED);
		}
	}

	// ---- A forced bank visit unlocks no teleports from the bank ----

	@Test
	public void bankVisitForTargetItemsDoesNotUseBankedTeleports()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.settings.teleportationItems = TeleportationItem.INVENTORY;
			api.bank(ItemID.SPADE, 1);
			Map<String, Object> spadeOnly = api.query(START, target(EMIRS_ARENA, ItemID.SPADE));
			api.bank(ItemID.SPADE, 1, ItemID.RING_OF_DUELING_8, 1);
			Map<String, Object> withRing = api.query(START, target(EMIRS_ARENA, ItemID.SPADE));
			assertEquals(true, withRing.get("bankVisited"));
			assertEquals("a ring in the bank changes nothing", cost(spadeOnly), cost(withRing));
			assertTrue(transportsUsed(withRing).stream().noneMatch(t -> t.contains("Ring of dueling")));

			api.settings.teleportationItems = TeleportationItem.INVENTORY_AND_BANK;
			Map<String, Object> bankTeleports = api.query(START, target(EMIRS_ARENA, ItemID.SPADE));
			assertTrue("with bank teleports allowed the ring is used",
				transportsUsed(bankTeleports).stream().anyMatch(t -> t.contains("Ring of dueling")));
			assertTrue(cost(bankTeleports) < cost(withRing));
		}
	}

	// ---- The A* backend agrees with legacy ----

	@Test
	public void legacyIsTheDefaultAndDispatchesToTheLegacyImplementation()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			assertSame(PathfinderBackend.LEGACY, api.settings.pathfinderBackend());
			ActiveSearch search = api.path(START, FAR);
			assertTrue(search instanceof Pathfinder);
			assertFalse(search instanceof AStarPathfinder);
		}
	}

	@Test
	public void aStarBackendRoutesThroughTheBankAtTheSameCost()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.bank(ItemID.SPADE, 1);
			int legacy = cost(api.query(START, target(NEAR, ItemID.SPADE)));

			api.settings.selectedBackend = PathfinderBackend.ASTAR;
			ActiveSearch search = api.path(START, target(NEAR, ItemID.SPADE));
			assertTrue(search instanceof AStarPathfinder);
			PathfinderResult result = search.getResult();
			assertNotNull(result);
			assertTrue(result.getMessage(), result.isReached());
			assertEquals(legacy, result.getPathCost());
			assertTrue(last(search.getPath()).getBankVisitState() == BankVisitState.BANKED);
			assertEquals(WorldPointUtil.packWorldPoint(NEAR), last(search.getPath()).getPackedPosition());
		}
	}

	@Test
	public void aStarBackendHandlesEveryStatus()
	{
		try (PluginApiHarness api = new PluginApiHarness())
		{
			api.settings.selectedBackend = PathfinderBackend.ASTAR;

			api.carry(ItemID.SPADE, 1);
			ActiveSearch carried = api.path(START, target(NEAR, ItemID.SPADE));
			assertEquals(19, carried.getResult().getPathCost());
			assertFalse(last(carried.getPath()).getBankVisitState() == BankVisitState.BANKED);

			api.carry().bank(ItemID.SPADE, 1);
			ActiveSearch farther = api.path(START, targets(target(NEAR, ItemID.SPADE), FARTHER));
			assertEquals(WorldPointUtil.packWorldPoint(FARTHER), last(farther.getPath()).getPackedPosition());
			assertEquals(39, farther.getResult().getPathCost());

			ActiveSearch blocked = api.path(START, target(BLOCKED, ItemID.SPADE));
			assertTrue(blocked.getResult().isReached());
			assertTrue(last(blocked.getPath()).getBankVisitState() == BankVisitState.BANKED);

			api.bank();
			ActiveSearch unknown = api.path(START, target(NEAR, ItemID.SPADE));
			assertEquals(19, unknown.getResult().getPathCost());
			assertEquals("Not found in inventory or bank: Item 952", api.missingItemsLabel());
		}
	}

	private static PathStep last(List<PathStep> path)
	{
		return path.get(path.size() - 1);
	}
}
