package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;

/**
 * The items the player holds, as item id to quantity: what they carry (inventory, worn items and
 * rune pouch), and that plus the bank as last seen. A bank not opened this session is empty.
 * Immutable; {@link PathfinderConfig} takes a new snapshot on every refresh.
 */
public final class HeldItems
{
	public static final HeldItems NONE = new HeldItems(Map.of(), Map.of(), false);

	private final Map<Integer, Integer> carried;
	private final Map<Integer, Integer> carriedAndBanked;
	private final boolean bankSeen;

	public HeldItems(Map<Integer, Integer> carried, Map<Integer, Integer> carriedAndBanked, boolean bankSeen)
	{
		this.carried = Map.copyOf(carried);
		this.carriedAndBanked = Map.copyOf(carriedAndBanked);
		this.bankSeen = bankSeen;
	}

	/** Inventory, worn items and the rune pouch. */
	public Map<Integer, Integer> carried()
	{
		return carried;
	}

	/** The carried items plus the bank's contents. */
	public Map<Integer, Integer> carriedAndBanked()
	{
		return carriedAndBanked;
	}

	/** Whether the bank has been opened this session, so its contents are known. */
	public boolean isBankSeen()
	{
		return bankSeen;
	}

	/** Whether {@code required} is carried, covered once the bank is added, or neither. */
	public TargetStatus statusOf(TransportItems required)
	{
		return statusOf(required, Set.of());
	}

	/**
	 * As {@link #statusOf(TransportItems)}, with the player's declared
	 * {@link Unlock unlocks} relieving unlock branches.
	 */
	public TargetStatus statusOf(TransportItems required, Set<Unlock> unlocks)
	{
		if (covers(carried, required, unlocks))
		{
			return TargetStatus.CARRIED;
		}
		if (covers(carriedAndBanked, required, unlocks))
		{
			return TargetStatus.BANK;
		}
		return TargetStatus.UNKNOWN;
	}

	/** The item groups of {@code required} that the carried items and the bank together don't cover. */
	public List<ItemRequirement> missing(TransportItems required)
	{
		return missing(required, Set.of());
	}

	/**
	 * As {@link #missing(TransportItems)}, with the player's declared
	 * {@link Unlock unlocks} relieving unlock branches.
	 */
	public List<ItemRequirement> missing(TransportItems required, Set<Unlock> unlocks)
	{
		List<ItemRequirement> missing = new ArrayList<>();
		for (ItemRequirement group : required.getRequirements())
		{
			if (!covers(carriedAndBanked, new TransportItems(List.of(group)), unlocks))
			{
				missing.add(group);
			}
		}
		return missing;
	}

	// Required items are held, never spent, so no currency threshold applies.
	private static boolean covers(Map<Integer, Integer> items, TransportItems required, Set<Unlock> unlocks)
	{
		return required.isSatisfiedBy(items, Set.of(), Integer.MAX_VALUE, unlocks);
	}
}
