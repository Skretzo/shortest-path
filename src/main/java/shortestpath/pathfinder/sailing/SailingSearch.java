package shortestpath.pathfinder.sailing;

import java.util.Arrays;

import static net.runelite.api.Constants.REGION_SIZE;
import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.NodeGraph;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.pathfinder.VisitedTiles;

/**
 * One sailing search's moves, boat, targets and when it counts as arriving (see {@link SailingPathfinder}).
 * <p>
 * With the boat's hull, the path arrives once a target tile is inside the hull, where the path ends and facing the way
 * it sailed there. Without one, moves land whole tiles away, often several at a time, so a boat can't stop on every
 * tile: at speed 2 every move goes an even number of tiles across and up together, so half of all tiles are out of
 * reach. The path then arrives once the boat is on a target or next to it.
 * <p>
 * It also remembers the cheapest cost each tile has been queued at, so that a tile is only queued again when a route
 * reaches it for less — or, with a hull, when a costlier move would arrive on a heading none has tried: a dearer one
 * would only be dequeued once the tile is done.
 */
final class SailingSearch
{
	private static final int REGION_SHIFT = Integer.numberOfTrailingZeros(REGION_SIZE);
	private static final int REGION_MASK = REGION_SIZE - 1;
	private static final int PLANES = 4;
	private static final int HEADINGS = 16;
	// Sailing moves cost the distance they sail (SailingMoves.length), plus a thousandth of a tile per change of
	// heading, so that of the routes that are equally short the one with the fewest, longest legs wins.
	private static final int SAILING_COST_PER_TURN = 1;

	final SailingMoves moves;
	/** The boat's hull, or {@code null} to keep only the boat's centre clear. */
	final BoatHull hull;
	private final int[] targets;
	// The targets plus their fallback tiles: the search can also end by landing on one of them
	private final int[] goals;
	// The heading the boat is facing where the route starts, or -1 when that isn't known: the start node has no
	// move to face, so without it a target under the hull counts as arrived facing any heading — even one the
	// boat, berthed, isn't pointing
	private final int initialHeading;
	// How far from a target the boat's tile can be while arriving, in tiles: next to it diagonally, or with a hull, as
	// far as the hull reaches plus half a tile's diagonal
	private final double arrivalReach;
	private final SplitFlagMap.RegionExtent extent;
	// The cheapest cost each tile has been queued at, without and with a bank visited, as one array per region and plane,
	// made as the search reaches them
	private final int[][][] queuedCosts = new int[2][][];
	// Which moves have already been queued into each tile at a cost that doesn't beat the tile's cheapest, as a bitmask.
	// A tile's cheapest route may face a way its hull doesn't cover a target in, so a costlier move that would arrive is
	// still queued — once per move
	private final int[][][] queuedArrivals = new int[2][][];

	SailingSearch(SailingMoves moves, BoatHull hull, int[] targets, int[] goals)
	{
		this(moves, hull, targets, goals, WorldPointUtil.UNDEFINED);
	}

	SailingSearch(SailingMoves moves, BoatHull hull, int[] targets, int[] goals, int initialHeading)
	{
		this.moves = moves;
		this.hull = hull;
		this.targets = targets;
		this.goals = goals;
		this.initialHeading = initialHeading;
		arrivalReach = hull == null ? Math.sqrt(2) : hull.reach() + Math.sqrt(2) / 2;
		extent = SplitFlagMap.getRegionExtents();
		int regions = (extent.getWidth() + 1) * (extent.getHeight() + 1) * PLANES;
		queuedCosts[0] = new int[regions][];
		queuedCosts[1] = new int[regions][];
		queuedArrivals[0] = new int[regions][];
		queuedArrivals[1] = new int[regions][];
	}

	/**
	 * The heading the boat faces on {@code node}: the way the move that reached it sailed, or the heading it was
	 * facing where the route began on the start node, which no move reached. -1 means unknown, which lets
	 * {@link #arrivedTarget} try every heading.
	 */
	int arrivalHeading(NodeGraph graph, int node)
	{
		int heading = sailingArrivalHeading(graph, node, moves);
		return heading < 0 ? initialHeading : heading;
	}

	// The heading of the sailing move that reached node, or -1 if it wasn't reached by one (such as the search's
	// start, which faces the boat's own heading instead — see SailingSearch.arrivalHeading).
	// The move is stored on the node rather than recovered from its displacement, which different headings can share
	// at low speeds
	private static int sailingArrivalHeading(NodeGraph graph, int node, SailingMoves moves)
	{
		int move = graph.moveIndex(node);
		return move < 0 ? -1 : moves.heading(move);
	}

