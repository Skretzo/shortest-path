package shortestpath.transport.requirement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;

/**
 * Represents all item requirements for a transport.
 * All requirements must be satisfied.
 */
@Getter
public class TransportItems
{
	private final List<ItemRequirement> requirements;

	public TransportItems(List<ItemRequirement> requirements)
	{
		this.requirements = List.copyOf(requirements);
	}

	/**
	 * Creates TransportItems from legacy array format for backwards compatibility.
	 */
	public TransportItems(int[][] items, int[][] staves, int[][] offhands, int[] quantities)
	{
		List<ItemRequirement> reqs = new ArrayList<>();
		for (int i = 0; i < items.length; i++)
		{
			reqs.add(new ItemRequirement(
				items[i],
				staves != null && i < staves.length ? staves[i] : new int[0],
				offhands != null && i < offhands.length ? offhands[i] : new int[0],
				quantities[i]));
		}
		this.requirements = Collections.unmodifiableList(reqs);
	}

	/**
	 * Merges two TransportItems, combining all requirements from both.
	 * If either is null, returns the other.
	 */
	public static TransportItems merge(TransportItems first, TransportItems second)
	{
		if (first == null)
		{
			return second;
		}
		if (second == null)
		{
			return first;
		}
		List<ItemRequirement> merged = new ArrayList<>();
		merged.addAll(first.requirements);
		merged.addAll(second.requirements);
		return new TransportItems(merged);
	}

	/**
	 * Gets the number of item requirements.
	 */
	public int size()
	{
		return requirements.size();
	}

	/**
	 * Returns whether the player can satisfy every item requirement at once.
	 *
	 * <p>Runes (and other counted items) are applied first, then at most one offhand
	 * (tome), then at most one staff. Combination staves may cover multiple leftover
	 * rune types because the same item id appears in more than one staff list. Two
	 * different staves cannot be used together.
	 */
	public boolean isSatisfiedBy(Map<Integer, Integer> itemCounts, Set<Integer> currencies, int currencyThreshold)
	{
		if (requirements.isEmpty())
		{
			return true;
		}

		List<ItemRequirement> leftover = new ArrayList<>();
		boolean usedOffhand = false;
		for (ItemRequirement req : requirements)
		{
			Boolean itemsResult = satisfiedByItems(req, itemCounts, currencies, currencyThreshold);
			if (itemsResult == null)
			{
				return false;
			}
			if (itemsResult)
			{
				continue;
			}
			if (!usedOffhand && hasOwnedSubstitute(req.getOffhandIds(), itemCounts, req.getQuantity(), true))
			{
				usedOffhand = true;
				continue;
			}
			leftover.add(req);
		}

		if (leftover.isEmpty())
		{
			return true;
		}
		return existsOneStaffCovering(leftover, itemCounts);
	}

	/**
	 * {@code true} if counted items pay this requirement, {@code false} if not,
	 * {@code null} if the player has the items but they exceed the currency threshold.
	 */
	private static Boolean satisfiedByItems(
		ItemRequirement req,
		Map<Integer, Integer> itemCounts,
		Set<Integer> currencies,
		int currencyThreshold)
	{
		int[] itemIds = req.getItemIds();
		if (itemIds == null)
		{
			return false;
		}
		int requiredQuantity = req.getQuantity();
		for (int itemId : itemIds)
		{
			if (!hasQuantity(itemCounts, itemId, requiredQuantity, false))
			{
				continue;
			}
			if (currencies != null && currencies.contains(itemId) && requiredQuantity > currencyThreshold)
			{
				return null;
			}
			return true;
		}
		return false;
	}

	private static boolean existsOneStaffCovering(List<ItemRequirement> leftover, Map<Integer, Integer> itemCounts)
	{
		Set<Integer> candidates = null;
		for (ItemRequirement req : leftover)
		{
			Set<Integer> ownedStaves = ownedIds(req.getStaffIds(), itemCounts, req.getQuantity(), true);
			if (ownedStaves.isEmpty())
			{
				return false;
			}
			if (candidates == null)
			{
				candidates = ownedStaves;
			}
			else
			{
				candidates.retainAll(ownedStaves);
			}
			if (candidates.isEmpty())
			{
				return false;
			}
		}
		return true;
	}

	private static boolean hasOwnedSubstitute(int[] ids, Map<Integer, Integer> itemCounts, int requiredQuantity, boolean unlimited)
	{
		return !ownedIds(ids, itemCounts, requiredQuantity, unlimited).isEmpty();
	}

	private static Set<Integer> ownedIds(int[] ids, Map<Integer, Integer> itemCounts, int requiredQuantity, boolean unlimited)
	{
		if (ids == null || ids.length == 0)
		{
			return Set.of();
		}
		Set<Integer> owned = new HashSet<>();
		for (int itemId : ids)
		{
			if (hasQuantity(itemCounts, itemId, requiredQuantity, unlimited))
			{
				owned.add(itemId);
			}
		}
		return owned;
	}

	private static boolean hasQuantity(Map<Integer, Integer> itemCounts, int itemId, int requiredQuantity, boolean unlimited)
	{
		int quantity = itemCounts.getOrDefault(itemId, 0);
		if (unlimited)
		{
			return requiredQuantity > 0 && quantity >= 1 || requiredQuantity == 0 && quantity == 0;
		}
		return requiredQuantity > 0 && quantity >= requiredQuantity || requiredQuantity == 0 && quantity == 0;
	}

	// Legacy getters for backwards compatibility
	public int[][] getItems()
	{
		int[][] items = new int[requirements.size()][];
		for (int i = 0; i < requirements.size(); i++)
		{
			items[i] = requirements.get(i).getItemIds();
		}
		return items;
	}

	public int[][] getStaves()
	{
		int[][] staves = new int[requirements.size()][];
		for (int i = 0; i < requirements.size(); i++)
		{
			staves[i] = requirements.get(i).getStaffIds();
		}
		return staves;
	}

	public int[][] getOffhands()
	{
		int[][] offhands = new int[requirements.size()][];
		for (int i = 0; i < requirements.size(); i++)
		{
			offhands[i] = requirements.get(i).getOffhandIds();
		}
		return offhands;
	}

	public int[] getQuantities()
	{
		int[] quantities = new int[requirements.size()];
		for (int i = 0; i < requirements.size(); i++)
		{
			quantities[i] = requirements.get(i).getQuantity();
		}
		return quantities;
	}

	private String toString(int[][] array)
	{
		StringBuilder text = new StringBuilder();
		for (int[] inner : array)
		{
			text.append((text.length() == 0) ? "" : ", ").append(Arrays.toString(inner));
		}
		return "[" + text + "]";
	}

	@Override
	public int hashCode()
	{
		return requirements.hashCode();
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}
		TransportItems that = (TransportItems) o;
		if (requirements.size() != that.requirements.size())
		{
			return false;
		}
		for (int i = 0; i < requirements.size(); i++)
		{
			if (!requirements.get(i).equals(that.requirements.get(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString()
	{
		return "[" +
			toString(getItems()) + ", " +
			toString(getStaves()) + ", " +
			toString(getOffhands()) + ", " +
			Arrays.toString(getQuantities()) + "]";
	}
}
