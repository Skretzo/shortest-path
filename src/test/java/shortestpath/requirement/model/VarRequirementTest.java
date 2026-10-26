package shortestpath.requirement.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

/**
 * Contract pin for {@link VarRequirement} equality: all four fields are
 * load-bearing because the load interner keys its dedup pool on them.
 * Hash-consistency is asserted, never hash values.
 */
public class VarRequirementTest
{
	private static VarRequirement varbit(int id, int value, VarCheckType checkType)
	{
		return new VarRequirement(VarRequirement.VarType.VARBIT, id, value, checkType);
	}

	@Test
	public void equalsAndHashCodeFollowAllFourFields()
	{
		VarRequirement first = varbit(123, 5, VarCheckType.GREATER);
		VarRequirement second = varbit(123, 5, VarCheckType.GREATER);

		assertNotSame(first, second);
		assertEquals(first, first);
		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());

		// Each field is part of the key: a single-field mutation is unequal.
		assertNotEquals(first,
			new VarRequirement(VarRequirement.VarType.VARPLAYER, 123, 5, VarCheckType.GREATER));
		assertNotEquals(first, varbit(124, 5, VarCheckType.GREATER));
		assertNotEquals(first, varbit(123, 6, VarCheckType.GREATER));
		assertNotEquals(first, varbit(123, 5, VarCheckType.EQUAL));
	}

	@Test
	public void equalsRejectsNullAndForeignTypes()
	{
		VarRequirement requirement = varbit(123, 5, VarCheckType.GREATER);
		assertFalse(requirement.equals(null));
		assertNotEquals(requirement, "VARBIT[123 > 5]");
	}
}
