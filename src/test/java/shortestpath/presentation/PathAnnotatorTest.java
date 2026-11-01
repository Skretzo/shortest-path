package shortestpath.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import net.runelite.api.ItemContainer;

import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.items.ItemStateService;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.BankPickupAnalysis;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.poh.PohService;
import shortestpath.presentation.BankPickupRequirements.BankPickupResult;
import shortestpath.requirement.TransportEligibility;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class PathAnnotatorTest
{
	private static final int LANDING = WorldPointUtil.packWorldPoint(1858, 7051, 0);

	private PohService pohService;
	private PathAnnotator annotator;

	@Before
	public void setUp()
	{
		pohService = PohService.forTesting();
		annotator = PathAnnotator.forTesting(pohService, null);
	}

	private static Transport transport(TransportType type, String displayInfo, String objectInfo, int destination)
	{
		return new Transport.TransportBuilder()
			.type(type)
			.displayInfo(displayInfo)
			.objectInfo(objectInfo)
			.destination(destination)
			.build();
	}

	@Test
	public void testFormatTransportDisplay()
	{
		pohService.putFromDialogLine("3: Lumbridge");

		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);
		assertEquals("3: Lumbridge Portal", annotator.formatTransportDisplay(portal));

		// Non-portal transports pass through untouched, keybinds or not.
		Transport ring = transport(TransportType.FAIRY_RING, "DIQ", null, 0);
		assertEquals("DIQ", annotator.formatTransportDisplay(ring));

		Transport unnamed = transport(TransportType.FAIRY_RING, null, null, 0);
		assertNull(annotator.formatTransportDisplay(unnamed));
	}

	@Test
	public void testGetPohExitInfo()
	{
		int inside1 = WorldPointUtil.packWorldPoint(1900, 7060, 0);
		int inside2 = WorldPointUtil.packWorldPoint(1901, 7060, 0);
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);

		// The exit step records the transport the search used, which is what
		// transportsForEdge resolves for the boundary edge — no engine config
		// needed.
		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);
		List<PathStep> path = List.of(
			new PathStep(outside, BankVisitState.CARRIED),
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, portal));
		assertEquals("Nexus: Lumbridge Portal",
			annotator.getPohExitInfo(LANDING, path, 0));

		Transport glory = transport(TransportType.TELEPORTATION_BOX, "Edgeville", "Mounted Amulet of Glory", 0);
		List<PathStep> gloryPath = List.of(
			new PathStep(outside, BankVisitState.CARRIED),
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, glory));
		assertEquals("Mounted Glory: Edgeville",
			annotator.getPohExitInfo(LANDING, gloryPath, 0));
	}

	@Test
	public void testGetPohExitInfoFirstEdgeTeleport()
	{
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);

		// A route that leaves the POH on its first edge arrives here with
		// currentIndex = -1: the overlay passes pathIndex - 1 so the scan
		// starts at the current edge, and global teleports are always
		// first-edge exits because their abstract node hangs off the start
		// tile. The guard must admit exactly -1.
		Transport pod = transport(TransportType.TELEPORTATION_ITEM, "Grand seed pod", null, 0);
		List<PathStep> path = List.of(
			new PathStep(LANDING, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, pod));
		assertEquals("Grand seed pod",
			annotator.getPohExitInfo(LANDING, path, -1));
	}

	@Test
	public void testGetPohExitInfoFirstEdgePortal()
	{
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);

		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);
		List<PathStep> path = List.of(
			new PathStep(LANDING, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, portal));
		assertEquals("Nexus: Lumbridge Portal",
			annotator.getPohExitInfo(LANDING, path, -1));
	}

	@Test
	public void testGetPohExitInfoRejectedInputs()
	{
		int inside1 = WorldPointUtil.packWorldPoint(1900, 7060, 0);
		int inside2 = WorldPointUtil.packWorldPoint(1901, 7060, 0);
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);
		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);

		// A non-POH destination produces no exit info.
		List<PathStep> path = List.of(
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, portal));
		assertNull(annotator.getPohExitInfo(outside, path, 0));

		assertNull(annotator.getPohExitInfo(LANDING, null, 0));
		// -1 is admitted (scan from the first edge); only values below it
		// are rejected.
		assertNull(annotator.getPohExitInfo(LANDING, path, -2));

		// A path that never crosses the boundary produces no exit info.
		List<PathStep> allInside = List.of(
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(inside1, BankVisitState.CARRIED));
		assertNull(annotator.getPohExitInfo(LANDING, allInside, 0));
	}

	// ---------------------------------------------------------------
	// POH exit label tables — every table entry is pinned literally so
	// the table conversion provably preserves the chain's output.
	// ---------------------------------------------------------------

	@Test
	public void objInfoLabelTablePinsEveryPairInFirstMatchOrder()
	{
		// Order is load-bearing: the first objInfo.contains(match) wins.
		assertEquals(4, PathAnnotator.OBJINFO_LABELS.size());
		assertEquals(List.of("Amulet of Glory", "Mounted Glory: "),
			PathAnnotator.OBJINFO_LABELS.get(0));
		assertEquals(List.of("Mythical cape", "Mythical Cape: "),
			PathAnnotator.OBJINFO_LABELS.get(1));
		assertEquals(List.of("Xeric's Talisman", "Xeric's Talisman: "),
			PathAnnotator.OBJINFO_LABELS.get(2));
		assertEquals(List.of("Digsite", "Digsite Pendant: "),
			PathAnnotator.OBJINFO_LABELS.get(3));
		assertEquals("Jewelry Box: ", PathAnnotator.OBJINFO_FALLBACK_LABEL);
	}

	@Test
	public void exitPrefixTablePinsEveryTypeAndThePassthrough()
	{
		assertEquals("Nexus: ",
			PathAnnotator.EXIT_PREFIX.get(TransportType.TELEPORTATION_PORTAL_POH));
		// The missing colon is upstream display text, not a typo — pinned
		// literally so it cannot be normalized to "Fairy Ring: ".
		assertEquals("Fairy Ring ",
			PathAnnotator.EXIT_PREFIX.get(TransportType.FAIRY_RING));
		assertEquals("Spirit Tree: ",
			PathAnnotator.EXIT_PREFIX.get(TransportType.SPIRIT_TREE));
		assertEquals("Obelisk: ",
			PathAnnotator.EXIT_PREFIX.get(TransportType.WILDERNESS_OBELISK));
		// The teleportation box takes the objInfo arm, not this table.
		assertNull(PathAnnotator.EXIT_PREFIX.get(TransportType.TELEPORTATION_BOX));
		// Unlisted types pass the exit info through unprefixed.
		assertNull(PathAnnotator.EXIT_PREFIX.get(TransportType.TELEPORTATION_ITEM));
	}

	@Test
	public void testGetPohExitInfoLabelArms()
	{
		// Every objInfo pair plus the null/unmatched fallback, end to end.
		assertEquals("Mythical Cape: Falador", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.TELEPORTATION_BOX,
				"Falador", "Mounted Mythical cape", 0)), 0));
		assertEquals("Xeric's Talisman: Xeric's Heart", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.TELEPORTATION_BOX,
				"Xeric's Heart", "Xeric's Talisman", 0)), 0));
		assertEquals("Digsite Pendant: Digsite", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.TELEPORTATION_BOX,
				"Digsite", "Digsite pendant", 0)), 0));
		assertEquals("Jewelry Box: Al Kharid", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.TELEPORTATION_BOX,
				"Al Kharid", "Basic Jewellery Box 37492", 0)), 0));
		assertEquals("Jewelry Box: Home", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.TELEPORTATION_BOX,
				"Home", null, 0)), 0));

		// First-match order: an objInfo matching two pairs takes the earlier.
		assertEquals("Mounted Glory: Edgeville", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.TELEPORTATION_BOX,
				"Edgeville", "Amulet of Glory Digsite", 0)), 0));

		// Every exitType pair plus the passthrough arm, end to end.
		assertEquals("Fairy Ring DIQ", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.FAIRY_RING, "DIQ", null, 0)), 0));
		assertEquals("Spirit Tree: Tree Gnome Village", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.SPIRIT_TREE,
				"Tree Gnome Village", null, 0)), 0));
		assertEquals("Obelisk: Level 44", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.WILDERNESS_OBELISK,
				"Level 44", null, 0)), 0));
		assertEquals("Grand seed pod", annotator.getPohExitInfo(LANDING,
			pohExitPath(transport(TransportType.TELEPORTATION_ITEM,
				"Grand seed pod", null, 0)), 0));
	}

	private static List<PathStep> pohExitPath(Transport exitTransport)
	{
		int inside1 = WorldPointUtil.packWorldPoint(1900, 7060, 0);
		int inside2 = WorldPointUtil.packWorldPoint(1901, 7060, 0);
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);
		return List.of(
			new PathStep(outside, BankVisitState.CARRIED),
			new PathStep(inside1, BankVisitState.CARRIED),
			new PathStep(inside2, BankVisitState.CARRIED),
			new PathStep(outside, BankVisitState.CARRIED, exitTransport));
	}

	// ---------------------------------------------------------------
	// Display facades — detached arm and attached delegation
	// ---------------------------------------------------------------

	@Test
	public void detachedAnnotatorAnswersEveryDisplayReadWithTheEmptyArm()
	{
		PathAnnotator detached = PathAnnotator.forTesting();

		assertNull(detached.getMap());
		assertNull(detached.getTransports());
		assertNull(detached.getAllDisplayTransports());
		assertNull(detached.getEligibility());
		assertNull(detached.getDestinations("fairy_ring"));
		assertFalse(detached.hasDestination("fairy_ring"));
		assertFalse(detached.isBankOpen());
		assertNull(detached.getBankPickup(List.of(), 0, null));
	}

	@Test
	public void displayFacadesDelegateToTheAttachedConfig()
	{
		PathfinderConfig config = mock(PathfinderConfig.class);
		CollisionMap map = mock(CollisionMap.class);
		PrimitiveIntHashMap<Transport[]> transports = new PrimitiveIntHashMap<>(4);
		PrimitiveIntHashMap<Transport[]> allTransports = new PrimitiveIntHashMap<>(4);
		// TransportEligibility is a final value type — a real empty snapshot
		// proves the pass-through just as well as a mock would.
		TransportEligibility eligibility =
			TransportEligibility.forPlayerAndBank(Map.of(), Map.of(), 0, Map.of());
		Set<Integer> destinations = Set.of(1, 2);
		when(config.getMap()).thenReturn(map);
		when(config.getTransports()).thenReturn(transports);
		when(config.getAllDisplayTransports()).thenReturn(allTransports);
		when(config.getEligibility()).thenReturn(eligibility);
		when(config.getDestinations("fairy_ring")).thenReturn(destinations);
		when(config.hasDestination("fairy_ring")).thenReturn(true);

		annotator.attach(config);

		assertSame(map, annotator.getMap());
		assertSame(transports, annotator.getTransports());
		assertSame(allTransports, annotator.getAllDisplayTransports());
		assertSame(eligibility, annotator.getEligibility());
		assertSame(destinations, annotator.getDestinations("fairy_ring"));
		assertTrue(annotator.hasDestination("fairy_ring"));
	}

	@Test
	public void isBankOpenReadsItemStateAndBankPickupReadsConfig()
	{
		ItemStateService itemState = mock(ItemStateService.class);
		PathfinderConfig config = mock(PathfinderConfig.class);
		PathAnnotator wired = PathAnnotator.forTesting(pohService, itemState);
		wired.attach(config);

		assertFalse(wired.isBankOpen());

		ItemContainer bank = mock(ItemContainer.class);
		when(itemState.getBank()).thenReturn(bank);
		assertTrue(wired.isBankOpen());

		List<PathStep> path = List.of(new PathStep(LANDING, BankVisitState.CARRIED));
		BankPickupAnalysis analysis = new BankPickupAnalysis(
			List.of(List.of(Map.of(954, 1L))), Set.of(954));
		when(config.getBankPickup(path, 0, null)).thenReturn(analysis);

		BankPickupResult result = wired.getBankPickup(path, 0, null);
		assertNotNull(result);
		assertEquals(Set.of(954), result.bankItemIds);
		assertEquals("the raw pickup map is formatted into a phrase",
			List.of("1 Unknown item"), result.phrases);
	}

	// ---------------------------------------------------------------
	// Missing-items label — one short entry per item group
	// ---------------------------------------------------------------

	/** The label of one item group whose ids 0, 1, 2, ... have these names (null: no name). */
	private static String groupLabel(String... names)
	{
		List<Integer> ids = new ArrayList<>();
		for (int id = 0; id < names.length; id++)
		{
			ids.add(id);
		}
		return PathAnnotator.itemGroupLabel(ids, id -> names[id]);
	}

	/** An item group as a plugin message decodes it: one branch per item id. */
	private static ItemRequirement group(int... ids)
	{
		List<ItemRequirement.Branch> branches = new ArrayList<>();
		for (int id : ids)
		{
			branches.add(new ItemRequirement.Branch(new int[] {id}, new int[0], new int[0], 1));
		}
		return new ItemRequirement(branches);
	}

	@Test
	public void itemGroupLabelFoldsTheVariantsOfAnItem()
	{
		assertEquals("any Combat bracelet", groupLabel("Combat bracelet(4)", "Combat bracelet(3)",
			"Combat bracelet(2)", "Combat bracelet(1)", "Combat bracelet", "Combat bracelet(6)", "Combat bracelet(5)"));
		assertEquals("any Ahrim's hood", groupLabel("Ahrim's hood", "Ahrim's hood 100", "Ahrim's hood 75",
			"Ahrim's hood 50", "Ahrim's hood 25", "Ahrim's hood 0"));
		assertEquals("any Slayer helmet", groupLabel("Slayer helmet", "Slayer helmet (i)", "Black slayer helmet",
			"Twisted slayer helmet (i)"));
		assertEquals("any Logs", groupLabel("Logs", "Achey tree logs", "Oak logs", "Willow logs", "Teak logs",
			"Arctic pine logs", "Maple logs", "Mahogany logs", "Yew logs", "Camphor logs", "Magic logs",
			"Ironwood logs", "Redwood logs", "Rosewood logs"));
		// Charges with no uncharged item in the group fold by the count at the end of the name.
		assertEquals("any Ring of dueling", groupLabel("Ring of dueling(1)", "Ring of dueling(2)",
			"Ring of dueling(3)", "Ring of dueling(4)", "Ring of dueling(5)", "Ring of dueling(6)",
			"Ring of dueling(7)", "Ring of dueling(8)"));
	}

	@Test
	public void itemGroupLabelNamesOneItemOnce()
	{
		assertEquals("Spade", groupLabel("Spade"));
		// An item and its noted copy share a name.
		assertEquals("Tinderbox", groupLabel("Tinderbox", "Tinderbox"));
		// A lone item keeps its count.
		assertEquals("Ring of dueling(8)", groupLabel("Ring of dueling(8)", "Ring of dueling(8)"));
	}

	@Test
	public void itemGroupLabelJoinsDifferentItemsWithOr()
	{
		assertEquals("Rope or Knife", groupLabel("Rope", "Knife"));
		assertEquals("Mirror shield or V's shield", groupLabel("Mirror shield", "V's shield", "Mirror shield"));
		assertEquals("Nose peg or any Slayer helmet", groupLabel("Nose peg", "Slayer helmet", "Slayer helmet (i)",
			"Black slayer helmet", "Black slayer helmet (i)", "Twisted slayer helmet", "Twisted slayer helmet (i)",
			"Nose peg"));
		// Only whole words make a variant: an unholy book is not a holy book.
		assertEquals("Holy book or Unholy book", groupLabel("Holy book", "Unholy book"));
		// Only a count in brackets is dropped: these shards are three different items.
		assertEquals("Godsword shard 1 or Godsword shard 2 or Godsword shard 3",
			groupLabel("Godsword shard 1", "Godsword shard 2", "Godsword shard 3"));
	}

	@Test
	public void itemGroupLabelCallsALongListByTheWordsItsEntriesEndIn()
	{
		assertEquals("any axe", groupLabel("Bronze axe", "Iron axe", "Steel axe", "Black axe", "Mithril axe",
			"Adamant axe", "Rune axe", "Dragon axe", "Dragon axe (or)", "Crystal axe", "Crystal axe (inactive)",
			"Bronze felling axe", "Iron felling axe", "Steel felling axe", "Black felling axe", "Mithril felling axe",
			"Adamant felling axe", "Rune felling axe", "Dragon felling axe", "Crystal felling axe",
			"Crystal felling axe (inactive)", "Blessed axe", "Gilded axe", "Infernal axe", "Infernal axe (uncharged)",
			"Infernal axe (or)", "3rd Age axe", "3rd Age felling axe"));
		assertEquals("any slayer helmet", groupLabel("Black slayer helmet", "Green slayer helmet",
			"Red slayer helmet", "Purple slayer helmet", "Turquoise slayer helmet", "Hydra slayer helmet (i)"));
		// Up to MAX_LISTED_ENTRIES entries stay listed: these are not any cape.
		assertEquals("any Fire cape or any Fire max cape or any Infernal cape or any Infernal max cape",
			groupLabel("Fire cape", "Fire cape (l)", "Fire max cape", "Fire max cape (l)", "Infernal cape",
				"Infernal cape (l)", "Infernal max cape", "Infernal max cape (l)"));
		// Entries that end in different words stay listed.
		assertEquals("Lit candle or Lit torch or Oil lamp or Candle lantern or Oil lantern or Mining helmet",
			groupLabel("Lit candle", "Lit torch", "Oil lamp", "Candle lantern", "Oil lantern", "Mining helmet"));
	}

	@Test
	public void itemGroupLabelListsIdsWithoutANameLast()
	{
		assertEquals("Spade or Item 0 or Item 2", groupLabel(null, "Spade", null));
		// Placeholders never fold: two unnamed ids are two items.
		assertEquals("Item 0 or Item 1", groupLabel(null, null));
	}

	@Test
	public void missingItemsLabelJoinsGroupsAndNotesAnUncheckedBank()
	{
		Map<Integer, String> names = Map.of(952, "Spade", 954, "Rope", 946, "Knife",
			11118, "Combat bracelet(4)", 11126, "Combat bracelet", 11972, "Combat bracelet(6)");
		List<ItemRequirement> missing = List.of(group(952), group(954, 946), group(11118, 11126, 11972));

		assertEquals("Not found in inventory or bank: Spade, Rope or Knife, any Combat bracelet",
			PathAnnotator.missingItemsLabel(missing, names::get, true));
		assertEquals("Not found in inventory or bank: Spade (bank not checked)",
			PathAnnotator.missingItemsLabel(List.of(group(952)), names::get, false));
	}
}
