package shortestpath.pathfinder;

import java.util.List;

/**
 * The plugin-facing handle for an active route search.
 */
public interface ActiveSearch extends Runnable
{
	void cancel();

	int getStart();

	/** Where the route may end, with the items each target needs on arrival. */
	Targets getTargets();

	List<PathStep> getPath();

	boolean isDone();

	/**
	 * Whether this search sails a boat's headings rather than walking (see
	 * {@link Pathfinder#isSailing}); the render side reads it for the sailing
	 * draw and reached verdict instead of casting to {@link Pathfinder}.
	 */
	boolean isSailing();

	PathfinderResult getResult();

	PathfinderStats getStats();
}
