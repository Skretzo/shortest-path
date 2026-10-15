package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;

public class HeldItemsTest
{
	private static final TransportItems SPADE = TargetsTest.items(ItemID.SPADE);
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
}
