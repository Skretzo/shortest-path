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
import shortestpath.transport.BalloonRoute;




@SuppressWarnings("SameReturnValue")
@ConfigGroup(ShortestPathPlugin.CONFIG_GROUP)
public interface ShortestPathConfig extends Config
{
	@ConfigSection(
		name = "Settings",
		description = "Options for the pathfinding. Transport, teleport-restriction, unlock and routing options live in the Shortest Path sidebar panel.",
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

	@ConfigItem(
		keyName = "pathfinderAlgorithm",
		name = "Pathfinder Algorithm",
		description = "Algorithm used for pathfinding",
		hidden = true
	)
	default PathfinderBackend pathfinderBackend()
	{
		return PathfinderBackend.LEGACY;
	}

	@ConfigItem(
		keyName = "avoidWilderness",
		name = "Avoid Wilderness",
		description = "Whether the wilderness should be avoided if possible<br>" +
			"(otherwise, will e.g. use wilderness lever from Edgeville to Ardougne)",
		hidden = true
	)
	default boolean avoidWilderness()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useAgilityShortcuts",
		name = "Use Agility Shortcuts",
		description = "Whether to include agility shortcuts in the path.<br>" +
			"You must also have the required agility level",
		hidden = true
	)
	default boolean useAgilityShortcuts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useGrappleShortcuts",
		name = "Use Grapple Shortcuts",
		description = "Whether to include crossbow grapple agility shortcuts in the path.<br>" +
			"You must also have the required agility, ranged and strength levels",
		hidden = true
	)
	default boolean useGrappleShortcuts()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useBoats",
		name = "Use Boats",
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
		name = "Use Canoes",
		description = "Whether to include canoes in the path",
		hidden = true
	)
	default boolean useCanoes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useCharterShips",
		name = "Use Charter Ships",
		description = "Whether to include charter ships in the path",
		hidden = true
	)
	default boolean useCharterShips()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useShips",
		name = "Use Ships",
		description = "Whether to include passenger ships in the path<br>" +
			"(e.g. the customs ships to Karamja)",
		hidden = true
	)
	default boolean useShips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useFairyRings",
		name = "Use Fairy Rings",
		description = "Whether to include fairy rings in the path.<br>" +
			"You must also have completed the required quests or miniquests",
		hidden = true
	)
	default boolean useFairyRings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useGnomeGliders",
		name = "Use Gnome Gliders",
		description = "Whether to include gnome gliders in the path",
		hidden = true
	)
	default boolean useGnomeGliders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useHotAirBalloons",
		name = "Use Hot Air Balloons",
		description = "Whether to include hot air balloons in the path",
		hidden = true
	)
	default boolean useHotAirBalloons()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useMagicCarpets",
		name = "Use Magic Carpets",
		description = "Whether to include magic carpets in the path",
		hidden = true
	)
	default boolean useMagicCarpets()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useMagicMushtrees",
		name = "Use Magic Mushtrees",
		description = "Whether to include Fossil Island Magic Mushtrees in the path<br>" +
			"(e.g. the Mycelium transport network from Verdant Valley to Mushroom Meadow)",
		hidden = true
	)
	default boolean useMagicMushtrees()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useMinecarts",
		name = "Use Minecarts",
		description = "Whether to include minecarts in the path<br>" +
			"(e.g. the Keldagrim and Lovakengj minecart networks)",
		hidden = true
	)
	default boolean useMinecarts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useQuetzals",
		name = "Use Quetzals",
		description = "Whether to include quetzals in the path",
		hidden = true
	)
	default boolean useQuetzals()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useSpiritTrees",
		name = "Use Spirit Trees",
		description = "Whether to include spirit trees in the path",
		hidden = true
	)
	default boolean useSpiritTrees()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationItems",
		name = "Use Teleportation Items",
		description = "Whether to include teleportation items from the player's inventory and equipment.<br>" +
			"Options labelled (perm) only use permanent non-charge items.<br>" +
			"The All options do not check skill, quest or item requirements.",
		hidden = true
	)
	default TeleportationItem useTeleportationItems()
	{
		return TeleportationItem.INVENTORY_NON_CONSUMABLE;
	}

	@ConfigItem(
		keyName = "useTeleportationLevers",
		name = "Use Teleportation Levers",
		description = "Whether to include teleportation levers in the path<br>" +
			"(e.g. the lever from Edgeville to Wilderness)",
		hidden = true
	)
	default boolean useTeleportationLevers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationPortals",
		name = "Use Teleportation Portals",
		description = "Whether to include teleportation portals in the path<br>" +
			"(e.g. the portal from Ferox Enclave to Castle Wars)",
		hidden = true
	)
	default boolean useTeleportationPortals()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationSpells",
		name = "Use Teleportation Spells",
		description = "Whether to include teleportation spells in the path",
		hidden = true
	)
	default boolean useTeleportationSpells()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationSpellsHome",
		name = "Use Home Teleport Spells",
		description = "Whether to include Home Teleport spells in the path",
		hidden = true
	)
	default boolean useTeleportationSpellsHome()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useTeleportationMinigames",
		name = "Use Teleportation To Minigames",
		description = "Whether to include teleportation to minigames/activities/grouping in the path<br>" +
			"(e.g. the Nightmare Zone minigame teleport). These teleports share a 20 minute cooldown.",
		hidden = true
	)
	default boolean useTeleportationMinigames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useWildernessObelisks",
		name = "Use Wilderness Obelisks",
		description = "Whether to include wilderness obelisks in the path",
		hidden = true
	)
	default boolean useWildernessObelisks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useSeasonalTransports",
		name = "Use Seasonal Transports",
		description = "Whether to include seasonal transports like League teleports in the path",
		hidden = true
	)
	default boolean useSeasonalTransports()
	{
		return false;
	}

	@ConfigItem(
		keyName = "respawnPrifddinas",
		name = "Prifddinas Respawn Point",
		description = "Enable if your respawn point is Prifddinas.<br>" +
			"The game does not expose the Prifddinas respawn to the client,<br>" +
			"so the plugin cannot detect it automatically",
		hidden = true
	)
	default boolean respawnPrifddinas()
	{
		return false;
	}

	@ConfigItem(
		keyName = "unlockCanoeAxe",
		name = "Axe Stored At A Canoe Station",
		description = "Enable if you have stored an axe at a canoe station.<br>" +
			"The game does not expose the stored axe to the client,<br>" +
			"so the plugin cannot detect it automatically.<br>" +
			"Enabling this declares the unlock without verification",
		hidden = true
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
		hidden = true
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
		hidden = true
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
		hidden = true
	)
	default boolean unlockMorUlRek()
	{
		return false;
	}

	@ConfigItem(
		keyName = "includeBankPath",
		name = "Include Path To Bank",
		description = "Whether to include the path to the closest bank<br>" +
			"when suggesting teleports from the bank",
		hidden = true
	)
	default boolean includeBankPath()
	{
		return false;
	}

	@ConfigItem(
		keyName = "currencyThreshold",
		name = "Currency Threshold",
		description = "The maximum amount of currency to use on a single transportation method." +
			"<br>The currencies affected by the threshold are coins, trading sticks, ecto-tokens and warrior guild tokens.",
		hidden = true
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
		hidden = true
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
		name = "Recalculate Distance",
		description = "Distance from the path the player should be for it to be recalculated (-1 for never)",
		hidden = true
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
		name = "Finish Distance",
		description = "Distance from the target tile at which the path should be ended (-1 for never)",
		hidden = true
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
		name = "Unreachable Target Distance",
		description = "Distance from the target at which a finished path is considered not to reach the target." +
			"<br>Useful for determining if a path is potentially invalid.",
		hidden = true
	)
	default int unreachableTargetDistance()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "showUnreachableText",
		name = "Show Unreachable Text",
		description = "Whether to display text on the player tile when the destination cannot be reached",
		hidden = true
	)
	default boolean showUnreachableText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTileCounter",
		name = "Show Tile Counter",
		description = "Whether to display the number of tiles travelled, number of tiles remaining or disable counting",
		position = 76,
		section = sectionSettings
	)
	default TileCounter showTileCounter()
	{
		return TileCounter.DISABLED;
	}

	@ConfigItem(
		keyName = "tileCounterStep",
		name = "Tile Counter Step",
		description = "The number of tiles between the displayed tile counter numbers",
		position = 77,
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
		name = "Calculation Cutoff",
		description = "The cutoff threshold in number of ticks (0.6 seconds) of no progress being<br>" +
			"made towards the path target before the calculation will be stopped",
		position = 78,
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
		keyName = "aStarHeuristicWeight",
		name = "A* Heuristic Weight",
		description = "Makes the A* backend search faster at the price of a suboptimal route.<br>" +
			"The route costs at most this percentage of the optimal route's cost:<br>" +
			"100 always finds the optimal route, 300 may find one up to 3x as costly.",
		hidden = true
	)
	default int aStarHeuristicWeight()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "showTransportInfo",
		name = "Show Transport Info",
		description = "Whether to display transport destination hint info, e.g. which chat option and text to click",
		hidden = true
	)
	default boolean showTransportInfo()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBankPickupInfo",
		name = "Show Transport Hint At Pickup",
		description = "When standing at a bank on the path, also show the transport hint for the next step requiring an item pickup",
		hidden = true
	)
	default boolean showBankPickupInfo()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightBankPickupItems",
		name = "Highlight Bank Pickup Items",
		description = "Highlight items in the bank that need to be picked up for the current path",
		hidden = true
	)
	default boolean highlightBankPickupItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "filterBankPickupItems",
		name = "Path Items bank filter",
		description = "Add a Path Items button to the bank that shows only the items the current path needs." +
			"<br>The button will only appear while the path needs items from the bank",
		hidden = true
	)
	default boolean filterBankPickupItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightSpellbookSpells",
		name = "Highlight Spellbook Spells",
		description = "Highlight spells in the spellbook that need to be cast for the current path step",
		hidden = true
	)
	default boolean highlightSpellbookSpells()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightInventoryItems",
		name = "Highlight Inventory Items",
		description = "Highlight items in the inventory and equipment that need to be used for the current path step",
		hidden = true
	)
	default boolean highlightInventoryItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "collisionAwareBlockedTargets",
		name = "Collision-Aware Blocked Targets",
		description = "When the target tile cannot be stood on, limit the fallback goal tiles to tiles" +
			"<br>connected to it through the collision map (the same side of walls), instead of every" +
			"<br>walkable tile within the unreachable target distance",
		hidden = true
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
		hidden = true
	)
	default boolean useSailingMoves()
	{
		return true;
	}

	@ConfigItem(
		keyName = "usePoh",
		name = "Enable POH Teleports",
		description = "Master toggle for all Player-Owned House (POH) teleports.<br>" +
			"When disabled, all POH transports are excluded regardless of individual settings below.",
		hidden = true
	)
	default boolean usePoh()
	{
		return false;
	}

	@ConfigItem(
		keyName = "usePohFairyRing",
		name = "POH Fairy Ring",
		description = "Whether to include the POH fairy ring in the path.<br>" +
			"Enable this if you have built a fairy ring in your house (85 Construction or boosted)",
		hidden = true
	)
	default boolean usePohFairyRing()
	{
		return false;
	}

	@ConfigItem(
		keyName = "usePohSpiritTree",
		name = "POH Spirit Tree",
		description = "Whether to include the POH spirit tree in the path.<br>" +
			"Enable this if you have built a spirit tree in your house (75 Construction, 83 Farming or boosted)",
		hidden = true
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
		name = "POH Portal Nexus Teleports",
		description = "Select the teleports available from your POH Portal Nexus",
		hidden = true
	)
	default Set<PohNexusPortal> pohNexusPortals()
	{
		return useTeleportationPortalsPoh()
			? EnumSet.allOf(PohNexusPortal.class)
			: EnumSet.noneOf(PohNexusPortal.class);
	}

	@ConfigItem(
		keyName = "pohJewelleryBoxTier",
		name = "POH Jewellery Box Tier",
		description = "The tier of jewellery box built in your POH<br>" +
			"(Basic: 1-9, Fancy: A-J, Ornate: K-R). Set to None to disable jewellery box.",
		hidden = true
	)
	default JewelleryBoxTier pohJewelleryBoxTier()
	{
		return JewelleryBoxTier.ORNATE;
	}

	@ConfigItem(
		keyName = "pohMountedItems",
		name = "POH Mounted Items",
		description = "Select the mounted POH items available in your house",
		hidden = true
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
		name = "POH Wilderness Obelisk",
		description = "Whether to include the POH wilderness obelisk in the path.<br>" +
			"Enable this if you have built an obelisk in your house (80 Construction or boosted)",
		hidden = true
	)
	default boolean usePohObelisk()
	{
		return false;
	}

	@Range(
		max = 10000
	)
	@ConfigItem(
		keyName = "costAgilityShortcuts",
		name = "Agility Shortcut Threshold",
		description = "How many extra tiles an agility shortcut must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Grapple Shortcut Threshold",
		description = "How many extra tiles a grapple shortcut must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Boat Threshold",
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
		name = "Canoe Threshold",
		description = "How many extra tiles a canoe must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Charter Ship Threshold",
		description = "How many extra tiles a charter ship must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Ship Threshold",
		description = "How many extra tiles a passenger ship must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Fairy Ring Threshold",
		description = "How many extra tiles a fairy ring must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Gnome Glider Threshold",
		description = "How many extra tiles a gnome glider must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Hot Air Balloon Threshold",
		description = "How many extra tiles a hot air balloon must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Magic Carpets Threshold",
		description = "How many extra tiles a magic carpet must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Magic Mushtrees Threshold",
		description = "How many extra tiles a magic mushtree must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Minecart Threshold",
		description = "How many extra tiles a minecart must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Quetzal Threshold",
		description = "How many extra tiles a quetzal must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Quetzal Whistle Threshold",
		description = "How many extra tiles a quetzal whistle teleport must save<br>" +
			"to be preferred over using a landing site",
		hidden = true
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
		name = "Spirit Tree Threshold",
		description = "How many extra tiles a spirit tree must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Teleportation Item (Non-Consumable) Threshold",
		description = "How many extra tiles a non-consumable (permanent) teleportation item<br>" +
			"must save to be preferred over walking or other transports",
		hidden = true
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
		name = "Teleportation Item (Consumable) Threshold",
		description = "How many extra tiles a consumable (non-permanent) teleportation item<br>" +
			"must save to be preferred over walking or other transports",
		hidden = true
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
		name = "Teleportation Box Threshold",
		description = "How many extra tiles a teleportation box must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Teleportation Lever Threshold",
		description = "How many extra tiles a teleportation lever must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Teleportation Portal Threshold",
		description = "How many extra tiles a teleportation portal (including POH<br>" +
			"nexus portals) must save to be preferred over walking or<br>" +
			"other transports",
		hidden = true
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
		name = "Teleportation Spell Threshold",
		description = "How many extra tiles a teleportation spell must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Home Teleport Spell Threshold",
		description = "How many extra tiles a Home Teleport spell must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Teleportation To Minigame Threshold",
		description = "How many extra tiles a minigame teleport must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Wilderness Obelisk Threshold",
		description = "How many extra tiles a wilderness obelisk must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Seasonal Transport Threshold",
		description = "How many extra tiles a seasonal transport must save<br>" +
			"to be preferred over walking or other transports",
		hidden = true
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
		name = "Bank Visit Threshold",
		description = "How many extra tiles fetching a teleport item from your bank must save<br>" +
			"to be preferred over options already at hand (0 treats banking as free)",
		hidden = true
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
		name = "Draw Path On World Map",
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
		name = "Draw Path On Minimap",
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
		name = "Draw Path On Tiles",
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
		name = "Path Style",
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
		name = "Teleport Pulse",
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
		name = "Collision Map",
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
		name = "Teleport Pulse",
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
		name = "Bank Pickup Highlight",
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
		name = "Clear Current Path",
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
		name = "Draw Transports",
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
		name = "Draw Collision Map",
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
		name = "Show Debug Panel",
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
		name = "Show Click Points",
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
		name = "Post Transports",
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

	@ConfigItem(
		keyName = "blockedTeleportItems",
		name = "Blocked Teleport Items",
		description = "Internal: comma-separated restricted teleport item ids and per-item threshold overrides (id or id:tiles). Managed by the Shortest Path sidebar.",
		hidden = true
	)
	@SuppressWarnings("unused")
	default String blockedTeleportItems()
	{
		return "";
	}

	@ConfigItem(
		keyName = "unlockBalloonLogBasket",
		name = "Balloon Log Basket Stocked",
		description = "Enable if you have stored logs in the balloon transport baskets.<br>" +
			"The game does not expose the stored logs to the client,<br>" +
			"so the plugin cannot detect them automatically.<br>" +
			"Enabling this declares the unlock without verification",
		hidden = true
	)
	@SuppressWarnings("unused")
	default boolean unlockBalloonLogBasket()
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
	@ConfigItem(
		keyName = "balloonLogBasketRoutes",
		name = "Balloon Log Basket Routes",
		description = "The balloon routes whose destination basket is stocked with logs.<br>" +
			"The game does not expose the stored logs to the client,<br>" +
			"so the plugin cannot detect them automatically.<br>" +
			"Managed by the Shortest Path sidebar; defaults to every route<br>" +
			"when the legacy basket toggle is set, and to none otherwise",
		hidden = true
	)
	@SuppressWarnings("unused")
	default Set<BalloonRoute> balloonLogBasketRoutes()
	{
		return unlockBalloonLogBasket()
			? EnumSet.allOf(BalloonRoute.class)
			: EnumSet.noneOf(BalloonRoute.class);
	}

}
