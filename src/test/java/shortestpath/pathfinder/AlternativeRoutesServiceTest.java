package shortestpath.pathfinder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.AlternativeRoutesMode;
import shortestpath.AlternativeRoutesService;
import shortestpath.MethodAvailability;
import shortestpath.RouteOption;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportMethod;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.transport.TransportType;

/**
 * Covers the alternative-routes generator: the walk cheapest case now stops at the pure-walk option
 * (nothing costlier shown after it), teleport weights are charged into route cost, and user exclusions
 * keep a method out of the results.
 */
@RunWith(MockitoJUnitRunner.class)
public class AlternativeRoutesServiceTest
{
	// Two tiles west of Varrock centre: walking (cost 2) beats every teleport, so walking is the whole
	// route list.
	private static final int START = WorldPointUtil.packWorldPoint(3215, 3424, 0);
	private static final int TARGET = WorldPointUtil.packWorldPoint(3213, 3424, 0);
	// Lumbridge, far south: here the Varrock Teleport lands on TARGET (Varrock centre) and beats the long
	// walk, so a teleport route is the cheapest and does show up — used to test weights and exclusions.
	private static final int FAR_START = WorldPointUtil.packWorldPoint(3222, 3218, 0);

	@Mock
	Client client;
	@Mock
	ClientThread clientThread;
	@Mock
	ItemContainer inventory;
	@Mock
	ItemContainer bank;
	@Mock
	ShortestPathConfig config;

	@Before
	public void before()
	{
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.useTeleportationSpells()).thenReturn(true);
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenAnswer(invocation -> Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		// Runes for Varrock Teleport in the inventory.
		doReturn(new Item[]{
			new Item(ItemID.LAWRUNE, 10),
			new Item(ItemID.AIRRUNE, 30),
			new Item(ItemID.FIRERUNE, 10),
		}).when(inventory).getItems();
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		// The service bounces refreshes onto the client thread; run them inline.
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
	}

	@Test
	public void walkBestRouteStopsAtWalk() throws Exception
	{
		// Walking the 2 tiles beats every teleport, so we stop at the pure-walk option: the result is
		// just the walk route, with no costlier teleport routes tacked on after it.
		PathfinderConfig planning = new TestPathfinderConfig(client, config).copyForPlanning();
		planning.setPlanningMode(false);
		planning.setConsiderBank(false);
		AlternativeRoutesService service = new AlternativeRoutesService(clientThread, planning);

		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<List<RouteOption>> finalRoutes = new AtomicReference<>(List.of());
		service.generate(START, Set.of(TARGET), Set.of(), AlternativeRoutesMode.OWNED_INVENTORY, 10,
			(routes, catalog, unavailable, isDone) ->
			{
				if (isDone)
				{
					finalRoutes.set(routes);
					done.countDown();
				}
			});
		assertTrue("Generation should complete", done.await(120, TimeUnit.SECONDS));
		service.shutdown();

		List<RouteOption> routes = finalRoutes.get();
		assertTrue("Expected the walking route", !routes.isEmpty());
		assertTrue("The last route must be the pure-walk option (nothing shown after it)",
			routes.get(routes.size() - 1).isWalkOnly());
		assertTrue("No teleport route should be shown once walking is the cheapest option",
			routes.stream().noneMatch(r -> r.getMethods().stream().anyMatch(
				m -> m.getType() == TransportType.TELEPORTATION_SPELL)));
		assertTrue("Every route should reach the target",
			routes.stream().allMatch(RouteOption::isReached));
	}

