package shortestpath.transport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import shortestpath.ShortestPathPlugin;
import shortestpath.Util;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueRegion;

/**
 * Unit tests for the per-transport seasonal-league region override
 * (column {@code Region override} in seasonal_transports.tsv). The
 * override replaces the chunk-classifier result for the destination
 * endpoint in
 * {@code PathfinderConfig.isTransportRegionAllowed(Transport)}.
 */
public class TransportRegionOverrideTest
{
	private Map<Integer, Set<Transport>> transports;

	@Before
	public void setUp()
	{
		transports = new HashMap<>();
	}

	private Transport firstTransport(int origin)
	{
		Set<Transport> set = transports.get(origin);
		Assert.assertNotNull("Expected transports for origin " + origin, set);
		Assert.assertFalse("Transport set for origin " + origin + " should not be empty", set.isEmpty());
		return set.iterator().next();
	}

	// ── Builder parsing ───────────────────────────────────────────────

	@Test
	public void testBuilderAcceptsValidEnum()
	{
		Transport t = new Transport.TransportBuilder()
			.origin(WorldPointUtil.packWorldPoint(3200, 3200, 0))
			.destination(WorldPointUtil.packWorldPoint(3300, 3300, 0))
			.type(TransportType.TRANSPORT)
			.regionOverride("ASGARNIA")
			.build();
		Assert.assertEquals(LeagueRegion.ASGARNIA, t.getRegionOverride());
	}

	@Test
	public void testBuilderAcceptsLowercaseAndWhitespace()
	{
		Transport t = new Transport.TransportBuilder()
			.origin(WorldPointUtil.packWorldPoint(3200, 3200, 0))
			.destination(WorldPointUtil.packWorldPoint(3300, 3300, 0))
			.type(TransportType.TRANSPORT)
			.regionOverride("  kandarin  ")
			.build();
		Assert.assertEquals(LeagueRegion.KANDARIN, t.getRegionOverride());
	}

	@Test
	public void testBuilderNullValueLeavesOverrideUnset()
	{
		Transport t = new Transport.TransportBuilder()
			.origin(WorldPointUtil.packWorldPoint(3200, 3200, 0))
			.destination(WorldPointUtil.packWorldPoint(3300, 3300, 0))
			.type(TransportType.TRANSPORT)
			.regionOverride((String) null)
			.build();
		Assert.assertNull(t.getRegionOverride());
	}

	@Test
	public void testBuilderEmptyValueLeavesOverrideUnset()
	{
		Transport t = new Transport.TransportBuilder()
			.origin(WorldPointUtil.packWorldPoint(3200, 3200, 0))
			.destination(WorldPointUtil.packWorldPoint(3300, 3300, 0))
			.type(TransportType.TRANSPORT)
			.regionOverride("")
			.build();
		Assert.assertNull(t.getRegionOverride());
	}

	@Test
	public void testBuilderInvalidValueLeavesOverrideUnset()
	{
		// Unknown enum name is logged and silently ignored so a single
		// malformed row does not break the whole TSV load.
		Transport t = new Transport.TransportBuilder()
			.origin(WorldPointUtil.packWorldPoint(3200, 3200, 0))
			.destination(WorldPointUtil.packWorldPoint(3300, 3300, 0))
			.type(TransportType.TRANSPORT)
			.regionOverride("NOT_A_REGION")
			.build();
		Assert.assertNull(t.getRegionOverride());
	}

	// ── TSV round-trip ────────────────────────────────────────────────

	@Test
	public void testTsvRoundTripPopulatesOverride()
	{
		String contents = "# Origin\tDestination\tDuration\tRegion override\n" +
			"3200 3200 0\t3300 3300 0\t5\tVARLAMORE\n";

		TransportLoader.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);

