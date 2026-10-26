package shortestpath.pathfinder.astar;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import shortestpath.pathfinder.CollisionMap;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Immutable primitive-array representation of the A* backend's static routing data. */
public final class RoutingStatic
{
	private final int[] searchTiles;
	private final TileComponents searchComponents;
	// Zero means absent; other values are distances within the sorted tile array.
	private final char[] northOffsets, southOffsets;
	private final byte[] walkingMasks;
	private final int[] siteTiles, siteComponentOffsets, siteComponentIds;
	@Getter @Accessors(fluent = true) private final int routingComponentCount;
	private final int[] componentSiteOffsets, componentSiteIds, reachableBankTiles;
	private final BitSet reachableBankNodes, reachableBankSites;
	private final int[] crossingFromSite, crossingToSite, crossingCosts;
	@Getter @Accessors(fluent = true) private final int sparseOriginalCount, sparseVertexCount, sparseSteinerCount;
	private final int sparseUndirectedEdgeCount;
	@Getter @Accessors(fluent = true) private final int sparseAdjacencyCount;
	private final int[] sparseOffsets, sparseDestinations, sparseWeights;

	private RoutingStatic(int[] searchTiles, TileComponents searchComponents, byte[] walkingMasks,
		char[] northOffsets, char[] southOffsets, int[] siteTiles, int[] siteComponentOffsets, int[] siteComponentIds,
		int routingComponentCount, int[] componentSiteOffsets, int[] componentSiteIds, int[] reachableBankTiles,
		int[] crossingFromSite, int[] crossingToSite, int[] crossingCosts, int sparseOriginalCount,
		int sparseVertexCount, int sparseSteinerCount, int sparseUndirectedEdgeCount, int sparseAdjacencyCount,
		int[] sparseOffsets, int[] sparseDestinations, int[] sparseWeights)
	{
		this.searchTiles = searchTiles;
		this.searchComponents = searchComponents;
		this.walkingMasks = walkingMasks;
		this.northOffsets = northOffsets;
		this.southOffsets = southOffsets;
		this.siteTiles = siteTiles;
		this.siteComponentOffsets = siteComponentOffsets;
		this.siteComponentIds = siteComponentIds;
		this.routingComponentCount = routingComponentCount;
		this.componentSiteOffsets = componentSiteOffsets;
		this.componentSiteIds = componentSiteIds;
		this.reachableBankTiles = reachableBankTiles;
		this.reachableBankNodes = new BitSet(searchTiles.length);
		this.reachableBankSites = new BitSet(siteTiles.length);
		for (int tile : reachableBankTiles)
		{
			int node = IntArrays.binarySearch(searchTiles, tile);
			if (node >= 0) reachableBankNodes.set(node);
			int site = IntArrays.binarySearch(siteTiles, tile);
			if (site >= 0) reachableBankSites.set(site);
		}
		this.crossingFromSite = crossingFromSite;
		this.crossingToSite = crossingToSite;
		this.crossingCosts = crossingCosts;
		this.sparseOriginalCount = sparseOriginalCount;
		this.sparseVertexCount = sparseVertexCount;
		this.sparseSteinerCount = sparseSteinerCount;
		this.sparseUndirectedEdgeCount = sparseUndirectedEdgeCount;
		this.sparseAdjacencyCount = sparseAdjacencyCount;
		this.sparseOffsets = sparseOffsets;
		this.sparseDestinations = sparseDestinations;
		this.sparseWeights = sparseWeights;
	}

	/**
	 * Takes exclusive ownership of freshly built arrays and validates them before publication.
	 * Callers must never modify the supplied arrays or expose them to other code after this call.
	 */
	static RoutingStatic takeOwnership(int[] searchTiles, TileComponents searchComponents, byte[] walkingMasks,
		char[] northOffsets, char[] southOffsets, int[] siteTiles, int[] siteComponentOffsets, int[] siteComponentIds,
		int routingComponentCount, int[] componentSiteOffsets, int[] componentSiteIds, int[] reachableBankTiles,
		int[] crossingFromSite, int[] crossingToSite, int[] crossingCosts, int sparseOriginalCount,
		int sparseVertexCount, int sparseSteinerCount, int sparseUndirectedEdgeCount, int sparseAdjacencyCount,
		int[] sparseOffsets, int[] sparseDestinations, int[] sparseWeights) throws IOException
	{
		RoutingStatic result = new RoutingStatic(searchTiles, searchComponents, walkingMasks,
			northOffsets, southOffsets, siteTiles, siteComponentOffsets,
			siteComponentIds, routingComponentCount, componentSiteOffsets, componentSiteIds,
			reachableBankTiles, crossingFromSite, crossingToSite, crossingCosts,
			sparseOriginalCount, sparseVertexCount, sparseSteinerCount, sparseUndirectedEdgeCount, sparseAdjacencyCount,
			sparseOffsets, sparseDestinations, sparseWeights);
		result.validate();
		return result;
	}

