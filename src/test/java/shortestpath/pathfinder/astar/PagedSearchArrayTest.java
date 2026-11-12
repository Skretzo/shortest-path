package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import org.junit.Test;

public class PagedSearchArrayTest
{
	@Test
	public void readsDoNotAllocateAndWritesAllocateOnlyTheirPage() throws Exception
	{
		for (int defaultValue : new int[] {0, CostArithmetic.INF})
		{
			PagedSearchArray array = new PagedSearchArray(2049, defaultValue);
			Field field = PagedSearchArray.class.getDeclaredField("pages");
			field.setAccessible(true);
			int[][] pages = (int[][]) field.get(array);
			assertEquals(3, pages.length);
			for (int index : new int[] {0, 1023, 1024, 2047, 2048})
				assertEquals(defaultValue, array.get(index));
			for (int[] page : pages) assertNull(page);

			array.set(1024, 7);
			assertNull(pages[0]);
			assertNull(pages[2]);
			int[] writtenPage = pages[1];
			assertEquals(1024, writtenPage.length);
			assertEquals(defaultValue, array.get(1025));
			array.set(1024, 3);
			array.set(2047, 0);
			assertSame(writtenPage, pages[1]);
			assertEquals(3, array.get(1024));
			assertEquals(0, array.get(2047));

			array.set(1023, 11);
			array.set(2048, 19);
			assertEquals(11, array.get(1023));
			assertEquals(19, array.get(2048));
			assertEquals(defaultValue, array.get(0));
			assertEquals(defaultValue, new PagedSearchArray(2049, defaultValue).get(1024));
		}
	}
}
