package shortestpath.pathfinder.astar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;

/**
 * Serializes a {@link ComponentLayer} in the format {@link ComponentLayer#read} accepts, and
 * generates the packaged {@code routing-components.bin} from the committed collision map and
 * routing cuts: {@code ComponentLayerWriter CUTS OUTPUT}.
 */
public final class ComponentLayerWriter
{
	private ComponentLayerWriter()
	{
	}

	static byte[] write(ComponentLayer layer)
	{
		int runs = layer.starts.length;
		ByteArrayOutputStream out = new ByteArrayOutputStream(runs * 5 + layer.crossings.length * 4 + 32);
		out.write(ComponentLayer.MAGIC, 0, ComponentLayer.MAGIC.length);
		varint(out, ComponentLayer.VERSION);
		varint(out, runs);
		varint(out, layer.crossings.length);
		for (int i = 0; i < runs; i++)
		{
			if (layer.lengths[i] < 1) throw new IllegalArgumentException("run " + i + " is empty");
			varint(out, layer.lengths[i] - 1);
		}
		for (int i = 0, end = 0; i < runs; i++)
		{
			varint(out, layer.starts[i] - end);
			end = layer.starts[i] + layer.lengths[i];
		}
		for (int i = 0; i < runs; i++)
		{
			if (layer.ids[i] < 1) throw new IllegalArgumentException("run " + i + " has component id " + layer.ids[i]);
			varint(out, layer.ids[i]);
		}
		for (int tile : layer.crossings)
		{
			out.write(tile >>> 24);
			out.write(tile >>> 16);
			out.write(tile >>> 8);
			out.write(tile);
		}
		return out.toByteArray();
	}

	/** Unsigned base-128, least-significant group first. */
	private static void varint(ByteArrayOutputStream out, int value)
	{
		while ((value & ~0x7f) != 0)
		{
			out.write((value & 0x7f) | 0x80);
			value >>>= 7;
		}
		out.write(value);
	}

	public static void main(String[] args) throws IOException
	{
		if (args.length != 2) throw new IllegalArgumentException("usage: ComponentLayerWriter CUTS OUTPUT");
		RoutingCuts cuts = RoutingCuts.read(Files.readAllBytes(Paths.get(args[0])));
		if (cuts.cutCount() == 0) throw new IllegalStateException("no routing cuts in " + args[0]);
		CollisionMap collision = new CollisionMap(SplitFlagMap.fromResources());
		ComponentLayer layer = RoutingStaticBuilder.generate(collision, cuts.pairs());
		byte[] bytes = write(layer);
		Path output = Paths.get(args[1]).toAbsolutePath();
		Files.createDirectories(output.getParent());
		Files.write(output, bytes);
		System.out.println("routing components: " + layer.starts.length + " runs, " + layer.crossings.length / 2
			+ " crossings, " + bytes.length + " bytes -> " + output);
	}
}
