package shortestpath.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.settings.Effect;

/**
 * Pins the item-state producer seam contract: which game inputs the service
 * admits, which {@link ItemChange} fact each admitted input returns, and the
 * ownership rules for the live open-bank reference. The shell relies on a
 * non-null fact meaning "a tracked input changed" and on the declared
 * {@link Effect}s to decide follow-up actions, so ignored/unmapped returns
 * are the failure mode these tests guard against.
 */
public class ItemStateServiceTest
{
	private static void assertEligibilityStale(ItemChange change, String what)
	{
		assertNotNull(what + " produces a change fact", change);
		assertTrue(what + " declares ELIGIBILITY_STALE",
			change.getEffects().contains(Effect.ELIGIBILITY_STALE));
	}

	@Test
	public void bankContainerIsStoredAsLiveRefAndDeclaresEligibilityStale()
	{
		ItemStateService service = ItemStateService.forTesting();
		ItemContainer bank = mock(ItemContainer.class);

		ItemChange change = service.onContainerChanged(InventoryID.BANK, bank);

		assertEligibilityStale(change, "bank container event");
		assertSame("the open bank stays a live reference, never copied", bank, service.getBank());
	}

	@Test
	public void inventoryContainerDeclaresEligibilityStaleButKeepsBankRef()
	{
		ItemStateService service = ItemStateService.forTesting();
		ItemContainer bank = mock(ItemContainer.class);
		ItemContainer inv = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		ItemChange change = service.onContainerChanged(InventoryID.INV, inv);

		assertEligibilityStale(change, "inventory container event");
		assertSame("inventory events never overwrite the bank ref", bank, service.getBank());
	}

	@Test
	public void wornContainerDeclaresEligibilityStaleButKeepsBankRef()
	{
		ItemStateService service = ItemStateService.forTesting();
		ItemContainer bank = mock(ItemContainer.class);
		ItemContainer worn = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		ItemChange change = service.onContainerChanged(InventoryID.WORN, worn);

		assertEligibilityStale(change, "equipment container event");
		assertSame("equipment events never overwrite the bank ref", bank, service.getBank());
	}

	@Test
	public void unrelatedContainerReturnsNullAndTouchesNoState()
	{
		ItemStateService service = ItemStateService.forTesting();
		ItemContainer bank = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		ItemChange change = service.onContainerChanged(InventoryID.LOOTING_BAG, mock(ItemContainer.class));

		assertNull("an untracked container admits no fact", change);
		assertSame("an untracked container never overwrites the bank ref", bank, service.getBank());
	}

	@Test
	public void lumbridgeDiaryVarbitDeclaresEligibilityStale()
	{
		ItemStateService service = ItemStateService.forTesting();

		ItemChange change = service.onVarbitChanged(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);

		assertEligibilityStale(change, "Lumbridge diary varbit");
	}

	@Test
	public void runePouchRuneVarbitsDeclareEligibilityStale()
	{
		ItemStateService service = ItemStateService.forTesting();

		for (int varbitId : OwnedItems.RUNE_POUCH_RUNE_VARBITS)
		{
			assertEligibilityStale(service.onVarbitChanged(varbitId),
				"rune pouch rune varbit " + varbitId);
		}
	}

	@Test
	public void runePouchAmountVarbitsDeclareEligibilityStale()
	{
		ItemStateService service = ItemStateService.forTesting();

		for (int varbitId : OwnedItems.RUNE_POUCH_AMOUNT_VARBITS)
		{
			assertEligibilityStale(service.onVarbitChanged(varbitId),
				"rune pouch amount varbit " + varbitId);
		}
	}

	@Test
	public void unrelatedVarbitReturnsNull()
	{
		ItemStateService service = ItemStateService.forTesting();

		assertNull("an untracked varbit admits no fact",
			service.onVarbitChanged(VarbitID.FAIRY2_QUEENCURE_QUEST));
	}

	@Test
	public void noteBankContainerSeedsTheBankRefForHarnesses()
	{
		ItemStateService service = ItemStateService.forTesting();
		ItemContainer bank = mock(ItemContainer.class);

		service.noteBankContainer(bank);

		assertSame(bank, service.getBank());
	}

	@Test
	public void changeFactExposesKeyAndEffectsAsAValue()
	{
		ItemStateService service = ItemStateService.forTesting();

		ItemChange first = service.onContainerChanged(InventoryID.BANK, mock(ItemContainer.class));
		ItemChange second = service.onContainerChanged(InventoryID.BANK, mock(ItemContainer.class));

		assertEquals("container:" + InventoryID.BANK, first.getKey());
		assertNotSame("each observed change is its own fact", first, second);
		try
		{
			first.getEffects().add(Effect.DISPLAY_ONLY);
			fail("the fact's effect set must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
		}
	}
}
