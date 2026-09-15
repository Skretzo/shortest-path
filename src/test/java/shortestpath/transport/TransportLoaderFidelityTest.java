package shortestpath.transport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.ShortestPathPlugin;
import shortestpath.Util;
import shortestpath.WorldPointUtil;
import shortestpath.transport.parser.ItemRequirementParser;
import shortestpath.transport.parser.QuestParser;
import shortestpath.transport.parser.SkillRequirementParser;
import shortestpath.transport.parser.TransportRecord;
import shortestpath.transport.parser.TsvParser;
import shortestpath.transport.parser.VarRequirement;
import shortestpath.transport.parser.VarRequirementParser;

/**
 * Loader-fidelity lint for the committed transport TSV data.
 *
 * The transport parsers degrade silently in several places: {@link TsvParser}
 * maps only the columns a row actually carries, {@code WorldPointParser} turns
 * any non-three-part coordinate into a permutation marker, and the requirement
 * parsers log-and-continue on bad tokens. A malformed row can therefore pass a
 * "file loaded" check while producing no transport at all. These tests run
 * every committed row through the real {@link TsvParser} ->
 * {@link TransportLoader#addTransportsFromContents} path and assert that a
 * concretely-anchored row always yields a {@link Transport} in the loader's
 * map, that header cells match the canonical {@link TransportRecord.Fields}
 * names exactly, and that requirement cells are fully consumed by their
 * parsers. Failures are collected per file so one run reports every bad row.
 */
public class TransportLoaderFidelityTest
{
	/**
	 * Every committed transport resource the plugin loads. Explicitly listed so
	 * a file dropped from the data set is a test failure, not an invisible
	 * reduction in coverage.
	 */
	private static final String[] TRANSPORT_RESOURCES = {
		"/transports/agility_shortcuts.tsv",
		"/transports/boats.tsv",
		"/transports/canoes.tsv",
		"/transports/charter_ships.tsv",
		"/transports/fairy_rings.tsv",
		"/transports/gnome_gliders.tsv",
		"/transports/hot_air_balloons.tsv",
		"/transports/magic_carpets.tsv",
		"/transports/magic_mushtrees.tsv",
		"/transports/minecarts.tsv",
		"/transports/quetzal_whistle.tsv",
		"/transports/quetzals.tsv",
		"/transports/seasonal_transports.tsv",
		"/transports/ships.tsv",
		"/transports/spirit_trees.tsv",
		"/transports/teleportation_boxes.tsv",
		"/transports/teleportation_items.tsv",
		"/transports/teleportation_levers.tsv",
		"/transports/teleportation_minigames.tsv",
		"/transports/teleportation_portals.tsv",
		"/transports/teleportation_portals_poh.tsv",
		"/transports/teleportation_spells.tsv",
		"/transports/teleportation_spells_home.tsv",
		"/transports/transports.tsv",
		"/transports/wilderness_obelisks.tsv",
	};

	private static final Set<String> CANONICAL_FIELDS = new HashSet<>(Arrays.asList(
		TransportRecord.Fields.ORIGIN,
		TransportRecord.Fields.DESTINATION,
		TransportRecord.Fields.SKILLS,
		TransportRecord.Fields.ITEMS,
		TransportRecord.Fields.QUESTS,
		TransportRecord.Fields.DURATION,
		TransportRecord.Fields.DISPLAY_INFO,
		TransportRecord.Fields.CONSUMABLE,
		TransportRecord.Fields.WILDERNESS_LEVEL,
		TransportRecord.Fields.OBJECT_INFO,
		TransportRecord.Fields.VARBITS,
		TransportRecord.Fields.VAR_PLAYERS,
		TransportRecord.Fields.REGION_OVERRIDE));

	/**
	 * A concrete coordinate cell is exactly three space-separated unsigned
	 * integers. Anything else non-empty silently becomes a permutation marker
	 * inside {@code WorldPointParser}.
	 */
	private static final Pattern CONCRETE_POINT = Pattern.compile("^\\d+ \\d+ \\d+$");

