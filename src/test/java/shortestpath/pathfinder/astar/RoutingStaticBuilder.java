package shortestpath.pathfinder.astar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.IntDeque;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Generates the routing component layer ({@link ComponentLayer}) offline and composes it with
 * {@link RoutingStaticLoader}, so tests and tooling derive a {@link RoutingStatic} exactly the way
 * the plugin does from the bundled layer.
 *
 * <p>The layer is derived from the {@link CollisionMap} and the separator cut edges:
 * <ol>
 *   <li>Every walkable tile is enumerated in ascending unsigned packed order.</li>
 *   <li>Cuts that are no longer walking edges are ignored and duplicates dropped.</li>
 *   <li>Routing components: a flood fill over ordinary walking edges that does not cross a valid
 *       cut. Cuts that no longer separate two routing components are dropped; the rest become
 *       crossings. Cuts therefore only affect performance, never routes.</li>
 *   <li>The component ids are stored as runs of consecutive tiles, with the crossings.</li>
 * </ol>
 *
 * <p>{@link #build(CollisionMap, Map, Set, int[], int)} then hands that layer, the raw
 * (account-independent) transports of every {@link TransportType}, and the bank list to
 * {@link RoutingStaticLoader}.
 */
public final class RoutingStaticBuilder
{
	private static final int REGION_SIZE = 64;

	private RoutingStaticBuilder()
	{
	}

	/** Derivation counts of one build, for the cut report and the tests. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Diagnostics
	{
		public final long totalNanos;

		/** Every walkable tile of the collision map. */
		public final int walkableTileCount;
		public final int naturalComponentCount;
		public final int routingComponentCount;
		/** The most search tiles in one routing component. */
		public final int largestRoutingComponentSize;
		public final int reachableNaturalComponentCount;

		public final int inputCutCount;
		public final int duplicateCutCount;
		public final int invalidCutCount;
		public final int nonSeparatingCutCount;
		public final int crossingCount;

		public final int searchTileCount;
		public final int siteCount;
		/** Components whose sites the network joins by exact walks. */
		public final int exactComponentCount;

		@Override
		public String toString()
		{
			return "RoutingStaticBuilder.Diagnostics{"
				+ "totalMs=" + (totalNanos / 1_000_000)
				+ ", walkableTileCount=" + walkableTileCount + ", naturalComponentCount=" + naturalComponentCount
				+ ", routingComponentCount=" + routingComponentCount
				+ ", largestRoutingComponentSize=" + largestRoutingComponentSize
				+ ", reachableNaturalComponentCount=" + reachableNaturalComponentCount
				+ ", inputCutCount=" + inputCutCount + ", duplicateCutCount=" + duplicateCutCount
				+ ", invalidCutCount=" + invalidCutCount + ", nonSeparatingCutCount=" + nonSeparatingCutCount
				+ ", crossingCount=" + crossingCount + ", searchTileCount=" + searchTileCount
				+ ", siteCount=" + siteCount + ", exactComponentCount=" + exactComponentCount + "}";
		}
	}

	/** The derived static world plus the diagnostics collected while building it. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Result
	{
		public final RoutingStatic routingStatic;
		public final Diagnostics diagnostics;
	}

	/**
	 * Builds the derived {@link RoutingStatic}: generates the layer from the collision map and the
	 * cuts, then loads it with {@link RoutingStaticLoader}.
	 *
	 * @param collision      authoritative walking-neighbour source.
	 * @param rawTransports  the plugin's raw, unfiltered, account-independent transports, keyed by
	 *                       packed origin tile or {@link shortestpath.WorldPointUtil#UNDEFINED} for
	 *                       origin-less (global) transports/teleports &mdash;
	 *                       {@code ResourceLoader.transports()}'s direct result. Must
	 *                       include every {@link TransportType}, seasonal included.
	 * @param bankTiles      the whole bank destination set (the destination TSVs' "bank" column),
	 *                       with no account filtering.
	 * @param cuts           candidate separator cut edges as packed {@code (from, to)} pairs:
	 *                       {@code cuts[2*i]}/{@code cuts[2*i+1]}.
	 */
	public static Result build(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports,
		Set<Integer> bankTiles, int[] cuts) throws IOException
	{
		return build(collision, rawTransports, bankTiles, cuts, RoutingStaticLoader.SEED_TILE);
	}

	/**
	 * Same as {@link #build(CollisionMap, Map, Set, int[])}, but with an overridable structural
	 * reachability seed tile. Production callers use the loader with its fixed seed; this overload
	 * lets synthetic-world unit tests exercise structural reachability without embedding the real
	 * seed tile {@code (3221, 3218, 0)} in every tiny test world.
	 */
	public static Result build(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports,
		Set<Integer> bankTiles, int[] cuts, int seedTile) throws IOException
	{
		long started = System.nanoTime();
		WalkableTiles walkable = enumerateWalkableTiles(collision);
		CutSet cutSet = validateCuts(cuts, walkable);
		int[] componentIds = new int[walkable.tiles.length];
		floodFillComponents(walkable, cutSet.blocked, componentIds);
		ComponentLayer layer = formLayer(walkable, componentIds, cutSet.valid);

		RoutingStatic routingStatic = RoutingStaticLoader.build(collision, rawTransports, bankTiles, layer, seedTile);

		int[] natural = RoutingStaticLoader.naturalComponents(layer);
		int naturalComponentCount = 0;
		for (int id = 1; id < natural.length; id++)
		{
			if (natural[id] == id) naturalComponentCount++;
		}
		int reachableNaturalComponentCount = 0;
		for (boolean reachable : RoutingStaticLoader.structuralReachability(collision, layer, natural,
			new boolean[natural.length], rawTransports, seedTile))
		{
			if (reachable) reachableNaturalComponentCount++;
		}
		int[] searchTilesPerComponent = new int[routingStatic.routingComponentCount()];
		int largestRoutingComponentSize = 0;
		for (int i = 0; i < routingStatic.searchTileCount(); i++)
		{
			largestRoutingComponentSize = Math.max(largestRoutingComponentSize,
				++searchTilesPerComponent[routingStatic.routingComponent(i)]);
		}
		int crossingCount = layer.crossings.length / 2;
		Diagnostics diagnostics = new Diagnostics(System.nanoTime() - started, walkable.tiles.length,
			naturalComponentCount, routingStatic.routingComponentCount() - 1, largestRoutingComponentSize,
			reachableNaturalComponentCount, cuts.length / 2, cutSet.duplicateCount, cutSet.invalidCount,
			cutSet.valid.size() - crossingCount, crossingCount, routingStatic.searchTileCount(),
			routingStatic.siteCount(), routingStatic.exactWalks().exactComponentCount());
		return new Result(routingStatic, diagnostics);
	}

	/**
	 * The routing components of every walkable tile and the separating cuts between them, as the
	 * {@link ComponentLayer} that {@link RoutingStaticLoader} reads. Ids are numbered from 1 in order
	 * of each component's first tile, with the valid cuts blocked; nothing hash-ordered reaches the output.
	 */
	static ComponentLayer generate(CollisionMap collision, int[] cuts)
	{
		WalkableTiles walkable = enumerateWalkableTiles(collision);
		CutSet cutSet = validateCuts(cuts, walkable);
		int[] componentIds = new int[walkable.tiles.length];
		floodFillComponents(walkable, cutSet.blocked, componentIds);
		return formLayer(walkable, componentIds, cutSet.valid);
	}

	/** The runs of equal component ids over the walkable tiles, plus the valid cuts that separate two components. */
	private static ComponentLayer formLayer(WalkableTiles walkable, int[] componentIds, List<long[]> validCuts)
	{
		int runCount = 0;
		for (int i = 0, length = 0; i < componentIds.length; i++)
		{
			length = extendsRun(walkable, componentIds, i, length) ? length + 1 : 1;
			if (length == 1) runCount++;
		}
		int[] starts = new int[runCount];
		char[] lengths = new char[runCount];
		int[] ids = new int[runCount];
		for (int i = 0, run = -1; i < componentIds.length; i++)
		{
			if (extendsRun(walkable, componentIds, i, run < 0 ? 0 : lengths[run])) lengths[run]++;
			else
			{
				starts[++run] = walkable.tiles[i];
				lengths[run] = 1;
				ids[run] = componentIds[i];
			}
		}

		List<Crossing> crossings = buildCrossings(validCuts, componentIds, walkable);
		int[] crossingTiles = new int[crossings.size() * 2];
		for (int i = 0; i < crossings.size(); i++)
		{
			crossingTiles[2 * i] = crossings.get(i).fromTile;
			crossingTiles[2 * i + 1] = crossings.get(i).toTile;
		}
		return new ComponentLayer(starts, lengths, ids, crossingTiles);
	}

	/** Whether tile {@code index} continues a run of {@code length} tiles ending at the tile before it. */
	private static boolean extendsRun(WalkableTiles walkable, int[] componentIds, int index, int length)
	{
		return index > 0 && length < Character.MAX_VALUE && walkable.tiles[index] == walkable.tiles[index - 1] + 1
			&& componentIds[index] == componentIds[index - 1];
	}

	/**
	 * The ordinary walking graph over every walkable tile, with natural components and structural
	 * reachability computed exactly as {@link #build} does. The tooling cut generator partitions
	 * this graph, so generated cuts and the runtime agree on what a walking edge is.
	 */
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class WalkingGraph
	{
		private final WalkableTiles walkable;
		private final int[] naturalId;
		@Getter @Accessors(fluent = true) private final int naturalComponentCount;
		private final boolean[] reachableNatural;

		/** Number of walkable tiles; tile indexes follow ascending unsigned packed order. */
		public int tileCount()
		{
			return walkable.tiles.length;
		}

		public int tile(int index)
		{
			return walkable.tiles[index];
		}

		/** Index of a walkable tile, or -1 when the tile is not walkable. */
		public int indexOf(int tile)
		{
			return tilePosition(walkable, tile);
		}

		/** Natural component id of a tile index, in {@code 1..naturalComponentCount()}. */
		public int naturalComponent(int index)
		{
			return naturalId[index];
		}

		public boolean isStructurallyReachable(int naturalComponent)
		{
			return reachableNatural[naturalComponent];
		}

		/** Indexes of the walkable tiles reachable in one ordinary walking step. */
		public int[] neighbours(int index)
		{
			int[] tiles = ordinaryNeighborsFromMask(walkable.tiles[index], walkable.masks[index]);
			int[] result = new int[tiles.length];
			int count = 0;
			for (int tile : tiles)
			{
				int neighbour = tilePosition(walkable, tile);
				if (neighbour >= 0) result[count++] = neighbour;
			}
			return count == result.length ? result : Arrays.copyOf(result, count);
		}
	}

	/** Builds the walking graph used for cut generation; see {@link WalkingGraph}. */
	public static WalkingGraph walkingGraph(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports)
	{
		return walkingGraph(collision, rawTransports, RoutingStaticLoader.SEED_TILE);
	}

	static WalkingGraph walkingGraph(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports, int seedTile)
	{
		WalkableTiles walkable = enumerateWalkableTiles(collision);
		int[] naturalId = new int[walkable.tiles.length];
		int naturalComponentCount = floodFillComponents(walkable, null, naturalId);
		ComponentLayer layer = formLayer(walkable, naturalId, List.of());
		int[] natural = RoutingStaticLoader.naturalComponents(layer);
		boolean[] reachable = RoutingStaticLoader.structuralReachability(collision, layer, natural,
			new boolean[natural.length], rawTransports, seedTile);
		return new WalkingGraph(walkable, naturalId, naturalComponentCount, reachable);
	}

	// ---- Enumeration of every walkable tile in ascending unsigned packed order ----

	@AllArgsConstructor
	private static final class WalkableTiles
	{
		final int[] tiles;
		final byte[] masks;
		/** Per 64-tile group, the group's first index and the bitmask of tiles present. */
		final PrimitiveIntHashMap<long[]> index;
	}

	private static WalkableTiles enumerateWalkableTiles(CollisionMap collision)
	{
		TileSelection selected = TileSelection.allWalkable(collision);
		int[] tiles = new int[selected.tileCount()];
		byte[] masks = new byte[tiles.length];
		int next = 0;
		// Packed tiles sort by plane, then y, then x; emit that order without sorting or copying.
		for (int plane = 0; plane < 4; plane++)
		{
			for (int ry = 0; ry < selected.height; ry++)
			{
				int baseY = (selected.minY + ry) * REGION_SIZE;
				for (int ly = 0; ly < REGION_SIZE; ly++)
				{
					int rowStart = ly * REGION_SIZE;
					for (int rx = 0; rx < selected.width; rx++)
					{
						BitSet region = selected.regions[(rx + ry * selected.width) * 4 + plane];
						if (region == null) continue;
						int baseX = (selected.minX + rx) * REGION_SIZE;
						for (int bit = region.nextSetBit(rowStart); bit >= 0 && bit < rowStart + REGION_SIZE;
							bit = region.nextSetBit(bit + 1))
						{
							int tile = WorldPointUtil.packWorldPoint(baseX + (bit & (REGION_SIZE - 1)), baseY + ly, plane);
							tiles[next] = tile;
							masks[next++] = collision.ordinaryWalkingMask(tile);
						}
					}
				}
			}
		}
		PrimitiveIntHashMap<long[]> index = new PrimitiveIntHashMap<>(tiles.length / 64 + 1);
		for (int i = 0; i < tiles.length; i++)
		{
			int group = tiles[i] >>> 6;
			long[] entry = index.get(group);
			if (entry == null)
			{
				entry = new long[] {i, 0};
				index.put(group, entry);
			}
			entry[1] |= 1L << (tiles[i] & 63);
		}
		return new WalkableTiles(tiles, masks, index);
	}

	/** One bit per walkable tile, allocating bits only for regions that have any. */
	private static final class TileSelection
	{
		final BitSet[] regions;
		final int minX, minY, width, height;

		TileSelection(CollisionMap collision)
		{
			SplitFlagMap.RegionExtent extent = collision.regionExtent();
			minX = extent.getMinX();
			minY = extent.getMinY();
			width = extent.getWidth() + 1;
			height = extent.getHeight() + 1;
			regions = new BitSet[width * height * 4];
		}

		static TileSelection allWalkable(CollisionMap collision)
		{
			TileSelection selected = new TileSelection(collision);
			for (int ry = 0; ry < selected.height; ry++)
			{
				for (int rx = 0; rx < selected.width; rx++)
				{
					int region = rx + ry * selected.width;
					int baseX = (selected.minX + rx) * REGION_SIZE;
					int baseY = (selected.minY + ry) * REGION_SIZE;
					for (int plane = 0; plane < Math.min(4, collision.getRegionPlaneCounts(region)); plane++)
					{
						for (int ly = 0; ly < REGION_SIZE; ly++)
						{
							for (int lx = 0; lx < REGION_SIZE; lx++)
							{
								if (!collision.isBlocked(baseX + lx, baseY + ly, plane))
									selected.mark(region * 4 + plane, ly * REGION_SIZE + lx);
							}
						}
					}
				}
			}
			return selected;
		}

		int tileCount()
		{
			int count = 0;
			for (BitSet region : regions)
				if (region != null) count += region.cardinality();
			return count;
		}

		private void mark(int region, int bit)
		{
			BitSet bits = regions[region];
			if (bits == null) regions[region] = bits = new BitSet(REGION_SIZE * REGION_SIZE);
			bits.set(bit);
		}
	}

	// ---- Flood fill (natural when blocked==null, routing when blocked is a valid-cut set) ----

	/** Returns the number of components assigned (ids {@code 1..count}), 0 if there were no walkable tiles. */
	private static int floodFillComponents(WalkableTiles walkable, int[] blocked, int[] componentOut)
	{
		int n = walkable.tiles.length;
		IntDeque queue = new IntDeque(4096);
		int nextId = 1;
		for (int seed = 0; seed < n; seed++)
		{
			if (componentOut[seed] != 0)
			{
				continue;
			}
			queue.addLast(seed);
			componentOut[seed] = nextId;
			while (!queue.isEmpty())
			{
				int position = queue.pollFirst();
				int tile = walkable.tiles[position];
				int x = WorldPointUtil.unpackWorldX(tile);
				int y = WorldPointUtil.unpackWorldY(tile);
				int plane = WorldPointUtil.unpackWorldPlane(tile);
				int mask = Byte.toUnsignedInt(walkable.masks[position]);
				for (int bit = 0; bit < 8; bit++)
				{
					if ((mask & (1 << bit)) == 0)
					{
						continue;
					}
					int neighbor = WorldPointUtil.packWorldPoint(x + NEIGHBOR_DX[bit], y + NEIGHBOR_DY[bit], plane);
					int neighborPosition = tilePosition(walkable, neighbor);
					if (neighborPosition < 0 || componentOut[neighborPosition] != 0)
					{
						continue;
					}
					if (blocked != null && IntArrays.binarySearch(blocked,
						blockedEdgeKey(tile, neighbor, position, neighborPosition, bit)) >= 0)
					{
						continue;
					}
					componentOut[neighborPosition] = nextId;
					queue.addLast(neighborPosition);
				}
			}
			nextId++;
		}
		return nextId - 1;
	}

	// Clockwise 0-N,1-NE,2-E,3-SE,4-S,5-SW,6-W,7-NW; the bit layout of CollisionMap.ordinaryWalkingMask.
	private static final int[] NEIGHBOR_DX = {0, 1, 1, 1, 0, -1, -1, -1};
	private static final int[] NEIGHBOR_DY = {1, 1, 0, -1, -1, -1, 0, 1};

	private static int[] ordinaryNeighborsFromMask(int tile, byte maskByte)
	{
		int mask = Byte.toUnsignedInt(maskByte);
		int x = WorldPointUtil.unpackWorldX(tile);
		int y = WorldPointUtil.unpackWorldY(tile);
		int z = WorldPointUtil.unpackWorldPlane(tile);
		int[] result = new int[Integer.bitCount(mask)];
		int count = 0;
		for (int bit = 0; bit < 8; bit++)
		{
			if ((mask & (1 << bit)) != 0)
			{
				result[count++] = WorldPointUtil.packWorldPoint(x + NEIGHBOR_DX[bit], y + NEIGHBOR_DY[bit], z);
			}
		}
		return result;
	}

	// ---- Cuts: validity ("ignore cuts that are not current walking edges") + canonical dedup ----

	@AllArgsConstructor
	private static final class CutSet
	{
		final List<long[]> valid; // each entry {a, b} as raw ints boxed in a long[2] for simplicity
		final int[] blocked; // sorted edge keys, see blockedEdgeKey
		final int duplicateCount;
		final int invalidCount;
	}

	private static CutSet validateCuts(int[] cuts, WalkableTiles walkable)
	{
		HashSet<Long> seenCanonical = new HashSet<>();
		int duplicateCount = 0;
		List<long[]> candidates = new ArrayList<>();
		int pairCount = cuts.length / 2;
		for (int i = 0; i < pairCount; i++)
		{
			int rawA = cuts[2 * i];
			int rawB = cuts[2 * i + 1];
			int a = Integer.compareUnsigned(rawA, rawB) <= 0 ? rawA : rawB;
			int b = Integer.compareUnsigned(rawA, rawB) <= 0 ? rawB : rawA;
			long key = canonicalKey(a, b);
			if (!seenCanonical.add(key))
			{
				duplicateCount++;
				continue;
			}
			candidates.add(new long[] {Integer.toUnsignedLong(a), Integer.toUnsignedLong(b)});
		}
		List<long[]> valid = new ArrayList<>();
		int[] blockedKeys = new int[candidates.size()];
		int blockedCount = 0;
		int invalidCount = 0;
		for (long[] candidate : candidates)
		{
			int a = (int) candidate[0];
			int b = (int) candidate[1];
			int posA = tilePosition(walkable, a);
			int[] neighborsOfA = posA < 0 ? new int[0] : ordinaryNeighborsFromMask(a, walkable.masks[posA]);
			if (posA < 0 || !IntArrays.contains(neighborsOfA, 0, neighborsOfA.length, b))
			{
				invalidCount++;
				continue;
			}
			valid.add(candidate);
			int direction = directionIndex(WorldPointUtil.unpackWorldX(b) - WorldPointUtil.unpackWorldX(a),
				WorldPointUtil.unpackWorldY(b) - WorldPointUtil.unpackWorldY(a));
			blockedKeys[blockedCount++] = (posA << 3) | direction;
		}
		int[] blocked = Arrays.copyOf(blockedKeys, blockedCount);
		Arrays.sort(blocked); // keys are non-negative, so signed order is unsigned order
		return new CutSet(valid, blocked, duplicateCount, invalidCount);
	}

	private static long canonicalKey(int a, int b)
	{
		int min = Integer.compareUnsigned(a, b) <= 0 ? a : b;
		int max = Integer.compareUnsigned(a, b) <= 0 ? b : a;
		return (Integer.toUnsignedLong(min) << 32) | Integer.toUnsignedLong(max);
	}

	// ---- Section: routing components / crossings ----

	@AllArgsConstructor
	private static final class Crossing
	{
		final int fromTile, toTile;
	}

	private static List<Crossing> buildCrossings(List<long[]> validCuts, int[] routingId, WalkableTiles walkable)
	{
		List<Crossing> crossings = new ArrayList<>();
		for (long[] cut : validCuts)
		{
			int a = (int) cut[0];
			int b = (int) cut[1];
			int posA = tilePosition(walkable, a);
			int posB = tilePosition(walkable, b);
			if (posA < 0 || posB < 0 || routingId[posA] == routingId[posB])
			{
				continue;
			}
			crossings.add(new Crossing(a, b));
		}
		crossings.sort((left, right) ->
		{
			int cmp = Integer.compareUnsigned(left.fromTile, right.fromTile);
			return cmp != 0 ? cmp : Integer.compareUnsigned(left.toTile, right.toTile);
		});
		return crossings;
	}

	// ---- Small shared utilities ----

	/**
	 * Index of a walkable tile, or -1: the shared map holds {@code {firstIndex, presentBits}}
	 * per 64-tile group, so the index is the group's first index plus the tile's rank among
	 * the group's present bits.
	 */
	private static int tilePosition(WalkableTiles walkable, int tile)
	{
		long[] group = walkable.index.get(tile >>> 6);
		if (group == null)
		{
			return -1;
		}
		long bit = 1L << (tile & 63);
		if ((group[1] & bit) == 0)
		{
			return -1;
		}
		return (int) group[0] + Long.bitCount(group[1] & (bit - 1));
	}

	/** The direction index ({@link #NEIGHBOR_DX}/{@link #NEIGHBOR_DY} order) of a one-step delta. */
	private static int directionIndex(int dx, int dy)
	{
		for (int bit = 0; bit < 8; bit++)
		{
			if (NEIGHBOR_DX[bit] == dx && NEIGHBOR_DY[bit] == dy)
			{
				return bit;
			}
		}
		throw new IllegalArgumentException("non-adjacent edge delta " + dx + "," + dy);
	}

	/**
	 * Canonical key of a blocked walking edge: the index of its unsigned-lowest tile (the
	 * {@code validateCuts} pair order) and the direction from that tile to the other.
	 */
	private static int blockedEdgeKey(int tile, int neighbor, int position, int neighborPosition, int direction)
	{
		boolean tileIsLow = Integer.compareUnsigned(tile, neighbor) <= 0;
		int anchor = tileIsLow ? position : neighborPosition;
		int dir = tileIsLow ? direction : direction ^ 4; // i^4 is the opposite direction in NEIGHBOR_* order
		return (anchor << 3) | dir;
	}
}
