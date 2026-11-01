package shortestpath.pathfinder.astar;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.transport.Transport;

/**
 * Builds the A* backend's {@link RoutingStatic} from a {@link ComponentLayer} (the routing
 * components of every walkable tile, derived offline from the collision map and the separator
 * cuts), the {@link CollisionMap}, the raw (account-independent) transports and global teleports,
 * and the bank list.
 *
 * <ol>
 *   <li>Natural components: the layer's routing components joined through its crossings.</li>
 *   <li>Structural reachability: natural components reachable from Lumbridge through any
 *       transport or global teleport of any type, seasonal included, regardless of requirements.</li>
 *   <li>Search tiles: every tile of a reachable natural component, with its walking mask, routing
 *       component and north/south neighbour indexes.</li>
 *   <li>Sites (transport endpoints, crossing endpoints and reachable banks), their component
 *       attachments in both directions, and separator crossings.</li>
 *   <li>The sparse walking network over the sites ({@link SparseWalkingNetworkBuilder}).</li>
 * </ol>
 *
 * <p>Routing components are numbered by rank among the candidates: the components attached to a
 * transport endpoint, the seed tile or a crossing endpoint, which is how the numbers of a
 * derivation that only ever saw that land come out. Endpoints of cuts that were dropped when the
 * layer was generated (no longer walking edges, or no longer separating) are not candidates, so on
 * unreachable land the component count and the ids numbered after them can differ from a derivation
 * that treated every cut endpoint as an attachment; routes never do.
 */
public final class RoutingStaticLoader
{
	/** Structural-reachability seed tile (Lumbridge). */
	static final int SEED_TILE = WorldPointUtil.packWorldPoint(3221, 3218, 0);
	/** One full {@code y} unit in the packed encoding (bit 15). */
	private static final int Y_UNIT = 1 << 15;
	/**
	 * Upper bound on the search tiles of one layer. The world has well under ten million walkable
	 * tiles; the bound only keeps a damaged layer from sizing arrays that cannot be allocated.
	 */
	private static final long MAX_SEARCH_TILES = Integer.MAX_VALUE / 16;

	private RoutingStaticLoader()
	{
	}

	/**
	 * Builds the derived {@link RoutingStatic} from the packaged component layer.
	 *
	 * @param collision     authoritative walking-neighbour source.
	 * @param rawTransports the plugin's raw, unfiltered, account-independent transports, keyed by
	 *                      packed origin tile or {@link WorldPointUtil#UNDEFINED} for origin-less
	 *                      (global) transports/teleports. Must include every transport type,
	 *                      seasonal included.
	 * @param bankTiles     the whole bank destination set, with no account filtering.
	 * @throws IOException when the layer is missing, malformed, or does not fit the collision map.
	 */
	public static RoutingStatic build(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports,
		Set<Integer> bankTiles) throws IOException
	{
		try (InputStream stream = ComponentLayer.class.getResourceAsStream(ComponentLayer.RESOURCE))
		{
			return build(collision, rawTransports, bankTiles, ComponentLayer.read(stream), SEED_TILE);
		}
	}

