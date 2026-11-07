package shortestpath.pathfinder.sailing;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.astar.EstimateEdges;

/**
 * What the search's estimate knows of the sea: from each mooring's land tile to each other's, a bound on boarding a boat
 * of the fleet there, sailing it across and stepping off. The estimate only sees tiles and transports, so it sees
 * across the sea by these; the search itself sails the boat on the sea graph ({@link SeaSearch}).
 */
public final class SailingEstimates
{
	private SailingEstimates()
	{
	}

	/**
	 * The edges for {@code fleet} between {@code moorings}, those whose requirements the account meets: for each boat
	 * that can be boarded at the first, the cheapest of those alike, and might sail to the second, the shortest way on
	 * its kind's {@link SeaGraph} with every part fitted, at the boat's top speed. A boat a shipwright brings is in the
	 * banked layer, and also in the carried one when the player carries its coins.
	 */
	public static EstimateEdges edges(SailingFleet fleet, Collection<SailingMooring> moorings, SailingWaters waters,
		Map<SailingVessel.Kind, SeaGraph> graphs)
	{
		EstimateEdges.Builder edges = new EstimateEdges.Builder();
		for (SailingMooring from : moorings)
		{
			// The cheapest of each different boat to board here
			Map<SailingVessel, Integer> cheapest = new LinkedHashMap<>();
			for (SailingFleet.Boat boat : fleet.boats())
			{
				int coins = boat.boardingCost(from);
				if (coins == SailingFleet.UNAVAILABLE || !from.fits(boat.vessel())) continue;
				cheapest.merge(boat.vessel(), coins, Math::min);
			}
			for (Map.Entry<SailingVessel, Integer> boat : cheapest.entrySet())
			{
				SailingVessel vessel = boat.getKey();
				SeaGraph graph = graphs.get(vessel.kind());
				if (graph == null) continue;
				for (SailingMooring to : moorings)
				{
					float tiles = graph.legTiles(from, to);
					if (to == from || !Float.isFinite(tiles) || !waters.sails(vessel, from, to)) continue;
					int ticks = SailingMooring.BOARD_TICKS + (int) Math.floor(tiles / vessel.moves().topSpeed())
						+ SailingMooring.DISEMBARK_TICKS;
					if (boat.getValue() <= fleet.carriedCoins())
						edges.add(BankVisitState.CARRIED, from.landTile(), to.landTile(), ticks);
					edges.add(BankVisitState.BANKED, from.landTile(), to.landTile(), ticks);
				}
			}
		}
		return edges.build();
	}
}
