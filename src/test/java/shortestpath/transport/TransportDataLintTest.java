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
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.Util;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.requirement.TeleportRestriction;
import shortestpath.transport.parser.TransportRecord;
import shortestpath.transport.parser.TsvParser;

/**
 * Unit tests for validating transport data integrity.
 * These tests validate the consistency and correctness of transport data files.
 */
public class TransportDataLintTest
{
	@Test
	public void corpTeleportsAndWildernessEntranceUseTheSameCave()
	{
		int landing = WorldPointUtil.packWorldPoint(2964, 4382, 2);
		int wilderness = WorldPointUtil.packWorldPoint(3206, 3681, 0);
		Map<Integer, Set<Transport>> transports = ResourceLoader.transports();
		int teleports = 0;
		for (Set<Transport> set : transports.values())
		{
			for (Transport transport : set)
			{
				if (transport.getDisplayInfo() != null && transport.getDisplayInfo().endsWith("Corporeal Beast"))
				{
					Assert.assertEquals(transport.getDisplayInfo(), landing, transport.getDestination());
					teleports++;
				}
			}
		}
		Assert.assertEquals(3, teleports);
		Assert.assertTrue(transports.getOrDefault(landing, Set.of()).stream()
			.anyMatch(transport -> transport.getDestination() == wilderness));
		Assert.assertTrue(transports.getOrDefault(wilderness, Set.of()).stream()
			.anyMatch(transport -> transport.getDestination() == landing));
	}

	/**
	 * Destinations already known to be dead ends, formatted as the failure message lists them. Fix the
	 * data and remove the entry; the test fails if an entry here no longer is a dead end.
	 */
	private static final Set<String> KNOWN_DEAD_ENDS = new HashSet<>(Arrays.asList(
		// Seasonal transports
		"1253 3750 0  SEASONAL_TRANSPORTS  Fairy Mushroom: Kourend - Farming Guild (planted)",
		"1304 9398 0  SEASONAL_TRANSPORTS  Map of Alacrity: Varlamore - Tonali cavern: Log balance",
		"1309 3807 0  SEASONAL_TRANSPORTS  Evil Eye: Kourend - Alchemical Hydra",
		"1311 9507 0  SEASONAL_TRANSPORTS  Map of Alacrity: Varlamore - Mokhaiotl ruins: Pillar",
		"1315 9378 0  SEASONAL_TRANSPORTS  Map of Alacrity: Varlamore - Tonali cavern: Stepping stone",
		"1331 10227 0  SEASONAL_TRANSPORTS  Map of Alacrity: Kourend - Karuulm slayer dungeon: Hydra pipe",
		"1341 2917 0  SEASONAL_TRANSPORTS  Map of Alacrity: Varlamore - Aldarin: Rocks",
		"1479 3851 0  SEASONAL_TRANSPORTS  Banker's Briefcase: Kourend - Blast mine (south)",
		"1611 10066 0  SEASONAL_TRANSPORTS  Map of Alacrity: Kourend - Catacombs: Dragons stepping stones",
		"1693 3542 0  SEASONAL_TRANSPORTS  Fairy Mushroom: Kourend - Hosidius (planted)",
		"2081 6372 0  SEASONAL_TRANSPORTS  Evil Eye: Desert - Leviathan",
		"2339 3111 0  SEASONAL_TRANSPORTS  Fairy Mushroom: Kandarin - Poison Waste",
		"2613 3858 0  SEASONAL_TRANSPORTS  Fairy Mushroom: Fremennik - Etceteria (planted)",
		"2742 3830 0  SEASONAL_TRANSPORTS  Map of Alacrity: Fremennik - Rellekka: Hunter area handholds",
		"2766 9342 0  SEASONAL_TRANSPORTS  Map of Alacrity: Karamja - Shaman caves: Crevice",
		"2802 3203 0  SEASONAL_TRANSPORTS  Fairy Mushroom: Karamja - Brimhaven (planted)",
		"2913 5300 0  SEASONAL_TRANSPORTS  Map of Alacrity: Asgarnia - God wars dungeon: Saradomin rope",
		"3039 4969 0  SEASONAL_TRANSPORTS  Banker's Briefcase: Asgarnia - Rogues' Den",
		"3046 10332 0  SEASONAL_TRANSPORTS  Map of Alacrity: Wilderness - Deep wilderness dungeon: Crevice",
		"3060 3258 0  SEASONAL_TRANSPORTS  Fairy Mushroom: Asgarnia - Port Sarim (planted)",
		"3066 10150 0  SEASONAL_TRANSPORTS  Map of Alacrity: Wilderness - Wilderness god wars dungeon: Entry crevice",
		"3107 4791 0  SEASONAL_TRANSPORTS  Evil Eye: Varlamore - Abyssal Sire",
		"3108 10351 0  SEASONAL_TRANSPORTS  Banker's Briefcase: Wilderness - Mage Arena",
		"3115 3675 0  SEASONAL_TRANSPORTS  Evil Eye: Wilderness - Artio",
		"3200 10136 0  SEASONAL_TRANSPORTS  Map of Alacrity: Wilderness - Revenant cave: Medium pillar south",
		"3202 10196 0  SEASONAL_TRANSPORTS  Map of Alacrity: Wilderness - Revenant cave: Medium pillar north 2",
		"3219 12441 0  SEASONAL_TRANSPORTS  Map of Alacrity: Tirannwn - Iorwerth dungeon: Gap north",
		"3237 12420 0  SEASONAL_TRANSPORTS  Map of Alacrity: Tirannwn - Iorwerth dungeon: Gap south",
		"3241 10145 0  SEASONAL_TRANSPORTS  Map of Alacrity: Wilderness - Revenant cave: Hard pillar",
		"3291 3849 0  SEASONAL_TRANSPORTS  Evil Eye: Wilderness - Callisto",
		"3367 3318 1  SEASONAL_TRANSPORTS  Banker's Briefcase: Desert - Mage Training Arena",
		"3384 10142 0  SEASONAL_TRANSPORTS  Map of Alacrity: Wilderness - Wilderness slayer cave: Jellies",
		"3384 10142 0  SEASONAL_TRANSPORTS  Map of Alacrity: Wilderness - Wilderness slayer cave: Lesser demons",
		"3557 9798 0  SEASONAL_TRANSPORTS  Map of Alacrity: Morytania - Meyerditch lab: Cave",
		"3646 3219 0  SEASONAL_TRANSPORTS  Evil Eye: Morytania - Theatre of Blood"
	));


