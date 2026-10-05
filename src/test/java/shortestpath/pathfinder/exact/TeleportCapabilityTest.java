package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

	@Test
	public void teleportAreasAreWhereTheCapabilityAllowsCasting()
	{
		for (TeleportCapability capability : TeleportCapability.values())
		{
			WalkGoal area = WalkGoal.teleportArea(capability);
			for (int x = 2900; x < 3500; x += 7)
				for (int y = 3500; y < 4250; y += 3)
				{
					assertEquals(capability + " " + x + "," + y, TeleportCapability.at(x, y).allows(capability),
						area.contains(x, y));
					assertEquals(area.contains(x, y), WalkGoal.ticks(area.lowerBound(x, y)) == 0);
				}
		}
		WalkGoal over20 = WalkGoal.teleportArea(TeleportCapability.OVER_20);
		WalkGoal over0 = WalkGoal.teleportArea(TeleportCapability.OVER_0);
		WalkGoal all = WalkGoal.teleportArea(TeleportCapability.ALL);
		// Straight south out of level 30 wilderness; out of level 20 wilderness, west is nearer.
		assertEquals(41, WalkGoal.ticks(over20.lowerBound(3000, 3800)));
		assertEquals(3000 - 2943, WalkGoal.ticks(over0.lowerBound(3000, 3800)));
		assertEquals(3700 - 3679, WalkGoal.ticks(over0.lowerBound(3100, 3700)));
		// The level-30 area runs further north than the level-20 one.
		assertEquals(4208 - 4150, WalkGoal.ticks(over0.lowerBound(3200, 4150)));
		// From the level-13 obelisk landing, Ferox Enclave is five tiles north.
		assertEquals(5, WalkGoal.ticks(all.lowerBound(3156, 3620)));
	}

	@Test
	public void teleportAreaBoundNeverOverestimatesAroundFeroxEnclave()
	{
		WalkGoal all = WalkGoal.teleportArea(TeleportCapability.ALL);
		for (int x = 3100; x < 3180; x += 3)
			for (int y = 3590; y < 3670; y += 3)
			{
				int nearest = Integer.MAX_VALUE;
				for (int gx = x - 100; gx <= x + 100; gx++)
					for (int gy = y - 100; gy <= y + 100; gy++)
						if (all.contains(gx, gy))
							nearest = Math.min(nearest, Math.max(Math.abs(gx - x), Math.abs(gy - y)));
				assertTrue(x + "," + y, WalkGoal.ticks(all.lowerBound(x, y)) <= nearest);
			}
	}

	private static TeleportCapability at(int x, int y)
	{
		return TeleportCapability.at(WorldPointUtil.packWorldPoint(x, y, 0));
	}
}
