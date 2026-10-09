package shortestpath.pathfinder.exact;

import java.util.Arrays;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;

/** Query-lifetime raw seeds and provenance-reduced exact seed scans. */
public final class PreparedHeuristic
{
	private final TargetOverlay overlay;
	private final ReverseLabels reverse;
	private final int[][] seedTiles;
	private final int[][] seedLabels;
	private final int[][] generatorTiles;
	private final int[][] generatorLabels;
	private final long preparationNanos;

	private PreparedHeuristic(TargetOverlay overlay, ReverseLabels reverse, int[][] seedTiles, int[][] seedLabels,
		int[][] generatorTiles, int[][] generatorLabels, long preparationNanos)
	{
		this.overlay = overlay;
		this.reverse = reverse;
		this.seedTiles = seedTiles;
		this.seedLabels = seedLabels;
		this.generatorTiles = generatorTiles;
		this.generatorLabels = generatorLabels;
		this.preparationNanos = preparationNanos;
	}

	public static PreparedHeuristic prepare(TargetOverlay overlay, ReverseLabels reverse)
	{
		if (reverse == null || reverse.overlay() != overlay)
			throw new IllegalArgumentException("reverse labels belong to another target overlay");
		long started = System.nanoTime();
		int bucketCount = overlay.routingStatic().routingComponentCount() * 2;
		IntList[] raw = new IntList[bucketCount];
		GeneratorList[] reduced = new GeneratorList[bucketCount];
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
			tiles[i] = raw[i] == null ? new int[0] : raw[i].tiles();
			labels[i] = raw[i] == null ? new int[0] : raw[i].labels();
			generatorTiles[i] = reduced[i] == null ? new int[0] : reduced[i].tiles();
			generatorLabels[i] = reduced[i] == null ? new int[0] : reduced[i].labels();
		}
		return new PreparedHeuristic(overlay, reverse, tiles, labels, generatorTiles, generatorLabels,
			System.nanoTime() - started);
	}

	public TargetOverlay overlay()
	{
		return overlay;
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
	public long preparationNanos()
	{
		return preparationNanos;
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
		int best = ExactCosts.INF;
		for (int i = 0; i < bucketTiles.length; i++)
			best = Math.min(best, ExactCosts.add(bucketLabels[i],
				WorldPointUtil.distanceBetween(packed, bucketTiles[i])));
		return best;
	}

	private static void addSite(IntList[] raw, GeneratorList[] reduced, ReverseLabels reverse,
		TargetOverlay overlay, int node, int packed, int[] components, BankVisitState banked)
	{
		int state = SiteGraph.stateId(node, banked);
		int distance = reverse.label(node, banked);
		if (distance == ExactCosts.INF)
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
				reduced[key] = new GeneratorList();
			reduced[key].add(generatorState, generatorTile, generatorLabel);
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
		if (direct == ExactCosts.INF)
			return false;
		if (reverse.generatorWeightDoubled(state) == ExactCosts.INF
			|| reverse.doubledLabel(state) != ExactCosts.add(reverse.generatorWeightDoubled(state), ExactCosts.twice(direct)))
			return false;
		int[] originComponents = overlay.nodeComponents(originNode);
		return contains(originComponents, component);
	}

	private int targetLabel(int packed, BankVisitState banked)
	{
		int target = overlay.targetIndex(packed);
		return target < 0 ? ExactCosts.INF : reverse.targetLabel(target, banked);
	}

	private static int key(int component, BankVisitState banked)
	{
		return component * 2 + (banked == BankVisitState.BANKED ? 1 : 0);
	}

	private static void add(IntList[] raw, int key, int packed, int label)
	{
		if (raw[key] == null)
			raw[key] = new IntList();
		raw[key].add(packed, label);
	}

	private static boolean contains(int[] values, int target)
	{
		for (int value : values)
			if (value == target)
				return true;
		return false;
	}

	private static int total(int[][] values)
	{
		int total = 0;
		for (int[] value : values)
			total += value.length;
		return total;
	}

	private static final class IntList
	{
		private int[] tiles = new int[8], labels = new int[8];
		private int size;

		void add(int tile, int label)
		{
			if (size == tiles.length)
			{
				tiles = Arrays.copyOf(tiles, size * 2);
				labels = Arrays.copyOf(labels, size * 2);
			}
			tiles[size] = tile;
			labels[size++] = label;
		}
		int[] tiles()
		{
			return Arrays.copyOf(tiles, size);
		}
		int[] labels()
		{
			return Arrays.copyOf(labels, size);
		}
	}

	private static final class GeneratorList
	{
		private int[] ids = new int[8], tiles = new int[8], labels = new int[8];
		private int size;

		void add(int id, int tile, int label)
		{
			for (int i = 0; i < size; i++)
				if (ids[i] == id)
					return;
			if (size == ids.length)
			{
				ids = Arrays.copyOf(ids, size * 2);
				tiles = Arrays.copyOf(tiles, size * 2);
				labels = Arrays.copyOf(labels, size * 2);
			}
			ids[size] = id;
			tiles[size] = tile;
			labels[size++] = label;
		}
		int[] tiles()
		{
			sort();
			return Arrays.copyOf(tiles, size);
		}
		int[] labels()
		{
			sort();
			return Arrays.copyOf(labels, size);
		}
		private void sort()
		{
			for (int i = 1; i < size; i++)
			{
				int j = i;
				while (j > 0 && ids[j] < ids[j - 1])
				{
					int id = ids[j];
					ids[j] = ids[j - 1];
					ids[j - 1] = id;
					int tile = tiles[j];
					tiles[j] = tiles[j - 1];
					tiles[j - 1] = tile;
					int label = labels[j];
					labels[j] = labels[j - 1];
					labels[j - 1] = label;
					j--;
				}
			}
		}
	}
}
