package shortestpath.pathfinder.astar;

import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Which global teleports the A* backend lets the player cast from a tile, by the tile's
 * wilderness band. These are legacy's global-teleport abstract nodes ({@code AbstractNodeKind}):
 *
 * <pre>
 * OVER_30  level 31+     cast level 31  GLOBAL_TELEPORTS_OVER_30
 * OVER_20  levels 21-30  cast level 30  GLOBAL_TELEPORTS_OVER_20
 * OVER_0   levels 1-20   cast level 20  GLOBAL_TELEPORTS_OVER_0
 * ALL      level 0       cast level 0   GLOBAL_TELEPORTS_NORMAL
 * </pre>
 *
 * A global is castable with a capability when {@link Transport#isUsableAtWildernessLevel} allows
 * the capability's cast level, as in legacy. Cast levels only fall from {@code OVER_30} to
 * {@code ALL}, so each capability can cast everything the one before it can.
 *
 * <p>The bands are {@code WildernessChecker}'s level-30, level-20 and wilderness areas (checked by
 * {@code TeleportCapabilityTest}), on every plane, inlined here because the forward search asks
 * for every state it pops.
 */
public enum TeleportCapability
{
	OVER_30(31), OVER_20(30), OVER_0(20), ALL(0);

	@Getter @Accessors(fluent = true) private final int castLevel;

	TeleportCapability(int castLevel)
	{
		this.castLevel = castLevel;
	}

	/** Whether {@code transport} can be cast as a global teleport in this band. */
	public boolean canCast(Transport transport)
	{
		return transport.isUsableAtWildernessLevel(castLevel);
	}

	/** Whether a tile with this capability may cast everything {@code required} can. */
	boolean allows(TeleportCapability required)
	{
		return castLevel <= required.castLevel;
	}

	/**
	 * {left, bottom, width, height} areas inside the above-ground wilderness area that are not
	 * wilderness: Ferox Enclave and its south edge.
	 */
	static final int[][] NOT_WILDERNESS = {{3123, 3622, 2, 10}, {3125, 3617, 16, 23}, {3138, 3636, 18, 10},
		{3141, 3625, 14, 11}, {3141, 3619, 7, 6}, {2997, 3525, 34, 9}, {3005, 3534, 21, 10}, {3000, 3534, 5, 5},
		{3031, 3525, 2, 2}};

	public static TeleportCapability at(int tile)
	{
		return at(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile));
	}

	static TeleportCapability at(int x, int y)
	{
		// The wilderness areas and the level 30 and level 20 bands, unrolled; NOT_WILDERNESS cuts out of the first.
		if (x < 2944 || x >= 3462 || y < 3525) return ALL;
		if (y >= 9918)
		{
			if (y >= 10376) return ALL;
			return y >= 10155 ? OVER_30 : y >= 10075 ? OVER_20 : OVER_0;
		}
		if (x >= 3392) return ALL;
		if (y >= 3760 && y < 4208) return OVER_30;
		if (y >= 3680 && y < 4128) return OVER_20;
		if (y >= 3973 || inAny(NOT_WILDERNESS, x, y)) return ALL;
		return OVER_0;
	}

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
