package shortestpath.transport;

import lombok.Getter;
import net.runelite.api.Skill;

@Getter
public enum TransportType {
    TRANSPORT("/transports/transports.tsv", null, null),
    AGILITY_SHORTCUT("/transports/agility_shortcuts.tsv", "useAgilityShortcuts", "costAgilityShortcuts") {
        @Override
        public TransportType refine(int[] skillLevels) {
            if (skillLevels[Skill.RANGED.ordinal()] > 1 || skillLevels[Skill.STRENGTH.ordinal()] > 1) {
                return GRAPPLE_SHORTCUT;
            }
            return this;
        }
    },
    GRAPPLE_SHORTCUT(null, "useGrappleShortcuts", "costGrappleShortcuts"),
    BOAT("/transports/boats.tsv", "useBoats", "costBoats"),
    CANOE("/transports/canoes.tsv", "useCanoes", "costCanoes"),
    CHARTER_SHIP("/transports/charter_ships.tsv", "useCharterShips", "costCharterShips"),
    SHIP("/transports/ships.tsv", "useShips", "costShips"),
    FAIRY_RING("/transports/fairy_rings.tsv", "useFairyRings", "costFairyRings", 6),
    GNOME_GLIDER("/transports/gnome_gliders.tsv", "useGnomeGliders", "costGnomeGliders", 6),
    HOT_AIR_BALLOON("/transports/hot_air_balloons.tsv", "useHotAirBalloons", "costHotAirBalloons", 7),
    MAGIC_CARPET("/transports/magic_carpets.tsv", "useMagicCarpets", "costMagicCarpets"),
    MAGIC_MUSHTREE("/transports/magic_mushtrees.tsv", "useMagicMushtrees", "costMagicMushtrees", 5),
    MINECART("/transports/minecarts.tsv", "useMinecarts", "costMinecarts"),
    QUETZAL("/transports/quetzals.tsv", "useQuetzals", "costQuetzals"),
    QUETZAL_WHISTLE("/transports/quetzal_whistle.tsv", "useQuetzals", "costQuetzalWhistle") {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    SEASONAL_TRANSPORTS("/transports/seasonal_transports.tsv", "useSeasonalTransports", "costSeasonalTransports"),
    SPIRIT_TREE("/transports/spirit_trees.tsv", "useSpiritTrees", "costSpiritTrees", 5),
    TELEPORTATION_BOX("/transports/teleportation_boxes.tsv", null, "costTeleportationBoxes"),
    TELEPORTATION_ITEM("/transports/teleportation_items.tsv", null, "costNonConsumableTeleportationItems") {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    TELEPORTATION_LEVER("/transports/teleportation_levers.tsv", "useTeleportationLevers", "costTeleportationLevers"),
    TELEPORTATION_MINIGAME("/transports/teleportation_minigames.tsv", "useTeleportationMinigames", "costTeleportationMinigames") {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    TELEPORTATION_PORTAL("/transports/teleportation_portals.tsv", "useTeleportationPortals", "costTeleportationPortals"),
    TELEPORTATION_PORTAL_POH("/transports/teleportation_portals_poh.tsv", "useTeleportationPortalsPoh", null),
    TELEPORTATION_SPELL("/transports/teleportation_spells.tsv", "useTeleportationSpells", "costTeleportationSpells") {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    WILDERNESS_OBELISK("/transports/wilderness_obelisks.tsv", "useWildernessObelisks", "costWildernessObelisks"),
    ;

    private final String resourcePath;
    private final String configKey;
    private final String costKey;
    private final Integer radiusThreshold;

    TransportType(String resourcePath, String configKey, String costKey) {
        this(resourcePath, configKey, costKey, null);
    }

    TransportType(String resourcePath, String configKey, String costKey, Integer radiusThreshold) {
        this.resourcePath = resourcePath;
        this.configKey = configKey;
        this.costKey = costKey;
        this.radiusThreshold = radiusThreshold;
    }

    public boolean hasResourcePath() {
        return resourcePath != null;
    }

    public boolean hasRadiusThreshold() {
        return radiusThreshold != null;
    }

    public boolean hasConfigKey() {
        return configKey != null;
    }

    public boolean hasCostKey() {
        return costKey != null;
    }

    /*
     * Indicates whether a TransportType is a teleport.
     * Levers, portals and wilderness obelisks are considered transports
     * and not teleports because they have a pre-defined origin and no
     * wilderness level limit.
     */
    public boolean isTeleport() {
        return false;
    }

    /**
     * Refines the TransportType based on the required skill levels.
     */
    public TransportType refine(int[] skillLevels) {
        return this;
    }
}
