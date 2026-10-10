package shortestpath;

import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

@RunWith(MockitoJUnitRunner.class)
public class TransportsForEdgeTest
{
	private static final Map<Integer, Set<Transport>> TRANSPORTS = TransportLoader.loadAllFromResources();
	private static final int LUMBRIDGE_LANDING = WorldPointUtil.packWorldPoint(3221, 3218, 0);

	@Mock
	PathfinderConfig pathfinderConfig;

	@InjectMocks
	ShortestPathPlugin plugin;

	private Transport lumbridgeTeleport;
	private Transport lumbridgeHomeTeleport;

	@Before
	public void before()
	{
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
		when(pathfinderConfig.getUsableTeleports(anyBoolean())).thenReturn(teleports);
	}

	private void mockLocalTransports(int origin, Transport... local)
	{
		PrimitiveIntHashMap<Transport[]> packed = new PrimitiveIntHashMap<>(1);
		packed.put(origin, local);
		when(pathfinderConfig.getTransportsPacked(anyBoolean())).thenReturn(packed);
	}

	private void mockNoLocalTransports()
	{
		when(pathfinderConfig.getTransportsPacked(anyBoolean())).thenReturn(new PrimitiveIntHashMap<>(0));
	}

	@Test
	public void testWalkedEdgeOntoTeleportLandingShowsNoHint()
	{
		mockTeleports(lumbridgeTeleport, lumbridgeHomeTeleport);
		mockNoLocalTransports();
		// The path walks onto the Lumbridge teleport landing tile — a single-tile
		// edge that cannot be a teleport, so no teleport hint should be produced.
		PathStep from = new PathStep(WorldPointUtil.packWorldPoint(3222, 3218, 0), false);
		PathStep to = new PathStep(LUMBRIDGE_LANDING, false);
		Assert.assertTrue(plugin.transportsForEdge(from, to).isEmpty());
	}

	@Test
	public void testWalkedEdgeEndingOnTeleportLandingShowsNoHint()
	{
		mockTeleports(lumbridgeTeleport, lumbridgeHomeTeleport);
		mockNoLocalTransports();
		// Same suppression when the landing tile is the last step of the path:
		// the path does not go beyond the tile, so no teleport is used.
		PathStep from = new PathStep(WorldPointUtil.packWorldPoint(3221, 3219, 0), false);
		PathStep to = new PathStep(LUMBRIDGE_LANDING, false);
		Assert.assertTrue(plugin.transportsForEdge(from, to).isEmpty());
	}

	@Test
	public void testJumpEdgeOntoTeleportLandingKeepsFallback()
	{
		mockTeleports(lumbridgeTeleport, lumbridgeHomeTeleport);
		mockNoLocalTransports();
		// Manually constructed steps carry no transport identity; on an actual
		// jump the teleport remains a candidate.
		PathStep from = new PathStep(WorldPointUtil.packWorldPoint(3200, 3200, 0), false);
		PathStep to = new PathStep(LUMBRIDGE_LANDING, false);
		Assert.assertEquals(Set.of(lumbridgeTeleport, lumbridgeHomeTeleport), plugin.transportsForEdge(from, to));
	}

	@Test
	public void testRecordedTransportIsReturnedDirectly()
	{
		PathStep from = new PathStep(WorldPointUtil.packWorldPoint(3200, 3200, 0), false);
		PathStep to = new PathStep(LUMBRIDGE_LANDING, false, lumbridgeTeleport);
		Assert.assertEquals(Set.of(lumbridgeTeleport), plugin.transportsForEdge(from, to));
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
		PathStep from = new PathStep(adjacentLocal.getOrigin(), false);
		PathStep to = new PathStep(adjacentLocal.getDestination(), false);
		Assert.assertEquals(Set.of(adjacentLocal), plugin.transportsForEdge(from, to));
	}
}
