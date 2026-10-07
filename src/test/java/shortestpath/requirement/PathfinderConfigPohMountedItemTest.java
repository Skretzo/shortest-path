package shortestpath.requirement;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.TestShortestPathConfig;
import shortestpath.transport.PohMountedItem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PathfinderConfigPohMountedItemTest
{
	private static final Map<PohMountedItem, String> OBJECTS = Map.of(
		PohMountedItem.GLORY, "Edgeville Amulet of Glory 13523",
		PohMountedItem.XERICS_TALISMAN, "Look out Xeric's Talisman 33411",
		PohMountedItem.DIGSITE_PENDANT, "Digsite Digsite Pendant 33417",
		PohMountedItem.MYTHICAL_CAPE, "Teleport Mythical cape 31986");

	@Test
	public void emptySelectionDisablesEveryMountedItem()
	{
		Set<PohMountedItem> selected = EnumSet.noneOf(PohMountedItem.class);
		for (String objectInfo : OBJECTS.values())
		{
			assertFalse(Requirements.isPohMountedItemEnabled(selected, objectInfo));
		}
	}

	@Test
	public void singletonSelectionsAreIndependent()
	{
		assertSelection(PohMountedItem.XERICS_TALISMAN);
		assertSelection(PohMountedItem.GLORY);
	}

	@Test
	public void allMountedItemsReproduceTheEnabledBehaviour()
	{
		Set<PohMountedItem> selected = EnumSet.allOf(PohMountedItem.class);
		for (String objectInfo : OBJECTS.values())
		{
			assertTrue(Requirements.isPohMountedItemEnabled(selected, objectInfo));
		}
	}

	@Test
	public void legacyBooleanSuppliesTheInitialMultiSelection()
	{
		assertTrue(new TestShortestPathConfig().pohMountedItems().containsAll(
			EnumSet.allOf(PohMountedItem.class)));
		TestShortestPathConfig legacyDisabled = new TestShortestPathConfig()
		{
			@Override
			public boolean usePohMountedItems()
			{
				return false;
			}
		};
		assertTrue(legacyDisabled.pohMountedItems().isEmpty());
	}

	@Test
	public void carriedXericsTalismanIsNotAMountedObject()
	{
		String carriedDisplayInfo = "Xeric's talisman: 1. Xeric's Lookout";
		assertNull(PohMountedItem.fromObjectInfo(carriedDisplayInfo));
		assertTrue(Requirements.isPohMountedItemEnabled(
			EnumSet.noneOf(PohMountedItem.class), carriedDisplayInfo));
	}

	private static void assertSelection(PohMountedItem selectedItem)
	{
		Set<PohMountedItem> selected = EnumSet.of(selectedItem);
		for (Map.Entry<PohMountedItem, String> entry : OBJECTS.entrySet())
		{
			assertSame(entry.getKey(), PohMountedItem.fromObjectInfo(entry.getValue()));
			if (entry.getKey().equals(selectedItem))
			{
				assertTrue(Requirements.isPohMountedItemEnabled(selected, entry.getValue()));
			}
			else
			{
				assertFalse(Requirements.isPohMountedItemEnabled(selected, entry.getValue()));
			}
		}
	}
}
