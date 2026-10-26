package shortestpath.requirement.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;

import java.util.List;
import org.junit.Test;

/**
 * Contract pin for {@link ItemRequirement} and {@link ItemRequirement.Branch}
 * equality: a requirement is its ordered branch list, and a branch is its
 * three id arrays, quantity and unlock. Hash-consistency is asserted, never
 * hash values.
 */
public class ItemRequirementTest
{
	@Test
	public void branchEqualityCoversEveryField()
	{
		ItemRequirement.Branch first = new ItemRequirement.Branch(new int[]{1, 2}, null, null, 1);
		ItemRequirement.Branch second = new ItemRequirement.Branch(new int[]{1, 2}, null, null, 1);

		assertNotSame(first, second);
		assertEquals(first, first);
		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());

		// Each field is part of the key: a single-field mutation is unequal.
		assertNotEquals(first, new ItemRequirement.Branch(new int[]{1, 3}, null, null, 1));
		assertNotEquals(first, new ItemRequirement.Branch(new int[]{1, 2}, new int[]{4}, null, 1));
		assertNotEquals(first, new ItemRequirement.Branch(new int[]{1, 2}, null, new int[]{5}, 1));
		assertNotEquals(first, new ItemRequirement.Branch(new int[]{1, 2}, null, null, 2));
		assertNotEquals(first,
			new ItemRequirement.Branch(new int[]{1, 2}, null, null, 1, Unlock.CANOE_AXE));
		assertFalse(first.equals(null));
		assertNotEquals(first, "branch");
	}

	@Test
	public void requirementEqualityFollowsTheBranchList()
	{
		ItemRequirement first = new ItemRequirement(List.of(
			new ItemRequirement.Branch(new int[]{1, 2}, null, null, 1),
			new ItemRequirement.Branch(new int[]{3}, new int[]{4}, null, 5)));
		ItemRequirement second = new ItemRequirement(List.of(
			new ItemRequirement.Branch(new int[]{1, 2}, null, null, 1),
			new ItemRequirement.Branch(new int[]{3}, new int[]{4}, null, 5)));

		assertNotSame(first, second);
		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());

		// The array-constructor shape produces an equal requirement.
		assertEquals(new ItemRequirement(new int[]{1, 2}, null, null, 1),
			new ItemRequirement(List.of(new ItemRequirement.Branch(new int[]{1, 2}, null, null, 1))));

		// A differing branch makes the requirement differ.
		ItemRequirement other = new ItemRequirement(List.of(
			new ItemRequirement.Branch(new int[]{1, 2}, null, null, 1),
			new ItemRequirement.Branch(new int[]{3}, new int[]{4}, null, 6)));
		assertNotEquals(first, other);

		// Declaration order is part of the value.
		ItemRequirement reordered = new ItemRequirement(List.of(
			new ItemRequirement.Branch(new int[]{3}, new int[]{4}, null, 5),
			new ItemRequirement.Branch(new int[]{1, 2}, null, null, 1)));
		assertNotEquals(first, reordered);

		assertFalse(first.equals(null));
	}
}
