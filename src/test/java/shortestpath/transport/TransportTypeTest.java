package shortestpath.transport;

import static org.junit.Assert.*;

import org.junit.Test;

public class TransportTypeTest {

    @Test
    public void testIsTeleport() {
        assertTrue(TransportType.TELEPORTATION_ITEM.isTeleport());
        assertTrue(TransportType.TELEPORTATION_MINIGAME.isTeleport());
        assertTrue(TransportType.TELEPORTATION_SPELL.isTeleport());
        assertTrue(TransportType.QUETZAL_WHISTLE.isTeleport());

        assertFalse(TransportType.TRANSPORT.isTeleport());
        assertFalse(TransportType.AGILITY_SHORTCUT.isTeleport());
        assertFalse(TransportType.GRAPPLE_SHORTCUT.isTeleport());
        assertFalse(TransportType.BOAT.isTeleport());
        assertFalse(TransportType.CANOE.isTeleport());
        assertFalse(TransportType.CHARTER_SHIP.isTeleport());
        assertFalse(TransportType.SHIP.isTeleport());
        assertFalse(TransportType.FAIRY_RING.isTeleport());
        assertFalse(TransportType.GNOME_GLIDER.isTeleport());
        assertFalse(TransportType.HOT_AIR_BALLOON.isTeleport());
        assertFalse(TransportType.MAGIC_CARPET.isTeleport());
        assertFalse(TransportType.MAGIC_MUSHTREE.isTeleport());
        assertFalse(TransportType.MINECART.isTeleport());
        assertFalse(TransportType.QUETZAL.isTeleport());
        assertFalse(TransportType.SEASONAL_TRANSPORTS.isTeleport());
        assertFalse(TransportType.SPIRIT_TREE.isTeleport());
        assertFalse(TransportType.TELEPORTATION_BOX.isTeleport());
        assertFalse(TransportType.TELEPORTATION_LEVER.isTeleport());
        assertFalse(TransportType.TELEPORTATION_PORTAL.isTeleport());
        assertFalse(TransportType.WILDERNESS_OBELISK.isTeleport());
    }
}
