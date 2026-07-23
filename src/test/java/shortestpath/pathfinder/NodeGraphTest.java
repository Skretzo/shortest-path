package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import shortestpath.SnapshotAssertions;
import shortestpath.WorldPointUtil;

/**
 * Pins down the cost formulas and flag bookkeeping of the structure-of-arrays node store so a
 * regression in any of the three distinct cost rules (tile walk, transport, abstract) fails fast.
 * The expected values mirror the maths of the old {@code Node}/{@code TransportNode} classes.
 */
public class NodeGraphTest
{
	@Test
	public void startNodeHasZeroCostAndNoPrevious()
	{
		NodeGraph graph = new NodeGraph(16);
		int start = graph.createStart(WorldPointUtil.packWorldPoint(3200, 3200, 0));

		assertEquals(0, graph.cost(start));
		assertEquals(NodeGraph.NO_NODE, graph.previous(start));
		assertTrue(graph.isTile(start));
		assertFalse(graph.isAbstract(start));
		assertFalse(graph.isTransport(start));
		assertFalse(graph.bankVisited(start));
	}

	@Test
	public void tileFromTileAddsWalkingDistance()
	{
		NodeGraph graph = new NodeGraph(16);
		int a = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int b = WorldPointUtil.packWorldPoint(3205, 3203, 0);
		int start = graph.createStart(a);
		int tile = graph.createTile(b, start, false);

		assertEquals(WorldPointUtil.distanceBetween(a, b), graph.cost(tile));
		assertEquals(start, graph.previous(tile));
		assertTrue(graph.isTile(tile));
		assertEquals(graph.cost(tile), graph.compareCost(tile));
	}

	@Test
	public void tileFromAbstractAddsNoWalkingDistance()
	{
		NodeGraph graph = new NodeGraph(16);
		int a = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int b = WorldPointUtil.packWorldPoint(3300, 3300, 0);
		int start = graph.createStart(a);
		int tileBeforeAbstract = graph.createTile(WorldPointUtil.packWorldPoint(3201, 3200, 0), start, false);
		int abstractNode = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, tileBeforeAbstract, true);
		int tileFromAbstract = graph.createTile(b, abstractNode, true);

		// Reaching a tile from an abstract node adds no travel cost: it inherits the abstract cost.
		assertEquals(graph.cost(abstractNode), graph.cost(tileFromAbstract));
		assertTrue(graph.bankVisited(tileFromAbstract));
	}

	@Test
	public void abstractInheritsPreviousCostAndCarriesKind()
	{
		NodeGraph graph = new NodeGraph(16);
		int start = graph.createStart(WorldPointUtil.packWorldPoint(3200, 3200, 0));
		int tile = graph.createTile(WorldPointUtil.packWorldPoint(3210, 3200, 0), start, false);
		int abstractNode = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_OVER_30, tile, false);

		assertEquals(graph.cost(tile), graph.cost(abstractNode));
		assertTrue(graph.isAbstract(abstractNode));
		assertFalse(graph.isTile(abstractNode));
		assertEquals(AbstractNodeKind.GLOBAL_TELEPORTS_OVER_30, graph.abstractKind(abstractNode));
		assertEquals(WorldPointUtil.UNDEFINED, graph.packedPosition(abstractNode));
	}

	@Test
	public void transportCostIsPreviousPlusTravelAndAdditionalWithNoDistance()
	{
		NodeGraph graph = new NodeGraph(16);
		int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int destination = WorldPointUtil.packWorldPoint(2800, 3400, 0);
		int start = graph.createStart(origin);
		int prev = graph.createTile(WorldPointUtil.packWorldPoint(3201, 3200, 0), start, false);

		int travelTime = 6;
		int additionalCost = 50;
		int differentialCost = 4;
		int transport = graph.createTransport(destination, prev, travelTime, additionalCost, false, true, differentialCost);

		// No walking-distance term for transports, unlike a walked tile.
		assertEquals(graph.cost(prev) + travelTime + additionalCost, graph.cost(transport));
		assertEquals(graph.cost(transport) + differentialCost, graph.compareCost(transport));
		assertTrue(graph.isTransport(transport));
		assertTrue(graph.isDelayedVisit(transport));
		assertTrue(graph.isTile(transport)); // transports are concrete tile destinations
		assertEquals(differentialCost, graph.differentialCost(transport));
	}

	@Test
	public void nonDelayedTransportHasNoDelayedFlagAndZeroDifferential()
	{
		NodeGraph graph = new NodeGraph(16);
		int start = graph.createStart(WorldPointUtil.packWorldPoint(3200, 3200, 0));
		int transport = graph.createTransport(WorldPointUtil.packWorldPoint(2800, 3400, 0), start, 6, 0, true, false, 0);

		assertTrue(graph.isTransport(transport));
		assertFalse(graph.isDelayedVisit(transport));
		assertEquals(0, graph.differentialCost(transport));
		assertEquals(graph.cost(transport), graph.compareCost(transport));
		assertTrue(graph.bankVisited(transport));
	}

	@Test
	public void pathStepsSkipAbstractNodesInOrder()
	{
		NodeGraph graph = new NodeGraph(16);
		int a = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int b = WorldPointUtil.packWorldPoint(3201, 3200, 0);
		int c = WorldPointUtil.packWorldPoint(2800, 3400, 0);
		int start = graph.createStart(a);
		int tile = graph.createTile(b, start, false);
		int abstractNode = graph.createAbstract(AbstractNodeKind.GLOBAL_TELEPORTS_NORMAL, tile, false);
		int teleportDest = graph.createTransport(c, abstractNode, 6, 0, false, false, 0);

		var steps = graph.getPathSteps(teleportDest);
		assertEquals(3, steps.size()); // start, tile, teleportDest (abstract is skipped)
		assertEquals(a, steps.get(0).getPackedPosition());
		assertEquals(b, steps.get(1).getPackedPosition());
		assertEquals(c, steps.get(2).getPackedPosition());

		assertEquals(c, graph.getClosestTilePosition(teleportDest));
		assertEquals(b, graph.getClosestTilePosition(abstractNode));

		SnapshotAssertions.assertRouteSnapshot("node-graph-path-steps-skip-abstract", steps);
	}

	@Test
	public void releaseMakesChainWalksReturnEmpty()
	{
		NodeGraph graph = new NodeGraph(16);
		int start = graph.createStart(WorldPointUtil.packWorldPoint(3200, 3200, 0));
		graph.release();

		assertTrue(graph.getPathSteps(start).isEmpty());
		assertEquals(WorldPointUtil.UNDEFINED, graph.getClosestTilePosition(start));
	}
}
