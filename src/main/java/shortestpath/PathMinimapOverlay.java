package shortestpath;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
        if (!plugin.drawMinimap || plugin.getPathfinder() == null) {
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setClip(plugin.getMinimapClipArea());

        List<Integer> pathPoints = plugin.getPathfinder().getPath();
        Color pathColor = plugin.getPathfinder().isDone() ? plugin.colourPath : plugin.colourPathCalculating;
        for (int pathPoint : pathPoints) {
            if (WorldPointUtil.unpackWorldPlane(pathPoint) != client.getPlane()) {
                continue;
            }

            drawOnMinimap(graphics, pathPoint, pathColor);
        }
        for (int target : plugin.getPathfinder().getTargets()) {
            if (target != pathPoints.get(pathPoints.size() - 1)) {
                drawOnMinimap(graphics, target, plugin.colourPathCalculating);
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
