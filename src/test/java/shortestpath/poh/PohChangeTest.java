package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import java.util.Set;
import org.junit.Test;

import shortestpath.settings.Effect;

public class PohChangeTest
{
	@Test
	public void testFactCarriesKeyAndEffects()
	{
		PohChange change = new PohChange("dialogLine", Set.of(Effect.DISPLAY_ONLY));

		assertEquals("dialogLine", change.getKey());
		assertEquals(Set.of(Effect.DISPLAY_ONLY), change.getEffects());
	}

	@Test
	public void testDisplayOnlyChangesNeverInvalidateRoutes()
	{
		// Keybind changes are display metadata: nothing they observe can
		// invalidate a running search.
		PohChange change = new PohChange("dialog", Set.of(Effect.DISPLAY_ONLY));

		assertFalse(change.getEffects().contains(Effect.ROUTE_INVALIDATING));
	}

	@Test
	public void testEffectsAreImmutable()
	{
		PohChange change = new PohChange("key", Set.of(Effect.DISPLAY_ONLY));

		assertThrows(UnsupportedOperationException.class,
			() -> change.getEffects().add(Effect.DISPLAY_ONLY));
	}
}
