package shortestpath.pathfinder.exact;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;

public class RoutingCutsTest
{
	private static final RoutingCuts.Parameters PARAMETERS =
		new RoutingCuts.Parameters(20000, 500, 32, 40, 42, "strong");

	@Test
	public void roundTripsAndCanonicalisesPairs() throws IOException
	{
		// Reversed, unsorted and duplicated pairs are normalised; high-bit tiles sort unsigned.
		int high = 0x80000001;
		RoutingCuts cuts = new RoutingCuts(0x1234L, PARAMETERS, new int[] {5, 3, high, 1, 3, 5, 2, 4});

		assertArrayEquals(new int[] {1, high, 2, 4, 3, 5}, cuts.pairs());
		RoutingCuts read = RoutingCuts.read(cuts.write());
		assertArrayEquals(cuts.pairs(), read.pairs());
		assertEquals(0x1234L, read.collisionFingerprint());
		assertEquals(PARAMETERS, read.parameters());
		assertArrayEquals(cuts.write(), read.write());
	}

	@Test
	public void rejectsMalformedFiles()
	{
		byte[] valid = new RoutingCuts(7L, PARAMETERS, new int[] {1, 2, 3, 4}).write();

		byte[] badMagic = valid.clone();
		badMagic[0] = 'X';
		assertRejected(badMagic, "magic");

		byte[] badVersion = valid.clone();
		badVersion[8] = 2;
		assertRejected(badVersion, "version");

		assertRejected(Arrays.copyOf(valid, valid.length - 1), "truncated");
		assertRejected(Arrays.copyOf(valid, valid.length + 4), "trailing");

		byte[] unsorted = valid.clone();
		int pairs = valid.length - 16;
		System.arraycopy(valid, pairs + 8, unsorted, pairs, 8);
		System.arraycopy(valid, pairs, unsorted, pairs + 8, 8);
		assertRejected(unsorted, "ascending");

		byte[] reversed = valid.clone();
		System.arraycopy(valid, pairs + 4, reversed, pairs, 4);
		System.arraycopy(valid, pairs, reversed, pairs + 4, 4);
		assertRejected(reversed, "canonical");
	}

	@Test
	public void packagedCutsParse()
	{
		RoutingCuts cuts = RoutingCuts.loadFromResources();
		assertTrue(cuts.cutCount() > 0);
	}

	private static void assertRejected(byte[] bytes, String reason)
	{
		try
		{
			RoutingCuts.read(bytes);
			fail("expected rejection: " + reason);
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains(reason));
		}
	}
}
