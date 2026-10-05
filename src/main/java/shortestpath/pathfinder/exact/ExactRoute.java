package shortestpath.pathfinder.exact;

import java.util.List;
import shortestpath.pathfinder.PathStep;

/**
 * A reconstructed exact route: its steps, plus what the search knew about each of them that the
 * steps alone do not say. A step reached through a global-teleport hub records which hub, since
 * the hub is not a tile and does not appear among the steps; every step records the cost the
 * search reached it at.
 */
public final class ExactRoute
{
	/** The step was reached from the previous step directly: a walk, a bank visit or a transport. */
	public static final byte FROM_STEP = 0;
	/** The first hub arrival; the step is the destination of a teleport cast with capability {@code arrival - 1}. */
	private static final byte FROM_HUB = 1;

	private final List<PathStep> steps;
	private final byte[] arrivals;
	private final int[] costs;

	ExactRoute(List<PathStep> steps, byte[] arrivals, int[] costs)
	{
		if (arrivals.length != steps.size() || costs.length != steps.size())
			throw new IllegalArgumentException("route metadata does not match its steps");
		this.steps = List.copyOf(steps);
		this.arrivals = arrivals;
		this.costs = costs;
	}

	/** A route of plain steps with no search metadata: every step is reached from the previous one. */
	public static ExactRoute of(List<PathStep> steps, int[] costs)
	{
		return new ExactRoute(steps, new byte[steps.size()], costs.clone());
	}

	public List<PathStep> steps()
	{
		return steps;
	}

	public int size()
	{
		return steps.size();
	}

	/** The arrival of a step reached through the hub casting the globals {@code capability} allows. */
	public static byte fromHub(TeleportCapability capability)
	{
		return (byte) (FROM_HUB + capability.ordinal());
	}

	/** The capability of the hub an arrival came through; {@code arrival} must not be {@link #FROM_STEP}. */
	public static TeleportCapability hubCapability(byte arrival)
	{
		return TeleportCapability.values()[arrival - FROM_HUB];
	}

	/** How step {@code index} was reached: {@link #FROM_STEP} or {@link #fromHub one of the hubs}. */
	public byte arrival(int index)
	{
		return arrivals[index];
	}

	/** The cost at which the search reached step {@code index}. */
	public int cost(int index)
	{
		return costs[index];
	}
}
