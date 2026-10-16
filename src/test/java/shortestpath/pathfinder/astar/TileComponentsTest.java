package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;
import org.junit.Test;

public class TileComponentsTest
{
	@Test
	public void compactStorageKeepsUnsignedValuesThrough65535() throws Exception
	{
		TileComponents components = new TileComponents(3, 65536);
		components.set(0, 0);
		components.set(1, 32768);
		components.set(2, 65535);
		assertEquals(3, components.length());
		assertEquals(0, components.get(0));
		assertEquals(32768, components.get(1));
		assertEquals(65535, components.get(2));
		assertNotNull(storage(components, "compactIds"));
		assertNull(storage(components, "wideIds"));
	}

	@Test
	public void largerWorldUsesFullSizeValuesWithoutTruncating() throws Exception
	{
		TileComponents components = new TileComponents(2, 65537);
		components.set(0, 65535);
		components.set(1, 65536);
		assertEquals(65535, components.get(0));
		assertEquals(65536, components.get(1));
		assertNull(storage(components, "compactIds"));
		assertNotNull(storage(components, "wideIds"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsValuesOutsideTheWorldsComponentCount()
	{
		new TileComponents(1, 65536).set(0, 65536);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativeValues()
	{
		new TileComponents(1, 1).set(0, -1);
	}

	private static Object storage(TileComponents components, String name) throws Exception
	{
		Field field = TileComponents.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(components);
	}
}
