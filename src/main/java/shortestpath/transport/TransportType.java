package shortestpath.transport;

import lombok.Getter;
import net.runelite.api.Skill;
import shortestpath.ShortestPathConfig;

import java.util.function.Function;

@Getter
public enum TransportType {
    TRANSPORT("/transports/transports.tsv", null, null),
    AGILITY_SHORTCUT("/transports/agility_shortcuts.tsv", ShortestPathConfig::useAgilityShortcuts, ShortestPathConfig::costAgilityShortcuts) {
        @Override
        public TransportType refine(int[] skillLevels) {
            if (skillLevels[Skill.RANGED.ordinal()] > 1 || skillLevels[Skill.STRENGTH.ordinal()] > 1) {
                return GRAPPLE_SHORTCUT;
            }
            return this;
        }
    },
    GRAPPLE_SHORTCUT(null, ShortestPathConfig::useGrappleShortcuts, ShortestPathConfig::costGrappleShortcuts),
    BOAT("/transports/boats.tsv", ShortestPathConfig::useBoats, ShortestPathConfig::costBoats),
    CANOE("/transports/canoes.tsv", ShortestPathConfig::useCanoes, ShortestPathConfig::costCanoes),
    CHARTER_SHIP("/transports/charter_ships.tsv", ShortestPathConfig::useCharterShips, ShortestPathConfig::costCharterShips),
    SHIP("/transports/ships.tsv", ShortestPathConfig::useShips, ShortestPathConfig::costShips),
    FAIRY_RING("/transports/fairy_rings.tsv", ShortestPathConfig::useFairyRings, ShortestPathConfig::costFairyRings, 6),
    GNOME_GLIDER("/transports/gnome_gliders.tsv", ShortestPathConfig::useGnomeGliders, ShortestPathConfig::costGnomeGliders, 6),
    HOT_AIR_BALLOON("/transports/hot_air_balloons.tsv", ShortestPathConfig::useHotAirBalloons, ShortestPathConfig::costHotAirBalloons, 7),
    MAGIC_CARPET("/transports/magic_carpets.tsv", ShortestPathConfig::useMagicCarpets, ShortestPathConfig::costMagicCarpets),
    MAGIC_MUSHTREE("/transports/magic_mushtrees.tsv", ShortestPathConfig::useMagicMushtrees, ShortestPathConfig::costMagicMushtrees, 5),
    MINECART("/transports/minecarts.tsv", ShortestPathConfig::useMinecarts, ShortestPathConfig::costMinecarts),
    QUETZAL("/transports/quetzals.tsv", ShortestPathConfig::useQuetzals, ShortestPathConfig::costQuetzals),
    QUETZAL_WHISTLE("/transports/quetzal_whistle.tsv", ShortestPathConfig::useQuetzals, ShortestPathConfig::costQuetzalWhistle) {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    SEASONAL_TRANSPORTS("/transports/seasonal_transports.tsv", ShortestPathConfig::useSeasonalTransports, ShortestPathConfig::costSeasonalTransports),
    SPIRIT_TREE("/transports/spirit_trees.tsv", ShortestPathConfig::useSpiritTrees, ShortestPathConfig::costSpiritTrees, 5),
    TELEPORTATION_BOX("/transports/teleportation_boxes.tsv", null, ShortestPathConfig::costTeleportationBoxes),
    TELEPORTATION_ITEM("/transports/teleportation_items.tsv", null, ShortestPathConfig::costNonConsumableTeleportationItems) {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    TELEPORTATION_LEVER("/transports/teleportation_levers.tsv", ShortestPathConfig::useTeleportationLevers, ShortestPathConfig::costTeleportationLevers),
    TELEPORTATION_MINIGAME("/transports/teleportation_minigames.tsv", ShortestPathConfig::useTeleportationMinigames, ShortestPathConfig::costTeleportationMinigames) {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    TELEPORTATION_PORTAL("/transports/teleportation_portals.tsv", ShortestPathConfig::useTeleportationPortals, ShortestPathConfig::costTeleportationPortals),
    TELEPORTATION_PORTAL_POH("/transports/teleportation_portals_poh.tsv", ShortestPathConfig::useTeleportationPortalsPoh, null),
    TELEPORTATION_SPELL("/transports/teleportation_spells.tsv", ShortestPathConfig::useTeleportationSpells, ShortestPathConfig::costTeleportationSpells) {
        @Override
        public boolean isTeleport() {
            return true;
        }
    },
    WILDERNESS_OBELISK("/transports/wilderness_obelisks.tsv", ShortestPathConfig::useWildernessObelisks, ShortestPathConfig::costWildernessObelisks),
    ;

    private final String resourcePath;
    private final Function<ShortestPathConfig, Boolean> enabledGetter;
    private final Function<ShortestPathConfig, Integer> costGetter;
    private final Integer radiusThreshold;

    TransportType(String resourcePath, Function<ShortestPathConfig, Boolean> enabledGetter, Function<ShortestPathConfig, Integer> costGetter) {
        this(resourcePath, enabledGetter, costGetter, null);
    }

    TransportType(String resourcePath, Function<ShortestPathConfig, Boolean> enabledGetter, Function<ShortestPathConfig, Integer> costGetter, Integer radiusThreshold) {
        this.resourcePath = resourcePath;
        this.enabledGetter = enabledGetter;
        this.costGetter = costGetter;
        this.radiusThreshold = radiusThreshold;
    }

    public boolean hasResourcePath() {
        return resourcePath != null;
    }

    public boolean hasRadiusThreshold() {
        return radiusThreshold != null;
    }

    public boolean hasEnabledGetter() {
        return enabledGetter != null;
    }

    public boolean hasCostGetter() {
        return costGetter != null;
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
