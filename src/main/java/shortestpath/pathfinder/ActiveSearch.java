package shortestpath.pathfinder;

import java.util.List;
import java.util.Set;

/**
 * The plugin-facing handle for an active route search.
 */
public interface ActiveSearch extends Runnable
{
	void cancel();

	int getStart();

	Set<Integer> getTargets();

	List<PathStep> getPath();

	boolean isDone();

	PathfinderResult getResult();

	PathfinderStats getStats();
}
