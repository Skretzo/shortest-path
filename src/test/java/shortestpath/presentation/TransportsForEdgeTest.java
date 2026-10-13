package shortestpath.presentation;

import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.transport.Transport;
import shortestpath.transport.ResourceLoader;

@RunWith(MockitoJUnitRunner.class)
public class TransportsForEdgeTest
{
	private static final Map<Integer, Set<Transport>> TRANSPORTS = ResourceLoader.transports();
	private static final int LUMBRIDGE_LANDING = WorldPointUtil.packWorldPoint(3221, 3218, 0);

	@Mock
	PathfinderConfig pathfinderConfig;

	PathAnnotator annotator;

	private Transport lumbridgeTeleport;
	private Transport lumbridgeHomeTeleport;

	@Before
	public void before()
	{
		annotator = PathAnnotator.forTesting();
		annotator.attach(pathfinderConfig);
		for (Transport transport : TRANSPORTS.get(Transport.UNDEFINED_ORIGIN))
		{
			if (transport.getDestination() == LUMBRIDGE_LANDING)
			{
				if ("Lumbridge Teleport".equals(transport.getDisplayInfo()) && lumbridgeTeleport == null)
				{
					lumbridgeTeleport = transport;
				}
				else if ("Lumbridge Home Teleport".equals(transport.getDisplayInfo()) && lumbridgeHomeTeleport == null)
				{
					lumbridgeHomeTeleport = transport;
				}
			}
		}
		Assert.assertNotNull(lumbridgeTeleport);
		Assert.assertNotNull(lumbridgeHomeTeleport);
	}

	private void mockTeleports(Transport... teleports)
	{
		when(pathfinderConfig.getUsableTeleports(any(BankVisitState.class))).thenReturn(teleports);
	}

	private void mockLocalTransports(int origin, Transport... local)
	{
		PrimitiveIntHashMap<Transport[]> packed = new PrimitiveIntHashMap<>(1);
		packed.put(origin, local);
		when(pathfinderConfig.getTransportsPacked(any(BankVisitState.class))).thenReturn(packed);
	}

	private void mockNoLocalTransports()
	{
		when(pathfinderConfig.getTransportsPacked(any(BankVisitState.class))).thenReturn(new PrimitiveIntHashMap<>(0));
	}

	@Test
	public void testWalkedEdgeOntoTeleportLandingShowsNoHint()
	{
		mockTeleports(lumbridgeTeleport, lumbridgeHomeTeleport);
		mockNoLocalTransports();
		// The path walks onto the Lumbridge teleport landing tile — a single-tile
		// edge that cannot be a teleport, so no teleport hint should be produced.
		PathStep from = new PathStep(WorldPointUtil.packWorldPoint(3222, 3218, 0), BankVisitState.CARRIED);
		PathStep to = new PathStep(LUMBRIDGE_LANDING, BankVisitState.CARRIED);
		Assert.assertTrue(annotator.transportsForEdge(from, to).isEmpty());
	}

	@Test
	public void testWalkedEdgeEndingOnTeleportLandingShowsNoHint()
	{
		mockTeleports(lumbridgeTeleport, lumbridgeHomeTeleport);
		mockNoLocalTransports();
		// Same suppression when the landing tile is the last step of the path:
		// the path does not go beyond the tile, so no teleport is used.
		PathStep from = new PathStep(WorldPointUtil.packWorldPoint(3221, 3219, 0), BankVisitState.CARRIED);
		PathStep to = new PathStep(LUMBRIDGE_LANDING, BankVisitState.CARRIED);
		Assert.assertTrue(annotator.transportsForEdge(from, to).isEmpty());
	}

	@Test
	public void testJumpEdgeOntoTeleportLandingKeepsFallback()
	{
		mockTeleports(lumbridgeTeleport, lumbridgeHomeTeleport);
		mockNoLocalTransports();
		// Manually constructed steps carry no transport identity; on an actual
		// jump the teleport remains a candidate.
		PathStep from = new PathStep(WorldPointUtil.packWorldPoint(3200, 3200, 0), BankVisitState.CARRIED);
		PathStep to = new PathStep(LUMBRIDGE_LANDING, BankVisitState.CARRIED);
		Assert.assertEquals(Set.of(lumbridgeTeleport, lumbridgeHomeTeleport), annotator.transportsForEdge(from, to));
	}

	@Test
	public void testRecordedTransportIsReturnedDirectly()
	{
		PathStep from = new PathStep(WorldPointUtil.packWorldPoint(3200, 3200, 0), BankVisitState.CARRIED);
		PathStep to = new PathStep(LUMBRIDGE_LANDING, BankVisitState.CARRIED, lumbridgeTeleport);
		Assert.assertEquals(Set.of(lumbridgeTeleport), annotator.transportsForEdge(from, to));
	}

	@Test
	public void testWalkedEdgeStillReportsLocalTransport()
	{
		// A local transport that moves the player a single tile is still a valid
		// way to traverse a walked edge; only teleports are suppressed.
		Transport adjacentLocal = null;
		for (Map.Entry<Integer, Set<Transport>> entry : TRANSPORTS.entrySet())
		{
			int origin = entry.getKey();
			if (origin == Transport.UNDEFINED_ORIGIN)
			{
				continue;
			}
			for (Transport transport : entry.getValue())
			{
				if ((transport.getType() == null || !transport.getType().isTeleport())
					&& WorldPointUtil.distanceBetween2D(origin, transport.getDestination()) <= 1)
				{
					adjacentLocal = transport;
				}
			}
		}
		Assert.assertNotNull(adjacentLocal);
		mockTeleports(lumbridgeTeleport, lumbridgeHomeTeleport);
		mockLocalTransports(adjacentLocal.getOrigin(), adjacentLocal);
		PathStep from = new PathStep(adjacentLocal.getOrigin(), BankVisitState.CARRIED);
		PathStep to = new PathStep(adjacentLocal.getDestination(), BankVisitState.CARRIED);
		Assert.assertEquals(Set.of(adjacentLocal), annotator.transportsForEdge(from, to));
	}
}
