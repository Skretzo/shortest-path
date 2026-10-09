package shortestpath.spirittree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.mockito.ArgumentCaptor;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;
import org.junit.Before;
import org.junit.Test;

public class SpiritTreeServiceTest
{
	private SpiritTreeService state;

	@Before
	public void setUp()
	{
		state = new SpiritTreeService();
	}

	@Test
	public void grownTreeValueIsTravelable()
	{
		assertTrue(SpiritTreeService.spiritTreeTravelable(20));
	}

	@Test
	public void nonTwentyValuesAreNotTravelable()
	{
		// 0-7 weeds/empty, 8-19 growing, 21-31 diseased, 32-43 dead,
		// 44 check-health-only, 45-63 weeds — none may route.
		for (int v = 0; v <= 63; v++)
		{
			if (v == 20)
			{
				continue;
			}
			assertFalse("varbit value " + v + " must not be travelable",
				SpiritTreeService.spiritTreeTravelable(v));
		}
	}

	@Test
	public void decodeBoundaries()
	{
		assertFalse(SpiritTreeService.spiritTreeTravelable(19));
		assertTrue(SpiritTreeService.spiritTreeTravelable(20));
		assertFalse(SpiritTreeService.spiritTreeTravelable(21));
		assertFalse(SpiritTreeService.spiritTreeTravelable(43));
		assertFalse(SpiritTreeService.spiritTreeTravelable(44));
		assertFalse(SpiritTreeService.spiritTreeTravelable(45));
	}

	@Test
	public void patchMapIsTotalAndBidirectional()
	{
		assertEquals(5, SpiritTreeService.patchNames().size());
		assertEquals(12082, SpiritTreeService.regionForPatch("Port Sarim"));
		assertEquals(4771, SpiritTreeService.varbitForPatch("Port Sarim"));
		assertEquals(10300, SpiritTreeService.regionForPatch("Etceteria"));
		assertEquals(4772, SpiritTreeService.varbitForPatch("Etceteria"));
		assertEquals(11058, SpiritTreeService.regionForPatch("Brimhaven"));
		assertEquals(4772, SpiritTreeService.varbitForPatch("Brimhaven"));
		assertEquals(6711, SpiritTreeService.regionForPatch("Hosidius"));
		assertEquals(7904, SpiritTreeService.varbitForPatch("Hosidius"));
		assertEquals(4922, SpiritTreeService.regionForPatch("Farming Guild"));
		assertEquals(4771, SpiritTreeService.varbitForPatch("Farming Guild"));
	}

	@Test
	public void regionLookupReturnsMappedPatch()
	{
		assertEquals("Port Sarim", SpiritTreeService.patchNameForRegion(12082));
		assertEquals("Farming Guild", SpiritTreeService.patchNameForRegion(4922));
		assertNull(SpiritTreeService.patchNameForRegion(11826)); // unmapped region
		assertEquals(-1, SpiritTreeService.regionForPatch("Tree Gnome Village"));
		assertEquals(-1, SpiritTreeService.varbitForPatch("Tree Gnome Village"));
	}

	@Test
	public void patchAnchorTilesResolveToPatch()
	{
		// The transport rows' destination tiles must land on their patch;
		// the POH spirit tree tile must not be claimed by any patch.
		assertEquals("Port Sarim", SpiritTreeService.patchNameForTile(3058, 3257));
		assertEquals("Etceteria", SpiritTreeService.patchNameForTile(2613, 3855));
		assertEquals("Brimhaven", SpiritTreeService.patchNameForTile(2800, 3203));
		assertEquals("Hosidius", SpiritTreeService.patchNameForTile(1693, 3540));
		assertEquals("Farming Guild", SpiritTreeService.patchNameForTile(1251, 3750));
		assertNull(SpiritTreeService.patchNameForTile(1858, 7051)); // POH spirit tree
	}

	@Test
	public void patchBoundsStayInsideTheirRegion()
	{
		// Every tile covered by a patch's bounds must resolve back to that
		// patch and lie inside the patch's own region — otherwise the
		// region-scoped varbit would not describe the tiles the bounds gate.
		for (String name : SpiritTreeService.patchNames())
		{
			int[] bounds = SpiritTreeService.boundsForPatch(name);
			int region = SpiritTreeService.regionForPatch(name);
			for (int x = bounds[0]; x <= bounds[2]; x++)
			{
				for (int y = bounds[1]; y <= bounds[3]; y++)
				{
					assertEquals(name + " tile " + x + "," + y + " outside region " + region,
						region, (x >> 6) << 8 | (y >> 6));
					assertEquals(name, SpiritTreeService.patchNameForTile(x, y));
				}
			}
		}
	}

