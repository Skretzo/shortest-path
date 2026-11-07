package shortestpath.pathfinder.sailing;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import static net.runelite.api.Constants.REGION_SIZE;

/**
 * A number per tile over the regions of plane 0, 0 for most, as bytes that are read where they lie. Written as the
 * count of regions, then per region its coordinates (two shorts) and one of:
 * <ul>
 * <li>{@code 0}, all its numbered tiles one number: the number (an int) and a bit per tile, set where it is;</li>
 * <li>{@code 1}, a few numbers: how many (a byte), each (an int), then a byte per tile, its number's place among them
 * from 1, or 0;</li>
 * <li>{@code 2}, any: an int per tile.</li>
 * </ul>
 */
final class TileLayer
{
	private static final int TILES = REGION_SIZE * REGION_SIZE;
	private static final int UNIFORM = 0, PALETTE = 1;

	private final byte[] data;
	// Where each region's part starts in data (at its format byte), by the caller's region index; -1 for none
	private final int[] offsets;

	/**
	 * The layer at {@code offset} in {@code data}.
	 *
	 * @param regionIndex the caller's index for region (x, y), or -1 for one it doesn't keep
	 */
	TileLayer(byte[] data, int offset, int regionCount, IntBinaryOperator regionIndex)
	{
		this.data = data;
		offsets = new int[regionCount];
		Arrays.fill(offsets, -1);
		int at = offset;
		int count = readInt(at);
		at += Integer.BYTES;
		for (int i = 0; i < count; i++)
		{
			int region = regionIndex.applyAsInt(readShort(at), readShort(at + 2));
			at += 2 * Short.BYTES;
			if (region >= 0) offsets[region] = at;
			int format = data[at];
			if (format == UNIFORM) at += 1 + Integer.BYTES + TILES / Byte.SIZE;
			else if (format == PALETTE) at += 2 + Integer.BYTES * Byte.toUnsignedInt(data[at + 1]) + TILES;
			else at += 1 + Integer.BYTES * TILES;
		}
	}

	/** The number of tile {@code index} of region {@code region}, or 0. */
	int get(int region, int index)
	{
		int at = offsets[region];
		if (at < 0) return 0;
		switch (data[at])
		{
			case UNIFORM:
				return (data[at + 1 + Integer.BYTES + (index >>> 3)] & (1 << (index & 7))) != 0 ? readInt(at + 1) : 0;
			case PALETTE:
			{
				int place = Byte.toUnsignedInt(data[at + 2 + Integer.BYTES * Byte.toUnsignedInt(data[at + 1]) + index]);
				return place == 0 ? 0 : readInt(at + 2 + Integer.BYTES * (place - 1));
			}
			default:
				return readInt(at + 1 + Integer.BYTES * index);
		}
	}

	/** Whether region {@code region} has any numbered tile. */
	boolean has(int region)
	{
		return offsets[region] >= 0;
	}

	private int readInt(int at)
	{
		return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | (data[at + 3] & 0xFF);
	}

	private int readShort(int at)
	{
		return (data[at] & 0xFF) << 8 | (data[at + 1] & 0xFF);
	}
}
