package shortestpath.pathfinder.sailing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.runelite.api.Quest;
import shortestpath.WorldPointUtil;
import shortestpath.requirement.model.DestinationRequirements;

/**
 * A mooring point: one place where a player boards their own boat or leaves it, the boundary between walking and
 * sailing. Ports have a gangplank, islands a mooring point; both work the same way here.
 * <p>
 * The walking side is the land tile the player uses the gangplank from; the sailing side is the berth, the dock tile
 * the boat is moored at. A berth is blocked, so it is never a boat state: boarding puts the boat anywhere within
 * {@link #DOCKING_RANGE} of it that the boat can be ({@link SailingWaters#castOff}), and docking takes it from
 * anywhere in that range.
 */
@Getter
@Accessors(fluent = true)
public final class SailingMooring
{
	/**
	 * Ticks to board a moored boat and cast off: an estimate, the GPS plugin's 20-tick boarding and docking overhead
	 * less its {@link #DISEMBARK_TICKS}.
	 */
	public static final int BOARD_TICKS = 12;
	/** Ticks to dock and step off: an estimate, the GPS plugin's figure for mooring and stepping off. */
	public static final int DISEMBARK_TICKS = 8;
	/**
	 * How far from a berth the boat's tile may be along each axis, in tiles, to dock there or cast off: a square box,
	 * 17 tiles across. An approximation of the docking area the game's dock buoys mark round a berth, from 13 to about
	 * 20 tiles across where it is marked.
	 */
	public static final int DOCKING_RANGE = 8;
	/** The furthest a tile in docking range is from its berth in a straight line, in tiles. */
	public static final double DOCKING_REACH = DOCKING_RANGE * Math.sqrt(2);

	/** Index in {@link SailingMoorings#all()}: the mooring's identity within one data set. */
	private final int id;
	private final String name;
	private final int landTile;
	private final int berthTile;
	/** Skill levels and finished quests docking needs. */
	private final DestinationRequirements requirements;
	/** Quests docking needs started, finished or not. */
	private final Set<Quest> questsStarted;
	/** The boats that fit; every boat when the source names none. */
	private final Set<SailingVessel.Kind> vessels;
	/** The game's id for the dock, which a boat's port varbit holds; -1 if unknown. */
	private final int dock;
	/** Whether a shipwright here can bring the player's boat from wherever it is. */
	private final boolean shipwright;

	public SailingMooring(int id, String name, int landTile, int berthTile, DestinationRequirements requirements,
		Set<Quest> questsStarted, Set<SailingVessel.Kind> vessels, int dock, boolean shipwright)
	{
		this.id = id;
		this.name = name;
		this.landTile = landTile;
		this.berthTile = berthTile;
		this.requirements = requirements == null ? DestinationRequirements.EMPTY : requirements;
		this.questsStarted = questsStarted == null || questsStarted.isEmpty() ? Collections.emptySet()
			: Collections.unmodifiableSet(EnumSet.copyOf(questsStarted));
		this.vessels = vessels == null || vessels.isEmpty()
			? Collections.unmodifiableSet(EnumSet.allOf(SailingVessel.Kind.class))
			: Collections.unmodifiableSet(EnumSet.copyOf(vessels));
		this.dock = dock;
		this.shipwright = shipwright;
	}

	/** Whether {@code vessel} fits this mooring. */
	public boolean fits(SailingVessel vessel)
	{
		return vessels.contains(vessel.kind());
	}

	/** Whether a boat on {@code boatTile} is close enough to its berth to dock. */
	public boolean inDockingRange(int boatTile)
	{
		return WorldPointUtil.unpackWorldPlane(boatTile) == WorldPointUtil.unpackWorldPlane(berthTile)
			&& Math.abs(WorldPointUtil.unpackWorldX(boatTile) - WorldPointUtil.unpackWorldX(berthTile)) <= DOCKING_RANGE
			&& Math.abs(WorldPointUtil.unpackWorldY(boatTile) - WorldPointUtil.unpackWorldY(berthTile)) <= DOCKING_RANGE;
	}

	/** The tiles within docking range of the berth: where a boat can be to dock here. */
	public int[] dockingTiles()
	{
		int x = WorldPointUtil.unpackWorldX(berthTile), y = WorldPointUtil.unpackWorldY(berthTile), z = WorldPointUtil.unpackWorldPlane(berthTile);
		int[] tiles = new int[(2 * DOCKING_RANGE + 1) * (2 * DOCKING_RANGE + 1)];
		int k = 0;
		for (int dx = -DOCKING_RANGE; dx <= DOCKING_RANGE; dx++)
		{
			for (int dy = -DOCKING_RANGE; dy <= DOCKING_RANGE; dy++) tiles[k++] = WorldPointUtil.packWorldPoint(x + dx, y + dy, z);
		}
		return tiles;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
