package shortestpath.widgets;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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
 * world-map open guard and drawable clip area the map overlays draw
 * through, the minimap clip area the overlays and the menu code draw
 * through, and the last-opened menu position the selection read resolves
 * against. The clip
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
	// Underground world maps draw some areas away from their real tiles, and some
	// planes side by side. Per map name, from the game cache (the tooling's
	// WorldMapDumper): {x, y, width, height, dx, dy, plane} for each drawn area
	// that is not its own tiles on plane 0; the real tile is the spot + (dx, dy).
	private static final Map<String, List<int[]>> MAP_AREAS = new HashMap<>();

	static
	{
		try (Scanner lines = new Scanner(WidgetGeometry.class.getResourceAsStream("/world-map-areas.tsv"), "UTF-8"))
		{
			while (lines.hasNextLine())
			{
				String[] c = lines.nextLine().split("\t");
				if (!c[0].startsWith("#"))
				{
					MAP_AREAS.computeIfAbsent(c[0], k -> new ArrayList<>())
						.add(Arrays.stream(c, 1, 8).mapToInt(Integer::parseInt).toArray());
				}
			}
		}
	}

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
			int mapPoint = client.isMenuOpen() && menuPoint != null
				? calculateMapPoint(menuPoint.getX(), menuPoint.getY())
				: calculateMapPoint(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());
			return mapPoint == WorldPointUtil.UNDEFINED ? mapPoint : realMapPoint(mapPoint);
		}
		return WorldPointUtil.UNDEFINED;
	}

	// The real tile drawn at a spot of the open world map, which the map list
	// button names in one of its texts
	private int realMapPoint(int mapPoint)
	{
		int x = WorldPointUtil.unpackWorldX(mapPoint);
		int y = WorldPointUtil.unpackWorldY(mapPoint);
		Widget mapList = client.getWidget(InterfaceID.Worldmap.MAPLIST_DISPLAY);
		Widget[] texts = mapList == null ? null : mapList.getDynamicChildren();
		for (Widget text : texts == null ? new Widget[0] : texts)
		{
			for (int[] a : MAP_AREAS.getOrDefault(text == null ? null : text.getText(), List.of()))
			{
				if (x >= a[0] && y >= a[1] && x < a[0] + a[2] && y < a[1] + a[3])
				{
					return WorldPointUtil.packWorldPoint(x + a[4], y + a[5], a[6]);
				}
			}
		}
		return mapPoint;
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

	/**
	 * The world-map gate shared by the map overlays: the map is open — and
	 * its projections and clip are meaningful — exactly while the
	 * MAP_CONTAINER widget exists. A detached instance answers false.
	 */
	public boolean worldMapOpen()
	{
		return client != null && client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) != null;
	}

	/**
	 * Both axes of the world-map projection in one read: {@code null} when
	 * either axis collapses to {@code Integer.MIN_VALUE} (no client, or the
	 * world map is closed), so paired consumers check a single {@code null}
	 * instead of two sentinels.
	 */
	public Point mapWorldPointToGraphicsPoint(int packedWorldPoint)
	{
		int x = mapWorldPointToGraphicsPointX(packedWorldPoint);
		int y = mapWorldPointToGraphicsPointY(packedWorldPoint);
		if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE)
		{
			return null;
		}
		return new Point(x, y);
	}

	/**
	 * The drawable area of the world map: {@code baseRectangle} minus the
	 * overview panel and surface selector while they are open. The overlay
	 * still owns the {@code setClip} call — it deliberately never restores
	 * the clip. A detached instance yields the base rectangle's area.
	 */
	public Area getWorldMapClipArea(Rectangle baseRectangle)
	{
		if (client == null)
		{
			return new Area(baseRectangle);
		}
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
