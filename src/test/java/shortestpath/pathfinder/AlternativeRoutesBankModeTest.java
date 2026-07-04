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
 * Verifies the alternative-routes "Bank" mode replicates Shortest Path's bank-pickup behaviour on
 * the planning copy: a teleport item that exists only in the bank must produce a route that walks to
 * a bank, flips into bankVisited state, and then teleports — even when the user's own Shortest Path
 * config has teleport items disabled.
 * <p>
 * Mirrors {@code PathfinderTest.testCowbellAmuletInBankUsedAfterBankVisit} (Varrock centre → Cowbell
 * amulet destination, expected path length 36) so any divergence from the main plugin's behaviour
 * fails the parity assertion.
 */
@RunWith(MockitoJUnitRunner.class)
public class AlternativeRoutesBankModeTest
{
	private static final int VARROCK_CENTRE = WorldPointUtil.packWorldPoint(3213, 3424, 0);
	private static final int COWBELL_DESTINATION = WorldPointUtil.packWorldPoint(3259, 3277, 0);
	private static final int COWBELL_AMULET = 33104;

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
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		// The user's own Shortest Path config is deliberately restrictive: teleport items OFF.
		// The Owned modes force their own item setting, so they must work independently of it.
		when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
	}

	/**
	 * A planning copy in the "Owned" family: inventory-only, or inventory + bank.
	 */
	private PathfinderConfig planningCopy(boolean considerBank)
	{
		return planningCopy(considerBank, new TestPathfinderConfig(client, config));
	}

	private PathfinderConfig planningCopy(boolean considerBank, PathfinderConfig base)
	{
		PathfinderConfig planning = base.copyForPlanning();
		planning.setPlanningMode(false);
		planning.setBypassItemPossession(false);
		planning.setConsiderBank(considerBank);
		planning.refresh();
		return planning;
	}

	private Pathfinder run(PathfinderConfig planning)
	{
		Pathfinder pathfinder = new Pathfinder(planning, VARROCK_CENTRE, Set.of(COWBELL_DESTINATION));
		pathfinder.run();
		return pathfinder;
	}

	@Test
	public void bankModeUsesBankedTeleportViaBankVisit()
	{
		// Bank open: the live container (event-captured by onItemContainerChanged) has the amulet.
		doReturn(new Item[]{new Item(COWBELL_AMULET, 1)}).when(bank).getItems();
		PathfinderConfig base = new TestPathfinderConfig(client, config);
		base.bank = bank;

		Pathfinder pathfinder = run(planningCopy(true, base));

		assertTrue("Bank mode should reach the target", pathfinder.getResult().isReached());
		assertTrue("Route should pass through a bank (bankVisited state)",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		// Parity with PathfinderTest.testCowbellAmuletInBankUsedAfterBankVisit (main plugin behaviour).
		assertEquals("Bank-mode route should match the main plugin's bank-pickup path length",
			36, pathfinder.getPath().size());
	}

	@Test
	public void bankModeSurvivesLiveContainerEmptiedOnBankClose()
	{
		// Regression (user-reported): once the bank interface closes, the client can EMPTY the live
		// container — and with it every reference to it, including the event-captured one. The items
		// snapshot taken while the bank was open must keep bank routes working.
		doReturn(new Item[0]).when(bank).getItems(); // live container emptied on close
		PathfinderConfig base = new TestPathfinderConfig(client, config);
		base.bank = bank;
		base.setBankSnapshot(new Item[]{new Item(COWBELL_AMULET, 1)}); // captured while the bank was open

		Pathfinder pathfinder = run(planningCopy(true, base));

		assertTrue("Bank mode should reach the target using the bank snapshot", pathfinder.getResult().isReached());
		assertTrue("Route should pass through a bank (bankVisited state)",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		assertEquals("Route should match the bank-pickup path length", 36, pathfinder.getPath().size());
	}

	@Test
	public void inventoryModeDoesNotUseBankedItems()
	{
		// Same scenario but in Owned/Inventory mode: the amulet is only in the bank, so the route
		// must not use it nor route through a bank.
		Pathfinder pathfinder = run(planningCopy(false));

		assertTrue("Walking route should still reach the target", pathfinder.getResult().isReached());
		assertFalse("Inventory mode must not enter bankVisited state",
			pathfinder.getPath().stream().anyMatch(PathStep::isBankVisited));
		assertTrue("Without the banked teleport the route must be longer than the bank-mode route",
			pathfinder.getPath().size() > 36);
	}
}
