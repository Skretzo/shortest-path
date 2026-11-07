package shortestpath.sailing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldEntityConfig;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.DBTableID;
import shortestpath.WorldPointUtil;
import shortestpath.requirement.ClientPlayerStateSource;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;

/**
 * Owns the plugin's sailing state observed at refresh time: the boarded-boat
 * varbit, the side-panel speed varbits, and the boarded boat's entity-graph
 * facts (hull bounds and config id, packed boat point, in-tile pivot, target
 * orientation). {@link #refresh(PlayerStateSource)} captures all of it once
 * per engine refresh on the client thread and publishes a single immutable
 * {@link SailingState} through a volatile holder, so readers on other threads
 * never observe a torn snapshot.
 *
 * <p>The producer contract mirrors the other domain services: capture happens
 * at refresh, and change facts are minted at observation points and declared
 * to the injected {@link RefreshCoordinator} — {@link #onVarbitChanged} is
 * the observation arm, re-capturing on the sailing varbit events and
 * declaring a {@link SailingChange} only when the route-relevant fields
 * moved. Capture only ever reads; nothing here writes back to the
 * engine.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class SailingService
{
	private final Client client;
	private final RefreshCoordinator coordinator;

	/**
	 * The last published snapshot — {@link SailingState#EMPTY} until the first
	 * refresh, so {@link #getState()} never returns null. Written solely
	 * through {@link #publish} as a single volatile write of an immutable
	 * value; readers on the render and pathfinder threads see whole
	 * snapshots, never a torn field mix.
	 */
	@Getter private volatile SailingState state = SailingState.EMPTY;

	private SailingService()
	{
		this(null, null);
	}

	/**
	 * Test/harness seam: returns an instance detached from Guice. The
	 * production instance is the injected singleton. Without a client the
	 * detached instance resolves no entity state, so a refresh captures the
	 * varbit side of the handed source alone — the same reduced world the
	 * test harnesses run against today.
	 */
	public static SailingService forTesting()
	{
		return new SailingService();
	}

	/**
	 * Test/harness seam for callers that exercise the entity side of the
	 * capture: the handed client drives the boat lookup while the instance
	 * stays detached from the coordinator channel.
	 */
	public static SailingService forTesting(Client client)
	{
		return new SailingService(client, null);
	}

	/**
	 * Test seam for asserting change declares: the returned instance hands
	 * minted facts to the given (typically mocked) {@link RefreshCoordinator}.
	 */
	public static SailingService forTesting(RefreshCoordinator coordinator)
	{
		return new SailingService(null, coordinator);
	}

	/**
	 * Test seam for callers that need both sides wired: entity capture
	 * through the handed client and fact declares to the handed
	 * {@link RefreshCoordinator} channel.
	 */
	public static SailingService forTesting(Client client, RefreshCoordinator coordinator)
	{
		return new SailingService(client, coordinator);
	}

	/**
	 * Republishes {@link SailingState#EMPTY}. The service is a singleton on
	 * the plugin injector, which RuneLite reuses across shutDown/startUp
	 * cycles, so the shell calls this on shutdown to keep one session's
	 * snapshot from feeding the next enable before its first refresh
	 * publishes.
	 */
	public void reset()
	{
		publish(SailingState.EMPTY);
	}

	/**
	 * The side panel's resistance varbits, by the restricted sea area each lets the boarded boat enter: each is set
	 * when the boat has the part the area needs (an inoculation station, an adamant keel, an adamant helm, an eternal
	 * brazier). Tempor Tantrum's is a level, the mast and sails' tier; see {@link #STORM_RESISTANCE_TO_ENTER}.
	 */
	private static final Map<SeaArea, Integer> AREA_VARBITS = new EnumMap<>(Map.of(
		SeaArea.FETID, VarbitID.SAILING_SIDEPANEL_BOAT_FETIDWATER_RESISTANT,
		SeaArea.CRYSTALLINE, VarbitID.SAILING_SIDEPANEL_BOAT_CRYSTALFLECKED_RESISTANT,
		SeaArea.KELP, VarbitID.SAILING_SIDEPANEL_BOAT_TANGLEDKELP_RESISTANT,
		SeaArea.NORTHERN, VarbitID.SAILING_SIDEPANEL_BOAT_ICYSEAS_RESISTANT,
		SeaArea.TEMPOR_TANTRUM, VarbitID.SAILING_SIDEPANEL_BOAT_STORMRESISTANCE));
	/**
	 * The storm resistance a boat needs to sail Tempor Tantrum's stormy seas: the wiki asks for oak and linen sails or
	 * better, and the plain wooden ones are taken to resist nothing.
	 */
	static final int STORM_RESISTANCE_TO_ENTER = 1;

	/**
	 * Each boat slot's varbits, slot 1 first: whether it is owned, its type, the dock it is moored at, then the option
	 * fitted in each slot of the boat that decides where it may sail (see {@link #boat}).
	 */
	private static final int[][] SLOT_VARBITS = {
		{VarbitID.SAILING_BOAT_1_OWNED, VarbitID.SAILING_BOAT_1_TYPE, VarbitID.SAILING_BOAT_1_PORT, VarbitID.SAILING_BOAT_1_KEEL, VarbitID.SAILING_BOAT_1_HULL, VarbitID.SAILING_BOAT_1_SAIL, VarbitID.SAILING_BOAT_1_STEERING, VarbitID.SAILING_BOAT_1_BRAZIER, VarbitID.SAILING_BOAT_1_HOTSPOT_5, VarbitID.SAILING_BOAT_1_HOTSPOT_9},
		{VarbitID.SAILING_BOAT_2_OWNED, VarbitID.SAILING_BOAT_2_TYPE, VarbitID.SAILING_BOAT_2_PORT, VarbitID.SAILING_BOAT_2_KEEL, VarbitID.SAILING_BOAT_2_HULL, VarbitID.SAILING_BOAT_2_SAIL, VarbitID.SAILING_BOAT_2_STEERING, VarbitID.SAILING_BOAT_2_BRAZIER, VarbitID.SAILING_BOAT_2_HOTSPOT_5, VarbitID.SAILING_BOAT_2_HOTSPOT_9},
		{VarbitID.SAILING_BOAT_3_OWNED, VarbitID.SAILING_BOAT_3_TYPE, VarbitID.SAILING_BOAT_3_PORT, VarbitID.SAILING_BOAT_3_KEEL, VarbitID.SAILING_BOAT_3_HULL, VarbitID.SAILING_BOAT_3_SAIL, VarbitID.SAILING_BOAT_3_STEERING, VarbitID.SAILING_BOAT_3_BRAZIER, VarbitID.SAILING_BOAT_3_HOTSPOT_5, VarbitID.SAILING_BOAT_3_HOTSPOT_9},
		{VarbitID.SAILING_BOAT_4_OWNED, VarbitID.SAILING_BOAT_4_TYPE, VarbitID.SAILING_BOAT_4_PORT, VarbitID.SAILING_BOAT_4_KEEL, VarbitID.SAILING_BOAT_4_HULL, VarbitID.SAILING_BOAT_4_SAIL, VarbitID.SAILING_BOAT_4_STEERING, VarbitID.SAILING_BOAT_4_BRAZIER, VarbitID.SAILING_BOAT_4_HOTSPOT_5, VarbitID.SAILING_BOAT_4_HOTSPOT_9},
		{VarbitID.SAILING_BOAT_5_OWNED, VarbitID.SAILING_BOAT_5_TYPE, VarbitID.SAILING_BOAT_5_PORT, VarbitID.SAILING_BOAT_5_KEEL, VarbitID.SAILING_BOAT_5_HULL, VarbitID.SAILING_BOAT_5_SAIL, VarbitID.SAILING_BOAT_5_STEERING, VarbitID.SAILING_BOAT_5_BRAZIER, VarbitID.SAILING_BOAT_5_HOTSPOT_5, VarbitID.SAILING_BOAT_5_HOTSPOT_9}
	};
	private static final int OWNED = 0, TYPE = 1, PORT = 2, KEEL = 3, HULL = 4, SAIL = 5, STEERING = 6, BRAZIER = 7,
		HOTSPOT_5 = 8, HOTSPOT_9 = 9;
	/** The first keel and helm option, in each boat's option list, that resists crystals and kelp: adamant. */
	private static final int ADAMANT_OPTION = 4;
	/** A boat's port varbit when it isn't at a dock: it capsized, or it was bottled. */
	private static final int DOCK_CAPSIZED = 254, DOCK_BOTTLED = 255;

	/**
	 * The boats the player owns, from their slots' varbits. The fitted parts are option numbers in the boat type's
	 * lists in the cache (sailing_boat's keel, sail, steering and brazier options, from 0, and its hotspots' facility
	 * options, from 1 with 0 for none), which give the restricted areas:
	 * <ul>
	 * <li>crystal-flecked water: an adamant keel or better (option 4 on; a raft has no keel);</li>
	 * <li>tangled kelp: an adamant helm or better (steering option 4 on);</li>
	 * <li>Tempor Tantrum: oak mast and linen sails or better (sail option 1 on, storm resistance 1 or 2);</li>
	 * <li>the icy seas: the eternal brazier (brazier option 1);</li>
	 * <li>fetid water: the inoculation station, the only option of a skiff's hotspot 5 and a sloop's hotspot 9.</li>
	 * </ul>
	 */
	private static List<SailingBoat> boats(PlayerStateSource source)
	{
		List<SailingBoat> boats = new ArrayList<>();
		for (int slot = 1; slot <= SLOT_VARBITS.length; slot++)
		{
			int[] varbits = SLOT_VARBITS[slot - 1];
			if (source.varbit(varbits[OWNED]) == 0) continue;
			int[] values = new int[varbits.length];
			for (int i = 0; i < varbits.length; i++) values[i] = source.varbit(varbits[i]);
			boats.add(boat(source, slot, values));
		}
		return boats;
	}

	// One slot's boat from its varbits' values
	private static SailingBoat boat(PlayerStateSource source, int slot, int[] values)
	{
		int type = values[TYPE];
		Set<SeaArea> areas = EnumSet.noneOf(SeaArea.class);
		if (type != SailingBoat.RAFT && values[KEEL] >= ADAMANT_OPTION) areas.add(SeaArea.CRYSTALLINE);
		if (values[STEERING] >= ADAMANT_OPTION) areas.add(SeaArea.KELP);
		if (values[SAIL] >= 1) areas.add(SeaArea.TEMPOR_TANTRUM);
		if (values[BRAZIER] >= 1) areas.add(SeaArea.NORTHERN);
		if ((type == SailingBoat.SKIFF && values[HOTSPOT_5] == 1) || (type == SailingBoat.SLOOP && values[HOTSPOT_9] == 1))
			areas.add(SeaArea.FETID);
		int hull = values[HULL];
		return new SailingBoat(slot, type, values[PORT], areas, BoatSpeed.ofHull(hull),
			recoveryCost(source, type, hull, values[PORT]));
	}

	/** The game's fee for recovering a boat, by script 9090, or -1 when it can't be read. */
	static int recoveryCost(PlayerStateSource source, int type, int hull, int dock)
	{
		int[] rows = {DBTableID.SailingBoat.Row.SAILING_BOAT_RAFT,
			DBTableID.SailingBoat.Row.SAILING_BOAT_SKIFF,
			DBTableID.SailingBoat.Row.SAILING_BOAT_SLOOP};
		if (type < 0 || type >= rows.length || BoatSpeed.ofHull(hull) == 0) return -1;
		int row = rows[type];
		int base = dbInt(source, row, DBTableID.SailingBoat.COL_RETRIEVAL_COST, 0);
		int hullRow = dbInt(source, row, DBTableID.SailingBoat.COL_HULL_OPTION, hull);
		if (base <= 0 || hullRow < 0) return -1;
		int stats = dbInt(source, hullRow, DBTableID.SailingBoatHull.COL_FACILITY_STATS, 0);
		if (stats < 0) return -1;
		int percent = dbInt(source, stats,
			DBTableID.SailingBoatFacilityStats.COL_BOAT_ADDITIONAL_RECOVERY_COST_PERCENTAGE, 0);
		if (percent < 0) return -1;
		// A hull surcharge, plus half again if the boat capsized, or double if it was bottled
		long surcharge = dock == DOCK_CAPSIZED ? base / 2 : dock == DOCK_BOTTLED ? base : 0;
		long cost = base + (long) base * percent / 100 + surcharge;
		return cost > Integer.MAX_VALUE ? -1 : (int) cost;
	}

	private static int dbInt(PlayerStateSource source, int row, int column, int index)
	{
		Object[] values = source.dbTableField(row, column, 0);
		return values != null && index >= 0 && index < values.length && values[index] instanceof Integer
			? (Integer) values[index] : -1;
	}

	private static boolean isBoatVarbit(int varbitId)
	{
		if (varbitId == VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED) return true;
		for (int[] varbits : SLOT_VARBITS) for (int varbit : varbits) if (varbit == varbitId) return true;
		return false;
	}

	/** The restricted sea areas the boarded boat's parts let it enter, from the side panel's resistances. */
	static Set<SeaArea> seaAreas(PlayerStateSource source)
	{
		Set<SeaArea> areas = EnumSet.noneOf(SeaArea.class);
		AREA_VARBITS.forEach((area, varbit) ->
		{
			int needed = area == SeaArea.TEMPOR_TANTRUM ? STORM_RESISTANCE_TO_ENTER : 1;
			if (source.varbit(varbit) >= needed) areas.add(area);
		});
		return areas;
	}

	/**
	 * Captures the sailing inputs into one immutable snapshot and publishes
	 * it. The varbit side reads the sailing varbits whenever the source
	 * reports {@link GameState#LOGGED_IN} — deliberately not gated on the
	 * aboard flag, since the side-panel reads are unconditional today —
	 * and clears to zero otherwise, matching the existing reset; only the
	 * resistances are read aboard alone, as they describe the boarded boat.
	 * The entity
	 * side resolves the boat, its hull bounds, packed point, in-tile pivot
	 * and target orientation through the {@link WorldPointUtil} statics; it
	 * runs only while the source reports {@link GameState#LOGGED_IN}, since
	 * nothing reads the entity fields logged out and the world-view graph
	 * can be mid-rebuild on the far side of a hop. A null client (detached
	 * instance), a null local player, or a top-level world view collapses
	 * it to the no-boat values.
	 * <p>
	 * Client thread only, called from the engine's refresh.
	 */
	public void refresh(PlayerStateSource source)
	{
		source.checkOnClientThread();

		boolean loggedIn = GameState.LOGGED_IN.equals(source.gameState());
		boolean aboard = loggedIn && source.varbit(VarbitID.SAILING_BOARDED_BOAT) != 0;
		int baseSpeed = loggedIn ? source.varbit(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED) : 0;
		int speedCap = loggedIn ? source.varbit(VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDCAP) : 0;
		int acceleration = loggedIn ? source.varbit(VarbitID.SAILING_SIDEPANEL_BOAT_ACCELERATION) : 0;
		Set<SeaArea> seaAreas = aboard ? seaAreas(source) : SeaArea.NONE;
		List<SailingBoat> boats = loggedIn ? boats(source) : List.of();
		// The slot of the personal boat last boarded: the one aboard, while aboard, which is then not at its dock
		int boardedSlot = aboard ? source.varbit(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED) : 0;

		Player localPlayer = !loggedIn || client == null ? null : client.getLocalPlayer();
		WorldEntity boat = localPlayer == null ? null : WorldPointUtil.boat(client, localPlayer);
		LocalPoint boatLp = localPlayer == null ? null : WorldPointUtil.boatLocation(client, localPlayer);
		WorldEntityConfig bounds = boat == null ? null : boat.getConfig();

		boolean hasBoat = boat != null;
		boolean hasBounds = bounds != null;
		int boundsX = hasBounds ? bounds.getBoundsX() : 0;
		int boundsY = hasBounds ? bounds.getBoundsY() : 0;
		int boundsWidth = hasBounds ? bounds.getBoundsWidth() : 0;
		int boundsHeight = hasBounds ? bounds.getBoundsHeight() : 0;
		int hullConfigId = hasBounds ? bounds.getId() : -1;
		int boatPoint = boatLp == null ? WorldPointUtil.UNDEFINED
			: WorldPointUtil.fromLocalInstance(client, boatLp);
		// The game anchors a sailing search at the boat's tile centre; the pivot
		// is the boat's offset from it in local units (128ths of a tile).
		int pivotX = boatLp == null ? 0
			: (boatLp.getX() & (Perspective.LOCAL_TILE_SIZE - 1)) - Perspective.LOCAL_HALF_TILE_SIZE;
		int pivotY = boatLp == null ? 0
			: (boatLp.getY() & (Perspective.LOCAL_TILE_SIZE - 1)) - Perspective.LOCAL_HALF_TILE_SIZE;
		int heading = boat == null ? -1 : boat.getTargetOrientation();

		publish(new SailingState(aboard, baseSpeed, speedCap, acceleration, seaAreas, boats, boardedSlot,
			hasBoat, hasBounds, boundsX, boundsY, boundsWidth, boundsHeight,
			hullConfigId, boatPoint, pivotX, pivotY, heading));
	}

	/**
	 * Consumes a varbit event: one of the sailing varbits re-captures
	 * and publishes a fresh snapshot, then declares a {@link SailingChange}
	 * carrying {@link Effect#ROUTE_INVALIDATING} when the new state differs
	 * on any route-relevant field — boarding or disembarking changes whether
	 * the shown route is sail-able at all, and a hull or speed change alters
	 * what the route costs. Any other varbit is not sailing state and
	 * declares nothing. Positional boat movement is not material — the diff
	 * only flags changes the route consumes (aboard, the hull bounds, and
	 * base speed), so a boat sliding across its tile or a side-panel stat
	 * nothing routes on publishes freely without restarting the search.
	 * <p>
	 * Client thread only — the shell's varbit events arrive there.
	 */
	public void onVarbitChanged(int varbitId)
	{
		if (client == null)
		{
			return;
		}
		if (varbitId != VarbitID.SAILING_BOARDED_BOAT
			&& varbitId != VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED
			&& varbitId != VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDCAP
			&& varbitId != VarbitID.SAILING_SIDEPANEL_BOAT_ACCELERATION
			&& !AREA_VARBITS.containsValue(varbitId)
			&& !isBoatVarbit(varbitId))
		{
			return;
		}

		SailingState previous = state;
		refresh(new ClientPlayerStateSource(client));
		SailingChange change = previous.materiallyEquals(state)
			? null
			: new SailingChange("varbit:" + varbitId, Set.of(Effect.ROUTE_INVALIDATING));
		declare(change);
	}

	/**
	 * Hands an admitted fact to the refresh coordinator. Detached
	 * {@code forTesting} instances carry no coordinator, so the declare
	 * no-ops there; a null fact declares nothing, mirroring the return-null
	 * convention the shell's mapper used to guard.
	 */
	private void declare(SailingChange change)
	{
		if (change != null && coordinator != null)
		{
			coordinator.sailingChanged(change);
		}
	}

	/**
	 * Single-point publication of the captured snapshot: one volatile write
	 * of an immutable {@link SailingState}, so a reader on another thread can
	 * never observe a half-built state.
	 */
	private void publish(SailingState snapshot)
	{
		state = snapshot;
	}
}
