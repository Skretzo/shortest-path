package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;

/** Which (tile, bank layer) states finish a route, from the targets' items and the map. */
public class GoalStatesTest
{
	private static final int START = WorldPointUtil.packWorldPoint(3222, 3218, 0);
	private static final int WALKABLE = WorldPointUtil.packWorldPoint(3240, 3225, 0);
	private static final int OTHER = WorldPointUtil.packWorldPoint(3260, 3230, 0);
	// A blocked tile: a route to it ends on a walkable fallback tile beside it.
	private static final int BLOCKED = WorldPointUtil.packWorldPoint(3255, 3225, 0);

	private static final HeldItems SPADE_CARRIED = new HeldItems(Map.of(ItemID.SPADE, 1), Map.of(ItemID.SPADE, 1), true);
	private static final HeldItems SPADE_BANKED = new HeldItems(Map.of(), Map.of(ItemID.SPADE, 1), true);
	private static final HeldItems NOTHING = new HeldItems(Map.of(), Map.of(), true);

	private static PathfinderConfig config;

	@BeforeClass
	public static void config()
	{
		Client client = mock(Client.class);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		config = new TestPathfinderConfig(client, new TestShortestPathConfig());
		config.refresh();
	}

	private static Target spadeAt(int point)
	{
		return Target.requiring(point, TargetsTest.items(ItemID.SPADE));
	}

	private static TargetGoals tiles(Set<Integer> points)
	{
		return TargetGoals.resolve(points, config::isTransportDestination, TargetGoals.collisionRule(config, START));
	}

	private static boolean needsBank(HeldItems held, Target... targets)
	{
		return GoalStates.needsBank(Targets.of(List.of(targets)), held);
	}

	private static GoalStates goals(HeldItems held, Target... targets)
	{
		Targets all = Targets.of(List.of(targets));
		return GoalStates.resolve(tiles(all.points()), all, held);
	}

	@Test
	public void targetsWithoutItemsFinishInEitherLayer()
	{
		GoalStates goals = goals(NOTHING, Target.at(WALKABLE));
		assertTrue(goals.accepts(WALKABLE, BankVisitState.CARRIED));
		assertTrue(goals.accepts(WALKABLE, BankVisitState.BANKED));
		assertEquals(3, goals.acceptedLayers(WALKABLE));
		assertFalse(needsBank(NOTHING, Target.at(WALKABLE)));
		assertEquals(TargetStatus.CARRIED, goals.arrival(WALKABLE, BankVisitState.CARRIED).getStatus());
	}

	@Test
	public void carriedItemsFinishInEitherLayer()
	{
		GoalStates goals = goals(SPADE_CARRIED, spadeAt(WALKABLE));
		assertEquals(3, goals.acceptedLayers(WALKABLE));
		assertTrue(goals.accepts(WALKABLE, BankVisitState.CARRIED));
		assertFalse(needsBank(SPADE_CARRIED, spadeAt(WALKABLE)));
	}

	@Test
	public void bankedItemsOnlyFinishAfterABankVisit()
	{
		GoalStates goals = goals(SPADE_BANKED, spadeAt(WALKABLE));
		assertEquals(2, goals.acceptedLayers(WALKABLE));
		assertFalse(goals.accepts(WALKABLE, BankVisitState.CARRIED));
		assertTrue(goals.accepts(WALKABLE, BankVisitState.BANKED));
		assertTrue(needsBank(SPADE_BANKED, spadeAt(WALKABLE)));
		assertNull("an unbanked path does not arrive", goals.arrival(WALKABLE, BankVisitState.CARRIED));
		assertEquals(TargetStatus.BANK, goals.arrival(WALKABLE, BankVisitState.BANKED).getStatus());
	}

	@Test
	public void unknownTargetIsDroppedWhileAnotherCanBeCompleted()
	{
		GoalStates goals = goals(NOTHING, spadeAt(WALKABLE), Target.at(OTHER));
		assertEquals(0, goals.acceptedLayers(WALKABLE));
		assertFalse(goals.accepts(WALKABLE, BankVisitState.CARRIED));
		assertFalse(goals.accepts(WALKABLE, BankVisitState.BANKED));
		assertTrue(goals.accepts(OTHER, BankVisitState.CARRIED));
		assertEquals(Set.of(OTHER), goals.tiles());
	}

	@Test
	public void unknownTargetsFinishAnywhereWhenNothingCanBeCompleted()
	{
		GoalStates goals = goals(NOTHING, spadeAt(WALKABLE), spadeAt(OTHER));
		assertEquals(3, goals.acceptedLayers(WALKABLE));
		assertEquals(3, goals.acceptedLayers(OTHER));
		assertTrue(goals.accepts(WALKABLE, BankVisitState.CARRIED));
		assertTrue(goals.accepts(OTHER, BankVisitState.BANKED));
		assertFalse(needsBank(NOTHING, spadeAt(WALKABLE), spadeAt(OTHER)));
		Arrival arrival = goals.arrival(WALKABLE, BankVisitState.CARRIED);
		assertEquals(TargetStatus.UNKNOWN, arrival.getStatus());
		assertEquals(TargetsTest.items(ItemID.SPADE).getRequirements(), arrival.getMissingItems());
	}

