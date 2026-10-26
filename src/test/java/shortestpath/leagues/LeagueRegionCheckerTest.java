package shortestpath.leagues;

import org.junit.Test;
import shortestpath.WorldPointUtil;

import static org.junit.Assert.assertEquals;

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
	}

	@Test
	public void unmappedTileFallsBackToNeutral()
	{
		// chunk (10, 10) = 2570 - not in stub regions.tsv
		int packed = WorldPointUtil.packWorldPoint(640, 640, 0);
		assertEquals(LeagueRegion.NEUTRAL, LeagueRegionChecker.getRegion(packed));
	}
}
