package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.WildernessChecker;

public class TeleportCapabilityTest
{
	@Test
	public void capabilityFollowsTheWildernessCheckerLevels()
	{
		for (int plane = 0; plane < 4; plane += 3)
			for (int x = 2900; x < 3500; x++)
				for (int[] ys : new int[][] {{3600, 4250}, {10000, 10420}})
					for (int y = ys[0]; y < ys[1]; y++)
					{
						int tile = WorldPointUtil.packWorldPoint(x, y, plane);
						TeleportCapability expected = WildernessChecker.isInLevel30Wilderness(tile)
							? TeleportCapability.NONE : WildernessChecker.isInLevel20Wilderness(tile)
							? TeleportCapability.WILDERNESS : TeleportCapability.ALL;
						assertEquals(x + "," + y + "," + plane, expected, TeleportCapability.at(tile));
					}
	}

	@Test
	public void teleportAreasAreWhereTheCapabilityAllowsCasting()
	{
		WalkGoal wilderness = WalkGoal.teleportArea(TeleportCapability.WILDERNESS);
		WalkGoal all = WalkGoal.teleportArea(TeleportCapability.ALL);
		for (int x = 2900; x < 3500; x += 7)
			for (int y = 3600; y < 4250; y += 3)
			{
				TeleportCapability capability = TeleportCapability.at(x, y);
				assertEquals(capability != TeleportCapability.NONE, wilderness.contains(x, y));
				assertEquals(capability == TeleportCapability.ALL, all.contains(x, y));
				assertEquals(wilderness.contains(x, y), WalkGoal.ticks(wilderness.lowerBound(x, y)) == 0);
				assertEquals(all.contains(x, y), WalkGoal.ticks(all.lowerBound(x, y)) == 0);
			}
		// Straight south out of level 30 wilderness; out of level 20 wilderness, west is nearer.
		assertEquals(41, WalkGoal.ticks(wilderness.lowerBound(3000, 3800)));
		assertEquals(3000 - 2943, WalkGoal.ticks(all.lowerBound(3000, 3800)));
		assertEquals(3700 - 3679, WalkGoal.ticks(all.lowerBound(3100, 3700)));
		// The level-30 area runs further north than the level-20 one.
		assertEquals(4208 - 4150, WalkGoal.ticks(all.lowerBound(3200, 4150)));
	}
}
