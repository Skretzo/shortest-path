package shortestpath.transport;

import static org.junit.Assert.*;

import org.junit.Test;

public class TransportTypeTest {

    @Test
    public void testIsTeleport() {
        assertTrue(TransportType.isTeleport(TransportType.TELEPORTATION_ITEM));
        assertTrue(TransportType.isTeleport(TransportType.TELEPORTATION_MINIGAME));
        assertTrue(TransportType.isTeleport(TransportType.TELEPORTATION_SPELL));

        assertFalse(TransportType.isTeleport(TransportType.TRANSPORT));
        assertFalse(TransportType.isTeleport(TransportType.AGILITY_SHORTCUT));
        assertFalse(TransportType.isTeleport(TransportType.GRAPPLE_SHORTCUT));
        assertFalse(TransportType.isTeleport(TransportType.BOAT));
        assertFalse(TransportType.isTeleport(TransportType.CANOE));
        assertFalse(TransportType.isTeleport(TransportType.CHARTER_SHIP));
        assertFalse(TransportType.isTeleport(TransportType.SHIP));
        assertFalse(TransportType.isTeleport(TransportType.FAIRY_RING));
        assertFalse(TransportType.isTeleport(TransportType.GNOME_GLIDER));
        assertFalse(TransportType.isTeleport(TransportType.HOT_AIR_BALLOON));
        assertFalse(TransportType.isTeleport(TransportType.MAGIC_CARPET));
        assertFalse(TransportType.isTeleport(TransportType.MAGIC_MUSHTREE));
        assertFalse(TransportType.isTeleport(TransportType.MINECART));
        assertFalse(TransportType.isTeleport(TransportType.QUETZAL));
        assertFalse(TransportType.isTeleport(TransportType.SEASONAL_TRANSPORTS));
        assertFalse(TransportType.isTeleport(TransportType.SPIRIT_TREE));
        assertFalse(TransportType.isTeleport(TransportType.TELEPORTATION_BOX));
        assertFalse(TransportType.isTeleport(TransportType.TELEPORTATION_LEVER));
        assertFalse(TransportType.isTeleport(TransportType.TELEPORTATION_PORTAL));
        assertFalse(TransportType.isTeleport(TransportType.WILDERNESS_OBELISK));

        assertFalse(TransportType.isTeleport(null));
    }
}
