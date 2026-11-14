package shortestpath.poh;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import net.runelite.api.CollisionDataFlag;
import org.junit.Test;

public class HousePathTest
{
	private static int[][] open(int size)
	{
		return new int[size][size];
	}

	@Test
	public void straightAcrossAnOpenRoom()
	{
		List<int[]> path = HousePath.find(open(10), 1, 1, List.of(new HousePath.Area(6, 1, 6, 1)));
		assertArrayEquals(new int[]{1, 1}, path.get(0));
		// Stops beside the object, not on it.
		assertArrayEquals(new int[]{5, 1}, path.get(path.size() - 1));
		assertEquals(5, path.size());
	}

	@Test
	public void standingBesideItIsAlreadyThere()
	{
		List<int[]> path = HousePath.find(open(10), 5, 1, List.of(new HousePath.Area(6, 1, 6, 1)));
		assertEquals(1, path.size());
	}

	@Test
	public void goesAroundAWallThroughTheDoorway()
	{
		// A wall along x = 4 (blocking east-west) with a gap at y = 8.
		int[][] flags = open(10);
		for (int y = 0; y < 10; y++)
		{
			if (y == 8)
			{
				continue;
			}
			flags[4][y] |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;
			flags[5][y] |= CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		List<int[]> path = HousePath.find(flags, 1, 1, List.of(new HousePath.Area(7, 1, 7, 1)));
		assertTrue(path.size() > 6);
		boolean throughGap = false;
		for (int i = 1; i < path.size(); i++)
		{
			int[] a = path.get(i - 1);
			int[] b = path.get(i);
			if (a[0] == 4 && b[0] == 5)
			{
				throughGap = a[1] == 8 && b[1] == 8;
			}
		}
		assertTrue("crosses the wall only at the doorway", throughGap);
	}

	@Test
	public void noDiagonalCutThroughACorner()
	{
		// The tiles east and north of (1, 1) are solid: the diagonal to (2, 2) is not allowed.
		int[][] flags = open(5);
		flags[2][1] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		flags[1][2] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		assertEquals(false, HousePath.canStep(flags, 1, 1, 1, 1, 5, 5));
		flags[1][2] = 0;
		assertEquals(false, HousePath.canStep(flags, 1, 1, 1, 1, 5, 5));
		flags[2][1] = 0;
		assertEquals(true, HousePath.canStep(flags, 1, 1, 1, 1, 5, 5));
	}

	@Test
	public void aWallBetweenTheTileAndTheObjectIsNotAGoal()
	{
		int[][] flags = open(6);
		// The object at (3, 3); the tile south of it has a wall on its north side.
		flags[3][2] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
		List<HousePath.Area> box = List.of(new HousePath.Area(3, 3, 3, 3));
		assertEquals(false, HousePath.isGoal(flags, 3, 2, box));
		assertEquals(true, HousePath.isGoal(flags, 2, 3, box));
	}

	@Test
	public void aWallMountIsUsedFromItsOwnTile()
	{
		// A mounted glory on the west wall of (5, 3): stand on (5, 3), not beside it.
		List<int[]> path = HousePath.find(open(10), 8, 3, List.of(new HousePath.Area(5, 3, 5, 3, true)));
		assertArrayEquals(new int[]{5, 3}, path.get(path.size() - 1));
		assertEquals(false, HousePath.isGoal(open(10), 6, 3, List.of(new HousePath.Area(5, 3, 5, 3, true))));
	}

	@Test
	public void unreachableIsEmpty()
	{
		int[][] flags = open(6);
		for (int i = 0; i < 6; i++)
		{
			flags[3][i] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		}
		assertTrue(HousePath.find(flags, 1, 1, List.of(new HousePath.Area(5, 1, 5, 1))).isEmpty());
		assertTrue(HousePath.find(flags, 1, 1, List.of()).isEmpty());
		assertTrue(HousePath.find(flags, 9, 9, List.of(new HousePath.Area(1, 1, 1, 1))).isEmpty());
	}

	@Test
	public void theNearestOfSeveralIsChosen()
	{
		List<int[]> path = HousePath.find(open(20), 2, 2,
			List.of(new HousePath.Area(15, 15, 15, 15), new HousePath.Area(2, 6, 2, 6)));
		assertArrayEquals(new int[]{2, 5}, path.get(path.size() - 1));
	}

	@Test
	public void aLargeObjectIsReachedFromAnySide()
	{
		// A 2x2 object at (4..5, 4..5), approached from the west.
		List<int[]> path = HousePath.find(open(10), 0, 5, List.of(new HousePath.Area(4, 4, 5, 5)));
		assertArrayEquals(new int[]{3, 5}, path.get(path.size() - 1));
	}
}