	/** As above, from a given layer and structural reachability seed tile. */
	static RoutingStatic build(CollisionMap collision, Map<Integer, Set<Transport>> rawTransports,
		Set<Integer> bankTiles, ComponentLayer layer, int seedTile) throws IOException
	{
		for (int tile : layer.crossings)
		{
			if (layer.component(tile) == 0)
				throw new IOException("routing component layer is stale: crossing tile " + tile + " is not covered");
		}
		int[] natural = naturalComponents(layer);
		boolean[] candidate = new boolean[natural.length];
		boolean[] reachable = structuralReachability(collision, layer, natural, candidate, rawTransports, seedTile);
		for (int tile : layer.crossings) candidate[natural[layer.component(tile)]] = true;

		// Routing components are ranked among the candidates; ranking by reachability would renumber tiles.
		int[] renumbered = new int[natural.length];
		int routingComponentCount = 1;
		for (int id = 1; id < natural.length; id++)
		{
			if (candidate[natural[id]]) renumbered[id] = routingComponentCount++;
		}

		// The reader validates each run alone, so the total is bounded here, before anything is sized from it.
		long total = 0;
		for (int run = 0; run < layer.starts.length; run++)
		{
			if (reachable[natural[layer.ids[run]]]) total += layer.lengths[run];
		}
		if (total > MAX_SEARCH_TILES)
			throw new IOException("routing component layer is stale: " + total + " search tiles");
		int count = (int) total;
		int[] tiles = new int[count];
		byte[] masks = new byte[count];
		TileComponents components = new TileComponents(count, routingComponentCount);
		int next = 0;
		for (int run = 0; run < layer.starts.length; run++)
		{
			if (!reachable[natural[layer.ids[run]]]) continue;
			for (int offset = 0; offset < layer.lengths[run]; offset++)
			{
				int tile = layer.starts[run] + offset;
				byte mask = collision.ordinaryWalkingMask(tile);
				if (mask == 0) throw new IOException("routing component layer is stale: tile " + tile + " is not walkable");
				tiles[next] = tile;
				masks[next] = mask;
				components.set(next++, renumbered[layer.ids[run]]);
			}
		}

		// The tiles ascend in unsigned packed order, so both neighbour indexes come from one sweep each.
		char[] north = new char[count];
		char[] south = new char[count];
		for (int i = 0, n = 0, s = 0; i < count; i++)
		{
			int y = WorldPointUtil.unpackWorldY(tiles[i]);
			if (y != 32767)
			{
				while (n < count && Integer.compareUnsigned(tiles[n], tiles[i] + Y_UNIT) < 0) n++;
				if (n < count && tiles[n] == tiles[i] + Y_UNIT) north[i] = (char) (n - i);
			}
			if (y != 0)
			{
				while (Integer.compareUnsigned(tiles[s], tiles[i] - Y_UNIT) < 0) s++;
				if (tiles[s] == tiles[i] - Y_UNIT) south[i] = (char) (i - s);
			}
		}

		SitesAndAttachments sites = buildSites(rawTransports, bankTiles, layer.crossings, tiles, components, collision,
			routingComponentCount);
		int crossingCount = layer.crossings.length / 2;
		int[] crossingFrom = new int[crossingCount];
		int[] crossingTo = new int[crossingCount];
		int[] crossingCost = new int[crossingCount];
		for (int i = 0; i < crossingCount; i++)
		{
			crossingFrom[i] = IntArrays.binarySearch(sites.siteTiles, layer.crossings[2 * i]);
			crossingTo[i] = IntArrays.binarySearch(sites.siteTiles, layer.crossings[2 * i + 1]);
			crossingCost[i] = 1;
		}

		// The exact walks need the search tiles and sites in their final form, so they walk a
		// first RoutingStatic without a network, and the network is built after them.
		int siteCount = sites.siteTiles.length;
		RoutingStatic walking = RoutingStatic.takeOwnership(tiles, components, masks, north, south, sites.siteTiles,
			sites.siteComponentOffsets, sites.siteComponentIds, routingComponentCount, sites.componentSiteOffsets,
			sites.componentSiteIds, sites.reachableBankTiles, crossingFrom, crossingTo, crossingCost, siteCount,
			siteCount, 0, 0, 0, new int[siteCount + 1], new int[0], new int[0]);
		ExactWalks exactWalks = ExactWalks.build(walking);
		SparseWalkingNetworkBuilder.Result sparse = SparseWalkingNetworkBuilder.build(sites.siteTiles,
			sites.siteComponentOffsets, sites.siteComponentIds, exactWalks);
		return RoutingStatic.takeOwnership(tiles, components, masks, north, south, sites.siteTiles,
			sites.siteComponentOffsets, sites.siteComponentIds, routingComponentCount, sites.componentSiteOffsets,
			sites.componentSiteIds, sites.reachableBankTiles, crossingFrom, crossingTo, crossingCost,
			sparse.originalCount, sparse.vertexCount, sparse.steinerCount, sparse.undirectedEdgeCount,
			sparse.adjacencyCount(), sparse.offsets, sparse.destinations, sparse.weights, exactWalks);
	}

