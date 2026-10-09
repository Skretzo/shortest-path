package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.settings.Effect;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

public class PohServiceTest
{
	private static final int LANDING = WorldPointUtil.packWorldPoint(1858, 7051, 0);

	private PohService service;

	@Before
	public void setUp()
	{
		service = PohService.forTesting();
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
	public void testPohBoundsAreInclusive()
	{
		assertTrue(PohService.isInsidePoh(1856, 7040));
		assertTrue(PohService.isInsidePoh(2047, 7111));
		assertTrue(PohService.isInsidePoh(1858, 7051));
	}

	@Test
	public void testPohBoundsAreExclusive()
	{
		// 1855 is excluded so the Daddy's Home miniquest area stays outside.
		assertFalse(PohService.isInsidePoh(1855, 7040));
		assertFalse(PohService.isInsidePoh(2048, 7051));
		assertFalse(PohService.isInsidePoh(1858, 7039));
		assertFalse(PohService.isInsidePoh(1858, 7112));
	}

	@Test
	public void testRemapPohDestinations()
	{
		int inside = WorldPointUtil.packWorldPoint(1900, 7060, 0);
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);

		Transport inPoh = transport(TransportType.FAIRY_RING, "DIQ", null, inside);
		Transport atLanding = transport(TransportType.FAIRY_RING, "landing", null, LANDING);
		Transport outPoh = transport(TransportType.FAIRY_RING, "outside", null, outside);

		Map<Integer, Set<Transport>> transports = new HashMap<>();
		transports.put(LANDING, new HashSet<>(Set.of(inPoh, atLanding, outPoh)));
		PohService.remapPohDestinations(transports);

		assertEquals(LANDING, inPoh.getDestination());
		assertEquals(LANDING, atLanding.getDestination());
		assertEquals(outside, outPoh.getDestination());
	}

	@Test
	public void testRemapPohDestinationsEmptyMapIsNoOp()
	{
		Map<Integer, Set<Transport>> transports = new HashMap<>();
		PohService.remapPohDestinations(transports);
		assertTrue(transports.isEmpty());
	}

	@Test
	public void testNexusPortalEnablement()
	{
		Set<PohNexusPortal> enabled = Set.of(PohNexusPortal.LUMBRIDGE);

		assertTrue(PohService.isNexusPortalEnabled(enabled, "Lumbridge Portal"));
		assertFalse(PohService.isNexusPortalEnabled(enabled, "Varrock Portal"));
		assertTrue(PohService.isNexusPortalEnabled(enabled, null));
		assertTrue(PohService.isNexusPortalEnabled(enabled, "Nonexistent Portal"));
	}

	@Test
	public void testMountedItemEnablement()
	{
		Set<PohMountedItem> enabled = Set.of(PohMountedItem.GLORY);

		assertTrue(PohService.isMountedItemEnabled(enabled, "Amulet of Glory 29192"));
		assertFalse(PohService.isMountedItemEnabled(enabled, "Mythical cape 22114"));
		assertTrue(PohService.isMountedItemEnabled(enabled, "Completely Unknown Object"));
		assertTrue(PohService.isMountedItemEnabled(enabled, null));
	}

