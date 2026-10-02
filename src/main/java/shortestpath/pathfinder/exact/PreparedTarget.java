package shortestpath.pathfinder.exact;

import java.util.function.BooleanSupplier;
import shortestpath.pathfinder.CollisionMap;

/**
 * Target-lifetime preparation: the target overlay, its reverse labels and the heuristic built
 * from them. None of it depends on the start, so one prepared target serves any number of
 * forward searches for the same account snapshot. With several target tiles, a search ends at
 * whichever target is cheapest to reach.
 */
public final class PreparedTarget
{
	private final TargetOverlay overlay;
	private final PreparedHeuristic heuristic;
	private final long reverseSearchNanos;
	private final long heuristicPrepareNanos;

	private PreparedTarget(TargetOverlay overlay, PreparedHeuristic heuristic, long reverseSearchNanos,
		long heuristicPrepareNanos)
	{
		this.overlay = overlay;
		this.heuristic = heuristic;
		this.reverseSearchNanos = reverseSearchNanos;
		this.heuristicPrepareNanos = heuristicPrepareNanos;
	}

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

	public ExactForwardSearch.Result search(int start, BooleanSupplier cancelled, double heuristicWeight)
	{
		return ExactForwardSearch.search(overlay, heuristic, start, cancelled, heuristicWeight);
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
