package shortestpath.overlay;

import static org.junit.Assert.assertEquals;

import java.util.List;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.poh.PohService;
import shortestpath.poh.HouseObject;
import shortestpath.poh.HouseScan;
import shortestpath.transport.Transport;

public class HouseObjectOverlayTest
{
	private static HouseObject object(int id)
	{
		return new HouseObject(null, id, HouseScan.serves(id));
	}

	@Test
	public void aPortalPrefersTheChamberPortalOverTheNexus()
	{
		HouseObject portal = object(ObjectID.POH_PORTAL_MARBLE_ARDOUGNE);
		HouseObject nexus = object(ObjectID.POH_NEXUS_PORTAL_2);
		List<HouseObject> house = List.of(nexus, portal);
		assertEquals(List.of(portal),
			HouseObjectOverlay.pick(house, HouseScan.targetsFor(ObjectID.POH_PORTAL_TEAK_ARDOUGNE)));
		assertEquals(List.of(nexus), HouseObjectOverlay.pick(List.of(nexus),
			HouseScan.targetsFor(ObjectID.POH_PORTAL_TEAK_ARDOUGNE)));
	}

	@Test
	public void anyBoxTierTakesABoxTeleport()
	{
		HouseObject box = object(ObjectID.POH_JEWELLERY_BOX_3);
		assertEquals(List.of(box), HouseObjectOverlay.pick(List.of(box), HouseScan.targetsFor(37492)));
	}

	@Test
	public void theSpiritualFairyTreeTakesBoth()
	{
		HouseObject tree = object(ObjectID.XMAS20_POH_SPIRIT_RING);
		assertEquals(List.of(tree), HouseObjectOverlay.pick(List.of(tree), HouseScan.targetsFor(ObjectID.POH_FAIRY_RING)));
		assertEquals(List.of(tree), HouseObjectOverlay.pick(List.of(tree), HouseScan.targetsFor(ObjectID.POH_SPIRIT_TREE)));
	}

	@Test
	public void theExitIsFoundWhereTheRouteReachesTheHouse()
	{
		// Planned outside: walk, teleport into the house, then leave it.
		List<PathStep> path = List.of(
			new PathStep(WorldPointUtil.packWorldPoint(3200, 3200, 0), null),
			new PathStep(WorldPointUtil.packWorldPoint(3201, 3200, 0), null),
			new PathStep(WorldPointUtil.packWorldPoint(PohService.POH_LANDING_X, PohService.POH_LANDING_Y, 0), null),
			new PathStep(WorldPointUtil.packWorldPoint(2662, 3305, 0), null));
		assertEquals(2, HouseObjectOverlay.firstHouseStep(path));
		assertEquals(-1, HouseObjectOverlay.firstHouseStep(path.subList(0, 2)));
	}

	@Test
	public void anUnreadVisitedNexusSaysWhatToCheck()
	{
		HouseObject nexus = object(ObjectID.POH_NEXUS_PORTAL_2);
		HouseObject box = object(ObjectID.POH_JEWELLERY_BOX_3);
		Transport barrows = new Transport.TransportBuilder().displayInfo("Barrows Portal").build();
		assertEquals("Open and close the Portal Nexus", HouseObjectOverlay.checkNexusFor(barrows, List.of(nexus), true));
		// Read already, or own house: no note.
		assertEquals(null, HouseObjectOverlay.checkNexusFor(barrows, List.of(nexus), false));
		// The route uses something else.
		assertEquals(null, HouseObjectOverlay.checkNexusFor(barrows, List.of(box), true));
		assertEquals(null, HouseObjectOverlay.checkNexusFor(null, List.of(nexus), true));
	}

	@Test
	public void aTeleportOnAnotherFloorIsNamed()
	{
		net.runelite.api.TileObject upstairs = org.mockito.Mockito.mock(net.runelite.api.TileObject.class);
		org.mockito.Mockito.when(upstairs.getPlane()).thenReturn(1);
		HouseObject nexus = new HouseObject(upstairs, ObjectID.POH_NEXUS_PORTAL_2, HouseScan.serves(ObjectID.POH_NEXUS_PORTAL_2));
		assertEquals("Open and close the Portal Nexus (upstairs)",
			HouseObjectOverlay.otherFloorNote(List.of(nexus), 0, HouseObjectOverlay.OPEN_AND_CLOSE));
		assertEquals("Use the Portal Nexus (downstairs)", HouseObjectOverlay.otherFloorNote(List.of(nexus), 2, null));
		assertEquals(null, HouseObjectOverlay.otherFloorNote(List.of(nexus), 1, null));
		assertEquals(null, HouseObjectOverlay.otherFloorNote(List.of(), 0, null));
	}

	@Test
	public void nothingMatchesNothing()
	{
		assertEquals(List.of(), HouseObjectOverlay.pick(List.of(object(ObjectID.POH_FAIRY_RING)),
			HouseScan.targetsFor(ObjectID.POH_WILDERNESS_OBELISK)));
		assertEquals(List.of(), HouseScan.targetsFor(-1));
	}
}
