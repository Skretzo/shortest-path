package shortestpath;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import shortestpath.settings.TeleportationItem;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohNexusPortal;
import shortestpath.poh.PohMountedItem;
import shortestpath.transport.BalloonRoute;

@ConfigGroup(ShortestPathPlugin.CONFIG_GROUP)
public interface ShortestPathConfig extends Config
{
	@ConfigSection(
		name = "Settings",
		description = "Pathfinding options. Transport, teleport-restriction, unlock and routing options live in the Shortest Path sidebar panel.",
		position = 0
	)
	String sectionSettings = "sectionSettings";

	@ConfigItem(
		keyName = "showSidebarPanel",
		name = "Show Sidebar Panel",
		description = "Whether the Shortest Path icon is shown in the RuneLite sidebar",
		position = 70,
		section = sectionSettings
	)
	default boolean showSidebarPanel()
	{
		return true;
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * Legacy persisted setting used as the migration default for {@link #pohMountedItems()}.
	 * Remove once the old config value is no longer supported.
	 */
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	@ConfigSection(
		name = "Display",
		description = "Path display on world map, minimap and scene tiles",
		position = 177
	)
	String sectionDisplay = "sectionDisplay";

	@ConfigItem(
		keyName = "drawMap",
		name = "Draw Path On World Map",
		description = "Draw the path on the world map",
		position = 178,
		section = sectionDisplay
	)
	default boolean drawMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawMinimap",
		name = "Draw Path On Minimap",
		description = "Draw the path on the minimap",
		position = 179,
		section = sectionDisplay
	)
	default boolean drawMinimap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawTiles",
		name = "Draw Path On Tiles",
		description = "Draw the path on the game tiles",
		position = 180,
		section = sectionDisplay
	)
	default boolean drawTiles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pathStyle",
		name = "Path Style",
		description = "Draw the path as tiles, a segmented line, or a line with direction arrows",
		position = 181,
		section = sectionDisplay
	)
	default TileStyle pathStyle()
	{
		return TileStyle.TILES;
	}

	@ConfigItem(
		keyName = "showTeleportPulse",
		name = "Teleport Pulse",
		description = "Pulse the tile when the next teleport on the path is due",
		position = 182,
		section = sectionDisplay
	)
	default boolean showTeleportPulse()
	{
		return false;
	}

	@ConfigSection(
		name = "Colours",
		description = "Path colours on map, minimap and scene tiles",
		position = 183
	)
	String sectionColours = "sectionColours";

	@Alpha
	@ConfigItem(
		keyName = "colourPath",
		name = "Path",
		description = "Path tile colour on map, minimap and scene",
		position = 184,
		section = sectionColours
	)
	default Color colourPath()
	{
		return new Color(255, 0, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourPathCalculating",
		name = "Calculating",
		description = "Path tile colour while calculating," +
			"<br>and of unused targets with multiple targets",
		position = 185,
		section = sectionColours
	)
	default Color colourPathCalculating()
	{
		return new Color(0, 0, 255);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourPathUnreachable",
		name = "Unreachable",
		description = "Path tile colour when the target is still too far away",
		position = 186,
		section = sectionColours
	)
	default Color colourPathUnreachable()
	{
		return new Color(200, 40, 240);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourTransports",
		name = "Transports",
		description = "Transport tile colour",
		position = 187,
		section = sectionColours
	)
	default Color colourTransports()
	{
		return new Color(0, 255, 0, 128);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourCollisionMap",
		name = "Collision map",
		description = "Collision map tile colour",
		position = 188,
		section = sectionColours
	)
	default Color colourCollisionMap()
	{
		return new Color(0, 128, 255, 128);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourText",
		name = "Text",
		description = "Text colour for the tile counter and fairy ring codes",
		position = 189,
		section = sectionColours
	)
	default Color colourText()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "colourTeleportPulse",
		name = "Teleport pulse",
		description = "Pulsing teleport highlight colour",
		position = 190,
		section = sectionColours
	)
	default Color colourTeleportPulse()
	{
		return new Color(255, 170, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourBankPickupHighlight",
		name = "Bank Pickup Highlight",
		description = "Highlight colour for bank items needed on the current path",
		position = 200,
		section = sectionColours
	)
	default Color colourBankPickupHighlight()
	{
		return new Color(0, 255, 255, 255);
	}

	@ConfigSection(
		name = "Hotkeys",
		description = "Keyboard shortcut options",
		position = 201
	)
	String sectionHotkeys = "sectionHotkeys";

	@ConfigItem(
		keyName = "clearPathHotkey",
		name = "Clear current path",
		description = "Clear-path hotkey",
		position = 202,
		section = sectionHotkeys
	)
	default Keybind clearPathHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigSection(
		name = "Debug Options",
		description = "Debugging options",
		position = 203,
		closedByDefault = true
	)
	String sectionDebug = "sectionDebug";

	@ConfigItem(
		keyName = "drawTransports",
		name = "Draw Transports",
		description = "Draw all transports on map and game view.<br>White = available, Orange = unavailable (missing requirements)",
		position = 204,
		section = sectionDebug
	)
	default boolean drawTransports()
	{
		return false;
	}

	@ConfigItem(
		keyName = "drawCollisionMap",
		name = "Draw Collision Map",
		description = "Draw the collision map",
		position = 205,
		section = sectionDebug
	)
	default boolean drawCollisionMap()
	{
		return false;
	}

	@ConfigItem(
		keyName = "drawDebugPanel",
		name = "Show debug panel",
		description = "Show the pathfinding debug stats panel",
		position = 206,
		section = sectionDebug
	)
	default boolean drawDebugPanel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "drawClickPoints",
		name = "Show click points",
		description = "Highlight route click points in magenta in the game view",
		position = 208,
		section = sectionDebug
	)
	default boolean drawClickPoints()
	{
		return false;
	}

	@ConfigItem(
		keyName = "postTransports",
		name = "Post Transports",
		description = "Post the current path's transports as a PluginMessage event",
		position = 209,
		section = sectionDebug
	)
	default boolean postTransports()
	{
		return false;
	}

	
	
	
	
	
	
	
	/**
	 * The balloon routes whose destination basket holds the required logs.
	 * While this key has never been written its default derives from the
	 * legacy {@link #unlockBalloonLogBasket()} flag (on =&gt; every route,
	 * off =&gt; none); the first written per-route set takes over
	 * permanently, after which the flag keeps meaning only for profiles that
	 * never wrote this key.
	 */
	
}
