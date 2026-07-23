package shortestpath;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Item;
import org.junit.Assert;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.transport.Transport;

public final class SnapshotAssertions
{
	private static final Path SNAPSHOT_ROOT = Paths.get("src", "test", "resources", "snapshots");
	private static final String UPDATE_PROPERTY = "updateSnapshots";
	private static final String UPDATE_ENV = "UPDATE_SNAPSHOTS";

	private SnapshotAssertions()
	{
	}

	public static void assertSnapshot(String snapshotName, Object value)
	{
		assertSnapshot(SnapshotState.EMPTY, snapshotName, value);
	}

	public static void assertRouteSnapshot(String snapshotName, List<PathStep> steps)
	{
		assertRouteSnapshot(SnapshotState.EMPTY, snapshotName, steps);
	}

	public static void assertRouteSnapshot(SnapshotState state, String snapshotName, List<PathStep> steps)
	{
		StringBuilder out = new StringBuilder();
		appendRoute(out, state == null ? SnapshotState.EMPTY : state, steps, "");
		String actual = ensureTrailingNewline(out);
		assertRenderedSnapshot(snapshotName, actual);
	}

	public static void assertSnapshot(SnapshotState state, String snapshotName, Object value)
	{
		String actual = render(state, value);
		assertRenderedSnapshot(snapshotName, actual);
	}

