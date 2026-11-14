package shortestpath.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import shortestpath.poh.HouseScan;
import shortestpath.poh.PohNexusPortal;

public class HouseMenuHighlightTest
{
	@Test
	public void boxLinesMatchByName()
	{
		assertTrue(HouseScan.isBoxLine("1: Emir's Arena", "1: Emir's Arena"));
		assertTrue(HouseScan.isBoxLine("1: Emir's Arena", "<col=ccccff>1</col>: Emir's Arena"));
		assertTrue(HouseScan.isBoxLine("A: Warriors' Guild", "Warriors' Guild"));
		assertFalse(HouseScan.isBoxLine("5: Burthorpe", "6: Barbarian Outpost"));
		assertFalse(HouseScan.isBoxLine("5: Burthorpe", ""));
		assertFalse(HouseScan.isBoxLine(null, "5: Burthorpe"));
	}

	@Test
	public void nexusLineKeys()
	{
		assertEquals("6", HouseScan.lineKey("<col=ffffff>6</col> : Barrows"));
		assertEquals("A", HouseScan.lineKey("a : Camelot"));
		assertEquals("F12", HouseScan.lineKey("F12: Weiss"));
		assertEquals(null, HouseScan.lineKey("Barrows"));
		assertEquals(null, HouseScan.lineKey(null));
	}

	@Test
	public void promptNamesTheKeyOrTheLine()
	{
		assertEquals("Press B", HouseMenuHighlightOverlay.keyPrompt("B", "Barrows"));
		assertEquals("Click Barrows", HouseMenuHighlightOverlay.keyPrompt(null, "Barrows"));
	}

	@Test
	public void pairedNexusDestinationsAreToldApart()
	{
		assertTrue(HouseScan.isNexusLineFor("Grand Exchange Portal", "<col=ffffff>B</col> : Grand Exchange"));
		assertFalse(HouseScan.isNexusLineFor("Grand Exchange Portal", "1 : Varrock"));
		assertTrue(HouseScan.isNexusLineFor("Varrock Portal", "1 : Varrock"));
		assertTrue(HouseScan.isNexusLineFor("Seers' Village Portal", "Seers' Village"));
		assertFalse(HouseScan.isNexusLineFor("Camelot Portal", "Seers' Village"));
		assertTrue(HouseScan.isNexusLineFor("Ghorrock Portal", "F : Frozen Waste Plateau (Ghorrock)"));
		assertFalse(HouseScan.isNexusLineFor("Barrows Portal", ""));
	}

	@Test
	public void nexusLinesMatchTheRouteDestination()
	{
		assertEquals(PohNexusPortal.fromDisplayInfo("Ardougne Portal"), HouseScan.nexusLine("<col=ffffff>3 : Ardougne"));
		assertEquals(PohNexusPortal.fromDisplayInfo("Grand Exchange Portal"), HouseScan.nexusLine("Grand Exchange"));
	}
}
