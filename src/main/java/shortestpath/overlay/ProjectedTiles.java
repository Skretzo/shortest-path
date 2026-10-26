package shortestpath.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;

import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.poh.PohService;
import shortestpath.presentation.PathAnnotator;

/**
 * The shared packed-point projection walk: map a packed world point through
 * {@link WorldPointUtil#toLocalInstance} (one point, or several inside an
 * instance), convert each mapped point to a {@link LocalPoint}, apply the
 * caller's projector, and hand each non-null projection to the consumer.
 * Iterating every mapped point — not just the first — is the contract the
 * tile fills rely on inside instances.
 * <p>
 * The baked-in skips are: the {@link WorldPointUtil#UNDEFINED} sentinel, a
 * null local point, and a null projection. The sentinel check is a
 * normalization, not a uniform former guard — only {@code drawTile} and
 * {@code tileCenter} tested for it, while {@code drawOnMinimap},
 * {@code drawLabelAtPackedLocation}, and {@code drawTeleportPulse} relied on
 * {@code toLocalPoint(UNDEFINED)} returning {@code null}; hoisting it here is
 * safe because a sentinel can never project. Per-site input guards
 * ({@code client == null}, {@code b == UNDEFINED}) and pre-projection checks
 * like {@code tileCenter}'s plane test stay at the call sites — the optional
 * {@code pointFilter} is the slot that check folds into later. The consumer
 * returning {@code true} stops the walk early ({@code tileCenter}'s
 * first-valid semantics).
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

	/**
	 * The shared per-step walk for the tile-indexed style branches
	 * (TURN_MARKERS / SPRITE_MARKERS / the TILES default): for every path
	 * step the caller's marker predicate decides whether a marker draws —
	 * the POH skip wraps that marker draw only — while the per-step
	 * transport info and the trailing unreached-targets pass run
	 * unconditionally. The loop index doubles as the tile counter
	 * ({@code counter == i} in all three branches, POH-skipped steps
	 * included); the segment-pair styles keep their own
	 * {@code 1 + counter++} loops and never ride this walker.
	 * <p>
	 * Draw state stays on the overlay: the walker yields steps and calls
	 * back into {@link PathTileOverlay#drawTransportInfo} and
	 * {@link PathTileOverlay#drawUnreachedTargets}; per-frame accumulators
	 * and per-style counters remain instance state there.
	 */
	static void forEachVisibleStep(PathTileOverlay overlay, Graphics2D graphics, List<PathStep> path,
		PathAnnotator annotator, Color colorCalculating, ActiveSearch search,
		BiPredicate<List<PathStep>, Integer> markerPredicate,
		BiConsumer<PathStep, Integer> markerDraw, boolean drawUnreached)
	{
		for (int i = 0; i < path.size(); i++)
		{
			PathStep currentStep = path.get(i);
			int pathPoint = currentStep.getPackedPosition();
			if (markerPredicate.test(path, i)
				&& !PohService.isInsidePoh(WorldPointUtil.unpackWorldX(pathPoint),
					WorldPointUtil.unpackWorldY(pathPoint)))
			{
				markerDraw.accept(currentStep, i);
			}
			overlay.drawTransportInfo(graphics, currentStep, annotator.nextPathStep(path, i), path, i, search);
		}
		overlay.drawUnreachedTargets(graphics, path, colorCalculating, drawUnreached, search);
	}
}
