package shortestpath.pathfinder;

import java.util.List;
import lombok.Getter;

@Getter
public class PathfinderResult
{
	public static final int NO_PATH_COST = -1;

	private final int start;
	private final int target;
	private final boolean reached;
	private final List<PathStep> pathSteps;
	private final int closestReachedPoint;
	private final int pathCost;
	private final int nodesChecked;
	private final int transportsChecked;
	private final long elapsedNanos;
	private final PathTerminationReason terminationReason;

	public PathfinderResult(
		int start,
		int target,
		boolean reached,
		List<PathStep> pathSteps,
		int closestReachedPoint,
		int nodesChecked,
		int transportsChecked,
		long elapsedNanos,
		PathTerminationReason terminationReason)
	{
		this(
			start,
			target,
			reached,
			pathSteps,
			closestReachedPoint,
			NO_PATH_COST,
			nodesChecked,
			transportsChecked,
			elapsedNanos,
			terminationReason
		);
	}

	public PathfinderResult(
		int start,
		int target,
		boolean reached,
		List<PathStep> pathSteps,
		int closestReachedPoint,
		int pathCost,
		int nodesChecked,
		int transportsChecked,
		long elapsedNanos,
		PathTerminationReason terminationReason)
	{
		this.start = start;
		this.target = target;
		this.reached = reached;
		this.pathSteps = pathSteps;
		this.closestReachedPoint = closestReachedPoint;
		this.pathCost = pathCost;
		this.nodesChecked = nodesChecked;
		this.transportsChecked = transportsChecked;
		this.elapsedNanos = elapsedNanos;
		this.terminationReason = terminationReason;
	}
}
