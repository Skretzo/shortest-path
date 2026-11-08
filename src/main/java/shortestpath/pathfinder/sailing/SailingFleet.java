package shortestpath.pathfinder.sailing;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * What the router may assume about the player's boats, fixed when the route is asked for: the boats they can board on
 * foot and where, and the boat they are aboard, if any. Every fact comes from the account or the game, never from the
 * Sailing level. A boat stays where it was throughout a route.
 */
@Getter
@Accessors(fluent = true)
public final class SailingFleet
{
	/** No boats: routes walk. */
	public static final SailingFleet NONE = new SailingFleet(List.of(), null, Integer.MAX_VALUE);

	/** {@link Boat#boardingCost} for a mooring the boat can't be boarded at. */
	public static final int UNAVAILABLE = -1;

	/** One of the player's boats, and what boarding it at each mooring costs. */
	@Getter
	@Accessors(fluent = true)
	public static final class Boat
	{
		/** {@link #dock} for a boat moored at every mooring. */
		private static final int EVERYWHERE = -2;

		private final SailingVessel vessel;
		/** The game's id of the dock the boat is moored at, or {@link #EVERYWHERE}; matches no mooring if unknown. */
		private final int dock;
		/** Coins to have the boat brought to a shipwright's mooring, or {@link #UNAVAILABLE}. */
		private final int recoveryCost;

		private Boat(SailingVessel vessel, int dock, int recoveryCost)
		{
			this.vessel = vessel;
			this.dock = dock;
			this.recoveryCost = recoveryCost;
		}

		/**
		 * A boat moored at the dock with the game's id {@code dock}, which a shipwright can bring to their own mooring
		 * for {@code recoveryCost} coins ({@link #UNAVAILABLE} if not).
		 */
		public static Boat at(SailingVessel vessel, int dock, int recoveryCost)
		{
			return new Boat(vessel, dock, recoveryCost);
		}

		/**
		 * A boat moored at every mooring: a declared assumption for accounts whose boat positions are not modelled, as
		 * if the boat had been moved to the mooring the route boards at before setting off.
		 */
		public static Boat everywhere(SailingVessel vessel)
		{
			return new Boat(vessel, EVERYWHERE, UNAVAILABLE);
		}

		/** Coins to board the boat at {@code mooring}: 0 where it is moored, its recovery at a shipwright's, else {@link #UNAVAILABLE}. */
		public int boardingCost(SailingMooring mooring)
		{
			if (dock == EVERYWHERE || mooring.dock() >= 0 && mooring.dock() == dock) return 0;
			return mooring.shipwright() ? recoveryCost : UNAVAILABLE;
		}
	}

	/** The boat the player is aboard, where it is and which way it faces. */
	@Getter
	@Accessors(fluent = true)
	public static final class Aboard
	{
		private final int tile;
		/** One of the 16 headings (0 south, 8 north), or -1 when unknown. */
		private final int heading;
		private final SailingVessel vessel;

		public Aboard(int tile, int heading, SailingVessel vessel)
		{
			this.tile = tile;
			this.heading = heading;
			this.vessel = vessel;
		}
	}

	/** The boats the player owns and can board on foot. */
	private final List<Boat> boats;
	/** The boat the player is aboard, or {@code null}. */
	private final Aboard aboard;
	/** The coins the player carries, which pay for a recovery without a bank visit. */
	private final int carriedCoins;

	public SailingFleet(List<Boat> boats, Aboard aboard, int carriedCoins)
	{
		this.boats = List.copyOf(boats);
		this.aboard = aboard;
		this.carriedCoins = carriedCoins;
	}

	/** Each of {@code vessels} moored at every mooring ({@link Boat#everywhere}), none aboard. */
	public static SailingFleet everywhere(List<SailingVessel> vessels)
	{
		List<Boat> boats = new ArrayList<>();
		for (SailingVessel vessel : vessels) boats.add(Boat.everywhere(vessel));
		return new SailingFleet(boats, null, Integer.MAX_VALUE);
	}

	/** Aboard {@code aboard}, with no other boats. */
	public static SailingFleet aboard(Aboard aboard)
	{
		return new SailingFleet(List.of(), aboard, Integer.MAX_VALUE);
	}

	/** This fleet with the player on foot: no boat aboard. */
	public SailingFleet ashore()
	{
		return aboard == null ? this : new SailingFleet(boats, null, carriedCoins);
	}

	/** Whether a recovery costs more than the player carries, so a route may need to visit a bank to pay for it. */
	public boolean recoveryMayNeedBank()
	{
		return boats.stream().anyMatch(boat -> boat.recoveryCost() > carriedCoins);
	}
}
