package shortestpath.pathfinder.exact;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Derives the exact backend's {@link RoutingStatic} from the {@link CollisionMap}, the raw
 * (account-independent) transports and global teleports, the bank list, and separator cut edges.
 *
 * <ol>
 *   <li>Natural components: flood fill over ordinary walking edges.</li>
 *   <li>Routing components: the same flood fill, not crossing cut edges. Cuts that are no longer
 *       walking edges are ignored; cuts that no longer separate two routing components are
 *       dropped. Cuts therefore only affect performance, never routes.</li>
 *   <li>Structural reachability: natural components reachable from Lumbridge through any
 *       non-seasonal transport or global teleport, regardless of requirements.</li>
 *   <li>Search tiles: every tile of a reachable natural component, with its walking mask,
 *       routing component and north/south neighbour indexes.</li>
 *   <li>Sites (transport endpoints, crossing endpoints and reachable banks), their component
 *       attachments in both directions, and separator crossings.</li>
 *   <li>The sparse walking network over the sites ({@link SparseWalkingNetworkBuilder}).</li>
 * </ol>
 */
public final class RoutingStaticBuilder
{
	/** Structural-reachability seed tile (Lumbridge). */
	private static final int SEED_TILE = WorldPointUtil.packWorldPoint(3221, 3218, 0);
	/** One full {@code y} unit in the packed encoding (bit 15). */
	private static final int Y_UNIT = 1 << 15;
	private static final EnumSet<TransportType> STRUCTURAL_IGNORED_TYPES = EnumSet.of(TransportType.SEASONAL_TRANSPORTS);

	private RoutingStaticBuilder()
	{
	}

	/** Per-step timings and derivation counts, for the plugin's build diagnostics. */
	public static final class Diagnostics
	{
		public final long enumerateWalkableNanos;
		public final long naturalComponentsNanos;
		public final long cutValidationNanos;
		public final long routingComponentsNanos;
		public final long reachabilityNanos;
		public final long searchTilesNanos;
		public final long sitesAndAttachmentsNanos;
		public final long sparseNetworkNanos;
		public final long validateNanos;
		public final long totalNanos;

		public final int walkableTileCount;
		public final int naturalComponentCount;
		public final int routingComponentCount;
		public final int largestRoutingComponentSize;
		public final int reachableNaturalComponentCount;

		public final int inputCutCount;
		public final int duplicateCutCount;
		public final int invalidCutCount;
		public final int nonSeparatingCutCount;
		public final int crossingCount;

		public final int searchTileCount;
		public final int siteCount;
		public final int reachableBankCount;

		Diagnostics(long enumerateWalkableNanos, long naturalComponentsNanos, long cutValidationNanos,
			long routingComponentsNanos, long reachabilityNanos, long searchTilesNanos,
			long sitesAndAttachmentsNanos, long sparseNetworkNanos, long validateNanos, long totalNanos,
			int walkableTileCount, int naturalComponentCount, int routingComponentCount,
			int largestRoutingComponentSize, int reachableNaturalComponentCount, int inputCutCount,
			int duplicateCutCount, int invalidCutCount, int nonSeparatingCutCount, int crossingCount,
			int searchTileCount, int siteCount, int reachableBankCount)
		{
			this.enumerateWalkableNanos = enumerateWalkableNanos;
			this.naturalComponentsNanos = naturalComponentsNanos;
			this.cutValidationNanos = cutValidationNanos;
			this.routingComponentsNanos = routingComponentsNanos;
			this.reachabilityNanos = reachabilityNanos;
			this.searchTilesNanos = searchTilesNanos;
			this.sitesAndAttachmentsNanos = sitesAndAttachmentsNanos;
			this.sparseNetworkNanos = sparseNetworkNanos;
			this.validateNanos = validateNanos;
			this.totalNanos = totalNanos;
			this.walkableTileCount = walkableTileCount;
			this.naturalComponentCount = naturalComponentCount;
			this.routingComponentCount = routingComponentCount;
			this.largestRoutingComponentSize = largestRoutingComponentSize;
			this.reachableNaturalComponentCount = reachableNaturalComponentCount;
			this.inputCutCount = inputCutCount;
			this.duplicateCutCount = duplicateCutCount;
			this.invalidCutCount = invalidCutCount;
			this.nonSeparatingCutCount = nonSeparatingCutCount;
			this.crossingCount = crossingCount;
			this.searchTileCount = searchTileCount;
			this.siteCount = siteCount;
			this.reachableBankCount = reachableBankCount;
		}

