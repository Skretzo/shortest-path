package shortestpath.pathfinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import shortestpath.WorldPointUtil;

/**
 * Resolves the requested targets into the tiles a search should terminate on.
 * A walkable target terminates on itself; a blocked target can never produce
 * walk-in edges, so it expands to the nearest fallback tiles within the
 * configured unreachable distance — every walkable tile in the square, or only
 * the tiles on the target's enclosed (interior) side when
 * collisionAwareBlockedTargets is on (issue #640).
 */
public final class TargetGoals
{
	// How many walking steps the enclosure check around a blocked target
	// takes. Must stay short: within a couple of steps a wall still separates
	// the two sides, while further out both sides connect around wall ends.
	private static final int ENCLOSURE_DEPTH = 3;
	// How far out from a blocked target to score walkable candidates. Tiles
	// in the innermost ring holding a sufficiently enclosed candidate become
	// goals, so this bounds the enclosure scan; deeper rings only contribute
	// transport destinations.
	private static final int ENCLOSURE_SCAN_RADIUS = 4;
	// Upper bound for the blocked-target expansion scan. The unreachable distance
	// config allows values up to 20000; scanning a (2r+1)^2 square per blocked
	// target must stay cheap since it runs per request on the client thread.
	private static final int MAX_GOAL_EXPANSION_RADIUS = 64;

	private final Set<Integer> goals;
	private final boolean viable;

	private TargetGoals(Set<Integer> goals, boolean viable)
	{
		this.goals = goals;
		this.viable = viable;
	}

	/**
	 * The termination set: the requested targets plus, for each blocked target,
	 * the fallback tiles it resolved to.
	 */
	public Set<Integer> goals()
	{
		return goals;
	}

	/**
	 * Whether any requested target could possibly terminate a search: non-blocked,
	 * a transport destination, or resolved to at least one walkable nearby tile.
	 * When false no goal can ever be visited, so the search can be skipped.
	 */
	public boolean hasViableGoal()
	{
		return viable;
	}

	public static TargetGoals resolve(PathfinderConfig config, int start, Set<Integer> targets)
	{
		final CollisionMap map = config.getMap();
		Set<Integer> resolvedGoals = targets;
		boolean viable = false;
		for (int target : targets)
		{
			final int x = WorldPointUtil.unpackWorldX(target);
			final int y = WorldPointUtil.unpackWorldY(target);
			final int z = WorldPointUtil.unpackWorldPlane(target);
			if (!map.isBlocked(x, y, z))
			{
				viable = true;
				continue;
			}
			// A transport landing on the target can still visit it even when the tile
			// is blocked for walking (e.g. fairy rings).
			if (config.isTransportDestination(target))
			{
				viable = true;
			}
			// The target tile is blocked so it can never be walked into. Route to the
			// nearest fallback tiles within the configured unreachable distance
			// instead: walkable tiles, and blocked tiles that a transport can land
			// on (a teleport that lands adjacent to the target still gets close
			// enough). Both modes scan expanding rings and keep only the innermost
			// ring that yields goals, so the path ends on the tiles closest to the
			// target rather than whichever radius tile is cheapest for the player.
			// The scan is bounded: the config allows huge distances and this runs
			// per pathfinding request on the client thread.
			final int radius = Math.min(config.getUnreachableTargetDistance(), MAX_GOAL_EXPANSION_RADIUS);
			// When collisionAwareBlockedTargets is on, walkable fallback goals are
			// limited to the target's open side, so the path cannot "reach the
			// target" from the wrong side of a wall. Transport destinations stay
			// goals in both modes.
			final Set<Integer> targetGoals = config.isCollisionAwareBlockedTargets()
				? expandingConnectedGoals(config, map, start, target, radius)
				: expandingRingGoals(config, map, start, x, y, z, radius);
			if (!targetGoals.isEmpty())
			{
				if (resolvedGoals == targets)
				{
					resolvedGoals = new HashSet<>(targets);
				}
				resolvedGoals.addAll(targetGoals);
				viable = true;
			}
		}
		return new TargetGoals(resolvedGoals, viable);
	}

