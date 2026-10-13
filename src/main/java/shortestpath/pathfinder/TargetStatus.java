package shortestpath.pathfinder;

/**
 * Whether the player holds a target's required items, checked once when the search starts.
 */
public enum TargetStatus
{
	/** The inventory and worn items cover the requirement: the target counts in either bank layer. */
	CARRIED,
	/**
	 * The requirement is only covered once the bank's contents are added: the target counts only
	 * after the path has visited a bank.
	 */
	BANK,
	/**
	 * Neither the carried items nor the bank as last seen cover the requirement. A bank not opened
	 * this session counts as empty, as it does for item-gated transports. The target is only used
	 * when no other target can be completed.
	 */
	UNKNOWN
}
