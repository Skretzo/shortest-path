package shortestpath;

import com.google.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.HashSet;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class PathMapOverlay extends Overlay {
    private final Client client;
    private final ShortestPathPlugin plugin;

    @Inject
    private PathMapOverlay(Client client, ShortestPathPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.MANUAL);
        drawAfterLayer(ComponentID.WORLD_MAP_MAPVIEW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.drawMap) {
            return null;
        }

        if (client.getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            return null;
        }

        Rectangle worldMapRectangle = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW).getBounds();
        Area worldMapClipArea = getWorldMapClipArea(worldMapRectangle);
        graphics.setClip(worldMapClipArea);

        if (plugin.drawCollisionMap) {
            graphics.setColor(plugin.colourCollisionMap);
            int mapWorldPoint = plugin.calculateMapPoint(worldMapRectangle.x, worldMapRectangle.y);
            int extentX = WorldPointUtil.unpackWorldX(mapWorldPoint);
            int extentY = WorldPointUtil.unpackWorldY(mapWorldPoint);
            int extentWidth = getWorldMapExtentWidth(worldMapRectangle);
            int extentHeight = getWorldMapExtentHeight(worldMapRectangle);
            final CollisionMap map = plugin.getMap();
            final int z = client.getPlane();
            for (int x = extentX; x < (extentX + extentWidth + 1); x++) {
                for (int y = extentY - extentHeight; y < (extentY + 1); y++) {
                    if (map.isBlocked(x, y, z)) {
                        drawOnMap(graphics, WorldPointUtil.packWorldPoint(x, y, z), false, null);
                    }
                }
            }
        }

        if (plugin.drawTransports) {
            graphics.setColor(Color.WHITE);
            for (int a : plugin.getTransports().keySet()) {
                if (a == Transport.UNDEFINED_ORIGIN) {
                    continue; // skip teleports
                }

                int mapAX = plugin.mapWorldPointToGraphicsPointX(a);
                int mapAY = plugin.mapWorldPointToGraphicsPointY(a);
                if (!worldMapClipArea.contains(mapAX, mapAY)) {
                    continue;
                }

                for (Transport b : plugin.getTransports().getOrDefault(a, new HashSet<>())) {
                    if (b == null || TransportType.isTeleport(b.getType())) {
                        continue; // skip teleports
                    }

                    int mapBX = plugin.mapWorldPointToGraphicsPointX(b.getDestination());
                    int mapBY = plugin.mapWorldPointToGraphicsPointY(b.getDestination());
                    if (!worldMapClipArea.contains(mapBX, mapBY)) {
                        continue;
                    }

                    graphics.drawLine(mapAX, mapAY, mapBX, mapBY);
                }
            }
        }

        if (plugin.getPathfinder() != null) {
            Color colour = plugin.getPathfinder().isDone() ? plugin.colourPath : plugin.colourPathCalculating;
            PrimitiveIntList path = plugin.getPathfinder().getPath();
            Point cursorPos = client.getMouseCanvasPosition();
            for (int i = 0; i < path.size(); i++) {
                graphics.setColor(colour);
                int point = path.get(i);
                int lastPoint = (i > 0) ? path.get(i - 1) : point;
                if (WorldPointUtil.distanceBetween(point, lastPoint) > 1) {
                    drawOnMap(graphics, lastPoint, point, true, cursorPos);
                }
                drawOnMap(graphics, point, true, cursorPos);
            }
            for (int target : plugin.getPathfinder().getTargets()) {
                if (path.size() > 0 && target != path.get(path.size() - 1)) {
                    graphics.setColor(plugin.colourPathCalculating);
                    drawOnMap(graphics, target, true, cursorPos);
                }
            }
        }

        return null;
    }

    private void drawOnMap(Graphics2D graphics, int point, boolean checkHover, Point cursorPos) {
        drawOnMap(graphics, point, WorldPointUtil.dxdy(point, 1, -1), checkHover, cursorPos);
    }

    private void drawOnMap(Graphics2D graphics, int point, int offsetPoint, boolean checkHover, Point cursorPos) {
        int startX = plugin.mapWorldPointToGraphicsPointX(point);
        int startY = plugin.mapWorldPointToGraphicsPointY(point);
        int endX = plugin.mapWorldPointToGraphicsPointX(offsetPoint);
        int endY = plugin.mapWorldPointToGraphicsPointY(offsetPoint);

        if (startX == Integer.MIN_VALUE || startY == Integer.MIN_VALUE ||
            endX == Integer.MIN_VALUE || endY == Integer.MIN_VALUE) {
            return;
        }

        int x = startX;
        int y = startY;
        final int width = endX - x;
        final int height = endY - y;
        x -= width / 2;
        y -= height / 2;

        if (WorldPointUtil.distanceBetween(point, offsetPoint) > 1) {
            graphics.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
            graphics.drawLine(startX, startY, endX, endY);
        } else {
            if (checkHover && cursorPos != null &&
                cursorPos.getX() >= x && cursorPos.getX() <= (endX - width / 2) &&
                cursorPos.getY() >= y && cursorPos.getY() <= (endY - width / 2)) {
                graphics.setColor(graphics.getColor().darker());
            }
            graphics.fillRect(x, y, width, height);
        }
    }

    private Area getWorldMapClipArea(Rectangle baseRectangle) {
        final Widget overview = client.getWidget(ComponentID.WORLD_MAP_OVERVIEW_MAP);
        final Widget surfaceSelector = client.getWidget(ComponentID.WORLD_MAP_SURFACE_SELECTOR);

        Area clipArea = new Area(baseRectangle);

        if (overview != null && !overview.isHidden()) {
            clipArea.subtract(new Area(overview.getBounds()));
        }

        if (surfaceSelector != null && !surfaceSelector.isHidden()) {
            clipArea.subtract(new Area(surfaceSelector.getBounds()));
        }

        return clipArea;
    }

    private int getWorldMapExtentWidth(Rectangle baseRectangle) {
        return (
            WorldPointUtil.unpackWorldX(
                plugin.calculateMapPoint(
                    baseRectangle.x + baseRectangle.width,
                    baseRectangle.y + baseRectangle.height)) -
            WorldPointUtil.unpackWorldX(
                plugin.calculateMapPoint(
                    baseRectangle.x,
                    baseRectangle.y)));
    }

    private int getWorldMapExtentHeight(Rectangle baseRectangle) {
        return (
            WorldPointUtil.unpackWorldY(
                plugin.calculateMapPoint(
                    baseRectangle.x,
                    baseRectangle.y)) -
            WorldPointUtil.unpackWorldY(
                plugin.calculateMapPoint(
                    baseRectangle.x + baseRectangle.width,
                    baseRectangle.y + baseRectangle.height)));
    }
}
