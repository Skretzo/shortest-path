package shortestpath.pathfinder.astar;

/**
 * Per-tile component numbers, using two bytes when the world's component count permits it.
 * Mutable during construction; ownership passes to the read-only RoutingStatic when built.
 */
final class TileComponents
{
	private final char[] compactIds;
	private final int[] wideIds;
	private final int componentCount;

	TileComponents(int tileCount, int componentCount)
	{
		if (componentCount < 0) throw new IllegalArgumentException("negative component count");
		this.componentCount = componentCount;
		compactIds = componentCount <= Character.MAX_VALUE + 1 ? new char[tileCount] : null;
		wideIds = compactIds == null ? new int[tileCount] : null;
	}

	int length()
	{
		return compactIds == null ? wideIds.length : compactIds.length;
	}

	int get(int tile)
	{
		return compactIds == null ? wideIds[tile] : compactIds[tile];
	}

	void set(int tile, int component)
	{
		if (component < 0 || component >= componentCount)
			throw new IllegalArgumentException("component out of range");
		if (compactIds == null) wideIds[tile] = component;
		else compactIds[tile] = (char) component;
	}
}
