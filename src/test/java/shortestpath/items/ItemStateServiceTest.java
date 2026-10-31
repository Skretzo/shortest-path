package shortestpath.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import shortestpath.LogCapture;
import shortestpath.pathfinder.BankPickupProjection;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.BankPickupAnalysis;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;

/**
 * Pins the item-state producer seam contract: which game inputs the service
 * admits, which {@link ItemChange} fact each admitted input declares to the
 * coordinator, and the ownership rules for the live open-bank reference. The
 * coordinator relies on a declared fact meaning "a tracked input changed" and
 * on the declared {@link Effect}s to decide follow-up actions, so silent
 * inputs and declares the old return-null paths guarded are the failure
 * modes these tests guard against. The adversarial half of this file covers
 * the collection boundary (null/empty containers, non-positive quantities,
 * banked-pouch selection, bank-path pooling) and the bank-pickup cache's
 * (path identity, index, dirty) contract.
 */
public class ItemStateServiceTest
{
	@Rule
	public final LogCapture log = new LogCapture("shortestpath.items.ItemStateService");

	private static void assertEligibilityStale(ItemChange change, String what)
	{
		assertNotNull(what + " produces a change fact", change);
		assertTrue(what + " declares ELIGIBILITY_STALE",
			change.getEffects().contains(Effect.ELIGIBILITY_STALE));
	}

