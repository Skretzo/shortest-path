package shortestpath;

public enum TransportType {
    TRANSPORT,
    AGILITY_SHORTCUT,
    GRAPPLE_SHORTCUT,
    BOAT,
    CANOE,
    CHARTER_SHIP,
    SHIP,
    FAIRY_RING,
    GNOME_GLIDER,
    HOT_AIR_BALLOON,
    MINECART,
    QUETZAL,
    SPIRIT_TREE,
    TELEPORTATION_ITEM,
    TELEPORTATION_LEVER,
    TELEPORTATION_MINIGAME,
    TELEPORTATION_PORTAL,
    TELEPORTATION_SPELL,
    WILDERNESS_OBELISK,
    ;

    /*
     * Indicates whether a TransportType is a teleport.
     * Levers, portals and wilderness obelisks are considered transports
     * and not teleports because they have a pre-defined origin and no
     * wilderness level limit.
     */
    public static boolean isTeleport(TransportType transportType) {
        if (transportType == null) {
            return false;
        }
        switch (transportType) {
            case TELEPORTATION_ITEM:
            case TELEPORTATION_MINIGAME:
            case TELEPORTATION_SPELL:
                return true;
            default:
                return false;
        }
    }

    /*
     * Indicates whether a TransportType is a resource movement.
     * Resource movements are any transport that is not walking.
     */
    public static boolean isResourceMovement(TransportType transportType) {
        if (transportType == null) {
            return false;
        }
        if (isTeleport(transportType)) {
            return true;
        }
        switch (transportType) {
            case FAIRY_RING:
            case GNOME_GLIDER:
            case HOT_AIR_BALLOON:
            case BOAT:
            case CANOE:
            case CHARTER_SHIP:
            case SHIP:
            case MINECART:
            case QUETZAL:
            case SPIRIT_TREE:
            case TELEPORTATION_LEVER:
            case TELEPORTATION_PORTAL:
            case TELEPORTATION_SPELL:
            case WILDERNESS_OBELISK:
                return true;
            default:
                return false;
        }
    }
}