	/**
	 * The target the boat, on the tile at {@code packedPosition} and facing {@code heading}, has arrived at: its hull
	 * covers it, facing any heading if {@code heading} is -1 (unknown); or without a hull, it's on a target or next to
	 * it. {@link WorldPointUtil#UNDEFINED} if it hasn't arrived.
	 */
	int arrivedTarget(int packedPosition, int heading)
	{
		for (int target : targets)
		{
			if (hull == null)
			{
				if (WorldPointUtil.distanceBetween(target, packedPosition) <= 1)
				{
					return target;
				}
				continue;
			}
			if (WorldPointUtil.unpackWorldPlane(target) != WorldPointUtil.unpackWorldPlane(packedPosition))
			{
				continue;
			}
			int dx = WorldPointUtil.unpackWorldX(target) - WorldPointUtil.unpackWorldX(packedPosition);
			int dy = WorldPointUtil.unpackWorldY(target) - WorldPointUtil.unpackWorldY(packedPosition);
			for (int facing = heading < 0 ? 0 : heading; facing < (heading < 0 ? HEADINGS : heading + 1); facing++)
			{
				if (hull.covers(facing, dx, dy))
				{
					return target;
				}
			}
		}
		return WorldPointUtil.UNDEFINED;
	}

	/**
	 * A lower bound on the distance left from tile (x, y), in the units of {@link SailingMoves#length}: the nearest
	 * straight-line distance to a goal tile — which the boat has to land on — or to a target less how far from it the
	 * boat can arrive. Rounded down, so it never overestimates.
	 */
	int estimate(int x, int y)
	{
		// Landing on a goal tile ends the search exactly; a target only needs the boat within
		// arrival reach of it
		double tilesLeft = Double.MAX_VALUE;
		for (int goal : goals)
		{
			long dx = WorldPointUtil.unpackWorldX(goal) - x;
			long dy = WorldPointUtil.unpackWorldY(goal) - y;
			tilesLeft = Math.min(tilesLeft, Math.sqrt(dx * dx + dy * dy));
		}
		for (int target : targets)
		{
			long dx = WorldPointUtil.unpackWorldX(target) - x;
			long dy = WorldPointUtil.unpackWorldY(target) - y;
			tilesLeft = Math.min(tilesLeft, Math.sqrt(dx * dx + dy * dy) - arrivalReach);
		}
		if (tilesLeft == Double.MAX_VALUE)
		{
			return 0;
		}
		return (int) (SailingMoves.LENGTH_UNITS_PER_TILE * Math.max(0, tilesLeft));
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
	 * Notes that tile (x, y, z) is queued at {@code cost}; only lowers the noted cost, so it stays the
	 * cheapest the tile has been queued at (see {@link #queuedCost}).
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
		int index = tileIndex(x, y);
		byRegion[region][index] = Math.min(byRegion[region][index], cost);
	}

	/**
	 * Lets a move reaching tile (x, y, z) at a cost that doesn't beat its cheapest queue anyway when the
	 * boat would arrive on the move's heading — the tile's cheapest route may face a way its hull doesn't
	 * cover a target in. Marks the move queued, so each move is let through once. Only for hull searches:
	 * without a hull arrival doesn't depend on facing.
	 *
	 * @return whether the move should be queued despite its cost
	 */
	boolean queueArrival(int packedPosition, int move, int x, int y, int z, boolean bankVisited)
	{
		if (hull == null || arrivedTarget(packedPosition, moves.heading(move)) == WorldPointUtil.UNDEFINED)
		{
			return false;
		}
		int region = regionIndex(x, y, z);
		if (region < 0)
		{
			return false;
		}
		int[][] byRegion = queuedArrivals[bankVisited ? 1 : 0];
		int[] arrivals = byRegion[region];
		if (arrivals == null)
		{
			arrivals = new int[REGION_SIZE * REGION_SIZE];
			byRegion[region] = arrivals;
		}
		int index = tileIndex(x, y);
		int bit = 1 << move;
		if ((arrivals[index] & bit) != 0)
		{
			return false;
		}
		arrivals[index] |= bit;
		return true;
	}

	/**
	 * Lets go of the costs noted per tile, and the hull's caches, once the search is done.
	 */
	void release()
	{
		Arrays.fill(queuedCosts[0], null);
		Arrays.fill(queuedCosts[1], null);
		Arrays.fill(queuedArrivals[0], null);
		Arrays.fill(queuedArrivals[1], null);
		if (hull != null)
		{
			hull.release();
		}
	}

	// Sailing moves are different lengths, so they are queued by cost on the pending heap like transports rather than
	// the FIFO boundary queue, which is only ordered for unit-cost walking edges: each costs the distance it sails, so
	// the search finds the shortest route rather than the quickest. A tile is only queued again if a route reaches it
	// for less, since a dearer one would only be dequeued once the tile is done — or, with a hull, if a dearer move
	// would arrive on a heading none has tried, since the cheapest route's facing may not cover a target. The cost
	// check comes first, which costs less than the line or hull check. With a hull, the whole boat must fit along the
	// move, which also keeps its centre clear.
	void addNeighbors(CollisionMap map, PrimitiveIntList out, int node, int x, int y, int z, boolean bankVisited,
		VisitedTiles visited, NodeGraph graph)
	{
		// The boat faces the way it sailed into the node — or, on the start node, the way it's pointing, so
		// carrying straight on isn't a turn but putting about first is
		final int arrivalHeading = arrivalHeading(graph, node);
		// The first move gets the hull's berthed-tile escape: a boat can sit over tiles it then sails off
		final boolean firstMove = graph.moveIndex(node) < 0;
		final int costSoFar = graph.cost(node);
		for (int i = 0; i < moves.size(); i++)
		{
			final int dx = moves.dx(i);
			final int dy = moves.dy(i);
			final int neighborPacked = WorldPointUtil.packWorldPoint(x + dx, y + dy, z);
			final int cost = moves.length(i) + (arrivalHeading >= 0 && arrivalHeading != moves.heading(i) ? SAILING_COST_PER_TURN : 0);
			if (visited.get(neighborPacked, bankVisited)
				|| (costSoFar + cost >= queuedCost(x + dx, y + dy, z, bankVisited)
					&& !queueArrival(neighborPacked, i, x + dx, y + dy, z, bankVisited))
				|| !(hull == null ? canSailLine(map, x, y, z, dx, dy)
					: hull.canMove(map, x, y, z, moves.heading(i), dx, dy, firstMove)))
			{
				continue;
			}
			out.add(graph.createWeightedTile(neighborPacked, node, cost, estimate(x + dx, y + dy), bankVisited, i));
		}
	}

	/**
	 * Whether a straight sailing move from tile (x, y, z) by (dx, dy) tiles only crosses open tiles: every tile the
	 * line between the tile centres passes through must be one walking step from the previous one, so a move never
	 * passes over a blocked tile (such as the blocked strip along coastlines). Where the line passes exactly through a
	 * tile corner, both ways around the corner must be open.
	 */
	public static boolean canSailLine(CollisionMap map, int x, int y, int z, int dx, int dy)
	{
		final int nx = Math.abs(dx);
		final int ny = Math.abs(dy);
		final int sx = Integer.signum(dx);
		final int sy = Integer.signum(dy);
		// From a blocked tile, such as one a moored boat sits on, any open tile next to it can be entered, as when
		// walking. Every tile after the first is entered through an open side, so it isn't blocked either.
		boolean fromBlocked = map.isBlocked(x, y, z);
		int cx = x;
		int cy = y;
		int ix = 0;
		int iy = 0;
		while (ix < nx || iy < ny)
		{
			// Compares where the line crosses the next vertical and horizontal tile edges
			long decision = (long) (1 + 2 * ix) * ny - (long) (1 + 2 * iy) * nx;
			if (decision == 0)
			{
				if (!canCross(map, cx, cy, z, sx, 0, fromBlocked) || !canCross(map, cx + sx, cy, z, 0, sy, false)
					|| !canCross(map, cx, cy, z, 0, sy, fromBlocked) || !canCross(map, cx, cy + sy, z, sx, 0, false))
				{
					return false;
				}
				cx += sx;
				cy += sy;
				ix++;
				iy++;
			}
			else if (decision < 0)
			{
				if (!canCross(map, cx, cy, z, sx, 0, fromBlocked))
				{
					return false;
				}
				cx += sx;
				ix++;
			}
			else
			{
				if (!canCross(map, cx, cy, z, 0, sy, fromBlocked))
				{
					return false;
				}
				cy += sy;
				iy++;
			}
			fromBlocked = false;
		}
		return true;
	}

	// Whether a boat can cross from tile (x, y, z) into the tile next to it (ex, ey) away, one of which is 0: through
	// the open side between them, or from a blocked tile into any open one
	private static boolean canCross(CollisionMap map, int x, int y, int z, int ex, int ey, boolean fromBlocked)
	{
		if (fromBlocked)
		{
			return !map.isBlocked(x + ex, y + ey, z);
		}
		if (ey == 0)
		{
			return ex < 0 ? map.w(x, y, z) : map.e(x, y, z);
		}
		return ey < 0 ? map.s(x, y, z) : map.n(x, y, z);
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
