package shortestpath.pathfinder.exact;

import java.util.Arrays;

/** Exact relaxed reverse labels with a clique oracle and sparse production path. */
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

	private ReverseLabels(TargetOverlay overlay, int[] labels, int[] generatorOrigins, int[] generatorWeights,
		int settled, int stale, int pushes, int sparseRelaxations, int explicitRelaxations,
		int attachmentRelaxations, long elapsedNanos, boolean sparse)
	{
		this.overlay = overlay;
		this.labels = labels;
		this.generatorOrigins = generatorOrigins;
		this.generatorWeights = generatorWeights;
		this.settled = settled;
		this.stale = stale;
		this.pushes = pushes;
		this.sparseRelaxations = sparseRelaxations;
		this.explicitRelaxations = explicitRelaxations;
		this.attachmentRelaxations = attachmentRelaxations;
		this.elapsedNanos = elapsedNanos;
		this.sparse = sparse;
	}

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
		Arrays.fill(labels, ExactCosts.INF);
		ExactMinHeap queue = new ExactMinHeap(Math.max(16, labels.length));
		int pre = overlay.targetState(false), post = overlay.targetState(true);
		labels[pre] = labels[post] = 0;
		queue.push(0, 0, pre);
		queue.push(0, 0, post);
		int settled = 0, stale = 0, pushes = 2;
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
						ExactCosts.add(cost, graph.reverseCost(edge))))
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
							ExactCosts.add(cost, distance(stat.siteTile(node), stat.siteTile(other)))))
							pushes++;
					}
				}
				if (overlay.synthetic() && attachedToTarget(stat.siteComponents(node), overlay.components())
					&& relax(labels, queue, overlay.targetState(banked),
						ExactCosts.add(cost, distance(stat.siteTile(node), overlay.packedTarget()))))
				{
					pushes++;
				}
			}
			else if (overlay.synthetic() && node == overlay.targetNode())
			{
				for (int i = 0; i < overlay.attachmentCount(); i++)
				{
					if (relax(labels, queue, SiteGraph.stateId(overlay.attachmentSite(i), banked),
						ExactCosts.add(cost, overlay.attachmentCost(i))))
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
		Arrays.fill(doubled, ExactCosts.INF);
		Arrays.fill(origins, -1);
		Arrays.fill(weights, ExactCosts.INF);
		ExactMinHeap queue = new ExactMinHeap(Math.max(16, stateCount));
		int pre = overlay.targetState(false), post = overlay.targetState(true);
		doubled[pre] = doubled[post] = 0;
		origins[pre] = pre;
		origins[post] = post;
		weights[pre] = weights[post] = 0;
		queue.push(0, 0, pre);
		queue.push(0, 0, post);
		int settled = 0, stale = 0, pushes = 2;
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
					int edgeCost = ExactCosts.twice(graph.reverseCost(edge));
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
			if (overlay.synthetic())
			{
				if (vertex == overlay.targetNode())
				{
					for (int i = 0; i < overlay.attachmentCount(); i++)
					{
						attachmentEdges++;
						if (relax(doubled, origins, weights, queue, state,
							SiteGraph.stateId(overlay.attachmentSite(i), banked),
							ExactCosts.twice(overlay.attachmentCost(i)), false))
							pushes++;
					}
				}
				else if (vertex < originalCount && attachedToTarget(stat.siteComponents(vertex), overlay.components()))
				{
					attachmentEdges++;
					if (relax(doubled, origins, weights, queue, state, overlay.targetState(banked),
						ExactCosts.twice(distance(stat.siteTile(vertex), overlay.packedTarget())), false))
						pushes++;
				}
			}
		}
		int[] labels = new int[routingNodes * 2];
		int[] routingOrigins = new int[routingNodes * 2];
		int[] routingWeights = new int[routingNodes * 2];
		for (int state = 0; state < labels.length; state++)
		{
			labels[state] = ExactCosts.halve(doubled[state]);
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
		return ExactCosts.twice(labels[state]);
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

	private static boolean relax(int[] labels, ExactMinHeap queue, int state, int candidate)
	{
		if (candidate == ExactCosts.INF || candidate >= labels[state])
			return false;
		labels[state] = candidate;
		queue.push(candidate, candidate, state);
		return true;
	}

	private static boolean relax(int[] distances, int[] origins, int[] weights, ExactMinHeap queue, int from,
		int next, int edgeCost, boolean startsGenerator)
	{
		int candidate = ExactCosts.add(distances[from], edgeCost);
		if (candidate == ExactCosts.INF || candidate >= distances[next])
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

	private static boolean attachedToTarget(int[] components, int[] targetComponents)
	{
		for (int left : components)
			for (int right : targetComponents)
				if (left == right)
					return true;
		return false;
	}

	private static int distance(int left, int right)
	{
		return shortestpath.WorldPointUtil.distanceBetween(left, right);
	}
}
