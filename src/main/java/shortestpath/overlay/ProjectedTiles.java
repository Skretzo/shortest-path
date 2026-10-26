package shortestpath.overlay;

import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;

import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;

/**
 * The shared packed-point projection walk: map a packed world point through
 * {@link WorldPointUtil#toLocalInstance} (one point, or several inside an
 * instance), convert each mapped point to a {@link LocalPoint}, apply the
 * caller's projector, and hand each non-null projection to the consumer.
 * Iterating every mapped point — not just the first — is the contract the
 * tile fills rely on inside instances.
 * <p>
 * Only the skips that were uniform across every former call site are baked
 * in: the {@link WorldPointUtil#UNDEFINED} sentinel, a null local point, and
 * a null projection. Per-site input guards ({@code client == null},
 * {@code b == UNDEFINED}) and pre-projection checks like {@code tileCenter}'s
 * plane test stay at the call sites — the optional {@code pointFilter} is the
 * slot that check folds into later. The consumer returning {@code true} stops
 * the walk early ({@code tileCenter}'s first-valid semantics).
 * <p>
 * This package is a leaf: nothing here may reference the plugin type.
 */
final class ProjectedTiles
{
	private ProjectedTiles()
	{
	}

	static <T> void eachProjectedTile(Client client, int packed, IntPredicate pointFilter,
		Function<LocalPoint, T> projector, Predicate<T> consumer)
	{
		PrimitiveIntList points = WorldPointUtil.toLocalInstance(client, packed);
		for (int i = 0; i < points.size(); i++)
		{
			int point = points.get(i);
			if (point == WorldPointUtil.UNDEFINED || (pointFilter != null && !pointFilter.test(point)))
			{
				continue;
			}

			LocalPoint lp = WorldPointUtil.toLocalPoint(client, point);
			if (lp == null)
			{
				continue;
			}

			T result = projector.apply(lp);
			if (result == null)
			{
				continue;
			}

			if (consumer.test(result))
			{
				return;
			}
		}
	}
}
