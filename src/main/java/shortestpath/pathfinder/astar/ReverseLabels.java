package shortestpath.pathfinder.astar;

import java.util.Arrays;
import lombok.AllArgsConstructor;

/** Reverse labels in the relaxed graph with a clique oracle and sparse production path. */
@AllArgsConstructor
public final class ReverseLabels
{
	private final TargetOverlay overlay;
	private final int[] labels;
	private final int[] generatorOrigins;
	private final int[] generatorWeights;
	private final int settled;
	private final int stale;
	private final int pushes;
	private final int sparseRelaxations;
	private final int explicitRelaxations;
	private final int attachmentRelaxations;
	private final long elapsedNanos;
	private final boolean sparse;



	/** Production path; test fixtures without a sparse network fall back to the clique reference. */
	public static ReverseLabels compute(TargetOverlay overlay)
	{
		return usableSparse(overlay) ? computeSparse(overlay) : computeClique(overlay);
	}

	/** Transparent component-clique correctness oracle. */
	static ReverseLabels computeClique(TargetOverlay overlay)
	{
		long started = System.nanoTime();
		SiteGraph graph = overlay.graph();
		RoutingStatic stat = overlay.routingStatic();
		int spatialCount = graph.spatialNodeCount();
		int[] labels = new int[overlay.queryNodeCount() * 2];
		Arrays.fill(labels, CostArithmetic.INF);
		AStarMinHeap queue = new AStarMinHeap(Math.max(16, labels.length));
		int pushes = 0;
		for (int target = 0; target < overlay.targetCount(); target++)
		{
			for (boolean banked : new boolean[] {false, true})
			{
				int seed = overlay.targetState(target, banked);
				labels[seed] = 0;
				queue.push(0, 0, seed);
				pushes++;
			}
		}
		int settled = 0, stale = 0;
		while (queue.poll())
		{
			int state = queue.state(), cost = queue.cost();
			if (cost != labels[state])
			{
				stale++;
				continue;
			}
			settled++;
			int node = state / 2;
			boolean banked = (state & 1) != 0;
			if (node < graph.nodeCount())
			{
				for (int edge = graph.reverseEdgeStart(state); edge < graph.reverseEdgeEnd(state); edge++)
				{
					if (relax(labels, queue, graph.reverseFromState(edge),
						CostArithmetic.add(cost, graph.reverseCost(edge))))
						pushes++;
				}
			}
			if (node < spatialCount)
			{
				for (int component : stat.siteComponents(node))
				{
					for (int other : stat.componentSites(component))
					{
						if (other != node && relax(labels, queue, SiteGraph.stateId(other, banked),
							CostArithmetic.add(cost, distance(stat.siteTile(node), stat.siteTile(other)))))
							pushes++;
					}
				}
				// Targets are seeded at zero, so walking from a site into a target never improves it.
			}
			else
			{
				int target = overlay.syntheticTarget(node);
				for (int i = 0; target >= 0 && i < overlay.attachmentCount(target); i++)
				{
					if (relax(labels, queue, SiteGraph.stateId(overlay.attachmentSite(target, i), banked),
						CostArithmetic.add(cost, overlay.attachmentCost(target, i))))
						pushes++;
				}
			}
		}
		return new ReverseLabels(overlay, labels, null, null, settled, stale, pushes, 0, 0, 0,
			System.nanoTime() - started, false);
	}

