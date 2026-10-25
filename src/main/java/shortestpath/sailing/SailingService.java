package shortestpath.sailing;

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
 * the observation arm, re-capturing on the four sailing varbit events and
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
	 * Captures the sailing inputs into one immutable snapshot and publishes
	 * it. The varbit side reads all four sailing varbits whenever the source
	 * reports {@link GameState#LOGGED_IN} — deliberately not gated on the
	 * aboard flag, since the side-panel reads are unconditional today —
	 * and clears to zero otherwise, matching the existing reset. The entity
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

		publish(new SailingState(aboard, baseSpeed, speedCap, acceleration,
			hasBoat, hasBounds, boundsX, boundsY, boundsWidth, boundsHeight,
			hullConfigId, boatPoint, pivotX, pivotY, heading));
	}

	/**
	 * Consumes a varbit event: one of the four sailing varbits re-captures
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
			&& varbitId != VarbitID.SAILING_SIDEPANEL_BOAT_ACCELERATION)
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
