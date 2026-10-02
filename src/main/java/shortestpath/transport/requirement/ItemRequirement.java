package shortestpath.transport.requirement;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import shortestpath.Util;

/**
 * Represents an item requirement with its variations and quantity.
 * For example: AIR_RUNE=3 where AIR_RUNE can be substituted by DUST_RUNE,
 * SMOKE_RUNE, etc.
 * OR alternatives are kept as separate {@link Branch branches} with their own
 * quantities, for example SHANTAY_PASS=1|COINS=5 is satisfied by one Shantay
 * pass or by 5 coins.
 */
@Getter
public class ItemRequirement
{
	/**
	 * One {@code NAME=quantity} alternative of an OR group. Its itemIds are
	 * that item's variation ids.
	 */
	@Getter
	public static final class Branch
	{
		private final int[] itemIds;
		private final int[] staffIds;
		private final int[] offhandIds;
		private final int quantity;

		public Branch(int[] itemIds, int[] staffIds, int[] offhandIds, int quantity)
		{
			this.itemIds = itemIds;
			this.staffIds = staffIds;
			this.offhandIds = offhandIds;
			this.quantity = quantity;
		}

		@Override
		public int hashCode()
		{
			int result = Arrays.hashCode(itemIds);
			result = 31 * result + Arrays.hashCode(staffIds);
			result = 31 * result + Arrays.hashCode(offhandIds);
			result = 31 * result + quantity;
			return result;
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
			Branch that = (Branch) o;
			return quantity == that.quantity &&
				Arrays.equals(itemIds, that.itemIds) &&
				Arrays.equals(staffIds, that.staffIds) &&
				Arrays.equals(offhandIds, that.offhandIds);
		}
	}

	/**
	 * The OR alternatives of this requirement, in declaration order, each with
	 * its own quantity. A requirement without OR alternatives has one branch.
	 */
	private final List<Branch> branches;

	/**
	 * The item IDs that satisfy this requirement (variations of every OR branch)
	 */
	private final int[] itemIds;

	/**
	 * Staff IDs that can substitute runes for this requirement
	 */
	private final int[] staffIds;

	/**
	 * Offhand IDs that can substitute for this requirement
	 */
	private final int[] offhandIds;

	/**
	 * The largest quantity any branch requires; kept for callers that predate
	 * per-branch quantities (e.g. external report tooling). OR branches can
	 * need different quantities, so satisfaction logic must use
	 * {@link #getBranches()}.
	 */
	private final int quantity;

	public ItemRequirement(int[] itemIds, int[] staffIds, int[] offhandIds, int quantity)
	{
		this(List.of(new Branch(itemIds, staffIds, offhandIds, quantity)));
	}

	public ItemRequirement(List<Branch> branches)
	{
		if (branches == null || branches.isEmpty())
		{
			throw new IllegalArgumentException("An item requirement needs at least one branch");
		}
		this.branches = List.copyOf(branches);
		int maxQuantity = -1;
		int[][] itemIdArrays = new int[this.branches.size()][];
		int[][] staffIdArrays = new int[this.branches.size()][];
		int[][] offhandIdArrays = new int[this.branches.size()][];
		for (int i = 0; i < this.branches.size(); i++)
		{
			Branch branch = this.branches.get(i);
			itemIdArrays[i] = branch.getItemIds();
			staffIdArrays[i] = branch.getStaffIds();
			offhandIdArrays[i] = branch.getOffhandIds();
			maxQuantity = Math.max(maxQuantity, branch.getQuantity());
		}
		if (this.branches.size() == 1)
		{
			Branch only = this.branches.get(0);
			this.itemIds = only.getItemIds();
			this.staffIds = only.getStaffIds();
			this.offhandIds = only.getOffhandIds();
		}
		else
		{
			this.itemIds = Util.concatenate(itemIdArrays);
			this.staffIds = Util.concatenate(staffIdArrays);
			this.offhandIds = Util.concatenate(offhandIdArrays);
		}
		this.quantity = maxQuantity;
	}

	@Override
	public int hashCode()
	{
		return branches.hashCode();
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
		ItemRequirement that = (ItemRequirement) o;
		return branches.equals(that.branches);
	}
}
