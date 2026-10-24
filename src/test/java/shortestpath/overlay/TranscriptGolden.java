package shortestpath.overlay;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Assert;

/**
 * Golden-transcript compare/regenerate helper for the overlay draw-call tests.
 *
 * <p>Each scenario's expected transcript lives at
 * {@code src/test/resources/overlay-transcripts/<scenario>.txt}, loaded through
 * the test classpath (the Gradle {@code java} plugin puts
 * {@code src/test/resources} on it — no build file change needed). One line
 * per recorded call; the comparison is a line diff that reports the first
 * mismatch with its line number so a drift reads immediately.
 *
 * <p>Regeneration is gated on the {@code TRANSCRIPT_REGEN} environment
 * variable — an env var rather than a {@code -D} flag because Gradle test JVMs
 * do not inherit CLI system properties without a {@code systemProperty} line
 * in {@code build.gradle}, which stays untouched:
 *
 * <pre>TRANSCRIPT_REGEN=1 ./gradlew -p shortest-path test --tests '*OverlayTranscriptTest*'</pre>
 *
 * Regen is for deliberate draw changes only — the goldens are the
 * consolidation oracle, so a regen that masks accidental drift voids it.
 * The write goes to the source tree (the Gradle test working directory is the
 * {@code shortest-path} project dir), then a clean re-run compares.
 */
final class TranscriptGolden
{
	private static final String RESOURCE_DIR = "overlay-transcripts";

	private TranscriptGolden()
	{
	}

	static void compareOrRegen(String scenario, List<String> transcript)
	{
		String body = String.join("\n", transcript) + "\n";
		if (System.getenv("TRANSCRIPT_REGEN") != null)
		{
			regen(scenario, body);
			return;
		}
		String expected = read(scenario);
		String[] expectedLines = expected.split("\n", -1);
		String[] actualLines = body.split("\n", -1);
		for (int i = 0; i < Math.max(expectedLines.length, actualLines.length); i++)
		{
			String e = i < expectedLines.length ? expectedLines[i] : "<missing>";
			String a = i < actualLines.length ? actualLines[i] : "<missing>";
			if (!e.equals(a))
			{
				Assert.fail(scenario + " transcript drift at line " + (i + 1)
					+ "\n  expected: " + e + "\n  actual:   " + a
					+ "\n(regenerate with TRANSCRIPT_REGEN=1 only for a deliberate draw change)");
			}
		}
	}

	private static String read(String scenario)
	{
		String name = RESOURCE_DIR + "/" + scenario + ".txt";
		try (InputStream in = TranscriptGolden.class.getClassLoader().getResourceAsStream(name))
		{
			if (in == null)
			{
				Assert.fail("missing golden " + name + " — record it with TRANSCRIPT_REGEN=1");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	private static void regen(String scenario, String body)
	{
		Path out = Paths.get("src/test/resources", RESOURCE_DIR, scenario + ".txt");
		try
		{
			Files.createDirectories(out.getParent());
			Files.write(out, body.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
}
