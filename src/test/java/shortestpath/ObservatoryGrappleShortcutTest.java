package shortestpath;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportItems;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for the Observatory Grapple Shortcut transport.
 * Tests the transport at coordinates 2449 3155 0 to 2444 3165 0 with requirements:
 * - 23 Agility
 * - CROSSBOW=1
 * - MITH_GRAPPLE=1
 * - Observatory Quest
 */
public class ObservatoryGrappleShortcutTest {
    
    private Map<Integer, Set<Transport>> transports;
    private static final String TRANSPORT_DATA = 
        "# Origin\tDestination\tmenuOption menuTarget objectID\tSkills\tItems\tVarbits\tVarPlayers\tDuration\tQuests\n" +
        "2449 3155 0\t2444 3165 0\tGrapple Rocks 31850\t23 Agility\tCROSSBOW=1&MITH_GRAPPLE=1\t\t\t\tObservatory Quest\n";
    
    @Before
    public void setUp() {
        transports = new HashMap<>();
        TransportLoader.addTransportsFromContents(transports, TRANSPORT_DATA, TransportType.GRAPPLE_SHORTCUT, 0);
    }
    
    private Transport getTransport() {
        int origin = WorldPointUtil.packWorldPoint(2449, 3155, 0);
        Set<Transport> transportSet = transports.get(origin);
        Assert.assertNotNull("Transport set should exist for origin", transportSet);
        Assert.assertEquals("Should have exactly one transport", 1, transportSet.size());
        return transportSet.iterator().next();
    }
    
    /**
     * Scenario 1: Verify that the transport has the correct 23 Agility requirement
     */
    @Test
    public void testHasAgilityRequirement() {
        Transport transport = getTransport();
        
        int agilityLevel = transport.getSkillLevels()[Skill.AGILITY.ordinal()];
        Assert.assertEquals("Transport should require 23 Agility", 23, agilityLevel);
    }
    
    /**
     * Scenario 2: Verify that the transport requires a crossbow item
     */
    @Test
    public void testHasCrossbowRequirement() {
        Transport transport = getTransport();
        
        TransportItems items = transport.getItemRequirements();
        Assert.assertNotNull("Transport should have item requirements", items);
        
        int[][] requiredItems = items.getItems();
        int[] quantities = items.getQuantities();
        
        Assert.assertEquals("Should have two item requirements (CROSSBOW and MITH_GRAPPLE)", 2, requiredItems.length);
        
        // Find the crossbow requirement
        boolean foundCrossbow = false;
        for (int i = 0; i < requiredItems.length; i++) {
            int[] itemGroup = requiredItems[i];
            // Check if this group contains any crossbow variant
            // The CROSSBOW ItemVariation should expand to multiple crossbow types
            if (itemGroup.length > 1) { // Crossbows have multiple variants
                foundCrossbow = true;
                Assert.assertEquals("Crossbow quantity should be 1", 1, quantities[i]);
                break;
            }
        }
        
        Assert.assertTrue("Transport should require a crossbow", foundCrossbow);
    }
    
    /**
     * Scenario 3: Verify that the transport requires a mithril grapple item
     */
    @Test
    public void testHasMithrilGrappleRequirement() {
        Transport transport = getTransport();
        
        TransportItems items = transport.getItemRequirements();
        Assert.assertNotNull("Transport should have item requirements", items);
        
        int[][] requiredItems = items.getItems();
        int[] quantities = items.getQuantities();
        
        Assert.assertEquals("Should have two item requirements (CROSSBOW and MITH_GRAPPLE)", 2, requiredItems.length);
        
        // Find the mithril grapple requirement
        boolean foundMithGrapple = false;
        for (int i = 0; i < requiredItems.length; i++) {
            int[] itemGroup = requiredItems[i];
            // MITH_GRAPPLE should be a single item (ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE)
            if (itemGroup.length == 1) {
                foundMithGrapple = true;
                Assert.assertEquals("Mithril grapple quantity should be 1", 1, quantities[i]);
                break;
            }
        }
        
        Assert.assertTrue("Transport should require a mithril grapple", foundMithGrapple);
    }
    
