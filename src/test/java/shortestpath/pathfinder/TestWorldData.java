package shortestpath.pathfinder;

import java.util.Map;
import java.util.Set;
import shortestpath.poh.PohService;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.transport.ResourceLoader;
import shortestpath.transport.Transport;

/**
 * The plugin's world data, loaded lazily once per JVM and shared by every test that works on
 * real map/transport data. Each kind lives in its own Initialization-on-Demand holder, so a test
 * touching only {@link #map()} never pays for the transport load or the routing-static build.
 *
 * <p><b>Read-only contract:</b> every returned map/set/object is shared JVM state — callers must
 * never mutate it. {@link PohService#remapPohDestinations} rewrites {@link Transport} objects in
 * place, so it is applied exactly once, inside the holder that produces the remapped view; a test
 * that needs to mutate (or re-remap) loads fresh via {@link ResourceLoader} or copies.
 * {@link SplitFlagMap#fromResources()} rewrites the static {@code regionExtents} as a side
 * effect, which is another reason the map loads through one holder instead of per test class.
 *
 * <p><b>Kinds:</b> {@link #map()} — collision map; {@link #transports()} — POH-remapped
 * transports; {@link #routingStatic()} — the full-world A* routing static (the ~4s world build,
 * memoized through the same provider the production code uses).
 *
 * <p>This class is compiled into the tooling composite build via a fileTree source share, so it
 * must stay Mockito-free and RuneLite-client-agnostic: main-tree types only.
 */
public final class TestWorldData
{
	private TestWorldData()
	{
	}

	private static final class CollisionData
	{
		static final SplitFlagMap DATA = SplitFlagMap.fromResources();
	}

	private static final class Transports
	{
		static final Map<Integer, Set<Transport>> REMAPPED = load();

		private static Map<Integer, Set<Transport>> load()
		{
			Map<Integer, Set<Transport>> transports = ResourceLoader.transports();
			PohService.remapPohDestinations(transports);
			return transports;
		}
	}

	private static final class AStarStatic
	{
		static final RoutingStatic STATIC = new AStarRoutingStaticProvider(
			() -> new CollisionMap(CollisionData.DATA)).get();
	}

	/** The collision map, decompressed once per JVM. */
	public static SplitFlagMap map()
	{
		return CollisionData.DATA;
	}

	/** Transports with POH destinations remapped — the view pathfinder tests expect. */
	public static Map<Integer, Set<Transport>> transports()
	{
		return Transports.REMAPPED;
	}

	/** The full-world A* routing static, built once per JVM and shared across tests. */
	public static RoutingStatic routingStatic()
	{
		return AStarStatic.STATIC;
	}
}
