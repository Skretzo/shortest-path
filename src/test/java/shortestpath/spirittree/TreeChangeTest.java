package shortestpath.spirittree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import shortestpath.settings.Effect;

/**
 * The spirit-tree change fact is the only hand-off between the service and
 * the plugin shell — pin its shape so a drifted value type breaks loudly
 * here rather than silently at the consumption site.
 */
public class TreeChangeTest
{
	@Test
	public void factCarriesKeyAndEffects()
	{
		TreeChange change = new TreeChange("varbit:Port Sarim", Set.of(Effect.ROUTE_INVALIDATING));

		assertEquals("varbit:Port Sarim", change.getKey());
		assertEquals(Set.of(Effect.ROUTE_INVALIDATING), change.getEffects());
	}

	@Test
	public void effectsAreImmutable()
	{
		Set<Effect> mutable = new HashSet<>();
		mutable.add(Effect.ROUTE_INVALIDATING);
		TreeChange change = new TreeChange("menu", mutable);

		mutable.add(Effect.ELIGIBILITY_STALE);
		assertEquals(Set.of(Effect.ROUTE_INVALIDATING), change.getEffects());
		assertThrows(UnsupportedOperationException.class,
			() -> change.getEffects().add(Effect.DISPLAY_ONLY));
	}

	@Test
	public void producersEmitRouteInvalidatingOnly()
	{
		// A changed tree set invalidates running paths but never item or
		// quest eligibility — the service must only ever declare
		// ROUTE_INVALIDATING. Drive every producer and assert the emitted
		// effect set carries it and nothing else.
		SpiritTreeService service = SpiritTreeService.forTesting();
		TreeChange profile = service.loadFromProfile();
		assertEquals(Set.of(Effect.ROUTE_INVALIDATING), profile.getEffects());

		TreeChange varbit = service.applyVarbitSample("Port Sarim", 20);
		assertEquals(Set.of(Effect.ROUTE_INVALIDATING), varbit.getEffects());

		TreeChange menu = service.applyMenuSnapshot(Set.of("Etceteria"), Set.of("Etceteria"));
		assertEquals(Set.of(Effect.ROUTE_INVALIDATING), menu.getEffects());
	}

	@Test
	public void unchangedObservationsEmitNullFacts()
	{
		SpiritTreeService service = SpiritTreeService.forTesting();
		service.applyVarbitSample("Port Sarim", 20);

		// An identical re-read is not a change — no fact is emitted.
		assertNull(service.applyVarbitSample("Port Sarim", 20));
		assertNull(service.applyMenuSnapshot(Set.of("Port Sarim"), Set.of("Port Sarim")));
	}
}
