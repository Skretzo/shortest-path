package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

public class TargetsTest
{
	private static final int A = 1;
	private static final int B = 2;
	private static final int C = 3;

	static TransportItems items(int... ids)
	{
		List<ItemRequirement> groups = new java.util.ArrayList<>();
		for (int id : ids)
		{
			groups.add(new ItemRequirement(new int[] {id}, new int[0], new int[0], 1));
		}
		return new TransportItems(groups);
	}

	@Test
	public void pointsMakeTargetsWithoutItems()
	{
		Targets targets = Targets.at(List.of(A, B));
		assertEquals(Set.of(A, B), targets.points());
		assertFalse(targets.hasRequiredItems());
		assertEquals(Target.at(A), targets.get(A));
		assertSame(Targets.NONE, Targets.at(List.of()));
	}

	@Test
	public void sameTileTwiceIsFineWhenItAsksForTheSameItems()
	{
		Targets targets = Targets.of(List.of(Target.requiring(A, items(ItemID.SPADE)),
			Target.requiring(A, items(ItemID.SPADE))));
		assertEquals(1, targets.size());
		assertTrue(targets.hasRequiredItems());
	}

	@Test(expected = IllegalArgumentException.class)
	public void sameTileTwiceWithDifferentItemsIsAmbiguous()
	{
		Targets.of(List.of(Target.requiring(A, items(ItemID.SPADE)), Target.at(A)));
	}

	@Test
	public void restrictingKeepsItemsAndAddsPlainTargets()
	{
		Target spade = Target.requiring(A, items(ItemID.SPADE));
		Targets restricted = Targets.of(List.of(spade, Target.at(B))).restrictTo(List.of(A, C));
		assertEquals(Set.of(A, C), restricted.points());
		assertEquals(spade, restricted.get(A));
		assertEquals(Target.at(C), restricted.get(C));
	}

	@Test
	public void plusKeepsTheTargetsAlreadyThere()
	{
		Target spade = Target.requiring(A, items(ItemID.SPADE));
		Targets added = Targets.of(List.of(spade)).plus(Targets.at(List.of(A, B)));
		assertEquals(spade, added.get(A));
		assertEquals(Target.at(B), added.get(B));
	}

	@Test
	public void equalTargetsAreEqualWhateverTheirOrder()
	{
		assertEquals(Targets.at(List.of(A, B)), Targets.at(List.of(B, A)));
		assertFalse(Targets.at(List.of(A)).equals(Targets.of(List.of(Target.requiring(A, items(ItemID.SPADE))))));
	}
}
