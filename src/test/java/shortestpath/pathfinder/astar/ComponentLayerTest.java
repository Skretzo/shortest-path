package shortestpath.pathfinder.astar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import org.junit.Test;
import shortestpath.WorldPointUtil;

/** The component layer's format: round trip, tile lookup, determinism and every rejection. */
public class ComponentLayerTest
{
	private static int tile(int x, int y, int plane)
	{
		return WorldPointUtil.packWorldPoint(x, y, plane);
	}

	/** Runs on planes 0 and 3, a gap above 2^31, a maximal run and three crossings. */
	private static ComponentLayer layer()
	{
		int first = tile(10, 10, 0);
		int second = first + 3;
		int upper = tile(20, 30, 3);
		int last = upper + 1 + 5;
		return new ComponentLayer(new int[] {first, second, upper, last}, new char[] {3, 65535, 1, 2},
			new int[] {1, 2, 3, 1}, new int[] {first, second, second + 1, upper, upper, last});
	}

	/** Builds a layer file by hand, for shapes the writer refuses to produce. */
	private static final class Encoder
	{
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();

		Encoder()
		{
			out.write(ComponentLayer.MAGIC, 0, ComponentLayer.MAGIC.length);
		}

		Encoder varint(long value)
		{
			for (; (value & ~0x7fL) != 0; value >>>= 7) out.write((int) (value & 0x7f) | 0x80);
			out.write((int) value);
			return this;
		}

		Encoder raw(int... bytes)
		{
			for (int value : bytes) out.write(value);
			return this;
		}

		Encoder tile(int tile)
		{
			return raw(tile >>> 24, tile >>> 16, tile >>> 8, tile);
		}

		byte[] bytes()
		{
			return out.toByteArray();
		}
	}

	private static Encoder header(long runs, long crossings)
	{
		return new Encoder().varint(ComponentLayer.VERSION).varint(runs).varint(crossings);
	}

	private static ComponentLayer read(byte[] bytes) throws IOException
	{
		return ComponentLayer.read(new ByteArrayInputStream(bytes));
	}

	private static IOException rejected(byte[] bytes)
	{
		try
		{
			read(bytes);
		}
		catch (IOException expected)
		{
			assertNotNull(expected.getMessage());
			return expected;
		}
		throw new AssertionError("a malformed layer was accepted");
	}

	private static void assertRejectedWith(String part, byte[] bytes)
	{
		String message = rejected(bytes).getMessage();
		assertTrue(message, message.contains(ComponentLayer.RESOURCE) && message.contains(part));
	}

	@Test
	public void roundTripKeepsEveryColumn() throws Exception
	{
		ComponentLayer expected = layer();
		ComponentLayer actual = read(ComponentLayerWriter.write(expected));

		assertArrayEquals(expected.starts, actual.starts);
		assertArrayEquals(expected.lengths, actual.lengths);
		assertArrayEquals(expected.ids, actual.ids);
		assertArrayEquals(expected.crossings, actual.crossings);
		int gap = actual.starts[2] - (actual.starts[1] + actual.lengths[1]);
		assertTrue("gap " + Integer.toUnsignedString(gap), Integer.compareUnsigned(gap, Integer.MIN_VALUE) > 0);
	}

	@Test
	public void emptyLayerRoundTrips() throws Exception
	{
		ComponentLayer actual = read(ComponentLayerWriter.write(new ComponentLayer(new int[0], new char[0], new int[0],
			new int[0])));

		assertEquals(0, actual.starts.length);
		assertEquals(0, actual.component(tile(10, 10, 0)));
	}

	@Test
	public void componentFindsRunsAndMissesGaps() throws Exception
	{
		ComponentLayer layer = read(ComponentLayerWriter.write(layer()));
		int first = layer.starts[0], second = layer.starts[1], upper = layer.starts[2], last = layer.starts[3];

		assertEquals(1, layer.component(first));
		assertEquals(1, layer.component(first + 2));
		assertEquals(0, layer.component(first - 1));
		assertEquals(2, layer.component(second));
		assertEquals(2, layer.component(second + 65534));
		assertEquals(0, layer.component(second + 65535));
		assertEquals(3, layer.component(upper));
		assertEquals(0, layer.component(upper + 1));
		assertEquals(1, layer.component(last));
		assertEquals(1, layer.component(last + 1));
		assertEquals(0, layer.component(last + 2));
		assertEquals(0, layer.component(tile(5, 5, 1)));
	}

	@Test
	public void generatedLayerIsByteForByteDeterministic() throws Exception
	{
		int a = tile(10, 10, 0), b = tile(11, 10, 0), c = tile(12, 10, 0);
		int[] cuts = {b, c};
		byte[] first = ComponentLayerWriter.write(RoutingStaticBuilder.generate(chain(a, b, c), cuts));
		byte[] second = ComponentLayerWriter.write(RoutingStaticBuilder.generate(chain(a, b, c), cuts));

		assertTrue(first.length > ComponentLayer.MAGIC.length);
		assertArrayEquals(first, second);
		ComponentLayer layer = read(first);
		assertEquals(1, layer.component(a));
		assertEquals(1, layer.component(b));
		assertEquals(2, layer.component(c));
		assertArrayEquals(new int[] {b, c}, layer.crossings);
	}

