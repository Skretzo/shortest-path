package shortestpath.poh;

import java.util.Objects;
import java.util.Set;

import lombok.Getter;

import shortestpath.settings.Effect;

/**
 * An immutable fact describing a single portal-nexus keybind change: the
 * input that changed (keyed as {@code "dialog"} for a teleport-menu or
 * configuration-slot parse, {@code "dialogLine"} for one scripted line, or
 * {@code "profile"} for an RSProfile reload) and the {@link Effect}s it
 * triggers. Produced by {@link PohService}; the shell reads the effect set
 * to decide which follow-up actions to run rather than re-deriving them
 * from the event.
 *
 * <p>POH keybinds only ever emit {@link Effect#DISPLAY_ONLY}: the keybind
 * map feeds display formatting and never invalidates a route or alters
 * eligibility, so route-invalidation and eligibility effects are never
 * present.
 */
@Getter
public final class PohChange
{
	private final String key;
	private final Set<Effect> effects;

	PohChange(String key, Set<Effect> effects)
	{
		this.key = Objects.requireNonNull(key);
		this.effects = Set.copyOf(effects);
	}
}
