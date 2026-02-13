package shortestpath.transport;

import org.junit.Test;
import org.junit.Assert;
import shortestpath.WorldPointUtil;
import shortestpath.transport.parser.VarCheckType;
import shortestpath.transport.parser.VarRequirement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tests for Quetzal transport VarPlayer checks.
 * Verifies that destinations requiring built platforms are correctly filtered.
 */
public class QuetzalTransportTest {

    // VarPlayer 4182 bit values for quetzal platforms
    private static final int VARPLAYER_QUETZAL_PLATFORMS = 4182;
    private static final int BIT_KASTORI = 8;          // 4182&8
    private static final int BIT_CAM_TORUM = 32;       // 4182&32
    private static final int BIT_COLOSSAL_WYRM = 64;   // 4182&64
    private static final int BIT_OUTER_FORTIS = 128;   // 4182&128
    private static final int BIT_FORTIS_COLOSSEUM = 256; // 4182&256
    private static final int BIT_SALVAGER_OVERLOOK = 2048; // 4182&2048

    @Test
    public void testQuetzalTransportsAreLoaded() {
        Map<Integer, Set<Transport>> transports = new HashMap<>();
        String contents = getQuetzalTsvContents();

        TransportLoader.addTransportsFromContents(transports, contents, TransportType.QUETZAL, 10);

        Assert.assertFalse("Quetzal transports should be loaded", transports.isEmpty());
        System.out.println("Loaded " + transports.size() + " quetzal origin locations");
    }

    @Test
    public void testQuetzalTransportsFromActualResource() throws IOException {
        // Load from actual resource file
        InputStream is = getClass().getResourceAsStream("/transports/quetzals.tsv");
        Assert.assertNotNull("quetzals.tsv resource should exist", is);

        String contents = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        is.close();

        Map<Integer, Set<Transport>> transports = new HashMap<>();
        TransportLoader.addTransportsFromContents(transports, contents, TransportType.QUETZAL, 10);

        Assert.assertFalse("Quetzal transports should be loaded from resource", transports.isEmpty());
        System.out.println("Loaded " + transports.size() + " quetzal origin locations from resource");

        // Check that Kastori destination has VarPlayer check
        int kastoriPacked = WorldPointUtil.packWorldPoint(1344, 3022, 0);
        boolean foundKastoriWithCheck = false;

        for (Set<Transport> transportSet : transports.values()) {
            for (Transport t : transportSet) {
                if (t.getDestination() == kastoriPacked) {
                    System.out.println("Found transport to Kastori from " +
                        WorldPointUtil.unpackWorldX(t.getOrigin()) + "," +
                        WorldPointUtil.unpackWorldY(t.getOrigin()));
                    System.out.println("  VarRequirements: " + t.getVarRequirements().size());
                    System.out.println("  VarPlayers: " + t.getVarPlayers().size());
                    for (VarRequirement vp : t.getVarPlayers()) {
                        System.out.println("    VarPlayer: id=" + vp.getId() +
                            ", value=" + vp.getValue() + ", check=" + vp.getCheckType());
                        if (vp.getId() == VARPLAYER_QUETZAL_PLATFORMS && vp.getValue() == BIT_KASTORI) {
                            foundKastoriWithCheck = true;
                        }
                    }
                }
            }
        }

        Assert.assertTrue("Kastori transport should have VarPlayer 4182&8 check", foundKastoriWithCheck);
    }

