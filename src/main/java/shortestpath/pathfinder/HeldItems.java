package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import lombok.Getter;

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
	@Getter private final boolean bankSeen;

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


	/** Whether {@code required} is carried, covered once the bank is added, or neither. */
	public TargetStatus statusOf(TransportItems required)
	{
		if (covers(carried, required))
		{
			return TargetStatus.CARRIED;
		}
		if (covers(carriedAndBanked, required))
		{
			return TargetStatus.BANK;
		}
		return TargetStatus.UNKNOWN;
	}

	/** The item groups of {@code required} that the carried items and the bank together don't cover. */
	public List<ItemRequirement> missing(TransportItems required)
	{
		List<ItemRequirement> missing = new ArrayList<>();
		for (ItemRequirement group : required.getRequirements())
		{
			if (!covers(carriedAndBanked, new TransportItems(List.of(group))))
			{
				missing.add(group);
			}
		}
		return missing;
	}

	// Required items are held, never spent, so no currency threshold applies.
	private static boolean covers(Map<Integer, Integer> items, TransportItems required)
	{
		return required.isSatisfiedBy(items, Set.of(), Integer.MAX_VALUE);
	}
}
