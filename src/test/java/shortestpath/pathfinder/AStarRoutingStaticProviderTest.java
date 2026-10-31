package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class AStarRoutingStaticProviderTest
{
	/** A collision map in which nothing is walkable, so the bundled layer cannot fit it. */
	private static final class EmptyCollisionMap extends CollisionMap
	{
		EmptyCollisionMap()
		{
			super(null);
		}

		@Override
		public byte ordinaryWalkingMask(int packedPoint)
		{
			return 0;
		}

		@Override
		public int[] ordinaryWalkingNeighbors(int packedPoint)
		{
			return new int[0];
		}

		@Override
		public boolean isBlocked(int x, int y, int z)
		{
			return true;
		}
	}

	@Test
	public void aLayerThatDoesNotFitTheCollisionMapFailsOnceAndStaysFailed()
	{
		AtomicInteger suppliedMaps = new AtomicInteger();
		AStarRoutingStaticProvider provider = new AStarRoutingStaticProvider(() ->
		{
			suppliedMaps.incrementAndGet();
			return new EmptyCollisionMap();
		});

		IllegalStateException first = null;
		try
		{
			provider.get();
			fail("a layer that disagrees with the collision map must not load");
		}
		catch (IllegalStateException expected)
		{
			first = expected;
		}
		assertTrue(first.getCause() instanceof IOException);
		assertNotNull(first.getCause().getMessage());
		assertTrue(first.getCause().getMessage(), first.getCause().getMessage().contains("stale"));

		try
		{
			provider.get();
			fail("the failure is remembered");
		}
		catch (IllegalStateException second)
		{
			assertSame(first, second);
		}
		assertEquals(1, suppliedMaps.get());
	}
}
