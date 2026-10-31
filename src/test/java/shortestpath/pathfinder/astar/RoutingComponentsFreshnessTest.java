package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Test;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;

/**
 * Guards {@code routing-components.bin}: it is derived data, so it must equal what the committed
 * collision map and routing cuts generate. When the map or the cuts change, regenerate it:
 *
 * <pre>ROUTING_COMPONENTS_REGEN=1 ./gradlew -p shortest-path test --tests '*RoutingComponentsFreshnessTest' --rerun</pre>
 *
 * Only the values {@code 1} and {@code true} opt in; any other value is ignored with a stderr
 * note and the file is compared normally. While regenerating nothing is compared. The write goes
 * to the source tree (the Gradle test working directory is the {@code shortest-path} project
 * directory); a clean re-run then compares.
 */
public class RoutingComponentsFreshnessTest
{
	private static final String REGEN_VARIABLE = "ROUTING_COMPONENTS_REGEN";
	private static final Path COMMITTED = Paths.get("src", "main", "resources", "routing-components.bin");

	@Test
	public void committedLayerMatchesTheCollisionMapAndCuts() throws IOException
	{
		RoutingCuts cuts = RoutingCuts.loadFromResources();
		assertTrue("routing-cuts.bin missing or empty: the freshness check would be vacuous", cuts.cutCount() > 0);

		CollisionMap collision = new CollisionMap(SplitFlagMap.fromResources());
		byte[] expected = ComponentLayerWriter.write(RoutingStaticBuilder.generate(collision, cuts.pairs()));

		if (regenRequested())
		{
			Files.createDirectories(COMMITTED.toAbsolutePath().getParent());
			Files.write(COMMITTED, expected);
			System.err.println(REGEN_VARIABLE + " active - rewrote " + COMMITTED + " (" + expected.length
				+ " bytes), nothing was compared");
			return;
		}

		byte[] committed;
		try (InputStream stream = ComponentLayer.class.getResourceAsStream(ComponentLayer.RESOURCE))
		{
			if (stream == null) fail("routing-components.bin is missing; " + regenerationHint());
			committed = stream.readAllBytes();
		}
		if (!Arrays.equals(expected, committed))
		{
			fail("routing-components.bin is stale for the committed collision-map.zip and routing-cuts.bin ("
				+ committed.length + " bytes committed, " + expected.length + " bytes generated); " + regenerationHint());
		}
	}

	@Test
	public void committedLayerReadsStrictly() throws IOException
	{
		try (InputStream stream = ComponentLayer.class.getResourceAsStream(ComponentLayer.RESOURCE))
		{
			ComponentLayer layer = ComponentLayer.read(stream);
			assertTrue(layer.starts.length > 0);
			assertTrue("the layer has no crossings", layer.crossings.length > 0);
		}
	}

	private static String regenerationHint()
	{
		return "regenerate it with " + REGEN_VARIABLE + "=1 ./gradlew test --tests '*RoutingComponentsFreshnessTest'"
			+ " --rerun (or the tooling repository's routingComponents task)";
	}

	private static boolean regenRequested()
	{
		String value = System.getenv(REGEN_VARIABLE);
		if (value == null) return false;
		if ("1".equals(value) || "true".equalsIgnoreCase(value)) return true;
		System.err.println(REGEN_VARIABLE + "=" + value + " ignored - regeneration requires " + REGEN_VARIABLE
			+ "=1; comparing against the committed layer normally");
		return false;
	}
}
