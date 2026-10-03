package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import shortestpath.WorldPointUtil;

/**
 * Pop-order contract tests for {@link IntMinHeap}: nodes must dequeue in the
 * ({@link NodeGraph#compareCost}, node id) total order. Node ids are creation order, so
 * equal-cost nodes pop FIFO — before this ordering existed, equal-cost pops followed
 * heap-internal order dependent on the insertion sequence, making the chosen equal-cost
 * path nondeterministic across runs.
 */
public class IntMinHeapTest
{
	private static int pack(int x, int y)
	{
		return WorldPointUtil.packWorldPoint(x, y, 0);
	}

	private static int startNode(NodeGraph graph)
	{
		return graph.createStart(pack(3200, 3200));
	}

	@Test
	public void equalCostNodesPopInCreationOrder()
	{
		NodeGraph graph = new NodeGraph(16);
		int start = startNode(graph);
		// All one step from the start tile: differentialCost is 0 for tiles,
		// so compareCost == cost == 1 for each.
		int first = graph.createTile(pack(3201, 3200), start, false);
		int second = graph.createTile(pack(3200, 3201), start, false);

		IntMinHeap heap = new IntMinHeap(graph, 8);
		heap.add(first);
		heap.add(second);

		assertEquals("Equal-cost nodes must pop in creation order", first, heap.poll());
		assertEquals(second, heap.poll());
		assertEquals(NodeGraph.NO_NODE, heap.poll());
	}

	@Test
	public void manyEqualCostNodesPopFifo()
	{
		NodeGraph graph = new NodeGraph(16);
		int start = startNode(graph);
		int[] nodes = {
			graph.createTile(pack(3201, 3200), start, false),
			graph.createTile(pack(3200, 3201), start, false),
			graph.createTile(pack(3199, 3200), start, false),
			graph.createTile(pack(3200, 3199), start, false),
			graph.createTile(pack(3201, 3201), start, false),
		};

		IntMinHeap heap = new IntMinHeap(graph, 4);
		// Insert out of id order; the id tiebreak must still yield FIFO by creation.
		heap.add(nodes[2]);
		heap.add(nodes[0]);
		heap.add(nodes[4]);
		heap.add(nodes[1]);
		heap.add(nodes[3]);

		for (int i = 0; i < nodes.length; i++)
		{
			assertEquals("Pop " + i + " must be the i-th created equal-cost node", nodes[i], heap.poll());
		}
	}

	@Test
	public void lowerCostPopsFirstRegardlessOfInsertionOrder()
	{
		NodeGraph graph = new NodeGraph(16);
		int start = startNode(graph);
		int far = graph.createTile(pack(3205, 3200), start, false);   // cost 5, created first
		int near = graph.createTile(pack(3201, 3200), start, false);  // cost 1

		IntMinHeap heap = new IntMinHeap(graph, 8);
		heap.add(far);
		heap.add(near);

		assertEquals("Cost must dominate the ordering", near, heap.poll());
		assertEquals(far, heap.poll());
	}

	@Test
	public void mixedCostsFollowCostThenCreationOrder()
	{
		NodeGraph graph = new NodeGraph(16);
		int start = startNode(graph);
		int a = graph.createTile(pack(3202, 3200), start, false); // cost 2
		int b = graph.createTile(pack(3201, 3200), start, false); // cost 1
		int c = graph.createTile(pack(3200, 3202), start, false); // cost 2
		int d = graph.createTile(pack(3199, 3200), start, false); // cost 1
		int e = graph.createTile(pack(3203, 3200), start, false); // cost 3

		IntMinHeap heap = new IntMinHeap(graph, 8);
		heap.add(a);
		heap.add(b);
		heap.add(c);
		heap.add(d);
		heap.add(e);

		assertEquals(b, heap.poll());
		assertEquals(d, heap.poll());
		assertEquals(a, heap.poll());
		assertEquals(c, heap.poll());
		assertEquals(e, heap.poll());
		assertEquals(NodeGraph.NO_NODE, heap.poll());
	}
}
