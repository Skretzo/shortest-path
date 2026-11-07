package shortestpath.pathfinder.sailing;

import java.util.Arrays;

import net.runelite.api.Perspective;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * The moves the sailing search uses for one boat speed.
 * <p>
 * A boat sails in one of 16 headings, and each tick it moves by that heading's velocity rounded to
 * quarter tiles, so the real direction of a heading depends on the speed: at speed 2, NNE moves 3
 * quarter tiles across and 7 up per tick (23.2 degrees, not 22.5). Holding a heading for 1, 2 or 4
 * ticks always lands a whole number of tiles away, with the boat at the same spot within its tile.
 * Each move here is one heading held for the fewest such ticks, so every leg of a path is a direction
 * the boat can actually sail, and costs the ticks it takes.
 * <p>
 * The search uses the boat's base speed for the whole route, since speed boosts are random and
 * temporary; {@link #ESTIMATE} is the fallback when the base speed can't be read.
 */
public final class SailingMoves
{
	private static final String[] HEADING_NAMES = {
		"S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW", "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE"
	};

	/**
	 * Fallback speed when the boat's base speed can't be read. Straight and diagonal headings go the same
	 * way at every speed; at speed 2 the in-between headings (NNE and so on) are within a couple of degrees
	 * of their direction at the common speeds 2 to 4.
	 */
	public static final double ESTIMATED_SPEED = 2.0;

	/**
	 * The fastest a real boat's base speed can be, in tiles per tick: the common speeds are 2 to 4, and
	 * double the fastest leaves headroom for quicker hulls. A speed above it is a stale or mis-scaled
	 * reading rather than a faster boat — the moves it builds are so long that nothing can sail them —
	 * so the estimate is used instead.
	 */
	public static final double MAX_SPEED = 8.0;

	public static final SailingMoves ESTIMATE = forSpeed(ESTIMATED_SPEED);

	/**
	 * The speed a boat can always slow to, in tiles per tick: to come in to a berth, or round a bend too tight for its
	 * full speed. It turns as at full speed, a heading a tick as it moves.
	 */
	public static final double SLOW_SPEED = 1.0;
	private static final SailingMoves SLOW = forSpeed(SLOW_SPEED);
	// These moves and the slow ones, made on first ask
	private volatile SailingMoves withSlow;

	@Getter @Accessors(fluent = true) private final double speed;
	private final int[] dx;
	private final int[] dy;
	private final int[] ticks;
	private final int[] headings;
	private final String[] names;
	private final double longestMove, topSpeed;

	private SailingMoves(double speed, int[] dx, int[] dy, int[] ticks, int[] headings, String[] names)
	{
		this.speed = speed;
		this.dx = dx;
		this.dy = dy;
		this.ticks = ticks;
		this.headings = headings;
		this.names = names;
		double longest = 0, top = 0;
		for (int move = 0; move < dx.length; move++)
		{
			longest = Math.max(longest, Math.hypot(dx[move], dy[move]));
			top = Math.max(top, Math.hypot(dx[move], dy[move]) / ticks[move]);
		}
		buildFacets();
		longestMove = longest;
		topSpeed = top;
	}

	/**
	 * @param tilesPerTick boat speed, e.g. 1.5 or 2.0; an out-of-range value (including NaN) gives the estimate
	 */
	public static SailingMoves forSpeed(double tilesPerTick)
	{
		if (!(tilesPerTick > 0) || tilesPerTick > MAX_SPEED)
		{
			return ESTIMATE;
		}
		int[] dx = new int[HEADING_NAMES.length];
		int[] dy = new int[HEADING_NAMES.length];
		int[] ticks = new int[HEADING_NAMES.length];
		int[] headings = new int[HEADING_NAMES.length];
		String[] names = new String[HEADING_NAMES.length];
		int count = 0;
		for (int heading = 0; heading < HEADING_NAMES.length; heading++)
		{
			// Headings are 128 angle units apart: 0 is south, 512 west, 1024 north, 1536 east
			int angle = heading * 128;
			int quarterX = quarterTiles(-Perspective.SINE[angle] * tilesPerTick / 512.0);
			int quarterY = quarterTiles(-Perspective.COSINE[angle] * tilesPerTick / 512.0);
			if (quarterX == 0 && quarterY == 0)
			{
				continue;
			}
			// Fewest ticks (1, 2 or 4) after which the boat is a whole number of tiles away
			int k = 1;
			while ((k * quarterX) % 4 != 0 || (k * quarterY) % 4 != 0)
			{
				k *= 2;
			}
			dx[count] = k * quarterX / 4;
			dy[count] = k * quarterY / 4;
			ticks[count] = k;
			headings[count] = heading;
			names[count] = HEADING_NAMES[heading];
			count++;
		}
		if (count == 0)
		{
			// Below about 0.125 tiles per tick every heading rounds to no movement; a garbage or
			// stale base-speed reading must not produce a search that can never move
			return ESTIMATE;
		}
		return new SailingMoves(tilesPerTick, Arrays.copyOf(dx, count), Arrays.copyOf(dy, count),
			Arrays.copyOf(ticks, count), Arrays.copyOf(headings, count),
			Arrays.copyOf(names, count));
	}

	/**
	 * These moves and those at {@link #SLOW_SPEED}: what a boat can sail, slowing where it must. A slow move going as
	 * far as one of these is left out, being slower.
	 */
	public SailingMoves withSlow()
	{
		SailingMoves both = withSlow;
		if (both != null) return both;
		int n = dx.length;
		int[] bx = Arrays.copyOf(dx, n + SLOW.size()), by = Arrays.copyOf(dy, n + SLOW.size());
		int[] bt = Arrays.copyOf(ticks, n + SLOW.size()), bh = Arrays.copyOf(headings, n + SLOW.size());
		String[] bn = Arrays.copyOf(names, n + SLOW.size());
		int count = n;
		for (int move = 0; move < SLOW.size(); move++)
		{
			if (indexOf(SLOW.dx[move], SLOW.dy[move]) >= 0) continue;
			bx[count] = SLOW.dx[move];
			by[count] = SLOW.dy[move];
			bt[count] = SLOW.ticks[move];
			bh[count] = SLOW.headings[move];
			bn[count++] = SLOW.names[move] + " slowly";
		}
		both = new SailingMoves(speed, Arrays.copyOf(bx, count), Arrays.copyOf(by, count), Arrays.copyOf(bt, count),
			Arrays.copyOf(bh, count), Arrays.copyOf(bn, count));
		withSlow = both;
		return both;
	}

	// The game's velocity along one axis: rounded to local units (128 per tile), then to quarter tiles (32 each)
	private static int quarterTiles(double localUnits)
	{
		double local = Math.signum(localUnits) * Math.round(Math.abs(localUnits));
		return (int) (Math.signum(local) * Math.round(Math.abs(local) / 32.0));
	}

	// The convex hull of the moves' velocities, as facets {nx, ny}: a velocity v is inside when n.v <= 1 for each
	private double[] facetX, facetY;
	private double maxFacet;

	private void buildFacets()
	{
		int n = dx.length;
		double[][] v = new double[n][];
		for (int i = 0; i < n; i++) v[i] = new double[] {(double) dx[i] / ticks[i], (double) dy[i] / ticks[i]};
		java.util.Arrays.sort(v, (a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]));
		double[][] hull = new double[2 * n + 1][];
		int k = 0;
		for (int i = 0; i < n; i++)
		{
			while (k >= 2 && cross(hull[k - 2], hull[k - 1], v[i]) <= 1e-12) k--;
			hull[k++] = v[i];
		}
		int lower = k + 1;
		for (int i = n - 2; i >= 0; i--)
		{
			while (k >= lower && cross(hull[k - 2], hull[k - 1], v[i]) <= 1e-12) k--;
			hull[k++] = v[i];
		}
		k--;
		facetX = new double[k];
		facetY = new double[k];
		maxFacet = 0;
		for (int i = 0; i < k; i++)
		{
			double[] a = hull[i], b = hull[(i + 1) % k];
			double nx = b[1] - a[1], ny = a[0] - b[0];
			double scale = nx * a[0] + ny * a[1];
			facetX[i] = nx / scale;
			facetY[i] = ny / scale;
			maxFacet = Math.max(maxFacet, Math.hypot(facetX[i], facetY[i]));
		}
	}

	private static double cross(double[] o, double[] a, double[] b)
	{
		return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
	}

	/** A lower bound on the ticks to sail (x, y) tiles by these moves, however they are combined. */
	public double openWaterTicks(int x, int y)
	{
		double most = 0;
		for (int i = 0; i < facetX.length; i++) most = Math.max(most, facetX[i] * x + facetY[i] * y);
		return most;
	}

	/** {@link #openWaterTicks(int, int)} for a displacement off the tile grid. */
	public double openWaterTicks(double x, double y)
	{
		double most = 0;
		for (int i = 0; i < facetX.length; i++) most = Math.max(most, facetX[i] * x + facetY[i] * y);
		return most;
	}

	/** The most {@link #openWaterTicks} one tile of displacement can change by. */
	public double ticksPerTile()
	{
		return maxFacet;
	}


	public int size()
	{
		return dx.length;
	}

	public int dx(int move)
	{
		return dx[move];
	}

	public int dy(int move)
	{
		return dy[move];
	}

	public int ticks(int move)
	{
		return ticks[move];
	}

	/**
	 * The heading the move holds: 0 is south, 4 west, 8 north and 12 east, like the game's orientation divided by 128.
	 */
	public int heading(int move)
	{
		return headings[move];
	}

	public String name(int move)
	{
		return names[move];
	}

	/** How far the move sails in a straight line, in tiles. */
	public double distance(int move)
	{
		return Math.hypot(dx[move], dy[move]);
	}

	/** The farthest any move sails, in tiles. */
	public double longestMove()
	{
		return longestMove;
	}

	/** The fastest any move sails in a straight line, in tiles per tick. */
	public double topSpeed()
	{
		return topSpeed;
	}

	/**
	 * The heading of the move that goes (dx, dy) tiles, or -1 if there is none.
	 */
	public int headingOf(int dx, int dy)
	{
		int move = indexOf(dx, dy);
		return move < 0 ? -1 : headings[move];
	}

	/**
	 * Index of the move that goes (dx, dy) tiles, or -1 if there is none.
	 */
	public int indexOf(int dx, int dy)
	{
		for (int i = 0; i < this.dx.length; i++)
		{
			if (this.dx[i] == dx && this.dy[i] == dy)
			{
				return i;
			}
		}
		return -1;
	}
}
