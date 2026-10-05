package shortestpath.transport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;

/**
 * The collision map leaves the open sea walkable (for Sailing), so a transport that starts or
 * lands on a sea tile joins the whole body of water to the map, and routes can then walk across
 * the ocean. Until Sailing is modelled, no transport may enter the open sea from outside it.
 */
public class SeaReachabilityTest
{
	/** One open-water tile in each walkable body of open sea on plane 0. */
	private static final int[][] SEA_SEEDS = {
		{2048, 2104}, // the sea around Karamja, Ape Atoll and the southern and western coasts
		{3072, 3968}, // the sea east of Morytania and north of the Wilderness
		{1024, 3040}, // the sea west of Kourend and Varlamore
		{1024, 2104}, // the south-western sea
	};

	private static CollisionMap map;
	private static Map<Integer, Set<Transport>> transports;

	@BeforeClass
	public static void load()
	{
		map = new CollisionMap(SplitFlagMap.fromResources());
		transports = TransportLoader.loadAllFromResources();
	}

	@Test
	public void testNoTransportEntersTheOpenSea()
	{
		List<String> leaks = new ArrayList<>();
		for (int[] seed : SEA_SEEDS)
		{
			BitSet sea = walkableComponent(seed[0], seed[1]);
			Assert.assertTrue("Sea seed " + seed[0] + "," + seed[1] + " should be open, walkable water",
				sea.cardinality() > 100_000);
			Assert.assertFalse("The sea at " + seed[0] + "," + seed[1] + " must not be walkable from Lumbridge",
				contains(sea, WorldPointUtil.packWorldPoint(3222, 3218, 0)));

			for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet())
			{
				for (Transport transport : entry.getValue())
				{
					if (contains(sea, transport.getDestination()) && !contains(sea, transport.getOrigin()))
					{
						leaks.add(describe(transport) + " lands in the sea at " + seed[0] + "," + seed[1]);
					}
				}
			}
		}
		Assert.assertTrue("Transports must not enter the open sea:\n" + String.join("\n", leaks), leaks.isEmpty());
	}

	private static BitSet walkableComponent(int seedX, int seedY)
	{
		BitSet seen = new BitSet(1 << 28);
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		seen.set(index(seedX, seedY));
		queue.add(index(seedX, seedY));
		while (!queue.isEmpty())
		{
			int tile = queue.poll();
			int x = tile >>> 14;
			int y = tile & 0x3fff;
			visit(seen, queue, map.n(x, y, 0), x, y + 1);
			visit(seen, queue, map.s(x, y, 0), x, y - 1);
			visit(seen, queue, map.e(x, y, 0), x + 1, y);
			visit(seen, queue, map.w(x, y, 0), x - 1, y);
		}
		return seen;
	}

	private static void visit(BitSet seen, ArrayDeque<Integer> queue, boolean open, int x, int y)
	{
		if (open && !seen.get(index(x, y)))
		{
			seen.set(index(x, y));
			queue.add(index(x, y));
		}
	}

	private static int index(int x, int y)
	{
		return x << 14 | y;
	}

	private static boolean contains(BitSet sea, int packedPoint)
	{
		if (packedPoint == Transport.UNDEFINED_ORIGIN || packedPoint == Transport.LOCATION_PERMUTATION
			|| WorldPointUtil.unpackWorldPlane(packedPoint) != 0)
		{
			return false;
		}
		int x = WorldPointUtil.unpackWorldX(packedPoint);
		int y = WorldPointUtil.unpackWorldY(packedPoint);
		return x >= 0 && x < 1 << 14 && y >= 0 && y < 1 << 14 && sea.get(index(x, y));
	}

	private static String describe(Transport transport)
	{
		return transport.getType() + " " + point(transport.getOrigin()) + " -> " + point(transport.getDestination())
			+ (transport.getDisplayInfo() != null ? " (" + transport.getDisplayInfo() + ")" : "");
	}

	private static String point(int packedPoint)
	{
		if (packedPoint == Transport.UNDEFINED_ORIGIN || packedPoint == Transport.LOCATION_PERMUTATION)
		{
			return "anywhere";
		}
		return WorldPointUtil.unpackWorldX(packedPoint) + "," + WorldPointUtil.unpackWorldY(packedPoint) + ","
			+ WorldPointUtil.unpackWorldPlane(packedPoint);
	}
}
