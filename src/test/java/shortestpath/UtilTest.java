package shortestpath;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import javax.annotation.Nonnull;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link Util} helper methods.
 */
public class UtilTest
{

	// --- readAllBytes -----------------------------------------------------

	@Test
	public void readAllBytesEmptyStream() throws IOException
	{
		byte[] data = Util.readAllBytes(new ByteArrayInputStream(new byte[0]));
		Assert.assertNotNull(data);
		Assert.assertEquals(0, data.length);
	}

	@Test
	public void readAllBytesSmall() throws IOException
	{
		byte[] expected = {1, 2, 3, 4, 5};
		byte[] data = Util.readAllBytes(new ByteArrayInputStream(expected));
		Assert.assertArrayEquals(expected, data);
	}

	@Test
	public void readAllBytesLargerThanBuffer() throws IOException
	{
		// Create data larger than the internal 1024 byte buffer to exercise loop logic
		byte[] expected = new byte[4096];
		for (int i = 0; i < expected.length; i++)
		{
			expected[i] = (byte) (i * 31); // deterministic pattern
		}
		byte[] data = Util.readAllBytes(new ByteArrayInputStream(expected));
		Assert.assertArrayEquals(expected, data);
	}

	@Test(expected = IOException.class)
	public void readAllBytesPropagatesIOException() throws IOException
	{
		// InputStream that throws after first read
		InputStream failing = new FilterInputStream(new ByteArrayInputStream(new byte[]{1, 2, 3}))
		{
			private boolean first = true;

			@Override
			public int read(@Nonnull byte[] b, int off, int len) throws IOException
			{
				if (first)
				{
					first = false;
					return super.read(b, off, len);
				}
				throw new IOException("boom");
			}
		};
		// Should throw on second read attempt
		Util.readAllBytes(failing);
	}

	// --- each -------------------------------------------------------------

	@Test
	public void eachVisitsSameRowsAsHandRolledReader() throws IOException
	{
		String path = "/config-items.tsv";

		List<String> expectedLines = new ArrayList<>();
		List<String[]> expectedCols = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			Objects.requireNonNull(Util.class.getResourceAsStream(path)), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				expectedLines.add(line);
				expectedCols.add(line.split("\t", -1));
			}
		}

		List<String> actualLines = new ArrayList<>();
		List<String[]> actualCols = new ArrayList<>();
		Util.each(path, (line, cols) ->
		{
			actualLines.add(line);
			actualCols.add(cols);
		});

		Assert.assertEquals(expectedLines, actualLines);
		Assert.assertEquals(expectedCols.size(), actualCols.size());
		for (int i = 0; i < expectedCols.size(); i++)
		{
			Assert.assertArrayEquals(expectedCols.get(i), actualCols.get(i));
		}
		Assert.assertFalse(expectedLines.isEmpty());
	}

	@Test
	public void eachSkipsCommentsAndBlankLines() throws IOException
	{
		// item-variations.tsv carries a '#' header comment; assert the skip
		// applies by comparing row counts with and without the '#' filter.
		int withSkip = countRows("/item-variations.tsv", true);
		int withoutCommentSkip = countRows("/item-variations.tsv", false);
		Assert.assertTrue(withSkip < withoutCommentSkip);
	}

	private static int countRows(String path, boolean skipComments) throws IOException
	{
		int[] count = {0};
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			Objects.requireNonNull(Util.class.getResourceAsStream(path)), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || (skipComments && line.startsWith("#")))
				{
					continue;
				}
				count[0]++;
			}
		}
		return count[0];
	}

	@Test
	public void eachMissingResourceThrowsNpe()
	{
		Assert.assertThrows(NullPointerException.class,
			() -> Util.each("/no-such-resource.tsv", (line, cols) ->
			{
			}));
	}

	@Test
	public void eachPropagatesRowConsumerExceptions()
	{
		IllegalStateException boom = new IllegalStateException("boom");
		IllegalStateException thrown = Assert.assertThrows(IllegalStateException.class,
			() -> Util.each("/config-items.tsv", (line, cols) ->
			{
				throw boom;
			}));
		Assert.assertSame(boom, thrown);
	}

	// --- resourceString ---------------------------------------------------

	@Test
	public void resourceStringMatchesLiteralRead() throws IOException
	{
		String path = "/item-variations.tsv";
		String expected = new String(
			Util.readAllBytes(Objects.requireNonNull(Util.class.getResourceAsStream(path))),
			StandardCharsets.UTF_8);
		Assert.assertEquals(expected, Util.resourceString(path));
	}

	@Test
	public void resourceStringMissingResourceThrowsNpe()
	{
		Assert.assertThrows(NullPointerException.class,
			() -> Util.resourceString("/no-such-resource.tsv"));
	}

	// --- concatenate ------------------------------------------------------

	@Test
	public void concatenateAllNullOrEmptyReturnsNull()
	{
		Assert.assertNull(Util.concatenate(new int[][]{null, new int[0], null}));
	}

	@Test
	public void concatenateSingleArrayIdentity()
	{
		int[] a = {1, 2, 3};
		Assert.assertArrayEquals(a, Util.concatenate(new int[][]{a}));
	}

	@Test
	public void concatenateSkipsNulls()
	{
		int[] a = {1, 2};
		int[] c = {3};
		int[] result = Util.concatenate(new int[][]{a, null, c});
		Assert.assertArrayEquals(new int[]{1, 2, 3}, result);
	}

	@Test
	public void concatenateMultiple()
	{
		int[] a = {1};
		int[] b = {2, 3};
		int[] c = {4, 5, 6};
		int[] result = Util.concatenate(new int[][]{a, b, c});
		Assert.assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, result);
	}

	@Test
	public void concatenateLarge()
	{
		// Use random sizes to stress size accumulation & copy loop
		Random rnd = new Random(123);
		int[][] arrays = new int[50][];
		int total = 0;
		for (int i = 0; i < arrays.length; i++)
		{
			int len = rnd.nextInt(10); // small to keep test fast
			if (len == 0)
			{
				arrays[i] = new int[0];
			}
			else
			{
				arrays[i] = new int[len];
				for (int j = 0; j < len; j++)
				{
					arrays[i][j] = i * 100 + j;
				}
				total += len;
			}
		}
		int[] combined = Util.concatenate(arrays);
		if (total == 0)
		{
			Assert.assertNull(combined);
		}
		else
		{
			int count = 0;
			for (int[] arr : arrays)
			{
				if (arr == null)
				{
					continue;
				}
				for (int v : arr)
				{
					Assert.assertNotNull(combined);
					Assert.assertEquals(v, combined[count++]);
				}
			}
			Assert.assertNotNull(combined);
			Assert.assertEquals(total, combined.length);
		}
	}
}
