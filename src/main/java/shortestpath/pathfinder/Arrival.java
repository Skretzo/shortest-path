package shortestpath.pathfinder;

import java.util.List;
import shortestpath.requirement.model.ItemRequirement;
import lombok.Getter;

/**
 * The target a finished route reaches, and whether the player will hold its items there. Immutable.
 */
public final class Arrival
{
	@Getter private final Target target;
	@Getter private final TargetStatus status;
	@Getter private final List<ItemRequirement> missingItems;

	public Arrival(Target target, TargetStatus status, List<ItemRequirement> missingItems)
	{
		this.target = target;
		this.status = status;
		this.missingItems = List.copyOf(missingItems);
	}



	/**
	 * How the player comes to hold the target's items: {@link TargetStatus#CARRIED} for a target
	 * that needs none.
	 */


	/**
	 * For an {@link TargetStatus#UNKNOWN} target, the item groups found neither carried nor in the
	 * bank; empty otherwise.
	 */


	/** Whether the route goes through a bank to pick up the target's items. */
	public boolean picksUpItemsFromBank()
	{
		return status == TargetStatus.BANK;
	}
}
