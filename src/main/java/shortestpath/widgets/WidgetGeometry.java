package shortestpath.widgets;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.RequiredArgsConstructor;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.game.SpriteManager;

import shortestpath.WorldPointUtil;

/**
 * Owns the plugin's widget and viewport geometry: the world-map point
 * conversions that turn canvas coordinates into packed world points, the
 * minimap clip area the overlays and the menu code draw through, and the
 * last-opened menu position the selection read resolves against. The clip
 * area and the two mask sprites it is built from are cached and invalidated
 * together whenever the minimap widget's bounds change — the self-assigning
 * bounds check is the single invalidation point.
 * <p>
 * This package is a leaf: nothing here may reference the plugin type.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WidgetGeometry
{
	private final Client client;
	private final SpriteManager spriteManager;

	private Point lastMenuOpenedPoint;
	private Shape minimapClipFixed;
	private Shape minimapClipResizeable;
	private BufferedImage minimapSpriteFixed;
	private BufferedImage minimapSpriteResizeable;
	private Rectangle minimapRectangle = new Rectangle();

	private WidgetGeometry()
	{
		this(null, null);
	}

	/**
	 * Detached-sprite seam for tests that exercise only the map reads —
	 * the clip arms need a sprite manager, so callers probing the minimap
	 * caches wire the full constructor or
	 * {@link #forTesting(Client, SpriteManager)}.
	 */
	WidgetGeometry(Client client)
	{
		this(client, null);
	}

	/**
	 * Test/harness seam: returns an instance detached from Guice. The
	 * production instance is the injected singleton. Without a client the
	 * detached instance collapses to the no-op arms — null clip areas and
	 * the {@code Integer.MIN_VALUE}/{@link WorldPointUtil#UNDEFINED}
	 * sentinels — and never throws.
	 */
	public static WidgetGeometry forTesting()
	{
		return new WidgetGeometry();
	}

	/**
	 * Test seam for callers that exercise the widget-backed reads: the
	 * handed client and sprite manager drive the minimap and world-map
	 * lookups while the instance stays detached from Guice.
	 */
	public static WidgetGeometry forTesting(Client client, SpriteManager spriteManager)
	{
		return new WidgetGeometry(client, spriteManager);
	}

	/**
	 * Records where the canvas sat when a menu opened — the single write
	 * path for the point {@link #getSelectedWorldPoint()} resolves against.
	 * The shell's {@code MenuOpened} forwarder supplies the mouse canvas
	 * position.
	 */
	public void menuOpened(Point menuPoint)
	{
		lastMenuOpenedPoint = menuPoint;
	}

	public int getSelectedWorldPoint()
	{
		if (client == null)
		{
			return WorldPointUtil.UNDEFINED;
		}
		if (client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null)
		{
			if (client.getTopLevelWorldView().getSelectedSceneTile() != null)
			{
				return WorldPointUtil.fromLocalInstance(client, client.getTopLevelWorldView().getSelectedSceneTile().getLocalLocation());
			}
		}
		else
		{
			Point menuPoint = lastMenuOpenedPoint;
			return client.isMenuOpen() && menuPoint != null
				? calculateMapPoint(menuPoint.getX(), menuPoint.getY())
				: calculateMapPoint(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());
		}
		return WorldPointUtil.UNDEFINED;
	}

	public int calculateMapPoint(int pointX, int pointY)
	{
		if (client == null)
		{
			return WorldPointUtil.UNDEFINED;
		}
		WorldMap worldMap = client.getWorldMap();
		float zoom = worldMap.getWorldMapZoom();
		int mapPoint = WorldPointUtil.packWorldPoint(worldMap.getWorldMapPosition().getX(), worldMap.getWorldMapPosition().getY(), 0);
		int middleX = mapWorldPointToGraphicsPointX(mapPoint);
		int middleY = mapWorldPointToGraphicsPointY(mapPoint);

		if (pointX == Integer.MIN_VALUE || pointY == Integer.MIN_VALUE ||
			middleX == Integer.MIN_VALUE || middleY == Integer.MIN_VALUE)
		{
			return WorldPointUtil.UNDEFINED;
		}

		final int dx = (int) ((pointX - middleX) / zoom);
		final int dy = (int) ((-(pointY - middleY)) / zoom);

		return WorldPointUtil.dxdy(mapPoint, dx, dy);
	}

	public int mapWorldPointToGraphicsPointX(int packedWorldPoint)
	{
		if (client == null)
		{
			return Integer.MIN_VALUE;
		}
		WorldMap worldMap = client.getWorldMap();

		float pixelsPerTile = worldMap.getWorldMapZoom();

		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map != null)
		{
			Rectangle worldMapRect = map.getBounds();

			int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);

			Point worldMapPosition = worldMap.getWorldMapPosition();

			int xTileOffset = WorldPointUtil.unpackWorldX(packedWorldPoint) + widthInTiles / 2 - worldMapPosition.getX();

			int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
			xGraphDiff += (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
			xGraphDiff += (int) worldMapRect.getX();

			return xGraphDiff;
		}
		return Integer.MIN_VALUE;
	}

	public int mapWorldPointToGraphicsPointY(int packedWorldPoint)
	{
		if (client == null)
		{
			return Integer.MIN_VALUE;
		}
		WorldMap worldMap = client.getWorldMap();

		float pixelsPerTile = worldMap.getWorldMapZoom();

		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map != null)
		{
			Rectangle worldMapRect = map.getBounds();

			int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

			Point worldMapPosition = worldMap.getWorldMapPosition();

			int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
			int yTileOffset = (yTileMax - WorldPointUtil.unpackWorldY(packedWorldPoint) - 1) * -1;

			int yGraphDiff = (int) (yTileOffset * pixelsPerTile);
			yGraphDiff -= (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
			yGraphDiff = worldMapRect.height - yGraphDiff;
			yGraphDiff += (int) worldMapRect.getY();

			return yGraphDiff;
		}
		return Integer.MIN_VALUE;
	}

	private Widget getMinimapDrawWidget()
	{
		if (client == null)
		{
			return null;
		}
		if (client.isResized())
		{
			if (client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1)
			{
				return client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
			}
			return client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
		}
		return client.getWidget(InterfaceID.Toplevel.MINIMAP);
	}

	private Shape getMinimapClipAreaSimple()
	{
		Widget minimapDrawArea = getMinimapDrawWidget();

		if (minimapDrawArea == null || minimapDrawArea.isHidden())
		{
			return null;
		}

		Rectangle bounds = minimapDrawArea.getBounds();

		return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
	}

	public Shape getMinimapClipArea()
	{
		Widget minimapWidget = getMinimapDrawWidget();

		if (minimapWidget == null || minimapWidget.isHidden() || !minimapRectangle.equals(minimapRectangle = minimapWidget.getBounds()))
		{
			minimapClipFixed = null;
			minimapClipResizeable = null;
			minimapSpriteFixed = null;
			minimapSpriteResizeable = null;
		}

		if (minimapWidget == null || minimapWidget.isHidden())
		{
			return null;
		}

		if (client.isResized())
		{
			if (minimapClipResizeable != null)
			{
				return minimapClipResizeable;
			}
			if (minimapSpriteResizeable == null)
			{
				minimapSpriteResizeable = spriteManager.getSprite(SpriteID.RESIZE_MAP_MASK, 0);
			}
			if (minimapSpriteResizeable != null)
			{
				minimapClipResizeable = bufferedImageToPolygon(minimapSpriteResizeable);
				return minimapClipResizeable;
			}
			return getMinimapClipAreaSimple();
		}
		if (minimapClipFixed != null)
		{
			return minimapClipFixed;
		}
		if (minimapSpriteFixed == null)
		{
			minimapSpriteFixed = spriteManager.getSprite(SpriteID.FIXED_MAP_MASK, 0);
		}
		if (minimapSpriteFixed != null)
		{
			minimapClipFixed = bufferedImageToPolygon(minimapSpriteFixed);
			return minimapClipFixed;
		}
		return getMinimapClipAreaSimple();
	}

	private Polygon bufferedImageToPolygon(BufferedImage image)
	{
		Color outsideColour = null;
		Color previousColour;
		final int width = image.getWidth();
		final int height = image.getHeight();
		List<java.awt.Point> points = new ArrayList<>();
		for (int y = 0; y < height; y++)
		{
			previousColour = outsideColour;
			for (int x = 0; x < width; x++)
			{
				int rgb = image.getRGB(x, y);
				int a = (rgb & 0xff000000) >>> 24;
				int r = (rgb & 0x00ff0000) >> 16;
				int g = (rgb & 0x0000ff00) >> 8;
				int b = (rgb & 0x000000ff);
				Color colour = new Color(r, g, b, a);
				if (x == 0 && y == 0)
				{
					outsideColour = colour;
					previousColour = colour;
				}
				if (!colour.equals(outsideColour) && previousColour.equals(outsideColour))
				{
					points.add(new java.awt.Point(x, y));
				}
				if ((colour.equals(outsideColour) || x == (width - 1)) && !previousColour.equals(outsideColour))
				{
					points.add(0, new java.awt.Point(x, y));
				}
				previousColour = colour;
			}
		}
		int offsetX = minimapRectangle.x;
		int offsetY = minimapRectangle.y;
		Polygon polygon = new Polygon();
		for (java.awt.Point point : points)
		{
			polygon.addPoint(point.x + offsetX, point.y + offsetY);
		}
		return polygon;
	}
}
