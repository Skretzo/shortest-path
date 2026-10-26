package shortestpath.poh;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import shortestpath.Util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * TSV-to-enum parity for {@link PohNexusPortal}: the bundled
 * {@code poh-nexus-portals.tsv} is the row source, so a mistyped or missing
 * row would silently change the enabled-portal set at eligibility. These
 * checks read the resource the same way the enum's loader does — same path,
 * same {@code Util.each} comment/blank skipping, same tab split.
 */
public class PohNexusPortalTsvTest
{
	private static Map<String, String[]> rows()
	{
		Map<String, String[]> rows = new HashMap<>();
		try
		{
			Util.each("/poh-nexus-portals.tsv", (line, cols) -> rows.put(cols[0], cols));
		}
		catch (Exception e)
		{
			throw new AssertionError("failed to load poh-nexus-portals.tsv", e);
		}
		return rows;
	}

	@Test
	public void everyConstantHasExactlyOneTsvRow()
	{
		Map<String, String[]> rows = rows();
		assertEquals("every enum constant needs exactly one TSV row",
			PohNexusPortal.values().length, rows.size());
		for (PohNexusPortal portal : PohNexusPortal.values())
		{
			assertTrue("no TSV row for " + portal.name(), rows.containsKey(portal.name()));
		}
	}

	@Test
	public void labelAndGroupComeFromTheTsv()
	{
		for (Map.Entry<String, String[]> row : rows().entrySet())
		{
			PohNexusPortal portal = PohNexusPortal.valueOf(row.getKey());
			assertEquals(row.getKey() + " label", row.getValue()[1], portal.toString());
			assertEquals(row.getKey() + " group",
				PohNexusPortal.PortalGroup.valueOf(row.getValue()[2]), portal.group());
		}
	}

	@Test
	public void fromDisplayInfoRoundTripsEveryTsvDisplayInfo()
	{
		for (Map.Entry<String, String[]> row : rows().entrySet())
		{
			PohNexusPortal portal = PohNexusPortal.valueOf(row.getKey());
			for (String displayInfo : row.getValue()[3].split(","))
			{
				assertSame(displayInfo, portal, PohNexusPortal.fromDisplayInfo(displayInfo));
			}
		}
	}

	@Test
	public void noDuplicateDisplayInfos()
	{
		// The enum's static-init dup-check throws on a duplicated displayInfo;
		// the shipped resource is fixed at build time, so the row-level check
		// here asserts the same invariant the dup-check guards.
		Set<String> seen = new HashSet<>();
		for (String[] cols : rows().values())
		{
			for (String displayInfo : cols[3].split(","))
			{
				assertTrue("duplicate displayInfo: " + displayInfo, seen.add(displayInfo));
			}
		}
	}

	@Test
	public void enumSetDefaultPathsCoverEveryConstant()
	{
		assertEquals(PohNexusPortal.values().length,
			EnumSet.allOf(PohNexusPortal.class).size());
		assertTrue(EnumSet.noneOf(PohNexusPortal.class).isEmpty());
	}
}