	@Test
	public void teleportWeightIsChargedIntoRouteCost() throws Exception
	{
		// Far from Varrock the teleport (landing on the target) beats the long walk, so it shows up.
		// With a 100-step weight on teleport spells, that route's cost must carry the surcharge.
		when(config.costTeleportationSpells()).thenReturn(100);
		PathfinderConfig planning = new TestPathfinderConfig(client, config).copyForPlanning();
		planning.setPlanningMode(false);
		planning.setConsiderBank(false);
		AlternativeRoutesService service = new AlternativeRoutesService(clientThread, planning);

		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<List<RouteOption>> finalRoutes = new AtomicReference<>(List.of());
		service.generate(FAR_START, Set.of(TARGET), Set.of(), AlternativeRoutesMode.OWNED_INVENTORY, 10,
			(routes, catalog, unavailable, isDone) ->
			{
				if (isDone)
				{
					finalRoutes.set(routes);
					done.countDown();
				}
			});
		assertTrue("Generation should complete", done.await(120, TimeUnit.SECONDS));
		service.shutdown();

		RouteOption spellRoute = finalRoutes.get().stream()
			.filter(r -> r.getMethods().stream().anyMatch(m -> m.getType() == TransportType.TELEPORTATION_SPELL))
			.findFirst().orElse(null);
		assertTrue("A Varrock Teleport route should be the cheapest way there", spellRoute != null);
		assertTrue("The teleport route must carry the 100-step weight, got " + spellRoute.getTotalCost(),
			spellRoute.getTotalCost() >= 100);
	}

	@Test
	public void bankModeRouteShowsWalkToBankBeforeTeleport() throws Exception
	{
		// Cowbell amulet only in the bank (Varrock centre -> cowbell destination). The route must
		// contain the walked leg to a bank BEFORE the teleport jump — not teleport from the start tile.
		int varrockCentre = WorldPointUtil.packWorldPoint(3213, 3424, 0);
		int cowbellDestination = WorldPointUtil.packWorldPoint(3259, 3277, 0);
		doReturn(new Item[]{new Item(33104, 1)}).when(bank).getItems();
		PathfinderConfig base = new TestPathfinderConfig(client, config);
		base.bank = bank; // what ShortestPathPlugin.onItemContainerChanged does while the bank is open

		PathfinderConfig planning = base.copyForPlanning();
		planning.setPlanningMode(false);
		planning.setConsiderBank(true);
		AlternativeRoutesService service = new AlternativeRoutesService(clientThread, planning);

		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<List<RouteOption>> finalRoutes = new AtomicReference<>(List.of());
		service.generate(varrockCentre, Set.of(cowbellDestination), Set.of(), AlternativeRoutesMode.OWNED_WITH_BANK, 10,
			(routes, catalog, unavailable, isDone) ->
			{
				if (isDone)
				{
					finalRoutes.set(routes);
					done.countDown();
				}
			});
		assertTrue("Generation should complete", done.await(120, TimeUnit.SECONDS));
		service.shutdown();

		RouteOption bankedRoute = finalRoutes.get().stream()
			.filter(r -> r.getMethods().stream().anyMatch(m -> m.getType() == TransportType.TELEPORTATION_ITEM))
			.findFirst().orElse(null);
		assertTrue("A route using the banked teleport item should exist", bankedRoute != null);

		List<PathStep> path = bankedRoute.getPath();
		int firstBankVisited = -1;
		for (int i = 0; i < path.size(); i++)
		{
			if (path.get(i).isBankVisited())
			{
				firstBankVisited = i;
				break;
			}
		}
		assertTrue("Route must pass through a bank (bankVisited must flip)", firstBankVisited >= 0);
		assertTrue("Route must include the walked leg to the bank before the teleport, but bankVisited "
				+ "flipped at step " + firstBankVisited + " of " + path.size(),
			firstBankVisited > 5);
		assertTrue("Route must be flagged as going via the bank (panel indicator)", bankedRoute.isViaBank());
	}

