package shortestpath.transport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.transport.requirement.ItemRequirement;

/**
 * Determines what items need to be picked up from the bank for a given path.
 * This handles transport-specific requirements like the Dramen staff for fairy rings.
 * Multiple transports that connect the same edge are treated as alternatives (OR):
 * if the player can satisfy any one of them, no pickup is needed; otherwise the
 * cheapest single alternative that can be filled from the bank is chosen.
 */
@SuppressWarnings("unused") // Only static methods are used, incorrectly flagged
public final class BankPickupRequirements
{

	/** Combined result of a bank pickup computation: display phrases and item IDs for highlighting. */
	public static class BankPickupResult
	{
		public final List<String> phrases;
		public final Set<Integer> bankItemIds;

		BankPickupResult(List<String> phrases, Set<Integer> bankItemIds)
		{
			this.phrases = phrases;
			this.bankItemIds = bankItemIds;
		}

		/**
		 * Computes the bank pickup requirements for a given bank step in the path.
		 * Returns both the human-readable pickup phrases (for display) and the item IDs
		 * (for bank slot highlighting) in a single pass over the remaining path.
		 *
		 * @param client           The game client
		 * @param bank             The bank ItemContainer
		 * @param pathfinderConfig The pathfinder config for bank-aware transport lookups
		 * @param bankLocations    Set of bank location coordinates
		 * @param path             The current path
		 * @param pathIndex        The current step index in the path
		 * @return BankPickupResult with phrases and item IDs, or an empty result if not at a bank step
		 */
		public static BankPickupResult compute(
			Client client,
			ItemContainer bank,
			PathfinderConfig pathfinderConfig,
			Set<Integer> bankLocations,
			List<PathStep> path,
			int pathIndex)
		{
			List<String> resultPhrases = new ArrayList<>();
			Set<Integer> resultIds = new HashSet<>();

			if (bank == null || path == null || pathIndex < 0 || pathIndex >= path.size())
			{
				return new BankPickupResult(resultPhrases, resultIds);
			}

			// Only compute for bank steps.
			int currentPoint = path.get(pathIndex).getPackedPosition();
			if (!bankLocations.contains(currentPoint))
			{
				return new BankPickupResult(resultPhrases, resultIds);
			}

			// Snapshot bank contents.
			Map<Integer, Integer> bankHas = new HashMap<>();
			OwnedItems.addContainer(bankHas, bank);

			// Runes in a rune pouch sitting in the bank, as rune id to amount.
			int bankPouchId = BankPickupRequirements.findBankPouch(bankHas);
			Map<Integer, Integer> bankPouchRunes = bankPouchId == -1
				? Map.of()
				: OwnedItems.runePouchContents(client);

			// Snapshot what the player already has (inventory + equipment + rune pouch in hand).
			Map<Integer, Integer> playerHas = BankPickupRequirements.collectPlayerItems(client);

			// Each entry is one edge's pickup phrase, e.g. "Air rune (3), Law rune or Varrock teleport".
			LinkedHashSet<String> phrases = new LinkedHashSet<>();
			Set<Integer> itemIds = new HashSet<>();
			boolean usesFairyRing = false;

			// Walk each edge in the remaining path in a single pass.
			for (int i = pathIndex; i < path.size() - 1; i++)
			{
				int stepPoint = path.get(i).getPackedPosition();
				int nextPoint = path.get(i + 1).getPackedPosition();
				boolean banked = path.get(i + 1).isBankVisited();
				TransportAvailability availability = pathfinderConfig.getTransportAvailability(banked);

				List<Transport> edgeAlternatives = new ArrayList<>();
				for (Transport t : availability.getTransportsAt(stepPoint))
				{
					if (t.getDestination() == nextPoint)
					{
						edgeAlternatives.add(t);
					}
				}
				for (Transport t : availability.getUsableTeleports())
				{
					if (t.getDestination() == nextPoint)
					{
						edgeAlternatives.add(t);
					}
				}
				if (edgeAlternatives.isEmpty())
				{
					continue;
				}

				// Fairy rings handled once globally via the staff requirement below.
				List<Transport> nonFairy = new ArrayList<>();
				for (Transport t : edgeAlternatives)
				{
					if (TransportType.FAIRY_RING.equals(t.getType()))
					{
						usesFairyRing = true;
					}
					else
					{
						nonFairy.add(t);
					}
				}
				if (nonFairy.isEmpty())
				{
					continue;
				}

				// If at least one alternative requires no items, nothing to pick up for this edge.
				boolean anyAlternativeIsFree = false;
				for (Transport t : nonFairy)
				{
					if (t.getItemRequirements() == null || t.getItemRequirements().size() == 0)
					{
						anyAlternativeIsFree = true;
						break;
					}
				}
				if (anyAlternativeIsFree)
				{
					continue;
				}

				// If any alternative is fully satisfied by the player already, no pickup needed.
				boolean satisfied = false;
				for (Transport t : nonFairy)
				{
					if (BankPickupRequirements.transportSatisfiedBy(t, playerHas))
					{
						satisfied = true;
						break;
					}
				}
				if (satisfied)
				{
					continue;
				}

				// Build phrases (alternatives the bank can fully supply) and collect item IDs in one pass.
				LinkedHashSet<String> altStrings = new LinkedHashSet<>();
				for (Transport t : nonFairy)
				{
					Map<Integer, Long> pickups = BankPickupRequirements.computeBankPickups(
						t, playerHas, bankHas, bankPouchId, bankPouchRunes);
					if (pickups != null && !pickups.isEmpty())
					{
						// Bank can fully satisfy this alternative: contribute to display phrase.
						altStrings.add(BankPickupRequirements.formatPickups(client, pickups));
					}
					// Always collect actual bank item IDs for highlighting.
					// computeBankPickups uses canonical IDs (e.g. air rune) for display, but the bank
					// may only hold a variant (e.g. mist rune), so we resolve the real ID separately.
					BankPickupRequirements.collectPartialBankItemIds(
						t, playerHas, bankHas, bankPouchId, bankPouchRunes, itemIds);
				}
				if (!altStrings.isEmpty())
				{
					phrases.add(String.join(" or ", altStrings));
				}
			}

			// Fairy ring staff (Dramen / Lunar) is a single OR requirement across the whole trip.
			// Not needed if the Lumbridge Elite diary is complete.
			if (usesFairyRing && client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1)
			{
				int[] staffIds = ItemVariations.DRAMEN_STAFF.getIds();
				if (BankPickupRequirements.findCovering(staffIds, 1, Map.of(), playerHas) == -1)
				{
					// Use the canonical ID (Dramen staff) for the display phrase.
					if (BankPickupRequirements.findCovering(staffIds, 1, Map.of(), bankHas) != -1)
					{
						Map<Integer, Long> single = new LinkedHashMap<>();
						single.put(staffIds[0], 1L);
						phrases.add(BankPickupRequirements.formatPickups(client, single));
						// Highlight all staff variants present in the bank.
						BankPickupRequirements.addCovering(staffIds, 1, Map.of(), bankHas, itemIds);
					}
				}
			}

			resultPhrases.addAll(phrases);
			resultIds.addAll(itemIds);
			return new BankPickupResult(resultPhrases, resultIds);
		}
	}

