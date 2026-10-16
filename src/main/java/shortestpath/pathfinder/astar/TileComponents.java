package shortestpath.pathfinder.astar;

/** Per-tile component numbers, using two bytes when the world's component count permits it. */
final class TileComponents
{
	private final char[] small;
	private final int[] large;
	private final int componentCount;

	TileComponents(int tileCount, int componentCount)
	{
		if (componentCount < 0) throw new IllegalArgumentException("negative component count");
		this.componentCount = componentCount;
		small = componentCount <= Character.MAX_VALUE + 1 ? new char[tileCount] : null;
		large = small == null ? new int[tileCount] : null;
	}

	int length()
	{
		return small == null ? large.length : small.length;
	}

	int get(int tile)
	{
		return small == null ? large[tile] : small[tile];
	}

	void set(int tile, int component)
	{
		if (component < 0 || component >= componentCount)
			throw new IllegalArgumentException("component out of range");
		if (small == null) large[tile] = component;
		else small[tile] = (char) component;
	}
}
