package shortestpath.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import shortestpath.WorldPointUtil;

/**
 * Unit tests for {@link TransportMembership} and the {@code F2P} column
 * (blank = members-only, {@code f2p} = both world types, {@code f2p-only} =
 * free-to-play worlds only).
 */
public class TransportMembershipTest
{
	private static final int A = WorldPointUtil.packWorldPoint(3200, 3200, 0);
	private static final int B = WorldPointUtil.packWorldPoint(3300, 3300, 0);
	private static final int C = WorldPointUtil.packWorldPoint(3210, 3210, 0);
	private static final int D = WorldPointUtil.packWorldPoint(3310, 3310, 0);
	private static final int E = WorldPointUtil.packWorldPoint(3220, 3220, 0);
	private static final int F = WorldPointUtil.packWorldPoint(3320, 3320, 0);

	private Map<Integer, Set<Transport>> transports;

	@Before
	public void setUp()
	{
		transports = new HashMap<>();
	}

	// ── Parsing column values ─────────────────────────────────────────

	@Test
	public void testFromValueParsesTheThreeValues()
	{
		Assert.assertEquals(TransportMembership.MEMBERS, TransportMembership.fromValue(""));
		Assert.assertEquals(TransportMembership.F2P, TransportMembership.fromValue("f2p"));
		Assert.assertEquals(TransportMembership.F2P_ONLY, TransportMembership.fromValue("f2p-only"));
	}

	@Test
	public void testFromValueTreatsNullAndWhitespaceAsBlank()
	{
		Assert.assertEquals(TransportMembership.MEMBERS, TransportMembership.fromValue(null));
		Assert.assertEquals(TransportMembership.MEMBERS, TransportMembership.fromValue("  "));
		Assert.assertEquals(TransportMembership.F2P, TransportMembership.fromValue(" f2p "));
	}

	@Test
	public void testFromValueRejectsUnknownValues()
	{
		Assert.assertNull(TransportMembership.fromValue("F2P"));
		Assert.assertNull(TransportMembership.fromValue("members"));
		Assert.assertNull(TransportMembership.fromValue("p2p"));
	}

	// ── World-type semantics ──────────────────────────────────────────

	@Test
	public void testIsUsableOn()
	{
		Assert.assertTrue(TransportMembership.MEMBERS.isUsableOn(true));
		Assert.assertFalse(TransportMembership.MEMBERS.isUsableOn(false));
		Assert.assertTrue(TransportMembership.F2P.isUsableOn(true));
		Assert.assertTrue(TransportMembership.F2P.isUsableOn(false));
		Assert.assertFalse(TransportMembership.F2P_ONLY.isUsableOn(true));
		Assert.assertTrue(TransportMembership.F2P_ONLY.isUsableOn(false));
	}

	@Test
	public void testCombineIsUsableOnlyWhereBothHalvesAre()
	{
		for (TransportMembership first : TransportMembership.values())
		{
			for (TransportMembership second : TransportMembership.values())
			{
				TransportMembership combined = TransportMembership.combine(first, second);
				for (boolean membersWorld : new boolean[]{true, false})
				{
					boolean expected = first.isUsableOn(membersWorld) && second.isUsableOn(membersWorld);
					boolean actual = combined != null && combined.isUsableOn(membersWorld);
					Assert.assertEquals(first + " + " + second + " on membersWorld=" + membersWorld,
						expected, actual);
				}
			}
		}
	}

	@Test
	public void testCombineMembersWithF2pOnlyHasNoValue()
	{
		Assert.assertNull(TransportMembership.combine(TransportMembership.MEMBERS, TransportMembership.F2P_ONLY));
		Assert.assertNull(TransportMembership.combine(TransportMembership.F2P_ONLY, TransportMembership.MEMBERS));
	}

	// ── Builder ───────────────────────────────────────────────────────

	@Test
	public void testBuilderDefaultsToMembers()
	{
		Assert.assertEquals(TransportMembership.MEMBERS, builder().build().getMembership());
	}

