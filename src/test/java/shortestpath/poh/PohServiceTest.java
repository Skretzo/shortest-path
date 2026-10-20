package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import shortestpath.WorldPointUtil;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.widgets.GameWidgets;

public class PohServiceTest
{
	private static final int LANDING = WorldPointUtil.packWorldPoint(1858, 7051, 0);

	private PohService service;
	private RefreshCoordinator coordinator;

	@Before
	public void setUp()
	{
		coordinator = mock(RefreshCoordinator.class);
		service = PohService.forTesting(coordinator);
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
		Set<PohMountedItem> allItems = new HashSet<>(java.util.Arrays.asList(PohMountedItem.values()));

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
		Set<PohMountedItem> allItems = new HashSet<>(java.util.Arrays.asList(PohMountedItem.values()));

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
		Set<PohMountedItem> allItems = new HashSet<>(java.util.Arrays.asList(PohMountedItem.values()));

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
		service.putFromDialogLine("9: Lumbridge");
		service.putFromDialogLine("F12: Lunar Isle");
		service.putFromDialogLine("F13: Catherby");
		service.putFromDialogLine("Shift+F3: Ourania");
		service.putFromDialogLine("Waterbirth Island");

		// Only the three keyed lines declare — unsupported or unkeyed
		// lines carry no keybind information and admit nothing.
		verify(coordinator, times(3)).pohChanged(any());

		assertEquals("F3: Varrock Portal", service.apply("Varrock Portal"));
		assertEquals("9: Lumbridge Portal", service.apply("Lumbridge Portal"));
		assertEquals("F12: Lunar Isle Portal", service.apply("Lunar Isle Portal"));
		assertEquals("Catherby Portal", service.apply("Catherby Portal"));
		assertEquals("Ourania Portal", service.apply("Ourania Portal"));
		assertEquals("Waterbirth Island Portal", service.apply("Waterbirth Island Portal"));
	}

	@Test
	public void testDialogKeybindLearnEmitsDisplayOnlyFact()
	{
		service.putFromDialogLine("Y: Lassar");

		ArgumentCaptor<PohChange> captor = ArgumentCaptor.forClass(PohChange.class);
		verify(coordinator).pohChanged(captor.capture());
		assertEquals("dialogLine", captor.getValue().getKey());
		assertEquals(Set.of(Effect.DISPLAY_ONLY), captor.getValue().getEffects());
	}

	/**
	 * A widgets seam answering the given packed-id rows and nothing for
	 * every other id — the dialog reads collapse to ordered string
	 * fixtures.
	 */
	private static GameWidgets stubDialogRows(Map<Integer, List<String>> rows)
	{
		GameWidgets widgets = mock(GameWidgets.class);
		when(widgets.selfAndChildTexts(anyInt())).thenReturn(List.of());
		rows.forEach((id, texts) -> when(widgets.selfAndChildTexts(id)).thenReturn(texts));
		return widgets;
	}

	@Test
	public void testRefreshFromDialogReturnsWhetherItParsed()
	{
		// The dialog-retry predicate consumes the boolean as "something
		// parsed", matching the old non-null fact condition.
		GameWidgets widgets = stubDialogRows(Map.of(
			InterfaceID.TelenexusTeleport.TEXT1, List.of("Y: Lassar")));
		PohService dialog = new PohService(null, coordinator, widgets);

		assertTrue("a parseable dialog reports a mapping", dialog.refreshFromDialog());
		verify(coordinator).pohChanged(any());
		assertEquals("Y: Lassar Portal", dialog.apply("Lassar Portal"));

		PohService empty = new PohService(null, coordinator, stubDialogRows(Map.of()));
		assertFalse("no keyed widgets means nothing parsed", empty.refreshFromDialog());
		verify(coordinator, times(1)).pohChanged(any());
	}

	@Test
	public void testRefreshFromDialogReadsSelfTextBeforeChildren()
	{
		// The container's own text leads the row list. A child row
		// colliding on the same normalized name overwrites it, so the
		// last-wins outcome proves the self-first ordering held.
		GameWidgets widgets = stubDialogRows(Map.of(
			InterfaceID.TelenexusTeleport.TEXT1, List.of("Y: Lassar", "3: Lassar")));
		PohService dialog = new PohService(null, coordinator, widgets);

		assertTrue(dialog.refreshFromDialog());
		assertEquals("3: Lassar Portal", dialog.apply("Lassar Portal"));
	}

	@Test
	public void testRefreshFromDialogFallsBackToConfigurationSlots()
	{
		// When the teleport menu yields nothing, the configuration slot
		// list supplies the keyed names — and its header rows are ignored.
		GameWidgets widgets = stubDialogRows(Map.of(
			InterfaceID.Telenexus.SLOTTED_LIST, List.of("Slots", "4: Camelot")));
		PohService dialog = new PohService(null, coordinator, widgets);

		assertTrue(dialog.refreshFromDialog());
		assertEquals("4: Camelot Portal", dialog.apply("Camelot Portal"));
	}

	@Test
	public void testRefreshFromDialogStaysTrueOnAnIdenticalReRead()
	{
		// An unchanged re-read still produces a fact — its effect set is
		// empty, so the coordinator's own guard decides nothing runs — but
		// the boolean must stay true or the retry loop would spin forever.
		GameWidgets widgets = stubDialogRows(Map.of(
			InterfaceID.TelenexusTeleport.TEXT1, List.of("Y: Lassar")));
		PohService dialog = new PohService(null, coordinator, widgets);

		assertTrue(dialog.refreshFromDialog());
		assertTrue(dialog.refreshFromDialog());
		verify(coordinator, times(2)).pohChanged(any());
	}

	@Test
	public void testPersistedFunctionKeysAreRetained()
	{
		Map<String, String> keys = new HashMap<>();
		PohService.deserialize("2|lassar=Y|waterbirth island=F3", keys);

		assertEquals(Map.of("lassar", "Y", "waterbirth island", "F3"), keys);
	}
}
