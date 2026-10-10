package shortestpath.sailing;

import java.util.Objects;
import java.util.Set;

import lombok.Getter;

import shortestpath.settings.Effect;

/**
 * An immutable fact describing a single sailing-state change: the input that
 * changed (keyed as {@code "varbit:<id>"} for one of the four sailing varbits)
 * and the {@link Effect}s it triggers. Produced by {@link SailingService};
 * the shell reads the effect set to decide which follow-up actions to run
 * rather than re-deriving them from the event.
 *
 * <p>Sailing only ever emits {@link Effect#ROUTE_INVALIDATING}: ordinary boat
 * movement — position, pivot, heading — is filtered out before a fact is
 * minted, so a declared fact always means "the route's sailing inputs
 * changed" and never merely "the boat moved".
 */
@Getter
public final class SailingChange
{
	private final String key;
	private final Set<Effect> effects;

	SailingChange(String key, Set<Effect> effects)
	{
		this.key = Objects.requireNonNull(key);
		this.effects = Set.copyOf(effects);
	}
}
