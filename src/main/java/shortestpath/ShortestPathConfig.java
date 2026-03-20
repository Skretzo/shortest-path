package shortestpath;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(ShortestPathPlugin.CONFIG_GROUP)
public interface ShortestPathConfig extends Config {
    @ConfigSection(
        name = "Settings",
        description = "Options for the pathfinding",
        position = 0
    )
    String sectionSettings = "sectionSettings";

    @ConfigItem(
        keyName = "avoidWilderness",
        name = "Avoid wilderness",
        description = "Whether the wilderness should be avoided if possible<br>" +
            "(otherwise, will e.g. use wilderness lever from Edgeville to Ardougne)",
        position = 1,
        section = sectionSettings
    )
    default boolean avoidWilderness() {
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
    default boolean useAgilityShortcuts() {
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
    default boolean useGrappleShortcuts() {
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
    default boolean useBoats() {
        return true;
    }

    @ConfigItem(
        keyName = "useCanoes",
        name = "Use canoes",
        description = "Whether to include canoes in the path",
        position = 5,
        section = sectionSettings
    )
    default boolean useCanoes() {
        return false;
    }

    @ConfigItem(
        keyName = "useCharterShips",
        name = "Use charter ships",
        description = "Whether to include charter ships in the path",
        position = 6,
        section = sectionSettings
    )
    default boolean useCharterShips() {
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
    default boolean useShips() {
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
    default boolean useFairyRings() {
        return true;
    }

    @ConfigItem(
        keyName = "useGnomeGliders",
        name = "Use gnome gliders",
        description = "Whether to include gnome gliders in the path",
        position = 9,
        section = sectionSettings
    )
    default boolean useGnomeGliders() {
        return true;
    }

    @ConfigItem(
        keyName = "useHotAirBalloons",
        name = "Use hot air balloons",
        description = "Whether to include hot air balloons in the path",
        position = 10,
        section = sectionSettings
    )
    default boolean useHotAirBalloons() {
        return false;
    }

    @ConfigItem(
        keyName = "useMagicCarpets",
        name = "Use magic carpets",
        description = "Whether to include magic carpets in the path",
        position = 11,
        section = sectionSettings
    )
    default boolean useMagicCarpets() {
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
    default boolean useMagicMushtrees() {
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
    default boolean useMinecarts() {
        return true;
    }

    @ConfigItem(
        keyName = "useQuetzals",
        name = "Use quetzals",
        description = "Whether to include quetzals in the path",
        position = 14,
        section = sectionSettings
    )
    default boolean useQuetzals() {
        return true;
    }

    @ConfigItem(
        keyName = "useSpiritTrees",
        name = "Use spirit trees",
        description = "Whether to include spirit trees in the path",
        position = 15,
        section = sectionSettings
    )
    default boolean useSpiritTrees() {
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
    default TeleportationItem useTeleportationItems() {
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
    default boolean useTeleportationLevers() {
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
    default boolean useTeleportationPortals() {
        return true;
    }

    @ConfigItem(
        keyName = "useTeleportationSpells",
        name = "Use teleportation spells",
        description = "Whether to include teleportation spells in the path",
        position = 19,
        section = sectionSettings
    )
    default boolean useTeleportationSpells() {
        return true;
    }

    @ConfigItem(
        keyName = "useTeleportationMinigames",
        name = "Use teleportation to minigames",
        description = "Whether to include teleportation to minigames/activities/grouping in the path<br>" +
            "(e.g. the Nightmare Zone minigame teleport). These teleports share a 20 minute cooldown.",
        position = 20,
        section = sectionSettings
    )
    default boolean useTeleportationMinigames() {
        return true;
    }

    @ConfigItem(
        keyName = "useWildernessObelisks",
        name = "Use wilderness obelisks",
        description = "Whether to include wilderness obelisks in the path",
        position = 21,
        section = sectionSettings
    )
    default boolean useWildernessObelisks() {
        return true;
    }

    @ConfigItem(
        keyName = "useSeasonalTransports",
        name = "Use seasonal transports",
        description = "Whether to include seasonal transports like League teleports in the path",
        position = 22,
        section = sectionSettings
    )
    default boolean useSeasonalTransports() {
        return false;
    }

    @ConfigItem(
        keyName = "includeBankPath",
        name = "Include path to bank",
        description = "Whether to include the path to the closest bank<br>" +
            "when suggesting teleports from the bank",
        position = 23,
        section = sectionSettings
    )
    default boolean includeBankPath() {
        return false;
    }

    @ConfigItem(
        keyName = "currencyThreshold",
        name = "Currency threshold",
        description = "The maximum amount of currency to use on a single transportation method." +
            "<br>The currencies affected by the threshold are coins, trading sticks, ecto-tokens and warrior guild tokens.",
        position = 24,
        section = sectionSettings
    )
    default int currencyThreshold() {
        return 100000;
    }

    @ConfigItem(
        keyName = "cancelInstead",
        name = "Cancel instead of recalculating",
        description = "Whether the path should be cancelled rather than recalculated " +
            "when the recalculate distance limit is exceeded",
        position = 25,
        section = sectionSettings
    )
    default boolean cancelInstead() {
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
        position = 26,
        section = sectionSettings
    )
    default int recalculateDistance() {
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
        position = 27,
        section = sectionSettings
    )
    default int reachedDistance() {
        return 5;
    }

    @ConfigItem(
        keyName = "showTileCounter",
        name = "Show tile counter",
        description = "Whether to display the number of tiles travelled, number of tiles remaining or disable counting",
        position = 28,
        section = sectionSettings
    )
    default TileCounter showTileCounter() {
        return TileCounter.DISABLED;
    }

    @ConfigItem(
        keyName = "tileCounterStep",
        name = "Tile counter step",
        description = "The number of tiles between the displayed tile counter numbers",
        position = 29,
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
        position = 30,
        section = sectionSettings
    )
    default int calculationCutoff()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "showTransportInfo",
        name = "Show transport info",
        description = "Whether to display transport destination hint info, e.g. which chat option and text to click",
        position = 31,
        section = sectionSettings
    )
    default boolean showTransportInfo() {
        return true;
    }

    @ConfigSection(
        name = "Player-Owned House",
        description = "Options for POH (Player-Owned House) teleports",
        position = 32,
        closedByDefault = true
    )
    String sectionPoh = "sectionPoh";

    @ConfigItem(
        keyName = "usePoh",
        name = "Enable POH teleports",
        description = "Master toggle for all Player-Owned House (POH) teleports.<br>" +
            "When disabled, all POH transports are excluded regardless of individual settings below.",
        position = 33,
        section = sectionPoh
    )
    default boolean usePoh() {
        return false;
    }

    @ConfigItem(
        keyName = "usePohFairyRing",
        name = "POH fairy ring",
        description = "Whether to include the POH fairy ring in the path.<br>" +
            "Enable this if you have built a fairy ring in your house (85 Construction or boosted)",
        position = 34,
        section = sectionPoh
    )
    default boolean usePohFairyRing() {
        return false;
    }

    @ConfigItem(
        keyName = "usePohSpiritTree",
        name = "POH spirit tree",
        description = "Whether to include the POH spirit tree in the path.<br>" +
            "Enable this if you have built a spirit tree in your house (75 Construction, 83 Farming or boosted)",
        position = 35,
        section = sectionPoh
    )
    default boolean usePohSpiritTree() {
        return false;
    }

    @ConfigItem(
        keyName = "useTeleportationPortalsPoh",
        name = "POH portal nexus",
        description = "Whether to include POH teleportation portals/nexus in the path",
        position = 36,
        section = sectionPoh
    )
    default boolean useTeleportationPortalsPoh() {
        return false;
    }

    @ConfigSection(
        name = "POH Portal Nexus Teleports",
        description = "Toggle individual portals in your POH portal nexus.<br>" +
            "Disable portals you have not unlocked in your nexus.",
        position = 37,
        closedByDefault = true
    )
    String sectionPohNexusPortals = "sectionPohNexusPortals";

    @ConfigItem(
        keyName = "usePohNexusAnnakarl",
        name = "Annakarl",
        description = "Whether to include the Annakarl portal from the POH portal nexus",
        position = 38,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusAnnakarl() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusArceuusLibrary",
        name = "Arceuus Library",
        description = "Whether to include the Arceuus Library portal from the POH portal nexus",
        position = 39,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusArceuusLibrary() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusArdougne",
        name = "Ardougne",
        description = "Whether to include the Ardougne portal from the POH portal nexus",
        position = 40,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusArdougne() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusBarbarianOutpost",
        name = "Barbarian Outpost",
        description = "Whether to include the Barbarian Outpost portal from the POH portal nexus",
        position = 41,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusBarbarianOutpost() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusBarrows",
        name = "Barrows",
        description = "Whether to include the Barrows portal from the POH portal nexus",
        position = 42,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusBarrows() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusBattlefront",
        name = "Battlefront",
        description = "Whether to include the Battlefront portal from the POH portal nexus",
        position = 43,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusBattlefront() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusCamelot",
        name = "Camelot / Seers' Village",
        description = "Whether to include the Camelot/Seers' Village portal from the POH portal nexus.<br>" +
            "This portal redirects to Camelot or Seers' Village depending on your construction setting.",
        position = 44,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusCamelot() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusCarrallanger",
        name = "Carrallanger",
        description = "Whether to include the Carrallanger portal from the POH portal nexus",
        position = 45,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusCarrallanger() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusCatherby",
        name = "Catherby",
        description = "Whether to include the Catherby portal from the POH portal nexus",
        position = 46,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusCatherby() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusCemetery",
        name = "Cemetery",
        description = "Whether to include the Cemetery portal from the POH portal nexus",
        position = 47,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusCemetery() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusCivitasIllaFortis",
        name = "Civitas illa Fortis",
        description = "Whether to include the Civitas illa Fortis portal from the POH portal nexus",
        position = 48,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusCivitasIllaFortis() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusDareeyak",
        name = "Dareeyak",
        description = "Whether to include the Dareeyak portal from the POH portal nexus",
        position = 49,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusDareeyak() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusDraynorManor",
        name = "Draynor Manor",
        description = "Whether to include the Draynor Manor portal from the POH portal nexus",
        position = 50,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusDraynorManor() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusFalador",
        name = "Falador",
        description = "Whether to include the Falador portal from the POH portal nexus",
        position = 51,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusFalador() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusFenkenstrainsCastle",
        name = "Fenkenstrain's Castle",
        description = "Whether to include the Fenkenstrain's Castle portal from the POH portal nexus",
        position = 52,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusFenkenstrainsCastle() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusFishingGuild",
        name = "Fishing Guild",
        description = "Whether to include the Fishing Guild portal from the POH portal nexus",
        position = 53,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusFishingGuild() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusGhorrock",
        name = "Ghorrock",
        description = "Whether to include the Ghorrock portal from the POH portal nexus",
        position = 54,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusGhorrock() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusHarmonyIsland",
        name = "Harmony Island",
        description = "Whether to include the Harmony Island portal from the POH portal nexus",
        position = 55,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusHarmonyIsland() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusIcePlateau",
        name = "Ice Plateau",
        description = "Whether to include the Ice Plateau portal from the POH portal nexus",
        position = 56,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusIcePlateau() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusKharyrll",
        name = "Kharyrll",
        description = "Whether to include the Kharyrll portal from the POH portal nexus",
        position = 57,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusKharyrll() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusKourend",
        name = "Kourend",
        description = "Whether to include the Kourend portal from the POH portal nexus",
        position = 58,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusKourend() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusLassar",
        name = "Lassar",
        description = "Whether to include the Lassar portal from the POH portal nexus",
        position = 59,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusLassar() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusLumbridge",
        name = "Lumbridge",
        description = "Whether to include the Lumbridge portal from the POH portal nexus",
        position = 60,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusLumbridge() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusLunarIsle",
        name = "Lunar Isle",
        description = "Whether to include the Lunar Isle portal from the POH portal nexus",
        position = 61,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusLunarIsle() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusMarim",
        name = "Marim",
        description = "Whether to include the Marim portal from the POH portal nexus",
        position = 62,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusMarim() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusMindAltar",
        name = "Mind Altar",
        description = "Whether to include the Mind Altar portal from the POH portal nexus",
        position = 63,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusMindAltar() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusOurania",
        name = "Ourania",
        description = "Whether to include the Ourania portal from the POH portal nexus",
        position = 64,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusOurania() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusPaddewwa",
        name = "Paddewwa",
        description = "Whether to include the Paddewwa portal from the POH portal nexus",
        position = 65,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusPaddewwa() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusPortKhazard",
        name = "Port Khazard",
        description = "Whether to include the Port Khazard portal from the POH portal nexus",
        position = 66,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusPortKhazard() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusRespawn",
        name = "Respawn",
        description = "Whether to include the Respawn portal from the POH portal nexus.<br>" +
            "This portal sends you to your current respawn point.",
        position = 67,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusRespawn() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusSalveGraveyard",
        name = "Salve Graveyard",
        description = "Whether to include the Salve Graveyard portal from the POH portal nexus",
        position = 68,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusSalveGraveyard() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusSenntisten",
        name = "Senntisten",
        description = "Whether to include the Senntisten portal from the POH portal nexus",
        position = 69,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusSenntisten() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusTrollheim",
        name = "Trollheim",
        description = "Whether to include the Trollheim portal from the POH portal nexus",
        position = 70,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusTrollheim() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusTrollStronghold",
        name = "Troll Stronghold",
        description = "Whether to include the Troll Stronghold portal from the POH portal nexus",
        position = 71,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusTrollStronghold() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusVarrock",
        name = "Varrock / Grand Exchange",
        description = "Whether to include the Varrock/Grand Exchange portal from the POH portal nexus.<br>" +
            "This portal redirects to Varrock or the Grand Exchange depending on your construction setting.",
        position = 72,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusVarrock() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusWaterbirthIsland",
        name = "Waterbirth Island",
        description = "Whether to include the Waterbirth Island portal from the POH portal nexus",
        position = 73,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusWaterbirthIsland() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusWatchtower",
        name = "Watchtower / Yanille",
        description = "Whether to include the Watchtower/Yanille portal from the POH portal nexus.<br>" +
            "This portal redirects to Watchtower or Yanille depending on your construction setting.",
        position = 74,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusWatchtower() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusWeiss",
        name = "Weiss",
        description = "Whether to include the Weiss portal from the POH portal nexus",
        position = 75,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusWeiss() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohNexusWestArdougne",
        name = "West Ardougne",
        description = "Whether to include the West Ardougne portal from the POH portal nexus",
        position = 76,
        section = sectionPohNexusPortals
    )
    default boolean usePohNexusWestArdougne() {
        return true;
    }

    @ConfigItem(
        keyName = "pohJewelleryBoxTier",
        name = "POH jewellery box tier",
        description = "The tier of jewellery box built in your POH<br>" +
            "(Basic: 1-9, Fancy: A-J, Ornate: K-R). Set to None to disable jewellery box.",
        position = 77,
        section = sectionPoh
    )
    default JewelleryBoxTier pohJewelleryBoxTier() {
        return JewelleryBoxTier.ORNATE;
    }

    @ConfigItem(
        keyName = "usePohMountedItems",
        name = "POH mounted items",
        description = "Whether to include POH mounted items in the path<br>" +
            "(e.g. mounted glory, Xeric's talisman, digsite pendant, mythical cape)",
        position = 78,
        section = sectionPoh
    )
    default boolean usePohMountedItems() {
        return true;
    }

    @ConfigItem(
        keyName = "usePohObelisk",
        name = "POH wilderness obelisk",
        description = "Whether to include the POH wilderness obelisk in the path.<br>" +
            "Enable this if you have built an obelisk in your house (80 Construction or boosted)",
        position = 79,
        section = sectionPoh
    )
    default boolean usePohObelisk() {
        return false;
    }

    @ConfigSection(
        name = "Transport Thresholds",
        description = "Set customizable thresholds for how much faster a transportation<br>"+
            "method must be to be preferred over other methods",
        position = 80,
        closedByDefault = true
    )
    String sectionThresholds = "sectionThresholds";

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costAgilityShortcuts",
        name = "Agility shortcut threshold",
        description = "How many extra tiles an agility shortcut must save<br>" +
            "to be preferred over walking or other transports",
        position = 81,
        section = sectionThresholds
    )
    default int costAgilityShortcuts() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costGrappleShortcuts",
        name = "Grapple shortcut threshold",
        description = "How many extra tiles a grapple shortcut must save<br>" +
            "to be preferred over walking or other transports",
        position = 82,
        section = sectionThresholds
    )
    default int costGrappleShortcuts() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costBoats",
        name = "Boat threshold",
        description = "How many extra tiles a small boat must save<br>" +
            "to be preferred over walking or other transports",
        position = 83,
        section = sectionThresholds
    )
    default int costBoats() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costCanoes",
        name = "Canoe threshold",
        description = "How many extra tiles a canoe must save<br>" +
            "to be preferred over walking or other transports",
        position = 84,
        section = sectionThresholds
    )
    default int costCanoes() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costCharterShips",
        name = "Charter ship threshold",
        description = "How many extra tiles a charter ship must save<br>" +
            "to be preferred over walking or other transports",
        position = 85,
        section = sectionThresholds
    )
    default int costCharterShips() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costShips",
        name = "Ship threshold",
        description = "How many extra tiles a passenger ship must save<br>" +
            "to be preferred over walking or other transports",
        position = 86,
        section = sectionThresholds
    )
    default int costShips() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costFairyRings",
        name = "Fairy ring threshold",
        description = "How many extra tiles a fairy ring must save<br>" +
            "to be preferred over walking or other transports",
        position = 87,
        section = sectionThresholds
    )
    default int costFairyRings() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costGnomeGliders",
        name = "Gnome glider threshold",
        description = "How many extra tiles a gnome glider must save<br>" +
            "to be preferred over walking or other transports",
        position = 88,
        section = sectionThresholds
    )
    default int costGnomeGliders() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costHotAirBalloons",
        name = "Hot air balloon threshold",
        description = "How many extra tiles a hot air balloon must save<br>" +
            "to be preferred over walking or other transports",
        position = 89,
        section = sectionThresholds
    )
    default int costHotAirBalloons() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costMagicCarpets",
        name = "Magic carpets threshold",
        description = "How many extra tiles a magic carpet must save<br>" +
            "to be preferred over walking or other transports",
        position = 90,
        section = sectionThresholds
    )
    default int costMagicCarpets() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costMagicMushtrees",
        name = "Magic mushtrees threshold",
        description = "How many extra tiles a magic mushtree must save<br>" +
            "to be preferred over walking or other transports",
        position = 91,
        section = sectionThresholds
    )
    default int costMagicMushtrees() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costMinecarts",
        name = "Minecart threshold",
        description = "How many extra tiles a minecart must save<br>" +
            "to be preferred over walking or other transports",
        position = 92,
        section = sectionThresholds
    )
    default int costMinecarts() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costQuetzals",
        name = "Quetzal threshold",
        description = "How many extra tiles a quetzal must save<br>" +
            "to be preferred over walking or other transports",
        position = 93,
        section = sectionThresholds
    )
    default int costQuetzals() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costSpiritTrees",
        name = "Spirit tree threshold",
        description = "How many extra tiles a spirit tree must save<br>" +
            "to be preferred over walking or other transports",
        position = 94,
        section = sectionThresholds
    )
    default int costSpiritTrees() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costNonConsumableTeleportationItems",
        name = "Teleportation item (non-consumable) threshold",
        description = "How many extra tiles a non-consumable (permanent) teleportation item<br>" +
            "must save to be preferred over walking or other transports",
        position = 95,
        section = sectionThresholds
    )
    default int costNonConsumableTeleportationItems() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costConsumableTeleportationItems",
        name = "Teleportation item (consumable) threshold",
        description = "How many extra tiles a consumable (non-permanent) teleportation item<br>" +
            "must save to be preferred over walking or other transports",
        position = 96,
        section = sectionThresholds
    )
    default int costConsumableTeleportationItems() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costTeleportationBoxes",
        name = "Teleportation box threshold",
        description = "How many extra tiles a teleportation box must save<br>" +
            "to be preferred over walking or other transports",
        position = 97,
        section = sectionThresholds
    )
    default int costTeleportationBoxes() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costTeleportationLevers",
        name = "Teleportation lever threshold",
        description = "How many extra tiles a teleportation lever must save<br>" +
            "to be preferred over walking or other transports",
        position = 98,
        section = sectionThresholds
    )
    default int costTeleportationLevers() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costTeleportationPortals",
        name = "Teleportation portal threshold",
        description = "How many extra tiles a teleportation portal must save<br>" +
            "to be preferred over walking or other transports",
        position = 99,
        section = sectionThresholds
    )
    default int costTeleportationPortals() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costTeleportationSpells",
        name = "Teleportation spell threshold",
        description = "How many extra tiles a teleportation spell must save<br>" +
            "to be preferred over walking or other transports",
        position = 100,
        section = sectionThresholds
    )
    default int costTeleportationSpells() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costTeleportationMinigames",
        name = "Teleportation to minigame threshold",
        description = "How many extra tiles a minigame teleport must save<br>" +
            "to be preferred over walking or other transports",
        position = 101,
        section = sectionThresholds
    )
    default int costTeleportationMinigames() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costWildernessObelisks",
        name = "Wilderness obelisk threshold",
        description = "How many extra tiles a wilderness obelisk must save<br>" +
            "to be preferred over walking or other transports",
        position = 102,
        section = sectionThresholds
    )
    default int costWildernessObelisks() {
        return 0;
    }

    @Range(
        min = 0,
        max = 10000
    )
    @ConfigItem(
        keyName = "costSeasonalTransports",
        name = "Seasonal transport threshold",
        description = "How many extra tiles a seasonal transport must save<br>" +
            "to be preferred over walking or other transports",
        position = 103,
        section = sectionThresholds
    )
    default int costSeasonalTransports() {
        return 0;
    }

    @ConfigSection(
        name = "Display",
        description = "Options for displaying the path on the world map, minimap and scene tiles",
        position = 104
    )
    String sectionDisplay = "sectionDisplay";

    @ConfigItem(
        keyName = "drawMap",
        name = "Draw path on world map",
        description = "Whether the path should be drawn on the world map",
        position = 105,
        section = sectionDisplay
    )
    default boolean drawMap() {
        return true;
    }

    @ConfigItem(
        keyName = "drawMinimap",
        name = "Draw path on minimap",
        description = "Whether the path should be drawn on the minimap",
        position = 106,
        section = sectionDisplay
    )
    default boolean drawMinimap() {
        return true;
    }

    @ConfigItem(
        keyName = "drawTiles",
        name = "Draw path on tiles",
        description = "Whether the path should be drawn on the game tiles",
        position = 107,
        section = sectionDisplay
    )
    default boolean drawTiles() {
        return true;
    }

    @ConfigItem(
        keyName = "pathStyle",
        name = "Path style",
        description = "Whether to display the path as tiles or a segmented line",
        position = 108,
        section = sectionDisplay
    )
    default TileStyle pathStyle() {
        return TileStyle.TILES;
    }

    @ConfigSection(
        name = "Colours",
        description = "Colours for the path map, minimap and scene tiles",
        position = 109
    )
    String sectionColours = "sectionColours";

    @Alpha
    @ConfigItem(
        keyName = "colourPath",
        name = "Path",
        description = "Colour of the path tiles on the world map, minimap and in the game scene",
        position = 110,
        section = sectionColours
    )
    default Color colourPath() {
        return new Color(255, 0, 0);
    }

    @Alpha
    @ConfigItem(
        keyName = "colourPathCalculating",
        name = "Calculating",
        description = "Colour of the path tiles while the pathfinding calculation is in progress," +
            "<br>and the colour of unused targets if there are more than a single target",
        position = 111,
        section = sectionColours
    )
    default Color colourPathCalculating() {
        return new Color(0, 0, 255);
    }

    @Alpha
    @ConfigItem(
        keyName = "colourTransports",
        name = "Transports",
        description = "Colour of the transport tiles",
        position = 112,
        section = sectionColours
    )
    default Color colourTransports() {
        return new Color(0, 255, 0, 128);
    }

    @Alpha
    @ConfigItem(
        keyName = "colourCollisionMap",
        name = "Collision map",
        description = "Colour of the collision map tiles",
        position = 113,
        section = sectionColours
    )
    default Color colourCollisionMap() {
        return new Color(0, 128, 255, 128);
    }

    @Alpha
    @ConfigItem(
        keyName = "colourText",
        name = "Text",
        description = "Colour of the text of the tile counter and fairy ring codes",
        position = 114,
        section = sectionColours
    )
    default Color colourText() {
        return Color.WHITE;
    }

    @ConfigSection(
        name = "Debug Options",
        description = "Various options for debugging",
        position = 115,
        closedByDefault = true
    )
    String sectionDebug = "sectionDebug";

    @ConfigItem(
        keyName = "drawTransports",
        name = "Draw transports",
        description = "Whether transports should be drawn",
        position = 116,
        section = sectionDebug
    )
    default boolean drawTransports() {
        return false;
    }

    @ConfigItem(
        keyName = "drawCollisionMap",
        name = "Draw collision map",
        description = "Whether the collision map should be drawn",
        position = 117,
        section = sectionDebug
    )
    default boolean drawCollisionMap() {
        return false;
    }

    @ConfigItem(
        keyName = "drawDebugPanel",
        name = "Show debug panel",
        description = "Toggles displaying the pathfinding debug stats panel",
        position = 118,
        section = sectionDebug
    )
    default boolean drawDebugPanel() {
        return false;
    }

    @ConfigItem(
        keyName = "postTransports",
        name = "Post transports",
        description = "Whether to post the transports used in the current path as a PluginMessage event",
        position = 119,
        section = sectionDebug
    )
    default boolean postTransports() {
        return false;
    }

    @ConfigItem(
        keyName = "builtTeleportationBoxes",
        name = "",
        description = "ID=X Y Z;ID=X Y Z;ID=X Y Z",
        hidden = true
    )
    default String builtTeleportationBoxes() {
        return "";
    }

    @ConfigItem(
        keyName = "builtTeleportationBoxes",
        name = "",
        description = "",
        hidden = true
    )
    void setBuiltTeleportationBoxes(String content);

    @ConfigItem(
        keyName = "builtTeleportationPortalsPoh",
        name = "",
        description = "ID=X Y Z;ID=X Y Z;ID=X Y Z",
        hidden = true
    )
    default String builtTeleportationPortalsPoh() {
        return "";
    }

    @ConfigItem(
        keyName = "builtTeleportationPortalsPoh",
        name = "",
        description = "",
        hidden = true
    )
    void setBuiltTeleportationPortalsPoh(String content);
}
