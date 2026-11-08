package shortestpath.pathfinder;

import java.util.List;
import lombok.Getter;
import shortestpath.transport.Transport;

@Getter
public final class PathStep
{
	private final int packedPosition;
	private final BankVisitState bankVisitState;
	/**
	 * The transport that produced the edge leading to this step, or null when the step
	 * was reached by walking (or was built without transport identity). Carrying the
	 * chosen transport on the step removes the ambiguity of reconstructing "which
	 * transport was used" from origin+destination, which cannot distinguish transports
	 * that share a destination tile.
	 */
	private final Transport transport;
	/** How the step was reached from the previous one; see {@link Traversal}. */
	private final Traversal traversal;

	public PathStep(int packedPosition, BankVisitState bankVisitState, Transport transport, Traversal traversal)
	{
		if (traversal == null) throw new NullPointerException("traversal");
		this.packedPosition = packedPosition;
		this.bankVisitState = bankVisitState;
		this.transport = transport;
		this.traversal = traversal;
	}

	/** A walking step, or a transport step when {@code transport} is set. */
	public PathStep(int packedPosition, BankVisitState bankVisitState, Transport transport)
	{
		this(packedPosition, bankVisitState, transport, transport == null ? Traversal.WALK : Traversal.TRANSPORT);
	}

	public PathStep(int packedPosition, BankVisitState bankVisitState)
	{
		this(packedPosition, bankVisitState, null);
	}

	/** Whether this step is a point of a sailing leg: see {@link Traversal#SAILING}. */
	public boolean isSailing()
	{
		return traversal == Traversal.SAILING;
	}

	/** Whether the boat sails from step {@code index - 1} of {@code path} to step {@code index}: both are points of a sailing leg. */
	public static boolean sailedInto(List<PathStep> path, int index)
	{
		return index > 0 && index < path.size() && path.get(index - 1).isSailing() && path.get(index).isSailing();
	}

	/** A step of a sailing leg: see {@link Traversal#SAILING}. */
	public static PathStep sailing(int packedPosition, BankVisitState bankVisitState)
	{
		return sailing(packedPosition, bankVisitState, null);
	}

	/** A step of a sailing leg that shows {@code boarding} where the boat is boarded for it. */
	public static PathStep sailing(int packedPosition, BankVisitState bankVisitState, Transport boarding)
	{
		return new PathStep(packedPosition, bankVisitState, boarding, Traversal.SAILING);
	}
}
