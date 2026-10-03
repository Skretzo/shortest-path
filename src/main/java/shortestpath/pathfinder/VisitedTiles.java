package shortestpath.pathfinder;

import static net.runelite.api.Constants.REGION_SIZE;

import shortestpath.WorldPointUtil;

public class VisitedTiles
{
	private final SplitFlagMap.RegionExtent regionExtents;
	private final int widthInclusive;

	private final VisitedRegion[] visitedRegionsWithoutBank;
	private final VisitedRegion[] visitedRegionsWithBank;
	private final CollisionMap map;
	// Abstract nodes are visited separately from tile nodes because they represent
	// global search states, not map positions.
	private final boolean[] abstractVisitedWithoutBank = new boolean[AbstractNodeKind.values().length];
	private final boolean[] abstractVisitedWithBank = new boolean[AbstractNodeKind.values().length];
	private final int bankVisitCost;

	public VisitedTiles(CollisionMap map, int bankVisitCost)
	{
		this.map = map;
		this.bankVisitCost = bankVisitCost;
		regionExtents = SplitFlagMap.getRegionExtents();
		widthInclusive = regionExtents.getWidth() + 1;
		final int heightInclusive = regionExtents.getHeight() + 1;

		visitedRegionsWithoutBank = new VisitedRegion[widthInclusive * heightInclusive];
		visitedRegionsWithBank = new VisitedRegion[widthInclusive * heightInclusive];
	}

	public boolean get(int packedPoint, boolean bankVisited)
	{
		final int x = WorldPointUtil.unpackWorldX(packedPoint);
		final int y = WorldPointUtil.unpackWorldY(packedPoint);
		final int plane = WorldPointUtil.unpackWorldPlane(packedPoint);
		return get(x, y, plane, bankVisited);
	}

	public boolean get(int x, int y, int plane, boolean bankVisited)
	{
		VisitedRegion[] visitedRegions = bankVisited ? visitedRegionsWithBank : visitedRegionsWithoutBank;
		final int regionIndex = getRegionIndex(x / REGION_SIZE, y / REGION_SIZE);
		if (regionIndex < 0 || regionIndex >= visitedRegions.length)
		{
			return true; // Region is out of bounds; report that it's been visited to avoid exploring it
			// further
		}

		final VisitedRegion region = visitedRegions[regionIndex];
		if (region == null)
		{
			return false;
		}

		return region.get(x % REGION_SIZE, y % REGION_SIZE, plane);
	}

	public boolean set(int packedPoint, boolean bankVisited)
	{
		final int x = WorldPointUtil.unpackWorldX(packedPoint);
		final int y = WorldPointUtil.unpackWorldY(packedPoint);
		final int plane = WorldPointUtil.unpackWorldPlane(packedPoint);
		return set(x, y, plane, bankVisited);
	}

	public boolean get(int id, NodeGraph graph)
	{
		if (graph.isTile(id))
		{
			return get(graph.packedPosition(id), graph.bankVisited(id));
		}
		return getAbstract(graph.abstractKind(id), graph.bankVisited(id));
	}

	public boolean getAbstract(AbstractNodeKind abstractKind, boolean bankVisited)
	{
		return bankVisited
			? abstractVisitedWithBank[abstractKind.ordinal()]
			: abstractVisitedWithoutBank[abstractKind.ordinal()];
	}

	public boolean set(int id, NodeGraph graph)
	{
		if (graph.isTile(id))
		{
			return set(graph.packedPosition(id), graph.bankVisited(id));
		}

		final AbstractNodeKind abstractKind = graph.abstractKind(id);
		boolean visited = getAbstract(abstractKind, graph.bankVisited(id));
		if (graph.bankVisited(id))
		{
			abstractVisitedWithBank[abstractKind.ordinal()] = true;
		}
		// Dominance is only valid when banking is free. A banked arrival pays the configured
		// bank visit cost, so when that cost is positive the banked arrival can be strictly
		// more expensive than a later unbanked arrival at the same state — marking the
		// unbanked bucket would prune the cheaper continuation and lose the better route.
		if (bankVisitCost <= 0 || !graph.bankVisited(id))
		{
			abstractVisitedWithoutBank[abstractKind.ordinal()] = true;
		}
		return !visited;
	}

	public boolean set(int x, int y, int plane, boolean bankVisited)
	{
		final int regionIndex = getRegionIndex(x / REGION_SIZE, y / REGION_SIZE);
		if (regionIndex < 0 || regionIndex >= visitedRegionsWithoutBank.length)
		{
			return false; // Region is out of bounds; report that it's been visited to avoid exploring it
			// further
		}

		if (bankVisited)
		{
			boolean unique = setInRegion(visitedRegionsWithBank, regionIndex, x, y, plane);
			// A banked tile only dominates the equivalent unbanked tile when banking is
			// free: with a positive bank visit cost the banked arrival paid the penalty, so
			// a cheaper unbanked arrival must still be allowed to reach this tile.
			if (bankVisitCost <= 0)
			{
				setInRegion(visitedRegionsWithoutBank, regionIndex, x, y, plane);
			}
			return unique;
		}

		return setInRegion(visitedRegionsWithoutBank, regionIndex, x, y, plane);
	}

	private boolean setInRegion(VisitedRegion[] visitedRegions, int regionIndex, int x, int y, int plane)
	{
		VisitedRegion region = visitedRegions[regionIndex];
		if (region == null)
		{
			region = new VisitedRegion(map.getRegionPlaneCounts(regionIndex));
			visitedRegions[regionIndex] = region;
		}
		return region.set(x % REGION_SIZE, y % REGION_SIZE, plane);
	}

	public void clear()
	{
		for (int i = 0; i < visitedRegionsWithoutBank.length; ++i)
		{
			visitedRegionsWithoutBank[i] = null;
			visitedRegionsWithBank[i] = null;
		}
		for (int i = 0; i < abstractVisitedWithoutBank.length; i++)
		{
			abstractVisitedWithoutBank[i] = false;
			abstractVisitedWithBank[i] = false;
		}
	}

	private int getRegionIndex(int regionX, int regionY)
	{
		return (regionX - regionExtents.minX) + (regionY - regionExtents.minY) * widthInclusive;
	}

	private static class VisitedRegion
	{
		// This assumes a row is at most 64 tiles and fits in a long
		private final long[] planes;
		private final byte planeCount;

		VisitedRegion(byte planeCount)
		{
			this.planeCount = planeCount;
			this.planes = new long[planeCount * REGION_SIZE];
		}

		// Sets a tile as visited in the tile bitset
		// Returns true if the tile is unique and hasn't been seen before or false if it
		// was seen before
		public boolean set(int x, int y, int plane)
		{
			if (plane >= planeCount)
			{
				// Plane is out of bounds; report that it has been visited to avoid further
				// exploration
				return false;
			}
			final int index = y + plane * REGION_SIZE;
			boolean unique = (planes[index] & (1L << x)) == 0;
			planes[index] |= 1L << x;
			return unique;
		}

		public boolean get(int x, int y, int plane)
		{
			if (plane >= planeCount)
			{
				// This check is necessary since we check visited tiles before checking the
				// collision map, e.g. the node
				// at (2816, 3455, 1) will check its neighbour to the north which is in a new
				// region with no plane = 1
				return true;
			}
			return (planes[y + plane * REGION_SIZE] & (1L << x)) != 0;
		}
	}
}