    @Test
    public void testKastoriHasVarPlayerCheck() {
        Map<Integer, Set<Transport>> transports = new HashMap<>();
        String contents = getQuetzalTsvContents();

        TransportLoader.addTransportsFromContents(transports, contents, TransportType.QUETZAL, 10);

        // Find a transport going TO Kastori (1344 3022 0)
        int kastoriPacked = WorldPointUtil.packWorldPoint(1344, 3022, 0);

        boolean foundKastoriDestination = false;
        boolean hasVarPlayerCheck = false;

        for (Set<Transport> transportSet : transports.values()) {
            for (Transport t : transportSet) {
                if (t.getDestination() == kastoriPacked) {
                    foundKastoriDestination = true;
                    Set<VarRequirement> varPlayers = t.getVarPlayers();
                    System.out.println("Kastori transport from " +
                        WorldPointUtil.unpackWorldX(t.getOrigin()) + "," +
                        WorldPointUtil.unpackWorldY(t.getOrigin()) +
                        " has " + varPlayers.size() + " VarPlayer requirements");

                    for (VarRequirement vp : varPlayers) {
                        System.out.println("  VarPlayer: id=" + vp.getId() + ", value=" + vp.getValue() + ", check=" + vp.getCheckType());
                        if (vp.getId() == VARPLAYER_QUETZAL_PLATFORMS) {
                            hasVarPlayerCheck = true;
                        }
                    }
                }
            }
        }

        Assert.assertTrue("Should find transports to Kastori", foundKastoriDestination);
        Assert.assertTrue("Kastori transport should have VarPlayer 4182 check", hasVarPlayerCheck);
    }

    @Test
    public void testVarPlayerCheckBlocksUnbuiltPlatform() {
        // Simulate player who has NOT built Kastori (bit 8 not set)
        Map<Integer, Integer> varPlayerValues = new HashMap<>();
        varPlayerValues.put(VARPLAYER_QUETZAL_PLATFORMS, 0); // No platforms built

        VarRequirement kastoriCheck = VarRequirement.varPlayer(VARPLAYER_QUETZAL_PLATFORMS, BIT_KASTORI, VarCheckType.BIT_SET);

        Assert.assertFalse("Kastori should be blocked when platform not built",
            kastoriCheck.check(varPlayerValues));
    }

    @Test
    public void testVarPlayerCheckAllowsBuiltPlatform() {
        // Simulate player who HAS built Kastori (bit 8 set)
        Map<Integer, Integer> varPlayerValues = new HashMap<>();
        varPlayerValues.put(VARPLAYER_QUETZAL_PLATFORMS, BIT_KASTORI); // Kastori built

        VarRequirement kastoriCheck = VarRequirement.varPlayer(VARPLAYER_QUETZAL_PLATFORMS, BIT_KASTORI, VarCheckType.BIT_SET);

        Assert.assertTrue("Kastori should be allowed when platform is built",
            kastoriCheck.check(varPlayerValues));
    }

    @Test
    public void testVarPlayerCheckWithMultiplePlatformsBuilt() {
        // Simulate player who has built multiple platforms
        Map<Integer, Integer> varPlayerValues = new HashMap<>();
        int builtPlatforms = BIT_KASTORI | BIT_CAM_TORUM | BIT_OUTER_FORTIS;
        varPlayerValues.put(VARPLAYER_QUETZAL_PLATFORMS, builtPlatforms);

        // Kastori should be allowed
        VarRequirement kastoriCheck = VarRequirement.varPlayer(VARPLAYER_QUETZAL_PLATFORMS, BIT_KASTORI, VarCheckType.BIT_SET);
        Assert.assertTrue("Kastori should be allowed", kastoriCheck.check(varPlayerValues));

        // Cam Torum should be allowed
        VarRequirement camTorumCheck = VarRequirement.varPlayer(VARPLAYER_QUETZAL_PLATFORMS, BIT_CAM_TORUM, VarCheckType.BIT_SET);
        Assert.assertTrue("Cam Torum should be allowed", camTorumCheck.check(varPlayerValues));

        // Colossal Wyrm should be blocked (not built)
        VarRequirement wyrmCheck = VarRequirement.varPlayer(VARPLAYER_QUETZAL_PLATFORMS, BIT_COLOSSAL_WYRM, VarCheckType.BIT_SET);
        Assert.assertFalse("Colossal Wyrm should be blocked", wyrmCheck.check(varPlayerValues));
    }