		@Override
		public String toString()
		{
			return "RoutingStaticBuilder.Diagnostics{"
				+ "totalMs=" + (totalNanos / 1_000_000) + ", enumerateWalkableMs=" + (enumerateWalkableNanos / 1_000_000)
				+ ", naturalComponentsMs=" + (naturalComponentsNanos / 1_000_000)
				+ ", cutValidationMs=" + (cutValidationNanos / 1_000_000)
				+ ", routingComponentsMs=" + (routingComponentsNanos / 1_000_000)
				+ ", reachabilityMs=" + (reachabilityNanos / 1_000_000)
				+ ", searchTilesMs=" + (searchTilesNanos / 1_000_000)
				+ ", sitesAndAttachmentsMs=" + (sitesAndAttachmentsNanos / 1_000_000)
				+ ", sparseNetworkMs=" + (sparseNetworkNanos / 1_000_000)
				+ ", validateMs=" + (validateNanos / 1_000_000)
				+ ", walkableTileCount=" + walkableTileCount + ", naturalComponentCount=" + naturalComponentCount
				+ ", routingComponentCount=" + routingComponentCount
				+ ", largestRoutingComponentSize=" + largestRoutingComponentSize
				+ ", reachableNaturalComponentCount=" + reachableNaturalComponentCount
				+ ", inputCutCount=" + inputCutCount + ", duplicateCutCount=" + duplicateCutCount
				+ ", invalidCutCount=" + invalidCutCount + ", nonSeparatingCutCount=" + nonSeparatingCutCount
				+ ", crossingCount=" + crossingCount + ", searchTileCount=" + searchTileCount
				+ ", siteCount=" + siteCount + ", reachableBankCount=" + reachableBankCount + "}";
		}
	}

	/** The derived static world plus the diagnostics collected while building it. */
	public static final class Result
	{
		public final RoutingStatic routingStatic;
		public final Diagnostics diagnostics;

		Result(RoutingStatic routingStatic, Diagnostics diagnostics)
		{
			this.routingStatic = routingStatic;
			this.diagnostics = diagnostics;
		}
	}

	/**
	 * Builds the derived {@link RoutingStatic}.
	 *
	 * @param collision      authoritative walking-neighbour source.
	 * @param rawTransports  the plugin's raw, unfiltered, account-independent transports, keyed by
	 *                       packed origin tile or {@link shortestpath.WorldPointUtil#UNDEFINED} for
	 *                       origin-less (global) transports/teleports &mdash;
	 *                       {@code TransportLoader.loadAllFromResources()}'s direct result. Must
	 *                       include every {@link TransportType}, seasonal included.
	 * @param bankTiles      the whole bank destination set ({@code Destination.tsv}'s "bank" column),
	 *                       with no account filtering.
	 * @param cuts           candidate separator cut edges as packed {@code (from, to)} pairs:
	 *                       {@code cuts[2*i]}/{@code cuts[2*i+1]}.
	 */
	public static Result build(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports,
		Set<Integer> bankTiles, int[] cuts) throws IOException
	{
		return build(collision, rawTransports, bankTiles, cuts, SEED_TILE);
	}

