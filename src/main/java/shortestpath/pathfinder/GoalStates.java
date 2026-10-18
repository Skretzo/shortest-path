package shortestpath.pathfinder;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The search states that finish a route: a goal tile, in a bank layer where the player holds that
 * target's items.
 *
 * <p>Searches already tell apart a path that has visited a bank from one that hasn't, because
 * banked items unlock transports. Required items use the same split. Each target's
 * {@link TargetStatus} is decided once, from the player's {@link HeldItems}, before the search
 * starts:
 * <ul>
 *     <li>{@link TargetStatus#CARRIED} (or no required items): the target finishes the route in
 *     either layer.</li>
 *     <li>{@link TargetStatus#BANK}: only after a bank visit, so the search itself picks the bank
 *     that makes the whole trip cheapest, and weighs that against the other targets.</li>
 *     <li>{@link TargetStatus#UNKNOWN}: never, while any other target can be completed. When none
 *     can, every target finishes the route in either layer, so the player still sees the way to
 *     the closest one.</li>
 * </ul>
 * A blocked target's fallback tiles from {@link TargetGoals} finish the route in the same layers
 * as the target itself.
 */
public final class GoalStates
{
	private static final int UNBANKED = 1;
	private static final int BANKED = 2;
	private static final int EITHER = UNBANKED | BANKED;

	private final Targets targets;
	private final TargetGoals tiles;
	private final HeldItems heldItems;
	private final Map<Integer, TargetStatus> statuses;
	// The layers each target finishes the route in, as UNBANKED/BANKED bits.
	private final Map<Integer, Integer> layers;
	// Goal tiles that finish the route in either layer, and those only after banking. Disjoint.
	private final Set<Integer> eitherLayerTiles;
	private final Set<Integer> bankedOnlyTiles;
	private final Set<Integer> allTiles;
	private final boolean viable;

	private GoalStates(Targets targets, TargetGoals tiles, HeldItems heldItems, Map<Integer, TargetStatus> statuses,
		Map<Integer, Integer> layers)
	{
		this.targets = targets;
		this.tiles = tiles;
		this.heldItems = heldItems;
		this.statuses = statuses;
		this.layers = layers;
		Set<Integer> eitherLayer = new HashSet<>();
		Set<Integer> bankedOnly = new HashSet<>();
		boolean anyViable = false;
		for (Map.Entry<Integer, Integer> entry : layers.entrySet())
		{
			int target = entry.getKey();
			int targetLayers = entry.getValue();
			if (targetLayers == 0)
			{
				continue;
			}
			anyViable |= tiles.isViable(target);
			Set<Integer> into = targetLayers == EITHER ? eitherLayer : bankedOnly;
			into.add(target);
			into.addAll(tiles.fallbacks(target));
		}
		// A tile that finishes the route in either layer for one target needs no bank for another.
		bankedOnly.removeAll(eitherLayer);
		Set<Integer> all = new HashSet<>(eitherLayer);
		all.addAll(bankedOnly);
		this.eitherLayerTiles = Collections.unmodifiableSet(eitherLayer);
		this.bankedOnlyTiles = Collections.unmodifiableSet(bankedOnly);
		this.allTiles = Collections.unmodifiableSet(all);
		this.viable = anyViable;
	}

	/** Puts {@code targets}, resolved to {@code tiles}, into the layers {@code heldItems} decide. */
	static GoalStates resolve(TargetGoals tiles, Targets targets, HeldItems heldItems)
	{
		Map<Integer, TargetStatus> statuses = new HashMap<>();
		Map<Integer, Integer> layers = new HashMap<>();
		boolean anyCompletable = false;
		for (Target target : targets)
		{
			TargetStatus status = statusOf(target, heldItems);
			int targetLayers = status == TargetStatus.CARRIED ? EITHER : status == TargetStatus.BANK ? BANKED : 0;
			statuses.put(target.getPoint(), status);
			layers.put(target.getPoint(), targetLayers);
			anyCompletable |= targetLayers != 0 && tiles.isViable(target.getPoint());
		}
		if (!anyCompletable)
		{
			// No target can be completed with what the player holds, so route as if none needed
			// items: the closest target is still where the player is going.
			layers.replaceAll((point, targetLayers) -> EITHER);
		}
		return new GoalStates(targets, tiles, heldItems, statuses, layers);
	}

	/**
	 * Whether some target's items are only in the bank, so the route may have to visit one. A
	 * property of the targets and the items held, not of the tiles they resolve to.
	 */
	public static boolean needsBank(Targets targets, HeldItems heldItems)
	{
		for (Target target : targets)
		{
			if (statusOf(target, heldItems) == TargetStatus.BANK)
			{
				return true;
			}
		}
		return false;
	}

	private static TargetStatus statusOf(Target target, HeldItems heldItems)
	{
		return target.hasRequiredItems() ? heldItems.statusOf(target.getRequiredItems()) : TargetStatus.CARRIED;
	}

	/** Whether reaching {@code tile} in the given bank layer finishes the route. */
	public boolean accepts(int tile, BankVisitState banked)
	{
		return eitherLayerTiles.contains(tile) || banked == BankVisitState.BANKED && bankedOnlyTiles.contains(tile);
	}

	/** Whether a path in the given bank layer may finish at or near the target at {@code point}. */
	public boolean acceptsTarget(int point, BankVisitState banked)
	{
		return (layers.getOrDefault(point, 0) & (banked == BankVisitState.BANKED ? BANKED : UNBANKED)) != 0;
	}

	/** Every tile that finishes the route in some layer. */
	public Set<Integer> tiles()
	{
		return allTiles;
	}

	/**
	 * Whether any target that finishes the route could ever be reached; see
	 * {@link TargetGoals#hasViableGoal()}.
	 */
	public boolean hasViableGoal()
	{
		return viable;
	}

	/**
	 * The arrival of a route that ends on {@code tile} in the given layer: the target the tile
	 * belongs to, the tile itself when it is a target, and otherwise the first target it is a
	 * fallback tile for. {@code null} when the tile finishes the route for no target.
	 */
	public Arrival arrival(int tile, BankVisitState banked)
	{
		Integer reached = acceptsTarget(tile, banked) ? Integer.valueOf(tile) : null;
		for (int point : targets.points())
		{
			if (reached == null && acceptsTarget(point, banked) && tiles.fallbacks(point).contains(tile))
			{
				reached = point;
			}
		}
		if (reached == null)
		{
			return null;
		}
		Target target = targets.get(reached);
		TargetStatus status = statuses.get(reached);
		return new Arrival(target, status, status == TargetStatus.UNKNOWN
			? heldItems.missing(target.getRequiredItems()) : List.of());
	}
}
