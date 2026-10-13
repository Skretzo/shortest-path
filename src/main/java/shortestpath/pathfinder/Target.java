package shortestpath.pathfinder;

import shortestpath.WorldPointUtil;

/** A tile a route may end on. Immutable. */
public final class Target
{
	private final int point;

	private Target(int point)
	{
		this.point = point;
	}

	/** A target at {@code point}. */
	public static Target at(int point)
	{
		return new Target(point);
	}

	/** The packed tile. */
	public int getPoint()
	{
		return point;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Target && point == ((Target) o).point;
	}

	@Override
	public int hashCode()
	{
		return point;
	}

	@Override
	public String toString()
	{
		return WorldPointUtil.unpackWorldPoint(point).toString();
	}
}