	private void validate() throws IOException
	{
		aligned(searchComponents.length(), searchTiles.length, "search components");
		aligned(walkingMasks.length, searchTiles.length, "walking masks");
		aligned(northOffsets.length, searchTiles.length, "north nodes");
		aligned(southOffsets.length, searchTiles.length, "south nodes");
		ascending(searchTiles, "search tiles");
		ascending(siteTiles, "site tiles");
		ascending(reachableBankTiles, "reachable bank tiles");
		refs(searchComponents, routingComponentCount, "search component");
		nodeOffsets(northOffsets, true);
		nodeOffsets(southOffsets, false);
		csr(siteComponentOffsets, siteComponentIds, siteTiles.length, routingComponentCount, "site components");
		csr(componentSiteOffsets, componentSiteIds, routingComponentCount, siteTiles.length, "component sites");
		for (int tile : reachableBankTiles)
			if (siteIndex(tile) < 0)
				throw new IOException("reachable bank tile is not a static site: " + Integer.toUnsignedString(tile));
		aligned(crossingToSite.length, crossingFromSite.length, "crossing destinations");
		aligned(crossingCosts.length, crossingFromSite.length, "crossing costs");
		for (int i = 0; i < crossingFromSite.length; i++)
		{
			if (crossingFromSite[i] < 0 || crossingFromSite[i] >= siteTiles.length || crossingToSite[i] < 0
				|| crossingToSite[i] >= siteTiles.length)
				throw new IOException("crossing site out of range at index " + i);
			if (crossingFromSite[i] == crossingToSite[i] || crossingCosts[i] < 0)
				throw new IOException("invalid crossing at index " + i);
		}
		if (sparseOriginalCount != siteTiles.length || sparseSteinerCount != sparseVertexCount - sparseOriginalCount
			|| sparseAdjacencyCount != 2L * sparseUndirectedEdgeCount)
			throw new IOException("inconsistent sparse counts");
		csr(sparseOffsets, sparseDestinations, sparseVertexCount, sparseVertexCount, "sparse");
		for (int weight : sparseWeights)
			if (weight < 0)
				throw new IOException("negative sparse weight");
		if (sparseWeights.length != sparseDestinations.length)
			throw new IOException("sparse weight length mismatch");
		validateRelations();
	}

	private static void aligned(int actualLength, int expectedLength, String label) throws IOException
	{
		if (actualLength != expectedLength)
			throw new IOException(label + " length mismatch");
	}

	private static void ascending(int[] values, String label) throws IOException
	{
		for (int i = 1; i < values.length; i++)
			if (Integer.compareUnsigned(values[i - 1], values[i]) >= 0)
				throw new IOException(label + " is not strictly ascending at index " + i);
	}

	private static void refs(TileComponents values, int limit, String label) throws IOException
	{
		for (int i = 0; i < values.length(); i++)
		{
			int component = values.get(i);
			if (component < 0 || component >= limit)
				throw new IOException(label + " out of range");
		}
	}

	private static void nodeOffsets(char[] offsets, boolean north) throws IOException
	{
		for (int i = 0; i < offsets.length; i++)
		{
			int offset = offsets[i];
			int node = north ? i + offset : i - offset;
			if (offset > 32768 || node < 0 || node >= offsets.length)
				throw new IOException("neighbour offset out of range");
		}
	}

	private static void csr(int[] offsets, int[] values, int groups, int limit, String label) throws IOException
	{
		if (offsets.length != groups + 1 || offsets.length == 0 || offsets[0] != 0
			|| offsets[offsets.length - 1] != values.length)
			throw new IOException(label + " offsets are invalid");
		for (int i = 1; i < offsets.length; i++)
			if (offsets[i] < offsets[i - 1])
				throw new IOException(label + " offsets are not monotonic at index " + i);
		for (int value : values)
			if (value < 0 || value >= limit)
				throw new IOException(label + " reference out of range");
	}

