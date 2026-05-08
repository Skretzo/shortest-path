package shortestpath.leagues;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Locks down the {@link LeagueRegion#isAlwaysUnlocked()} and
 * {@link LeagueRegion#isAlwaysBlocked()} contract. The Demonic Pacts League
 * starts players locked to Varlamore alone — every other region (including
 * Karamja, which the wiki describes as a free unlock at 80 tasks) flows
 * through the same area-slot varbits and must be reported as <em>not</em>
 * always-unlocked here.
 */
public class LeagueRegionTest
{
	@Test
	public void onlyVarlamoreAndNeutralAreAlwaysUnlocked()
	{
		assertTrue(LeagueRegion.VARLAMORE.isAlwaysUnlocked());
		assertTrue(LeagueRegion.NEUTRAL.isAlwaysUnlocked());

		assertFalse(LeagueRegion.KARAMJA.isAlwaysUnlocked());
		assertFalse(LeagueRegion.ASGARNIA.isAlwaysUnlocked());
		assertFalse(LeagueRegion.KANDARIN.isAlwaysUnlocked());
		assertFalse(LeagueRegion.FREMENNIK.isAlwaysUnlocked());
		assertFalse(LeagueRegion.KOUREND.isAlwaysUnlocked());
		assertFalse(LeagueRegion.WILDERNESS.isAlwaysUnlocked());
		assertFalse(LeagueRegion.MORYTANIA.isAlwaysUnlocked());
		assertFalse(LeagueRegion.DESERT.isAlwaysUnlocked());
		assertFalse(LeagueRegion.TIRANNWN.isAlwaysUnlocked());
		assertFalse(LeagueRegion.MISTHALIN.isAlwaysUnlocked());
	}

	@Test
	public void misthalinIsTheOnlyAlwaysBlockedRegion()
	{
		for (LeagueRegion region : LeagueRegion.values())
		{
			assertEquals(
				"isAlwaysBlocked mismatch for " + region,
				region == LeagueRegion.MISTHALIN,
				region.isAlwaysBlocked());
		}
	}
}
