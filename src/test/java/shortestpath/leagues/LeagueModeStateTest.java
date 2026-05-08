package shortestpath.leagues;

import java.util.EnumSet;
import org.junit.Test;
import shortestpath.WorldPointUtil;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LeagueModeStateTest
{
	private static final int VARROCK_WEST = WorldPointUtil.packWorldPoint(3186, 3438, 0);
	private static final int CIVITAS = WorldPointUtil.packWorldPoint(1781, 3100, 0);

	@Test
	public void nonSeasonalReportsEverythingUnlocked()
	{
		LeagueModeState state = new LeagueModeState();
		state.setForTest(false, EnumSet.noneOf(LeagueRegion.class));

		assertTrue(state.isUnlocked(LeagueRegion.MISTHALIN));
		assertTrue(state.isUnlocked(LeagueRegion.KOUREND));
		assertFalse(state.isInBlockedRegion(VARROCK_WEST));
	}

	@Test
	public void seasonalBlocksMisthalinAndLocksUnpickedRegions()
	{
		LeagueModeState state = new LeagueModeState();
		state.setForTest(true, EnumSet.of(LeagueRegion.KANDARIN));

		assertTrue(state.isUnlocked(LeagueRegion.VARLAMORE));
		assertTrue(state.isUnlocked(LeagueRegion.KARAMJA));
		assertTrue(state.isUnlocked(LeagueRegion.NEUTRAL));
		assertTrue(state.isUnlocked(LeagueRegion.KANDARIN));
		assertFalse(state.isUnlocked(LeagueRegion.MISTHALIN));
		assertFalse(state.isUnlocked(LeagueRegion.MORYTANIA));

		assertTrue(state.isInBlockedRegion(VARROCK_WEST));
		assertFalse(state.isInBlockedRegion(CIVITAS));
	}

	@Test
	public void seasonalAllowsExplicitVarlamoreAndKaramjaWithoutPicks()
	{
		LeagueModeState state = new LeagueModeState();
		state.setForTest(true, EnumSet.noneOf(LeagueRegion.class));

		assertTrue(state.isUnlocked(LeagueRegion.VARLAMORE));
		assertTrue(state.isUnlocked(LeagueRegion.KARAMJA));
		assertFalse(state.isUnlocked(LeagueRegion.WILDERNESS));
	}
}
