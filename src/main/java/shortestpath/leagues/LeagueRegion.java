package shortestpath.leagues;

/**
 * Identifies the Demonic Pacts League area that a tile belongs to.
 *
 * <p>
 * The first three values are universally accessible regardless of player
 * progression: {@link #VARLAMORE} is the starting region, {@link #KARAMJA}
 * unlocks for free with the first area unlock, and {@link #NEUTRAL} captures
 * areas reachable from anywhere (Death's office, POH, Zanaris, the Abyssal
 * Area, random events, instances, dynamic regions, tutorial island).
 * </p>
 *
 * <p>
 * {@link #MISTHALIN} is permanently inaccessible during the league. The
 * remaining regions are unlockable through task completion.
 * </p>
 *
 * <p>
 * Region geometry is sourced from {@code leagues/regions.json}: a mapping from
 * OSRS map region id to one of these enum names. Tiles with no mapping fall
 * back to {@link #NEUTRAL}.
 * </p>
 */
public enum LeagueRegion
{
	VARLAMORE,
	KARAMJA,
	ASGARNIA,
	KANDARIN,
	FREMENNIK,
	KOUREND,
	WILDERNESS,
	MORYTANIA,
	DESERT,
	TIRANNWN,
	MISTHALIN,
	NEUTRAL;

	/**
	 * Whether this region is reachable regardless of which area unlocks the
	 * player has chosen. Always-unlocked regions are filtered through but
	 * never gate transports.
	 */
	public boolean isAlwaysUnlocked()
	{
		return this == VARLAMORE || this == KARAMJA || this == NEUTRAL;
	}

	/**
	 * Whether this region is permanently blocked during the league. Tiles in
	 * always-blocked regions reject both walking and transport traversal
	 * (see {@code LeagueRegionChecker} and {@code PathfinderConfig.useTransport}).
	 */
	public boolean isAlwaysBlocked()
	{
		return this == MISTHALIN;
	}
}
