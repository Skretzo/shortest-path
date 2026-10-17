package shortestpath.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.scheduler.RefreshCoordinator;
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
	 * producers declare must carry only effects the coordinator maps — item
	 * events declare exactly {@link Effect#ELIGIBILITY_STALE} — and every
	 * untracked input admits no declare at all, so a dropped branch can never
	 * lose a declared effect.
	 */
	@Test
	public void everyProducerFactDeclaresExactlyEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		int admitted = 0;
		for (int containerId : new int[]{InventoryID.BANK, InventoryID.INV, InventoryID.WORN})
		{
			service.onContainerChanged(containerId, mock(ItemContainer.class));
			admitted++;
		}

		service.onVarbitChanged(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);
		admitted++;
		for (int varbitId : OwnedItems.RUNE_POUCH_RUNE_VARBITS)
		{
			service.onVarbitChanged(varbitId);
			admitted++;
		}
		for (int varbitId : OwnedItems.RUNE_POUCH_AMOUNT_VARBITS)
		{
			service.onVarbitChanged(varbitId);
			admitted++;
		}

		// Untracked inputs declare nothing at all.
		service.onContainerChanged(InventoryID.LOOTING_BAG, mock(ItemContainer.class));
		service.onVarbitChanged(VarbitID.FAIRY2_QUEENCURE_QUEST);

		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(admitted)).itemsChanged(captor.capture());
		for (ItemChange change : captor.getAllValues())
		{
			assertEquals("fact " + change.getKey() + " declares only the mapped effect",
				Set.of(Effect.ELIGIBILITY_STALE), change.getEffects());
		}
	}
}
