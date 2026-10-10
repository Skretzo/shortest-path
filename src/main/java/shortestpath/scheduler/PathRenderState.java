package shortestpath.scheduler;

import java.awt.Color;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.sailing.SailingService;
import shortestpath.settings.Settings;

/**
 * The render-side read seam over the scheduler's published state: which
 * search is drawing, whether it sails, where the boat's in-tile pivot sits,
 * and whether the player is aboard. Read-only — every value is a volatile
 * field or a published immutable snapshot, so a per-frame read on the render
 * thread never blocks and never observes a torn mix. This is the overlays'
 * presentation contract; it currently carries the sailing surface.
 */
@Singleton
public final class PathRenderState
{
	/**
	 * The colour of the path while on a boat, which sails the boat's
	 * headings. Moved here off the plugin shell; the RGB is unchanged.
	 */
	public static final Color SAILING_PATH_COLOUR = new Color(0, 120, 255);

	private final PathScheduler scheduler;
	private final SailingService sailing;
	private final Settings settings;

	@Inject
	public PathRenderState(PathScheduler scheduler, SailingService sailing, Settings settings)
	{
		this.scheduler = scheduler;
		this.sailing = sailing;
		this.settings = settings;
	}

	/**
	 * Whether the active search sails a boat's headings rather than walking
	 * (see {@link ActiveSearch#isSailing}), so it has a point at the end of
	 * each move and is drawn as lines along its headings.
	 */
	public boolean isSailing()
	{
		ActiveSearch search = scheduler.getActiveSearch();
		return search != null && search.isSailing();
	}

	/**
	 * The boat's in-tile pivot X for the current sailing dispatch, in local
	 * units signed from the tile centre. This is the value written when the
	 * sailing search was dispatched — it matches what that search drew.
	 */
	public int getSailingPivotX()
	{
		return scheduler.getSailingPivotX();
	}

	/**
	 * The boat's in-tile pivot Y for the current sailing dispatch, in local
	 * units signed from the tile centre. This is the value written when the
	 * sailing search was dispatched — it matches what that search drew.
	 */
	public int getSailingPivotY()
	{
		return scheduler.getSailingPivotY();
	}

	/**
	 * Whether the last published sailing snapshot reports the player aboard
	 * a boat — the read behind the disembark hint.
	 */
	public boolean isOnSailingBoat()
	{
		return sailing.getState().isAboard();
	}

	/**
	 * Whether the finished search's path counts as unreachable — the verdict
	 * behind the unreachable-path colour. A walking path measures the
	 * closest target's distance from the path end against the configured
	 * threshold; a sailing path trusts the search's own reached verdict,
	 * since the hull covering the target is what arrival means there.
	 */
	public boolean isPathUnreachable()
	{
		ActiveSearch pathfinder = scheduler.getActiveSearch();
		if (pathfinder == null || !pathfinder.isDone())
		{
			return false;
		}

		List<PathStep> path = pathfinder.getPath();
		if (path == null || pathfinder.getTargets().isEmpty())
		{
			return false;
		}

		// A sailing path arrives once the boat's hull covers the target, with the boat's own tile up to a boat length
		// from it, so the search says whether it got there
		if (pathfinder.isSailing())
		{
			PathfinderResult result = pathfinder.getResult();
			return result != null && !result.isReached();
		}

		// A finished search can produce an empty path when no goal tile could ever be
		// reached (e.g. a blocked target); measure the distance from the start then.
		int endPoint = path.isEmpty() ? pathfinder.getStart()
			: path.get(path.size() - 1).getPackedPosition();
		int closestTargetDistance = Integer.MAX_VALUE;
		for (int target : pathfinder.getTargets())
		{
			closestTargetDistance = Math.min(closestTargetDistance, WorldPointUtil.distanceBetween(target, endPoint));
		}

		return closestTargetDistance > settings.display().unreachableTargetDistance();
	}
}
