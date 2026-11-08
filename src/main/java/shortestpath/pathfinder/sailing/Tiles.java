package shortestpath.pathfinder.sailing;

import java.util.Arrays;
import shortestpath.WorldPointUtil;

/** Straight-line geometry between packed tiles, as sailing measures it: tile centres, on one plane. */
final class Tiles
{
	private Tiles()
	{
	}

	/** The straight-line distance between the centres of tiles {@code a} and {@code b}, in tiles. */
	static double distance(int a, int b)
	{
		return Math.hypot(WorldPointUtil.unpackWorldX(a) - WorldPointUtil.unpackWorldX(b),
			WorldPointUtil.unpackWorldY(a) - WorldPointUtil.unpackWorldY(b));
	}

	/** The least distance from any of {@code tiles} to {@code b}, or {@link Double#MAX_VALUE} for none. */
	static double distance(int[] tiles, int b)
	{
		double least = Double.MAX_VALUE;
		for (int tile : tiles) least = Math.min(least, distance(tile, b));
		return least;
	}

	/** The tiles on {@code centre}'s plane whose centres are within {@code radius} tiles of its centre. */
	static int[] within(int centre, double radius)
	{
		int reach = (int) Math.ceil(radius);
		int x = WorldPointUtil.unpackWorldX(centre), y = WorldPointUtil.unpackWorldY(centre);
		int plane = WorldPointUtil.unpackWorldPlane(centre);
		int[] tiles = new int[(2 * reach + 1) * (2 * reach + 1)];
		int count = 0;
		for (int dy = -reach; dy <= reach; dy++)
		{
			for (int dx = -reach; dx <= reach; dx++)
			{
				if (dx * dx + dy * dy <= radius * radius) tiles[count++] = WorldPointUtil.packWorldPoint(x + dx, y + dy, plane);
			}
		}
		return Arrays.copyOf(tiles, count);
	}
}
