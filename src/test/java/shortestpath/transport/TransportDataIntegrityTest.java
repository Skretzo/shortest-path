package shortestpath.transport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import shortestpath.ShortestPathPlugin;
import shortestpath.Util;
import shortestpath.transport.parser.TransportRecord;

/**
 * Integrity checks for the {@code F2P} column in the committed transport TSVs.
 */
public class TransportDataIntegrityTest
{
	private static Map<Integer, Set<Transport>> transports;

	@BeforeClass
	public static void loadTransports()
	{
		transports = TransportLoader.loadAllFromResources();
	}

	@Test
	public void transportsTsvHasNoF2pColumn() throws IOException
	{
		Assert.assertFalse(
			"transports.tsv has an F2P column, but TransportType.TRANSPORT is exempt from filtering, so it is "
				+ "ignored. Classify every row, then remove the exemption and this test (see TransportType).",
			headers(TransportType.TRANSPORT).contains(TransportRecord.Fields.F2P));
	}

	@Test
	public void transportExemptionIsKeptUntilTransportsTsvIsClassified() throws IOException
	{
		if (!headers(TransportType.TRANSPORT).contains(TransportRecord.Fields.F2P))
		{
			Assert.assertFalse(
				"TransportType.TRANSPORT is no longer exempt from filtering, but transports.tsv has no F2P column, "
					+ "so every row in it is members-only. Restore the exemption or classify the file first.",
				TransportType.TRANSPORT.isMembershipFiltered());
		}
	}

	/**
	 * Spot checks of classifications that are easy to get wrong, so an
	 * accidental data edit shows up as a test failure.
	 */
	@Test
	public void knownClassificationsAreUnchanged()
	{
		assertDisplayInfo(TransportType.TELEPORTATION_SPELL, "Varrock Teleport", TransportMembership.F2P);
		// Needs the Varrock medium diary, which is members-only
		assertDisplayInfo(TransportType.TELEPORTATION_SPELL, "Varrock Teleport: Grand Exchange", TransportMembership.MEMBERS);
		assertDisplayInfo(TransportType.TELEPORTATION_SPELL, "Teleport to House", TransportMembership.MEMBERS);
		assertDisplayInfo(TransportType.TELEPORTATION_SPELL_HOME, "Lumbridge Home Teleport", TransportMembership.F2P);
		assertDisplayInfo(TransportType.TELEPORTATION_SPELL_HOME, "Edgeville Home Teleport", TransportMembership.MEMBERS);
		assertDisplayInfo(TransportType.TELEPORTATION_ITEM, "Skull sceptre", TransportMembership.F2P);
		assertDisplayInfo(TransportType.TELEPORTATION_ITEM, "Mythical cape", TransportMembership.MEMBERS);
		assertDisplayInfo(TransportType.TELEPORTATION_MINIGAME, "Castle Wars Minigame Teleport", TransportMembership.F2P);
		assertDisplayInfo(TransportType.TELEPORTATION_MINIGAME, "Giant's Foundry Minigame Teleport", TransportMembership.MEMBERS);
		assertObjectInfo(TransportType.TELEPORTATION_PORTAL, "Enter Large door 30388", TransportMembership.F2P_ONLY);
		assertObjectInfo(TransportType.TELEPORTATION_PORTAL, "Enter Free-for-all portal 26645", TransportMembership.F2P);
		assertObjectInfo(TransportType.SHIP, "Musa Point Captain Tobias 14979", TransportMembership.F2P);
		// The Pandemonium is Sailing content, which is members-only
		assertObjectInfo(TransportType.SHIP, "The Pandemonium Captain Tobias 14979", TransportMembership.MEMBERS);
		// River Lum canoes are free-to-play; River Dougne canoes are members-only
		assertObjectInfo(TransportType.CANOE, "Paddle Canoe Canoe Station 12163", TransportMembership.F2P);
		assertObjectInfo(TransportType.CANOE, "Paddle Canoe Canoe Station 60845", TransportMembership.MEMBERS);
	}

	private static void assertDisplayInfo(TransportType type, String displayInfo, TransportMembership expected)
	{
		assertMatches(type, "display info \"" + displayInfo + "\"",
			t -> displayInfo.equals(t.getDisplayInfo()), expected);
	}

	private static void assertObjectInfo(TransportType type, String objectInfo, TransportMembership expected)
	{
		assertMatches(type, "object info \"" + objectInfo + "\"",
			t -> objectInfo.equals(t.getObjectInfo()), expected);
	}

	private static void assertMatches(TransportType type, String description,
		Predicate<Transport> matcher, TransportMembership expected)
	{
		int matches = 0;
		for (Set<Transport> set : transports.values())
		{
			for (Transport transport : set)
			{
				if (transport.getType() == type && matcher.test(transport))
				{
					matches++;
					Assert.assertEquals(type + " with " + description, expected, transport.getMembership());
				}
			}
		}
		Assert.assertTrue("No " + type + " transport with " + description, matches > 0);
	}

	private static List<String> headers(TransportType type) throws IOException
	{
		String header = read(type).split("\n", 2)[0];
		if (header.startsWith("#"))
		{
			header = header.substring(1).trim();
		}
		return Arrays.asList(header.split("\t"));
	}

	private static String read(TransportType type) throws IOException
	{
		try (InputStream in = ShortestPathPlugin.class.getResourceAsStream(type.getResourcePath()))
		{
			return new String(Util.readAllBytes(Objects.requireNonNull(in)), StandardCharsets.UTF_8);
		}
	}
}
