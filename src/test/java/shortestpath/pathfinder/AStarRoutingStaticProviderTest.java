package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import shortestpath.pathfinder.astar.RoutingStatic;

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
		public long fingerprint()
		{
			return 0;
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

	private static IllegalStateException failureOf(AStarRoutingStaticProvider provider)
	{
		try
		{
			provider.get();
		}
		catch (IllegalStateException expected)
		{
			return expected;
		}
		throw new AssertionError("the provider loaded a layer it must refuse");
	}

	private static void assertRemembered(AStarRoutingStaticProvider provider, IllegalStateException first,
		AtomicInteger suppliedMaps)
	{
		assertSame(first, failureOf(provider));
		assertEquals(1, suppliedMaps.get());
	}

	@Test
	public void aMissingLayerFailsOnceAndStaysFailed()
	{
		AtomicInteger suppliedMaps = new AtomicInteger();
		AStarRoutingStaticProvider provider = new AStarRoutingStaticProvider(() ->
		{
			suppliedMaps.incrementAndGet();
			return new EmptyCollisionMap();
		}, () -> null);

		IllegalStateException first = failureOf(provider);

		assertTrue(first.getCause() instanceof IOException);
		assertTrue(first.getCause().getMessage(), first.getCause().getMessage().contains("missing"));
		assertRemembered(provider, first, suppliedMaps);
	}

	@Test
	public void aCorruptLayerFailsOnceAndStaysFailed()
	{
		AtomicInteger suppliedMaps = new AtomicInteger();
		AStarRoutingStaticProvider provider = new AStarRoutingStaticProvider(() ->
		{
			suppliedMaps.incrementAndGet();
			return new EmptyCollisionMap();
		}, () -> new ByteArrayInputStream(new byte[] {1, 2, 3}));

		IllegalStateException first = failureOf(provider);

		assertTrue(first.getCause() instanceof IOException);
		assertTrue(first.getCause().getMessage(), first.getCause().getMessage().contains("routing-components.bin"));
		assertRemembered(provider, first, suppliedMaps);
	}

	@Test
	public void aFailingCollisionSupplierFailsOnceAndStaysFailed()
	{
		AtomicInteger suppliedMaps = new AtomicInteger();
		AStarRoutingStaticProvider provider = new AStarRoutingStaticProvider(() ->
		{
			suppliedMaps.incrementAndGet();
			throw new IllegalStateException("collision map not loaded");
		});

		IllegalStateException first = failureOf(provider);

		assertEquals("collision map not loaded", first.getMessage());
		assertRemembered(provider, first, suppliedMaps);
	}

	@Test
	public void theBundledLayerLoadsAgainstTheBundledMapAndIsLoadedOnce() throws Exception
	{
		AtomicInteger suppliedMaps = new AtomicInteger();
		AStarRoutingStaticProvider provider = new AStarRoutingStaticProvider(() ->
		{
			suppliedMaps.incrementAndGet();
			return new CollisionMap(SplitFlagMap.fromResources());
		});

		RoutingStatic first = provider.get();

		assertTrue(first.searchTileCount() > 0);
		assertSame(first, provider.get());
		assertEquals(1, suppliedMaps.get());
	}
}
