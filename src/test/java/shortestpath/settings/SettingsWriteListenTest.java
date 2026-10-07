package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.Test;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import shortestpath.TestShortestPathConfig;
import shortestpath.transport.PohNexusPortal;

/**
 * Pins the panel write/listen/keyed-read contract on {@link Settings} —
 * the service surface the reworked panel consumes in place of its own
 * writeConfig/registerSync machinery.
 *
 * <p>The ordering contract mirrors the panel machinery it replaces:
 * {@code write()} is the sole mutation path (it delegates straight to
 * {@code ConfigManager.setConfiguration}), the {@link ConfigChanged} a write
 * produces is classified write-originated so it republishes views and yields
 * a normal {@link ConfigChange} fact but never triggers the external-delivery
 * path for that key, and same-key listeners fire synchronously inside
 * {@code write()}. External (non-write-originated) changes deliver their
 * listeners on the EDT via {@code SwingUtilities.invokeLater}.
 *
 * <p>Keyed reads return the configured value — the panel displays what is
 * stored, so payload overrides must not leak into these reads.
 */
public class SettingsWriteListenTest
{
	private static final String GROUP = "shortestpath";

	private static ConfigChanged event(String key)
	{
		ConfigChanged changed = new ConfigChanged();
		changed.setGroup(GROUP);
		changed.setKey(key);
		return changed;
	}

	/**
	 * Flushes the EDT so every invokeLater-delivered listener has run.
	 */
	private static void flushEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	private static Settings service(ConfigManager configManager, TestShortestPathConfig config)
	{
		return new Settings(configManager, config);
	}

