package shortestpath.pathfinder.exact;

import java.util.Arrays;
import java.util.Comparator;
import shortestpath.WorldPointUtil;

/**
 * Builds the sparse same-component walking network used by {@link ReverseLabels#computeSparse}.
 *
 * <p>Within each routing component, walking distance between two sites on the same plane is at
 * least their Chebyshev distance. Instead of connecting every pair of sites, the construction
 * works in rotated Manhattan coordinates {@code a = x + y}, {@code b = x - y}, where Manhattan
 * distance is exactly twice Chebyshev distance, and adds Steiner vertices along balanced
 * coordinate splits so that the shortest path between any two sites equals that distance.
 * Every edge weight is therefore doubled walking distance; {@link ReverseLabels#computeSparse}
 * halves it once at the end.
 */
public final class SparseWalkingNetworkBuilder
{
	private SparseWalkingNetworkBuilder()
	{
	}

	/** Section G in flat, undirected CSR form (adjacencyCount == 2 * undirectedEdgeCount). */
	public static final class Result
	{
		public final int originalCount;
		public final int vertexCount;
		public final int steinerCount;
		public final int undirectedEdgeCount;
		public final int[] offsets;
		public final int[] destinations;
		public final int[] weights;

		private Result(int originalCount, int vertexCount, int steinerCount, int undirectedEdgeCount, int[] offsets,
			int[] destinations, int[] weights)
		{
			this.originalCount = originalCount;
			this.vertexCount = vertexCount;
			this.steinerCount = steinerCount;
			this.undirectedEdgeCount = undirectedEdgeCount;
			this.offsets = offsets;
			this.destinations = destinations;
			this.weights = weights;
		}

		public int adjacencyCount()
		{
			return destinations.length;
		}
	}

	/**
	 * Builds the network from the static sites and their routing-component attachments:
	 *
	 * <ul>
	 *   <li>{@code siteTiles[s]}: packed tile of site {@code s}, ascending.</li>
	 *   <li>{@code siteComponentOffsets}, {@code siteComponentIds}: each site's ascending
	 *       routing-component attachments. A site attached to several components contributes
	 *       one point to each component, all sharing the same site vertex.</li>
	 * </ul>
	 *
	 * <p>Vertices {@code 0..siteTiles.length-1} are the sites, followed by Steiner vertices. A
	 * site with no component attachment is an isolated vertex.
	 *
	 * @param siteTiles              packed tiles, ascending unsigned packed order, one per site
	 * @param siteComponentOffsets   length {@code siteTiles.length + 1}, CSR offsets into {@code siteComponentIds}
	 * @param siteComponentIds       routing-component ids per site, ascending within each site's range
	 * @return the flattened CSR sparse walking network (section G)
	 */
	public static Result build(int[] siteTiles, int[] siteComponentOffsets, int[] siteComponentIds)
	{
		int originalCount = siteTiles.length;
		int maxComponentId = -1;
		for (int cid : siteComponentIds)
		{
			if (cid > maxComponentId)
			{
				maxComponentId = cid;
			}
		}
		int componentCount = maxComponentId + 1;

		// Bucket sites by component id; only components that own at least one site are processed,
		// in ascending id order.
		int[] bucketCounts = new int[componentCount];
		for (int cid : siteComponentIds)
		{
			bucketCounts[cid]++;
		}
		int[] bucketOffsets = new int[componentCount + 1];
		for (int c = 0; c < componentCount; c++)
		{
			bucketOffsets[c + 1] = bucketOffsets[c] + bucketCounts[c];
		}
		int[] bucketSites = new int[siteComponentIds.length];
		int[] cursor = bucketOffsets.clone();
		for (int site = 0; site < originalCount; site++)
		{
			for (int p = siteComponentOffsets[site]; p < siteComponentOffsets[site + 1]; p++)
			{
				int cid = siteComponentIds[p];
				bucketSites[cursor[cid]++] = site;
			}
		}

		EdgeList edges = new EdgeList();
		int next = originalCount;
		for (int cid = 0; cid < componentCount; cid++)
		{
			int start = bucketOffsets[cid];
			int end = bucketOffsets[cid + 1];
			int size = end - start;
			if (size == 0)
			{
				continue;
			}
			Point[] points = new Point[size];
			for (int i = 0; i < size; i++)
			{
				int siteIndex = bucketSites[start + i];
				int tile = siteTiles[siteIndex];
				int x = WorldPointUtil.unpackWorldX(tile);
				int y = WorldPointUtil.unpackWorldY(tile);
				points[i] = new Point(siteIndex, x + y, x - y);
			}
			Arrays.sort(points, BY_A_B_ORIGINAL);
			next = buildComponent(next, points, edges);
		}

		int vertexCount = next;
		int steinerCount = vertexCount - originalCount;
		return undirectedAdjacency(originalCount, vertexCount, steinerCount, edges);
	}

