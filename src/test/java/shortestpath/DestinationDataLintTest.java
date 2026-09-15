package shortestpath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

/**
 * Loader-fidelity and format lint for the four destination TSV resources
 * consumed by {@link Destination#loadAllFromResources()}.
 *
 * {@link Destination#addDestinations} degrades silently: a row whose
 * {@code Destination} cell is not a three-part {@code x y z} coordinate is
 * skipped with at most a {@code log.error}, and a dropped resource produces a
 * missing or empty category with no failure. These tests assert the loaded
 * map's category shape, that every committed row's {@code Destination} cell is
 * a concrete three-part numeric coordinate, that every such coordinate
 * actually lands in the loaded set, and that headers carry the documented
 * destination columns.
 */
public class DestinationDataLintTest
{
	/**
	 * The exact resource paths {@link Destination#loadAllFromResources()}
	 * loads, explicitly listed so a change to the consumed set is deliberate.
	 */
	private static final String[] DESTINATION_RESOURCES = {
		"/destinations/game_features/altar.tsv",
		"/destinations/game_features/bank.tsv",
		"/destinations/training/anvil.tsv",
		"/destinations/shopping/apothecary.tsv",
	};

	private static final Set<String> EXPECTED_CATEGORIES = new HashSet<>(Arrays.asList(
		"altar", "bank", "anvil", "apothecary"));

	/**
	 * Columns documented for destination TSVs: the coordinate cell plus the
	 * requirement/info columns {@link Destination} reads.
	 */
	private static final Set<String> DOCUMENTED_COLUMNS = new HashSet<>(Arrays.asList(
		"Destination", "Info", "Skills", "Quests", "Varbits", "VarPlayers"));

	private static final Pattern CONCRETE_POINT = Pattern.compile("^\\d+ \\d+ \\d+$");

	private static String readResource(String path) throws IOException
	{
		return new String(
			Util.readAllBytes(Objects.requireNonNull(ShortestPathPlugin.class.getResourceAsStream(path))),
			StandardCharsets.UTF_8);
	}

	/**
	 * The category key {@link Destination#addDestinations} derives from a
	 * resource path: the path component immediately preceding the file
	 * extension (e.g. {@code /destinations/game_features/bank.tsv -> bank}).
	 */
	private static String categoryKey(String path)
	{
		String[] parts = path.replace(".", "/").split("/");
		return parts[parts.length - 2];
	}

	private static String[] headers(String contents)
	{
		String headerLine = contents.split("\n", -1)[0];
		if (headerLine.endsWith("\r"))
		{
			headerLine = headerLine.substring(0, headerLine.length() - 1);
		}
		if (headerLine.startsWith("# "))
		{
			headerLine = headerLine.substring(2);
		}
		else if (headerLine.startsWith("#"))
		{
			headerLine = headerLine.substring(1);
		}
		return headerLine.split("\t");
	}

