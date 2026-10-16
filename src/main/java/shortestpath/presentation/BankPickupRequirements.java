package shortestpath.presentation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import shortestpath.pathfinder.BankPickupAnalysis;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.requirement.model.TransportItems;

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
		 * Formats a raw {@link BankPickupAnalysis} for display: each group of
		 * alternative pickup maps becomes one phrase joined by " or ", with
		 * identical phrases collapsed (two edges can ask for the same items).
		 */
		public static BankPickupResult format(Client client, BankPickupAnalysis analysis)
		{
			if (analysis == null)
			{
				return null;
			}
			List<String> phrases = new ArrayList<>(new LinkedHashSet<>(
				analysis.pickupAlternatives.stream()
					.map(alternatives -> alternatives.stream()
						.map(items -> formatPickups(client, items))
						.collect(Collectors.joining(" or ")))
					.collect(Collectors.toList())));
			return new BankPickupResult(phrases, analysis.bankItemIds);
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
			return compute(client, bank, pathfinderConfig, bankLocations, path, pathIndex, null);
		}

		/**
		 * As {@link #compute(Client, ItemContainer, PathfinderConfig, Set, List, int)}, also
		 * listing {@code targetItems} — the items the path's target needs on arrival — at the
		 * bank step where the path visits the bank.
		 */
		public static BankPickupResult compute(
			Client client,
			ItemContainer bank,
			PathfinderConfig pathfinderConfig,
			Set<Integer> bankLocations,
			List<PathStep> path,
			int pathIndex,
			TransportItems targetItems)
		{
			return format(client, BankPickupAnalysis.analyze(
				bank, pathfinderConfig, bankLocations, path, pathIndex, targetItems));
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
