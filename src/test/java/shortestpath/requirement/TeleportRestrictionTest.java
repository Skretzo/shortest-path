package shortestpath.requirement;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;
import shortestpath.ItemVariations;
import shortestpath.requirement.model.TransportItems;

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

	@Test
	public void repAtDestRecordsParseIntoTheOptionBlockMap()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();
		Map<Integer, Set<Integer>> options = new HashMap<>();

		TeleportRestriction.parseBlocked("13121@87359490,1706,13121@999,2552@5",
			blocked, thresholds, options);

		Assert.assertEquals(Set.of(1706), blocked);
		Assert.assertTrue(thresholds.isEmpty());
		Assert.assertEquals(Map.of(13121, Set.of(87359490, 999), 2552, Set.of(5)), options);
	}

	@Test
	public void malformedAtRecordsAreSkippedAndGoodEntriesSurvive()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();
		Map<Integer, Set<Integer>> options = new HashMap<>();

		// "x@" and "@y" each have one empty side; "a@b" has no ints; "1@2@3"
		// carries a second '@' making the split unparseable; "@" alone has
		// two empty sides. The valid sibling must survive untouched.
		TeleportRestriction.parseBlocked("x@,@y,a@b,1@2@3,@,13121@1234,42",
			blocked, thresholds, options);

		Assert.assertEquals(Set.of(42), blocked);
		Assert.assertTrue(thresholds.isEmpty());
		Assert.assertEquals(Map.of(13121, Set.of(1234)), options);
	}

	@Test
	public void negativeIdsThresholdsAndRepsAreSkipped()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();
		Map<Integer, Set<Integer>> options = new HashMap<>();

		// "-5" is a parseable int but no item id is negative; "12:-3" and
		// "-7:4" carry negative halves that are equally meaningless; a
		// negative rep is no displayItemId. All skip like non-numerics.
		// The destination half stays unchecked: packed destinations at
		// plane >= 2 set the sign bit and are legitimately negative.
		TeleportRestriction.parseBlocked("-5,12:-3,-7:4,-13121@999,13121@-87359490",
			blocked, thresholds, options);

		Assert.assertTrue(blocked.isEmpty());
		Assert.assertTrue(thresholds.isEmpty());
		Assert.assertEquals(Map.of(13121, Set.of(-87359490)), options);
	}

	@Test
	public void toCsvSortsOptionRecordsByRepThenDestination()
	{
		String csv = TeleportRestriction.toCsv(Set.of(300, 100), Map.of(200, 5),
			Map.of(20, Set.of(9, 3), 7, Set.of(11)));

		Assert.assertEquals("100,300,200:5,7@11,20@3,20@9", csv);
	}

	@Test
	public void repAtDestRecordsRoundTripThroughSerializeAndParse()
	{
		Map<Integer, Set<Integer>> options =
			Map.of(13121, Set.of(87359490, 999), 2552, Set.of(42));

		String csv = TeleportRestriction.toCsv(Set.of(12), Map.of(34, 7), options);
		Assert.assertEquals("12,34:7,2552@42,13121@999,13121@87359490", csv);

		Set<Integer> parsedBlocked = new HashSet<>();
		Map<Integer, Integer> parsedThresholds = new HashMap<>();
		Map<Integer, Set<Integer>> parsedOptions = new HashMap<>();
		TeleportRestriction.parseBlocked(csv, parsedBlocked, parsedThresholds, parsedOptions);

		Assert.assertEquals(Set.of(12), parsedBlocked);
		Assert.assertEquals(Map.of(34, 7), parsedThresholds);
		Assert.assertEquals(options, parsedOptions);
	}

	@Test
	public void preFeatureCsvParsesIdenticallyThroughTheThreeOutputOverload()
	{
		Set<Integer> blocked = new HashSet<>();
		Map<Integer, Integer> thresholds = new HashMap<>();
		Map<Integer, Set<Integer>> options = new HashMap<>();

		TeleportRestriction.parseBlocked("123,abc,42:30,42:10", blocked, thresholds, options);

		Assert.assertEquals(Set.of(123), blocked);
		Assert.assertEquals(Map.of(42, 30), thresholds);
		Assert.assertTrue(options.isEmpty());
	}

	@Test
	public void representativeIndexCoversEveryFamilyMember()
	{
		Map<Integer, Integer> index = TeleportRestriction.representativeIndex();
		for (TeleportRestriction.Family family : TeleportRestriction.loadFamilies())
		{
			for (int member : family.memberItemIds())
			{
				Assert.assertEquals("member " + member + " of " + family.name(),
					family.displayItemId(), (int) index.getOrDefault(member, -1));
			}
		}
	}

	/**
	 * The Ardougne-cloaks family serves two distinct destinations: the
	 * monastery row every cloak reaches plus the farm rows gated on diary
	 * tier — both must surface with their Display-info labels.
	 */
	@Test
	public void ardougneCloaksExposeTheirTwoDestinationOptions()
	{
		int monastery = shortestpath.WorldPointUtil.packWorldPoint(2607, 3221, 0);
		int farm = shortestpath.WorldPointUtil.packWorldPoint(2665, 3374, 0);

		Assert.assertEquals(
			Map.of(monastery, "Kandarin Monastery", farm, "Ardougne Farm"),
			familyNamed("Ardougne cloaks").options());
	}

	/**
	 * The three varbit-gated Ardougne-farm rows (and the three Morytania
	 * slime-pit rows) share their destination and label — they collapse to
	 * one option, not three.
	 */
	@Test
	public void servingRowsSharingADestinationDedupeToOneOption()
	{
		Assert.assertEquals(2, familyNamed("Morytania legs").options().size());
		Assert.assertEquals(2, familyNamed("Ardougne cloaks").options().size());
	}

	/**
	 * A {@code Display info} cell carrying no {@code ':'} labels its option
	 * by the whole cell text.
	 */
	@Test
	public void noColonDisplayInfoLabelsByTheWholeCell()
	{
		TeleportRestriction.Family cowbell = familyNamed("Cowbell amulet");
		Assert.assertEquals(1, cowbell.options().size());
		Assert.assertTrue(cowbell.options().containsValue("Cowbell amulet"));
	}

	/**
	 * A family whose serving rows all reach one destination yields exactly
	 * one option entry — the UI grants it no option surface.
	 */
	@Test
	public void singleDestinationFamilyYieldsOneOptionEntry()
	{
		Assert.assertEquals(1, familyNamed("Fremennik sea boots").options().size());
	}

	/**
	 * The Skills necklace's two Farming Guild arrivals (the plain landing and
	 * the 45-Farming inside arrival) are one in-game teleport name — they
	 * collapse into a single option covering both tiles, while the other
	 * guilds keep their distinct entries.
	 */
	@Test
	public void skillsNecklaceCollapsesTheFarmingGuildArrivalsIntoOneOption()
	{
		TeleportRestriction.Family necklace = familyNamed("Skills necklace (1-6)");
		int farmingGuild = 0;
		for (String label : necklace.options().values())
		{
			if ("Farming Guild".equals(label))
			{
				farmingGuild++;
			}
		}
		Assert.assertEquals("the two Farming Guild arrivals collapse to one option",
			1, farmingGuild);
		Assert.assertEquals("the six real menu entries stay distinct options",
			6, necklace.options().size());
	}

	/**
	 * The house tablet's ten varbit-gated portal towns are not ten player
	 * choices: the in-game menu is "Inside the house" / "Outside the house",
	 * so the family yields exactly those two options.
	 */
	@Test
	public void teleportToHouseTabletYieldsInsideAndOutsideOnly()
	{
		TeleportRestriction.Family house = familyNamed("Teleport to house");
		Assert.assertEquals(2, house.options().size());
		Assert.assertTrue(house.options().containsValue("Inside the house"));
		Assert.assertTrue(house.options().containsValue("Outside the house"));
	}

	/**
	 * The Construction cape's varbit-gated home arrivals and its direct
	 * portal teleports reach the same tiles — they merge under the plain
	 * portal-town name rather than a "Home (town)" variant.
	 */
	@Test
	public void constructionCapeMergesHomeAndPortalRowsUnderTheTownName()
	{
		TeleportRestriction.Family capes =
			familyNamed("Construction cape");
		Assert.assertTrue(capes.options().containsValue("Rimmington"));
		Assert.assertTrue(capes.options().containsValue("Aldarin"));
		Assert.assertFalse(capes.options().containsValue("Home (Rimmington)"));
	}

	/**
	 * The Max cape's varbit-gated "Tele to POH" town arrivals merge into its
	 * unconditional "POH Portals" submenu entries on the shared tiles.
	 */
	@Test
	public void maxCapeMergesTeleToPohRowsUnderPohPortals()
	{
		TeleportRestriction.Family maxCapes = familyNamed("Max cape");
		Assert.assertTrue(maxCapes.options().containsValue("POH Portals: Rimmington"));
		Assert.assertTrue(maxCapes.options().containsValue("POH Portals: Aldarin"));
		Assert.assertFalse(maxCapes.options().containsValue("Tele to POH (Rimmington)"));
	}

	/**
	 * Royal and Grand seed pods are different items whose teleports land on
	 * the same Grand Tree tile — each per-item family yields its own option
	 * named for where the teleport goes.
	 */
	@Test
	public void seedPodFamiliesShareTheGrandTreeOption()
	{
		Assert.assertTrue(familyNamed("Royal seed pod").options()
			.containsValue("Grand Tree"));
		Assert.assertTrue(familyNamed("Grand seed pod").options()
			.containsValue("Grand Tree"));
	}

	/**
	 * An option record naming a group's canonical destination expands to
	 * every destination in the group, so the policy gate sees literal tiles.
	 */
	@Test
	public void expandOptionDestsExpandsACanonicalDestToItsGroup()
	{
		int outside = shortestpath.WorldPointUtil.packWorldPoint(1248, 3719, 0);
		int inside = shortestpath.WorldPointUtil.packWorldPoint(1248, 3725, 0);

		Map<Integer, Set<Integer>> expanded = TeleportRestriction.expandOptionDests(
			Map.of(11105, Set.of(outside)));

		Assert.assertEquals(Set.of(outside, inside), expanded.get(11105));
	}

	/**
	 * A stale record naming a non-canonical member of the group resolves to
	 * the same full group — TSV reordering can never orphan a stored block.
	 */
	@Test
	public void expandOptionDestsExpandsANonCanonicalMemberDestToTheSameGroup()
	{
		int outside = shortestpath.WorldPointUtil.packWorldPoint(1248, 3719, 0);
		int inside = shortestpath.WorldPointUtil.packWorldPoint(1248, 3725, 0);

		Map<Integer, Set<Integer>> expanded = TeleportRestriction.expandOptionDests(
			Map.of(11105, Set.of(inside)));

		Assert.assertEquals(Set.of(outside, inside), expanded.get(11105));
	}

	/**
	 * Records naming an unknown rep, or a rep whose dest belongs to no option
	 * group, pass through as literal blocked tiles — hand-edited or stale CSV
	 * degrades harmlessly rather than dropping the block.
	 */
	@Test
	public void expandOptionDestsPassesUnknownRepsAndDestsThrough()
	{
		Map<Integer, Set<Integer>> expanded = TeleportRestriction.expandOptionDests(
			Map.of(99999, Set.of(12345), 11105, Set.of(54321)));

		Assert.assertEquals(Set.of(12345), expanded.get(99999));
		Assert.assertEquals(Set.of(54321), expanded.get(11105));
	}

	/**
	 * The catch-all {@code # Other} bucket is split so every item family
	 * carries its own name in the restriction checklist — the blocker UI
	 * groups by family, so a shared bucket hid nine unrelated items under
	 * one meaningless heading.
	 */
	@Test
	public void theOtherBucketIsSplitIntoNamedItemFamilies()
	{
		Set<String> names = new HashSet<>();
		for (TeleportRestriction.Family family : TeleportRestriction.loadFamilies())
		{
			names.add(family.name());
		}
		Assert.assertFalse("no catch-all 'Other' family", names.contains("Other"));
		for (String expected : new String[]{
			"Pharaoh's sceptre", "Skull sceptre", "Xeric's talisman", "Chronicle",
			"Amulet of the eye", "Ring of the elements", "Hallowed crystal shard",
			"Ring of shadows", "Pendant of ates"})
		{
			Assert.assertTrue("family '" + expected + "' is its own checklist group",
				names.contains(expected));
		}
	}

	/**
	 * The charged Skull sceptre (9013) and its uncharged variant (21276)
	 * are one item for restriction purposes — toggling one must cover both,
	 * so they stay members of a single family.
	 */
	@Test
	public void skullSceptreGroupsBothItemIdsUnderOneFamily()
	{
		TeleportRestriction.Family sceptre = familyNamed("Skull sceptre");
		Assert.assertEquals(Set.of(9013, 21276), sceptre.memberItemIds());
		Assert.assertEquals(9013, sceptre.displayItemId());
	}

	/**
	 * A family's rep is its lowest member id — the checklist's display icon
	 * and every stored rep-{@code @}-dest record key off it.
	 */
	@Test
	public void splitFamiliesUseTheirLowestMemberIdAsTheDisplayId()
	{
		Map<String, Integer> expectedReps = Map.of(
			"Pharaoh's sceptre", 26948,
			"Xeric's talisman", 13393,
			"Chronicle", 13660,
			"Amulet of the eye", 26914,
			"Ring of the elements", 26818,
			"Hallowed crystal shard", 24709,
			"Ring of shadows", 28327,
			"Pendant of ates", 29893);
		for (Map.Entry<String, Integer> entry : expectedReps.entrySet())
		{
			TeleportRestriction.Family family = familyNamed(entry.getKey());
			Assert.assertEquals(entry.getKey() + " reps by its lowest member id",
				(int) entry.getValue(), family.displayItemId());
		}
	}

	/**
	 * The injected fairy-ring staff requirement is player-restrictable like an
	 * item family: the checklist's code-defined family must cover every staff
	 * variant the injected requirement accepts — the same ItemVariations name —
	 * and blocking the whole set must leave the ring requirement with no
	 * unblocked alternative while blocking a proper subset keeps it routable.
	 */
	@Test
	public void fairyStaffFamilyCoversEveryInjectedStaffVariant()
	{
		TeleportRestriction.Family staves = TeleportRestriction.fairyStaffFamily();

		Set<Integer> injected = new HashSet<>();
		for (int id : ItemVariations.fromName("DRAMEN_STAFF").getIds())
		{
			injected.add(id);
		}
		Assert.assertEquals("the family covers every staff id the ring requirement accepts",
			injected, staves.memberItemIds());
		Assert.assertTrue("displayItemId is a member",
			staves.memberItemIds().contains(staves.displayItemId()));
		for (int id : staves.memberItemIds())
		{
			Assert.assertFalse("member id " + id + " has a non-empty label",
				staves.memberLabels().getOrDefault(id, "").isEmpty());
		}

		// The injected requirement's shape — one AND entry whose OR branches
		// are the staff variants, the same construction
		// TransportEligibility#wrapFairyRingStaff merges onto the ring.
		TransportItems ringRequirement = new TransportItems(
			new int[][]{null},
			new int[][]{ItemVariations.fromName("DRAMEN_STAFF").getIds()},
			new int[][]{null},
			new int[]{1});
		Assert.assertFalse("blocking every staff variant disables the ring",
			ringRequirement.survivesBlockedItems(staves.memberItemIds()));
		Set<Integer> partial = new HashSet<>(staves.memberItemIds());
		partial.remove(partial.iterator().next());
		Assert.assertTrue("a surviving staff variant keeps the ring routable",
			ringRequirement.survivesBlockedItems(partial));
	}

	/**
	 * The staff family is checklist-only: the generated map's invariant —
	 * every member id traces to a {@code teleportation_items.tsv} Items
	 * cell — excludes it, so {@link TeleportRestriction#loadFamilies()} must
	 * not return it while {@link TeleportRestriction#checklistFamilies()}
	 * must add exactly it.
	 */
	@Test
	public void fairyStaffFamilyIsChecklistOnlyNotGenerated()
	{
		Set<Integer> staffIds = TeleportRestriction.fairyStaffFamily().memberItemIds();
		for (TeleportRestriction.Family family : TeleportRestriction.loadFamilies())
		{
			Assert.assertTrue("generated family '" + family.name()
				+ "' owns no fairy staff id",
				Collections.disjoint(family.memberItemIds(), staffIds));
		}

		List<TeleportRestriction.Family> checklist = TeleportRestriction.checklistFamilies();
		Assert.assertEquals("the checklist adds exactly one code-defined family",
			TeleportRestriction.loadFamilies().size() + 1, checklist.size());
		Assert.assertTrue("the added family is the fairy staves",
			checklist.stream().anyMatch(f -> f.name().equals("Fairy staves")));
	}

	private static TeleportRestriction.Family familyNamed(String name)
	{
		for (TeleportRestriction.Family family : TeleportRestriction.loadFamilies())
		{
			if (family.name().equals(name))
			{
				return family;
			}
		}
		throw new AssertionError("no restriction family named " + name);
	}
}
