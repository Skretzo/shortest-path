package shortestpath.leagues;

import org.junit.Test;
import shortestpath.WorldPointUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LeagueRegionCheckerTest
{
	@Test
	public void resolvesKnownVarlamoreTile()
	{
		// Civitas illa Fortis east bank: chunk (27, 48) = 6960
		int packed = WorldPointUtil.packWorldPoint(1781, 3100, 0);
		assertEquals(LeagueRegion.VARLAMORE, LeagueRegionChecker.getRegion(packed));
	}

	@Test
	public void resolvesKnownMisthalinTile()
	{
		// Varrock west bank: chunk (49, 53) = 12597
		int packed = WorldPointUtil.packWorldPoint(3186, 3438, 0);
		assertEquals(LeagueRegion.MISTHALIN, LeagueRegionChecker.getRegion(packed));
		assertTrue(LeagueRegionChecker.isInMisthalin(packed));
	}

	@Test
	public void unmappedTileFallsBackToNeutral()
	{
		// chunk (10, 10) = 2570 - not in stub regions.tsv
		int packed = WorldPointUtil.packWorldPoint(640, 640, 0);
		assertEquals(LeagueRegion.NEUTRAL, LeagueRegionChecker.getRegion(packed));
		assertFalse(LeagueRegionChecker.isInMisthalin(packed));
	}

	@Test
	public void reloadOverridesMapping()
	{
		// Capture original
		int someTile = WorldPointUtil.packWorldPoint(640, 640, 0);
		LeagueRegionChecker.reload("2570\tWILDERNESS\n");
		try
		{
			assertEquals(LeagueRegion.WILDERNESS, LeagueRegionChecker.getRegion(someTile));
		}
		finally
		{
			// Reset to whatever the resource file ships
			LeagueRegionChecker.reload(null);
		}
	}
}
