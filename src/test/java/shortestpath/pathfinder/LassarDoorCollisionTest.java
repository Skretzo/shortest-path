package shortestpath.pathfinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LassarDoorCollisionTest
{
	@Test
	public void doorsBlockBothDirections()
	{
		CollisionMap map = new CollisionMap(SplitFlagMap.fromResources());
		for (int y : new int[]{6432, 6433})
		{
			assertFalse(map.e(2669, y, 0));
			assertFalse(map.w(2670, y, 0));
			assertTrue(map.wallE(2669, y, 0));
		}
	}
}
