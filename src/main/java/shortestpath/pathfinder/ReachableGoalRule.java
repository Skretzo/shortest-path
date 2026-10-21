package shortestpath.pathfinder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import shortestpath.WorldPointUtil;

/**
 * The goal rule for a search that knows which tiles some route can reach, so that no goal lies in
 * disconnected space: a sealed pocket, a staff area behind a counter, open sea.
 *
 * <ol>
 *   <li>A walkable, reachable target is its own goal.</li>
 *   <li>Any other target looks for candidates around it, within the unreachable target distance:
 *       the reachable tiles and the tiles a transport lands on, never the player's own tile.</li>
 *   <li>When there are none, the target cannot be reached and the search need not run.</li>
 *   <li>Otherwise its goals are the candidates of the innermost Chebyshev ring that has any. On
 *       the ring touching the target, a reachable tile counts only in the first non-empty tier of:
 *       cardinal tiles that share no wall edge with the target, cardinal tiles behind one (the
 *       target is itself a wall or rock face), diagonal tiles likewise. Transport landings always
 *       count. The search ends at whichever goal is cheapest to reach.</li>
 * </ol>
 */
final class ReachableGoalRule implements GoalRule
{
	private final CollisionMap map;
	private final IntPredicate transportDestination;
	private final IntPredicate reachable;
	private final int start;
	private final int radius;

	/** {@code reachable} is true for the tiles some route can get to. */
	ReachableGoalRule(CollisionMap map, IntPredicate transportDestination, IntPredicate reachable, int start,
		int unreachableDistance)
	{
		this.map = map;
		this.transportDestination = transportDestination;
		this.reachable = reachable;
		this.start = start;
		this.radius = TargetGoals.expansionRadius(unreachableDistance);
	}

	// Rule 1.
	@Override
	public boolean endsOnItself(int target)
	{
		return !TargetGoals.isBlocked(map, target) && reachable.test(target);
	}

	// Rules 2 and 4; an empty set is rule 3.
	@Override
	public Set<Integer> fallbackTiles(int target)
	{
		for (int ring = 1; ring <= radius; ring++)
		{
			final Set<Integer> reachableTiles = new HashSet<>();
			final Set<Integer> landings = new HashSet<>();
			final int[] offsets = TargetGoals.ringOffsets(ring);
			for (int i = 0; i < offsets.length; i += 2)
			{
				final int tile = WorldPointUtil.packWorldPoint(WorldPointUtil.unpackWorldX(target) + offsets[i],
					WorldPointUtil.unpackWorldY(target) + offsets[i + 1], WorldPointUtil.unpackWorldPlane(target));
				if (tile == start)
				{
					continue;
				}
				if (reachable.test(tile))
				{
					reachableTiles.add(tile);
				}
				if (transportDestination.test(tile))
				{
					landings.add(tile);
				}
			}
			final Set<Integer> candidates = ring == 1 ? interactionTier(target, reachableTiles) : reachableTiles;
			candidates.addAll(landings);
			if (!candidates.isEmpty())
			{
				return candidates;
			}
		}
		return Set.of();
	}

	// The first non-empty tier of the tiles touching the target, in the order
	// they can be interacted from: cardinal before diagonal, and open before
	// behind a wall edge.
	private Set<Integer> interactionTier(int target, Set<Integer> adjacent)
	{
		for (boolean diagonal : new boolean[] {false, true})
		{
			for (boolean walled : new boolean[] {false, true})
			{
				final Set<Integer> tier = new HashSet<>();
				for (int tile : adjacent)
				{
					if (isDiagonal(target, tile) == diagonal && isWalled(target, tile) == walled)
					{
						tier.add(tile);
					}
				}
				if (!tier.isEmpty())
				{
					return tier;
				}
			}
		}
		return new HashSet<>();
	}

	private static boolean isDiagonal(int target, int tile)
	{
		return WorldPointUtil.unpackWorldX(tile) != WorldPointUtil.unpackWorldX(target)
			&& WorldPointUtil.unpackWorldY(tile) != WorldPointUtil.unpackWorldY(target);
	}

	private boolean isWalled(int target, int tile)
	{
		return TargetGoals.wallSeparated(map, WorldPointUtil.unpackWorldX(target), WorldPointUtil.unpackWorldY(target),
			WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile), WorldPointUtil.unpackWorldPlane(target));
	}
}
