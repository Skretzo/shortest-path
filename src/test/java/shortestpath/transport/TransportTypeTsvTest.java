package shortestpath.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import shortestpath.Util;

/**
 * TSV-to-registry parity for {@link TransportType}: the bundled
 * {@code transport-types.tsv} is the row source, so a mistyped, missing,
 * or surplus row would silently change which resource a type loads, the
 * config keys it reads, or a flag the wilderness routing relies on.
 * These checks read the resource the same way the enum's loader does
 * ({@link Util#each} with the same path), assert the row set is
 * one-to-one with the constants in declaration order, re-check every
 * declared column against the resolved constant, pin the literals the
 * rows must reproduce, and exercise the loader's loud posture on
 * malformed, missing, and mis-parsed input.
 */
public class TransportTypeTsvTest
{
	private static Map<String, String[]> rows()
	{
		return TransportType.loadRows("/transport-types.tsv");
	}

	@Test
	public void everyConstantHasExactlyOneTsvRow()
	{
		Map<String, String[]> rows = rows();
		assertEquals("every enum constant needs exactly one TSV row",
			TransportType.values().length, rows.size());
		for (TransportType type : TransportType.values())
		{
			assertTrue("no TSV row for " + type.name(), rows.containsKey(type.name()));
		}
		// The ctor fails loudly on a constant with no row; the enum's
		// static-init valueOf pass fails loudly on a row naming no
		// constant — asserted by resolving every shipped name here.
		for (String name : rows.keySet())
		{
			TransportType.valueOf(name);
		}
	}

	@Test
	public void rowsFollowDeclarationOrder() throws Exception
	{
		List<String> tsvNames = new ArrayList<>();
		Util.each("/transport-types.tsv", (line, cols) -> tsvNames.add(cols[0]));
		List<String> declared = new ArrayList<>();
		for (TransportType type : TransportType.values())
		{
			declared.add(type.name());
		}
		assertEquals("TSV rows must follow enum declaration order", declared, tsvNames);
	}

	/**
	 * Every column of every row must resolve to the value the constant
	 * reports: nulls round-trip through empty cells, the radius parses
	 * to its {@link Integer}, the teleport flag reads strictly, and the
	 * shares-with name resolves to the paired constant.
	 */
	@Test
	public void everyRowResolvesItsDeclaredData()
	{
		for (Map.Entry<String, String[]> entry : rows().entrySet())
		{
			String name = entry.getKey();
			String[] cols = entry.getValue();
			TransportType type = TransportType.valueOf(name);

			assertEquals(name + " resourcePath",
				cols[1].isEmpty() ? null : cols[1], type.getResourcePath());
			assertEquals(name + " enabledKey",
				cols[2].isEmpty() ? null : cols[2], type.getEnabledKey());
			assertEquals(name + " costKey",
				cols[3].isEmpty() ? null : cols[3], type.getCostKey());
			assertEquals(name + " radiusThreshold",
				cols[4].isEmpty() ? null : Integer.valueOf(cols[4]), type.getRadiusThreshold());
			assertEquals(name + " teleport", "true".equals(cols[5]), type.isTeleport());
			assertEquals(name + " sharesDestinationsWith",
				cols[6].isEmpty() ? null : cols[6],
				type.sharesDestinationsWith() == null ? null : type.sharesDestinationsWith().name());
			assertEquals(name + " differentialCostKey",
				cols[7].isEmpty() ? null : cols[7], type.differentialCostKey());
		}
	}