	@Test
	public void userExcludedTeleportIsNotSeeded() throws Exception
	{
		PathfinderConfig planning = new TestPathfinderConfig(client, config).copyForPlanning();
		planning.setPlanningMode(false);
		planning.setConsiderBank(false);
		AlternativeRoutesService service = new AlternativeRoutesService(clientThread, planning);

		// Far from Varrock the Varrock Teleport (landing on the target) is the cheapest route, so it
		// surfaces. Capture its method identity from a first, unexcluded run.
		CountDownLatch firstDone = new CountDownLatch(1);
		AtomicReference<List<RouteOption>> firstRoutes = new AtomicReference<>(List.of());
		service.generate(FAR_START, Set.of(TARGET), Set.of(), AlternativeRoutesMode.OWNED_INVENTORY, 10,
			(routes, catalog, unavailable, isDone) ->
			{
				if (isDone)
				{
					firstRoutes.set(routes);
					firstDone.countDown();
				}
			});
		assertTrue(firstDone.await(120, TimeUnit.SECONDS));
		Set<TeleportMethod> spellMethods = new java.util.HashSet<>();
		for (RouteOption route : firstRoutes.get())
		{
			for (TeleportMethod method : route.getMethods())
			{
				if (method.getType() == TransportType.TELEPORTATION_SPELL)
				{
					spellMethods.add(method);
				}
			}
		}
		assertTrue("Precondition: a spell route was found", !spellMethods.isEmpty());

		// Re-run with those methods excluded: no spell route may appear.
		CountDownLatch secondDone = new CountDownLatch(1);
		AtomicReference<List<RouteOption>> secondRoutes = new AtomicReference<>(List.of());
		service.generate(FAR_START, Set.of(TARGET), spellMethods, AlternativeRoutesMode.OWNED_INVENTORY, 10,
			(routes, catalog, unavailable, isDone) ->
			{
				if (isDone)
				{
					secondRoutes.set(routes);
					secondDone.countDown();
				}
			});
		assertTrue(secondDone.await(120, TimeUnit.SECONDS));
		service.shutdown();

		assertTrue("Excluded spell must not be seeded back in",
			secondRoutes.get().stream().noneMatch(r -> r.getMethods().stream().anyMatch(spellMethods::contains)));
	}

	@Test
	public void inBankMethodFlaggedUnavailableOnlyOutsideBankMode() throws Exception
	{
		// Cowbell amulet only in the bank: Owned/Inventory must flag its method IN_BANK in the
		// unavailable map, while Owned/Inv+bank treats it as usable (its routes walk to a bank), so it
		// must NOT be flagged there. The catalog contains the method either way.
		int cowbellDestination = WorldPointUtil.packWorldPoint(3259, 3277, 0);
		PathfinderConfig base = new TestPathfinderConfig(client, config);
		base.setBankSnapshot(new Item[]{new Item(33104, 1)});
		PathfinderConfig planning = base.copyForPlanning();
		planning.setPlanningMode(false);
		planning.setConsiderBank(false);
		AlternativeRoutesService service = new AlternativeRoutesService(clientThread, planning);

		Map<TeleportMethod, MethodAvailability> inventoryUnavailable =
			finalUnavailable(service, AlternativeRoutesMode.OWNED_INVENTORY);
		Map<TeleportMethod, MethodAvailability> bankUnavailable =
			finalUnavailable(service, AlternativeRoutesMode.OWNED_WITH_BANK);
		service.shutdown();

		TeleportMethod cowbell = planning.getMethodCatalog().stream()
			.filter(m -> m.getType() == TransportType.TELEPORTATION_ITEM && m.getDestination() == cowbellDestination)
			.findFirst()
			.orElseThrow(() -> new AssertionError("Cowbell amulet method missing from the catalog"));
		assertTrue("Inventory mode must flag the banked item as IN_BANK",
			inventoryUnavailable.get(cowbell) == MethodAvailability.IN_BANK);
		assertTrue("Inv+bank mode must treat the banked item as usable (not flagged)",
			!bankUnavailable.containsKey(cowbell));
	}

