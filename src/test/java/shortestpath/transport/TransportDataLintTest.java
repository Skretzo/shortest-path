package shortestpath.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;

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
		Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();
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
}
