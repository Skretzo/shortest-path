package pathfinder;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;

@PluginDescriptor(
    name = "Shortest Path Test",
    description = "Test the Shortest Path Plugin Message events"
)
public class PluginMessageTestPlugin extends Plugin {
    private static final String PLUGIN_MESSAGE_NAME = "shortestpath";
    private static final String PLUGIN_MESSAGE_PATH = "path";
    private static final String PLUGIN_MESSAGE_CLEAR = "clear";
    private static final String PLUGIN_MESSAGE_START = "start";
    private static final String PLUGIN_MESSAGE_TARGET = "target";
    private static final String PLUGIN_MESSAGE_CONFIG_OVERRIDE = "config";
    private static final String CLEAR = "Clear";
    private static final String PATH = ColorUtil.wrapWithColorTag("Path (PluginMessage)", JagexColors.MENU_TARGET);
    private static final String SET = "Set";
    private static final String START_INT = ColorUtil.wrapWithColorTag("Start Integer (PluginMessage)", JagexColors.MENU_TARGET);
    private static final String START_WP = ColorUtil.wrapWithColorTag("Start WorldPoint (PluginMessage)", JagexColors.MENU_TARGET);
    private static final String TARGET_INT = ColorUtil.wrapWithColorTag("Target Integer(s) (PluginMessage)", JagexColors.MENU_TARGET);
    private static final String TARGET_WP = ColorUtil.wrapWithColorTag("Target WorldPoint(s) (PluginMessage)", JagexColors.MENU_TARGET);
    private static final String CONFIG_COLOUR_PATH = ColorUtil.wrapWithColorTag("Yellow path colour (PluginMessage)", JagexColors.MENU_TARGET);

    private Set<WorldPoint> targets = new HashSet<>(10);
    private Point lastMenuOpenedPoint;

    @Inject
    private Client client;

    @Inject
    private EventBus eventBus;

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        lastMenuOpenedPoint = client.getMouseCanvasPosition();

        if (!client.isKeyPressed(KeyCode.KC_SHIFT)) {
            return;
        }

        for (MenuEntry menuEntry : event.getMenuEntries()) {
            if (MenuAction.WALK.equals(menuEntry.getType())) {
                addMenuEntry(event, SET, TARGET_INT, 1);
                addMenuEntry(event, SET, TARGET_WP, 1);
                addMenuEntry(event, SET, CONFIG_COLOUR_PATH, 1);
                if (!targets.isEmpty()) {
                    addMenuEntry(event, SET, START_INT, 1);
                    addMenuEntry(event, SET, START_WP, 1);
                }
                addMenuEntry(event, CLEAR, PATH, 1);
                break;
            }
        }

