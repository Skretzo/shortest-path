package shortestpath.items;

import java.util.Objects;
import java.util.Set;

import lombok.Getter;

import shortestpath.settings.Effect;

/**
 * An immutable fact describing a single item-state change: the input that
 * changed (keyed as {@code "container:<id>"} or {@code "varbit:<id>"}) and the
 * {@link Effect}s it triggers. Produced by {@link ItemStateService}; the shell
 * reads the effect set to decide which follow-up actions to run rather than
 * re-deriving them from the event.
 */
@Getter
public final class ItemChange
{
	private final String key;
	private final Set<Effect> effects;

	ItemChange(String key, Set<Effect> effects)
	{
		this.key = Objects.requireNonNull(key);
		this.effects = Set.copyOf(effects);
	}
}
