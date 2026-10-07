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
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.PohMountedItem;




@SuppressWarnings("SameReturnValue")
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
		keyName = "pathfinderBackend",
		name = "Pathfinder backend",
		description = "Backend used for pathfinding",
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
		description = "Whether the wilderness should be avoided if possible<br>" +
			"(otherwise, will e.g. use wilderness lever from Edgeville to Ardougne)",
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
		description = "Whether to include agility shortcuts in the path.<br>" +
			"You must also have the required agility level",
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
		description = "Whether to include crossbow grapple agility shortcuts in the path.<br>" +
			"You must also have the required agility, ranged and strength levels",
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
		position = 44,
		section = sectionSettings
	)
	default boolean useBoats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useCanoes",
		name = "Use canoes",
		description = "Whether to include canoes in the path",
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
		description = "Whether to include charter ships in the path",
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
		description = "Whether to include passenger ships in the path<br>" +
			"(e.g. the customs ships to Karamja)",
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
		description = "Whether to include fairy rings in the path.<br>" +
			"You must also have completed the required quests or miniquests",
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
		description = "Whether to include gnome gliders in the path",
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
		description = "Whether to include hot air balloons in the path",
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
		description = "Whether to include magic carpets in the path",
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
		description = "Whether to include Fossil Island Magic Mushtrees in the path<br>" +
			"(e.g. the Mycelium transport network from Verdant Valley to Mushroom Meadow)",
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
		description = "Whether to include minecarts in the path<br>" +
			"(e.g. the Keldagrim and Lovakengj minecart networks)",
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
		description = "Whether to include quetzals in the path",
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
		description = "Whether to include spirit trees in the path",
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
		description = "Whether to include teleportation items from the player's inventory and equipment.<br>" +
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
		description = "Whether to include teleportation levers in the path<br>" +
			"(e.g. the lever from Edgeville to Wilderness)",
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
		description = "Whether to include teleportation portals in the path<br>" +
			"(e.g. the portal from Ferox Enclave to Castle Wars)",
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
		description = "Whether to include teleportation spells in the path",
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
		description = "Whether to include Home Teleport spells in the path",
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
		description = "Whether to include teleportation to minigames/activities/grouping in the path<br>" +
			"(e.g. the Nightmare Zone minigame teleport). These teleports share a 20 minute cooldown.",
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
		description = "Whether to include wilderness obelisks in the path",
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
		description = "Whether to include seasonal transports like League teleports in the path",
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
		description = "Enable if you have used an ancient tablet on your Xeric's talisman.<br>" +
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
		keyName = "includeBankPath",
		name = "Include path to bank",
		description = "Whether to include the path to the closest bank<br>" +
			"when suggesting teleports from the bank",
		position = 68,
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
		position = 69,
		section = sectionSettings
	)
	default int currencyThreshold()
	{
		return 100000;
	}

	@ConfigItem(
		keyName = "cancelInstead",
		name = "Cancel instead of recalculating",
		description = "Whether the path should be cancelled rather than recalculated " +
			"when the recalculate distance limit is exceeded",
		position = 70,
		section = sectionSettings
	)
	default boolean cancelInstead()
	{
		return false;
	}

	@Range(
		min = -1,
		max = 20000
	)
	@ConfigItem(
		keyName = "recalculateDistance",
		name = "Recalculate distance",
		description = "Distance from the path the player should be for it to be recalculated (-1 for never)",
		position = 71,
		section = sectionSettings
	)
	default int recalculateDistance()
	{
		return 10;
	}

	@Range(
		min = -1,
		max = 50
	)
	@ConfigItem(
		keyName = "finishDistance",
		name = "Finish distance",
		description = "Distance from the target tile at which the path should be ended (-1 for never)",
		position = 72,
		section = sectionSettings
	)
	default int reachedDistance()
	{
		return 5;
	}

	@Range(
		max = 20000
	)
	@ConfigItem(
		keyName = "unreachableTargetDistanceThreshold",
		name = "Unreachable target distance",
		description = "Distance from the target at which a finished path is considered not to reach the target." +
			"<br>Useful for determining if a path is potentially invalid.",
		position = 73,
		section = sectionSettings
	)
	default int unreachableTargetDistance()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "showUnreachableText",
		name = "Show unreachable text",
		description = "Whether to display text on the player tile when the destination cannot be reached",
		position = 74,
		section = sectionSettings
	)
	default boolean showUnreachableText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTileCounter",
		name = "Show tile counter",
		description = "Whether to display the number of tiles travelled, number of tiles remaining or disable counting",
		position = 75,
		section = sectionSettings
	)
	default TileCounter showTileCounter()
	{
		return TileCounter.DISABLED;
	}

	@ConfigItem(
		keyName = "tileCounterStep",
		name = "Tile counter step",
		description = "The number of tiles between the displayed tile counter numbers",
		position = 76,
		section = sectionSettings
	)
	default int tileCounterStep()
	{
		return 1;
	}

	@Units(
		value = Units.TICKS
	)
	@Range(
		min = 1,
		max = 30
	)
	@ConfigItem(
		keyName = "calculationCutoff",
		name = "Calculation cutoff",
		description = "The cutoff threshold in number of ticks (0.6 seconds) of no progress being<br>" +
			"made towards the path target before the calculation will be stopped",
		position = 77,
		section = sectionSettings
	)
	default int calculationCutoff()
	{
		return 5;
	}

	@Range(
		min = 100,
		max = 300
	)
	@ConfigItem(
		keyName = "exactHeuristicWeight",
		name = "Exact heuristic weight",
		description = "Makes the exact backend search faster at the price of a suboptimal route.<br>" +
			"The route costs at most this percentage of the optimal route's cost:<br>" +
			"100 always finds the optimal route, 300 may find one up to 3x as costly.",
		position = 81,
		section = sectionSettings
	)
	default int exactHeuristicWeight()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "showTransportInfo",
		name = "Show transport info",
		description = "Whether to display transport destination hint info, e.g. which chat option and text to click",
		position = 82,
		section = sectionSettings
	)
	default boolean showTransportInfo()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBankPickupInfo",
		name = "Show transport hint at pickup",
		description = "When standing at a bank on the path, also show the transport hint for the next step requiring an item pickup",
		position = 83,
		section = sectionSettings
	)
	default boolean showBankPickupInfo()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightBankPickupItems",
		name = "Highlight bank pickup items",
		description = "Highlight items in the bank that need to be picked up for the current path",
		position = 136,
		section = sectionSettings
	)
	default boolean highlightBankPickupItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightSpellbookSpells",
		name = "Highlight spellbook spells",
		description = "Highlight spells in the spellbook that need to be cast for the current path step",
		position = 137,
		section = sectionSettings
	)
	default boolean highlightSpellbookSpells()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightInventoryItems",
		name = "Highlight inventory items",
		description = "Highlight items in the inventory and equipment that need to be used for the current path step",
		position = 138,
		section = sectionSettings
	)
	default boolean highlightInventoryItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "collisionAwareBlockedTargets",
		name = "Collision-aware blocked targets",
		description = "When the target tile cannot be stood on, limit the fallback goal tiles to tiles" +
			"<br>connected to it through the collision map (the same side of walls), instead of every" +
			"<br>walkable tile within the unreachable target distance",
		position = 139,
		section = sectionSettings
	)
	default boolean collisionAwareBlockedTargets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useSailingMoves",
		name = "Sail on boats",
		description = "While on a boat, find a path that sails the boat's 16 headings at its base speed<br>" +
			"and draw it in blue, instead of a walking path",
		position = 140,
		section = sectionSettings
	)
	default boolean useSailingMoves()
	{
		return true;
	}

	@ConfigSection(
		name = "Player-Owned House",
		description = "Options for POH (Player-Owned House) teleports",
		position = 141,
		closedByDefault = true
	)
	String sectionPoh = "sectionPoh";

	@ConfigItem(
		keyName = "usePoh",
		name = "Enable POH teleports",
		description = "Master toggle for all Player-Owned House (POH) teleports.<br>" +
			"When disabled, all POH transports are excluded regardless of individual settings below.",
		position = 142,
		section = sectionPoh
	)
	default boolean usePoh()
	{
		return false;
	}

	@ConfigItem(
		keyName = "usePohFairyRing",
		name = "POH fairy ring",
		description = "Whether to include the POH fairy ring in the path.<br>" +
			"Enable this if you have built a fairy ring in your house (85 Construction or boosted)",
		position = 143,
		section = sectionPoh
	)
	default boolean usePohFairyRing()
	{
		return false;
	}

	@ConfigItem(
		keyName = "usePohSpiritTree",
		name = "POH spirit tree",
		description = "Whether to include the POH spirit tree in the path.<br>" +
			"Enable this if you have built a spirit tree in your house (75 Construction, 83 Farming or boosted)",
		position = 144,
		section = sectionPoh
	)
	default boolean usePohSpiritTree()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useTeleportationPortalsPoh",
		name = "",
		description = "",
		hidden = true
	)
	default boolean useTeleportationPortalsPoh()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohNexusPortals",
		name = "POH Portal Nexus teleports",
		description = "Select the teleports available from your POH Portal Nexus",
		position = 146,
		section = sectionPoh
	)
	default Set<PohNexusPortal> pohNexusPortals()
	{
		return useTeleportationPortalsPoh()
			? EnumSet.allOf(PohNexusPortal.class)
			: EnumSet.noneOf(PohNexusPortal.class);
	}

	@ConfigItem(
		keyName = "pohJewelleryBoxTier",
		name = "POH jewellery box tier",
		description = "The tier of jewellery box built in your POH<br>" +
			"(Basic: 1-9, Fancy: A-J, Ornate: K-R). Set to None to disable jewellery box.",
		position = 147,
		section = sectionPoh
	)
	default JewelleryBoxTier pohJewelleryBoxTier()
	{
		return JewelleryBoxTier.ORNATE;
	}

	@ConfigItem(
		keyName = "pohMountedItems",
		name = "POH mounted items",
		description = "Select the mounted POH items available in your house",
		position = 148,
		section = sectionPoh
	)
	default Set<PohMountedItem> pohMountedItems()
	{
		return usePohMountedItems()
			? EnumSet.allOf(PohMountedItem.class)
			: EnumSet.noneOf(PohMountedItem.class);
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
		description = "Whether to include the POH wilderness obelisk in the path.<br>" +
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
		position = 153,
		section = sectionThresholds
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
		description = "How many extra tiles a teleportation portal must save<br>" +
			"to be preferred over walking or other transports",
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
		description = "How many extra tiles fetching a teleport item from your bank must save<br>" +
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
		description = "Options for displaying the path on the world map, minimap and scene tiles",
		position = 177
	)
	String sectionDisplay = "sectionDisplay";

	@ConfigItem(
		keyName = "drawMap",
		name = "Draw path on world map",
		description = "Whether the path should be drawn on the world map",
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
		description = "Whether the path should be drawn on the minimap",
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
		description = "Whether the path should be drawn on the game tiles",
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
		description = "Whether to display the path as tiles, a segmented line, or a line with direction arrows",
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
		description = "Animate a pulsing highlight on the tile when the next teleport on the path is due",
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
		description = "Colour of the path tiles on the world map, minimap and in the game scene",
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
		description = "Colour of the path tiles while the pathfinding calculation is in progress," +
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
		description = "Colour of the path tiles when pathfinding has finished but the target is still too far away",
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
		description = "Colour used to highlight bank items that need to be picked up for the current path",
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
		description = "Draw all transports on the map and game view.<br>White = available, Orange = unavailable (missing requirements)",
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
		description = "Whether the collision map should be drawn",
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
		description = "Whether to post the transports used in the current path as a PluginMessage event",
		position = 209,
		section = sectionDebug
	)
	default boolean postTransports()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unreachableText",
		name = "",
		description = "Text shown on the player tile when the destination cannot be reached",
		hidden = true
	)
	default String unreachableText()
	{
		return "Destination could not be reached";
	}

	@ConfigItem(
		keyName = "builtTeleportationBoxes",
		name = "",
		description = "ID=X Y Z;ID=X Y Z;ID=X Y Z",
		hidden = true
	)
	@SuppressWarnings("unused")
	default String builtTeleportationBoxes()
	{
		return "";
	}

	@ConfigItem(
		keyName = "builtTeleportationBoxes",
		name = "",
		description = "",
		hidden = true
	)
	@SuppressWarnings("unused")
	void setBuiltTeleportationBoxes(String content);

	@ConfigItem(
		keyName = "builtTeleportationPortalsPoh",
		name = "",
		description = "ID=X Y Z;ID=X Y Z;ID=X Y Z",
		hidden = true
	)
	@SuppressWarnings("unused")
	default String builtTeleportationPortalsPoh()
	{
		return "";
	}

	@ConfigItem(
		keyName = "builtTeleportationPortalsPoh",
		name = "",
		description = "",
		hidden = true
	)
	@SuppressWarnings("unused")
	void setBuiltTeleportationPortalsPoh(String content);

}
