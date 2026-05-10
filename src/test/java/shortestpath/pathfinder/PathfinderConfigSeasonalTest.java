package shortestpath.pathfinder;

import java.util.EnumSet;
import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.leagues.LeagueRegion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigSeasonalTest
{
	private static final int VARROCK_WEST = WorldPointUtil.packWorldPoint(3186, 3438, 0);
	private static final int LUMBRIDGE = WorldPointUtil.packWorldPoint(3225, 3220, 0);
	private static final int CIVITAS = WorldPointUtil.packWorldPoint(1781, 3100, 0);
	private static final int FORTIS_CIVITAS_WEST = WorldPointUtil.packWorldPoint(1646, 3112, 0);

	@Mock
	private Client client;
	@Mock
	private ShortestPathConfig config;

	private PathfinderConfig pathfinderConfig;

	@Before
	public void before()
	{
		pathfinderConfig = new TestPathfinderConfig(client, config);
	}

	@Test
	public void avoidBlockedRegionIsNoOpOffSeasonalWorlds()
	{
		LeagueModeState state = pathfinderConfig.getLeagueModeState();
		state.setForTest(false, EnumSet.noneOf(LeagueRegion.class));

		// Walking from Civitas towards Varrock would normally cross from Varlamore into Misthalin.
		// Off-seasonal we expect zero league filtering.
		assertFalse(pathfinderConfig.avoidBlockedRegion(CIVITAS, VARROCK_WEST, false));
		assertFalse(pathfinderConfig.avoidBlockedRegion(VARROCK_WEST, LUMBRIDGE, false));
	}

	@Test
	public void avoidBlockedRegionBlocksMisthalinEntryOnSeasonal()
	{
		LeagueModeState state = pathfinderConfig.getLeagueModeState();
		state.setForTest(true, EnumSet.of(LeagueRegion.KANDARIN));

		// Crossing FROM neutral/varlamore INTO Misthalin must be blocked.
		assertTrue(pathfinderConfig.avoidBlockedRegion(CIVITAS, VARROCK_WEST, false));
	}

	@Test
	public void avoidBlockedRegionPermitsMovementInsideMisthalin()
	{
		LeagueModeState state = pathfinderConfig.getLeagueModeState();
		state.setForTest(true, EnumSet.of(LeagueRegion.KANDARIN));

		// Both endpoints inside Misthalin must NOT be blocked, otherwise a player who
		// somehow lands inside the blocked region could not walk out.
		assertFalse(pathfinderConfig.avoidBlockedRegion(VARROCK_WEST, LUMBRIDGE, true));
	}

	@Test
	public void avoidBlockedRegionDoesNotInterfereWithVarlamoreInternalMoves()
	{
		LeagueModeState state = pathfinderConfig.getLeagueModeState();
		state.setForTest(true, EnumSet.noneOf(LeagueRegion.class));

		assertFalse(pathfinderConfig.avoidBlockedRegion(CIVITAS, FORTIS_CIVITAS_WEST, false));
	}
}