    /**
     * Scenario 4: Verify that the transport requires Observatory Quest completion
     */
    @Test
    public void testHasObservatoryQuestRequirement() {
        Transport transport = getTransport();
        
        Set<Quest> quests = transport.getQuests();
        Assert.assertNotNull("Transport should have quest requirements", quests);
        Assert.assertEquals("Should have exactly one quest requirement", 1, quests.size());
        Assert.assertTrue("Should require Observatory Quest", quests.contains(Quest.OBSERVATORY_QUEST));
        Assert.assertTrue("Transport should be quest locked", transport.isQuestLocked());
    }
    
    /**
     * Scenario 5: Verify that other skill levels are not required (should be 0)
     */
    @Test
    public void testNoOtherSkillRequirements() {
        Transport transport = getTransport();
        
        int[] skillLevels = transport.getSkillLevels();
        
        // Check all skills except Agility should be 0
        for (Skill skill : Skill.values()) {
            if (skill != Skill.AGILITY) {
                int level = skillLevels[skill.ordinal()];
                Assert.assertEquals("Skill " + skill.getName() + " should not be required", 0, level);
            }
        }
        
        // Check total level, combat level, and quest points (last 3 indices)
        int totalLevelIndex = Skill.values().length;
        Assert.assertEquals("Total level should not be required", 0, skillLevels[totalLevelIndex]);
        Assert.assertEquals("Combat level should not be required", 0, skillLevels[totalLevelIndex + 1]);
        Assert.assertEquals("Quest points should not be required", 0, skillLevels[totalLevelIndex + 2]);
    }
    
    /**
     * Scenario 6: Verify the transport has correct origin and destination coordinates
     */
    @Test
    public void testTransportCoordinates() {
        Transport transport = getTransport();
        
        int expectedOrigin = WorldPointUtil.packWorldPoint(2449, 3155, 0);
        int expectedDestination = WorldPointUtil.packWorldPoint(2444, 3165, 0);
        
        Assert.assertEquals("Origin should be 2449 3155 0", expectedOrigin, transport.getOrigin());
        Assert.assertEquals("Destination should be 2444 3165 0", expectedDestination, transport.getDestination());
        
        // Verify unpacked coordinates
        Assert.assertEquals("Origin X should be 2449", 2449, WorldPointUtil.unpackWorldX(transport.getOrigin()));
        Assert.assertEquals("Origin Y should be 3155", 3155, WorldPointUtil.unpackWorldY(transport.getOrigin()));
        Assert.assertEquals("Origin plane should be 0", 0, WorldPointUtil.unpackWorldPlane(transport.getOrigin()));
        
        Assert.assertEquals("Destination X should be 2444", 2444, WorldPointUtil.unpackWorldX(transport.getDestination()));
        Assert.assertEquals("Destination Y should be 3165", 3165, WorldPointUtil.unpackWorldY(transport.getDestination()));
        Assert.assertEquals("Destination plane should be 0", 0, WorldPointUtil.unpackWorldPlane(transport.getDestination()));
    }
    
    /**
     * Bonus Scenario: Verify the transport has correct object information
     */
    @Test
    public void testObjectInformation() {
        Transport transport = getTransport();
        
        String objectInfo = transport.getObjectInfo();
        Assert.assertNotNull("Transport should have object info", objectInfo);
        Assert.assertEquals("Object info should match", "Grapple Rocks 31850", objectInfo);
    }
    
    /**
     * Bonus Scenario: Verify the transport type is GRAPPLE_SHORTCUT
     */
    @Test
    public void testTransportType() {
        Transport transport = getTransport();
        
        Assert.assertEquals("Transport type should be GRAPPLE_SHORTCUT", 
            TransportType.GRAPPLE_SHORTCUT, transport.getType());
    }
}