	/**
	 * Same as {@link #build(CollisionMap, Map, Set, int[])}, but with an overridable structural
	 * reachability seed tile. Production callers must use the four-argument overload (the
	 * production seed policy is not configurable); this overload exists so synthetic-world unit
	 * tests can exercise structural reachability without embedding the real seed tile
	 * {@code (3221, 3218, 0)} in every tiny test world.
	 */
	public static Result build(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports,
		Set<Integer> bankTiles, int[] cuts, int seedTile) throws IOException
	{
		long totalStarted = System.nanoTime();

		long stepStarted = System.nanoTime();
		WalkableTiles walkable = enumerateWalkableTiles(collision);
		long enumerateWalkableNanos = System.nanoTime() - stepStarted;

		stepStarted = System.nanoTime();
		int[] naturalId = new int[walkable.tiles.length];
		int naturalComponentCount = floodFillComponents(walkable, null, naturalId);
		long naturalComponentsNanos = System.nanoTime() - stepStarted;

		stepStarted = System.nanoTime();
		CutSet cutSet = validateCuts(cuts, walkable);
		long cutValidationNanos = System.nanoTime() - stepStarted;

		stepStarted = System.nanoTime();
		int[] routingId = new int[walkable.tiles.length];
		int maxRoutingComponentId = floodFillComponents(walkable, cutSet.blocked, routingId);
		// Component arrays are indexed by id, so capacity is maxComponentId + 1 with empty groups retained.
		int routingComponentCount = maxRoutingComponentId + 1;
		int largestRoutingComponentSize = largestGroup(routingId, maxRoutingComponentId);
		long routingComponentsNanos = System.nanoTime() - stepStarted;

		stepStarted = System.nanoTime();
		boolean[] reachableNatural = structuralReachability(collision, walkable, naturalId, naturalComponentCount,
			rawTransports, seedTile);
		int reachableNaturalCount = count(reachableNatural);
		long reachabilityNanos = System.nanoTime() - stepStarted;

		stepStarted = System.nanoTime();
		SearchTiles search = buildSearchTiles(walkable, naturalId, routingId, reachableNatural);
		long searchTilesNanos = System.nanoTime() - stepStarted;

		stepStarted = System.nanoTime();
		List<Crossing> crossings = buildCrossings(cutSet.valid, routingId, walkable);
		int nonSeparating = cutSet.valid.size() - crossings.size();
		SitesAndAttachments sites = buildSites(rawTransports, bankTiles, crossings, search, collision,
			routingComponentCount);
		long sitesAndAttachmentsNanos = System.nanoTime() - stepStarted;

		stepStarted = System.nanoTime();
		SparseWalkingNetworkBuilder.Result sparse = SparseWalkingNetworkBuilder.build(sites.siteTiles,
			sites.siteComponentOffsets, sites.siteComponentIds);
		long sparseNetworkNanos = System.nanoTime() - stepStarted;

		int[] crossingFrom = new int[crossings.size()];
		int[] crossingTo = new int[crossings.size()];
		int[] crossingCost = new int[crossings.size()];
		for (int i = 0; i < crossings.size(); i++)
		{
			Crossing crossing = crossings.get(i);
			crossingFrom[i] = binarySearchUnsigned(sites.siteTiles, crossing.fromTile);
			crossingTo[i] = binarySearchUnsigned(sites.siteTiles, crossing.toTile);
			crossingCost[i] = 1;
		}

		stepStarted = System.nanoTime();
		RoutingStatic result = RoutingStatic.create(search.tiles, search.components, search.masks, search.north,
			search.south, sites.siteTiles, sites.siteComponentOffsets, sites.siteComponentIds, routingComponentCount,
			sites.componentSiteOffsets, sites.componentSiteIds, sites.reachableBankTiles, crossingFrom, crossingTo,
			crossingCost, sparse.originalCount, sparse.vertexCount, sparse.steinerCount, sparse.undirectedEdgeCount,
			sparse.adjacencyCount(), sparse.offsets, sparse.destinations, sparse.weights);
		long validateNanos = System.nanoTime() - stepStarted;

		long totalNanos = System.nanoTime() - totalStarted;
		Diagnostics diagnostics = new Diagnostics(enumerateWalkableNanos, naturalComponentsNanos, cutValidationNanos,
			routingComponentsNanos, reachabilityNanos, searchTilesNanos, sitesAndAttachmentsNanos, sparseNetworkNanos,
			validateNanos, totalNanos, walkable.tiles.length, naturalComponentCount, maxRoutingComponentId,
			largestRoutingComponentSize, reachableNaturalCount, cuts.length / 2, cutSet.duplicateCount,
			cutSet.invalidCount, nonSeparating, crossings.size(), search.tiles.length, sites.siteTiles.length,
			sites.reachableBankTiles.length);
		return new Result(result, diagnostics);
	}

	/**
	 * The ordinary walking graph over every walkable tile, with natural components and structural
	 * reachability computed exactly as {@link #build} does. The tooling cut generator partitions
	 * this graph, so generated cuts and the runtime agree on what a walking edge is.
	 */
	public static final class WalkingGraph
	{
		private final WalkableTiles walkable;
		private final int[] naturalId;
		private final int naturalComponentCount;
		private final boolean[] reachableNatural;

		private WalkingGraph(WalkableTiles walkable, int[] naturalId, int naturalComponentCount,
			boolean[] reachableNatural)
		{
			this.walkable = walkable;
			this.naturalId = naturalId;
			this.naturalComponentCount = naturalComponentCount;
			this.reachableNatural = reachableNatural;
		}

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
			return walkable.index.get(tile);
		}

		/** Natural component id of a tile index, in {@code 1..naturalComponentCount()}. */
		public int naturalComponent(int index)
		{
			return naturalId[index];
		}

