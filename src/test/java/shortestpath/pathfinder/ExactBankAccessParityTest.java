package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.ShortestPathConfig;
import shortestpath.settings.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.exact.ExactRoutingSession;
import shortestpath.pathfinder.exact.PreparedRoutingAccount;
import shortestpath.pathfinder.exact.RoutingStatic;

/**
 * Exact must bank only where {@link PathfinderConfig#bankAccessible(int)} allows, like legacy.
 *
 * <p>The Farming Guild bank needs 85 Farming (bank.tsv); nothing else in the data is gated at 85
 * Farming, so 84 and 85 differ only in whether that bank is usable. The only teleport is a banked
 * camulet, so a route that banks at the guild is cheaper than one that walks to another bank.
 */
public class ExactBankAccessParityTest
{
	private static final int FARMING_GUILD_BANK = WorldPointUtil.packWorldPoint(1248, 3758, 0);
	private static final int START = WorldPointUtil.packWorldPoint(1249, 3752, 0);
	private static final int ENAKHRAS_TEMPLE = WorldPointUtil.packWorldPoint(3105, 9315, 0);
	private static final int BANK_VISIT_COST = 5;

	private static RoutingStatic routingStatic;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = new ExactRoutingStaticProvider(
			() -> new CollisionMap(SplitFlagMap.fromResources())).get();
	}

	@Test
	public void preparedAccountsShareStaticBanksButDifferInAccess()
	{
		PreparedRoutingAccount without = config(84).prepareExactRoutingAccount(true);
		PreparedRoutingAccount with = config(85).prepareExactRoutingAccount(true);

		assertTrue("the static data knows the bank", isStaticBank(FARMING_GUILD_BANK));
		assertFalse(without.bankAccessible(FARMING_GUILD_BANK));
		assertTrue(with.bankAccessible(FARMING_GUILD_BANK));
		assertNotEquals("bank access is part of the account identity", without.fingerprint(), with.fingerprint());
	}

	@Test
	public void inaccessibleBankIsNotUsedAndMatchesLegacy()
	{
		PathfinderConfig config = config(84);
		assertFalse(config.bankAccessible(FARMING_GUILD_BANK));

		PathfinderResult legacy = legacy(config);
		ExactPathfinder exact = exact(config, null);

		assertTrue(legacy.isReached());
		assertTrue(exact.getResult().isReached());
		assertFalse("exact banked at the Farming Guild without 85 Farming", banksAt(exact.getPath(), FARMING_GUILD_BANK));
		assertEquals(legacy.getPathCost(), exact.getResult().getPathCost());
	}

	@Test
	public void accessibleBankIsUsedAndMatchesLegacy()
	{
		PathfinderConfig config = config(85);
		assertTrue(config.bankAccessible(FARMING_GUILD_BANK));

		PathfinderResult legacy = legacy(config);
		ExactPathfinder exact = exact(config, null);

		assertTrue(legacy.isReached());
		assertTrue(exact.getResult().isReached());
		assertTrue("exact should bank at the Farming Guild", banksAt(exact.getPath(), FARMING_GUILD_BANK));
		assertEquals(legacy.getPathCost(), exact.getResult().getPathCost());
		assertTrue("banking at the guild should be cheaper than walking to another bank",
			exact.getResult().getPathCost() < exact(config(84), null).getResult().getPathCost());
	}

	@Test
	public void bankVisitCostIsChargedLikeLegacy()
	{
		for (int farmingLevel : new int[] {84, 85})
		{
			PathfinderConfig free = config(farmingLevel, 0);
			PathfinderConfig costed = config(farmingLevel, BANK_VISIT_COST);
			PathfinderResult legacy = legacy(costed);
			ExactPathfinder exact = exact(costed, null);

			assertTrue(exact.getResult().isReached());
			assertEquals("Farming " + farmingLevel, legacy.getPathCost(), exact.getResult().getPathCost());
			assertTrue(exact.getPath().stream().anyMatch(PathStep::isBankVisited));
			assertEquals("the route still banks, so it pays the visit once",
				exact(free, null).getResult().getPathCost() + BANK_VISIT_COST, exact.getResult().getPathCost());
		}
	}

	@Test
	public void sessionDoesNotReuseStaleBankAccess()
	{
		ExactRoutingSession session = new ExactRoutingSession();
		int withCost = exact(config(85), session).getResult().getPathCost();

		ExactPathfinder without = exact(config(84), session);
		assertFalse("the account graph must be rebuilt when bank access changes", without.isGraphReused());
		assertFalse(banksAt(without.getPath(), FARMING_GUILD_BANK));
		assertEquals(legacy(config(84)).getPathCost(), without.getResult().getPathCost());

		ExactPathfinder with = exact(config(85), session);
		assertFalse(with.isGraphReused());
		assertEquals(withCost, with.getResult().getPathCost());
		assertTrue(exact(config(85), session).isGraphReused());

		ExactPathfinder costed = exact(config(85, BANK_VISIT_COST), session);
		assertFalse("the account graph must be rebuilt when the bank visit cost changes", costed.isGraphReused());
		assertEquals(withCost + BANK_VISIT_COST, costed.getResult().getPathCost());
	}

	private static boolean isStaticBank(int tile)
	{
		for (int i = 0; i < routingStatic.reachableBankCount(); i++)
			if (routingStatic.reachableBankTile(i) == tile) return true;
		return false;
	}

	private static boolean banksAt(List<PathStep> path, int tile)
	{
		for (int i = 1; i < path.size(); i++)
		{
			if (!path.get(i - 1).isBankVisited() && path.get(i).isBankVisited())
				return path.get(i - 1).getPackedPosition() == tile || path.get(i).getPackedPosition() == tile;
		}
		return false;
	}

	private static PathfinderResult legacy(PathfinderConfig config)
	{
		Pathfinder pathfinder = new Pathfinder(config, START, Set.of(ENAKHRAS_TEMPLE));
		pathfinder.run();
		return pathfinder.getResult();
	}

	private static ExactPathfinder exact(PathfinderConfig config, ExactRoutingSession session)
	{
		ExactPathfinder pathfinder = new ExactPathfinder(config, routingStatic, session, START,
			Set.of(ENAKHRAS_TEMPLE), null, 1);
		pathfinder.run();
		return pathfinder;
	}

	private static PathfinderConfig config(int farmingLevel)
	{
		return config(farmingLevel, 0);
	}

	/** A logged-in account with 99 in every skill except Farming and a camulet in the bank. */
	private static PathfinderConfig config(int farmingLevel, int bankVisitCost)
	{
		Client client = mock(Client.class);
		ShortestPathConfig settings = mock(ShortestPathConfig.class);
		ItemContainer empty = mock(ItemContainer.class);
		ItemContainer bank = mock(ItemContainer.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getBoostedSkillLevel(Skill.FARMING)).thenReturn(farmingLevel);
		doReturn(new Item[0]).when(empty).getItems();
		doReturn(empty).when(client).getItemContainer(InventoryID.INV);
		doReturn(empty).when(client).getItemContainer(InventoryID.WORN);
		doReturn(new Item[] {new Item(ItemID.CAMULET, 1)}).when(bank).getItems();
		when(settings.calculationCutoff()).thenReturn(500);
		when(settings.currencyThreshold()).thenReturn(10000000);
		when(settings.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY_AND_BANK);
		when(settings.costBankVisit()).thenReturn(bankVisitCost);

		PathfinderConfig config = new TestPathfinderConfig(client, settings, QuestState.FINISHED, true, true);
		config.bank = bank;
		config.refresh();
		return config;
	}
}
