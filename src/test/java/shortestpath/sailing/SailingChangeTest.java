package shortestpath.sailing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import net.runelite.api.gameval.VarbitID;
import shortestpath.settings.Effect;

/**
 * The {@link SailingChange} fact is the channel by which the sailing producer
 * talks to the shell: an immutable key plus the {@link Effect}s the change
 * triggers. These tests pin the value semantics — immutability in both
 * directions — so a declared fact can never be rewritten after minting.
 */
public class SailingChangeTest
{
	@Test
	public void exposesKeyAndEffectsFromConstruction()
	{
		SailingChange change = new SailingChange("varbit:" + VarbitID.SAILING_BOARDED_BOAT,
			Set.of(Effect.ROUTE_INVALIDATING));

		assertEquals("varbit:" + VarbitID.SAILING_BOARDED_BOAT, change.getKey());
		assertEquals(Set.of(Effect.ROUTE_INVALIDATING), change.getEffects());
	}

	@Test
	public void callerHeldSetMutationDoesNotLeakIntoTheFact()
	{
		Set<Effect> effects = new HashSet<>();
		effects.add(Effect.ROUTE_INVALIDATING);
		SailingChange change = new SailingChange("varbit:1", effects);

		effects.add(Effect.DISPLAY_ONLY);
		effects.clear();

		assertEquals("the fact snapshots its effects at construction",
			Set.of(Effect.ROUTE_INVALIDATING), change.getEffects());
	}

	@Test
	public void returnedEffectSetRejectsMutation()
	{
		SailingChange change = new SailingChange("k", Set.of(Effect.ROUTE_INVALIDATING));

		assertThrows(UnsupportedOperationException.class,
			() -> change.getEffects().add(Effect.DISPLAY_ONLY));
	}

	@Test
	public void nullKeyIsRejectedAtConstruction()
	{
		assertThrows(NullPointerException.class,
			() -> new SailingChange(null, Set.of(Effect.ROUTE_INVALIDATING)));
	}

	@Test
	public void nullEffectSetIsRejectedAtConstruction()
	{
		assertThrows(NullPointerException.class, () -> new SailingChange("k", null));
	}
}
