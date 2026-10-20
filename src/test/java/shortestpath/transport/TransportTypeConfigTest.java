package shortestpath.transport;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;
import shortestpath.ShortestPathConfig;
import shortestpath.settings.ConfigKey;
import shortestpath.TestShortestPathConfig;
import shortestpath.poh.PohNexusPortal;
import shortestpath.settings.TeleportationItem;
import shortestpath.settings.Settings;
import java.util.Set;
import java.util.HashSet;

/**
 * Tests for TransportTypeConfig.
 * These tests ensure that the functional getter mechanism works correctly and
 * that
 * all config getters defined in TransportType work with ShortestPathConfig.
 */
@RunWith(MockitoJUnitRunner.class)
public class TransportTypeConfigTest
{

	TestShortestPathConfig config = new TestShortestPathConfig();

	/**
	 * Verifies that every enabledGetter defined in TransportType returns a boolean
	 * when applied to a config object.
	 */
	@Test
	public void testAllEnabledGettersReturnBoolean()
	{
		setupDefaultMocks();

		List<String> errors = new ArrayList<>();

		for (TransportType type : TransportType.values())
		{
			if (!type.hasEnabledKey())
			{
				continue; // No enabled key for this type, skip
			}

			try
			{
				Boolean result = Settings.wrap(config).configuredBool(type.getEnabledKey());
				assertNotNull("enabledGetter for " + type.name() + " should not return null", result);
			}
			catch (Exception e)
			{
				errors.add(String.format("%s: %s", type.name(), e.getMessage()));
			}
		}

		if (!errors.isEmpty())
		{
			fail("Errors invoking enabledGetters:\n  " + String.join("\n  ", errors));
		}
	}

	/**
	 * Verifies that every costGetter defined in TransportType returns an integer
	 * when applied to a config object.
	 */
	@Test
	public void testAllCostGettersReturnInteger()
	{
		setupDefaultMocks();

		List<String> errors = new ArrayList<>();

		for (TransportType type : TransportType.values())
		{
			if (!type.hasCostKey())
			{
				continue; // No cost key for this type, skip
			}

			try
			{
				Integer result = (Integer) Settings.wrap(config)
					.configuredValue(ConfigKey.forKey(type.getCostKey()).baseRow().getKey());
				assertNotNull("costGetter for " + type.name() + " should not return null", result);
			}
			catch (Exception e)
			{
				errors.add(String.format("%s: %s", type.name(), e.getMessage()));
			}
		}

		if (!errors.isEmpty())
		{
			fail("Errors invoking costGetters:\n  " + String.join("\n  ", errors));
		}
	}

