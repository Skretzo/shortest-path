package shortestpath.requirement;

import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;
import shortestpath.poh.PohNexusPortal;
import shortestpath.poh.PohService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathfinderConfigPohNexusTest
{
	@Test
	public void testSimplePortalFiltering()
	{
		Set<PohNexusPortal> selected = EnumSet.of(PohNexusPortal.ANNAKARL);

		assertTrue(PohService.isNexusPortalEnabled(selected, "Annakarl Portal"));
		assertFalse(PohService.isNexusPortalEnabled(selected, "Ardougne Portal"));
	}

	@Test
	public void testGroupedPortalFiltering()
	{
		Set<PohNexusPortal> selected = EnumSet.of(PohNexusPortal.CAMELOT);

		assertTrue(PohService.isNexusPortalEnabled(selected, "Camelot Portal"));
		assertTrue(PohService.isNexusPortalEnabled(selected, "Seers' Village Portal"));

		selected = EnumSet.noneOf(PohNexusPortal.class);
		assertFalse(PohService.isNexusPortalEnabled(selected, "Camelot Portal"));
		assertFalse(PohService.isNexusPortalEnabled(selected, "Seers' Village Portal"));
	}

	@Test
	public void testRespawnPortalFiltering()
	{
		Set<PohNexusPortal> selected = EnumSet.of(PohNexusPortal.RESPAWN);
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertTrue(PohService.isNexusPortalEnabled(selected, displayInfo));
		}

		selected = EnumSet.noneOf(PohNexusPortal.class);
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertFalse(PohService.isNexusPortalEnabled(selected, displayInfo));
		}
	}

	@Test
	public void testUnknownPortalRemainsEnabled()
	{
		assertTrue(PohService.isNexusPortalEnabled(
			EnumSet.noneOf(PohNexusPortal.class), "Boat Portal"));
	}
}
