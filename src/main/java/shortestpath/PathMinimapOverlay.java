package shortestpath;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class PathMinimapOverlay extends Overlay {
    private final Client client;
    private final ShortestPathPlugin plugin;

    @Inject
    private PathMinimapOverlay(Client client, ShortestPathPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.drawMinimap) {
            return null;
        }

        Shape minimapClipArea = plugin.getMinimapClipArea();
        if (minimapClipArea == null) {
            return null;
        } else {
            graphics.setClip(plugin.getMinimapClipArea());
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        
        if (plugin.showBothPaths && plugin.getWalkingPathfinder() != null && plugin.getWalkingPathfinder().getPath() != null) {
            List<Integer> walkingPathPoints = plugin.getWalkingPathfinder().getPath();
            Color walkingPathColor = plugin.getWalkingPathfinder().isDone() 
                ? new Color(plugin.colourWalkingPath.getRed(), plugin.colourWalkingPath.getGreen(), 
                           plugin.colourWalkingPath.getBlue(), plugin.colourWalkingPath.getAlpha() / 2)
                : new Color(plugin.colourPathCalculating.getRed(), plugin.colourPathCalculating.getGreen(),
                           plugin.colourPathCalculating.getBlue(), plugin.colourPathCalculating.getAlpha() / 2);
            
            for (int pathPoint : walkingPathPoints) {
                if (WorldPointUtil.unpackWorldPlane(pathPoint) != client.getPlane()) {
                    continue;
                }
                drawOnMinimap(graphics, pathPoint, walkingPathColor);
            }
        }

        // Draw main path
        if (plugin.getPathfinder() != null && plugin.getPathfinder().getPath() != null) {
            List<Integer> pathPoints = plugin.getPathfinder().getPath();
            Color pathColor = plugin.getPathfinder().isDone() ? plugin.colourPath : plugin.colourPathCalculating;
            for (int pathPoint : pathPoints) {
                if (WorldPointUtil.unpackWorldPlane(pathPoint) != client.getPlane()) {
                    continue;
                }

                drawOnMinimap(graphics, pathPoint, pathColor);
            }
            for (int target : plugin.getPathfinder().getTargets()) {
                if (pathPoints.size() > 0 && target != pathPoints.get(pathPoints.size() - 1)) {
                    drawOnMinimap(graphics, target, plugin.colourPathCalculating);
                }
            }
        }

        return null;
    }

    private void drawOnMinimap(Graphics2D graphics, int location, Color color) {
        for (int point : WorldPointUtil.toLocalInstance(client, location)) {
            LocalPoint lp = WorldPointUtil.toLocalPoint(client, point);

            if (lp == null) {
                continue;
            }

            Point posOnMinimap = Perspective.localToMinimap(client, lp);

            if (posOnMinimap == null) {
                continue;
            }

            renderMinimapRect(client, graphics, posOnMinimap, color);
        }
    }

    public static void renderMinimapRect(Client client, Graphics2D graphics, Point center, Color color) {
        double angle = client.getCameraYawTarget() * Perspective.UNIT;
        double tileSize = client.getMinimapZoom();
        int x = (int) Math.round(center.getX() - tileSize / 2);
        int y = (int) Math.round(center.getY() - tileSize / 2);
        int width = (int) Math.round(tileSize);
        int height = (int) Math.round(tileSize);
        graphics.setColor(color);
        graphics.rotate(angle, center.getX(), center.getY());
        graphics.fillRect(x, y, width, height);
        graphics.rotate(-angle, center.getX(), center.getY());
    }
}
