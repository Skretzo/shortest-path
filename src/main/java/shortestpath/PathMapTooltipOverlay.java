package shortestpath;

import com.google.inject.Inject;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class PathMapTooltipOverlay extends Overlay {
    private static final int TOOLTIP_OFFSET_HEIGHT = 25;
    private static final int TOOLTIP_OFFSET_WIDTH = 15;
    private static final int TOOLTIP_PADDING_HEIGHT = 1;
    private static final int TOOLTIP_PADDING_WIDTH = 2;
    private static final int TOOLTIP_TEXT_OFFSET_HEIGHT = -2;
    
    // POH (Player Owned House) bounds for detecting when path goes through POH
    private static final int POH_MIN_X = 1792;
    private static final int POH_MAX_X = 2047;
    private static final int POH_MIN_Y = 5696;
    private static final int POH_MAX_Y = 5767;

    private final Client client;
    private final ShortestPathPlugin plugin;

    @Inject
    private PathMapTooltipOverlay(Client client, ShortestPathPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_HIGHEST);
        setLayer(OverlayLayer.MANUAL);
        drawAfterInterface(InterfaceID.WORLD_MAP);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.drawMap || client.getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            return null;
        }

        if (plugin.getPathfinder() != null) {
            PrimitiveIntList path = plugin.getPathfinder().getPath();
            Point cursorPos = client.getMouseCanvasPosition();
            for (int i = 0; i < path.size(); i++) {
                int nextPoint = WorldPointUtil.UNDEFINED;
                if (path.size() > i + 1) {
                    nextPoint = path.get(i + 1);
                }
                if (drawTooltip(graphics, cursorPos, path.get(i), nextPoint, i + 1, path, i)) {
                    return null;
                }
            }
            for (int target : plugin.getPathfinder().getTargets()) {
                if (path.size() > 0 && target != path.get(path.size() - 1)) {
                    drawTooltip(graphics, cursorPos, target, WorldPointUtil.UNDEFINED, -1, path, -1);
                }
            }
        }

        return null;
    }

    private boolean drawTooltip(Graphics2D graphics, Point cursorPos, int point, int nextPoint, int n, PrimitiveIntList path, int pathIndex) {
        int offsetPoint = WorldPointUtil.dxdy(point, 1, -1);
        int startX = plugin.mapWorldPointToGraphicsPointX(point);
        int startY = plugin.mapWorldPointToGraphicsPointY(point);
        int endX = plugin.mapWorldPointToGraphicsPointX(offsetPoint);
        int endY = plugin.mapWorldPointToGraphicsPointY(offsetPoint);

        if (startX == Integer.MIN_VALUE || startY == Integer.MIN_VALUE ||
            endX == Integer.MIN_VALUE || endY == Integer.MIN_VALUE) {
            return false;
        }

        int width = endX - startX;

        if (cursorPos.getX() < (startX - width / 2) || cursorPos.getX() > (endX - width / 2) ||
            cursorPos.getY() < (startY - width / 2) || cursorPos.getY() > (endY - width / 2)) {
            return false;
        }

        List<String> rows = new ArrayList<>(Arrays.asList("Shortest path:",
            n < 0 ? "Unused target" : ("Step " + n + " of " + plugin.getPathfinder().getPath().size())));
        if (nextPoint != WorldPointUtil.UNDEFINED) {
            for (Transport transport : plugin.getTransports().getOrDefault(point, new HashSet<>())) {
                if (nextPoint == transport.getDestination()
                    && transport.getDisplayInfo() != null && !transport.getDisplayInfo().isEmpty()) {
                    String displayInfo = transport.getDisplayInfo();
                    // Check if this transport goes to POH - if so, look ahead to find the exit transport
                    String pohExitInfo = getPohExitInfo(nextPoint, path, pathIndex);
                    if (pohExitInfo != null) {
                        displayInfo = displayInfo + " (Exit: " + pohExitInfo + ")";
                    }
                    rows.add(displayInfo);
                    break;
                }
            }
        }

        graphics.setFont(FontManager.getRunescapeFont());
        FontMetrics fm = graphics.getFontMetrics();
        int tooltipHeight = fm.getHeight();
        int tooltipWidth = rows.stream().map(fm::stringWidth).max(Integer::compareTo).get();

        int clippedHeight = tooltipHeight * rows.size() + TOOLTIP_PADDING_HEIGHT * 2;
        int clippedWidth = tooltipWidth + TOOLTIP_PADDING_WIDTH * 2;

        Rectangle worldMapBounds = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW).getBounds();
        int worldMapRightBoundary = worldMapBounds.width + worldMapBounds.x;
        int worldMapBottomBoundary = worldMapBounds.height + worldMapBounds.y;

        int drawPointX = startX + TOOLTIP_OFFSET_WIDTH;
        int drawPointY = startY;
        if (drawPointX + clippedWidth > worldMapRightBoundary) {
            drawPointX = worldMapRightBoundary - clippedWidth;
        }
        if (drawPointY + clippedHeight > worldMapBottomBoundary) {
            drawPointY = startY - clippedHeight;
        }
        drawPointY += TOOLTIP_OFFSET_HEIGHT;

        int tooltipRectX = drawPointX - TOOLTIP_PADDING_WIDTH;
        int tooltipRectY = drawPointY - TOOLTIP_PADDING_HEIGHT;

        graphics.setColor(JagexColors.TOOLTIP_BACKGROUND);
        graphics.fillRect(tooltipRectX, tooltipRectY, clippedWidth, clippedHeight);

        graphics.setColor(JagexColors.TOOLTIP_BORDER);
        graphics.drawRect(tooltipRectX, tooltipRectY, clippedWidth, clippedHeight);

        graphics.setColor(JagexColors.TOOLTIP_TEXT);
        for (int i = 0; i < rows.size(); i++) {
            graphics.drawString(rows.get(i), drawPointX, drawPointY + TOOLTIP_TEXT_OFFSET_HEIGHT + (i + 1) * tooltipHeight);
        }

        return true;
    }

    /**
     * Checks if the destination is inside POH and looks ahead in the path to find the exit transport.
     * If the immediate exit leads to a fairy ring or other notable transport shortly after,
     * that information is included instead.
     * @param destination The destination point to check
     * @param path The full path
     * @param currentIndex The current index in the path
     * @return The display info of the POH exit transport, or null if not applicable
     */
    private String getPohExitInfo(int destination, PrimitiveIntList path, int currentIndex) {
        if (path == null || currentIndex < 0) {
            return null;
        }
        
        int destX = WorldPointUtil.unpackWorldX(destination);
        int destY = WorldPointUtil.unpackWorldY(destination);
        
        // Check if destination is inside POH
        if (destX < POH_MIN_X || destX > POH_MAX_X || destY < POH_MIN_Y || destY > POH_MAX_Y) {
            return null;
        }

        int pohExitIndex = -1;
        String immediateExitInfo = null;

        // Look ahead in the path to find the next transport that exits POH
        for (int i = currentIndex + 1; i < path.size() - 1; i++) {
            int stepLocation = path.get(i);
            int nextLocation = path.get(i + 1);
            
            int stepX = WorldPointUtil.unpackWorldX(stepLocation);
            int stepY = WorldPointUtil.unpackWorldY(stepLocation);
            int nextX = WorldPointUtil.unpackWorldX(nextLocation);
            int nextY = WorldPointUtil.unpackWorldY(nextLocation);
            
            // Check if this step is inside POH but next step is outside (exit transport)
            boolean stepInsidePoh = stepX >= POH_MIN_X && stepX <= POH_MAX_X && stepY >= POH_MIN_Y && stepY <= POH_MAX_Y;
            boolean nextInsidePoh = nextX >= POH_MIN_X && nextX <= POH_MAX_X && nextY >= POH_MIN_Y && nextY <= POH_MAX_Y;
            
            if (stepInsidePoh && !nextInsidePoh) {
                pohExitIndex = i + 1; // Index of the first step outside POH
                // Found the exit transport - get its display info
                for (Transport transport : plugin.getTransports().getOrDefault(stepLocation, new HashSet<>())) {
                    if (nextLocation == transport.getDestination()) {
                        String exitInfo = transport.getDisplayInfo();
                        if (exitInfo != null && !exitInfo.isEmpty()) {
                            TransportType exitType = transport.getType();
                            if (TransportType.TELEPORTATION_BOX.equals(exitType)) {
                                String objInfo = transport.getObjectInfo();
                                if (objInfo != null && objInfo.contains("Xeric's Talisman")) {
                                    immediateExitInfo = "Xeric's Talisman: " + exitInfo;
                                } else if (objInfo != null && objInfo.contains("Digsite")) {
                                    immediateExitInfo = "Digsite Pendant: " + exitInfo;
                                } else {
                                    immediateExitInfo = "Jewelry Box: " + exitInfo;
                                }
                            } else if (TransportType.TELEPORTATION_PORTAL_POH.equals(exitType)) {
                                immediateExitInfo = "Nexus: " + exitInfo.replace(" Portal", "");
                            } else {
                                immediateExitInfo = exitInfo.replace(" Portal", "");
                            }
                        }
                        break;
                    }
                }
                break;
            }
            
            // If we've left POH without finding a transport, stop looking
            if (!stepInsidePoh) {
                break;
            }
        }
        
        // If we found a POH exit, look further ahead to see if there's a fairy ring,
        // spirit tree, or other notable transport used shortly after
        if (pohExitIndex > 0 && pohExitIndex < path.size() - 1) {
            String secondaryTransportInfo = findSecondaryTransport(path, pohExitIndex);
            if (secondaryTransportInfo != null) {
                return secondaryTransportInfo;
            }
        }
        
        return immediateExitInfo;
    }

    /**
     * Looks ahead from the POH exit point to find fairy rings, spirit trees, or other
     * notable transports that might be the real destination reason for going through POH.
     * @param path The full path
     * @param startIndex The index to start looking from (first step outside POH)
     * @return The display info of a secondary transport if found within a reasonable distance, or null
     */
    private String findSecondaryTransport(PrimitiveIntList path, int startIndex) {
        // Look up to 30 steps ahead (reasonable walking distance to a nearby transport)
        int maxLookahead = Math.min(startIndex + 30, path.size() - 1);
        
        for (int i = startIndex; i < maxLookahead; i++) {
            int stepLocation = path.get(i);
            int nextLocation = path.get(i + 1);
            
            for (Transport transport : plugin.getTransports().getOrDefault(stepLocation, new HashSet<>())) {
                if (nextLocation == transport.getDestination()) {
                    TransportType type = transport.getType();
                    // Check for notable transport types that are likely the real reason for the route
                    if (TransportType.FAIRY_RING.equals(type)) {
                        String code = transport.getDisplayInfo();
                        if (code != null && !code.isEmpty()) {
                            return "Fairy Ring " + code;
                        }
                    } else if (TransportType.SPIRIT_TREE.equals(type)) {
                        String dest = transport.getDisplayInfo();
                        if (dest != null && !dest.isEmpty()) {
                            return "Spirit Tree: " + dest;
                        }
                    } else if (TransportType.GNOME_GLIDER.equals(type)) {
                        String dest = transport.getDisplayInfo();
                        if (dest != null && !dest.isEmpty()) {
                            return "Glider: " + dest;
                        }
                    } else if (TransportType.WILDERNESS_OBELISK.equals(type)) {
                        String dest = transport.getDisplayInfo();
                        if (dest != null && !dest.isEmpty()) {
                            return "Obelisk: " + dest;
                        }
                    }
                    // For other transports, we just use the immediate POH exit
                    // as they're likely the actual destination
                }
            }
        }
        
        return null;
    }
}
