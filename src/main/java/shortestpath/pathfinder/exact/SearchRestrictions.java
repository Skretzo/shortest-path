package shortestpath.pathfinder.exact;

import java.util.function.IntPredicate;
import shortestpath.pathfinder.WildernessChecker;

/**
 * Per-search positional restrictions applied on top of the prepared graph, mirroring the gates
 * the legacy pathfinder checks while relaxing edges ({@code avoidWilderness} and the league's
 * always-blocked region).
 *
 * <p>They are per-search rather than part of the account identity: the underlying setting can
 * change on every config refresh, and enforcing them removes edges instead of adding any, so
 * the prepared target's heuristic only ever under-estimates the restricted graph's distances and
 * stays admissible. Sessions may therefore reuse prepared accounts, targets and heuristics
 * across searches regardless of these settings.
 */
public final class SearchRestrictions
{
	private static final SearchRestrictions NONE = new SearchRestrictions(false, null);

	/** Avoid stepping or teleporting into the wilderness from outside it. */
	private final boolean avoidWilderness;
	/** Whether a tile lies in the league's always-blocked region, or {@code null} when the gate is off. */
	private final IntPredicate blockedRegion;

	private SearchRestrictions(boolean avoidWilderness, IntPredicate blockedRegion)
	{
		this.avoidWilderness = avoidWilderness;
		this.blockedRegion = blockedRegion;
	}

	/** No gates: every edge the graph offers may be relaxed. */
	public static SearchRestrictions none()
	{
		return NONE;
	}

	/**
	 * @param avoidWilderness bar steps and global teleports that enter the wilderness from
	 *                        outside it; the caller folds in legacy's exemption for a target in
	 *                        the wilderness
	 * @param blockedRegion   tests whether a tile lies in the league's always-blocked region, or
	 *                        {@code null} when the gate is off (a non-seasonal world, or a target
	 *                        inside the blocked region lifting the gate, as in legacy)
	 */
	public static SearchRestrictions of(boolean avoidWilderness, IntPredicate blockedRegion)
	{
		return avoidWilderness || blockedRegion != null ? new SearchRestrictions(avoidWilderness, blockedRegion)
			: NONE;
	}

	/** Whether any gate is active; when false, relaxing an edge never asks for a tile's status. */
	boolean anyGate()
	{
		return avoidWilderness || blockedRegion != null;
	}

	boolean avoidsWilderness()
	{
		return avoidWilderness;
	}

	/** Whether {@code tile} is inside the league's always-blocked region; false when the gate is off. */
	boolean inBlockedRegion(int tile)
	{
		return blockedRegion != null && blockedRegion.test(tile);
	}

	/**
	 * Whether a walking step between adjacent tiles may be relaxed, including the step onto a
	 * blocked transport origin. Like legacy, crossing into the wilderness or the blocked region
	 * is barred while moving inside either area, or out of it, is allowed.
	 */
	public boolean stepAllowed(int fromTile, int toTile)
	{
		if (!anyGate())
		{
			return true;
		}
		return stepAllowed(WildernessChecker.isInWilderness(fromTile), WildernessChecker.isInWilderness(toTile),
			inBlockedRegion(fromTile), inBlockedRegion(toTile));
	}

	boolean stepAllowed(boolean fromWilderness, boolean toWilderness, boolean fromBlocked, boolean toBlocked)
	{
		if (avoidWilderness && !fromWilderness && toWilderness)
		{
			return false;
		}
		return blockedRegion == null || fromBlocked || !toBlocked;
	}

	/**
	 * Whether a global teleport cast with {@code capability} may land on {@code destination}.
	 * {@link TeleportCapability#ALL} means the cast happens outside the wilderness, so a
	 * wilderness landing is barred while avoiding it; a cast from inside the wilderness
	 * (any {@code OVER_*} capability) may land inside it, as in legacy.
	 */
	boolean globalAllowed(TeleportCapability capability, int destination)
	{
		return !avoidWilderness || capability != TeleportCapability.ALL
			|| !WildernessChecker.isInWilderness(destination);
	}
}
