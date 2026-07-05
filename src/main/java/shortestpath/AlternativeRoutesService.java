package shortestpath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.pathfinder.TransportAvailability;
import shortestpath.transport.Transport;

/**
 * Generates up to {@link #MAX_ROUTES} alternative shortest paths to a target, each using a different
 * set of teleport/transport methods.
 * <p>
 * Strategy: run the planning-mode pathfinder, record the methods the best path used, exclude that
 * path's primary method, and search again. Repeating this yields successively-different routes in
 * increasing cost order. The user's own exclusions (methods they switched off in the panel) are
 * applied on top of every search.
 * <p>
 * Searches run on a dedicated background thread. Each search needs the planning config's transport
 * availability rebuilt for the current exclusion set, and {@link PathfinderConfig#refresh()} must run
 * on the client thread (it reads live game state), so the worker bounces each refresh onto the client
 * thread and waits for it before running the search off-thread.
 */
@Slf4j
public class AlternativeRoutesService
{
	public static final int MAX_ROUTES = 10;
	private static final int MAX_ROUTES_CAP = 50;
	private static final long CLIENT_THREAD_TIMEOUT_SECONDS = 10;
	/**
	 * When the exact target is unreachable, routes are accepted while their endpoint stays within this
	 * many tiles of the best route's endpoint (they all converge on the closest reachable area).
	 */
	private static final int CLOSEST_DISTANCE_TOLERANCE = 10;

	/**
	 * Receives progressive updates for one generation: the catalog as soon as it's known, then the
	 * routes-so-far after each one is found, and a final call with {@code done == true}. Invoked on
	 * the worker thread; the caller marshals to the Swing EDT. Stale generations stop emitting.
	 * {@code unavailable} maps each catalog method the player cannot use in the current mode to the
	 * reason why (missing item, in the bank, missing level/quest, not unlocked), so the panel can mark
	 * and explain them; it is populated in every mode.
	 */
	public interface ResultListener
	{
		void onUpdate(List<RouteOption> routes, List<TeleportMethod> catalog,
			Map<TeleportMethod, MethodAvailability> unavailable, boolean done);
	}

