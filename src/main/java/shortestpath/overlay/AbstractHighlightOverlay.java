package shortestpath.overlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;

abstract class AbstractHighlightOverlay extends Overlay
{
	// Extra tiles of grace past the recalculation distance: beyond it the route
	// recalculates but the stale path stays on screen while the replacement is
	// being computed, so the nearest step is still the best guide meanwhile.
	private static final int REPLANNING_MARGIN_TILES = 5;

	protected final Client client;
	protected final PathScheduler scheduler;
	protected final PathAnnotator annotator;
	protected final Settings settings;

	protected AbstractHighlightOverlay(Client client, PathScheduler scheduler, PathAnnotator annotator,
		Settings settings)
	{
		this.client = client;
		this.scheduler = scheduler;
		this.annotator = annotator;
		this.settings = settings;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	/**
	 * The shared highlight pipeline: the subclass gate, the active-search
	 * and path guards, the per-overlay extra gates, the player-path index
	 * bound, then the subclass draw tail. Evaluation order matches the
	 * original trio bodies stage for stage — the extra gates ran after the
	 * path guards and before the path-index bound, and they keep that
	 * placement here.
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!enabled())
		{
			return null;
		}

		ActiveSearch search = scheduler.getActiveSearch();
		if (search == null)
		{
			return null;
		}

		List<PathStep> path = search.getPath();
		if (path == null || path.isEmpty())
		{
			return null;
		}

		if (!extraGatesPass())
		{
			return null;
		}

		int pathIndex = getPlayerPathIndex(path);
		if (!pathIndexUsable(pathIndex, path))
		{
			return null;
		}

		return drawHighlights(graphics, path, pathIndex, search);
	}

	/** Whether this overlay's highlight setting is on. */
	protected abstract boolean enabled();

	/**
	 * Per-overlay conjuncts that run after the path guards and before the
	 * path-index bound (bank's widget/open/destination checks, spellbook's
	 * spell-layer widget). Inventory has none.
	 */
	protected boolean extraGatesPass()
	{
		return true;
	}

	/**
	 * The per-overlay path-index bound. The default requires a next step
	 * ({@code pathIndex + 1 < path.size()}) because the draw tail indexes
	 * {@code path.get(pathIndex + 1)}; bank never does, so it overrides
	 * this with the looser {@code pathIndex >= 0}.
	 */
	protected boolean pathIndexUsable(int pathIndex, List<PathStep> path)
	{
		return pathIndex >= 0 && pathIndex + 1 < path.size();
	}

	/** The subclass draw tail: id-set computation plus per-child drawing. */
	protected abstract Dimension drawHighlights(Graphics2D graphics, List<PathStep> path,
		int pathIndex, ActiveSearch search);

	/**
	 * The shared per-widget outline loop: draw the {@code colour} outline on every
	 * non-hidden widget carrying an item id in {@code ids}, minus any id in
	 * {@code skipIds} — the equipped-staff exclusion, where wearing the item already
	 * satisfies the requirement so it is never highlighted. A {@code quantity} of 0
	 * reads the widget's own stack size; a positive value forces it — the equipment
	 * arm pins 1 because worn items are not stacks.
	 */
	static void drawItemOutlines(Graphics2D graphics, ItemManager itemManager, Widget[] widgets,
		Set<Integer> ids, Color colour)
	{
		drawItemOutlines(graphics, itemManager, widgets, ids, null, 0, colour);
	}

	static void drawItemOutlines(Graphics2D graphics, ItemManager itemManager, Widget[] widgets,
		Set<Integer> ids, Set<Integer> skipIds, int quantity, Color colour)
	{
		if (widgets == null)
		{
			return;
		}
		for (Widget item : widgets)
		{
			if (item == null || item.isHidden() || item.getItemId() < 0
				|| !ids.contains(item.getItemId())
				|| (skipIds != null && skipIds.contains(item.getItemId())))
			{
				continue;
			}
			BufferedImage outline = itemManager.getItemOutline(item.getItemId(),
				quantity > 0 ? quantity : item.getItemQuantity(), colour);
			if (outline != null)
			{
				graphics.drawImage(outline, item.getBounds().x, item.getBounds().y, null);
			}
		}
	}


	protected int getPlayerPathIndex(List<PathStep> path)
	{
		if (client.getLocalPlayer() == null)
		{
			return -1;
		}
		int playerLocation = WorldPointUtil.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
		return playerPathIndex(path, playerLocation, settings.lifecycle().recalculateDistance());
	}

	static int playerPathIndex(List<PathStep> path, int playerLocation, int recalculateDistance)
	{
		for (int i = 0; i < path.size(); i++)
		{
			if (path.get(i).getPackedPosition() == playerLocation)
			{
				return i;
			}
		}

		// On an exact-match miss the player may still be close enough to the path that the
		// route has not been recalculated; keep the highlight on the nearest step for as
		// long as the displayed route survives. The strict bound mirrors isNearPath, and
		// steps on other planes (Integer.MAX_VALUE distance) are never candidates.
		int maxDistance = recalculateDistance < 0 ? -1 : recalculateDistance + REPLANNING_MARGIN_TILES;
		int bestIndex = -1;
		int bestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < path.size(); i++)
		{
			int distance = WorldPointUtil.distanceBetween(playerLocation, path.get(i).getPackedPosition());
			if (distance == Integer.MAX_VALUE || (maxDistance >= 0 && distance >= maxDistance))
			{
				continue;
			}
			if (distance < bestDistance)
			{
				bestDistance = distance;
				bestIndex = i;
			}
		}
		return bestIndex;
	}
}
