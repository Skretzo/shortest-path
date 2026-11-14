package shortestpath.overlay;

import java.util.List;
import javax.inject.Singleton;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.poh.PohService;

/**
 * Which house visit on the route the player is on, so the house overlays
 * pick the right one when a route passes through the house more than once.
 * It counts the house visits the player has already passed, which survives
 * the route being found again from its old start while the player is
 * inside. Updated once per game tick.
 */
@Singleton
public class HouseRouteProgress
{
	private List<PathStep> path;
	/** House visits on the route before the player's last place outside a house. */
	private int visitsPassed;

	/** Notes the player's place on {@code path}. */
	public void tick(List<PathStep> path, int playerLocation, int recalculateDistance)
	{
		if (path != this.path)
		{
			this.path = path;
			// A route found from where the player stands inside a house starts with that visit.
			if (path != null && !path.isEmpty() && inHouse(path.get(0).getPackedPosition())
				&& inHouse(playerLocation))
			{
				visitsPassed = 0;
			}
		}
		if (path == null || playerLocation == WorldPointUtil.UNDEFINED || inHouse(playerLocation))
		{
			return;
		}
		int index = AbstractHighlightOverlay.playerPathIndex(path, playerLocation, recalculateDistance);
		if (index >= 0)
		{
			visitsPassed = visitsBefore(path, index);
		}
	}

	/** The index of the house visit the player is on (or heading to), or -1. */
	public int houseVisit(List<PathStep> path)
	{
		return visitStart(path, path == this.path ? visitsPassed : 0);
	}

	public void reset()
	{
		path = null;
		visitsPassed = 0;
	}

	/** How many house visits start before {@code index}. */
	static int visitsBefore(List<PathStep> path, int index)
	{
		int visits = 0;
		boolean inside = false;
		for (int i = 0; i < Math.min(index + 1, path.size()); i++)
		{
			boolean house = inHouse(path.get(i).getPackedPosition());
			if (house && !inside && i < index)
			{
				visits++;
			}
			inside = house;
		}
		return visits;
	}

	/** The first step of house visit number {@code visit} (0-based), or of the last visit; -1 without any. */
	static int visitStart(List<PathStep> path, int visit)
	{
		int seen = -1;
		int last = -1;
		boolean inside = false;
		for (int i = 0; i < path.size(); i++)
		{
			boolean house = inHouse(path.get(i).getPackedPosition());
			if (house && !inside)
			{
				seen++;
				last = i;
				if (seen == visit)
				{
					return i;
				}
			}
			inside = house;
		}
		return last;
	}

	private static boolean inHouse(int point)
	{
		return point != WorldPointUtil.UNDEFINED
			&& PohService.isInsidePoh(WorldPointUtil.unpackWorldX(point), WorldPointUtil.unpackWorldY(point));
	}
}
