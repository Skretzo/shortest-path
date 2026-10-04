package shortestpath.pathfinder.exact;

import java.util.Arrays;
import shortestpath.WorldPointUtil;

/**
 * Where a walking leg may end on its plane: every tile from which the route's next action can be
 * taken with the same consequences. That is one tile for a fixed waypoint, the origins of one
 * transport action for a transport, and an area for a global teleport, which can be cast anywhere
 * the wilderness allows it.
 */
abstract class WalkGoal
{
	/** Whether the leg may end at {@code (x, y)}. */
	abstract boolean contains(int x, int y);

	/**
	 * A lexicographic lower bound on the rest of the leg from {@code (x, y)}: the fewest ticks to
	 * any goal tile in the high 32 bits and, among goal tiles that few ticks away, the least axis
	 * travel in the low 32 bits. Taking both from the same goal tile keeps the bound consistent.
	 */
	abstract long lowerBound(int x, int y);

	/**
	 * Whether the leg may end by stepping onto a goal tile that is blocked, as the exact search
	 * walks onto the blocked origin of a transport (a door or an obstacle) to take it.
	 */
	abstract boolean entersBlockedTiles();

	static long bound(int ticks, int axisTravel)
	{
		return (long) ticks << 32 | axisTravel;
	}

	static int ticks(long bound)
	{
		return (int) (bound >>> 32);
	}

	static int axisTravel(long bound)
	{
		return (int) bound;
	}

	/** One tile: a fixed waypoint, the final destination, or a transport only taken from there. */
	static WalkGoal tile(int packed)
	{
		return tiles(new int[] {packed}, WorldPointUtil.unpackWorldPlane(packed));
	}

	/** The tiles of {@code packed} on {@code plane}, all equivalent places to take the next action. */
	static WalkGoal tiles(int[] packed, int plane)
	{
		int[] xs = new int[packed.length], ys = new int[packed.length];
		int count = 0;
		for (int tile : packed)
		{
			if (WorldPointUtil.unpackWorldPlane(tile) != plane) continue;
			xs[count] = WorldPointUtil.unpackWorldX(tile);
			ys[count++] = WorldPointUtil.unpackWorldY(tile);
		}
		if (count == 0) throw new IllegalArgumentException("goal has no tile on plane " + plane);
		return new Tiles(Arrays.copyOf(xs, count), Arrays.copyOf(ys, count));
	}

	/** Every tile from which teleports needing {@code capability} can be cast. */
	static WalkGoal teleportArea(TeleportCapability capability)
	{
		return new TeleportArea(capability);
	}

	private static final class Tiles extends WalkGoal
	{
		private final int[] xs, ys;

		Tiles(int[] xs, int[] ys)
		{
			this.xs = xs;
			this.ys = ys;
		}

		@Override
		boolean contains(int x, int y)
		{
			for (int i = 0; i < xs.length; i++)
				if (xs[i] == x && ys[i] == y) return true;
			return false;
		}

		@Override
		long lowerBound(int x, int y)
		{
			long best = Long.MAX_VALUE;
			for (int i = 0; i < xs.length; i++)
			{
				int dx = Math.abs(xs[i] - x), dy = Math.abs(ys[i] - y);
				best = Math.min(best, bound(Math.max(dx, dy), dx + dy));
			}
			return best;
		}

		@Override
		boolean entersBlockedTiles()
		{
			return true;
		}

		@Override
		public String toString()
		{
			return xs.length == 1 ? "tile " + xs[0] + "," + ys[0] : xs.length + " tiles";
		}
	}

	/**
	 * The tiles outside a few rectangles, or inside a few holes in them. Leaving the rectangles takes
	 * at least as many moves as the furthest any containing rectangle's nearest edge is, and a
	 * straight walk to that edge costs as much axis travel as it does ticks; reaching a hole is
	 * bounded like reaching a tile.
	 */
	private static final class TeleportArea extends WalkGoal
	{
		private final TeleportCapability capability;
		private final int[][] excluded, holes;
		private final int left, bottom, right, top;

		TeleportArea(TeleportCapability capability)
		{
			this.capability = capability;
			this.excluded = capability.excludedAreas();
			this.holes = capability.holes();
			int l = Integer.MAX_VALUE, b = Integer.MAX_VALUE, r = Integer.MIN_VALUE, t = Integer.MIN_VALUE;
			for (int[] area : excluded)
			{
				l = Math.min(l, area[0]);
				b = Math.min(b, area[1]);
				r = Math.max(r, area[0] + area[2]);
				t = Math.max(t, area[1] + area[3]);
			}
			left = l;
			bottom = b;
			right = r;
			top = t;
		}

		@Override
		boolean contains(int x, int y)
		{
			return TeleportCapability.at(x, y).allows(capability);
		}

		@Override
		long lowerBound(int x, int y)
		{
			if (x < left || x >= right || y < bottom || y >= top) return 0;
			int distance = 0;
			for (int[] area : excluded)
			{
				if (!TeleportCapability.inArea(area, x, y)) continue;
				int left = x - area[0] + 1, right = area[0] + area[2] - x;
				int down = y - area[1] + 1, up = area[1] + area[3] - y;
				distance = Math.max(distance, Math.min(Math.min(left, right), Math.min(down, up)));
			}
			long best = bound(distance, distance);
			for (int[] hole : holes)
			{
				int dx = Math.max(0, Math.max(hole[0] - x, x - (hole[0] + hole[2] - 1)));
				int dy = Math.max(0, Math.max(hole[1] - y, y - (hole[1] + hole[3] - 1)));
				best = Math.min(best, bound(Math.max(dx, dy), dx + dy));
			}
			return best;
		}

		@Override
		boolean entersBlockedTiles()
		{
			return false;
		}

		@Override
		public String toString()
		{
			return "teleport area " + capability;
		}
	}
}
