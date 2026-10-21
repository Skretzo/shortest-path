package shortestpath.pathfinder.astar;

import java.util.function.BooleanSupplier;
import shortestpath.pathfinder.CollisionMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Target-lifetime preparation: the target overlay, its reverse labels and the heuristic built
 * from them. None of it depends on the start, so one prepared target serves any number of
 * forward searches for the same account snapshot. With several target tiles, a search ends at
 * whichever target is cheapest to reach.
 */
@AllArgsConstructor
public final class PreparedTarget
{
	@Getter @Accessors(fluent = true) private final TargetOverlay overlay;
	@Getter @Accessors(fluent = true) private final PreparedHeuristic heuristic;
	@Getter @Accessors(fluent = true) private final long reverseSearchNanos;
	@Getter @Accessors(fluent = true) private final long heuristicPrepareNanos;



	public static PreparedTarget prepare(SiteGraph graph, CollisionMap collision, int packed)
	{
		return prepare(graph, collision, new int[] {packed});
	}

	public static PreparedTarget prepare(SiteGraph graph, CollisionMap collision, int[] packedTargets)
	{
		return prepare(new TargetOverlay(graph, collision, packedTargets));
	}

	public static PreparedTarget prepare(SiteGraph graph, CollisionMap collision, int[] packedTargets, int[] terminalLayers)
	{
		return prepare(new TargetOverlay(graph, collision, packedTargets, terminalLayers));
	}

	static PreparedTarget prepare(SiteGraph graph, CollisionMap collision, long[] targets)
	{
		return prepare(new TargetOverlay(graph, collision, targets));
	}

	private static PreparedTarget prepare(TargetOverlay overlay)
	{
		long phaseStarted = System.nanoTime();
		ReverseLabels reverse = ReverseLabels.compute(overlay);
		long reverseSearchNanos = System.nanoTime() - phaseStarted;
		phaseStarted = System.nanoTime();
		PreparedHeuristic heuristic = PreparedHeuristic.prepare(overlay, reverse);
		long heuristicPrepareNanos = System.nanoTime() - phaseStarted;
		return new PreparedTarget(overlay, heuristic, reverseSearchNanos, heuristicPrepareNanos);
	}

	public AStarForwardSearch.Result search(int start, BooleanSupplier cancelled, double heuristicWeight)
	{
		return search(start, cancelled, heuristicWeight, SearchRestrictions.none(), AStarForwardSearch.NO_PROGRESS);
	}

	/**
	 * {@code restrictions} mirrors the positional gates legacy applies per edge; they only remove
	 * edges, so the prepared heuristic stays admissible and this target may be reused.
	 * {@code progressed} runs each time the closest-reached-tile record improves.
	 */
	public AStarForwardSearch.Result search(int start, BooleanSupplier cancelled, double heuristicWeight,
		SearchRestrictions restrictions, Runnable progressed)
	{
		return AStarForwardSearch.search(overlay, heuristic, start, cancelled, true, heuristicWeight,
			restrictions, progressed);
	}

	/**
	 * As {@link #search(int, BooleanSupplier, double, SearchRestrictions, Runnable)}, finishing only
	 * at target states {@code goal} accepts; see {@link GoalTest}.
	 */
	public AStarForwardSearch.Result search(int start, BooleanSupplier cancelled, double heuristicWeight,
		SearchRestrictions restrictions, Runnable progressed, GoalTest goal)
	{
		return AStarForwardSearch.search(overlay, heuristic, start, cancelled, true, heuristicWeight,
			restrictions, progressed, goal);
	}



	/** The target tiles, in unsigned order. */
	public int[] packedTargets()
	{
		return overlay.packedTargets();
	}
	/** Time the reverse relaxed search took when this target was prepared. */

	/** Time the heuristic seed/generator tables took when this target was prepared. */

}