	@Test
	public void testNoDuplicateOriginDestinationPairs()
	{
		// Load all transport data from resources
		HashMap<Integer, Set<Transport>> transports = ResourceLoader.transports();

		// Track all origin-destination-type combinations to check for exact duplicates
		Set<String> transportSignatures = new HashSet<>();
		Map<String, String> duplicateInfo = new HashMap<>();
		List<String> duplicatesFound = new ArrayList<>();

		for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet())
		{
			int origin = entry.getKey();
			Set<Transport> transportSet = entry.getValue();

			for (Transport transport : transportSet)
			{
				int destination = transport.getDestination();

				// Create a comprehensive signature that includes all transport properties
				// This helps distinguish between legitimate different transport variants vs
				// true duplicates
				// ObjectInfo is critical for distinguishing fairy rings with different item
				// requirements
				// (e.g., dramen staff vs lunar staff are represented by different objectIDs in
				// objectInfo)
				// The display info is there of the one edge case in the fairy ring system.
				// There are two options to go to Zanaris, one is with the code `B K S` and the
				// other is with the `zanaris` option.
				// only the display info is different between those two options so we need to
				// include it in the signature.
				String signature = String.format(
					"%s -> %s [%s] '%s' Consumable:%s Items:%s Quests:%s Varbits:%s VarPlayers:%s ObjectInfo:%s",
					WorldPointUtil.unpackWorldX(origin) + " " + WorldPointUtil.unpackWorldY(origin) + " "
						+ WorldPointUtil.unpackWorldPlane(origin),
					WorldPointUtil.unpackWorldX(destination) + " " + WorldPointUtil.unpackWorldY(destination) + " "
						+ WorldPointUtil.unpackWorldPlane(destination),
					transport.getType(),
					transport.getDisplayInfo() != null ? transport.getDisplayInfo() : "",
					transport.isConsumable(),
					transport.getItemRequirements() != null ? transport.getItemRequirements().toString() : "none",
					transport.getQuests().toString(),
					transport.getVarbits().toString(),
					transport.getVarPlayers().toString(),
					transport.getObjectInfo() != null ? transport.getObjectInfo() : "none");

				// Check if this exact transport signature already exists
				if (transportSignatures.contains(signature))
				{
					String existingInfo = duplicateInfo.get(signature);
					String newInfo = String.format("MaxWilderness: %d, Skills: %s",
						transport.getMaxWildernessLevel(),
						Arrays.toString(transport.getSkillLevels().toArray()));

					duplicatesFound.add(String.format("Exact duplicate transport: %s\n" +
							"First occurrence: %s\n" +
							"Duplicate occurrence: %s\n",
						signature, existingInfo, newInfo));
				}
				else
				{
					// Add this signature to our tracking set
					transportSignatures.add(signature);

					// Store information about this transport for error reporting
					String info = String.format("MaxWilderness: %d, Skills: %s",
						transport.getMaxWildernessLevel(),
						Arrays.toString(transport.getSkillLevels().toArray()));
					duplicateInfo.put(signature, info);
				}
			}
		}