	private void validateRelations() throws IOException
	{
		for (int site = 0; site < siteTiles.length; site++)
			for (int p = siteComponentOffsets[site]; p < siteComponentOffsets[site + 1]; p++)
				if (!IntArrays.contains(componentSiteIds, componentSiteOffsets[siteComponentIds[p]],
						componentSiteOffsets[siteComponentIds[p] + 1], site))
					throw new IOException("site/component relation is not reciprocal");
		for (int component = 0; component < routingComponentCount; component++)
			for (int p = componentSiteOffsets[component]; p < componentSiteOffsets[component + 1]; p++)
				if (!IntArrays.contains(siteComponentIds, siteComponentOffsets[componentSiteIds[p]],
						siteComponentOffsets[componentSiteIds[p] + 1], component))
					throw new IOException("component/site relation is not reciprocal");
	}


	public int searchTileCount()
	{
		return searchTiles.length;
	}
	public int siteCount()
	{
		return siteTiles.length;
	}

	public int reachableBankCount()
	{
		return reachableBankTiles.length;
	}
	public int reachableBankTile(int index)
	{
		return reachableBankTiles[index];
	}
	/**
	 * Whether the search tile is a bank in the world. This is account-independent: whether an
	 * account may bank there is {@link PreparedRoutingAccount#bankAccessible(int)}.
	 */
	public boolean isBankNode(int node)
	{
		return reachableBankNodes.get(node);
	}
	/** Whether the site is a bank in the world; see {@link #isBankNode(int)}. */
	public boolean isBankSite(int site)
	{
		return site >= 0 && reachableBankSites.get(site);
	}
	public int crossingCount()
	{
		return crossingFromSite.length;
	}
	public int searchTile(int index)
	{
		return searchTiles[index];
	}
	public int walkingMask(int index)
	{
		return Byte.toUnsignedInt(walkingMasks[index]);
	}
	public int northNode(int index)
	{
		return northOffsets[index] == 0 ? -1 : index + northOffsets[index];
	}
	public int southNode(int index)
	{
		return southOffsets[index] == 0 ? -1 : index - southOffsets[index];
	}
	public int routingComponent(int index)
	{
		return searchComponents.get(index);
	}
	public int siteTile(int index)
	{
		return siteTiles[index];
	}
	public int searchIndex(int packed)
	{
		return IntArrays.binarySearch(searchTiles, packed);
	}
	public int siteIndex(int packed)
	{
		return IntArrays.binarySearch(siteTiles, packed);
	}
	public int[] siteComponents(int site)
	{
		return Arrays.copyOfRange(siteComponentIds, siteComponentOffsets[site], siteComponentOffsets[site + 1]);
	}
	public int[] componentSites(int component)
	{
		return Arrays.copyOfRange(
			componentSiteIds, componentSiteOffsets[component], componentSiteOffsets[component + 1]);
	}
	public int crossingFromSite(int index)
	{
		return crossingFromSite[index];
	}
	public int crossingToSite(int index)
	{
		return crossingToSite[index];
	}
	public int crossingCost(int index)
	{
		return crossingCosts[index];
	}
	public int sparseOffset(int index)
	{
		return sparseOffsets[index];
	}
	public int sparseDestination(int index)
	{
		return sparseDestinations[index];
	}
	public int sparseWeight(int index)
	{
		return sparseWeights[index];
	}

	public int[] attachments(int packedPoint, CollisionMap collision)
	{
		int index = searchIndex(packedPoint);
		if (index >= 0)
			return new int[] {searchComponents.get(index)};
		int[] result = new int[8];
		int count = 0;
		for (int neighbor : collision.ordinaryWalkingNeighbors(packedPoint))
		{
			int neighborIndex = searchIndex(neighbor);
			if (neighborIndex >= 0 && !IntArrays.contains(result, 0, count, searchComponents.get(neighborIndex)))
				result[count++] = searchComponents.get(neighborIndex);
		}
		Arrays.sort(result, 0, count);
		return Arrays.copyOf(result, count);
	}

}
