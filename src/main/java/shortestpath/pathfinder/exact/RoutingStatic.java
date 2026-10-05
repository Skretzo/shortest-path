package shortestpath.pathfinder.exact;

import java.io.IOException;
import java.util.Arrays;
import shortestpath.pathfinder.CollisionMap;

/** Immutable primitive-array representation of the exact backend's static routing data. */
public final class RoutingStatic
{
	private final int[] searchTiles, searchComponents, northNodes, southNodes;
	private final byte[] walkingMasks;
	private final int[] siteTiles, siteComponentOffsets, siteComponentIds;
	private final int routingComponentCount;
	private final int[] componentSiteOffsets, componentSiteIds, reachableBankTiles;
	private final byte[] reachableBankNodes, reachableBankSites;
	private final int[] crossingFromSite, crossingToSite, crossingCosts;
	private final int sparseOriginalCount, sparseVertexCount, sparseSteinerCount;
	private final int sparseUndirectedEdgeCount, sparseAdjacencyCount;
	private final int[] sparseOffsets, sparseDestinations, sparseWeights;

	private RoutingStatic(int[] searchTiles, int[] searchComponents, byte[] walkingMasks, int[] northNodes,
		int[] southNodes, int[] siteTiles, int[] siteComponentOffsets, int[] siteComponentIds,
		int routingComponentCount, int[] componentSiteOffsets, int[] componentSiteIds, int[] reachableBankTiles,
		int[] crossingFromSite, int[] crossingToSite, int[] crossingCosts, int sparseOriginalCount,
		int sparseVertexCount, int sparseSteinerCount, int sparseUndirectedEdgeCount, int sparseAdjacencyCount,
		int[] sparseOffsets, int[] sparseDestinations, int[] sparseWeights)
	{
		this.searchTiles = searchTiles;
		this.searchComponents = searchComponents;
		this.walkingMasks = walkingMasks;
		this.northNodes = northNodes;
		this.southNodes = southNodes;
		this.siteTiles = siteTiles;
		this.siteComponentOffsets = siteComponentOffsets;
		this.siteComponentIds = siteComponentIds;
		this.routingComponentCount = routingComponentCount;
		this.componentSiteOffsets = componentSiteOffsets;
		this.componentSiteIds = componentSiteIds;
		this.reachableBankTiles = reachableBankTiles;
		this.reachableBankNodes = new byte[searchTiles.length];
		this.reachableBankSites = new byte[siteTiles.length];
		for (int tile : reachableBankTiles)
		{
			int node = binarySearch(searchTiles, tile);
			if (node >= 0) reachableBankNodes[node] = 1;
			int site = binarySearch(siteTiles, tile);
			if (site >= 0) reachableBankSites[site] = 1;
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

	static RoutingStatic create(int[] searchTiles, int[] searchComponents, byte[] walkingMasks, int[] northNodes,
		int[] southNodes, int[] siteTiles, int[] siteComponentOffsets, int[] siteComponentIds,
		int routingComponentCount, int[] componentSiteOffsets, int[] componentSiteIds, int[] reachableBankTiles,
		int[] crossingFromSite, int[] crossingToSite, int[] crossingCosts, int sparseOriginalCount,
		int sparseVertexCount, int sparseSteinerCount, int sparseUndirectedEdgeCount, int sparseAdjacencyCount,
		int[] sparseOffsets, int[] sparseDestinations, int[] sparseWeights) throws IOException
	{
		RoutingStatic result = new RoutingStatic(searchTiles.clone(), searchComponents.clone(), walkingMasks.clone(),
			northNodes.clone(), southNodes.clone(), siteTiles.clone(), siteComponentOffsets.clone(),
			siteComponentIds.clone(), routingComponentCount, componentSiteOffsets.clone(), componentSiteIds.clone(),
			reachableBankTiles.clone(), crossingFromSite.clone(), crossingToSite.clone(), crossingCosts.clone(),
			sparseOriginalCount, sparseVertexCount, sparseSteinerCount, sparseUndirectedEdgeCount, sparseAdjacencyCount,
			sparseOffsets.clone(), sparseDestinations.clone(), sparseWeights.clone());
		result.validate();
		return result;
	}

	private void validate() throws IOException
	{
		aligned(searchComponents, searchTiles.length, "search components");
		aligned(walkingMasks, searchTiles.length, "walking masks");
		aligned(northNodes, searchTiles.length, "north nodes");
		aligned(southNodes, searchTiles.length, "south nodes");
		ascending(searchTiles, "search tiles");
		ascending(siteTiles, "site tiles");
		ascending(reachableBankTiles, "reachable bank tiles");
		refs(searchComponents, routingComponentCount, "search component");
		nodeRefs(northNodes, searchTiles.length, "north node");
		nodeRefs(southNodes, searchTiles.length, "south node");
		csr(siteComponentOffsets, siteComponentIds, siteTiles.length, routingComponentCount, "site components");
		csr(componentSiteOffsets, componentSiteIds, routingComponentCount, siteTiles.length, "component sites");
		for (int tile : reachableBankTiles)
			if (siteIndex(tile) < 0)
				throw new IOException("reachable bank tile is not a static site: " + Integer.toUnsignedString(tile));
		aligned(crossingToSite, crossingFromSite.length, "crossing destinations");
		aligned(crossingCosts, crossingFromSite.length, "crossing costs");
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

	private static void aligned(Object array, int length, String label) throws IOException
	{
		if (java.lang.reflect.Array.getLength(array) != length)
			throw new IOException(label + " length mismatch");
	}

	private static void ascending(int[] values, String label) throws IOException
	{
		for (int i = 1; i < values.length; i++)
			if (Integer.compareUnsigned(values[i - 1], values[i]) >= 0)
				throw new IOException(label + " is not strictly ascending at index " + i);
	}

	private static void refs(int[] values, int limit, String label) throws IOException
	{
		for (int value : values)
			if (value < 0 || value >= limit)
				throw new IOException(label + " out of range");
	}

	private static void nodeRefs(int[] values, int limit, String label) throws IOException
	{
		for (int value : values)
			if (value != -1 && (value < 0 || value >= limit))
				throw new IOException(label + " out of range");
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
				if (!contains(componentSiteIds, componentSiteOffsets[siteComponentIds[p]],
						componentSiteOffsets[siteComponentIds[p] + 1], site))
					throw new IOException("site/component relation is not reciprocal");
		for (int component = 0; component < routingComponentCount; component++)
			for (int p = componentSiteOffsets[component]; p < componentSiteOffsets[component + 1]; p++)
				if (!contains(siteComponentIds, siteComponentOffsets[componentSiteIds[p]],
						siteComponentOffsets[componentSiteIds[p] + 1], component))
					throw new IOException("component/site relation is not reciprocal");
	}

	private static boolean contains(int[] values, int start, int end, int target)
	{
		for (int i = start; i < end; i++)
			if (values[i] == target)
				return true;
		return false;
	}

	public int searchTileCount()
	{
		return searchTiles.length;
	}
	public int siteCount()
	{
		return siteTiles.length;
	}
	public int routingComponentCount()
	{
		return routingComponentCount;
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
		return reachableBankNodes[node] != 0;
	}
	/** Whether the site is a bank in the world; see {@link #isBankNode(int)}. */
	public boolean isBankSite(int site)
	{
		return site >= 0 && reachableBankSites[site] != 0;
	}
	public int crossingCount()
	{
		return crossingFromSite.length;
	}
	public int sparseVertexCount()
	{
		return sparseVertexCount;
	}
	public int sparseOriginalCount()
	{
		return sparseOriginalCount;
	}
	public int sparseSteinerCount()
	{
		return sparseSteinerCount;
	}
	public int sparseAdjacencyCount()
	{
		return sparseAdjacencyCount;
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
		return northNodes[index];
	}
	public int southNode(int index)
	{
		return southNodes[index];
	}
	public int routingComponent(int index)
	{
		return searchComponents[index];
	}
	public int siteTile(int index)
	{
		return siteTiles[index];
	}
	public int searchIndex(int packed)
	{
		return binarySearch(searchTiles, packed);
	}
	public int siteIndex(int packed)
	{
		return binarySearch(siteTiles, packed);
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
			return new int[] {searchComponents[index]};
		int[] result = new int[8];
		int count = 0;
		for (int neighbor : collision.ordinaryWalkingNeighbors(packedPoint))
		{
			int neighborIndex = searchIndex(neighbor);
			if (neighborIndex >= 0 && !contains(result, 0, count, searchComponents[neighborIndex]))
				result[count++] = searchComponents[neighborIndex];
		}
		Arrays.sort(result, 0, count);
		return Arrays.copyOf(result, count);
	}

	private static int binarySearch(int[] values, int target)
	{
		int low = 0, high = values.length - 1;
		while (low <= high)
		{
			int middle = low + (high - low) / 2;
			int compare = Integer.compareUnsigned(values[middle], target);
			if (compare == 0)
				return middle;
			if (compare < 0)
				low = middle + 1;
			else
				high = middle - 1;
		}
		return -1;
	}
}
