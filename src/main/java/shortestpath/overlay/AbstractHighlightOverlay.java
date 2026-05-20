package shortestpath.overlay;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.ShortestPathPlugin;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;

abstract class AbstractHighlightOverlay extends Overlay
{
	protected final Client client;
	protected final ShortestPathPlugin plugin;

	protected AbstractHighlightOverlay(Client client, ShortestPathPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
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
		for (int i = 0; i < path.size(); i++)
		{
			if (path.get(i).getPackedPosition() == playerLocation)
			{
				return i;
			}
		}
		return -1;
	}
}
