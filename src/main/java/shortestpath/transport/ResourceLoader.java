package shortestpath.transport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import shortestpath.Util;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueRegion;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.requirement.model.SkillLevels;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.transport.parser.FieldParser;
import shortestpath.transport.parser.QuestParser;
import shortestpath.transport.parser.SkillRequirementParser;
import shortestpath.transport.parser.TransportRecord;
import shortestpath.transport.parser.TsvParser;
import shortestpath.transport.parser.VarRequirementParser;

/**
 * The single typed resource-loading path for the plugin's bundled data.
 * <p>
 * Every classpath resource the plugin reads is anchored on this class and
 * exposed through one typed static entry point per resource. Each entry point
 * owns its resource's domain mapping <em>and its failure policy</em> — the
 * policies are not uniform: transports fail fast (NPE on a missing resource,
 * {@code RuntimeException} on {@link IOException}), league regions degrade to
 * an empty map with a warning, and the collision map hands the caller a raw
 * stream so the zip walk keeps its own failure posture. These postures are
 * behaviour; they are preserved verbatim from the incumbent readers.
 * <p>
 * The class is a loader, not a universal TSV iterator — per-resource row
 * idioms that diverge from {@link TsvParser} stay as private methods here
 * rather than being normalized.
 */
@Slf4j
public final class ResourceLoader
{
	private static final TsvParser tsvParser = new TsvParser();
	private static final FieldParser<SkillLevels> SKILL_PARSER = new SkillRequirementParser();
	private static final FieldParser<Set<Quest>> QUEST_PARSER = new QuestParser();
	private static final FieldParser<Set<VarRequirement>> VARBIT_PARSER = VarRequirementParser.forVarbits();
	private static final FieldParser<Set<VarRequirement>> VARPLAYER_PARSER = VarRequirementParser.forVarPlayers();
	private static final String DELIM_COLUMN = "\t";
	private static final String PREFIX_COMMENT = "#";
	private static final String FILE_EXTENSION = ".";
	private static final String DELIM_PATH = "/";
	private static final String DELIM = " ";
	private static final String LEAGUE_REGIONS_PATH = "/leagues/regions.tsv";
	private static final String FAMILY_RESOURCE = "/transports/teleport_restrictions.tsv";
	private static final String TELEPORTATION_ITEMS_RESOURCE = "/transports/teleportation_items.tsv";

	private ResourceLoader()
	{
	}

