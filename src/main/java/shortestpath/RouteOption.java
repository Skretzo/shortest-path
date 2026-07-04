package shortestpath;

import java.util.List;
import lombok.Getter;
import shortestpath.pathfinder.PathStep;

/**
 * One alternative route to the target, produced by {@link AlternativeRoutesService}. Each route is a
 * shortest path under the constraint that it uses a different set of teleport/transport methods than
 * the other routes in the same result list.
 */
@Getter
public final class RouteOption
{
	/**
	 * The full tile path, as returned by the pathfinder.
	 */
	private final List<PathStep> path;
	/**
	 * The teleport/transport methods used along the path, in travel order. Empty for a walk-only route.
	 */
	private final List<TeleportMethod> methods;
	/**
	 * Blended search cost (walk distance + transport ticks/penalties); lower is shorter.
	 */
	private final int totalCost;
	/**
	 * Whether the path actually reaches the exact target. When false it ends at the closest reachable
	 * tile (mirrors Shortest Path's own behaviour for unreachable targets, e.g. an NPC/object tile).
	 */
	private final boolean reached;
	/**
	 * Whether one of this route's methods requires an item that must first be withdrawn from the bank
	 * (the path includes a walk to a bank before that method is used).
	 */
	private final boolean viaBank;

	public RouteOption(List<PathStep> path, List<TeleportMethod> methods, int totalCost, boolean reached,
		boolean viaBank)
	{
		this.path = path;
		this.methods = methods;
		this.totalCost = totalCost;
		this.reached = reached;
		this.viaBank = viaBank;
	}

	/**
	 * The method the route leans on first; the one the panel offers to exclude. Null for walk-only.
	 */
	public TeleportMethod primaryMethod()
	{
		return methods.isEmpty() ? null : methods.get(0);
	}

	public boolean isWalkOnly()
	{
		return methods.isEmpty();
	}
}
