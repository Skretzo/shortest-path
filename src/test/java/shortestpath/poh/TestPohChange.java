package shortestpath.poh;

import java.util.Set;

import shortestpath.settings.Effect;

/**
 * Same-package fixture: the fact's constructor is package-private so only
 * producer paths can mint facts, and the refresh-coordinator test needs to
 * hand the coordinator facts without widening that visibility.
 */
public final class TestPohChange
{
	private TestPohChange()
	{
	}

	public static PohChange of(String key, Set<Effect> effects)
	{
		return new PohChange(key, effects);
	}
}
