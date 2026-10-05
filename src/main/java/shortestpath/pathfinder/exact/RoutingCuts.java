package shortestpath.pathfinder.exact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Separator cut edges for the exact backend, stored as {@code routing-cuts.bin}.
 *
 * <p>The cuts are generated offline with KaHIP by the tooling repository and committed next to
 * {@code collision-map.zip}. They only affect performance: {@link RoutingStaticBuilder} ignores
 * cuts that are no longer walking edges or no longer separate anything, so stale cuts never make
 * routes wrong.
 *
 * <p>Format, all little-endian:
 * <pre>
 * 8 bytes  ASCII "RSPCUTS\0"
 * u32      format version, exactly 1
 * u64      FNV-1a 64 of the collision-map.zip bytes the cuts were generated from (informational)
 * i32 x 5  maximum component size, minimum child size, maximum separator size, imbalance, seed
 * u8 + n   KaHIP preconfiguration name, ASCII
 * u32      cut count
 * u32 x 2n cut pairs (from, to): from &lt; to (unsigned), pairs strictly ascending
 * </pre>
 * The reader rejects a wrong header, truncation, unsorted or duplicate pairs, and trailing bytes.
 */
public final class RoutingCuts
{
	public static final String RESOURCE = "/routing-cuts.bin";
	private static final byte[] MAGIC = "RSPCUTS\0".getBytes(StandardCharsets.US_ASCII);
	private static final int FORMAT_VERSION = 1;
	private static final long FNV_OFFSET = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;

	private final long collisionFingerprint;
	private final Parameters parameters;
	private final int[] pairs;

	/** The KaHIP partitioning parameters the cuts were generated with. */
	public static final class Parameters
	{
		public final int maximumComponentSize;
		public final int minimumChildSize;
		public final int maximumSeparatorSize;
		public final int imbalance;
		public final int seed;
		public final String preconfiguration;

