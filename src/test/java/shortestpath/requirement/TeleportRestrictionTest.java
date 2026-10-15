package shortestpath.requirement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 * Direct unit coverage of the {@code blockedTeleportItems} CSV contract shared
 * by the sidebar restriction checklist and {@code refresh()}.
 * {@link TeleportRestriction#parseBlocked} reads user- or profile-authored CSV
 * into a blocked-id set plus a per-item threshold map;
 * {@link TeleportRestriction#toCsv} serializes the panel's state back. A bare
 * {@code id} record blocks the item; an {@code id:N} record sets a per-item
 * threshold override of N tiles. Malformed entries are skipped with a warning —
 * the parser must never throw on hand-edited config.
 */
public class TeleportRestrictionTest
{
	@Test
	public void bareIdsParseIntoTheBlockedSet()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();

		TeleportRestriction.parseBlocked("123,456", blocked, thresholds);

		Assert.assertEquals(Set.of(123, 456), blocked);
		Assert.assertTrue(thresholds.isEmpty());
	}

	@Test
	public void colonEntriesParseIntoTheThresholdMap()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();

		TeleportRestriction.parseBlocked("123:30,7:5", blocked, thresholds);

		Assert.assertTrue(blocked.isEmpty());
		Assert.assertEquals(Map.of(123, 30, 7, 5), thresholds);
	}

	@Test
	public void malformedEntriesAreSkippedAndGoodEntriesSurvive()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();

		// "abc" is not an id; "456:x" has a non-integer threshold — the whole
		// entry is dropped, 456 is NOT partially blocked; "12:3:4" has a second
		// colon making the threshold unparseable; ":5" has an empty id; "12:"
		// has an empty threshold; empty segments between commas are ignored.
		TeleportRestriction.parseBlocked("123,abc,456:x,789:10,,12:3:4,:5,12:", blocked, thresholds);

		Assert.assertEquals(Set.of(123), blocked);
		Assert.assertEquals(Map.of(789, 10), thresholds);
	}

	@Test
	public void duplicateThresholdEntriesKeepTheMax()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();

		TeleportRestriction.parseBlocked("42:10,42:30,42:20", blocked, thresholds);

		Assert.assertEquals(Map.of(42, 30), thresholds);
	}

	@Test
	public void nullAndBlankCsvProduceNoRestrictions()
	{
		for (String csv : new String[]{null, "", "   "})
		{
			Set<Integer> blocked = new HashSet<>();
			Map<Integer, Integer> thresholds = new HashMap<>();

			TeleportRestriction.parseBlocked(csv, blocked, thresholds);

			Assert.assertTrue("csv '" + csv + "' must leave the blocked set empty", blocked.isEmpty());
			Assert.assertTrue("csv '" + csv + "' must leave the threshold map empty", thresholds.isEmpty());
		}
	}

	@Test
	public void whitespaceAroundEntriesIsTolerated()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();

		TeleportRestriction.parseBlocked(" 12 , 34 : 7 ", blocked, thresholds);

		Assert.assertEquals(Set.of(12), blocked);
		Assert.assertEquals(Map.of(34, 7), thresholds);
	}

	@Test
	public void toCsvEmitsSortedBlockedIdsThenSortedThresholdRecords()
	{
		String csv = TeleportRestriction.toCsv(Set.of(300, 100), Map.of(200, 5, 50, 9));

		Assert.assertEquals("100,300,50:9,200:5", csv);
	}

	@Test
	public void toCsvWithEmptyOrNullInputsProducesEmptyString()
	{
		Assert.assertEquals("", TeleportRestriction.toCsv(Set.of(), Map.of()));
		Assert.assertEquals("", TeleportRestriction.toCsv(null, null));
		Assert.assertEquals("", TeleportRestriction.toCsv(Set.of(), null));
		Assert.assertEquals("", TeleportRestriction.toCsv(null, Map.of()));
	}

	@Test
	public void toCsvEmitsOnlyThePopulatedSide()
	{
		Assert.assertEquals("1", TeleportRestriction.toCsv(Set.of(1), null));
		Assert.assertEquals("2:8", TeleportRestriction.toCsv(null, Map.of(2, 8)));
	}

	@Test
	public void csvRoundTripsThroughSerializeAndParse()
	{
		Set<Integer> blocked = Set.of(5, 90);
		Map<Integer, Integer> thresholds = Map.of(7, 3, 42, 100);

		String csv = TeleportRestriction.toCsv(blocked, thresholds);
		Assert.assertEquals("5,90,7:3,42:100", csv);

		Set<Integer> parsedBlocked = new HashSet<>();
		Map<Integer, Integer> parsedThresholds = new HashMap<>();
		TeleportRestriction.parseBlocked(csv, parsedBlocked, parsedThresholds);

		Assert.assertEquals(blocked, parsedBlocked);
		Assert.assertEquals(thresholds, parsedThresholds);
	}

	/**
	 * The parser is permissive: {@code "123,123:30"} lands the id in BOTH
	 * outputs. Disjointness is a writer-side convention (blocking a family
	 * clears its threshold overrides) — hand-edited CSV can overlap, and the
	 * parse must not reject it.
	 */
	@Test
	public void theSameIdMayBeBothBlockedAndThresholded()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();

		TeleportRestriction.parseBlocked("123,123:30", blocked, thresholds);

		Assert.assertEquals(Set.of(123), blocked);
		Assert.assertEquals(Map.of(123, 30), thresholds);
	}
}
