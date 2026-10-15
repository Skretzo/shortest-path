package shortestpath.pathfinder;

import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import shortestpath.items.ItemStateService;
import shortestpath.presentation.BankPickupRequirements.BankPickupResult;
import shortestpath.requirement.model.TransportItems;

/**
 * The bank-pickup projection for the active path: which items a route's
 * target needs picked up when the path passes a bank. Lives on the engine
 * side — it reads path state and the item-state service — so the item-state
 * service never pulls path types in. Cached on
 * (item-state revision, path identity, step index, search identity): the
 * revision covers container/varbit events, the search identity covers
 * restarts that reuse a path, and a search still awaiting its result
 * recomputes each call since the target's items are not yet known.
 */
public class BankPickupProjection
{
	private BankPickupResult cache;
	private List<PathStep> cachePath;
	private int cacheIndex = -1;
	private ActiveSearch cacheSearch;
	private int cacheRevision = -1;
	private boolean cachePending;

	public BankPickupResult get(Client client, ItemStateService itemState, PathfinderConfig config,
		List<PathStep> path, int pathIndex, ActiveSearch search)
	{
		Set<Integer> bankLocations = config.getDestinations("bank");
		ItemContainer bank = itemState.getBank();
		if (bank == null || bankLocations == null
				|| path == null || pathIndex < 0 || pathIndex >= path.size())
		{
			return null;
		}
		PathfinderResult result = search == null ? null : search.getResult();
		// The target's items are only known once the search has its result, which appears just
		// after the final path: keep recomputing until then.
		boolean pending = search != null && result == null;
		if (!cachePending && !pending && itemState.itemStateRevision() == cacheRevision
			&& path == cachePath && pathIndex == cacheIndex && search == cacheSearch)
		{
			return cache;
		}
		cacheRevision = itemState.itemStateRevision();
		cachePath = path;
		cacheIndex = pathIndex;
		cacheSearch = search;
		cachePending = pending;
		cache = BankPickupResult.compute(
				client, bank, config, bankLocations, path, pathIndex,
				itemsToPickUp(result));
		return cache;
	}

	/** The items a route's target needs from the bank, or {@code null} when it picks none up. */
	private static TransportItems itemsToPickUp(PathfinderResult result)
	{
		Arrival arrival = result == null ? null : result.getArrival();
		return arrival != null && arrival.picksUpItemsFromBank() ? arrival.getTarget().getRequiredItems() : null;
	}
}
