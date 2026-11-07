package shortestpath.pathfinder.astar;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.pathfinder.CollisionMap;

/**
 * Keeps A* routing preparation alive between queries, following the model's lifetimes:
 *
 * <pre>
 * static   RoutingStatic                 owned by the caller
 * account  SiteGraph                     reused while the routing fingerprint is unchanged
 * target   PreparedTarget                reused for the same target set while its SiteGraph and
 *                                        collision map are
 * search   AStarForwardSearch            per query
 * </pre>
 *
 * <p>The account key is the {@link PreparedRoutingAccount#fingerprint() routing fingerprint}
 * rather than object identity, because the plugin recompiles the account before every query even
 * when nothing routing-relevant changed. Changing the graph drops every prepared target.
 *
 * <p>Prepared targets keep the collision map they were built with and the forward search reads
 * it, so they are only reused with that same collision map instance.
 */
public final class AStarRoutingSession
{
	public static final int DEFAULT_TARGET_CAPACITY = 16;

	private final int targetCapacity;
	private final LinkedHashMap<TargetKey, PreparedTarget> targets;
	private SiteGraph graph;
	private CollisionMap targetCollision;

	public AStarRoutingSession()
	{
		this(DEFAULT_TARGET_CAPACITY);
	}

	public AStarRoutingSession(int targetCapacity)
	{
		if (targetCapacity < 1) throw new IllegalArgumentException("target capacity must be positive");
		this.targetCapacity = targetCapacity;
		this.targets = new LinkedHashMap<>(targetCapacity * 2, 0.75f, true);
	}

	/** The account-lifetime graph for {@code account}, rebuilt only when its fingerprint changes. */
	public synchronized Lookup<SiteGraph> graph(RoutingStatic stat, PreparedRoutingAccount account)
	{
		if (stat == null || account == null) throw new NullPointerException();
		if (graph != null && graph.routingStatic() == stat
			&& graph.preparedAccount().fingerprint() == account.fingerprint())
		{
			return new Lookup<>(graph, true);
		}
		graph = new SiteGraph(stat, account);
		targets.clear();
		targetCollision = null;
		return new Lookup<>(graph, false);
	}

	/** The prepared target for {@code packed}, reused while {@code graph} is the session's graph. */
	public Lookup<PreparedTarget> target(SiteGraph graph, CollisionMap collision, int packed)
	{
		return target(graph, collision, new int[] {packed});
	}

	/**
	 * The prepared target for the set {@code packedTargets} (order and duplicates do not matter),
	 * reused while {@code graph} is the session's graph.
	 */
	public synchronized Lookup<PreparedTarget> target(SiteGraph graph, CollisionMap collision, int[] packedTargets)
	{
		return target(graph, collision, new TargetKey(packedTargets, null));
	}

	/** Target identity includes the terminal layers; duplicate coordinates union their masks. */
	public synchronized Lookup<PreparedTarget> target(SiteGraph graph, CollisionMap collision, int[] packedTargets,
		int[] terminalLayers)
	{
		return target(graph, collision, new TargetKey(packedTargets, java.util.Objects.requireNonNull(terminalLayers)));
	}

	/**
	 * The prepared target for {@code packedTargets} with seed costs (see {@link PreparedTarget#prepare(SiteGraph,
	 * CollisionMap, int[], int[], int[])}): the same target set with other seeds is another entry.
	 */
	public synchronized Lookup<PreparedTarget> target(SiteGraph graph, CollisionMap collision, int[] packedTargets,
		int[] terminalLayers, int[] seedCosts)
	{
		return target(graph, collision, new TargetKey(packedTargets, java.util.Objects.requireNonNull(terminalLayers),
			java.util.Objects.requireNonNull(seedCosts)));
	}

	private Lookup<PreparedTarget> target(SiteGraph graph, CollisionMap collision, TargetKey key)
	{
		if (graph == null || collision == null) throw new NullPointerException();
		if (graph != this.graph)
			return new Lookup<>(key.prepare(graph, collision), false);
		if (collision != targetCollision)
		{
			targets.clear();
			targetCollision = collision;
		}
		PreparedTarget cached = targets.get(key);
		if (cached != null) return new Lookup<>(cached, true);
		PreparedTarget prepared = key.prepare(graph, collision);
		targets.put(key, prepared);
		for (Iterator<Map.Entry<TargetKey, PreparedTarget>> eldest = targets.entrySet().iterator();
			targets.size() > targetCapacity; )
		{
			eldest.next();
			eldest.remove();
		}
		return new Lookup<>(prepared, false);
	}

	/** Drops the prepared targets but keeps the account graph. */
	public synchronized void clearTargets()
	{
		targets.clear();
	}

	/** Drops every cached stage, e.g. when the static routing data is replaced. */
	public synchronized void clear()
	{
		graph = null;
		targets.clear();
		targetCollision = null;
	}

	public synchronized int cachedTargetCount()
	{
		return targets.size();
	}

	/** Unsigned tile coordinates and their two terminal-layer bits, sorted and duplicate-free. */
	@EqualsAndHashCode(onlyExplicitlyIncluded = true)
	private static final class TargetKey
	{
		@EqualsAndHashCode.Include
		private final long[] states;
		// Each canonical target's seed cost, or null for destinations alone
		@EqualsAndHashCode.Include
		private final int[] seeds;

		TargetKey(int[] packedTargets, int[] terminalLayers)
		{
			states = TargetOverlay.canonicalTargets(packedTargets, terminalLayers);
			seeds = null;
		}

		TargetKey(int[] packedTargets, int[] terminalLayers, int[] seedCosts)
		{
			states = TargetOverlay.canonicalTargets(packedTargets, terminalLayers);
			seeds = TargetOverlay.canonicalSeedCosts(states, packedTargets, seedCosts);
		}

		PreparedTarget prepare(SiteGraph graph, CollisionMap collision)
		{
			return seeds == null ? PreparedTarget.prepare(graph, collision, states)
				: PreparedTarget.prepare(graph, collision, states, seeds);
		}
	}

	/** A cached stage together with whether it was reused rather than built by this call. */
	@Getter
	@Accessors(fluent = true)
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Lookup<T>
	{
		private final T value;
		private final boolean reused;
	}
}