	@Test
	public void fallbackTilesFinishInTheirTargetsLayers()
	{
		GoalStates goals = goals(SPADE_BANKED, spadeAt(BLOCKED));
		Set<Integer> fallbacks = tiles(Set.of(BLOCKED)).fallbacks(BLOCKED);
		assertFalse("the blocked target has walkable tiles beside it", fallbacks.isEmpty());
		for (int fallback : fallbacks)
		{
			assertEquals(2, goals.acceptedLayers(fallback));
			assertFalse(goals.accepts(fallback, BankVisitState.CARRIED));
			assertTrue(goals.accepts(fallback, BankVisitState.BANKED));
			Arrival arrival = goals.arrival(fallback, BankVisitState.BANKED);
			assertEquals("a fallback tile arrives at its target", BLOCKED, arrival.getTarget().getPoint());
			assertEquals(TargetStatus.BANK, arrival.getStatus());
		}
	}

	@Test
	public void sharedFallbackUnionsItsTargetsAcceptedLayers()
	{
		Targets targets = Targets.of(List.of(spadeAt(BLOCKED), Target.at(OTHER)));
		GoalRule rule = new GoalRule()
		{
			@Override public boolean endsOnItself(int target)
			{
				return false;
			}
			@Override public Set<Integer> fallbackTiles(int target)
			{
				return Set.of(WALKABLE);
			}
		};
		GoalStates goals = GoalStates.resolve(TargetGoals.resolve(targets.points(), tile -> false, rule),
			targets, SPADE_BANKED);
		assertEquals(3, goals.acceptedLayers(WALKABLE));
		assertTrue(goals.accepts(WALKABLE, BankVisitState.CARRIED));
		assertTrue(goals.accepts(WALKABLE, BankVisitState.BANKED));
		assertEquals(2, goals.acceptedLayers(BLOCKED));
	}

	@Test
	public void tileAcceptedInEitherLayerForOneTargetNeedsNoBank()
	{
		GoalStates goals = goals(SPADE_BANKED, spadeAt(WALKABLE), Target.at(OTHER));
		assertTrue(needsBank(SPADE_BANKED, spadeAt(WALKABLE), Target.at(OTHER)));
		assertTrue(goals.accepts(OTHER, BankVisitState.CARRIED));
		assertFalse(goals.accepts(WALKABLE, BankVisitState.CARRIED));
	}

	@Test
	public void filteredTargetsComeBackWithTheirItems()
	{
		WildernessSettings settings = new WildernessSettings();
		Client filteringClient = mock(Client.class);
		when(filteringClient.getClientThread()).thenReturn(Thread.currentThread());
		PathfinderConfig filtering = new TestPathfinderConfig(filteringClient, settings);
		int wilderness = WorldPointUtil.packWorldPoint(3100, 3600, 0);
		Targets targets = Targets.of(List.of(spadeAt(wilderness), Target.at(WALKABLE)));

		filtering.refresh();
		assertEquals(Set.of(WALKABLE), filtering.filterLocations(targets, false).points());

		settings.avoidWilderness = false;
		filtering.refresh();
		Targets revived = filtering.filterLocations(Targets.at(List.of(WALKABLE)), true);
		assertEquals(spadeAt(wilderness), revived.get(wilderness));
	}

	@Test
	public void wildernessStaysFilteredWhenReviveIsRequested()
	{
		WildernessSettings settings = new WildernessSettings();
		Client filteringClient = mock(Client.class);
		when(filteringClient.getClientThread()).thenReturn(Thread.currentThread());
		PathfinderConfig filtering = new TestPathfinderConfig(filteringClient, settings);
		int wilderness = WorldPointUtil.packWorldPoint(3100, 3600, 0);
		Targets targets = Targets.of(List.of(spadeAt(wilderness), Target.at(WALKABLE)));

		filtering.refresh();
		// canReviveFiltered must not resurrect wilderness targets while the
		// wilderness filter is still active — restart paths pass true here,
		// so a config toggle would otherwise re-add the just-filtered tile.
		assertEquals(Set.of(WALKABLE), filtering.filterLocations(targets, true).points());
	}

	private static final class WildernessSettings extends TestShortestPathConfig
	{
		boolean avoidWilderness = true;

		@Override
		public Object value(String keyName)
		{
			return "avoidWilderness".equals(keyName) ? avoidWilderness : super.value(keyName);
		}
	}
}
