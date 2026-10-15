package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Color;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import net.runelite.client.config.ConfigItem;
import shortestpath.ShortestPathConfig;
import shortestpath.TestShortestPathConfig;
import shortestpath.TileCounter;
import shortestpath.TileStyle;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.transport.TransportType;

/**
 * Registry-consistency gate for {@link ConfigKey}: the table must classify
 * every config key exactly once and pair each key with the read method its
 * stored value belongs to.
 *
 * <p>Pinned invariants:
 * <ul>
 * <li>every {@code @ConfigItem.keyName} on {@link ShortestPathConfig} has a
 * registry row — including the hidden write-pair keys whose keyName is shared
 * by a getter and a {@code void set*} method — and no row names a key that
 * does not exist;</li>
 * <li>every {@link TransportType#getEnabledKey()}/{@link TransportType#getCostKey()}
 * resolves to a row whose coercion matches the paired getter's return type;</li>
 * <li>the {@code costQuetzalWhistle} override key deliberately pairs with the
 * shared {@code costQuetzals} getter — divergence is asserted by name, not
 * left to naming convention;</li>
 * <li>getter/setter ambiguity resolves to the read method — the reflective
 * rule is {@code parameterCount == 0 && returnType != void}, and the typed
 * registry getters resolve to the same methods by construction.</li>
 * </ul>
 */
public class ConfigKeyTest
{
	/**
	 * Key names that intentionally have no registry row. Currently empty:
	 * every {@code @ConfigItem} is classified — the set exists so a future
	 * exemption is a named decision the test reports, not a silent gap.
	 */
	private static final Set<String> EXEMPTIONS = Set.of();

