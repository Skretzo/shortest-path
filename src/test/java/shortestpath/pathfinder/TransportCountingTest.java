package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.Test;

import shortestpath.transport.Transport;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;

public class TransportCountingTest {
    private static final Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();

    @Test
    public void testNumberOfCharterShips() {
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.CHARTER_SHIP.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /*
         * Info:
         * There are currently 16 unique charter ship origin/destinations.
         * If every combination was possible then it would be 16^2 = 256.
         * It is impossible to travel from and to the same place, so subtract 16.
         * It is also impossible to travel between certain places, presumably
         * because the distance between them is too small. Currently 12 of these.
         */
        assertEquals(16 * 16 - 16 - 12, actualCount);
    }

    @Test
    public void testNumberOfGnomeGliders() {
        // All permutations of gnome glider transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.GNOME_GLIDER.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /* 
         * Info:
         * NB: Lemanto Andra (Digsite) can only be destination and not origin
         * single_glider_origin_locations * (number_of_gnome_gliders - 1)
         *   1 * 6   // Ta Quir Priw (Gnome Stronghold)
         * + 3 * 6   // Gandius (Karamja)
         * + 3 * 6   // Kar-Hewo (Al-Kharid)
         * + 2 * 6   // Sindarpos (White Wolf Mountain)
         * + 3 * 6   // Lemantolly Undri (Feldip Hills)
         * + 3 * 6   // Ookookolly Undri (Ape Atoll)
         * = 90
         */
        assertEquals(90, actualCount);
    }

    @Test
    public void testNumberOfHotAirBalloons() {
        // All permutations of hot air balloon transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.HOT_AIR_BALLOON.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /* 
         * Info:
         * single_hot_air_balloon_origin_locations * (number_of_hot_air_balloons - 1)
         *   6 * 5   // Entrana
         * + 8 * 5   // Taverley
         * + 8 * 5   // Crafting Guild
         * + 8 * 5   // Varrock
         * + 7 * 5   // Castle Wars
         * + 8 * 5   // Grand Tree
         * = 225
         */
        assertEquals(225, actualCount);
    }

    @Test
    public void testNumberOfMagicMushtrees() {
        // All permutations of magic mushtree transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.MAGIC_MUSHTREE.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /*
         * Info:
         * single_mushtree_origin_locations * (number_of_magic_mushtrees - 1)
         *   2 * 3   // House on the Hill
         * + 2 * 3   // Verdant Valley
         * + 2 * 3   // Sticky Swamp
         * + 2 * 3   // Mushroom Meadow
         * = 24
         */
        assertEquals(24, actualCount);
    }

    @Test
    public void testNumberOfQuetzals() {
        // All but 2 permutations of quetzal transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.QUETZAL.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /*
         * Info:
         * NB: Primio can only be used between Varrock and Civitas illa Fortis
         * single_quetzal_origin_locations * (number_of_quetzals - 1) + 2
         *   1 * 13 // Aldarin
         * + 1 * 13 // Auburnvale
         * + 1 * 13 // Civitas illa Fortis
         * + 1 * 13 // Hunter Guild
         * + 1 * 13 // Quetzacalli Gorge
         * + 1 * 13 // Sunset Coast
         * + 1 * 13 // Tal Teklan
         * + 1 * 13 // The Teomat
         * + 1 * 13 // Cam Torum entrance
         * + 1 * 13 // Colossal Wyrm Remains
         * + 1 * 13 // Fortis Colosseum
         * + 1 * 13 // Kastori
         * + 1 * 13 // Outer Fortis
         * + 1 * 13 // Salvager Overlook
         * + 1 // Varrock -> Civitas illa Fortis
         * + 1 // Civitas illa Fortis -> Varrock
         * = 182 + 2
         * = 184
         */
        assertEquals(184, actualCount);
    }

    @Test
    public void testNumberOfSpiritTrees() {
        // All permutations of spirit tree transports are resolved from origins and destinations
        int actualCount = 0;
        for (int origin : transports.keySet()) {
            for (Transport transport : transports.get(origin)) {
                if (TransportType.SPIRIT_TREE.equals(transport.getType())) {
                    actualCount++;
                }
            }
        }
        /*
         * Info:
         * single_tree_origin_locations * (number_of_spirit_trees - 1)
         *   15 * 12   // Tree Gnome Village
         * + 14 * 12   // Gnome Stronghold
         * +  8 * 12   // Battlefield of Khazard
         * +  8 * 12   // Grand Exchange
         * +  8 * 12   // Feldip Hills
         * +  7 * 12   // Prifddinas
         * + 12 * 12   // Port Sarim
         * + 12 * 12   // Etceteria
         * + 12 * 12   // Brimhaven
         * + 12 * 12   // Hosidius
         * + 12 * 12   // Farming Guild
         * +  1 * 12   // Player-owned house
         * + 12 * 12   // Poison Waste
         * = 1596
         */
        assertEquals(1596, actualCount);
    }
}