	/**
	 * The natural component (the root routing component id) of every routing component id: the
	 * components the layer's crossings join. Index 0 is unused.
	 */
	static int[] naturalComponents(ComponentLayer layer)
	{
		int componentCount = 1;
		for (int id : layer.ids) componentCount = Math.max(componentCount, id + 1);
		int[] parent = new int[componentCount];
		for (int i = 0; i < componentCount; i++) parent[i] = i;
		for (int i = 0; i < layer.crossings.length; i += 2)
			parent[root(parent, layer.component(layer.crossings[i]))] = root(parent, layer.component(layer.crossings[i + 1]));
		for (int i = 0; i < componentCount; i++) parent[i] = root(parent, i);
		return parent;
	}

	private static int root(int[] parent, int id)
	{
		while (parent[id] != id)
		{
			parent[id] = parent[parent[id]];
			id = parent[id];
		}
		return id;
	}

	// ---- Structural reachability: closure over NATURAL components ----
	// Reachability must use natural, not routing, components: a cut never makes a tile unreachable.
	// The closure is an account-independent overapproximation of the geography routes can use, so it
	// deliberately follows every known transport, seasonal ones included, whatever its requirements.
	// It only prunes components nothing can reach, such as the sea. Whether an account can take a
	// transport is decided later, by PreparedRoutingAccount and the forward search.

