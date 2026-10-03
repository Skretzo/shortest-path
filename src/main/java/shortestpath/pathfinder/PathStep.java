package shortestpath.pathfinder;

import lombok.Getter;
import shortestpath.transport.Transport;

@Getter
public final class PathStep
{
	private final int packedPosition;
	private final boolean bankVisited;
	/**
	 * The transport that produced the edge leading to this step, or null when the step
	 * was reached by walking (or was built without transport identity). Carrying the
	 * chosen transport on the step removes the ambiguity of reconstructing "which
	 * transport was used" from origin+destination, which cannot distinguish transports
	 * that share a destination tile.
	 */
	private final Transport transport;

	public PathStep(int packedPosition, boolean bankVisited)
	{
		this(packedPosition, bankVisited, null);
	}

	public PathStep(int packedPosition, boolean bankVisited, Transport transport)
	{
		this.packedPosition = packedPosition;
		this.bankVisited = bankVisited;
		this.transport = transport;
	}
}