	/** Sparse CSR reverse Dijkstra, including internal Steiner states. */
	static ReverseLabels computeSparse(TargetOverlay overlay)
	{
		long started = System.nanoTime();
		SiteGraph graph = overlay.graph();
		RoutingStatic stat = overlay.routingStatic();
		int routingNodes = overlay.queryNodeCount();
		int originalCount = stat.sparseOriginalCount();
		int stateCount = (routingNodes + stat.sparseSteinerCount()) * 2;
		int[] doubled = new int[stateCount];
		int[] origins = new int[stateCount];
		int[] weights = new int[stateCount];
		Arrays.fill(doubled, CostArithmetic.INF);
		Arrays.fill(origins, -1);
		Arrays.fill(weights, CostArithmetic.INF);
		AStarMinHeap queue = new AStarMinHeap(Math.max(16, stateCount));
		int pushes = 0;
		for (int target = 0; target < overlay.targetCount(); target++)
		{
			for (boolean banked : new boolean[] {false, true})
			{
				int seed = overlay.targetState(target, banked);
				doubled[seed] = 0;
				origins[seed] = seed;
				weights[seed] = 0;
				queue.push(0, 0, seed);
				pushes++;
			}
		}
		int settled = 0, stale = 0;
		int sparseEdges = 0, explicitEdges = 0, attachmentEdges = 0;
		while (queue.poll())
		{
			int state = queue.state(), cost = queue.cost();
			if (cost != doubled[state])
			{
				stale++;
				continue;
			}
			settled++;
			int vertex = state / 2;
			boolean banked = (state & 1) != 0;
			if (vertex < graph.nodeCount())
			{
				for (int edge = graph.reverseEdgeStart(state); edge < graph.reverseEdgeEnd(state); edge++)
				{
					explicitEdges++;
					int edgeCost = CostArithmetic.twice(graph.reverseCost(edge));
					if (relax(doubled, origins, weights, queue, state,
						graph.reverseFromState(edge), edgeCost, graph.reverseStartsGenerator(edge)))
						pushes++;
				}
			}
			if (vertex < originalCount || vertex >= routingNodes)
			{
				int sparseVertex = vertex < originalCount ? vertex : originalCount + vertex - routingNodes;
				for (int edge = stat.sparseOffset(sparseVertex); edge < stat.sparseOffset(sparseVertex + 1); edge++)
				{
					sparseEdges++;
					int destination = sparseNode(routingNodes, originalCount, stat.sparseDestination(edge));
					if (relax(doubled, origins, weights, queue, state, SiteGraph.stateId(destination, banked),
						stat.sparseWeight(edge), false))
						pushes++;
				}
			}
			// Targets are seeded at zero, so walking from a site into a target never improves it;
			// only a synthetic target's own attachment edges need relaxing.
			int target = vertex < routingNodes ? overlay.syntheticTarget(vertex) : -1;
			for (int i = 0; target >= 0 && i < overlay.attachmentCount(target); i++)
			{
				attachmentEdges++;
				if (relax(doubled, origins, weights, queue, state,
					SiteGraph.stateId(overlay.attachmentSite(target, i), banked),
					CostArithmetic.twice(overlay.attachmentCost(target, i)), false))
					pushes++;
			}
		}
		int[] labels = new int[routingNodes * 2];
		int[] routingOrigins = new int[routingNodes * 2];
		int[] routingWeights = new int[routingNodes * 2];
		for (int state = 0; state < labels.length; state++)
		{
			labels[state] = CostArithmetic.halve(doubled[state]);
			routingOrigins[state] = origins[state];
			routingWeights[state] = weights[state];
		}
		return new ReverseLabels(overlay, labels, routingOrigins, routingWeights, settled, stale, pushes,
			sparseEdges, explicitEdges, attachmentEdges, System.nanoTime() - started, true);
	}

	public TargetOverlay overlay()
	{
		return overlay;
	}
	public int label(int node, boolean banked)
	{
		return labels[SiteGraph.stateId(node, banked)];
	}
	public int targetLabel(boolean banked)
	{
		return labels[overlay.targetState(banked)];
	}
	public int targetLabel(int target, boolean banked)
	{
		return labels[overlay.targetState(target, banked)];
	}
	public int stateCount()
	{
		return labels.length;
	}
	public int[] labels()
	{
		return labels.clone();
	}
	public int settledCount()
	{
		return settled;
	}
	public int staleCount()
	{
		return stale;
	}
	public int pushCount()
	{
		return pushes;
	}
	public boolean sparse()
	{
		return sparse;
	}
	public int sparseRelaxationCount()
	{
		return sparseRelaxations;
	}
	public int explicitRelaxationCount()
	{
		return explicitRelaxations;
	}
	public int attachmentRelaxationCount()
	{
		return attachmentRelaxations;
	}
	public long elapsedNanos()
	{
		return elapsedNanos;
	}
	boolean hasProvenance()
	{
		return generatorOrigins != null;
	}
	int generatorOrigin(int state)
	{
		return generatorOrigins[state];
	}
	int generatorWeightDoubled(int state)
	{
		return generatorWeights[state];
	}
	int doubledLabel(int state)
	{
		return CostArithmetic.twice(labels[state]);
	}

	private static boolean usableSparse(TargetOverlay overlay)
	{
		RoutingStatic stat = overlay.routingStatic();
		return stat.sparseAdjacencyCount() != 0 || stat.siteCount() <= 1;
	}

	private static int sparseNode(int routingNodes, int originalCount, int sparseVertex)
	{
		return sparseVertex < originalCount ? sparseVertex : routingNodes + sparseVertex - originalCount;
	}

	private static boolean relax(int[] labels, AStarMinHeap queue, int state, int candidate)
	{
		if (candidate == CostArithmetic.INF || candidate >= labels[state])
			return false;
		labels[state] = candidate;
		queue.push(candidate, candidate, state);
		return true;
	}

	private static boolean relax(int[] distances, int[] origins, int[] weights, AStarMinHeap queue, int from,
		int next, int edgeCost, boolean startsGenerator)
	{
		int candidate = CostArithmetic.add(distances[from], edgeCost);
		if (candidate == CostArithmetic.INF || candidate >= distances[next])
			return false;
		distances[next] = candidate;
		if (startsGenerator)
		{
			origins[next] = next;
			weights[next] = candidate;
		}
		else
		{
			origins[next] = origins[from];
			weights[next] = weights[from];
		}
		queue.push(candidate, candidate, next);
		return true;
	}

	private static int distance(int left, int right)
	{
		return shortestpath.WorldPointUtil.distanceBetween(left, right);
	}
}
