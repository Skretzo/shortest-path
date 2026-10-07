package shortestpath.requirement;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ItemVariations;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.requirement.BankPickupRequirements.BankPickupResult;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Tests {@link BankPickupResult#compute} against eligibility snapshots: the pickup
 * phrases and bank item highlights read from the same evaluation the pathfinder used,
 * keyed by the transport recorded on each path step.
 */
@RunWith(MockitoJUnitRunner.class)
public class BankPickupRequirementsTest
{
	private static final int NO_POUCH = -1;
	private static final int[] NO_SUBSTITUTES = new int[0];
	private static final int BANK_TILE = WorldPointUtil.packWorldPoint(3100, 3500, 0);
	private static final int EDGE_ORIGIN = WorldPointUtil.packWorldPoint(3101, 3500, 0);
	private static final int EDGE_DESTINATION = WorldPointUtil.packWorldPoint(3102, 3500, 0);
	private static final int EDGE_DESTINATION_2 = WorldPointUtil.packWorldPoint(3103, 3500, 0);
	private static final Set<Integer> BANK_LOCATIONS = Set.of(BANK_TILE);
	private static final Transport LAW_ONLY = teleport(rune(ItemVariations.LAW_RUNE, 1));
	private static final Transport LAW_AND_AIR = teleport(
		rune(ItemVariations.LAW_RUNE, 1), airRuneWithStaff(3));
	private static final Transport FALADOR_TELEPORT = teleport(
		rune(ItemVariations.LAW_RUNE, 1), rune(ItemVariations.AIR_RUNE, 3), rune(ItemVariations.WATER_RUNE, 1));

	@Mock
	private Client client;
	@Mock
	private PathfinderConfig pathfinderConfig;
	@Mock
	private ItemContainer bank;
	@Mock
	private ItemComposition lawRune;
	@Mock
	private ItemComposition dramenStaff;
	@Mock
	private ItemComposition coins;
	@Mock
	private ItemComposition quetzalWhistle;
	@Mock
	private ItemComposition runePouch;
	@Mock
	private ItemComposition airRune;
	@Mock
	private ItemComposition mistRune;
	@Mock
	private ItemComposition rope;
	@Mock
	private ItemComposition tradeSticks;

	private final Map<Integer, Integer> playerHas = new HashMap<>();
	private final Map<Integer, Integer> bankHas = new HashMap<>();

	@Test
	public void recordedTransportProducesPickupPhraseDespiteFreeAlternative()
	{
		// The path records the law-requiring teleport it actually used; a free transport
		// sharing the destination must not suppress the pickup for the recorded one.
		bankHas.put(ItemID.LAWRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		// The leading walk edge carries no recorded transport, so compute still
		// consults the availability fallback for it.
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf(edge(TransportType.TRANSPORT, null), LAW_ONLY));
		when(client.getItemDefinition(ItemID.LAWRUNE)).thenReturn(lawRune);
		when(lawRune.getName()).thenReturn("Law rune");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false, LAW_ONLY));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("1 Law rune"), result.phrases);
		assertEquals(Set.of(ItemID.LAWRUNE), result.bankItemIds);
	}

	@Test
	public void identitylessStepWithFreeAlternativeProducesNothing()
	{
		// Without a recorded transport, destination-matched alternatives apply: any one
		// of them being free means no pickup for the edge.
		bankHas.put(ItemID.LAWRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf(
				edge(TransportType.TRANSPORT, null),
				edge(TransportType.TELEPORTATION_SPELL, LAW_ONLY.getItemRequirements())));

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertTrue(result.bankItemIds.isEmpty());
	}

	@Test
	public void bankShortProducesNoPhraseButStillHighlights()
	{
		// 1 banked air covers the carried shortfall but no water is anywhere, so the bank
		// cannot fully supply the teleport: no phrase, but the air is still highlighted.
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 2);
		bankHas.put(ItemID.AIRRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf());

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false, FALADOR_TELEPORT));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertEquals(Set.of(ItemID.AIRRUNE), result.bankItemIds);
	}

	@Test
	public void playerSatisfyingTheRecordedTransportNeedsNoPickup()
	{
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.AIRRUNE, 3);
		playerHas.put(ItemID.WATERRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf());

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false, FALADOR_TELEPORT));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertTrue(result.bankItemIds.isEmpty());
	}

	@Test
	public void fairyRingPathPicksUpTheBankedDramenStaff()
	{
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		bankHas.put(ItemID.DRAMEN_STAFF_AIR, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(true));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf());
		when(client.getItemDefinition(ItemID.DRAMEN_STAFF)).thenReturn(dramenStaff);
		when(dramenStaff.getName()).thenReturn("Dramen staff");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false,
				new Transport.TransportBuilder().type(TransportType.FAIRY_RING).build()));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("1 Dramen staff"), result.phrases);
		assertEquals(Set.of(ItemID.DRAMEN_STAFF, ItemID.DRAMEN_STAFF_AIR), result.bankItemIds);
	}

	@Test
	public void fairyRingPathNeedsNoStaffWhenTheDiaryIsComplete()
	{
		bankHas.put(ItemID.DRAMEN_STAFF, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf());

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false,
				new Transport.TransportBuilder().type(TransportType.FAIRY_RING).build()));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertTrue(result.bankItemIds.isEmpty());
	}

	@Test
	public void missingEligibilitySnapshotYieldsEmptyResult()
	{
		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false, LAW_ONLY));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertTrue(result.bankItemIds.isEmpty());
	}

	@Test
	public void awayFromABankStepYieldsEmptyResult()
	{
		List<PathStep> path = List.of(
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false, LAW_ONLY));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertTrue(result.bankItemIds.isEmpty());
	}

	@Test
	public void twoCoinFaresCombineIntoASinglePickup()
	{
		// Issue #636: two 3,000-coin fares must ask for the combined 6,000, not two
		// per-edge 3,000 phrases that dedupe into one.
		bankHas.put(ItemID.COINS, 10_000);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(client.getItemDefinition(ItemID.COINS)).thenReturn(coins);
		when(coins.getName()).thenReturn("Coins");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, coinFare(3_000)),
			new PathStep(EDGE_DESTINATION, false, coinFare(3_000)));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("Coins (6,000)"), result.phrases);
		assertEquals(Set.of(ItemID.COINS), result.bankItemIds);
	}

	@Test
	public void carriedCoinsTopUpTheCombinedFare()
	{
		playerHas.put(ItemID.COINS, 4_000);
		bankHas.put(ItemID.COINS, 10_000);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(client.getItemDefinition(ItemID.COINS)).thenReturn(coins);
		when(coins.getName()).thenReturn("Coins");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, coinFare(3_000)),
			new PathStep(EDGE_DESTINATION, false, coinFare(3_000)));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("Coins (2,000)"), result.phrases);
		assertEquals(Set.of(ItemID.COINS), result.bankItemIds);
	}

	@Test
	public void aSpentRuneIsCountedAcrossEdges()
	{
		// Carrying one law covers the first cast only; the second needs a bank pickup.
		playerHas.put(ItemID.LAWRUNE, 1);
		bankHas.put(ItemID.LAWRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(client.getItemDefinition(ItemID.LAWRUNE)).thenReturn(lawRune);
		when(lawRune.getName()).thenReturn("Law rune");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, LAW_ONLY),
			new PathStep(EDGE_DESTINATION, false, LAW_ONLY));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("1 Law rune"), result.phrases);
		assertEquals(Set.of(ItemID.LAWRUNE), result.bankItemIds);
	}

	@Test
	public void carriedRunesCoveringBothCastsProduceNoPickup()
	{
		playerHas.put(ItemID.LAWRUNE, 2);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, LAW_ONLY),
			new PathStep(EDGE_DESTINATION, false, LAW_ONLY));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertTrue(result.bankItemIds.isEmpty());
	}

	@Test
	public void aCarriedStaffSuppressesAirSpendAcrossCasts()
	{
		// The staff satisfies the air side of both casts without being consumed, so
		// only the law runes are spent: one carried cast, one bank pickup.
		playerHas.put(ItemID.STAFF_OF_AIR, 1);
		playerHas.put(ItemID.LAWRUNE, 1);
		bankHas.put(ItemID.LAWRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(client.getItemDefinition(ItemID.LAWRUNE)).thenReturn(lawRune);
		when(lawRune.getName()).thenReturn("Law rune");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, LAW_AND_AIR),
			new PathStep(EDGE_DESTINATION, false, LAW_AND_AIR));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("1 Law rune"), result.phrases);
		assertEquals(Set.of(ItemID.LAWRUNE), result.bankItemIds);
	}

	@Test
	public void unsuppliableSecondCastHighlightsWithoutAPhrase()
	{
		// The second cast cannot be paid: the bank law is highlighted even though
		// the missing water keeps the bank from fully supplying the edge.
		playerHas.put(ItemID.LAWRUNE, 1);
		playerHas.put(ItemID.WATERRUNE, 1);
		bankHas.put(ItemID.LAWRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false,
				teleport(rune(ItemVariations.LAW_RUNE, 1), rune(ItemVariations.WATER_RUNE, 1))),
			new PathStep(EDGE_DESTINATION, false,
				teleport(rune(ItemVariations.LAW_RUNE, 1), rune(ItemVariations.WATER_RUNE, 1))));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertEquals(Set.of(ItemID.LAWRUNE), result.bankItemIds);
	}

	@Test
	public void aNonConsumableToolCoversRepeatedEdges()
	{
		// A carried rope is not consumed by a non-consumable transport, so one rope
		// satisfies the gate twice and nothing is picked up.
		playerHas.put(ItemID.ROPE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false,
				edge(TransportType.TRANSPORT, singleItem(ItemID.ROPE, 1))),
			new PathStep(EDGE_DESTINATION, false,
				edge(TransportType.TRANSPORT, singleItem(ItemID.ROPE, 1))));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertTrue(result.bankItemIds.isEmpty());
	}

	@Test
	public void consumableWhistlesSpendAcrossEdges()
	{
		playerHas.put(ItemID.HG_QUETZALWHISTLE_BASIC, 1);
		bankHas.put(ItemID.HG_QUETZALWHISTLE_BASIC, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(client.getItemDefinition(ItemID.HG_QUETZALWHISTLE_BASIC)).thenReturn(quetzalWhistle);
		when(quetzalWhistle.getName()).thenReturn("Basic quetzal whistle");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, quetzalWhistle()),
			new PathStep(EDGE_DESTINATION, false, quetzalWhistle()));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("1 Basic quetzal whistle"), result.phrases);
		assertEquals(Set.of(ItemID.HG_QUETZALWHISTLE_BASIC), result.bankItemIds);
	}

	@Test
	public void aNonConsumableTeleportItemCoversRepeatedEdges()
	{
		playerHas.put(ItemID.AMULET_OF_GLORY, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false,
				edge(TransportType.TELEPORTATION_ITEM, singleItem(ItemID.AMULET_OF_GLORY, 1))),
			new PathStep(EDGE_DESTINATION, false,
				edge(TransportType.TELEPORTATION_ITEM, singleItem(ItemID.AMULET_OF_GLORY, 1))));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertTrue(result.phrases.isEmpty());
		assertTrue(result.bankItemIds.isEmpty());
	}

	@Test
	public void aMultiAlternativeEdgeKeepsItsOrGroup()
	{
		// The identity-less step falls back to destination-matched alternatives; two
		// bank-suppliable choices stay an "or" group at ledger-aware amounts.
		bankHas.put(ItemID.COINS, 10_000);
		bankHas.put(ItemID.LAWRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf(
				coinFare(3_000),
				edge(TransportType.TELEPORTATION_SPELL, LAW_ONLY.getItemRequirements())));
		when(client.getItemDefinition(ItemID.COINS)).thenReturn(coins);
		when(client.getItemDefinition(ItemID.LAWRUNE)).thenReturn(lawRune);
		when(coins.getName()).thenReturn("Coins");
		when(lawRune.getName()).thenReturn("Law rune");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(1, result.phrases.size());
		String group = result.phrases.get(0);
		assertTrue("unexpected alternatives group: " + group,
			"Coins (3,000) or 1 Law rune".equals(group)
				|| "1 Law rune or Coins (3,000)".equals(group));
		assertEquals(Set.of(ItemID.COINS, ItemID.LAWRUNE), result.bankItemIds);
	}

	@Test
	public void combinedSingletonPickupsLeadAlternativeGroups()
	{
		// A later single-alternative edge merges into the combined pickup map, which
		// renders as one leading phrase before any "or" groups.
		bankHas.put(ItemID.COINS, 10_000);
		bankHas.put(ItemID.LAWRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf(
				coinFare(3_000),
				edge(TransportType.TELEPORTATION_SPELL, LAW_ONLY.getItemRequirements())));
		when(client.getItemDefinition(ItemID.COINS)).thenReturn(coins);
		when(client.getItemDefinition(ItemID.LAWRUNE)).thenReturn(lawRune);
		when(coins.getName()).thenReturn("Coins");
		when(lawRune.getName()).thenReturn("Law rune");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false),
			new PathStep(EDGE_DESTINATION_2, false, coinFare(3_000)));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(2, result.phrases.size());
		assertEquals("Coins (3,000)", result.phrases.get(0));
		String group = result.phrases.get(1);
		assertTrue("unexpected alternatives group: " + group,
			"Coins (3,000) or 1 Law rune".equals(group)
				|| "1 Law rune or Coins (3,000)".equals(group));
	}

	@Test
	public void combinedPickupsCannotExceedWhatTheBankHolds()
	{
		// The bank holds one fare, not two: the second edge can no longer be
		// supplied once the first commits its withdrawal, so the hint asks only
		// for what is actually withdrawable.
		bankHas.put(ItemID.COINS, 3_000);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(client.getItemDefinition(ItemID.COINS)).thenReturn(coins);
		when(coins.getName()).thenReturn("Coins");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, coinFare(3_000)),
			new PathStep(EDGE_DESTINATION, false, coinFare(3_000)));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("Coins (3,000)"), result.phrases);
		assertEquals(Set.of(ItemID.COINS), result.bankItemIds);
	}

	@Test
	public void aCommittedBankPouchIsNotOfferedTwice()
	{
		// The banked pouch's 5 law runes are credited into the pool when its plan
		// commits; the second cast can draw the pool down to 2, but the pouch must
		// not be offered — or counted — again, so the real shortfall resolves to
		// the 2 loose laws the bank holds.
		bankHas.put(ItemID.BH_RUNE_POUCH, 1);
		bankHas.put(ItemID.LAWRUNE, 2);
		when(pathfinderConfig.getEligibility()).thenReturn(
			eligibility(false, ItemID.BH_RUNE_POUCH, Map.of(ItemID.LAWRUNE, 5)));
		when(client.getItemDefinition(ItemID.BH_RUNE_POUCH)).thenReturn(runePouch);
		when(client.getItemDefinition(ItemID.LAWRUNE)).thenReturn(lawRune);
		when(runePouch.getName()).thenReturn("Rune pouch");
		when(lawRune.getName()).thenReturn("Law rune");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, teleport(rune(ItemVariations.LAW_RUNE, 3))),
			new PathStep(EDGE_DESTINATION, false, teleport(rune(ItemVariations.LAW_RUNE, 4))));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("1 Rune pouch, 2 Law rune"), result.phrases);
		assertEquals(Set.of(ItemID.BH_RUNE_POUCH, ItemID.LAWRUNE), result.bankItemIds);
	}

	@Test
	public void aCommittedPouchFundsALaterPayableEdge()
	{
		// The pouch credit carries past the cast that needed it: the second cast
		// is payable straight from the leftover pouch runes, so the only pickup
		// is the pouch itself.
		bankHas.put(ItemID.BH_RUNE_POUCH, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(
			eligibility(false, ItemID.BH_RUNE_POUCH, Map.of(ItemID.LAWRUNE, 5)));
		when(client.getItemDefinition(ItemID.BH_RUNE_POUCH)).thenReturn(runePouch);
		when(runePouch.getName()).thenReturn("Rune pouch");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, teleport(rune(ItemVariations.LAW_RUNE, 3))),
			new PathStep(EDGE_DESTINATION, false, teleport(rune(ItemVariations.LAW_RUNE, 2))));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("1 Rune pouch"), result.phrases);
		assertEquals(Set.of(ItemID.BH_RUNE_POUCH), result.bankItemIds);
	}

	@Test
	public void nonCoinCurrenciesSpendAcrossEdges()
	{
		// Secondary currencies are spent like coins: the same banked stock of
		// trade sticks funds both fares and combines into a single amount.
		bankHas.put(ItemID.VILLAGE_TRADE_STICKS, 10);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(client.getItemDefinition(ItemID.VILLAGE_TRADE_STICKS)).thenReturn(tradeSticks);
		when(tradeSticks.getName()).thenReturn("Trading sticks");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false,
				edge(TransportType.TRANSPORT, singleItem(ItemID.VILLAGE_TRADE_STICKS, 4))),
			new PathStep(EDGE_DESTINATION, false,
				edge(TransportType.TRANSPORT, singleItem(ItemID.VILLAGE_TRADE_STICKS, 4))));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("Trading sticks (8)"), result.phrases);
		assertEquals(Set.of(ItemID.VILLAGE_TRADE_STICKS), result.bankItemIds);
	}

	@Test
	public void aMidPathBankVisitDoesNotRefillTheHintPool()
	{
		// The hint models one withdrawal session at the bank it is shown at: the
		// bank-visited step selects the banked availability view but does not top
		// the pool back up, so the rope a later bank could supply is still asked
		// for at the first one.
		playerHas.put(ItemID.COINS, 3_000);
		bankHas.put(ItemID.ROPE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf());
		when(client.getItemDefinition(ItemID.ROPE)).thenReturn(rope);
		when(rope.getName()).thenReturn("Rope");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, coinFare(3_000)),
			new PathStep(EDGE_DESTINATION, true),
			new PathStep(EDGE_DESTINATION_2, false,
				edge(TransportType.TRANSPORT, singleItem(ItemID.ROPE, 1))));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("1 Rope"), result.phrases);
		assertEquals(Set.of(ItemID.ROPE), result.bankItemIds);
	}

	@Test
	public void identicalOrGroupsOnDifferentEdgesAreNotDeduped()
	{
		// Two edges each offering the same pair of bank-suppliable fares must both
		// render their "or" group — each is a separate choice the player satisfies.
		bankHas.put(ItemID.COINS, 20_000);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(pathfinderConfig.getTransportAvailability(anyBoolean())).thenReturn(
			TestPathfinderConfig.availabilityOf(
				coinFare(3_000, BANK_TILE, EDGE_ORIGIN),
				coinFare(3_000, BANK_TILE, EDGE_ORIGIN),
				coinFare(3_000, EDGE_ORIGIN, EDGE_DESTINATION),
				coinFare(3_000, EDGE_ORIGIN, EDGE_DESTINATION)));
		when(client.getItemDefinition(ItemID.COINS)).thenReturn(coins);
		when(coins.getName()).thenReturn("Coins");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false),
			new PathStep(EDGE_DESTINATION, false));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("Coins (3,000) or Coins (3,000)", "Coins (3,000) or Coins (3,000)"),
			result.phrases);
		assertEquals(Set.of(ItemID.COINS), result.bankItemIds);
	}

	@Test
	public void variantResolvedPickupsKeepSeparateDisplayIds()
	{
		// The combined phrase merges per displayed item id: the first cast tops up
		// the carried mist rune while the second resolves canonical air, so the two
		// distinct bank withdrawals stay two distinct entries.
		playerHas.put(ItemID.MISTRUNE, 1);
		bankHas.put(ItemID.MISTRUNE, 2);
		bankHas.put(ItemID.AIRRUNE, 3);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
		when(client.getItemDefinition(ItemID.MISTRUNE)).thenReturn(mistRune);
		when(client.getItemDefinition(ItemID.AIRRUNE)).thenReturn(airRune);
		when(mistRune.getName()).thenReturn("Mist rune");
		when(airRune.getName()).thenReturn("Air rune");

		List<PathStep> path = List.of(
			new PathStep(BANK_TILE, false),
			new PathStep(EDGE_ORIGIN, false, teleport(rune(ItemVariations.AIR_RUNE, 3))),
			new PathStep(EDGE_DESTINATION, false, teleport(rune(ItemVariations.AIR_RUNE, 3))));

		BankPickupResult result = BankPickupResult.compute(
			client, bank, pathfinderConfig, BANK_LOCATIONS, path, 0);

		assertEquals(List.of("2 Mist rune, 3 Air rune"), result.phrases);
		assertEquals(Set.of(ItemID.MISTRUNE, ItemID.AIRRUNE), result.bankItemIds);
	}

	@Test
	public void formatPickupsNamesQuantitiesAndCurrencyAmounts()
	{
		when(client.getItemDefinition(ItemID.LAWRUNE)).thenReturn(lawRune);
		when(client.getItemDefinition(ItemID.COINS)).thenReturn(coins);
		when(lawRune.getName()).thenReturn("Law rune");
		when(coins.getName()).thenReturn("Coins");
		Map<Integer, Long> pickups = new LinkedHashMap<>();
		pickups.put(ItemID.LAWRUNE, 2L);
		pickups.put(ItemID.COINS, 5L);

		assertEquals("2 Law rune, Coins (5)", BankPickupRequirements.formatPickups(client, pickups));
	}

	private TransportEligibility eligibility(boolean fairyRingStaffRequired)
	{
		return eligibility(fairyRingStaffRequired, NO_POUCH, Map.of());
	}

	private TransportEligibility eligibility(boolean fairyRingStaffRequired, int bankPouchId,
		Map<Integer, Integer> bankPouchRunes)
	{
		Map<Integer, Integer> bankPathItems = new HashMap<>(playerHas);
		bankHas.forEach((itemId, quantity) -> bankPathItems.merge(itemId, quantity, Integer::sum));
		return new TransportEligibility(playerHas, bankPathItems, bankHas, bankPouchId, bankPouchRunes,
			fairyRingStaffRequired, TeleportationItem.NONE, Integer.MAX_VALUE, Set.of());
	}

	private static ItemRequirement rune(ItemVariations rune, int quantity)
	{
		return new ItemRequirement(rune.getIds(), NO_SUBSTITUTES, NO_SUBSTITUTES, quantity);
	}

	private static ItemRequirement airRuneWithStaff(int quantity)
	{
		return new ItemRequirement(ItemVariations.AIR_RUNE.getIds(),
			ItemVariations.STAFF_OF_AIR.getIds(), NO_SUBSTITUTES, quantity);
	}

	private static TransportItems singleItem(int itemId, int quantity)
	{
		return new TransportItems(List.of(
			new ItemRequirement(new int[]{itemId}, NO_SUBSTITUTES, NO_SUBSTITUTES, quantity)));
	}

	private static Transport coinFare(int coins)
	{
		return coinFare(coins, EDGE_ORIGIN, EDGE_DESTINATION);
	}

	private static Transport coinFare(int coins, int origin, int destination)
	{
		return edge(TransportType.TRANSPORT, singleItem(ItemID.COINS, coins), origin, destination);
	}

	private static Transport quetzalWhistle()
	{
		return new Transport.TransportBuilder()
			.type(TransportType.QUETZAL_WHISTLE)
			.origin(EDGE_ORIGIN)
			.destination(EDGE_DESTINATION)
			.isConsumable(true)
			.itemRequirements(singleItem(ItemID.HG_QUETZALWHISTLE_BASIC, 1))
			.build();
	}

	private static Transport teleport(ItemRequirement... requirements)
	{
		return new Transport.TransportBuilder()
			.type(TransportType.TELEPORTATION_SPELL)
			.itemRequirements(new TransportItems(List.of(requirements)))
			.build();
	}

	private static Transport edge(TransportType type, TransportItems requirements)
	{
		return edge(type, requirements, EDGE_ORIGIN, EDGE_DESTINATION);
	}

	private static Transport edge(TransportType type, TransportItems requirements, int origin, int destination)
	{
		Transport.TransportBuilder builder = new Transport.TransportBuilder()
			.type(type)
			.origin(origin)
			.destination(destination);
		if (requirements != null)
		{
			builder.itemRequirements(requirements);
		}
		return builder.build();
	}
}
