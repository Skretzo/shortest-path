package shortestpath.leagues;

import java.util.EnumSet;
import static org.junit.Assert.assertEquals;
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
		state.setForTest(true, EnumSet.of(LeagueRegion.KANDARIN, LeagueRegion.KARAMJA));

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
	public void seasonalStartsWithOnlyVarlamoreUnlocked()
	{
		// Pre-80-task state: only Varlamore. Karamja must require its
		// own varbit pick like every other region.
		LeagueModeState state = new LeagueModeState();
		state.setForTest(true, EnumSet.noneOf(LeagueRegion.class));

		assertTrue(state.isUnlocked(LeagueRegion.VARLAMORE));
		assertFalse(state.isUnlocked(LeagueRegion.KARAMJA));
		assertFalse(state.isUnlocked(LeagueRegion.WILDERNESS));
	}

	@Test
	public void areaSelectionVarbitsCoverAllSixSlots()
	{
		// Catches accidental drift if Jagex or RuneLite renumber the
		// LEAGUE_AREA_SELECTION_* varbits. Six contiguous IDs starting at
		// 10662 mirror the gameval VarbitID table.
		assertEquals(6, LeagueModeState.AREA_SELECTION_VARBITS.length);
		for (int i = 0; i < 6; i++)
		{
			assertEquals(LeagueModeState.AREA_SELECTION_VARBITS[i], 10662 + i);
		}
	}
}
