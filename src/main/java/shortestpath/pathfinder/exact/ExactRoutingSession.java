package shortestpath.pathfinder.exact;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import shortestpath.pathfinder.CollisionMap;

/**
 * Keeps exact-routing preparation alive between queries, following the model's lifetimes:
 *
 * <pre>
 * static   RoutingStatic                 owned by the caller
 * account  SiteGraph                     reused while the routing fingerprint is unchanged
 * target   PreparedTarget                reused for the same target set while its SiteGraph and
 *                                        collision map are
 * search   ExactForwardSearch            per query
 * </pre>
 *
 * <p>The account key is the {@link PreparedRoutingAccount#fingerprint() routing fingerprint}
 * rather than object identity, because the plugin recompiles the account before every query even
 * when nothing routing-relevant changed. Changing the graph drops every prepared target.
 *
 * <p>Prepared targets keep the collision map they were built with and the forward search reads
 * it, so they are only reused with that same collision map instance.
 */
public final class ExactRoutingSession
{
	public static final int DEFAULT_TARGET_CAPACITY = 16;

	private final int targetCapacity;
	private final LinkedHashMap<TargetKey, PreparedTarget> targets;
	private SiteGraph graph;
	private CollisionMap targetCollision;

	public ExactRoutingSession()
	{
		this(DEFAULT_TARGET_CAPACITY);
	}

	public ExactRoutingSession(int targetCapacity)
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
		if (graph == null || collision == null || packedTargets == null) throw new NullPointerException();
		if (graph != this.graph)
			return new Lookup<>(PreparedTarget.prepare(graph, collision, packedTargets), false);
		if (collision != targetCollision)
		{
			targets.clear();
			targetCollision = collision;
		}
		TargetKey key = new TargetKey(packedTargets);
		PreparedTarget cached = targets.get(key);
		if (cached != null) return new Lookup<>(cached, true);
		PreparedTarget prepared = PreparedTarget.prepare(graph, collision, packedTargets);
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

	/** A target set in canonical (sorted, duplicate-free) form. */
	private static final class TargetKey
	{
		private final int[] tiles;
		private final int hash;

		TargetKey(int[] packedTargets)
		{
			int[] sorted = packedTargets.clone();
			Arrays.sort(sorted);
			int count = 0;
			for (int i = 0; i < sorted.length; i++)
				if (count == 0 || sorted[count - 1] != sorted[i])
					sorted[count++] = sorted[i];
			tiles = Arrays.copyOf(sorted, count);
			hash = Arrays.hashCode(tiles);
		}

		@Override
		public boolean equals(Object other)
		{
			return other instanceof TargetKey && Arrays.equals(tiles, ((TargetKey) other).tiles);
		}

		@Override
		public int hashCode()
		{
			return hash;
		}
	}

	/** A cached stage together with whether it was reused rather than built by this call. */
	public static final class Lookup<T>
	{
		private final T value;
		private final boolean reused;

		Lookup(T value, boolean reused)
		{
			this.value = value;
			this.reused = reused;
		}

		public T value()
		{
			return value;
		}
		public boolean reused()
		{
			return reused;
		}
	}
}
