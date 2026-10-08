package shortestpath.spirittree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Set;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import shortestpath.ShortestPathPlugin;
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
		SpiritTreeService persisted = new SpiritTreeService(configManager);
		persisted.loadFromProfile();

		assertNull(persisted.getTravelableTreesOrNull());
		assertTrue(persisted.getTravelableTrees().isEmpty());
	}

	@Test
	public void varbitSampleMarksGrownTreeTravelable()
	{
		assertTrue(state.applyVarbitSample("Port Sarim", 20));
		assertEquals(Set.of("Port Sarim"), state.getTravelableTrees());
	}

	@Test
	public void varbitSampleBelowTwentyIsNotTravelable()
	{
		// First observation is recorded (null → empty resolved) but the
		// travelable set itself did not change, so no change is reported.
		assertFalse(state.applyVarbitSample("Port Sarim", 21));
		assertTrue(state.getTravelableTrees().isEmpty());
		assertEquals(Set.of(), state.getTravelableTreesOrNull());
	}

	@Test
	public void inRegionResampleEvictsStalePositive()
	{
		state.applyVarbitSample("Port Sarim", 20);
		assertEquals(Set.of("Port Sarim"), state.getTravelableTrees());

		assertTrue(state.applyVarbitSample("Port Sarim", 32)); // dead
		assertTrue(state.getTravelableTrees().isEmpty());
	}

	@Test
	public void repeatedSameSampleReportsNoChange()
	{
		state.applyVarbitSample("Port Sarim", 20);
		assertFalse(state.applyVarbitSample("Port Sarim", 20)); // identical read → no change
		assertTrue(state.applyVarbitSample("Port Sarim", 21));  // travelable → diseased: change
		assertFalse(state.applyVarbitSample("Port Sarim", 8));  // still not travelable → no change
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
		assertTrue(state.applyMenuSnapshot(Set.of("Port Sarim"), Set.of()));
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

		SpiritTreeService persisted = new SpiritTreeService(configManager);
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

		SpiritTreeService persisted = new SpiritTreeService(configManager);
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

		SpiritTreeService persisted = new SpiritTreeService(configManager);
		persisted.loadFromProfile();

		assertTrue(persisted.getTravelableTrees().isEmpty());
	}

	@Test
	public void persistIfDirtyWritesChangedKeys()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreeService persisted = new SpiritTreeService(configManager);
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
		SpiritTreeService persisted = new SpiritTreeService(configManager);
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

		SpiritTreeService persisted = new SpiritTreeService(configManager);
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
		SpiritTreeService persisted = new SpiritTreeService(configManager);

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

		SpiritTreeService persisted = new SpiritTreeService(configManager);
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

		SpiritTreeService persisted = new SpiritTreeService(configManager);
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

		SpiritTreeService persisted = new SpiritTreeService(configManager);
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

		ShortestPathPlugin.SpiritTreeMenuSnapshot snapshot =
			ShortestPathPlugin.parseSpiritTreeMenuRows(new Widget[]{row}, false);

		assertEquals(Set.of("Port Sarim"), snapshot.listed);
		assertTrue(snapshot.available.isEmpty());

		state.applyVarbitSample("Port Sarim", 20);
		assertTrue(state.applyMenuSnapshot(snapshot.listed, snapshot.available));
		assertTrue(state.getTravelableTrees().isEmpty());
	}
}