	private static void addTransports(
		Map<Integer, Set<Transport>> transports, String path, TransportType transportType,
		int radiusThreshold)
	{
		try
		{
			String s = new String(
				Util.readAllBytes(Objects.requireNonNull(ResourceLoader.class.getResourceAsStream(path))),
				StandardCharsets.UTF_8);
			addTransportsFromContents(transports, s, transportType, radiusThreshold);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static void addTransportsFromContents(
		Map<Integer, Set<Transport>> transports,
		String contents,
		TransportType transportType,
		int radiusThreshold)
	{
		List<TransportRecord> records = tsvParser.parse(contents);

		Set<Transport> newTransports = new LinkedHashSet<>();
		for (TransportRecord record : records)
		{
			Transport transport = new Transport(record, transportType);
			newTransports.add(transport);
		}

		/*
		 * A transport with origin A and destination B is one-way and must
		 * be duplicated as origin B and destination A to become two-way.
		 * Example: key-locked doors
		 *
		 * A transport with origin A and a missing destination is one-way,
		 * but can go from origin A to all destinations with a missing origin.
		 * Example: fairy ring AIQ -> <blank>
		 *
		 * A transport with a missing origin and destination B is one-way,
		 * but can go from all origins with a missing destination to destination B.
		 * Example: fairy ring <blank> -> AIQ
		 *
		 * Identical transports from origin A to destination A are skipped, and
		 * non-identical transports from origin A to destination A can be skipped
		 * by specifying a radius threshold to ignore almost identical coordinates.
		 * Example: fairy ring AIQ -> AIQ
		 */
		Set<Transport> transportOrigins = new LinkedHashSet<>();
		Set<Transport> transportDestinations = new LinkedHashSet<>();
		for (Transport transport : newTransports)
		{
			int origin = transport.getOrigin();
			int destination = transport.getDestination();
			// Logic to determine ordinary transport vs teleport vs permutation (e.g. fairy
			// ring)
			if ((origin == Transport.UNDEFINED_ORIGIN && destination == Transport.UNDEFINED_DESTINATION)
				|| (origin == Transport.LOCATION_PERMUTATION && destination == Transport.LOCATION_PERMUTATION))
			{
				continue;
			}
			else if (origin != Transport.LOCATION_PERMUTATION && origin != Transport.UNDEFINED_ORIGIN
				&& destination == Transport.LOCATION_PERMUTATION)
			{
				transportOrigins.add(transport);
			}
			else if (origin == Transport.LOCATION_PERMUTATION && destination != Transport.UNDEFINED_DESTINATION)
			{
				transportDestinations.add(transport);
			}
			if (origin != Transport.LOCATION_PERMUTATION
				&& destination != Transport.UNDEFINED_DESTINATION && destination != Transport.LOCATION_PERMUTATION
				&& (origin == Transport.UNDEFINED_ORIGIN || origin != destination))
			{
				transports.computeIfAbsent(origin, k -> new LinkedHashSet<>()).add(transport);
			}
		}
		for (Transport origin : transportOrigins)
		{
			for (Transport destination : transportDestinations)
			{
				// The radius threshold prevents transport permutations from including (almost)
				// same origin and destination
				if (WorldPointUtil.distanceBetween2D(origin.getOrigin(), destination.getDestination()) > radiusThreshold)
				{
					Transport combined = new Transport(origin, destination);
					transports
						.computeIfAbsent(origin.getOrigin(), k -> new LinkedHashSet<>())
						.add(combined);
				}
			}
		}
	}

	/**
	 * Loads every bundled transport TSV (one per {@link TransportType} with a
	 * resource path) and returns the origin-keyed transport map with
	 * requirement interning applied. A missing resource throws
	 * {@link NullPointerException}; an {@link IOException} is wrapped in
	 * {@link RuntimeException}.
	 */
	public static HashMap<Integer, Set<Transport>> transports()
	{
		HashMap<Integer, Set<Transport>> transports = new HashMap<>();

		for (TransportType type : TransportType.values())
		{
			if (type.hasResourcePath())
			{
				addTransports(transports, type.getResourcePath(), type,
					type.hasRadiusThreshold() ? type.getRadiusThreshold() : 0);
			}
		}

		internRequirements(transports);

		return transports;
	}

	/**
	 * Loads a predefined subset of destination categories from bundled TSV resources.
	 *
	 * @return a map from destination category key to a set of packed world point integers.
	 */
	public static Map<String, Set<Integer>> destinations()
	{
		Map<String, Set<Integer>> destinations = new HashMap<>(10);
		addDestinations(destinations, "/destinations/game_features/altar.tsv");
		addDestinations(destinations, "/destinations/game_features/bank.tsv");
		addDestinations(destinations, "/destinations/training/anvil.tsv");
		addDestinations(destinations, "/destinations/shopping/apothecary.tsv");
		return destinations;
	}

	/**
	 * Parses a TSV resource of destination coordinates and merges them into the provided map.
	 * The key in the destination map is derived from the directory component immediately preceding the file
	 * name (e.g., {@code /destinations/game_features/bank.tsv -> bank}).
	 *
	 * @param destinations accumulator map of category key to a set of packed world point integers.
	 * @param path         classpath resource path to a TSV file beginning with a header line.
	 * @throws RuntimeException wrapping {@link IOException} if the resource cannot be read.
	 */
	private static void addDestinations(Map<String, Set<Integer>> destinations, String path)
	{
		try
		{
			String s = new String(Util.readAllBytes(Objects.requireNonNull(ResourceLoader.class.getResourceAsStream(path))),
				StandardCharsets.UTF_8);
			parseDestinations(destinations, path, s);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Content seam for the destinations row idiom: header-aware with
	 * duplicate-header accumulation and drop-arity field splitting, kept
	 * verbatim from the incumbent reader. Package-private for tests.
	 */
	static void parseDestinations(Map<String, Set<Integer>> destinations, String path, String contents)
	{
		Scanner scanner = new Scanner(contents);

		// Header line is the first line in the file and will start with either '#' or
		// '# '
		String[] headers = parse_header_line(scanner);

		String[] parts = path.replace(FILE_EXTENSION, DELIM_PATH).split(DELIM_PATH);
		String entry = parts[parts.length - 2];

		while (scanner.hasNextLine())
		{
			String line = scanner.nextLine();

			if (line.startsWith(PREFIX_COMMENT) || line.isBlank())
			{
				continue;
			}

			String[] fields = line.split(DELIM_COLUMN);
			Map<String, List<String>> fieldMap = new HashMap<>();
			for (int i = 0; i < headers.length; i++)
			{
				if (i < fields.length)
				{
					List<String> values = fieldMap.getOrDefault(headers[i], new ArrayList<>());
					values.add(fields[i]);
					fieldMap.put(headers[i], values);
				}
			}
			for (String field : fieldMap.keySet())
			{
				if ("Destination".equals(field))
				{
					for (String value : fieldMap.get(field))
					{
						try
						{
							String[] destinationArray = value.split(DELIM);
							if (destinationArray.length == 3)
							{
								Set<Integer> entryDestinations = destinations.getOrDefault(entry, new HashSet<>());
								entryDestinations.add(WorldPointUtil.packWorldPoint(
									Integer.parseInt(destinationArray[0]),
									Integer.parseInt(destinationArray[1]),
									Integer.parseInt(destinationArray[2])));
								destinations.put(entry, entryDestinations);
							}
						}
						catch (NumberFormatException e)
						{
							log.error("Invalid destination coordinate", e);
						}
					}
				}
			}
		}
		scanner.close();
	}

	private static String[] parse_header_line(Scanner scanner)
	{
		String headerLine = scanner.nextLine();
		headerLine = headerLine.startsWith(PREFIX_COMMENT + " ")
			? headerLine.replace(PREFIX_COMMENT + " ", PREFIX_COMMENT)
			: headerLine;
		headerLine = headerLine.startsWith(PREFIX_COMMENT) ? headerLine.replace(PREFIX_COMMENT, "") : headerLine;
		return headerLine.split(DELIM_COLUMN);
	}

	/**
	 * Per-packed-point requirements for bank tiles, parsed from {@code bank.tsv} columns
	 * Skills, Quests, Varbits, and VarPlayers (same TSV format as transports).
	 * Each row is one way to use the bank, so a tile listed on several rows (a skill level
	 * or a diary, say) is usable when any one of them is met.
	 * Tiles with no requirement rows behave as {@link DestinationRequirements#EMPTY}.
	 */
	public static Map<Integer, List<DestinationRequirements>> bankRequirements()
	{
		final String path = "/destinations/game_features/bank.tsv";
		try
		{
			String s = new String(Util.readAllBytes(Objects.requireNonNull(ResourceLoader.class.getResourceAsStream(path))), StandardCharsets.UTF_8);
			return parseBankRequirements(s);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Content seam for the bank-requirements row idiom: keep-arity field
	 * splitting with trimmed headers, kept verbatim from the incumbent
	 * reader. Package-private for tests.
	 */
	static Map<Integer, List<DestinationRequirements>> parseBankRequirements(String contents)
	{
		Map<Integer, List<DestinationRequirements>> requirements = new HashMap<>();
		final String DELIM_COLUMN = "\t";
		final String PREFIX_COMMENT = "#";
		final String DELIM = " ";

		try (Scanner scanner = new Scanner(contents))
		{
			String[] headers = parse_header_line(scanner);
			for (int i = 0; i < headers.length; i++)
			{
				headers[i] = headers[i].trim();
			}

			int destCol = indexOf(headers, "Destination");
			int skillsCol = indexOf(headers, "Skills");
			int questsCol = indexOf(headers, "Quests");
			int varbitsCol = indexOf(headers, "Varbits");
			int varPlayersCol = indexOf(headers, "VarPlayers");
			if (destCol < 0)
			{
				return requirements;
			}

			while (scanner.hasNextLine())
			{
				String line = scanner.nextLine();
				if (line.startsWith(PREFIX_COMMENT) || line.isBlank())
				{
					continue;
				}
				String[] fields = line.split(DELIM_COLUMN, -1);
				if (fields.length <= destCol)
				{
					continue;
				}
				String destField = fields[destCol].trim();
				String[] destinationArray = destField.split(DELIM);
				if (destinationArray.length != 3)
				{
					continue;
				}
				int packed;
				try
				{
					packed = WorldPointUtil.packWorldPoint(
						Integer.parseInt(destinationArray[0]),
						Integer.parseInt(destinationArray[1]),
						Integer.parseInt(destinationArray[2]));
				}
				catch (NumberFormatException e)
				{
					log.error("Invalid destination coordinate in bank.tsv", e);
					continue;
				}

				String skillsStr = fieldAt(fields, skillsCol);
				String questsStr = fieldAt(fields, questsCol);
				String varbitsStr = fieldAt(fields, varbitsCol);
				String varPlayersStr = fieldAt(fields, varPlayersCol);

				SkillLevels skillLevels = skillsCol >= 0 ? SKILL_PARSER.parse(blankToNull(skillsStr)) : SkillLevels.NONE;
				Set<Quest> quests = questsCol >= 0 ? QUEST_PARSER.parse(blankToNull(questsStr)) : Set.of();
				Set<VarRequirement> varbits = varbitsCol >= 0 ? VARBIT_PARSER.parse(blankToNull(varbitsStr)) : Set.of();
				Set<VarRequirement> varPlayers = varPlayersCol >= 0 ? VARPLAYER_PARSER.parse(blankToNull(varPlayersStr)) : Set.of();

				DestinationRequirements rowReq = new DestinationRequirements(skillLevels, quests, varbits, varPlayers);
				requirements.computeIfAbsent(packed, k -> new ArrayList<>(1)).add(rowReq);
			}
		}
		return requirements;
	}

	private static int indexOf(String[] headers, String name)
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

	private static String fieldAt(String[] fields, int col)
	{
		if (col < 0 || col >= fields.length)
		{
			return "";
		}
		return fields[col];
	}

	private static String blankToNull(String s)
	{
		return s == null || s.isBlank() ? null : s;
	}

	/**
	 * Loads the league region mapping from {@code /leagues/regions.tsv}. A
	 * missing resource or a read failure degrades to an empty map (every tile
	 * resolves to {@link LeagueRegion#NEUTRAL} downstream) rather than
	 * crashing — the incumbent posture.
	 */
	public static Map<Integer, LeagueRegion> leagueRegions()
	{
		try (InputStream in = ResourceLoader.class.getResourceAsStream(LEAGUE_REGIONS_PATH))
		{
			if (in == null)
			{
				log.warn("League regions resource not found at {}; defaulting all tiles to NEUTRAL", LEAGUE_REGIONS_PATH);
				return new HashMap<>();
			}
			String body = new String(Util.readAllBytes(Objects.requireNonNull(in)), StandardCharsets.UTF_8);
			return parseLeagueRegions(body);
		}
		catch (IOException e)
		{
			log.error("Failed to load league regions from {}", LEAGUE_REGIONS_PATH, e);
			return new HashMap<>();
		}
	}

	/**
	 * Content seam for the league-regions row grammar: two-column
	 * whitespace-split rows keyed by region id, non-header and
	 * non-numeric/unknown rows warn-and-skip. Kept verbatim from the
	 * incumbent reader.
	 */
	public static Map<Integer, LeagueRegion> parseLeagueRegions(String tsv)
	{
		Map<Integer, LeagueRegion> result = new HashMap<>();
		if (tsv == null || tsv.isEmpty())
		{
			return result;
		}
		int lineNumber = 0;
		for (String rawLine : tsv.split("\\R"))
		{
			lineNumber++;
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] parts = line.split("\\s+", 2);
			if (parts.length != 2)
			{
				log.warn("Skipping malformed league regions row {}: '{}'", lineNumber, rawLine);
				continue;
			}
			final int regionId;
			try
			{
				regionId = Integer.parseInt(parts[0]);
			}
			catch (NumberFormatException e)
			{
				log.warn("Skipping league regions row {} with non-numeric region id '{}'", lineNumber, parts[0]);
				continue;
			}
			final LeagueRegion region;
			try
			{
				region = LeagueRegion.valueOf(parts[1]);
			}
			catch (IllegalArgumentException e)
			{
				log.warn("Skipping league regions row {} with unknown region '{}'", lineNumber, parts[1]);
				continue;
			}
			result.put(regionId, region);
		}
		return result;
	}

	/**
	 * A raw parsed row of the bundled teleport-restriction family map. The
	 * loader owns the resource read and row idiom; the caller maps rows onto
	 * its own model ({@link shortestpath.requirement.TeleportRestriction.Family}).
	 */
	public static final class RestrictionFamilyRow
	{
		private final String name;
		private final int displayItemId;
		private final Set<Integer> memberItemIds;
		private final Map<Integer, String> memberLabels;

		public RestrictionFamilyRow(String name, int displayItemId, Set<Integer> memberItemIds,
			Map<Integer, String> memberLabels)
		{
			this.name = name;
			this.displayItemId = displayItemId;
			this.memberItemIds = memberItemIds;
			this.memberLabels = memberLabels;
		}

		public String name()
		{
			return name;
		}

		public int displayItemId()
		{
			return displayItemId;
		}

		public Set<Integer> memberItemIds()
		{
			return memberItemIds;
		}

		public Map<Integer, String> memberLabels()
		{
			return memberLabels;
		}
	}

	/**
	 * Loads the bundled teleport-restriction family map from
	 * {@code /transports/teleport_restrictions.tsv}. A missing resource
	 * degrades to an empty list — panel construction must not crash on
	 * packaging gaps; an {@link IOException} is wrapped in
	 * {@link RuntimeException}.
	 */
	public static List<RestrictionFamilyRow> families()
	{
		final String contents;
		try
		{
			InputStream stream = ResourceLoader.class.getResourceAsStream(FAMILY_RESOURCE);
			if (stream == null)
			{
				log.warn("Teleport restriction family resource {} is missing — degrading to no rows",
					FAMILY_RESOURCE);
				return Collections.emptyList();
			}
			contents = new String(Util.readAllBytes(stream), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		return parseFamilies(contents);
	}

	/**
	 * Loads the raw records of {@code /transports/teleportation_items.tsv}
	 * through the shared {@link TsvParser} — the same resource
	 * {@link TransportType#TELEPORTATION_ITEM} loads its transports from.
	 * Records stay raw field maps; domain mapping is the caller's job (same
	 * division as {@link #families()}/{@link #parseFamilies}). A missing
	 * resource degrades to an empty list with a warning; an
	 * {@link IOException} is wrapped in {@link RuntimeException}.
	 */
	public static List<TransportRecord> teleportationItemRecords()
	{
		final String contents;
		try
		{
			InputStream stream =
				ResourceLoader.class.getResourceAsStream(TELEPORTATION_ITEMS_RESOURCE);
			if (stream == null)
			{
				log.warn("Teleportation items resource {} is missing — degrading to no records",
					TELEPORTATION_ITEMS_RESOURCE);
				return Collections.emptyList();
			}
			contents = new String(Util.readAllBytes(stream), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		return tsvParser.parse(contents);
	}

	/**
	 * Content seam for the teleport-restriction row idiom: positional
	 * keep-arity field splitting with '#' comment rows skipped, malformed
	 * rows warn-and-skip, and an absent or malformed memberLabels column
	 * degrades to an empty map for that family. Kept verbatim from the
	 * incumbent reader. Package-private for tests.
	 */
	static List<RestrictionFamilyRow> parseFamilies(String contents)
	{
		List<RestrictionFamilyRow> families = new ArrayList<>();
		for (String line : contents.split("\\R"))
		{
			if (line.startsWith(PREFIX_COMMENT) || line.isBlank())
			{
				continue;
			}

			String[] fields = line.split(DELIM_COLUMN, -1);
			if (fields.length < 3)
			{
				log.warn("Skipping malformed teleport_restrictions row ({} columns): {}", fields.length, line);
				continue;
			}

			String name = fields[0].trim();
			if (name.isEmpty())
			{
				log.warn("Skipping teleport_restrictions row with empty family name");
				continue;
			}

			try
			{
				int displayItemId = Integer.parseInt(fields[1].trim());
				Set<Integer> memberItemIds = parseIdSet(fields[2]);
				Map<Integer, String> memberLabels =
					fields.length > 3 ? parseMemberLabels(fields[3]) : Collections.emptyMap();
				families.add(new RestrictionFamilyRow(name, displayItemId,
					Collections.unmodifiableSet(memberItemIds),
					Collections.unmodifiableMap(memberLabels)));
			}
			catch (NumberFormatException e)
			{
				log.warn("Skipping malformed teleport_restrictions row: {}", line);
			}
		}

		return Collections.unmodifiableList(families);
	}

	private static Set<Integer> parseIdSet(String column)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (String token : column.split(","))
		{
			String trimmed = token.trim();
			if (!trimmed.isEmpty())
			{
				ids.add(Integer.parseInt(trimmed));
			}
		}
		return ids;
	}

	private static Map<Integer, String> parseMemberLabels(String column)
	{
		Map<Integer, String> labels = new LinkedHashMap<>();
		for (String entry : column.split(","))
		{
			String trimmed = entry.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}

			int eq = trimmed.indexOf('=');
			if (eq < 0)
			{
				log.warn("Skipping malformed memberLabels entry: {}", trimmed);
				continue;
			}

			try
			{
				labels.put(Integer.parseInt(trimmed.substring(0, eq)), trimmed.substring(eq + 1));
			}
			catch (NumberFormatException e)
			{
				log.warn("Skipping malformed memberLabels entry: {}", trimmed);
			}
		}
		return labels;
	}

	/**
	 * Returns a caller-owned stream over {@code /collision-map.zip}. A missing
	 * resource throws {@link NullPointerException}; read failures surface to
	 * the caller (the zip walk wraps them in {@link java.io.UncheckedIOException}).
	 * The caller is responsible for closing the stream.
	 */
	public static InputStream collisionMap()
	{
		return Objects.requireNonNull(ResourceLoader.class.getResourceAsStream("/collision-map.zip"));
	}

	/**
	 * Deduplicates the requirement objects shared across the loaded transports, so identical
	 * {@code TransportItems}/{@code VarRequirement} requirements point at one shared instance
	 * instead of a per-transport copy (issue #491). The interning pools are local and discarded
	 * once loading finishes.
	 */
	private static void internRequirements(Map<Integer, Set<Transport>> transports)
	{
		LoadInterner interner = new LoadInterner();
		Set<Transport> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Set<Transport> set : transports.values())
		{
			for (Transport transport : set)
			{
				if (visited.add(transport))
				{
					transport.internRequirements(interner);
				}
			}
		}
	}
}
