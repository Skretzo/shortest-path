package shortestpath.transport;

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
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TestPathfinderConfig;
import shortestpath.transport.BankPickupRequirements.BankPickupResult;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
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
	private static final Set<Integer> BANK_LOCATIONS = Set.of(BANK_TILE);
	private static final Transport LAW_ONLY = teleport(rune(ItemVariations.LAW_RUNE, 1));
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

	private final Map<Integer, Integer> playerHas = new HashMap<>();
	private final Map<Integer, Integer> bankHas = new HashMap<>();

	@Test
	public void recordedTransportProducesPickupPhraseDespiteFreeAlternative()
	{
		// The path records the law-requiring teleport it actually used; a free transport
		// sharing the destination must not suppress the pickup for the recorded one.
		bankHas.put(ItemID.LAWRUNE, 1);
		when(pathfinderConfig.getEligibility()).thenReturn(eligibility(false));
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
		Map<Integer, Integer> bankPathItems = new HashMap<>(playerHas);
		bankHas.forEach((itemId, quantity) -> bankPathItems.merge(itemId, quantity, Integer::sum));
		return new TransportEligibility(playerHas, bankPathItems, bankHas, NO_POUCH, Map.of(),
			fairyRingStaffRequired, TeleportationItem.NONE, Integer.MAX_VALUE, Set.of());
	}

	private static ItemRequirement rune(ItemVariations rune, int quantity)
	{
		return new ItemRequirement(rune.getIds(), NO_SUBSTITUTES, NO_SUBSTITUTES, quantity);
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
		Transport.TransportBuilder builder = new Transport.TransportBuilder()
			.type(type)
			.origin(EDGE_ORIGIN)
			.destination(EDGE_DESTINATION);
		if (requirements != null)
		{
			builder.itemRequirements(requirements);
		}
		return builder.build();
	}
}
