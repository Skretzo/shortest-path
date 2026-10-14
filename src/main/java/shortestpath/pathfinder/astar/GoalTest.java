package shortestpath.pathfinder.astar;

import shortestpath.pathfinder.BankVisitState;

/**
 * Whether reaching a target tile in a bank layer finishes a search. The prepared target and its
 * heuristic count every target tile in both layers; a search may accept fewer of those states, for
 * a target whose items are only held after a bank visit. Accepting fewer states only removes ways
 * to finish, so the prepared heuristic still never over-estimates and the target can be reused.
 */
@FunctionalInterface
public interface GoalTest
{
	boolean accepts(int tile, BankVisitState banked);
}
