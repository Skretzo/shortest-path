package shortestpath.transport.requirement;

/**
 * Player-declared unlock states that the game does not expose to the client,
 * referenced by {@code UNLOCK_<NAME>=1} tokens in transport Items cells.
 * An unlock branch satisfies a requirement by declaration alone: it never
 * contributes items and is never covered by inventory, staff or offhand ids.
 * An unlock alternative inside an OR group acts as a relief
 * ({@code AXE=1|UNLOCK_CANOE_AXE=1}), while an ANDed pure-unlock requirement
 * acts as a gate ({@code 13393=1&UNLOCK_XERICS_HONOUR=1}).
 */
public enum Unlock
{
	/**
	 * An axe stored at a canoe station, usable instead of a carried axe.
	 */
	CANOE_AXE,

	/**
	 * Dragontooth Island free passage, paying no ecto-token toll.
	 */
	DRAGONTOOTH,

	/**
	 * Xeric's Honour, unlocking the talisman teleports.
	 */
	XERICS_HONOUR,
	;

	private static final String TOKEN_PREFIX = "UNLOCK_";

	/**
	 * Resolves an {@code UNLOCK_<NAME>} token to its unlock, or {@code null}
	 * when the token does not name a known unlock. Callers that accept the
	 * token class must fail loudly on {@code null} so that a typo'd unlock
	 * name can never silently drop a requirement.
	 */
	public static Unlock fromName(String name)
	{
		if (name == null || !name.startsWith(TOKEN_PREFIX))
		{
			return null;
		}
		try
		{
			return Unlock.valueOf(name.substring(TOKEN_PREFIX.length()));
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
