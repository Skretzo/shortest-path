package shortestpath.pathfinder;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import shortestpath.Destination;
import shortestpath.pathfinder.exact.RoutingCuts;
import shortestpath.pathfinder.exact.RoutingStatic;
import shortestpath.pathfinder.exact.RoutingStaticBuilder;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;

/**
 * Builds the exact backend's static routing data once per plugin session, on first use.
 *
 * <p>The build runs on whichever thread first calls {@link #get()} (the pathfinding executor in
 * the plugin), never on the client thread. A failed build is remembered and rethrown so a broken
 * resource does not trigger a rebuild on every query.
 */
@Slf4j
public final class ExactRoutingStaticProvider implements Supplier<RoutingStatic>
{
	private final Supplier<CollisionMap> collision;
	private RoutingStatic routingStatic;
	private RuntimeException failure;
	private RoutingStaticBuilder.Diagnostics diagnostics;

	public ExactRoutingStaticProvider(Supplier<CollisionMap> collision)
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
			Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();
			Set<Integer> banks = Destination.loadAllFromResources().get("bank");
			RoutingCuts cuts = RoutingCuts.loadFromResources();
			if (cuts.cutCount() == 0)
			{
				log.warn("{} is missing or empty; building exact routing data without cuts",
					RoutingCuts.RESOURCE);
			}
			RoutingStaticBuilder.Result result = RoutingStaticBuilder.build(collision.get(), transports,
				banks == null ? Set.of() : banks, cuts.pairs());
			diagnostics = result.diagnostics;
			routingStatic = result.routingStatic;
			log.debug("Built exact routing data with cuts for collision map {}: {}",
				Long.toHexString(cuts.collisionFingerprint()), diagnostics);
			return routingStatic;
		}
		catch (IOException error)
		{
			failure = new IllegalStateException("cannot build exact routing data", error);
			throw failure;
		}
		catch (RuntimeException error)
		{
			failure = error;
			throw error;
		}
	}

	/** Diagnostics from the completed build, or {@code null} before the first successful build. */
	public synchronized RoutingStaticBuilder.Diagnostics getDiagnostics()
	{
		return diagnostics;
	}
}