	private static int columnIndex(String[] headers, String name)
	{
		for (int i = 0; i < headers.length; i++)
		{
			if (name.equals(headers[i]))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Data rows of a destination file the way {@link Destination#addDestinations}
	 * sees them: lines after the header, comments and blanks skipped, split on
	 * tabs (trailing empty cells dropped, matching the loader's split).
	 */
	private static List<String[]> dataRows(String contents, List<Integer> lineNumbers)
	{
		String[] lines = contents.split("\n", -1);
		List<String[]> rows = new ArrayList<>();
		for (int i = 1; i < lines.length; i++)
		{
			String line = lines[i];
			if (line.endsWith("\r"))
			{
				line = line.substring(0, line.length() - 1);
			}
			if (line.startsWith("#") || line.isBlank())
			{
				continue;
			}
			rows.add(line.split("\t"));
			lineNumbers.add(i + 1);
		}
		return rows;
	}

	private static void report(List<String> failures, String checkName)
	{
		if (!failures.isEmpty())
		{
			StringBuilder message = new StringBuilder();
			message.append(String.format("%s found %d problem(s):\n", checkName, failures.size()));
			for (String failure : failures)
			{
				message.append("  ").append(failure).append('\n');
			}
			Assert.fail(message.toString());
		}
	}

	/**
	 * Loader fidelity: {@link Destination#loadAllFromResources()} must return
	 * exactly the four consumed categories, each non-empty, and every
	 * concrete {@code Destination} cell in every committed row must land in
	 * its category's set. A resource that fails to load or a row the loader
	 * silently skips shows up here as a missing/empty category or a missing
	 * packed point.
	 */
	@Test
	public void testLoadAllFromResourcesProducesEveryRow() throws IOException
	{
		List<String> failures = new ArrayList<>();

		Map<String, Set<Integer>> destinations = Destination.loadAllFromResources();
		Assert.assertEquals(
			"loadAllFromResources() categories differ from the consumed resource set",
			EXPECTED_CATEGORIES, destinations.keySet());

		for (String path : DESTINATION_RESOURCES)
		{
			String category = categoryKey(path);
			Set<Integer> loaded = destinations.get(category);
			if (loaded == null || loaded.isEmpty())
			{
				failures.add(String.format(
					"%s: category '%s' is missing or empty after loadAllFromResources()",
					path, category));
				continue;
			}

			String contents = readResource(path);
			String[] headers = headers(contents);
			int destinationIdx = columnIndex(headers, "Destination");
			if (destinationIdx < 0)
			{
				failures.add(path + ": header carries no Destination column");
				continue;
			}

			List<Integer> lineNumbers = new ArrayList<>();
			List<String[]> rows = dataRows(contents, lineNumbers);
			for (int i = 0; i < rows.size(); i++)
			{
				String cell = destinationIdx < rows.get(i).length ? rows.get(i)[destinationIdx] : "";
				if (!CONCRETE_POINT.matcher(cell).matches())
				{
					// Malformed cells are reported by
					// testDestinationCellsAreThreePartNumeric; skip them here.
					continue;
				}
				String[] parts = cell.split(" ");
				int packed = WorldPointUtil.packWorldPoint(
					Integer.parseInt(parts[0]),
					Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]));
				if (!loaded.contains(packed))
				{
					failures.add(String.format(
						"%s:%d: concrete destination '%s' is absent from the loaded '%s' set",
						path, lineNumbers.get(i), cell, category));
				}
			}
		}

		report(failures, "testLoadAllFromResourcesProducesEveryRow");
	}

	/**
	 * Row-shape fidelity: every non-comment row's {@code Destination} cell
	 * must be a three-part numeric {@code x y z} coordinate. The loader
	 * log-and-skips malformed cells, so a bad row is a test failure naming
	 * file+line rather than a silently dropped destination.
	 */
	@Test
	public void testDestinationCellsAreThreePartNumeric() throws IOException
	{
		List<String> failures = new ArrayList<>();

		for (String path : DESTINATION_RESOURCES)
		{
			String contents = readResource(path);
			String[] headers = headers(contents);
			int destinationIdx = columnIndex(headers, "Destination");
			if (destinationIdx < 0)
			{
				failures.add(path + ": header carries no Destination column");
				continue;
			}

			List<Integer> lineNumbers = new ArrayList<>();
			List<String[]> rows = dataRows(contents, lineNumbers);
			for (int i = 0; i < rows.size(); i++)
			{
				String cell = destinationIdx < rows.get(i).length ? rows.get(i)[destinationIdx] : "";
				if (!CONCRETE_POINT.matcher(cell).matches())
				{
					failures.add(String.format(
						"%s:%d: Destination cell '%s' is not a three-part numeric coordinate",
						path, lineNumbers.get(i), cell));
				}
			}
		}

		report(failures, "testDestinationCellsAreThreePartNumeric");
	}

	/**
	 * Header coverage: each file's header row must carry the {@code
	 * Destination} column and use only documented destination columns.
	 */
	@Test
	public void testHeadersCarryDestinationColumn() throws IOException
	{
		List<String> failures = new ArrayList<>();

		for (String path : DESTINATION_RESOURCES)
		{
			String[] headers = headers(readResource(path));
			boolean hasDestination = false;
			for (String header : headers)
			{
				if ("Destination".equals(header))
				{
					hasDestination = true;
				}
				else if (!DOCUMENTED_COLUMNS.contains(header))
				{
					failures.add(String.format(
						"%s: header cell '%s' is not a documented destination column",
						path, header));
				}
			}
			if (!hasDestination)
			{
				failures.add(path + ": header carries no Destination column");
			}
		}

		report(failures, "testHeadersCarryDestinationColumn");
	}
}