		public int naturalComponentCount()
		{
			return naturalComponentCount;
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
				int neighbour = walkable.index.get(tile);
				if (neighbour >= 0) result[count++] = neighbour;
			}
			return count == result.length ? result : Arrays.copyOf(result, count);
		}
	}

	/** Builds the walking graph used for cut generation; see {@link WalkingGraph}. */
	public static WalkingGraph walkingGraph(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports)
	{
		return walkingGraph(collision, rawTransports, SEED_TILE);
	}

	static WalkingGraph walkingGraph(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports, int seedTile)
	{
		WalkableTiles walkable = enumerateWalkableTiles(collision);
		int[] naturalId = new int[walkable.tiles.length];
		int naturalComponentCount = floodFillComponents(walkable, null, naturalId);
		boolean[] reachable = structuralReachability(collision, walkable, naturalId, naturalComponentCount,
			rawTransports, seedTile);
		return new WalkingGraph(walkable, naturalId, naturalComponentCount, reachable);
	}

	// ---- Step 0: enumerate every collision-walkable tile, ascending unsigned packed order ----

	private static final class WalkableTiles
	{
		final int[] tiles;
		final byte[] masks;
		final TileIndex index;

		WalkableTiles(int[] tiles, byte[] masks, TileIndex index)
		{
			this.tiles = tiles;
			this.masks = masks;
			this.index = index;
		}
	}

	private static WalkableTiles enumerateWalkableTiles(CollisionMap collision)
	{
		final int regionSize = 64;
		SplitFlagMap.RegionExtent extent = collision.regionExtent();
		int widthInclusive = extent.getWidth() + 1;
		IntArrayList tiles = new IntArrayList();
		ByteArrayList masks = new ByteArrayList();
		for (int ry = 0; ry <= extent.getHeight(); ry++)
		{
			int regionY = extent.getMinY() + ry;
			for (int rx = 0; rx <= extent.getWidth(); rx++)
			{
				int regionX = extent.getMinX() + rx;
				int index = rx + ry * widthInclusive;
				int planeCount = collision.getRegionPlaneCounts(index);
				if (planeCount <= 0)
				{
					continue;
				}
				int baseX = regionX * regionSize;
				int baseY = regionY * regionSize;
				for (int plane = 0; plane < planeCount; plane++)
				{
					for (int ly = 0; ly < regionSize; ly++)
					{
						int y = baseY + ly;
						for (int lx = 0; lx < regionSize; lx++)
						{
							int x = baseX + lx;
							if (!collision.isBlocked(x, y, plane))
							{
								int packed = WorldPointUtil.packWorldPoint(x, y, plane);
								tiles.add(packed);
								masks.add(collision.ordinaryWalkingMask(packed));
							}
						}
					}
				}
			}
		}
		int[] tileArray = tiles.toArray();
		byte[] maskArray = masks.toArray();
		sortUnsignedWithPayload(tileArray, maskArray);
		return new WalkableTiles(tileArray, maskArray, new TileIndex(tileArray));
	}

	// ---- Step 1/3: flood fill (natural when blocked==null, routing when blocked is a valid-cut set) ----

	/** Returns the number of components assigned (ids {@code 1..count}), 0 if there were no walkable tiles. */
	private static int floodFillComponents(WalkableTiles walkable, LongOpenHashSet blocked, int[] componentOut)
	{
		int n = walkable.tiles.length;
		int[] queue = new int[n == 0 ? 1 : n];
		int nextId = 1;
		for (int seed = 0; seed < n; seed++)
		{
			if (componentOut[seed] != 0)
			{
				continue;
			}
			int head = 0, tail = 0;
			queue[tail++] = seed;
			componentOut[seed] = nextId;
			while (head < tail)
			{
				int position = queue[head++];
				int tile = walkable.tiles[position];
				for (int neighbor : ordinaryNeighborsFromMask(tile, walkable.masks[position]))
				{
					int neighborPosition = walkable.index.get(neighbor);
					if (neighborPosition < 0 || componentOut[neighborPosition] != 0)
					{
						continue;
					}
					if (blocked != null && blocked.contains(canonicalKey(tile, neighbor)))
					{
						continue;
					}
					componentOut[neighborPosition] = nextId;
					queue[tail++] = neighborPosition;
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

	private static int largestGroup(int[] componentOf, int componentCount)
	{
		if (componentCount <= 0)
		{
			return 0;
		}
		int[] sizes = new int[componentCount + 1];
		for (int component : componentOf)
		{
			sizes[component]++;
		}
		int largest = 0;
		for (int size : sizes)
		{
			largest = Math.max(largest, size);
		}
		return largest;
	}

	// ---- Cuts: validity ("ignore cuts that are not current walking edges") + canonical dedup ----

	private static final class CutSet
	{
		final List<long[]> valid; // each entry {a, b} as raw ints boxed in a long[2] for simplicity
		final LongOpenHashSet blocked;
		final int duplicateCount;
		final int invalidCount;

		CutSet(List<long[]> valid, LongOpenHashSet blocked, int duplicateCount, int invalidCount)
		{
			this.valid = valid;
			this.blocked = blocked;
			this.duplicateCount = duplicateCount;
			this.invalidCount = invalidCount;
		}
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
		LongOpenHashSet blocked = new LongOpenHashSet(Math.max(16, candidates.size() * 2));
		int invalidCount = 0;
		for (long[] candidate : candidates)
		{
			int a = (int) candidate[0];
			int b = (int) candidate[1];
			int posA = walkable.index.get(a);
			int[] neighborsOfA = posA < 0 ? new int[0] : ordinaryNeighborsFromMask(a, walkable.masks[posA]);
			if (posA < 0 || !contains(neighborsOfA, 0, neighborsOfA.length, b))
			{
				invalidCount++;
				continue;
			}
			valid.add(candidate);
			blocked.add(canonicalKey(a, b));
		}
		return new CutSet(valid, blocked, duplicateCount, invalidCount);
	}

	private static long canonicalKey(int a, int b)
	{
		int min = Integer.compareUnsigned(a, b) <= 0 ? a : b;
		int max = Integer.compareUnsigned(a, b) <= 0 ? b : a;
		return (Integer.toUnsignedLong(min) << 32) | Integer.toUnsignedLong(max);
	}

	// ---- Structural reachability: closure over NATURAL components ----
	// Reachability must use natural, not routing, components: a cut never makes a tile unreachable.

	private static boolean[] structuralReachability(CollisionMap collision, WalkableTiles walkable, int[] naturalId,
		int naturalComponentCount, Map<Integer, Set<Transport>> rawTransports, int seedTile)
	{
		Map<Integer, List<Integer>> edges = new HashMap<>();
		Set<Integer> globalDestinations = new HashSet<>();
		for (Set<Transport> set : rawTransports.values())
		{
			for (Transport transport : set)
			{
				int origin = transport.getOrigin();
				int destination = transport.getDestination();
				boolean allowed = !STRUCTURAL_IGNORED_TYPES.contains(transport.getType());

				// attachmentEdges: every endpoint point of every transport, any type, any origin state.
				int[] originAttachments = origin == WorldPointUtil.UNDEFINED ? new int[0]
					: naturalAttachments(origin, walkable, naturalId, collision);
				int[] destinationAttachments = naturalAttachments(destination, walkable, naturalId, collision);
				addAttachmentClique(edges, originAttachments);
				addAttachmentClique(edges, destinationAttachments);

				if (!allowed)
				{
					continue;
				}
				if (origin == WorldPointUtil.UNDEFINED)
				{
					// globalDestinations: allowed global-teleport destinations.
					for (int naturalComponent : destinationAttachments)
					{
						globalDestinations.add(naturalComponent);
					}
				}
				else
				{
					// transportEdges: allowed local transports, directed origin -> destination.
					for (int fromComponent : originAttachments)
					{
						for (int toComponent : destinationAttachments)
						{
							edges.computeIfAbsent(fromComponent, k -> new ArrayList<>()).add(toComponent);
						}
					}
				}
			}
		}

		int[] seedComponents = naturalAttachments(seedTile, walkable, naturalId, collision);
		if (seedComponents.length == 0)
		{
			throw new IllegalStateException(
				"missing structural reachability seed: no natural component attaches to the production seed tile");
		}

		boolean[] reachable = new boolean[naturalComponentCount + 1];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int seed : seedComponents)
		{
			if (!reachable[seed])
			{
				reachable[seed] = true;
				queue.add(seed);
			}
		}
		while (!queue.isEmpty())
		{
			int component = queue.poll();
			for (int next : edges.getOrDefault(component, List.of()))
			{
				if (!reachable[next])
				{
					reachable[next] = true;
					queue.add(next);
				}
			}
			for (int global : globalDestinations)
			{
				if (!reachable[global])
				{
					reachable[global] = true;
					queue.add(global);
				}
			}
		}
		return reachable;
	}

	private static void addAttachmentClique(Map<Integer, List<Integer>> edges, int[] attachments)
	{
		if (attachments.length < 2)
		{
			return;
		}
		for (int a : attachments)
		{
			for (int b : attachments)
			{
				if (a != b)
				{
					edges.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
				}
			}
		}
	}

	/**
	 * Natural components a point attaches to: its own component if it is walkable, otherwise
	 * the union of its ordinary walking neighbours' components.
	 */
	private static int[] naturalAttachments(int point, WalkableTiles walkable, int[] naturalId, CollisionMap collision)
	{
		int position = walkable.index.get(point);
		if (position >= 0)
		{
			return new int[] {naturalId[position]};
		}
		int[] neighbors = collision.ordinaryWalkingNeighbors(point);
		int[] buffer = new int[8];
		int count = 0;
		for (int neighbor : neighbors)
		{
			int neighborPosition = walkable.index.get(neighbor);
			if (neighborPosition >= 0)
			{
				int component = naturalId[neighborPosition];
				if (!contains(buffer, 0, count, component))
				{
					buffer[count++] = component;
				}
			}
		}
		return Arrays.copyOf(buffer, count);
	}

	// ---- Section A: search tiles, routing components, walking masks, north/south ----

	private static final class SearchTiles
	{
		final int[] tiles, components, north, south;
		final byte[] masks;

		SearchTiles(int[] tiles, int[] components, byte[] masks, int[] north, int[] south)
		{
			this.tiles = tiles;
			this.components = components;
			this.masks = masks;
			this.north = north;
			this.south = south;
		}
	}

	private static SearchTiles buildSearchTiles(WalkableTiles walkable, int[] naturalId, int[] routingId,
		boolean[] reachableNatural)
	{
		int n = walkable.tiles.length;
		IntArrayList tiles = new IntArrayList();
		IntArrayList components = new IntArrayList();
		ByteArrayList masks = new ByteArrayList();
		for (int i = 0; i < n; i++)
		{
			if (reachableNatural[naturalId[i]])
			{
				tiles.add(walkable.tiles[i]);
				components.add(routingId[i]);
				masks.add(walkable.masks[i]);
			}
		}
		int[] tileArray = tiles.toArray();
		int[] componentArray = components.toArray();
		byte[] maskArray = masks.toArray();
		int[] north = new int[tileArray.length];
		int[] south = new int[tileArray.length];
		for (int i = 0; i < tileArray.length; i++)
		{
			north[i] = binarySearchUnsigned(tileArray, tileArray[i] + Y_UNIT);
			south[i] = binarySearchUnsigned(tileArray, tileArray[i] - Y_UNIT);
		}
		return new SearchTiles(tileArray, componentArray, maskArray, north, south);
	}

	// ---- Section: routing components / crossings ----

	private static final class Crossing
	{
		final int fromTile, toTile;

		Crossing(int fromTile, int toTile)
		{
			this.fromTile = fromTile;
			this.toTile = toTile;
		}
	}

	private static List<Crossing> buildCrossings(List<long[]> validCuts, int[] routingId, WalkableTiles walkable)
	{
		List<Crossing> crossings = new ArrayList<>();
		for (long[] cut : validCuts)
		{
			int a = (int) cut[0];
			int b = (int) cut[1];
			int posA = walkable.index.get(a);
			int posB = walkable.index.get(b);
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

	// ---- Sections B/C/D/E: sites and attachments ----

	private static final class SitesAndAttachments
	{
		final int[] siteTiles, siteComponentOffsets, siteComponentIds, componentSiteOffsets, componentSiteIds;
		final int[] reachableBankTiles;

		SitesAndAttachments(int[] siteTiles, int[] siteComponentOffsets, int[] siteComponentIds,
			int[] componentSiteOffsets, int[] componentSiteIds, int[] reachableBankTiles)
		{
			this.siteTiles = siteTiles;
			this.siteComponentOffsets = siteComponentOffsets;
			this.siteComponentIds = siteComponentIds;
			this.componentSiteOffsets = componentSiteOffsets;
			this.componentSiteIds = componentSiteIds;
			this.reachableBankTiles = reachableBankTiles;
		}
	}

	private static SitesAndAttachments buildSites(Map<Integer, Set<Transport>> rawTransports, Set<Integer> bankTiles,
		List<Crossing> crossings, SearchTiles search, CollisionMap collision, int routingComponentCount)
	{
		HashSet<Integer> siteSet = new HashSet<>();
		for (Set<Transport> set : rawTransports.values())
		{
			for (Transport transport : set)
			{
				if (transport.getOrigin() != WorldPointUtil.UNDEFINED)
				{
					siteSet.add(transport.getOrigin());
				}
				siteSet.add(transport.getDestination());
			}
		}
		for (Crossing crossing : crossings)
		{
			siteSet.add(crossing.fromTile);
			siteSet.add(crossing.toTile);
		}
		List<Integer> reachableBanks = new ArrayList<>();
		for (int bank : bankTiles)
		{
			if (routingAttachments(bank, search, collision).length > 0)
			{
				reachableBanks.add(bank);
				siteSet.add(bank);
			}
		}

		int[] siteTiles = toSortedUnsignedArray(siteSet);
		int[] reachableBankTiles = toSortedUnsignedArray(reachableBanks);

		int[] siteComponentOffsets = new int[siteTiles.length + 1];
		int[][] perSite = new int[siteTiles.length][];
		int totalSiteComponentValues = 0;
		for (int i = 0; i < siteTiles.length; i++)
		{
			perSite[i] = routingAttachments(siteTiles[i], search, collision);
			siteComponentOffsets[i + 1] = siteComponentOffsets[i] + perSite[i].length;
			totalSiteComponentValues += perSite[i].length;
		}
		int[] siteComponentIds = new int[totalSiteComponentValues];
		int cursor = 0;
		for (int[] components : perSite)
		{
			for (int component : components)
			{
				siteComponentIds[cursor++] = component;
			}
		}

		int[] groupCounts = new int[routingComponentCount];
		for (int component : siteComponentIds)
		{
			groupCounts[component]++;
		}
		int[] componentSiteOffsets = new int[routingComponentCount + 1];
		for (int i = 0; i < routingComponentCount; i++)
		{
			componentSiteOffsets[i + 1] = componentSiteOffsets[i] + groupCounts[i];
		}
		int[] componentSiteIds = new int[componentSiteOffsets[routingComponentCount]];
		int[] fillCursor = Arrays.copyOf(componentSiteOffsets, componentSiteOffsets.length);
		for (int site = 0; site < siteTiles.length; site++)
		{
			for (int p = siteComponentOffsets[site]; p < siteComponentOffsets[site + 1]; p++)
			{
				int component = siteComponentIds[p];
				componentSiteIds[fillCursor[component]++] = site;
			}
		}

		return new SitesAndAttachments(siteTiles, siteComponentOffsets, siteComponentIds, componentSiteOffsets,
			componentSiteIds, reachableBankTiles);
	}

	/**
	 * Routing components a point attaches to, using the final reachability-filtered search tiles:
	 * the tile's own component if it is a search tile, otherwise those of its walking neighbours.
	 * This matches {@link RoutingStatic#attachments} at runtime.
	 */
	private static int[] routingAttachments(int point, SearchTiles search, CollisionMap collision)
	{
		int index = binarySearchUnsigned(search.tiles, point);
		if (index >= 0)
		{
			return new int[] {search.components[index]};
		}
		int[] neighbors = collision.ordinaryWalkingNeighbors(point);
		int[] buffer = new int[8];
		int count = 0;
		for (int neighbor : neighbors)
		{
			int neighborIndex = binarySearchUnsigned(search.tiles, neighbor);
			if (neighborIndex >= 0)
			{
				int component = search.components[neighborIndex];
				if (!contains(buffer, 0, count, component))
				{
					buffer[count++] = component;
				}
			}
		}
		Arrays.sort(buffer, 0, count);
		return Arrays.copyOf(buffer, count);
	}

	// ---- Small shared utilities ----

	private static boolean contains(int[] values, int start, int end, int target)
	{
		for (int i = start; i < end; i++)
		{
			if (values[i] == target)
			{
				return true;
			}
		}
		return false;
	}

	private static int count(boolean[] values)
	{
		int count = 0;
		for (boolean value : values)
		{
			if (value)
			{
				count++;
			}
		}
		return count;
	}

	private static int[] toSortedUnsignedArray(java.util.Collection<Integer> values)
	{
		int[] result = new int[values.size()];
		int i = 0;
		for (int value : values)
		{
			result[i++] = value;
		}
		sortUnsigned(result);
		return result;
	}

	private static void sortUnsigned(int[] values)
	{
		for (int i = 0; i < values.length; i++)
		{
			values[i] ^= Integer.MIN_VALUE;
		}
		Arrays.sort(values);
		for (int i = 0; i < values.length; i++)
		{
			values[i] ^= Integer.MIN_VALUE;
		}
	}

	/**
	 * Sorts {@code keys} into ascending unsigned order, permuting {@code payload} (one byte per
	 * key) the same way. Packs each (flipped-signed key, payload byte) pair into a single
	 * {@code long} so the whole sort is a primitive {@code long[]} sort with no boxing, which
	 * matters at multi-million-tile scale.
	 */
	private static void sortUnsignedWithPayload(int[] keys, byte[] payload)
	{
		long[] packed = new long[keys.length];
		for (int i = 0; i < keys.length; i++)
		{
			long unsignedKey = Integer.toUnsignedLong(keys[i]);
			packed[i] = (unsignedKey << 8) | (payload[i] & 0xffL);
		}
		Arrays.sort(packed);
		for (int i = 0; i < keys.length; i++)
		{
			keys[i] = (int) (packed[i] >>> 8);
			payload[i] = (byte) packed[i];
		}
	}

	private static int nextPowerOfTwo(int value)
	{
		int highest = Integer.highestOneBit(value);
		return highest == value ? highest : highest * 2;
	}

	private static int binarySearchUnsigned(int[] values, int target)
	{
		int low = 0, high = values.length - 1;
		while (low <= high)
		{
			int middle = low + (high - low) / 2;
			int compare = Integer.compareUnsigned(values[middle], target);
			if (compare == 0)
			{
				return middle;
			}
			if (compare < 0)
			{
				low = middle + 1;
			}
			else
			{
				high = middle - 1;
			}
		}
		return -1;
	}

	// ---- Primitive-int growable list / hash structures (avoid boxing at multi-million-tile scale) ----

	private static final class IntArrayList
	{
		private int[] data = new int[1024];
		private int size;

		void add(int value)
		{
			if (size == data.length)
			{
				data = Arrays.copyOf(data, data.length * 2);
			}
			data[size++] = value;
		}

		int[] toArray()
		{
			return Arrays.copyOf(data, size);
		}
	}

	private static final class ByteArrayList
	{
		private byte[] data = new byte[1024];
		private int size;

		void add(byte value)
		{
			if (size == data.length)
			{
				data = Arrays.copyOf(data, data.length * 2);
			}
			data[size++] = value;
		}

		byte[] toArray()
		{
			return Arrays.copyOf(data, size);
		}
	}

	/** Open-addressing packed-tile (u32) to array-index map, sized for tens of millions of entries. */
	private static final class TileIndex
	{
		private final int[] keys;
		private final int[] slots; // slots[h] - 1 == index; 0 == empty
		private final int mask;

		TileIndex(int[] sortedTiles)
		{
			int capacity = nextPowerOfTwo(Math.max(16, sortedTiles.length) * 4);
			keys = new int[capacity];
			slots = new int[capacity];
			mask = capacity - 1;
			for (int i = 0; i < sortedTiles.length; i++)
			{
				int h = hash(sortedTiles[i]) & mask;
				while (slots[h] != 0)
				{
					h = (h + 1) & mask;
				}
				keys[h] = sortedTiles[i];
				slots[h] = i + 1;
			}
		}

		int get(int tile)
		{
			int h = hash(tile) & mask;
			while (slots[h] != 0)
			{
				if (keys[h] == tile)
				{
					return slots[h] - 1;
				}
				h = (h + 1) & mask;
			}
			return -1;
		}

		private static int hash(int value)
		{
			int h = value * 0x9E3779B1;
			return h ^ (h >>> 16);
		}
	}

	/** Open-addressing packed 64-bit canonical-cut-pair set. */
	private static final class LongOpenHashSet
	{
		private long[] keys;
		private int mask;
		private int size;

		LongOpenHashSet(int expectedSize)
		{
			int capacity = nextPowerOfTwo(Math.max(16, expectedSize) * 2);
			keys = new long[capacity];
			Arrays.fill(keys, Long.MIN_VALUE);
			mask = capacity - 1;
		}

		void add(long value)
		{
			int h = hash(value) & mask;
			while (keys[h] != Long.MIN_VALUE)
			{
				if (keys[h] == value)
				{
					return;
				}
				h = (h + 1) & mask;
			}
			keys[h] = value;
			size++;
		}

		boolean contains(long value)
		{
			int h = hash(value) & mask;
			while (keys[h] != Long.MIN_VALUE)
			{
				if (keys[h] == value)
				{
					return true;
				}
				h = (h + 1) & mask;
			}
			return false;
		}

		private static int hash(long value)
		{
			long h = value * 0x9E3779B97F4A7C15L;
			h ^= h >>> 32;
			return (int) h;
		}
	}
}
