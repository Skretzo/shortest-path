package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.*;
import org.junit.Test;

public class NexusNamesTest
{
	@Test
	public void menuLines()
	{
		assertEquals(PohNexusPortal.VARROCK, NexusNames.portal("<col=ffffff>1 : Varrock"));
		assertEquals(PohNexusPortal.VARROCK, NexusNames.portal("Grand Exchange"));
		assertEquals(PohNexusPortal.CAMELOT, NexusNames.portal("Seers' Village"));
		assertEquals(PohNexusPortal.WATCHTOWER, NexusNames.portal("Yanille"));
		assertEquals(PohNexusPortal.GHORROCK, NexusNames.portal("F : Frozen Waste Plateau (Ghorrock)"));
		assertEquals(PohNexusPortal.KOUREND, NexusNames.portal("Kourend Castle"));
		assertEquals(PohNexusPortal.RESPAWN, NexusNames.portal("Respawn (Ferox Enclave)"));
		assertEquals(PohNexusPortal.FENKENSTRAINS_CASTLE, NexusNames.portal("Fenkenstrain's Castle"));
	}

	@Test
	public void otherLinesAreSkipped()
	{
		assertNull(NexusNames.portal("Slots"));
		assertNull(NexusNames.portal(""));
		assertNull(NexusNames.portal(null));
		assertNull(NexusNames.portal("Xeric's Talisman"));
	}

	@Test
	public void everyDestinationIsFoundByItsOwnNames()
	{
		for (PohNexusPortal portal : PohNexusPortal.values())
		{
			for (String name : portal.toString().split(" / "))
			{
				assertEquals(name, portal, NexusNames.portal(name));
			}
			for (String displayInfo : portal.displayInfos())
			{
				assertEquals(displayInfo, portal, NexusNames.portal(displayInfo));
			}
		}
	}

	@Test
	public void collectsASet()
	{
		assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE, PohNexusPortal.LUMBRIDGE),
			NexusNames.portals(List.of("1 : Ardougne", "Slots", "2 : Lumbridge", "3 : Lumbridge")));
	}
}
