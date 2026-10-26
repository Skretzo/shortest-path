package shortestpath.poh;

import java.util.EnumSet;
import org.junit.Test;
import shortestpath.TestShortestPathConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import java.util.Set;
import java.util.HashSet;

public class PohNexusPortalTest
{
	@Test
	public void testSimpleMappings()
	{
		assertSame(PohNexusPortal.APE_ATOLL_DUNGEON, PohNexusPortal.fromDisplayInfo("Ape Atoll Dungeon Portal"));
		assertSame(PohNexusPortal.ANNAKARL, PohNexusPortal.fromDisplayInfo("Annakarl Portal"));
		assertSame(PohNexusPortal.ARCEUUS_LIBRARY, PohNexusPortal.fromDisplayInfo("Arceuus Library Portal"));
	}

	@Test
	public void testGroupedMappings()
	{
		assertSame(PohNexusPortal.CAMELOT, PohNexusPortal.fromDisplayInfo("Camelot Portal"));
		assertSame(PohNexusPortal.CAMELOT, PohNexusPortal.fromDisplayInfo("Seers' Village Portal"));
		assertSame(PohNexusPortal.VARROCK, PohNexusPortal.fromDisplayInfo("Varrock Portal"));
		assertSame(PohNexusPortal.VARROCK, PohNexusPortal.fromDisplayInfo("Grand Exchange Portal"));
		assertSame(PohNexusPortal.WATCHTOWER, PohNexusPortal.fromDisplayInfo("Watchtower Portal"));
		assertSame(PohNexusPortal.WATCHTOWER, PohNexusPortal.fromDisplayInfo("Yanille Portal"));
	}

	@Test
	public void testRespawnMappings()
	{
		assertSame(PohNexusPortal.RESPAWN, PohNexusPortal.fromDisplayInfo("Respawn Portal (Lumbridge)"));
		assertSame(PohNexusPortal.RESPAWN, PohNexusPortal.fromDisplayInfo("Respawn Portal (Prifddinas)"));
		assertSame(PohNexusPortal.RESPAWN, PohNexusPortal.fromDisplayInfo("Respawn Portal (Civitas illa Fortis)"));
	}

	@Test
	public void testUnknownMapping()
	{
		assertNull(PohNexusPortal.fromDisplayInfo("Boat Portal"));
		assertNull(PohNexusPortal.fromDisplayInfo(null));
	}

	@Test
	public void testLegacyConfigSuppliesTheInitialPortalSelection()
	{
		assertEquals(Set.<PohNexusPortal>of(), new TestShortestPathConfig().pohNexusPortals());
		TestShortestPathConfig legacyEnabled = new TestShortestPathConfig()
		{
			@Override
			public Object value(String keyName)
			{
				return "useTeleportationPortalsPoh".equals(keyName) ? true : super.value(keyName);
			}
		};
		assertEquals(new HashSet<>(java.util.Arrays.asList(PohNexusPortal.values())), legacyEnabled.pohNexusPortals());
	}
}
