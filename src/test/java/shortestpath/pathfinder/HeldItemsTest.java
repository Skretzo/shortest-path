package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.transport.parser.ItemRequirementParser;

public class HeldItemsTest
{
	private static final TransportItems SPADE = TargetsTest.items(ItemID.SPADE);
	private static final TransportItems XERICS_GATE = new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR=1");
	private static final TransportItems TALISMAN_AND_GATE = new ItemRequirementParser()
		.parse("13393=1&UNLOCK_XERICS_HONOUR=1");
	private static final ItemRequirement THOUSAND_COINS = new ItemRequirement(new int[] {ItemID.COINS}, new int[0],
		new int[0], 1000);

	@Test
	public void carriedBankedOrNeither()
	{
		assertEquals(TargetStatus.CARRIED,
			new HeldItems(Map.of(ItemID.SPADE, 1), Map.of(ItemID.SPADE, 2), true).statusOf(SPADE));
		assertEquals(TargetStatus.BANK, new HeldItems(Map.of(), Map.of(ItemID.SPADE, 1), true).statusOf(SPADE));
		assertEquals(TargetStatus.UNKNOWN, new HeldItems(Map.of(), Map.of(), false).statusOf(SPADE));
	}

	@Test
	public void quantitiesCountCarriedAndBankedTogether()
	{
		TransportItems coins = new TransportItems(List.of(THOUSAND_COINS));
		HeldItems held = new HeldItems(Map.of(ItemID.COINS, 400), Map.of(ItemID.COINS, 1400), true);
		assertEquals(TargetStatus.BANK, held.statusOf(coins));
	}

	@Test
	public void requiredItemsAreHeldNotSpent()
	{
		// Transports refuse to spend more than the currency threshold; holding coins has no limit.
		TransportItems coins = new TransportItems(List.of(THOUSAND_COINS));
		assertEquals(TargetStatus.CARRIED, new HeldItems(Map.of(ItemID.COINS, 1000), Map.of(), true).statusOf(coins));
	}

	@Test
	public void missingListsOnlyTheGroupsNotFound()
	{
		TransportItems spadeAndRope = TargetsTest.items(ItemID.SPADE, ItemID.ROPE);
		HeldItems held = new HeldItems(Map.of(), Map.of(ItemID.SPADE, 1), true);
		assertEquals(List.of(spadeAndRope.getRequirements().get(1)), held.missing(spadeAndRope));
	}

	@Test
	public void declaredUnlockMarksRequirementCarried()
	{
		// An unlock-only requirement is satisfied by declaring its unlock,
		// even with nothing held or banked.
		HeldItems empty = new HeldItems(Map.of(), Map.of(), false);
		assertEquals(TargetStatus.CARRIED, empty.statusOf(XERICS_GATE, Set.of(Unlock.XERICS_HONOUR)));
		assertEquals(TargetStatus.UNKNOWN, empty.statusOf(XERICS_GATE, Set.of()));
	}

	@Test
	public void declaredUnlockNeverRelievesTheItemTerm()
	{
		// The unlock term of an ANDed gate is its own requirement: a declared
		// honour still needs the talisman, and a held talisman still needs the
		// declared honour.
		HeldItems empty = new HeldItems(Map.of(), Map.of(), false);
		assertEquals(TargetStatus.UNKNOWN,
			empty.statusOf(TALISMAN_AND_GATE, Set.of(Unlock.XERICS_HONOUR)));

		HeldItems talisman = new HeldItems(Map.of(13393, 1), Map.of(13393, 1), true);
		assertEquals(TargetStatus.CARRIED,
			talisman.statusOf(TALISMAN_AND_GATE, Set.of(Unlock.XERICS_HONOUR)));
		assertEquals(TargetStatus.UNKNOWN, talisman.statusOf(TALISMAN_AND_GATE, Set.of()));
	}

	@Test
	public void missingDropsTheUnlockGroupWhenDeclared()
	{
		HeldItems empty = new HeldItems(Map.of(), Map.of(), false);
		assertEquals(List.of(), empty.missing(XERICS_GATE, Set.of(Unlock.XERICS_HONOUR)));
		assertEquals(List.of(XERICS_GATE.getRequirements().get(0)), empty.missing(XERICS_GATE, Set.of()));

		// In an ANDed gate the declared unlock drops only its own group.
		HeldItems talisman = new HeldItems(Map.of(13393, 1), Map.of(13393, 1), true);
		assertEquals(List.of(), talisman.missing(TALISMAN_AND_GATE, Set.of(Unlock.XERICS_HONOUR)));
		assertEquals(List.of(TALISMAN_AND_GATE.getRequirements().get(1)),
			talisman.missing(TALISMAN_AND_GATE, Set.of()));
	}

	@Test
	public void oneArgOverloadsMatchAnEmptyUnlockSet()
	{
		HeldItems held = new HeldItems(Map.of(), Map.of(ItemID.SPADE, 1), true);
		assertEquals(held.statusOf(SPADE), held.statusOf(SPADE, Set.of()));
		TransportItems spadeAndRope = TargetsTest.items(ItemID.SPADE, ItemID.ROPE);
		assertEquals(held.missing(spadeAndRope), held.missing(spadeAndRope, Set.of()));
	}

	@Test
	public void missingKeepsDeclarationOrder()
	{
		// Unsatisfied groups come back in the order they were declared,
		// unlock-gated groups included.
		TransportItems mixed = new ItemRequirementParser().parse("13121=1&UNLOCK_XERICS_HONOUR=1&13124=1");
		HeldItems held = new HeldItems(Map.of(), Map.of(13124, 1), true);
		assertEquals(List.of(mixed.getRequirements().get(0), mixed.getRequirements().get(1)),
			held.missing(mixed, Set.of()));
	}
}
