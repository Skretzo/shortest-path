package shortestpath.requirement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;

/** The bank step's "Pick up" list for the items a path's target needs on arrival. */
public class BankPickupTargetItemsTest
{
	private static final int[] NONE = new int[0];
	private static final int BANK_TILE = WorldPointUtil.packWorldPoint(3092, 3243, 0);
	private static final int NEXT_TILE = WorldPointUtil.packWorldPoint(3093, 3243, 0);
	private static final int TARGET = WorldPointUtil.packWorldPoint(3100, 3243, 0);

	private final Client client = mock(Client.class);
	private final Map<Integer, Integer> playerHas = new HashMap<>();
	private final Map<Integer, Integer> bankHas = new HashMap<>();
	private final Set<String> phrases = new LinkedHashSet<>();
	private final Set<Integer> highlights = new java.util.HashSet<>();

	@Before
	public void names()
	{
		Map<Integer, String> names = Map.of(ItemID.SPADE, "Spade", ItemID.ROPE, "Rope", ItemID.COINS, "Coins");
		doAnswer(call ->
		{
			ItemComposition item = mock(ItemComposition.class);
			when(item.getName()).thenReturn(names.get((Integer) call.getArgument(0)));
			return item;
		}).when(client).getItemDefinition(anyInt());
	}

	private static ItemRequirement anyOf(Map<Integer, Integer> idsToQuantity)
	{
		List<ItemRequirement.Branch> branches = new java.util.ArrayList<>();
		idsToQuantity.forEach((id, quantity) -> branches.add(new ItemRequirement.Branch(new int[] {id}, NONE, NONE, quantity)));
		return new ItemRequirement(branches);
	}

	private static TransportItems needs(ItemRequirement... groups)
	{
		return new TransportItems(List.of(groups));
	}

	private void pickups(TransportItems target)
	{
		TransportEligibility.BankPickupPlan plan =
			TransportEligibility.forPlayerAndBank(playerHas, bankHas, -1, Map.of()).targetPickupPlan(target);
		for (Map.Entry<Integer, Long> entry : plan.items.entrySet())
		{
			Map<Integer, Long> single = new java.util.LinkedHashMap<>();
			single.put(entry.getKey(), entry.getValue());
			phrases.add(BankPickupRequirements.formatPickups(client, single));
		}
		highlights.addAll(plan.bankItemIds);
	}

	@Test
	public void bankedItemIsPickedUpAndHighlighted()
	{
		bankHas.put(ItemID.SPADE, 1);
		pickups(needs(anyOf(Map.of(ItemID.SPADE, 1))));
		assertEquals(Set.of("1 Spade"), phrases);
		assertEquals(Set.of(ItemID.SPADE), highlights);
	}

	@Test
	public void carriedItemNeedsNoPickup()
	{
		playerHas.put(ItemID.SPADE, 1);
		bankHas.put(ItemID.SPADE, 1);
		pickups(needs(anyOf(Map.of(ItemID.SPADE, 1))));
		assertTrue(phrases.isEmpty());
		assertTrue(highlights.isEmpty());
	}

	@Test
	public void alternativeTheBankHoldsIsChosen()
	{
		bankHas.put(ItemID.ROPE, 3);
		pickups(needs(anyOf(Map.of(ItemID.SPADE, 1, ItemID.ROPE, 1))));
		assertEquals(Set.of("1 Rope"), phrases);
		assertEquals(Set.of(ItemID.ROPE), highlights);
	}

	@Test
	public void onlyTheShortfallIsPickedUp()
	{
		playerHas.put(ItemID.COINS, 400);
		bankHas.put(ItemID.COINS, 5000);
		pickups(needs(anyOf(Map.of(ItemID.COINS, 1000))));
		assertEquals(Set.of("Coins (600)"), phrases);
	}

	@Test
	public void groupsTheBankCannotCoverAreLeftOut()
	{
		bankHas.put(ItemID.SPADE, 1);
		pickups(needs(anyOf(Map.of(ItemID.SPADE, 1)), anyOf(Map.of(ItemID.ROPE, 1))));
		assertEquals(Set.of("1 Spade"), phrases);
	}

	@Test
	public void bankVisitIsTheStepBeforeAndTheFirstBankedStep()
	{
		List<PathStep> path = List.of(new PathStep(NEXT_TILE, false), new PathStep(BANK_TILE, false),
			new PathStep(BANK_TILE, true), new PathStep(NEXT_TILE, true));
		assertFalse(BankPickupRequirements.visitsBankAt(path, 0));
		assertTrue(BankPickupRequirements.visitsBankAt(path, 1));
		assertTrue(BankPickupRequirements.visitsBankAt(path, 2));
		assertFalse(BankPickupRequirements.visitsBankAt(path, 3));
		assertTrue("a path that starts banked visits at its start",
			BankPickupRequirements.visitsBankAt(List.of(new PathStep(BANK_TILE, true)), 0));
	}

	@Test
	public void computeListsTargetItemsOnlyAtTheBankVisit()
	{
		ItemContainer bank = mock(ItemContainer.class);
		doReturn(new Item[] {new Item(ItemID.SPADE, 1)}).when(bank).getItems();
		ItemContainer empty = mock(ItemContainer.class);
		doReturn(new Item[0]).when(empty).getItems();
		doReturn(empty).when(client).getItemContainer(InventoryID.INV);
		doReturn(empty).when(client).getItemContainer(InventoryID.WORN);
		PathfinderConfig config = mock(PathfinderConfig.class);
		when(config.getTransportAvailability(anyBoolean())).thenReturn(TransportAvailabilityFixture.of());
		when(config.getUnlocks()).thenReturn(Set.of());
		when(config.getEligibility()).thenReturn(
			TransportEligibility.forPlayerAndBank(Map.of(), Map.of(ItemID.SPADE, 1), -1, Map.of()));
		TransportItems spade = needs(anyOf(Map.of(ItemID.SPADE, 1)));
		List<PathStep> path = List.of(new PathStep(BANK_TILE, false), new PathStep(BANK_TILE, true),
			new PathStep(NEXT_TILE, true), new PathStep(TARGET, true));
		Set<Integer> banks = Set.of(BANK_TILE);

		BankPickupRequirements.BankPickupResult atBank = BankPickupRequirements.BankPickupResult.compute(
			client, bank, config, banks, path, 0, spade);
		assertEquals(List.of("1 Spade"), atBank.phrases);
		assertEquals(Set.of(ItemID.SPADE), atBank.bankItemIds);

		assertTrue("without target items nothing is listed", BankPickupRequirements.BankPickupResult.compute(
			client, bank, config, banks, path, 0).phrases.isEmpty());

		List<PathStep> walksPast = List.of(new PathStep(BANK_TILE, false), new PathStep(NEXT_TILE, false),
			new PathStep(TARGET, false));
		assertTrue("a bank tile the path only walks over is no visit", BankPickupRequirements.BankPickupResult
			.compute(client, bank, config, banks, walksPast, 0, spade).phrases.isEmpty());
	}
}