	/**
	 * Worker threads for the parallel seed searches (the exclusion loop itself is inherently
	 * sequential). Small pool: searches are CPU-bound.
	 */
	private static final int SEED_POOL_SIZE = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2));

	private final ClientThread clientThread;
	private final PathfinderConfig planningConfig;
	private final ExecutorService executor;
	private final ExecutorService seedExecutor;
	// Bumped on every generate()/cancel() so a stale in-flight generation discards its result.
	private final AtomicInteger generation = new AtomicInteger();

	public AlternativeRoutesService(ClientThread clientThread, PathfinderConfig planningConfig)
	{
		this.clientThread = clientThread;
		this.planningConfig = planningConfig;
		this.executor = Executors.newSingleThreadExecutor(
			new ThreadFactoryBuilder().setNameFormat("shortest-path-alts-%d").setDaemon(true).build());
		this.seedExecutor = Executors.newFixedThreadPool(SEED_POOL_SIZE,
			new ThreadFactoryBuilder().setNameFormat("shortest-path-alts-seed-%d").setDaemon(true).build());
	}

	/**
	 * Asynchronously computes the alternative routes, streaming progressive updates to {@code listener}
	 * (catalog first, then each route as it's found, then a final done update). Supersedes any in-flight
	 * generation.
	 */
	public void generate(int start, Set<Integer> targets, Set<TeleportMethod> userExclusions,
		AlternativeRoutesMode mode, int maxRoutes, ResultListener listener)
	{
		final int gen = generation.incrementAndGet();
		final Set<Integer> targetsCopy = new HashSet<>(targets);
		final Set<TeleportMethod> userExclusionsCopy = new HashSet<>(userExclusions);
		executor.submit(() ->
		{
			try
			{
				computeRoutes(gen, start, targetsCopy, userExclusionsCopy, mode, maxRoutes, listener);
			}
			catch (Exception e)
			{
				log.warn("Alternative route generation failed", e);
			}
		});
	}

	public void cancel()
	{
		generation.incrementAndGet();
	}

	public void shutdown()
	{
		executor.shutdownNow();
		seedExecutor.shutdownNow();
	}

	private void computeRoutes(int gen, int start, Set<Integer> targets,
		Set<TeleportMethod> userExclusions, AlternativeRoutesMode mode, int maxRoutes, ResultListener listener)
	{
		final int limit = Math.max(1, Math.min(maxRoutes, MAX_ROUTES_CAP));
		final Set<Integer> ends = new HashSet<>(targets);
		final Set<TeleportMethod> excluded = new HashSet<>(userExclusions);
		final GenTimer timer = new GenTimer();
		final long wallStart = System.nanoTime();

		// Single client-thread pass per generation, with NO exclusions: snapshots the game state and
		// builds the full availability (the complete method catalog, and the base lists that per-search
		// availability is rebuilt from off-thread), and drops targets the avoid-wilderness setting forbids.
		long clientStart = System.nanoTime();
		boolean refreshed = refreshOnClientThread(Collections.emptySet(), ends, mode);
		timer.clientNanos += System.nanoTime() - clientStart;
		if (!refreshed)
		{
			emit(gen, listener, List.of(), List.of(), Map.of(), true);
			return;
		}
		final List<TeleportMethod> catalog = new ArrayList<>(planningConfig.getMethodCatalog());
		// The catalog is the full method universe in every mode; flag the entries the player can't use
		// in THIS mode, with the reason, so the panel can mark them. A banked item counts as usable only
		// in the "Inventory + bank" mode (its route walks to a bank); every other mode marks it.
		final Map<TeleportMethod, MethodAvailability> statuses = planningConfig.getMethodAvailability();
		final Map<TeleportMethod, MethodAvailability> notUsable = new HashMap<>();
		for (TeleportMethod method : catalog)
		{
			MethodAvailability status = statuses.getOrDefault(method, MethodAvailability.AVAILABLE);
			if (status == MethodAvailability.AVAILABLE
				|| (status == MethodAvailability.IN_BANK && mode == AlternativeRoutesMode.OWNED_WITH_BANK))
			{
				continue;
			}
			notUsable.put(method, status);
		}
		final Map<TeleportMethod, MethodAvailability> unavailable = Collections.unmodifiableMap(notUsable);
		if (ends.isEmpty())
		{
			emit(gen, listener, List.of(), catalog, unavailable, true);
			return;
		}
		// Show the catalog right away while the routes are still computing.
		emit(gen, listener, List.of(), catalog, unavailable, false);

		log.debug("[alt-routes] searching: start={}, target={}, mode={}, usableTeleports={}, catalog={}",
			WorldPointUtil.unpackWorldPoint(start),
			WorldPointUtil.unpackWorldPoint(ends.iterator().next()),
			mode, planningConfig.getUsableTeleports(false).length, catalog.size());

		// Snapshot the global-teleport candidates now, while availability reflects no exclusions; used
		// to seed extra routes if the exclusion loop dries up before the limit.
		final List<Transport> seedCandidates = new ArrayList<>(Arrays.asList(
			planningConfig.getUsableTeleports(mode == AlternativeRoutesMode.OWNED_WITH_BANK)));

		final List<RouteOption> routes = new ArrayList<>();
		final Set<String> seenSignatures = new HashSet<>();
		// Remaining distance to the target of the first route's endpoint; -1 until known. For an
		// unreachable exact target (e.g. an NPC tile) every route ends at the closest reachable area,
		// so later routes are only accepted while they get equally close (small tolerance).
		int bestRemaining = -1;

		for (int i = 0; i < limit; i++)
		{
			if (gen != generation.get())
			{
				return;
			}
			// Rebuild availability for the current exclusion set — pure computation over the base lists
			// captured by the client-thread pass above, so no client-thread round-trip per search.
			long rebuildStart = System.nanoTime();
			planningConfig.rebuildAvailabilityWithExclusions(excluded);
			timer.rebuildNanos += System.nanoTime() - rebuildStart;

			long searchStart = System.nanoTime();
			Pathfinder pathfinder = new Pathfinder(planningConfig, start, ends);
			pathfinder.run();
			timer.searchNanos += System.nanoTime() - searchStart;
			timer.searches++;
			PathfinderResult result = pathfinder.getResult();
			List<PathStep> path = (result != null) ? result.getPathSteps() : List.of();
			if (result == null || path.isEmpty())
			{
				log.debug("[alt-routes] search #{} produced no path: result={}, reason={}",
					i, result == null ? "null" : "empty",
					result == null ? "n/a" : result.getTerminationReason());
				break;
			}

			boolean reached = result.isReached();
			// Unreachable exact targets (e.g. NPC tiles) still have meaningful alternatives: different
			// methods all ending at the closest reachable area. Keep enumerating while routes get
			// equally close; stop once exclusions make the search end up meaningfully further away.
			int remaining = reached ? 0 : remainingDistance(path, ends);
			if (bestRemaining < 0)
			{
				bestRemaining = remaining;
			}
			else if (remaining > bestRemaining + CLOSEST_DISTANCE_TOLERANCE)
			{
				log.debug("[alt-routes] search #{} ends {} tiles from target (best {}); stopping with {} route(s)",
					i, remaining, bestRemaining, routes.size());
				break;
			}

			MethodScan scan = scanMethods(planningConfig, path);
			List<TeleportMethod> methods = scan.methods;

			// Distinct method-signature gate: if this route uses the same ordered methods as a previous
			// one, excluding more would only reshuffle, so stop.
			if (!seenSignatures.add(signature(methods)))
			{
				break;
			}
			routes.add(new RouteOption(path, methods, result.getTotalCost(), scan.rawCost, reached,
				scan.bankGated, scan.walkBefore, scan.trailingWalk));
			// Stream the route we just found so the panel shows it immediately.
			emit(gen, listener, new ArrayList<>(routes), catalog, unavailable, false);

			TeleportMethod primary = methods.isEmpty() ? null : methods.get(0);
			if (primary == null)
			{
				// Walk-only route: the exclusion strategy has nothing left to remove. Seeding below can
				// still surface teleport routes that lost to walking on cost.
				break;
			}
			excluded.add(primary);
		}

		// Stop at the pure-walk option: once walking there is on the list, anything more expensive than
		// just walking isn't worth showing. Seeding only ever surfaces teleports that lost to walking on
		// cost (i.e. routes MORE expensive than walk-only), so skip it entirely when a walk-only route was
		// already found; only seed to fill slots when walking never came up (e.g. every route teleports).
		boolean hasWalkOnly = routes.stream().anyMatch(RouteOption::isWalkOnly);
		if (!hasWalkOnly && routes.size() < limit && !routes.isEmpty())
		{
			seedTeleportRoutes(gen, start, ends, userExclusions, mode, limit,
				seedCandidates, routes, seenSignatures, catalog, unavailable, listener, bestRemaining, timer);
		}

		routes.sort(Comparator.comparingInt(RouteOption::getTotalCost));
		// Drop anything after the pure-walk option (belt-and-braces alongside the skipped seeding above).
		for (int i = 0; i < routes.size(); i++)
		{
			if (routes.get(i).isWalkOnly())
			{
				routes.subList(i + 1, routes.size()).clear();
				break;
			}
		}
		synchronized (timer)
		{
			log.debug("[alt-routes] generated {} route(s); timing: wall={}ms client={}ms rebuild={}ms searchCpu={}ms ({} searches)",
				routes.size(),
				(System.nanoTime() - wallStart) / 1_000_000,
				timer.clientNanos / 1_000_000,
				timer.rebuildNanos / 1_000_000,
				timer.searchNanos / 1_000_000,
				timer.searches);
		}
		emit(gen, listener, new ArrayList<>(routes), catalog, unavailable, true);
	}

	/**
	 * Per-generation timing breakdown: time blocked on the client thread, time rebuilding
	 * availability off-thread, and time in the searches. Seed searches run in parallel, so their
	 * rebuild/search nanos are CPU-summed across workers (can exceed wall time); accumulation from
	 * worker threads synchronizes on this object.
	 */
	private static final class GenTimer
	{
		private long clientNanos;
		private long rebuildNanos;
		private long searchNanos;
		private int searches;
	}

	/**
	 * Seeds additional routes when the exclusion loop ended early: for each candidate global teleport
	 * (closest landing to the target first), run one search with every OTHER global teleport excluded,
	 * so the result is "the best route if you use this teleport". Routes with an already-seen method
	 * signature, walk-only results, or endpoints meaningfully further than the best route are skipped.
	 */
	private void seedTeleportRoutes(int gen, int start, Set<Integer> ends, Set<TeleportMethod> userExclusions,
		AlternativeRoutesMode mode, int limit, List<Transport> seedCandidates, List<RouteOption> routes,
		Set<String> seenSignatures, List<TeleportMethod> catalog, Map<TeleportMethod, MethodAvailability> unavailable,
		ResultListener listener, int bestRemaining, GenTimer timer)
	{
		final int target = closestTarget(start, ends);
		final int startDistance = WorldPointUtil.distanceBetween(start, target);

		// Rank candidates by how close they land to the target; drop ones that don't get closer than
		// the start, user-excluded methods, and duplicate landings (e.g. tab vs spell to the same tile).
		final Map<Integer, Transport> byDestination = new LinkedHashMap<>();
		seedCandidates.sort(Comparator.comparingInt(t -> WorldPointUtil.distanceBetween(t.getDestination(), target)));
		final Set<TeleportMethod> allSeedMethods = new HashSet<>();
		for (Transport transport : seedCandidates)
		{
			allSeedMethods.add(TeleportMethod.fromTransport(transport));
		}
		for (Transport transport : seedCandidates)
		{
			if (WorldPointUtil.distanceBetween(transport.getDestination(), target) >= startDistance
				|| userExclusions.contains(TeleportMethod.fromTransport(transport)))
			{
				continue;
			}
			byDestination.putIfAbsent(transport.getDestination(), transport);
		}

		final int maxAttempts = (limit - routes.size()) * 3;
		final List<Transport> attempts = new ArrayList<>(Math.min(maxAttempts, byDestination.size()));
		for (Transport seed : byDestination.values())
		{
			if (attempts.size() >= maxAttempts)
			{
				break;
			}
			attempts.add(seed);
		}
		if (attempts.isEmpty())
		{
			return;
		}

		// One config per concurrent worker (shares immutable data + the refreshed state); the seed
		// searches are independent, so they run in parallel on the seed pool. Results are collected
		// and accepted on this (generation) thread in completion order.
		final int workers = Math.min(SEED_POOL_SIZE, attempts.size());
		final Queue<PathfinderConfig> configPool = new ConcurrentLinkedQueue<>();
		for (int i = 0; i < workers; i++)
		{
			configPool.add(planningConfig.copyForParallelSearch());
		}
		final AtomicBoolean stop = new AtomicBoolean(false);
		final CompletionService<SeedResult> completion = new ExecutorCompletionService<>(seedExecutor);
		final List<Future<SeedResult>> futures = new ArrayList<>(attempts.size());
		for (Transport seed : attempts)
		{
			futures.add(completion.submit(() ->
				runSeedSearch(gen, stop, start, ends, userExclusions, allSeedMethods, seed, bestRemaining, configPool, timer)));
		}

		try
		{
			for (int i = 0; i < futures.size() && routes.size() < limit && gen == generation.get(); i++)
			{
				SeedResult seedResult;
				try
				{
					seedResult = completion.take().get();
				}
				catch (ExecutionException e)
				{
					log.warn("Seed search failed", e);
					continue;
				}
				if (seedResult == null || !seenSignatures.add(signature(seedResult.scan.methods)))
				{
					continue;
				}
				routes.add(new RouteOption(seedResult.path, seedResult.scan.methods,
					seedResult.totalCost, seedResult.scan.rawCost, seedResult.reached,
					seedResult.scan.bankGated, seedResult.scan.walkBefore, seedResult.scan.trailingWalk));
				emit(gen, listener, new ArrayList<>(routes), catalog, unavailable, false);
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		finally
		{
			stop.set(true);
			for (Future<SeedResult> future : futures)
			{
				future.cancel(false);
			}
		}
	}

	/**
	 * One parallel seed attempt: rebuild availability on a worker-owned config with every other
	 * global teleport excluded, search, and pre-filter the result. Returns null when rejected.
	 */
	private SeedResult runSeedSearch(int gen, AtomicBoolean stop, int start, Set<Integer> ends,
		Set<TeleportMethod> userExclusions, Set<TeleportMethod> allSeedMethods, Transport seed,
		int bestRemaining, Queue<PathfinderConfig> configPool, GenTimer timer)
	{
		if (gen != generation.get() || stop.get())
		{
			return null;
		}
		PathfinderConfig config = configPool.poll();
		if (config == null)
		{
			// Should not happen (pool size == max concurrency), but never block on it.
			config = planningConfig.copyForParallelSearch();
		}
		try
		{
			// Exclude every other global teleport so the search is forced onto (at most) this one.
			Set<TeleportMethod> seedExclusions = new HashSet<>(allSeedMethods);
			seedExclusions.remove(TeleportMethod.fromTransport(seed));
			seedExclusions.addAll(userExclusions);
			long rebuildStart = System.nanoTime();
			config.rebuildAvailabilityWithExclusions(seedExclusions);
			long searchStart = System.nanoTime();
			Pathfinder pathfinder = new Pathfinder(config, start, ends);
			pathfinder.run();
			long searchEnd = System.nanoTime();
			synchronized (timer)
			{
				timer.rebuildNanos += searchStart - rebuildStart;
				timer.searchNanos += searchEnd - searchStart;
				timer.searches++;
			}

			PathfinderResult result = pathfinder.getResult();
			List<PathStep> path = (result != null) ? result.getPathSteps() : List.of();
			if (result == null || path.isEmpty())
			{
				return null;
			}
			boolean reached = result.isReached();
			int remaining = reached ? 0 : remainingDistance(path, ends);
			if (bestRemaining >= 0 && remaining > bestRemaining + CLOSEST_DISTANCE_TOLERANCE)
			{
				return null;
			}
			MethodScan scan = scanMethods(config, path);
			if (scan.methods.isEmpty())
			{
				// Walk-only: the seed teleport didn't help.
				return null;
			}
			return new SeedResult(path, scan, result.getTotalCost(), reached);
		}
		finally
		{
			configPool.offer(config);
		}
	}

	/**
	 * A candidate route produced by one parallel seed search, before signature dedup on the
	 * generation thread.
	 */
	private static final class SeedResult
	{
		private final List<PathStep> path;
		private final MethodScan scan;
		private final int totalCost;
		private final boolean reached;

		SeedResult(List<PathStep> path, MethodScan scan, int totalCost, boolean reached)
		{
			this.path = path;
			this.scan = scan;
			this.totalCost = totalCost;
			this.reached = reached;
		}
	}

	private static int remainingDistance(List<PathStep> path, Set<Integer> targets)
	{
		int end = path.get(path.size() - 1).getPackedPosition();
		int best = Integer.MAX_VALUE;
		for (int target : targets)
		{
			best = Math.min(best, WorldPointUtil.distanceBetween(end, target));
		}
		return best;
	}

	private static int closestTarget(int start, Set<Integer> targets)
	{
		int best = WorldPointUtil.UNDEFINED;
		int bestDistance = Integer.MAX_VALUE;
		for (int target : targets)
		{
			int distance = WorldPointUtil.distanceBetween(start, target);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = target;
			}
		}
		return best;
	}

	private void emit(int gen, ResultListener listener, List<RouteOption> routes,
		List<TeleportMethod> catalog, Map<TeleportMethod, MethodAvailability> unavailable, boolean done)
	{
		if (gen == generation.get())
		{
			listener.onUpdate(routes, catalog, unavailable, done);
		}
	}

	/**
	 * Runs {@code setExcludedMethods + refresh} (and, when {@code endsToFilter} is non-null, target
	 * wilderness filtering) on the client thread and blocks the worker until it completes.
	 *
	 * @return false if the client thread did not run the task within the timeout.
	 */
	private boolean refreshOnClientThread(Set<TeleportMethod> excluded, Set<Integer> endsToFilter, AlternativeRoutesMode mode)
	{
		final Set<TeleportMethod> excludedSnapshot = new HashSet<>(excluded);
		final CountDownLatch latch = new CountDownLatch(1);
		clientThread.invokeLater(() ->
		{
			try
			{
				planningConfig.setPlanningMode(mode == AlternativeRoutesMode.ALL_EVERYTHING);
				planningConfig.setBypassItemPossession(!mode.isOwned());
				planningConfig.setConsiderBank(mode == AlternativeRoutesMode.OWNED_WITH_BANK);
				planningConfig.setExcludedMethods(excludedSnapshot);
				planningConfig.refresh();
				if (endsToFilter != null)
				{
					planningConfig.filterLocations(endsToFilter, true);
				}
			}
			finally
			{
				latch.countDown();
			}
		});
		try
		{
			if (!latch.await(CLIENT_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
			{
				log.warn("Timed out waiting for planning config refresh on client thread");
				return false;
			}
			return true;
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Derives the ordered list of teleport/transport methods a path uses, by inspecting each edge for
	 * a method-type transport (anything beyond plain walking connectors) whose destination matches.
	 * Also collects which of those methods are bank-gated (only available in the post-bank state), i.e.
	 * the route walks to a bank to withdraw that method's required item first — so the panel can say
	 * which method the bank detour is for.
	 */
	private MethodScan scanMethods(PathfinderConfig config, List<PathStep> path)
	{
		List<TeleportMethod> methods = new ArrayList<>();
		Set<TeleportMethod> bankGated = new LinkedHashSet<>();
		if (path == null)
		{
			return new MethodScan(methods, bankGated, 0, new ArrayList<>(), 0);
		}
		int rawCost = 0;
		// Walking-leg lengths: tiles walked before each method (parallel to `methods`), and after the
		// last one. Plain connectors (doors, stairs, shortcuts) count into the leg they sit in.
		List<Integer> walkBefore = new ArrayList<>();
		int legSteps = 0;
		for (int i = 1; i < path.size(); i++)
		{
			PathStep from = path.get(i - 1);
			PathStep to = path.get(i);
			boolean bankVisited = from.isBankVisited() || to.isBankVisited();
			Transport chosen = matchMethodTransport(config, from.getPackedPosition(), to.getPackedPosition(), bankVisited);
			if (chosen != null)
			{
				TeleportMethod method = TeleportMethod.fromTransport(chosen);
				methods.add(method);
				walkBefore.add(legSteps);
				legSteps = 0;
				// Bank-gated: used in the post-bank state and not available without the bank.
				if (bankVisited && !availableWithoutBank(config, from.getPackedPosition(), chosen))
				{
					bankGated.add(method);
				}
			}
			// Raw cost: what the edge costs without any configured weights — a transport edge counts
			// only its travel time, a walking edge its tile distance (mirrors the search's own cost
			// accumulation minus the additional/weight terms).
			Transport edgeTransport = chosen != null
				? chosen
				: matchAnyTransport(config, from.getPackedPosition(), to.getPackedPosition(), bankVisited);
			int edgeCost = edgeTransport != null
				? edgeTransport.getDuration()
				: WorldPointUtil.distanceBetween(from.getPackedPosition(), to.getPackedPosition());
			rawCost += edgeCost;
			if (chosen == null)
			{
				legSteps += edgeCost;
			}
		}
		return new MethodScan(methods, bankGated, rawCost, walkBefore, legSteps);
	}

	private static boolean availableWithoutBank(PathfinderConfig config, int origin, Transport transport)
	{
		for (Transport candidate : config.getTransportsPacked(false)
			.getOrDefault(origin, TransportAvailability.EMPTY_TRANSPORTS))
		{
			if (candidate == transport)
			{
				return true;
			}
		}
		for (Transport candidate : config.getUsableTeleports(false))
		{
			if (candidate == transport)
			{
				return true;
			}
		}
		return false;
	}

	private static final class MethodScan
	{
		private final List<TeleportMethod> methods;
		private final Set<TeleportMethod> bankGated;
		// Path cost without any configured weights: walk distance plus transport travel times only.
		private final int rawCost;
		// Tiles walked before each method (parallel to methods) and after the last one.
		private final List<Integer> walkBefore;
		private final int trailingWalk;

		MethodScan(List<TeleportMethod> methods, Set<TeleportMethod> bankGated, int rawCost,
			List<Integer> walkBefore, int trailingWalk)
		{
			this.methods = methods;
			this.bankGated = bankGated;
			this.rawCost = rawCost;
			this.walkBefore = walkBefore;
			this.trailingWalk = trailingWalk;
		}
	}

	private static Transport matchMethodTransport(PathfinderConfig config, int origin, int destination, boolean bankVisited)
	{
		// Fixed-origin networks (fairy rings, spirit trees, boats, ...) keyed by their origin tile.
		Transport[] atOrigin = config.getTransportsPacked(bankVisited)
			.getOrDefault(origin, TransportAvailability.EMPTY_TRANSPORTS);
		for (Transport transport : atOrigin)
		{
			if (transport.getDestination() == destination && TeleportMethod.isMethodType(transport.getType()))
			{
				return transport;
			}
		}
		// Global teleports (spells/items/...): castable from anywhere, so the origin is wherever the
		// player stood; match purely on destination.
		for (Transport transport : config.getUsableTeleports(bankVisited))
		{
			if (transport.getDestination() == destination && TeleportMethod.isMethodType(transport.getType()))
			{
				return transport;
			}
		}
		return null;
	}

	/**
	 * Like {@link #matchMethodTransport} but without the method-type filter: also matches plain
	 * connectors (doors, stairs, agility shortcuts, ...) so the raw-cost scan can use the transport's
	 * travel time for any edge the search traversed via a transport.
	 */
	private static Transport matchAnyTransport(PathfinderConfig config, int origin, int destination, boolean bankVisited)
	{
		Transport[] atOrigin = config.getTransportsPacked(bankVisited)
			.getOrDefault(origin, TransportAvailability.EMPTY_TRANSPORTS);
		for (Transport transport : atOrigin)
		{
			if (transport.getDestination() == destination)
			{
				return transport;
			}
		}
		for (Transport transport : config.getUsableTeleports(bankVisited))
		{
			if (transport.getDestination() == destination)
			{
				return transport;
			}
		}
		return null;
	}

	private static String signature(List<TeleportMethod> methods)
	{
		if (methods.isEmpty())
		{
			return "<walk-only>";
		}
		StringBuilder sb = new StringBuilder();
		for (TeleportMethod method : methods)
		{
			sb.append(method.toString()).append('|');
		}
		return sb.toString();
	}
}
