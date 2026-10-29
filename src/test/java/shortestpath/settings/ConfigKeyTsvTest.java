package shortestpath.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.Test;

import shortestpath.Util;

/**
 * TSV-to-registry parity for {@link ConfigKey}: the bundled
 * {@code config-keys.tsv} is the row source, so a mistyped, missing, or
 * surplus row would silently change a persisted default or a coercion the
 * refresh path relies on. These checks read the resource the same way the
 * enum's loader does ({@link Util#each} with the same path), assert the
 * row set is one-to-one with the constants, re-check every declared
 * column against the resolved row, and exercise the loader's loud
 * posture on malformed, missing, and mis-parsed input.
 */
public class ConfigKeyTsvTest
{
	private static Map<String, String[]> rows()
	{
		return ConfigKey.loadRows("/config-keys.tsv");
	}

	@Test
	public void everyConstantHasExactlyOneTsvRow()
	{
		Map<String, String[]> rows = rows();
		assertEquals("every enum constant needs exactly one TSV row",
			ConfigKey.values().length, rows.size());
		for (ConfigKey row : ConfigKey.values())
		{
			assertTrue("no TSV row for " + row.name(), rows.containsKey(row.name()));
		}
		// The ctor fails loudly on a constant with no row; the enum's
		// static-init valueOf pass fails loudly on a row naming no
		// constant — asserted by resolving every shipped name here.
		for (String name : rows.keySet())
		{
			ConfigKey.valueOf(name);
		}
	}

	@Test
	public void everyRowResolvesItsDeclaredData()
	{
		for (Map.Entry<String, String[]> entry : rows().entrySet())
		{
			String name = entry.getKey();
			String[] cols = entry.getValue();
			ConfigKey row = ConfigKey.valueOf(name);

			assertEquals(name + " key", cols[1], row.getKey());
			assertEquals(name + " coercion",
				ConfigKey.Coercion.valueOf(cols[6]), row.getCoercion());

			Set<Effect> effects = EnumSet.noneOf(Effect.class);
			for (String effect : cols[7].split(","))
			{
				effects.add(Effect.valueOf(effect));
			}
			assertEquals(name + " effects", effects, row.getEffects());

			if (cols[2].isEmpty())
			{
				assertTrue(name + " resolves as a visible row", !row.isKeyed());
				assertNull(name + " carries no persisted type", row.getType());
			}
			else
			{
				assertTrue(name + " resolves as a keyed row", row.isKeyed());
				assertEquals(name + " type", cols[2], row.getType().getSimpleName());
			}
			if (cols[3].isEmpty())
			{
				assertNull(name + " carries no element", row.getElement());
			}
			else
			{
				assertEquals(name + " element", cols[3], row.getElement().getSimpleName());
			}
			assertEquals(name + " base row",
				cols[5].isEmpty() ? name : cols[5], row.baseRow().name());
		}
	}

	/**
	 * The {@code def} column must produce the same default the row
	 * resolves: literals round-trip through {@link ConfigKey#resolveDefault},
	 * and {@code flag:<key>} tokens produce the flag-derived allOf/noneOf
	 * set the lambda carried.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void declaredDefaultsResolveToTheLiteral()
	{
		for (Map.Entry<String, String[]> entry : rows().entrySet())
		{
			String name = entry.getKey();
			String[] cols = entry.getValue();
			if (cols[2].isEmpty())
			{
				continue; // visible rows carry no default
			}
			ConfigKey row = ConfigKey.valueOf(name);
			String token = cols[4];
			if (token.startsWith("flag:"))
			{
				String flagKey = token.substring("flag:".length());
				Function<ConfigKey, Object> base = k ->
					k.getKey().equals(flagKey) ? Boolean.TRUE : null;
				Set<Object> all = (Set<Object>) row.resolveDefault(base);
				assertEquals(name + " flag-true default is the full set",
					EnumSet.allOf(row.getElement()), all);
				Function<ConfigKey, Object> flagOff = k ->
					k.getKey().equals(flagKey) ? Boolean.FALSE : null;
				assertTrue(name + " flag-false default is empty",
					((Set<Object>) row.resolveDefault(flagOff)).isEmpty());
				continue;
			}
			Object resolved = row.resolveDefault(k -> null);
			if (row.getType() == boolean.class)
			{
				assertEquals(name + " boolean def", Boolean.valueOf(token), resolved);
			}
			else if (row.getType() == int.class)
			{
				assertEquals(name + " int def", Integer.valueOf(token), resolved);
			}
			else if (row.getType() == String.class)
			{
				assertEquals(name + " String def", token, resolved);
			}
			else
			{
				assertEquals(name + " enum def", token, ((Enum<?>) resolved).name());
			}
		}
	}

	/**
	 * Every visible row binds a {@link VisibleGetters} entry; the map
	 * carries no binding for a hidden or non-existent row, and the bound
	 * function is the getter the row reports on both read paths.
	 */
	@Test
	public void visibleRowsBindThroughVisibleGetters()
	{
		for (ConfigKey row : ConfigKey.values())
		{
			if (row.isKeyed())
			{
				continue;
			}
			var bound = VisibleGetters.BY_NAME.get(row.name());
			assertNotNull(row.name() + " has no VisibleGetters binding", bound);
			assertSame(row.name() + " getter is the bound function", bound, row.getGetter());
			assertSame(row.name() + " configured getter is the bound function",
				bound, row.getConfiguredGetter());
		}
		for (String name : VisibleGetters.BY_NAME.keySet())
		{
			ConfigKey row = ConfigKey.valueOf(name);
			assertTrue(name + " binding targets a non-visible row", !row.isKeyed());
		}
	}

	@Test
	public void malformedArityIsRejected()
	{
		assertThrows("a row with the wrong column count must fail the load",
			IllegalStateException.class,
			() -> ConfigKey.loadRows("/config-keys-bad-arity.tsv"));
	}

	@Test
	public void missingResourceIsRejected()
	{
		assertThrows("a missing resource must fail the load",
			IllegalStateException.class,
			() -> ConfigKey.loadRows("/config-keys-does-not-exist.tsv"));
	}

	@Test
	public void unparseableLiteralIsRejected()
	{
		assertThrows(IllegalStateException.class,
			() -> ConfigKey.parseDef("X", boolean.class, null, "MAYBE"));
		assertThrows(IllegalStateException.class,
			() -> ConfigKey.parseDef("X", int.class, null, "seven"));
		assertThrows(IllegalStateException.class,
			() -> ConfigKey.parseDef("X", TeleportationItem.class, null, "NOT_A_MODE"));
		assertThrows(IllegalStateException.class,
			() -> ConfigKey.parseDef("X", Set.class, null, "flag:usePoh"));
		assertThrows(IllegalStateException.class,
			() -> ConfigKey.parseType("X", "hologram"));
		assertThrows(IllegalStateException.class,
			() -> ConfigKey.parseElement("X", "Nothing"));
		assertThrows(IllegalStateException.class,
			() -> ConfigKey.parseCoercion("X", "SOMETHING"));
		assertThrows(IllegalStateException.class,
			() -> ConfigKey.parseEffects("X", "NO_SUCH_EFFECT"));
	}
}
