package shortestpath.pathfinder.astar;

import java.util.function.BooleanSupplier;
import shortestpath.pathfinder.CollisionMap;
import lombok.AllArgsConstructor;

/**
 * Target-lifetime preparation: the target overlay, its reverse labels and the heuristic built
 * from them. None of it depends on the start, so one prepared target serves any number of
 * forward searches for the same account snapshot. With several target tiles, a search ends at
 * whichever target is cheapest to reach.
 */
@AllArgsConstructor
public final class PreparedTarget
{
	private final TargetOverlay overlay;
	private final PreparedHeuristic heuristic;
	private final long reverseSearchNanos;
	private final long heuristicPrepareNanos;



	public static PreparedTarget prepare(SiteGraph graph, CollisionMap collision, int packed)
	{
		return prepare(graph, collision, new int[] {packed});
	}

	public static PreparedTarget prepare(SiteGraph graph, CollisionMap collision, int[] packedTargets)
	{
		TargetOverlay overlay = new TargetOverlay(graph, collision, packedTargets);
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

	public TargetOverlay overlay()
	{
		return overlay;
	}
	public PreparedHeuristic heuristic()
	{
		return heuristic;
	}
	/** The target tiles, in unsigned order. */
	public int[] packedTargets()
	{
		return overlay.packedTargets();
	}
	/** Time the reverse relaxed search took when this target was prepared. */
	public long reverseSearchNanos()
	{
		return reverseSearchNanos;
	}
	/** Time the heuristic seed/generator tables took when this target was prepared. */
	public long heuristicPrepareNanos()
	{
		return heuristicPrepareNanos;
	}
}
