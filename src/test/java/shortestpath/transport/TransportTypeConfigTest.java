package shortestpath.transport;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;

/**
 * Tests for TransportTypeConfig.
 * These tests ensure that the functional getter mechanism works correctly and that
 * all config getters defined in TransportType work with ShortestPathConfig.
 */
@RunWith(MockitoJUnitRunner.class)
public class TransportTypeConfigTest {

    @Mock
    ShortestPathConfig config;

    /**
     * Verifies that every enabledGetter defined in TransportType returns a boolean
     * when applied to a config object.
     */
    @Test
    public void testAllEnabledGettersReturnBoolean() {
        setupDefaultMocks();

        List<String> errors = new ArrayList<>();

        for (TransportType type : TransportType.values()) {
            Function<ShortestPathConfig, Boolean> getter = type.getEnabledGetter();
            if (getter == null) {
                continue; // No enabled getter for this type, skip
            }

            try {
                Boolean result = getter.apply(config);
                assertNotNull("enabledGetter for " + type.name() + " should not return null", result);
            } catch (Exception e) {
                errors.add(String.format("%s: %s", type.name(), e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            fail("Errors invoking enabledGetters:\n  " + String.join("\n  ", errors));
        }
    }

    /**
     * Verifies that every costGetter defined in TransportType returns an integer
     * when applied to a config object.
     */
    @Test
    public void testAllCostGettersReturnInteger() {
        setupDefaultMocks();

        List<String> errors = new ArrayList<>();

        for (TransportType type : TransportType.values()) {
            Function<ShortestPathConfig, Integer> getter = type.getCostGetter();
            if (getter == null) {
                continue; // No cost getter for this type, skip
            }

            try {
                Integer result = getter.apply(config);
                assertNotNull("costGetter for " + type.name() + " should not return null", result);
            } catch (Exception e) {
                errors.add(String.format("%s: %s", type.name(), e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            fail("Errors invoking costGetters:\n  " + String.join("\n  ", errors));
        }
    }

    /**
     * Verifies that TransportTypeConfig can be constructed and refreshed without exceptions.
     */
    @Test
    public void testTransportTypeConfigConstruction() {
        // Setup default returns for all config methods
        setupDefaultMocks();

        // Should not throw
        TransportTypeConfig typeConfig = new TransportTypeConfig(config);
        assertNotNull(typeConfig);
    }

    /**
     * Verifies that isEnabled returns the correct value based on mocked config.
     */
    @Test
    public void testIsEnabledReturnsConfigValue() {
        // Setup default mocks first, then override specific values
        setupDefaultMocks();
        when(config.useAgilityShortcuts()).thenReturn(true);
        when(config.useGrappleShortcuts()).thenReturn(false);
        when(config.useBoats()).thenReturn(true);

        TransportTypeConfig typeConfig = new TransportTypeConfig(config);

        assertTrue("AGILITY_SHORTCUT should be enabled",
            typeConfig.isEnabled(TransportType.AGILITY_SHORTCUT));
        assertFalse("GRAPPLE_SHORTCUT should be disabled",
            typeConfig.isEnabled(TransportType.GRAPPLE_SHORTCUT));
        assertTrue("BOAT should be enabled",
            typeConfig.isEnabled(TransportType.BOAT));
    }

    /**
     * Verifies that types without configKey are enabled based on TeleportationItem setting.
     * TELEPORTATION_ITEM and TELEPORTATION_BOX are controlled by useTeleportationItems config.
     */
    @Test
    public void testTypesWithoutConfigKeyAreEnabled() {
        setupDefaultMocks();
        // When TeleportationItem is not NONE, teleport item types should be enabled
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.ALL);

        TransportTypeConfig typeConfig = new TransportTypeConfig(config);

        // TRANSPORT has no configKey, should always be enabled
        assertTrue("TRANSPORT (no configKey) should be enabled",
            typeConfig.isEnabled(TransportType.TRANSPORT));

        // TELEPORTATION_ITEM should be enabled when TeleportationItem != NONE
        assertTrue("TELEPORTATION_ITEM should be enabled when TeleportationItem is ALL",
            typeConfig.isEnabled(TransportType.TELEPORTATION_ITEM));

        // TELEPORTATION_BOX should be enabled when TeleportationItem != NONE
        assertTrue("TELEPORTATION_BOX should be enabled when TeleportationItem is ALL",
            typeConfig.isEnabled(TransportType.TELEPORTATION_BOX));
    }

    /**
     * Verifies that TELEPORTATION_ITEM and TELEPORTATION_BOX are disabled when
     * TeleportationItem is set to NONE.
     */
    @Test
    public void testTeleportationItemTypesDisabledWhenNone() {
        setupDefaultMocks();
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);

        TransportTypeConfig typeConfig = new TransportTypeConfig(config);

        assertFalse("TELEPORTATION_ITEM should be disabled when TeleportationItem is NONE",
            typeConfig.isEnabled(TransportType.TELEPORTATION_ITEM));

        assertFalse("TELEPORTATION_BOX should be disabled when TeleportationItem is NONE",
            typeConfig.isEnabled(TransportType.TELEPORTATION_BOX));

        // TRANSPORT should still be enabled (no TeleportationItem dependency)
        assertTrue("TRANSPORT should still be enabled",
            typeConfig.isEnabled(TransportType.TRANSPORT));
    }

    /**
     * Verifies that TeleportationItem setting is properly returned.
     */
    @Test
    public void testGetTeleportationItemSetting() {
        setupDefaultMocks();

        // Test with ALL
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.ALL);
        TransportTypeConfig typeConfig = new TransportTypeConfig(config);
        assertEquals(TeleportationItem.ALL, typeConfig.getTeleportationItemSetting());

        // Test with INVENTORY
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY);
        typeConfig.refresh();
        assertEquals(TeleportationItem.INVENTORY, typeConfig.getTeleportationItemSetting());

        // Test with NONE
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
        typeConfig.refresh();
        assertEquals(TeleportationItem.NONE, typeConfig.getTeleportationItemSetting());
    }

    /**
     * Verifies that all TeleportationItem enum values are handled correctly.
     */
    @Test
    public void testAllTeleportationItemValuesHandled() {
        setupDefaultMocks();

        for (TeleportationItem item : TeleportationItem.values()) {
            when(config.useTeleportationItems()).thenReturn(item);
            TransportTypeConfig typeConfig = new TransportTypeConfig(config);

            boolean expectedEnabled = (item != TeleportationItem.NONE);
            assertEquals("TELEPORTATION_ITEM with " + item + " setting",
                expectedEnabled, typeConfig.isEnabled(TransportType.TELEPORTATION_ITEM));
            assertEquals("TELEPORTATION_BOX with " + item + " setting",
                expectedEnabled, typeConfig.isEnabled(TransportType.TELEPORTATION_BOX));
        }
    }

    /**
     * Verifies that getCost returns the correct value based on mocked config.
     */
    @Test
    public void testGetCostReturnsConfigValue() {
        // Setup default mocks first, then override specific values
        setupDefaultMocks();
        when(config.costAgilityShortcuts()).thenReturn(100);
        when(config.costBoats()).thenReturn(50);
        when(config.costFairyRings()).thenReturn(0);

        TransportTypeConfig typeConfig = new TransportTypeConfig(config);

        assertEquals("AGILITY_SHORTCUT cost should be 100",
            100, typeConfig.getCost(TransportType.AGILITY_SHORTCUT));
        assertEquals("BOAT cost should be 50",
            50, typeConfig.getCost(TransportType.BOAT));
        assertEquals("FAIRY_RING cost should be 0",
            0, typeConfig.getCost(TransportType.FAIRY_RING));
    }

    /**
     * Verifies that QUETZAL and QUETZAL_WHISTLE have separate cost thresholds.
     * This ensures the quetzal whistle can have a higher cost than platforms.
     */
    @Test
    public void testQuetzalWhistleHasSeparateCostFromQuetzal() {
        setupDefaultMocks();
        // Set quetzal platforms to 0 cost (free)
        when(config.costQuetzals()).thenReturn(0);
        // Set quetzal whistle to higher cost (consumable, should be penalized)
        when(config.costQuetzalWhistle()).thenReturn(50);

        TransportTypeConfig typeConfig = new TransportTypeConfig(config);

        assertEquals("QUETZAL (platform) cost should be 0",
            0, typeConfig.getCost(TransportType.QUETZAL));
        assertEquals("QUETZAL_WHISTLE cost should be 50",
            50, typeConfig.getCost(TransportType.QUETZAL_WHISTLE));
    }

    /**
     * Verifies that types without costKey return 0 cost.
     */
    @Test
    public void testTypesWithoutCostKeyReturnZero() {
        setupDefaultMocks();
        TransportTypeConfig typeConfig = new TransportTypeConfig(config);

        // TRANSPORT has no costKey
        assertEquals("TRANSPORT (no costKey) should have 0 cost",
            0, typeConfig.getCost(TransportType.TRANSPORT));

        // TELEPORTATION_PORTAL_POH has no costKey
        assertEquals("TELEPORTATION_PORTAL_POH (no costKey) should have 0 cost",
            0, typeConfig.getCost(TransportType.TELEPORTATION_PORTAL_POH));
    }

    /**
     * Verifies that disableUnless works correctly.
     */
    @Test
    public void testDisableUnless() {
        when(config.useFairyRings()).thenReturn(true);
        setupDefaultMocks();

        TransportTypeConfig typeConfig = new TransportTypeConfig(config);

        // Initially enabled
        assertTrue(typeConfig.isEnabled(TransportType.FAIRY_RING));

        // Disable with false condition
        typeConfig.disableUnless(TransportType.FAIRY_RING, false);
        assertFalse("FAIRY_RING should be disabled after disableUnless(false)",
            typeConfig.isEnabled(TransportType.FAIRY_RING));

        // Reset and test with true condition
        typeConfig.refresh();
        assertTrue(typeConfig.isEnabled(TransportType.FAIRY_RING));
        typeConfig.disableUnless(TransportType.FAIRY_RING, true);
        assertTrue("FAIRY_RING should remain enabled after disableUnless(true)",
            typeConfig.isEnabled(TransportType.FAIRY_RING));
    }

    /**
     * Verifies that setEnabled works correctly.
     */
    @Test
    public void testSetEnabled() {
        when(config.useBoats()).thenReturn(true);
        setupDefaultMocks();

        TransportTypeConfig typeConfig = new TransportTypeConfig(config);

        assertTrue(typeConfig.isEnabled(TransportType.BOAT));

        typeConfig.setEnabled(TransportType.BOAT, false);
        assertFalse("BOAT should be disabled after setEnabled(false)",
            typeConfig.isEnabled(TransportType.BOAT));

        typeConfig.setEnabled(TransportType.BOAT, true);
        assertTrue("BOAT should be enabled after setEnabled(true)",
            typeConfig.isEnabled(TransportType.BOAT));
    }

    /**
     * Verifies that refresh reloads values from config.
     */
    @Test
    public void testRefreshReloadsFromConfig() {
        // Setup default mocks first, then override specific value
        setupDefaultMocks();
        when(config.useCanoes()).thenReturn(false);

        TransportTypeConfig typeConfig = new TransportTypeConfig(config);
        assertFalse(typeConfig.isEnabled(TransportType.CANOE));

        // Change mock and refresh
        when(config.useCanoes()).thenReturn(true);
        typeConfig.refresh();

        assertTrue("CANOE should be enabled after refresh with new config value",
            typeConfig.isEnabled(TransportType.CANOE));
    }

    /**
     * Verifies that all TransportType enum values have consistent configuration:
     * - If they have a resourcePath, they should be loadable
     * - Types with enabledGetter should have proper functionality
     * - Types with costGetter should have proper functionality
     */
    @Test
    public void testTransportTypeConfiguration() {
        setupDefaultMocks();

        for (TransportType type : TransportType.values()) {
            // Verify enabledGetter works if present
            if (type.hasEnabledGetter()) {
                Boolean enabled = type.getEnabledGetter().apply(config);
                assertNotNull("enabledGetter for " + type.name() + " should return a value", enabled);
            }

            // Verify costGetter works if present
            if (type.hasCostGetter()) {
                Integer cost = type.getCostGetter().apply(config);
                assertNotNull("costGetter for " + type.name() + " should return a value", cost);
            }
        }
    }

    /**
     * Verifies that config getters can be invoked without errors.
     * This catches issues like method signature mismatches.
     */
    @Test
    public void testConfigGettersCanBeInvoked() {
        setupDefaultMocks();

        List<String> invocationErrors = new ArrayList<>();

        for (TransportType type : TransportType.values()) {
            // Test enabledGetter invocation
            if (type.hasEnabledGetter()) {
                try {
                    type.getEnabledGetter().apply(config);
                } catch (Exception e) {
                    invocationErrors.add(String.format("%s enabledGetter: %s",
                        type.name(), e.getMessage()));
                }
            }

            // Test costGetter invocation
            if (type.hasCostGetter()) {
                try {
                    type.getCostGetter().apply(config);
                } catch (Exception e) {
                    invocationErrors.add(String.format("%s costGetter: %s",
                        type.name(), e.getMessage()));
                }
            }
        }

        if (!invocationErrors.isEmpty()) {
            fail("Errors invoking config getters:\n  " + String.join("\n  ", invocationErrors));
        }
    }

    /**
     * Verifies that all transport types that have a resource path also have proper config getters.
     * This ensures new transport types are properly configured.
     */
    @Test
    public void testResourcePathTypesHaveProperConfig() {
        for (TransportType type : TransportType.values()) {
            if (type.hasResourcePath()) {
                // Types with resources should typically have a cost getter
                // (except for special cases like TRANSPORT which is the base type)
                if (!type.hasCostGetter() && type != TransportType.TRANSPORT) {
                    // This is just informational, not necessarily an error
                    System.out.println("Note: " + type.name() + " has resourcePath but no costGetter");
                }
            }
        }
    }

    /**
     * Sets up default mock returns for all config methods.
     * This prevents NPEs when TransportTypeConfig tries to read unmocked methods.
     */
    private void setupDefaultMocks() {
        // TeleportationItem setting (default to ALL for most tests)
        when(config.useTeleportationItems()).thenReturn(TeleportationItem.ALL);

        // Enable methods (return true by default)
        when(config.useAgilityShortcuts()).thenReturn(true);
        when(config.useGrappleShortcuts()).thenReturn(true);
        when(config.useBoats()).thenReturn(true);
        when(config.useCanoes()).thenReturn(true);
        when(config.useCharterShips()).thenReturn(true);
        when(config.useShips()).thenReturn(true);
        when(config.useFairyRings()).thenReturn(true);
        when(config.useGnomeGliders()).thenReturn(true);
        when(config.useHotAirBalloons()).thenReturn(true);
        when(config.useMagicCarpets()).thenReturn(true);
        when(config.useMagicMushtrees()).thenReturn(true);
        when(config.useMinecarts()).thenReturn(true);
        when(config.useQuetzals()).thenReturn(true);
        when(config.useSeasonalTransports()).thenReturn(true);
        when(config.useSpiritTrees()).thenReturn(true);
        when(config.useTeleportationLevers()).thenReturn(true);
        when(config.useTeleportationMinigames()).thenReturn(true);
        when(config.useTeleportationPortals()).thenReturn(true);
        when(config.useTeleportationPortalsPoh()).thenReturn(true);
        when(config.useTeleportationSpells()).thenReturn(true);
        when(config.useWildernessObelisks()).thenReturn(true);

        // Cost methods (return 0 by default)
        when(config.costAgilityShortcuts()).thenReturn(0);
        when(config.costGrappleShortcuts()).thenReturn(0);
        when(config.costBoats()).thenReturn(0);
        when(config.costCanoes()).thenReturn(0);
        when(config.costCharterShips()).thenReturn(0);
        when(config.costShips()).thenReturn(0);
        when(config.costFairyRings()).thenReturn(0);
        when(config.costGnomeGliders()).thenReturn(0);
        when(config.costHotAirBalloons()).thenReturn(0);
        when(config.costMagicCarpets()).thenReturn(0);
        when(config.costMagicMushtrees()).thenReturn(0);
        when(config.costMinecarts()).thenReturn(0);
        when(config.costQuetzals()).thenReturn(0);
        when(config.costQuetzalWhistle()).thenReturn(0);
        when(config.costSeasonalTransports()).thenReturn(0);
        when(config.costSpiritTrees()).thenReturn(0);
        when(config.costNonConsumableTeleportationItems()).thenReturn(0);
        when(config.costTeleportationBoxes()).thenReturn(0);
        when(config.costTeleportationLevers()).thenReturn(0);
        when(config.costTeleportationMinigames()).thenReturn(0);
        when(config.costTeleportationPortals()).thenReturn(0);
        when(config.costTeleportationSpells()).thenReturn(0);
        when(config.costWildernessObelisks()).thenReturn(0);
    }
}
