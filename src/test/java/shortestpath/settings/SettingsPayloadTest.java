package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import shortestpath.TestShortestPathConfig;

/**
 * Pins the {@link Settings} service-level payload contract: apply, clear, and
 * the raw echo. The override map is stored verbatim for the plugin-message
 * echo, copied defensively on apply, and every publication swaps the views
 * atomically with the map so a reader never sees a torn pair.
 */
public class SettingsPayloadTest
{
	@Test
	public void applyReachesViewsAtomically()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		Settings.DisplayView before = settings.display();

		settings.applyOverrides(Map.of("showBankPickupInfo", true, "tileCounterStep", 9));

		assertFalse("held view snapshot keeps the old value", before.showBankPickupInfo());
		assertTrue(settings.display().showBankPickupInfo());
		assertEquals(9, settings.display().tileCounterStep());
		assertTrue(settings.effective().bool(ConfigKey.SHOW_BANK_PICKUP_INFO));
		assertNotSame(before, settings.display());
	}

	@Test
	public void stringCoercionsApplyThroughTheService()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		settings.applyOverrides(Map.of("useTeleportationItems", "Inventory and Bank"));

		// The read shape the transport-type consumer uses: configured base
		// plus payload coercion through the service seam.
		assertEquals(TeleportationItem.INVENTORY_AND_BANK,
			settings.coerceTeleportationItem("useTeleportationItems",
				(TeleportationItem) settings.configuredValue("useTeleportationItems")));
	}

	@Test
	public void clearRestoresConfiguredValuesEverywhere()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		settings.applyOverrides(Map.of("showBankPickupInfo", true, "avoidWilderness", false));

		settings.clearOverrides();

		assertTrue(settings.rawOverrides().isEmpty());
		assertFalse(settings.display().showBankPickupInfo());
		assertEquals(stub.avoidWilderness(), settings.effective().bool(ConfigKey.AVOID_WILDERNESS));
	}

	@Test
	public void rawEchoIsVerbatimIncludingUnknownKeys()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		Map<String, Object> payload = Map.of(
			"avoidWilderness", false,
			"notARealKey", 42,
			"alsoNotReal", "text");
		settings.applyOverrides(payload);

		assertEquals(payload, settings.rawOverrides());
	}

	@Test
	public void rawEchoIsImmutable()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		settings.applyOverrides(Map.of("avoidWilderness", false));

		assertThrows(UnsupportedOperationException.class,
			() -> settings.rawOverrides().put("includeBankPath", true));
	}

	@Test
	public void applyCopiesThePayloadDefensively()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		Map<String, Object> payload = new HashMap<>();
		payload.put("avoidWilderness", false);
		settings.applyOverrides(payload);

		payload.put("includeBankPath", true);

		assertFalse("post-apply mutations of the caller map must not leak in",
			settings.effective().bool(ConfigKey.INCLUDE_BANK_PATH));
		assertFalse(settings.rawOverrides().containsKey("includeBankPath"));
	}

	@Test
	public void emptyPayloadIsANoOp()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		settings.applyOverrides(Map.of("avoidWilderness", false));
		Settings.DisplayView before = settings.display();

		settings.applyOverrides(Map.of());
		settings.applyOverrides(null);

		assertSame("empty payloads must not republish", before, settings.display());
		assertFalse(settings.effective().bool(ConfigKey.AVOID_WILDERNESS));
		assertEquals(Map.of("avoidWilderness", false), settings.rawOverrides());
	}

	@Test
	public void unknownKeysAreInertOnEveryGetter()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		settings.applyOverrides(Map.of("notARealKey", true));

		assertEquals(stub.avoidWilderness(), settings.effective().bool(ConfigKey.AVOID_WILDERNESS));
		assertEquals(stub.includeBankPath(), settings.effective().bool(ConfigKey.INCLUDE_BANK_PATH));
		assertEquals(stub.currencyThreshold(), settings.effective().num(ConfigKey.CURRENCY_THRESHOLD));
		assertEquals(stub.useTeleportationItems(),
			settings.coerceTeleportationItem("useTeleportationItems", stub.useTeleportationItems()));
	}
}