	private enum Axis
	{
		A, B
	}

	private static final class Point
	{
		final int original;
		final int a;
		final int b;

		Point(int original, int a, int b)
		{
			this.original = original;
			this.a = a;
			this.b = b;
		}
	}

	private static final Comparator<Point> BY_A_B_ORIGINAL = (x, y) ->
	{
		if (x.a != y.a)
		{
			return Integer.compare(x.a, y.a);
		}
		if (x.b != y.b)
		{
			return Integer.compare(x.b, y.b);
		}
		return Integer.compare(x.original, y.original);
	};

	private static int coordOf(Axis axis, Point p)
	{
		return axis == Axis.A ? p.a : p.b;
	}

	private static int otherCoordOf(Axis axis, Point p)
	{
		return axis == Axis.A ? p.b : p.a;
	}

	private static Comparator<Point> axisComparator(Axis axis)
	{
		return (x, y) ->
		{
			int cx = coordOf(axis, x);
			int cy = coordOf(axis, y);
			if (cx != cy)
			{
				return Integer.compare(cx, cy);
			}
			return BY_A_B_ORIGINAL.compare(x, y);
		};
	}

	/**
	 * Recursively splits one component's points. {@code pts} arrives in inherited order (the
	 * global {@code a,b,original} sort at the top level, or a slice of the parent split's
	 * axis-sorted list below it); that order, not any re-sort here, decides which Steiner vertex
	 * id each point receives in {@link #addProjectionLevel}, keeping numbering deterministic.
	 */
	private static int buildComponent(int next, Point[] pts, EdgeList edges)
	{
		if (pts.length <= 1)
		{
			return next;
		}
		int a0 = pts[0].a;
		int b0 = pts[0].b;
		boolean allSameA = true;
		boolean allSameB = true;
		for (Point p : pts)
		{
			if (p.a != a0)
			{
				allSameA = false;
			}
			if (p.b != b0)
			{
				allSameB = false;
			}
		}
		if (allSameA && allSameB)
		{
			// Coincident points are chained with zero-weight edges; pts is already sorted by `original`
			// here, since a/b are constant and the inherited order derives from a sort
			// keyed on (axis-or-a-b, a, b, original).
			for (int i = 0; i + 1 < pts.length; i++)
			{
				edges.add(pts[i].original, pts[i + 1].original, 0);
			}
			return next;
		}
		Axis axis = allSameA ? Axis.B : Axis.A;
		return splitOn(axis, next, pts, edges);
	}

	/** Splits {@code pts} at the most balanced gap along {@code axis} and recurses on both halves. */
	private static int splitOn(Axis axis, int next, Point[] pts, EdgeList edges)
	{
		Point[] sorted = pts.clone();
		Arrays.sort(sorted, axisComparator(axis));
		int split = balancedGapSplit(axis, sorted);
		Point[] left = Arrays.copyOfRange(sorted, 0, split);
		Point[] right = Arrays.copyOfRange(sorted, split, sorted.length);
		int splitCoordinate = Math.floorDiv(
			coordOf(axis, sorted[split - 1]) + coordOf(axis, sorted[split]), 2);
		int afterProjection = addProjectionLevel(axis, splitCoordinate, next, pts, edges);
		int afterLeft = buildComponent(afterProjection, left, edges);
		return buildComponent(afterLeft, right, edges);
	}

