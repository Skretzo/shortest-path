package shortestpath.pathfinder;

import shortestpath.WorldPointUtil;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public class CollisionMap
{
	private final SplitFlagMap collisionData;

	/**
	 * The region grid bounds to scan when enumerating every collision tile (e.g. the
	 * routing-component generator in the test sources). Delegates to
	 * {@link SplitFlagMap#getRegionExtents()} by default; overridable so tests can substitute a
	 * small synthetic extent without touching that shared static state.
	 */
	public SplitFlagMap.RegionExtent regionExtent()
	{
		return SplitFlagMap.getRegionExtents();
	}

	public byte getRegionPlaneCounts(int regionIndex)
	{
		return collisionData.getRegionPlaneCounts(regionIndex);
	}

	private boolean get(int x, int y, int z, int flag)
	{
		return collisionData.get(x, y, z, flag);
	}

	public boolean n(int x, int y, int z)
	{
		return get(x, y, z, SplitFlagMap.FLAG_NORTH);
	}

	public boolean s(int x, int y, int z)
	{
		return n(x, y - 1, z);
	}

	public boolean e(int x, int y, int z)
	{
		return get(x, y, z, SplitFlagMap.FLAG_EAST);
	}

	public boolean w(int x, int y, int z)
	{
		return e(x - 1, y, z);
	}

	// The wall flags record whether a structural boundary (a wall or door
	// edge) sits on the edge, regardless of whether the edge is currently
	// passable. They stay unset on edges that are blocked only by an
	// object's footprint or a tile-level floor/seal block.
	public boolean wallN(int x, int y, int z)
	{
		return get(x, y, z, SplitFlagMap.FLAG_WALL_NORTH);
	}

	public boolean wallE(int x, int y, int z)
	{
		return get(x, y, z, SplitFlagMap.FLAG_WALL_EAST);
	}

	public boolean wallW(int x, int y, int z)
	{
		return wallE(x - 1, y, z);
	}

	private boolean ne(int x, int y, int z)
	{
		return n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z);
	}

	private boolean nw(int x, int y, int z)
	{
		return n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z);
	}

	private boolean se(int x, int y, int z)
	{
		return s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z);
	}

	private boolean sw(int x, int y, int z)
	{
		return s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z);
	}

	public boolean isBlocked(int x, int y, int z)
	{
		return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z);
	}

	/**
	 * Returns the ordinary 8-direction walking mask (bits N, NE, E, SE, S, SW, W, NW); a diagonal
	 * is open only when both flanking cardinal moves are.
	 */
	public byte ordinaryWalkingMask(int packedPoint)
	{
		final int x = WorldPointUtil.unpackWorldX(packedPoint);
		final int y = WorldPointUtil.unpackWorldY(packedPoint);
		final int z = WorldPointUtil.unpackWorldPlane(packedPoint);
		if (isBlocked(x, y, z))
		{
			return 0;
		}

		int mask = 0;
		if (n(x, y, z)) mask |= 1;
		if (ne(x, y, z)) mask |= 1 << 1;
		if (e(x, y, z)) mask |= 1 << 2;
		if (se(x, y, z)) mask |= 1 << 3;
		if (s(x, y, z)) mask |= 1 << 4;
		if (sw(x, y, z)) mask |= 1 << 5;
		if (w(x, y, z)) mask |= 1 << 6;
		if (nw(x, y, z)) mask |= 1 << 7;
		return (byte) mask;
	}

	/** Returns authoritative walking neighbours without transport-specific edges. */
	public int[] ordinaryWalkingNeighbors(int packedPoint)
	{
		final int x = WorldPointUtil.unpackWorldX(packedPoint);
		final int y = WorldPointUtil.unpackWorldY(packedPoint);
		final int z = WorldPointUtil.unpackWorldPlane(packedPoint);
		if (!isBlocked(x, y, z))
		{
			final int mask = Byte.toUnsignedInt(ordinaryWalkingMask(packedPoint));
			final int[] result = new int[Integer.bitCount(mask)];
			int index = 0;
			for (int bit = 0; bit < 8; bit++)
			{
				if ((mask & (1 << bit)) != 0)
				{
					result[index++] = WorldPointUtil.packWorldPoint(
						x + new int[]{0, 1, 1, 1, 0, -1, -1, -1}[bit],
						y + new int[]{1, 1, 0, -1, -1, -1, 0, 1}[bit], z);
				}
			}
			return result;
		}

		int[] result = new int[8];
		int count = 0;
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx == 0 && dy == 0 || !isWalkable(x + dx, y + dy, z))
				{
					continue;
				}
				if (dx == 0 || dy == 0 || (isWalkable(x + dx, y, z) && isWalkable(x, y + dy, z)))
				{
					result[count++] = WorldPointUtil.packWorldPoint(x + dx, y + dy, z);
				}
			}
		}
		return java.util.Arrays.copyOf(result, count);
	}

	private boolean isWalkable(int x, int y, int z)
	{
		return n(x, y, z) || s(x, y, z) || e(x, y, z) || w(x, y, z);
	}
}
