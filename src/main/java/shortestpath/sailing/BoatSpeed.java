package shortestpath.sailing;

/**
 * A boat's base speed as the game stores it: in 128ths of a tile per tick. Every speed the plugin keeps is in these
 * units; tiles per tick are only worked out where the moves are built and where speeds are shown.
 */
public final class BoatSpeed
{
	/** Units per tile per tick. */
	public static final int UNITS = 128;
	/** The speed assumed when the game's can't be read: 2 tiles per tick, a mid-game hull. */
	public static final int ESTIMATED = 256;
	/**
	 * Each hull option's base speed, the same for every boat type: wooden, oak, teak, mahogany, camphor, ironwood,
	 * rosewood (the hull's facility stats in the cache).
	 */
	static final int[] HULLS = {192, 192, 256, 256, 320, 320, 384};
	/** The fastest base speed a hull gives. */
	public static final int FASTEST = 384;

	private BoatSpeed()
	{
	}

	/** The base speed hull option {@code hull} gives, or 0 for an option the plugin doesn't know. */
	public static int ofHull(int hull)
	{
		return hull >= 0 && hull < HULLS.length ? HULLS[hull] : 0;
	}

	/** {@code speed} in tiles per tick. */
	public static double tilesPerTick(int speed)
	{
		return speed / (double) UNITS;
	}
}
