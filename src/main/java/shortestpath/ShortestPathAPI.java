package shortestpath;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;

import java.util.Map;

// This class forms the outward-facing "API" to be used by other plugins
@Slf4j
public class ShortestPathAPI {
	private static final String COMMAND_PREFIX = "shortestpath:";

	private final ShortestPathPlugin shortestPathPlugin;

	ShortestPathAPI(ShortestPathPlugin plugin) {
		this.shortestPathPlugin = plugin;
	}

	@SuppressWarnings("unchecked")
	private static <T> T getValidatedObject(Object object, Class<T> expectedType) {
		if (!expectedType.isInstance(object)) {
			return null;
		}
		return (T) object;
	}

	public void parseDataMap(Map<?, ?> data) {
		Plugin plugin;
		if (data.containsKey("plugin")) {
			plugin = getValidatedObject(data.get("plugin"), Plugin.class);
			if (plugin == null) {
				log.debug("Received data without a valid 'plugin' object type");
				return;
			}
		} else {
			log.trace("Received data not containing a 'plugin' key; ignoring");
			return;
		}
		PluginIdentifier requester = new PluginIdentifier(plugin);

		String command = getValidatedObject(data.get("command"), String.class);
		if (command == null) {
			log.debug("{} - Received data without a valid 'command' object", requester);
			return;
		}

		if (!command.startsWith(COMMAND_PREFIX)) {
			log.debug("{} - Command uses an invalid prefix: {}", requester, command);
			return;
		}

		command = command.substring(command.indexOf(':') + 1);
		switch (command) {
			case "request-path": handleRequestPath(requester, data); break;
			case "clear-path": handleClearPath(requester); break;
			default: log.debug("{} - Invalid command: {}", requester, command);
		}
	}

	private void handleRequestPath(PluginIdentifier requester, Map<?, ?> data) {
		WorldPoint target = getValidatedObject(data.get("target"), WorldPoint.class);
		if (target == null) {
			log.debug("{} - 'target' is either not set or contains an invalid object type", requester);
			return;
		}

		WorldPoint start = null;
		if (data.containsKey("start")) {
			Object startObj = data.get("start");
			if (startObj != null && !(startObj instanceof WorldPoint)) {
				log.debug("{} - 'start' contained an invalid object type", requester);
				return;
			}
			start = getValidatedObject(startObj, WorldPoint.class);
		}

		boolean hideMarker = false;
		if (data.containsKey("hide-marker")) {
			Boolean hideMarkerObj = getValidatedObject(data.get("hide-marker"), Boolean.class);
			if (hideMarkerObj == null) {
				log.debug("{} - 'hide-marker' contained an invalid object type", requester);
				return;
			}
			hideMarker = Boolean.TRUE.equals(hideMarkerObj);
		}

		PathParameters parameters = new PathParameters(requester, start, target, hideMarker, true);
		shortestPathPlugin.requestPath(parameters);
	}

	private void handleClearPath(PluginIdentifier requester) {
		shortestPathPlugin.clearPath(requester);
	}
}
