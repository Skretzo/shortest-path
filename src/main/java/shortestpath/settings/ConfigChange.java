package shortestpath.settings;

import java.util.Set;

import lombok.Getter;

/**
 * An immutable fact describing a single config change: the key that changed
 * and the {@link Effect}s it triggers. Produced by
 * {@link Settings#onConfigChanged}; the shell reads the effect set to decide
 * which follow-up actions to run rather than re-deriving them from the key.
 */
@Getter
public final class ConfigChange
{
	private final String key;
	private final Set<Effect> effects;

	ConfigChange(String key, Set<Effect> effects)
	{
		this.key = key;
		this.effects = Set.copyOf(effects);
	}
}
