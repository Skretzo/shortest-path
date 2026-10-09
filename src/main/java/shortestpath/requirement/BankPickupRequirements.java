package shortestpath.requirement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.requirement.model.TransportItems;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Determines what items need to be picked up from the bank for a given path.
 * Multiple transports that connect the same edge are treated as alternatives (OR):
 * if the player can satisfy any one of them, no pickup is needed; otherwise the
 * alternatives the bank can fully supply are shown joined by "or".
 * All item evaluation comes from the {@link TransportEligibility} snapshot captured
 * at refresh time — the same evaluation the pathfinder used — keyed by the transport
 * recorded on each path step rather than re-derived here.
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
		 * @param bank             The bank ItemContainer — only null-checked to gate
		 *                         "a bank is open"; the bank contents themselves come
		 *                         from the eligibility snapshot
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

			// The eligibility snapshot captured at refresh time holds the carried/bank
			// pools and answers every item question below; without it there is nothing
			// to show.
			TransportEligibility eligibility = pathfinderConfig.getEligibility();
			if (eligibility == null)
			{
				return new BankPickupResult(resultPhrases, resultIds);
			}

			// Each entry is one edge's pickup phrase, e.g. "Air rune (3), Law rune or Varrock teleport".
			LinkedHashSet<String> phrases = new LinkedHashSet<>();
			Set<Integer> itemIds = new HashSet<>();
			boolean usesFairyRing = false;

			// Walk each edge in the remaining path in a single pass.
			for (int i = pathIndex; i < path.size() - 1; i++)
			{
				int stepPoint = path.get(i).getPackedPosition();
				int nextPoint = path.get(i + 1).getPackedPosition();
				BankVisitState banked = path.get(i + 1).getBankVisitState();

				List<Transport> edgeAlternatives = new ArrayList<>();
				// When the step carries the transport the search actually used, that single
				// transport is the edge — destination-matched alternatives (including a
				// no-requirement transport sharing the destination) must not suppress the
				// pickup phrase or bank highlight for the transport the path takes.
				Transport usedTransport = path.get(i + 1).getTransport();
				if (usedTransport != null)
				{
					edgeAlternatives.add(usedTransport);
				}
				else
				{
					TransportAvailability availability = pathfinderConfig.getTransportAvailability(banked);
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
					if (eligibility.satisfiedByPlayer(t))
					{
						satisfied = true;
						break;
					}
				}
				if (satisfied)
				{
					continue;
				}

				// Build phrases (alternatives the bank can fully supply) and collect the
				// bank item IDs to highlight from the same per-transport plan.
				LinkedHashSet<String> altStrings = new LinkedHashSet<>();
				for (Transport t : nonFairy)
				{
					TransportEligibility.BankPickupPlan plan = eligibility.bankPickupPlan(t);
					if (plan.items != null && !plan.items.isEmpty())
					{
						// Bank can fully satisfy this alternative: contribute to display phrase.
						altStrings.add(BankPickupRequirements.formatPickups(client, plan.items));
					}
					// Always collect the actual bank item IDs for highlighting — the plan's
					// display IDs are canonical (e.g. air rune) while the bank may only hold
					// a variant (e.g. mist rune).
					itemIds.addAll(plan.bankItemIds);
				}
				if (!altStrings.isEmpty())
				{
					phrases.add(String.join(" or ", altStrings));
				}
			}

			// Fairy ring staff (Dramen / Lunar) is a single OR requirement across the whole trip.
			if (usesFairyRing)
			{
				TransportEligibility.BankPickupPlan staff = eligibility.fairyStaffPickup();
				if (staff != null)
				{
					phrases.add(BankPickupRequirements.formatPickups(client, staff.items));
					itemIds.addAll(staff.bankItemIds);
				}
			}

			resultPhrases.addAll(phrases);
			resultIds.addAll(itemIds);
			return new BankPickupResult(resultPhrases, resultIds);
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
			// A detached (forTesting) item-state service passes no client —
			// degrade to the placeholder name rather than throwing.
			String itemName = client == null ? null : client.getItemDefinition(itemId).getName();
			if (itemName == null || itemName.isEmpty() || "null".equals(itemName))
			{
				itemName = "Unknown item";
			}
			boolean isCurrency = TransportItems.CURRENCIES.contains(itemId);
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
}
