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
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.items.ItemStateService;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.presentation.PathAnnotator;
import shortestpath.requirement.Capability;
import shortestpath.requirement.model.SkillLevels;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.TransportType;

/** What an A* search that can't reach its target suggests unlocking, on the real world data. */
@RunWith(MockitoJUnitRunner.class)
public class UnlockSuggestionsTest
{
	private static final int LUMBRIDGE = WorldPointUtil.packWorldPoint(3222, 3218, 0);
	private static final int SUNSET_COAST = WorldPointUtil.packWorldPoint(1495, 2985, 0);
	private static final int ALDARIN = WorldPointUtil.packWorldPoint(1432, 2960, 0);

	private static RoutingStatic routingStatic;

	@Mock
	Client client;
	@Mock
	ItemContainer inventory;
	private final TestShortestPathConfig config = new TestShortestPathConfig();
	private PathfinderConfig pathfinderConfig;

	@BeforeClass
	public static void buildRoutingStatic()
	{
		routingStatic = TestWorldData.routingStatic();
	}

	@Before
	public void before()
	{
		config.set("calculationCutoff", 30);
		config.set("currencyThreshold", 10000000);
		config.set("useTeleportationItems", TeleportationItem.NONE);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
		// What is found shouldn't depend on how busy the machine is; the budget test sets it back.
		UnlockSuggestions.timeScale = 10;
	}

	@After
	public void after()
	{
		UnlockSuggestions.timeScale = 1;
	}

