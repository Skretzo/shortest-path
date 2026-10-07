package shortestpath.pathfinder;

import java.util.Arrays;

import static net.runelite.api.Constants.REGION_SIZE;
import shortestpath.WorldPointUtil;

/**
 * One sailing search's moves, targets and when it counts as arriving (see {@link Pathfinder}).
 * <p>
 * Moves land whole tiles away, often several at a time, so a boat can't stop on every tile: at speed 2 every move
 * goes an even number of tiles across and up together, so half of all tiles are out of reach. The path therefore
 * arrives once the boat is on a target or next to it.
 * <p>
 * It also remembers the cheapest cost each tile has been queued at, so that a tile is only queued again when a route
 * reaches it for less: a dearer one would only be dequeued once the tile is done.
 */
final class SailingSearch
{
	private static final int REGION_SHIFT = Integer.numberOfTrailingZeros(REGION_SIZE);
	private static final int REGION_MASK = REGION_SIZE - 1;
	private static final int PLANES = 4;
	// How far from a target the boat's tile can be while arriving, in tiles: next to it diagonally
	private static final double ARRIVAL_REACH = Math.sqrt(2);

	final SailingMoves moves;
	private final int[] targets;
	private final SplitFlagMap.RegionExtent extent;
	// The cheapest cost each tile has been queued at, without and with a bank visited, as one array per region and plane,
	// made as the search reaches them
	private final int[][][] queuedCosts = new int[2][][];

	SailingSearch(SailingMoves moves, int[] targets)
	{
		this.moves = moves;
		this.targets = targets;
		extent = SplitFlagMap.getRegionExtents();
		int regions = (extent.getWidth() + 1) * (extent.getHeight() + 1) * PLANES;
		queuedCosts[0] = new int[regions][];
		queuedCosts[1] = new int[regions][];
	}

	/**
	 * Whether the boat, on the tile at {@code packedPosition}, has arrived: it's on a target or next to it.
	 */
	boolean hasArrived(int packedPosition)
	{
		for (int target : targets)
		{
			if (WorldPointUtil.distanceBetween(target, packedPosition) <= 1)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * A lower bound on the distance left from tile (x, y), in the units of {@link SailingMoves#length}: the straight-line
	 * distance to the nearest target, less how far from it the boat can arrive. Rounded down, so it never overestimates.
	 */
	int estimate(int x, int y)
	{
		long best = Long.MAX_VALUE;
		for (int target : targets)
		{
			long dx = WorldPointUtil.unpackWorldX(target) - x;
			long dy = WorldPointUtil.unpackWorldY(target) - y;
			best = Math.min(best, dx * dx + dy * dy);
		}
		if (best == Long.MAX_VALUE)
		{
			return 0;
		}
		double tilesLeft = Math.max(0, Math.sqrt(best) - ARRIVAL_REACH);
		return (int) (SailingMoves.LENGTH_UNITS_PER_TILE * tilesLeft);
	}

	/**
	 * The cheapest cost tile (x, y, z) has been queued at so far, or {@link Integer#MAX_VALUE} if it hasn't been; 0 if it's
	 * off the map, so it never is.
	 */
	int queuedCost(int x, int y, int z, boolean bankVisited)
	{
		int region = regionIndex(x, y, z);
		if (region < 0)
		{
			return 0;
		}
		int[] costs = queuedCosts[bankVisited ? 1 : 0][region];
		return costs == null ? Integer.MAX_VALUE : costs[tileIndex(x, y)];
	}

	/**
	 * Notes that tile (x, y, z) is queued at {@code cost}, cheaper than before (see {@link #queuedCost}).
	 */
	void setQueuedCost(int x, int y, int z, boolean bankVisited, int cost)
	{
		int region = regionIndex(x, y, z);
		int[][] byRegion = queuedCosts[bankVisited ? 1 : 0];
		if (byRegion[region] == null)
		{
			byRegion[region] = new int[REGION_SIZE * REGION_SIZE];
			Arrays.fill(byRegion[region], Integer.MAX_VALUE);
		}
		byRegion[region][tileIndex(x, y)] = cost;
	}

	/**
	 * Lets go of the costs noted per tile, once the search is done.
	 */
	void release()
	{
		Arrays.fill(queuedCosts[0], null);
		Arrays.fill(queuedCosts[1], null);
	}

	// Index of the region and plane holding tile (x, y, z), or -1 if it's off the map
	private int regionIndex(int x, int y, int z)
	{
		int regionX = (x >> REGION_SHIFT) - extent.getMinX();
		int regionY = (y >> REGION_SHIFT) - extent.getMinY();
		if (regionX < 0 || regionY < 0 || regionX > extent.getWidth() || regionY > extent.getHeight() || z < 0 || z >= PLANES)
		{
			return -1;
		}
		return (regionX + regionY * (extent.getWidth() + 1)) * PLANES + z;
	}

	private static int tileIndex(int x, int y)
	{
		return ((y & REGION_MASK) << REGION_SHIFT) | (x & REGION_MASK);
	}
}