		int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		Transport t = firstTransport(origin);
		Assert.assertEquals(LeagueRegion.VARLAMORE, t.getRegionOverride());
	}

	@Test
	public void testTsvRoundTripWithoutOverrideLeavesNull()
	{
		String contents = "# Origin\tDestination\tDuration\tRegion override\n" +
			"3200 3200 0\t3300 3300 0\t5\t\n";

		TransportLoader.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);

		int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		Transport t = firstTransport(origin);
		Assert.assertNull(t.getRegionOverride());
	}

	@Test
	public void testTsvRoundTripWithoutColumnLeavesNull()
	{
		String contents = "# Origin\tDestination\tDuration\n" +
			"3200 3200 0\t3300 3300 0\t5\n";

		TransportLoader.addTransportsFromContents(transports, contents, TransportType.TRANSPORT, 0);

		int origin = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		Transport t = firstTransport(origin);
		Assert.assertNull(t.getRegionOverride());
	}

	// ── Real seasonal_transports.tsv resource ─────────────────────────

	/**
	 * Locks in the active set of per-row overrides shipped with the
	 * plugin. The bbox classifier in leagues_regions.tsv is the source
	 * of truth; any chunk that still disagrees with the wiki keeps an
	 * override here. Adjust the expected map when adding, removing, or
	 * relocating an override and document the reason in the TSV.
	 */
	@Test
	public void testSeasonalTransportsResourceCarriesDocumentedOverrides() throws IOException
	{
		Map<Integer, LeagueRegion> expected = new HashMap<>();
		// Banker's Briefcase: Aldarin — chunk cx=23 cy=53 is Kourend
		// (Land's End shoreline), wiki lists destination under Varlamore.
		expected.put(WorldPointUtil.packWorldPoint(1502, 3410, 0), LeagueRegion.VARLAMORE);
		// Map of Alacrity: Trollheim Wilderness climb — chunk cx=46
		// cy=57 is Wilderness, wiki lists destination under Asgarnia.
		expected.put(WorldPointUtil.packWorldPoint(2946, 3678, 0), LeagueRegion.ASGARNIA);
		// Fairy Mushroom: AIR (SE of Ardougne) — chunk cx=42 cy=50 is
		// Karamja (Brimhaven east), wiki lists destination under Kandarin.
		expected.put(WorldPointUtil.packWorldPoint(2700, 3247, 0), LeagueRegion.KANDARIN);
		// Map of Alacrity: Barbarian Outpost Basalt causeway — chunk
		// cx=39 cy=56 stays FREMENNIK because the Lighthouse dominates
		// the chunk; the Basalt causeway is listed under Kandarin on
		// the league wiki and so carries a per-row override.
		expected.put(WorldPointUtil.packWorldPoint(2522, 3595, 0), LeagueRegion.KANDARIN);
		// Map of Alacrity: Sinclair Mansion Log balance — chunk cx=42
		// cy=56 is FREMENNIK; the destination is listed under Kandarin
		// on the league wiki and so carries a per-row override.
		expected.put(WorldPointUtil.packWorldPoint(2722, 3592, 0), LeagueRegion.KANDARIN);
		// Fairy Mushroom: DLR (Poison Waste south of Isafdar) — chunk
		// cx=34 cy=48 is Tirannwn, wiki lists destination under Kandarin.
		expected.put(WorldPointUtil.packWorldPoint(2213, 3099, 0), LeagueRegion.KANDARIN);

		Map<Integer, LeagueRegion> seen = new HashMap<>();
		for (Set<Transport> set : loadSeasonalTransports().values())
		{
			for (Transport t : set)
			{
				if (t.getRegionOverride() != null)
				{
					seen.put(t.getDestination(), t.getRegionOverride());
				}
			}
		}

		Assert.assertEquals(
			"Active region overrides drifted from documented set — update both this test "
				+ "and the in-file comments when changing the override list.",
			expected, seen);
	}

	private Map<Integer, Set<Transport>> loadSeasonalTransports() throws IOException
	{
		Map<Integer, Set<Transport>> loaded = new HashMap<>();
		try (InputStream in = ShortestPathPlugin.class.getResourceAsStream(
			"/transports/seasonal_transports.tsv"))
		{
			String contents = new String(
				Util.readAllBytes(Objects.requireNonNull(in)),
				StandardCharsets.UTF_8);
			TransportLoader.addTransportsFromContents(
				loaded, contents, TransportType.SEASONAL_TRANSPORTS, 0);
		}
		return loaded;
	}
}
