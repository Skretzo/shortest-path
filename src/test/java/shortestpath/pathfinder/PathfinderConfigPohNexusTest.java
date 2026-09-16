package shortestpath.pathfinder;

import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;
import shortestpath.transport.PohNexusPortal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathfinderConfigPohNexusTest
{
	@Test
	public void testSimplePortalFiltering()
	{
		Set<PohNexusPortal> selected = EnumSet.of(PohNexusPortal.ANNAKARL);

		assertTrue(PathfinderConfig.isPohNexusPortalEnabled(selected, "Annakarl Portal"));
		assertFalse(PathfinderConfig.isPohNexusPortalEnabled(selected, "Ardougne Portal"));
	}

	@Test
	public void testGroupedPortalFiltering()
	{
		Set<PohNexusPortal> selected = EnumSet.of(PohNexusPortal.CAMELOT);

		assertTrue(PathfinderConfig.isPohNexusPortalEnabled(selected, "Camelot Portal"));
		assertTrue(PathfinderConfig.isPohNexusPortalEnabled(selected, "Seers' Village Portal"));

		selected = EnumSet.noneOf(PohNexusPortal.class);
		assertFalse(PathfinderConfig.isPohNexusPortalEnabled(selected, "Camelot Portal"));
		assertFalse(PathfinderConfig.isPohNexusPortalEnabled(selected, "Seers' Village Portal"));
	}

	@Test
	public void testRespawnPortalFiltering()
	{
		Set<PohNexusPortal> selected = EnumSet.of(PohNexusPortal.RESPAWN);
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertTrue(PathfinderConfig.isPohNexusPortalEnabled(selected, displayInfo));
		}

		selected = EnumSet.noneOf(PohNexusPortal.class);
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertFalse(PathfinderConfig.isPohNexusPortalEnabled(selected, displayInfo));
		}
	}

	@Test
	public void testUnknownPortalRemainsEnabled()
	{
		assertTrue(PathfinderConfig.isPohNexusPortalEnabled(
			EnumSet.noneOf(PohNexusPortal.class), "Boat Portal"));
	}
}