	/**
	 * Collects item IDs from the bank that partially satisfy a transport's requirements.
	 * Called when the bank cannot fully satisfy the transport (so no phrase is generated),
	 * but we still want to highlight any relevant items the bank does have.
	 */
	static void collectPartialBankItemIds(Transport transport,
		Map<Integer, Integer> playerHas,
		Map<Integer, Integer> bankHas,
		int bankPouchId,
		Map<Integer, Integer> bankPouchRunes,
		Set<Integer> itemIds)
	{
		if (transport.getItemRequirements() == null)
		{
			return;
		}
		// Add the bank rune pouch if it is taken; its runes then count as carried.
		Map<Integer, Integer> carried = carriedWithBankPouch(transport, playerHas, bankHas, bankPouchRunes);
		if (carried != playerHas)
		{
			itemIds.add(bankPouchId);
		}
		for (ItemRequirement req : transport.getItemRequirements().getRequirements())
		{
			if (playerSatisfies(req, carried))
			{
				continue;
			}
			// Add all item variants (e.g. mist/dust/smoke rune for air rune) that cover the
			// shortfall of any OR branch, at that branch's quantity.
			for (ItemRequirement.Branch branch : req.getBranches())
			{
				addCovering(branch.getItemIds(), pickupQuantity(branch), carried, bankHas, itemIds);
			}
			// Add all staff variants (e.g. all air battlestaff types) present in bank.
			addCovering(req.getStaffIds(), 1, Map.of(), bankHas, itemIds);
			// Add all offhand variants (e.g. tome of fire) present in bank.
			addCovering(req.getOffhandIds(), 1, Map.of(), bankHas, itemIds);
		}
	}

