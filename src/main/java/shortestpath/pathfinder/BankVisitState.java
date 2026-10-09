package shortestpath.pathfinder;

/**
 * Whether a path has visited a bank yet — the traversal-state dimension the
 * search tracks alongside position. {@link #CARRIED} is the initial state: the
 * route may still take a bank-visit transition and carries only inventory
 * items. {@link #BANKED} is the state after that transition: bank contents
 * have joined the usable item pool.
 */
public enum BankVisitState
{
	CARRIED,
	BANKED
}
