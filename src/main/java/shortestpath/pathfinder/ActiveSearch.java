package shortestpath.pathfinder;

import java.util.List;
import java.util.Map;

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
	 * The engine's display name for the debug overlay — the render side reads
	 * it instead of casting to a concrete backend.
	 */
	String backendName();

	/**
	 * Whether this search sails a boat's headings rather than walking (see
	 * {@code Pathfinder#isSailing}); the render side reads it for the sailing
	 * draw and reached verdict instead of casting to {@code Pathfinder}.
	 */
	boolean isSailing();

	/**
	 * Whether {@link #getPath()} is still the previous route shown while the
	 * new route computes ({@link AStarPathfinder#showUntilDone}); backends
	 * without a provisional-path mode report false.
	 */
	default boolean isShowingProvisionalPath()
	{
		return false;
	}

	/**
	 * The path indices the user clicked to build this route, when the backend
	 * records them; empty for backends that do not.
	 */
	default List<Integer> getClickPoints()
	{
		return List.of();
	}

	/**
	 * Backend-specific diagnostic lines for the debug overlay, as ordered
	 * label to display text; empty for backends with nothing to report.
	 */
	default Map<String, String> debugStats()
	{
		return Map.of();
	}

	PathfinderResult getResult();

	PathfinderStats getStats();
}
