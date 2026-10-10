package shortestpath.sailing;

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
	}

	@Test
	public void stateBeforeAnyRefreshIsEmpty()
	{
		SailingService service = SailingService.forTesting();
		assertNotNull(service.getState());
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

		when(boat.getTargetLocation()).thenReturn(
			new LocalPoint(10 * 128 + 80, 20 * 128 + 40, BOAT_WORLDVIEW));

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
		when(boat.getTargetLocation()).thenReturn(
			new LocalPoint(11 * 128 + 80, 20 * 128 + 40, BOAT_WORLDVIEW));
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
