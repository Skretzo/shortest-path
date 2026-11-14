package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.*;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;
import shortestpath.Util;

public class HouseScanTest
{
	@Test
	public void emptyHouseHasNothing()
	{
		HouseScan scan = HouseScan.of(List.of());
		assertFalse(scan.fairyRing());
		assertFalse(scan.spiritTree());
		assertFalse(scan.obelisk());
		assertFalse(scan.nexus());
		assertEquals(JewelleryBoxTier.NONE, scan.boxTier());
		assertTrue(scan.mountedItems().isEmpty());
		assertTrue(scan.chamberPortals().isEmpty());
	}

	@Test
	public void sameHouseComparesEveryFeature()
	{
		HouseScan a = HouseScan.of(List.of(ObjectID.POH_FAIRY_RING, ObjectID.POH_NEXUS_PORTAL_1));
		assertTrue(a.sameHouse(HouseScan.of(List.of(ObjectID.POH_NEXUS_PORTAL_1, ObjectID.POH_FAIRY_RING))));
		assertFalse(a.sameHouse(HouseScan.of(List.of(ObjectID.POH_NEXUS_PORTAL_1))));
		assertFalse(a.sameHouse(null));
	}

	@Test
	public void gardenFeatures()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_FAIRY_RING, ObjectID.POH_WILDERNESS_OBELISK));
		assertTrue(scan.fairyRing());
		assertFalse(scan.spiritTree());
		assertTrue(scan.obelisk());
	}

	@Test
	public void spiritualFairyTreeCountsAsBoth()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_SPIRIT_RING));
		assertTrue(scan.fairyRing());
		assertTrue(scan.spiritTree());
	}

	@Test
	public void leaguesAndChristmasTreesCount()
	{
		for (int id : new int[]{ObjectID.LEAGUE_5_POH_SPIRIT_RING, ObjectID.XMAS20_POH_SPIRIT_RING})
		{
			HouseScan scan = HouseScan.of(List.of(id));
			assertTrue(scan.fairyRing());
			assertTrue(scan.spiritTree());
		}
		for (int id : new int[]{ObjectID.LEAGUE_5_POH_SPIRIT_TREE, ObjectID.XMAS20_POH_SPIRIT_TREE})
		{
			HouseScan scan = HouseScan.of(List.of(id));
			assertFalse(scan.fairyRing());
			assertTrue(scan.spiritTree());
		}
		assertTrue(HouseScan.of(List.of(ObjectID.POH_NEXUS_PORTAL_LEAGUE_5)).nexus());
	}

	@Test
	public void objectIdFromTransportInfo()
	{
		assertEquals(13619, HouseScan.objectId("Enter Ardougne Portal 13619"));
		assertEquals(-1, HouseScan.objectId("Teleport to house"));
		assertEquals(-1, HouseScan.objectId(null));
	}

	@Test
	public void jewelleryBoxTierFromAnyOfItsObjects()
	{
		assertEquals(JewelleryBoxTier.BASIC, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_1));
		assertEquals(JewelleryBoxTier.BASIC, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_1_BASE));
		assertEquals(JewelleryBoxTier.BASIC, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_1_WINTERTODT));
		assertEquals(JewelleryBoxTier.BASIC, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_1_COLOSSEUM));
		assertEquals(JewelleryBoxTier.FANCY, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_2));
		assertEquals(JewelleryBoxTier.FANCY, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_2_FARMING_GUILD));
		assertEquals(JewelleryBoxTier.ORNATE, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_3));
		assertEquals(JewelleryBoxTier.ORNATE, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_3_KHARID));
		assertEquals(JewelleryBoxTier.ORNATE, HouseScan.boxTier(ObjectID.POH_JEWELLERY_BOX_3_COLOSSEUM));
		assertEquals(JewelleryBoxTier.NONE, HouseScan.boxTier(ObjectID.POH_ACHIEVEMENT_HOTSPOT_JEWELLERYBOX));
	}

	@Test
	public void jewelleryBoxIdsTheTransportsUseAreKnown()
	{
		// The transport rows name these three; a box that shows one of them must still read.
		assertEquals(JewelleryBoxTier.BASIC, HouseScan.boxTier(37492));
		assertEquals(JewelleryBoxTier.FANCY, HouseScan.boxTier(37501));
		assertEquals(JewelleryBoxTier.ORNATE, HouseScan.boxTier(37520));
	}

	@Test
	public void highestBoxTierWins()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_JEWELLERY_BOX_3_BASE, ObjectID.POH_JEWELLERY_BOX_1));
		assertEquals(JewelleryBoxTier.ORNATE, scan.boxTier());
	}

	@Test
	public void mountedItems()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_TROPHY_AMULETOFGLORY_4, ObjectID.POH_AMULET_XERIC_HEART,
			ObjectID.POH_AMULET_DIG_FOSSIL, ObjectID.POH_MOUNTED_MYTHICALCAPE));
		assertEquals(EnumSet.allOf(PohMountedItem.class), scan.mountedItems());
	}

	@Test
	public void mountedAmuletsBeforeTheirDestinationIsPicked()
	{
		assertEquals(PohMountedItem.XERICS_TALISMAN, HouseScan.mountedItem(ObjectID.POH_AMULET_XERIC));
		assertEquals(PohMountedItem.DIGSITE_PENDANT, HouseScan.mountedItem(ObjectID.POH_AMULET_DIGSITE));
		assertEquals(PohMountedItem.DIGSITE_PENDANT, HouseScan.mountedItem(ObjectID.POH_AMULET_DIG_DIGSITE));
		assertEquals(PohMountedItem.MYTHICAL_CAPE, HouseScan.mountedItem(ObjectID.POH_TROPHY_MYTHICAL_CAPE));
	}

	@Test
	public void nexus()
	{
		assertTrue(HouseScan.of(List.of(ObjectID.POH_NEXUS_PORTAL_2)).nexus());
	}

	@Test
	public void emptySpacesAreNotFurniture()
	{
		// Every house has the nexus room's empty spaces: "Portal Nexus space", rug, curtain and amulet spaces.
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_TELENEXUS_1, ObjectID.POH_TELENEXUS_2_MIDDLE,
			ObjectID.POH_TELENEXUS_2_SIDE, ObjectID.POH_TELENEXUS_2_CORNER, ObjectID.POH_TELENEXUS_3,
			ObjectID.POH_NEXUS_4_AMULET, ObjectID.POH_ACHIEVEMENT_HOTSPOT_JEWELLERYBOX));
		assertFalse(scan.nexus());
		assertEquals(JewelleryBoxTier.NONE, scan.boxTier());
		assertFalse(HouseScan.isHouseFeature(ObjectID.POH_TELENEXUS_1));
	}

	@Test
	public void houseFeatures()
	{
		assertTrue(HouseScan.isHouseFeature(ObjectID.POH_JEWELLERY_BOX_2));
		assertTrue(HouseScan.isHouseFeature(ObjectID.POH_SPIRIT_RING));
		assertTrue(HouseScan.isHouseFeature(ObjectID.POH_PORTAL_TEAK_ARDOUGNE));
		assertTrue(HouseScan.isHouseFeature(ObjectID.POH_AMULET_XERIC));
		assertFalse(HouseScan.isHouseFeature(ObjectID.POH_BOXING_RING_MAT_CORNER));
	}

	@Test
	public void everyPortalRowMapsToItsDestination() throws Exception
	{
		Util.each("/transports/teleportation_portals_poh.tsv", (line, cols) ->
		{
			PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(cols[5].trim());
			assertNotNull(line, portal);
			String[] words = cols[2].trim().split(" ");
			int id = Integer.parseInt(words[words.length - 1]);
			assertEquals(line, Set.of(portal), HouseScan.of(List.of(id)).chamberPortals());
		});
	}

	@Test
	public void portalsBeforeTheirDestinationIsPicked()
	{
		HouseScan scan = HouseScan.of(List.of(ObjectID.POH_PORTAL_MARBLE_VARROCK, ObjectID.POH_PORTAL_TEAK_CAMELOT,
			ObjectID.POH_PORTAL_MAG_YANILLE, ObjectID.POH_PORTAL_MARBLE_RESPAWN));
		assertEquals(EnumSet.of(PohNexusPortal.VARROCK, PohNexusPortal.CAMELOT, PohNexusPortal.WATCHTOWER,
			PohNexusPortal.RESPAWN), scan.chamberPortals());
	}
}