	/** An account with no quests done, every skill at {@code level}, carrying {@code items}. */
	private void setup(int level, Item... items)
	{
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(level);
		doReturn(items).when(inventory).getItems();
		pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.NOT_STARTED, true, true);
		pathfinderConfig.refresh();
	}

	private AStarPathfinder search(int start, int target)
	{
		AStarPathfinder search = new AStarPathfinder(pathfinderConfig, routingStatic, start, Set.of(target), null);
		search.run();
		search.findUnlockSuggestions();
		return search;
	}

	@Test
	public void carryingCoinsForTheBoatToAldarinComesBeforeTwilightsPromise()
	{
		// On foot Aldarin is an island: from the Sunset Coast, Antonia's boat takes 20 coins and
		// the quetzal needs Twilight's Promise.
		config.disableTransportToggles();
		config.set("useShips", true);
		config.set("useQuetzals", true);
		setup(99);

		AStarPathfinder search = search(SUNSET_COAST, ALDARIN);

		assertFalse(search.getResult().isReached());
		UnlockSuggestions.Suggestion first = search.getUnlockSuggestions().get(0);
		assertEquals(Capability.items(Map.of(ItemID.COINS, 20)), first.getCapability());
		assertEquals(1, first.getTransports().size());
		assertEquals(TransportType.SHIP, first.getTransports().get(0).getType());
		assertEquals("Aldarin", first.getTransports().get(0).getDisplayInfo());

		// And following the suggestion does get there.
		setup(99, new Item(ItemID.COINS, 20));
		assertTrue(search(SUNSET_COAST, ALDARIN).getResult().isReached());
	}

	@Test
	public void coinsInTheBankReadAsAReminderToBringThem()
	{
		// An ironman with the fare in the bank but not on them, and routes through a bank turned off.
		ItemContainer bank = mock(ItemContainer.class);
		doReturn(new Item[]{new Item(ItemID.COINS, 1000)}).when(bank).getItems();
		ItemStateService itemState = ItemStateService.forTesting();
		itemState.onContainerChanged(InventoryID.BANK, bank);
		config.disableTransportToggles();
		config.set("useShips", true);
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		doReturn(new Item[0]).when(inventory).getItems();
		pathfinderConfig = new TestPathfinderConfig(client, config, QuestState.NOT_STARTED, true, true);
		pathfinderConfig.setItemStateService(itemState);
		pathfinderConfig.refresh();

		AStarPathfinder search = search(SUNSET_COAST, ALDARIN);

		UnlockSuggestions.Suggestion first = search.getUnlockSuggestions().get(0);
		assertEquals(Capability.Effort.OWNED_ITEMS, first.getEffort());
		ItemComposition coins = mock(ItemComposition.class);
		when(coins.getName()).thenReturn("Coins");
		when(client.getItemDefinition(ItemID.COINS)).thenReturn(coins);
		assertEquals("Bring 20 Coins from your bank to use: Ship Aldarin",
			PathAnnotator.forTesting(client, null, null).getUnlockSuggestionLines(search).get(0));
	}

	/** The Sunset Coast to Aldarin search with ships and quetzals only, running {@code completed} once it's done. */
	private AStarPathfinder aldarinSearch(Consumer<AStarPathfinder> completed)
	{
		config.disableTransportToggles();
		config.set("useShips", true);
		config.set("useQuetzals", true);
		setup(99);
		AStarPathfinder[] search = new AStarPathfinder[1];
		search[0] = new AStarPathfinder(pathfinderConfig, routingStatic, SUNSET_COAST, Set.of(ALDARIN),
			() -> completed.accept(search[0]));
		return search[0];
	}

	@Test
	public void theSearchPublishesItsResultWithoutLookingForUnlocks()
	{
		// So the unreachable text shows at once, and the search's own time is only the search.
		boolean[] doneWithoutSuggestions = new boolean[1];
		AStarPathfinder search = aldarinSearch(done -> doneWithoutSuggestions[0] = done.isDone()
			&& !done.getResult().isReached() && done.getUnlockSuggestions().isEmpty());

		search.run();

		assertTrue("unreachable is out before the suggestions", doneWithoutSuggestions[0]);
		assertEquals("run() didn't look", 0, search.getUnlockSearchNanos());
		search.findUnlockSuggestions();
		assertFalse(search.getUnlockSuggestions().isEmpty());
	}

	@Test
	public void aCancelledSearchDropsItsSuggestions()
	{
		// Cancelled as soon as its result is out, as when a new search replaces it: it doesn't look.
		AStarPathfinder replaced = aldarinSearch(AStarPathfinder::cancel);
		replaced.run();
		replaced.findUnlockSuggestions();
		assertTrue(replaced.getUnlockSuggestions().isEmpty());
		assertEquals(0, replaced.getUnlockSearchNanos());

		// Cancelled after they're found: they're dropped.
		AStarPathfinder later = aldarinSearch(done -> assertTrue(done.isDone()));
		later.run();
		later.findUnlockSuggestions();
		assertFalse(later.getUnlockSuggestions().isEmpty());
		later.cancel();
		assertTrue(later.getUnlockSuggestions().isEmpty());
	}

	@Test
	public void withoutTheBoatTheQuetzalNeedsTwilightsPromise()
	{
		config.disableTransportToggles();
		config.set("useQuetzals", true);
		setup(99);

		UnlockSuggestions.Suggestion suggestion = search(SUNSET_COAST, ALDARIN).getUnlockSuggestions().get(0);

		assertEquals(Capability.quests(Set.of(Quest.TWILIGHTS_PROMISE)), suggestion.getCapability());
		assertEquals(Capability.Effort.PROGRESS, suggestion.getEffort());
		assertTrue(suggestion.getTransports().stream().allMatch(transport -> transport.getType() == TransportType.QUETZAL));
	}

	@Test
	public void aTargetOnlyASettingClosesSuggestsNothingAtOnce()
	{
		// With neither the boat nor the quetzal on, no unlock gets the player onto Aldarin.
		config.disableTransportToggles();
		setup(99);

		AStarPathfinder search = search(SUNSET_COAST, ALDARIN);

		assertFalse(search.getResult().isReached());
		assertEquals(UnlockSuggestions.Outcome.CLOSED, search.getUnlockSearch().getOutcome());
		assertTrue(search.getUnlockSuggestions().isEmpty());
		assertEquals(0, search.getUnlockSearch().getRerunNanos());
	}

	@Test
	public void unlocksAlongOnePathAreSuggestedTogether()
	{
		// Just off Aldarin: Antonia's boat lands on the island, and the way on from there is behind
		// Death on the Isle. Neither the fare nor the quest gets there alone; both together do.
		config.disableTransportToggles();
		config.set("useShips", true);
		config.set("useQuetzals", true);
		setup(99);
		int target = WorldPointUtil.packWorldPoint(1455, 2934, 0);

		AStarPathfinder search = search(SUNSET_COAST, target);

		assertFalse(search.getResult().isReached());
		UnlockSuggestions.Suggestion suggestion = search.getUnlockSuggestions().get(0);
		assertEquals(Capability.of(Set.of(Quest.DEATH_ON_THE_ISLE), SkillLevels.NONE, Map.of(ItemID.COINS, 20)),
			suggestion.getCapability());
		assertEquals(TransportType.SHIP, suggestion.getTransports().get(0).getType());
		assertTrue(suggestion.getTransports().size() > 1);
		setup(99, new Item(ItemID.COINS, 20));
		assertFalse("the fare alone doesn't help", search(SUNSET_COAST, target).getResult().isReached());
	}

	@Test
	public void itemsComeBeforeAMajorSkillGoal()
	{
		// By Tai Bwo Wannai on a level 1 account, carrying an axe and a machete to cut through the
		// jungle gets there, and is far less than the 79 Agility of the vine shortcut, which also does.
		int target = WorldPointUtil.packWorldPoint(2795, 2940, 0);
		setup(1);

		AStarPathfinder search = search(LUMBRIDGE, target);

		assertFalse(search.getResult().isReached());
		List<UnlockSuggestions.Suggestion> suggestions = search.getUnlockSuggestions();
		assertFalse(search.debugStats().toString(), suggestions.isEmpty());
		assertEquals(Map.of(ItemID.BRONZE_AXE, 1, ItemID.MACHETTE, 1), suggestions.get(0).getCapability().getItems());
		int[] agility = new int[SkillLevels.LENGTH];
		agility[Skill.AGILITY.ordinal()] = 79;
		for (UnlockSuggestions.Suggestion suggestion : suggestions)
		{
			assertNotEquals(SkillLevels.of(agility), suggestion.getCapability().getSkillLevels());
		}
		when(client.getBoostedSkillLevel(Skill.AGILITY)).thenReturn(79);
		pathfinderConfig.refresh();
		assertTrue("79 Agility also gets there", search(LUMBRIDGE, target).getResult().isReached());
	}

	@Test
	public void suggestionsStayWithinOneTickOnLongRoutes()
	{
		// A level 1 account has the most locked transports to weigh.
		UnlockSuggestions.timeScale = 1;
		setup(1);
		for (int target : new int[] {ALDARIN, WorldPointUtil.packWorldPoint(2932, 2936, 0),
			WorldPointUtil.packWorldPoint(2795, 2940, 0), WorldPointUtil.packWorldPoint(1388, 9313, 0)})
		{
			AStarPathfinder search = search(LUMBRIDGE, target);

			assertFalse(search.getResult().isReached());
			assertFalse(search.getUnlockSuggestions().isEmpty());
			// It stops itself at the budget.
			assertTrue(search.debugStats().toString(), search.getUnlockSearchNanos() < UnlockSuggestions.BUDGET_NANOS);
		}
	}
}
