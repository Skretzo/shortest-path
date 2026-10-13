package shortestpath.pathfinder;

import java.util.List;

/**
 * The plugin-facing handle for an active route search.
 */
public interface ActiveSearch extends Runnable
{
	void cancel();

	int getStart();

	/** Where the route may end. */
	Targets getTargets();

	List<PathStep> getPath();

	boolean isDone();

	PathfinderResult getResult();

	PathfinderStats getStats();
}
