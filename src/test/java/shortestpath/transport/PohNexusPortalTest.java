package shortestpath.transport;

import java.util.EnumSet;
import org.junit.Test;
import shortestpath.TestShortestPathConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PohNexusPortalTest
{
	@Test
	public void testSimpleMappings()
	{
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
		for (String displayInfo : PohNexusPortal.RESPAWN.getDisplayInfos())
		{
			assertSame(PohNexusPortal.RESPAWN, PohNexusPortal.fromDisplayInfo(displayInfo));
		}
	}

	@Test
	public void testAllMappingsRoundTrip()
	{
		for (PohNexusPortal portal : PohNexusPortal.values())
		{
			for (String displayInfo : portal.getDisplayInfos())
			{
				assertSame(portal, PohNexusPortal.fromDisplayInfo(displayInfo));
			}
		}
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
		assertEquals(EnumSet.noneOf(PohNexusPortal.class), new TestShortestPathConfig().pohNexusPortals());
		TestShortestPathConfig legacyEnabled = new TestShortestPathConfig()
		{
			@Override
			public boolean useTeleportationPortalsPoh()
			{
				return true;
			}
		};
		assertEquals(EnumSet.allOf(PohNexusPortal.class), legacyEnabled.pohNexusPortals());
	}
}