	private static void assertRenderedSnapshot(String snapshotName, String actual)
	{
		Path snapshotPath = snapshotPath(snapshotName);

		if (shouldUpdateSnapshots())
		{
			writeSnapshot(snapshotPath, actual);
			return;
		}

		if (!Files.exists(snapshotPath))
		{
			writeSnapshot(snapshotPath, actual);
			Assert.fail("Created missing snapshot " + snapshotPath + ". Review it and re-run the test.");
		}

		String expected;
		try
		{
			expected = normalizeNewlines(Files.readString(snapshotPath, StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new AssertionError("Could not read snapshot " + snapshotPath, e);
		}

		Assert.assertEquals("Snapshot mismatch: " + snapshotPath, expected, actual);
	}

	public static String render(Object value)
	{
		return render(SnapshotState.EMPTY, value);
	}

	public static String render(SnapshotState state, Object value)
	{
		StringBuilder out = new StringBuilder();
		appendValue(out, state == null ? SnapshotState.EMPTY : state, value, "");
		return ensureTrailingNewline(out);
	}

	private static String ensureTrailingNewline(StringBuilder out)
	{
		if (out.length() == 0 || out.charAt(out.length() - 1) != '\n')
		{
			out.append('\n');
		}
		return out.toString();
	}

	private static Path snapshotPath(String snapshotName)
	{
		if (snapshotName == null || snapshotName.isBlank())
		{
			throw new IllegalArgumentException("Snapshot name must not be blank");
		}
		if (!snapshotName.matches("[A-Za-z0-9._-]+"))
		{
			throw new IllegalArgumentException("Snapshot name may only contain letters, digits, dots, underscores, and dashes: " + snapshotName);
		}
		return SNAPSHOT_ROOT.resolve(snapshotName + ".snap");
	}

	private static boolean shouldUpdateSnapshots()
	{
		return Boolean.getBoolean(UPDATE_PROPERTY) || "true".equalsIgnoreCase(System.getenv(UPDATE_ENV));
	}

	private static void writeSnapshot(Path snapshotPath, String actual)
	{
		try
		{
			Files.createDirectories(snapshotPath.getParent());
			Files.writeString(snapshotPath, actual, StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new AssertionError("Could not write snapshot " + snapshotPath, e);
		}
	}

	private static String normalizeNewlines(String value)
	{
		return value.replace("\r\n", "\n").replace('\r', '\n');
	}

	private static void appendValue(StringBuilder out, SnapshotState state, Object value, String indent)
	{
		if (value == null)
		{
			out.append("null\n");
		}
		else if (value instanceof Pathfinder)
		{
			appendPathfinder(out, state, (Pathfinder) value, indent);
		}
		else if (value instanceof PathfinderResult)
		{
			appendPathfinderResult(out, state, (PathfinderResult) value, indent);
		}
		else if (isPathStepList(value))
		{
			appendRoute(out, state, (List<?>) value, indent);
		}
		else if (value instanceof PathStep)
		{
			out.append(indent).append(formatPathStep((PathStep) value)).append('\n');
		}
		else if (isPackedWorldPoint(value))
		{
			out.append(indent).append(formatPoint((Integer) value)).append('\n');
		}
		else if (value instanceof Transport)
		{
			out.append(indent).append(formatTransport((Transport) value)).append('\n');
		}
		else if (value instanceof Map<?, ?>)
		{
			appendMap(out, state, (Map<?, ?>) value, indent);
		}
		else if (value instanceof Iterable<?>)
		{
			appendIterable(out, state, (Iterable<?>) value, indent);
		}
		else if (value.getClass().isArray())
		{
			appendArray(out, state, value, indent);
		}
		else
		{
			out.append(indent).append(value).append('\n');
		}
	}

	private static void appendPathfinder(StringBuilder out, SnapshotState state, Pathfinder pathfinder, String indent)
	{
		PathfinderResult result = pathfinder.getResult();
		if (result != null)
		{
			appendPathfinderResult(out, state, result, indent);
			return;
		}

		out.append(indent).append("pathfinder:\n");
		out.append(indent).append("\tstart: ").append(formatPoint(pathfinder.getStart())).append('\n');
		out.append(indent).append("\ttargets:\n");
		appendIterable(out, state, pathfinder.getTargets(), indent + "\t\t");
		out.append(indent).append("\tdone: ").append(pathfinder.isDone()).append('\n');
		appendRoute(out, state, pathfinder.getPath(), indent + "\t");
	}

	private static void appendPathfinderResult(StringBuilder out, SnapshotState state, PathfinderResult result, String indent)
	{
		out.append(indent).append("pathfinder-result:\n");
		out.append(indent).append("\tstart: ").append(formatPoint(result.getStart())).append('\n');
		out.append(indent).append("\ttarget: ").append(formatPoint(result.getTarget())).append('\n');
		out.append(indent).append("\treached: ").append(result.isReached()).append('\n');
		out.append(indent).append("\tclosest-reached: ").append(formatPoint(result.getClosestReachedPoint())).append('\n');
		out.append(indent).append("\ttermination: ").append(result.getTerminationReason()).append('\n');
		out.append(indent).append("\tnodes-checked: ").append(result.getNodesChecked()).append('\n');
		out.append(indent).append("\ttransports-checked: ").append(result.getTransportsChecked()).append('\n');
		appendRoute(out, state, result.getPathSteps(), indent + "\t");
	}

	private static void appendRoute(StringBuilder out, SnapshotState state, List<?> steps, String indent)
	{
		appendState(out, state, indent);
		out.append(indent).append("route:\n");
		out.append(indent).append("\tsteps: ").append(steps.size()).append('\n');
		for (int i = 0; i < steps.size(); i++)
		{
			PathStep step = (PathStep) steps.get(i);
			if (i > 0 && !((PathStep) steps.get(i - 1)).isBankVisited() && step.isBankVisited())
			{
				out.append(indent).append("\t=> bank visited\n");
			}
			if (i > 0)
			{
				PathStep previous = (PathStep) steps.get(i - 1);
				Transport transport = findTransport(state, previous, step);
				if (transport != null)
				{
					out.append(indent).append("\t=> ").append(formatTransport(transport)).append('\n');
				}
				else if (!isAdjacent(previous.getPackedPosition(), step.getPackedPosition()))
				{
					out.append(indent).append("\t=> unlabelled transport or jump\n");
				}
			}
			out.append(indent).append('\t').append(padIndex(i)).append(' ').append(formatPathStep(step)).append('\n');
		}
	}

	private static Transport findTransport(SnapshotState state, PathStep previous, PathStep step)
	{
		Collection<Transport> transports = state.getTransports(previous.getPackedPosition(), previous.isBankVisited());
		for (Transport transport : transports)
		{
			if (transport.getDestination() == step.getPackedPosition())
			{
				return transport;
			}
		}
		return null;
	}

	private static boolean isAdjacent(int previous, int current)
	{
		return WorldPointUtil.unpackWorldPlane(previous) == WorldPointUtil.unpackWorldPlane(current)
			&& Math.abs(WorldPointUtil.unpackWorldX(previous) - WorldPointUtil.unpackWorldX(current)) <= 1
			&& Math.abs(WorldPointUtil.unpackWorldY(previous) - WorldPointUtil.unpackWorldY(current)) <= 1;
	}

	private static String padIndex(int i)
	{
		if (i < 10)
		{
			return "000" + i;
		}
		if (i < 100)
		{
			return "00" + i;
		}
		if (i < 1000)
		{
			return "0" + i;
		}
		return Integer.toString(i);
	}

	private static String formatPathStep(PathStep step)
	{
		return formatPoint(step.getPackedPosition());
	}

	private static void appendState(StringBuilder out, SnapshotState state, String indent)
	{
		if (!state.hasMetadata())
		{
			return;
		}
		out.append(indent).append("state:\n");
		appendItems(out, "inventory", state.inventory, indent + "\t");
		appendItems(out, "equipment", state.equipment, indent + "\t");
		appendItems(out, "bank", state.bank, indent + "\t");
		if (state.skillLevel != null)
		{
			out.append(indent).append("\tlevels: all=").append(state.skillLevel).append('\n');
		}
		if (!state.varbits.isEmpty())
		{
			out.append(indent).append("\tvarbits:");
			state.varbits.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(entry -> out.append(' ').append(entry.getKey()).append('=').append(entry.getValue()));
			out.append('\n');
		}
	}

	private static void appendItems(StringBuilder out, String label, Item[] items, String indent)
	{
		if (items == null || items.length == 0)
		{
			return;
		}
		out.append(indent).append(label).append(':');
		for (Item item : items)
		{
			if (item != null && item.getId() >= 0 && item.getQuantity() > 0)
			{
				out.append(' ').append(item.getId()).append('x').append(item.getQuantity());
			}
		}
		out.append('\n');
	}

	private static String formatTransport(Transport transport)
	{
		List<String> parts = new ArrayList<>();
		parts.add("type=" + transport.getType());
		parts.add("from=" + formatPoint(transport.getOrigin()));
		parts.add("to=" + formatPoint(transport.getDestination()));
		parts.add("duration=" + transport.getDuration());
		if (transport.getDisplayInfo() != null)
		{
			parts.add("display=\"" + escapeInline(transport.getDisplayInfo()) + "\"");
		}
		if (transport.getObjectInfo() != null)
		{
			parts.add("object=\"" + escapeInline(transport.getObjectInfo()) + "\"");
		}
		return "transport " + String.join(" ", parts);
	}

	private static String formatPoint(int packedPoint)
	{
		if (packedPoint == WorldPointUtil.UNDEFINED)
		{
			return "undefined";
		}
		return "(" + WorldPointUtil.unpackWorldX(packedPoint) + ", "
			+ WorldPointUtil.unpackWorldY(packedPoint) + ", "
			+ WorldPointUtil.unpackWorldPlane(packedPoint) + ")";
	}

	private static String escapeInline(String value)
	{
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private static boolean isPackedWorldPoint(Object value)
	{
		if (!(value instanceof Integer))
		{
			return false;
		}

		int packed = (Integer) value;
		if (packed == WorldPointUtil.UNDEFINED)
		{
			return true;
		}

		int x = WorldPointUtil.unpackWorldX(packed);
		int y = WorldPointUtil.unpackWorldY(packed);
		return x > 0 && x < 16384 && y > 0 && y < 16384;
	}

	private static boolean isPathStepList(Object value)
	{
		if (!(value instanceof List<?>))
		{
			return false;
		}

		List<?> list = (List<?>) value;
		return !list.isEmpty() && list.get(0) instanceof PathStep;
	}

	private static void appendMap(StringBuilder out, SnapshotState state, Map<?, ?> map, String indent)
	{
		out.append(indent).append("map:\n");
		List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
		entries.sort(Comparator.comparing(entry -> Objects.toString(entry.getKey())));
		for (Map.Entry<?, ?> entry : entries)
		{
			out.append(indent).append("\t").append(entry.getKey()).append(":\n");
			appendValue(out, state, entry.getValue(), indent + "\t\t");
		}
	}

	private static void appendIterable(StringBuilder out, SnapshotState state, Iterable<?> values, String indent)
	{
		out.append(indent).append("list:\n");
		for (Object item : values)
		{
			out.append(indent).append("\t- ");
			appendInlineOrNested(out, state, item, indent + "\t  ");
		}
	}

	private static void appendArray(StringBuilder out, SnapshotState state, Object array, String indent)
	{
		out.append(indent).append("list:\n");
		int length = Array.getLength(array);
		for (int i = 0; i < length; i++)
		{
			out.append(indent).append("\t- ");
			appendInlineOrNested(out, state, Array.get(array, i), indent + "\t  ");
		}
	}

	private static void appendInlineOrNested(StringBuilder out, SnapshotState state, Object value, String indent)
	{
		if (isPackedWorldPoint(value))
		{
			out.append(formatPoint((Integer) value)).append('\n');
			return;
		}
		if (value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>)
		{
			out.append(value).append('\n');
			return;
		}

		out.append('\n');
		appendValue(out, state, value, indent);
	}

	public interface TransportProvider
	{
		Collection<Transport> getTransports(int origin, boolean bankVisited);
	}

	public static final class SnapshotState
	{
		private static final SnapshotState EMPTY = new SnapshotState((origin, bankVisited) -> List.of());

		private final TransportProvider transportProvider;
		private Item[] inventory;
		private Item[] equipment;
		private Item[] bank;
		private Integer skillLevel;
		private Map<Integer, Integer> varbits = Map.of();

		private SnapshotState(TransportProvider transportProvider)
		{
			this.transportProvider = transportProvider;
		}

		public static SnapshotState empty()
		{
			return EMPTY;
		}

		public static SnapshotState withTransports(TransportProvider transportProvider)
		{
			return new SnapshotState(transportProvider);
		}

		public SnapshotState withMetadata(Item[] inventory, Item[] equipment, Item[] bank,
			Integer skillLevel, Map<Integer, Integer> varbits)
		{
			this.inventory = inventory;
			this.equipment = equipment;
			this.bank = bank;
			this.skillLevel = skillLevel;
			this.varbits = varbits == null ? Map.of() : varbits;
			return this;
		}

		private boolean hasMetadata()
		{
			return inventory != null || equipment != null || bank != null || skillLevel != null || !varbits.isEmpty();
		}

		private Collection<Transport> getTransports(int origin, boolean bankVisited)
		{
			return transportProvider.getTransports(origin, bankVisited);
		}
	}
}
