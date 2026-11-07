package shortestpath.pathfinder.sailing;

/** Whether a boat may move from one packed tile to another: the walking search's positional gates, at sea. */
@FunctionalInterface
public interface SeaGate
{
	SeaGate NONE = (from, to) -> true;

	boolean allowed(int from, int to);
}
