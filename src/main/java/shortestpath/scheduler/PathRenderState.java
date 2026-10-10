package shortestpath.scheduler;

import java.awt.Color;

import javax.inject.Inject;
import javax.inject.Singleton;

import shortestpath.pathfinder.ActiveSearch;
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
}
