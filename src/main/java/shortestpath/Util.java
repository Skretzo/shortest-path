package shortestpath;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * General utility helpers for I/O and primitive array manipulation used by the
 * shortest path plugin.
 */
public class Util
{
	/**
	 * Reads all bytes from the provided {@link InputStream} until EOF.
	 * This method does not close the stream; the caller retains responsibility for
	 * resource management.
	 *
	 * @param in the input stream to read from.
	 * @return a newly allocated byte array containing all bytes read (may be empty,
	 * never {@code null}).
	 * @throws IOException if an I/O error occurs while reading.
	 */
	public static byte[] readAllBytes(InputStream in) throws IOException
	{
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];

		while (true)
		{
			int read = in.read(buffer, 0, buffer.length);

			if (read == -1)
			{
				return result.toByteArray();
			}

			result.write(buffer, 0, read);
		}
	}

	/**
	 * Reads a bundled TSV resource line by line, invoking {@code row} for
	 * every non-empty, non-{@code #}-comment line with the raw line and its
	 * keep-arity tab-split columns ({@code split("\t", -1)}). The resource is
	 * resolved from an absolute classpath path ({@code "/name.tsv"}) anchored
	 * on {@code Util.class} — identical to each caller's own
	 * {@code *.class.getResourceAsStream} lookup for {@code /}-prefixed
	 * paths. A missing resource throws {@link NullPointerException}; an I/O
	 * failure propagates to the caller so each call site keeps its own error
	 * posture.
	 *
	 * @param path the absolute classpath path of the resource to read.
	 * @param row  consumer invoked once per visited row with the line and its
	 *             columns.
	 * @throws IllegalArgumentException if {@code path} is not absolute.
	 * @throws IOException if an I/O error occurs while reading.
	 */
	public static void each(String path, BiConsumer<String, String[]> row) throws IOException
	{
		if (!path.startsWith("/"))
		{
			throw new IllegalArgumentException("resource path must be absolute: " + path);
		}
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
				row.accept(line, line.split("\t", -1));
			}
		}
	}

	/**
	 * Reads an entire bundled resource into a UTF-8 {@link String}. Absolute
	 * classpath paths only; a missing resource throws
	 * {@link NullPointerException} and an I/O failure propagates to the
	 * caller.
	 *
	 * @param path the absolute classpath path of the resource to read.
	 * @return the resource contents decoded as UTF-8.
	 * @throws IllegalArgumentException if {@code path} is not absolute.
	 * @throws IOException if an I/O error occurs while reading.
	 */
	public static String resourceString(String path) throws IOException
	{
		if (!path.startsWith("/"))
		{
			throw new IllegalArgumentException("resource path must be absolute: " + path);
		}
		return new String(
			readAllBytes(Objects.requireNonNull(Util.class.getResourceAsStream(path))),
			StandardCharsets.UTF_8);
	}

	/**
	 * Concatenates the contents of multiple {@code int[]} arrays into one
	 * contiguous array.
	 * {@code null} elements in {@code arrays} are skipped. If all arrays are
	 * {@code null} or empty, {@code null}
	 * is returned (this mirrors the existing behavior relied upon by callers).
	 *
	 * @param arrays an array of {@code int[]} segments to concatenate (may contain
	 *               {@code null}).
	 * @return a new combined array, or {@code null} if there are no elements to
	 * copy.
	 */
	public static int[] concatenate(int[][] arrays)
	{
		int n = 0;
		for (int[] value : arrays)
		{
			n += (value == null) ? 0 : value.length;
		}
		if (n == 0)
		{
			return null;
		}
		int[] array = new int[n];
		int k = 0;
		for (int[] ints : arrays)
		{
			if (ints != null)
			{
				for (int anInt : ints)
				{
					array[k++] = anInt;
				}
			}
		}
		return array;
	}
}