	@Test
	public void storedValueRoundTrips()
	{
		String stored = SpiritTreeService.serializeObserved(20, 1700000000L);
		assertEquals(Integer.valueOf(20), SpiritTreeService.parseStoredValue(stored));
	}

	@Test
	public void malformedStoredValuesAreSkipped()
	{
		assertNull(SpiritTreeService.parseStoredValue(null));
		assertNull(SpiritTreeService.parseStoredValue(""));
		assertNull(SpiritTreeService.parseStoredValue("no-colon"));
		assertNull(SpiritTreeService.parseStoredValue(":"));
		assertNull(SpiritTreeService.parseStoredValue(":123"));
		assertNull(SpiritTreeService.parseStoredValue("20:"));
		assertNull(SpiritTreeService.parseStoredValue("abc:123"));
		assertNull(SpiritTreeService.parseStoredValue("20:abc"));
		assertNull(SpiritTreeService.parseStoredValue("20:123:456"));
		assertNull(SpiritTreeService.parseStoredValue("99999999999999999999999:1"));
	}

	@Test
	public void emptyStoredConfigYieldsNoDetection()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.loadFromProfile();

		assertNull(persisted.getTravelableTreesOrNull());
		assertTrue(persisted.getTravelableTrees().isEmpty());
	}

	@Test
	public void varbitSampleMarksGrownTreeTravelable()
	{
		assertNotNull(state.applyVarbitSample("Port Sarim", 20));
		assertEquals(Set.of("Port Sarim"), state.getTravelableTrees());
	}

	@Test
	public void varbitSampleBelowTwentyIsNotTravelable()
	{
		// First observation is recorded (null → empty resolved) but the
		// travelable set itself did not change, so no change is reported.
		assertNull(state.applyVarbitSample("Port Sarim", 21));
		assertTrue(state.getTravelableTrees().isEmpty());
		assertEquals(Set.of(), state.getTravelableTreesOrNull());
	}

	@Test
	public void inRegionResampleEvictsStalePositive()
	{
		state.applyVarbitSample("Port Sarim", 20);
		assertEquals(Set.of("Port Sarim"), state.getTravelableTrees());

		assertNotNull(state.applyVarbitSample("Port Sarim", 32)); // dead
		assertTrue(state.getTravelableTrees().isEmpty());
	}

	@Test
	public void repeatedSameSampleReportsNoChange()
	{
		state.applyVarbitSample("Port Sarim", 20);
		assertNull(state.applyVarbitSample("Port Sarim", 20)); // identical read → no change
		assertNotNull(state.applyVarbitSample("Port Sarim", 21));  // travelable → diseased: change
		assertNull(state.applyVarbitSample("Port Sarim", 8));  // still not travelable → no change
	}

	@Test
	public void regionSettlesAfterTwoConsecutiveTicks()
	{
		state.notePlayerRegion(12082, 10);
		assertFalse(state.isRegionSettled(12082)); // entry tick: slot may be stale
		state.notePlayerRegion(12082, 11);
		assertTrue(state.isRegionSettled(12082));
	}

	@Test
	public void regionChangeUnSettles()
	{
		state.notePlayerRegion(12082, 10);
		state.notePlayerRegion(12082, 11);
		assertTrue(state.isRegionSettled(12082));

		state.notePlayerRegion(4922, 12);
		assertFalse(state.isRegionSettled(4922));
		assertFalse(state.isRegionSettled(12082));
	}

	@Test
	public void tickGapUnSettles()
	{
		state.notePlayerRegion(12082, 10);
		state.notePlayerRegion(12082, 11);
		assertTrue(state.isRegionSettled(12082));

		// A skipped tick (map loading, relog into the same region) means the
		// slot may have been repopulated — require another consecutive tick.
		state.notePlayerRegion(12082, 13);
		assertFalse(state.isRegionSettled(12082));
		state.notePlayerRegion(12082, 14);
		assertTrue(state.isRegionSettled(12082));
	}

	@Test
	public void unknownRegionUnSettles()
	{
		state.notePlayerRegion(12082, 10);
		state.notePlayerRegion(12082, 11);
		state.notePlayerRegion(-1, 12); // player location unknown
		assertFalse(state.isRegionSettled(12082));
		assertFalse(state.isRegionSettled(-1));
		state.notePlayerRegion(12082, 13);
		assertFalse(state.isRegionSettled(12082));
	}

	@Test
	public void menuSnapshotCoversListedPatchesOnly()
	{
		state.applyVarbitSample("Farming Guild", 20);
		state.applyMenuSnapshot(Set.of("Port Sarim", "Etceteria"), Set.of("Port Sarim"));

		assertEquals(Set.of("Port Sarim", "Farming Guild"), state.getTravelableTrees());
	}

	@Test
	public void menuSnapshotEvictsCoveredStalePositive()
	{
		state.applyVarbitSample("Port Sarim", 20);
		// Menu lists Port Sarim greyed out: authoritative unavailable for a covered patch.
		assertNotNull(state.applyMenuSnapshot(Set.of("Port Sarim"), Set.of()));
		assertTrue(state.getTravelableTrees().isEmpty());
	}

	@Test
	public void partialMenuSnapshotNeverShrinksUncoveredEntries()
	{
		state.applyVarbitSample("Port Sarim", 20);
		state.applyVarbitSample("Etceteria", 20);
		// Menu covers only Etceteria; Port Sarim is unvisited in the snapshot.
		state.applyMenuSnapshot(Set.of("Etceteria"), Set.of("Etceteria"));

		assertEquals(Set.of("Port Sarim", "Etceteria"), state.getTravelableTrees());
	}

	@Test
	public void persistedPositiveSurvivesReload()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771"))
			.thenReturn("20:1700000000");

		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.loadFromProfile();

		assertEquals(Set.of("Port Sarim"), persisted.getTravelableTrees());
	}

	@Test
	public void persistedNegativeDoesNotRoute()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", "spiritTree.11058.4772"))
			.thenReturn("32:1700000000");

		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.loadFromProfile();

		assertTrue(persisted.getTravelableTrees().isEmpty());
		assertEquals(Set.of(), persisted.getTravelableTreesOrNull());
	}

	@Test
	public void malformedPersistedEntryIsSkipped()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771"))
			.thenReturn("garbage");

		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.loadFromProfile();

		assertTrue(persisted.getTravelableTrees().isEmpty());
	}

	@Test
	public void persistIfDirtyWritesChangedKeys()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.applyVarbitSample("Port Sarim", 20);
		persisted.persistIfDirty();

		verify(configManager).setRSProfileConfiguration(
			eq("shortestpath"),
			eq("spiritTree.12082.4771"),
			contains("20:"));
	}

	@Test
	public void persistIfDirtySkipsCleanState()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.persistIfDirty();
		persisted.applyVarbitSample("Port Sarim", 20);
		persisted.persistIfDirty();
		// No further state change: a second persist must not write again.
		persisted.persistIfDirty();

		verify(configManager).setRSProfileConfiguration(
			eq("shortestpath"),
			eq("spiritTree.12082.4771"),
			contains("20:"));
		// An unobserved patch must not produce any profile write.
		verify(configManager, never()).setRSProfileConfiguration(
			eq("shortestpath"),
			eq("spiritTree.11058.4772"),
			contains("0:"));
		verify(configManager, never()).unsetRSProfileConfiguration(
			"shortestpath", "spiritTree.11058.4772");
	}

	@Test
	public void evictionUnsetsPersistedPositive()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771"))
			.thenReturn("20:1700000000");

		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.loadFromProfile();
		persisted.applyVarbitSample("Port Sarim", 0); // cleared patch
		persisted.persistIfDirty();

		verify(configManager).unsetRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771");
	}

	@Test
	public void persistRoundTripsThroughUnset()
	{
		// travelable → non-travelable → travelable must end with a live key
		// again, not a stale unset or an unwritten second set.
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreeService persisted = new SpiritTreeService(configManager, null);

		persisted.applyVarbitSample("Port Sarim", 20);
		persisted.persistIfDirty();
		persisted.applyVarbitSample("Port Sarim", 0);
		persisted.persistIfDirty();
		persisted.applyVarbitSample("Port Sarim", 20);
		persisted.persistIfDirty();

		verify(configManager, times(2)).setRSProfileConfiguration(
			eq("shortestpath"),
			eq("spiritTree.12082.4771"),
			contains("20:"));
		verify(configManager).unsetRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771");
	}

	@Test
	public void repeatNonTravelableFlushUnsetsOnce()
	{
		// A second dirty flush for another patch must not unset the same key
		// again — the absent-key state is already persisted.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771"))
			.thenReturn("20:1700000000");

		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.loadFromProfile();
		persisted.applyVarbitSample("Port Sarim", 32); // dead
		persisted.persistIfDirty();
		persisted.applyVarbitSample("Etceteria", 20);
		persisted.persistIfDirty();

		verify(configManager).unsetRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771");
	}

	@Test
	public void legacyNegativeResidueIsUnsetOnFlush()
	{
		// Entries persisted by the previous scheme as "0:<ts>" still parse as
		// non-travelable observations; the next dirty flush removes the key.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771"))
			.thenReturn("0:1700000000");

		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.loadFromProfile();
		assertTrue(persisted.getTravelableTrees().isEmpty());

		persisted.applyVarbitSample("Etceteria", 20); // dirty for another patch
		persisted.persistIfDirty();

		verify(configManager).unsetRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771");
	}

	@Test
	public void loadFromProfileClearsPreviousAccount()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			"shortestpath", "spiritTree.12082.4771"))
			.thenReturn("20:1700000000")
			.thenReturn(null);

		SpiritTreeService persisted = new SpiritTreeService(configManager, null);
		persisted.loadFromProfile();
		assertEquals(Set.of("Port Sarim"), persisted.getTravelableTrees());

		persisted.loadFromProfile();
		assertTrue(persisted.getTravelableTrees().isEmpty());
		assertNull(persisted.getTravelableTreesOrNull());
	}

	@Test
	public void nullConfigManagerIsSafe()
	{
		state.loadFromProfile();
		state.applyVarbitSample("Port Sarim", 20);
		state.persistIfDirty(); // must not throw

		assertEquals(Set.of("Port Sarim"), state.getTravelableTrees());
	}

	@Test
	public void greyedMenuRowWithMarkupStillListsPatch()
	{
		// A greyed row can leave a closing tag on the name capture; the parse
		// must strip it or the row misses the patch table and the menu loses
		// its authority to evict a stale positive for that patch.
		Widget row = mock(Widget.class);
		when(row.getText()).thenReturn("<col=735a28>7</col>: <col=5f5f5f>Port Sarim</col>");

		SpiritTreeMenuSnapshot snapshot =
			SpiritTreeService.parseSpiritTreeMenuRows(new Widget[]{row}, false);

		assertEquals(Set.of("Port Sarim"), snapshot.listed);
		assertTrue(snapshot.available.isEmpty());

		state.applyVarbitSample("Port Sarim", 20);
		assertNotNull(state.applyMenuSnapshot(snapshot.listed, snapshot.available));
		assertTrue(state.getTravelableTrees().isEmpty());
	}

	// --- Producer-seam contract: publication + facts through the absorbed
	// entry points (tick, menu, refresh, profile load). ---

	@Test
	public void publishedSetIsNullBeforeAnyObservation()
	{
		assertNull(state.getAvailableSpiritTrees());
	}

	@Test
	public void loadFromProfilePublishesResolvedSet()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration("shortestpath", "spiritTree.12082.4771"))
			.thenReturn("20:1700000000");
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);

		SpiritTreeService service = new SpiritTreeService(configManager, null, coordinator);
		service.loadFromProfile();

		// A profile reload re-resolves state, so the declare is unconditional.
		ArgumentCaptor<TreeChange> captor = ArgumentCaptor.forClass(TreeChange.class);
		verify(coordinator).treeSetChanged(captor.capture());
		assertEquals("profile", captor.getValue().getKey());
		assertTrue(captor.getValue().getEffects().contains(Effect.ROUTE_INVALIDATING));
		assertEquals(Set.of("Port Sarim"), service.getAvailableSpiritTrees());
	}

	@Test
	public void loadFromProfileWithNoKeysPublishesUnresolvedNull()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SpiritTreeService service = new SpiritTreeService(configManager, null, coordinator);

		service.loadFromProfile();

		verify(coordinator).treeSetChanged(any());
		assertNull(service.getAvailableSpiritTrees());
	}

	@Test
	public void trackedRefreshSamplesOnlyAfterRegionSettles()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreeService service = new SpiritTreeService(configManager, null);
		service.notePlayerRegion(12082, 10); // region-entry tick — not settled

		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.localPlayerWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(source.varbit(VarbitID.FARMING_TRANSMIT_A)).thenReturn(20);

		service.refreshAvailability(source);
		assertNull(service.getAvailableSpiritTrees()); // entry-tick sample refused

		service.notePlayerRegion(12082, 11); // second consecutive tick — settled
		service.refreshAvailability(source);

		assertEquals(Set.of("Port Sarim"), service.getAvailableSpiritTrees());
	}

	@Test
	public void modalWidgetSuppressesTrackedRefreshSample()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreeService service = new SpiritTreeService(configManager, null);
		service.notePlayerRegion(12082, 10);
		service.notePlayerRegion(12082, 11);

		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.localPlayerWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(source.modalWidgetOpen()).thenReturn(true);
		when(source.varbit(VarbitID.FARMING_TRANSMIT_A)).thenReturn(20);

		service.refreshAvailability(source);

		assertNull(service.getAvailableSpiritTrees());
	}

	@Test
	public void detachedRefreshMergesLiveSampleWithoutObserving()
	{
		SpiritTreeService service = SpiritTreeService.forTesting();
		service.setAvailableSpiritTreesForTest(Set.of("Etceteria"));

		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.localPlayerWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(source.varbit(VarbitID.FARMING_TRANSMIT_A)).thenReturn(20);

		service.refreshAvailability(source);

		assertEquals(Set.of("Etceteria", "Port Sarim"), service.getAvailableSpiritTrees());
		// The merge is published-only: no observation was recorded, so the
		// raw resolution stays unresolved.
		assertNull(service.getTravelableTreesOrNull());
	}

	@Test
	public void detachedRefreshEvictsStalePublishedPatch()
	{
		SpiritTreeService service = SpiritTreeService.forTesting();
		service.setAvailableSpiritTreesForTest(Set.of("Port Sarim", "Etceteria"));

		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.localPlayerWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(source.varbit(VarbitID.FARMING_TRANSMIT_A)).thenReturn(32); // dead

		service.refreshAvailability(source);

		assertEquals(Set.of("Etceteria"), service.getAvailableSpiritTrees());
	}

	@Test
	public void detachedRefreshBypassesRegionSettlement()
	{
		SpiritTreeService service = SpiritTreeService.forTesting();

		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.localPlayerWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(source.varbit(VarbitID.FARMING_TRANSMIT_A)).thenReturn(20);

		// No notePlayerRegion at all — the detached arm never settles.
		service.refreshAvailability(source);

		assertEquals(Set.of("Port Sarim"), service.getAvailableSpiritTrees());
	}

	@Test
	public void onGameTickSamplesAndPublishesOnceSettled()
	{
		Client client = mock(Client.class);
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(client.getTickCount()).thenReturn(10, 11);
		when(client.getVarbitValue(VarbitID.FARMING_TRANSMIT_A)).thenReturn(20);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);

		SpiritTreeService service =
			new SpiritTreeService(mock(ConfigManager.class), client, coordinator);

		service.onGameTick(); // region-entry tick does not sample
		verify(coordinator, never()).treeSetChanged(any());

		service.onGameTick();

		ArgumentCaptor<TreeChange> captor = ArgumentCaptor.forClass(TreeChange.class);
		verify(coordinator).treeSetChanged(captor.capture());
		assertEquals("varbit:Port Sarim", captor.getValue().getKey());
		assertEquals(Set.of("Port Sarim"), service.getAvailableSpiritTrees());
	}

	@Test
	public void onGameTickWithoutPlayerDeclaresNothing()
	{
		Client client = mock(Client.class);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SpiritTreeService service = new SpiritTreeService(null, client, coordinator);

		service.onGameTick();

		verify(coordinator, never()).treeSetChanged(any());
		assertNull(service.getAvailableSpiritTrees());
	}

	@Test
	public void onMenuOpenedAppliesMenuAndPublishes()
	{
		Client client = mock(Client.class);
		Widget container = mock(Widget.class);
		Widget first = mock(Widget.class);
		Widget listed = mock(Widget.class);
		Widget greyed = mock(Widget.class);
		when(client.getWidget(InterfaceID.MENU, 3)).thenReturn(container);
		when(container.getDynamicChildren()).thenReturn(new Widget[]{first, listed, greyed});
		when(first.getText()).thenReturn("<col=735a28>1</col>: Tree Gnome Village");
		when(listed.getText()).thenReturn("<col=735a28>3</col>: Port Sarim");
		when(greyed.getText()).thenReturn("<col=735a28>7</col>: <col=5f5f5f>Etceteria</col>");
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);

		SpiritTreeService service =
			new SpiritTreeService(mock(ConfigManager.class), client, coordinator);
		service.onMenuOpened(false);

		ArgumentCaptor<TreeChange> captor = ArgumentCaptor.forClass(TreeChange.class);
		verify(coordinator).treeSetChanged(captor.capture());
		assertEquals("menu", captor.getValue().getKey());
		assertEquals(Set.of("Port Sarim"), service.getAvailableSpiritTrees());
	}

	@Test
	public void onMenuOpenedIgnoresForeignMenu()
	{
		Client client = mock(Client.class);
		Widget container = mock(Widget.class);
		Widget first = mock(Widget.class);
		when(client.getWidget(InterfaceID.MENU, 3)).thenReturn(container);
		when(container.getDynamicChildren()).thenReturn(new Widget[]{first});
		when(first.getText()).thenReturn("<col=735a28>1</col>: Some other interface");
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);

		SpiritTreeService service =
			new SpiritTreeService(mock(ConfigManager.class), client, coordinator);

		service.onMenuOpened(false);

		verify(coordinator, never()).treeSetChanged(any());
		assertNull(service.getAvailableSpiritTrees());
	}

	@Test
	public void onMenuOpenedWithoutContainerDeclaresNothing()
	{
		Client client = mock(Client.class);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SpiritTreeService service =
			new SpiritTreeService(mock(ConfigManager.class), client, coordinator);

		service.onMenuOpened(false);
		service.onMenuOpened(true);

		verify(coordinator, never()).treeSetChanged(any());
	}

	@Test
	public void onMenuOpenedEmptyChildrenDeclaresNothing()
	{
		Client client = mock(Client.class);
		Widget container = mock(Widget.class);
		when(client.getWidget(InterfaceID.MENU, 3)).thenReturn(container);
		when(container.getDynamicChildren()).thenReturn(new Widget[0]);
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);

		SpiritTreeService service =
			new SpiritTreeService(mock(ConfigManager.class), client, coordinator);

		service.onMenuOpened(false);

		verify(coordinator, never()).treeSetChanged(any());
	}

	@Test
	public void onMenuOpenedUsesNewMenuArm()
	{
		Client client = mock(Client.class);
		Widget container = mock(Widget.class);
		Widget first = mock(Widget.class);
		Widget listed = mock(Widget.class);
		when(client.getWidget(InterfaceID.MENU_NEW, 9)).thenReturn(container);
		when(container.getDynamicChildren()).thenReturn(new Widget[]{first, listed});
		when(first.getText()).thenReturn("<col=ffffff>1</col>: Tree Gnome Village");
		when(listed.getText()).thenReturn("<col=ffffff>3</col>: Brimhaven");
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);

		SpiritTreeService service =
			new SpiritTreeService(mock(ConfigManager.class), client, coordinator);
		service.onMenuOpened(true);

		verify(coordinator).treeSetChanged(any());
		assertEquals(Set.of("Brimhaven"), service.getAvailableSpiritTrees());
	}

	@Test
	public void greyedMenuRowEvictsStalePositiveFromPublishedSet()
	{
		Client client = mock(Client.class);
		Widget container = mock(Widget.class);
		Widget first = mock(Widget.class);
		Widget greyed = mock(Widget.class);
		when(client.getWidget(InterfaceID.MENU, 3)).thenReturn(container);
		when(container.getDynamicChildren()).thenReturn(new Widget[]{first, greyed});
		when(first.getText()).thenReturn("<col=735a28>1</col>: Tree Gnome Village");
		when(greyed.getText()).thenReturn("<col=735a28>7</col>: <col=5f5f5f>Port Sarim</col>");

		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SpiritTreeService service =
			new SpiritTreeService(mock(ConfigManager.class), client, coordinator);
		service.applyVarbitSample("Port Sarim", 20); // prior positive observation
		service.onMenuOpened(false);

		verify(coordinator).treeSetChanged(any());
		// Observed but nothing travelable — empty set, never back to null.
		assertEquals(Set.of(), service.getAvailableSpiritTrees());
	}

	@Test
	public void menuPartialListingKeepsUnlistedPatches()
	{
		Client client = mock(Client.class);
		Widget container = mock(Widget.class);
		Widget first = mock(Widget.class);
		Widget listed = mock(Widget.class);
		when(client.getWidget(InterfaceID.MENU, 3)).thenReturn(container);
		when(container.getDynamicChildren()).thenReturn(new Widget[]{first, listed});
		when(first.getText()).thenReturn("<col=735a28>1</col>: Tree Gnome Village");
		when(listed.getText()).thenReturn("<col=735a28>3</col>: Port Sarim");

		SpiritTreeService service = new SpiritTreeService(mock(ConfigManager.class), client);
		service.applyVarbitSample("Farming Guild", 20); // menu never lists it
		service.onMenuOpened(false);

		// A partial snapshot never shrinks unvisited entries.
		assertEquals(Set.of("Farming Guild", "Port Sarim"), service.getAvailableSpiritTrees());
	}

	@Test
	public void onGameTickSkipsSampleWhileModalOpen()
	{
		Client client = mock(Client.class);
		Player player = mock(Player.class);
		net.runelite.api.HashTable<net.runelite.api.WidgetNode> table = mock(net.runelite.api.HashTable.class);
		net.runelite.api.WidgetNode node = mock(net.runelite.api.WidgetNode.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(client.getTickCount()).thenReturn(10, 11);
		when(client.getComponentTable()).thenReturn(table);
		when(table.iterator()).thenAnswer(invocation -> java.util.List.of(node).iterator());
		when(node.getModalMode()).thenReturn(net.runelite.api.widgets.WidgetModalMode.MODAL_CLICKTHROUGH);

		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		SpiritTreeService service =
			new SpiritTreeService(mock(ConfigManager.class), client, coordinator);
		service.onGameTick();
		service.onGameTick(); // settled tick, but the modal suppresses the sample

		verify(coordinator, never()).treeSetChanged(any());
		verify(client, never()).getVarbitValue(VarbitID.FARMING_TRANSMIT_A);
		assertNull(service.getAvailableSpiritTrees());
	}

	@Test
	public void onGameTickPersistsFreshObservationOnSameTick()
	{
		Client client = mock(Client.class);
		Player player = mock(Player.class);
		ConfigManager configManager = mock(ConfigManager.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(client.getTickCount()).thenReturn(10, 11);
		when(client.getVarbitValue(VarbitID.FARMING_TRANSMIT_A)).thenReturn(20);

		SpiritTreeService service = new SpiritTreeService(configManager, client);
		service.onGameTick();
		service.onGameTick();

		// persistIfDirty runs inside the tick — the fresh observation is
		// written on the same tick rather than the next.
		verify(configManager).setRSProfileConfiguration(
			eq("shortestpath"), eq("spiritTree.12082.4771"), contains("20:"));
	}

	@Test
	public void trackedRefreshDoesNotPublishWhileUnresolved()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreeService service = new SpiritTreeService(configManager, null);
		service.notePlayerRegion(11826, 10); // unmapped region
		service.notePlayerRegion(11826, 11);

		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.localPlayerWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));

		service.refreshAvailability(source);

		// No observation exists and none was sampled — the unresolved null
		// must not be overwritten by an empty set.
		assertNull(service.getAvailableSpiritTrees());
	}

	@Test
	public void trackedRefreshNonTravelableSampleEvictsPersistedPositive()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration("shortestpath", "spiritTree.12082.4771"))
			.thenReturn("20:1700000000");
		SpiritTreeService service = new SpiritTreeService(configManager, null);
		service.loadFromProfile();
		service.notePlayerRegion(12082, 10);
		service.notePlayerRegion(12082, 11);

		PlayerStateSource source = mock(PlayerStateSource.class);
		when(source.localPlayerWorldLocation()).thenReturn(new WorldPoint(3060, 3258, 0));
		when(source.varbit(VarbitID.FARMING_TRANSMIT_A)).thenReturn(0); // patch cleared

		service.refreshAvailability(source);

		assertEquals(Set.of(), service.getAvailableSpiritTrees());
	}

	@Test
	public void publishedSetIsImmutable()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration("shortestpath", "spiritTree.12082.4771"))
			.thenReturn("20:1700000000");
		SpiritTreeService service = new SpiritTreeService(configManager, null);
		service.loadFromProfile();

		assertThrows(UnsupportedOperationException.class,
			() -> service.getAvailableSpiritTrees().add("Hosidius"));
	}
}
