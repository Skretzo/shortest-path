package shortestpath.pathfinder;

import java.util.List;
import shortestpath.requirement.model.ItemRequirement;

/**
 * The target a finished route reaches, and whether the player will hold its items there. Immutable.
 */
public final class Arrival
{
	private final Target target;
	private final TargetStatus status;
	private final List<ItemRequirement> missingItems;

	public Arrival(Target target, TargetStatus status, List<ItemRequirement> missingItems)
	{
		this.target = target;
		this.status = status;
		this.missingItems = List.copyOf(missingItems);
	}

	public Target getTarget()
	{
		return target;
	}

	/**
	 * How the player comes to hold the target's items: {@link TargetStatus#CARRIED} for a target
	 * that needs none.
	 */
	public TargetStatus getStatus()
	{
		return status;
	}

	/**
	 * For an {@link TargetStatus#UNKNOWN} target, the item groups found neither carried nor in the
	 * bank; empty otherwise.
	 */
	public List<ItemRequirement> getMissingItems()
	{
		return missingItems;
	}

	/** Whether the route goes through a bank to pick up the target's items. */
	public boolean picksUpItemsFromBank()
	{
		return status == TargetStatus.BANK;
	}
}
