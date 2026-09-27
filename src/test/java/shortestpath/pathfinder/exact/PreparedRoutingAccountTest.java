package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.pathfinder.TransportAvailabilityFixture;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class PreparedRoutingAccountTest
{
	@Test
	public void localTransportsAreOrderedUnsignedAcrossPlanes()
	{
		int[] origins = {
			WorldPointUtil.packWorldPoint(3200, 3200, 3),
			WorldPointUtil.packWorldPoint(3200, 3200, 0),
			WorldPointUtil.packWorldPoint(3201, 3200, 2),
			WorldPointUtil.packWorldPoint(3200, 3201, 1),
		};
		Transport[] transports = new Transport[origins.length];
		for (int i = 0; i < origins.length; i++)
			transports[i] = new Transport.TransportBuilder().origin(origins[i]).destination(origins[i] + 1)
				.type(TransportType.TRANSPORT).duration(1).build();
		TransportAvailability availability = TransportAvailabilityFixture.of(transports);

		PreparedRoutingAccount account = PreparedRoutingAccount.compile(availability, availability, false, true,
			ignored -> 0);

		assertEquals(origins.length, account.localCount(false));
		for (int i = 1; i < account.localCount(false); i++)
			assertTrue(Integer.compareUnsigned(account.localOrigin(false, i - 1), account.localOrigin(false, i)) <= 0);
	}
}
