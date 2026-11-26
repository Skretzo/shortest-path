package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.TestShortestPathConfig;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.astar.PreparedRoutingAccount;
import shortestpath.settings.Settings;

/** The preferredBanks and costOtherBanks settings reach the account the search runs on. */
@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigPreferredBanksTest
{
	private static final int VARROCK_WEST = WorldPointUtil.packWorldPoint(3185, 3436, 0);
	private static final int VARROCK_EAST = WorldPointUtil.packWorldPoint(3253, 3420, 0);

	@Mock
	private Client client;
	private final TestShortestPathConfig config = new TestShortestPathConfig();
	private PathfinderConfig pathfinderConfig;

	@Before
	public void setup()
	{
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		config.set("costBankVisit", 2);
		config.set("costOtherBanks", 15);
		pathfinderConfig = new PathfinderConfig(client, config, Settings.wrap(config));
	}

	@Test
	public void otherBanksCostTheThresholdMore()
	{
		config.set("preferredBanks", "Varrock West");
		PreparedRoutingAccount account = account();
		assertEquals(2, account.bankVisitCost(VARROCK_WEST));
		assertEquals(17, account.bankVisitCost(VARROCK_EAST));
	}

	@Test
	public void noPreferredBankChargesEveryBankTheSame()
	{
		PreparedRoutingAccount account = account();
		assertEquals(2, account.bankVisitCost(VARROCK_WEST));
		assertEquals(2, account.bankVisitCost(VARROCK_EAST));
	}

	/** Standing beside a bank never sends the route to a preferred one further away. */
	@Test
	public void aBankNearTheStartCountsAsPreferred()
	{
		config.set("preferredBanks", "Varrock West");
		pathfinderConfig.refresh();
		int besideEast = WorldPointUtil.packWorldPoint(3253, 3425, 0);
		assertEquals(2, pathfinderConfig.prepareAStarRoutingAccount(true, false, besideEast).bankVisitCost(VARROCK_EAST));
		int pastTheThreshold = WorldPointUtil.packWorldPoint(3253, 3436, 0);
		assertEquals(17, pathfinderConfig.prepareAStarRoutingAccount(true, false, pastTheThreshold).bankVisitCost(VARROCK_EAST));
		assertEquals("another floor is not near", 17,
			pathfinderConfig.prepareAStarRoutingAccount(true, false, WorldPointUtil.packWorldPoint(3253, 3425, 1))
				.bankVisitCost(VARROCK_EAST));
	}

	private PreparedRoutingAccount account()
	{
		pathfinderConfig.refresh();
		return pathfinderConfig.prepareAStarRoutingAccount(true);
	}
}
