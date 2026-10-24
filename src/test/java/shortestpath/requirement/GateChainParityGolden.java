package shortestpath.requirement;

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
 * Golden-verdict compare/regenerate helper for the gate-chain parity tests.
 *
 * <p>Each scenario's expected verdict table lives at
 * {@code src/test/resources/gate-chain-parity/<scenario>.txt}, loaded through
 * the test classpath (the Gradle {@code java} plugin puts
 * {@code src/test/resources} on it — no build file change needed). One line
 * per recorded fixture verdict; the comparison is a line diff that reports
 * the first mismatch with its line number so a drift reads immediately.
 *
 * <p>Regeneration is gated on the {@code VERDICT_REGEN} environment
 * variable — an env var rather than a {@code -D} flag because Gradle test
 * JVMs do not inherit CLI system properties without a {@code systemProperty}
 * line in {@code build.gradle}, which stays untouched:
 *
 * <pre>VERDICT_REGEN=1 ./gradlew -p shortest-path test --tests '*GateChainParityTest*'</pre>
 *
 * Only the values {@code 1} and {@code true} opt in — a stray
 * {@code VERDICT_REGEN} set to anything else is ignored with a stderr
 * note, and the suite compares normally. When regen is active a banner and
 * every rewritten file go to stderr so the run cannot read as an ordinary
 * green pass: nothing is compared while regenerating. {@code VERDICT_REGEN}
 * belongs to this suite alone — {@code TRANSCRIPT_REGEN} gates the unrelated
 * overlay draw-call goldens.
 *
 * <p>Regen is for deliberate fixture changes only — the goldens are the
 * gate-chain parity oracle, so a regen that masks accidental verdict drift
 * voids it. The write goes to the source tree (the Gradle test working
 * directory is the {@code shortest-path} project dir), then a clean re-run
 * compares.
 */
final class GateChainParityGolden
{
	private static final String RESOURCE_DIR = "gate-chain-parity";
	private static final boolean REGEN = regenRequested();
	private static boolean regenAnnounced;

	private GateChainParityGolden()
	{
	}

	private static boolean regenRequested()
	{
		String value = System.getenv("VERDICT_REGEN");
		if (value == null)
		{
			return false;
		}
		if ("1".equals(value) || "true".equalsIgnoreCase(value))
		{
			return true;
		}
		// A set-but-unrecognized value must not vacuate the oracle.
		System.err.println("VERDICT_REGEN=" + value + " ignored — regeneration"
			+ " requires VERDICT_REGEN=1; comparing against goldens normally");
		return false;
	}

	static void compareOrRegen(String scenario, List<String> lines)
	{
		String body = String.join("\n", lines) + "\n";
		if (REGEN)
		{
			if (!regenAnnounced)
			{
				regenAnnounced = true;
				System.err.println("VERDICT_REGEN active — rewriting goldens,"
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
			if (!e.equals(a))
			{
				Assert.fail(scenario + " verdict drift at line " + (i + 1)
					+ "\n  expected: " + e + "\n  actual:   " + a
					+ "\n(regenerate with VERDICT_REGEN=1 only for a deliberate fixture change)");
			}
		}
	}

	private static String read(String scenario)
	{
		String name = RESOURCE_DIR + "/" + scenario + ".txt";
		try (InputStream in = GateChainParityGolden.class.getClassLoader().getResourceAsStream(name))
		{
			if (in == null)
			{
				Assert.fail("missing golden " + name + " — record it with VERDICT_REGEN=1");
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