	/**
	 * A single raw TSV data row, kept parallel to the record list
	 * {@link TsvParser#parse} produces so assertion failures can name the
	 * file line they came from.
	 */
	private static final class RawFile
	{
		final String[] headers;
		final List<String[]> dataFields = new ArrayList<>();
		final List<Integer> lineNumbers = new ArrayList<>();

		RawFile(String[] headers)
		{
			this.headers = headers;
		}
	}

	private static String readResource(String path) throws IOException
	{
		return new String(
			Util.readAllBytes(Objects.requireNonNull(ShortestPathPlugin.class.getResourceAsStream(path))),
			StandardCharsets.UTF_8);
	}

	/**
	 * Independently scans raw file contents the same way {@link TsvParser}
	 * does: first line is the header (a leading {@code #} or {@code # } is
	 * stripped), later lines starting with {@code #} or blank are skipped.
	 */
	private static RawFile scanRaw(String contents)
	{
		String[] lines = contents.split("\n", -1);
		Assert.assertTrue("Transport file is empty", lines.length > 0);

		String headerLine = stripCarriageReturn(lines[0]);
		if (headerLine.startsWith("# "))
		{
			headerLine = headerLine.substring(2);
		}
		else if (headerLine.startsWith("#"))
		{
			headerLine = headerLine.substring(1);
		}
		RawFile raw = new RawFile(headerLine.split("\t"));

		for (int i = 1; i < lines.length; i++)
		{
			String line = stripCarriageReturn(lines[i]);
			if (line.startsWith("#") || line.isBlank())
			{
				continue;
			}
			raw.dataFields.add(line.split("\t", -1));
			raw.lineNumbers.add(i + 1);
		}
		return raw;
	}