		// Report results
		if (!duplicatesFound.isEmpty())
		{
			StringBuilder message = new StringBuilder();
			message.append(String.format("Found %d duplicate transport entries in the data files:\n\n",
				duplicatesFound.size()));
			for (String duplicate : duplicatesFound)
			{
				message.append(duplicate).append("\n");
			}
			Assert.fail(message.toString());
		}

		// If we get here, no exact duplicates were found
		System.out.printf("Successfully validated %d unique transport signatures across all transport data files.\n",
			transportSignatures.size());
	}

	/**
	 * Every transport destination must let the player continue the route. A destination may be a
	 * blocked tile (a fairy ring is), because the pathfinder steps off a blocked tile onto any open
	 * neighbour, but a blocked tile whose neighbours are all blocked too, with no transport starting
	 * there, is a dead end: the route can only leave it by a global teleport. Such a destination is
	 * almost always the object's own footprint instead of the tile the game places the player on.
	 */
	@Test
	public void testEveryTransportDestinationCanBeWalkedAwayFrom()
	{
		CollisionMap map = new CollisionMap(SplitFlagMap.fromResources());
		HashMap<Integer, Set<Transport>> transports = ResourceLoader.transports();
		Map<Integer, Boolean> escapable = new HashMap<>();
		Set<String> stranded = new TreeSet<>();
		for (Set<Transport> set : transports.values())
		{
			for (Transport transport : set)
			{
				int destination = transport.getDestination();
				if (!escapable.computeIfAbsent(destination, tile -> canEscape(map, transports, tile)))
				{
					stranded.add(String.format("%d %d %d  %s  %s",
						WorldPointUtil.unpackWorldX(destination), WorldPointUtil.unpackWorldY(destination),
						WorldPointUtil.unpackWorldPlane(destination), transport.getType(),
						transport.getDisplayInfo() != null ? transport.getDisplayInfo() : ""));
				}
			}
		}

		Set<String> unexpected = new TreeSet<>(stranded);
		unexpected.removeAll(KNOWN_DEAD_ENDS);
		Set<String> fixed = new TreeSet<>(KNOWN_DEAD_ENDS);
		fixed.removeAll(stranded);
		if (!unexpected.isEmpty())
		{
			Assert.fail(String.format("%d transport destinations cannot be walked away from:%n%s",
				unexpected.size(), String.join(System.lineSeparator(), unexpected)));
		}
		if (!fixed.isEmpty())
		{
			Assert.fail(String.format("%d known dead ends are fixed; remove them from KNOWN_DEAD_ENDS:%n%s",
				fixed.size(), String.join(System.lineSeparator(), fixed)));
		}
	}

	/**
	 * A concrete origin must be walkable, interactable from an open cardinal neighbour, or reached
	 * by another transport. Global teleports have no concrete origin and are excluded.
	 */
	@Test
	public void testEveryTransportOriginCanBeApproached()
	{
		CollisionMap map = new CollisionMap(SplitFlagMap.fromResources());
		Map<Integer, Set<Transport>> transports = ResourceLoader.transports();
		Set<Integer> arrivals = new HashSet<>();
		for (Set<Transport> set : transports.values())
		{
			for (Transport transport : set)
			{
				arrivals.add(transport.getDestination());
			}
		}

		Set<String> inaccessible = new TreeSet<>();
		for (Map.Entry<Integer, Set<Transport>> entry : transports.entrySet())
		{
			int origin = entry.getKey();
			if (origin == Transport.UNDEFINED_ORIGIN || canApproach(map, arrivals, origin))
			{
				continue;
			}
			for (Transport transport : entry.getValue())
			{
				inaccessible.add(String.format("%d %d %d  %s  %s",
					WorldPointUtil.unpackWorldX(origin), WorldPointUtil.unpackWorldY(origin),
					WorldPointUtil.unpackWorldPlane(origin), transport.getType(), transport.getObjectInfo()));
			}
		}
		Assert.assertTrue(String.format("%d transport origins cannot be approached:%n%s",
			inaccessible.size(), String.join(System.lineSeparator(), inaccessible)), inaccessible.isEmpty());
	}

	private static boolean canApproach(CollisionMap map, Set<Integer> arrivals, int origin)
	{
		int x = WorldPointUtil.unpackWorldX(origin);
		int y = WorldPointUtil.unpackWorldY(origin);
		int z = WorldPointUtil.unpackWorldPlane(origin);
		// shortcut: assumes incoming transports are reachable; trace chains if isolated cycles appear.
		if (!map.isBlocked(x, y, z) || arrivals.contains(origin))
		{
			return true;
		}
		int[][] cardinals = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
		for (int[] d : cardinals)
		{
			if (!map.isBlocked(x + d[0], y + d[1], z)
				|| arrivals.contains(WorldPointUtil.packWorldPoint(x + d[0], y + d[1], z)))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean canEscape(CollisionMap map, Map<Integer, Set<Transport>> transports, int destination)
	{
		return map.ordinaryWalkingNeighbors(destination).length > 0 || hasOnwardTransport(map, transports, destination);
	}

	/**
	 * Whether a transport (other than a global teleport) starts on the tile, or on a blocked cardinal
	 * neighbour, which the pathfinder also uses (e.g. a fairy ring next to the player).
	 */
	private static boolean hasOnwardTransport(CollisionMap map, Map<Integer, Set<Transport>> transports, int tile)
	{
		if (transports.containsKey(tile))
		{
			return true;
		}
		int x = WorldPointUtil.unpackWorldX(tile);
		int y = WorldPointUtil.unpackWorldY(tile);
		int z = WorldPointUtil.unpackWorldPlane(tile);
		int[][] cardinals = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
		for (int[] d : cardinals)
		{
			if (map.isBlocked(x + d[0], y + d[1], z)
				&& transports.containsKey(WorldPointUtil.packWorldPoint(x + d[0], y + d[1], z)))
			{
				return true;
			}
		}
		return false;
	}
	/**
	 * Validates the generated teleport restriction family map against
	 * {@code teleportation_items.tsv}: every numeric id referenced by an Items
	 * cell belongs to exactly one family, every map member id exists in the
	 * TSV, family names are non-empty and unique, and every member id carries
	 * a non-empty display label.
	 */
	@Test
	public void testTeleportRestrictionFamilyMap() throws IOException
	{
		List<TeleportRestriction.Family> families = TeleportRestriction.loadFamilies();
		Assert.assertFalse("teleport_restrictions.tsv should contain at least one family", families.isEmpty());

		// Collect the raw numeric item ids referenced by the source TSV's Items
		// cells (UNLOCK_* tokens are unlock requirements, not item ids)
		String itemsTsv = new String(
			Util.readAllBytes(Objects.requireNonNull(
				TeleportRestriction.class.getResourceAsStream("/transports/teleportation_items.tsv"))),
			StandardCharsets.UTF_8);
		List<TransportRecord> records = new TsvParser().parse(itemsTsv);

		Set<Integer> tsvItemIds = new HashSet<>();
		for (TransportRecord record : records)
		{
			String items = record.getItems();
			if (items == null || items.isEmpty())
			{
				continue;
			}
			String normalized = items.replace(" ", "").replace("&&", "&").replace("||", "|").toUpperCase();
			for (String andPart : normalized.split("&"))
			{
				for (String orPart : andPart.split("\\|"))
				{
					if (orPart.isEmpty() || orPart.startsWith("UNLOCK_"))
					{
						continue;
					}
					String name = orPart.split("=", 2)[0];
					if (name.chars().allMatch(Character::isDigit))
					{
						tsvItemIds.add(Integer.parseInt(name));
					}
				}
			}
		}
		Assert.assertFalse("teleportation_items.tsv should reference item ids", tsvItemIds.isEmpty());

		// Every TSV id must belong to exactly one family (the member sets form a
		// partition); family names must be non-empty and unique; every member id
		// must carry a non-empty label whose key is a member id
		Set<String> familyNames = new HashSet<>();
		Map<Integer, String> idOwner = new HashMap<>();
		List<String> duplicates = new ArrayList<>();
		for (TeleportRestriction.Family family : families)
		{
			Assert.assertFalse("family name must not be empty", family.name().isBlank());
			Assert.assertTrue("family name is a display label, not an authoring note (<= 80 chars): "
				+ family.name(), family.name().length() <= 80);
			Assert.assertTrue("duplicate family name: " + family.name(), familyNames.add(family.name()));
			Assert.assertFalse("family '" + family.name() + "' must have member ids",
				family.memberItemIds().isEmpty());
			Assert.assertTrue("displayItemId must be a member id in family '" + family.name() + "'",
				family.memberItemIds().contains(family.displayItemId()));

			for (int id : family.memberItemIds())
			{
				Assert.assertTrue("member ids must be positive item ids", id > 0);
				String label = family.memberLabels().get(id);
				Assert.assertNotNull("member id " + id + " in family '" + family.name()
					+ "' has no memberLabels entry", label);
				Assert.assertFalse("member id " + id + " in family '" + family.name()
					+ "' has an empty memberLabels entry", label.isEmpty());
				if (idOwner.put(id, family.name()) != null)
				{
					duplicates.add(id + " in '" + idOwner.get(id) + "' and '" + family.name() + "'");
				}
			}

			for (int labelKey : family.memberLabels().keySet())
			{
				Assert.assertTrue("memberLabels key " + labelKey + " is not a member id of family '"
					+ family.name() + "'", family.memberItemIds().contains(labelKey));
			}
		}
		Assert.assertTrue("item ids must belong to exactly one family: " + duplicates,
			duplicates.isEmpty());

		Set<Integer> mapItemIds = idOwner.keySet();
		Set<Integer> missingFromMap = new HashSet<>(tsvItemIds);
		missingFromMap.removeAll(mapItemIds);
		Assert.assertTrue("Items-cell ids missing from the family map: " + missingFromMap,
			missingFromMap.isEmpty());

		Set<Integer> missingFromTsv = new HashSet<>(mapItemIds);
		missingFromTsv.removeAll(tsvItemIds);
		Assert.assertTrue("family map member ids not found in teleportation_items.tsv: " + missingFromTsv,
			missingFromTsv.isEmpty());

		System.out.printf("Validated %d teleport restriction families covering %d item ids.\n",
			families.size(), mapItemIds.size());
	}

	/**
	 * Asserts every {@code teleportation_items.tsv} row serving a restriction
	 * family (its {@code Items} cell contributes a member item id) carries a
	 * {@code Display info} cell usable as the destination-option checkbox
	 * text: non-blank, and with a non-blank tail when the cell uses the
	 * {@code "<item>: <destination>"} form. Also asserts that within each
	 * family no two distinct destinations share one label — such a pair would
	 * render indistinguishable checkboxes. {@code Ardougne cloaks} must yield
	 * at least two options as the concrete drift tripwire.
	 */
	@Test
	public void testRestrictionServingRowLabels() throws IOException
	{
		List<TeleportRestriction.Family> families = TeleportRestriction.loadFamilies();
		Assert.assertFalse("teleport_restrictions.tsv should contain at least one family",
			families.isEmpty());

		// member id -> owning families, mirroring the production serving rule
		Set<Integer> memberIds = new HashSet<>();
		for (TeleportRestriction.Family family : families)
		{
			memberIds.addAll(family.memberItemIds());
		}

		String itemsTsv = new String(
			Util.readAllBytes(Objects.requireNonNull(
				TeleportRestriction.class.getResourceAsStream("/transports/teleportation_items.tsv"))),
			StandardCharsets.UTF_8);
		List<TransportRecord> records = new TsvParser().parse(itemsTsv);

		List<String> violations = new ArrayList<>();
		int servingRows = 0;
		for (TransportRecord record : records)
		{
			String items = record.getItems();
			if (items == null || items.isEmpty())
			{
				continue;
			}
			boolean serves = false;
			String normalized = items.replace(" ", "").replace("&&", "&").replace("||", "|")
				.toUpperCase();
			for (String andPart : normalized.split("&"))
			{
				for (String orPart : andPart.split("\\|"))
				{
					if (orPart.isEmpty() || orPart.startsWith("UNLOCK_"))
					{
						continue;
					}
					String name = orPart.split("=", 2)[0];
					if (!name.isEmpty() && name.chars().allMatch(Character::isDigit)
						&& memberIds.contains(Integer.parseInt(name)))
					{
						serves = true;
					}
				}
			}
			if (!serves)
			{
				continue;
			}
			servingRows++;
			String displayInfo = record.getDisplayInfo();
			String trimmed = displayInfo == null ? "" : displayInfo.trim();
			if (trimmed.isEmpty())
			{
				violations.add("blank Display info on family-serving row to '"
					+ record.getDestination() + "'");
			}
			else
			{
				int colon = trimmed.indexOf(':');
				if (colon >= 0 && trimmed.substring(colon + 1).trim().isEmpty())
				{
					violations.add("blank label tail after ':' on family-serving row '"
						+ trimmed + "'");
				}
			}
		}
		Assert.assertTrue("expected family-serving rows in teleportation_items.tsv", servingRows > 0);
		Assert.assertTrue("unusable family-serving row labels:\n" + String.join("\n", violations),
			violations.isEmpty());

		// Within a family every distinct destination needs a distinguishing
		// label — same-label-different-destination yields indistinguishable
		// checkboxes
		for (TeleportRestriction.Family family : families)
		{
			Map<Integer, String> options = family.options();
			Assert.assertFalse("family '" + family.name()
				+ "' should expose at least one destination option", options.isEmpty());
			Map<String, List<Integer>> byLabel = new HashMap<>();
			for (Map.Entry<Integer, String> option : options.entrySet())
			{
				byLabel.computeIfAbsent(option.getValue(), k -> new ArrayList<>())
					.add(option.getKey());
			}
			for (Map.Entry<String, List<Integer>> entry : byLabel.entrySet())
			{
				Assert.assertEquals("family '" + family.name() + "' label '" + entry.getKey()
					+ "' must not cover multiple destinations: " + entry.getValue(),
					1, entry.getValue().size());
			}
		}

		// Concrete drift tripwire: the cloaks serve Kandarin Monastery and
		// Ardougne Farm — if derivation or labels regress this fails first
		TeleportRestriction.Family ardougne = families.stream()
			.filter(f -> f.name().equals("Ardougne cloaks"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Ardougne cloaks family missing"));
		Assert.assertTrue("Ardougne cloaks should expose the monastery and farm destinations",
			ardougne.options().size() >= 2);

		System.out.printf("Validated %d family-serving rows; labels distinguish destinations "
			+ "within all %d families.\n", servingRows, families.size());
	}

	/**
	 * The balloon log-basket unlock is destination-keyed relief: the shared
	 * basket storage is invisible to the client, so a trip is satisfied by a
	 * carried log OR the declared unlock. That places hard constraints on where
	 * {@code UNLOCK_BALLOON_LOG_BASKET} may appear in
	 * {@code hot_air_balloons.tsv}:
	 * <ul>
	 * <li>exactly six destination-side rows (blank origin field) carry
	 * {@code <logId>=1|UNLOCK_BALLOON_LOG_BASKET=1} in the Items column;</li>
	 * <li>the token only ever appears as a {@code |}-OR-branch operand — never
	 * {@code &}-ANDed (an ANDed token would gate the route behind the toggle
	 * even for players carrying logs) and never as the sole content of an
	 * Items cell;</li>
	 * <li>every {@code Use Basket} departure row (non-blank origin) keeps an
	 * EMPTY Items cell — departure rows permute to every destination, so an
	 * origin-side requirement would charge the wrong log type.</li>
	 * </ul>
	 * Scope limit: the no-AND/no-pure-cell prohibition is specific to this
	 * token; other TSVs legitimately use ANDed or standalone unlock cells
	 * (e.g. {@code 13393=1&UNLOCK_XERICS_HONOUR=1}).
	 */
	@Test
	public void testBalloonLogBasketUnlockPlacement() throws IOException
	{
		String tsv = new String(
			Util.readAllBytes(Objects.requireNonNull(
				TransportDataLintTest.class.getResourceAsStream("/transports/hot_air_balloons.tsv"))),
			StandardCharsets.UTF_8);

		int unlockRows = 0;
		int departureRows = 0;
		List<String> violations = new ArrayList<>();
		int lineNo = 0;
		for (String line : tsv.split("\n"))
		{
			lineNo++;
			if (line.startsWith("#"))
			{
				continue;
			}
			String[] fields = line.split("\t", -1);
			String origin = fields[0].trim();
			String items = fields.length > 3 ? fields[3].trim() : "";
			boolean lineHasToken = line.contains("UNLOCK_BALLOON_LOG_BASKET");

			if (!origin.isEmpty())
			{
				// Departure row: origin-keyed permutation rows must not carry
				// an Items requirement at all.
				departureRows++;
				if (fields.length <= 2 || !fields[2].contains("Use Basket"))
				{
					violations.add("line " + lineNo
						+ ": non-blank-origin row is not a 'Use Basket' departure: " + line);
				}
				if (!items.isEmpty())
				{
					violations.add("line " + lineNo + ": departure row carries Items '" + items
						+ "' — destination-keyed consumption requires empty Items on origin rows");
				}
				continue;
			}

			if (lineHasToken && !items.contains("UNLOCK_BALLOON_LOG_BASKET"))
			{
				violations.add("line " + lineNo
					+ ": UNLOCK_BALLOON_LOG_BASKET appears outside the Items column: " + line);
			}
			if (!items.contains("UNLOCK_BALLOON_LOG_BASKET"))
			{
				continue;
			}
			unlockRows++;

			String[] orBranches = items.split("\\|");
			if (orBranches.length < 2)
			{
				violations.add("line " + lineNo + ": Items cell is pure-unlock '" + items
					+ "' — the unlock must relieve a log requirement, not stand alone");
			}
			for (String branch : orBranches)
			{
				if (branch.contains("UNLOCK_BALLOON_LOG_BASKET")
					&& !branch.trim().equals("UNLOCK_BALLOON_LOG_BASKET=1"))
				{
					violations.add("line " + lineNo + ": UNLOCK_BALLOON_LOG_BASKET must be a "
						+ "standalone |-OR-branch operand, never &-ANDed: '" + items + "'");
				}
			}
		}

		Assert.assertEquals("expected exactly 6 destination-side rows carrying "
			+ "UNLOCK_BALLOON_LOG_BASKET (Entrana, Taverley, Crafting Guild, Varrock, "
			+ "Castle Wars, Grand Tree)", 6, unlockRows);
		Assert.assertTrue("hot_air_balloons.tsv should contain 'Use Basket' departure rows",
			departureRows > 0);
		Assert.assertTrue("balloon unlock placement violations:\n" + String.join("\n", violations),
			violations.isEmpty());

		System.out.printf("Validated %d balloon unlock relief rows across %d departure rows.\n",
			unlockRows, departureRows);
	}
}
