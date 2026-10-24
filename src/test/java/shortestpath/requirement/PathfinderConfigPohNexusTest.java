package shortestpath.requirement;

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
		Set<PohNexusPortal> selected = Set.of(PohNexusPortal.ANNAKARL);

		assertTrue(PohService.isNexusPortalEnabled(selected, "Annakarl Portal"));
		assertFalse(PohService.isNexusPortalEnabled(selected, "Ardougne Portal"));
	}

	@Test
	public void testGroupedPortalFiltering()
	{
		Set<PohNexusPortal> selected = Set.of(PohNexusPortal.CAMELOT);

		assertTrue(PohService.isNexusPortalEnabled(selected, "Camelot Portal"));
		assertTrue(PohService.isNexusPortalEnabled(selected, "Seers' Village Portal"));

		selected = Set.<PohNexusPortal>of();
		assertFalse(PohService.isNexusPortalEnabled(selected, "Camelot Portal"));
		assertFalse(PohService.isNexusPortalEnabled(selected, "Seers' Village Portal"));
	}

	@Test
	public void testRespawnPortalFiltering()
	{
		Set<PohNexusPortal> selected = Set.of(PohNexusPortal.RESPAWN);
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertTrue(PohService.isNexusPortalEnabled(selected, displayInfo));
		}

		selected = Set.<PohNexusPortal>of();
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertFalse(PohService.isNexusPortalEnabled(selected, displayInfo));
		}
	}

	@Test
	public void testUnknownPortalRemainsEnabled()
	{
		assertTrue(PohService.isNexusPortalEnabled(
			Set.<PohNexusPortal>of(), "Boat Portal"));
	}
}
