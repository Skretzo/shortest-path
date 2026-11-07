package shortestpath.sailing;

import java.util.EnumSet;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldEntityConfig;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.DBTableID;
import shortestpath.WorldPointUtil;
import shortestpath.requirement.ClientPlayerStateSource;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;

/**
 * Pins the capture arm of the sailing producer: a refresh reads the four
 * sailing varbits through the handed {@link ClientPlayerStateSource} whenever
 * the client is logged in — never gated on the aboard flag — and resolves the
 * entity side (boat presence, hull bounds and config id, packed boat point,
 * in-tile pivot, target orientation) only when the service holds a client.
 * The publication contract the readers rely on — one immutable
 * {@link SailingState}, {@link SailingState#EMPTY} before the first refresh —
 * is what these tests guard. The event-arm cases pin the declare contract:
 * only the four sailing varbits re-capture, and only a material diff mints a
 * fact — untracked events, identical snapshots, positional-only movement and
 * detached instances all declare nothing.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SailingServiceTest
{
	/** A non-{@link WorldView#TOPLEVEL} world view id, i.e. "the player is on a boat". */
	private static final int BOAT_WORLDVIEW = 5;

	@Mock
	Client client;

	@Before
	public void before()
	{
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
	}

	private ClientPlayerStateSource source()
	{
		return new ClientPlayerStateSource(client);
	}

	private void stubVarbits(int boarded, int baseSpeed, int speedCap, int acceleration)
	{
		when(client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT)).thenReturn(boarded);
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED)).thenReturn(baseSpeed);
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDCAP)).thenReturn(speedCap);
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_ACCELERATION)).thenReturn(acceleration);
	}

	@Test
	public void eachOwnedBoatIsReadFromItsSlot()
	{
		stubVarbits(0, 0, 0, 0);
		// Slot 1: a skiff at dock 21 with an adamant keel, a mithril helm, wooden sails, the eternal brazier, an
		// inoculation station and a camphor hull
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_TYPE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_PORT)).thenReturn(21);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_KEEL)).thenReturn(4);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_STEERING)).thenReturn(3);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_BRAZIER)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_HOTSPOT_5)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_1_HULL)).thenReturn(4);
		// Slot 3: a raft at dock 0 with oak sails and a rune helm; slot 2 is not owned
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_3_OWNED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_3_SAIL)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_BOAT_3_STEERING)).thenReturn(5);
		SailingService service = SailingService.forTesting();

		service.refresh(source());

		assertEquals(List.of(
			new SailingBoat(1, 1, 21, EnumSet.of(SeaArea.CRYSTALLINE, SeaArea.NORTHERN, SeaArea.FETID), 320),
			new SailingBoat(3, 0, 0, EnumSet.of(SeaArea.TEMPOR_TANTRUM, SeaArea.KELP), 192)),
			service.getState().getBoats());
		assertEquals("not aboard", 0, service.getState().getBoardedSlot());
	}

	@Test
	public void recoveryFeeIncludesHullAndCapsizedOrBottledSurcharge()
	{
		when(client.getDBTableField(DBTableID.SailingBoat.Row.SAILING_BOAT_SKIFF,
			DBTableID.SailingBoat.COL_RETRIEVAL_COST, 0)).thenReturn(new Object[]{3750});
		when(client.getDBTableField(DBTableID.SailingBoat.Row.SAILING_BOAT_SKIFF,
			DBTableID.SailingBoat.COL_HULL_OPTION, 0)).thenReturn(new Object[]{100, 100, 100, 100, 102});
		when(client.getDBTableField(100, DBTableID.SailingBoatHull.COL_FACILITY_STATS, 0))
			.thenReturn(new Object[]{101});
		when(client.getDBTableField(101, DBTableID.SailingBoatFacilityStats.COL_BOAT_ADDITIONAL_RECOVERY_COST_PERCENTAGE, 0))
			.thenReturn(new Object[]{10});
		when(client.getDBTableField(102, DBTableID.SailingBoatHull.COL_FACILITY_STATS, 0))
			.thenReturn(new Object[]{103});
		when(client.getDBTableField(103, DBTableID.SailingBoatFacilityStats.COL_BOAT_ADDITIONAL_RECOVERY_COST_PERCENTAGE, 0))
			.thenReturn(new Object[]{50});

		assertEquals(4125, SailingService.recoveryCost(new ClientPlayerStateSource(client), 1, 0, 6));
		assertEquals(4125, SailingService.recoveryCost(new ClientPlayerStateSource(client), 1, 0, 253));
		assertEquals(6000, SailingService.recoveryCost(new ClientPlayerStateSource(client), 1, 0, 254));
		assertEquals(7875, SailingService.recoveryCost(new ClientPlayerStateSource(client), 1, 0, 255));
		assertEquals(5625, SailingService.recoveryCost(new ClientPlayerStateSource(client), 1, 4, 6));
		assertEquals(-1, SailingService.recoveryCost(new ClientPlayerStateSource(client), 3, 0, 6));
		assertEquals(-1, SailingService.recoveryCost(new ClientPlayerStateSource(client), 1, 99, 6));
		assertEquals(-1, SailingService.recoveryCost(new ClientPlayerStateSource(client), 0, 0, 6));
	}

	@Test
	public void theBoardedBoatEntersTheAreasItsPartsResist()
	{
		stubVarbits(1, 192, 384, 96);
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_FETIDWATER_RESISTANT)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_STORMRESISTANCE)).thenReturn(2);
		SailingService service = SailingService.forTesting();

		service.refresh(source());

		assertEquals(EnumSet.of(SeaArea.FETID, SeaArea.TEMPOR_TANTRUM), service.getState().getSeaAreas());
	}

	@Test
	public void aBoatWithoutPartsEntersNoRestrictedArea()
	{
		stubVarbits(1, 192, 384, 96);
		SailingService service = SailingService.forTesting();

		service.refresh(source());

		assertEquals(SeaArea.NONE, service.getState().getSeaAreas());
	}

	@Test
	public void theResistancesAreOnlyReadAboard()
	{
		stubVarbits(0, 192, 384, 96);
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_ICYSEAS_RESISTANT)).thenReturn(1);
		SailingService service = SailingService.forTesting();

		service.refresh(source());

		assertEquals(SeaArea.NONE, service.getState().getSeaAreas());
	}

	@Test
	public void detachedInstanceCapturesVarbitsButNoEntityState()
	{
		stubVarbits(1, 192, 384, 96);
		SailingService service = SailingService.forTesting();

		service.refresh(source());

		SailingState state = service.getState();
		assertTrue(state.isAboard());
		assertEquals(192, state.getBaseSpeed());
		assertEquals(384, state.getSpeedCap());
		assertEquals(96, state.getAcceleration());
		// No client on the service means no entity side at all, even though the
		// handed source is client-backed.
		assertFalse(state.hasBoat());
		assertFalse(state.hasBounds());
		assertEquals(WorldPointUtil.UNDEFINED, state.getBoatPoint());
		assertEquals(-1, state.getHullConfigId());
		assertEquals(-1, state.getHeading());
		assertEquals(0, state.getPivotX());
		assertEquals(0, state.getPivotY());
		verify(client, never()).getLocalPlayer();
	}

	@Test
	public void entityCaptureResolvesBoatBoundsPointPivotAndHeading()
	{
		stubVarbits(1, 192, 384, 96);

		Player player = mock(Player.class);
		WorldView boatView = mock(WorldView.class);
		WorldView top = mock(WorldView.class);
		@SuppressWarnings("unchecked")
		IndexedObjectSet<WorldEntity> entities = mock(IndexedObjectSet.class);
		WorldEntity boat = mock(WorldEntity.class);
		WorldEntityConfig bounds = mock(WorldEntityConfig.class);

		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldView()).thenReturn(boatView);
		when(boatView.getId()).thenReturn(BOAT_WORLDVIEW);
		when(client.getTopLevelWorldView()).thenReturn(top);
		doReturn(entities).when(top).worldEntities();
		when(entities.byIndex(BOAT_WORLDVIEW)).thenReturn(boat);

		when(bounds.getBoundsX()).thenReturn(1);
		when(bounds.getBoundsY()).thenReturn(2);
		when(bounds.getBoundsWidth()).thenReturn(3);
		when(bounds.getBoundsHeight()).thenReturn(2);
		when(bounds.getId()).thenReturn(42);
		when(boat.getConfig()).thenReturn(bounds);
		when(boat.getTargetOrientation()).thenReturn(7);

		// 80/40 sub-tile units inside a tile put the pivots at +16/-24 from its centre.
		LocalPoint boatLp = new LocalPoint(10 * 128 + 80, 20 * 128 + 40, BOAT_WORLDVIEW);
		when(boat.getTargetLocation()).thenReturn(boatLp);

		// The boat's world view, consulted by fromLocalInstance when packing the point.
		WorldView boatSceneView = mock(WorldView.class);
		when(client.getWorldView(BOAT_WORLDVIEW)).thenReturn(boatSceneView);
		when(boatSceneView.getPlane()).thenReturn(0);
		when(boatSceneView.isInstance()).thenReturn(false);
		when(boatSceneView.getBaseX()).thenReturn(3000);
		when(boatSceneView.getBaseY()).thenReturn(3100);

		SailingService service = SailingService.forTesting(client);
		service.refresh(source());

		SailingState state = service.getState();
		assertTrue(state.isAboard());
		assertEquals(192, state.getBaseSpeed());
		assertEquals(384, state.getSpeedCap());
		assertEquals(96, state.getAcceleration());
		assertTrue(state.hasBoat());
		assertTrue(state.hasBounds());
		assertEquals(1, state.getBoundsX());
		assertEquals(2, state.getBoundsY());
		assertEquals(3, state.getBoundsWidth());
		assertEquals(2, state.getBoundsHeight());
		assertEquals(42, state.getHullConfigId());
		assertEquals(WorldPointUtil.packWorldPoint(3010, 3120, 0), state.getBoatPoint());
		assertEquals(16, state.getPivotX());
		assertEquals(-24, state.getPivotY());
		assertEquals(7, state.getHeading());
	}

	@Test
	public void topLevelWorldViewCollapsesTheEntitySide()
	{
		stubVarbits(1, 192, 384, 96);

		Player player = mock(Player.class);
		WorldView top = mock(WorldView.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldView()).thenReturn(top);
		when(top.getId()).thenReturn(WorldView.TOPLEVEL);

		SailingService service = SailingService.forTesting(client);
		service.refresh(source());

		SailingState state = service.getState();
		assertTrue("the varbit side still reflects the source", state.isAboard());
		assertFalse("a top-level world view means no boat in the scene", state.hasBoat());
		assertFalse(state.hasBounds());
		assertEquals(WorldPointUtil.UNDEFINED, state.getBoatPoint());
		assertEquals(-1, state.getHeading());
	}

	@Test
	public void missingWorldViewCollapsesTheEntitySide()
	{
		stubVarbits(1, 192, 384, 96);

		// A live player whose world view is still being rebuilt (mid world
		// hop): the capture must collapse rather than throw.
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldView()).thenReturn(null);

		SailingService service = SailingService.forTesting(client);
		service.refresh(source());

		SailingState state = service.getState();
		assertTrue("the varbit side still reflects the source", state.isAboard());
		assertFalse(state.hasBoat());
		assertFalse(state.hasBounds());
		assertEquals(WorldPointUtil.UNDEFINED, state.getBoatPoint());
	}

	@Test
	public void missingTopLevelWorldViewCollapsesTheEntitySide()
	{
		stubVarbits(1, 192, 384, 96);

		Player player = mock(Player.class);
		WorldView boatView = mock(WorldView.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldView()).thenReturn(boatView);
		when(boatView.getId()).thenReturn(BOAT_WORLDVIEW);
		when(client.getTopLevelWorldView()).thenReturn(null);

		SailingService service = SailingService.forTesting(client);
		service.refresh(source());

		SailingState state = service.getState();
		assertTrue("the varbit side still reflects the source", state.isAboard());
		assertFalse(state.hasBoat());
		assertFalse(state.hasBounds());
		assertEquals(WorldPointUtil.UNDEFINED, state.getBoatPoint());
	}

	@Test
	public void loggedOutRefreshReadsNoVarbitsAndClearsThem()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		SailingService service = SailingService.forTesting(client);

		service.refresh(source());

		SailingState state = service.getState();
		assertFalse(state.isAboard());
		assertEquals(0, state.getBaseSpeed());
		assertEquals(0, state.getSpeedCap());
		assertEquals(0, state.getAcceleration());
		verify(client, never()).getVarbitValue(anyInt());
		// The entity side feeds nothing logged out, so it stays unopened —
		// the world-view graph is the piece a hop can leave half-built.
		verify(client, never()).getLocalPlayer();
		assertFalse(state.hasBoat());
		assertEquals(WorldPointUtil.UNDEFINED, state.getBoatPoint());
	}

	@Test
	public void stateBeforeAnyRefreshIsEmpty()
	{
		SailingService service = SailingService.forTesting();
		assertNotNull(service.getState());
		assertSame(SailingState.EMPTY, service.getState());
	}

	@Test
	public void resetRepublishesTheEmptySnapshot()
	{
		stubVarbits(1, 192, 384, 96);
		SailingService service = SailingService.forTesting(client);
		service.refresh(source());
		assertTrue(service.getState().isAboard());

		service.reset();

		assertSame(SailingState.EMPTY, service.getState());
	}

	@Test
	public void untrackedVarbitDeclaresNothing()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(client, coordinator);

		service.onVarbitChanged(VarbitID.FAIRY2_QUEENCURE_QUEST);

		verify(coordinator, never()).sailingChanged(any());
	}

	@Test
	public void trackedVarbitWithIdenticalSnapshotDeclaresNothing()
	{
		stubVarbits(1, 192, 384, 96);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(client, coordinator);
		service.refresh(source());

		// The re-capture produces an identical snapshot, so the event mints no fact.
		service.onVarbitChanged(VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDCAP);

		verify(coordinator, never()).sailingChanged(any());
	}

	@Test
	public void boardingAnotherPersonalBoatInvalidatesTheRoute()
	{
		stubVarbits(1, 192, 384, 96);
		when(client.getVarbitValue(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED)).thenReturn(1);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(client, coordinator);
		service.refresh(source());

		when(client.getVarbitValue(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED)).thenReturn(2);
		service.onVarbitChanged(VarbitID.SAILING_LAST_PERSONAL_BOAT_BOARDED);

		assertEquals(2, service.getState().getBoardedSlot());
		ArgumentCaptor<SailingChange> captor = ArgumentCaptor.forClass(SailingChange.class);
		verify(coordinator).sailingChanged(captor.capture());
		assertTrue(captor.getValue().getEffects().contains(Effect.ROUTE_INVALIDATING));
	}

	@Test
	public void boardingDeclaresARouteInvalidatingFact()
	{
		stubVarbits(0, 192, 384, 96);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(client, coordinator);
		service.refresh(source());

		// The varbit flips between captures; with no entity chain stubbed the
		// aboard flag is the only field that moves.
		when(client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT)).thenReturn(1);
		service.onVarbitChanged(VarbitID.SAILING_BOARDED_BOAT);

		ArgumentCaptor<SailingChange> captor = ArgumentCaptor.forClass(SailingChange.class);
		verify(coordinator).sailingChanged(captor.capture());
		SailingChange change = captor.getValue();
		assertEquals("varbit:" + VarbitID.SAILING_BOARDED_BOAT, change.getKey());
		assertTrue(change.getEffects().contains(Effect.ROUTE_INVALIDATING));
	}

	@Test
	public void fittingAPartDeclaresARouteInvalidatingFact()
	{
		stubVarbits(1, 192, 384, 96);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(client, coordinator);
		service.refresh(source());

		// The route keeps out of water the boat's parts don't let it into, so a new part is material.
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_CRYSTALFLECKED_RESISTANT)).thenReturn(1);
		service.onVarbitChanged(VarbitID.SAILING_SIDEPANEL_BOAT_CRYSTALFLECKED_RESISTANT);

		ArgumentCaptor<SailingChange> captor = ArgumentCaptor.forClass(SailingChange.class);
		verify(coordinator).sailingChanged(captor.capture());
		assertTrue(captor.getValue().getEffects().contains(Effect.ROUTE_INVALIDATING));
	}

	@Test
	public void baseSpeedChangeDeclaresARouteInvalidatingFact()
	{
		stubVarbits(1, 192, 384, 96);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(client, coordinator);
		service.refresh(source());

		// The route sails at the base speed, so a change there is material.
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED)).thenReturn(160);
		service.onVarbitChanged(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED);

		ArgumentCaptor<SailingChange> captor = ArgumentCaptor.forClass(SailingChange.class);
		verify(coordinator).sailingChanged(captor.capture());
		assertTrue(captor.getValue().getEffects().contains(Effect.ROUTE_INVALIDATING));
	}

	@Test
	public void unconsumedVarbitChangesDeclareNothing()
	{
		stubVarbits(1, 192, 384, 96);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(client, coordinator);
		service.refresh(source());

		// The speed cap and acceleration varbits are captured for snapshot
		// fidelity, but nothing routes on them — their changes must not
		// mint a restart.
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDCAP)).thenReturn(300);
		service.onVarbitChanged(VarbitID.SAILING_SIDEPANEL_BOAT_SPEEDCAP);
		when(client.getVarbitValue(VarbitID.SAILING_SIDEPANEL_BOAT_ACCELERATION)).thenReturn(64);
		service.onVarbitChanged(VarbitID.SAILING_SIDEPANEL_BOAT_ACCELERATION);

		verify(coordinator, never()).sailingChanged(any());
	}

	@Test
	public void boatMovementWithoutMaterialChangeDeclaresNothing()
	{
		stubVarbits(1, 192, 384, 96);

		Player player = mock(Player.class);
		WorldView boatView = mock(WorldView.class);
		WorldView top = mock(WorldView.class);
		@SuppressWarnings("unchecked")
		IndexedObjectSet<WorldEntity> entities = mock(IndexedObjectSet.class);
		WorldEntity boat = mock(WorldEntity.class);
		WorldEntityConfig bounds = mock(WorldEntityConfig.class);

		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldView()).thenReturn(boatView);
		when(boatView.getId()).thenReturn(BOAT_WORLDVIEW);
		when(client.getTopLevelWorldView()).thenReturn(top);
		doReturn(entities).when(top).worldEntities();
		when(entities.byIndex(BOAT_WORLDVIEW)).thenReturn(boat);

		when(bounds.getBoundsX()).thenReturn(1);
		when(bounds.getBoundsY()).thenReturn(2);
		when(bounds.getBoundsWidth()).thenReturn(3);
		when(bounds.getBoundsHeight()).thenReturn(2);
		when(bounds.getId()).thenReturn(42);
		when(boat.getConfig()).thenReturn(bounds);
		when(boat.getTargetOrientation()).thenReturn(7);

		when(boat.getTargetLocation()).thenReturn(new LocalPoint(10 * 128 + 80, 20 * 128 + 40, BOAT_WORLDVIEW));

		WorldView boatSceneView = mock(WorldView.class);
		when(client.getWorldView(BOAT_WORLDVIEW)).thenReturn(boatSceneView);
		when(boatSceneView.getPlane()).thenReturn(0);
		when(boatSceneView.isInstance()).thenReturn(false);
		when(boatSceneView.getBaseX()).thenReturn(3000);
		when(boatSceneView.getBaseY()).thenReturn(3100);

		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(client, coordinator);
		service.refresh(source());

		// The boat slides a tile: point and pivots move, nothing material does.
		when(boat.getTargetLocation()).thenReturn(new LocalPoint(11 * 128 + 80, 20 * 128 + 40, BOAT_WORLDVIEW));
		service.onVarbitChanged(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED);

		verify(coordinator, never()).sailingChanged(any());
	}

	@Test
	public void detachedInstanceIgnoresVarbitEvents()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SailingService service = SailingService.forTesting(coordinator);

		service.onVarbitChanged(VarbitID.SAILING_BOARDED_BOAT);

		verify(coordinator, never()).sailingChanged(any());
	}
}
