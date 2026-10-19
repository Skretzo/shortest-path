package shortestpath.transport.parser;

import org.junit.Assert;
import org.junit.Test;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;

/**
 * Pins the {@code UNLOCK_<NAME>=1} grammar contract: an unlock token is only
 * legal in its {@code =1} form, and any other quantity — or any quantity on an
 * unknown unlock name — is a loud {@link IllegalArgumentException} wherever the
 * token appears (pure cell, OR branch, or AND term).
 */
public class ItemRequirementParserTest
{

	@Test(expected = IllegalArgumentException.class)
	public void zeroQuantityUnlockIsRejected()
	{
		new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR=0");
	}

	@Test(expected = IllegalArgumentException.class)
	public void greaterThanOneQuantityUnlockIsRejected()
	{
		new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR=2");
	}

	@Test(expected = IllegalArgumentException.class)
	public void negativeQuantityUnlockIsRejected()
	{
		new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR=-1");
	}

	@Test(expected = IllegalArgumentException.class)
	public void unknownUnlockNameIsRejected()
	{
		new ItemRequirementParser().parse("UNLOCK_BOGUS=1");
	}

	@Test(expected = IllegalArgumentException.class)
	public void nonNumericUnlockQuantityIsRejected()
	{
		new ItemRequirementParser().parse("13393=1&UNLOCK_XERICS_HONOUR=abc");
	}

	@Test(expected = IllegalArgumentException.class)
	public void missingQuantityUnlockIsRejected()
	{
		new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR");
	}

	@Test(expected = IllegalArgumentException.class)
	public void overflowingUnlockQuantityIsRejected()
	{
		new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR=99999999999");
	}

	@Test(expected = IllegalArgumentException.class)
	public void leadingZeroUnlockQuantityIsRejected()
	{
		// '=1' is a grammar contract: only the literal string "1" is legal,
		// matching check_tsv.py's string-level check.
		new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR=01");
	}

	@Test(expected = IllegalArgumentException.class)
	public void signedUnlockQuantityIsRejected()
	{
		new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR=+1");
	}

	@Test(expected = IllegalArgumentException.class)
	public void nonOneUnlockInsideOrGroupIsRejected()
	{
		new ItemRequirementParser().parse("AXE=1|UNLOCK_XERICS_HONOUR=2");
	}

	@Test(expected = IllegalArgumentException.class)
	public void nonOneUnlockInsideAndGroupIsRejected()
	{
		new ItemRequirementParser().parse("13393=1&UNLOCK_XERICS_HONOUR=0");
	}

	@Test
	public void legalUnlockTokenParsesToUnlockOnlyBranch()
	{
		TransportItems items = new ItemRequirementParser().parse("UNLOCK_XERICS_HONOUR=1");

		Assert.assertNotNull("UNLOCK_*=1 must parse", items);
		Assert.assertEquals("One ANDed requirement", 1, items.getRequirements().size());

		ItemRequirement requirement = items.getRequirements().get(0);
		Assert.assertEquals("One OR branch", 1, requirement.getBranches().size());

		ItemRequirement.Branch branch = requirement.getBranches().get(0);
		Assert.assertTrue("Branch must be unlock-only", branch.isUnlockOnly());
		Assert.assertEquals("Branch carries the named unlock", Unlock.XERICS_HONOUR, branch.getUnlock());
	}
}
