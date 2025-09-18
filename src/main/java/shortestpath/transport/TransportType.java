package shortestpath.transport;

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
    MAGIC_CARPET,
    MAGIC_MUSHTREE,
    MINECART,
    QUETZAL,
    SEASONAL_TRANSPORTS,
    SPIRIT_TREE,
    TELEPORTATION_BOX,
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
}
