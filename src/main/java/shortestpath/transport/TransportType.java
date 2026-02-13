package shortestpath.transport;

import lombok.Getter;
import net.runelite.api.Skill;

@Getter
public enum TransportType {
    TRANSPORT("/transports/transports.tsv"),
    AGILITY_SHORTCUT("/transports/agility_shortcuts.tsv") {
        @Override
        public TransportType refine(int[] skillLevels) {
            if (skillLevels[Skill.RANGED.ordinal()] > 1 || skillLevels[Skill.STRENGTH.ordinal()] > 1) {
                return GRAPPLE_SHORTCUT;
            }
            return this;
        }
    },
    GRAPPLE_SHORTCUT(null),
    BOAT("/transports/boats.tsv"),
    CANOE("/transports/canoes.tsv"),
    CHARTER_SHIP("/transports/charter_ships.tsv"),
    SHIP("/transports/ships.tsv"),
    FAIRY_RING("/transports/fairy_rings.tsv"),
    GNOME_GLIDER("/transports/gnome_gliders.tsv", 6),
    HOT_AIR_BALLOON("/transports/hot_air_balloons.tsv", 7),
    MAGIC_CARPET("/transports/magic_carpets.tsv"),
    MAGIC_MUSHTREE("/transports/magic_mushtrees.tsv", 5),
    MINECART("/transports/minecarts.tsv"),
    QUETZAL("/transports/quetzals.tsv"),
    SEASONAL_TRANSPORTS("/transports/seasonal_transports.tsv"),
    SPIRIT_TREE("/transports/spirit_trees.tsv", 5),
    TELEPORTATION_BOX("/transports/teleportation_boxes.tsv"),
    TELEPORTATION_ITEM("/transports/teleportation_items.tsv") {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    TELEPORTATION_LEVER("/transports/teleportation_levers.tsv"),
    TELEPORTATION_MINIGAME("/transports/teleportation_minigames.tsv") {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    TELEPORTATION_PORTAL("/transports/teleportation_portals.tsv"),
    TELEPORTATION_PORTAL_POH("/transports/teleportation_portals_poh.tsv"),
    TELEPORTATION_SPELL("/transports/teleportation_spells.tsv") {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    WILDERNESS_OBELISK("/transports/wilderness_obelisks.tsv"),
    ;

    private final String resourcePath;
    private final Integer radiusThreshold;

    TransportType(String resourcePath) {
        this(resourcePath, null);
    }

    TransportType(String resourcePath, Integer radiusThreshold) {
        this.resourcePath = resourcePath;
        this.radiusThreshold = radiusThreshold;
    }

    public boolean hasResourcePath() {
        return resourcePath != null;
    }

    public boolean hasRadiusThreshold() {
        return radiusThreshold != null;
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
