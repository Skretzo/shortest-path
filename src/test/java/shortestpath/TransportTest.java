package shortestpath;

import org.junit.Test;
import org.junit.Assert;
import org.junit.Before;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Comprehensive unit tests for {@link Transport#addTransportsFromContents} method.
 */
public class TransportTest {

    private Map<Integer, Set<Transport>> transports;

    @Before
    public void setUp() {
        transports = new HashMap<>();
    }

    // Helper method to get the first transport from a set
    private Transport getFirstTransport(Set<Transport> transportSet) {
        Assert.assertFalse("Transport set should not be empty", transportSet.isEmpty());
        return transportSet.iterator().next();
    }
    
    @Test
    public void testBasicTransportParsing() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "3200 3200 0\t3300 3300 0\t5\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int originPacked = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        int destinationPacked = WorldPointUtil.packWorldPoint(3300, 3300, 0);
        
        Assert.assertTrue("Should contain origin", transports.containsKey(originPacked));
        Set<Transport> transportSet = transports.get(originPacked);
        Assert.assertEquals("Should have exactly one transport", 1, transportSet.size());
        
        Transport transport = getFirstTransport(transportSet);
        Assert.assertEquals("Origin should match", originPacked, transport.getOrigin());
        Assert.assertEquals("Destination should match", destinationPacked, transport.getDestination());
        Assert.assertEquals("Duration should match", 5, transport.getDuration());
        Assert.assertEquals("Type should match", TransportType.TRANSPORT, transport.getType());
    }

    @Test
    public void testEmptyAndCommentLines() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "# This is a comment\n" +
                         "\n" +
                         "3200 3200 0\t3300 3300 0\t5\n" +
                         "\n" +
                         "# Another comment\n" +
                         "3400 3400 0\t3500 3500 0\t10\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        Assert.assertEquals("Should have exactly 2 origins", 2, transports.size());
        
        int origin1 = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        int origin2 = WorldPointUtil.packWorldPoint(3400, 3400, 0);
        
        Assert.assertTrue("Should contain first origin", transports.containsKey(origin1));
        Assert.assertTrue("Should contain second origin", transports.containsKey(origin2));

        // Check exact contents for each origin
        int dest1 = WorldPointUtil.packWorldPoint(3300, 3300, 0);
        int dest2 = WorldPointUtil.packWorldPoint(3500, 3500, 0);
        Transport t1 = getFirstTransport(transports.get(origin1));
        Transport t2 = getFirstTransport(transports.get(origin2));
        Assert.assertEquals("Origin1 destination should match", dest1, t1.getDestination());
        Assert.assertEquals("Origin1 duration should match", 5, t1.getDuration());
        Assert.assertEquals("Origin2 destination should match", dest2, t2.getDestination());
        Assert.assertEquals("Origin2 duration should match", 10, t2.getDuration());
    }

    @Test
    public void testMultipleTransportsFromSameOrigin() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "3200 3200 0\t3300 3300 0\t5\n" +
                         "3200 3200 0\t3400 3400 0\t10\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Assert.assertTrue("Should contain origin", transports.containsKey(origin));
        
        Set<Transport> transportSet = transports.get(origin);
        Assert.assertEquals("Should have exactly two transports", 2, transportSet.size());

        int d1 = WorldPointUtil.packWorldPoint(3300, 3300, 0);
        int d2 = WorldPointUtil.packWorldPoint(3400, 3400, 0);
        boolean hasD1 = false, hasD2 = false;
        for (Transport t : transportSet) {
            if (t.getDestination() == d1) {
                hasD1 = true;
                Assert.assertEquals("Duration for first should match", 5, t.getDuration());
            } else if (t.getDestination() == d2) {
                hasD2 = true;
                Assert.assertEquals("Duration for second should match", 10, t.getDuration());
            }
        }
        Assert.assertTrue("Should contain destination 1", hasD1);
        Assert.assertTrue("Should contain destination 2", hasD2);
    }

    @Test
    public void testTeleportDurationAdjustment() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "\t3300 3300 0\t0\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TELEPORTATION_SPELL, 0);
        
        // Teleportation spells have undefined origin, so we need to check the transport differently
        // Since origin is undefined, this should create a transport but not add it to the map
        Assert.assertTrue("No transports should be added for undefined origin teleports", transports.isEmpty());
    }

    @Test
    public void testNonTeleportDurationNotAdjusted() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "3200 3200 0\t3300 3300 0\t0\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        Assert.assertEquals("Duration should remain 0 for non-teleports", 0, transport.getDuration());
    }

    @Test
    public void testPermutationTransports() {
        // Simulating fairy ring transport where origins and destinations are permutations
        String contents = "# Origin\tDestination\tDisplay info\n" +
                         "3100 3100 0\t\tAIQ\n" +
                         "3200 3200 0\t\tBJR\n" +
                         "\t3300 3300 0\tAIQ\n" +
                         "\t3400 3400 0\tBJR\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.FAIRY_RING, 0);
        
        // Should create permutations: AIQ->AIQ, AIQ->BJR, BJR->AIQ, BJR->BJR
        // AIQ->AIQ and BJR->BJR should be filtered out by distance check (same coordinates)
        
        int origin1 = WorldPointUtil.packWorldPoint(3100, 3100, 0);
        int origin2 = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        
        Assert.assertTrue("Should contain first origin", transports.containsKey(origin1));
        Assert.assertTrue("Should contain second origin", transports.containsKey(origin2));
        
        // Each origin should have two transports (to each of the two destinations)
        Set<Transport> transports1 = transports.get(origin1);
        Set<Transport> transports2 = transports.get(origin2);
        
        Assert.assertEquals("Origin 1 should have two transports", 2, transports1.size());
        Assert.assertEquals("Origin 2 should have two transports", 2, transports2.size());

        int dA = WorldPointUtil.packWorldPoint(3300, 3300, 0);
        int dB = WorldPointUtil.packWorldPoint(3400, 3400, 0);
        boolean o1dA = false, o1dB = false;
        for (Transport t : transports1) {
            Assert.assertEquals("Type should be fairy ring", TransportType.FAIRY_RING, t.getType());
            if (t.getDestination() == dA) o1dA = true;
            if (t.getDestination() == dB) o1dB = true;
        }
        Assert.assertTrue("Origin1 should go to 3300,3300,0", o1dA);
        Assert.assertTrue("Origin1 should go to 3400,3400,0", o1dB);
        boolean o2dA = false, o2dB = false;
        for (Transport t : transports2) {
            Assert.assertEquals("Type should be fairy ring", TransportType.FAIRY_RING, t.getType());
            if (t.getDestination() == dA) o2dA = true;
            if (t.getDestination() == dB) o2dB = true;
        }
        Assert.assertTrue("Origin2 should go to 3300,3300,0", o2dA);
        Assert.assertTrue("Origin2 should go to 3400,3400,0", o2dB);
    }

    @Test
    public void testPermutationTransportsWithRadius() {
        // Test that close coordinates are filtered out based on radius threshold
        String contents = "# Origin\tDestination\tDisplay info\n" +
                         "3100 3100 0\t\tLocation1\n" +
                         "3200 3200 0\t\tLocation2\n" +
                         "\t3101 3101 0\tLocation1\n" +  // Very close to first origin
                         "\t3400 3400 0\tLocation2\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.SPIRIT_TREE, 5);
        
        int origin1 = WorldPointUtil.packWorldPoint(3100, 3100, 0);
        int origin2 = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        
        Assert.assertTrue("Should contain first origin", transports.containsKey(origin1));
        Assert.assertTrue("Should contain second origin", transports.containsKey(origin2));
        
        // Origin 1 should only have transport to far destination (3400, 3400, 0)
        // because (3101, 3101, 0) is within radius threshold
        Set<Transport> transports1 = transports.get(origin1);
        Assert.assertEquals("Origin 1 should have one transport", 1, transports1.size());
        
        Transport transport1 = getFirstTransport(transports1);
        int expectedDestination = WorldPointUtil.packWorldPoint(3400, 3400, 0);
        Assert.assertEquals("Should transport to far destination", expectedDestination, transport1.getDestination());
    }

    @Test
    public void testOriginOnlyTransport() {
        // Test transport with origin but no destination (should not be added to map)
        String contents = "# Origin\tDestination\tDisplay info\n" +
                         "3100 3100 0\t\tOriginOnly\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.FAIRY_RING, 0);
        
        // No destinations available, so no transports should be created
        Assert.assertTrue("No transports should be created without destinations", transports.isEmpty());
    }

    @Test
    public void testDestinationOnlyTransport() {
        // Test transport with destination but no origin (should not be added to map)
        String contents = "# Origin\tDestination\tDisplay info\n" +
                         "\t3100 3100 0\tDestinationOnly\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.FAIRY_RING, 0);
        
        // No origins available, so no transports should be created
        Assert.assertTrue("No transports should be created without origins", transports.isEmpty());
    }

    @Test
    public void testSkillRequirementsParsing() {
        String contents = "# Origin\tDestination\tSkills\n" +
                         "3200 3200 0\t3300 3300 0\t50 Agility;70 Strength\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.AGILITY_SHORTCUT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        // Check skill requirements
        Assert.assertEquals("Agility level should be 50", 50, transport.getSkillLevels()[Skill.AGILITY.ordinal()]);
        Assert.assertEquals("Strength level should be 70", 70, transport.getSkillLevels()[Skill.STRENGTH.ordinal()]);
        Assert.assertEquals("Attack level should be 0 (default)", 0, transport.getSkillLevels()[Skill.ATTACK.ordinal()]);
    }

    @Test
    public void testSpecialSkillRequirements() {
        String contents = "# Origin\tDestination\tSkills\n" +
                         "3200 3200 0\t3300 3300 0\t1500 Total;100 Combat;200 Quest\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        int[] skillLevels = transport.getSkillLevels();
        int totalLevelIndex = Skill.values().length;
        int combatLevelIndex = Skill.values().length + 1;
        int questPointsIndex = Skill.values().length + 2;
        
        Assert.assertEquals("Total level should be 1500", 1500, skillLevels[totalLevelIndex]);
        Assert.assertEquals("Combat level should be 100", 100, skillLevels[combatLevelIndex]);
        Assert.assertEquals("Quest points should be 200", 200, skillLevels[questPointsIndex]);
    }

    @Test
    public void testEmptySkillRequirements() {
        String contents = "# Origin\tDestination\tSkills\n" +
                         "3200 3200 0\t3300 3300 0\t\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        // All skill levels should be 0 (default)
        for (int level : transport.getSkillLevels()) {
            Assert.assertEquals("All skill levels should be 0", 0, level);
        }
    }

    @Test
    public void testMixedSkillRequirements() {
        String contents = "# Origin\tDestination\tSkills\n" +
                         "3200 3200 0\t3300 3300 0\t25 Magic;60 Ranged;1200 Total\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Assert.assertEquals("Magic level should be 25", 25, transport.getSkillLevels()[Skill.MAGIC.ordinal()]);
        Assert.assertEquals("Ranged level should be 60", 60, transport.getSkillLevels()[Skill.RANGED.ordinal()]);
        
        int totalLevelIndex = Skill.values().length;
        Assert.assertEquals("Total level should be 1200", 1200, transport.getSkillLevels()[totalLevelIndex]);
        
        // Other skills should be 0
        Assert.assertEquals("Attack should be 0", 0, transport.getSkillLevels()[Skill.ATTACK.ordinal()]);
    }

    @Test
    public void testSimpleItemRequirements() {
        String contents = "# Origin\tDestination\tItems\n" +
                         "3200 3200 0\t3300 3300 0\t995=5\n";  // 5 coins
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        TransportItems items = transport.getItemRequirements();
        Assert.assertNotNull("Item requirements should not be null", items);
        
        int[][] requiredItems = items.getItems();
        int[] quantities = items.getQuantities();
        
        Assert.assertEquals("Should have one item requirement", 1, requiredItems.length);
        Assert.assertEquals("Should have one quantity requirement", 1, quantities.length);
        Assert.assertEquals("Item should be coins (995)", 995, requiredItems[0][0]);
        Assert.assertEquals("Quantity should be 5", 5, quantities[0]);
    }

    @Test
    public void testMultipleItemRequirementsWithAnd() {
        String contents = "# Origin\tDestination\tItems\n" +
                         "3200 3200 0\t3300 3300 0\t995=5&561=10\n";  // 5 coins AND 10 nature runes
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        TransportItems items = transport.getItemRequirements();
        int[][] requiredItems = items.getItems();
        int[] quantities = items.getQuantities();
        
        Assert.assertEquals("Should have two item requirements", 2, requiredItems.length);
        Assert.assertEquals("Should have two quantity requirements", 2, quantities.length);
        
        // Find coins and nature runes in the requirements
        boolean foundCoins = false, foundNatureRunes = false;
        for (int i = 0; i < requiredItems.length; i++) {
            if (requiredItems[i][0] == 995) {
                foundCoins = true;
                Assert.assertEquals("Coins quantity should be 5", 5, quantities[i]);
            } else if (requiredItems[i][0] == 561) {
                foundNatureRunes = true;
                Assert.assertEquals("Nature runes quantity should be 10", 10, quantities[i]);
            }
        }
        Assert.assertTrue("Should find coins requirement", foundCoins);
        Assert.assertTrue("Should find nature runes requirement", foundNatureRunes);
    }

    @Test
    public void testItemRequirementsWithOr() {
        String contents = "# Origin\tDestination\tItems\n" +
                         "3200 3200 0\t3300 3300 0\t995=5|561=10\n";  // 5 coins OR 10 nature runes
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        TransportItems items = transport.getItemRequirements();
        int[][] requiredItems = items.getItems();
        int[] quantities = items.getQuantities();
        
        Assert.assertEquals("Should have one item requirement (OR group)", 1, requiredItems.length);
        Assert.assertEquals("Should have one quantity requirement", 1, quantities.length);
        
        // The OR group should contain both items
        int[] itemGroup = requiredItems[0];
        Assert.assertEquals("Should have two items in OR group", 2, itemGroup.length);
        
        // Should contain both coins and nature runes
        boolean hasCoins = false, hasNatureRunes = false;
        for (int itemId : itemGroup) {
            if (itemId == 995) hasCoins = true;
            if (itemId == 561) hasNatureRunes = true;
        }
        Assert.assertTrue("Should contain coins", hasCoins);
        Assert.assertTrue("Should contain nature runes", hasNatureRunes);
        
        // Quantity should be the maximum (10)
        Assert.assertEquals("Quantity should be max of OR group", 10, quantities[0]);
    }

    @Test
    public void testItemVariationsRequirements() {
        String contents = "# Origin\tDestination\tItems\n" +
                         "3200 3200 0\t3300 3300 0\tCOINS=100\n";  // Using item variation name
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        TransportItems items = transport.getItemRequirements();
        Assert.assertNotNull("Item requirements should not be null", items);
        
        int[][] requiredItems = items.getItems();
        int[] quantities = items.getQuantities();
        
        Assert.assertEquals("Should have one item requirement", 1, requiredItems.length);
        Assert.assertEquals("Quantity should be 100", 100, quantities[0]);
        
        // Should resolve to the item IDs from the COINS variation
        Assert.assertTrue("Should have at least one item", requiredItems[0].length > 0);
    }

    @Test
    public void testEmptyItemRequirements() {
        String contents = "# Origin\tDestination\tItems\n" +
                         "3200 3200 0\t3300 3300 0\t\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Assert.assertNull("Item requirements should be null", transport.getItemRequirements());
    }

    @Test
    public void testComplexItemRequirements() {
        String contents = "# Origin\tDestination\tItems\n" +
                         "3200 3200 0\t3300 3300 0\t995=50&561=5|556=10&557=3\n";  // Complex AND/OR combination
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        TransportItems items = transport.getItemRequirements();
        Assert.assertNotNull("Item requirements should not be null", items);
        
        int[][] requiredItems = items.getItems();
        int[] quantities = items.getQuantities();
        
        // Should have 3 item groups: coins AND (nature|air) runes AND earth runes
        Assert.assertEquals("Should have three item groups", 3, requiredItems.length);
        Assert.assertEquals("Should have three quantities", 3, quantities.length);
    }

    @Test
    public void testSingleQuestRequirement() {
        String contents = "# Origin\tDestination\tQuests\n" +
                         "3200 3200 0\t3300 3300 0\tSea Slug\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Set<Quest> quests = transport.getQuests();
        Assert.assertEquals("Should have one quest requirement", 1, quests.size());
        Assert.assertTrue("Should contain Sea Slug quest", quests.contains(Quest.SEA_SLUG));
        Assert.assertTrue("Should be quest locked", transport.isQuestLocked());
    }

    @Test
    public void testMultipleQuestRequirements() {
        String contents = "# Origin\tDestination\tQuests\n" +
                         "3200 3200 0\t3300 3300 0\tSea Slug;Rum Deal\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Set<Quest> quests = transport.getQuests();
        Assert.assertEquals("Should have two quest requirements", 2, quests.size());
        Assert.assertTrue("Should contain Sea Slug quest", quests.contains(Quest.SEA_SLUG));
        Assert.assertTrue("Should contain Rum Deal quest", quests.contains(Quest.RUM_DEAL));
        Assert.assertTrue("Should be quest locked", transport.isQuestLocked());
    }

    @Test
    public void testNoQuestRequirements() {
        String contents = "# Origin\tDestination\tQuests\n" +
                         "3200 3200 0\t3300 3300 0\t\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Set<Quest> quests = transport.getQuests();
        Assert.assertTrue("Should have no quest requirements", quests.isEmpty());
        Assert.assertFalse("Should not be quest locked", transport.isQuestLocked());
    }

    @Test
    public void testInvalidQuestName() {
        String contents = "# Origin\tDestination\tQuests\n" +
                         "3200 3200 0\t3300 3300 0\tNonExistentQuest\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Set<Quest> quests = transport.getQuests();
        Assert.assertTrue("Should have no quest requirements for invalid quest", quests.isEmpty());
        Assert.assertFalse("Should not be quest locked", transport.isQuestLocked());
    }

    @Test
    public void testVarbitRequirementsEqual() {
        String contents = "# Origin\tDestination\tVarbits\n" +
                         "3200 3200 0\t3300 3300 0\t123=5\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Set<TransportVarbit> varbits = transport.getVarbits();
        Assert.assertEquals("Should have one varbit requirement", 1, varbits.size());
        
        TransportVarbit varbit = varbits.iterator().next();
        Assert.assertEquals("Varbit ID should be 123", 123, varbit.getId());
    }

    @Test
    public void testVarbitRequirementsMultipleTypes() {
        String contents = "# Origin\tDestination\tVarbits\n" +
                         "3200 3200 0\t3300 3300 0\t123=5;456>10;789<20;999&1;888@60\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Set<TransportVarbit> varbits = transport.getVarbits();
        Assert.assertEquals("Should have five varbit requirements", 5, varbits.size());
        
        // Verify all expected varbit IDs are present
        boolean hasEqual = false, hasGreater = false, hasSmaller = false, hasBitSet = false, hasCooldown = false;
        for (TransportVarbit varbit : varbits) {
            switch (varbit.getId()) {
                case 123: hasEqual = true; break;
                case 456: hasGreater = true; break;
                case 789: hasSmaller = true; break;
                case 999: hasBitSet = true; break;
                case 888: hasCooldown = true; break;
            }
        }
        Assert.assertTrue("Should have equal check", hasEqual);
        Assert.assertTrue("Should have greater check", hasGreater);
        Assert.assertTrue("Should have smaller check", hasSmaller);
        Assert.assertTrue("Should have bit set check", hasBitSet);
        Assert.assertTrue("Should have cooldown check", hasCooldown);
    }

    @Test
    public void testVarPlayerRequirements() {
        String contents = "# Origin\tDestination\tVarPlayers\n" +
                         "3200 3200 0\t3300 3300 0\t555=1;666>5\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Set<TransportVarPlayer> varPlayers = transport.getVarPlayers();
        Assert.assertEquals("Should have two varplayer requirements", 2, varPlayers.size());
        
        // Verify expected varplayer IDs are present
        boolean hasVarPlayer555 = false, hasVarPlayer666 = false;
        for (TransportVarPlayer varPlayer : varPlayers) {
            if (varPlayer.getId() == 555) hasVarPlayer555 = true;
            if (varPlayer.getId() == 666) hasVarPlayer666 = true;
        }
        Assert.assertTrue("Should have varplayer 555", hasVarPlayer555);
        Assert.assertTrue("Should have varplayer 666", hasVarPlayer666);
    }

    @Test
    public void testEmptyVarRequirements() {
        String contents = "# Origin\tDestination\tVarbits\tVarPlayers\n" +
                         "3200 3200 0\t3300 3300 0\t\t\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Assert.assertTrue("Should have no varbit requirements", transport.getVarbits().isEmpty());
        Assert.assertTrue("Should have no varplayer requirements", transport.getVarPlayers().isEmpty());
    }

    @Test
    public void testAdditionalTransportFields() {
        String contents = "# Origin\tDestination\tDuration\tDisplay info\tConsumable\tWilderness level\tmenuOption menuTarget objectID\n" +
                         "3200 3200 0\t3300 3300 0\t15\tTest Display\tT\t5\tUse TestObject 12345\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TELEPORTATION_ITEM, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        Assert.assertEquals("Duration should be 15", 15, transport.getDuration());
        Assert.assertEquals("Display info should match", "Test Display", transport.getDisplayInfo());
        Assert.assertTrue("Should be consumable", transport.isConsumable());
        Assert.assertEquals("Wilderness level should be 5", 5, transport.getMaxWildernessLevel());
        Assert.assertEquals("Object info should match", "Use TestObject 12345", transport.getObjectInfo());
    }

    @Test
    public void testConsumableFalseValues() {
        String contents = "# Origin\tDestination\tConsumable\n" +
                         "3200 3200 0\t3300 3300 0\tF\n" +
                         "3300 3300 0\t3400 3400 0\tno\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TELEPORTATION_ITEM, 0);
        
        for (Set<Transport> transportSet : transports.values()) {
            for (Transport transport : transportSet) {
                Assert.assertFalse("Should not be consumable", transport.isConsumable());
            }
        }
    }

    @Test
    public void testMalformedData() {
        String contents = "# Origin\tDestination\tSkills\tItems\tDuration\tWilderness level\n" +
                         "3200 3200 0\t3300 3300 0\tinvalid skill\tinvalid=items\tinvalid_duration\tinvalid_wilderness\n";
        
        // Should not throw exception, just use default values
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        // Should have default values for malformed data
        Assert.assertEquals("Duration should be 0 (default)", 0, transport.getDuration());
        Assert.assertEquals("Wilderness level should be -1 (default)", -1, transport.getMaxWildernessLevel());
        Assert.assertNull("Item requirements should be null", transport.getItemRequirements());
        
        // All skill levels should be 0
        for (int level : transport.getSkillLevels()) {
            Assert.assertEquals("Skill levels should be 0", 0, level);
        }
    }

    @Test
    public void testEmptyFile() {
        String contents = "# Origin\tDestination\n";  // Header only, no data rows
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        Assert.assertTrue("Should have no transports for empty file", transports.isEmpty());
    }

    @Test
    public void testOnlyHeaderFile() {
        String contents = "# Origin\tDestination\tDuration\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        Assert.assertTrue("Should have no transports for header-only file", transports.isEmpty());
    }

    @Test
    public void testMissingFields() {
        String contents = "# Origin\tDestination\tSkills\n" +
                         "3200 3200 0\t3300 3300 0\n";  // Missing skills field value
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        // Should handle missing fields gracefully
        Assert.assertNotNull("Transport should be created", transport);
        Assert.assertEquals("Origin should be correct", origin, transport.getOrigin());
    }

    @Test
    public void testHeaderWithDifferentCommentFormats() {
        String contents1 = "#Origin\tDestination\tDuration\n" +
                          "3200 3200 0\t3300 3300 0\t5\n";
        
        String contents2 = "# Origin\tDestination\tDuration\n" +
                          "3200 3200 0\t3300 3300 0\t5\n";
        
    Transport.addTransportsFromContents(transports, contents1, TransportType.TRANSPORT, 0);
    Map<Integer, Set<Transport>> transports2 = new HashMap<>();
    Transport.addTransportsFromContents(transports2, contents2, TransportType.TRANSPORT, 0);

    int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
    int dest = WorldPointUtil.packWorldPoint(3300, 3300, 0);
    Assert.assertEquals("Both formats should create one origin", 1, transports.size());
    Assert.assertEquals("Both formats should create one origin", 1, transports2.size());
    Assert.assertTrue("First should contain origin", transports.containsKey(origin));
    Assert.assertTrue("Second should contain origin", transports2.containsKey(origin));
    Assert.assertEquals("First transport destination should match", dest, getFirstTransport(transports.get(origin)).getDestination());
    Assert.assertEquals("Second transport destination should match", dest, getFirstTransport(transports2.get(origin)).getDestination());
    }

    @Test
    public void testAgilityShortcutToGrappleShortcutConversion() {
        String contents = "# Origin\tDestination\tSkills\n" +
                         "3200 3200 0\t3300 3300 0\t70 Ranged;75 Strength\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.AGILITY_SHORTCUT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        // Should be converted to GRAPPLE_SHORTCUT because it has Ranged or Strength requirements
        Assert.assertEquals("Should be converted to grapple shortcut", TransportType.GRAPPLE_SHORTCUT, transport.getType());
    }

    @Test
    public void testAgilityShortcutRemainsAgility() {
        String contents = "# Origin\tDestination\tSkills\n" +
                         "3200 3200 0\t3300 3300 0\t70 Agility\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.AGILITY_SHORTCUT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        // Should remain AGILITY_SHORTCUT because no Ranged or Strength requirements
        Assert.assertEquals("Should remain agility shortcut", TransportType.AGILITY_SHORTCUT, transport.getType());
    }

    @Test
    public void testTeleportDurationMinimum() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "\t3300 3300 0\t0\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TELEPORTATION_SPELL, 0);
        
        // Since this is a teleport with undefined origin, it won't be added to the map
        // But we can verify the behavior by checking a regular teleport item
        
        String contents2 = "# Origin\tDestination\tDuration\n" +
                          "3200 3200 0\t3300 3300 0\t0\n";
        
        Map<Integer, Set<Transport>> transports2 = new HashMap<>();
        Transport.addTransportsFromContents(transports2, contents2, TransportType.TELEPORTATION_ITEM, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports2.get(origin));
        
        // Teleports should have minimum duration of 1
        Assert.assertTrue("Teleport duration should be at least 1", transport.getDuration() >= 1);
    }

    @Test
    public void testNonTeleportDurationNotModified() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "3200 3200 0\t3300 3300 0\t0\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.BOAT, 0);
        
        int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        Transport transport = getFirstTransport(transports.get(origin));
        
        // Non-teleports should keep their original duration
        Assert.assertEquals("Non-teleport duration should remain 0", 0, transport.getDuration());
    }

    @Test
    public void testUndefinedOriginTeleport() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "\t3300 3300 0\t5\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TELEPORTATION_ITEM, 0);
        
        // Teleports with undefined origin should not be added to the map
        Assert.assertTrue("No transports should be added for undefined origin", transports.isEmpty());
    }

    @Test
    public void testUndefinedDestinationTeleport() {
        String contents = "# Origin\tDestination\tDuration\n" +
                         "3200 3200 0\t\t5\n";
        
        Transport.addTransportsFromContents(transports, contents, TransportType.TELEPORTATION_ITEM, 0);
        
        // Teleports with undefined destination should not be added to the map  
        Assert.assertTrue("No transports should be added for undefined destination", transports.isEmpty());
    }

    @Test
    public void testRadiusThresholdFiltering() {
        String contents = "# Origin\tDestination\tDisplay info\n" +
                         "3100 3100 0\t\tLocation1\n" +
                         "3200 3200 0\t\tLocation2\n" +
                         "\t3102 3102 0\tLocation1\n" +  // Distance 2.8 from first origin
                         "\t3105 3105 0\tLocation1\n" +  // Distance 7.1 from first origin
                         "\t3400 3400 0\tLocation2\n";
        
        // Test with radius threshold of 5
        Transport.addTransportsFromContents(transports, contents, TransportType.SPIRIT_TREE, 5);
        
        int origin1 = WorldPointUtil.packWorldPoint(3100, 3100, 0);
        int origin2 = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        
        Assert.assertTrue("Should contain first origin", transports.containsKey(origin1));
        Assert.assertTrue("Should contain second origin", transports.containsKey(origin2));
        
        Set<Transport> transports1 = transports.get(origin1);
        Set<Transport> transports2 = transports.get(origin2);

    // Exact expectations given radius=5 and Chebyshev metric with strict '>'
    // Distance to 3102 is 2 (filtered), to 3105 is 5 (filtered), to 3400 is 300 (kept)
    Assert.assertEquals("Origin 1 should have one transport (3400)", 1, transports1.size());
        Assert.assertEquals("Origin 2 should have three transports (3102,3105,3400)", 3, transports2.size());

        int closeDestination = WorldPointUtil.packWorldPoint(3102, 3102, 0);
        int midDestination = WorldPointUtil.packWorldPoint(3105, 3105, 0);
        int farDestination = WorldPointUtil.packWorldPoint(3400, 3400, 0);

        boolean o1hasClose = false, o1hasMid = false, o1hasFar = false;
        for (Transport t : transports1) {
            if (t.getDestination() == closeDestination) o1hasClose = true;
            if (t.getDestination() == midDestination) o1hasMid = true;
            if (t.getDestination() == farDestination) o1hasFar = true;
        }
        Assert.assertFalse("Origin1 should not include very close destination", o1hasClose);
        Assert.assertFalse("Origin1 should not include mid destination at equal threshold", o1hasMid);
        Assert.assertTrue("Origin1 should include far destination", o1hasFar);

        boolean o2hasClose = false, o2hasMid = false, o2hasFar = false;
        for (Transport t : transports2) {
            if (t.getDestination() == closeDestination) o2hasClose = true;
            if (t.getDestination() == midDestination) o2hasMid = true;
            if (t.getDestination() == farDestination) o2hasFar = true;
        }
        Assert.assertTrue("Origin2 should include close destination (not close to origin2)", o2hasClose);
        Assert.assertTrue("Origin2 should include mid destination", o2hasMid);
        Assert.assertTrue("Origin2 should include far destination", o2hasFar);
    }

    @Test
    public void testZeroRadiusThreshold() {
        String contents = "# Origin\tDestination\tDisplay info\n" +
                         "3100 3100 0\t\tLocation1\n" +
                         "3200 3200 0\t\tLocation2\n" +
                         "\t3101 3101 0\tLocation1\n" +  // Very close
                         "\t3400 3400 0\tLocation2\n";
        
        // Test with radius threshold of 0 (no filtering)
        Transport.addTransportsFromContents(transports, contents, TransportType.FAIRY_RING, 0);
        
        int origin1 = WorldPointUtil.packWorldPoint(3100, 3100, 0);
        int origin2 = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        
        Set<Transport> transports1 = transports.get(origin1);
        Set<Transport> transports2 = transports.get(origin2);
        
        // With radius 0, only exact same coordinates should be filtered out
        // All different coordinates should be included
        Assert.assertEquals("Origin 1 should have 2 transports", 2, transports1.size());
        Assert.assertEquals("Origin 2 should have 2 transports", 2, transports2.size());

        int nearDest = WorldPointUtil.packWorldPoint(3101, 3101, 0);
        int farDest = WorldPointUtil.packWorldPoint(3400, 3400, 0);
        boolean o1near = false, o1far = false, o2near = false, o2far = false;
        for (Transport t : transports1) {
            if (t.getDestination() == nearDest) o1near = true;
            if (t.getDestination() == farDest) o1far = true;
        }
        for (Transport t : transports2) {
            if (t.getDestination() == nearDest) o2near = true;
            if (t.getDestination() == farDest) o2far = true;
        }
        Assert.assertTrue("Origin1 should include near destination", o1near);
        Assert.assertTrue("Origin1 should include far destination", o1far);
        Assert.assertTrue("Origin2 should include near destination", o2near);
        Assert.assertTrue("Origin2 should include far destination", o2far);
    }

    @Test
    public void testExactSameCoordinatesFiltered() {
        String contents = "# Origin\tDestination\tDisplay info\n" +
                         "3100 3100 0\t\tLocation1\n" +
                         "\t3100 3100 0\tLocation1\n";  // Exact same coordinates
        
        Transport.addTransportsFromContents(transports, contents, TransportType.FAIRY_RING, 0);
        
        // Should not create transport from location to itself
        Assert.assertTrue("Should not create transport to same location", transports.isEmpty());
    }

    @Test
    public void testLargeRadiusThreshold() {
        String contents = "# Origin\tDestination\tDisplay info\n" +
                         "3100 3100 0\t\tLocation1\n" +
                         "3200 3200 0\t\tLocation2\n" +
                         "\t3150 3150 0\tLocation1\n" +  // Distance ~70
                         "\t3400 3400 0\tLocation2\n";    // Distance much larger
        
        // Test with large radius threshold
        Transport.addTransportsFromContents(transports, contents, TransportType.SPIRIT_TREE, 100);
        
        int origin1 = WorldPointUtil.packWorldPoint(3100, 3100, 0);
        int origin2 = WorldPointUtil.packWorldPoint(3200, 3200, 0);
        
        Set<Transport> transports1 = transports.get(origin1);
        Set<Transport> transports2 = transports.get(origin2);
        
        // With large radius, only very far destinations should remain
        Assert.assertEquals("Origin 1 should have 1 transport (close one filtered)", 1, transports1.size());
        Assert.assertEquals("Origin 2 should have 1 transport (close one filtered)", 1, transports2.size());
        
        // Verify the remaining transport goes to the far destination
        Transport transport1 = getFirstTransport(transports1);
        int farDestination = WorldPointUtil.packWorldPoint(3400, 3400, 0);
        Assert.assertEquals("Should transport to far destination", farDestination, transport1.getDestination());
    }

    @Test
    public void testPermutationOnlyRowsAreIgnored() {
        String contents = "# Origin\tDestination\tDisplay info\n" +
                          "\t\tOnlyLabel\n"; // both origin and destination missing -> permutation-only

        Transport.addTransportsFromContents(transports, contents, TransportType.FAIRY_RING, 0);
        Assert.assertTrue("No transports should be created for permutation-only row", transports.isEmpty());
    }
}
