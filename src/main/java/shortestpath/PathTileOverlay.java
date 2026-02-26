package shortestpath;

import com.google.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class PathTileOverlay extends Overlay {
    private final Client client;
    private final ShortestPathPlugin plugin;
    private static final int TRANSPORT_LABEL_GAP = 3;

    @Inject
    public PathTileOverlay(Client client, ShortestPathPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    private static final Color COLOR_AVAILABLE = Color.WHITE;
    private static final Color COLOR_UNAVAILABLE = Color.ORANGE;

    private void renderTransports(Graphics2D graphics) {
        Map<Integer, Set<Transport>> allTransports = plugin.getAllTransports();
        Map<Integer, Set<Transport>> availableTransports = plugin.getTransports();

        for (int a : allTransports.keySet()) {
            if (a == Transport.UNDEFINED_ORIGIN) {
                continue; // skip teleports
            }

            Point ca = tileCenter(a);
            if (ca == null) {
                continue;
            }

            boolean drawStart = false;
            StringBuilder s = new StringBuilder();
            Set<Transport> availableAtOrigin = availableTransports.getOrDefault(a, new HashSet<>());

            for (Transport b : allTransports.getOrDefault(a, new HashSet<>())) {
                if (b == null || TransportType.isTeleport(b.getType())) {
                    continue; // skip teleports
                }

                boolean isAvailable = availableAtOrigin.contains(b);
                graphics.setColor(isAvailable ? COLOR_AVAILABLE : COLOR_UNAVAILABLE);

                PrimitiveIntList destinations = WorldPointUtil.toLocalInstance(client, b.getDestination());
                for (int i = 0; i < destinations.size(); i++) {
                    int destination = destinations.get(i);
                    if (destination == Transport.UNDEFINED_DESTINATION) {
                        continue;
                    }
                    Point cb = tileCenter(destination);
                    if (cb != null) {
                        graphics.drawLine(ca.getX(), ca.getY(), cb.getX(), cb.getY());
                        drawStart = true;
                    }
                    if (WorldPointUtil.unpackWorldPlane(destination) > WorldPointUtil.unpackWorldPlane(a)) {
                        s.append("+");
                    } else if (WorldPointUtil.unpackWorldPlane(destination) < WorldPointUtil.unpackWorldPlane(a)) {
                        s.append("-");
                    } else {
                        s.append("=");
                    }
                }
            }

            if (drawStart) {
                drawTile(graphics, a, plugin.colourTransports, -1, true);
            }

            graphics.setColor(Color.WHITE);
            graphics.drawString(s.toString(), ca.getX(), ca.getY());
        }
    }

    private void renderCollisionMap(Graphics2D graphics) {
        CollisionMap map = plugin.getMap();
        for (Tile[] row : client.getScene().getTiles()[client.getPlane()]) {
            for (Tile tile : row) {
                if (tile == null) {
                    continue;
                }

                Polygon tilePolygon = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());

                if (tilePolygon == null) {
                    continue;
                }

                int location = WorldPointUtil.fromLocalInstance(client, tile.getLocalLocation());
                int x = WorldPointUtil.unpackWorldX(location);
                int y = WorldPointUtil.unpackWorldY(location);
                int z = WorldPointUtil.unpackWorldPlane(location);

                String s = (!map.n(x, y, z) ? "n" : "") +
                        (!map.s(x, y, z) ? "s" : "") +
                        (!map.e(x, y, z) ? "e" : "") +
                        (!map.w(x, y, z) ? "w" : "");

                if (map.isBlocked(x, y, z)) {
                    graphics.setColor(plugin.colourCollisionMap);
                    graphics.fill(tilePolygon);
                }
                if (!s.isEmpty() && !s.equals("nsew")) {
                    graphics.setColor(Color.WHITE);
                    int stringX = (int) (tilePolygon.getBounds().getCenterX() - graphics.getFontMetrics().getStringBounds(s, graphics).getWidth() / 2);
                    int stringY = (int) tilePolygon.getBounds().getCenterY();
                    graphics.drawString(s, stringX, stringY);
                }
            }
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.drawTransports) {
            renderTransports(graphics);
        }

        if (plugin.drawCollisionMap) {
            renderCollisionMap(graphics);
        }

        if (plugin.drawTiles && plugin.getPathfinder() != null && plugin.getPathfinder().getPath() != null) {
            Color colorCalculating = new Color(
                plugin.colourPathCalculating.getRed(),
                plugin.colourPathCalculating.getGreen(),
                plugin.colourPathCalculating.getBlue(),
                plugin.colourPathCalculating.getAlpha() / 2);
            Color color = plugin.getPathfinder().isDone()
                ? new Color(
                    plugin.colourPath.getRed(),
                    plugin.colourPath.getGreen(),
                    plugin.colourPath.getBlue(),
                    plugin.colourPath.getAlpha() / 2)
                : colorCalculating;

            PrimitiveIntList path = plugin.getPathfinder().getPath();
            int counter = 0;
            if (TileStyle.LINES.equals(plugin.pathStyle)) {
                for (int i = 1; i < path.size(); i++) {
                    drawLine(graphics, path.get(i - 1), path.get(i), color, 1 + counter++);
                    drawTransportInfo(graphics, path.get(i - 1), path.get(i), path, i - 1);
                }
            } else {
                boolean showTiles = TileStyle.TILES.equals(plugin.pathStyle);
                for (int i = 0; i < path.size(); i++) {
                    // Skip drawing tiles inside POH (no collision data, tiles render at wrong positions)
                    int pathX = WorldPointUtil.unpackWorldX(path.get(i));
                    int pathY = WorldPointUtil.unpackWorldY(path.get(i));
                    if (!ShortestPathPlugin.isInsidePoh(pathX, pathY)) {
                        drawTile(graphics, path.get(i), color, counter, showTiles);
                    }
                    counter++;
                    drawTransportInfo(graphics, path.get(i), (i + 1 == path.size()) ? WorldPointUtil.UNDEFINED : path.get(i + 1), path, i);
                }
                for (int target : plugin.getPathfinder().getTargets()) {
                    if (path.size() > 0 && target != path.get(path.size() - 1)) {
                        drawTile(graphics, target, colorCalculating, -1, showTiles);
                    }
                }
            }
        }

        return null;
    }

    private Point tileCenter(int b) {
        if (b == WorldPointUtil.UNDEFINED || client == null) {
            return null;
        }

        if (WorldPointUtil.unpackWorldPlane(b) != client.getPlane()) {
            return null;
        }

        LocalPoint lp = WorldPointUtil.toLocalPoint(client, b);
        if (lp == null) {
            return null;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return null;
        }

        int cx = poly.getBounds().x + poly.getBounds().width / 2;
        int cy = poly.getBounds().y + poly.getBounds().height / 2;
        return new Point(cx, cy);
    }

    private void drawTile(Graphics2D graphics, int location, Color color, int counter, boolean draw) {
        if (client == null) {
            return;
        }

        PrimitiveIntList points = WorldPointUtil.toLocalInstance(client, location);
        for (int i = 0; i < points.size(); i++) {
            int point = points.get(i);
            if (point == WorldPointUtil.UNDEFINED) {
                continue;
            }

            LocalPoint lp = WorldPointUtil.toLocalPoint(client, point);
            if (lp == null) {
                continue;
            }

            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly == null) {
                continue;
            }

            if (draw) {
                graphics.setColor(color);
                graphics.fill(poly);
            }

            drawCounter(graphics, poly.getBounds().getCenterX(), poly.getBounds().getCenterY(), counter);
        }
    }

    private void drawLine(Graphics2D graphics, int startLoc, int endLoc, Color color, int counter) {
        PrimitiveIntList starts = WorldPointUtil.toLocalInstance(client, startLoc);
        PrimitiveIntList ends = WorldPointUtil.toLocalInstance(client, endLoc);

        if (starts.isEmpty() || ends.isEmpty()) {
            return;
        }

        int start = starts.get(0);
        int end = ends.get(0);

        final int z = client.getPlane();
        if (WorldPointUtil.unpackWorldPlane(start) != z) {
            return;
        }

        LocalPoint lpStart = WorldPointUtil.toLocalPoint(client, start);
        LocalPoint lpEnd = WorldPointUtil.toLocalPoint(client, end);

        if (lpStart == null || lpEnd == null) {
            return;
        }

        final int startHeight = Perspective.getTileHeight(client, lpStart, z);
        final int endHeight = Perspective.getTileHeight(client, lpEnd, z);

        Point p1 = Perspective.localToCanvas(client, lpStart.getX(), lpStart.getY(), startHeight);
        Point p2 = Perspective.localToCanvas(client, lpEnd.getX(), lpEnd.getY(), endHeight);

        if (p1 == null || p2 == null) {
            return;
        }

        Line2D.Double line = new Line2D.Double(p1.getX(), p1.getY(), p2.getX(), p2.getY());

        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(4));
        graphics.draw(line);

        if (counter == 1) {
            drawCounter(graphics, p1.getX(), p1.getY(), 0);
        }
        drawCounter(graphics, p2.getX(), p2.getY(), counter);
    }

    private void drawCounter(Graphics2D graphics, double x, double y, int counter) {
        if (counter >= 0 && !TileCounter.DISABLED.equals(plugin.showTileCounter)) {
            int n = plugin.tileCounterStep > 0 ? plugin.tileCounterStep : 1;
            int s = plugin.getPathfinder().getPath().size();
            if ((counter % n != 0) && (s != (counter + 1))) {
                return;
            }
            if (TileCounter.REMAINING.equals(plugin.showTileCounter)) {
                counter = s - counter - 1;
            }
            if (n > 1 && counter == 0) {
                return;
            }
            String counterText = Integer.toString(counter);
            graphics.setColor(plugin.colourText);
            graphics.drawString(
                counterText,
                (int) (x - graphics.getFontMetrics().getStringBounds(counterText, graphics).getWidth() / 2), (int) y);
        }
    }

    private void drawTransportInfo(Graphics2D graphics, int location, int locationEnd, PrimitiveIntList path, int pathIndex) {
        if (locationEnd == WorldPointUtil.UNDEFINED || !plugin.showTransportInfo ||
            WorldPointUtil.unpackWorldPlane(location) != client.getPlane()) {
            return;
        }

        // Workaround for weird pathing inside PoH to instead show info on the player tile
        LocalPoint playerLocalPoint = client.getLocalPlayer().getLocalLocation();
        int playerPackedPoint = WorldPointUtil.fromLocalInstance(client, playerLocalPoint);
        int px = WorldPointUtil.unpackWorldX(playerPackedPoint);
        int py = WorldPointUtil.unpackWorldY(playerPackedPoint);
        int tx = WorldPointUtil.unpackWorldX(location);
        int ty = WorldPointUtil.unpackWorldY(location);
        boolean transportAndPlayerInsidePoh = ShortestPathPlugin.isInsidePoh(tx, ty) && ShortestPathPlugin.isInsidePoh(px, py);

        // When inside POH, only show the POH exit info once (not per-transport)
        if (transportAndPlayerInsidePoh) {
            String pohExitInfo = plugin.getPohExitInfo(locationEnd, path, pathIndex);
            if (pohExitInfo == null) {
                return;
            }

            // Find the display name of the teleport that brought us to POH
            String text = null;
            for (Transport transport : plugin.getTransports().getOrDefault(location, new HashSet<>())) {
                if (locationEnd != transport.getDestination()) {
                    continue;
                }
                text = transport.getDisplayInfo();
                if (text != null && !text.isEmpty()) {
                    break;
                }
            }
            if (text == null || text.isEmpty()) {
                return;
            }
            text = text + " (Exit: " + pohExitInfo + ")";

            Point p = Perspective.localToCanvas(client, playerLocalPoint, client.getPlane());
            if (p == null) {
                return;
            }

            Rectangle2D textBounds = graphics.getFontMetrics().getStringBounds(text, graphics);
            double height = textBounds.getHeight();
            int x = (int) (p.getX() - textBounds.getWidth() / 2);
            int y = (int) (p.getY() - height);
            graphics.setColor(Color.BLACK);
            graphics.drawString(text, x + 1, y + 1);
            graphics.setColor(plugin.colourText);
            graphics.drawString(text, x, y);
            return;
        }

        int vertical_offset = 0;
        for (Transport transport : plugin.getTransports().getOrDefault(location, new HashSet<>())) {
            if (locationEnd != transport.getDestination()) {
                continue;
            }

            String text = transport.getDisplayInfo();
            if (text == null || text.isEmpty()) {
                continue;
            }

            // Check if this transport goes to POH - if so, look ahead to find the exit transport
            String pohExitInfo = plugin.getPohExitInfo(locationEnd, path, pathIndex);
            if (pohExitInfo != null) {
                text = text + " (Exit: " + pohExitInfo + ")";
            }

            PrimitiveIntList points = WorldPointUtil.toLocalInstance(client, location);
            for (int i = 0; i < points.size(); i++) {
                LocalPoint lp = WorldPointUtil.toLocalPoint(client, points.get(i));
                if (lp == null) {
                    continue;
                }

                Point p = Perspective.localToCanvas(client, lp, client.getPlane());
                if (p == null) {
                    continue;
                }

                Rectangle2D textBounds = graphics.getFontMetrics().getStringBounds(text, graphics);
                double height = textBounds.getHeight();
                int x = (int) (p.getX() - textBounds.getWidth() / 2);
                int y = (int) (p.getY() - height) - vertical_offset;
                graphics.setColor(Color.BLACK);
                graphics.drawString(text, x + 1, y + 1);
                graphics.setColor(plugin.colourText);
                graphics.drawString(text, x, y);

                vertical_offset += (int) height + TRANSPORT_LABEL_GAP;
            }
        }
    }
}
