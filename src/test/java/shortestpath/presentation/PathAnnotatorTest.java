package shortestpath.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathStep;
import shortestpath.poh.PohService;
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
}
