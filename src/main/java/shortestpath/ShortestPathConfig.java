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
		position = 1,
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
		position = 2,
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
		position = 3,
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
		position = 4,
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
		position = 5,
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
		position = 6,
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
		position = 7,
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
		position = 8,
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
		position = 9,
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
		position = 10,
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
		position = 11,
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
		position = 12,
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
		position = 13,
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
		position = 14,
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
		position = 15,
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
		position = 16,
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
		position = 17,
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
		position = 18,
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
		position = 19,
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
		position = 20,
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
		position = 21,
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
		position = 22,
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
		position = 23,
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
		position = 24,
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
		position = 25,
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
		position = 26,
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
		position = 27,
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
		position = 28,
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
		position = 29,
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
		position = 30,
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
		position = 31,
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
		position = 32,
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
		position = 33,
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
		position = 34,
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
		position = 35,
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
		position = 36,
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
		position = 37,
		section = sectionSettings
	)
	default int calculationCutoff()
	{
		return 5;
	}

	@Range(
		min = 100,
		max = 1000
	)
	@ConfigItem(
		keyName = "exactHeuristicWeight",
		name = "Exact heuristic weight",
		description = "Heuristic weight used by the exact backend, as a percentage.<br>" +
			"100 preserves exact paths; higher values trade path quality for speed.",
		position = 41,
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
		position = 38,
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
		position = 39,
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
		position = 92,
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
		position = 93,
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
		position = 94,
		section = sectionSettings
	)
	default boolean highlightInventoryItems()
	{
		return true;
	}

	@ConfigSection(
		name = "Player-Owned House",
		description = "Options for POH (Player-Owned House) teleports",
		position = 96,
		closedByDefault = true
	)
	String sectionPoh = "sectionPoh";

	@ConfigItem(
		keyName = "usePoh",
		name = "Enable POH teleports",
		description = "Master toggle for all Player-Owned House (POH) teleports.<br>" +
			"When disabled, all POH transports are excluded regardless of individual settings below.",
		position = 97,
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
		position = 98,
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
		position = 99,
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
		position = 101,
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
		position = 102,
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
		position = 103,
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
		position = 104,
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
		position = 105,
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
		position = 106,
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
		position = 107,
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
		position = 108,
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
		position = 109,
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
		position = 110,
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
		position = 111,
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
		position = 112,
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
		position = 113,
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
		position = 114,
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
		position = 115,
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
		position = 116,
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
		position = 117,
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
		position = 118,
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
		position = 119,
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
		position = 120,
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
		position = 121,
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
		position = 122,
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
		position = 123,
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
		position = 124,
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
		position = 125,
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
		position = 126,
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
		position = 127,
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
		position = 128,
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
		position = 129,
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
		position = 130,
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
		position = 131,
		section = sectionThresholds
	)
	default int costBankVisit()
	{
		return 0;
	}

	@ConfigSection(
		name = "Display",
		description = "Options for displaying the path on the world map, minimap and scene tiles",
		position = 132
	)
	String sectionDisplay = "sectionDisplay";

	@ConfigItem(
		keyName = "drawMap",
		name = "Draw path on world map",
		description = "Whether the path should be drawn on the world map",
		position = 133,
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
		position = 134,
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
		position = 135,
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
		position = 136,
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
		position = 137,
		section = sectionDisplay
	)
	default boolean showTeleportPulse()
	{
		return false;
	}

	@ConfigSection(
		name = "Colours",
		description = "Colours for the path map, minimap and scene tiles",
		position = 138
	)
	String sectionColours = "sectionColours";

	@Alpha
	@ConfigItem(
		keyName = "colourPath",
		name = "Path",
		description = "Colour of the path tiles on the world map, minimap and in the game scene",
		position = 139,
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
		position = 140,
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
		position = 141,
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
		position = 142,
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
		position = 143,
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
		position = 144,
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
		position = 145,
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
		position = 155,
		section = sectionColours
	)
	default Color colourBankPickupHighlight()
	{
		return new Color(0, 255, 255, 255);
	}

	@ConfigSection(
		name = "Hotkeys",
		description = "Options for keyboard shortcuts",
		position = 156
	)
	String sectionHotkeys = "sectionHotkeys";

	@ConfigItem(
		keyName = "clearPathHotkey",
		name = "Clear current path",
		description = "Hotkey to clear the current path",
		position = 157,
		section = sectionHotkeys
	)
	default Keybind clearPathHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigSection(
		name = "Debug Options",
		description = "Various options for debugging",
		position = 158,
		closedByDefault = true
	)
	String sectionDebug = "sectionDebug";

	@ConfigItem(
		keyName = "drawTransports",
		name = "Draw transports",
		description = "Draw all transports on the map and game view.<br>White = available, Orange = unavailable (missing requirements)",
		position = 159,
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
		position = 160,
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
		position = 161,
		section = sectionDebug
	)
	default boolean drawDebugPanel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "postTransports",
		name = "Post transports",
		description = "Whether to post the transports used in the current path as a PluginMessage event",
		position = 162,
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
