package shortestpath.settings;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import shortestpath.transport.TransportType;

/**
 * The keys routing reads must be exactly the {@link ConfigKey} rows marked
 * {@link Effect#ROUTE_INVALIDATING}: a routing read without the effect leaves
 * a running route stale after the setting changes, and the effect on a key
 * routing never reads restarts searches for nothing.
 *
 * <p>Routing reads its settings in {@code PathfinderConfig}, either as a
 * {@code ConfigKey.X} constant or through a transport type's enabled, cost
 * and differential-cost keys; a {@code flag:} set default also reads its flag.
 */
public class RoutingKeyParityTest
{
	private static final Pattern CONSTANT = Pattern.compile("ConfigKey\\.([A-Z][A-Z_]+)\\b");

	@Test
	public void routingReadsAreExactlyTheRouteInvalidatingRows() throws IOException
	{
		String source = new String(Files.readAllBytes(
			Paths.get("src/main/java/shortestpath/pathfinder/PathfinderConfig.java")));
		SortedSet<ConfigKey> read = new TreeSet<>();
		Matcher constant = CONSTANT.matcher(source);
		while (constant.find())
		{
			read.add(ConfigKey.valueOf(constant.group(1)));
		}
		for (TransportType type : TransportType.values())
		{
			for (String key : new String[]{type.getEnabledKey(), type.getCostKey(), type.differentialCostKey()})
			{
				if (key != null)
				{
					read.add(ConfigKey.forKey(key));
				}
			}
		}
		for (ConfigKey key : new TreeSet<>(read))
		{
			if (key.getFlag() != null)
			{
				read.add(key.getFlag());
			}
		}

		SortedSet<ConfigKey> routeInvalidating = new TreeSet<>();
		for (ConfigKey key : ConfigKey.values())
		{
			if (key.getEffects().contains(Effect.ROUTE_INVALIDATING))
			{
				routeInvalidating.add(key);
			}
		}
		assertEquals(routeInvalidating, read);
	}
}
