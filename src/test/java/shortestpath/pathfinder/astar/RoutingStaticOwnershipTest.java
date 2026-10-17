package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class RoutingStaticOwnershipTest
{
	@Test
	public void keepsTheSuppliedArraysAndDoesNotExposeThem() throws Exception
	{
		Map<String, Object> arrays = arrays();
		RoutingStatic stat = takeOwnership(arrays);
		for (Map.Entry<String, Object> entry : arrays.entrySet())
		{
			Field field = RoutingStatic.class.getDeclaredField(entry.getKey());
			field.setAccessible(true);
			assertSame(entry.getKey(), entry.getValue(), field.get(stat));
		}

		int[] components = stat.siteComponents(0);
		int[] sites = stat.componentSites(0);
		components[0] = 99;
		sites[0] = 99;
		assertArrayEquals(new int[] {0}, stat.siteComponents(0));
		assertArrayEquals(new int[] {0, 1}, stat.componentSites(0));
	}

	@Test(expected = IOException.class)
	public void stillRejectsInvalidArrayReferences() throws Exception
	{
		Map<String, Object> arrays = arrays();
		TileComponents invalid = new TileComponents(2, 100);
		invalid.set(0, 99);
		arrays.put("searchComponents", invalid);
		takeOwnership(arrays);
	}

	@Test
	public void neighbourOffsetsHandleAbsentAndLargestRowDistance() throws Exception
	{
		int count = 32769;
		Map<String, Object> arrays = arrays();
		int[] tiles = new int[count];
		for (int i = 0; i < count; i++) tiles[i] = i;
		char[] north = new char[count], south = new char[count];
		north[0] = 32768;
		south[32768] = 32768;
		arrays.put("searchTiles", tiles);
		arrays.put("searchComponents", new TileComponents(count, 1));
		arrays.put("walkingMasks", new byte[count]);
		arrays.put("northOffsets", north);
		arrays.put("southOffsets", south);
		RoutingStatic stat = takeOwnership(arrays);

		assertEquals(32768, stat.northNode(0));
		assertEquals(0, stat.southNode(32768));
		assertEquals(-1, stat.northNode(1));
		assertEquals(-1, stat.southNode(0));
	}

	@Test(expected = IOException.class)
	public void rejectsNeighbourOffsetOutsideTheTileArray() throws Exception
	{
		Map<String, Object> arrays = arrays();
		((char[]) arrays.get("northOffsets"))[0] = 2;
		takeOwnership(arrays);
	}

	@Test
	public void bankBitsKeepWordBoundariesAndLastPartialWord() throws Exception
	{
		int[] tiles = new int[130];
		for (int i = 0; i < tiles.length; i++) tiles[i] = i;
		int[] banks = {0, 63, 64, 129};
		RoutingStatic stat = RoutingStatic.takeOwnership(tiles, new TileComponents(130, 1), new byte[130],
			new char[130], new char[130], banks, new int[] {0, 1, 2, 3, 4}, new int[4], 1,
			new int[] {0, 4}, new int[] {0, 1, 2, 3}, banks, new int[0], new int[0], new int[0],
			4, 4, 0, 0, 0, new int[5], new int[0], new int[0]);

		for (int i = 0; i < tiles.length; i++)
			assertEquals(i == 0 || i == 63 || i == 64 || i == 129, stat.isBankNode(i));
		for (int i = 0; i < banks.length; i++) assertEquals(true, stat.isBankSite(i));
		assertFalse(stat.isBankSite(-1));
		RoutingStatic emptyBanks = RoutingStaticTestFixture.createT3();
		assertFalse(emptyBanks.isBankNode(0));
		assertFalse(emptyBanks.isBankSite(0));
	}

	private static Map<String, Object> arrays()
	{
		Map<String, Object> arrays = new LinkedHashMap<>();
		arrays.put("searchTiles", new int[] {10, 11});
		arrays.put("searchComponents", new TileComponents(2, 1));
		arrays.put("walkingMasks", new byte[] {4, 64});
		arrays.put("northOffsets", new char[2]);
		arrays.put("southOffsets", new char[2]);
		arrays.put("siteTiles", new int[] {10, 11});
		arrays.put("siteComponentOffsets", new int[] {0, 1, 2});
		arrays.put("siteComponentIds", new int[] {0, 0});
		arrays.put("componentSiteOffsets", new int[] {0, 2});
		arrays.put("componentSiteIds", new int[] {0, 1});
		arrays.put("reachableBankTiles", new int[] {11});
		arrays.put("crossingFromSite", new int[] {0});
		arrays.put("crossingToSite", new int[] {1});
		arrays.put("crossingCosts", new int[] {1});
		arrays.put("sparseOffsets", new int[] {0, 1, 2});
		arrays.put("sparseDestinations", new int[] {1, 0});
		arrays.put("sparseWeights", new int[] {2, 2});
		return arrays;
	}

	private static RoutingStatic takeOwnership(Map<String, Object> a) throws IOException
	{
		return RoutingStatic.takeOwnership((int[]) a.get("searchTiles"), (TileComponents) a.get("searchComponents"),
			(byte[]) a.get("walkingMasks"), (char[]) a.get("northOffsets"), (char[]) a.get("southOffsets"),
			(int[]) a.get("siteTiles"), (int[]) a.get("siteComponentOffsets"), (int[]) a.get("siteComponentIds"),
			1, (int[]) a.get("componentSiteOffsets"), (int[]) a.get("componentSiteIds"),
			(int[]) a.get("reachableBankTiles"), (int[]) a.get("crossingFromSite"), (int[]) a.get("crossingToSite"),
			(int[]) a.get("crossingCosts"), 2, 2, 0, 1, 2,
			(int[]) a.get("sparseOffsets"), (int[]) a.get("sparseDestinations"), (int[]) a.get("sparseWeights"));
	}
}
