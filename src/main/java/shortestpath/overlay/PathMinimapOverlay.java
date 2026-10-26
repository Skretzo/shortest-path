package shortestpath.overlay;

import com.google.inject.Inject;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.scheduler.PathRenderState;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;
import shortestpath.widgets.WidgetGeometry;

public class PathMinimapOverlay extends Overlay
{
	private final Client client;
	private final Settings settings;
	private final PathRenderState renderState;
	private final PathScheduler scheduler;
	private final WidgetGeometry geometry;

	@Inject
	private PathMinimapOverlay(Client client, Settings settings, PathRenderState renderState,
		PathScheduler scheduler, WidgetGeometry geometry)
	{
		this.client = client;
		this.settings = settings;
		this.renderState = renderState;
		this.scheduler = scheduler;
		this.geometry = geometry;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	public static void renderMinimapRect(Client client, Graphics2D graphics, Point center, Color color)
	{
		renderMinimapRect(client, graphics, center, color, 1.0);
	}

	// The scale overloads draw a sailing path's half-size dots
	public static void renderMinimapRect(Client client, Graphics2D graphics, Point center, Color color, double scale)
	{
		double angle = client.getCameraYawTarget() * Perspective.UNIT14;
		double tileSize = client.getMinimapZoom() * scale;
		int x = (int) Math.round(center.getX() - tileSize / 2);
		int y = (int) Math.round(center.getY() - tileSize / 2);
		int width = (int) Math.round(tileSize);
		int height = (int) Math.round(tileSize);
		graphics.setColor(color);
		graphics.rotate(angle, center.getX(), center.getY());
		graphics.fillRect(x, y, width, height);
		graphics.rotate(-angle, center.getX(), center.getY());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ActiveSearch search = scheduler.getActiveSearch();
		if (!settings.display().drawMinimap() || search == null)
		{
			return null;
		}

		Shape minimapClipArea = geometry.getMinimapClipArea();
		if (minimapClipArea == null)
		{
			return null;
		}
		else
		{
			graphics.setClip(minimapClipArea);
		}
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		java.util.List<PathStep> pathPoints = search.getPath();
		// On a boat the path sails the boat's headings, with a point a few tiles apart at the end of each move: half-size
		// blue dots
		boolean sailing = renderState.isSailing();
		Color pathColor = sailing && !renderState.isPathUnreachable()
			? PathRenderState.SAILING_PATH_COLOUR : renderState.getPathColor();
		for (PathStep point : pathPoints)
		{
			int pathPoint = point.getPackedPosition();
			if (WorldPointUtil.unpackWorldPlane(pathPoint) != client.getTopLevelWorldView().getPlane())
			{
				continue;
			}

			drawOnMinimap(graphics, pathPoint, pathColor, sailing ? 0.5 : 1.0);
		}
		for (int target : search.getTargets().points())
		{
			if (!pathPoints.isEmpty() && target != pathPoints.get(pathPoints.size() - 1).getPackedPosition())
			{
				drawOnMinimap(graphics, target, settings.display().colourPathCalculating());
			}
		}

		return null;
	}

	private void drawOnMinimap(Graphics2D graphics, int location, Color color)
	{
		drawOnMinimap(graphics, location, color, 1.0);
	}

	private void drawOnMinimap(Graphics2D graphics, int location, Color color, double scale)
	{
		ProjectedTiles.eachProjectedTile(client, location, null,
			lp -> Perspective.localToMinimap(client, lp), posOnMinimap ->
			{
				renderMinimapRect(client, graphics, posOnMinimap, color, scale);
				return false;
			});
	}
}