	@Test
	public void testJewelleryBoxTiers()
	{
		Transport basic = transport(TransportType.TELEPORTATION_BOX, "Edgeville", "Basic Jewellery Box 37492", 0);
		Transport fancy = transport(TransportType.TELEPORTATION_BOX, "Pollnivneach", "Fancy Jewellery Box 37501", 0);
		Transport ornate = transport(TransportType.TELEPORTATION_BOX, "Mount Quidamortem", "Ornate Jewellery Box 37520", 0);
		Set<PohMountedItem> allItems = EnumSet.allOf(PohMountedItem.class);

		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.NONE, allItems, basic));
		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.NONE, allItems, fancy));
		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.NONE, allItems, ornate));

		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.BASIC, allItems, basic));
		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.BASIC, allItems, fancy));
		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.BASIC, allItems, ornate));

		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.FANCY, allItems, basic));
		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.FANCY, allItems, fancy));
		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.FANCY, allItems, ornate));

		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.ORNATE, allItems, basic));
		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.ORNATE, allItems, fancy));
		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.ORNATE, allItems, ornate));
	}

	@Test
	public void testJewelleryBoxMountedItems()
	{
		Set<PohMountedItem> enabled = Set.of(PohMountedItem.GLORY);
		Set<PohMountedItem> allItems = EnumSet.allOf(PohMountedItem.class);

		Transport glory = transport(TransportType.TELEPORTATION_BOX, "Edgeville", "Amulet of Glory 29192", 0);
		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.BASIC, enabled, glory));
		// The ornate box covers the mounted glory's destinations, so the glory
		// is suppressed at the ORNATE tier regardless of enablement.
		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.ORNATE, enabled, glory));

		Transport xerics = transport(TransportType.TELEPORTATION_BOX, "Xeric's Lookout", "Xeric's Talisman 13393", 0);
		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.BASIC, enabled, xerics));

		// Mounted items dispatch on enablement, not on the jewellery-box tier.
		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.NONE, allItems, xerics));
		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.NONE, allItems,
			transport(TransportType.TELEPORTATION_BOX, "Digsite", "Digsite Pendant 11190", 0)));
		assertTrue(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.NONE, allItems,
			transport(TransportType.TELEPORTATION_BOX, "Guild", "Mythical cape 22114", 0)));
	}

	@Test
	public void testJewelleryBoxEdgeCases()
	{
		Set<PohMountedItem> allItems = EnumSet.allOf(PohMountedItem.class);

		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.ORNATE, allItems,
			transport(TransportType.TELEPORTATION_BOX, "nowhere", null, 0)));
		assertFalse(PohService.jewelleryBoxSatisfied(JewelleryBoxTier.ORNATE, allItems,
			transport(TransportType.TELEPORTATION_BOX, "nowhere", "Unrecognised Object 99999", 0)));
	}

	@Test
	public void testVariantEnabled()
	{
		Set<PohNexusPortal> lumbridge = Set.of(PohNexusPortal.LUMBRIDGE);

		Transport fairy = transport(TransportType.FAIRY_RING, "DIQ", null, 0);
		assertTrue(PohService.variantEnabled(fairy, true, false, false, lumbridge));
		assertFalse(PohService.variantEnabled(fairy, false, true, true, lumbridge));

		Transport tree = transport(TransportType.SPIRIT_TREE, "Your house", null, 0);
		assertTrue(PohService.variantEnabled(tree, false, true, false, lumbridge));
		assertFalse(PohService.variantEnabled(tree, true, false, true, lumbridge));

		Transport obelisk = transport(TransportType.WILDERNESS_OBELISK, "13", null, 0);
		assertTrue(PohService.variantEnabled(obelisk, false, false, true, lumbridge));
		assertFalse(PohService.variantEnabled(obelisk, true, true, false, lumbridge));

		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);
		assertTrue(PohService.variantEnabled(portal, false, false, false, lumbridge));
		assertFalse(PohService.variantEnabled(portal, true, true, true, Set.of(PohNexusPortal.VARROCK)));

		// Unknown and absent portal names pass — the enabled set only ever
		// disables portals the enum recognises.
		assertTrue(PohService.variantEnabled(
			transport(TransportType.TELEPORTATION_PORTAL_POH, "Nonexistent Portal", null, 0),
			false, false, false, Set.of()));
		assertTrue(PohService.variantEnabled(
			transport(TransportType.TELEPORTATION_PORTAL_POH, null, null, 0),
			false, false, false, Set.of()));

		// Transports without a POH variant always pass.
		assertTrue(PohService.variantEnabled(
			transport(TransportType.AGILITY_SHORTCUT, "climb", null, 0),
			false, false, false, Set.of()));
	}

	@Test
	public void testDialogKeybindsRequireExplicitPrefix()
	{
		service.putFromDialogLine("F3</col>: Varrock");
		service.putFromDialogLine("0: Lumbridge");
		service.putFromDialogLine("F12: Lunar Isle");
		assertNull(service.putFromDialogLine("F13: Catherby"));
		assertNull(service.putFromDialogLine("Shift+F3: Ourania"));
		assertNull(service.putFromDialogLine("Waterbirth Island"));

		assertEquals("F3: Varrock Portal", service.apply("Varrock Portal"));
		assertEquals("0: Lumbridge Portal", service.apply("Lumbridge Portal"));
		assertEquals("F12: Lunar Isle Portal", service.apply("Lunar Isle Portal"));
		assertEquals("Catherby Portal", service.apply("Catherby Portal"));
		assertEquals("Ourania Portal", service.apply("Ourania Portal"));
		assertEquals("Waterbirth Island Portal", service.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testDialogKeybindLearnEmitsDisplayOnlyFact()
	{
		PohChange change = service.putFromDialogLine("Y: Lassar");

		assertEquals("dialogLine", change.getKey());
		assertEquals(Set.of(Effect.DISPLAY_ONLY), change.getEffects());
	}

	@Test
	public void testPersistedFunctionKeysAreEvicted()
	{
		Map<String, String> keys = new HashMap<>();
		PohService.deserialize("lassar=Y|waterbirth island=F3|catherby=0", keys);

		assertEquals(Map.of("lassar", "Y", "catherby", "0"), keys);
	}

	@Test
	public void testFormatTransportDisplay()
	{
		service.putFromDialogLine("3: Lumbridge");

		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);
		assertEquals("3: Lumbridge Portal", service.formatTransportDisplay(portal));

		// Non-portal transports pass through untouched, keybinds or not.
		Transport ring = transport(TransportType.FAIRY_RING, "DIQ", null, 0);
		assertEquals("DIQ", service.formatTransportDisplay(ring));

		Transport unnamed = transport(TransportType.FAIRY_RING, null, null, 0);
		assertNull(service.formatTransportDisplay(unnamed));
	}

	@Test
	public void testGetPohExitInfo()
	{
		int inside1 = WorldPointUtil.packWorldPoint(1900, 7060, 0);
		int inside2 = WorldPointUtil.packWorldPoint(1901, 7060, 0);
		int outside = WorldPointUtil.packWorldPoint(3000, 3300, 0);

		List<PathStep> path = List.of(
			new PathStep(outside, false),
			new PathStep(inside1, false),
			new PathStep(inside2, false),
			new PathStep(outside, false));

		Transport portal = transport(TransportType.TELEPORTATION_PORTAL_POH, "Lumbridge Portal", null, 0);
		assertEquals("Nexus: Lumbridge Portal",
			service.getPohExitInfo(LANDING, path, 0, (a, b) -> Set.of(portal)));

		Transport glory = transport(TransportType.TELEPORTATION_BOX, "Edgeville", "Mounted Amulet of Glory", 0);
		assertEquals("Mounted Glory: Edgeville",
			service.getPohExitInfo(LANDING, path, 0, (a, b) -> Set.of(glory)));
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
			new PathStep(inside1, false),
			new PathStep(inside2, false),
			new PathStep(outside, false));
		assertNull(service.getPohExitInfo(outside, path, 0, (a, b) -> Set.of(portal)));

		assertNull(service.getPohExitInfo(LANDING, null, 0, (a, b) -> Set.of(portal)));
		assertNull(service.getPohExitInfo(LANDING, path, -1, (a, b) -> Set.of(portal)));

		// A path that never crosses the boundary produces no exit info.
		List<PathStep> allInside = List.of(
			new PathStep(inside1, false),
			new PathStep(inside2, false),
			new PathStep(inside1, false));
		assertNull(service.getPohExitInfo(LANDING, allInside, 0, (a, b) -> Set.of(portal)));
	}
}