	private static String stripCarriageReturn(String line)
	{
		return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
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

	private static String cell(String[] fields, int index)
	{
		if (index < 0 || index >= fields.length)
		{
			return "";
		}
		return fields[index];
	}

	private static boolean isConcrete(String value)
	{
		return CONCRETE_POINT.matcher(value).matches();
	}

	private static int packConcrete(String value)
	{
		String[] parts = value.split(" ");
		return WorldPointUtil.packWorldPoint(
			Integer.parseInt(parts[0]),
			Integer.parseInt(parts[1]),
			Integer.parseInt(parts[2]));
	}

	private static TransportType typeFor(String path)
	{
		for (TransportType type : TransportType.values())
		{
			if (path.equals(type.getResourcePath()))
			{
				return type;
			}
		}
		return null;
	}

	private static boolean mapContainsDestination(Map<Integer, Set<Transport>> transports, int packedDestination)
	{
		for (Set<Transport> set : transports.values())
		{
			for (Transport transport : set)
			{
				if (transport.getDestination() == packedDestination)
				{
					return true;
				}
			}
		}
		return false;
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
	 * Row-count and parse fidelity: every row whose anchor cell is a concrete
	 * {@code x y z} triple must produce a {@link Transport} through the real
	 * {@link TsvParser} + {@link TransportLoader#addTransportsFromContents}
	 * path. A row that is well-formed yet yields no map entry is a silent
	 * drop, which is exactly what this test exists to catch.
	 *
	 * Rows whose concrete origin equals their concrete destination are
	 * exempt: {@link TransportLoader#addTransportsFromContents} documents
	 * that identical A->A transports are intentionally skipped.
	 */
	@Test
	public void testConcreteRowsProduceTransports() throws IOException
	{
		List<String> failures = new ArrayList<>();

		for (String path : TRANSPORT_RESOURCES)
		{
			String contents = readResource(path);
			RawFile raw = scanRaw(contents);

			List<TransportRecord> records = new TsvParser().parse(contents);
			if (records.size() != raw.dataFields.size())
			{
				failures.add(String.format(
					"%s: TsvParser produced %d records from %d data rows",
					path, records.size(), raw.dataFields.size()));
			}

			TransportType type = typeFor(path);
			if (type == null)
			{
				failures.add(path + ": no TransportType maps this resource path");
				continue;
			}

			Map<Integer, Set<Transport>> transports = new HashMap<>();
			TransportLoader.addTransportsFromContents(transports, contents, type,
				type.hasRadiusThreshold() ? type.getRadiusThreshold() : 0);

			int originIdx = columnIndex(raw.headers, TransportRecord.Fields.ORIGIN);
			int destinationIdx = columnIndex(raw.headers, TransportRecord.Fields.DESTINATION);

			for (int i = 0; i < raw.dataFields.size(); i++)
			{
				String[] fields = raw.dataFields.get(i);
				int lineNo = raw.lineNumbers.get(i);
				String originCell = cell(fields, originIdx);
				String destinationCell = cell(fields, destinationIdx);
				boolean originConcrete = isConcrete(originCell);
				boolean destinationConcrete = isConcrete(destinationCell);

				if (originConcrete)
				{
					int packedOrigin = packConcrete(originCell);

					if (destinationConcrete && packConcrete(destinationCell) == packedOrigin)
					{
						// Identical origin->destination rows are skipped by the
						// loader on purpose; not a silent drop.
						continue;
					}

					Set<Transport> produced = transports.get(packedOrigin);
					if (produced == null || produced.isEmpty())
					{
						failures.add(String.format(
							"%s:%d: concrete origin '%s' produced no transports",
							path, lineNo, originCell));
						continue;
					}

					if (destinationConcrete)
					{
						int packedDestination = packConcrete(destinationCell);
						boolean found = false;
						for (Transport transport : produced)
						{
							if (transport.getDestination() == packedDestination)
							{
								found = true;
								break;
							}
						}
						if (!found)
						{
							failures.add(String.format(
								"%s:%d: no transport from '%s' to concrete destination '%s'",
								path, lineNo, originCell, destinationCell));
						}
					}
				}
				else if (destinationConcrete)
				{
					// Destination-anchored rows (files without an Origin
					// column load under the undefined-origin key) and
					// permutation-destination rows (blank Origin cell) must
					// still surface as a produced transport destination.
					int packedDestination = packConcrete(destinationCell);
					if (!mapContainsDestination(transports, packedDestination))
					{
						failures.add(String.format(
							"%s:%d: concrete destination '%s' produced no transport",
							path, lineNo, destinationCell));
					}
				}
			}
		}

		report(failures, "testConcreteRowsProduceTransports");
	}

	/**
	 * Header coverage: every header cell of every file must be one of the
	 * canonical {@link TransportRecord.Fields} names. The parser maps columns
	 * by exact header text, so a misspelled or padded header silently drops
	 * the entire column.
	 */
	@Test
	public void testHeadersAreCanonical() throws IOException
	{
		List<String> failures = new ArrayList<>();

		for (String path : TRANSPORT_RESOURCES)
		{
			RawFile raw = scanRaw(readResource(path));
			for (String header : raw.headers)
			{
				if (!CANONICAL_FIELDS.contains(header))
				{
					failures.add(String.format(
						"%s: header cell '%s' is not a canonical TransportRecord.Fields column",
						path, header));
				}
			}
		}

		report(failures, "testHeadersAreCanonical");
	}

	/**
	 * Coordinate shape: every non-empty Origin/Destination cell must be a
	 * three-part numeric {@code x y z} triple. Blank cells are permutation
	 * markers and are skipped; any other content silently degrades to a
	 * permutation inside {@code WorldPointParser}.
	 */
	@Test
	public void testConcreteCoordinatesAreThreePartNumeric() throws IOException
	{
		List<String> failures = new ArrayList<>();

		for (String path : TRANSPORT_RESOURCES)
		{
			RawFile raw = scanRaw(readResource(path));
			int originIdx = columnIndex(raw.headers, TransportRecord.Fields.ORIGIN);
			int destinationIdx = columnIndex(raw.headers, TransportRecord.Fields.DESTINATION);

			for (int i = 0; i < raw.dataFields.size(); i++)
			{
				String[] fields = raw.dataFields.get(i);
				int lineNo = raw.lineNumbers.get(i);
				String originCell = cell(fields, originIdx);
				String destinationCell = cell(fields, destinationIdx);

				if (!originCell.isEmpty() && !isConcrete(originCell))
				{
					failures.add(String.format(
						"%s:%d: Origin cell '%s' is not a three-part numeric coordinate",
						path, lineNo, originCell));
				}
				if (!destinationCell.isEmpty() && !isConcrete(destinationCell))
				{
					failures.add(String.format(
						"%s:%d: Destination cell '%s' is not a three-part numeric coordinate",
						path, lineNo, destinationCell));
				}
			}
		}

		report(failures, "testConcreteCoordinatesAreThreePartNumeric");
	}

	/**
	 * Field-parser fidelity: every Varbits/VarPlayers clause, Items
	 * expression, Skills token, and Quests token in every committed row must
	 * be fully consumed by the real field parsers. These parsers
	 * log-and-continue on bad tokens, so a dropped clause is otherwise
	 * invisible: the requirement silently weakens.
	 */
	@Test
	public void testRequirementFieldsFullyParse() throws IOException
	{
		List<String> failures = new ArrayList<>();

		for (String path : TRANSPORT_RESOURCES)
		{
			String contents = readResource(path);
			RawFile raw = scanRaw(contents);
			List<TransportRecord> records = new TsvParser().parse(contents);

			for (int i = 0; i < records.size() && i < raw.lineNumbers.size(); i++)
			{
				TransportRecord record = records.get(i);
				int lineNo = raw.lineNumbers.get(i);

				if (record.has(TransportRecord.Fields.VARBITS))
				{
					for (String clause : record.getVarbits().split(";"))
					{
						if (clause.isEmpty())
						{
							continue;
						}
						Set<VarRequirement> parsed = VarRequirementParser.forVarbits().parse(clause);
						if (parsed.size() != 1)
						{
							failures.add(String.format(
								"%s:%d: Varbits clause '%s' did not parse to exactly one requirement",
								path, lineNo, clause));
						}
					}
				}

				if (record.has(TransportRecord.Fields.VAR_PLAYERS))
				{
					for (String clause : record.getVarPlayers().split(";"))
					{
						if (clause.isEmpty())
						{
							continue;
						}
						Set<VarRequirement> parsed = VarRequirementParser.forVarPlayers().parse(clause);
						if (parsed.size() != 1)
						{
							failures.add(String.format(
								"%s:%d: VarPlayers clause '%s' did not parse to exactly one requirement",
								path, lineNo, clause));
						}
					}
				}

				if (record.has(TransportRecord.Fields.ITEMS)
					&& new ItemRequirementParser().parse(record.getItems()) == null)
				{
					failures.add(String.format(
						"%s:%d: Items cell '%s' failed to parse",
						path, lineNo, record.getItems()));
				}

				if (record.has(TransportRecord.Fields.SKILLS))
				{
					for (String token : record.getSkills().split(";"))
					{
						if (token.isEmpty())
						{
							continue;
						}
						int[] levels = new SkillRequirementParser().parse(token);
						int set = 0;
						for (int level : levels)
						{
							if (level != 0)
							{
								set++;
							}
						}
						if (set != 1)
						{
							failures.add(String.format(
								"%s:%d: Skills token '%s' did not set exactly one requirement slot",
								path, lineNo, token));
						}
					}
				}

				if (record.has(TransportRecord.Fields.QUESTS))
				{
					for (String token : record.getQuests().split(";"))
					{
						if (token.isEmpty())
						{
							continue;
						}
						if (new QuestParser().parse(token).size() != 1)
						{
							failures.add(String.format(
								"%s:%d: Quests token '%s' matched no Quest enum entry",
								path, lineNo, token));
						}
					}
				}
			}
		}

		report(failures, "testRequirementFieldsFullyParse");
	}
}
