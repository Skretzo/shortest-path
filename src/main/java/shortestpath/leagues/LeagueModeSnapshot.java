package shortestpath.leagues;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable view of the league facts a {@link LeagueModeState} held at one
 * refresh. {@link LeagueModeState} is the live, per-config holder that
 * {@code refresh()} rewrites on every world hop or login; this snapshot is
 * what a per-refresh {@code RequirementContext} retains, so a requirement
 * chain keeps answering from the facts of the refresh that built it even if
 * a later refresh has already replaced the live state.
 *
 * <p>The read contract mirrors {@link LeagueModeState}'s: outside seasonal
 * mode every region is considered unlocked and no region is blocked, so
 * normal pathfinding is unaffected.
 */
public final class LeagueModeSnapshot
{
	/**
	 * The default snapshot: non-seasonal, not Deadman, no unlocks — the same
	 * facts a freshly constructed or null-source-refreshed
	 * {@link LeagueModeState} reports.
	 */
	public static final LeagueModeSnapshot NON_SEASONAL = new LeagueModeSnapshot(false, false, Set.of());

	private final boolean seasonal;
	private final boolean deadman;
	private final Set<LeagueRegion> unlockedRegions;

	public LeagueModeSnapshot(boolean seasonal, boolean deadman, Set<LeagueRegion> unlockedRegions)
	{
		this.seasonal = seasonal;
		this.deadman = deadman;
		this.unlockedRegions = unlockedRegions == null || unlockedRegions.isEmpty()
			? Set.of()
			: Collections.unmodifiableSet(EnumSet.copyOf(unlockedRegions));
	}

	public boolean isSeasonal()
	{
		return seasonal;
	}

	public boolean isDeadman()
	{
		return deadman;
	}

	/**
	 * Whether the supplied region is currently traversable. Outside of
	 * seasonal mode every region is considered unlocked.
	 */
	public boolean isUnlocked(LeagueRegion region)
	{
		if (region == null)
		{
			return true;
		}
		if (region.isAlwaysUnlocked())
		{
			return true;
		}
		if (!seasonal)
		{
			return true;
		}
		if (region.isAlwaysBlocked())
		{
			return false;
		}
		return unlockedRegions.contains(region);
	}

	/**
	 * Whether the supplied tile is in the always-blocked region while the
	 * player is on a seasonal world. Returns {@code false} on non-seasonal
	 * worlds so normal pathfinding is unaffected.
	 */
	public boolean isInBlockedRegion(int packedPoint)
	{
		if (!seasonal)
		{
			return false;
		}
		return LeagueRegionChecker.getRegion(packedPoint).isAlwaysBlocked();
	}
}
