package shortestpath.overlay;

import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathStep;
import shortestpath.transport.Transport;

public class TransportObjectOverlayTest
{
	private static final Transport LADDER = new Transport.TransportBuilder()
		.origin(WorldPointUtil.packWorldPoint(3001, 3000, 0))
		.destination(WorldPointUtil.packWorldPoint(3001, 3000, 1))
		.objectInfo("Climb-up Ladder 16683")
		.build();

	private static PathStep walk(int x, int y, int plane)
	{
		return new PathStep(WorldPointUtil.packWorldPoint(x, y, plane), BankVisitState.CARRIED);
	}

	private static PathStep arrive(int x, int y, int plane, Transport transport)
	{
		return new PathStep(WorldPointUtil.packWorldPoint(x, y, plane), BankVisitState.CARRIED, transport);
	}

	@Test
	public void nextTransportIsTheFirstTransportAfterThePlayer()
	{
		List<PathStep> path = Arrays.asList(walk(3000, 3000, 0), walk(3001, 3000, 0),
			arrive(3001, 3000, 1, LADDER), walk(3002, 3000, 1));
		assertSame(LADDER, TransportObjectOverlay.nextTransport(path, 0));
		assertSame(LADDER, TransportObjectOverlay.nextTransport(path, 1));
	}

	@Test
	public void noNextTransportOnceItIsUsed()
	{
		List<PathStep> path = Arrays.asList(walk(3001, 3000, 0), arrive(3001, 3000, 1, LADDER),
			walk(3002, 3000, 1));
		assertNull(TransportObjectOverlay.nextTransport(path, 1));
	}

	@Test
	public void objectIdIsTheLastWordWhenItIsANumber()
	{
		assertEquals(16683, TransportObjectOverlay.objectId("Climb-up Ladder 16683"));
		assertEquals(37321, TransportObjectOverlay.objectId("Open Door 37321"));
		assertEquals(1330, TransportObjectOverlay.objectId("Charter Trader Crewmember 1330"));
	}

	@Test
	public void noObjectIdWithoutATrailingNumber()
	{
		assertEquals(-1, TransportObjectOverlay.objectId(null));
		assertEquals(-1, TransportObjectOverlay.objectId(""));
		assertEquals(-1, TransportObjectOverlay.objectId("16683"));
		assertEquals(-1, TransportObjectOverlay.objectId("Climb-up Staircase"));
		assertEquals(-1, TransportObjectOverlay.objectId("Climb-up Ladder "));
		assertEquals(-1, TransportObjectOverlay.objectId("Enter Cave 12a"));
		assertEquals(-1, TransportObjectOverlay.objectId("Enter Cave 99999999999"));
	}
}