	private static SyntheticCollisionMap chain(int a, int b, int c)
	{
		return new SyntheticCollisionMap(Set.of(a, b, c),
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{a, b}, {b, c}}), 1);
	}

	@Test
	public void missingStreamIsRejected()
	{
		try
		{
			ComponentLayer.read(null);
			throw new AssertionError("a missing resource was accepted");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage(), expected.getMessage().contains(ComponentLayer.RESOURCE));
		}
	}

	@Test
	public void wrongMagicIsRejected()
	{
		byte[] bytes = ComponentLayerWriter.write(layer());
		bytes[0] ^= 1;
		assertRejectedWith("magic", bytes);
	}

	@Test
	public void wrongVersionIsRejected()
	{
		assertRejectedWith("version", new Encoder().varint(ComponentLayer.VERSION + 1).varint(0).varint(0).bytes());
	}

	@Test
	public void everyProperPrefixIsRejectedAsIOException()
	{
		byte[] bytes = ComponentLayerWriter.write(layer());
		for (int length = 0; length < bytes.length; length++)
			assertTrue("prefix " + length, rejected(Arrays.copyOf(bytes, length)).getMessage().contains(ComponentLayer.RESOURCE));
	}

	@Test
	public void trailingByteIsRejected()
	{
		byte[] valid = ComponentLayerWriter.write(layer());
		assertRejectedWith("trailing", Arrays.copyOf(valid, valid.length + 1));
	}

	@Test
	public void countsLargerThanTheFileAreRejectedBeforeAllocating()
	{
		assertRejectedWith("counts", header(Integer.MAX_VALUE, 0).bytes());
		assertRejectedWith("counts", header(0, 1L << 30).bytes());
		assertRejectedWith("counts", header(0, Integer.MAX_VALUE - 1).bytes());
	}

	@Test
	public void oddAndNegativeCountsAreRejected()
	{
		assertRejectedWith("counts", header(0, 1).tile(7).bytes());
		assertRejectedWith("counts", header(0, 3).tile(1).tile(2).tile(3).bytes());
		assertRejectedWith("counts", header(0xffffffffL, 0).bytes());
		assertRejectedWith("counts", header(0, 0xfffffffeL).bytes());
	}

	@Test
	public void runLongerThanSixtyFiveThousandFiveHundredThirtyFiveTilesIsRejected()
	{
		assertRejectedWith("run", header(1, 0).varint(65535).varint(0).varint(1).bytes());
		assertRejectedWith("run", header(1, 0).varint(0xffffffffL).varint(0).varint(1).bytes());
	}

	@Test
	public void componentIdZeroOrAboveTheRunCountIsRejected()
	{
		assertRejectedWith("id", header(2, 0).varint(0).varint(0).varint(0).varint(0).varint(0).varint(1).bytes());
		assertRejectedWith("id", header(2, 0).varint(0).varint(0).varint(0).varint(0).varint(1).varint(3).bytes());
	}

	@Test
	public void runStartingBelowThePreviousRunsEndIsRejected()
	{
		// The first run ends at 2^32, so the second one starts past the end of the tile range.
		assertRejectedWith("run", header(2, 0).varint(15).varint(0).varint(0xfffffff0L).varint(1).varint(1).varint(2).bytes());
		// A gap of 2^32 - 1 after a run ending at 100 wraps to 99.
		assertRejectedWith("run", header(2, 0).varint(99).varint(0).varint(0).varint(0xffffffffL).varint(1).varint(2).bytes());
	}

	@Test
	public void overLongVarintIsRejected()
	{
		byte[] bytes = new Encoder().varint(ComponentLayer.VERSION).varint(1).varint(0)
			.raw(0x80, 0x80, 0x80, 0x80, 0x80, 0x00).varint(0).varint(1).bytes();
		assertRejectedWith("varint", bytes);
	}

	@Test
	public void largestRunAndTheLastTileOfTheRangeAreAccepted() throws Exception
	{
		byte[] bytes = header(2, 0).varint(65534).varint(1).varint(0).varint(0xfffffffeL - 65535).varint(1).varint(2).bytes();
		ComponentLayer layer = read(bytes);

		assertEquals(65535, layer.lengths[0]);
		assertEquals(2, layer.component(-1));
	}

	@Test
	public void writerRefusesLayersTheReaderWouldReject()
	{
		assertWriterRefuses(new ComponentLayer(new int[] {0}, new char[] {0}, new int[] {1}, new int[0]));
		assertWriterRefuses(new ComponentLayer(new int[] {0}, new char[] {1}, new int[] {0}, new int[0]));
		assertWriterRefuses(new ComponentLayer(new int[] {0}, new char[] {1}, new int[] {-3}, new int[0]));
	}

	private static void assertWriterRefuses(ComponentLayer layer)
	{
		try
		{
			ComponentLayerWriter.write(layer);
			throw new AssertionError("the writer accepted an unreadable layer");
		}
		catch (IllegalArgumentException expected)
		{
			assertNotNull(expected.getMessage());
		}
	}

	@Test
	public void layerOfARealisticWorldResolvesEveryWalkableTile() throws Exception
	{
		int a = tile(10, 10, 0), b = tile(11, 10, 0), c = tile(40, 40, 0);
		SyntheticCollisionMap world = new SyntheticCollisionMap(Set.of(a, b, c),
			SyntheticCollisionMap.symmetricAdjacency(new int[][] {{a, b}}), 1);
		ComponentLayer layer = read(ComponentLayerWriter.write(RoutingStaticBuilder.generate(world, new int[0])));

		assertEquals(1, layer.component(a));
		assertEquals(1, layer.component(b));
		assertEquals(2, layer.component(c));
		assertEquals(0, layer.component(tile(41, 40, 0)));
	}
}
