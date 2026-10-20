package shortestpath.requirement.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	 * One {@code NAME=quantity} alternative of an OR group — either a set of
	 * interchangeable item ids with their staff/offhand substitutes
	 * ({@link ItemBranch}) or a declared {@link Unlock}
	 * ({@link UnlockBranch}). The kind is part of the type: a branch can
	 * never carry both item ids and an unlock, nor neither.
	 *
	 * <p>The private constructor keeps the hierarchy effectively sealed —
	 * only classes nested in this top-level class can extend it.</p>
	 */
	public abstract static class Branch
	{
		private Branch()
		{
		}

		/**
		 * The quantity this alternative asks for. Item branches carry the
		 * parsed quantity (0 is meaningful — e.g. {@code HEADSLOT=0} asks the
		 * player to hold none); unlock alternatives always report 1, the only
		 * quantity the {@code UNLOCK_<NAME>=1} grammar admits.
		 */
		public abstract int getQuantity();

		/**
		 * Whether this alternative is a declared-unlock term rather than an
		 * item term.
		 */
		public abstract boolean isUnlock();

		/**
		 * Whether this alternative is met by the player's declared unlocks;
		 * item alternatives never are.
		 */
		public boolean satisfiedByUnlocks(Set<Unlock> unlocks)
		{
			return false;
		}

		/**
		 * Whether the alternative keeps at least one unblocked id once the
		 * player-blocked item ids are removed. Unlock alternatives carry no
		 * ids and can never be blocked.
		 */
		public abstract boolean survivesBlockedIds(Set<Integer> blockedIds);

		/**
		 * Whether the alternative keeps at least one item id that is not
		 * option-blocked at {@code packedDest}: an id whose representative
		 * lookup is negative is not a family member and can never be
		 * option-blocked; a member id survives when its representative's
		 * blocked set lacks this destination. Unlock alternatives carry no
		 * ids and can never be blocked.
		 */
		public abstract boolean survivesOptionBlock(int packedDest, Map<Integer, Integer> repIndex,
			Map<Integer, Set<Integer>> blockedDests);

		/**
		 * Creates an item alternative. At least one of the three id arrays
		 * must carry an id — an alternative holding nothing could never be
		 * satisfied. The arrays are shared requirement data; they are cloned
		 * on the way in and out so a stray write cannot corrupt them.
		 */
		public static ItemBranch item(int[] itemIds, int[] staffIds, int[] offhandIds, int quantity)
		{
			return new ItemBranch(itemIds, staffIds, offhandIds, quantity);
		}

		/**
		 * Creates an unlock alternative satisfied by declaring {@code unlock}.
		 */
		public static UnlockBranch unlock(Unlock unlock)
		{
			return new UnlockBranch(unlock);
		}
	}

	/**
	 * An item alternative: the variation ids of one {@code NAME=quantity}
	 * term plus the staves and offhands that can substitute for it.
	 * {@link #getItemIds()} may be {@code null} only on a substitute-only
	 * branch — a shape the parser never emits but tests use to pin that
	 * substitutes are not option-blocked.
	 */
	public static final class ItemBranch extends Branch
	{
		private final int[] itemIds;
		private final int[] staffIds;
		private final int[] offhandIds;
		private final int quantity;

		private ItemBranch(int[] itemIds, int[] staffIds, int[] offhandIds, int quantity)
		{
			if (isEmpty(itemIds) && isEmpty(staffIds) && isEmpty(offhandIds))
			{
				throw new IllegalArgumentException(
					"An item branch needs at least one item, staff or offhand id");
			}
			this.itemIds = itemIds == null ? null : itemIds.clone();
			this.staffIds = staffIds == null ? null : staffIds.clone();
			this.offhandIds = offhandIds == null ? null : offhandIds.clone();
			this.quantity = quantity;
		}

		private static boolean isEmpty(int[] ids)
		{
			return ids == null || ids.length == 0;
		}

		public int[] getItemIds()
		{
			return itemIds == null ? null : itemIds.clone();
		}

		public int[] getStaffIds()
		{
			return staffIds == null ? null : staffIds.clone();
		}

		public int[] getOffhandIds()
		{
			return offhandIds == null ? null : offhandIds.clone();
		}

		@Override
		public int getQuantity()
		{
			return quantity;
		}

		@Override
		public boolean isUnlock()
		{
			return false;
		}

		/**
		 * Any unblocked item, staff or offhand id keeps the branch alive.
		 */
		@Override
		public boolean survivesBlockedIds(Set<Integer> blockedIds)
		{
			for (int[] ids : new int[][]{itemIds, staffIds, offhandIds})
			{
				if (ids == null)
				{
					continue;
				}
				for (int itemId : ids)
				{
					if (!blockedIds.contains(itemId))
					{
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Any item id that is not option-blocked at {@code packedDest} keeps
		 * the branch alive. Staff and offhand ids do not participate — they
		 * are substitutes, not the family's served items — so a
		 * substitute-only branch can never be option-blocked.
		 */
		@Override
		public boolean survivesOptionBlock(int packedDest, Map<Integer, Integer> repIndex,
			Map<Integer, Set<Integer>> blockedDests)
		{
			if (itemIds == null)
			{
				return true;
			}
			for (int itemId : itemIds)
			{
				Integer rep = repIndex.get(itemId);
				if (rep == null)
				{
					return true;
				}
				Set<Integer> dests = blockedDests.get(rep);
				if (dests == null || !dests.contains(packedDest))
				{
					return true;
				}
			}
			return false;
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
			ItemBranch that = (ItemBranch) o;
			return quantity == that.quantity &&
				Arrays.equals(itemIds, that.itemIds) &&
				Arrays.equals(staffIds, that.staffIds) &&
				Arrays.equals(offhandIds, that.offhandIds);
		}
	}

	/**
	 * An unlock alternative: satisfied by declaring its {@link Unlock} and
	 * never covered or blocked by items, staves or offhands. It carries no
	 * quantity — satisfaction never reads one.
	 */
	public static final class UnlockBranch extends Branch
	{
		private final Unlock unlock;

		private UnlockBranch(Unlock unlock)
		{
			if (unlock == null)
			{
				throw new IllegalArgumentException("An unlock branch needs an unlock");
			}
			this.unlock = unlock;
		}

		public Unlock getUnlock()
		{
			return unlock;
		}

		@Override
		public int getQuantity()
		{
			return 1;
		}

		@Override
		public boolean isUnlock()
		{
			return true;
		}

		@Override
		public boolean satisfiedByUnlocks(Set<Unlock> unlocks)
		{
			return unlocks.contains(unlock);
		}

		@Override
		public boolean survivesBlockedIds(Set<Integer> blockedIds)
		{
			return true;
		}

		@Override
		public boolean survivesOptionBlock(int packedDest, Map<Integer, Integer> repIndex,
			Map<Integer, Set<Integer>> blockedDests)
		{
			return true;
		}

		@Override
		public int hashCode()
		{
			return unlock.hashCode();
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
			UnlockBranch that = (UnlockBranch) o;
			return unlock == that.unlock;
		}
	}

	/**
	 * The OR alternatives of this requirement, in declaration order, each with
	 * its own quantity. A requirement without OR alternatives has one branch.
	 */
	private final List<Branch> branches;

	/**
	 * The item alternatives among {@link #branches}, in declaration order.
	 * Consumers that only care about items iterate this and never meet an
	 * unlock alternative.
	 */
	private final List<ItemBranch> itemBranches;

	/**
	 * The unlock alternatives among {@link #branches}, in declaration order.
	 */
	private final List<UnlockBranch> unlockBranches;

	/**
	 * The item IDs that satisfy this requirement (variations of every item
	 * branch); unlock alternatives contribute none.
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
		this(List.of(Branch.item(itemIds, staffIds, offhandIds, quantity)));
	}

	public ItemRequirement(List<Branch> branches)
	{
		if (branches == null || branches.isEmpty())
		{
			throw new IllegalArgumentException("An item requirement needs at least one branch");
		}
		this.branches = List.copyOf(branches);
		List<ItemBranch> items = new ArrayList<>(this.branches.size());
		List<UnlockBranch> unlocks = new ArrayList<>();
		int maxQuantity = -1;
		for (Branch branch : this.branches)
		{
			// The hierarchy is sealed, so this split is exhaustive.
			if (branch instanceof ItemBranch)
			{
				items.add((ItemBranch) branch);
			}
			else
			{
				unlocks.add((UnlockBranch) branch);
			}
			maxQuantity = Math.max(maxQuantity, branch.getQuantity());
		}
		this.itemBranches = Collections.unmodifiableList(items);
		this.unlockBranches = Collections.unmodifiableList(unlocks);
		int[][] itemIdArrays = new int[items.size()][];
		int[][] staffIdArrays = new int[items.size()][];
		int[][] offhandIdArrays = new int[items.size()][];
		for (int i = 0; i < items.size(); i++)
		{
			ItemBranch branch = items.get(i);
			itemIdArrays[i] = branch.getItemIds();
			staffIdArrays[i] = branch.getStaffIds();
			offhandIdArrays[i] = branch.getOffhandIds();
		}
		this.itemIds = Util.concatenate(itemIdArrays);
		this.staffIds = Util.concatenate(staffIdArrays);
		this.offhandIds = Util.concatenate(offhandIdArrays);
		this.quantity = maxQuantity;
	}

	// The array fields are the requirement's own state; hand out copies so a
	// stray write through a getter cannot corrupt shared requirement data.

	public int[] getItemIds()
	{
		return itemIds == null ? null : itemIds.clone();
	}

	public int[] getStaffIds()
	{
		return staffIds == null ? null : staffIds.clone();
	}

	public int[] getOffhandIds()
	{
		return offhandIds == null ? null : offhandIds.clone();
	}

	/**
	 * A pure-unlock requirement has no item alternatives: it is satisfied by
	 * declaring any one of its unlocks and can never be covered by items,
	 * staves or offhands. Item evaluation is not the only gate for such
	 * requirements, since teleportation-item modes can skip item evaluation
	 * entirely.
	 */
	public boolean isPureUnlock()
	{
		return itemBranches.isEmpty();
	}

	/**
	 * Whether this requirement keeps at least one OR alternative once the
	 * player-blocked item ids are removed: an item branch survives on any
	 * unblocked item, staff or offhand id, and an unlock branch can never be
	 * blocked. This is a structural property of the requirement, independent
	 * of what the player owns.
	 */
	public boolean hasUnblockedAlternative(Set<Integer> blockedIds)
	{
		for (Branch branch : branches)
		{
			if (branch.survivesBlockedIds(blockedIds))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether this requirement keeps at least one OR alternative that is not
	 * option-blocked at {@code packedDest}: an item branch survives on an
	 * item id whose family representative lookup is negative (a non-family
	 * item can never be option-blocked) or whose representative's
	 * blocked-destination set does not contain {@code packedDest}, and an
	 * unlock branch can never be blocked. Structural like
	 * {@link #hasUnblockedAlternative}, independent of what the player owns.
	 */
	public boolean hasUnoptionBlockedAlternative(int packedDest,
		Map<Integer, Integer> repIndex, Map<Integer, Set<Integer>> blockedDests)
	{
		for (Branch branch : branches)
		{
			if (branch.survivesOptionBlock(packedDest, repIndex, blockedDests))
			{
				return true;
			}
		}
		return false;
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