	/**
	 * The literals the pre-extraction tuples carried, pinned so the TSV
	 * can never silently re-type a flag or re-pair a constant: the five
	 * teleports, the quetzal/whistle destination pairing, the whistle's
	 * differential cost key, and the null-cell rows.
	 */
	@Test
	public void resolvedValuesMatchPreExtractionBehaviour()
	{
		assertTrue(TransportType.QUETZAL_WHISTLE.isTeleport());
		assertTrue(TransportType.TELEPORTATION_ITEM.isTeleport());
		assertTrue(TransportType.TELEPORTATION_MINIGAME.isTeleport());
		assertTrue(TransportType.TELEPORTATION_SPELL.isTeleport());
		assertTrue(TransportType.TELEPORTATION_SPELL_HOME.isTeleport());
		for (TransportType type : TransportType.values())
		{
			if (!type.isTeleport())
			{
				continue;
			}
			assertTrue(type.name() + " is an unlisted teleport",
				type == TransportType.QUETZAL_WHISTLE
					|| type == TransportType.TELEPORTATION_ITEM
					|| type == TransportType.TELEPORTATION_MINIGAME
					|| type == TransportType.TELEPORTATION_SPELL
					|| type == TransportType.TELEPORTATION_SPELL_HOME);
		}

		assertSame(TransportType.QUETZAL_WHISTLE,
			TransportType.QUETZAL.sharesDestinationsWith());
		assertSame(TransportType.QUETZAL,
			TransportType.QUETZAL_WHISTLE.sharesDestinationsWith());
		assertNull(TransportType.BOAT.sharesDestinationsWith());
		assertNull(TransportType.TRANSPORT.sharesDestinationsWith());

		assertEquals("costQuetzalWhistle",
			TransportType.QUETZAL_WHISTLE.differentialCostKey());
		assertNull(TransportType.FAIRY_RING.differentialCostKey());
		assertNull(TransportType.BOAT.differentialCostKey());

		assertFalse(TransportType.GRAPPLE_SHORTCUT.hasResourcePath());
		assertFalse(TransportType.TRANSPORT.hasEnabledKey());
		assertFalse(TransportType.TRANSPORT.hasCostKey());
		assertFalse(TransportType.TELEPORTATION_BOX.hasEnabledKey());
		assertFalse(TransportType.TELEPORTATION_ITEM.hasEnabledKey());
		assertFalse(TransportType.TELEPORTATION_PORTAL_POH.hasEnabledKey());
		assertEquals("costTeleportationPortals",
			TransportType.TELEPORTATION_PORTAL_POH.getCostKey());
		assertNull(TransportType.GRAPPLE_SHORTCUT.getResourcePath());
		assertNull(TransportType.TRANSPORT.getEnabledKey());

		assertTrue(TransportType.FAIRY_RING.hasRadiusThreshold());
		assertEquals(Integer.valueOf(6), TransportType.FAIRY_RING.getRadiusThreshold());
		assertEquals(Integer.valueOf(6), TransportType.GNOME_GLIDER.getRadiusThreshold());
		assertEquals(Integer.valueOf(7), TransportType.HOT_AIR_BALLOON.getRadiusThreshold());
		assertEquals(Integer.valueOf(5), TransportType.MAGIC_MUSHTREE.getRadiusThreshold());
		assertEquals(Integer.valueOf(5), TransportType.QUETZAL.getRadiusThreshold());
		assertEquals(Integer.valueOf(5), TransportType.SPIRIT_TREE.getRadiusThreshold());
		assertFalse(TransportType.BOAT.hasRadiusThreshold());
		assertNull(TransportType.BOAT.getRadiusThreshold());
	}

	@Test
	public void malformedArityIsRejected()
	{
		assertThrows("a row with the wrong column count must fail the load",
			IllegalStateException.class,
			() -> TransportType.loadRows("/transport-types-bad-arity.tsv"));
	}

	@Test
	public void duplicateRowIsRejected()
	{
		IllegalStateException e = assertThrows(
			"a repeated enum-name row must fail the load, not overwrite",
			IllegalStateException.class,
			() -> TransportType.loadRows("/transport-types-dup-row.tsv"));
		assertTrue("must be the duplicate check, not the arity check: " + e.getCause(),
			e.getCause().getMessage().startsWith("duplicate transport-types.tsv row"));
	}

	@Test
	public void missingResourceIsRejected()
	{
		assertThrows("a missing resource must fail the load",
			IllegalStateException.class,
			() -> TransportType.loadRows("/transport-types-does-not-exist.tsv"));
	}

	@Test
	public void unparseableTokensAreRejected()
	{
		assertThrows(IllegalStateException.class,
			() -> TransportType.parseRadius("X", "seven"));
		assertThrows(IllegalStateException.class,
			() -> TransportType.parseTeleport("X", "MAYBE"));
	}
}
