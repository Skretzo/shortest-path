package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.AbstractNodeKind;
import shortestpath.pathfinder.WildernessChecker;

public class TeleportCapabilityTest
{
	/** Legacy's global-teleport band for a tile, as {@code Pathfinder.updateWildernessLevel} narrows it. */
	private static AbstractNodeKind legacyKind(int tile)
	{
		return WildernessChecker.isInLevel30Wilderness(tile) ? AbstractNodeKind.GLOBAL_TELEPORTS_OVER_30
			: WildernessChecker.isInLevel20Wilderness(tile) ? AbstractNodeKind.GLOBAL_TELEPORTS_OVER_20
			: WildernessChecker.isInWilderness(tile) ? AbstractNodeKind.GLOBAL_TELEPORTS_OVER_0
			: AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL;
	}

	@Test
	public void capabilityIsLegacysGlobalTeleportBand()
	{
		for (int plane = 0; plane < 4; plane += 3)
			for (int x = 2900; x < 3500; x++)
				for (int[] ys : new int[][] {{3500, 4250}, {9890, 10420}})
					for (int y = ys[0]; y < ys[1]; y++)
					{
						int tile = WorldPointUtil.packWorldPoint(x, y, plane);
						AbstractNodeKind legacy = legacyKind(tile);
						TeleportCapability capability = TeleportCapability.at(tile);
						assertEquals(x + "," + y + "," + plane, legacy.maxWildernessLevel(), capability.castLevel());
					}
	}

	@Test
	public void bandBoundaries()
	{
		// Wilderness level is (y - 3520) / 8 + 1 above ground, from y = 3525.
		assertEquals(TeleportCapability.ALL, at(3200, 3400));
		assertEquals(TeleportCapability.ALL, at(3200, 3524));
		assertEquals("level 1", TeleportCapability.OVER_0, at(3200, 3525));
		assertEquals("level 13, the obelisk landing", TeleportCapability.OVER_0, at(3156, 3620));
		assertEquals("level 20", TeleportCapability.OVER_0, at(3200, 3679));
		assertEquals("level 21", TeleportCapability.OVER_20, at(3200, 3680));
		assertEquals("level 25", TeleportCapability.OVER_20, at(3200, 3712));
		assertEquals("level 30", TeleportCapability.OVER_20, at(3200, 3759));
		assertEquals("level 31", TeleportCapability.OVER_30, at(3200, 3760));
		assertEquals("Ferox Enclave is not wilderness", TeleportCapability.ALL, at(3130, 3630));
		assertEquals("underground level 1-20", TeleportCapability.OVER_0, at(3000, 10000));
	}

	@Test
	public void eachCapabilityCastsWhatTheOneBeforeItCan()
	{
		TeleportCapability[] order = {TeleportCapability.OVER_30, TeleportCapability.OVER_20,
			TeleportCapability.OVER_0, TeleportCapability.ALL};
		assertEquals(order.length, TeleportCapability.values().length);
		for (int i = 0; i < order.length; i++)
			for (int j = 0; j < order.length; j++)
				assertEquals(order[i] + " allows " + order[j], i >= j, order[i].allows(order[j]));
	}

	private static TeleportCapability at(int x, int y)
	{
		return TeleportCapability.at(WorldPointUtil.packWorldPoint(x, y, 0));
	}
}