        final Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);

        if (map != null
            && map.getBounds().contains(
                client.getMouseCanvasPosition().getX(),
                client.getMouseCanvasPosition().getY())) {
            addMenuEntry(event, SET, TARGET_INT, 0);
            addMenuEntry(event, SET, TARGET_WP, 0);
            addMenuEntry(event, SET, CONFIG_COLOUR_PATH, 0);
            if (!targets.isEmpty()) {
                addMenuEntry(event, SET, START_INT, 0);
                addMenuEntry(event, SET, START_WP, 0);
                addMenuEntry(event, CLEAR, PATH, 0);
            }
        }
    }

    private void addMenuEntry(MenuOpened event, String option, String target, int position) {
        client.createMenuEntry(position)
            .setOption(option)
            .setTarget(target)
            .setType(MenuAction.RUNELITE)
            .onClick(this::onMenuOptionClicked);
    }

    private void onMenuOptionClicked(MenuEntry entry) {
        boolean isMessageInt = false;
        boolean overrideColourPath = false;
        WorldPoint start = null;
        WorldPoint target = null;

        if (entry.getOption().equals(SET) && entry.getTarget().equals(TARGET_INT)) {
            isMessageInt = true;
            target = getSelectedWorldPoint();
        } else if (entry.getOption().equals(SET) && entry.getTarget().equals(TARGET_WP)) {
            target = getSelectedWorldPoint();
        } else if (entry.getOption().equals(SET) && entry.getTarget().equals(START_INT)) {
            isMessageInt = true;
            start = getSelectedWorldPoint();
        } else if (entry.getOption().equals(SET) && entry.getTarget().equals(START_WP)) {
            start = getSelectedWorldPoint();
        } else if (entry.getOption().equals(SET) && entry.getTarget().equals(CONFIG_COLOUR_PATH)) {
            overrideColourPath = true;
        } else if (entry.getOption().equals(CLEAR) && entry.getTarget().equals(PATH)) {
            targets.clear();
            eventBus.post(new PluginMessage(PLUGIN_MESSAGE_NAME, PLUGIN_MESSAGE_CLEAR));
            return;
        }

        if (target != null) {
            targets.add(target);
        }

        Set<Integer> convertedTargets = new HashSet<>();
        if (isMessageInt) {
            for (WorldPoint t : targets) {
                convertedTargets.add(packWorldPoint(t));
            }
        }

        Map<String, Object> data = new HashMap<>();

        if (start != null) {
            data.put(PLUGIN_MESSAGE_START, isMessageInt ? packWorldPoint(start) : start);
        }

        if (!targets.isEmpty()) {
            data.put(PLUGIN_MESSAGE_TARGET,
                targets.size() != 1 ? (isMessageInt ? convertedTargets : targets)
                : (isMessageInt ? convertedTargets.iterator().next() : targets.iterator().next()));
        }

        if (overrideColourPath) {
            Map<String, Object> configOverride = new HashMap<>();
            configOverride.put("colourPath", new Color(255, 255, 0));
            data.put(PLUGIN_MESSAGE_CONFIG_OVERRIDE, configOverride);
        }

        eventBus.post(new PluginMessage(PLUGIN_MESSAGE_NAME, PLUGIN_MESSAGE_PATH, data));
    }

    private WorldPoint getSelectedWorldPoint() {
        if (client.getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            if (client.getSelectedSceneTile() != null) {
                return WorldPoint.fromLocalInstance(client, client.getSelectedSceneTile().getLocalLocation());
            }
        } else {
            return client.isMenuOpen()
                ? calculateMapPoint(lastMenuOpenedPoint.getX(), lastMenuOpenedPoint.getY())
                : calculateMapPoint(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());
        }
        return null;
    }

    private WorldPoint calculateMapPoint(int pointX, int pointY) {
        WorldMap worldMap = client.getWorldMap();
        float zoom = worldMap.getWorldMapZoom();
        WorldPoint mapPoint = new WorldPoint(worldMap.getWorldMapPosition().getX(), worldMap.getWorldMapPosition().getY(), 0);
        int middleX = mapWorldPointToGraphicsPointX(mapPoint);
        int middleY = mapWorldPointToGraphicsPointY(mapPoint);

        if (pointX == Integer.MIN_VALUE || pointY == Integer.MIN_VALUE ||
            middleX == Integer.MIN_VALUE || middleY == Integer.MIN_VALUE) {
            return null;
        }

        final int dx = (int) ((pointX - middleX) / zoom);
        final int dy = (int) ((-(pointY - middleY)) / zoom);

        return mapPoint.dx(dx).dy(dy);
    }

    public int mapWorldPointToGraphicsPointX(WorldPoint worldPoint) {
        WorldMap worldMap = client.getWorldMap();

        float pixelsPerTile = worldMap.getWorldMapZoom();

        Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
        if (map != null) {
            Rectangle worldMapRect = map.getBounds();

            int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);

            Point worldMapPosition = worldMap.getWorldMapPosition();

            int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();

            int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
            xGraphDiff += pixelsPerTile - Math.ceil(pixelsPerTile / 2);
            xGraphDiff += (int) worldMapRect.getX();

            return xGraphDiff;
        }
        return Integer.MIN_VALUE;
    }

    public int mapWorldPointToGraphicsPointY(WorldPoint worldPoint) {
        WorldMap worldMap = client.getWorldMap();

        float pixelsPerTile = worldMap.getWorldMapZoom();

        Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
        if (map != null) {
            Rectangle worldMapRect = map.getBounds();

            int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

            Point worldMapPosition = worldMap.getWorldMapPosition();

            int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
            int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;

            int yGraphDiff = (int) (yTileOffset * pixelsPerTile);
            yGraphDiff -= pixelsPerTile - Math.ceil(pixelsPerTile / 2);
            yGraphDiff = worldMapRect.height - yGraphDiff;
            yGraphDiff += (int) worldMapRect.getY();

            return yGraphDiff;
        }
        return Integer.MIN_VALUE;
    }

    private static int packWorldPoint(WorldPoint wp) {
        if (wp == null) {
            return -1;
        }
        return (wp.getX() & 0x7FFF) | ((wp.getY() & 0x7FFF) << 15) | ((wp.getPlane() & 0x3) << 30);
    }
}
