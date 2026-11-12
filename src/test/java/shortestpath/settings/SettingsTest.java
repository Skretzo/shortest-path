package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import net.runelite.client.config.ConfigItem;
import shortestpath.ShortestPathConfig;
import shortestpath.TestShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.poh.PohNexusPortal;
import shortestpath.transport.TransportType;

/** A key's value is its override (when it has the key's type), else its stored value, else its default. */
public class SettingsTest
{
	private final TestShortestPathConfig config = new TestShortestPathConfig();
	private final Settings settings = Settings.wrap(config);

	@Test
	public void overrideWinsOverStoredWinsOverDefault()
	{
		assertEquals(100000, settings.num(ConfigKey.CURRENCY_THRESHOLD));
		config.set("currencyThreshold", 40);
		assertEquals(40, settings.num(ConfigKey.CURRENCY_THRESHOLD));
		settings.applyOverrides(Map.of("currencyThreshold", 70));
		assertEquals(70, settings.num(ConfigKey.CURRENCY_THRESHOLD));
		settings.clearOverrides();
		assertEquals(40, settings.num(ConfigKey.CURRENCY_THRESHOLD));
	}

	@Test
	public void overrideOfAnotherTypeIsIgnored()
	{
		settings.applyOverrides(Map.of("avoidWilderness", "no", "currencyThreshold", true, "colourPath", 7));
		assertTrue(settings.bool(ConfigKey.AVOID_WILDERNESS));
		assertEquals(100000, settings.num(ConfigKey.CURRENCY_THRESHOLD));
		assertEquals(new Color(255, 0, 0), settings.get(ConfigKey.COLOUR_PATH));
	}

	@Test
	public void enumOverridesUseTheDisplayName()
	{
		settings.applyOverrides(Map.of("pathStyle", "Arrow line", "showTileCounter", "REMAINING"));
		assertEquals(TileStyle.ARROW_LINE, settings.get(ConfigKey.PATH_STYLE));
		assertEquals(TileCounter.DISABLED, settings.get(ConfigKey.SHOW_TILE_COUNTER));
	}

	@Test
	public void overridesAreKeptVerbatimForTheEcho()
	{
		Map<String, Object> payload = Map.of("notAKey", 1, "useBoats", false);
		settings.applyOverrides(payload);
		assertEquals(payload, settings.getOverrides());
		settings.applyOverrides(Map.of());
		assertEquals("an empty payload keeps the overrides", payload, settings.getOverrides());
	}

	@Test
	public void configuredIgnoresOverrides()
	{
		settings.applyOverrides(Map.of("avoidWilderness", false));
		assertFalse(settings.bool(ConfigKey.AVOID_WILDERNESS));
		assertEquals(true, settings.configured("avoidWilderness"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void configuredRejectsUnknownKeys()
	{
		settings.configured("notAKey");
	}

	@Test
	public void flagSetDefaultFollowsTheFlagUntilTheSetIsWritten()
	{
		config.set("useTeleportationPortalsPoh", true);
		assertEquals(EnumSet.allOf(PohNexusPortal.class), settings.get(ConfigKey.POH_NEXUS_PORTALS));
		settings.applyOverrides(Map.of("useTeleportationPortalsPoh", false));
		assertEquals(Set.of(), settings.get(ConfigKey.POH_NEXUS_PORTALS));
		assertEquals("the panel shows the configured flag",
			EnumSet.allOf(PohNexusPortal.class), settings.configured("pohNexusPortals"));
		config.setPohNexusPortalsValue(Set.of(PohNexusPortal.VARROCK));
		assertEquals(Set.of(PohNexusPortal.VARROCK), settings.get(ConfigKey.POH_NEXUS_PORTALS));
	}

	@Test
	public void transportTypeKeysHaveRows()
	{
		for (TransportType type : TransportType.values())
		{
			for (String key : new String[]{type.getEnabledKey(), type.getCostKey(), type.differentialCostKey()})
			{
				if (key != null)
				{
					assertNotNull(type + " names " + key, ConfigKey.forKey(key));
				}
			}
		}
	}

	/** Display keys appear in RuneLite's config panel too, so their default is declared twice. */
	@Test
	public void defaultsMatchTheConfigItemDefaults() throws Throwable
	{
		ShortestPathConfig defaults = (ShortestPathConfig) Proxy.newProxyInstance(
			ShortestPathConfig.class.getClassLoader(), new Class<?>[]{ShortestPathConfig.class},
			(proxy, method, args) -> MethodHandles.privateLookupIn(ShortestPathConfig.class, MethodHandles.lookup())
				.unreflectSpecial(method, ShortestPathConfig.class).bindTo(proxy).invokeWithArguments(args));
		Settings unwritten = Settings.wrap(defaults);
		for (Method method : ShortestPathConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null || method.getParameterCount() != 0 || !method.isDefault()
				|| ConfigKey.forKey(item.keyName()) == null)
			{
				continue;
			}
			assertEquals(item.keyName(), method.invoke(defaults), unwritten.configured(item.keyName()));
		}
	}
}
