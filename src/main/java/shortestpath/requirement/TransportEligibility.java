package shortestpath.requirement;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.ItemVariations;
import shortestpath.items.OwnedItems;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.HeldItems;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import lombok.Getter;
import lombok.experimental.Accessors;

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
 * <li>{@link #bankPickupPlan} and {@link #targetPickupPlan} — the items the bank must
 * supply for one transport or target, plus the bank item ids to highlight.</li>
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
		new int[][]{ItemVariations.fromName("DRAMEN_STAFF").getIds()},
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
	/**
	 * Packed destinations of the balloon routes whose log basket the player
	 * declared stocked — the destinations a {@link TransportType#HOT_AIR_BALLOON}
	 * transport may land on to be granted {@link Unlock#BALLOON_LOG_BASKET}.
	 */
	private final Set<Integer> balloonLogBasketDestinations;
	/**
	 * {@link #unlocks} plus {@link Unlock#BALLOON_LOG_BASKET} — precomputed
	 * once so {@link #unlocksFor} hands out the same immutable set to every
	 * eligible balloon transport instead of allocating per call.
	 */
	private final Set<Unlock> unlocksWithBalloonBasket;
	@Getter @Accessors(fluent = true) private final HeldItems heldItems;

	public TransportEligibility(
		Map<Integer, Integer> carriedItems,
		Map<Integer, Integer> bankPathItems,
		Map<Integer, Integer> bankHas,
		int bankPouchId,
		Map<Integer, Integer> bankPouchRunes,
		boolean fairyRingStaffRequired,
		TeleportationItem teleportationItemSetting,
		int currencyThreshold,
		Set<Unlock> unlocks,
		boolean bankSeen,
		Set<Integer> balloonLogBasketDestinations)
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
		this.balloonLogBasketDestinations = Set.copyOf(balloonLogBasketDestinations);
		Set<Unlock> withBasket = EnumSet.noneOf(Unlock.class);
		withBasket.addAll(unlocks);
		withBasket.add(Unlock.BALLOON_LOG_BASKET);
		this.unlocksWithBalloonBasket = Collections.unmodifiableSet(withBasket);
		// What the player holds is a projection of this same collection: the carried
		// pool, plus the bank contents and a banked pouch's runes once the bank has
		// been seen — the one item snapshot also answers the target-status question.
		Map<Integer, Integer> carriedAndBanked = new HashMap<>(carriedItems);
		bankHas.forEach((itemId, quantity) -> carriedAndBanked.merge(itemId, quantity, Integer::sum));
		bankPouchRunes.forEach((itemId, quantity) -> carriedAndBanked.merge(itemId, quantity, Integer::sum));
		this.heldItems = new HeldItems(carriedItems, carriedAndBanked, bankSeen);
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
			false, TeleportationItem.NONE, Integer.MAX_VALUE, Set.of(), true, Set.of());
	}

	/**
	 * Captures the client state both the pathfinding verdicts and the bank-pickup plans
	 * read: the carried pool (inventory + worn + rune pouch in hand), the bank-path pool
	 * (which adds the bank contents when bank paths are enabled), the bank contents
	 * themselves, the runes inside a banked rune pouch, the fairy-ring staff gate and
	 * the currency threshold. Shared by
	 * {@link RequirementContext#capture RequirementContext.capture}
	 * and the lazy {@code PathfinderConfig#getEligibility()} rebuild so exactly one
	 * collection path exists.
	 */
	public static TransportEligibility collect(
		PlayerStateSource source,
		ItemContainer bank,
		TeleportationItem teleportationItemSetting,
		int currencyThreshold,
		boolean includeBankPath,
		Set<Unlock> unlocks)
	{
		return collect(source, bank, teleportationItemSetting, currencyThreshold,
			includeBankPath, unlocks, Set.of());
	}

	/**
	 * As {@link #collect(PlayerStateSource, ItemContainer, TeleportationItem, int,
	 * boolean, Set)}, additionally capturing the packed destinations of the balloon
	 * routes whose log basket the player declared stocked.
	 */
	public static TransportEligibility collect(
		PlayerStateSource source,
		ItemContainer bank,
		TeleportationItem teleportationItemSetting,
		int currencyThreshold,
		boolean includeBankPath,
		Set<Unlock> unlocks,
		Set<Integer> balloonLogBasketDestinations)
	{
		Map<Integer, Integer> carriedItems = collectItems(source, bank,
			teleportationItemSetting, true, true, false, true);
		Map<Integer, Integer> bankPathItems = includeBankPath
			? collectItems(source, bank, teleportationItemSetting, true, true, true, true)
			: carriedItems;
		Map<Integer, Integer> bankHas = new HashMap<>();
		OwnedItems.addContainer(bankHas, bank);
		int bankPouchId = -1;
		for (int pouchId : OwnedItems.RUNE_POUCHES)
		{
			if (bankHas.containsKey(pouchId))
			{
				bankPouchId = pouchId;
				break;
			}
		}
		Map<Integer, Integer> bankPouchRunes = bankPouchId == -1
			? Map.of()
			: source.runePouchContents();
		boolean fairyRingStaffRequired =
			source.varbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1;
		return new TransportEligibility(carriedItems, bankPathItems, bankHas, bankPouchId, bankPouchRunes,
			fairyRingStaffRequired, teleportationItemSetting, currencyThreshold,
			unlocks, bank != null, balloonLogBasketDestinations);
	}

	/**
	 * Item id to quantity over the selected containers, summed across containers.
	 */
	private static Map<Integer, Integer> collectItems(
		PlayerStateSource source,
		ItemContainer bank,
		TeleportationItem teleportationItemSetting,
		boolean checkInventory,
		boolean checkEquipment,
		boolean checkBank,
		boolean checkRunePouch)
	{
		Map<Integer, Integer> itemsAndQuantities = new HashMap<>(28 + 11 + 500);

		if (checkInventory)
		{
			OwnedItems.addContainer(itemsAndQuantities, source.itemContainer(InventoryID.INV));
		}

		if (checkEquipment)
		{
			OwnedItems.addContainer(itemsAndQuantities, source.itemContainer(InventoryID.WORN));
		}

		if (checkBank
			&& TeleportationItemPolicy.includesBank(teleportationItemSetting))
		{
			OwnedItems.addContainer(itemsAndQuantities, bank);
		}

		if (checkRunePouch)
		{
			source.addRunePouchContents(itemsAndQuantities);
		}

		return itemsAndQuantities;
	}

	/**
	 * What the player held when this snapshot was captured — carried items, those plus the
	 * last-seen bank, and whether the bank has been opened this session. The searches read
	 * it to decide each target's {@code TargetStatus}; it is derived from the same pools
	 * the eligibility verdicts use, so both can never observe different item state.
	 */


	/**
	 * The unlocks one transport's requirements are evaluated with: the declared
	 * unlocks, plus {@link Unlock#BALLOON_LOG_BASKET} when the transport is a
	 * hot-air balloon whose destination basket the player declared stocked.
	 * The grant is destination-scoped — only balloon rows carry the
	 * {@code UNLOCK_BALLOON_LOG_BASKET} token, so nothing else can consume it,
	 * and a balloon route whose destination is not configured still demands
	 * its specific log.
	 */
	private Set<Unlock> unlocksFor(Transport transport)
	{
		return TransportType.HOT_AIR_BALLOON.equals(transport.getType())
			&& balloonLogBasketDestinations.contains(transport.getDestination())
			? unlocksWithBalloonBasket
			: unlocks;
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
		// Mirror the requirement chain's teleportation-item gate: the shared
		// policy verdict bypasses the pools or rejects outright, and
		// EVALUATE_ITEMS falls through to pool evaluation below.
		if (TeleportRestriction.isItemTeleportType(transport.getType()))
		{
			switch (TeleportationItemPolicy.verdict(teleportationItemSetting, transport.isConsumable()))
			{
				case BYPASS:
					return true;
				case REJECT:
					return false;
				default:
					break;
			}
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
			|| transportItems.isSatisfiedBy(ownedItems, TransportItems.CURRENCIES, currencyThreshold,
				unlocksFor(transport));
	}

	/**
	 * Whether the carried pool alone meets the transport's item requirements, ignoring
	 * the currency threshold — the player already owns the items, so nothing is spent.
	 */
	public boolean satisfiedByPlayer(Transport transport)
	{
		TransportItems transportItems = transport.getItemRequirements();
		return transportItems == null
			|| transportItems.isSatisfiedBy(carriedItems, TransportItems.CURRENCIES, Integer.MAX_VALUE,
				unlocksFor(transport));
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
		Set<Unlock> transportUnlocks = unlocksFor(transport);
		Map<Integer, Integer> carried = carriedWithBankPouch(transport, carriedItems, bankHas, bankPouchRunes,
			transportUnlocks);
		if (carried != carriedItems)
		{
			items.put(bankPouchId, 1L);
			bankItemIds.add(bankPouchId);
		}

		boolean fullySupplied = true;
		for (ItemRequirement req : transport.getItemRequirements().getRequirements())
		{
			if (playerSatisfies(req, carried, transportUnlocks))
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
	 * The pickup plan for a target's required items: {@link BankPickupPlan#items} holds one
	 * entry per item group the player doesn't already carry, keyed by the first bank-covering
	 * item id of the covering OR branch and valued at the shortfall quantity; each entry is a
	 * separate display phrase. {@link BankPickupPlan#bankItemIds} collects every covering bank
	 * item across all branches. Item groups the player carries and groups the bank cannot
	 * cover contribute nothing.
	 */
	public BankPickupPlan targetPickupPlan(TransportItems targetItems)
	{
		Map<Integer, Long> items = new LinkedHashMap<>();
		Set<Integer> bankItemIds = new HashSet<>();
		for (ItemRequirement group : targetItems.getRequirements())
		{
			boolean carried = false;
			for (ItemRequirement.Branch branch : group.getBranches())
			{
				carried |= findCovering(branch.getItemIds(), branch.getQuantity(), carriedItems, Map.of()) != -1;
			}
			if (carried)
			{
				continue;
			}
			boolean phrased = false;
			for (ItemRequirement.Branch branch : group.getBranches())
			{
				int foundId = findCovering(branch.getItemIds(), branch.getQuantity(), carriedItems, bankHas);
				if (foundId == -1)
				{
					continue;
				}
				addCovering(branch.getItemIds(), branch.getQuantity(), carriedItems, bankHas, bankItemIds);
				if (!phrased)
				{
					items.put(foundId, (long) branch.getQuantity() - carriedItems.getOrDefault(foundId, 0));
					phrased = true;
				}
			}
		}
		return new BankPickupPlan(items, bankItemIds);
	}

	/**
	 * The result of {@link #bankPickupPlan}, {@link #fairyStaffPickup} or
	 * {@link #targetPickupPlan}: the pickup items (id to quantity; {@code null} when the
	 * bank cannot fully supply the transport) and the bank item ids to highlight.
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
