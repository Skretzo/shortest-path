package shortestpath.pathfinder;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import shortestpath.pathfinder.astar.*;
import shortestpath.transport.*;

/**
 * Builds the A* backend's static routing data once per plugin session, on first use.
 *
 * <p>The data comes from the bundled routing component layer plus the live collision map and
 * transports. The load runs on whichever thread first calls {@link #get()} (the pathfinding
 * executor in the plugin), never on the client thread. A layer that is missing, truncated or
 * malformed, or that was generated from different collision data than the loaded map (checked by
 * its fingerprint), fails once, logs an error naming the resource and how to regenerate it, and the
 * failure is remembered and rethrown so a broken resource does not trigger another attempt on every
 * query. There is no rebuild fallback: until the layer is regenerated, A* routing is unavailable.
 */
@Slf4j
public final class AStarRoutingStaticProvider implements Supplier<RoutingStatic>
{
	private static final String LAYER_RESOURCE = "routing-components.bin";
	private static final String REGENERATE = "ROUTING_COMPONENTS_REGEN=1 ./gradlew test"
		+ " --tests '*RoutingComponentsFreshnessTest' --rerun";

	private final Supplier<CollisionMap> collision;
	private RoutingStatic routingStatic;
	private RuntimeException failure;

	public AStarRoutingStaticProvider(Supplier<CollisionMap> collision)
	{
		this.collision = Objects.requireNonNull(collision, "collision");
	}

	@Override
	public synchronized RoutingStatic get()
	{
		if (routingStatic != null) return routingStatic;
		if (failure != null) throw failure;
		long started = System.nanoTime();
		try
		{
			Map<Integer, Set<Transport>> transports = ResourceLoader.transports();
			Set<Integer> banks = ResourceLoader.destinations().get("bank");
			routingStatic = RoutingStaticLoader.build(collision.get(), transports, banks == null ? Set.of() : banks);
			log.debug("Loaded A* routing data in {} ms: {} search tiles, {} sites",
				(System.nanoTime() - started) / 1_000_000, routingStatic.searchTileCount(), routingStatic.siteCount());
			return routingStatic;
		}
		catch (IOException error)
		{
			log.error("A* routing is unavailable: {} cannot be used ({}). It is derived from collision-map.zip;"
				+ " regenerate it with {}", LAYER_RESOURCE, error.getMessage(), REGENERATE);
			failure = new IllegalStateException("cannot build A* routing data", error);
			throw failure;
		}
		catch (OutOfMemoryError error)
		{
			// A layer that passes the reader can still size arrays past the heap. Remember it like any other
			// failure instead of letting the executor task die and every later query retry the load.
			log.error("A* routing is unavailable: not enough memory to load the routing data", error);
			failure = new IllegalStateException("not enough memory to build A* routing data", error);
			throw failure;
		}
		catch (RuntimeException error)
		{
			failure = error;
			throw error;
		}
	}
}