	@Test
	public void testBuilderAcceptsColumnValues()
	{
		Assert.assertEquals(TransportMembership.F2P, builder().membership("f2p").build().getMembership());
		Assert.assertEquals(TransportMembership.F2P_ONLY, builder().membership("f2p-only").build().getMembership());
	}

	@Test
	public void testBuilderInvalidValueFallsBackToMembers()
	{
		Assert.assertEquals(TransportMembership.MEMBERS, builder().membership("yes").build().getMembership());
	}

	// ── Loading from TSV ──────────────────────────────────────────────

	@Test
	public void testLoaderReadsF2pColumn()
	{
		String tsv = "# Origin\tDestination\tmenuOption menuTarget objectID\tDuration\tF2P\n"
			+ row(A, B, "f2p") + "\n"
			+ row(C, D, "f2p-only") + "\n"
			+ row(E, F, "") + "\n";
		TransportLoader.addTransportsFromContents(transports, tsv, TransportType.TELEPORTATION_PORTAL, 0);

		Assert.assertEquals(TransportMembership.F2P, only(A).getMembership());
		Assert.assertEquals(TransportMembership.F2P_ONLY, only(C).getMembership());
		Assert.assertEquals(TransportMembership.MEMBERS, only(E).getMembership());
	}

	@Test
	public void testLoaderWithoutF2pColumnDefaultsToMembers()
	{
		String tsv = "# Origin\tDestination\tmenuOption menuTarget objectID\tDuration\n"
			+ WorldPointUtil.unpackWorldX(A) + " " + WorldPointUtil.unpackWorldY(A) + " 0\t"
			+ WorldPointUtil.unpackWorldX(B) + " " + WorldPointUtil.unpackWorldY(B) + " 0\tEnter Door 1\t1\n";
		TransportLoader.addTransportsFromContents(transports, tsv, TransportType.TELEPORTATION_PORTAL, 0);

		Assert.assertEquals(TransportMembership.MEMBERS, only(A).getMembership());
	}

	@Test
	public void testLoaderCombinesOriginAndDestinationRows()
	{
		// Origin-only row A (f2p) joined with destination-only row B (members) -> members
		String tsv = "# Origin\tDestination\tmenuOption menuTarget objectID\tDuration\tF2P\n"
			+ point(A) + "\t\tUse Ring 1\t1\tf2p\n"
			+ "\t" + point(B) + "\tUse Ring 1\t1\t\n";
		TransportLoader.addTransportsFromContents(transports, tsv, TransportType.FAIRY_RING, 0);

		Transport combined = only(A);
		Assert.assertEquals(B, combined.getDestination());
		Assert.assertEquals(TransportMembership.MEMBERS, combined.getMembership());
	}

	@Test
	public void testLoaderSkipsCombinationsUsableOnNoWorld()
	{
		// Origin-only row A (f2p-only) joined with destination-only row B (members) is usable nowhere
		String tsv = "# Origin\tDestination\tmenuOption menuTarget objectID\tDuration\tF2P\n"
			+ point(A) + "\t\tUse Ring 1\t1\tf2p-only\n"
			+ "\t" + point(B) + "\tUse Ring 1\t1\t\n";
		TransportLoader.addTransportsFromContents(transports, tsv, TransportType.FAIRY_RING, 0);

		Assert.assertNull(transports.get(A));
	}

	private static Transport.TransportBuilder builder()
	{
		return new Transport.TransportBuilder()
			.origin(A)
			.destination(B)
			.type(TransportType.TELEPORTATION_PORTAL);
	}

	private static String point(int packed)
	{
		return WorldPointUtil.unpackWorldX(packed) + " " + WorldPointUtil.unpackWorldY(packed) + " "
			+ WorldPointUtil.unpackWorldPlane(packed);
	}

	private static String row(int origin, int destination, String f2p)
	{
		return point(origin) + "\t" + point(destination) + "\tEnter Door 1\t1\t" + f2p;
	}

	private Transport only(int origin)
	{
		Set<Transport> set = transports.get(origin);
		Assert.assertNotNull("Expected a transport from " + origin, set);
		Assert.assertEquals(1, set.size());
		return set.iterator().next();
	}
}
