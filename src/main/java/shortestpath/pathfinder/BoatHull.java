package shortestpath.pathfinder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static net.runelite.api.Constants.REGION_SIZE;
import net.runelite.api.Perspective;

/**
 * The hull of the player's boat, so that a sailing move is only taken if the whole boat fits along it, not just its
 * centre.
 * <p>
 * A boat is a rectangle in its own frame (the game's bounds for its world entity: the raft is 1 by 3 tiles, the skiff
 * 2 by 5 and the sloop 3 by 10, reaching further toward its bow), turned to face its heading, at the spot within its
 * tile where the boat sits. Every move lands whole tiles away, so the boat keeps that spot along the whole route.
 * Holding a heading for a move sweeps the rectangle from where the move starts to where it ends, over the convex hull
 * of the two (the game's rounding can take a move slightly off the boat's axis); the move is only taken if no blocked
 * tile overlaps it. Turning between moves isn't checked. A tile blocks a boat if it's blocked or has a wall on any
 * side. Touching a blocked tile's edge is fine, so a 3-wide sloop lined up exactly fits a 3-wide gap, as players have
 * seen in game.
 * <p>
 * An instance belongs to one search: it caches the shapes it sweeps and the blocked tiles it has looked at.
 */
public final class BoatHull
{
	private static final int HEADINGS = 16;
	// The game's angles: 2048 per turn, 128 per heading
	private static final int ANGLES_PER_HEADING = 2048 / HEADINGS;
	private static final double EPSILON = 1e-6;
	private static final int REGION_SHIFT = Integer.numberOfTrailingZeros(REGION_SIZE);
	private static final int REGION_MASK = REGION_SIZE - 1;
	private static final int PLANES = 4;

	// The rectangle in the boat's own frame, in local units (128 per tile): its centre across and along the boat
	// (the bow is toward negative along) and its size
	private final int boundsX;
	private final int boundsY;
	private final int boundsWidth;
	private final int boundsHeight;
	// Where the boat sits within its tile, in local units from the tile's centre
	private final int pivotX;
	private final int pivotY;

	// Tiles the hull covers, relative to the tile it starts on: {first row, then each row's first and last column}
	private final Map<Long, int[]> shapes = new HashMap<>();
	// How far the hull reaches from the centre of the boat's tile, facing any heading, in tiles
	private final double reach;
	// Tiles that block a boat, as one bit per tile and one long per row of each region and plane, filled in as the
	// search reaches them
	private final SplitFlagMap.RegionExtent extent;
	private final long[][] blockedRows;

	private BoatHull(int boundsX, int boundsY, int boundsWidth, int boundsHeight, int pivotX, int pivotY)
	{
		this.boundsX = boundsX;
		this.boundsY = boundsY;
		this.boundsWidth = boundsWidth;
		this.boundsHeight = boundsHeight;
		this.pivotX = pivotX;
		this.pivotY = pivotY;
		extent = SplitFlagMap.getRegionExtents();
		blockedRows = new long[(extent.getWidth() + 1) * (extent.getHeight() + 1) * PLANES][];

		double corner = 0;
		for (int across = -1; across <= 1; across += 2)
		{
			for (int along = -1; along <= 1; along += 2)
			{
				corner = Math.max(corner, Math.hypot(boundsX + across * boundsWidth / 2.0, boundsY + along * boundsHeight / 2.0));
			}
		}
		// A local unit more, for the game's rounding when it turns the hull
		reach = (Math.hypot(pivotX, pivotY) + corner + 1) / Perspective.LOCAL_TILE_SIZE;
	}

	/**
	 * @param boundsX      centre of the boat's rectangle across the boat, in local units (128 per tile)
	 * @param boundsY      centre of the rectangle along the boat, in local units; negative is toward the bow
	 * @param boundsWidth  width of the rectangle, in local units
	 * @param boundsHeight length of the rectangle, in local units
	 * @param pivotX       where the boat sits within its tile, in local units from the tile's centre
	 * @param pivotY       where the boat sits within its tile, in local units from the tile's centre
	 * @return the hull, or {@code null} if the bounds are empty
	 */
	public static BoatHull fromBounds(int boundsX, int boundsY, int boundsWidth, int boundsHeight, int pivotX, int pivotY)
	{
		if (boundsWidth <= 0 || boundsHeight <= 0)
		{
			return null;
		}
		return new BoatHull(boundsX, boundsY, boundsWidth, boundsHeight, pivotX, pivotY);
	}

	/**
	 * The hull's corners facing {@code heading}, in order round the rectangle, in tiles from the centre of the tile the
	 * boat is on: {x0, y0, x1, y1, x2, y2, x3, y3}.
	 */
	public double[] outline(int heading)
	{
		// corners() lists them across then along, so swap the last two to go round
		double[] corners = corners(heading * ANGLES_PER_HEADING, 0, 0);
		return new double[]{corners[0], corners[1], corners[2], corners[3], corners[6], corners[7], corners[4], corners[5]};
	}

