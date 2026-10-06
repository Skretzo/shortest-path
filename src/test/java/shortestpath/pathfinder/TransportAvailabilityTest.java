package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Ordering contract tests for {@link TransportAvailability.Builder}: the flat arrays emitted by
 * {@code build()} must follow add() order so neighbour expansion is deterministic across JVM
 * runs. {@link Transport} has no equals/hashCode, so a plain HashSet would iterate in
 * identity-hash order that varies per run.
 */
public class TransportAvailabilityTest
{
	private static Transport transport(int origin, int destination)
	{
		return new Transport.TransportBuilder()
			.origin(origin)
			.destination(destination)
			.type(TransportType.TRANSPORT)
			.build();
	}

	@Test
	public void perOriginArraysFollowAddOrder()
	{
		int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		Transport[] added = new Transport[8];
		TransportAvailability.Builder builder = new TransportAvailability.Builder(16);
		for (int i = 0; i < added.length; i++)
		{
			added[i] = transport(origin, WorldPointUtil.packWorldPoint(3300 + i, 3400, 0));
			builder.add(added[i]);
		}

		Transport[] at = builder.build().getTransportsAt(origin);
		assertEquals(added.length, at.length);
		for (int i = 0; i < added.length; i++)
		{
			assertSame("Transports must emit in add() order", added[i], at[i]);
		}
	}

	@Test
	public void usableTeleportsFollowAddOrder()
	{
		Transport[] added = new Transport[6];
		TransportAvailability.Builder builder = new TransportAvailability.Builder(16);
		for (int i = 0; i < added.length; i++)
		{
			added[i] = transport(
				WorldPointUtil.UNDEFINED,
				WorldPointUtil.packWorldPoint(3000 + i, 3000, 0));
			builder.add(added[i]);
		}

		Transport[] teleports = builder.build().getUsableTeleports();
		assertEquals(added.length, teleports.length);
		for (int i = 0; i < added.length; i++)
		{
			assertSame("Usable teleports must emit in add() order", added[i], teleports[i]);
		}
	}

	@Test
	public void addingSameInstanceTwiceDeduplicates()
	{
		int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		Transport transport = transport(origin, WorldPointUtil.packWorldPoint(3300, 3300, 0));
		TransportAvailability.Builder builder = new TransportAvailability.Builder(16);
		builder.add(transport);
		builder.add(transport);

		Transport[] at = builder.build().getTransportsAt(origin);
		assertEquals("Re-adding the same instance must not duplicate it", 1, at.length);
		assertSame(transport, at[0]);
	}
}
