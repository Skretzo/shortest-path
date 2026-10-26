package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ItemContainer;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.TransportItems;
import shortestpath.transport.Transport;

/**
 * The raw result of the bank-pickup analysis for a path: which pickup
 * requirements the bank can supply at the bank step, as structured data the
 * presentation layer formats into display phrases. Each entry of
 * {@link #pickupAlternatives} is one edge's OR-joined alternatives — a list of
 * item-id to quantity maps that display joins by "or" after formatting each
 * map into an item-name phrase.
 */
public class BankPickupAnalysis
{
	public final List<List<Map<Integer, Long>>> pickupAlternatives;
	public final Set<Integer> bankItemIds;

	public BankPickupAnalysis(List<List<Map<Integer, Long>>> pickupAlternatives, Set<Integer> bankItemIds)
	{
		this.pickupAlternatives = pickupAlternatives;
		this.bankItemIds = bankItemIds;
	}

	/**
	 * As {@link #analyze(ItemContainer, PathfinderConfig, Set, List, int, TransportItems)}
	 * with no target-item list.
	 */
	public static BankPickupAnalysis analyze(
		ItemContainer bank,
		PathfinderConfig pathfinderConfig,
		Set<Integer> bankLocations,
		List<PathStep> path,
		int pathIndex)
	{
		return analyze(bank, pathfinderConfig, bankLocations, path, pathIndex, null);
	}

	/**
	 * Walks the remaining path from {@code pathIndex} and collects, per edge whose
	 * item requirements the carried pool does not satisfy, the pickup maps the bank
	 * can fully supply (one map per usable alternative), plus every bank item id a
	 * satisfied plan touches for highlighting. {@code targetItems} — the items the
	 * path's target needs on arrival — are listed at the bank step where the path
	 * visits the bank.
	 */
	public static BankPickupAnalysis analyze(
		ItemContainer bank,
		PathfinderConfig pathfinderConfig,
		Set<Integer> bankLocations,
		List<PathStep> path,
		int pathIndex,
		TransportItems targetItems)
	{
		List<List<Map<Integer, Long>>> resultAlternatives = new ArrayList<>();
		Set<Integer> resultIds = new HashSet<>();

		if (bank == null || path == null || pathIndex < 0 || pathIndex >= path.size())
		{
			return new BankPickupAnalysis(resultAlternatives, resultIds);
		}

		// Only compute for bank steps.
		int currentPoint = path.get(pathIndex).getPackedPosition();
		if (!bankLocations.contains(currentPoint))
		{
			return new BankPickupAnalysis(resultAlternatives, resultIds);
		}

		// The eligibility snapshot captured at refresh time holds the carried/bank
		// pools and answers every item question below; without it there is nothing
		// to show.
		TransportEligibility eligibility = pathfinderConfig.getEligibility();
		if (eligibility == null)
		{
			return new BankPickupAnalysis(resultAlternatives, resultIds);
		}

		// Each group is one edge's pickup alternatives, e.g. the raw maps behind
		// "Air rune (3), Law rune or Varrock teleport".
		List<List<Map<Integer, Long>>> groups = new ArrayList<>();
		Set<Integer> itemIds = new HashSet<>();
		// Trip-level dedup: an identical pickup map emitted by more than one
		// edge — e.g. the fairy staff every ring now asks for — is shown once.
		Set<Map<Integer, Long>> seenPickups = new HashSet<>();

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

			// If at least one alternative requires no items, nothing to pick up for this edge.
			boolean anyAlternativeIsFree = false;
			for (Transport t : edgeAlternatives)
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
			for (Transport t : edgeAlternatives)
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

			// Collect the pickup maps of the alternatives the bank can fully
			// supply (display joins them by "or") and every bank item id any
			// plan touches — the plan's display ids are canonical (e.g. air
			// rune) while the bank may only hold a variant (e.g. mist rune).
			List<Map<Integer, Long>> supplyable = new ArrayList<>();
			for (Transport t : edgeAlternatives)
			{
				TransportEligibility.BankPickupPlan plan = eligibility.bankPickupPlan(t);
				if (plan.items != null && !plan.items.isEmpty() && seenPickups.add(plan.items))
				{
					supplyable.add(plan.items);
				}
				itemIds.addAll(plan.bankItemIds);
			}
			if (!supplyable.isEmpty())
			{
				groups.add(supplyable);
			}
		}

		if (targetItems != null && visitsBankAt(path, pathIndex))
		{
			TransportEligibility.BankPickupPlan targetPlan = eligibility.targetPickupPlan(targetItems);
			for (Map.Entry<Integer, Long> entry : targetPlan.items.entrySet())
			{
				Map<Integer, Long> single = new LinkedHashMap<>();
				single.put(entry.getKey(), entry.getValue());
				groups.add(List.of(single));
			}
			itemIds.addAll(targetPlan.bankItemIds);
		}

		resultAlternatives.addAll(groups);
		resultIds.addAll(itemIds);
		return new BankPickupAnalysis(resultAlternatives, resultIds);
	}

	/**
	 * Whether the path switches into its banked layer at step {@code index}: on the step before
	 * the switch or the first banked step, which share the bank tile.
	 */
	public static boolean visitsBankAt(List<PathStep> path, int index)
	{
		boolean banked = path.get(index).getBankVisitState() == BankVisitState.BANKED;
		if (!banked)
		{
			return index + 1 < path.size() && path.get(index + 1).getBankVisitState() == BankVisitState.BANKED;
		}
		return index == 0 || !(path.get(index - 1).getBankVisitState() == BankVisitState.BANKED);
	}
}