	@Test
	public void writeDelegatesToConfigManager()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());

		settings.write("avoidWilderness", false);

		verify(configManager).setConfiguration(GROUP, "avoidWilderness", false);
	}

	@Test
	public void unknownKeyWriteStillReachesConfigManager()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());

		settings.write("notARegistryKey", 7);

		verify(configManager).setConfiguration(GROUP, "notARegistryKey", 7);
	}

	@Test
	public void sameKeyListenerFiresSynchronouslyInsideWrite()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());
		AtomicInteger fired = new AtomicInteger();

		settings.listen("avoidWilderness", fired::incrementAndGet);
		settings.write("avoidWilderness", false);

		assertEquals("same-key listener ran inside write()", 1, fired.get());
	}

	@Test
	public void multipleListenersPerKeyAllFire()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());
		AtomicInteger first = new AtomicInteger();
		AtomicInteger second = new AtomicInteger();

		settings.listen("avoidWilderness", first::incrementAndGet);
		settings.listen("avoidWilderness", second::incrementAndGet);
		settings.write("avoidWilderness", false);

		assertEquals(1, first.get());
		assertEquals(1, second.get());
	}

	@Test
	public void differentKeyListenerDoesNotFireOnWrite() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());
		AtomicInteger fired = new AtomicInteger();

		settings.listen("avoidWilderness", fired::incrementAndGet);
		settings.write("drawMap", true);
		flushEdt();

		assertEquals("a write to another key never touches this listener", 0, fired.get());
	}

	/**
	 * The echo a write produces must still republish views and classify, but
	 * the listener fires exactly once — from the synchronous in-write
	 * delivery, not again through the external path.
	 */
	@Test
	public void writeOriginatedEchoDoesNotDoubleDeliver()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		TestShortestPathConfig config = new TestShortestPathConfig();
		Settings settings = service(configManager, config);
		AtomicInteger fired = new AtomicInteger();
		AtomicReference<ConfigChange> classified = new AtomicReference<>();

		// any(Object.class) pins the (String, String, Object) overload —
		// bare any() would resolve to the (String, String, String) one.
		doAnswer(invocation ->
		{
			// Reproduce ConfigManager's synchronous dispatch: mutate the
			// configured value, then hand the event back to the service.
			config.setCalculationCutoffValue(9);
			ConfigChanged echo = event(invocation.getArgument(1));
			classified.set(settings.onConfigChanged(echo));
			return null;
		}).when(configManager).setConfiguration(anyString(), anyString(), any(Object.class));

		settings.listen("avoidWilderness", fired::incrementAndGet);
		settings.write("avoidWilderness", false);

		assertEquals("listener fired once, inside write()", 1, fired.get());
		ConfigChange fact = classified.get();
		assertNotNull("the write's echo still ran classification", fact);
		assertTrue("write-originated echo still classifies effects",
			fact.getEffects().contains(Effect.ROUTE_INVALIDATING));
		assertEquals("the echo still republished the views",
			9, settings.lifecycle().calculationCutoff());
	}

	/**
	 * Marshalling is pinned deterministically by consuming the event on the
	 * EDT itself: an invokeLater delivery cannot run before the current EDT
	 * task finishes, so the listener must still be pending when the event
	 * returns — an inline (unmarshalled) delivery would have already fired.
	 */
	@Test
	public void externalChangeDeliversOnEdtNotInline() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());
		AtomicInteger fired = new AtomicInteger();

		settings.listen("avoidWilderness", fired::incrementAndGet);
		SwingUtilities.invokeAndWait(() ->
		{
			settings.onConfigChanged(event("avoidWilderness"));
			assertEquals("external delivery is marshalled, not inline", 0, fired.get());
		});
		flushEdt();
		assertEquals("listener ran on the EDT", 1, fired.get());
	}

	@Test
	public void externalChangeForOtherKeyStillDeliversDuringWrite() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());
		AtomicInteger ownKey = new AtomicInteger();
		AtomicInteger otherKey = new AtomicInteger();

		doAnswer(invocation ->
		{
			settings.onConfigChanged(event(invocation.getArgument(1)));
			// An unrelated external event arriving mid-write is not
			// write-originated — its listeners still get EDT delivery.
			settings.onConfigChanged(event("drawMap"));
			return null;
		}).when(configManager).setConfiguration(anyString(), anyString(), any(Object.class));

		settings.listen("avoidWilderness", ownKey::incrementAndGet);
		settings.listen("drawMap", otherKey::incrementAndGet);
		settings.write("avoidWilderness", false);
		flushEdt();

		assertEquals("same-key listener fired once inside write()", 1, ownKey.get());
		assertEquals("external event for another key still delivered", 1, otherKey.get());
	}

	@Test
	public void foreignGroupEventDeliversNothing() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());
		AtomicInteger fired = new AtomicInteger();

		settings.listen("avoidWilderness", fired::incrementAndGet);
		ConfigChanged foreign = event("avoidWilderness");
		foreign.setGroup("someOtherPlugin");
		assertNull(settings.onConfigChanged(foreign));
		flushEdt();

		assertEquals(0, fired.get());
	}

	@Test
	public void keyedReadsReturnConfiguredNotEffective()
	{
		TestShortestPathConfig config = new TestShortestPathConfig();
		Settings settings = Settings.wrap(config);
		assertTrue("configured default", config.avoidWilderness());

		settings.applyOverrides(Map.of("avoidWilderness", false));
		assertFalse("effective read sees the override",
			settings.effective().avoidWilderness());

		assertEquals("keyed read returns the configured value",
			true, settings.configuredValue("avoidWilderness"));
		assertTrue(settings.configuredBool("avoidWilderness"));
	}

	@Test
	public void configuredBoolReadsTheConfiguredValue()
	{
		TestShortestPathConfig config = new TestShortestPathConfig();
		Settings settings = Settings.wrap(config);
		assertFalse(settings.configuredBool("includeBankPath"));

		config.setIncludeBankPathValue(true);
		assertTrue(settings.configuredBool("includeBankPath"));
	}

	@Test
	public void configuredSetPassesThroughRaw()
	{
		Set<PohNexusPortal> portals = EnumSet.of(PohNexusPortal.VARROCK);
		TestShortestPathConfig config = new TestShortestPathConfig()
		{
			@Override
			public Set<PohNexusPortal> pohNexusPortals()
			{
				return portals;
			}
		};
		Settings settings = Settings.wrap(config);

		assertSame("keyed read returns the stored set untouched",
			portals, settings.configuredValue("pohNexusPortals"));
		assertSame(portals, settings.configuredSet("pohNexusPortals"));
	}

	@Test
	public void writePassesSetValuesThroughUntouched()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		Settings settings = service(configManager, new TestShortestPathConfig());
		Set<PohNexusPortal> portals = EnumSet.of(PohNexusPortal.VARROCK);

		settings.write("pohNexusPortals", portals);

		verify(configManager).setConfiguration(
			eq(GROUP), eq("pohNexusPortals"), same(portals));
	}

	/**
	 * The hidden built-* keys share a keyName between a read method and a
	 * void setter — the keyed read must resolve to the read method
	 * (parameterCount == 0, non-void return).
	 */
	@Test
	public void hiddenWritePairKeysResolveToReadMethod()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());

		assertEquals("", settings.configuredValue("builtTeleportationBoxes"));
		assertEquals("", settings.configuredValue("builtTeleportationPortalsPoh"));
	}

	@Test
	public void keyedReadOfUnknownKeyThrows()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		try
		{
			settings.configuredValue("notARegistryKey");
			fail("unknown keys must fail loudly");
		}
		catch (IllegalArgumentException expected)
		{
			assertTrue(expected.getMessage().contains("notARegistryKey"));
		}
	}

	@Test
	public void wrapSeamRejectsWrites()
	{
		Settings settings = Settings.wrap(new TestShortestPathConfig());
		try
		{
			settings.write("avoidWilderness", false);
			fail("a wrap-seam service has no config manager to write through");
		}
		catch (UnsupportedOperationException expected)
		{
		}
	}
}
