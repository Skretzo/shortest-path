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
		VisitedTiles visited = new VisitedTiles(collisionMap());
		int tile = WorldPointUtil.packWorldPoint(3200, 3200, 0);

		assertTrue(visited.set(tile, true));

		assertTrue(visited.get(tile, true));
		assertTrue(visited.get(tile, false));
		assertFalse(visited.set(tile, false));
	}

	@Test
	public void settingBankedAbstractNodeAlsoMarksUnbankedAbstractNodeVisited()
	{
		VisitedTiles visited = new VisitedTiles(collisionMap());
		NodeGraph graph = new NodeGraph(16);
		int banked = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, true);
		int unbanked = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, false);

		assertTrue(visited.set(banked, graph));

		assertTrue(visited.get(banked, graph));
		assertTrue(visited.get(unbanked, graph));
		assertFalse(visited.set(unbanked, graph));
	}

	@Test
	public void settingUnbankedNodeDoesNotMarkBankedNodeVisited()
	{
		VisitedTiles visited = new VisitedTiles(collisionMap());
		int tile = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		NodeGraph graph = new NodeGraph(16);
		int unbanked = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, false);
		int banked = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, NodeGraph.NO_NODE, true);

		assertTrue(visited.set(tile, false));
		assertTrue(visited.get(tile, false));
		assertFalse(visited.get(tile, true));

		assertTrue(visited.set(unbanked, graph));
		assertTrue(visited.get(unbanked, graph));
		assertFalse(visited.get(banked, graph));
	}
}
