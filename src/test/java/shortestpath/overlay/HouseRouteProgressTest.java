package shortestpath.overlay;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.poh.PohService;

public class HouseRouteProgressTest
{
	private static final int HOUSE = WorldPointUtil.packWorldPoint(PohService.POH_LANDING_X, PohService.POH_LANDING_Y, 0);

	private static int out(int x)
	{
		return WorldPointUtil.packWorldPoint(3200 + x, 3200, 0);
	}

	/** Outside 0-1, house 2, outside 3-4 (the bank), house 5, outside 6. */
	private static List<PathStep> twoVisits()
	{
		List<PathStep> path = new ArrayList<>();
		for (int point : new int[]{out(0), out(1), HOUSE, out(10), out(11), HOUSE, out(20)})
		{
			path.add(new PathStep(point, null));
		}
		return path;
	}

	@Test
	public void picksTheVisitThePlayerIsOn()
	{
		List<PathStep> path = twoVisits();
		HouseRouteProgress progress = new HouseRouteProgress();
		progress.tick(path, out(0), 10);
		assertEquals(2, progress.houseVisit(path));
		// At the bank between the visits: the second visit is next.
		progress.tick(path, out(11), 10);
		assertEquals(5, progress.houseVisit(path));
		// Inside the house, the count stays.
		progress.tick(path, HOUSE, 10);
		assertEquals(5, progress.houseVisit(path));
	}

	@Test
	public void aRouteFoundAgainFromItsOldStartKeepsTheVisit()
	{
		List<PathStep> path = twoVisits();
		HouseRouteProgress progress = new HouseRouteProgress();
		progress.tick(path, out(11), 10);
		progress.tick(path, HOUSE, 10);
		List<PathStep> again = twoVisits();
		progress.tick(again, HOUSE, 10);
		assertEquals(5, progress.houseVisit(again));
	}

	@Test
	public void aRouteFoundFromInsideTheHouseStartsThere()
	{
		HouseRouteProgress progress = new HouseRouteProgress();
		progress.tick(twoVisits(), out(11), 10);
		List<PathStep> fromHouse = List.of(new PathStep(HOUSE, null), new PathStep(out(20), null));
		progress.tick(fromHouse, HOUSE, 10);
		assertEquals(0, progress.houseVisit(fromHouse));
	}

	@Test
	public void counting()
	{
		List<PathStep> path = twoVisits();
		assertEquals(0, HouseRouteProgress.visitsBefore(path, 1));
		assertEquals(1, HouseRouteProgress.visitsBefore(path, 4));
		assertEquals(2, HouseRouteProgress.visitStart(path, 0));
		assertEquals(5, HouseRouteProgress.visitStart(path, 1));
		assertEquals(5, HouseRouteProgress.visitStart(path, 4));
		assertEquals(-1, HouseRouteProgress.visitStart(List.of(new PathStep(out(0), null)), 0));
	}
}
