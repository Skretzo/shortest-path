package shortestpath.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

import net.runelite.client.config.ConfigItem;
import shortestpath.ShortestPathConfig;
import shortestpath.transport.TransportType;

/**
 * Source-scan parity between the routing-key table and the code it
 * describes: the set of config keys the refresh path actually reads must
 * equal the {@link ConfigKey} rows classified
 * {@link Effect#ROUTE_INVALIDATING}. The table and the read set are two
 * halves of one fact — a routing read without a route-invalidating row
 * silently leaves a running route stale, and a route-invalidating row no
 * refresh path reads is dead classification (or the read moved somewhere
 * the scan does not follow). Both directions are asserted, so drift on
 * either side fails the build.
 *
 * <p>The expected set is never hardcoded: it is derived by scanning the
 * refresh-path method bodies in {@code PathfinderConfig} and
 * {@code TransportTypeConfig} for {@code effective().<method>()},
 * {@code config.<method>()} and {@code settings.coerce<Kind>("<key>", ...)}
 * reads, expanding the per-{@link TransportType} driven reads
 * ({@code getEnabledGetter}/{@code getCostGetter} applied to config, keyed
 * by {@code getEnabledKey()}/{@code getCostKey()}) across every enum
 * constant, and following {@code ShortestPathConfig} default methods that
 * delegate to further config getters (e.g. {@code pohNexusPortals()} reads
 * {@code useTeleportationPortalsPoh()}). Method names map to key names
 * through the {@code @ConfigItem} annotations on
 * {@link ShortestPathConfig}.
 *
 * <p>Sources are resolved relative to the test working directory (the
 * Gradle project dir) — the same idiom as the package-boundary lints. A
 * missing file fails with an explicit message rather than an empty scan.
 */
public class RoutingKeyParityTest
{
	private static final Path PATHFINDER_CONFIG =
		Paths.get("src/main/java/shortestpath/pathfinder/PathfinderConfig.java");
	private static final Path TRANSPORT_TYPE_CONFIG =
		Paths.get("src/main/java/shortestpath/transport/TransportTypeConfig.java");
	private static final Path SHORTEST_PATH_CONFIG =
		Paths.get("src/main/java/shortestpath/ShortestPathConfig.java");