	// The tiles on Chebyshev ring r as flat (dx, dy) pairs, perimeter order.
	// Generating the perimeter directly keeps ring scans O(r) rather than
	// O(r^2) over the whole square.
	private static int[] ringOffsets(int r)
	{
		final int[] offsets = new int[r * 16];
		int i = 0;
		for (int s = -r; s < r; s++)
		{
			offsets[i++] = s;
			offsets[i++] = -r;
			offsets[i++] = r;
			offsets[i++] = s;
			offsets[i++] = -s;
			offsets[i++] = r;
			offsets[i++] = -r;
			offsets[i++] = -s;
		}
		return offsets;
	}

	// Expanding Chebyshev rings around a blocked target: returns the innermost
	// ring that contains any walkable tile or transport destination. Used when
	// collisionAwareBlockedTargets is off, so connectivity is not considered.
	private static Set<Integer> expandingRingGoals(PathfinderConfig config, CollisionMap map,
		int start, int x, int y, int z, int radius)
	{
		final Set<Integer> goals = new HashSet<>();
		for (int r = 1; r <= radius && goals.isEmpty(); r++)
		{
			final int[] ring = ringOffsets(r);
			for (int i = 0; i < ring.length; i += 2)
			{
				addRingGoal(config, map, start, goals, x + ring[i], y + ring[i + 1], z);
			}
		}
		return goals;
	}

	private static void addRingGoal(PathfinderConfig config, CollisionMap map, int start,
		Set<Integer> goals, int nx, int ny, int nz)
	{
		final int packed = WorldPointUtil.packWorldPoint(nx, ny, nz);
		if (packed == start)
		{
			return;
		}
		if (!map.isBlocked(nx, ny, nz) || config.isTransportDestination(packed))
		{
			goals.add(packed);
		}
	}

	// A blocked tile's movement flags report every direction blocked, so the
	// target's own edges cannot tell apart a wall and the object's footprint
	// -- but the boundary flags still record which edges carry a wall or
	// door, and those exclude neighbours outright. Where
	// several sides remain, tiles on the target's side of a wall (its room or
	// corridor) reach fewer tiles in a few steps than tiles on open ground:
	// the most enclosed candidate of the innermost ring anchors the side, and
	// flooding from it stays inside the same wall-bounded component. Scanning
	// deeper rings matters only for targets hemmed in by other objects, where
	// every adjacent tile lies outside the room and only ring 2+ tiles sit
	// inside it. Transport destinations ignore walls entirely; they are
	// scanned per Chebyshev ring and count as equally near goals.
	private static Set<Integer> expandingConnectedGoals(PathfinderConfig config, CollisionMap map,
		int start, int target, int radius)
	{
		final int x = WorldPointUtil.unpackWorldX(target);
		final int y = WorldPointUtil.unpackWorldY(target);
		final int z = WorldPointUtil.unpackWorldPlane(target);
		final int scan = Math.min(radius, ENCLOSURE_SCAN_RADIUS);
		// Walkable candidates in the innermost non-empty ring: {packed, reach}.
		final ArrayList<int[]> candidates = new ArrayList<>();
		// The candidate marking the target's side is the most enclosed one
		// of the nearest ring with any candidate at all.
		int anchor = -1;
		int anchorRing = 0;
		int anchorReach = Integer.MAX_VALUE;
		for (int d = 1; d <= scan && anchorRing == 0; d++)
		{
			final int[] ring = ringOffsets(d);
			for (int i = 0; i < ring.length; i += 2)
			{
				final int nx = x + ring[i];
				final int ny = y + ring[i + 1];
				final int packed = WorldPointUtil.packWorldPoint(nx, ny, z);
				// The player's own tile is never a useful goal: ending on
				// the first dequeued node yields an invisible path. A
				// neighbour behind a structural boundary edge -- a wall
				// or door rather than the object's own footprint -- is
				// on the wrong side of the target.
				if (packed == start || map.isBlocked(nx, ny, z)
					|| (d == 1 && wallSeparated(map, x, y, nx, ny, z)))
				{
					continue;
				}
				final int reach = localReach(map, packed);
				candidates.add(new int[] {packed, reach});
				if (reach < anchorReach)
				{
					anchorReach = reach;
					anchor = packed;
				}
			}
			if (!candidates.isEmpty())
			{
				anchorRing = d;
			}
		}
		// Tiles that share the anchor's side of the target's walls. The
		// flood is bounded by the anchor's own ring and does not cross
		// boundary edges, so it cannot leak through a far door or around a
		// wall end and pull in candidates that are merely reachable rather
		// than on the same side.
		final Set<Integer> sameSide = new HashSet<>();
		if (anchor != -1)
		{
			final ArrayDeque<Integer> queue = new ArrayDeque<>();
			sameSide.add(anchor);
			queue.add(anchor);
			while (!queue.isEmpty())
			{
				final int from = queue.poll();
				final int fx = WorldPointUtil.unpackWorldX(from);
				final int fy = WorldPointUtil.unpackWorldY(from);
				for (int step : map.ordinaryWalkingNeighbors(from))
				{
					final int sx = WorldPointUtil.unpackWorldX(step);
					final int sy = WorldPointUtil.unpackWorldY(step);
					if (Math.max(Math.abs(sx - x), Math.abs(sy - y)) <= anchorRing
						&& !wallSeparated(map, fx, fy, sx, sy, z) && sameSide.add(step))
					{
						queue.add(step);
					}
				}
			}
		}
		final Set<Integer> goals = new HashSet<>();
		for (int d = 1; d <= radius && goals.isEmpty(); d++)
		{
			if (d == anchorRing)
			{
				for (int[] candidate : candidates)
				{
					if (sameSide.contains(candidate[0]))
					{
						goals.add(candidate[0]);
					}
				}
			}
			addTransportRingGoals(config, start, goals, x, y, z, d);
		}
		return goals;
	}

