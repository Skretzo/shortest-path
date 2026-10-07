package shortestpath.pathfinder;

import java.util.Arrays;

import net.runelite.api.Perspective;

/**
 * The moves the sailing search uses for one boat speed.
 * <p>
 * A boat sails in one of 16 headings, and each tick it moves by that heading's velocity rounded to
 * quarter tiles, so the real direction of a heading depends on the speed: at speed 2, NNE moves 3
 * quarter tiles across and 7 up per tick (23.2 degrees, not 22.5). Holding a heading for 1, 2 or 4
 * ticks always lands a whole number of tiles away, with the boat at the same spot within its tile.
 * Each move here is one heading held for the fewest such ticks, so every leg of a path is a direction
 * the boat can actually sail. The search looks for the shortest route rather than the quickest, so each
 * move costs the distance it sails ({@link #length}).
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
	public static final SailingMoves ESTIMATE = forSpeed(ESTIMATED_SPEED);

	/**
	 * {@link #length} is in thousandths of a tile, so that adding lengths up is exact.
	 */
	public static final int LENGTH_UNITS_PER_TILE = 1000;

	private final double speed;
	private final int[] dx;
	private final int[] dy;
	private final int[] ticks;
	private final int[] lengths;
	private final int[] headings;
	private final String[] names;

	private SailingMoves(double speed, int[] dx, int[] dy, int[] ticks, int[] lengths, int[] headings, String[] names)
	{
		this.speed = speed;
		this.dx = dx;
		this.dy = dy;
		this.ticks = ticks;
		this.lengths = lengths;
		this.headings = headings;
		this.names = names;
	}

	/**
	 * @param tilesPerTick boat speed, e.g. 1.5 or 2.0; must be above zero
	 */
	public static SailingMoves forSpeed(double tilesPerTick)
	{
		int[] dx = new int[HEADING_NAMES.length];
		int[] dy = new int[HEADING_NAMES.length];
		int[] ticks = new int[HEADING_NAMES.length];
		int[] lengths = new int[HEADING_NAMES.length];
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
			// Rounded up, so a route never costs less than its real length and the search's estimate stays a lower bound
			lengths[count] = (int) Math.ceil(Math.hypot(dx[count], dy[count]) * LENGTH_UNITS_PER_TILE);
			headings[count] = heading;
			names[count] = HEADING_NAMES[heading];
			count++;
		}
		return new SailingMoves(tilesPerTick, Arrays.copyOf(dx, count), Arrays.copyOf(dy, count),
			Arrays.copyOf(ticks, count), Arrays.copyOf(lengths, count), Arrays.copyOf(headings, count),
			Arrays.copyOf(names, count));
	}

	// The game's velocity along one axis: rounded to local units (128 per tile), then to quarter tiles (32 each)
	private static int quarterTiles(double localUnits)
	{
		double local = Math.signum(localUnits) * Math.round(Math.abs(localUnits));
		return (int) (Math.signum(local) * Math.round(Math.abs(local) / 32.0));
	}

	public double speed()
	{
		return speed;
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

	/**
	 * How far the move sails in a straight line, in thousandths of a tile ({@link #LENGTH_UNITS_PER_TILE}), rounded up.
	 */
	public int length(int move)
	{
		return lengths[move];
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
