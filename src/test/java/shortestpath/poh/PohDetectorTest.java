package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.*;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

public class PohDetectorTest
{
	private static final Set<PohNexusPortal> HAND_PICKED = EnumSet.of(PohNexusPortal.WEISS, PohNexusPortal.BARROWS);

	@Test
	public void noNexusMeansOnlyTheChamber()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_PORTAL_TEAK_ARDOUGNE));
		assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE), PohDetector.housePortals(scan, null, HAND_PICKED));
	}

	@Test
	public void aRemovedNexusDropsItsSavedList()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_PORTAL_TEAK_ARDOUGNE));
		assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE),
			PohDetector.housePortals(scan, EnumSet.of(PohNexusPortal.LUMBRIDGE), HAND_PICKED));
	}

	@Test
	public void anUnreadNexusKeepsTheHandPickedList()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_PORTAL_TEAK_ARDOUGNE, ObjectID.POH_NEXUS_PORTAL_1));
		assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE, PohNexusPortal.WEISS, PohNexusPortal.BARROWS),
			PohDetector.housePortals(scan, null, HAND_PICKED));
	}

	@Test
	public void aReadNexusReplacesIt()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_NEXUS_PORTAL_3));
		assertEquals(EnumSet.of(PohNexusPortal.LUMBRIDGE),
			PohDetector.housePortals(scan, EnumSet.of(PohNexusPortal.LUMBRIDGE), HAND_PICKED));
	}

	@Test
	public void matchingTheHouseTurnsOffWhatItLacks()
	{
		assertFalse(PohDetector.flag(false, true, false));
		assertEquals(JewelleryBoxTier.BASIC,
			PohDetector.boxTier(JewelleryBoxTier.BASIC, JewelleryBoxTier.ORNATE, false));
		assertEquals(EnumSet.of(PohMountedItem.GLORY),
			PohDetector.union(PohMountedItem.class, EnumSet.of(PohMountedItem.GLORY), Set.of()));
	}

	@Test
	public void keepingExtrasOnlyAdds()
	{
		assertTrue(PohDetector.flag(false, true, true));
		assertTrue(PohDetector.flag(true, false, true));
		assertFalse(PohDetector.flag(false, false, true));
		assertEquals(JewelleryBoxTier.ORNATE,
			PohDetector.boxTier(JewelleryBoxTier.BASIC, JewelleryBoxTier.ORNATE, true));
		assertEquals(JewelleryBoxTier.ORNATE,
			PohDetector.boxTier(JewelleryBoxTier.ORNATE, JewelleryBoxTier.BASIC, true));
		assertEquals(JewelleryBoxTier.FANCY,
			PohDetector.boxTier(JewelleryBoxTier.FANCY, null, true));
		assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE, PohNexusPortal.MIND_ALTAR),
			PohDetector.union(PohNexusPortal.class, EnumSet.of(PohNexusPortal.ARDOUGNE),
				EnumSet.of(PohNexusPortal.MIND_ALTAR)));
	}

	@Test
	public void waitsForTheFurnitureToArrive()
	{
		// Loaded at tick 100; furniture still spawning on 101 and 102.
		assertFalse(PohDetector.readyToScan(100, 100, 100));
		assertFalse(PohDetector.readyToScan(101, 100, 101));
		assertFalse(PohDetector.readyToScan(102, 100, 102));
		assertFalse(PohDetector.readyToScan(103, 100, 102));
		assertTrue(PohDetector.readyToScan(104, 100, 102));
	}

	@Test
	public void aQuietHouseIsReadAfterTheSettleTicks()
	{
		assertFalse(PohDetector.readyToScan(101, 100, 50));
		assertTrue(PohDetector.readyToScan(100 + PohDetector.SETTLE_TICKS, 100, 50));
	}

	@Test
	public void constantSpawnsDoNotHoldTheLookForever()
	{
		int tick = 100 + PohDetector.MAX_WAIT_TICKS;
		assertTrue(PohDetector.readyToScan(tick, 100, tick));
	}

	@Test
	public void theOpenMenuListKeepsOneOrder()
	{
		Set<PohNexusPortal> read = new java.util.HashSet<>(List.of(PohNexusPortal.WEISS, PohNexusPortal.ARDOUGNE,
			PohNexusPortal.LUMBRIDGE));
		assertEquals(List.of(PohNexusPortal.ARDOUGNE, PohNexusPortal.LUMBRIDGE, PohNexusPortal.WEISS),
			new java.util.ArrayList<>(PohDetector.sorted(read)));
	}

	@Test
	public void aVisitedHouseGivesItsOwnTeleports()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_SPIRIT_RING, ObjectID.POH_JEWELLERY_BOX_2,
			ObjectID.POH_TROPHY_AMULETOFGLORY_4, ObjectID.POH_PORTAL_TEAK_ARDOUGNE, ObjectID.POH_NEXUS_PORTAL_1));
		java.util.Map<shortestpath.settings.ConfigKey, Object> unread = PohDetector.visitValues(scan, null);
		assertEquals(true, unread.get(shortestpath.settings.ConfigKey.USE_POH));
		assertEquals(true, unread.get(shortestpath.settings.ConfigKey.USE_POH_FAIRY_RING));
		assertEquals(true, unread.get(shortestpath.settings.ConfigKey.USE_POH_SPIRIT_TREE));
		assertEquals(false, unread.get(shortestpath.settings.ConfigKey.USE_POH_OBELISK));
		assertEquals(JewelleryBoxTier.FANCY, unread.get(shortestpath.settings.ConfigKey.POH_JEWELLERY_BOX_TIER));
		assertEquals(EnumSet.of(PohMountedItem.GLORY), unread.get(shortestpath.settings.ConfigKey.POH_MOUNTED_ITEMS));
		// Until its menu is read here the nexus may hold anything, so every destination counts.
		assertEquals(EnumSet.allOf(PohNexusPortal.class), unread.get(shortestpath.settings.ConfigKey.POH_NEXUS_PORTALS));
		// Without a nexus, only the portal chamber.
		HouseScan noNexus = HouseScan.of(List.of(ObjectID.POH_PORTAL_TEAK_ARDOUGNE));
		assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE),
			PohDetector.visitValues(noNexus, null).get(shortestpath.settings.ConfigKey.POH_NEXUS_PORTALS));
		assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE, PohNexusPortal.BARROWS),
			PohDetector.visitValues(scan, EnumSet.of(PohNexusPortal.BARROWS))
				.get(shortestpath.settings.ConfigKey.POH_NEXUS_PORTALS));
	}

	@Test
	public void nexusSeenRoundTrips()
	{
		Set<PohNexusPortal> seen = EnumSet.of(PohNexusPortal.ARDOUGNE, PohNexusPortal.TROLL_STRONGHOLD);
		assertEquals(seen, PohDetector.parseNexusSeen(PohDetector.serializeNexusSeen(seen)));
		Set<PohNexusPortal> none = EnumSet.noneOf(PohNexusPortal.class);
		assertEquals(none, PohDetector.parseNexusSeen(PohDetector.serializeNexusSeen(none)));
	}

	@Test
	public void unreadOrUnknownNexusSeen()
	{
		assertNull(PohDetector.parseNexusSeen(null));
		assertNull(PohDetector.parseNexusSeen("ARDOUGNE"));
		assertEquals(EnumSet.of(PohNexusPortal.ARDOUGNE), PohDetector.parseNexusSeen("1:ARDOUGNE,SOMEWHERE_NEW"));
	}
}