	/** Identifier call sites — {@code name(} — inside a method body. */
	private static final Pattern CALL = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\(");
	/** {@code ...effective().<method>()} reads on the settings seam. */
	private static final Pattern EFFECTIVE_READ =
		Pattern.compile("effective\\(\\)\\s*\\.\\s*([A-Za-z_$][\\w$]*)\\s*\\(");
	/**
	 * {@code config.<method>()} bypass reads on the raw config proxy. A
	 * qualified receiver ({@code this.config.x()}) also matches — the
	 * excluded set drops {@code .} precisely so a dotted receiver still
	 * counts as a config read.
	 */
	private static final Pattern CONFIG_READ =
		Pattern.compile("(?:^|[^\\w$])config\\s*\\.\\s*([A-Za-z_$][\\w$]*)\\s*\\(");
	/** {@code settings.coerce<Kind>("<key>", ...)} override-aware reads. */
	private static final Pattern COERCE_READ =
		Pattern.compile("coerce\\w+\\s*\\(\\s*\"([^\"]+)\"");
	/** Keywords whose {@code name(} is never a method declaration. */
	private static final Set<String> CONTROL_TOKENS = Set.of(
		"if", "for", "while", "switch", "catch", "try", "do", "else",
		"synchronized", "return", "throw", "new", "assert", "super", "this",
		"case", "default");

	private static SortedSet<String> refreshReadKeys;

	@BeforeClass
	public static void deriveRefreshReadSet() throws IOException
	{
		refreshReadKeys = new TreeSet<>(routingReads());
	}

	/**
	 * The config keys the refresh path reads, derived entirely from source:
	 * every {@code effective()}/{@code config} getter invocation reachable
	 * from {@code refresh()} through same-file calls, the literal keys in
	 * {@code coerce*("key", ...)} calls, the per-transport-type driven keys,
	 * and keys read transitively through {@code ShortestPathConfig} default
	 * methods.
	 */
	static Set<String> routingReads() throws IOException
	{
		Map<String, String> methodToKey = configMethodToKey();
		Map<String, String> pathfinderBodies = methodBodies(readSource(PATHFINDER_CONFIG));
		Map<String, String> transportTypeBodies = methodBodies(readSource(TRANSPORT_TYPE_CONFIG));
		Map<String, String> configBodies = methodBodies(readSource(SHORTEST_PATH_CONFIG));

		Set<String> keys = new TreeSet<>();
		Set<String> readMethods = new HashSet<>();
		boolean enabledDriven = false;
		boolean costDriven = false;

		List<Map<String, String>> refreshSources =
			Arrays.asList(pathfinderBodies, transportTypeBodies);
		Set<String> scannedBodies = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.add("refresh");
		while (!queue.isEmpty())
		{
			String name = queue.poll();
			for (Map<String, String> bodies : refreshSources)
			{
				String body = bodies.get(name);
				if (body == null || !scannedBodies.add(name + body))
				{
					continue;
				}
				Matcher effective = EFFECTIVE_READ.matcher(body);
				while (effective.find())
				{
					readMethods.add(effective.group(1));
				}
				Matcher config = CONFIG_READ.matcher(body);
				while (config.find())
				{
					readMethods.add(config.group(1));
				}
				Matcher coerce = COERCE_READ.matcher(body);
				while (coerce.find())
				{
					keys.add(coerce.group(1));
				}
				enabledDriven |= body.contains("getEnabledKey()")
					|| body.contains("getEnabledGetter()");
				costDriven |= body.contains("getCostKey()")
					|| body.contains("getCostGetter()");
				Matcher calls = CALL.matcher(body);
				while (calls.find())
				{
					String called = calls.group(1);
					if (!CONTROL_TOKENS.contains(called))
					{
						queue.add(called);
					}
				}
			}
		}

		// Resolve read method names to key names, following config default
		// methods that delegate to further config getters.
		Deque<String> methods = new ArrayDeque<>(readMethods);
		Set<String> seenMethods = new HashSet<>();
		while (!methods.isEmpty())
		{
			String method = methods.poll();
			if (!seenMethods.add(method))
			{
				continue;
			}
			String key = methodToKey.get(method);
			if (key != null)
			{
				keys.add(key);
			}
			String body = configBodies.get(method);
			if (body == null)
			{
				continue;
			}
			Matcher inner = CALL.matcher(body);
			while (inner.find())
			{
				String nested = inner.group(1);
				if (methodToKey.containsKey(nested))
				{
					methods.add(nested);
				}
			}
		}

		if (enabledDriven)
		{
			for (TransportType type : TransportType.values())
			{
				if (type.getEnabledKey() != null)
				{
					keys.add(type.getEnabledKey());
				}
			}
		}
		if (costDriven)
		{
			for (TransportType type : TransportType.values())
			{
				if (type.getCostKey() != null)
				{
					keys.add(type.getCostKey());
				}
			}
		}
		return keys;
	}

	/**
	 * Read a required source file; a missing file is a hard failure — an
	 * empty scan must never read as green.
	 */
	private static String readSource(Path path) throws IOException
	{
		assertTrue("source file not found (tests must run with the project "
			+ "dir as working directory): " + path.toAbsolutePath(),
			Files.isRegularFile(path));
		return new String(Files.readAllBytes(path));
	}

	/**
	 * {@code methodName -> @ConfigItem keyName} for every annotated
	 * {@link ShortestPathConfig} getter.
	 */
	private static Map<String, String> configMethodToKey()
	{
		Map<String, String> methodToKey = new HashMap<>();
		for (Method method : ShortestPathConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && !item.keyName().isEmpty())
			{
				methodToKey.put(method.getName(), item.keyName());
			}
		}
		return methodToKey;
	}

	/**
	 * Extract {@code methodName -> body text} for every declaration in the
	 * source. Comments are stripped first so javadoc references cannot
	 * register as reads; overloads merge into one concatenated body.
	 */
	private static Map<String, String> methodBodies(String source)
	{
		String code = stripComments(source);
		Map<String, String> bodies = new HashMap<>();
		Matcher matcher = CALL.matcher(code);
		while (matcher.find())
		{
			String name = matcher.group(1);
			if (CONTROL_TOKENS.contains(name) || precededByNew(code, matcher.start()))
			{
				continue;
			}
			int closeParen = matchDelimiter(code, matcher.end() - 1, '(', ')');
			if (closeParen < 0)
			{
				continue;
			}
			int brace = skipToBrace(code, closeParen);
			if (brace < 0)
			{
				continue;
			}
			int closeBrace = matchDelimiter(code, brace, '{', '}');
			if (closeBrace < 0)
			{
				continue;
			}
			bodies.merge(name, code.substring(brace + 1, closeBrace), String::concat);
		}
		return bodies;
	}

