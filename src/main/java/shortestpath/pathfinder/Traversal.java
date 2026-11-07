package shortestpath.pathfinder;

/**
 * How a route step was reached from the step before it. Lets later passes and the overlays treat each part of a
 * mixed route by what it is, rather than guessing from how far apart its steps are: a sailing move can be a single
 * tile, like a walking step.
 */
public enum Traversal
{
	/** A walking step, or the route's first step. */
	WALK,
	/** A transport or teleport; the step carries it. */
	TRANSPORT,
	/** The boat's tile at the end of a sailing move, or on boarding; or the land tile on disembarking. */
	SAILING
}
