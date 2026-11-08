package shortestpath.sailing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * One boat the player owns, as its slot's varbits describe it: what kind it is, the dock it is moored at, the
 * restricted sea areas its parts let it enter and its base speed. Plain values only, like {@link SailingState}.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class SailingBoat
{
	/** The game's boat types the router sails, as a slot's type varbit and the sailing_boat table number them. */
	public static final int RAFT = 0, SKIFF = 1, SLOOP = 2;

	/** The boat's slot, 1 to 5, as the game numbers them. */
	private final int slot;
	/** The game's boat type: {@link #RAFT}, {@link #SKIFF} or {@link #SLOOP}, or one the router doesn't sail. */
	private final int type;
	/** The game's id of the dock the boat is moored at. */
	private final int dock;
	private final Set<SeaArea> seaAreas;
	/** Base speed in 1/128ths of a tile per tick, from the hull. */
	private final int baseSpeed;
	/** Coins to recover this boat, or -1 when its price is unknown. */
	private final int recoveryCost;

	public SailingBoat(int slot, int type, int dock, Set<SeaArea> seaAreas, int baseSpeed)
	{
		this(slot, type, dock, seaAreas, baseSpeed, -1);
	}

	public SailingBoat(int slot, int type, int dock, Set<SeaArea> seaAreas, int baseSpeed, int recoveryCost)
	{
		this.slot = slot;
		this.type = type;
		this.dock = dock;
		this.seaAreas = seaAreas.isEmpty() ? SeaArea.NONE : Collections.unmodifiableSet(EnumSet.copyOf(seaAreas));
		this.baseSpeed = baseSpeed;
		this.recoveryCost = recoveryCost;
	}
}