		public Parameters(int maximumComponentSize, int minimumChildSize, int maximumSeparatorSize, int imbalance,
			int seed, String preconfiguration)
		{
			this.maximumComponentSize = maximumComponentSize;
			this.minimumChildSize = minimumChildSize;
			this.maximumSeparatorSize = maximumSeparatorSize;
			this.imbalance = imbalance;
			this.seed = seed;
			this.preconfiguration = Objects.requireNonNull(preconfiguration, "preconfiguration");
			if (preconfiguration.length() > 255 || !StandardCharsets.US_ASCII.newEncoder().canEncode(preconfiguration))
				throw new IllegalArgumentException("preconfiguration must be ASCII and at most 255 characters");
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof Parameters)) return false;
			Parameters that = (Parameters) other;
			return maximumComponentSize == that.maximumComponentSize && minimumChildSize == that.minimumChildSize
				&& maximumSeparatorSize == that.maximumSeparatorSize && imbalance == that.imbalance
				&& seed == that.seed && preconfiguration.equals(that.preconfiguration);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(maximumComponentSize, minimumChildSize, maximumSeparatorSize, imbalance, seed,
				preconfiguration);
		}

		@Override
		public String toString()
		{
			return maximumComponentSize + " " + minimumChildSize + " " + maximumSeparatorSize + " " + imbalance
				+ " " + seed + " " + preconfiguration;
		}
	}

	/**
	 * @param pairs cut edges as packed {@code (from, to)} tile pairs; they are canonicalised
	 *              ({@code from < to}), sorted and de-duplicated
	 */
	public RoutingCuts(long collisionFingerprint, Parameters parameters, int[] pairs)
	{
		if (pairs.length % 2 != 0) throw new IllegalArgumentException("cut pairs must have even length");
		this.collisionFingerprint = collisionFingerprint;
		this.parameters = Objects.requireNonNull(parameters, "parameters");
		this.pairs = canonicalise(pairs);
	}

	private RoutingCuts(long collisionFingerprint, Parameters parameters, int[] pairs, boolean trusted)
	{
		this.collisionFingerprint = collisionFingerprint;
		this.parameters = parameters;
		this.pairs = pairs;
	}

	public long collisionFingerprint()
	{
		return collisionFingerprint;
	}

	public Parameters parameters()
	{
		return parameters;
	}

	public int cutCount()
	{
		return pairs.length / 2;
	}

	/** Cut edges as a flat array: {@code [2*i]} is the lower tile, {@code [2*i+1]} the higher. */
	public int[] pairs()
	{
		return pairs.clone();
	}

	/** No cuts at all: builds without separators, so it only costs performance. */
	public static RoutingCuts empty()
	{
		return new RoutingCuts(0, new Parameters(0, 0, 0, 0, 0, ""), new int[0]);
	}

	/**
	 * Loads the packaged {@code routing-cuts.bin}, or an empty index when the jar does not
	 * contain it: missing cuts only affect build performance, never the routes. A corrupt
	 * resource still fails loudly, since that indicates a broken build rather than an
	 * optional one.
	 */
	public static RoutingCuts loadFromResources()
	{
		try (InputStream stream = RoutingCuts.class.getResourceAsStream(RESOURCE))
		{
			if (stream == null) return empty();
			return read(stream.readAllBytes());
		}
		catch (IOException error)
		{
			throw new IllegalStateException("cannot load " + RESOURCE + ": " + error.getMessage(), error);
		}
	}

	public static RoutingCuts read(byte[] bytes) throws IOException
	{
		ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		byte[] magic = new byte[MAGIC.length];
		require(input, magic.length, "magic");
		input.get(magic);
		if (!Arrays.equals(magic, MAGIC)) throw new IOException("routing-cuts wrong magic");
		require(input, 4 + 8 + 5 * 4 + 1, "header");
		int version = input.getInt();
		if (version != FORMAT_VERSION) throw new IOException("unsupported routing-cuts version " + version);
		long fingerprint = input.getLong();
		int maximumComponentSize = input.getInt();
		int minimumChildSize = input.getInt();
		int maximumSeparatorSize = input.getInt();
		int imbalance = input.getInt();
		int seed = input.getInt();
		int nameLength = Byte.toUnsignedInt(input.get());
		require(input, nameLength, "preconfiguration");
		byte[] name = new byte[nameLength];
		input.get(name);
		for (byte value : name)
			if (value < 0x20 || value > 0x7e) throw new IOException("routing-cuts preconfiguration is not ASCII");
		require(input, 4, "cut count");
		long count = Integer.toUnsignedLong(input.getInt());
		if (count * 8 != input.remaining())
			throw new IOException(count * 8 > input.remaining() ? "truncated routing-cuts pairs"
				: "routing-cuts has trailing bytes");
		int[] pairs = new int[(int) count * 2];
		for (int i = 0; i < pairs.length; i += 2)
		{
			pairs[i] = input.getInt();
			pairs[i + 1] = input.getInt();
			if (Integer.compareUnsigned(pairs[i], pairs[i + 1]) >= 0)
				throw new IOException("routing-cuts pair " + i / 2 + " is not canonical");
			if (i > 0 && comparePairs(pairs, i - 2, i) >= 0)
				throw new IOException("routing-cuts pairs are not strictly ascending at " + i / 2);
		}
		return new RoutingCuts(fingerprint, new Parameters(maximumComponentSize, minimumChildSize,
			maximumSeparatorSize, imbalance, seed, new String(name, StandardCharsets.US_ASCII)), pairs, true);
	}

	public byte[] write()
	{
		byte[] name = parameters.preconfiguration.getBytes(StandardCharsets.US_ASCII);
		ByteBuffer output = ByteBuffer.allocate(MAGIC.length + 4 + 8 + 5 * 4 + 1 + name.length + 4 + pairs.length * 4)
			.order(ByteOrder.LITTLE_ENDIAN);
		output.put(MAGIC);
		output.putInt(FORMAT_VERSION);
		output.putLong(collisionFingerprint);
		output.putInt(parameters.maximumComponentSize);
		output.putInt(parameters.minimumChildSize);
		output.putInt(parameters.maximumSeparatorSize);
		output.putInt(parameters.imbalance);
		output.putInt(parameters.seed);
		output.put((byte) name.length);
		output.put(name);
		output.putInt(pairs.length / 2);
		for (int value : pairs)
			output.putInt(value);
		return output.array();
	}

	/** FNV-1a 64 over raw bytes; used to identify the collision map a cut file was generated from. */
	public static long fingerprint(byte[] bytes)
	{
		long hash = FNV_OFFSET;
		for (byte value : bytes)
			hash = (hash ^ (value & 0xffL)) * FNV_PRIME;
		return hash;
	}

	private static void require(ByteBuffer input, int bytes, String label) throws IOException
	{
		if (input.remaining() < bytes) throw new IOException("truncated routing-cuts " + label);
	}

	private static int[] canonicalise(int[] raw)
	{
		long[] keys = new long[raw.length / 2];
		for (int i = 0; i < keys.length; i++)
		{
			int a = raw[2 * i], b = raw[2 * i + 1];
			if (a == b) throw new IllegalArgumentException("cut is a self edge");
			int low = Integer.compareUnsigned(a, b) < 0 ? a : b;
			int high = low == a ? b : a;
			keys[i] = (Integer.toUnsignedLong(low) << 32) | Integer.toUnsignedLong(high);
		}
		long[] unique = Arrays.stream(keys).sorted().distinct().toArray();
		int[] result = new int[unique.length * 2];
		for (int i = 0; i < unique.length; i++)
		{
			result[2 * i] = (int) (unique[i] >>> 32);
			result[2 * i + 1] = (int) unique[i];
		}
		return result;
	}

	private static int comparePairs(int[] pairs, int left, int right)
	{
		int compare = Integer.compareUnsigned(pairs[left], pairs[right]);
		return compare != 0 ? compare : Integer.compareUnsigned(pairs[left + 1], pairs[right + 1]);
	}
}
