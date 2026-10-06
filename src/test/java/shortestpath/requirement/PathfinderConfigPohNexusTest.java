package shortestpath.requirement;

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

		assertTrue(Requirements.isPohNexusPortalEnabled(selected, "Annakarl Portal"));
		assertFalse(Requirements.isPohNexusPortalEnabled(selected, "Ardougne Portal"));
	}

	@Test
	public void testGroupedPortalFiltering()
	{
		Set<PohNexusPortal> selected = EnumSet.of(PohNexusPortal.CAMELOT);

		assertTrue(Requirements.isPohNexusPortalEnabled(selected, "Camelot Portal"));
		assertTrue(Requirements.isPohNexusPortalEnabled(selected, "Seers' Village Portal"));

		selected = EnumSet.noneOf(PohNexusPortal.class);
		assertFalse(Requirements.isPohNexusPortalEnabled(selected, "Camelot Portal"));
		assertFalse(Requirements.isPohNexusPortalEnabled(selected, "Seers' Village Portal"));
	}

	@Test
	public void testRespawnPortalFiltering()
	{
		Set<PohNexusPortal> selected = EnumSet.of(PohNexusPortal.RESPAWN);
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertTrue(Requirements.isPohNexusPortalEnabled(selected, displayInfo));
		}

		selected = EnumSet.noneOf(PohNexusPortal.class);
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertFalse(Requirements.isPohNexusPortalEnabled(selected, displayInfo));
		}
	}

	@Test
	public void testUnknownPortalRemainsEnabled()
	{
		assertTrue(Requirements.isPohNexusPortalEnabled(
			EnumSet.noneOf(PohNexusPortal.class), "Boat Portal"));
	}
}
