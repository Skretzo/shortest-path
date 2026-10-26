package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.util.List;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.TileStyle;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathRenderState;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;
import shortestpath.transport.Transport;
import shortestpath.widgets.WidgetGeometry;

public class PathMapOverlay extends Overlay
{
	private final Client client;
	private final Settings settings;
	private final PathRenderState renderState;
	private final PathScheduler scheduler;
	private final WidgetGeometry geometry;
	private final PathAnnotator annotator;

	@Inject
	private PathMapOverlay(Client client, Settings settings, PathRenderState renderState,
		PathScheduler scheduler, WidgetGeometry geometry, PathAnnotator annotator)
	{
		this.client = client;
		this.settings = settings;
		this.renderState = renderState;
		this.scheduler = scheduler;
		this.geometry = geometry;
		this.annotator = annotator;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.MANUAL);
		drawAfterLayer(InterfaceID.Worldmap.MAP_CONTAINER);
	}

	private static final Color COLOR_AVAILABLE = Color.WHITE;
	private static final Color COLOR_UNAVAILABLE = Color.ORANGE;

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!settings.display().drawMap())
		{
			return null;
		}

		if (client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null)
		{
			return null;
		}

		Rectangle worldMapRectangle = Objects.requireNonNull(client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER)).getBounds();
		Area worldMapClipArea = getWorldMapClipArea(worldMapRectangle);
		graphics.setClip(worldMapClipArea);

		if (settings.display().drawCollisionMap())
		{
			graphics.setColor(settings.display().colourCollisionMap());
			int mapWorldPoint = geometry.calculateMapPoint(worldMapRectangle.x, worldMapRectangle.y);
			int extentX = WorldPointUtil.unpackWorldX(mapWorldPoint);
			int extentY = WorldPointUtil.unpackWorldY(mapWorldPoint);
			int extentWidth = getWorldMapExtentWidth(worldMapRectangle);
			int extentHeight = getWorldMapExtentHeight(worldMapRectangle);
			final CollisionMap map = annotator.getMap();
			final int z = client.getTopLevelWorldView().getPlane();
			for (int x = extentX; x < (extentX + extentWidth + 1); x++)
			{
				for (int y = extentY - extentHeight; y < (extentY + 1); y++)
				{
					if (map.isBlocked(x, y, z))
					{
						drawOnMap(graphics, WorldPointUtil.packWorldPoint(x, y, z), false, null);
					}
				}
			}
		}

		if (settings.display().drawTransports())
		{
			OverlayDrawing.forEachDisplayTransport(annotator, new OverlayDrawing.TransportVisitor()
			{
				private int mapAX;
				private int mapAY;

				@Override
				public boolean beginOrigin(int origin)
				{
					mapAX = geometry.mapWorldPointToGraphicsPointX(origin);
					mapAY = geometry.mapWorldPointToGraphicsPointY(origin);
					return worldMapClipArea.contains(mapAX, mapAY);
				}

				@Override
				public void visit(int origin, Transport b, boolean isAvailable)
				{
					int mapBX = geometry.mapWorldPointToGraphicsPointX(b.getDestination());
					int mapBY = geometry.mapWorldPointToGraphicsPointY(b.getDestination());
					if (!worldMapClipArea.contains(mapBX, mapBY))
					{
						return;
					}

					graphics.setColor(isAvailable ? COLOR_AVAILABLE : COLOR_UNAVAILABLE);
					graphics.drawLine(mapAX, mapAY, mapBX, mapBY);
				}
			});
		}

		ActiveSearch search = scheduler.getActiveSearch();
		if (search != null)
		{
			Color colour = renderState.getPathColor();
			List<PathStep> path = search.getPath();
			Point cursorPos = client.getMouseCanvasPosition();
			if (renderState.isSailing())
			{
				// On a boat the path sails the boat's headings: a line along each heading it holds, with a small square
				// at the end of each move
				graphics.setColor(renderState.isPathUnreachable() ? renderState.getPathColor() : PathRenderState.SAILING_PATH_COLOUR);
				for (int i = 0; i < path.size(); i++)
				{
					int point = path.get(i).getPackedPosition();
					if (i > 0)
					{
						drawSolidLineOnMap(graphics, path.get(i - 1).getPackedPosition(), point);
					}
					drawSmallSquareOnMap(graphics, point);
				}
			}
			else if (TileStyle.ARROW_LINE.equals(settings.display().pathStyle()))
			{
				graphics.setColor(colour);
				drawArrowPath(graphics, path);
			}
			else
			{
				for (int i = 0; i < path.size(); i++)
				{
					graphics.setColor(colour);
					int point = path.get(i).getPackedPosition();
					int lastPoint = (i > 0) ? path.get(i - 1).getPackedPosition() : point;
					if (WorldPointUtil.distanceBetween(point, lastPoint) > 1)
					{
						drawOnMap(graphics, lastPoint, point, true, cursorPos);
					}
					drawOnMap(graphics, point, true, cursorPos);
				}
			}
			for (int target : search.getTargets().points())
			{
				if (!path.isEmpty() && target != path.get(path.size() - 1).getPackedPosition())
				{
					graphics.setColor(settings.display().colourPathCalculating());
					drawOnMap(graphics, target, true, cursorPos);
				}
			}
		}

		return null;
	}

	/**
	 * Draws the path as a directed polyline: consecutive same-direction walking steps are compressed
	 * into one straight segment, each segment ends in a small arrowhead showing travel direction, and
	 * teleport/transport jumps are dashed. (Arrowhead technique adapted from Quest Helper's
	 * DirectionArrow, see {@link ArrowHead}.)
	 */
	private void drawArrowPath(Graphics2D graphics, List<PathStep> path)
	{
		final Stroke walkStroke = new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		final Stroke jumpStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);

		int i = 0;
		while (i < path.size() - 1)
		{
			int from = path.get(i).getPackedPosition();
			int next = path.get(i + 1).getPackedPosition();

			if (WorldPointUtil.distanceBetween(from, next) > 1)
			{
				// Teleport/transport jump: dashed segment.
				drawMapSegment(graphics, from, next, jumpStroke);
				i++;
				continue;
			}

			// Extend the straight walking run while the direction stays the same.
			int dx = WorldPointUtil.unpackWorldX(next) - WorldPointUtil.unpackWorldX(from);
			int dy = WorldPointUtil.unpackWorldY(next) - WorldPointUtil.unpackWorldY(from);
			int j = i + 1;
			while (j < path.size() - 1)
			{
				int a = path.get(j).getPackedPosition();
				int b = path.get(j + 1).getPackedPosition();
				if (WorldPointUtil.distanceBetween(a, b) > 1
					|| WorldPointUtil.unpackWorldX(b) - WorldPointUtil.unpackWorldX(a) != dx
					|| WorldPointUtil.unpackWorldY(b) - WorldPointUtil.unpackWorldY(a) != dy)
				{
					break;
				}
				j++;
			}
			drawMapSegment(graphics, from, path.get(j).getPackedPosition(), walkStroke);
			i = j;
		}
	}

	private void drawMapSegment(Graphics2D graphics, int from, int to, Stroke stroke)
	{
		int x1 = geometry.mapWorldPointToGraphicsPointX(from);
		int y1 = geometry.mapWorldPointToGraphicsPointY(from);
		int x2 = geometry.mapWorldPointToGraphicsPointX(to);
		int y2 = geometry.mapWorldPointToGraphicsPointY(to);
		if (x1 == Integer.MIN_VALUE || y1 == Integer.MIN_VALUE
			|| x2 == Integer.MIN_VALUE || y2 == Integer.MIN_VALUE)
		{
			return;
		}
		graphics.setStroke(stroke);
		graphics.drawLine(x1, y1, x2, y2);
		// Skip the head on segments too short to fit it (e.g. zoomed far out).
		if (Math.hypot(x2 - x1, y2 - y1) >= 10)
		{
			ArrowHead.draw(graphics, x1, y1, x2, y2, 7);
		}
	}

	private void drawSmallSquareOnMap(Graphics2D graphics, int point)
	{
		int x = geometry.mapWorldPointToGraphicsPointX(point);
		int y = geometry.mapWorldPointToGraphicsPointY(point);
		int nextX = geometry.mapWorldPointToGraphicsPointX(WorldPointUtil.dxdy(point, 1, -1));
		if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || nextX == Integer.MIN_VALUE)
		{
			return;
		}
		int size = Math.max(2, (nextX - x) / 2);
		graphics.fillRect(x - size / 2, y - size / 2, size, size);
	}

	private void drawSolidLineOnMap(Graphics2D graphics, int from, int to)
	{
		int x1 = geometry.mapWorldPointToGraphicsPointX(from);
		int y1 = geometry.mapWorldPointToGraphicsPointY(from);
		int x2 = geometry.mapWorldPointToGraphicsPointX(to);
		int y2 = geometry.mapWorldPointToGraphicsPointY(to);
		if (x1 == Integer.MIN_VALUE || y1 == Integer.MIN_VALUE || x2 == Integer.MIN_VALUE || y2 == Integer.MIN_VALUE)
		{
			return;
		}
		graphics.setStroke(new BasicStroke(2));
		graphics.drawLine(x1, y1, x2, y2);
	}

	private void drawOnMap(Graphics2D graphics, int point, boolean checkHover, Point cursorPos)
	{
		drawOnMap(graphics, point, WorldPointUtil.dxdy(point, 1, -1), checkHover, cursorPos);
	}

	private void drawOnMap(Graphics2D graphics, int point, int offsetPoint, boolean checkHover, Point cursorPos)
	{
		int startX = geometry.mapWorldPointToGraphicsPointX(point);
		int startY = geometry.mapWorldPointToGraphicsPointY(point);
		int endX = geometry.mapWorldPointToGraphicsPointX(offsetPoint);
		int endY = geometry.mapWorldPointToGraphicsPointY(offsetPoint);

		if (startX == Integer.MIN_VALUE || startY == Integer.MIN_VALUE ||
			endX == Integer.MIN_VALUE || endY == Integer.MIN_VALUE)
		{
			return;
		}

		int x = startX;
		int y = startY;
		final int width = endX - x;
		final int height = endY - y;
		x -= width / 2;
		y -= height / 2;

		if (WorldPointUtil.distanceBetween(point, offsetPoint) > 1)
		{
			graphics.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
			graphics.drawLine(startX, startY, endX, endY);
		}
		else
		{
			if (checkHover && cursorPos != null &&
				cursorPos.getX() >= x && cursorPos.getX() <= (endX - width / 2) &&
				cursorPos.getY() >= y && cursorPos.getY() <= (endY - width / 2))
			{
				graphics.setColor(graphics.getColor().darker());
			}
			graphics.fillRect(x, y, width, height);
		}
	}

	private Area getWorldMapClipArea(Rectangle baseRectangle)
	{
		final Widget overview = client.getWidget(InterfaceID.Worldmap.OVERVIEW_CONTAINER);
		final Widget surfaceSelector = client.getWidget(InterfaceID.Worldmap.MAPLIST_BOX_GRAPHIC0);

		Area clipArea = new Area(baseRectangle);

		if (overview != null && !overview.isHidden())
		{
			clipArea.subtract(new Area(overview.getBounds()));
		}

		if (surfaceSelector != null && !surfaceSelector.isHidden())
		{
			clipArea.subtract(new Area(surfaceSelector.getBounds()));
		}

		return clipArea;
	}

	private int getWorldMapExtentWidth(Rectangle baseRectangle)
	{
		return (WorldPointUtil.unpackWorldX(
			geometry.calculateMapPoint(
				baseRectangle.x + baseRectangle.width,
				baseRectangle.y + baseRectangle.height))
			-
			WorldPointUtil.unpackWorldX(
				geometry.calculateMapPoint(
					baseRectangle.x,
					baseRectangle.y)));
	}

	private int getWorldMapExtentHeight(Rectangle baseRectangle)
	{
		return (WorldPointUtil.unpackWorldY(
			geometry.calculateMapPoint(
				baseRectangle.x,
				baseRectangle.y))
			-
			WorldPointUtil.unpackWorldY(
				geometry.calculateMapPoint(
					baseRectangle.x + baseRectangle.width,
					baseRectangle.y + baseRectangle.height)));
	}
}