	/**
	 * Which natural components are structurally reachable. Every component a transport endpoint or
	 * the seed attaches to is also marked in {@code candidate}.
	 */
	static boolean[] structuralReachability(CollisionMap collision, ComponentLayer layer, int[] natural,
		boolean[] candidate, Map<Integer, Set<Transport>> rawTransports, int seedTile)
	{
		Map<Integer, List<Integer>> edges = new HashMap<>();
		Set<Integer> globalDestinations = new HashSet<>();
		for (Set<Transport> set : rawTransports.values())
		{
			for (Transport transport : set)
			{
				int origin = transport.getOrigin();
				int destination = transport.getDestination();

				// attachmentEdges: every endpoint point of every transport, any type, any origin state.
				int[] originAttachments = origin == WorldPointUtil.UNDEFINED ? new int[0]
					: naturalAttachments(origin, layer, natural, collision);
				int[] destinationAttachments = naturalAttachments(destination, layer, natural, collision);
				addAttachmentClique(edges, originAttachments);
				addAttachmentClique(edges, destinationAttachments);
				for (int component : originAttachments) candidate[component] = true;
				for (int component : destinationAttachments) candidate[component] = true;

				if (origin == WorldPointUtil.UNDEFINED)
				{
					// globalDestinations: global-teleport destinations.
					for (int component : destinationAttachments) globalDestinations.add(component);
				}
				else
				{
					// transportEdges: local transports, directed origin -> destination.
					for (int fromComponent : originAttachments)
					{
						for (int toComponent : destinationAttachments)
							edges.computeIfAbsent(fromComponent, k -> new ArrayList<>()).add(toComponent);
					}
				}
			}
		}

		int[] seedComponents = naturalAttachments(seedTile, layer, natural, collision);
		if (seedComponents.length == 0)
		{
			throw new IllegalStateException(
				"missing structural reachability seed: no natural component attaches to the production seed tile");
		}

		boolean[] reachable = new boolean[natural.length];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		for (int seed : seedComponents)
		{
			candidate[seed] = true;
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
		if (attachments.length < 2) return;
		for (int a : attachments)
		{
			for (int b : attachments)
			{
				if (a != b) edges.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
			}
		}
	}

	/**
	 * Natural components a point attaches to: its own component if it is walkable, otherwise
	 * the union of its ordinary walking neighbours' components.
	 */
	private static int[] naturalAttachments(int point, ComponentLayer layer, int[] natural, CollisionMap collision)
	{
		int id = layer.component(point);
		if (id != 0) return new int[] {natural[id]};
		int[] buffer = new int[8];
		int count = 0;
		for (int neighbor : collision.ordinaryWalkingNeighbors(point))
		{
			int neighborId = layer.component(neighbor);
			if (neighborId != 0 && !IntArrays.contains(buffer, 0, count, natural[neighborId]))
				buffer[count++] = natural[neighborId];
		}
		return Arrays.copyOf(buffer, count);
	}

	// ---- Sites and attachments ----

	@AllArgsConstructor
	private static final class SitesAndAttachments
	{
		final int[] siteTiles, siteComponentOffsets, siteComponentIds, componentSiteOffsets, componentSiteIds;
		final int[] reachableBankTiles;
	}

	private static SitesAndAttachments buildSites(Map<Integer, Set<Transport>> rawTransports, Set<Integer> bankTiles,
		int[] crossings, int[] tiles, TileComponents components, CollisionMap collision, int routingComponentCount)
	{
		HashSet<Integer> siteSet = new HashSet<>();
		for (Set<Transport> set : rawTransports.values())
		{
			for (Transport transport : set)
			{
				if (transport.getOrigin() != WorldPointUtil.UNDEFINED) siteSet.add(transport.getOrigin());
				siteSet.add(transport.getDestination());
			}
		}
		for (int tile : crossings) siteSet.add(tile);
		List<Integer> reachableBanks = new ArrayList<>();
		for (int bank : bankTiles)
		{
			if (routingAttachments(bank, tiles, components, collision).length > 0)
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
			perSite[i] = routingAttachments(siteTiles[i], tiles, components, collision);
			siteComponentOffsets[i + 1] = siteComponentOffsets[i] + perSite[i].length;
			totalSiteComponentValues += perSite[i].length;
		}
		int[] siteComponentIds = new int[totalSiteComponentValues];
		int cursor = 0;
		for (int[] siteComponents : perSite)
		{
			for (int component : siteComponents) siteComponentIds[cursor++] = component;
		}

		int[] groupCounts = new int[routingComponentCount];
		for (int component : siteComponentIds) groupCounts[component]++;
		int[] componentSiteOffsets = new int[routingComponentCount + 1];
		for (int i = 0; i < routingComponentCount; i++)
			componentSiteOffsets[i + 1] = componentSiteOffsets[i] + groupCounts[i];
		int[] componentSiteIds = new int[componentSiteOffsets[routingComponentCount]];
		int[] fillCursor = Arrays.copyOf(componentSiteOffsets, componentSiteOffsets.length);
		for (int site = 0; site < siteTiles.length; site++)
		{
			for (int p = siteComponentOffsets[site]; p < siteComponentOffsets[site + 1]; p++)
				componentSiteIds[fillCursor[siteComponentIds[p]]++] = site;
		}

		return new SitesAndAttachments(siteTiles, siteComponentOffsets, siteComponentIds, componentSiteOffsets,
			componentSiteIds, reachableBankTiles);
	}

	/**
	 * Routing components a point attaches to, using the final reachability-filtered search tiles:
	 * the tile's own component if it is a search tile, otherwise those of its walking neighbours.
	 * This matches {@link RoutingStatic#attachments} at runtime.
	 */
	private static int[] routingAttachments(int point, int[] tiles, TileComponents components, CollisionMap collision)
	{
		int index = IntArrays.binarySearch(tiles, point);
		if (index >= 0) return new int[] {components.get(index)};
		int[] buffer = new int[8];
		int count = 0;
		for (int neighbor : collision.ordinaryWalkingNeighbors(point))
		{
			int neighborIndex = IntArrays.binarySearch(tiles, neighbor);
			if (neighborIndex >= 0 && !IntArrays.contains(buffer, 0, count, components.get(neighborIndex)))
				buffer[count++] = components.get(neighborIndex);
		}
		Arrays.sort(buffer, 0, count);
		return Arrays.copyOf(buffer, count);
	}

	private static int[] toSortedUnsignedArray(Collection<Integer> values)
	{
		int[] result = new int[values.size()];
		int i = 0;
		for (int value : values) result[i++] = value;
		sortUnsigned(result);
		return result;
	}

	private static void sortUnsigned(int[] values)
	{
		for (int i = 0; i < values.length; i++) values[i] ^= Integer.MIN_VALUE;
		Arrays.sort(values);
		for (int i = 0; i < values.length; i++) values[i] ^= Integer.MIN_VALUE;
	}
}
