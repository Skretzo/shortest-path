package shortestpath.sailing;

import lombok.AccessLevel;
import lombok.Getter;

import shortestpath.WorldPointUtil;

/**
 * One immutable snapshot of the plugin's sailing state, captured on the client
 * thread and published as a single volatile reference so readers on other
 * threads never observe a torn mix of fields from different refreshes. The
 * varbit side carries the boarded-boat flag and the raw side-panel speed
 * values (in the game's own units); the entity side carries the boarded
 * boat's hull bounds, hull config id, packed world point, in-tile pivot, and
 * target orientation. Deliberately primitives only — no {@code Client},
 * {@code WorldEntity}, {@code LocalPoint}, or hull-type references — so the
 * sailing package never couples to the entity graph or the search engine.
 */
@Getter
public final class SailingState
{
	/**
	 * The never-refreshed snapshot: not aboard, no boat, no bounds, every
	 * numeric field at its documented empty value ({@code boatPoint} =
	 * {@link WorldPointUtil#UNDEFINED}, {@code hullConfigId} = -1,
	 * {@code heading} = -1, the rest 0).
	 */
	public static final SailingState EMPTY = new SailingState(
		false, 0, 0, 0, false, false, 0, 0, 0, 0, -1, WorldPointUtil.UNDEFINED, 0, 0, -1);

	/** Whether the boarded-boat varbit reported the player on a boat. */
	private final boolean aboard;
	/** Raw {@code SAILING_SIDEPANEL_BOAT_BASESPEED} varbit value, in 1/128ths of a tile per tick. */
	private final int baseSpeed;
	/** Raw {@code SAILING_SIDEPANEL_BOAT_SPEEDCAP} varbit value. */
	private final int speedCap;
	/** Raw {@code SAILING_SIDEPANEL_BOAT_ACCELERATION} varbit value. */
	private final int acceleration;
	/** Whether the entity-graph boat lookup resolved (the boat is in the scene). */
	@Getter(AccessLevel.NONE)
	private final boolean hasBoat;
	/** Whether the boat's entity config (hull bounds) resolved. */
	@Getter(AccessLevel.NONE)
	private final boolean hasBounds;
	private final int boundsX;
	private final int boundsY;
	private final int boundsWidth;
	private final int boundsHeight;
	/** The boat entity's config id, or -1 when no config resolved. */
	private final int hullConfigId;
	/** Packed world point of the boat's location, or {@link WorldPointUtil#UNDEFINED} when there is none. */
	private final int boatPoint;
	/** The boat's position within its tile in local units, signed from the tile centre; 0 when there is no boat point. */
	private final int pivotX;
	private final int pivotY;
	/** The boat's target orientation, or -1 when there is no boat. */
	private final int heading;

	SailingState(boolean aboard, int baseSpeed, int speedCap, int acceleration,
		boolean hasBoat, boolean hasBounds,
		int boundsX, int boundsY, int boundsWidth, int boundsHeight,
		int hullConfigId, int boatPoint, int pivotX, int pivotY, int heading)
	{
		this.aboard = aboard;
		this.baseSpeed = baseSpeed;
		this.speedCap = speedCap;
		this.acceleration = acceleration;
		this.hasBoat = hasBoat;
		this.hasBounds = hasBounds;
		this.boundsX = boundsX;
		this.boundsY = boundsY;
		this.boundsWidth = boundsWidth;
		this.boundsHeight = boundsHeight;
		this.hullConfigId = hullConfigId;
		this.boatPoint = boatPoint;
		this.pivotX = pivotX;
		this.pivotY = pivotY;
		this.heading = heading;
	}

	public boolean hasBoat()
	{
		return hasBoat;
	}

	public boolean hasBounds()
	{
		return hasBounds;
	}

	/**
	 * Whether two snapshots differ on a field the route actually consumes:
	 * {@code aboard} gates the sailing dispatch, the boat/bounds presence
	 * flags and the bounds feed the hull every move must fit, and
	 * {@code baseSpeed} prices the sailing moves. Positional fields
	 * ({@code boatPoint}, {@code pivotX}, {@code pivotY}, {@code heading})
	 * are deliberately excluded — ordinary boat movement never changes a
	 * route's sailing inputs — and so are the captured-but-unconsumed
	 * fields ({@code speedCap}, {@code acceleration},
	 * {@code hullConfigId}): the snapshot keeps them for fidelity, but no
	 * consumer reads them, so a diff there must never mint a change fact
	 * that would restart a search.
	 */
	boolean materiallyEquals(SailingState other)
	{
		return other != null
			&& aboard == other.aboard
			&& hasBoat == other.hasBoat
			&& hasBounds == other.hasBounds
			&& boundsX == other.boundsX
			&& boundsY == other.boundsY
			&& boundsWidth == other.boundsWidth
			&& boundsHeight == other.boundsHeight
			&& baseSpeed == other.baseSpeed;
	}
}