	/**
	 * Verifies that TransportTypeConfig can be constructed and refreshed without
	 * exceptions.
	 */
	@Test
	public void testTransportTypeConfigConstruction()
	{
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
	public void testIsEnabledReturnsConfigValue()
	{
		// Setup default mocks first, then override specific values
		setupDefaultMocks();
		config.set("useAgilityShortcuts", true);
		config.set("useGrappleShortcuts", false);
		config.set("useBoats", true);

		TransportTypeConfig typeConfig = new TransportTypeConfig(config);

		assertTrue("AGILITY_SHORTCUT should be enabled",
			typeConfig.isEnabled(TransportType.AGILITY_SHORTCUT));
		assertFalse("GRAPPLE_SHORTCUT should be disabled",
			typeConfig.isEnabled(TransportType.GRAPPLE_SHORTCUT));
		assertTrue("BOAT should be enabled",
			typeConfig.isEnabled(TransportType.BOAT));
	}

	@Test
	public void testPohPortalMasterToggle()
	{
		setupDefaultMocks();
		config.set("pohNexusPortals", Set.<PohNexusPortal>of());
		TransportTypeConfig typeConfig = new TransportTypeConfig(config);
		assertFalse(typeConfig.isEnabled(TransportType.TELEPORTATION_PORTAL_POH));

		config.set("pohNexusPortals", Set.of(PohNexusPortal.ARDOUGNE));
		typeConfig.refresh();
		assertTrue(typeConfig.isEnabled(TransportType.TELEPORTATION_PORTAL_POH));
	}

	/**
	 * Verifies that types without configKey are enabled based on TeleportationItem
	 * setting.
	 * TELEPORTATION_ITEM and TELEPORTATION_BOX are controlled by
	 * useTeleportationItems config.
	 */
	@Test
	public void testTypesWithoutConfigKeyAreEnabled()
	{
		setupDefaultMocks();
		// When TeleportationItem is not NONE, teleport item types should be enabled
		config.set("useTeleportationItems", TeleportationItem.ALL);

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
	public void testTeleportationItemTypesDisabledWhenNone()
	{
		setupDefaultMocks();
		config.set("useTeleportationItems", TeleportationItem.NONE);

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
	public void testGetTeleportationItemSetting()
	{
		setupDefaultMocks();

		// Test with ALL
		config.set("useTeleportationItems", TeleportationItem.ALL);
		TransportTypeConfig typeConfig = new TransportTypeConfig(config);
		assertEquals(TeleportationItem.ALL, typeConfig.getTeleportationItemSetting());

		// Test with INVENTORY
		config.set("useTeleportationItems", TeleportationItem.INVENTORY);
		typeConfig.refresh();
		assertEquals(TeleportationItem.INVENTORY, typeConfig.getTeleportationItemSetting());

		// Test with NONE
		config.set("useTeleportationItems", TeleportationItem.NONE);
		typeConfig.refresh();
		assertEquals(TeleportationItem.NONE, typeConfig.getTeleportationItemSetting());
	}

	/**
	 * Verifies that all TeleportationItem enum values are handled correctly.
	 */
	@Test
	public void testAllTeleportationItemValuesHandled()
	{
		setupDefaultMocks();

		for (TeleportationItem item : TeleportationItem.values())
		{
			config.set("useTeleportationItems", item);
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
	public void testGetCostReturnsConfigValue()
	{
		// Setup default mocks first, then override specific values
		setupDefaultMocks();
		config.set("costAgilityShortcuts", 100);
		config.set("costBoats", 50);
		config.set("costFairyRings", 0);

		TransportTypeConfig typeConfig = new TransportTypeConfig(config);

		assertEquals("AGILITY_SHORTCUT cost should be 100",
			100, typeConfig.getCost(TransportType.AGILITY_SHORTCUT));
		assertEquals("BOAT cost should be 50",
			50, typeConfig.getCost(TransportType.BOAT));
		assertEquals("FAIRY_RING cost should be 0",
			0, typeConfig.getCost(TransportType.FAIRY_RING));
	}

	/**
	 * Verifies that types without costKey return 0 cost.
	 */
	@Test
	public void testTypesWithoutCostKeyReturnZero()
	{
		setupDefaultMocks();
		TransportTypeConfig typeConfig = new TransportTypeConfig(config);

		// TRANSPORT has no costKey
		assertEquals("TRANSPORT (no costKey) should have 0 cost",
			0, typeConfig.getCost(TransportType.TRANSPORT));
	}

	/**
	 * Verifies that POH nexus portals share the teleportation portal threshold.
	 */
	@Test
	public void testPohNexusPortalsUseTeleportationPortalThreshold()
	{
		setupDefaultMocks();
		config.set("costTeleportationPortals", 25);
		TransportTypeConfig typeConfig = new TransportTypeConfig(config);

		assertEquals(25, typeConfig.getCost(TransportType.TELEPORTATION_PORTAL));
		assertEquals(25, typeConfig.getCost(TransportType.TELEPORTATION_PORTAL_POH));
	}

	/**
	 * Verifies that disableUnless works correctly.
	 */
	@Test
	public void testDisableUnless()
	{
		config.set("useFairyRings", true);
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
	public void testSetEnabled()
	{
		config.set("useBoats", true);
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
	public void testRefreshReloadsFromConfig()
	{
		// Setup default mocks first, then override specific value
		setupDefaultMocks();
		config.set("useCanoes", false);

		TransportTypeConfig typeConfig = new TransportTypeConfig(config);
		assertFalse(typeConfig.isEnabled(TransportType.CANOE));

		// Change mock and refresh
		config.set("useCanoes", true);
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
	public void testTransportTypeConfiguration()
	{
		setupDefaultMocks();

		for (TransportType type : TransportType.values())
		{
			// Verify enabledGetter works if present
			if (type.hasEnabledKey())
			{
				Boolean enabled = Settings.wrap(config).configuredBool(type.getEnabledKey());
				assertNotNull("enabledGetter for " + type.name() + " should return a value", enabled);
			}

			// Verify costGetter works if present
			if (type.hasCostKey())
			{
				Integer cost = (Integer) Settings.wrap(config).configuredValue(ConfigKey.forKey(type.getCostKey()).baseRow().getKey());
				assertNotNull("costGetter for " + type.name() + " should return a value", cost);
			}
		}
	}

	/**
	 * Verifies that config getters can be invoked without errors.
	 * This catches issues like method signature mismatches.
	 */
	@Test
	public void testConfigGettersCanBeInvoked()
	{
		setupDefaultMocks();

		List<String> invocationErrors = new ArrayList<>();

		for (TransportType type : TransportType.values())
		{
			// Test enabledGetter invocation
			if (type.hasEnabledKey())
			{
				try
				{
					Settings.wrap(config).configuredBool(type.getEnabledKey());
				}
				catch (Exception e)
				{
					invocationErrors.add(String.format("%s enabledGetter: %s",
						type.name(), e.getMessage()));
				}
			}

			// Test costGetter invocation
			if (type.hasCostKey())
			{
				try
				{
					Settings.wrap(config).configuredValue(ConfigKey.forKey(type.getCostKey()).baseRow().getKey());
				}
				catch (Exception e)
				{
					invocationErrors.add(String.format("%s costGetter: %s",
						type.name(), e.getMessage()));
				}
			}
		}

		if (!invocationErrors.isEmpty())
		{
			fail("Errors invoking config getters:\n  " + String.join("\n  ", invocationErrors));
		}
	}

	/**
	 * Verifies that all transport types that have a resource path also have proper
	 * config getters.
	 * This ensures new transport types are properly configured.
	 */
	@Test
	public void testResourcePathTypesHaveProperConfig()
	{
		for (TransportType type : TransportType.values())
		{
			if (type.hasResourcePath())
			{
				// Types with resources should typically have a cost getter
				// (except for special cases like TRANSPORT which is the base type)
				if (!type.hasCostKey() && type != TransportType.TRANSPORT)
				{
					// This is just informational, not necessarily an error
					System.out.println("Note: " + type.name() + " has resourcePath but no costGetter");
				}
			}
		}
	}

	/**
	 * Payload overrides stored on the injected Settings must reach the
	 * transport-type enabled and cost reads through their per-type keys.
	 */
	@Test
	public void testPayloadOverridesReachEnabledAndCost()
	{
		setupDefaultMocks();
		Settings settings = Settings.wrap(config);
		settings.applyOverrides(Map.of(
			"useCanoes", false,
			"costBoats", 42));

		TransportTypeConfig typeConfig = new TransportTypeConfig(config, settings);

		assertFalse("CANOE should be disabled by the useCanoes payload",
			typeConfig.isEnabled(TransportType.CANOE));
		assertEquals("BOAT cost should read the costBoats payload",
			42, typeConfig.getCost(TransportType.BOAT));
	}

	/**
	 * A payload for the shared {@code useQuetzals} key must reach both the
	 * QUETZAL and the QUETZAL_WHISTLE rows, which carry the same enabledKey.
	 */
	@Test
	public void testSharedQuetzalEnabledKeyReachesBothRows()
	{
		setupDefaultMocks();
		Settings settings = Settings.wrap(config);
		settings.applyOverrides(Map.of("useQuetzals", false));

		TransportTypeConfig typeConfig = new TransportTypeConfig(config, settings);

		assertFalse(typeConfig.isEnabled(TransportType.QUETZAL));
		assertFalse(typeConfig.isEnabled(TransportType.QUETZAL_WHISTLE));
	}

	/**
	 * QUETZAL_WHISTLE reads its own {@code costQuetzalWhistle} key even though
	 * its cost getter is the shared {@code costQuetzals}.
	 */
	@Test
	public void testQuetzalWhistleCostKeyReachesOnlyTheWhistleRow()
	{
		setupDefaultMocks();
		Settings settings = Settings.wrap(config);
		settings.applyOverrides(Map.of("costQuetzalWhistle", 7));

		TransportTypeConfig typeConfig = new TransportTypeConfig(config, settings);

		assertEquals("QUETZAL_WHISTLE cost should read the costQuetzalWhistle payload",
			7, typeConfig.getCost(TransportType.QUETZAL_WHISTLE));
		assertEquals("QUETZAL cost must not see the whistle-only key",
			0, typeConfig.getCost(TransportType.QUETZAL));
	}

	/**
	 * The {@code costQuetzals} key belongs to the QUETZAL row and must not leak
	 * into the whistle row despite the shared cost getter.
	 */
	@Test
	public void testQuetzalCostKeyDoesNotReachTheWhistleRow()
	{
		setupDefaultMocks();
		Settings settings = Settings.wrap(config);
		settings.applyOverrides(Map.of("costQuetzals", 8));

		TransportTypeConfig typeConfig = new TransportTypeConfig(config, settings);

		assertEquals("QUETZAL cost should read the costQuetzals payload",
			8, typeConfig.getCost(TransportType.QUETZAL));
		assertEquals("QUETZAL_WHISTLE must not see the costQuetzals key",
			0, typeConfig.getCost(TransportType.QUETZAL_WHISTLE));
	}

	/**
	 * With both quetzal cost keys in the payload, each row resolves the value
	 * stored under its own key.
	 */
	@Test
	public void testBothQuetzalCostKeysResolveToTheirOwnRows()
	{
		setupDefaultMocks();
		Settings settings = Settings.wrap(config);
		settings.applyOverrides(Map.of(
			"costQuetzals", 8,
			"costQuetzalWhistle", 7));

		TransportTypeConfig typeConfig = new TransportTypeConfig(config, settings);

		assertEquals(8, typeConfig.getCost(TransportType.QUETZAL));
		assertEquals(7, typeConfig.getCost(TransportType.QUETZAL_WHISTLE));
	}

	/**
	 * The {@code useTeleportationItems} payload value arrives as a display
	 * string and must coerce through TeleportationItem.fromType.
	 */
	@Test
	public void testTeleportationItemOverrideReachesTheSetting()
	{
		setupDefaultMocks();
		config.set("useTeleportationItems", TeleportationItem.NONE);
		Settings settings = Settings.wrap(config);
		settings.applyOverrides(Map.of("useTeleportationItems", "Inventory and Bank"));

		TransportTypeConfig typeConfig = new TransportTypeConfig(config, settings);

		assertEquals(TeleportationItem.INVENTORY_AND_BANK,
			typeConfig.getTeleportationItemSetting());
	}

	/**
	 * Sets up default mock returns for all config methods.
	 * This prevents NPEs when TransportTypeConfig tries to read unmocked methods.
	 */
	private void setupDefaultMocks()
	{
		// TeleportationItem setting (default to ALL for most tests)
		config.set("useTeleportationItems", TeleportationItem.ALL);

		// Enable methods (return true by default)
		config.set("useAgilityShortcuts", true);
		config.set("useGrappleShortcuts", true);
		config.set("useBoats", true);
		config.set("useCanoes", true);
		config.set("useCharterShips", true);
		config.set("useShips", true);
		config.set("useFairyRings", true);
		config.set("useGnomeGliders", true);
		config.set("useHotAirBalloons", true);
		config.set("useMagicCarpets", true);
		config.set("useMagicMushtrees", true);
		config.set("useMinecarts", true);
		config.set("useQuetzals", true);
		config.set("useSeasonalTransports", true);
		config.set("useSpiritTrees", true);
		config.set("useTeleportationLevers", true);
		config.set("useTeleportationMinigames", true);
		config.set("useTeleportationPortals", true);
		config.set("pohNexusPortals", new HashSet<>(java.util.Arrays.asList(PohNexusPortal.values())));
		config.set("useTeleportationSpells", true);
		config.set("useWildernessObelisks", true);
		config.set("useTeleportationSpellsHome", true);

		// Cost methods (return 0 by default)
		config.set("costAgilityShortcuts", 0);
		config.set("costGrappleShortcuts", 0);
		config.set("costBoats", 0);
		config.set("costCanoes", 0);
		config.set("costCharterShips", 0);
		config.set("costShips", 0);
		config.set("costFairyRings", 0);
		config.set("costGnomeGliders", 0);
		config.set("costHotAirBalloons", 0);
		config.set("costMagicCarpets", 0);
		config.set("costMagicMushtrees", 0);
		config.set("costMinecarts", 0);
		config.set("costQuetzals", 0);
		config.set("costSeasonalTransports", 0);
		config.set("costSpiritTrees", 0);
		config.set("costNonConsumableTeleportationItems", 0);
		config.set("costTeleportationBoxes", 0);
		config.set("costTeleportationLevers", 0);
		config.set("costTeleportationMinigames", 0);
		config.set("costTeleportationPortals", 0);
		config.set("costTeleportationSpells", 0);
		config.set("costWildernessObelisks", 0);
		config.set("costTeleportationSpellsHome", 0);
	}
}