	/**
	 * Runs one generation in the given mode and returns the final update's unavailable map.
	 */
	private Map<TeleportMethod, MethodAvailability> finalUnavailable(
		AlternativeRoutesService service, AlternativeRoutesMode mode) throws Exception
	{
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<TeleportMethod, MethodAvailability>> result = new AtomicReference<>(Map.of());
		service.generate(START, Set.of(TARGET), Set.of(), mode, 10,
			(routes, catalog, unavailable, isDone) ->
			{
				if (isDone)
				{
					result.set(unavailable);
					done.countDown();
				}
			});
		assertTrue("Generation should complete", done.await(120, TimeUnit.SECONDS));
		return result.get();
	}

	@Test
	public void bankPickupWeightChargedExactlyOnceIntoBankedRouteCost() throws Exception
	{
		// Run the same bank-pickup scenario twice: first with no bank-pickup weight, then with a
		// 60-step weight (small enough that banking still beats the ~147-tile walk, so the route isn't
		// dropped by the stop-at-walk rule). The banked route's cost must rise by exactly the weight —
		// charged once on entering the banked state, not per edge.
		when(config.costBankPickup()).thenReturn(0, 60);
		int varrockCentre = WorldPointUtil.packWorldPoint(3213, 3424, 0);
		int cowbellDestination = WorldPointUtil.packWorldPoint(3259, 3277, 0);
		doReturn(new Item[]{new Item(33104, 1)}).when(bank).getItems();
		PathfinderConfig base = new TestPathfinderConfig(client, config);
		base.bank = bank;
		PathfinderConfig planning = base.copyForPlanning();
		planning.setPlanningMode(false);
		planning.setConsiderBank(true);
		AlternativeRoutesService service = new AlternativeRoutesService(clientThread, planning);

		int baseline = bankedRouteCost(service, varrockCentre, cowbellDestination);
		int weighted = bankedRouteCost(service, varrockCentre, cowbellDestination);
		service.shutdown();

		assertTrue("Baseline banked route should cost something, got " + baseline, baseline > 0);
		assertTrue("Banked route must carry the 60-step bank-pickup weight exactly once ("
				+ baseline + " -> " + weighted + ")",
			weighted == baseline + 60);
	}

	/**
	 * Runs one Owned/Inv+bank generation and returns the via-bank route's total cost.
	 */
	private int bankedRouteCost(AlternativeRoutesService service, int start, int target) throws Exception
	{
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<List<RouteOption>> finalRoutes = new AtomicReference<>(List.of());
		service.generate(start, Set.of(target), Set.of(), AlternativeRoutesMode.OWNED_WITH_BANK, 10,
			(routes, catalog, unavailable, isDone) ->
			{
				if (isDone)
				{
					finalRoutes.set(routes);
					done.countDown();
				}
			});
		assertTrue("Generation should complete", done.await(120, TimeUnit.SECONDS));
		RouteOption bankedRoute = finalRoutes.get().stream()
			.filter(RouteOption::isViaBank)
			.findFirst().orElse(null);
		assertTrue("A route via the bank should exist", bankedRoute != null);
		return bankedRoute.getTotalCost();
	}

	@Test
	public void everythingModeSurfacesTeleportsWithoutOwnedItems() throws Exception
	{
		// No inventory at all: the Owned modes could only walk, but "Everything" bypasses possession,
		// so teleport routes must still be offered.
		when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
		PathfinderConfig planning = new TestPathfinderConfig(client, config).copyForPlanning();
		AlternativeRoutesService service = new AlternativeRoutesService(clientThread, planning);

		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<List<RouteOption>> finalRoutes = new AtomicReference<>(List.of());
		service.generate(FAR_START, Set.of(TARGET), Set.of(), AlternativeRoutesMode.ALL_EVERYTHING, 10,
			(routes, catalog, unavailable, isDone) ->
			{
				if (isDone)
				{
					finalRoutes.set(routes);
					done.countDown();
				}
			});
		assertTrue("Generation should complete", done.await(120, TimeUnit.SECONDS));
		service.shutdown();

		assertTrue("Everything mode must offer teleport routes despite owning no items",
			finalRoutes.get().stream().anyMatch(r -> !r.getMethods().isEmpty()));
	}
}
