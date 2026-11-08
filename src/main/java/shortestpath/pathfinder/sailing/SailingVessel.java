package shortestpath.pathfinder.sailing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.sailing.BoatSpeed;
import shortestpath.sailing.SailingBoat;
import shortestpath.sailing.SeaArea;

/**
 * One boat a route can sail: its kind, which sets its hull ({@link BoatHull}), its base speed in the game's units
 * ({@link BoatSpeed}), and the restricted sea areas its parts let it enter.
 */
@Getter
@Accessors(fluent = true)
public final class SailingVessel
{
	/** The boat kinds, smallest first. */
	public enum Kind
	{
		RAFT(SailingBoat.RAFT, 0, 0, 128, 384),
		SKIFF(SailingBoat.SKIFF, 0, 0, 256, 640),
		SLOOP(SailingBoat.SLOOP, 0, -256, 384, 1280);

		/** The game's number for the boat type, as a boat slot's type varbit and the sailing_boat table hold it. */
		private final int gameType;
		// The game's world-entity bounds, in local units: across, along (negative toward the bow), width and length
		private final int boundsX, boundsY, boundsWidth, boundsHeight;
		// How far the hull reaches from the centre of the boat's tile, facing any heading, in tiles
		private final double reach;

		Kind(int gameType, int boundsX, int boundsY, int boundsWidth, int boundsHeight)
		{
			this.gameType = gameType;
			this.boundsX = boundsX;
			this.boundsY = boundsY;
			this.boundsWidth = boundsWidth;
			this.boundsHeight = boundsHeight;
			this.reach = BoatHull.reach(boundsX, boundsY, boundsWidth, boundsHeight);
		}

		/** How far the hull reaches from the centre of the boat's tile, facing any heading, in tiles. */
		double reach()
		{
			return reach;
		}

		/** The kind the game's boat type {@code gameType} is, or {@code null} for a boat the router doesn't sail. */
		public static Kind ofGameType(int gameType)
		{
			for (Kind kind : values()) if (kind.gameType == gameType) return kind;
			return null;
		}

		/** The standard kind with exactly these bounds, or {@code null}. */
		public static Kind ofBounds(int boundsX, int boundsY, int boundsWidth, int boundsHeight)
		{
			for (Kind kind : values())
			{
				if (kind.boundsX == boundsX && kind.boundsY == boundsY && kind.boundsWidth == boundsWidth
					&& kind.boundsHeight == boundsHeight) return kind;
			}
			return null;
		}

		/**
		 * Whether the part for {@code area} can be fitted to a boat of this kind: a raft, by the game's boat tables,
		 * has no keel and no room for an inoculation station.
		 */
		public boolean fits(SeaArea area)
		{
			return this != RAFT || area != SeaArea.CRYSTALLINE && area != SeaArea.FETID;
		}
	}

	private final Kind kind;
	/** Base speed, in {@link BoatSpeed} units. */
	private final int speed;
	/** The restricted sea areas the boat's parts let it enter; open water it always may. */
	private final Set<SeaArea> areas;
	/** The moves this boat sails at its base speed. */
	private final SailingMoves moves;
	// The hull asked what it covers, made on first ask: asked every tick for a boat at sea
	private volatile BoatHull coverage;

	private SailingVessel(Kind kind, int speed, Set<SeaArea> areas, SailingMoves moves)
	{
		this.kind = kind;
		this.speed = speed;
		Set<SeaArea> fitted = EnumSet.noneOf(SeaArea.class);
		for (SeaArea area : areas)
		{
			if (kind.fits(area)) fitted.add(area);
		}
		this.areas = Collections.unmodifiableSet(fitted);
		this.moves = moves;
	}

	/**
	 * A boat of {@code kind} at base speed {@code speed} ({@link BoatSpeed} units), with no parts for the restricted
	 * areas.
	 */
	public static SailingVessel of(Kind kind, int speed)
	{
		return new SailingVessel(kind, speed, SeaArea.NONE, SailingMoves.forSpeed(BoatSpeed.tilesPerTick(speed)));
	}

	/**
	 * This boat fitted for {@code areas}: the restricted areas whose parts it has. A part its kind can't take is left
	 * out.
	 */
	public SailingVessel withAreas(Set<SeaArea> areas)
	{
		return new SailingVessel(kind, speed, areas, moves);
	}

	/** This boat with every part its kind can take. */
	public SailingVessel fullyFitted()
	{
		return withAreas(SeaArea.ALL);
	}

	/** Whether the boat may sail into {@code area}; {@code null} is open water. */
	public boolean canEnter(SeaArea area)
	{
		return area == null || areas.contains(area);
	}

	/** A new hull for one search. Hulls cache what they sweep, so each search gets its own. */
	public BoatHull hull()
	{
		return BoatHull.fromBounds(kind.boundsX, kind.boundsY, kind.boundsWidth, kind.boundsHeight);
	}

	/** Whether the boat's hull on {@code tile} facing {@code heading} (-1 any) covers {@code target}. */
	public boolean hullCovers(int target, int tile, int heading)
	{
		if (coverage == null) coverage = hull();
		return coverage.coversTile(target, tile, heading);
	}

	/** A lower bound on the ticks to sail {@code tiles} tiles, however it goes: at the boat's top speed, rounded down. */
	public int ticksToSail(double tiles)
	{
		return (int) Math.floor(Math.max(0, tiles) / moves.topSpeed());
	}

	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof SailingVessel)) return false;
		SailingVessel vessel = (SailingVessel) other;
		return kind == vessel.kind && speed == vessel.speed && areas.equals(vessel.areas);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(kind, speed, areas);
	}

	@Override
	public String toString()
	{
		return kind + "@" + BoatSpeed.tilesPerTick(speed) + (areas.isEmpty() ? "" : areas.toString());
	}
}
