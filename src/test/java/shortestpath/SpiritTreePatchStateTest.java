package shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Set;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

public class SpiritTreePatchStateTest
{
	private SpiritTreePatchState state;

	@Before
	public void setUp()
	{
		state = new SpiritTreePatchState();
	}

	@Test
	public void grownTreeValueIsTravelable()
	{
		assertTrue(SpiritTreePatchState.spiritTreeTravelable(20));
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
				SpiritTreePatchState.spiritTreeTravelable(v));
		}
	}

	@Test
	public void decodeBoundaries()
	{
		assertFalse(SpiritTreePatchState.spiritTreeTravelable(19));
		assertTrue(SpiritTreePatchState.spiritTreeTravelable(20));
		assertFalse(SpiritTreePatchState.spiritTreeTravelable(21));
		assertFalse(SpiritTreePatchState.spiritTreeTravelable(43));
		assertFalse(SpiritTreePatchState.spiritTreeTravelable(44));
		assertFalse(SpiritTreePatchState.spiritTreeTravelable(45));
	}

	@Test
	public void patchMapIsTotalAndBidirectional()
	{
		assertEquals(5, SpiritTreePatchState.patchNames().size());
		assertEquals(12082, SpiritTreePatchState.regionForPatch("Port Sarim"));
		assertEquals(4771, SpiritTreePatchState.varbitForPatch("Port Sarim"));
		assertEquals(10300, SpiritTreePatchState.regionForPatch("Etceteria"));
		assertEquals(4772, SpiritTreePatchState.varbitForPatch("Etceteria"));
		assertEquals(11058, SpiritTreePatchState.regionForPatch("Brimhaven"));
		assertEquals(4772, SpiritTreePatchState.varbitForPatch("Brimhaven"));
		assertEquals(6711, SpiritTreePatchState.regionForPatch("Hosidius"));
		assertEquals(7904, SpiritTreePatchState.varbitForPatch("Hosidius"));
		assertEquals(4922, SpiritTreePatchState.regionForPatch("Farming Guild"));
		assertEquals(4771, SpiritTreePatchState.varbitForPatch("Farming Guild"));
	}

	@Test
	public void regionLookupReturnsMappedPatch()
	{
		assertEquals("Port Sarim", SpiritTreePatchState.patchNameForRegion(12082));
		assertEquals("Farming Guild", SpiritTreePatchState.patchNameForRegion(4922));
		assertNull(SpiritTreePatchState.patchNameForRegion(11826)); // unmapped region
		assertEquals(-1, SpiritTreePatchState.regionForPatch("Tree Gnome Village"));
		assertEquals(-1, SpiritTreePatchState.varbitForPatch("Tree Gnome Village"));
	}

	@Test
	public void storedValueRoundTrips()
	{
		String stored = SpiritTreePatchState.serializeObserved(20, 1700000000L);
		assertEquals(Integer.valueOf(20), SpiritTreePatchState.parseStoredValue(stored));
	}

	@Test
	public void malformedStoredValuesAreSkipped()
	{
		assertNull(SpiritTreePatchState.parseStoredValue(null));
		assertNull(SpiritTreePatchState.parseStoredValue(""));
		assertNull(SpiritTreePatchState.parseStoredValue("no-colon"));
		assertNull(SpiritTreePatchState.parseStoredValue(":"));
		assertNull(SpiritTreePatchState.parseStoredValue(":123"));
		assertNull(SpiritTreePatchState.parseStoredValue("20:"));
		assertNull(SpiritTreePatchState.parseStoredValue("abc:123"));
		assertNull(SpiritTreePatchState.parseStoredValue("20:abc"));
		assertNull(SpiritTreePatchState.parseStoredValue("20:123:456"));
		assertNull(SpiritTreePatchState.parseStoredValue("99999999999999999999999:1"));
	}

	@Test
	public void emptyStoredConfigYieldsNoDetection()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreePatchState persisted = new SpiritTreePatchState(configManager);
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
			ShortestPathPlugin.CONFIG_GROUP, "spiritTree.12082.4771"))
			.thenReturn("20:1700000000");

		SpiritTreePatchState persisted = new SpiritTreePatchState(configManager);
		persisted.loadFromProfile();

		assertEquals(Set.of("Port Sarim"), persisted.getTravelableTrees());
	}

	@Test
	public void persistedNegativeDoesNotRoute()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			ShortestPathPlugin.CONFIG_GROUP, "spiritTree.11058.4772"))
			.thenReturn("32:1700000000");

		SpiritTreePatchState persisted = new SpiritTreePatchState(configManager);
		persisted.loadFromProfile();

		assertTrue(persisted.getTravelableTrees().isEmpty());
		assertEquals(Set.of(), persisted.getTravelableTreesOrNull());
	}

	@Test
	public void malformedPersistedEntryIsSkipped()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			ShortestPathPlugin.CONFIG_GROUP, "spiritTree.12082.4771"))
			.thenReturn("garbage");

		SpiritTreePatchState persisted = new SpiritTreePatchState(configManager);
		persisted.loadFromProfile();

		assertTrue(persisted.getTravelableTrees().isEmpty());
	}

	@Test
	public void persistIfDirtyWritesChangedKeys()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreePatchState persisted = new SpiritTreePatchState(configManager);
		persisted.applyVarbitSample("Port Sarim", 20);
		persisted.persistIfDirty();

		verify(configManager).setRSProfileConfiguration(
			eq(ShortestPathPlugin.CONFIG_GROUP),
			eq("spiritTree.12082.4771"),
			contains("20:"));
	}

	@Test
	public void persistIfDirtySkipsCleanState()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		SpiritTreePatchState persisted = new SpiritTreePatchState(configManager);
		persisted.persistIfDirty();
		persisted.applyVarbitSample("Port Sarim", 20);
		persisted.persistIfDirty();
		// No further state change: a second persist must not write again.
		persisted.persistIfDirty();

		verify(configManager).setRSProfileConfiguration(
			eq(ShortestPathPlugin.CONFIG_GROUP),
			eq("spiritTree.12082.4771"),
			contains("20:"));
		verify(configManager, never()).setRSProfileConfiguration(
			eq(ShortestPathPlugin.CONFIG_GROUP),
			eq("spiritTree.11058.4772"),
			contains("0:"));
	}

	@Test
	public void evictionOverwritesPersistedPositive()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			ShortestPathPlugin.CONFIG_GROUP, "spiritTree.12082.4771"))
			.thenReturn("20:1700000000");

		SpiritTreePatchState persisted = new SpiritTreePatchState(configManager);
		persisted.loadFromProfile();
		persisted.applyVarbitSample("Port Sarim", 0); // cleared patch
		persisted.persistIfDirty();

		verify(configManager).setRSProfileConfiguration(
			eq(ShortestPathPlugin.CONFIG_GROUP),
			eq("spiritTree.12082.4771"),
			contains("0:"));
	}

	@Test
	public void loadFromProfileClearsPreviousAccount()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getRSProfileConfiguration(
			ShortestPathPlugin.CONFIG_GROUP, "spiritTree.12082.4771"))
			.thenReturn("20:1700000000")
			.thenReturn(null);

		SpiritTreePatchState persisted = new SpiritTreePatchState(configManager);
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
