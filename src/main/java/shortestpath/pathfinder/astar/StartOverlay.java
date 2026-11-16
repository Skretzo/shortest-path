package shortestpath.pathfinder.astar;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import shortestpath.PrimitiveIntList;
import shortestpath.pathfinder.CollisionMap;

/**
 * The start-side counterpart of {@link TargetOverlay}: the land a query adds to the static search
 * tiles around its start. Those tiles cover only land reachable from Lumbridge, which stands in for
 * the land reachable from the player. A start elsewhere (a quest instance, a random event, a
 * dungeon no known transport leads into) brings its own walkable pocket, walked from the collision
 * map, so routable land is Lumbridge's plus the start's.
 *
 * <p>Pocket tiles join the search as extra tiles with a zero heuristic, which stays admissible, and
 * count as reachable when goals are resolved; targets in other left-out land still fail before the
 * search. A start on or beside a search tile adds nothing.
 */
public final class StartOverlay
{
	/** Larger pockets are dropped: a search from them only gets the global teleports. */
	static final int MAX_TILES = 1 << 16;

	private final RoutingStatic stat;
	private final int[] tiles;

	private StartOverlay(RoutingStatic stat, int[] tiles)
	{
		this.stat = stat;
		this.tiles = tiles;
	}

	/** An overlay that adds nothing, for searches whose start is known to be on the search tiles. */
	public static StartOverlay none(RoutingStatic stat)
	{
		return new StartOverlay(stat, new int[0]);
	}

	public static StartOverlay of(RoutingStatic stat, CollisionMap collision, int start)
	{
		if (stat.searchIndex(start) >= 0 || stat.attachments(start, collision).length != 0) return none(stat);
		Set<Integer> seen = new HashSet<>();
		PrimitiveIntList pocket = new PrimitiveIntList();
		seen.add(start);
		pocket.add(start);
		for (int i = 0; i < pocket.size(); i++)
		{
			for (int next : collision.ordinaryWalkingNeighbors(pocket.get(i)))
			{
				if (stat.searchIndex(next) >= 0 || !seen.add(next)) continue;
				if (pocket.size() == MAX_TILES) return none(stat);
				pocket.add(next);
			}
		}
		// Flipping the sign bit makes the signed sort an unsigned one.
		int[] sorted = new int[pocket.size()];
		for (int i = 0; i < sorted.length; i++) sorted[i] = pocket.get(i) ^ Integer.MIN_VALUE;
		Arrays.sort(sorted);
		for (int i = 0; i < sorted.length; i++) sorted[i] ^= Integer.MIN_VALUE;
		return new StartOverlay(stat, sorted);
	}

	/** Whether some route from this start can walk on {@code tile}: a search tile or a pocket tile. */
	public boolean reachable(int tile)
	{
		return stat.searchIndex(tile) >= 0 || contains(tile);
	}

	boolean belongsTo(RoutingStatic routingStatic)
	{
		return stat == routingStatic;
	}

	/** Whether {@code tile} is one of the pocket tiles this overlay adds. */
	public boolean contains(int tile)
	{
		return IntArrays.binarySearch(tiles, tile) >= 0;
	}

	/** The pocket tiles, in unsigned order; empty when the start needs none. */
	int[] tiles()
	{
		return tiles;
	}

	/** The number of pocket tiles this overlay adds. */
	public int size()
	{
		return tiles.length;
	}
}
