package shortestpath.spirittree;

import java.util.Objects;
import java.util.Set;

import lombok.Getter;

import shortestpath.settings.Effect;

/**
 * An immutable fact describing a single spirit-tree state change: the input
 * that changed (keyed as {@code "varbit:<patch>"} for an in-region sample,
 * {@code "menu"} for a travel-menu parse, or {@code "profile"} for an
 * RSProfile reload) and the {@link Effect}s it triggers. Produced by
 * {@link SpiritTreeService}; the shell reads the effect set to decide which
 * follow-up actions to run rather than re-deriving them from the event.
 *
 * <p>Spirit trees only ever emit {@link Effect#ROUTE_INVALIDATING}: a changed
 * tree set can invalidate a running path but never alters item or quest
 * eligibility, so {@code ELIGIBILITY_STALE} is never present.
 */
@Getter
public final class TreeChange
{
	private final String key;
	private final Set<Effect> effects;

	TreeChange(String key, Set<Effect> effects)
	{
		this.key = Objects.requireNonNull(key);
		this.effects = Set.copyOf(effects);
	}
}
