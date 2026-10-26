package shortestpath.overlay;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Only the values {@code 1} and {@code true} opt in — a stray
 * {@code TRANSCRIPT_REGEN} set to anything else is ignored with a stderr
 * note, and the suite compares normally. When regen is active a banner and
 * every rewritten file go to stderr so the run cannot read as an ordinary
 * green pass: nothing is compared while regenerating.
 *
 * Regen is for deliberate draw changes only — the goldens are the
 * consolidation oracle, so a regen that masks accidental drift voids it.
 * The write goes to the source tree (the Gradle test working directory is the
 * {@code shortest-path} project dir), then a clean re-run compares.
 */
final class TranscriptGolden
{
	private static final String RESOURCE_DIR = "overlay-transcripts";
	private static final boolean REGEN = regenRequested();
	private static boolean regenAnnounced;
	/**
	 * The {@code @x,y} coordinate pair a {@code drawString} record carries
	 * — the last such pair on the line, pinned by the {@code | state}
	 * suffix or end-of-line so a coordinate-shaped substring inside the
	 * quoted text cannot be mistaken for it.
	 */
	private static final Pattern DRAW_STRING_COORDS =
		Pattern.compile(" @(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?= \\| |$)");
	/**
	 * Cross-platform slack on a {@code drawString} x coordinate. The
	 * info-banner and counter labels centre on
	 * {@code getStringBounds(...).getWidth()}, and the same font rasterises
	 * to measurably different advance widths on macOS vs Linux (~10px on a
	 * mid-length string — the goldens were recorded on macOS). x is the
	 * only font-metric input: the y baseline, text and draw state are
	 * platform-independent and stay byte-exact. Fixed-position drawStrings
	 * (canvas-anchored counters) differ by 0, so the tolerance forfeits
	 * nothing on deterministic positions while still catching a real
	 * anchoring change, which shifts x by far more than 32px.
	 */
	private static final double DRAW_STRING_X_TOLERANCE = 32.0;

	private TranscriptGolden()
	{
	}

	private static boolean regenRequested()
	{
		String value = System.getenv("TRANSCRIPT_REGEN");
		if (value == null)
		{
			return false;
		}
		if ("1".equals(value) || "true".equalsIgnoreCase(value))
		{
			return true;
		}
		// A set-but-unrecognized value must not vacuate the oracle.
		System.err.println("TRANSCRIPT_REGEN=" + value + " ignored — regeneration"
			+ " requires TRANSCRIPT_REGEN=1; comparing against goldens normally");
		return false;
	}

	static void compareOrRegen(String scenario, List<String> transcript)
	{
		String body = String.join("\n", transcript) + "\n";
		if (REGEN)
		{
			if (!regenAnnounced)
			{
				regenAnnounced = true;
				System.err.println("TRANSCRIPT_REGEN active — rewriting goldens,"
					+ " nothing is being compared");
			}
			regen(scenario, body);
			System.err.println("  rewrote " + scenario + ".txt");
			return;
		}
		String expected = read(scenario);
		String[] expectedLines = expected.split("\n", -1);
		String[] actualLines = body.split("\n", -1);
		for (int i = 0; i < Math.max(expectedLines.length, actualLines.length); i++)
		{
			String e = i < expectedLines.length ? expectedLines[i] : "<missing>";
			String a = i < actualLines.length ? actualLines[i] : "<missing>";
			if (!linesEqual(e, a))
			{
				Assert.fail(scenario + " transcript drift at line " + (i + 1)
					+ "\n  expected: " + e + "\n  actual:   " + a
					+ "\n(regenerate with TRANSCRIPT_REGEN=1 only for a deliberate draw change)");
			}
		}
	}

	/**
	 * One transcript line against the golden's — byte-exact except on
	 * {@code drawString} lines, where the x coordinate absorbs
	 * cross-platform font-metric drift within
	 * {@link #DRAW_STRING_X_TOLERANCE}. The quoted text, the y coordinate
	 * and the whole {@code | color/stroke/clip} suffix stay exact.
	 */
	private static boolean linesEqual(String expected, String actual)
	{
		if (expected.equals(actual))
		{
			return true;
		}
		if (!expected.startsWith("drawString") || !actual.startsWith("drawString"))
		{
			return false;
		}
		String[] e = splitDrawString(expected);
		String[] a = splitDrawString(actual);
		if (e == null || a == null || !e[0].equals(a[0]) || !e[2].equals(a[2]))
		{
			return false;
		}
		return Math.abs(Double.parseDouble(e[1]) - Double.parseDouble(a[1]))
			<= DRAW_STRING_X_TOLERANCE;
	}

	/**
	 * Splits a {@code drawString} line into the text through the {@code @}
	 * sign, the x coordinate, and the remainder ({@code ,y} plus the draw
	 * state suffix). {@code null} when the line carries no pinned
	 * coordinate pair — such lines never tolerate an inexact match.
	 */
	private static String[] splitDrawString(String line)
	{
		Matcher m = DRAW_STRING_COORDS.matcher(line);
		if (!m.find())
		{
			return null;
		}
		return new String[]{
			line.substring(0, m.start()),
			m.group(1),
			line.substring(m.start(2) - 1)
		};
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
