package shortestpath.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.settings.Effect;

/**
 * The {@link ItemChange} fact is the only channel by which the item-state
 * producers talk to the shell: an immutable key plus the {@link Effect}s the
 * change triggers. These tests pin the value semantics — immutability in both
 * directions — and the producer contract that every emitted fact declares
 * exactly {@link Effect#ELIGIBILITY_STALE}, the single effect the shell's
 * change-application maps.
 */
public class ItemChangeTest
{
	@Test
	public void exposesKeyAndEffectsFromConstruction()
	{
		ItemChange change = new ItemChange("container:" + InventoryID.INV,
			Set.of(Effect.ELIGIBILITY_STALE));

		assertEquals("container:" + InventoryID.INV, change.getKey());
		assertEquals(Set.of(Effect.ELIGIBILITY_STALE), change.getEffects());
	}

	@Test
	public void callerHeldSetMutationDoesNotLeakIntoTheFact()
	{
		Set<Effect> effects = new HashSet<>();
		effects.add(Effect.ELIGIBILITY_STALE);
		ItemChange change = new ItemChange("varbit:1", effects);

		effects.add(Effect.ROUTE_INVALIDATING);
		effects.clear();

		assertEquals("the fact snapshots its effects at construction",
			Set.of(Effect.ELIGIBILITY_STALE), change.getEffects());
	}

	@Test
	public void returnedEffectSetRejectsMutation()
	{
		ItemChange change = new ItemChange("k", Set.of(Effect.ELIGIBILITY_STALE));

		try
		{
			change.getEffects().add(Effect.DISPLAY_ONLY);
			fail("getEffects() must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
		}
	}

	@Test
	public void nullEffectSetIsRejectedAtConstruction()
	{
		assertThrows(NullPointerException.class, () -> new ItemChange("k", null));
	}

	/**
	 * The Wave-0 fact-consumption row: every {@link ItemChange} the service's
	 * producers emit must declare only effects the shell maps — item events
	 * declare exactly {@link Effect#ELIGIBILITY_STALE} — and every untracked
	 * input admits no fact at all, so an ignored return can never drop a
	 * declared effect.
	 */
	@Test
	public void everyProducerFactDeclaresExactlyEligibilityStale()
	{
		ItemStateService service = ItemStateService.forTesting();

		for (int containerId : new int[]{InventoryID.BANK, InventoryID.INV, InventoryID.WORN})
		{
			assertEquals("container " + containerId + " declares only the mapped effect",
				Set.of(Effect.ELIGIBILITY_STALE),
				service.onContainerChanged(containerId, mock(ItemContainer.class)).getEffects());
		}

		assertEquals(Set.of(Effect.ELIGIBILITY_STALE),
			service.onVarbitChanged(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE).getEffects());
		for (int varbitId : OwnedItems.RUNE_POUCH_RUNE_VARBITS)
		{
			assertEquals("rune varbit " + varbitId + " declares only the mapped effect",
				Set.of(Effect.ELIGIBILITY_STALE), service.onVarbitChanged(varbitId).getEffects());
		}
		for (int varbitId : OwnedItems.RUNE_POUCH_AMOUNT_VARBITS)
		{
			assertEquals("amount varbit " + varbitId + " declares only the mapped effect",
				Set.of(Effect.ELIGIBILITY_STALE), service.onVarbitChanged(varbitId).getEffects());
		}

		assertNull("an untracked container admits no fact",
			service.onContainerChanged(InventoryID.LOOTING_BAG, mock(ItemContainer.class)));
		assertNull("an untracked varbit admits no fact",
			service.onVarbitChanged(VarbitID.FAIRY2_QUEENCURE_QUEST));
	}
}
