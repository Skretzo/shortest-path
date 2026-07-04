package shortestpath.pathfinder;

import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;

/**
 * Covers the bank pick-up cost ("Bank pick-up threshold"): a one-off surcharge charged when the
 * route first enters the banked state, so routing through a bank to withdraw a teleport item only
 * wins when it saves more than that many tiles overall.
 * <p>
 * Scenario mirrors {@code PathfinderTest.testCowbellAmuletInBankUsedAfterBankVisit}: Varrock centre
 * to the Cowbell amulet destination with the amulet only in the bank — banking + teleporting takes
 * 36 steps, plain walking considerably more.
 */
@RunWith(MockitoJUnitRunner.class)
public class BankPickupCostTest
{
	private static final int VARROCK_CENTRE = WorldPointUtil.packWorldPoint(3213, 3424, 0);
	private static final int COWBELL_DESTINATION = WorldPointUtil.packWorldPoint(3259, 3277, 0);
	private static final int COWBELL_AMULET = 33104;
	private static final int BANKED_ROUTE_LENGTH = 36;

	@Mock
	Client client;
	@Mock
	ItemContainer bank;
	@Mock
	ShortestPathConfig config;

	@Before
	public void before()
	{
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10000000);
		when(config.includeBankPath()).thenReturn(true);
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		doReturn(new Item[]{new Item(COWBELL_AMULET, 1)}).when(bank).getItems();
	}

	private Pathfinder run(int bankPickupCost)
	{
		when(config.costBankPickup()).thenReturn(bankPickupCost);
		PathfinderConfig pathfinderConfig = new TestPathfinderConfig(client, config);
		pathfinderConfig.bank = bank;
		pathfinderConfig.refresh();
		Pathfinder pathfinder = new Pathfinder(pathfinderConfig, VARROCK_CENTRE, Set.of(COWBELL_DESTINATION));
		pathfinder.run();
		return pathfinder;
	}

	@Test
	public void bankRouteUsedWhenPickupCostIsFree()
	{
		Pathfinder pathfinder = run(0);
		assertTrue("Route should pass through a bank (bankVisited state)",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		assertEquals("Route should match the known bank-pickup path length",
			BANKED_ROUTE_LENGTH, pathfinder.getPath().size());
	}

	@Test
	public void smallPickupCostKeepsWorthwhileBankRoute()
	{
		// Banking saves far more than 20 tiles here, so a small surcharge must not change the route.
		Pathfinder pathfinder = run(20);
		assertTrue("Banking still wins when it saves more than the surcharge",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		assertEquals("The surcharge must not distort a still-worthwhile route",
			BANKED_ROUTE_LENGTH, pathfinder.getPath().size());
	}

	@Test
	public void pickupCostExceedingSavingsAvoidsBanking()
	{
		// The bankVisited flag flips whenever the walk merely crosses a bank tile, so the observable
		// for "did not bank for the item" is the absence of the teleport jump, not the flag.
		Pathfinder pathfinder = run(10000);
		assertTrue("The target must still be reached by walking", pathfinder.getResult().isReached());
		assertFalse("A surcharge larger than the saving must not use the banked teleport",
			hasTeleportJump(pathfinder));
		assertTrue("The walking route is longer than the banked route",
			pathfinder.getPath().size() > BANKED_ROUTE_LENGTH);
	}

	@Test
	public void inventoryTeleportDoesNotPayTheSurcharge()
	{
		// With the amulet in the inventory the teleport works without banking, so even a huge
		// surcharge must not deter it — only bank-gated transports pay.
		ItemContainer inventory = org.mockito.Mockito.mock(ItemContainer.class);
		doReturn(new Item[]{new Item(COWBELL_AMULET, 1)}).when(inventory).getItems();
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(inventory);

		Pathfinder pathfinder = run(10000);
		assertTrue("The inventory teleport should still be used", hasTeleportJump(pathfinder));
		assertTrue("Teleporting from the start is far shorter than walking to a bank first",
			pathfinder.getPath().size() < BANKED_ROUTE_LENGTH);
	}

	/**
	 * Whether any consecutive path steps are further apart than walking allows — with every transport
	 * type disabled except teleportation items, only the cowbell teleport can produce such a jump.
	 */
	private static boolean hasTeleportJump(Pathfinder pathfinder)
	{
		java.util.List<PathStep> path = pathfinder.getPath();
		for (int i = 1; i < path.size(); i++)
		{
			if (WorldPointUtil.distanceBetween(
				path.get(i - 1).getPackedPosition(), path.get(i).getPackedPosition()) > 2)
			{
				return true;
			}
		}
		return false;
	}
}
