package shortestpath;

import java.awt.Color;
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
		description = "Show the Shortest Path icon in the RuneLite sidebar",
		position = 70,
		section = sectionSettings
	)
	default boolean showSidebarPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "avoidWilderness",
		name = "Avoid Wilderness",
		description = "Whether the wilderness should be avoided if possible<br>" +
			"(otherwise, will e.g. use wilderness lever from Edgeville to Ardougne)",
		position = 10,
		section = sectionSettings
	)
	default boolean avoidWilderness()
	{
		return true;
	}

	@ConfigItem(
		keyName = "collisionAwareBlockedTargets",
		name = "Collision-Aware Blocked Targets",
		description = "When the target tile cannot be stood on, limit the fallback goal tiles to tiles<br>" +
			"connected to it through the collision map (the same side of walls), instead of every<br>" +
			"walkable tile within the unreachable target distance",
		position = 11,
		section = sectionSettings
	)
	default boolean collisionAwareBlockedTargets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useSailingMoves",
		name = "Sail On Boats",
		description = "While on a boat, find a path that sails the boat's 16 headings at its base speed<br>" +
			"with its whole hull clear, and draw it in blue, instead of a walking path",
		position = 12,
		section = sectionSettings
	)
	default boolean useSailingMoves()
	{
		return true;
	}

	@ConfigItem(
		keyName = "currencyThreshold",
		name = "Currency Threshold",
		description = "The maximum amount of currency to use on a single transportation method.<br>" +
			"The currencies affected by the threshold are coins, trading sticks, ecto-tokens and warrior guild tokens.",
		position = 13,
		section = sectionSettings
	)
	default int currencyThreshold()
	{
		return 100000;
	}

	@ConfigItem(
		keyName = "cancelInstead",
		name = "Cancel Instead Of Recalculating",
		description = "Whether the path should be cancelled rather than recalculated " +
			"when the recalculate distance limit is exceeded",
		position = 14,
		section = sectionSettings
	)
	default boolean cancelInstead()
	{
		return false;
	}

	@ConfigItem(
		keyName = "recalculateDistance",
		name = "Recalculate Distance",
		description = "Distance from the path the player should be for it to be recalculated (-1 for never)",
		position = 15,
		section = sectionSettings
	)
	default int recalculateDistance()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "finishDistance",
		name = "Finish Distance",
		description = "Distance from the target tile at which the path should be ended (-1 for never)",
		position = 16,
		section = sectionSettings
	)
	default int reachedDistance()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "unreachableTargetDistanceThreshold",
		name = "Unreachable Target Distance",
		description = "Distance from the target at which a finished path is considered not to reach the target.<br>" +
			"Useful for determining if a path is potentially invalid.",
		position = 17,
		section = sectionSettings
	)
	default int unreachableTargetDistance()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "showUnreachableText",
		name = "Show Unreachable Text",
		description = "Whether to display text on the player tile when the destination cannot be reached",
		position = 18,
		section = sectionSettings
	)
	default boolean showUnreachableText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "calculationCutoff",
		name = "Calculation Cutoff",
		description = "The cutoff threshold in number of ticks (0.6 seconds) of no progress being<br>" +
			"made towards the path target before the calculation will be stopped",
		position = 19,
		section = sectionSettings
	)
	default int calculationCutoff()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "useTeleportationItems",
		name = "Use Teleportation Items",
		description = "Whether to include teleportation items from the player's inventory and equipment.<br>" +
			"Options labelled (perm) only use permanent non-charge items.<br>" +
			"The All options do not check skill, quest or item requirements.",
		position = 20,
		section = sectionSettings
	)
	default TeleportationItem useTeleportationItems()
	{
		return TeleportationItem.INVENTORY_NON_CONSUMABLE;
	}

	@ConfigItem(
		keyName = "aStarHeuristicWeight",
		name = "A* Heuristic Weight",
		description = "Makes the A* backend search faster at the price of a suboptimal route.<br>" +
			"The route costs at most this percentage of the optimal route's cost:<br>" +
			"100 always finds the optimal route, 300 may find one up to 3x as costly.",
		position = 21,
		section = sectionSettings
	)
	default int aStarHeuristicWeight()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "unlockCanoeAxe",
		name = "Axe Stored At A Canoe Station",
		description = "Enable if you have stored an axe at a canoe station.<br>" +
			"The game does not expose the stored axe to the client,<br>" +
			"so the plugin cannot detect it automatically.<br>" +
			"Enabling this declares the unlock without verification",
		position = 22,
		section = sectionSettings
	)
	default boolean unlockCanoeAxe()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unlockXericsHonour",
		name = "Xeric's Honour Unlocked",
		description = "Enable if you have used an ancient tablet on your Xeric's talisman.<br>" +
			"The game does not expose the tablet unlock to the client,<br>" +
			"so the plugin cannot detect it automatically.<br>" +
			"Enabling this declares the unlock without verification",
		position = 23,
		section = sectionSettings
	)
	default boolean unlockXericsHonour()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unlockDragontoothPassage",
		name = "Dragontooth Island Free Passage",
		description = "Enable if you have permanently unlocked free boat passage to Dragontooth Island<br>" +
			"(the one-time Ghosts Ahoy reward paid to the ghost captain).<br>" +
			"The game does not expose the unlock to the client,<br>" +
			"so the plugin cannot detect it automatically.<br>" +
			"Enabling this declares the unlock without verification",
		position = 24,
		section = sectionSettings
	)
	default boolean unlockDragontoothPassage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unlockMorUlRek",
		name = "Mor Ul Rek Inner City Unlocked",
		description = "Enable after showing a fire cape to a TzHaar-Ket guard to permanently unlock the inner city.<br>" +
			"The plugin cannot currently detect this unlock automatically.<br>" +
			"Once enabled, you do not need to carry a fire cape to use the hot vent doors",
		position = 25,
		section = sectionSettings
	)
	default boolean unlockMorUlRek()
	{
		return false;
	}

	@ConfigItem(
		keyName = "respawnPrifddinas",
		name = "Prifddinas Respawn Point",
		description = "Enable if your respawn point is Prifddinas.<br>" +
			"The game does not expose the Prifddinas respawn to the client,<br>" +
			"so the plugin cannot detect it automatically",
		position = 26,
		section = sectionSettings
	)
	default boolean respawnPrifddinas()
	{
		return false;
	}

	@ConfigItem(
		keyName = "includeBankPath",
		name = "Include Path To Bank",
		description = "Whether to include the path to the closest bank<br>" +
			"when suggesting teleports from the bank",
		position = 27,
		section = sectionSettings
	)
	default boolean includeBankPath()
	{
		return false;
	}

	@ConfigItem(
		keyName = "costBankVisit",
		name = "Bank Visit Threshold",
		description = "How many extra tiles fetching a teleport item from your bank must save<br>" +
			"to be preferred over options already at hand (0 treats banking as free)",
		position = 28,
		section = sectionSettings
	)
	default int costBankVisit()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showBankPickupInfo",
		name = "Show Transport Hint At Pickup",
		description = "When standing at a bank on the path, also show the transport hint " +
			"for the next step requiring an item pickup",
		position = 29,
		section = sectionSettings
	)
	default boolean showBankPickupInfo()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightBankPickupItems",
		name = "Highlight Bank Pickup Items",
		description = "Highlight items in the bank that need to be picked up for the current path",
		position = 30,
		section = sectionSettings
	)
	default boolean highlightBankPickupItems()
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

	@ConfigItem(
		keyName = "showTransportInfo",
		name = "Show Transport Info",
		description = "Whether to display transport destination hint info, e.g. which chat option and text to click",
		position = 191,
		section = sectionDisplay
	)
	default boolean showTransportInfo()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightSpellbookSpells",
		name = "Highlight Spellbook Spells",
		description = "Highlight spells in the spellbook that need to be cast for the current path step",
		position = 192,
		section = sectionDisplay
	)
	default boolean highlightSpellbookSpells()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightInventoryItems",
		name = "Highlight Inventory Items",
		description = "Highlight items in the inventory and equipment that need to be used for the current path step",
		position = 193,
		section = sectionDisplay
	)
	default boolean highlightInventoryItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTileCounter",
		name = "Show Tile Counter",
		description = "Whether to display the number of tiles travelled, number of tiles remaining or disable counting",
		position = 194,
		section = sectionDisplay
	)
	default TileCounter showTileCounter()
	{
		return TileCounter.DISABLED;
	}

	@ConfigItem(
		keyName = "tileCounterStep",
		name = "Tile Counter Step",
		description = "The number of tiles between the displayed tile counter numbers",
		position = 195,
		section = sectionDisplay
	)
	default int tileCounterStep()
	{
		return 1;
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
		name = "Collision Map",
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
		name = "Teleport Pulse",
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
		name = "Clear Current Path",
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
		name = "Show Debug Panel",
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
		name = "Show Click Points",
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
