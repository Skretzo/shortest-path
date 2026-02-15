package shortestpath.transport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.transport.parser.VarCheckType;
import shortestpath.transport.parser.VarRequirement;

/**
 * Tests for Quetzal transport VarPlayer checks.
 * Verifies that destinations requiring built platforms are correctly filtered.
 */
public class QuetzalTransportTest {

    // VarPlayer 4182 bit values for quetzal platforms
    private static final int VARPLAYER_QUETZAL_PLATFORMS = 4182;
    private static final int BIT_KASTORI = 16384;          // 4182&16384
    private static final int BIT_CAM_TORUM = 32;       // 4182&32
    private static final int BIT_COLOSSAL_WYRM = 64;   // 4182&64
    private static final int BIT_OUTER_FORTIS = 128;   // 4182&128

    @Test
    public void testQuetzalTransportsAreLoaded() {
        Map<Integer, Set<Transport>> transports = new HashMap<>();
        String contents = getQuetzalTsvContents();

        TransportLoader.addTransportsFromContents(transports, contents, TransportType.QUETZAL, 10);

        Assert.assertFalse("Quetzal transports should be loaded", transports.isEmpty());
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

        // Check that Kastori destination has VarPlayer check
        int kastoriPacked = WorldPointUtil.packWorldPoint(1344, 3022, 0);
        boolean foundKastoriWithCheck = false;

        for (Set<Transport> transportSet : transports.values()) {
            for (Transport t : transportSet) {
                if (t.getDestination() == kastoriPacked) {
                    for (VarRequirement vp : t.getVarPlayers()) {
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

                    for (VarRequirement vp : varPlayers) {
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
    public void testQuetzalWhistleHasMultipleDestinations() throws IOException {
        // Load from actual resource file
        InputStream is = getClass().getResourceAsStream("/transports/quetzal_whistle.tsv");
        Assert.assertNotNull("quetzal_whistle.tsv resource should exist", is);

        String contents = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        is.close();

        Map<Integer, Set<Transport>> transports = new HashMap<>();
        TransportLoader.addTransportsFromContents(transports, contents, TransportType.QUETZAL_WHISTLE, 0);

        // Count Quetzal whistle destinations
        int quetzalWhistleDestinations = 0;
        for (Set<Transport> transportSet : transports.values()) {
            for (Transport t : transportSet) {
                String displayInfo = t.getDisplayInfo();
                if (displayInfo != null && displayInfo.startsWith("Quetzal whistle:")) {
                    quetzalWhistleDestinations++;
                }
            }
        }

        Assert.assertEquals("Should have 14 Quetzal whistle destinations", 14, quetzalWhistleDestinations);
    }

    @Test
    public void testQuetzalWhistleKastoriHasVarPlayerCheck() throws IOException {
        // Load from actual resource file
        InputStream is = getClass().getResourceAsStream("/transports/quetzal_whistle.tsv");
        Assert.assertNotNull("quetzal_whistle.tsv resource should exist", is);

        String contents = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        is.close();

        Map<Integer, Set<Transport>> transports = new HashMap<>();
        TransportLoader.addTransportsFromContents(transports, contents, TransportType.QUETZAL_WHISTLE, 0);

        int kastoriPacked = WorldPointUtil.packWorldPoint(1344, 3022, 0);
        boolean foundKastoriWhistle = false;
        boolean hasVarPlayerCheck = false;

        for (Set<Transport> transportSet : transports.values()) {
            for (Transport t : transportSet) {
                if (t.getDestination() == kastoriPacked) {
                    String displayInfo = t.getDisplayInfo();
                    if (displayInfo != null && displayInfo.contains("Quetzal whistle")) {
                        foundKastoriWhistle = true;
                        for (VarRequirement vp : t.getVarPlayers()) {
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

    /**
     * Returns test TSV content for quetzal transports.
     * This is a simplified version for unit testing.
     */
    private String getQuetzalTsvContents() {
        return "# Origin\tDestination\tmenuOption menuTarget objectID\tSkills\tItems\tQuests\tDuration\tDisplay info\tConsumable\tWilderness level\tVarbits\tVarPlayers\n" +
                "1389 2901 0\t\tTravel Renu 13350\t\t\tTwilight's Promise\t6\t\t\t\t\t\n" +
                "1411 3361 0\t\tTravel Renu 13350\t\t\tTwilight's Promise\t6\t\t\t\t\t\n" +
                "1697 3140 0\t\tTravel Renu 13350\t\t\tTwilight's Promise\t6\t\t\t\t\t\n" +
                "1344 3022 0\t\tTravel Renu 13350\t\t\tTwilight's Promise\t6\t\t\t\t\t4182&8\n" +
                "\t1389 2901 0\t\t\t\tTwilight's Promise\t6\tAldarin\t\t\t\t\n" +
                "\t1411 3361 0\t\t\t\tTwilight's Promise\t6\tAuburnvale\t\t\t\t\n" +
                "\t1697 3140 0\t\t\t\tTwilight's Promise\t6\tCivitas illa Fortis\t\t\t\t\n" +
                "\t1344 3022 0\t\t\t\tTwilight's Promise\t6\tKastori\t\t\t\t4182&8\n";
    }

    /**
     * Verifies that all QUETZAL_WHISTLE transports from the actual resource file
     * have the correct type and VarPlayer requirements.
     */
    @Test
    public void testQuetzalWhistleFromResourcesHasCorrectType() {
        Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();

        int kastoriPacked = WorldPointUtil.packWorldPoint(1344, 3022, 0);
        boolean foundQuetzalWhistleToKastori = false;
        boolean hasCorrectVarPlayerCheck = false;
        boolean varRequirementsContainsVarPlayer = false;

        // Find the QUETZAL_WHISTLE transport to Kastori
        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            for (Transport t : entry.getValue()) {
                if (t.getDestination() == kastoriPacked &&
                        TransportType.QUETZAL_WHISTLE.equals(t.getType())) {
                    foundQuetzalWhistleToKastori = true;

                    for (VarRequirement vp : t.getVarPlayers()) {
                        if (vp.getId() == VARPLAYER_QUETZAL_PLATFORMS &&
                                vp.getValue() == BIT_KASTORI &&
                                vp.getCheckType() == VarCheckType.BIT_SET) {
                            hasCorrectVarPlayerCheck = true;
                        }
                    }

                    // Also verify getVarRequirements() returns the same VarPlayer requirement
                    for (VarRequirement vr : t.getVarRequirements()) {
                        if (vr.isVarPlayer() && vr.getId() == VARPLAYER_QUETZAL_PLATFORMS) {
                            varRequirementsContainsVarPlayer = true;
                        }
                    }
                }
            }
        }

        Assert.assertTrue("Should find QUETZAL_WHISTLE transport to Kastori", foundQuetzalWhistleToKastori);
        Assert.assertTrue("QUETZAL_WHISTLE to Kastori should have VarPlayer 4182&8 check via getVarPlayers()", hasCorrectVarPlayerCheck);
        Assert.assertTrue("QUETZAL_WHISTLE to Kastori should have VarPlayer 4182 in getVarRequirements()", varRequirementsContainsVarPlayer);
    }

    /**
     * Verifies that ALL whistle transports have type QUETZAL_WHISTLE (not QUETZAL).
     * This ensures the separate cost threshold is applied correctly.
     */
    @Test
    public void testAllWhistleTransportsHaveCorrectType() {
        Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();

        int whistleCount = 0;
        int wrongTypeCount = 0;

        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            for (Transport t : entry.getValue()) {
                String displayInfo = t.getDisplayInfo();
                if (displayInfo != null && displayInfo.startsWith("Quetzal whistle:")) {
                    whistleCount++;
                    if (!TransportType.QUETZAL_WHISTLE.equals(t.getType())) {
                        wrongTypeCount++;
                    }
                }
            }
        }
        Assert.assertTrue("Should find whistle transports", whistleCount > 0);
        Assert.assertEquals("All whistle transports should have QUETZAL_WHISTLE type", 0, wrongTypeCount);
    }

    /**
     * Verifies that QUETZAL_WHISTLE and QUETZAL have different cost getters.
     */
    @Test
    public void testQuetzalTypesHaveDifferentCostGetters() {
        // Verify both have cost getters
        Assert.assertTrue("QUETZAL should have a cost getter", TransportType.QUETZAL.hasCostGetter());
        Assert.assertTrue("QUETZAL_WHISTLE should have a cost getter", TransportType.QUETZAL_WHISTLE.hasCostGetter());

        // Verify they reference different config methods by checking they're different function instances
        Assert.assertNotEquals("Cost getters should be different instances",
                TransportType.QUETZAL.getCostGetter(),
                TransportType.QUETZAL_WHISTLE.getCostGetter());
    }

    /**
     * Verifies that QUETZAL platform transports are loaded correctly.
     * These are the permutation transports that go between platforms.
     */
    @Test
    public void testQuetzalPlatformTransportsLoaded() {
        Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();

        int platformCount = 0;
        int aldarinOrigins = 0;

        // Aldarin platform location
        int aldarinPacked = WorldPointUtil.packWorldPoint(1389, 2901, 0);

        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            int origin = entry.getKey();
            for (Transport t : entry.getValue()) {
                if (TransportType.QUETZAL.equals(t.getType())) {
                    platformCount++;
                    if (origin == aldarinPacked) {
                        aldarinOrigins++;
                    }
                }
            }
        }

        Assert.assertTrue("Should find QUETZAL platform transports", platformCount > 0);
        // From Aldarin, you should be able to reach all other platforms
        Assert.assertTrue("Should have multiple transports from Aldarin", aldarinOrigins >= 7);
    }

    /**
     * Verifies that QUETZAL_WHISTLE transports have item requirements.
     * The whistle requires having a quetzal whistle item (29271, 29273, or 29275).
     */
    @Test
    public void testQuetzalWhistleHasItemRequirements() {
        Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();

        int whistleCount = 0;
        int whistlesWithItemReqs = 0;

        for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet()) {
            for (Transport t : entry.getValue()) {
                if (TransportType.QUETZAL_WHISTLE.equals(t.getType())) {
                    whistleCount++;
                    if (t.getItemRequirements() != null) {
                        whistlesWithItemReqs++;
                    }
                }
            }
        }

        Assert.assertTrue("Should find whistle transports", whistleCount > 0);
        Assert.assertEquals("All whistle transports should have item requirements",
                whistleCount, whistlesWithItemReqs);
    }
}

