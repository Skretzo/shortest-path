package shortestpath.pathfinder;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The targets of one route: at most one {@link Target} per tile. A search ends at whichever target
 * is cheapest to reach. Immutable.
 */
public final class Targets implements Iterable<Target>
{
	public static final Targets NONE = new Targets(Map.of());

	private final Map<Integer, Target> byPoint;

	private Targets(Map<Integer, Target> byPoint)
	{
		this.byPoint = byPoint;
	}

	/** Targets at {@code points}. */
	public static Targets at(Collection<Integer> points)
	{
		Map<Integer, Target> byPoint = new LinkedHashMap<>();
		for (int point : points)
		{
			byPoint.put(point, Target.at(point));
		}
		return of(byPoint);
	}

	/** {@code targets} as one set, keeping one target per tile. */
	public static Targets of(Collection<Target> targets)
	{
		Map<Integer, Target> byPoint = new LinkedHashMap<>();
		for (Target target : targets)
		{
			byPoint.putIfAbsent(target.getPoint(), target);
		}
		return of(byPoint);
	}

	private static Targets of(Map<Integer, Target> byPoint)
	{
		return byPoint.isEmpty() ? NONE : new Targets(Collections.unmodifiableMap(byPoint));
	}

	/** The packed target tiles. */
	public Set<Integer> points()
	{
		return byPoint.keySet();
	}

	/** The target at {@code point}, or {@code null} when there is none. */
	public Target get(int point)
	{
		return byPoint.get(point);
	}

	public boolean contains(int point)
	{
		return byPoint.containsKey(point);
	}

	public boolean isEmpty()
	{
		return byPoint.isEmpty();
	}

	public int size()
	{
		return byPoint.size();
	}

	/** The targets at exactly {@code points}: these targets where they have one, new ones elsewhere. */
	public Targets restrictTo(Collection<Integer> points)
	{
		Map<Integer, Target> byPoint = new LinkedHashMap<>();
		for (int point : points)
		{
			byPoint.put(point, this.byPoint.getOrDefault(point, Target.at(point)));
		}
		return of(byPoint);
	}

	/** These targets plus those of {@code more} at tiles these don't already have. */
	public Targets plus(Targets more)
	{
		Map<Integer, Target> byPoint = new LinkedHashMap<>(this.byPoint);
		for (Target target : more)
		{
			byPoint.putIfAbsent(target.getPoint(), target);
		}
		return of(byPoint);
	}

	@Override
	public Iterator<Target> iterator()
	{
		return byPoint.values().iterator();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Targets && byPoint.equals(((Targets) o).byPoint);
	}

	@Override
	public int hashCode()
	{
		return byPoint.hashCode();
	}

	@Override
	public String toString()
	{
		return byPoint.values().toString();
	}
}
