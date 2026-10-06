package shortestpath.requirement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import shortestpath.ItemVariations;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Immutable snapshot of everything transport item-requirement evaluation needs, captured
 * once per refresh on the client thread. One snapshot answers every layer's question so
 * they can no longer diverge:
 * <ul>
 * <li>{@link #usable} — the pathfinding verdict over the carried pool ({@code banked}
 * false) or the bank-path pool ({@code banked} true), honouring the teleportation-item
 * setting, the fairy-ring staff rule and the currency threshold.</li>
 * <li>{@link #satisfiedByPlayer} — the carried-pool-only check the overlays use, with no
 * currency threshold (the player already owns the items, so none would be spent).</li>
 * <li>{@link #bankPickupPlan} and {@link #fairyStaffPickup} — the items the bank must
 * supply for one transport, plus the bank item ids to highlight.</li>
 * </ul>
 *
 * <p>The pools are fixed at construction: {@code carriedItems} is inventory + worn +
 * rune pouch in hand, {@code bankPathItems} additionally contains the bank contents when
 * bank paths are enabled (otherwise it is the carried pool), {@code bankHas} is the raw
 * bank contents and {@code bankPouchRunes} the runes inside a banked rune pouch.
 *
 * <p>Pickup witnesses are resolved in a canonical order shared by phrase and highlight:
 * the banked rune pouch first (when taken); then, per requirement the carried pool does
 * not satisfy, topping up a variant the player already carries across all OR branches in
 * declaration order; else the OR branches in declaration order with the pure
 * (first-listed) variant preferred; else staff ids, then offhand ids. The displayed item
 * id is the carried variant when topping up, else the chosen branch's first
 * (canonical) id.
 */
public final class TransportEligibility
{
	/**
	 * Fairy-ring staff requirement: a Dramen/Lunar staff, unless the Lumbridge Elite
	 * diary is complete. This is the single representation of that rule.
	 */
	private static final TransportItems DRAMEN_STAFF = new TransportItems(
		new int[][]{null},
		new int[][]{ItemVariations.DRAMEN_STAFF.getIds()},
		new int[][]{null},
		new int[]{1});

	private final Map<Integer, Integer> carriedItems;
	private final Map<Integer, Integer> bankPathItems;
	private final Map<Integer, Integer> bankHas;
	private final int bankPouchId;
	private final Map<Integer, Integer> bankPouchRunes;
	private final boolean fairyRingStaffRequired;
	private final TeleportationItem teleportationItemSetting;
	private final int currencyThreshold;
	private final Set<Unlock> unlocks;

	public TransportEligibility(
		Map<Integer, Integer> carriedItems,
		Map<Integer, Integer> bankPathItems,
		Map<Integer, Integer> bankHas,
		int bankPouchId,
		Map<Integer, Integer> bankPouchRunes,
		boolean fairyRingStaffRequired,
		TeleportationItem teleportationItemSetting,
		int currencyThreshold,
		Set<Unlock> unlocks)
	{
		this.carriedItems = Map.copyOf(carriedItems);
		this.bankPathItems = Map.copyOf(bankPathItems);
		this.bankHas = Map.copyOf(bankHas);
		this.bankPouchId = bankPouchId;
		this.bankPouchRunes = Map.copyOf(bankPouchRunes);
		this.fairyRingStaffRequired = fairyRingStaffRequired;
		this.teleportationItemSetting = teleportationItemSetting;
		this.currencyThreshold = currencyThreshold;
		this.unlocks = Set.copyOf(unlocks);
	}

	/**
	 * Builds a snapshot whose bank-path pool is the carried pool merged with the bank
	 * contents, with no fairy-ring staff requirement, a {@link TeleportationItem#NONE}
	 * setting, no currency threshold and no declared unlocks.
	 */
	public static TransportEligibility forPlayerAndBank(
		Map<Integer, Integer> playerItems,
		Map<Integer, Integer> bankHas,
		int bankPouchId,
		Map<Integer, Integer> bankPouchRunes)
	{
		Map<Integer, Integer> bankPathItems = new HashMap<>(playerItems);
		bankHas.forEach((itemId, quantity) -> bankPathItems.merge(itemId, quantity, Integer::sum));
		return new TransportEligibility(playerItems, bankPathItems, bankHas, bankPouchId, bankPouchRunes,
			false, TeleportationItem.NONE, Integer.MAX_VALUE, Set.of());
	}

	/**
	 * Whether the transport's item requirements are met by the carried pool
	 * ({@code banked} false) or the bank-path pool ({@code banked} true). Teleportation
	 * item transports are first bypassed or blocked by the teleportation-item setting;
	 * fairy rings additionally require a Dramen/Lunar staff when the Lumbridge Elite
	 * diary is incomplete; everything else defers to
	 * {@link TransportItems#isSatisfiedBy} at the configured currency threshold.
	 */
	public boolean usable(Transport transport, boolean banked)
	{
		if (TransportType.TELEPORTATION_ITEM.equals(transport.getType())
			|| TransportType.SEASONAL_TRANSPORTS.equals(transport.getType())
			|| TransportType.QUETZAL_WHISTLE.equals(transport.getType()))
		{
			switch (teleportationItemSetting)
			{
				case ALL:
				case UNLOCKED:
					return true;
				case ALL_NON_CONSUMABLE:
				case UNLOCKED_NON_CONSUMABLE:
					// Mirror the requirement chain's teleportation-item gate:
					// non-consumable modes reject consumables even when their
					// items are owned.
					return !transport.isConsumable();
				case INVENTORY_NON_CONSUMABLE:
				case INVENTORY_AND_BANK_NON_CONSUMABLE:
					if (transport.isConsumable())
					{
						return false;
					}
					break;
				case NONE:
					return false;
				default:
					break;
			}
		}

		Map<Integer, Integer> ownedItems = banked ? bankPathItems : carriedItems;

		// Fairy rings require Dramen/Lunar staff unless the Lumbridge Elite diary is complete
		if (TransportType.FAIRY_RING.equals(transport.getType()) && fairyRingStaffRequired
			&& !DRAMEN_STAFF.isSatisfiedBy(ownedItems, TransportItems.CURRENCIES, currencyThreshold))
		{
			return false;
		}

		TransportItems transportItems = transport.getItemRequirements();
		return transportItems == null
			|| transportItems.isSatisfiedBy(ownedItems, TransportItems.CURRENCIES, currencyThreshold, unlocks);
	}

	/**
	 * Whether the carried pool alone meets the transport's item requirements, ignoring
	 * the currency threshold — the player already owns the items, so nothing is spent.
	 */
	public boolean satisfiedByPlayer(Transport transport)
	{
		TransportItems transportItems = transport.getItemRequirements();
		return transportItems == null
			|| transportItems.isSatisfiedBy(carriedItems, TransportItems.CURRENCIES, Integer.MAX_VALUE, unlocks);
	}

	/**
	 * A copy of the item ids of the Dramen/Lunar staves that unlock fairy rings without
	 * the Lumbridge Elite diary.
	 */
	public static int[] fairyStaffIds()
	{
		return DRAMEN_STAFF.getRequirements().get(0).getStaffIds().clone();
	}

	/**
	 * The items to pick up from the bank for one transport and the bank item ids to
	 * highlight. {@link BankPickupPlan#items} is {@code null} when the bank cannot fully
	 * supply the transport; the highlight ids are still populated with whatever relevant
	 * items the bank does hold.
	 */
	public BankPickupPlan bankPickupPlan(Transport transport)
	{
		Map<Integer, Long> items = new LinkedHashMap<>();
		Set<Integer> bankItemIds = new HashSet<>();
		if (transport.getItemRequirements() == null)
		{
			return new BankPickupPlan(items, bankItemIds);
		}

		// Prefer the bank rune pouch over individual runes. This avoids surfacing
		// combination rune variants (mist, dust, etc.) when the pouch already covers
		// the requirement.
		Map<Integer, Integer> carried = carriedWithBankPouch(transport, carriedItems, bankHas, bankPouchRunes, unlocks);
		if (carried != carriedItems)
		{
			items.put(bankPouchId, 1L);
			bankItemIds.add(bankPouchId);
		}

		boolean fullySupplied = true;
		for (ItemRequirement req : transport.getItemRequirements().getRequirements())
		{
			if (playerSatisfies(req, carried, unlocks))
			{
				continue;
			}

			// Highlight every bank item that covers part of the shortfall: all item
			// variants of each OR branch at that branch's quantity, plus any covering
			// staff or offhand the bank holds.
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				addCovering(branch.getItemIds(), pickupQuantity(branch), carried, bankHas, bankItemIds);
			}
			addCovering(req.getStaffIds(), 1, Map.of(), bankHas, bankItemIds);
			addCovering(req.getOffhandIds(), 1, Map.of(), bankHas, bankItemIds);

			if (!fullySupplied)
			{
				continue; // pickup resolution already failed; keep scanning for ids only
			}

			// Resolve the pickup under the canonical witness order. First top up a
			// variant the player already carries, across all OR branches, which saves a
			// slot, then fall back to the branches in declaration order (pure item
			// variant first within each branch). The displayed item id is the carried
			// variant when topping up, else the chosen branch's canonical id.
			ItemRequirement.Branch chosen = null;
			int foundId = -1;
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				foundId = findCarriedCovering(branch.getItemIds(), pickupQuantity(branch), carried, bankHas);
				if (foundId != -1)
				{
					chosen = branch;
					break;
				}
			}
			if (chosen == null)
			{
				for (ItemRequirement.Branch branch : req.getBranches())
				{
					foundId = findCovering(branch.getItemIds(), pickupQuantity(branch), carried, bankHas);
					if (foundId != -1)
					{
						chosen = branch;
						break;
					}
				}
			}
			if (chosen != null)
			{
				int carriedQty = carried.getOrDefault(foundId, 0);
				int displayId = carriedQty > 0 ? foundId : chosen.getItemIds()[0];
				items.merge(displayId, (long) (pickupQuantity(chosen) - carriedQty), Long::sum);
				continue;
			}
			foundId = findCovering(req.getStaffIds(), 1, Map.of(), bankHas);
			if (foundId == -1)
			{
				foundId = findCovering(req.getOffhandIds(), 1, Map.of(), bankHas);
			}
			if (foundId == -1)
			{
				fullySupplied = false; // bank can't satisfy this requirement
				continue;
			}
			items.merge(foundId, 1L, Long::sum);
		}
		return new BankPickupPlan(fullySupplied ? items : null, bankItemIds);
	}

	/**
	 * The single staff pickup for a path that uses fairy rings: one covering
	 * Dramen/Lunar staff from the bank, or {@code null} when the Lumbridge Elite diary
	 * is complete, the player already carries a covering staff, or the bank holds none.
	 */
	public BankPickupPlan fairyStaffPickup()
	{
		if (!fairyRingStaffRequired)
		{
			return null;
		}
		int[] staffIds = fairyStaffIds();
		if (findCovering(staffIds, 1, Map.of(), carriedItems) != -1)
		{
			return null;
		}
		Set<Integer> bankItemIds = new HashSet<>();
		addCovering(staffIds, 1, Map.of(), bankHas, bankItemIds);
		if (bankItemIds.isEmpty())
		{
			return null;
		}
		Map<Integer, Long> items = new LinkedHashMap<>();
		items.put(staffIds[0], 1L);
		return new BankPickupPlan(items, bankItemIds);
	}

	/**
	 * The result of {@link #bankPickupPlan} or {@link #fairyStaffPickup}: the pickup
	 * items (id to quantity; {@code null} when the bank cannot fully supply the
	 * transport) and the bank item ids to highlight.
	 */
	public static final class BankPickupPlan
	{
		public final Map<Integer, Long> items;
		public final Set<Integer> bankItemIds;

		private BankPickupPlan(Map<Integer, Long> items, Set<Integer> bankItemIds)
		{
			this.items = items;
			this.bankItemIds = bankItemIds;
		}
	}

	/**
	 * Returns true if the player already meets this requirement on its own, with enough of
	 * one item variant of any OR branch at that branch's quantity, or with a staff or
	 * offhand that substitutes for it. An unlock branch is met by declaring its unlock;
	 * it can never be covered by items, so it never contributes a pickup.
	 */
	private static boolean playerSatisfies(ItemRequirement req, Map<Integer, Integer> playerHas,
		Set<Unlock> unlocks)
	{
		for (ItemRequirement.Branch branch : req.getBranches())
		{
			if (branch.getUnlock() != null)
			{
				if (unlocks.contains(branch.getUnlock()))
				{
					return true;
				}
				continue;
			}
			if (findCovering(branch.getItemIds(), pickupQuantity(branch), Map.of(), playerHas) != -1)
			{
				return true;
			}
		}
		return findCovering(req.getStaffIds(), 1, Map.of(), playerHas) != -1
			|| findCovering(req.getOffhandIds(), 1, Map.of(), playerHas) != -1;
	}

	/**
	 * Returns true if {@code source} holds enough of {@code id} to make up what {@code carried}
	 * lacks of {@code requiredQty}.
	 */
	private static boolean covers(int id, int requiredQty, Map<Integer, Integer> carried, Map<Integer, Integer> source)
	{
		return source.getOrDefault(id, 0) >= requiredQty - carried.getOrDefault(id, 0);
	}

	/**
	 * Returns the first ID in {@code itemIds} that {@code source} {@link #covers covers}, or -1 if none does.
	 * Item variations list the pure item first, so it is preferred over combination variants.
	 */
	private static int findCovering(int[] itemIds, int requiredQty, Map<Integer, Integer> carried, Map<Integer, Integer> source)
	{
		if (itemIds == null)
		{
			return -1;
		}
		for (int id : itemIds)
		{
			if (covers(id, requiredQty, carried, source))
			{
				return id;
			}
		}
		return -1;
	}

	/**
	 * Returns the first ID in {@code itemIds} that the player already carries some of and that
	 * {@code source} {@link #covers covers}, or -1 if none does.
	 */
	private static int findCarriedCovering(int[] itemIds, int requiredQty, Map<Integer, Integer> carried,
		Map<Integer, Integer> source)
	{
		if (itemIds == null)
		{
			return -1;
		}
		for (int id : itemIds)
		{
			if (carried.getOrDefault(id, 0) > 0 && covers(id, requiredQty, carried, source))
			{
				return id;
			}
		}
		return -1;
	}

	/**
	 * Adds every ID in {@code itemIds} that {@code source} {@link #covers covers} to {@code out}.
	 */
	private static void addCovering(int[] itemIds, int requiredQty, Map<Integer, Integer> carried,
		Map<Integer, Integer> source, Set<Integer> out)
	{
		if (itemIds == null)
		{
			return;
		}
		for (int id : itemIds)
		{
			if (covers(id, requiredQty, carried, source))
			{
				out.add(id);
			}
		}
	}

	/**
	 * Returns the quantity an OR branch asks for: the branch quantity when that is
	 * positive, otherwise 1.
	 */
	private static int pickupQuantity(ItemRequirement.Branch branch)
	{
		return branch.getQuantity() > 0 ? branch.getQuantity() : 1;
	}

	/**
	 * Returns what the player carries plus the bank rune pouch's runes, if the pouch is taken.
	 * The pouch is taken when, for at least one requirement the player doesn't already meet,
	 * either the pouch alone covers the shortfall of any OR branch at that branch's quantity,
	 * or loose bank items alone cover the shortfall of no OR branch but the pouch and loose
	 * bank items together cover the shortfall of one of them (counted per item ID, never
	 * across variants).
	 * Otherwise the pouch is not taken and {@code playerHas} itself is returned.
	 */
	private static Map<Integer, Integer> carriedWithBankPouch(Transport transport,
		Map<Integer, Integer> playerHas,
		Map<Integer, Integer> bankHas,
		Map<Integer, Integer> bankPouchRunes,
		Set<Unlock> unlocks)
	{
		// Pouch runes plus loose bank items, per item ID.
		Map<Integer, Integer> pouchAndBank = new HashMap<>(bankHas);
		bankPouchRunes.forEach((runeId, amount) -> pouchAndBank.merge(runeId, amount, Integer::sum));
		for (ItemRequirement req : transport.getItemRequirements().getRequirements())
		{
			if (playerSatisfies(req, playerHas, unlocks))
			{
				continue;
			}
			boolean pouchAlone = false;
			boolean looseAlone = false;
			boolean combined = false;
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				int qty = pickupQuantity(branch);
				pouchAlone = pouchAlone || findCovering(branch.getItemIds(), qty, playerHas, bankPouchRunes) != -1;
				looseAlone = looseAlone || findCovering(branch.getItemIds(), qty, playerHas, bankHas) != -1;
				combined = combined || findCovering(branch.getItemIds(), qty, playerHas, pouchAndBank) != -1;
			}
			if (pouchAlone || (!looseAlone && combined))
			{
				Map<Integer, Integer> carried = new HashMap<>(playerHas);
				bankPouchRunes.forEach((runeId, amount) -> carried.merge(runeId, amount, Integer::sum));
				return carried;
			}
		}
		return playerHas;
	}
}
