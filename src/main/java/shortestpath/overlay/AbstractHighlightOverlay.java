package shortestpath.overlay;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.WorldPointUtil;
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
