package shortestpath.pathfinder.astar;

import java.util.Arrays;
import shortestpath.*;
import shortestpath.pathfinder.*;
import lombok.*;
import lombok.experimental.Accessors;

/** Query-lifetime raw seeds and provenance-reduced exact seed scans. */
@AllArgsConstructor
public final class PreparedHeuristic
{
	private static final int[] EMPTY = new int[0];

	@Getter @Accessors(fluent = true) private final TargetOverlay overlay;
	private final ReverseLabels reverse;
	private final int[][] seedTiles;
	private final int[][] seedLabels;
	private final int[][] generatorTiles;
	private final int[][] generatorLabels;
	@Getter @Accessors(fluent = true) private final long preparationNanos;



	public static PreparedHeuristic prepare(TargetOverlay overlay, ReverseLabels reverse)
	{
		if (reverse == null || reverse.overlay() != overlay)
			throw new IllegalArgumentException("reverse labels belong to another target overlay");
		long started = System.nanoTime();
		int bucketCount = overlay.routingStatic().routingComponentCount() * 2;
		PrimitiveIntList[] raw = new PrimitiveIntList[bucketCount];
		PrimitiveIntList[] reduced = new PrimitiveIntList[bucketCount];
		RoutingStatic stat = overlay.routingStatic();
		SiteGraph graph = overlay.graph();
		for (int site = 0; site < graph.spatialNodeCount(); site++)
		{
			addSite(raw, reduced, reverse, overlay, site, stat.siteTile(site), stat.siteComponents(site),
				BankVisitState.CARRIED);
			addSite(raw, reduced, reverse, overlay, site, stat.siteTile(site), stat.siteComponents(site),
				BankVisitState.BANKED);
		}
		for (int target = 0; target < overlay.targetCount(); target++)
		{
			if (!overlay.synthetic(target))
				continue;
			addSite(raw, reduced, reverse, overlay, overlay.targetNode(target), overlay.packedTarget(target),
				overlay.componentsView(target), BankVisitState.CARRIED);
			addSite(raw, reduced, reverse, overlay, overlay.targetNode(target), overlay.packedTarget(target),
				overlay.componentsView(target), BankVisitState.BANKED);
		}
		int[][] tiles = new int[bucketCount][], labels = new int[bucketCount][];
		int[][] generatorTiles = new int[bucketCount][], generatorLabels = new int[bucketCount][];
		for (int i = 0; i < bucketCount; i++)
		{
			tiles[i] = raw[i] == null ? EMPTY : raw[i].column(2, 0);
			labels[i] = raw[i] == null ? EMPTY : raw[i].column(2, 1);
			generatorTiles[i] = reduced[i] == null ? EMPTY : byId(reduced[i], 1);
			generatorLabels[i] = reduced[i] == null ? EMPTY : byId(reduced[i], 2);
		}
		return new PreparedHeuristic(overlay, reverse, tiles, labels, generatorTiles, generatorLabels,
			System.nanoTime() - started);
	}


	public ReverseLabels reverseLabels()
	{
		return reverse;
	}
	public int seedCount(int component, BankVisitState banked)
	{
		return seedTiles[key(component, banked)].length;
	}
	public int seedTile(int component, BankVisitState banked, int index)
	{
		return seedTiles[key(component, banked)][index];
	}
	public int seedLabel(int component, BankVisitState banked, int index)
	{
		return seedLabels[key(component, banked)][index];
	}
	public int generatorCount(int component, BankVisitState banked)
	{
		return generatorTiles[key(component, banked)].length;
	}
	public int generatorTile(int component, BankVisitState banked, int index)
	{
		return generatorTiles[key(component, banked)][index];
	}
	public int generatorLabel(int component, BankVisitState banked, int index)
	{
		return generatorLabels[key(component, banked)][index];
	}
	public int rawSeedCount()
	{
		return total(seedTiles);
	}
	public int generatorCount()
	{
		return total(generatorTiles);
	}
	public double generatorRatio()
	{
		int raw = rawSeedCount();
		return raw == 0 ? 0 : (double) generatorCount() / raw;
	}


	public int estimate(int packed, BankVisitState banked)
	{
		return estimate(packed, banked, overlay.routingStatic().attachments(packed, overlay.collision()));
	}

	public int estimate(TargetOverlay candidate, int packed, BankVisitState banked)
	{
		candidate.requireCompatible(overlay.graph());
		if (candidate != overlay)
			throw new IllegalArgumentException("heuristic belongs to another target query");
		return estimate(packed, banked);
	}

	/** Evaluate against the collision snapshot used to create the target overlay. */
	public int estimate(CollisionMap collision, int packed, BankVisitState banked)
	{
		if (collision == null)
			throw new NullPointerException("collision");
		return estimate(packed, banked, overlay.routingStatic().attachments(packed, collision));
	}

	public int estimate(int packed, BankVisitState banked, int[] components)
	{
		return estimateTable(packed, banked, components, generatorTiles, generatorLabels, true);
	}

	public int estimateBaseNode(int packed, BankVisitState banked, int component)
	{
		int best = targetLabel(packed, banked);
		int site = overlay.routingStatic().siteIndex(packed);
		if (site >= 0) best = Math.min(best, reverse.label(site, banked));
		return Math.min(best,
			estimateComponent(packed, banked, component, generatorTiles, generatorLabels, true));
	}

