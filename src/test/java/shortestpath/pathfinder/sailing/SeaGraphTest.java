package shortestpath.pathfinder.sailing;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.PriorityQueue;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.TestWorldData;
import shortestpath.sailing.SeaArea;

/** Each kind's sea graph, as the collision map holds it. */
public class SeaGraphTest
{
	private static final CollisionMap MAP = new CollisionMap(TestWorldData.map());

	@Test
	public void everyKindHasOne()
	{
		for (SailingVessel.Kind kind : SailingVessel.Kind.values())
		{
			SeaGraph graph = SeaGraph.of(MAP, kind);
			assertTrue(kind + " has nodes", graph.size() > 1000);
			assertEquals(SailingMoorings.all().size(), graph.moorings);
		}
	}

	@Test
	public void itReadsBackAsWritten()
	{
		SeaGraph graph = SeaGraph.of(MAP, SailingVessel.Kind.SLOOP);
		SeaGraph read = SeaGraph.read(graph.kind(), graph.write());
		assertEquals(graph.size(), read.size());
		assertArrayEquals(graph.runOffsets, read.runOffsets);
		assertArrayEquals(graph.runTargets, read.runTargets);
		assertArrayEquals(graph.runAreas, read.runAreas);
		assertArrayEquals(graph.dockMooring, read.dockMooring);
		assertArrayEquals(graph.launch, read.launch);
		assertArrayEquals(graph.legTiles, read.legTiles, 0);
	}

	@Test
	public void runsSeeEachOtherAndCarryTheAreasTheyCross()
	{
		SailingWaters waters = SailingWaters.of(MAP);
		for (SailingVessel.Kind kind : SailingVessel.Kind.values())
		{
			SeaGraph graph = SeaGraph.of(MAP, kind);
			int checked = 0;
			for (int i = 0; i < graph.size(); i += 37)
			{
				for (int k = graph.runOffsets[i]; k < graph.runOffsets[i + 1]; k += 11)
				{
					int j = graph.runTargets[k];
					assertTrue(graph.sees(graph.x[i], graph.y[i], graph.x[j], graph.y[j]));
					// Every area on the tiles along the run is in its set
					double length = Math.hypot(graph.x[j] - graph.x[i], graph.y[j] - graph.y[i]);
					for (double t = 0; t <= 1; t += 0.1 / Math.max(1, length))
					{
						int tile = WorldPointUtil.packWorldPoint((int) Math.round(graph.x[i] + t * (graph.x[j] - graph.x[i])),
							(int) Math.round(graph.y[i] + t * (graph.y[j] - graph.y[i])), 0);
						SeaArea area = waters.area(tile);
						if (area != null) assertTrue((graph.runAreas[k] & (1 << area.ordinal())) != 0);
					}
					checked++;
				}
			}
			assertTrue(checked > 100);
		}
	}

	// The ticks of the quickest way from casting off at from to docking at to, on the graph
	static double ticks(SeaGraph graph, SailingMoves moves, SailingMooring from, SailingMooring to)
	{
		double[] cost = new double[graph.size()];
		Arrays.fill(cost, Double.MAX_VALUE);
		int start = graph.launch[from.id()];
		cost[start] = 0;
		PriorityQueue<double[]> open = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
		open.add(new double[] {cost[start], start});
		double best = Double.MAX_VALUE;
		while (!open.isEmpty())
		{
			double[] top = open.poll();
			int i = (int) top[1];
			if (top[0] > cost[i] || top[0] >= best) continue;
			if (i == graph.launch[to.id()]) best = Math.min(best, cost[i]);
			for (int k = graph.runOffsets[i]; k < graph.runOffsets[i + 1]; k++)
			{
				int j = graph.runTargets[k];
				double next = cost[i] + moves.openWaterTicks(graph.x[j] - graph.x[i], graph.y[j] - graph.y[i]) + graph.runExtra[k];
				if (next >= cost[j]) continue;
				cost[j] = next;
				open.add(new double[] {next, j});
			}
		}
		return best;
	}
}
