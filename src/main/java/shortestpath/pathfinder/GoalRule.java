package shortestpath.pathfinder;

import java.util.Set;

/**
 * How a search turns a requested target into the tiles that end it. {@link TargetGoals} applies a
 * rule to every target; each search chooses the rule it resolves its targets with.
 */
public interface GoalRule
{
	/** Whether the search can end on the target's own tile. */
	boolean endsOnItself(int target);

	/**
	 * The tiles that stand in for a target the search cannot end on, or an empty set when there are
	 * none.
	 */
	Set<Integer> fallbackTiles(int target);
}