	/** Raw seed-scan oracle retained for reduced-generator differential checks. */
	int estimateRaw(int packed, BankVisitState banked, int[] components)
	{
		return estimateTable(packed, banked, components, seedTiles, seedLabels, false);
	}

	int estimateGenerators(int packed, BankVisitState banked, int[] components)
	{
		return estimateTable(packed, banked, components, generatorTiles, generatorLabels, false);
	}

	private int estimateTable(int packed, BankVisitState banked, int[] components, int[][] tiles, int[][] labels,
		boolean fallbackToRaw)
	{
		RoutingStatic stat = overlay.routingStatic();
		int best = targetLabel(packed, banked);
		int site = stat.siteIndex(packed);
		if (site >= 0)
			best = Math.min(best, reverse.label(site, banked));
		for (int component : components)
			best = Math.min(best, estimateComponent(packed, banked, component, tiles, labels, fallbackToRaw));
		return best;
	}

	private int estimateComponent(int packed, BankVisitState banked, int component, int[][] tiles, int[][] labels,
		boolean fallbackToRaw)
	{
		int key = key(component, banked);
		int[] bucketTiles = tiles[key];
		int[] bucketLabels = labels[key];
		if (fallbackToRaw && bucketTiles.length == 0)
		{
			bucketTiles = seedTiles[key];
			bucketLabels = seedLabels[key];
		}
		int best = CostArithmetic.INF;
		for (int i = 0; i < bucketTiles.length; i++)
			best = Math.min(best, CostArithmetic.add(bucketLabels[i],
				WorldPointUtil.distanceBetween(packed, bucketTiles[i])));
		return best;
	}

	private static void addSite(PrimitiveIntList[] raw, PrimitiveIntList[] reduced, ReverseLabels reverse,
		TargetOverlay overlay, int node, int packed, int[] components, BankVisitState banked)
	{
		int state = SiteGraph.stateId(node, banked);
		int distance = reverse.label(node, banked);
		if (distance == CostArithmetic.INF)
			return;
		for (int component : components)
		{
			int key = key(component, banked);
			add(raw, key, packed, distance);
			if (!reverse.hasProvenance())
				continue;
			int origin = reverse.generatorOrigin(state);
			int generatorState = validOrigin(reverse, overlay, node, packed, components, component, state, origin)
				? origin : state;
			int generatorNode = generatorState / 2;
			int generatorTile = overlay.nodeTile(generatorNode);
			int generatorLabel = reverse.label(generatorNode,
				(generatorState & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED);
			if (reduced[key] == null)
				reduced[key] = new PrimitiveIntList(24);
			PrimitiveIntList triples = reduced[key];
			boolean known = false;
			for (int i = 0; i < triples.size() && !known; i += 3)
				known = triples.get(i) == generatorState;
			if (!known)
			{
				triples.add(generatorState);
				triples.add(generatorTile);
				triples.add(generatorLabel);
			}
		}
	}

	private static boolean validOrigin(ReverseLabels reverse, TargetOverlay overlay, int node, int packed,
		int[] components, int component, int state, int origin)
	{
		if (origin < 0 || (origin & 1) != (state & 1))
			return false;
		int originNode = origin / 2;
		if (originNode >= overlay.graph().spatialNodeCount() && overlay.syntheticTarget(originNode) < 0)
			return false;
		int originTile = overlay.nodeTile(originNode);
		int direct = WorldPointUtil.distanceBetween(originTile, packed);
		if (direct == CostArithmetic.INF)
			return false;
		if (reverse.generatorWeightDoubled(state) == CostArithmetic.INF
			|| reverse.doubledLabel(state) != CostArithmetic.add(reverse.generatorWeightDoubled(state), CostArithmetic.twice(direct)))
			return false;
		int[] originComponents = overlay.nodeComponents(originNode);
		return IntArrays.contains(originComponents, component);
	}

	private int targetLabel(int packed, BankVisitState banked)
	{
		int target = overlay.targetIndex(packed);
		return target < 0 ? CostArithmetic.INF : reverse.targetLabel(target, banked);
	}

	private static int key(int component, BankVisitState banked)
	{
		return component * 2 + (banked == BankVisitState.BANKED ? 1 : 0);
	}

	private static void add(PrimitiveIntList[] raw, int key, int packed, int label)
	{
		if (raw[key] == null)
			raw[key] = new PrimitiveIntList(16);
		raw[key].add(packed);
		raw[key].add(label);
	}

	private static int total(int[][] values)
	{
		int total = 0;
		for (int[] value : values)
			total += value.length;
		return total;
	}

	/** The values at {@code offset} of (id, tile, label) triples, ordered by ascending id. */
	private static int[] byId(PrimitiveIntList triples, int offset)
	{
		long[] keys = new long[triples.size() / 3];
		for (int i = 0; i < keys.length; i++)
			keys[i] = (long) triples.get(3 * i) << 32 | i;
		Arrays.sort(keys);
		int[] out = new int[keys.length];
		for (int i = 0; i < out.length; i++)
			out[i] = triples.get(3 * (int) keys[i] + offset);
		return out;
	}
}
