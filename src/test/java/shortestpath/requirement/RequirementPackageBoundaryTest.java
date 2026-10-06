package shortestpath.requirement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Mechanical guard for the requirement package's dependency direction.
 *
 * <p>The middleware must stay RuneLite-client-free except for the named adapter files that
 * deliberately delegate to {@code (Client)}-taking helpers ({@link ClientPlayerStateSource} is the
 * capture-path source; {@link OwnedItems} and {@link BankPickupRequirements} expose the sanctioned
 * statics it and the display path reuse). The model package is the leaf layer: it must reference
 * neither the client nor anything under {@code shortestpath.pathfinder}. Per-search restrictions
 * ({@code SearchRestrictions}) belong to the exact backend's search layer and must never leak into
 * the middleware.
 */
public class RequirementPackageBoundaryTest
{
	private static final Path REQUIREMENT_ROOT = Paths.get("src/main/java/shortestpath/requirement");
	private static final Path MODEL_ROOT = REQUIREMENT_ROOT.resolve("model");

	/** Files sanctioned to reference {@code net.runelite.api.Client}. */
	private static final Set<String> CLIENT_WHITELIST = Set.of(
		"ClientPlayerStateSource.java", "OwnedItems.java", "BankPickupRequirements.java");

	@Test
	public void clientReferencesAreConfinedToSanctionedAdapters() throws IOException
	{
		Set<Path> scanned = sources(REQUIREMENT_ROOT);
		for (Path source : scanned)
		{
			boolean referencesClient = read(source).contains("net.runelite.api.Client");
			boolean whitelisted = CLIENT_WHITELIST.contains(source.getFileName().toString());
			assertFalse(source + " references net.runelite.api.Client but is not a sanctioned adapter",
				referencesClient && !whitelisted);
		}
		// The scan must actually reach the sanctioned adapters, so a moved source root cannot pass vacuously.
		for (String adapter : CLIENT_WHITELIST)
		{
			assertTrue("sanctioned adapter not scanned: " + adapter,
				scanned.stream().anyMatch(path -> path.getFileName().toString().equals(adapter)));
		}
	}

	@Test
	public void modelPackageIsALeaf() throws IOException
	{
		Set<Path> scanned = sources(MODEL_ROOT);
		assertFalse("no model sources scanned — wrong root?", scanned.isEmpty());
		for (Path source : scanned)
		{
			String text = read(source);
			assertFalse(source + " references net.runelite.api.Client",
				text.contains("net.runelite.api.Client"));
			assertFalse(source + " depends on pathfinder internals",
				text.contains("shortestpath.pathfinder."));
		}
	}

	@Test
	public void perSearchRestrictionsDoNotLeakIntoTheMiddleware() throws IOException
	{
		for (Path source : sources(REQUIREMENT_ROOT))
		{
			assertFalse(source + " references per-search SearchRestrictions",
				read(source).contains("SearchRestrictions"));
		}
	}

	private static Set<Path> sources(Path root) throws IOException
	{
		try (Stream<Path> files = Files.walk(root))
		{
			return files.filter(path -> path.toString().endsWith(".java"))
				.collect(Collectors.toCollection(HashSet::new));
		}
	}

	private static String read(Path source)
	{
		try
		{
			return Files.readString(source);
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}
}
