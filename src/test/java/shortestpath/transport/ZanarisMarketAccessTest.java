package shortestpath.transport;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;

public class ZanarisMarketAccessTest
{
	@Test
	public void marketDoorsRequireADiamondToEnterAndAllowFreeExit()
	{
		CollisionMap map = new CollisionMap(SplitFlagMap.fromResources());
		Map<Integer, Set<Transport>> transports = ResourceLoader.transports();
		int[][] crossings = {
			{2469, 4438, 2470, 4438}, {2469, 4437, 2470, 4437},
			{2466, 4434, 2466, 4433}, {2465, 4434, 2465, 4433}
		};
		for (int[] crossing : crossings)
		{
			int outside = WorldPointUtil.packWorldPoint(crossing[0], crossing[1], 0);
			int inside = WorldPointUtil.packWorldPoint(crossing[2], crossing[3], 0);
			Assert.assertFalse(map.isBlocked(crossing[0], crossing[1], 0));
			Assert.assertFalse(map.isBlocked(crossing[2], crossing[3], 0));
			Assert.assertFalse(Arrays.stream(map.ordinaryWalkingNeighbors(outside)).anyMatch(p -> p == inside));
			Assert.assertFalse(Arrays.stream(map.ordinaryWalkingNeighbors(inside)).anyMatch(p -> p == outside));
			Transport entry = edge(transports, outside, inside);
			Assert.assertNotNull(entry.getItemRequirements());
			Assert.assertFalse(entry.getItemRequirements().isSatisfiedBy(Map.of(), Set.of(), 0));
			Assert.assertFalse(entry.getItemRequirements().isSatisfiedBy(Map.of(ItemID.UNCUT_DIAMOND, 1), Set.of(), 0));
			Assert.assertTrue(entry.getItemRequirements().isSatisfiedBy(Map.of(ItemID.DIAMOND, 1), Set.of(), 0));
			Transport exit = edge(transports, inside, outside);
			Assert.assertTrue(exit.getItemRequirements() == null
				|| exit.getItemRequirements().isSatisfiedBy(Map.of(), Set.of(), 0));
		}
	}

	private static Transport edge(Map<Integer, Set<Transport>> transports, int origin, int destination)
	{
		return transports.get(origin).stream()
			.filter(t -> t.getDestination() == destination && t.getObjectInfo().startsWith("Open Magic door "))
			.findFirst().orElseThrow(AssertionError::new);
	}
}
