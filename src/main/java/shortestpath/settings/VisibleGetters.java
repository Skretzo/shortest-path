package shortestpath.settings;

import java.util.Map;
import java.util.function.Function;

import shortestpath.ShortestPathConfig;

/**
 * The {@link ShortestPathConfig} getter each visible {@link ConfigKey} row
 * binds to, keyed by the enum constant's name. The map lives outside the
 * enum and is keyed by name rather than {@code ConfigKey}: constant
 * constructors run their lookup while the enum's own initialisation is in
 * flight, so entries naming {@code ConfigKey} constants would read fields
 * that are not assigned yet.
 */
final class VisibleGetters
{
	static final Map<String, Function<ShortestPathConfig, Object>> BY_NAME = Map.ofEntries(
		Map.entry("SHOW_SIDEBAR_PANEL", ShortestPathConfig::showSidebarPanel),
		Map.entry("DRAW_MAP", ShortestPathConfig::drawMap),
		Map.entry("DRAW_MINIMAP", ShortestPathConfig::drawMinimap),
		Map.entry("DRAW_TILES", ShortestPathConfig::drawTiles),
		Map.entry("DRAW_CLICK_POINTS", ShortestPathConfig::drawClickPoints),
		Map.entry("PATH_STYLE", ShortestPathConfig::pathStyle),
		Map.entry("SHOW_TELEPORT_PULSE", ShortestPathConfig::showTeleportPulse),
		Map.entry("COLOUR_PATH", ShortestPathConfig::colourPath),
		Map.entry("COLOUR_PATH_CALCULATING", ShortestPathConfig::colourPathCalculating),
		Map.entry("COLOUR_PATH_UNREACHABLE", ShortestPathConfig::colourPathUnreachable),
		Map.entry("COLOUR_TRANSPORTS", ShortestPathConfig::colourTransports),
		Map.entry("COLOUR_COLLISION_MAP", ShortestPathConfig::colourCollisionMap),
		Map.entry("COLOUR_TEXT", ShortestPathConfig::colourText),
		Map.entry("COLOUR_TELEPORT_PULSE", ShortestPathConfig::colourTeleportPulse),
		Map.entry("COLOUR_BANK_PICKUP_HIGHLIGHT", ShortestPathConfig::colourBankPickupHighlight),
		Map.entry("CLEAR_PATH_HOTKEY", ShortestPathConfig::clearPathHotkey),
		Map.entry("DRAW_TRANSPORTS", ShortestPathConfig::drawTransports),
		Map.entry("DRAW_COLLISION_MAP", ShortestPathConfig::drawCollisionMap),
		Map.entry("DRAW_DEBUG_PANEL", ShortestPathConfig::drawDebugPanel),
		Map.entry("POST_TRANSPORTS", ShortestPathConfig::postTransports));

	private VisibleGetters()
	{
	}
}
