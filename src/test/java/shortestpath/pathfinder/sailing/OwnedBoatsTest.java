package shortestpath.pathfinder.sailing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.TestShortestPathConfig;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.astar.EstimateEdges;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.sailing.SeaArea;
import shortestpath.sailing.SailingService;
import shortestpath.items.ItemStateService;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/** The boats the player owns, read from their slots, as sailing transports from the moorings they are at. */
@RunWith(MockitoJUnitRunner.Silent.class)
public class OwnedBoatsTest
{
	// The game's dock ids (moorings.tsv's Dock column)
	private static final int CATHERBY = 6, PORT_ROBERTS = 21;

	@Mock
	Client client;
	private final TestShortestPathConfig config = new TestShortestPathConfig();
	private PathfinderConfig pathfinderConfig;

	@Before
	public void before()
	{
		config.set("calculationCutoff", 30);
		config.set("currencyThreshold", 10000000);
		config.set("useTeleportationItems", TeleportationItem.NONE);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		pathfinderConfig = new TestPathfinderConfig(client, config);
	}

	@Test
	public void manualStartPreservesRecoveryFees()
	{
		SailingVessel vessel = SailingVessel.of(SailingVessel.Kind.RAFT, 128);
		SailingMooring mooring = mooring("Catherby");
		SailingFleet fleet = new SailingFleet(List.of(SailingFleet.Boat.at(vessel, -1, 4125)),
			new SailingFleet.Aboard(0, 0, vessel), 1000);
		PreparedSailingCapability onFoot = new PreparedSailingCapability(fleet, List.of(mooring)).onFoot();

		assertTrue(onFoot.fleet().aboard() == null);
		assertEquals(4125, onFoot.fleet().boats().get(0).boardingCost(mooring));
		assertEquals(1000, onFoot.fleet().carriedCoins());
	}

	@Test
	public void noBoatsNoSailing()
	{
		pathfinderConfig.refresh();
		assertTrue(boardedAt(BankVisitState.CARRIED).isEmpty());
	}

