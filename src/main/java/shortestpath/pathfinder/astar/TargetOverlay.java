package shortestpath.pathfinder.astar;

import java.util.Arrays;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Query-local target sites and their point-attachment walking edges.
 *
 * <p>An overlay holds one or more target tiles; a search towards it ends at whichever target is
 * cheapest to reach. Targets that are static sites reuse their site node; every other target gets
 * its own synthetic node appended after the account graph's nodes, in target order.
 */
public final class TargetOverlay
{
	@Getter @Accessors(fluent = true) private final SiteGraph graph;
	private final RoutingStatic stat;
	@Getter @Accessors(fluent = true) private final PreparedRoutingAccount account;
	@Getter @Accessors(fluent = true) private final long accountFingerprint;
	private final CollisionMap collision;
	private final int[] packed;
	private final byte[] terminalLayers;
	private final int[] nodes;
	private final int[][] components;
	private final int[][] attachmentSites;
	private final int[][] attachmentCosts;
	private final int[] syntheticTargets;

	public TargetOverlay(SiteGraph graph, CollisionMap collision, int packed)
	{
		this(graph, collision, new int[] {packed});
	}

	public TargetOverlay(SiteGraph graph, CollisionMap collision, int[] packedTargets)
	{
		this(graph, collision, canonicalTargets(packedTargets, null));
	}

	/** Masks use 1 for carried and 2 for banked; duplicate tiles union their layers. */
	public TargetOverlay(SiteGraph graph, CollisionMap collision, int[] packedTargets, int[] terminalLayers)
	{
		this(graph, collision, canonicalTargets(packedTargets, java.util.Objects.requireNonNull(terminalLayers)));
	}

	TargetOverlay(SiteGraph graph, CollisionMap collision, long[] targets)
	{
		if (graph == null || collision == null)
			throw new NullPointerException();
		this.graph = graph;
		this.stat = graph.routingStatic();
		this.account = graph.preparedAccount();
		this.accountFingerprint = account.fingerprint();
		this.collision = collision;
		this.packed = new int[targets.length];
		this.terminalLayers = new byte[targets.length];
		for (int i = 0; i < targets.length; i++)
		{
			packed[i] = (int) (targets[i] >>> 2);
			this.terminalLayers[i] = (byte) (targets[i] & 3);
		}
		if (packed.length == 0)
			throw new IllegalArgumentException("target overlay needs at least one target");
		int count = packed.length;
		nodes = new int[count];
		components = new int[count][];
		attachmentSites = new int[count][];
		attachmentCosts = new int[count][];
		int[] synthetic = new int[count];
		int syntheticCount = 0;
		for (int target = 0; target < count; target++)
		{
			int existing = graph.nodeForTile(packed[target]);
			if (existing >= 0)
			{
				nodes[target] = existing;
				components[target] = stat.siteComponents(existing);
			}
			else
			{
				nodes[target] = graph.nodeCount() + syntheticCount;
				synthetic[syntheticCount++] = target;
				components[target] = stat.attachments(packed[target], collision);
			}
			attach(target);
		}
		syntheticTargets = Arrays.copyOf(synthetic, syntheticCount);
	}


	public RoutingStatic routingStatic()
	{
		return stat;
	}

	CollisionMap collision()
	{
		return collision;
	}

	public int queryNodeCount()
	{
		return graph.nodeCount() + syntheticTargets.length;
	}

	public int targetCount()
	{
		return packed.length;
	}
	/** The target tiles, in unsigned order. */
	public int[] packedTargets()
	{
		return packed.clone();
	}
	public int packedTarget(int target)
	{
		return packed[target];
	}
	/** Index of {@code tile} among the targets, or a negative value if it is not a target. */
	public int targetIndex(int tile)
	{
		if (packed.length == 1)
			return packed[0] == tile ? 0 : -1;
		int low = 0, high = packed.length - 1;
		while (low <= high)
		{
			int middle = (low + high) >>> 1, compare = Integer.compareUnsigned(packed[middle], tile);
			if (compare == 0) return middle;
			if (compare < 0) low = middle + 1; else high = middle - 1;
		}
		return -1;
	}
	public boolean isTarget(int tile)
	{
		return targetIndex(tile) >= 0;
	}
	public int targetLayers(int target)
	{
		return terminalLayers[target];
	}
	boolean isTerminal(int target, BankVisitState banked)
	{
		return (terminalLayers[target] & (banked == BankVisitState.BANKED ? 2 : 1)) != 0;
	}
	public boolean accepts(int tile, BankVisitState banked)
	{
		int target = targetIndex(tile);
		return target >= 0 && isTerminal(target, banked);
	}
	public int targetNode(int target)
	{
		return nodes[target];
	}
	public boolean synthetic(int target)
	{
		return nodes[target] >= graph.nodeCount();
	}
	public int[] components(int target)
	{
		return components[target].clone();
	}
	int[] componentsView(int target)
	{
		return components[target];
	}
	public int attachmentCount(int target)
	{
		return attachmentSites[target].length;
	}
	public int attachmentSite(int target, int index)
	{
		return attachmentSites[target][index];
	}
	public int attachmentCost(int target, int index)
	{
		return attachmentCosts[target][index];
	}
	public int targetState(int target, BankVisitState banked)
	{
		return SiteGraph.stateId(nodes[target], banked);
	}