	// The boundary flag for the edge between two cardinally adjacent tiles
	// is stored once, on the lower tile -- mirroring the movement flags.
	private static boolean wallBetween(CollisionMap map, int ax, int ay, int bx, int by, int z)
	{
		if (bx != ax)
		{
			return map.wallE(Math.min(ax, bx), ay, z);
		}
		return map.wallN(ax, Math.min(ay, by), z);
	}

	// A cardinal neighbour shares exactly one edge with the target; a
	// diagonal neighbour counts as separated only when both flanking
	// cardinal routes to it cross a boundary edge.
	private static boolean wallSeparated(CollisionMap map, int x, int y, int nx, int ny, int z)
	{
		final int dx = nx - x;
		final int dy = ny - y;
		if (dx == 0 || dy == 0)
		{
			return wallBetween(map, x, y, nx, ny, z);
		}
		return (wallBetween(map, x, y, x + dx, y, z) || wallBetween(map, x + dx, y, nx, ny, z))
			&& (wallBetween(map, x, y, x, y + dy, z) || wallBetween(map, x, y + dy, nx, ny, z));
	}

	// Counts the distinct tiles reachable from packedPoint within
	// ENCLOSURE_DEPTH ordinary walking steps -- a proxy for how enclosed the
	// tile is. Open ground scores near the geometric maximum; rooms and
	// corridors score lower because walls bound the flood.
	private static int localReach(CollisionMap map, int packedPoint)
	{
		// Each queued entry is the packed tile shifted by its remaining flood
		// depth, so one queue replaces per-depth draining.
		final Set<Integer> seen = new HashSet<>();
		final ArrayDeque<Long> queue = new ArrayDeque<>();
		seen.add(packedPoint);
		queue.add((long) packedPoint << 8 | ENCLOSURE_DEPTH);
		while (!queue.isEmpty())
		{
			final long entry = queue.poll();
			final int depth = (int) (entry & 0xff);
			if (depth == 0)
			{
				continue;
			}
			for (int neighbour : map.ordinaryWalkingNeighbors((int) (entry >> 8)))
			{
				if (seen.add(neighbour))
				{
					queue.add((long) neighbour << 8 | depth - 1);
				}
			}
		}
		return seen.size();
	}

	private static void addTransportRingGoals(PathfinderConfig config, int start,
		Set<Integer> goals, int x, int y, int z, int r)
	{
		final int[] ring = ringOffsets(r);
		for (int i = 0; i < ring.length; i += 2)
		{
			final int packed = WorldPointUtil.packWorldPoint(x + ring[i], y + ring[i + 1], z);
			if (packed != start && config.isTransportDestination(packed))
			{
				goals.add(packed);
			}
		}
	}
}
