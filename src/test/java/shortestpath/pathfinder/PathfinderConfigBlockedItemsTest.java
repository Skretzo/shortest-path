package shortestpath.pathfinder;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ItemVariations;
import shortestpath.ShortestPathConfig;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Per-item teleport restrictions from the hidden {@code blockedTeleportItems}
 * config key: a bare {@code id} record blocks the item from routing and an
 * {@code id:N} record overrides the tiles-saved threshold for transports that
 * reference it. The block gate sits in {@code checkTeleportationItemRules} —
 * the candidate-set filter — so all nine {@link TeleportationItem} modes honour
 * it: the {@code ALL}/{@code UNLOCKED} modes short-circuit item evaluation
 * entirely and would otherwise route around a block.
 */
@RunWith(MockitoJUnitRunner.class)
public class PathfinderConfigBlockedItemsTest
{
	// Item ids used by the synthetic transports below; they mirror real TSV
	// rows so the grammar under test is the same one the loader parses.
	private static final int ARDY_CLOAK_1 = 13121;
	private static final int ARDY_CLOAK_2 = 13122;
	private static final int ARDY_CLOAK_3 = 13123;
	private static final int ARDY_CLOAK_4 = 13124;
	private static final int ARDY_CLOAK_MAX = 20760;
	private static final int KARAMJA_GLOVES_4 = 13103;
	private static final int XERICS_TALISMAN = 13393;
	private static final int QUETZAL_WHISTLE_ITEM = 29271;
	private static final int SEASONAL_BRIEFCASE = 30361;

	// Five OR alternatives, mirroring the real Ardougne cloak monastery row.
	private static final Transport ARDY_CLOAK = teleport(
		"Ardougne cloak: Kandarin Monastery",
		"13121=1|13122=1|13123=1|13124=1|20760=1");
	private static final Transport KARAMJA_GLOVES = teleport(
		"Karamja gloves: Duradel", "13103=1");
	private static final Transport TWO_AND = teleport(
		"Two required items", "13121=1&13122=1");
	private static final Transport ITEM_AND_UNLOCK = teleport(
		"Xeric's talisman: Honour", "13393=1&UNLOCK_XERICS_HONOUR=1");
	private static final Transport ITEM_OR_UNLOCK = teleport(
		"Item or unlock", "13121=1|UNLOCK_XERICS_HONOUR=1");
	private static final Transport PURE_UNLOCK = teleport(
		"Unlock only", "UNLOCK_XERICS_HONOUR=1");
	private static final Transport RUNE_TELE = teleport(
		"Rune-substitute tele", "FIRE_RUNE=1");
	private static final Transport NO_REQUIREMENTS = teleport(
		"No requirements", null);
	private static final Transport CONSUMABLE_TELE = teleport(
		"Consumable", "13121=1", true);
	// Consumable so the whistle exercises the stacked
	// type-cost + consumable-cost pricing path.
	private static final Transport WHISTLE = new Transport.TransportBuilder()
		.type(TransportType.QUETZAL_WHISTLE)
		.destination("2607 3221 0")
		.displayInfo("Quetzal whistle")
		.itemRequirements("29271=1")
		.isConsumable(true)
		.build();
	// The destination sits in a region with no league mapping, which classifies
	// as the always-unlocked NEUTRAL region for the league-mode gate.
	private static final Transport SEASONAL = transport(TransportType.SEASONAL_TRANSPORTS,
		"League briefcase", "30361=1", "32767 32767 0");
	private static final Transport BOAT_ON_GLOVES = transport(TransportType.BOAT,
		"Charter ship", "13103=1");
	private static final Transport BOAT_ON_CLOAK = transport(TransportType.BOAT,
		"Charter ship", "13121=1");

	@Mock
	private Client client;
	@Mock
	private ShortestPathConfig config;
	@Mock
	private ItemContainer inventory;

	@Test
	public void emptyCsvLeavesTeleportItemsUsable()
	{
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "", ARDY_CLOAK, KARAMJA_GLOVES);

