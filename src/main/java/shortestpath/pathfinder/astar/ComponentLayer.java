package shortestpath.pathfinder.astar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import shortestpath.Util;

/**
 * The collision-derived half of the A* routing data, stored as {@code routing-components.bin}: a
 * routing component id for every walkable tile of the collision map, plus the separator crossings
 * between components.
 *
 * <p>Runs are maximal stretches of consecutive packed tiles that share one routing component id,
 * ascending in unsigned packed order and at most 65,535 tiles long. Ids are numbered from 1 in order
 * of each component's first tile, over all walkable tiles, with the valid separator cuts blocked.
 * Natural components (walking edges only) are not stored: they are the routing components joined
 * through the crossings.
 *
 * <p>Format: the 8-byte magic, then unsigned base-128 varints, least-significant group first, for
 * the version, the run count and the crossing int count; then three columns of run-count varints
 * (run length minus one, the gap from the previous run's end with the first gap measured from 0 in
 * unsigned 32-bit arithmetic, and the routing component id); then the crossings as big-endian 32-bit
 * packed tiles in (from, to) pairs. The reader rejects any other shape, so a stale or damaged
 * resource fails loudly instead of routing differently.
 */
final class ComponentLayer
{
	static final String RESOURCE = "/routing-components.bin";
	static final byte[] MAGIC = "RSPCOMP\0".getBytes(StandardCharsets.US_ASCII);
	static final int VERSION = 1;

	final int[] starts;
	final char[] lengths;
	final int[] ids;
	final int[] crossings;

	ComponentLayer(int[] starts, char[] lengths, int[] ids, int[] crossings)
	{
		this.starts = starts;
		this.lengths = lengths;
		this.ids = ids;
		this.crossings = crossings;
	}

	static ComponentLayer read(InputStream stream) throws IOException
	{
		if (stream == null) throw new IOException("missing " + RESOURCE);
		ByteBuffer in = ByteBuffer.wrap(Util.readAllBytes(stream));
		byte[] magic = new byte[MAGIC.length];
		if (in.remaining() < magic.length) throw new IOException("truncated " + RESOURCE);
		in.get(magic);
		if (!Arrays.equals(magic, MAGIC)) throw new IOException("wrong magic in " + RESOURCE);
		int version = varint(in);
		if (version != VERSION) throw new IOException("unsupported version " + version + " of " + RESOURCE);
		int runs = varint(in);
		int crossings = varint(in);
		if (runs < 0 || runs > in.remaining() || crossings < 0 || (long) crossings * 4 > in.remaining())
			throw new IOException("bad counts in " + RESOURCE);
		ComponentLayer layer = new ComponentLayer(new int[runs], new char[runs], new int[runs], new int[crossings]);
		for (int i = 0; i < runs; i++)
			layer.lengths[i] = (char) (varint(in) + 1);
		for (int i = 0, end = 0; i < runs; i++)
			end = (layer.starts[i] = end + varint(in)) + layer.lengths[i];
		for (int i = 0; i < runs; i++)
			layer.ids[i] = varint(in);
		for (int i = 0; i < crossings; i++)
			layer.crossings[i] = in.getInt();
		if (in.hasRemaining()) throw new IOException("trailing bytes in " + RESOURCE);
		return layer;
	}

	private static int varint(ByteBuffer in)
	{
		int value = 0;
		for (int shift = 0; ; shift += 7)
		{
			byte next = in.get();
			value |= (next & 0x7f) << shift;
			if (next >= 0) return value;
		}
	}

	/** The routing component of a tile, or 0 when no run covers it. */
	int component(int tile)
	{
		int low = 0, high = starts.length - 1;
		while (low <= high)
		{
			int middle = (low + high) >>> 1;
			if (Integer.compareUnsigned(tile, starts[middle]) < 0) high = middle - 1;
			else if (Integer.compareUnsigned(tile - starts[middle], lengths[middle]) < 0) return ids[middle];
			else low = middle + 1;
		}
		return 0;
	}
}
