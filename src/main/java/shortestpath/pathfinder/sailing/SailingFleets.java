package shortestpath.pathfinder.sailing;

import java.util.ArrayList;
import java.util.List;
import shortestpath.WorldPointUtil;
import shortestpath.sailing.BoatSpeed;
import shortestpath.sailing.SailingBoat;
import shortestpath.sailing.SailingState;

/** The live plugin's fleet: the player's boats as the game shows them ({@link SailingState}). */
public final class SailingFleets
{
	/**
	 * The fastest a real boat's base speed can be: double the fastest hull, for headroom. A reading above it is a stale
	 * or mis-scaled varbit rather than a faster boat, so the estimate is used instead.
	 */
	private static final int MAX_SPEED = (int) (SailingMoves.MAX_SPEED * BoatSpeed.UNITS);

	private SailingFleets()
	{
	}

	/**
	 * The player's boats from {@code state}: each boat they own moored at its dock, with its own parts and its hull's
	 * base speed, and the boat they are aboard, if any, which is then not at its dock.
	 *
	 * @param recoveryLimit the most coins a route may spend having a shipwright bring a boat
	 * @param coinsOwned    the coins the player has, carried and banked: a recovery can cost no more
	 * @param coinsCarried  the coins the player carries: a dearer recovery needs a bank visit first
	 */
	public static SailingFleet fromState(SailingState state, int recoveryLimit, long coinsOwned, int coinsCarried)
	{
		List<SailingFleet.Boat> boats = new ArrayList<>();
		for (SailingBoat boat : state.getBoats())
		{
			SailingVessel.Kind kind = SailingVessel.Kind.ofGameType(boat.getType());
			if (kind == null || boat.getSlot() == state.getBoardedSlot()) continue;
			SailingVessel vessel = SailingVessel.of(kind, speed(boat.getBaseSpeed())).withAreas(boat.getSeaAreas());
			int cost = boat.getRecoveryCost();
			boolean recoverable = cost > 0 && cost <= recoveryLimit && cost <= coinsOwned;
			boats.add(SailingFleet.Boat.at(vessel, boat.getDock(), recoverable ? cost : SailingFleet.UNAVAILABLE));
		}
		return new SailingFleet(boats, aboard(state), coinsCarried);
	}

	/**
	 * The boat the player is aboard, or {@code null} unless it is in the scene and a kind of boat the router sails: by its
	 * world entity's bounds, or else by the boat they last boarded.
	 */
	static SailingFleet.Aboard aboard(SailingState state)
	{
		if (!state.isAboard() || !state.hasBoat() || state.getBoatPoint() == WorldPointUtil.UNDEFINED) return null;
		SailingVessel.Kind kind = aboardKind(state);
		if (kind == null) return null;
		// The restricted sea areas its parts let it enter, from the side panel's resistances
		SailingVessel vessel = SailingVessel.of(kind, speed(state.getBaseSpeed())).withAreas(state.getSeaAreas());
		// One of the 16 headings 128 angle units apart, from the boat's target orientation
		int heading = state.getHeading() < 0 ? -1 : Math.floorMod((state.getHeading() + 64) / 128, 16);
		return new SailingFleet.Aboard(state.getBoatPoint(), heading, vessel);
	}

	private static SailingVessel.Kind aboardKind(SailingState state)
	{
		SailingVessel.Kind kind = !state.hasBounds() ? null : SailingVessel.Kind.ofBounds(state.getBoundsX(),
			state.getBoundsY(), state.getBoundsWidth(), state.getBoundsHeight());
		if (kind != null) return kind;
		for (SailingBoat boat : state.getBoats())
		{
			if (boat.getSlot() == state.getBoardedSlot()) return SailingVessel.Kind.ofGameType(boat.getType());
		}
		return null;
	}

	/** A base speed read from the game, or the estimate when it is unread or out of range. */
	static int speed(int read)
	{
		return read > 0 && read <= MAX_SPEED ? read : BoatSpeed.ESTIMATED;
	}
}