	/**
	 * Formats a pickup map (item id to quantity) as a comma-separated, human-readable string.
	 */
	static String formatPickups(Client client, Map<Integer, Long> pickups)
	{
		List<String> parts = new ArrayList<>(pickups.size());
		for (Map.Entry<Integer, Long> entry : pickups.entrySet())
		{
			int itemId = entry.getKey();
			long qty = entry.getValue();
			String itemName = client.getItemDefinition(itemId).getName();
			if (itemName == null || itemName.isEmpty() || "null".equals(itemName))
			{
				itemName = "Unknown item";
			}
			boolean isCurrency = PathfinderConfig.CURRENCIES.contains(itemId);
			if (isCurrency)
			{
				if (qty > 1)
				{
					itemName += " (" + String.format("%,d", qty) + ")";
				}
			}
			else
			{
				itemName = qty + " " + itemName;
			}
			parts.add(itemName);
		}
		return String.join(", ", parts);
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
	 * Returns true if the player already meets this requirement on its own, with enough of
	 * one item variant of any OR branch at that branch's quantity, or with a staff or
	 * offhand that substitutes for it.
	 */
	private static boolean playerSatisfies(ItemRequirement req, Map<Integer, Integer> playerHas)
	{
		for (ItemRequirement.Branch branch : req.getBranches())
		{
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
	 * Returns true if every requirement of the transport is already met by the player's
	 * inventory/equipment/rune-pouch (taking item-id, staff and offhand variations into account).
	 */
	public static boolean transportSatisfiedBy(Transport transport, Map<Integer, Integer> playerHas)
	{
		if (transport.getItemRequirements() == null)
		{
			return true;
		}
		return transport.getItemRequirements().isSatisfiedBy(
			playerHas, PathfinderConfig.CURRENCIES, Integer.MAX_VALUE);
	}

	/**
	 * For an unsatisfied transport, returns the items (id to qty) that need to be picked up
	 * from the bank to satisfy it, or null if the bank can't supply them.
	 * Each counted item asks only for the shortfall: the required quantity less what the
	 * player already carries of that item ID, per OR branch at that branch's quantity.
	 * Loose items are chosen from the variants the player already carries first, across all
	 * OR branches, then from all variants in branch declaration order. The displayed item ID
	 * comes from the branch actually picked.
	 * When the bank rune pouch is taken (see {@link #carriedWithBankPouch}), the pouch itself is
	 * returned as a pickup item (qty 1), and its runes count as carried for every requirement.
	 */
	static Map<Integer, Long> computeBankPickups(Transport transport,
		Map<Integer, Integer> playerHas,
		Map<Integer, Integer> bankHas,
		int bankPouchId,
		Map<Integer, Integer> bankPouchRunes)
	{
		Map<Integer, Long> pickups = new LinkedHashMap<>();
		if (transport.getItemRequirements() == null)
		{
			return pickups;
		}
		// Prefer bank rune pouch over individual runes. This avoids surfacing combination
		// rune variants (mist, dust, etc.) when the pouch already covers the requirement.
		Map<Integer, Integer> carried = carriedWithBankPouch(transport, playerHas, bankHas, bankPouchRunes);
		if (carried != playerHas)
		{
			pickups.put(bankPouchId, 1L);
		}
		for (ItemRequirement req : transport.getItemRequirements().getRequirements())
		{
			if (playerSatisfies(req, carried))
			{
				continue;
			}
			// Try to satisfy from bank directly. First top up a variant the player already carries,
			// across all OR branches, which saves a slot, then fall back to the branches in
			// declaration order (pure item variant first within each branch).
			// Use the canonical (first) item ID of the chosen branch for display so we show
			// "Air rune" rather than a combination rune variant like "Mist rune", unless the
			// player carries some of the chosen variant, which only that variant tops up.
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
				pickups.merge(displayId, (long) (pickupQuantity(chosen) - carriedQty), Long::sum);
				continue;
			}
			foundId = findCovering(req.getStaffIds(), 1, Map.of(), bankHas);
			if (foundId == -1)
			{
				foundId = findCovering(req.getOffhandIds(), 1, Map.of(), bankHas);
			}
			if (foundId == -1)
			{
				return null; // bank can't satisfy this requirement
			}
			pickups.merge(foundId, 1L, Long::sum);
		}
		return pickups;
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
		Map<Integer, Integer> bankPouchRunes)
	{
		// Pouch runes plus loose bank items, per item ID.
		Map<Integer, Integer> pouchAndBank = new HashMap<>(bankHas);
		bankPouchRunes.forEach((runeId, amount) -> pouchAndBank.merge(runeId, amount, Integer::sum));
		for (ItemRequirement req : transport.getItemRequirements().getRequirements())
		{
			if (playerSatisfies(req, playerHas))
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

	/**
	 * Returns the ID of the first rune pouch found in the bank, or -1 if there is none.
	 */
	private static int findBankPouch(Map<Integer, Integer> bankHas)
	{
		for (int pouchId : PathfinderConfig.RUNE_POUCHES)
		{
			if (bankHas.containsKey(pouchId))
			{
				return pouchId;
			}
		}
		return -1;
	}

	/**
	 * Snapshots what the player already has on them (inventory + equipment + rune pouch
	 * contents if the pouch is in inventory or equipped).
	 */
	public static Map<Integer, Integer> collectPlayerItems(Client client)
	{
		Map<Integer, Integer> totals = new HashMap<>();
		OwnedItems.addContainer(totals, client.getItemContainer(InventoryID.INV));
		OwnedItems.addContainer(totals, client.getItemContainer(InventoryID.WORN));
		OwnedItems.addRunePouchContents(client, totals);
		return totals;
	}
}
