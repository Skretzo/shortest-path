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
import shortestpath.pathfinder.PathfinderBackend;
import shortestpath.settings.TeleportationItem;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.poh.PohNexusPortal;
import shortestpath.poh.PohMountedItem;
import java.util.HashSet;

@ConfigGroup(ShortestPathPlugin.CONFIG_GROUP)
public interface ShortestPathConfig extends Config
{
	@ConfigSection(
		name = "Settings",
		description = "Options for the pathfinding",
		position = 0
	)
	String sectionSettings = "sectionSettings";

	@ConfigItem(
		keyName = "pathfinderAlgorithm",
		name = "Pathfinder algorithm",
		description = "Algorithm used for pathfinding",
		position = 40,
		section = sectionSettings
	)
	default PathfinderBackend pathfinderBackend()
	{
		return PathfinderBackend.LEGACY;
	}

	@ConfigItem(
		keyName = "avoidWilderness",
		name = "Avoid wilderness",
		description = "Avoid the wilderness where possible",
		position = 41,
		section = sectionSettings
	)
	default boolean avoidWilderness()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useAgilityShortcuts",
		name = "Use agility shortcuts",
		description = "Include agility shortcuts in the path.<br>",
		position = 42,
		section = sectionSettings
	)
	default boolean useAgilityShortcuts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useGrappleShortcuts",
		name = "Use grapple shortcuts",
		description = "Include crossbow grapple agility shortcuts in the path.<br>",
		position = 43,
		section = sectionSettings
	)
	default boolean useGrappleShortcuts()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useBoats",
		name = "Use boats",
		description = "Whether to include small boats in the path<br>" +
			"(e.g. the boat to Fishing Platform)",
		hidden = true
	)
	default boolean useBoats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useCanoes",
		name = "Use canoes",
		description = "Include canoes in the path",
		position = 45,
		section = sectionSettings
	)
	default boolean useCanoes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useCharterShips",
		name = "Use charter ships",
		description = "Include charter ships in the path",
		position = 46,
		section = sectionSettings
	)
	default boolean useCharterShips()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useShips",
		name = "Use ships",
		description = "Include passenger ships in the path<br>",
		position = 47,
		section = sectionSettings
	)
	default boolean useShips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useFairyRings",
		name = "Use fairy rings",
		description = "Include fairy rings in the path.<br>",
		position = 48,
		section = sectionSettings
	)
	default boolean useFairyRings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useGnomeGliders",
		name = "Use gnome gliders",
		description = "Include gnome gliders in the path",
		position = 49,
		section = sectionSettings
	)
	default boolean useGnomeGliders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useHotAirBalloons",
		name = "Use hot air balloons",
		description = "Include hot air balloons in the path",
		position = 50,
		section = sectionSettings
	)
	default boolean useHotAirBalloons()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useMagicCarpets",
		name = "Use magic carpets",
		description = "Include magic carpets in the path",
		position = 51,
		section = sectionSettings
	)
	default boolean useMagicCarpets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useMagicMushtrees",
		name = "Use magic mushtrees",
		description = "Include Fossil Island Magic Mushtrees in the path<br>",
		position = 52,
		section = sectionSettings
	)
	default boolean useMagicMushtrees()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useMinecarts",
		name = "Use minecarts",
		description = "Include minecarts in the path<br>",
		position = 53,
		section = sectionSettings
	)
	default boolean useMinecarts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useQuetzals",
		name = "Use quetzals",
		description = "Include quetzals in the path",
		position = 54,
		section = sectionSettings
	)
	default boolean useQuetzals()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useSpiritTrees",
		name = "Use spirit trees",
		description = "Include spirit trees in the path",
		position = 55,
		section = sectionSettings
	)
	default boolean useSpiritTrees()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationItems",
		name = "Use teleportation items",
		description = "Include teleportation items from player's inventory and equipment.<br>" +
			"Options labelled (perm) only use permanent non-charge items.<br>" +
			"The All options do not check skill, quest or item requirements.",
		position = 56,
		section = sectionSettings
	)
	default TeleportationItem useTeleportationItems()
	{
		return TeleportationItem.INVENTORY_NON_CONSUMABLE;
	}

	@ConfigItem(
		keyName = "useTeleportationLevers",
		name = "Use teleportation levers",
		description = "Include teleportation levers in the path<br>",
		position = 57,
		section = sectionSettings
	)
	default boolean useTeleportationLevers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationPortals",
		name = "Use teleportation portals",
		description = "Include teleportation portals in the path<br>",
		position = 58,
		section = sectionSettings
	)
	default boolean useTeleportationPortals()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationSpells",
		name = "Use teleportation spells",
		description = "Include teleportation spells in the path",
		position = 59,
		section = sectionSettings
	)
	default boolean useTeleportationSpells()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationSpellsHome",
		name = "Use Home Teleport spells",
		description = "Include Home Teleport spells in the path",
		position = 60,
		section = sectionSettings
	)
	default boolean useTeleportationSpellsHome()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationMinigames",
		name = "Use teleportation to minigames",
		description = "Include teleportation to minigames/activities/grouping <br>",
		position = 61,
		section = sectionSettings
	)
	default boolean useTeleportationMinigames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useWildernessObelisks",
		name = "Use wilderness obelisks",
		description = "Include wilderness obelisks in the path",
		position = 62,
		section = sectionSettings
	)
	default boolean useWildernessObelisks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useSeasonalTransports",
		name = "Use seasonal transports",
		description = "Include seasonal transports like League teleports in the path",
		position = 63,
		section = sectionSettings
	)
	default boolean useSeasonalTransports()
	{
		return false;
	}

	@ConfigItem(
		keyName = "respawnPrifddinas",
		name = "Prifddinas respawn point",
		description = "Enable if your respawn point is Prifddinas.<br>" +
			"The game does not expose the Prifddinas respawn to the client,<br>" +
			"so the plugin cannot detect it automatically",
		position = 64,
		section = sectionSettings
	)
	default boolean respawnPrifddinas()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unlockCanoeAxe",
		name = "Axe stored at a canoe station",
		description = "Enable if you have stored an axe at a canoe station.<br>" +
			"The game does not expose the stored axe to the client,<br>" +
			"so the plugin cannot detect it automatically.<br>" +
			"Enabling this declares the unlock without verification",
		position = 65,
		section = sectionSettings
	)
	default boolean unlockCanoeAxe()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unlockXericsHonour",
		name = "Xeric's Honour unlocked",
		description = "Enable if you have used an ancient tablet on Xeric's talisman.<br>" +
			"The game does not expose the tablet unlock to the client,<br>" +
			"so the plugin cannot detect it automatically.<br>" +
			"Enabling this declares the unlock without verification",
		position = 66,
		section = sectionSettings
	)
	default boolean unlockXericsHonour()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unlockDragontoothPassage",
		name = "Dragontooth Island free passage",
		description = "Enable if you have permanently unlocked free boat passage to Dragontooth Island<br>" +
			"(the one-time Ghosts Ahoy reward paid to the ghost captain).<br>" +
			"The game does not expose the unlock to the client,<br>" +
			"so the plugin cannot detect it automatically.<br>" +
			"Enabling this declares the unlock without verification",
		position = 67,
		section = sectionSettings
	)
	default boolean unlockDragontoothPassage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unlockMorUlRek",
		name = "Mor Ul Rek inner city unlocked",
		description = "Enable after showing a fire cape to a TzHaar-Ket guard to permanently unlock inner city.<br>" +
			"The plugin cannot currently detect this unlock automatically.<br>" +
			"Once enabled, you do not need to carry a fire cape to use the hot vent doors",
		position = 68,
		section = sectionSettings
	)
	default boolean unlockMorUlRek()
	{
		return false;
	}

	@ConfigItem(
		keyName = "includeBankPath",
		name = "Include path to bank",
		description = "Include the path to the closest bank<br>" +
			"when suggesting teleports from the bank",
		position = 69,
		section = sectionSettings
	)
	default boolean includeBankPath()
	{
		return false;
	}

	@ConfigItem(
		keyName = "currencyThreshold",
		name = "Currency threshold",
		description = "The maximum amount of currency to use on a single transportation method." +
			"<br>The currencies affected by the threshold are coins, trading sticks, ecto-tokens and warrior guild tokens.",
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
	@ConfigItem(
		keyName = "usePohMountedItems",
		name = "",
		description = "",
		hidden = true
	)
	default boolean usePohMountedItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "usePohObelisk",
		name = "POH wilderness obelisk",
		description = "Include the POH wilderness obelisk in the path.<br>" +
			"Enable this if you have built an obelisk in your house (80 Construction or boosted)",
		position = 149,
		section = sectionPoh
	)
	default boolean usePohObelisk()
	{
		return false;
	}

	@ConfigSection(
		name = "Transport Thresholds",
		description = "Set customizable thresholds for how much faster a transportation<br>" +
			"method must be to be preferred over other methods",
		position = 150,
		closedByDefault = true
	)
	String sectionThresholds = "sectionThresholds";

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costAgilityShortcuts",
		name = "Agility shortcut threshold",
		description = "How many extra tiles an agility shortcut must save<br>" +
			"to be preferred over walking or other transports",
		position = 151,
		section = sectionThresholds
	)
	default int costAgilityShortcuts()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costGrappleShortcuts",
		name = "Grapple shortcut threshold",
		description = "How many extra tiles a grapple shortcut must save<br>" +
			"to be preferred over walking or other transports",
		position = 152,
		section = sectionThresholds
	)
	default int costGrappleShortcuts()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costBoats",
		name = "Boat threshold",
		description = "How many extra tiles a small boat must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
	)
	default int costBoats()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costCanoes",
		name = "Canoe threshold",
		description = "How many extra tiles a canoe must save<br>" +
			"to be preferred over walking or other transports",
		position = 154,
		section = sectionThresholds
	)
	default int costCanoes()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costCharterShips",
		name = "Charter ship threshold",
		description = "How many extra tiles a charter ship must save<br>" +
			"to be preferred over walking or other transports",
		position = 155,
		section = sectionThresholds
	)
	default int costCharterShips()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costShips",
		name = "Ship threshold",
		description = "How many extra tiles a passenger ship must save<br>" +
			"to be preferred over walking or other transports",
		position = 156,
		section = sectionThresholds
	)
	default int costShips()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costFairyRings",
		name = "Fairy ring threshold",
		description = "How many extra tiles a fairy ring must save<br>" +
			"to be preferred over walking or other transports",
		position = 157,
		section = sectionThresholds
	)
	default int costFairyRings()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costGnomeGliders",
		name = "Gnome glider threshold",
		description = "How many extra tiles a gnome glider must save<br>" +
			"to be preferred over walking or other transports",
		position = 158,
		section = sectionThresholds
	)
	default int costGnomeGliders()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costHotAirBalloons",
		name = "Hot air balloon threshold",
		description = "How many extra tiles a hot air balloon must save<br>" +
			"to be preferred over walking or other transports",
		position = 159,
		section = sectionThresholds
	)
	default int costHotAirBalloons()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costMagicCarpets",
		name = "Magic carpets threshold",
		description = "How many extra tiles a magic carpet must save<br>" +
			"to be preferred over walking or other transports",
		position = 160,
		section = sectionThresholds
	)
	default int costMagicCarpets()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costMagicMushtrees",
		name = "Magic mushtrees threshold",
		description = "How many extra tiles a magic mushtree must save<br>" +
			"to be preferred over walking or other transports",
		position = 161,
		section = sectionThresholds
	)
	default int costMagicMushtrees()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costMinecarts",
		name = "Minecart threshold",
		description = "How many extra tiles a minecart must save<br>" +
			"to be preferred over walking or other transports",
		position = 162,
		section = sectionThresholds
	)
	default int costMinecarts()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costQuetzals",
		name = "Quetzal threshold",
		description = "How many extra tiles a quetzal must save<br>" +
			"to be preferred over walking or other transports",
		position = 163,
		section = sectionThresholds
	)
	default int costQuetzals()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costQuetzalWhistle",
		name = "Quetzal whistle threshold",
		description = "How many extra tiles a quetzal whistle teleport must save<br>" +
			"to be preferred over using a landing site",
		position = 164,
		section = sectionThresholds
	)
	default int costQuetzalWhistle()
	{
		return 15;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costSpiritTrees",
		name = "Spirit tree threshold",
		description = "How many extra tiles a spirit tree must save<br>" +
			"to be preferred over walking or other transports",
		position = 165,
		section = sectionThresholds
	)
	default int costSpiritTrees()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costNonConsumableTeleportationItems",
		name = "Teleportation item (non-consumable) threshold",
		description = "How many extra tiles a non-consumable (permanent) teleportation item<br>" +
			"must save to be preferred over walking or other transports",
		position = 166,
		section = sectionThresholds
	)
	default int costNonConsumableTeleportationItems()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costConsumableTeleportationItems",
		name = "Teleportation item (consumable) threshold",
		description = "How many extra tiles a consumable (non-permanent) teleportation item<br>" +
			"must save to be preferred over walking or other transports",
		position = 167,
		section = sectionThresholds
	)
	default int costConsumableTeleportationItems()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costTeleportationBoxes",
		name = "Teleportation box threshold",
		description = "How many extra tiles a teleportation box must save<br>" +
			"to be preferred over walking or other transports",
		position = 168,
		section = sectionThresholds
	)
	default int costTeleportationBoxes()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costTeleportationLevers",
		name = "Teleportation lever threshold",
		description = "How many extra tiles a teleportation lever must save<br>" +
			"to be preferred over walking or other transports",
		position = 169,
		section = sectionThresholds
	)
	default int costTeleportationLevers()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costTeleportationPortals",
		name = "Teleportation portal threshold",
		description = "How many extra tiles a teleportation portal (including POH<br>" +
			"nexus portals) must save to be preferred over walking or<br>" +
			"other transports",
		position = 170,
		section = sectionThresholds
	)
	default int costTeleportationPortals()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costTeleportationSpells",
		name = "Teleportation spell threshold",
		description = "How many extra tiles a teleportation spell must save<br>" +
			"to be preferred over walking or other transports",
		position = 171,
		section = sectionThresholds
	)
	default int costTeleportationSpells()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costTeleportationSpellsHome",
		name = "Home Teleport spell threshold",
		description = "How many extra tiles a Home Teleport spell must save<br>" +
			"to be preferred over walking or other transports",
		position = 172,
		section = sectionThresholds
	)
	default int costTeleportationSpellsHome()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costTeleportationMinigames",
		name = "Teleportation to minigame threshold",
		description = "How many extra tiles a minigame teleport must save<br>" +
			"to be preferred over walking or other transports",
		position = 173,
		section = sectionThresholds
	)
	default int costTeleportationMinigames()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costWildernessObelisks",
		name = "Wilderness obelisk threshold",
		description = "How many extra tiles a wilderness obelisk must save<br>" +
			"to be preferred over walking or other transports",
		position = 174,
		section = sectionThresholds
	)
	default int costWildernessObelisks()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costSeasonalTransports",
		name = "Seasonal transport threshold",
		description = "How many extra tiles a seasonal transport must save<br>" +
			"to be preferred over walking or other transports",
		position = 175,
		section = sectionThresholds
	)
	default int costSeasonalTransports()
	{
		return 0;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costBankVisit",
		name = "Bank visit threshold",
		description = "How many extra tiles fetching a teleport item from bank must save<br>" +
			"to be preferred over options already at hand (0 treats banking as free)",
		position = 176,
		section = sectionThresholds
	)
	default int costBankVisit()
	{
		return 0;
	}

	@ConfigSection(
		name = "Display",
		description = "Options for displaying path on world map, minimap and scene tiles",
		position = 177
	)
	String sectionDisplay = "sectionDisplay";

	@ConfigItem(
		keyName = "drawMap",
		name = "Draw path on world map",
		description = "Whether path should be drawn on the world map",
		position = 178,
		section = sectionDisplay
	)
	default boolean drawMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawMinimap",
		name = "Draw path on minimap",
		description = "Whether path should be drawn on the minimap",
		position = 179,
		section = sectionDisplay
	)
	default boolean drawMinimap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawTiles",
		name = "Draw path on tiles",
		description = "Whether path should be drawn on the game tiles",
		position = 180,
		section = sectionDisplay
	)
	default boolean drawTiles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pathStyle",
		name = "Path style",
		description = "display path as tiles, a segmented line, or a line with direction arrows",
		position = 181,
		section = sectionDisplay
	)
	default TileStyle pathStyle()
	{
		return TileStyle.TILES;
	}

	@ConfigItem(
		keyName = "showTeleportPulse",
		name = "Teleport pulse",
		description = "Animate a pulsing highlight on tile when next teleport on path is due",
		position = 182,
		section = sectionDisplay
	)
	default boolean showTeleportPulse()
	{
		return false;
	}

	@ConfigSection(
		name = "Colours",
		description = "Colours for the path map, minimap and scene tiles",
		position = 183
	)
	String sectionColours = "sectionColours";

	@Alpha
	@ConfigItem(
		keyName = "colourPath",
		name = "Path",
		description = "Colour of path tiles on world map, minimap and in game scene",
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
		description = "Colour of path tiles while pathfinding calculation is in progress," +
			"<br>and the colour of unused targets if there are more than a single target",
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
		description = "Colour of path tiles when pathfinding has finished but target is still too far away",
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
		description = "Colour of the transport tiles",
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
		description = "Colour of the collision map tiles",
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
		description = "Colour of the text of the tile counter and fairy ring codes",
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
		description = "Colour of the pulsing teleport highlight",
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
		name = "Bank pickup highlight",
		description = "Colour used to highlight bank items that need to be picked up for current path",
		position = 200,
		section = sectionColours
	)
	default Color colourBankPickupHighlight()
	{
		return new Color(0, 255, 255, 255);
	}

	@ConfigSection(
		name = "Hotkeys",
		description = "Options for keyboard shortcuts",
		position = 201
	)
	String sectionHotkeys = "sectionHotkeys";

	@ConfigItem(
		keyName = "clearPathHotkey",
		name = "Clear current path",
		description = "Hotkey to clear the current path",
		position = 202,
		section = sectionHotkeys
	)
	default Keybind clearPathHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigSection(
		name = "Debug Options",
		description = "Various options for debugging",
		position = 203,
		closedByDefault = true
	)
	String sectionDebug = "sectionDebug";

	@ConfigItem(
		keyName = "drawTransports",
		name = "Draw transports",
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
		name = "Draw collision map",
		description = "Whether collision map should be drawn",
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
		description = "Toggles displaying the pathfinding debug stats panel",
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
		description = "Highlight exact route click points in magenta in the game view",
		position = 208,
		section = sectionDebug
	)
	default boolean drawClickPoints()
	{
		return false;
	}

	@ConfigItem(
		keyName = "postTransports",
		name = "Post transports",
		description = "Whether to post transports used in current path as a PluginMessage event",
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