		assertTrue(usable(cfg, ARDY_CLOAK, false));
		assertTrue(usable(cfg, KARAMJA_GLOVES, false));
	}

	@Test
	public void blockedItemRejectedUnderEveryTeleportationItemMode()
	{
		for (TeleportationItem mode : TeleportationItem.values())
		{
			PathfinderConfig cfg = refreshConfig(mode, "13103",
				items(KARAMJA_GLOVES_4, ARDY_CLOAK_1), KARAMJA_GLOVES, ARDY_CLOAK);

			assertFalse("blocked item must not route under " + mode,
				usable(cfg, KARAMJA_GLOVES, false));
			assertFalse("blocked item must not route under " + mode + " on the bank path",
				usable(cfg, KARAMJA_GLOVES, true));
		}
	}

	@Test
	public void unblockedTransportSurvivesUnderEveryTeleportationItemMode()
	{
		for (TeleportationItem mode : TeleportationItem.values())
		{
			PathfinderConfig cfg = refreshConfig(mode, "13103",
				items(KARAMJA_GLOVES_4, ARDY_CLOAK_1), KARAMJA_GLOVES, ARDY_CLOAK);

			// NONE rejects every teleport item transport regardless of blocking;
			// the other eight modes keep the unblocked sibling usable.
			assertEquals("mode " + mode, mode != TeleportationItem.NONE,
				usable(cfg, ARDY_CLOAK, false));
		}
	}

	@Test
	public void orRequirementSurvivesWhenOnlyOneBranchBlocked()
	{
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121", ARDY_CLOAK);

		assertTrue(usable(cfg, ARDY_CLOAK, false));
	}

	@Test
	public void orRequirementRejectedWhenEveryBranchBlocked()
	{
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL,
			"13121,13122,13123,13124,20760", ARDY_CLOAK);

		assertFalse(usable(cfg, ARDY_CLOAK, false));
	}

	@Test
	public void andRequirementRejectedWhenOneTermFullyBlocked()
	{
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121", TWO_AND);

		assertFalse(usable(cfg, TWO_AND, false));
	}

	@Test
	public void andRequirementSurvivesWhenEachTermHasLiveBranch()
	{
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "99999", TWO_AND);

		assertTrue(usable(cfg, TWO_AND, false));
	}

	@Test
	public void survivingUnlockTermDoesNotRescueBlockedAndTerm()
	{
		when(config.unlockXericsHonour()).thenReturn(true);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13393", ITEM_AND_UNLOCK);

		// The UNLOCK_XERICS_HONOUR requirement survives blocking, but the
		// 13393 requirement is fully blocked, so the transport is rejected.
		assertFalse(usable(cfg, ITEM_AND_UNLOCK, false));
	}

	@Test
	public void unlockOrBranchIsNeverBlockable()
	{
		// The item branch is blocked, but the unlock-only branch carries no
		// item ids and can never be blocked, so the requirement stays alive.
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121", ITEM_OR_UNLOCK);

		assertTrue(usable(cfg, ITEM_OR_UNLOCK, false));
	}

	@Test
	public void unlockOrBranchPassesGateButItemCheckStillApplies()
	{
		// INVENTORY mode: the gate lets the transport through because the
		// unlock branch is unblockable, then normal item evaluation fails —
		// the player owns nothing and has not declared the unlock.
		PathfinderConfig cfg = refreshConfig(TeleportationItem.INVENTORY, "13121", ITEM_OR_UNLOCK);

		assertFalse(usable(cfg, ITEM_OR_UNLOCK, false));
	}

	@Test
	public void pureUnlockRequirementCannotBeBlocked()
	{
		when(config.unlockXericsHonour()).thenReturn(true);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13393,13121", PURE_UNLOCK);

		assertTrue(usable(cfg, PURE_UNLOCK, false));
	}

	@Test
	public void branchSurvivesOnUnblockedStaffOrOffhandSubstitute()
	{
		// Only the rune item ids are blocked; staff and offhand substitutes of
		// the same branch keep it alive.
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL,
			csvOf(ItemVariations.FIRE_RUNE.getIds()), RUNE_TELE);

		assertTrue(usable(cfg, RUNE_TELE, false));
	}

	@Test
	public void branchBlockedOnlyWhenEverySubstituteBlocked()
	{
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL,
			csvOf(ItemVariations.FIRE_RUNE.getIds(),
				ItemVariations.staves(ItemVariations.FIRE_RUNE),
				ItemVariations.offhands(ItemVariations.FIRE_RUNE)),
			RUNE_TELE);

		assertFalse(usable(cfg, RUNE_TELE, false));
	}

	@Test
	public void malformedCsvEntriesAreSkippedNotFatal()
	{
		// "abc" and "13103:x" are malformed and skipped; the bare "13103"
		// record still blocks the gloves.
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL,
			"abc," + KARAMJA_GLOVES_4 + ":x," + KARAMJA_GLOVES_4, KARAMJA_GLOVES, ARDY_CLOAK);

		assertFalse(usable(cfg, KARAMJA_GLOVES, false));
		assertTrue(usable(cfg, ARDY_CLOAK, false));
	}

	@Test
	public void entirelyMalformedCsvBlocksNothing()
	{
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "abc,1:2:3,,:x",
			KARAMJA_GLOVES, ARDY_CLOAK);

		assertTrue(usable(cfg, KARAMJA_GLOVES, false));
		assertTrue(usable(cfg, ARDY_CLOAK, false));
	}

	@Test
	public void nonTeleportTypesIgnoreTheBlockList()
	{
		// The same blocked id gates the teleport item but must never consult a
		// boat's item requirement.
		when(config.useBoats()).thenReturn(true);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13103",
			items(KARAMJA_GLOVES_4), BOAT_ON_GLOVES, KARAMJA_GLOVES);

		assertTrue(usable(cfg, BOAT_ON_GLOVES, false));
		assertFalse(usable(cfg, KARAMJA_GLOVES, false));
	}

	@Test
	public void nullItemRequirementsUnaffectedByBlockList()
	{
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121", NO_REQUIREMENTS);

		assertTrue(usable(cfg, NO_REQUIREMENTS, false));
	}

	@Test
	public void seasonalTransportsHonourTheBlockList()
	{
		when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));
		when(config.useSeasonalTransports()).thenReturn(true);

		PathfinderConfig unblocked = refreshConfig(TeleportationItem.ALL, "", SEASONAL);
		assertTrue("seasonal transport must be usable before blocking",
			usable(unblocked, SEASONAL, false));

		PathfinderConfig blocked = refreshConfig(TeleportationItem.ALL, "30361", SEASONAL);
		assertFalse(usable(blocked, SEASONAL, false));
	}

	@Test
	public void quetzalWhistlesHonourTheBlockList()
	{
		when(config.useQuetzals()).thenReturn(true);

		PathfinderConfig unblocked = refreshConfig(TeleportationItem.ALL, "", WHISTLE);
		assertTrue(usable(unblocked, WHISTLE, false));

		PathfinderConfig blocked = refreshConfig(TeleportationItem.ALL, "29271", WHISTLE);
		assertFalse(usable(blocked, WHISTLE, false));
	}

	@Test
	public void inventoryModeSurvivesOnOwnedUnblockedAlternative()
	{
		// Only the cloak-1 branch is blocked; the owned cloak-2 branch keeps
		// the transport usable — a coarser flattened-id check would reject it.
		PathfinderConfig cfg = refreshConfig(TeleportationItem.INVENTORY, "13121",
			items(ARDY_CLOAK_2), ARDY_CLOAK);

		assertTrue(usable(cfg, ARDY_CLOAK, false));
	}

	@Test
	public void thresholdOverrideReplacesWholeTypeCost()
	{
		when(config.costNonConsumableTeleportationItems()).thenReturn(10);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121:40", ARDY_CLOAK);

		// id:N is a threshold record, not a block record — the transport must
		// stay usable and its additional cost is the pinned value.
		assertTrue(usable(cfg, ARDY_CLOAK, false));
		assertEquals(40, cfg.getAdditionalTransportCost(ARDY_CLOAK));
	}

	@Test
	public void zeroThresholdFallsBackToTypeCost()
	{
		when(config.costNonConsumableTeleportationItems()).thenReturn(10);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121:0", ARDY_CLOAK);

		assertEquals(10, cfg.getAdditionalTransportCost(ARDY_CLOAK));
	}

	@Test
	public void maxThresholdWinsAcrossMatchingMemberIds()
	{
		when(config.costNonConsumableTeleportationItems()).thenReturn(10);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121:30,13122:50", ARDY_CLOAK);

		assertEquals(50, cfg.getAdditionalTransportCost(ARDY_CLOAK));
	}

	@Test
	public void nonMatchingThresholdLeavesTypeCost()
	{
		when(config.costNonConsumableTeleportationItems()).thenReturn(10);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "99999:40", ARDY_CLOAK);

		assertEquals(10, cfg.getAdditionalTransportCost(ARDY_CLOAK));
	}

	@Test
	public void thresholdOverrideIsWholeCostForConsumables()
	{
		when(config.costConsumableTeleportationItems()).thenReturn(7);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121:40", CONSUMABLE_TELE);

		// The pinned tiles-saved is the whole additional cost — it replaces
		// consumable-item pricing rather than stacking on top of it.
		assertEquals(40, cfg.getAdditionalTransportCost(CONSUMABLE_TELE));
	}

	@Test
	public void consumableWhistleThresholdReplacesStackedCost()
	{
		when(config.costQuetzals()).thenReturn(5);
		when(config.costConsumableTeleportationItems()).thenReturn(7);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "29271:40", WHISTLE);

		// Without the override this whistle costs 5 + 7 = 12; the pinned
		// override is the whole cost for the item, not an added component.
		assertEquals(40, cfg.getAdditionalTransportCost(WHISTLE));
	}

	@Test
	public void whistleWithoutOverrideKeepsStackedCost()
	{
		when(config.costQuetzals()).thenReturn(5);
		when(config.costConsumableTeleportationItems()).thenReturn(7);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "", WHISTLE);

		assertEquals(12, cfg.getAdditionalTransportCost(WHISTLE));
	}

	@Test
	public void seasonalThresholdOverrideApplies()
	{
		when(config.costSeasonalTransports()).thenReturn(4);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "30361:25", SEASONAL);

		assertEquals(25, cfg.getAdditionalTransportCost(SEASONAL));
	}

	@Test
	public void staffAndOffhandMemberIdsCarryThresholds()
	{
		when(config.costNonConsumableTeleportationItems()).thenReturn(10);
		// A threshold pinned on a staff substitute id applies too — substitutes
		// are member ids of the transport's item requirement.
		int staffId = ItemVariations.staves(ItemVariations.FIRE_RUNE)[0];
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, staffId + ":33", RUNE_TELE);

		assertEquals(33, cfg.getAdditionalTransportCost(RUNE_TELE));
	}

	@Test
	public void thresholdOverrideDoesNotReachNonTeleportTypes()
	{
		when(config.costBoats()).thenReturn(3);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL, "13121:40", BOAT_ON_CLOAK);

		assertEquals(3, cfg.getAdditionalTransportCost(BOAT_ON_CLOAK));
	}

	@Test
	public void malformedThresholdRecordsAreSkipped()
	{
		when(config.costNonConsumableTeleportationItems()).thenReturn(10);
		PathfinderConfig cfg = refreshConfig(TeleportationItem.ALL,
			"13121:abc,13122:50", ARDY_CLOAK);

		assertEquals(50, cfg.getAdditionalTransportCost(ARDY_CLOAK));
	}

	private static Transport teleport(String displayInfo, String items)
	{
		return teleport(displayInfo, items, false);
	}

	private static Transport teleport(String displayInfo, String items, boolean consumable)
	{
		Transport.TransportBuilder builder = new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_ITEM)
			.destination("2607 3221 0")
			.displayInfo(displayInfo)
			.isConsumable(consumable);
		if (items != null)
		{
			builder.itemRequirements(items);
		}
		return builder.build();
	}

	private static Transport transport(TransportType type, String displayInfo, String items)
	{
		return transport(type, displayInfo, items, "2607 3221 0");
	}

	private static Transport transport(TransportType type, String displayInfo, String items,
		String destination)
	{
		Transport.TransportBuilder builder = new Transport.TransportBuilder()
			.type(type)
			.destination(destination)
			.displayInfo(displayInfo);
		if (items != null)
		{
			builder.itemRequirements(items);
		}
		return builder.build();
	}

	private static Item[] items(int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 1);
		}
		return items;
	}

	private static String csvOf(int[]... idArrays)
	{
		StringBuilder sb = new StringBuilder();
		for (int[] ids : idArrays)
		{
			if (ids == null)
			{
				continue;
			}
			for (int id : ids)
			{
				if (sb.length() > 0)
				{
					sb.append(',');
				}
				sb.append(id);
			}
		}
		return sb.toString();
	}

	private PathfinderConfig refreshConfig(TeleportationItem mode, String blockedCsv,
		Transport... transports)
	{
		return refreshConfig(mode, blockedCsv, null, transports);
	}

	private PathfinderConfig refreshConfig(TeleportationItem mode, String blockedCsv,
		Item[] inventoryItems, Transport... transports)
	{
		when(config.calculationCutoff()).thenReturn(30);
		when(config.currencyThreshold()).thenReturn(10_000_000);
		when(config.pohNexusPortals()).thenReturn(Collections.emptySet());
		when(config.useTeleportationItems()).thenReturn(mode);
		// Lenient so the stub is valid both before and after refresh() reads
		// the key — strict-stub reporting must not mask a behavioural failure.
		lenient().when(config.blockedTeleportItems()).thenReturn(blockedCsv);
		when(client.getDBTableRows(DBTableID.Quest.ID)).thenReturn(List.of());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getClientThread()).thenReturn(Thread.currentThread());
		when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
		if (inventoryItems != null)
		{
			doReturn(inventory).when(client).getItemContainer(InventoryID.INV);
			when(inventory.getItems()).thenReturn(inventoryItems);
		}
		PathfinderConfig cfg = new BlockedItemsPathfinderConfig(client, config, transports);
		cfg.refresh();
		return cfg;
	}

	private static boolean usable(PathfinderConfig cfg, Transport transport, boolean bankVisited)
	{
		for (Transport t : cfg.getUsableTeleports(bankVisited))
		{
			if (t == transport)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * PathfinderConfig over a synthetic transport set. {@code mapData} is null
	 * because the {@code CollisionMap} thread-local only materializes through
	 * {@code getMap()}, which {@code refresh()} never touches.
	 */
	private static final class BlockedItemsPathfinderConfig extends PathfinderConfig
	{
		BlockedItemsPathfinderConfig(Client client, ShortestPathConfig config, Transport... transports)
		{
			super(client, config, null,
				Map.of(Transport.UNDEFINED_ORIGIN, Set.of(transports)),
				Map.of(), Map.of(), Map.of());
		}

		@Override
		public QuestState getQuestState(Quest quest)
		{
			return QuestState.FINISHED;
		}

		@Override
		public boolean varbitChecks(Transport transport, long evaluationTimeMinutes)
		{
			return false;
		}

		@Override
		public boolean varPlayerChecks(Transport transport, long evaluationTimeMinutes)
		{
			return false;
		}
	}
}
