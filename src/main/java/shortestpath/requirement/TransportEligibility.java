package shortestpath.requirement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.settings.TeleportationItem;
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
	 * ({@link BankVisitState#CARRIED}) or the bank-path pool ({@link BankVisitState#BANKED}).
	 * Teleportation item transports are first bypassed or blocked by the
	 * teleportation-item setting; fairy rings additionally require a Dramen/Lunar
	 * staff when the Lumbridge Elite diary is incomplete; everything else defers to
	 * {@link TransportItems#isSatisfiedBy} at the configured currency threshold.
	 */
	public boolean usable(Transport transport, BankVisitState banked)
	{
		Boolean typeVerdict = teleportationItemVerdict(transport);
		if (typeVerdict != null)
		{
			return typeVerdict;
		}

		Map<Integer, Integer> ownedItems = banked == BankVisitState.BANKED ? bankPathItems : carriedItems;

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
	 * The verdict the teleportation-item setting gives this transport type before any
	 * item check: {@code Boolean.TRUE} when the setting bypasses the item requirements
	 * outright, {@code Boolean.FALSE} when it blocks the type regardless of items
	 * ({@link TeleportationItem#NONE}), and {@code null} when the transport's item
	 * requirements must be consulted (any other type or setting).
	 */
	private Boolean teleportationItemVerdict(Transport transport)
	{
		TransportType type = transport.getType();
		if (!TransportType.TELEPORTATION_ITEM.equals(type)
			&& !TransportType.SEASONAL_TRANSPORTS.equals(type)
			&& !TransportType.QUETZAL_WHISTLE.equals(type))
		{
			return null;
		}
		switch (teleportationItemSetting)
		{
			case ALL:
			case UNLOCKED:
				return Boolean.TRUE;
			case ALL_NON_CONSUMABLE:
			case UNLOCKED_NON_CONSUMABLE:
				// Mirror the requirement chain's gate: non-consumable modes
				// reject consumables even when their items are owned.
				return transport.isConsumable() ? Boolean.FALSE : Boolean.TRUE;
			case INVENTORY_NON_CONSUMABLE:
			case INVENTORY_AND_BANK_NON_CONSUMABLE:
				return transport.isConsumable() ? Boolean.FALSE : null;
			case NONE:
				return Boolean.FALSE;
			default:
				return null;
		}
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
		return bankPickupPlan(transport, carriedItems, bankPouchRunes, bankHas);
	}

	/**
	 * {@link #bankPickupPlan(Transport)} evaluated against an arbitrary carried pool,
	 * pouch-rune source and bank supply, so {@link ConsumptionLedger#pickupPlan} can
	 * ask what the bank must supply after earlier edges already consumed or picked
	 * items up — and so a banked pouch whose runes were already credited into that
	 * pool is no longer offered (an empty pouch source disables every pouch branch
	 * below).
	 *
	 * <p>Bank coverage is resolved against a working copy of {@code bankSource}
	 * decremented as each requirement's pickup lands, so the same bank stock cannot
	 * fund two requirements of this plan; the caller's map is left untouched.
	 */
	private BankPickupPlan bankPickupPlan(Transport transport, Map<Integer, Integer> playerItems,
		Map<Integer, Integer> pouchRunes, Map<Integer, Integer> bankSource)
	{
		Map<Integer, Long> items = new LinkedHashMap<>();
		Map<Integer, Long> resolvedItems = new LinkedHashMap<>();
		Set<Integer> bankItemIds = new HashSet<>();
		if (transport.getItemRequirements() == null)
		{
			return new BankPickupPlan(items, bankItemIds, resolvedItems, false);
		}

		// Bank stock earmarked by earlier resolutions in this plan is spent: an id
		// counted toward one requirement's shortfall cannot cover another's.
		Map<Integer, Integer> bank = new HashMap<>(bankSource);

		// Prefer the bank rune pouch over individual runes. This avoids surfacing
		// combination rune variants (mist, dust, etc.) when the pouch already covers
		// the requirement.
		Map<Integer, Integer> carried = carriedWithBankPouch(transport, playerItems, bank, pouchRunes, unlocks);
		boolean pouchTaken = carried != playerItems;
		if (pouchTaken)
		{
			items.put(bankPouchId, 1L);
			resolvedItems.put(bankPouchId, 1L);
			bankItemIds.add(bankPouchId);
			debit(bank, bankPouchId, 1);
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
				addCovering(branch.getItemIds(), pickupQuantity(branch), carried, bank, bankItemIds);
			}
			addCovering(req.getStaffIds(), 1, Map.of(), bank, bankItemIds);
			addCovering(req.getOffhandIds(), 1, Map.of(), bank, bankItemIds);

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
				foundId = findCarriedCovering(branch.getItemIds(), pickupQuantity(branch), carried, bank);
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
					foundId = findCovering(branch.getItemIds(), pickupQuantity(branch), carried, bank);
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
				int withdrawn = pickupQuantity(chosen) - carriedQty;
				items.merge(displayId, (long) withdrawn, Long::sum);
				resolvedItems.merge(foundId, (long) withdrawn, Long::sum);
				debit(bank, foundId, withdrawn);
				continue;
			}
			foundId = findCovering(req.getStaffIds(), 1, Map.of(), bank);
			if (foundId == -1)
			{
				foundId = findCovering(req.getOffhandIds(), 1, Map.of(), bank);
			}
			if (foundId == -1)
			{
				fullySupplied = false; // bank can't satisfy this requirement
				continue;
			}
			items.merge(foundId, 1L, Long::sum);
			resolvedItems.merge(foundId, 1L, Long::sum);
			debit(bank, foundId, 1);
		}
		return new BankPickupPlan(fullySupplied ? items : null, bankItemIds, resolvedItems, pouchTaken);
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
		Map<Integer, Long> resolvedItems = new LinkedHashMap<>();
		resolvedItems.put(staffIds[0], 1L);
		return new BankPickupPlan(items, bankItemIds, resolvedItems, false);
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
		/**
		 * The real covering ids the pickup resolves to — the actual bank or carried
		 * variant id per requirement (never canonicalised for display) plus the bank
		 * pouch id when taken — mapped to quantity. {@link ConsumptionLedger#commit}
		 * credits these into its pool; unlike {@link #items} they are populated even
		 * when the bank cannot fully supply the transport.
		 */
		public final Map<Integer, Long> resolvedItems;
		/** Whether the plan withdraws the banked rune pouch. */
		public final boolean pouchTaken;

		private BankPickupPlan(Map<Integer, Long> items, Set<Integer> bankItemIds,
			Map<Integer, Long> resolvedItems, boolean pouchTaken)
		{
			this.items = items;
			this.bankItemIds = bankItemIds;
			this.resolvedItems = resolvedItems;
			this.pouchTaken = pouchTaken;
		}
	}

	/**
	 * A running item-pool view of this snapshot for questions about a whole path:
	 * the pool starts as a copy of {@code carriedItems}, {@link #spend} deducts what
	 * each transport consumes, {@link #commit} credits a committed bank pickup back
	 * in, and {@link #visitBank} refills it to the bank-path pool. The pathfinder
	 * checks every edge against one frozen snapshot; the ledger is what answers the
	 * sequential question "is this transport still payable after the earlier ones?"
	 * from that same snapshot.
	 */
	public ConsumptionLedger consumptionLedger()
	{
		return new ConsumptionLedger();
	}

	public final class ConsumptionLedger
	{
		private Map<Integer, Integer> pool = new HashMap<>(carriedItems);
		private Map<Integer, Integer> bankRemaining = new HashMap<>(bankHas);
		private boolean pouchRunesCredited;

		private ConsumptionLedger()
		{
		}

		/**
		 * Whether the running pool meets the transport's item requirements right now,
		 * with no currency threshold (owned items are spent, not budgeted against).
		 *
		 * <p>This is deliberately the item-level question: it ignores the
		 * type-level bypasses {@link #usable} applies, which is what the
		 * bank-pickup hint wants — a teleport routed without its item should
		 * still surface "pick up the item". Callers replaying a path that was
		 * actually routed (was this transport usable as the search saw it?)
		 * must use {@link #usableAsRouted} instead, or they will falsely flag
		 * transports the search legitimately routed under a bypassing setting.
		 */
		public boolean satisfied(Transport transport)
		{
			TransportItems transportItems = transport.getItemRequirements();
			return transportItems == null
				|| transportItems.isSatisfiedBy(pool, TransportItems.CURRENCIES, Integer.MAX_VALUE);
		}

		/**
		 * Whether this transport is still usable on the running pool as it was
		 * routed: the same type-level verdicts {@link #usable} applies under the
		 * teleportation-item setting, then the fairy-ring staff gate, then the
		 * {@link #satisfied} item check against the pool. Use this when replaying
		 * a recorded path — transports routed under a bypassing setting return
		 * true here even when the player owns none of their items.
		 */
		public boolean usableAsRouted(Transport transport)
		{
			Boolean typeVerdict = teleportationItemVerdict(transport);
			if (typeVerdict != null)
			{
				return typeVerdict;
			}
			if (TransportType.FAIRY_RING.equals(transport.getType()) && fairyRingStaffRequired
				&& !DRAMEN_STAFF.isSatisfiedBy(pool, TransportItems.CURRENCIES, currencyThreshold))
			{
				return false;
			}
			return satisfied(transport);
		}

		/**
		 * Deducts what taking the transport consumes from the pool. Payments resolve
		 * in the same allocation order {@link TransportItems#isSatisfiedBy} uses —
		 * per requirement, the first covering OR branch and item id in declaration
		 * order; else a single offhand; else one staff shared over the leftover
		 * requirements — so a carried air staff suppresses air-rune spend exactly as
		 * the satisfaction check allows it.
		 *
		 * <p>A resolved item payment deducts its branch quantity of the paying id iff
		 * the paying id is a {@link TransportItems#CURRENCIES currency}, the
		 * transport is flagged {@link Transport#isConsumable consumable}, or the
		 * transport casts a spell ({@link TransportType#TELEPORTATION_SPELL} or
		 * {@link TransportType#TELEPORTATION_SPELL_HOME}). Satisfaction via staves or
		 * offhands and every other item payment — tools, passes and other
		 * non-consumables are indistinguishable from tools in the current data —
		 * deducts nothing. Nothing is deducted when the pool cannot pay the
		 * transport at all; callers normally gate on {@link #satisfied} first.
		 */
		public void spend(Transport transport)
		{
			TransportItems transportItems = transport.getItemRequirements();
			if (transportItems == null)
			{
				return;
			}
			boolean itemPaymentsConsumed = transport.isConsumable()
				|| TransportType.TELEPORTATION_SPELL.equals(transport.getType())
				|| TransportType.TELEPORTATION_SPELL_HOME.equals(transport.getType());

			// Per-requirement payments assume disjoint paying ids: a merged transport
			// (TransportItems.merge concatenates requirements) can ask for the same
			// id in several requirements — e.g. coins=5 and coins=10. Each
			// requirement still pays independently here because that is what
			// physical payment does, while satisfied()/isSatisfiedBy checks every
			// requirement against the full pool and reports the transport as
			// payable. In that case the summed charge below exceeds the pool and
			// drains it to zero — erring toward over-charging so downstream edges
			// see fewer items rather than more.
			Map<Integer, Integer> payments = new HashMap<>();
			List<ItemRequirement> leftover = new ArrayList<>();
			boolean usedOffhand = false;
			for (ItemRequirement req : transportItems.getRequirements())
			{
				int paidId = -1;
				int paidQuantity = 0;
				for (ItemRequirement.Branch branch : req.getBranches())
				{
					int[] itemIds = branch.getItemIds();
					if (itemIds == null)
					{
						continue;
					}
					for (int itemId : itemIds)
					{
						if (TransportItems.hasQuantity(pool, itemId, branch.getQuantity(), false))
						{
							paidId = itemId;
							paidQuantity = branch.getQuantity();
							break;
						}
					}
					if (paidId != -1)
					{
						break;
					}
				}
				if (paidId != -1)
				{
					payments.merge(paidId, paidQuantity, Integer::sum);
					continue;
				}
				if (!usedOffhand && TransportItems.hasOwnedOffhand(req, pool))
				{
					usedOffhand = true;
					continue;
				}
				leftover.add(req);
			}
			if (!leftover.isEmpty() && !TransportItems.existsOneStaffCovering(leftover, pool))
			{
				return; // the pool cannot pay this transport — charge nothing
			}
			for (Map.Entry<Integer, Integer> payment : payments.entrySet())
			{
				int itemId = payment.getKey();
				if (itemPaymentsConsumed || TransportItems.CURRENCIES.contains(itemId))
				{
					// The summed per-requirement charge may exceed what the pool
					// holds (see above); clamp at zero so the pool never goes
					// negative and over-spend cannot hide a real shortfall.
					int remaining = pool.getOrDefault(itemId, 0) - payment.getValue();
					if (remaining > 0)
					{
						pool.put(itemId, remaining);
					}
					else
					{
						pool.remove(itemId);
					}
				}
			}
		}

		/**
		 * The bank pickup plan for one transport evaluated against the running pool
		 * rather than the frozen carried snapshot. Once a committed plan has credited
		 * the banked pouch's runes into the pool, the pouch is no longer offered:
		 * its runes already live in the pool, so offering them again would double
		 * count them and re-display the pouch as a pickup it cannot be. Bank
		 * coverage is checked against the remaining supply, so an earlier
		 * committed pickup cannot fund this edge a second time.
		 */
		public BankPickupPlan pickupPlan(Transport transport)
		{
			return bankPickupPlan(transport, pool, pouchRunesCredited ? Map.of() : bankPouchRunes,
				bankRemaining);
		}

		/**
		 * Credits a committed pickup plan into the pool — the player is assumed to
		 * withdraw the resolved items at the bank before continuing the path — and
		 * debits the remaining bank supply by the same amounts, so a later edge
		 * cannot plan to withdraw stock an earlier edge already claimed. The banked
		 * rune pouch's runes are credited once, the first time a plan takes the
		 * pouch. Plans the bank cannot fully supply credit nothing.
		 */
		public void commit(BankPickupPlan plan)
		{
			if (plan == null || plan.items == null)
			{
				return;
			}
			plan.resolvedItems.forEach((itemId, quantity) ->
			{
				// The pouch itself is only ever withdrawn once, alongside the rune
				// credit below — never grow the pool's pouch count beyond one.
				if (itemId == bankPouchId && pouchRunesCredited)
				{
					return;
				}
				pool.merge(itemId, quantity.intValue(), Integer::sum);
				debit(bankRemaining, itemId, quantity.intValue());
			});
			if (plan.pouchTaken && !pouchRunesCredited)
			{
				bankPouchRunes.forEach((runeId, amount) -> pool.merge(runeId, amount, Integer::sum));
				pouchRunesCredited = true;
			}
		}

		/**
		 * Refills the pool to the bank-path pool: visiting a bank tops the player
		 * back up to everything the snapshot saw carried or in the bank. The
		 * remaining bank supply is not restored — items already committed as
		 * withdrawals are gone from every bank the player might visit next.
		 * Consumers replaying a routed path call this at bank-visited steps;
		 * {@link BankPickupRequirements} deliberately does not, since the pickup
		 * hint models a single withdrawal session at the bank it is shown at.
		 */
		public void visitBank()
		{
			pool = new HashMap<>(bankPathItems);
			pouchRunesCredited = false;
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
	 * Deducts {@code quantity} of {@code itemId} from a supply map, clamping at zero —
	 * a supply entry never goes negative when a resolved withdrawal exceeds it.
	 */
	private static void debit(Map<Integer, Integer> supply, int itemId, int quantity)
	{
		int remaining = supply.getOrDefault(itemId, 0) - quantity;
		if (remaining > 0)
		{
			supply.put(itemId, remaining);
		}
		else
		{
			supply.remove(itemId);
		}
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
