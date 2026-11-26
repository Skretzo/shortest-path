package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * A preferred bank costs the bank visit alone; any other bank costs {@code costOtherBanks} more.
 * The start reaches NEAR in 1 and FAR in 5, and the target only by a teleport taken from a bank.
 */
public class PreferredBankTest
{
	private static final int A = WorldPointUtil.packWorldPoint(1000, 1000, 0);
	private static final int X = WorldPointUtil.packWorldPoint(1000, 1001, 0);
	private static final int NEAR = WorldPointUtil.packWorldPoint(1000, 1002, 0);
	private static final int FAR = WorldPointUtil.packWorldPoint(1000, 1003, 0);
	private static final int Y = WorldPointUtil.packWorldPoint(1000, 1004, 0);
	private static final int D = WorldPointUtil.packWorldPoint(1000, 1005, 0);

	@Test
	public void noPreferenceUsesTheCheapestBank()
	{
		assertRoute(account(Set.of(), 10), 2, NEAR);
	}

	@Test
	public void preferredBankWinsWhenTheOtherSavesLessThanTheTolerance()
	{
		assertRoute(account(Set.of(FAR), 10), 6, FAR);
	}

	@Test
	public void otherBankWinsWhenItSavesMoreThanTheTolerance()
	{
		assertRoute(account(Set.of(FAR), 3), 5, NEAR);
	}

	@Test
	public void preferringTheCheapestBankChangesNothing()
	{
		assertRoute(account(Set.of(NEAR), 10), 2, NEAR);
	}

	@Test
	public void visitCostChargesOnlyOtherBanks()
	{
		PreparedRoutingAccount account = account(Set.of(FAR), 10);
		assertEquals(0, account.bankVisitCost());
		assertEquals(0, account.bankVisitCost(FAR));
		assertEquals(10, account.bankVisitCost(NEAR));
		assertEquals(0, account(Set.of(), 10).bankVisitCost(NEAR));
		assertNotEquals(account.fingerprint(), account(Set.of(NEAR), 10).fingerprint());
	}

	/**
	 * The optimized search reaches NEAR first, so its pruning must compare FAR by its own visit cost
	 * to still cast FAR's banked teleport; the unpruned search is the reference.
	 */
	private static void assertRoute(PreparedRoutingAccount account, int cost, int bank)
	{
		TargetOverlay target = new TargetOverlay(new SiteGraph(network(), account), emptyCollision(), D);
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(target, ReverseLabelsOracle.compute(target));
		AStarForwardSearch.Result oracle = AStarForwardSearch.search(target, heuristic, A, () -> false, false, 1,
			SearchRestrictions.none(), AStarForwardSearch.NO_PROGRESS);
		AStarForwardSearch.Result optimized = AStarForwardSearch.search(target, heuristic, A, () -> false, true, 1,
			SearchRestrictions.none(), AStarForwardSearch.NO_PROGRESS);

		assertTrue(oracle.reached());
		assertTrue(optimized.reached());
		assertEquals(cost, oracle.cost());
		assertEquals(cost, optimized.cost());
		assertEquals(bank, bankVisited(optimized));
	}

	private static int bankVisited(AStarForwardSearch.Result result)
	{
		PathStep previous = null;
		for (PathStep step : result.path())
		{
			if (step.getBankVisitState() == BankVisitState.BANKED)
				return previous == null ? step.getPackedPosition() : previous.getPackedPosition();
			previous = step;
		}
		throw new AssertionError("route never banks: " + result.path());
	}

	private static PreparedRoutingAccount account(Set<Integer> preferred, int otherBankCost)
	{
		return PreparedRoutingAccount.compile(
			TransportAvailabilityFixture.of(local(A, NEAR, 1), local(A, FAR, 5)),
			TransportAvailabilityFixture.of(global(D, 1)), true,
			Set.of(NEAR, FAR), 0, preferred, otherBankCost, true, ignored -> 0);
	}

	private static RoutingStatic network()
	{
		try
		{
			return RoutingStaticTestFixture.takeOwnershipWithNetwork(new int[0], new TileComponents(0, 1), new byte[0],
				new char[0], new char[0],
				new int[] {A, X, NEAR, FAR, Y, D}, new int[] {0, 1, 2, 3, 4, 5, 6},
				new int[] {0, 0, 0, 0, 1, 1}, 2, new int[] {0, 4, 6},
				new int[] {0, 1, 2, 3, 4, 5}, new int[] {NEAR, FAR}, new int[0], new int[0], new int[0]);
		}
		catch (Exception error)
		{
			throw new AssertionError(error);
		}
	}

	private static Transport local(int origin, int destination, int duration)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination)
			.type(TransportType.TRANSPORT).duration(duration).build();
	}

	private static Transport global(int destination, int duration)
	{
		return new Transport.TransportBuilder().destination(destination)
			.type(TransportType.TELEPORTATION_ITEM).duration(duration).maxWildernessLevel(0).build();
	}

	private static CollisionMap emptyCollision()
	{
		return new CollisionMap(null)
		{
			@Override
			public int[] ordinaryWalkingNeighbors(int packedPoint)
			{
				return new int[0];
			}

			@Override
			public boolean isBlocked(int x, int y, int z)
			{
				return false;
			}
		};
	}
}
