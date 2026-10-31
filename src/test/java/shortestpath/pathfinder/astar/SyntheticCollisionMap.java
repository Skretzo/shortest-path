package shortestpath.pathfinder.astar;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;

/**
 * A small, entirely in-memory {@link CollisionMap} for {@link RoutingStaticBuilder} unit tests.
 * Every world-scanning entry point {@link RoutingStaticBuilder} uses is overridable on
 * {@link CollisionMap} for exactly this reason: {@link #regionExtent()},
 * {@link #getRegionPlaneCounts(int)}, {@link #isBlocked(int, int, int)},
 * {@link #ordinaryWalkingMask(int)} and {@link #ordinaryWalkingNeighbors(int)}. This class
 * therefore never touches a real {@link SplitFlagMap} or its shared static region-extent state.
 *
 * <p>Test worlds declare walkable tiles and an explicit, small adjacency list per tile (the test
 * author is responsible for keeping it symmetric, exactly like real collision data: if {@code b}
 * is a declared neighbour of {@code a}, {@code a} must also be a declared neighbour of {@code b}).
 * Only ordinary 8-directional adjacency is modelled; no transport-blocked-origin behaviour is
 * needed since {@link RoutingStaticBuilder} never uses that path.
 */
class SyntheticCollisionMap extends CollisionMap
{
	// Matches CollisionMap.ordinaryWalkingNeighbors's own bit layout exactly (0-N..7-NW).
	private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
	private static final int[] DY = {1, 1, 0, -1, -1, -1, 0, 1};

	private final Set<Integer> walkable;
	private final Map<Integer, int[]> neighbors;
	private final SplitFlagMap.RegionExtent extent;
	private final int planeCount;

	SyntheticCollisionMap(Set<Integer> walkableTiles, Map<Integer, int[]> neighborOverrides, int planeCount)
	{
		super(null);
		this.walkable = walkableTiles;
		this.neighbors = new HashMap<>(neighborOverrides);
		this.extent = new SplitFlagMap.RegionExtent(0, 0, 0, 0);
		this.planeCount = planeCount;
	}

	@Override
	public SplitFlagMap.RegionExtent regionExtent()
	{
		return extent;
	}

	@Override
	public byte getRegionPlaneCounts(int regionIndex)
	{
		return regionIndex == 0 ? (byte) planeCount : 0;
	}

	@Override
	public boolean isBlocked(int x, int y, int z)
	{
		return !walkable.contains(WorldPointUtil.packWorldPoint(x, y, z));
	}

	@Override
	public byte ordinaryWalkingMask(int packedPoint)
	{
		if (!walkable.contains(packedPoint))
		{
			return 0;
		}
		int x = WorldPointUtil.unpackWorldX(packedPoint);
		int y = WorldPointUtil.unpackWorldY(packedPoint);
		int z = WorldPointUtil.unpackWorldPlane(packedPoint);
		int[] declared = neighbors.getOrDefault(packedPoint, new int[0]);
		int mask = 0;
		for (int bit = 0; bit < 8; bit++)
		{
			int candidate = WorldPointUtil.packWorldPoint(x + DX[bit], y + DY[bit], z);
			if (contains(declared, candidate))
			{
				mask |= 1 << bit;
			}
		}
		return (byte) mask;
	}

	@Override
	public int[] ordinaryWalkingNeighbors(int packedPoint)
	{
		return neighbors.getOrDefault(packedPoint, new int[0]).clone();
	}

	private static boolean contains(int[] values, int target)
	{
		for (int value : values)
		{
			if (value == target)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Gives every walkable tile without a declared neighbour one open edge toward {@code (dx, dy)}, at
	 * a tile that is not walkable. A real collision map never has a walkable tile without a walking
	 * edge (a tile with no open cardinal flag is blocked), and the routing loader rejects a zero
	 * walking mask as a stale layer; some synthetic worlds want single tiles, and the stub keeps their
	 * masks non-zero while joining nothing. The stub direction must not point at a search tile.
	 */
	static Map<Integer, int[]> withStubEdges(Set<Integer> walkableTiles, Map<Integer, int[]> adjacency, int dx, int dy)
	{
		Map<Integer, int[]> result = new HashMap<>(adjacency);
		for (int tile : walkableTiles)
		{
			if (result.containsKey(tile)) continue;
			result.put(tile, new int[] {WorldPointUtil.packWorldPoint(WorldPointUtil.unpackWorldX(tile) + dx,
				WorldPointUtil.unpackWorldY(tile) + dy, WorldPointUtil.unpackWorldPlane(tile))});
		}
		return result;
	}

	/** Builds symmetric adjacency from an explicit list of open, undirected tile-pair edges. */
	static Map<Integer, int[]> symmetricAdjacency(int[][] edges)
	{
		Map<Integer, java.util.List<Integer>> adjacency = new HashMap<>();
		for (int[] edge : edges)
		{
			adjacency.computeIfAbsent(edge[0], k -> new java.util.ArrayList<>()).add(edge[1]);
			adjacency.computeIfAbsent(edge[1], k -> new java.util.ArrayList<>()).add(edge[0]);
		}
		Map<Integer, int[]> result = new HashMap<>();
		for (Map.Entry<Integer, java.util.List<Integer>> entry : adjacency.entrySet())
		{
			result.put(entry.getKey(), entry.getValue().stream().mapToInt(Integer::intValue).toArray());
		}
		return result;
	}
}
