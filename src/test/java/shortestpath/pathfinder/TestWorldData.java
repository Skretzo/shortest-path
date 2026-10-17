package shortestpath.pathfinder;

import java.util.Map;
import java.util.Set;
import shortestpath.poh.PohService;
import shortestpath.pathfinder.astar.RoutingCuts;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.requirement.model.DestinationRequirements;
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
 * <p><b>Two transport views:</b> {@link #transports()} is the POH-remapped view the pathfinder
 * tests expect; {@link #rawTransports()} is the unremapped parse the lint/fidelity data-regression
 * tests assert against. Never feed the remapped view to a lint test — its assertions would
 * silently see rewritten destinations.
 *
 * <p><b>Kinds:</b> {@link #map()}, {@link #transports()}, {@link #rawTransports()},
 * {@link #destinations()}, {@link #filteredDestinations()}, {@link #bankRequirements()},
 * {@link #routingCuts()}, {@link #routingStatic()} (the ~4s full-world A* build, memoized through
 * the same provider the production code uses).
 *
 * <p><b>Deliberate bypasses:</b> tests that exist to guard the data files themselves keep their
 * own loads and must not be migrated here — TransportDataLintTest, DestinationDataLintTest,
 * ResourceLoaderTest, ResourceLoaderFidelityTest, TransportCountingTest, SeaReachabilityTest,
 * RoutingStaticBuilderRealDataTest, RoutingCutsTest — because sharing one parse would narrow the
 * regression surface they deliberately re-execute.
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

	private static final class RawTransports
	{
		static final Map<Integer, Set<Transport>> ALL = ResourceLoader.transports();
	}

	private static final class Destinations
	{
		static final Map<String, Set<Integer>> ALL = ResourceLoader.destinations();
	}

	private static final class FilteredDestinations
	{
		static final Map<String, Set<Integer>> ALL = PathfinderConfig.filterDestinations(
			Destinations.ALL);
	}

	private static final class BankRequirements
	{
		static final Map<Integer, DestinationRequirements> ALL = ResourceLoader.bankRequirements();
	}

	private static final class Cuts
	{
		static final RoutingCuts ALL = RoutingCuts.loadFromResources();
	}

	private static final class AStarStatic
	{
		static final AStarRoutingStaticProvider PROVIDER = new AStarRoutingStaticProvider(
			() -> new CollisionMap(CollisionData.DATA));
		static final RoutingStatic STATIC = PROVIDER.get();
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

	/** Transports as parsed, without the POH remap — for lint/fidelity assertions. */
	public static Map<Integer, Set<Transport>> rawTransports()
	{
		return RawTransports.ALL;
	}

	/** All destinations as parsed. */
	public static Map<String, Set<Integer>> destinations()
	{
		return Destinations.ALL;
	}

	/** Destinations after the config's availability filter. */
	public static Map<String, Set<Integer>> filteredDestinations()
	{
		return FilteredDestinations.ALL;
	}

	/** Per-tile bank requirements for pickup-aware routing. */
	public static Map<Integer, DestinationRequirements> bankRequirements()
	{
		return BankRequirements.ALL;
	}

	/** The A* routing-cut table parsed once per JVM. */
	public static RoutingCuts routingCuts()
	{
		return Cuts.ALL;
	}

	/** The full-world A* routing static, built once per JVM and shared across tests. */
	public static RoutingStatic routingStatic()
	{
		return AStarStatic.STATIC;
	}

	/**
	 * The provider that built {@link #routingStatic()}. Injection points typed as the provider —
	 * the plugin scheduler's {@code aStarRoutingStatic} field — share this instance so their
	 * {@code get()} returns the already-built static instead of paying a second world build.
	 */
	public static AStarRoutingStaticProvider routingStaticProvider()
	{
		return AStarStatic.PROVIDER;
	}
}
