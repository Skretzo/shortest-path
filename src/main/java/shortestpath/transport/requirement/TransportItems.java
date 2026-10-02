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
			if (!usedOffhand && hasOwnedOffhand(req, itemCounts))
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
	 * {@code true} if counted items pay this requirement through any OR branch at that
	 * branch's quantity, {@code false} if not, {@code null} if the player has the items
	 * but every covering branch exceeds the currency threshold. A satisfied branch wins
	 * over a blocked one, whatever their order.
	 */
	private static Boolean satisfiedByItems(
		ItemRequirement req,
		Map<Integer, Integer> itemCounts,
		Set<Integer> currencies,
		int currencyThreshold)
	{
		boolean blocked = false;
		for (ItemRequirement.Branch branch : req.getBranches())
		{
			int[] itemIds = branch.getItemIds();
			if (itemIds == null)
			{
				continue;
			}
			for (int itemId : itemIds)
			{
				if (!hasQuantity(itemCounts, itemId, branch.getQuantity(), false))
				{
					continue;
				}
				if (currencies != null && currencies.contains(itemId) && branch.getQuantity() > currencyThreshold)
				{
					blocked = true;
					break;
				}
				return true;
			}
		}
		return blocked ? null : false;
	}

	/**
	 * {@code true} when any OR branch has an owned offhand id at that branch's quantity.
	 */
	private static boolean hasOwnedOffhand(ItemRequirement req, Map<Integer, Integer> itemCounts)
	{
		for (ItemRequirement.Branch branch : req.getBranches())
		{
			if (hasOwnedSubstitute(branch.getOffhandIds(), itemCounts, branch.getQuantity(), true))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean existsOneStaffCovering(List<ItemRequirement> leftover, Map<Integer, Integer> itemCounts)
	{
		Set<Integer> candidates = null;
		for (ItemRequirement req : leftover)
		{
			Set<Integer> ownedStaves = new HashSet<>();
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				ownedStaves.addAll(ownedIds(branch.getStaffIds(), itemCounts, branch.getQuantity(), true));
			}
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

	// Legacy getters for backwards compatibility. They emit one row per OR
	// branch, in requirement order and then branch order; a requirement
	// without OR alternatives still gives one row. Use size() or
	// getRequirements() to count AND groups.
	private int branchCount()
	{
		int n = 0;
		for (ItemRequirement req : requirements)
		{
			n += req.getBranches().size();
		}
		return n;
	}

	public int[][] getItems()
	{
		int[][] items = new int[branchCount()][];
		int i = 0;
		for (ItemRequirement req : requirements)
		{
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				items[i++] = branch.getItemIds();
			}
		}
		return items;
	}

	public int[][] getStaves()
	{
		int[][] staves = new int[branchCount()][];
		int i = 0;
		for (ItemRequirement req : requirements)
		{
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				staves[i++] = branch.getStaffIds();
			}
		}
		return staves;
	}

	public int[][] getOffhands()
	{
		int[][] offhands = new int[branchCount()][];
		int i = 0;
		for (ItemRequirement req : requirements)
		{
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				offhands[i++] = branch.getOffhandIds();
			}
		}
		return offhands;
	}

	public int[] getQuantities()
	{
		int[] quantities = new int[branchCount()];
		int i = 0;
		for (ItemRequirement req : requirements)
		{
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				quantities[i++] = branch.getQuantity();
			}
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