	public int syntheticCount()
	{
		return syntheticTargets.length;
	}
	/** The target index owning synthetic node {@code node}, or a negative value for any other node. */
	int syntheticTarget(int node)
	{
		int index = node - graph.nodeCount();
		return index >= 0 && index < syntheticTargets.length ? syntheticTargets[index] : -1;
	}
	/** Tile of a spatial site node or a synthetic target node. */
	int nodeTile(int node)
	{
		int target = syntheticTarget(node);
		return target >= 0 ? packed[target] : stat.siteTile(node);
	}
	/** Routing components of a spatial site node or a synthetic target node. */
	int[] nodeComponents(int node)
	{
		int target = syntheticTarget(node);
		return target >= 0 ? components[target] : stat.siteComponents(node);
	}

	/** The only target's tile; single-target overlays only. */
	public int packedTarget()
	{
		return packed[single()];
	}
	/** The only target's node; single-target overlays only. */
	public int targetNode()
	{
		return nodes[single()];
	}
	/** Whether the only target is synthetic; single-target overlays only. */
	public boolean synthetic()
	{
		return synthetic(single());
	}
	/** The only target's routing components; single-target overlays only. */
	public int[] components()
	{
		return components(single());
	}
	/** The only target's attachment count; single-target overlays only. */
	public int attachmentCount()
	{
		return attachmentCount(single());
	}
	/** The only target's attachment site; single-target overlays only. */
	public int attachmentSite(int index)
	{
		return attachmentSite(single(), index);
	}
	/** The only target's attachment cost; single-target overlays only. */
	public int attachmentCost(int index)
	{
		return attachmentCost(single(), index);
	}
	/** The only target's state; single-target overlays only. */
	public int targetState(BankVisitState banked)
	{
		return targetState(single(), banked);
	}

	void requireCompatible(SiteGraph candidate)
	{
		if (candidate.routingStatic() != stat || candidate.preparedAccount() != account
			|| candidate.preparedAccount().fingerprint() != accountFingerprint)
		{
			throw new IllegalArgumentException("target overlay belongs to another static/account snapshot");
		}
	}

	private int single()
	{
		if (packed.length != 1)
			throw new IllegalStateException("overlay has " + packed.length + " targets");
		return 0;
	}

	private void attach(int target)
	{
		int[] targetComponents = components[target];
		int capacity = 0;
		for (int component : targetComponents)
			capacity += stat.componentSites(component).length;
		int[] sites = new int[capacity];
		int count = 0;
		for (int component : targetComponents)
		{
			for (int site : stat.componentSites(component))
			{
				if (firstSharedComponent(targetComponents, site) == component)
					sites[count++] = site;
			}
		}
		attachmentSites[target] = Arrays.copyOf(sites, count);
		attachmentCosts[target] = new int[count];
		// Only a synthetic target's attachments enter the reverse search. Walking distances rather
		// than Chebyshev ones keep a target deep in a winding area from looking close to its
		// entrances, which would otherwise make the search flood everything within that slack.
		TargetWalkDistances walk = nodes[target] >= graph.nodeCount()
			? TargetWalkDistances.search(stat, packed[target], targetComponents) : null;
		for (int i = 0; i < count; i++)
		{
			int site = stat.siteTile(attachmentSites[target][i]);
			int chebyshev = distance(packed[target], site);
			attachmentCosts[target][i] = walk == null ? chebyshev : walk.bound(site, chebyshev);
		}
	}

	private int firstSharedComponent(int[] targetComponents, int site)
	{
		for (int component : targetComponents)
		{
			for (int siteComponent : stat.siteComponents(site))
				if (siteComponent == component)
					return component;
		}
		throw new IllegalStateException("target attachment missing shared routing component");
	}

	/** Encodes each unsigned tile followed by its two layer bits; null layers means either. */
	static long[] canonicalTargets(int[] values, int[] layers)
	{
		if (values == null) throw new NullPointerException("targets");
		if (layers != null && values.length != layers.length)
			throw new IllegalArgumentException("one layer mask is required per target");
		long[] sorted = new long[values.length];
		for (int i = 0; i < values.length; i++)
		{
			int mask = layers == null ? 3 : layers[i];
			if (mask < 1 || mask > 3) throw new IllegalArgumentException("target layers must be 1, 2 or 3");
			sorted[i] = Integer.toUnsignedLong(values[i]) << 2 | mask;
		}
		Arrays.sort(sorted);
		int count = 0;
		for (int i = 0; i < sorted.length; i++)
			if (count == 0 || (sorted[count - 1] >>> 2) != (sorted[i] >>> 2))
				sorted[count++] = sorted[i];
			else
				sorted[count - 1] |= sorted[i] & 3;
		return Arrays.copyOf(sorted, count);
	}

	private static int distance(int left, int right)
	{
		return shortestpath.WorldPointUtil.distanceBetween(left, right);
	}
}