	/**
	 * The fact the service most recently declared on the coordinator,
	 * asserting the channel saw exactly {@code declared} calls so a dropped
	 * or duplicated declare fails here rather than downstream.
	 */
	private static ItemChange lastDeclared(RefreshCoordinator coordinator, int declared)
	{
		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(declared)).itemsChanged(captor.capture());
		return captor.getValue();
	}

	@Test
	public void bankContainerIsStoredAsLiveRefAndDeclaresEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);
		ItemContainer bank = mock(ItemContainer.class);

		service.onContainerChanged(InventoryID.BANK, bank);

		assertEligibilityStale(lastDeclared(coordinator, 1), "bank container event");
		assertSame("the open bank stays a live reference, never copied", bank, service.getBank());
	}

	@Test
	public void inventoryContainerDeclaresEligibilityStaleButKeepsBankRef()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);
		ItemContainer bank = mock(ItemContainer.class);
		ItemContainer inv = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		service.onContainerChanged(InventoryID.INV, inv);

		assertEligibilityStale(lastDeclared(coordinator, 2), "inventory container event");
		assertSame("inventory events never overwrite the bank ref", bank, service.getBank());
	}

	@Test
	public void wornContainerDeclaresEligibilityStaleButKeepsBankRef()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);
		ItemContainer bank = mock(ItemContainer.class);
		ItemContainer worn = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		service.onContainerChanged(InventoryID.WORN, worn);

		assertEligibilityStale(lastDeclared(coordinator, 2), "equipment container event");
		assertSame("equipment events never overwrite the bank ref", bank, service.getBank());
	}

	@Test
	public void unrelatedContainerDeclaresNothingAndLeavesTheBankRef()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);
		ItemContainer bank = mock(ItemContainer.class);
		service.onContainerChanged(InventoryID.BANK, bank);

		service.onContainerChanged(InventoryID.LOOTING_BAG, mock(ItemContainer.class));

		verify(coordinator, times(1)).itemsChanged(any());
		assertSame("an untracked container never overwrites the bank ref", bank, service.getBank());
	}

	@Test
	public void lumbridgeDiaryVarbitDeclaresEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		service.onVarbitChanged(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE);

		assertEligibilityStale(lastDeclared(coordinator, 1), "Lumbridge diary varbit");
	}

	@Test
	public void runePouchRuneVarbitsDeclareEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		for (int varbitId : OwnedItems.RUNE_POUCH_RUNE_VARBITS)
		{
			service.onVarbitChanged(varbitId);
		}

		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(OwnedItems.RUNE_POUCH_RUNE_VARBITS.length))
			.itemsChanged(captor.capture());
		for (ItemChange change : captor.getAllValues())
		{
			assertEligibilityStale(change, "rune pouch rune varbit fact " + change.getKey());
		}
	}

	@Test
	public void runePouchAmountVarbitsDeclareEligibilityStale()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		for (int varbitId : OwnedItems.RUNE_POUCH_AMOUNT_VARBITS)
		{
			service.onVarbitChanged(varbitId);
		}

		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(OwnedItems.RUNE_POUCH_AMOUNT_VARBITS.length))
			.itemsChanged(captor.capture());
		for (ItemChange change : captor.getAllValues())
		{
			assertEligibilityStale(change, "rune pouch amount varbit fact " + change.getKey());
		}
	}

	@Test
	public void unrelatedVarbitDeclaresNothing()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		service.onVarbitChanged(VarbitID.FAIRY2_QUEENCURE_QUEST);

		verify(coordinator, never()).itemsChanged(any());
	}

	@Test
	public void noteBankContainerSeedsTheBankRefForHarnesses()
	{
		ItemStateService service = ItemStateService.forTesting();
		ItemContainer bank = mock(ItemContainer.class);

		service.noteBankContainer(bank);

		assertSame(bank, service.getBank());
	}

	@Test
	public void changeFactExposesKeyAndEffectsAsAValue()
	{
		RefreshCoordinator coordinator = mock(RefreshCoordinator.class);
		ItemStateService service = ItemStateService.forTesting(coordinator);

		service.onContainerChanged(InventoryID.BANK, mock(ItemContainer.class));
		service.onContainerChanged(InventoryID.BANK, mock(ItemContainer.class));

		ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
		verify(coordinator, times(2)).itemsChanged(captor.capture());
		ItemChange first = captor.getAllValues().get(0);
		ItemChange second = captor.getAllValues().get(1);

		assertEquals("container:" + InventoryID.BANK, first.getKey());
		assertNotSame("each observed change is its own fact", first, second);
		try
		{
			first.getEffects().add(Effect.DISPLAY_ONLY);
			fail("the fact's effect set must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
		}
	}

	// ------------------------------------------------------------------
	// Last-seen bank snapshot — the display-side per-account record feeding
	// the panel's Owned filter. Distinct from the live bank ref: captured
	// only while the bank is open, persisted under "lastSeenBankItems"
	// through RSProfile, surviving reset() and sessions, never consulted
	// by eligibility. These tests pin the capture/persist/load lifecycle
	// adversarially: the post-close drain, the legitimately emptied bank,
	// identical re-captures, corrupt profile data, and per-account scoping.
	// ------------------------------------------------------------------

	private static Item[] items(int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 1);
		}
		return items;
	}

	private static ItemContainer containerWith(Item... contents)
	{
		ItemContainer container = mock(ItemContainer.class);
		doReturn(contents).when(container).getItems();
		return container;
	}

	@Test
	public void noteLastSeenBankCapturesContentsWhileOpen()
	{
		ItemStateService service = ItemStateService.forTesting();

		service.noteLastSeenBank(containerWith(new Item(4151, 3), new Item(13121, 1)), true);

		assertEquals(Map.of(4151, 3, 13121, 1), service.lastSeenBank());
	}

	@Test
	public void noteLastSeenBankPersistsSortedCsvUnderRsProfile()
	{
		ConfigManager config = mock(ConfigManager.class);
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);

		service.noteLastSeenBank(containerWith(new Item(13121, 1), new Item(4151, 3)), true);

		verify(config).setRSProfileConfiguration(
			"shortestpath", "lastSeenBankItems", "4151:3,13121:1");
	}

	@Test
	public void noteLastSeenBankWhileClosedLeavesTheSnapshotUntouched()
	{
		ConfigManager config = mock(ConfigManager.class);
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);
		service.noteLastSeenBank(containerWith(items(13121)), true);

		// The post-close event arrives carrying an emptied container — the
		// bank is gone, not emptied — so it must not overwrite the snapshot.
		service.noteLastSeenBank(containerWith(), false);

		assertEquals(Map.of(13121, 1), service.lastSeenBank());
		verify(config, times(1)).setRSProfileConfiguration(anyString(), anyString(), any());
		verify(config, never()).unsetRSProfileConfiguration(anyString(), anyString());
	}

	@Test
	public void emptiedBankWhileOpenPersistsAsAnUnset()
	{
		ConfigManager config = mock(ConfigManager.class);
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);
		service.noteLastSeenBank(containerWith(items(13121)), true);

		// A legitimately emptied bank (opened with zero items) must replace
		// the snapshot — distinct from the post-close drain, which is ignored.
		service.noteLastSeenBank(containerWith(), true);

		assertEquals(Map.of(), service.lastSeenBank());
		verify(config).unsetRSProfileConfiguration("shortestpath", "lastSeenBankItems");
	}

	@Test
	public void loadFromProfileRepopulatesTheSnapshot()
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileConfiguration("shortestpath", "lastSeenBankItems"))
			.thenReturn("13121:1,4151:3");
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);

		service.loadFromProfile();

		assertEquals(Map.of(13121, 1, 4151, 3), service.lastSeenBank());
	}

	@Test
	public void loadFromProfileBlankOrMissingYieldsNoSnapshot()
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileConfiguration("shortestpath", "lastSeenBankItems"))
			.thenReturn("13121:1");
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);
		service.loadFromProfile();
		assertEquals(Map.of(13121, 1), service.lastSeenBank());

		for (String blank : new String[] {null, "", "   "})
		{
			when(config.getRSProfileConfiguration("shortestpath", "lastSeenBankItems"))
				.thenReturn(blank);
			service.loadFromProfile();
			assertEquals("a missing or blank value clears the snapshot, got [" + blank + "]",
				Map.of(), service.lastSeenBank());
		}
	}

	@Test
	public void lastSeenBankStaysScopedToTheActiveProfile()
	{
		ConfigManager config = mock(ConfigManager.class);
		// Account A's profile holds a snapshot…
		when(config.getRSProfileConfiguration("shortestpath", "lastSeenBankItems"))
			.thenReturn("13121:1");
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);
		service.loadFromProfile();
		assertEquals(Map.of(13121, 1), service.lastSeenBank());

		// …logging into account B (whose profile never stored one) must not
		// surface A's items: each load reads only the active profile's key.
		when(config.getRSProfileConfiguration("shortestpath", "lastSeenBankItems"))
			.thenReturn(null);
		service.loadFromProfile();

		assertEquals("another account's snapshot must not leak across profiles",
			Map.of(), service.lastSeenBank());
		verify(config, times(2))
			.getRSProfileConfiguration("shortestpath", "lastSeenBankItems");
	}

	@Test
	public void snapshotWritesGoThroughRsProfileNeverGlobalConfig()
	{
		ConfigManager config = mock(ConfigManager.class);
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);

		service.noteLastSeenBank(containerWith(items(13121)), true);

		verify(config).setRSProfileConfiguration(
			eq("shortestpath"), eq("lastSeenBankItems"), any());
		verify(config, never()).setConfiguration(anyString(), anyString(), anyString());
		verify(config, never()).setConfiguration(anyString(), anyString(), any(Object.class));
		verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), anyString());
		verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), any(Object.class));
	}

	@Test
	public void malformedProfileEntriesAreSkippedAndValidSiblingsSurvive()
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getRSProfileConfiguration("shortestpath", "lastSeenBankItems"))
			.thenReturn("abc,1:x,-5:2,:7,3:,4:0,13121:1, 4151:2 ");
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);

		service.loadFromProfile();

		assertEquals("only the well-formed entries survive",
			Map.of(13121, 1, 4151, 2), service.lastSeenBank());
		assertEquals("each skipped entry is logged once, and the sibling entries are not",
			6, log.messages().size());
		assertEquals("ignoring malformed lastSeenBankItems entry 'abc'", log.messages().get(0));

		when(config.getRSProfileConfiguration("shortestpath", "lastSeenBankItems"))
			.thenReturn("not a csv at all");
		service.loadFromProfile();
		assertEquals("whole-string garbage yields an empty snapshot, never a throw",
			Map.of(), service.lastSeenBank());
		assertEquals("the unparseable string is logged as one malformed entry",
			7, log.messages().size());
	}

	@Test
	public void recapturingIdenticalContentsSkipsTheRewrite()
	{
		ConfigManager config = mock(ConfigManager.class);
		ItemStateService service = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);

		service.noteLastSeenBank(containerWith(items(13121)), true);
		service.noteLastSeenBank(containerWith(items(13121)), true);

		verify(config, times(1))
			.setRSProfileConfiguration("shortestpath", "lastSeenBankItems", "13121:1");
	}

	@Test
	public void detachedServiceCapturesInMemoryOnlyAndNeverThrows()
	{
		ItemStateService service = ItemStateService.forTesting();

		service.noteLastSeenBank(containerWith(items(13121)), true);
		assertEquals(Map.of(13121, 1), service.lastSeenBank());

		// No config manager: the load clears to empty rather than throwing.
		service.loadFromProfile();
		assertEquals(Map.of(), service.lastSeenBank());
	}

	@Test
	public void resetDropsTheLiveBankButKeepsTheLastSeenSnapshot()
	{
		ItemStateService service = ItemStateService.forTesting();
		service.noteBankContainer(mock(ItemContainer.class));
		service.noteLastSeenBank(containerWith(items(13121)), true);

		service.reset();

		assertNull("reset clears the session-scoped live bank", service.getBank());
		assertEquals("the remembered snapshot survives by design",
			Map.of(13121, 1), service.lastSeenBank());
	}

	@Test
	public void lastSeenBankMapIsImmutablePerCall()
	{
		ItemStateService service = ItemStateService.forTesting();
		service.noteLastSeenBank(containerWith(items(13121)), true);

		try
		{
			service.lastSeenBank().put(999, 1);
			fail("the served snapshot must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
		}
		assertEquals("the rejected write left the snapshot untouched",
			Map.of(13121, 1), service.lastSeenBank());
	}

	@Test
	public void persistedCsvRoundTripsBackThroughLoad()
	{
		ConfigManager config = mock(ConfigManager.class);
		ItemStateService writer = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);
		writer.noteLastSeenBank(containerWith(new Item(13121, 2), new Item(4151, 3)), true);

		ArgumentCaptor<Object> written = ArgumentCaptor.forClass(Object.class);
		verify(config).setRSProfileConfiguration(
			eq("shortestpath"), eq("lastSeenBankItems"), written.capture());

		when(config.getRSProfileConfiguration("shortestpath", "lastSeenBankItems"))
			.thenReturn((String) written.getValue());
		ItemStateService reader = ItemStateService.forTesting(mock(RefreshCoordinator.class), config);
		reader.loadFromProfile();

		assertEquals(Map.of(13121, 2, 4151, 3), reader.lastSeenBank());
	}

	// ------------------------------------------------------------------
	// Bank-pickup projection — keyed on the item-state revision this service
	// bumps, plus (path identity, index, search identity)
	// ------------------------------------------------------------------

	private static PathfinderConfig pickupConfig()
	{
		PathfinderConfig config = mock(PathfinderConfig.class);
		// The bank-location set is only consulted to gate "is this a bank
		// step"; a real set keeps the compute seam reachable while the test
		// path deliberately misses it, yielding the empty result.
		when(config.getDestinations("bank")).thenReturn(Set.of(12345));
		return config;
	}

	private static ItemStateService serviceWithBank()
	{
		ItemStateService service = ItemStateService.forTesting();
		service.noteBankContainer(mock(ItemContainer.class));
		return service;
	}

	private static BankPickupAnalysis pickup(BankPickupProjection projection, ItemStateService service,
		PathfinderConfig config, List<PathStep> path, int pathIndex)
	{
		return projection.get(service, config, path, pathIndex, null);
	}

	@Test
	public void getBankPickupGuardsRejectBadInputs()
	{
		BankPickupProjection projection = new BankPickupProjection();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED));

		assertNull("no open bank means no projection",
			pickup(projection, ItemStateService.forTesting(), config, path, 0));

		ItemStateService service = serviceWithBank();
		assertNull("null path is rejected", pickup(projection, service, config, null, 0));
		assertNull("negative index is rejected", pickup(projection, service, config, path, -1));
		assertNull("out-of-range index is rejected",
			pickup(projection, service, config, path, path.size()));

		when(config.getDestinations("bank")).thenReturn(null);
		assertNull("a missing bank-destination set is rejected",
			pickup(projection, service, config, path, 0));
	}

	@Test
	public void bankPickupCacheHitsOnSamePathAndIndexIdentity()
	{
		BankPickupProjection projection = new BankPickupProjection();
		ItemStateService service = serviceWithBank();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED), new PathStep(888, BankVisitState.CARRIED));

		BankPickupAnalysis first = pickup(projection, service, config, path, 0);
		BankPickupAnalysis second = pickup(projection, service, config, path, 0);

		assertSame("same (revision, path identity, index, search) reuses the cached result",
			first, second);
	}

	@Test
	public void bankPickupCacheMissesOnDifferentIndexOrPathInstance()
	{
		BankPickupProjection projection = new BankPickupProjection();
		ItemStateService service = serviceWithBank();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED), new PathStep(888, BankVisitState.CARRIED));

		BankPickupAnalysis first = pickup(projection, service, config, path, 0);

		assertNotSame("a different index recomputes", first,
			pickup(projection, service, config, path, 1));

		List<PathStep> equalButSeparate = List.of(new PathStep(999, BankVisitState.CARRIED), new PathStep(888, BankVisitState.CARRIED));
		assertNotSame("an equal-but-separate path list recomputes — the key is identity",
			first, pickup(projection, service, config, equalButSeparate, 0));
	}

	@Test
	public void reseededBankForcesPickupRecompute()
	{
		BankPickupProjection projection = new BankPickupProjection();
		ItemStateService service = serviceWithBank();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED));

		BankPickupAnalysis first = pickup(projection, service, config, path, 0);
		service.noteBankContainer(mock(ItemContainer.class));

		assertNotSame("a re-seeded bank bumps the revision and defeats the cache",
			first, pickup(projection, service, config, path, 0));
	}

	@Test
	public void containerAndVarbitEventsDirtyThePickupCache()
	{
		BankPickupProjection projection = new BankPickupProjection();
		ItemStateService service = serviceWithBank();
		PathfinderConfig config = pickupConfig();
		List<PathStep> path = List.of(new PathStep(999, BankVisitState.CARRIED));

		BankPickupAnalysis first = pickup(projection, service, config, path, 0);
		service.onContainerChanged(InventoryID.INV, mock(ItemContainer.class));
		BankPickupAnalysis afterContainer = pickup(projection, service, config, path, 0);
		assertNotSame("a tracked container event recomputes", first, afterContainer);

		service.onVarbitChanged(OwnedItems.RUNE_POUCH_RUNE_VARBITS[0]);
		assertNotSame("a tracked varbit event recomputes",
			afterContainer, pickup(projection, service, config, path, 0));
	}
}