	/**
	 * All {@code @ConfigItem.keyName} values declared on the config interface,
	 * collected from every annotated method — getters and setters alike.
	 */
	private static Set<String> configItemKeyNames()
	{
		Set<String> keyNames = new TreeSet<>();
		for (Method method : ShortestPathConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				keyNames.add(item.keyName());
			}
		}
		return keyNames;
	}

	/**
	 * The read method carrying {@code keyName}: the annotated member with
	 * {@code parameterCount == 0 && returnType != void}. Keys shared between
	 * a getter and a {@code void set*} write method (the hidden built-* rows)
	 * resolve to the getter; a key with no read method resolves to
	 * {@code null}.
	 */
	private static Method readMethod(String keyName)
	{
		Method read = null;
		for (Method method : ShortestPathConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null || !keyName.equals(item.keyName()))
			{
				continue;
			}
			if (method.getParameterCount() == 0 && method.getReturnType() != void.class)
			{
				if (read != null && !read.equals(method))
				{
					fail("key " + keyName + " has two read methods: "
						+ read.getName() + " and " + method.getName());
				}
				read = method;
			}
		}
		return read;
	}

	/**
	 * The coercion a value of {@code type} requires; {@code null} when the
	 * type is inert to payloads (Coercion.NONE rows).
	 */
	private static Class<?> coercionType(ConfigKey.Coercion coercion)
	{
		switch (coercion)
		{
			case BOOLEAN:
				return boolean.class;
			case INT:
				return int.class;
			case COLOR:
				return Color.class;
			case TELEPORTATION_ITEM:
				return TeleportationItem.class;
			case JEWELLERY_BOX_TIER:
				return JewelleryBoxTier.class;
			case TILE_COUNTER:
				return TileCounter.class;
			case TILE_STYLE:
				return TileStyle.class;
			case NONE:
			default:
				return null;
		}
	}

	private static void assertCoercionMatchesGetter(ConfigKey row)
	{
		Method getter = readMethod(row.getKey());
		assertNotNull("registry key " + row.getKey() + " has no read method", getter);
		Class<?> expected = coercionType(row.getCoercion());
		if (expected == null)
		{
			return; // NONE rows are inert — any return type is acceptable
		}
		assertEquals("coercion of " + row.getKey() + " matches " + getter.getName()
			+ "'s return type", expected, getter.getReturnType());
	}

	@Test
	public void everyConfigItemKeyHasARegistryRow()
	{
		Set<String> keyNames = configItemKeyNames();
		Set<String> rowKeys = new TreeSet<>();
		for (ConfigKey row : ConfigKey.values())
		{
			rowKeys.add(row.getKey());
		}

		Set<String> missing = new TreeSet<>(keyNames);
		missing.removeAll(rowKeys);
		missing.removeAll(EXEMPTIONS);
		assertTrue("config keys with no registry row: " + missing, missing.isEmpty());

		Set<String> dead = new TreeSet<>(rowKeys);
		dead.removeAll(keyNames);
		dead.removeAll(EXEMPTIONS);
		assertTrue("registry rows naming no config key: " + dead, dead.isEmpty());
	}

	@Test
	public void everyRowCoercionMatchesItsPairedGetterType()
	{
		for (ConfigKey row : ConfigKey.values())
		{
			assertCoercionMatchesGetter(row);
		}
	}

	@Test
	public void transportTypeKeysResolveToRows()
	{
		for (TransportType type : TransportType.values())
		{
			String enabledKey = type.getEnabledKey();
			if (enabledKey != null)
			{
				ConfigKey row = ConfigKey.forKey(enabledKey);
				assertNotNull(type + " enabledKey " + enabledKey + " has no row", row);
				assertEquals(type + " enabledKey coercion",
					ConfigKey.Coercion.BOOLEAN, row.getCoercion());
				assertCoercionMatchesGetter(row);
			}
			String costKey = type.getCostKey();
			if (costKey != null)
			{
				ConfigKey row = ConfigKey.forKey(costKey);
				assertNotNull(type + " costKey " + costKey + " has no row", row);
				assertEquals(type + " costKey coercion",
					ConfigKey.Coercion.INT, row.getCoercion());
				assertCoercionMatchesGetter(row);
			}
		}
	}

	/**
	 * The registry getter for a key must read the same method the
	 * transport-type table pairs with that key. A proxy returning a distinct
	 * value per getter makes equivalence meaningful.
	 */
	@Test
	public void transportTypeRowsPairWithTheSameGetters()
	{
		ShortestPathConfig distinct = distinctValueConfig();
		for (TransportType type : TransportType.values())
		{
			if (type.getEnabledKey() != null && type.getEnabledGetter() != null)
			{
				ConfigKey row = ConfigKey.forKey(type.getEnabledKey());
				assertNotNull(row);
				assertEquals(type + " enabledKey " + type.getEnabledKey()
					+ " pairs with the transport table's enabled getter",
					type.getEnabledGetter().apply(distinct), row.getGetter().apply(distinct));
			}
			if (type.getCostKey() != null && type.getCostGetter() != null)
			{
				ConfigKey row = ConfigKey.forKey(type.getCostKey());
				assertNotNull(row);
				assertEquals(type + " costKey " + type.getCostKey()
					+ " pairs with the transport table's cost getter",
					type.getCostGetter().apply(distinct), row.getGetter().apply(distinct));
			}
		}
	}

	/**
	 * QUETZAL_WHISTLE's cost key is {@code costQuetzalWhistle} but the paired
	 * getter is the shared {@code costQuetzals} — asserted by name so the
	 * divergence can never be "fixed" back to a naming convention.
	 */
	@Test
	public void quetzalWhistleCostKeyPairsWithSharedQuetzalGetter()
	{
		assertEquals("costQuetzalWhistle", TransportType.QUETZAL_WHISTLE.getCostKey());
		TestShortestPathConfig config = new TestShortestPathConfig()
		{
			@Override
			public int costQuetzals()
			{
				return 11;
			}

			@Override
			public int costQuetzalWhistle()
			{
				return 22;
			}
		};

		ConfigKey row = ConfigKey.forKey("costQuetzalWhistle");
		assertNotNull("the override key must have its own row", row);
		assertEquals("the row reads the shared quetzal transport cost",
			11, row.getGetter().apply(config));
	}

	/**
	 * Every registry getter applies cleanly against the stub config — the
	 * read methods the rows resolve to are invocable and non-setter shaped.
	 */
	@Test
	public void registryGettersResolveToReadMethods()
	{
		TestShortestPathConfig config = new TestShortestPathConfig();
		for (ConfigKey row : ConfigKey.values())
		{
			Method read = readMethod(row.getKey());
			assertNotNull(row.getKey() + " resolves to a read method", read);
			assertEquals(row.getKey() + " read method takes no arguments",
				0, read.getParameterCount());
			assertTrue(row.getKey() + " read method returns a value",
				read.getReturnType() != void.class);
			row.getGetter().apply(config);
		}
	}

	/**
	 * The hidden write-pair keys share their keyName between a getter and a
	 * {@code void set*} method; resolution must pick the read side both
	 * reflectively and through the service's keyed read.
	 */
	@Test
	public void writePairKeysResolveToTheReadMethod()
	{
		for (String keyName : new String[]{
			"builtTeleportationBoxes", "builtTeleportationPortalsPoh"})
		{
			Method read = readMethod(keyName);
			assertNotNull(keyName + " must resolve to a read method", read);
			assertEquals(keyName, read.getName());
			assertEquals(keyName + " reads a String",
				String.class, read.getReturnType());
			assertEquals("keyed read returns the getter's value",
				"", Settings.wrap(new TestShortestPathConfig()).configuredValue(keyName));
		}
	}

	/**
	 * Keys the panel writes (avoidWilderness / highlightBankPickupItems)
	 * resolve to their read methods — the keyed read returns the configured
	 * value, never invokes a write path.
	 */
	@Test
	public void panelWrittenKeysResolveToReadMethods()
	{
		TestShortestPathConfig config = new TestShortestPathConfig()
		{
			@Override
			public boolean avoidWilderness()
			{
				return false;
			}

			@Override
			public boolean highlightBankPickupItems()
			{
				return true;
			}
		};
		Settings settings = Settings.wrap(config);

		assertEquals(false, settings.configuredValue("avoidWilderness"));
		assertEquals(true, settings.configuredValue("highlightBankPickupItems"));
		assertSame(boolean.class, readMethod("avoidWilderness").getReturnType());
		assertSame(boolean.class, readMethod("highlightBankPickupItems").getReturnType());
	}

	/**
	 * A config proxy whose primitive getters return a distinct value per
	 * method, so getter-equivalence assertions actually discriminate.
	 */
	private static ShortestPathConfig distinctValueConfig()
	{
		Map<String, Integer> ints = new HashMap<>();
		int[] counter = {0};
		return (ShortestPathConfig) Proxy.newProxyInstance(
			ConfigKeyTest.class.getClassLoader(),
			new Class<?>[]{ShortestPathConfig.class},
			(proxy, method, args) ->
			{
				Class<?> type = method.getReturnType();
				if (type == int.class)
				{
					return ints.computeIfAbsent(method.getName(), k -> counter[0] += 1000);
				}
				if (type == boolean.class)
				{
					return method.getName().length() % 2 == 0;
				}
				if (type == double.class)
				{
					return 0.0;
				}
				if (Set.class.isAssignableFrom(type))
				{
					return Set.of();
				}
				if (type == String.class)
				{
					return "";
				}
				if (type.isEnum())
				{
					Object[] constants = type.getEnumConstants();
					return constants == null || constants.length == 0 ? null : constants[0];
				}
				return null;
			});
	}
}
