package shortestpath.pathfinder;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import shortestpath.pathfinder.astar.RoutingStatic;
import shortestpath.pathfinder.astar.RoutingStaticLoader;
import shortestpath.transport.ResourceLoader;
import shortestpath.transport.Transport;

/**
 * Builds the A* backend's static routing data once per plugin session, on first use.
 *
 * <p>The data comes from the bundled routing component layer plus the live collision map and
 * transports. The load runs on whichever thread first calls {@link #get()} (the pathfinding
 * executor in the plugin), never on the client thread. A layer that is missing, corrupt or does not
 * match the collision map fails once, loudly, and the failure is remembered and rethrown so a
 * broken resource does not trigger another attempt on every query. There is no rebuild fallback.
 */
public final class AStarRoutingStaticProvider implements Supplier<RoutingStatic>
{
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
		try
		{
			Map<Integer, Set<Transport>> transports = ResourceLoader.transports();
			Set<Integer> banks = ResourceLoader.destinations().get("bank");
			routingStatic = RoutingStaticLoader.build(collision.get(), transports, banks == null ? Set.of() : banks);
			return routingStatic;
		}
		catch (IOException error)
		{
			failure = new IllegalStateException("cannot build A* routing data", error);
			throw failure;
		}
		catch (RuntimeException error)
		{
			failure = error;
			throw error;
		}
	}
}
