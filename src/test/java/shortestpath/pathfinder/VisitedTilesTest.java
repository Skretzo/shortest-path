package shortestpath.pathfinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import shortestpath.WorldPointUtil;

public class VisitedTilesTest
{
	private static CollisionMap collisionMap()
	{
		return new CollisionMap(SplitFlagMap.fromResources());
	}

	@Test
	public void settingBankedTileAlsoMarksUnbankedTileVisited()
	{
		VisitedTiles visited = new VisitedTiles(collisionMap(), 0);
		int tile = WorldPointUtil.packWorldPoint(3200, 3200, 0);

		assertTrue(visited.set(tile, BankVisitState.BANKED));

		assertTrue(visited.get(tile, BankVisitState.BANKED));
		assertTrue(visited.get(tile, BankVisitState.CARRIED));
		assertFalse(visited.set(tile, BankVisitState.CARRIED));
	}

	@Test
	public void settingBankedAbstractNodeAlsoMarksUnbankedAbstractNodeVisited()
	{
		VisitedTiles visited = new VisitedTiles(collisionMap(), 0);
		NodeGraph graph = new NodeGraph(16);
		int banked = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, BankVisitState.BANKED);
		int unbanked = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, BankVisitState.CARRIED);

		assertTrue(visited.set(banked, graph));

		assertTrue(visited.get(banked, graph));
		assertTrue(visited.get(unbanked, graph));
		assertFalse(visited.set(unbanked, graph));
	}

	@Test
	public void settingUnbankedNodeDoesNotMarkBankedNodeVisited()
	{
		VisitedTiles visited = new VisitedTiles(collisionMap(), 0);
		int tile = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		NodeGraph graph = new NodeGraph(16);
		int unbanked = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, BankVisitState.CARRIED);
		int banked = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, BankVisitState.BANKED);

		assertTrue(visited.set(tile, BankVisitState.CARRIED));
		assertTrue(visited.get(tile, BankVisitState.CARRIED));
		assertFalse(visited.get(tile, BankVisitState.BANKED));

		assertTrue(visited.set(unbanked, graph));
		assertTrue(visited.get(unbanked, graph));
		assertFalse(visited.get(banked, graph));
	}
	@Test
	public void bankedVisitDoesNotMarkUnbankedBucketWhenBankVisitHasCost()
	{
		// With a nonzero bank visit cost a banked arrival is strictly more expensive
		// than an unbanked arrival to the same state, so it must not dominate it.
		VisitedTiles visited = new VisitedTiles(collisionMap(), 10);
		int tile = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		NodeGraph graph = new NodeGraph(16);
		int bankedAbstract = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, BankVisitState.BANKED);
		int unbankedAbstract = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, BankVisitState.CARRIED);

		assertTrue(visited.set(tile, BankVisitState.BANKED));
		assertTrue(visited.get(tile, BankVisitState.BANKED));
		assertFalse(visited.get(tile, BankVisitState.CARRIED));

		assertTrue(visited.set(bankedAbstract, graph));
		assertTrue(visited.get(bankedAbstract, graph));
		assertFalse(visited.get(unbankedAbstract, graph));
	}

	@Test
	public void cheaperUnbankedContinuationSurvivesEarlierBankedVisit()
	{
		// A costlier banked arrival marking the unbanked bucket would discard a cheaper
		// unbanked continuation that reaches the same state afterwards.
		VisitedTiles visited = new VisitedTiles(collisionMap(), 10);
		int tile = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		NodeGraph graph = new NodeGraph(16);
		int bankedAbstract = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, BankVisitState.BANKED);
		int unbankedAbstract = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, BankVisitState.CARRIED);

		assertTrue(visited.set(tile, BankVisitState.BANKED));
		assertTrue(visited.set(tile, BankVisitState.CARRIED));
		assertTrue(visited.set(bankedAbstract, graph));
		assertTrue(visited.set(unbankedAbstract, graph));
	}
}