    @Test
    public void testAllBuiltRequiredDestinationsHaveVarPlayerChecks() {
        Map<Integer, Set<Transport>> transports = new HashMap<>();
        String contents = getQuetzalTsvContents();

        TransportLoader.addTransportsFromContents(transports, contents, TransportType.QUETZAL, 10);

        // Destinations that require building
        int[] requiredBuildDestinations = {
            WorldPointUtil.packWorldPoint(1779, 3111, 0), // Fortis Colosseum
            WorldPointUtil.packWorldPoint(1344, 3022, 0), // Kastori
            WorldPointUtil.packWorldPoint(1700, 3037, 0), // Outer Fortis
            WorldPointUtil.packWorldPoint(1670, 2933, 0), // Colossal Wyrm Remains
            WorldPointUtil.packWorldPoint(1446, 3108, 0), // Cam Torum
            WorldPointUtil.packWorldPoint(1613, 3300, 0), // Salvager Overlook
        };

        String[] destinationNames = {
            "Fortis Colosseum", "Kastori", "Outer Fortis",
            "Colossal Wyrm Remains", "Cam Torum", "Salvager Overlook"
        };

        for (int i = 0; i < requiredBuildDestinations.length; i++) {
            int destPacked = requiredBuildDestinations[i];
            String destName = destinationNames[i];

            boolean foundTransport = false;
            boolean hasVarPlayerCheck = false;

            for (Set<Transport> transportSet : transports.values()) {
                for (Transport t : transportSet) {
                    if (t.getDestination() == destPacked) {
                        foundTransport = true;
                        for (VarRequirement vp : t.getVarPlayers()) {
                            if (vp.getId() == VARPLAYER_QUETZAL_PLATFORMS) {
                                hasVarPlayerCheck = true;
                                break;
                            }
                        }
                    }
                }
            }

            Assert.assertTrue("Should find transport to " + destName, foundTransport);
            Assert.assertTrue(destName + " should have VarPlayer 4182 check", hasVarPlayerCheck);
        }
    }

    private String getQuetzalTsvContents() {
        // Return the expected quetzals.tsv content for testing
        return "# Origin\tDestination\tmenuOption menuTarget objectID\tQuests\tDuration\tDisplay info\tVarPlayers\n" +
            "1389 2901 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t\n" +
            "1411 3361 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t\n" +
            "1697 3140 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t\n" +
            "1585 3053 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t\n" +
            "1510 3222 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t\n" +
            "1548 2995 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t\n" +
            "1226 3091 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t\n" +
            "1437 3171 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t\n" +
            "1779 3111 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t4182&256\n" +
            "1344 3022 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t4182&8\n" +
            "1700 3037 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t4182&128\n" +
            "1670 2933 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t4182&64\n" +
            "1446 3108 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t4182&32\n" +
            "1613 3300 0\t\tTravel Renu 13350\tTwilight's Promise\t6\t\t4182&2048\n" +
            "\t1389 2901 0\t\tTwilight's Promise\t6\tAldarin\t\n" +
            "\t1411 3361 0\t\tTwilight's Promise\t6\tAuburnvale\t\n" +
            "\t1697 3140 0\t\tTwilight's Promise\t6\tCivitas illa Fortis\t\n" +
            "\t1585 3053 0\t\tTwilight's Promise\t6\tHunter Guild\t\n" +
            "\t1510 3222 0\t\tTwilight's Promise\t6\tQuetzacalli Gorge\t\n" +
            "\t1548 2995 0\t\tTwilight's Promise\t6\tSunset Coast\t\n" +
            "\t1226 3091 0\t\tTwilight's Promise\t6\tTal Teklan\t\n" +
            "\t1437 3171 0\t\tTwilight's Promise\t6\tThe Teomat\t\n" +
            "\t1779 3111 0\t\tTwilight's Promise\t6\tFortis Colosseum\t4182&256\n" +
            "\t1344 3022 0\t\tTwilight's Promise\t6\tKastori\t4182&8\n" +
            "\t1700 3037 0\t\tTwilight's Promise\t6\tOuter Fortis\t4182&128\n" +
            "\t1670 2933 0\t\tTwilight's Promise\t6\tColossal Wyrm Remains\t4182&64\n" +
            "\t1446 3108 0\t\tTwilight's Promise\t6\tCam Torum\t4182&32\n" +
            "\t1613 3300 0\t\tTwilight's Promise\t6\tSalvager Overlook\t4182&2048\n";
    }

