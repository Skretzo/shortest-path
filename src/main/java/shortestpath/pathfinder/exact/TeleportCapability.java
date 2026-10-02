package shortestpath.pathfinder.exact;

import shortestpath.WorldPointUtil;

/**
 * Which global teleports the exact backend lets the player cast from a tile: none above level 30
 * wilderness, only those usable up to level 30 between levels 20 and 30, and all of them
 * elsewhere. The areas are {@code WildernessChecker}'s level-20 and level-30 areas (checked by
 * {@code TeleportCapabilityTest}), on every plane, inlined here because the forward search asks
 * for every state it pops.
 */
enum TeleportCapability
{
	NONE, WILDERNESS, ALL;

	// {left, bottom, width, height}: above ground, then underground.
	static final int[][] ABOVE_LEVEL_30 = {{2944, 3760, 448, 448}, {2944, 10155, 518, 221}};
	static final int[][] ABOVE_LEVEL_20 = {{2944, 3680, 448, 448}, {2944, 10075, 518, 301}};

	static TeleportCapability at(int tile)
	{
		return at(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile));
	}

	static TeleportCapability at(int x, int y)
	{
		// Unrolled ABOVE_LEVEL_30 and ABOVE_LEVEL_20.
		if (x < 2944 || x >= 3462 || y < 3680) return ALL;
		if (x < 3392 && y >= 3760 && y < 4208 || y >= 10155 && y < 10376) return NONE;
		if (x < 3392 && y < 4128 || y >= 10075 && y < 10376) return WILDERNESS;
		return ALL;
	}

	/** The areas a tile must lie outside of all of for {@link #at} to give at least this capability. */
	int[][] excludedAreas()
	{
		if (this == WILDERNESS) return ABOVE_LEVEL_30;
		if (this == ALL) return EXCLUDED_FROM_ALL;
		throw new IllegalStateException("no teleports are castable with capability " + this);
	}

	/** Whether a tile with this capability may cast what {@code required} allows. */
	boolean allows(TeleportCapability required)
	{
		return compareTo(required) >= 0;
	}

	// The level-30 area reaches further north than the level-20 one, so both are excluded.
	private static final int[][] EXCLUDED_FROM_ALL = {ABOVE_LEVEL_30[0], ABOVE_LEVEL_30[1], ABOVE_LEVEL_20[0],
		ABOVE_LEVEL_20[1]};

	static boolean inAny(int[][] areas, int x, int y)
	{
		for (int[] area : areas)
			if (inArea(area, x, y)) return true;
		return false;
	}

	static boolean inArea(int[] area, int x, int y)
	{
		return x >= area[0] && x < area[0] + area[2] && y >= area[1] && y < area[1] + area[3];
	}
}