	/**
	 * How far the hull reaches from the centre of the boat's tile, facing any heading, in tiles.
	 */
	public double reach()
	{
		return reach;
	}

	/**
	 * Whether the hull, on a tile facing {@code heading}, covers the tile (dx, dy) tiles from it: overlaps it by more
	 * than touching its edge.
	 */
	public boolean covers(int heading, int dx, int dy)
	{
		int[] shape = shape(heading, 0, 0);
		int row = dy - shape[0];
		return row >= 0 && row < rows(shape) && dx >= shape[1 + 2 * row] && dx <= shape[2 + 2 * row];
	}

	/**
	 * Whether the hull, facing {@code heading}, can sail (dx, dy) tiles from tile (x, y, z) without overlapping a tile
	 * that blocks it anywhere along the way, where it starts and ends included.
	 */
	public boolean canMove(CollisionMap map, int x, int y, int z, int heading, int dx, int dy)
	{
		final int[] shape = shape(heading, dx, dy);
		for (int row = 0; row < rows(shape); row++)
		{
			if (anyBlocked(map, x + shape[1 + 2 * row], x + shape[2 + 2 * row], y + shape[0] + row, z))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Lets go of the shapes and blocked tiles cached during a search, once it's done; they're worked out again if needed.
	 */
	void release()
	{
		shapes.clear();
		Arrays.fill(blockedRows, null);
	}

	private static int rows(int[] shape)
	{
		return (shape.length - 1) / 2;
	}

	// Whether any tile from (x0, y) to (x1, y) blocks the boat
	private boolean anyBlocked(CollisionMap map, int x0, int x1, int y, int z)
	{
		for (int x = x0; x <= x1; )
		{
			int last = Math.min(x1, x | REGION_MASK);
			long[] rows = regionRows(map, x >> REGION_SHIFT, y >> REGION_SHIFT, z);
			if (rows == null)
			{
				return true;
			}
			int lo = x & REGION_MASK;
			long mask = (-1L >>> (63 - ((last & REGION_MASK) - lo))) << lo;
			if ((rows[y & REGION_MASK] & mask) != 0)
			{
				return true;
			}
			x = last + 1;
		}
		return false;
	}

	// The tiles of a region and plane that block a boat, one long per row, or null off the map
	private long[] regionRows(CollisionMap map, int regionX, int regionY, int z)
	{
		if (regionX < extent.getMinX() || regionX > extent.getMaxX() || regionY < extent.getMinY()
			|| regionY > extent.getMaxY() || z < 0 || z >= PLANES)
		{
			return null;
		}
		int index = ((regionX - extent.getMinX()) + (regionY - extent.getMinY()) * (extent.getWidth() + 1)) * PLANES + z;
		long[] rows = blockedRows[index];
		if (rows == null)
		{
			rows = new long[REGION_SIZE];
			final int baseX = regionX << REGION_SHIFT;
			final int baseY = regionY << REGION_SHIFT;
			// Which tiles are blocked, including a border one tile wide around the region
			final int size = REGION_SIZE + 2;
			boolean[] blocked = new boolean[size * size];
			for (int row = 0; row < size; row++)
			{
				for (int column = 0; column < size; column++)
				{
					blocked[row * size + column] = map.isBlocked(baseX + column - 1, baseY + row - 1, z);
				}
			}
			for (int row = 0; row < REGION_SIZE; row++)
			{
				long bits = 0;
				for (int column = 0; column < REGION_SIZE; column++)
				{
					final int x = baseX + column;
					final int y = baseY + row;
					final int i = (row + 1) * size + column + 1;
					// A side that can't be crossed into an open tile has a wall on it
					boolean wall = (!map.n(x, y, z) && !blocked[i + size]) || (!map.s(x, y, z) && !blocked[i - size])
						|| (!map.e(x, y, z) && !blocked[i + 1]) || (!map.w(x, y, z) && !blocked[i - 1]);
					if (blocked[i] || wall)
					{
						bits |= 1L << column;
					}
				}
				rows[row] = bits;
			}
			blockedRows[index] = rows;
		}
		return rows;
	}

	// The tiles the hull overlaps while sailing (dx, dy) tiles facing heading, relative to the tile it starts on
	private int[] shape(int heading, int dx, int dy)
	{
		long key = ((long) heading << 40) | ((long) (dx & 0xFFFFF) << 20) | (dy & 0xFFFFF);
		int[] shape = shapes.get(key);
		if (shape == null)
		{
			shape = rasterize(corners(heading * ANGLES_PER_HEADING, dx, dy));
			shapes.put(key, shape);
		}
		return shape;
	}

	// The hull's corners where it starts and where it ends, in tiles around the start tile's centre:
	// {x0, y0, x1, y1, ...}. They're turned in whole local units with the game's sine table, the way the game
	// turns models, so they're exact and edges that line up with tile edges do so exactly.
	private double[] corners(int angle, int dx, int dy)
	{
		final int cos = Perspective.COSINE[angle];
		final int sin = Perspective.SINE[angle];
		double[] points = new double[16];
		int i = 0;
		for (int end = 0; end <= 1; end++)
		{
			for (int across = -1; across <= 1; across += 2)
			{
				for (int along = -1; along <= 1; along += 2)
				{
					int modelX = boundsX + across * boundsWidth / 2;
					int modelY = boundsY + along * boundsHeight / 2;
					int x = pivotX + end * dx * Perspective.LOCAL_TILE_SIZE + ((modelX * cos + modelY * sin) >> 16);
					int y = pivotY + end * dy * Perspective.LOCAL_TILE_SIZE + ((modelY * cos - modelX * sin) >> 16);
					points[i++] = (double) x / Perspective.LOCAL_TILE_SIZE;
					points[i++] = (double) y / Perspective.LOCAL_TILE_SIZE;
				}
			}
		}
		return points;
	}

	// The tiles a convex shape (the hull of the given points) overlaps by more than touching, as rows
	private static int[] rasterize(double[] points)
	{
		double[] hull = convexHull(points);
		int n = hull.length / 2;
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++)
		{
			minY = Math.min(minY, hull[2 * i + 1]);
			maxY = Math.max(maxY, hull[2 * i + 1]);
		}
		// Tile row r covers y from r - 0.5 to r + 0.5, and column c covers x from c - 0.5 to c + 0.5
		int firstRow = (int) Math.floor(minY - 0.5 + EPSILON) + 1;
		int lastRow = (int) Math.ceil(maxY + 0.5 - EPSILON) - 1;
		int[] columns = new int[2 * Math.max(0, lastRow - firstRow + 1)];
		int rows = 0;
		int shapeFirstRow = firstRow;
		for (int row = firstRow; row <= lastRow; row++)
		{
			double[] range = xRange(hull, n, row - 0.5 + EPSILON, row + 0.5 - EPSILON);
			int first = (int) Math.floor(range[0] - 0.5 + EPSILON) + 1;
			int last = (int) Math.ceil(range[1] + 0.5 - EPSILON) - 1;
			if (first > last)
			{
				// Only the rows at either end can be empty, where the shape barely reaches them
				if (rows == 0)
				{
					shapeFirstRow = row + 1;
				}
				continue;
			}
			columns[2 * rows] = first;
			columns[2 * rows + 1] = last;
			rows++;
		}
		int[] shape = new int[1 + 2 * rows];
		shape[0] = shapeFirstRow;
		System.arraycopy(columns, 0, shape, 1, 2 * rows);
		return shape;
	}

	// The smallest and largest x of a convex polygon between two heights
	private static double[] xRange(double[] polygon, int n, double low, double high)
	{
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++)
		{
			double x1 = polygon[2 * i];
			double y1 = polygon[2 * i + 1];
			double x2 = polygon[2 * ((i + 1) % n)];
			double y2 = polygon[2 * ((i + 1) % n) + 1];
			if (y1 >= low && y1 <= high)
			{
				min = Math.min(min, x1);
				max = Math.max(max, x1);
			}
			for (double level : new double[]{low, high})
			{
				if ((y1 - level) * (y2 - level) < 0)
				{
					double x = x1 + (level - y1) * (x2 - x1) / (y2 - y1);
					min = Math.min(min, x);
					max = Math.max(max, x);
				}
			}
		}
		return new double[]{min, max};
	}

