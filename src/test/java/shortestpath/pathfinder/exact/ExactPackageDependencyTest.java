package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.Test;

public class ExactPackageDependencyTest
{
	@Test
	public void exactSourcesDoNotImportClientOrLegacySearch() throws IOException
	{
		Path sourceRoot = Paths.get("src/main/java/shortestpath/pathfinder/exact");
		try (Stream<Path> files = Files.walk(sourceRoot))
		{
			files.filter(path -> path.toString().endsWith(".java")).forEach(this::assertAllowedImports);
		}
	}

	private void assertAllowedImports(Path source)
	{
		try
		{
			String text = Files.readString(source);
			assertFalse(source + " imports RuneLite Client", text.contains("net.runelite.api.Client"));
			assertFalse(source + " imports legacy search", text.contains("shortestpath.pathfinder.Pathfinder"));
			assertFalse(source + " imports legacy graph", text.contains("NodeGraph"));
			assertFalse(source + " imports legacy visited state", text.contains("VisitedTiles"));
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}
}
