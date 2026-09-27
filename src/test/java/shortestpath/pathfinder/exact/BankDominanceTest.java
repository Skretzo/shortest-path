package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class BankDominanceTest
{
	@Test
	public void optimizedSearchMatchesUnprunedAndExercisesDominance()
	{
		RoutingStatic stat = dominanceStatic();
		PreparedRoutingAccount account = account();
		TargetOverlay target = new TargetOverlay(new SiteGraph(stat, account), emptyCollision(), D);
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(target, ReverseLabels.compute(target));

		ExactForwardSearch.Result oracle = ExactForwardSearch.search(target, heuristic, A, false);
		ExactForwardSearch.Result optimized = ExactForwardSearch.search(target, heuristic, A, true);

		assertEquals(oracle.reached(), optimized.reached());
		assertEquals(oracle.cost(), optimized.cost());
		assertEquals(12, optimized.cost());
		assertTrue(optimized.counters().bestBankUpdates() >= 2);
		assertTrue(optimized.counters().bankDominated() > 0);
		assertTrue(String.valueOf(optimized.counters().bankGlobalSuppressed()), optimized.counters().bankGlobalSuppressed() > 0);
		assertTrue(String.valueOf(optimized.counters().rekeys()), optimized.counters().rekeys() > 0);
		assertEquals(0, oracle.counters().bankGlobalSuppressed());
		assertEquals(0, oracle.counters().rekeys());
	}

	private static final int A = WorldPointUtil.packWorldPoint(1000, 1000, 0);
	private static final int X = WorldPointUtil.packWorldPoint(1000, 1001, 0);
	private static final int BANK1 = WorldPointUtil.packWorldPoint(1000, 1002, 0);
	private static final int BANK2 = WorldPointUtil.packWorldPoint(1000, 1003, 0);
	private static final int Y = WorldPointUtil.packWorldPoint(1000, 1004, 0);
	private static final int D = WorldPointUtil.packWorldPoint(1000, 1005, 0);

	private static RoutingStatic dominanceStatic()
	{
		try
		{
			return RoutingStatic.create(new int[0], new int[0], new byte[0], new int[0], new int[0],
				new int[] {A, X, BANK1, BANK2, Y, D}, new int[] {0, 1, 2, 3, 4, 5, 6},
				new int[] {0, 0, 0, 0, 1, 1}, 2, new int[] {0, 4, 6},
				new int[] {0, 1, 2, 3, 4, 5}, new int[] {BANK1, BANK2}, new int[0], new int[0], new int[0],
				6, 6, 0, 0, 0, new int[] {0, 0, 0, 0, 0, 0, 0}, new int[0], new int[0]);
		}
		catch (Exception error)
		{
			throw new AssertionError(error);
		}
	}

	private static PreparedRoutingAccount account()
	{
		return PreparedRoutingAccount.compile(
			TransportAvailabilityFixture.of(local(A, X, 1), local(A, BANK2, 5), local(X, BANK1, 1), local(BANK2, D, 7)),
			TransportAvailabilityFixture.of(local(BANK2, Y, 10), local(Y, D, 1), global(D, 100)), true, true, ignored -> 0);
	}

	private static Transport local(int origin, int destination, int duration)
	{
		return new Transport.TransportBuilder().origin(origin).destination(destination)
			.type(TransportType.TRANSPORT).duration(duration).build();
	}

	private static Transport global(int destination, int duration)
	{
		return new Transport.TransportBuilder().destination(destination)
			.type(TransportType.TELEPORTATION_ITEM).duration(duration).build();
	}

	private static CollisionMap emptyCollision()
	{
		return new CollisionMap(null)
		{
			@Override public int[] ordinaryWalkingNeighbors(int packedPoint)
		{ return new int[0];
		}
			@Override public boolean isBlocked(int x, int y, int z)
		{ return false;
		}
		};
	}
}
