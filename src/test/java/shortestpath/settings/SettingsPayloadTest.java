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
import shortestpath.settings.TeleportationItem;

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
		Settings.BankView before = settings.bank();

		settings.applyOverrides(Map.of("includeBankPath", true, "costBankVisit", 9));

		assertFalse("held view snapshot keeps the old value", before.includeBankPath());
		assertTrue(settings.bank().includeBankPath());
		assertEquals(9, settings.bank().costBankVisit());
		assertTrue(settings.effective().includeBankPath());
		assertNotSame(before, settings.bank());
	}

	@Test
	public void stringCoercionsApplyThroughTheService()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		settings.applyOverrides(Map.of("useTeleportationItems", "Inventory and Bank"));

		assertEquals(TeleportationItem.INVENTORY_AND_BANK, settings.effective().useTeleportationItems());
	}

	@Test
	public void clearRestoresConfiguredValuesEverywhere()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		settings.applyOverrides(Map.of("includeBankPath", true, "avoidWilderness", false));

		settings.clearOverrides();

		assertTrue(settings.rawOverrides().isEmpty());
		assertFalse(settings.bank().includeBankPath());
		assertEquals(stub.avoidWilderness(), settings.effective().avoidWilderness());
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
			settings.effective().includeBankPath());
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
		assertFalse(settings.effective().avoidWilderness());
		assertEquals(Map.of("avoidWilderness", false), settings.rawOverrides());
	}

	@Test
	public void unknownKeysAreInertOnEveryGetter()
	{
		TestShortestPathConfig stub = new TestShortestPathConfig();
		Settings settings = Settings.wrap(stub);
		settings.applyOverrides(Map.of("notARealKey", true));

		assertEquals(stub.avoidWilderness(), settings.effective().avoidWilderness());
		assertEquals(stub.includeBankPath(), settings.effective().includeBankPath());
		assertEquals(stub.currencyThreshold(), settings.effective().currencyThreshold());
		assertEquals(stub.useTeleportationItems(), settings.effective().useTeleportationItems());
	}
}