	// Andrew's monotone chain; returns the hull's corners in order as {x0, y0, x1, y1, ...}
	private static double[] convexHull(double[] points)
	{
		int n = points.length / 2;
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++)
		{
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> points[2 * a] != points[2 * b]
			? Double.compare(points[2 * a], points[2 * b]) : Double.compare(points[2 * a + 1], points[2 * b + 1]));
		int[] hull = new int[2 * n + 1];
		int k = 0;
		for (int j = 0; j < n; j++)
		{
			while (k >= 2 && cross(points, hull[k - 2], hull[k - 1], order[j]) <= 0)
			{
				k--;
			}
			hull[k++] = order[j];
		}
		int lower = k + 1;
		for (int j = n - 2; j >= 0; j--)
		{
			while (k >= lower && cross(points, hull[k - 2], hull[k - 1], order[j]) <= 0)
			{
				k--;
			}
			hull[k++] = order[j];
		}
		// The last corner is the first one again
		k--;
		double[] result = new double[2 * k];
		for (int i = 0; i < k; i++)
		{
			result[2 * i] = points[2 * hull[i]];
			result[2 * i + 1] = points[2 * hull[i] + 1];
		}
		return result;
	}

	private static double cross(double[] p, int o, int a, int b)
	{
		return (p[2 * a] - p[2 * o]) * (p[2 * b + 1] - p[2 * o + 1]) - (p[2 * a + 1] - p[2 * o + 1]) * (p[2 * b] - p[2 * o]);
	}
}
