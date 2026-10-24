package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.astar.RoutingStatic;
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
		List<UnlockSuggestions.Suggestion> suggestions = search.getUnlockSuggestions();
		UnlockSuggestions.Suggestion first = suggestions.get(0);
		assertEquals(Map.of(ItemID.COINS, 20), first.getCapability().getItems());
		assertEquals(TransportType.SHIP, first.getTransport().getType());
		assertEquals("Aldarin", first.getTransport().getDisplayInfo());
		assertTrue("Twilight's Promise is suggested after the coins", suggestions.stream().skip(1)
			.anyMatch(suggestion -> suggestion.getCapability().getQuests().equals(Set.of(Quest.TWILIGHTS_PROMISE))));

		// And following the first suggestion does get there.
		setup(99, new Item(ItemID.COINS, 20));
		assertTrue(search(SUNSET_COAST, ALDARIN).getResult().isReached());
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
	public void theUnreachableResultIsPublishedBeforeTheSuggestions()
	{
		boolean[] doneWithoutSuggestions = new boolean[1];
		AStarPathfinder search = aldarinSearch(done -> doneWithoutSuggestions[0] = done.isDone()
			&& !done.getResult().isReached() && done.getUnlockSuggestions().isEmpty());

		search.run();

		assertTrue("unreachable is out before the suggestions", doneWithoutSuggestions[0]);
		assertFalse(search.getUnlockSuggestions().isEmpty());
	}

	@Test
	public void aCancelledSearchDropsItsSuggestions()
	{
		// Cancelled as soon as its result is out, as when a new search replaces it: it doesn't look.
		AStarPathfinder replaced = aldarinSearch(AStarPathfinder::cancel);
		replaced.run();
		assertTrue(replaced.getUnlockSuggestions().isEmpty());
		assertEquals(0, replaced.getUnlockSearchNanos());

		// Cancelled after they're found: they're dropped.
		AStarPathfinder later = aldarinSearch(done -> assertTrue(done.isDone()));
		later.run();
		assertFalse(later.getUnlockSuggestions().isEmpty());
		later.cancel();
		assertTrue(later.getUnlockSuggestions().isEmpty());
	}

	@Test
	public void theBoatToAldarinIsNotSuggestedForATileItDoesNotLeadTo()
	{
		// Just off Aldarin, closer to where Antonia's boat lands than anything else locked, but the
		// boat doesn't get there. (For Aldarin itself it does; see above.)
		config.disableTransportToggles();
		config.set("useShips", true);
		config.set("useQuetzals", true);
		setup(99);
		int target = WorldPointUtil.packWorldPoint(1455, 2934, 0);

		AStarPathfinder search = search(SUNSET_COAST, target);

		assertFalse(search.getResult().isReached());
		for (UnlockSuggestions.Suggestion suggestion : search.getUnlockSuggestions())
		{
			assertNotEquals(TransportType.SHIP, suggestion.getTransport().getType());
		}
		setup(99, new Item(ItemID.COINS, 20));
		assertFalse("the fare doesn't help", search(SUNSET_COAST, target).getResult().isReached());
	}

	@Test
	public void anUnlockIsOnlySuggestedOnceTheSearchRerunReachesTheTarget()
	{
		// By Tai Bwo Wannai on a level 1 account, 79 Agility opens shortcuts that look like a way in
		// on the component graph, but the search rerun with them doesn't get there. Carrying an axe
		// and a machete to cut through the jungle does.
		int target = WorldPointUtil.packWorldPoint(2795, 2940, 0);
		setup(1);

		AStarPathfinder search = search(LUMBRIDGE, target);

		assertFalse(search.getResult().isReached());
		List<UnlockSuggestions.Suggestion> suggestions = search.getUnlockSuggestions();
		assertEquals(Map.of(ItemID.BRONZE_AXE, 1, ItemID.MACHETTE, 1), suggestions.get(0).getCapability().getItems());
		int[] agility = new int[SkillLevels.LENGTH];
		agility[Skill.AGILITY.ordinal()] = 79;
		for (UnlockSuggestions.Suggestion suggestion : suggestions)
		{
			assertNotEquals(SkillLevels.of(agility), suggestion.getCapability().getSkillLevels());
		}
		when(client.getBoostedSkillLevel(Skill.AGILITY)).thenReturn(79);
		pathfinderConfig.refresh();
		assertFalse("79 Agility doesn't help", search(LUMBRIDGE, target).getResult().isReached());
	}

	@Test
	public void suggestionsStayWithinOneTickOnLongRoutes()
	{
		// A level 1 account has the most locked transports to weigh: well over a hundred capabilities.
		setup(1);
		for (int target : new int[] {ALDARIN, WorldPointUtil.packWorldPoint(2932, 2936, 0),
			WorldPointUtil.packWorldPoint(2795, 2940, 0), WorldPointUtil.packWorldPoint(1388, 9313, 0)})
		{
			AStarPathfinder search = search(LUMBRIDGE, target);

			assertFalse(search.getResult().isReached());
			assertFalse(search.getUnlockSuggestions().isEmpty());
			assertTrue(search.debugStats().toString(), search.getUnlockSearchNanos() < UnlockSuggestions.BUDGET_NANOS);
		}
	}
}
