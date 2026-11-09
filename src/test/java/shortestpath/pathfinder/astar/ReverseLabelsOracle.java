package shortestpath.pathfinder.astar;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;

/**
 * Reverse labels for tests: the transparent component-clique reference the sparse production labels
 * are checked against.
 */
public final class ReverseLabelsOracle
{
	private ReverseLabelsOracle()
	{
	}

	/** The labels production computes; every static, hand-built or derived, carries a sparse network. */
	public static ReverseLabels compute(TargetOverlay overlay)
	{
		return ReverseLabels.computeSparse(overlay);
	}

	/**
	 * Transparent component-clique correctness oracle: every pair of a component's sites walks at
	 * Chebyshev distance, or at the full {@link ExactWalks#pairDistances} table's distance in a
	 * component the network joins by exact walks.
	 */
	public static ReverseLabels computeClique(TargetOverlay overlay)
	{
		long started = System.nanoTime();
		SiteGraph graph = overlay.graph();
		RoutingStatic stat = overlay.routingStatic();
		int spatialCount = graph.spatialNodeCount();
		int[] labels = new int[overlay.queryNodeCount() * 2];
		Arrays.fill(labels, CostArithmetic.INF);
		AStarMinHeap queue = new AStarMinHeap(Math.max(16, labels.length));
		ExactWalks exactWalks = stat.exactWalks();
		Map<Integer, int[][]> pairTables = new HashMap<>();
		int pushes = 0;
		for (int target = 0; target < overlay.targetCount(); target++)
		{
			for (BankVisitState banked : BankVisitState.values())
			{
				if (!overlay.isTerminal(target, banked)) continue;
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
			BankVisitState banked = (state & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
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
					int[] sites = stat.componentSites(component);
					boolean exact = exactWalks != null && exactWalks.isExact(component);
					int[][] pairs = exact ? pairTables.computeIfAbsent(component,
						c -> ExactWalks.pairDistances(stat, c)) : null;
					int self = exact ? Arrays.binarySearch(sites, node) : -1;
					for (int i = 0; i < sites.length; i++)
					{
						int other = sites[i];
						// Relaxing `other` from this site is the walk other -> node.
						int walk = exact ? pairs[i][self] : distance(stat.siteTile(node), stat.siteTile(other));
						if (other != node && relax(labels, queue, SiteGraph.stateId(other, banked),
							CostArithmetic.add(cost, walk)))
							pushes++;
					}
				}
				// Synthetic targets only emit reverse attachments; non-terminal tile estimates
				// also consult the component's finite site labels in PreparedHeuristic.
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
			System.nanoTime() - started);
	}

	private static boolean relax(int[] labels, AStarMinHeap queue, int state, int candidate)
	{
		if (candidate == CostArithmetic.INF || candidate >= labels[state])
			return false;
		labels[state] = candidate;
		queue.push(candidate, candidate, state);
		return true;
	}

	private static int distance(int left, int right)
	{
		return WorldPointUtil.distanceBetween(left, right);
	}
}
