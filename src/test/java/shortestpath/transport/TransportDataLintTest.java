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
import org.junit.Assert;
import org.junit.Test;
import shortestpath.Util;
import shortestpath.WorldPointUtil;
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
	public void testNoDuplicateOriginDestinationPairs()
	{
		// Load all transport data from resources
		HashMap<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();

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
						Arrays.toString(transport.getSkillLevels()));

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
						Arrays.toString(transport.getSkillLevels()));
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
}
