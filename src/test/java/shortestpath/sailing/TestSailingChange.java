package shortestpath.sailing;

import java.util.Set;

import shortestpath.settings.Effect;

/**
 * Same-package fixture: the fact's constructor is package-private so only
 * producer paths can mint facts, and the refresh-coordinator test needs to
 * hand the coordinator facts without widening that visibility.
 */
public final class TestSailingChange
{
	private TestSailingChange()
	{
	}

	public static SailingChange of(String key, Set<Effect> effects)
	{
		return new SailingChange(key, effects);
	}
}
