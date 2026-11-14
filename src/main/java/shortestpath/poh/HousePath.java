package shortestpath.poh;

import java.util.*;
import net.runelite.api.CollisionDataFlag;

/**
 * A walking path inside the loaded house, from the player to the nearest
 * of some house objects, over the scene's live collision flags. The bundled
 * map has no houses (every house is built differently), so the route stops
 * at the house; this fills the walk from the player to the teleport.
 * <p>
 * Steps follow the client's own movement checks: a step is blocked by a
 * full block on the tile stepped onto or a wall on the side stepped
 * through; a diagonal step also needs both side steps open. An object is
 * reached on a tile it covers that can be stood on, or on a tile beside it
 * with no wall in between. Pure, so it is testable without a client.
 */
public final class HousePath
{
	private static final int FULL = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
	private static final int N = CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
	private static final int E = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
	private static final int S = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
	private static final int W = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
	private static final int NE = CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST;
	private static final int NW = CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST;
	private static final int SE = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST;
	private static final int SW = CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST;

	/** Orthogonal steps first, so straight runs are preferred among equal lengths. */
	private static final int[][] STEPS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {1, -1}, {-1, -1}, {-1, 1}};

	private HousePath()
	{
	}

	/** The tiles an object covers, inclusive, in scene coordinates. */
	public static final class Area
	{
		final int minX;
		final int minY;
		final int maxX;
		final int maxY;
		/** Used only from its own tile, like something mounted on a wall. */
		final boolean ownTileOnly;

		public Area(int minX, int minY, int maxX, int maxY)
		{
			this(minX, minY, maxX, maxY, false);
		}

		public Area(int minX, int minY, int maxX, int maxY, boolean ownTileOnly)
		{
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			this.ownTileOnly = ownTileOnly;
		}
	}

	/**
	 * The shortest walk from {@code (startX, startY)} to the nearest of
	 * {@code targets}, as scene tiles from the start to the tile the object is
	 * used from; empty when none can be reached.
	 */
	public static List<int[]> find(int[][] flags, int startX, int startY, List<Area> targets)
	{
		int width = flags.length;
		int height = width == 0 ? 0 : flags[0].length;
		if (targets.isEmpty() || !inside(startX, startY, width, height))
		{
			return List.of();
		}
		int[] parent = new int[width * height];
		Arrays.fill(parent, -1);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		int start = startX * height + startY;
		parent[start] = start;
		queue.add(start);
		while (!queue.isEmpty())
		{
			int index = queue.poll();
			int x = index / height;
			int y = index % height;
			if (isGoal(flags, x, y, targets))
			{
				return trace(parent, index, start, height);
			}
			for (int[] step : STEPS)
			{
				int nx = x + step[0];
				int ny = y + step[1];
				if (!inside(nx, ny, width, height) || parent[nx * height + ny] != -1
					|| !canStep(flags, x, y, step[0], step[1], width, height))
				{
					continue;
				}
				parent[nx * height + ny] = index;
				queue.add(nx * height + ny);
			}
		}
		return List.of();
	}

	/** Whether one step from {@code (x, y)} by {@code (dx, dy)} is open. */
	static boolean canStep(int[][] flags, int x, int y, int dx, int dy, int width, int height)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (!inside(nx, ny, width, height))
		{
			return false;
		}
		if (dx == 0 && dy == 1)
		{
			return (flags[nx][ny] & (FULL | S)) == 0;
		}
		if (dx == 0 && dy == -1)
		{
			return (flags[nx][ny] & (FULL | N)) == 0;
		}
		if (dx == 1 && dy == 0)
		{
			return (flags[nx][ny] & (FULL | W)) == 0;
		}
		if (dx == -1 && dy == 0)
		{
			return (flags[nx][ny] & (FULL | E)) == 0;
		}
		if (dx == 1 && dy == 1)
		{
			return (flags[nx][ny] & (FULL | SW | S | W)) == 0
				&& (flags[x + 1][y] & (FULL | W)) == 0 && (flags[x][y + 1] & (FULL | S)) == 0;
		}
		if (dx == -1 && dy == 1)
		{
			return (flags[nx][ny] & (FULL | SE | S | E)) == 0
				&& (flags[x - 1][y] & (FULL | E)) == 0 && (flags[x][y + 1] & (FULL | S)) == 0;
		}
		if (dx == 1 && dy == -1)
		{
			return (flags[nx][ny] & (FULL | NW | N | W)) == 0
				&& (flags[x + 1][y] & (FULL | W)) == 0 && (flags[x][y - 1] & (FULL | N)) == 0;
		}
		if (dx == -1 && dy == -1)
		{
			return (flags[nx][ny] & (FULL | NE | N | E)) == 0
				&& (flags[x - 1][y] & (FULL | E)) == 0 && (flags[x][y - 1] & (FULL | N)) == 0;
		}
		return false;
	}

	/** On a tile an object covers that can be stood on, or beside it with no wall toward it. */
	static boolean isGoal(int[][] flags, int x, int y, List<Area> targets)
	{
		int tile = flags[x][y];
		if ((tile & FULL) != 0)
		{
			return false;
		}
		for (Area a : targets)
		{
			boolean alongX = x >= a.minX && x <= a.maxX;
			boolean alongY = y >= a.minY && y <= a.maxY;
			if (alongX && alongY)
			{
				return true;
			}
			if (a.ownTileOnly)
			{
				continue;
			}
			if ((alongX && y == a.minY - 1 && (tile & N) == 0)
				|| (alongX && y == a.maxY + 1 && (tile & S) == 0)
				|| (alongY && x == a.minX - 1 && (tile & E) == 0)
				|| (alongY && x == a.maxX + 1 && (tile & W) == 0))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean inside(int x, int y, int width, int height)
	{
		return x >= 0 && y >= 0 && x < width && y < height;
	}

	private static List<int[]> trace(int[] parent, int end, int start, int height)
	{
		List<int[]> path = new ArrayList<>();
		for (int index = end; ; index = parent[index])
		{
			path.add(new int[]{index / height, index % height});
			if (index == start)
			{
				break;
			}
		}
		Collections.reverse(path);
		return path;
	}
}
