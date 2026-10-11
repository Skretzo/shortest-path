package shortestpath.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import net.runelite.api.ItemContainer;

import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.items.ItemStateService;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.poh.PohService;
import shortestpath.presentation.BankPickupRequirements.BankPickupResult;
import shortestpath.requirement.TransportEligibility;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class PathAnnotatorTest
{
	private static final int LANDING = WorldPointUtil.packWorldPoint(1858, 7051, 0);

	private PohService pohService;
	private PathAnnotator annotator;

	@Before
	public void setUp()
	{
		pohService = PohService.forTesting();
		annotator = PathAnnotator.forTesting(pohService, null);
	}

	private static Transport transport(TransportType type, String displayInfo, String objectInfo, int destination)
	{
		return new Transport.TransportBuilder()
			.type(type)
			.displayInfo(displayInfo)
			.objectInfo(objectInfo)
			.destination(destination)
			.build();
	}

	@Test
	public void testFormatTransportDisplay()
	{
		pohService.putFromDialogLine("3: Lumbridge");

		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);
		assertEquals("3: Lumbridge Portal", annotator.formatTransportDisplay(portal));

		// Non-portal transports pass through untouched, keybinds or not.
		Transport ring = transport(TransportType.FAIRY_RING, "DIQ", null, 0);
		assertEquals("DIQ", annotator.formatTransportDisplay(ring));

		Transport unnamed = transport(TransportType.FAIRY_RING, null, null, 0);
		assertNull(annotator.formatTransportDisplay(unnamed));
	}

	@Test
	public void testGetPohExitInfo()
	{
		int inside1 = WorldPointUtil.packWorldPoint(1900, 7060, 0);
		int inside2 = WorldPointUtil.packWorldPoint(1901, 7060, 0);
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);

		// The exit step records the transport the search used, which is what
		// transportsForEdge resolves for the boundary edge — no engine config
		// needed.
		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);
		List<PathStep> path = List.of(
			new PathStep(outside, BankVisitState.CARRIED),
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, portal));
		assertEquals("Nexus: Lumbridge Portal",
			annotator.getPohExitInfo(LANDING, path, 0));

		Transport glory = transport(TransportType.TELEPORTATION_BOX, "Edgeville", "Mounted Amulet of Glory", 0);
		List<PathStep> gloryPath = List.of(
			new PathStep(outside, BankVisitState.CARRIED),
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, glory));
		assertEquals("Mounted Glory: Edgeville",
			annotator.getPohExitInfo(LANDING, gloryPath, 0));
	}

	@Test
	public void testGetPohExitInfoRejectedInputs()
	{
		int inside1 = WorldPointUtil.packWorldPoint(1900, 7060, 0);
		int inside2 = WorldPointUtil.packWorldPoint(1901, 7060, 0);
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);
		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);

		// A non-POH destination produces no exit info.
		List<PathStep> path = List.of(
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, portal));
		assertNull(annotator.getPohExitInfo(outside, path, 0));

		assertNull(annotator.getPohExitInfo(LANDING, null, 0));
		assertNull(annotator.getPohExitInfo(LANDING, path, -1));

		// A path that never crosses the boundary produces no exit info.
		List<PathStep> allInside = List.of(
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(inside1, BankVisitState.CARRIED));
		assertNull(annotator.getPohExitInfo(LANDING, allInside, 0));
	}

	// ---------------------------------------------------------------
	// Display facades — detached arm and attached delegation
	// ---------------------------------------------------------------

	@Test
	public void detachedAnnotatorAnswersEveryDisplayReadWithTheEmptyArm()
	{
		PathAnnotator detached = PathAnnotator.forTesting();

		assertNull(detached.getMap());
		assertNull(detached.getTransports());
		assertNull(detached.getAllDisplayTransports());
		assertNull(detached.getEligibility());
		assertNull(detached.getDestinations("fairy_ring"));
		assertFalse(detached.hasDestination("fairy_ring"));
		assertFalse(detached.isBankOpen());
		assertNull(detached.getBankPickup(List.of(), 0, null));
	}

	@Test
	public void displayFacadesDelegateToTheAttachedConfig()
	{
		PathfinderConfig config = mock(PathfinderConfig.class);
		CollisionMap map = mock(CollisionMap.class);
		PrimitiveIntHashMap<Transport[]> transports = new PrimitiveIntHashMap<>(4);
		PrimitiveIntHashMap<Transport[]> allTransports = new PrimitiveIntHashMap<>(4);
		// TransportEligibility is a final value type — a real empty snapshot
		// proves the pass-through just as well as a mock would.
		TransportEligibility eligibility =
			TransportEligibility.forPlayerAndBank(Map.of(), Map.of(), 0, Map.of());
		Set<Integer> destinations = Set.of(1, 2);
		when(config.getMap()).thenReturn(map);
		when(config.getTransports()).thenReturn(transports);
		when(config.getAllDisplayTransports()).thenReturn(allTransports);
		when(config.getEligibility()).thenReturn(eligibility);
		when(config.getDestinations("fairy_ring")).thenReturn(destinations);
		when(config.hasDestination("fairy_ring")).thenReturn(true);

		annotator.attach(config);

		assertSame(map, annotator.getMap());
		assertSame(transports, annotator.getTransports());
		assertSame(allTransports, annotator.getAllDisplayTransports());
		assertSame(eligibility, annotator.getEligibility());
		assertSame(destinations, annotator.getDestinations("fairy_ring"));
		assertTrue(annotator.hasDestination("fairy_ring"));
	}

	@Test
	public void isBankOpenAndBankPickupDelegateToItemState()
	{
		ItemStateService itemState = mock(ItemStateService.class);
		PathfinderConfig config = mock(PathfinderConfig.class);
		PathAnnotator wired = PathAnnotator.forTesting(pohService, itemState);
		wired.attach(config);

		assertFalse(wired.isBankOpen());

		ItemContainer bank = mock(ItemContainer.class);
		when(itemState.getBank()).thenReturn(bank);
		assertTrue(wired.isBankOpen());

		List<PathStep> path = List.of(new PathStep(LANDING, BankVisitState.CARRIED));
		BankPickupResult result = new BankPickupResult(List.of("a rope"), Set.of(954));
		when(itemState.getBankPickup(path, 0, config, null)).thenReturn(result);

		assertSame(result, wired.getBankPickup(path, 0, null));
	}
}
