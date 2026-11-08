package shortestpath.pathfinder.sailing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Names what the generated sailing data was made from: the collision map but for its sailing layer, the moorings, and
 * the version and settings of the sea graph. The generator writes it into the data, and a test fails when the data's no
 * longer matches; the plugin never reads it.
 */
public final class SailingDataFingerprint
{
	// Bump when the sailing layer's rules change, so the data is made again
	private static final int LAYER_VERSION = 1;

	private SailingDataFingerprint()
	{
	}

	/** The fingerprint for the collision map {@code zip} and {@code moorings}. */
	public static String of(byte[] zip, List<SailingMooring> moorings)
	{
		CRC32 crc = new CRC32();
		SortedMap<String, byte[]> entries = new TreeMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip)))
		{
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null)
			{
				if (!entry.isDirectory() && !entry.getName().startsWith(SailingWaters.LAYER + "/"))
					entries.put(entry.getName(), in.readAllBytes());
			}
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		for (Map.Entry<String, byte[]> entry : entries.entrySet())
		{
			crc.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
			crc.update(entry.getValue());
		}
		StringBuilder data = new StringBuilder();
		for (SailingMooring mooring : moorings)
		{
			data.append(mooring.name()).append(mooring.landTile()).append(mooring.berthTile())
				.append(mooring.vessels()).append(mooring.requirements().getSkillLevels())
				.append(mooring.requirements().getQuests()).append('\n');
		}
		data.append(SailingMooring.BOARD_TICKS).append(' ').append(SailingMooring.DISEMBARK_TICKS).append(' ')
			.append(SailingMooring.DOCKING_RANGE);
		crc.update(data.toString().getBytes(StandardCharsets.UTF_8));
		return Long.toHexString(crc.getValue()) + " layer " + LAYER_VERSION + " graph " + SeaGraph.VERSION + " smoothing "
			+ SeaGraph.TOLERANCE + "/" + SeaGraph.NEAR + " margin " + SeaGraph.MARGIN + " slow " + SailingMoves.SLOW_SPEED
			+ " corridor " + SeaGraph.CORRIDOR;
	}

	static byte[] resource(String path)
	{
		try (InputStream in = SailingDataFingerprint.class.getResourceAsStream(path))
		{
			if (in == null) throw new IllegalStateException("missing " + path);
			return in.readAllBytes();
		}
		catch (IOException error)
		{
			throw new IllegalStateException("cannot read " + path, error);
		}
	}
}