	private static boolean precededByNew(String code, int index)
	{
		int end = index;
		while (end > 0 && Character.isWhitespace(code.charAt(end - 1)))
		{
			end--;
		}
		return end >= 3 && code.substring(end - 3, end).equals("new")
			&& (end == 3 || !Character.isJavaIdentifierPart(code.charAt(end - 4)));
	}

	/**
	 * After a call's closing paren at {@code from} (exclusive), a method
	 * declaration continues with whitespace, an optional {@code throws}
	 * clause, then the body brace. Returns the brace index or -1 when the
	 * site is a call rather than a declaration.
	 */
	private static int skipToBrace(String code, int from)
	{
		int i = from;
		while (i < code.length() && Character.isWhitespace(code.charAt(i)))
		{
			i++;
		}
		if (code.startsWith("throws", i))
		{
			int brace = code.indexOf('{', i);
			int semi = code.indexOf(';', i);
			return semi < 0 || brace < semi ? brace : -1;
		}
		return i < code.length() && code.charAt(i) == '{' ? i : -1;
	}

	/**
	 * Index just past the delimiter matching {@code open} at {@code openIndex},
	 * or -1 if unbalanced. Depth counts only; character/string literals are
	 * not special-cased because config sources carry no braces in literals.
	 */
	private static int matchDelimiter(String code, int openIndex, char open, char close)
	{
		int depth = 0;
		for (int i = openIndex; i < code.length(); i++)
		{
			char c = code.charAt(i);
			if (c == open)
			{
				depth++;
			}
			else if (c == close)
			{
				depth--;
				if (depth == 0)
				{
					return i + 1;
				}
			}
		}
		return -1;
	}

	private static String stripComments(String source)
	{
		String noBlock = source.replaceAll("(?s)/\\*.*?\\*/", " ");
		return noBlock.replaceAll("(?m)//.*$", " ");
	}

	/**
	 * The table's route-invalidating side: every {@link ConfigKey} row whose
	 * effect set contains {@link Effect#ROUTE_INVALIDATING}.
	 */
	private static SortedSet<String> routingTableKeys()
	{
		SortedSet<String> keys = new TreeSet<>();
		for (ConfigKey row : ConfigKey.values())
		{
			if (row.isRouteInvalidating())
			{
				keys.add(row.getKey());
			}
		}
		return keys;
	}

	@Test
	public void refreshReadsEqualRouteInvalidatingRows()
	{
		SortedSet<String> routingKeys = routingTableKeys();

		SortedSet<String> unread = new TreeSet<>(refreshReadKeys);
		unread.removeAll(routingKeys);
		SortedSet<String> stale = new TreeSet<>(routingKeys);
		stale.removeAll(refreshReadKeys);

		StringBuilder message = new StringBuilder();
		if (!unread.isEmpty())
		{
			message.append("config keys the refresh path reads that are not "
				+ "classified ROUTE_INVALIDATING — a change to them leaves a "
				+ "running route stale: ").append(unread).append(' ');
		}
		if (!stale.isEmpty())
		{
			message.append("ROUTE_INVALIDATING rows the refresh path never "
				+ "reads — stale rows, or a read the source scan does not "
				+ "follow: ").append(stale);
		}
		assertTrue(message.toString(), unread.isEmpty() && stale.isEmpty());
	}

	/**
	 * Vacuity guard: the scan must actually observe reads — an empty
	 * derived set would pass a degenerate comparison while meaning the
	 * scanner or the source layout broke.
	 */
	@Test
	public void scanHasReach() throws IOException
	{
		assertTrue("source file not found: " + PATHFINDER_CONFIG.toAbsolutePath(),
			Files.isRegularFile(PATHFINDER_CONFIG));
		assertTrue("source file not found: " + TRANSPORT_TYPE_CONFIG.toAbsolutePath(),
			Files.isRegularFile(TRANSPORT_TYPE_CONFIG));
		assertTrue("source file not found: " + SHORTEST_PATH_CONFIG.toAbsolutePath(),
			Files.isRegularFile(SHORTEST_PATH_CONFIG));
		assertFalse("refresh-path scan observed no config reads — the scanner "
			+ "or the refresh path is broken", refreshReadKeys.isEmpty());
	}
}