    @Test
    public void testQuetzalWhistleHasMultipleDestinations() throws IOException {
        // Load from actual resource file
        InputStream is = getClass().getResourceAsStream("/transports/teleportation_items.tsv");
        Assert.assertNotNull("teleportation_items.tsv resource should exist", is);

        String contents = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        is.close();

        Map<Integer, Set<Transport>> transports = new HashMap<>();
        TransportLoader.addTransportsFromContents(transports, contents, TransportType.TELEPORTATION_ITEM, 0);

        // Count Quetzal whistle destinations
        int quetzalWhistleDestinations = 0;
        int[] expectedDestinations = {
            WorldPointUtil.packWorldPoint(1389, 2901, 0), // Aldarin
            WorldPointUtil.packWorldPoint(1411, 3361, 0), // Auburnvale
            WorldPointUtil.packWorldPoint(1697, 3140, 0), // Civitas illa Fortis
            WorldPointUtil.packWorldPoint(1585, 3053, 0), // Hunter Guild
            WorldPointUtil.packWorldPoint(1510, 3222, 0), // Quetzacalli Gorge
            WorldPointUtil.packWorldPoint(1548, 2995, 0), // Sunset Coast
            WorldPointUtil.packWorldPoint(1226, 3091, 0), // Tal Teklan
            WorldPointUtil.packWorldPoint(1437, 3171, 0), // The Teomat
            WorldPointUtil.packWorldPoint(1779, 3111, 0), // Fortis Colosseum
            WorldPointUtil.packWorldPoint(1344, 3022, 0), // Kastori
            WorldPointUtil.packWorldPoint(1700, 3037, 0), // Outer Fortis
            WorldPointUtil.packWorldPoint(1670, 2933, 0), // Colossal Wyrm Remains
            WorldPointUtil.packWorldPoint(1446, 3108, 0), // Cam Torum
            WorldPointUtil.packWorldPoint(1613, 3300, 0), // Salvager Overlook
        };

        for (Set<Transport> transportSet : transports.values()) {
            for (Transport t : transportSet) {
                String displayInfo = t.getDisplayInfo();
                if (displayInfo != null && displayInfo.startsWith("Quetzal whistle:")) {
                    quetzalWhistleDestinations++;
                    System.out.println("Found Quetzal whistle destination: " + displayInfo +
                        " at " + WorldPointUtil.unpackWorldX(t.getDestination()) + "," +
                        WorldPointUtil.unpackWorldY(t.getDestination()));
                }
            }
        }

        Assert.assertEquals("Should have 14 Quetzal whistle destinations", 14, quetzalWhistleDestinations);
    }

    @Test
    public void testQuetzalWhistleKastoriHasVarPlayerCheck() throws IOException {
        // Load from actual resource file
        InputStream is = getClass().getResourceAsStream("/transports/teleportation_items.tsv");
        Assert.assertNotNull("teleportation_items.tsv resource should exist", is);

        String contents = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        is.close();

        Map<Integer, Set<Transport>> transports = new HashMap<>();
        TransportLoader.addTransportsFromContents(transports, contents, TransportType.TELEPORTATION_ITEM, 0);

        int kastoriPacked = WorldPointUtil.packWorldPoint(1344, 3022, 0);
        boolean foundKastoriWhistle = false;
        boolean hasVarPlayerCheck = false;

        for (Set<Transport> transportSet : transports.values()) {
            for (Transport t : transportSet) {
                if (t.getDestination() == kastoriPacked) {
                    String displayInfo = t.getDisplayInfo();
                    if (displayInfo != null && displayInfo.contains("Quetzal whistle")) {
                        foundKastoriWhistle = true;
                        System.out.println("Found Quetzal whistle to Kastori: " + displayInfo);
                        System.out.println("  VarPlayers: " + t.getVarPlayers().size());
                        for (VarRequirement vp : t.getVarPlayers()) {
                            System.out.println("    VarPlayer: id=" + vp.getId() +
                                ", value=" + vp.getValue() + ", check=" + vp.getCheckType());
                            if (vp.getId() == VARPLAYER_QUETZAL_PLATFORMS && vp.getValue() == BIT_KASTORI) {
                                hasVarPlayerCheck = true;
                            }
                        }
                    }
                }
            }
        }

        Assert.assertTrue("Should find Quetzal whistle to Kastori", foundKastoriWhistle);
        Assert.assertTrue("Quetzal whistle to Kastori should have VarPlayer 4182&8 check", hasVarPlayerCheck);
    }
}