	@Test
	public void aMooredBoatSailsFromItsMooringOnly()
	{
		// A skiff in slot 1, moored at Catherby, with a camphor hull (2.5 tiles a tick)
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_TYPE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_PORT)).thenReturn(CATHERBY);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_HULL)).thenReturn(4);
		pathfinderConfig.refresh();

		assertEquals(Set.of("Catherby"), new HashSet<>(boardedAt(BankVisitState.CARRIED)));
	}

	@Test
	public void eachBoatEntersOnlyTheWaterItsOwnPartsAllow()
	{
		// Two sloops: slot 1 at Port Roberts with no parts, slot 2 at Catherby with an adamant keel
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_TYPE)).thenReturn(2);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_PORT)).thenReturn(PORT_ROBERTS);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_TYPE)).thenReturn(2);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_PORT)).thenReturn(CATHERBY);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_KEEL)).thenReturn(4);
		pathfinderConfig.refresh();

		SailingFleet fleet = pathfinderConfig.prepareSailingCapability().fleet();
		assertEquals(2, fleet.boats().size());
		SailingMooring roberts = mooring("Port Roberts"), catherby = mooring("Catherby");
		for (SailingFleet.Boat boat : fleet.boats())
		{
			boolean keeled = boat.vessel().canEnter(SeaArea.CRYSTALLINE);
			assertEquals(keeled, boat.boardingCost(catherby) == 0);
			assertEquals(!keeled, boat.boardingCost(roberts) == 0);
			assertFalse(boat.vessel().canEnter(SeaArea.KELP));
		}
	}

	@Test
	public void theBoatAboardIsNotAtItsDock()
	{
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_TYPE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_PORT)).thenReturn(CATHERBY);
		when(client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED)).thenReturn(1);
		pathfinderConfig.refresh();

		assertTrue(pathfinderConfig.prepareSailingCapability().fleet().boats().isEmpty());
	}

	@Test
	public void sailOnBoatsOffLeavesTheBoatsMoored()
	{
		config.set("useSailingMoves", false);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_TYPE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_PORT)).thenReturn(CATHERBY);
		pathfinderConfig.refresh();

		assertTrue(boardedAt(BankVisitState.CARRIED).isEmpty());
	}

	@Test
	public void recoveryUsesAnInclusiveGpLimitAndOnlyShipwrightPorts()
	{
		recoverableSkiff(4125);
		config.set("boatRecoveryThreshold", 4124);
		pathfinderConfig.refresh();
		assertFalse(canBoardAt("Port Roberts"));
		assertTrue(canBoardAt("Catherby"));

		config.set("boatRecoveryThreshold", 4125);
		pathfinderConfig.refresh();
		assertTrue(canBoardAt("Port Roberts"));
		assertFalse(canBoardAt("Dognose Island"));
		assertTrue(boardedAt(BankVisitState.CARRIED).contains("Port Roberts"));

		config.set("boatRecoveryThreshold", 0);
		pathfinderConfig.refresh();
		assertFalse(canBoardAt("Port Roberts"));
	}

	@Test
	public void recoveryRequiresEnoughCoinsAndAKnownFee()
	{
		recoverableSkiff(4124);
		config.set("boatRecoveryThreshold", 4125);
		pathfinderConfig.refresh();
		assertFalse(canBoardAt("Port Roberts"));

		when(client.getDBTableField(DBTableID.SailingBoat.Row.SAILING_BOAT_SKIFF,
			DBTableID.SailingBoat.COL_RETRIEVAL_COST, 0)).thenReturn(null);
		coins(100000);
		pathfinderConfig.refresh();
		assertFalse(canBoardAt("Port Roberts"));
		assertTrue(canBoardAt("Catherby"));
	}

	@Test
	public void bankCoinsCanPayRecoveryWithBankTeleportsDisabled()
	{
		recoverableSkiff(0);
		config.set("boatRecoveryThreshold", 4125);
		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(new Item[]{new Item(ItemID.COINS, 4125)});
		ItemStateService items = ItemStateService.forTesting();
		items.onContainerChanged(InventoryID.BANK, bank);
		pathfinderConfig.setItemStateService(items);
		pathfinderConfig.refresh();
		assertTrue(canBoardAt("Port Roberts"));
		assertTrue("bank routing enabled for recovery", pathfinderConfig.prepareAStarRoutingAccount(true).bankPathEnabled());
		assertFalse(boardedAt(BankVisitState.CARRIED).contains("Port Roberts"));
		assertTrue(boardedAt(BankVisitState.BANKED).contains("Port Roberts"));

		coins(1000);
		pathfinderConfig.refresh();
		assertFalse(boardedAt(BankVisitState.CARRIED).contains("Port Roberts"));
		assertTrue(boardedAt(BankVisitState.BANKED).contains("Port Roberts"));

		coins(4125);
		pathfinderConfig.refresh();
		assertTrue(boardedAt(BankVisitState.CARRIED).contains("Port Roberts"));

		coins(Integer.MAX_VALUE);
		pathfinderConfig.refresh();
		assertTrue(boardedAt(BankVisitState.BANKED).contains("Port Roberts"));
	}

	@Test
	public void aBoatAlreadyAtTheDockIsPreferredToPayingRecovery()
	{
		recoverableSkiff(4125);
		config.set("boatRecoveryThreshold", 4125);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_TYPE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_PORT)).thenReturn(PORT_ROBERTS);
		coins(0);
		pathfinderConfig.refresh();
		// The skiff moored there is free to board, so needs no coins carried
		assertTrue(boardedAt(BankVisitState.CARRIED).contains("Port Roberts"));
	}

	@Test
	public void theEstimateTakesTheFasterOfTwoBoats()
	{
		// Skiffs at Catherby: a wooden hull with a brazier, and a rosewood hull. Neither is better for every leg
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_TYPE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_PORT)).thenReturn(CATHERBY);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_BRAZIER)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_TYPE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_PORT)).thenReturn(CATHERBY);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_HULL)).thenReturn(6);
		pathfinderConfig.refresh();

		// Each boat's bound counts: the rosewood hull's is the lower where both go, the brazier's reaches the icy seas
		Map<Long, Integer> both = bounds(BankVisitState.CARRIED);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_2_OWNED)).thenReturn(0);
		pathfinderConfig.refresh();
		Map<Long, Integer> wooden = bounds(BankVisitState.CARRIED);
		assertTrue(both.keySet().containsAll(wooden.keySet()));
		assertTrue(wooden.entrySet().stream().allMatch(edge -> both.get(edge.getKey()) <= edge.getValue()));
		assertTrue(wooden.entrySet().stream().anyMatch(edge -> both.get(edge.getKey()) < edge.getValue()));
	}

	@Test
	public void identicalBoatsAreBoardedWhereCheapest()
	{
		SailingVessel skiff = SailingVessel.of(SailingVessel.Kind.SKIFF, 256);
		SailingMooring catherby = mooring("Catherby");
		SailingFleet fleet = new SailingFleet(List.of(SailingFleet.Boat.at(skiff, -1, 500),
			SailingFleet.Boat.at(skiff, catherby.dock(), 500), SailingFleet.Boat.at(skiff, -1, 100)), null, 0);
		pathfinderConfig.refresh();
		// No coins carried: the skiff moored at Catherby is boarded there for free
		EstimateEdges edges = SailingEstimates.edges(fleet, SailingMoorings.all(), SailingWaters.of(pathfinderConfig.getMap()),
			SeaGraph.all(pathfinderConfig.getMap()));
		boolean fromCatherby = false;
		for (int i = 0; i < edges.count(BankVisitState.CARRIED); i++)
			fromCatherby |= edges.origin(BankVisitState.CARRIED, i) == catherby.landTile();
		assertTrue(fromCatherby);
	}

	@Test
	public void aRecoveryCoveredByCarriedCoinsNeedsNoBankRouting()
	{
		recoverableSkiff(5000);
		config.set("boatRecoveryThreshold", 4125);
		pathfinderConfig.refresh();
		assertTrue(canBoardAt("Port Roberts"));
		assertFalse(pathfinderConfig.prepareAStarRoutingAccount(true).bankPathEnabled());
	}

	@Test
	public void bankedCoinsPayOnlyForARecovery()
	{
		// With the bank path on and banked items not used for teleports, a transport that costs coins still needs them
		// carried: only a boat's recovery may send the route to the bank for them
		config.set("includeBankPath", true);
		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(new Item[]{new Item(ItemID.COINS, 1000000)});
		ItemStateService items = ItemStateService.forTesting();
		items.onContainerChanged(InventoryID.BANK, bank);
		pathfinderConfig.setItemStateService(items);
		pathfinderConfig.refresh();
		Transport paid = new Transport.TransportBuilder().origin(1).destination(2).type(TransportType.CHARTER_SHIP)
			.duration(1).build().withItemRequirements(new shortestpath.requirement.model.TransportItems(List.of(
				new shortestpath.requirement.model.ItemRequirement(new int[]{ItemID.COINS}, null, null, 100))));
		assertFalse(pathfinderConfig.getEligibility().usable(paid, BankVisitState.BANKED));
	}

	@Test
	public void noBoatToRecoverNoBankRouting()
	{
		pathfinderConfig.refresh();
		assertFalse(pathfinderConfig.prepareAStarRoutingAccount(true).bankPathEnabled());
	}

	private boolean canBoardAt(String name)
	{
		PreparedSailingCapability capability = pathfinderConfig.prepareSailingCapability();
		return capability.fleet().boats().stream().anyMatch(boat -> capability.canBoard(mooring(name), boat));
	}

	private void recoverableSkiff(int coins)
	{
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_TYPE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_PORT)).thenReturn(CATHERBY);
		when(client.getDBTableField(DBTableID.SailingBoat.Row.SAILING_BOAT_SKIFF,
			DBTableID.SailingBoat.COL_RETRIEVAL_COST, 0)).thenReturn(new Object[]{3750});
		when(client.getDBTableField(DBTableID.SailingBoat.Row.SAILING_BOAT_SKIFF,
			DBTableID.SailingBoat.COL_HULL_OPTION, 0)).thenReturn(new Object[]{100});
		when(client.getDBTableField(100, DBTableID.SailingBoatHull.COL_FACILITY_STATS, 0))
			.thenReturn(new Object[]{101});
		when(client.getDBTableField(101, DBTableID.SailingBoatFacilityStats.COL_BOAT_ADDITIONAL_RECOVERY_COST_PERCENTAGE, 0))
			.thenReturn(new Object[]{10});
		pathfinderConfig.setSailingService(SailingService.forTesting(client));
		coins(coins);
	}

	private void coins(int amount)
	{
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.COINS, amount)});
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
	}

	// The moorings the estimate has the account boarding a boat at, in bank layer banked
	private List<String> boardedAt(BankVisitState banked)
	{
		EstimateEdges edges = pathfinderConfig.prepareAStarRoutingAccount(true).estimateEdges();
		List<String> names = new ArrayList<>();
		for (int i = 0; i < edges.count(banked); i++) names.add(SailingMoorings.atLand(edges.origin(banked, i)).name());
		return names;
	}

	// The estimate's bounds in bank layer banked, by origin and destination
	private Map<Long, Integer> bounds(BankVisitState banked)
	{
		EstimateEdges edges = pathfinderConfig.prepareAStarRoutingAccount(true).estimateEdges();
		Map<Long, Integer> found = new HashMap<>();
		for (int i = 0; i < edges.count(banked); i++)
			found.put(((long) edges.origin(banked, i) << 32) | (edges.destination(banked, i) & 0xFFFFFFFFL), edges.cost(banked, i));
		return found;
	}

	private static SailingMooring mooring(String name)
	{
		return SailingMoorings.all().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
	}
}