	/**
	 * Returns the split index {@code i}: {@code sorted[0..i)} and {@code sorted[i..)} lie on
	 * either side of the chosen gap; the caller
	 * derives the Steiner splitter coordinate from the pair straddling that index.
	 */
	private static int balancedGapSplit(Axis axis, Point[] sorted)
	{
		int n = sorted.length;
		int preferred = n / 2;
		int bestIndex = -1;
		int bestDiff = Integer.MAX_VALUE;
		for (int i = 1; i < n; i++)
		{
			if (coordOf(axis, sorted[i - 1]) < coordOf(axis, sorted[i]))
			{
				int diff = Math.abs(i - preferred);
				if (diff < bestDiff)
				{
					bestDiff = diff;
					bestIndex = i;
				}
			}
		}
		if (bestIndex < 0)
		{
			throw new IllegalStateException("sparse walking split without a strict coordinate gap");
		}
		return bestIndex;
	}

	/**
	 * Allocates one fresh
	 * Steiner vertex per point in {@code pts} (in {@code pts}'s own order), a spoke edge
	 * from each point to its projection vertex, then chains the projections in ascending
	 * order of the other axis coordinate (ties by original site index).
	 */
	private static int addProjectionLevel(Axis axis, int splitCoordinate, int firstProjection, Point[] pts,
		EdgeList edges)
	{
		int count = pts.length;
		int[] steinerIds = new int[count];
		for (int i = 0; i < count; i++)
		{
			steinerIds[i] = firstProjection + i;
			edges.add(pts[i].original, steinerIds[i], Math.abs(coordOf(axis, pts[i]) - splitCoordinate));
		}
		Integer[] order = new Integer[count];
		for (int i = 0; i < count; i++)
		{
			order[i] = i;
		}
		Arrays.sort(order, (x, y) ->
		{
			int ox = otherCoordOf(axis, pts[x]);
			int oy = otherCoordOf(axis, pts[y]);
			if (ox != oy)
			{
				return Integer.compare(ox, oy);
			}
			return Integer.compare(pts[x].original, pts[y].original);
		});
		for (int i = 0; i + 1 < count; i++)
		{
			int leftIndex = order[i];
			int rightIndex = order[i + 1];
			int weight = Math.abs(otherCoordOf(axis, pts[leftIndex]) - otherCoordOf(axis, pts[rightIndex]));
			edges.add(steinerIds[leftIndex], steinerIds[rightIndex], weight);
		}
		return firstProjection + count;
	}

	/** Growable primitive (from, to, weight) triples; final counts are small primitive arrays. */
	private static final class EdgeList
	{
		private int[] from = new int[16];
		private int[] to = new int[16];
		private int[] weight = new int[16];
		private int size;

		void add(int a, int b, int w)
		{
			if (size == from.length)
			{
				int capacity = size * 2;
				from = Arrays.copyOf(from, capacity);
				to = Arrays.copyOf(to, capacity);
				weight = Arrays.copyOf(weight, capacity);
			}
			from[size] = a;
			to[size] = b;
			weight[size] = w;
			size++;
		}
	}

	/**
	 * Materialises both directions of every edge as CSR adjacency. The order of entries within
	 * one vertex's range does not affect any distance.
	 */
	private static Result undirectedAdjacency(int originalCount, int vertexCount, int steinerCount, EdgeList edges)
	{
		int[] counts = new int[vertexCount];
		for (int i = 0; i < edges.size; i++)
		{
			counts[edges.from[i]]++;
			counts[edges.to[i]]++;
		}
		int[] offsets = new int[vertexCount + 1];
		for (int v = 0; v < vertexCount; v++)
		{
			offsets[v + 1] = offsets[v] + counts[v];
		}
		int[] destinations = new int[offsets[vertexCount]];
		int[] weights = new int[offsets[vertexCount]];
		int[] cursor = offsets.clone();
		for (int i = 0; i < edges.size; i++)
		{
			int a = edges.from[i];
			int b = edges.to[i];
			int w = edges.weight[i];
			destinations[cursor[a]] = b;
			weights[cursor[a]] = w;
			cursor[a]++;
			destinations[cursor[b]] = a;
			weights[cursor[b]] = w;
			cursor[b]++;
		}
		return new Result(originalCount, vertexCount, steinerCount, edges.size, offsets, destinations, weights);
	}
}
