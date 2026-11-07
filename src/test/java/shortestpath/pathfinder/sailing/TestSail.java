package shortestpath.pathfinder.sailing;

import java.util.ArrayList;
import java.util.List;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;

/** Sails one leg in a test, as a route at sea is drawn: from a boat tile until its hull covers a target. */
final class TestSail
{
	// How far round the start and the target the way may go, in tiles: room for the tests' detours
	private static final int MARGIN = 96;

	private TestSail()
	{
	}

	/**
	 * The boat's tile at the start and at the end of each move, from {@code start} facing {@code heading} (-1 unknown)
	 * until its hull covers {@code target}, by the fewest ticks and then turns within {@link #MARGIN} tiles of the box
	 * round both, or {@code null} if there is no way there.
	 */
	static List<PathStep> sail(CollisionMap map, SailingMoves moves, BoatHull hull, int heading, int start, int target)
	{
		int x0 = Math.min(WorldPointUtil.unpackWorldX(start), WorldPointUtil.unpackWorldX(target)) - MARGIN;
		int x1 = Math.max(WorldPointUtil.unpackWorldX(start), WorldPointUtil.unpackWorldX(target)) + MARGIN;
		int y0 = Math.min(WorldPointUtil.unpackWorldY(start), WorldPointUtil.unpackWorldY(target)) - MARGIN;
		int y1 = Math.max(WorldPointUtil.unpackWorldY(start), WorldPointUtil.unpackWorldY(target)) + MARGIN;
		// No estimate: every tile in the box is 0 ticks from the end, as far as the search knows
		TileInts estimates = new TileInts(map.regionExtent(), Integer.MAX_VALUE, Integer.MAX_VALUE);
		List<Integer> box = new ArrayList<>();
		for (int x = x0; x <= x1; x++)
		{
			for (int y = y0; y <= y1; y++)
			{
				int tile = WorldPointUtil.packWorldPoint(x, y, 0);
				estimates.set(tile, 0);
				box.add(tile);
			}
		}
		HeadingSearch.Sailed sailed = HeadingSearch.sail(map, moves, hull, tile -> true, SeaGate.NONE, new int[] {start},
			heading, estimates, box, (tile, facing) -> hull.coversTile(target, tile, facing), true, () -> false);
		if (sailed == null) return null;
		List<PathStep> path = new ArrayList<>();
		for (int tile : sailed.tiles) path.add(PathStep.sailing(tile, BankVisitState.CARRIED));
		return path;
	}
}
