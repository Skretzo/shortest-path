package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

public class HouseDebugTest
{
	@Test
	public void namesHouseObjects()
	{
		assertEquals("Ornate jewellery box", HouseDebug.name(ObjectID.POH_JEWELLERY_BOX_3));
		assertEquals("Ornate jewellery box", HouseDebug.name(ObjectID.POH_JEWELLERY_BOX_3_BASE));
		assertEquals("Mounted Glory", HouseDebug.name(ObjectID.POH_TROPHY_AMULETOFGLORY_4));
		assertEquals("Portal Nexus", HouseDebug.name(ObjectID.POH_NEXUS_PORTAL_3));
		assertEquals("Spiritual fairy tree", HouseDebug.name(ObjectID.POH_SPIRIT_RING));
		assertEquals("Ardougne portal", HouseDebug.name(ObjectID.POH_PORTAL_TEAK_ARDOUGNE));
		assertEquals("Fairy ring (29228)", HouseDebug.describe(ObjectID.POH_FAIRY_RING));
	}

	private static HouseObject object(int id)
	{
		return new HouseObject(null, id, HouseScan.serves(id));
	}

	@Test
	public void nexusSummaryAndSidebarText()
	{
		HouseObject nexus = object(ObjectID.POH_NEXUS_PORTAL_3);
		HouseObject tree = object(ObjectID.XMAS20_POH_SPIRIT_RING);
		HouseScan scan = HouseScan.of(java.util.List.of(nexus.id(), tree.id()));
		java.util.Set<PohNexusPortal> saved = java.util.EnumSet.of(PohNexusPortal.ARDOUGNE, PohNexusPortal.FALADOR);

		HouseDebug own = new HouseDebug(true, "\"Break\" on Teleport to house", "3 ticks ago, applied", scan, 40,
			java.util.List.of(nexus, tree), null, saved, false, false);
		assertEquals("yes, 2 teleports", own.nexusSummary());
		String text = HouseDebug.text(own);
		assertTrue(text, text.contains("House: yours"));
		assertTrue(text, text.contains("Routing: your own house"));
		assertTrue(text, text.contains("Fairy ring: combo"));
		assertTrue(text, text.contains("Nexus teleports (2):"));
		assertTrue(text, text.contains("  Ardougne"));
		assertTrue(text, text.contains("  Spiritual fairy tree (40779)"));
		assertTrue(text, text.indexOf("House objects (2):") < text.indexOf("Nexus teleports (2):"));

		// A friend's house: only what its menu listed this visit, and the menu count wins over the saved list.
		HouseDebug friend = new HouseDebug(false, "way in unknown", "1 ticks ago, not applied (not yours)", scan, 40,
			java.util.List.of(nexus), java.util.EnumSet.of(PohNexusPortal.WEISS), null, true, false);
		assertEquals("yes, 1 teleport", friend.nexusSummary());
		assertTrue(HouseDebug.text(friend).contains("Routing: this house's teleports"));
		HouseDebug unread = new HouseDebug(false, "way in unknown", "", scan, 40, java.util.List.of(nexus), null, null, true, true);
		assertEquals("yes, open it to read", unread.nexusSummary());
		assertTrue(HouseDebug.text(unread).contains("its nexus is not opened yet"));
		assertTrue(own.hasCombo());
		assertTrue(!unread.hasCombo());
		assertEquals("not read", unread.nexusShort());
		assertEquals("2 teleports", own.nexusShort());
		assertEquals("Not in a house.", HouseDebug.text(null));
	}

	@Test
	public void everyHouseFeatureHasAName()
	{
		for (int id : new int[]{ObjectID.POH_FAIRY_RING, ObjectID.POH_SPIRIT_TREE, ObjectID.POH_WILDERNESS_OBELISK,
			ObjectID.POH_JEWELLERY_BOX_1, ObjectID.POH_AMULET_XERIC, ObjectID.POH_AMULET_DIGSITE,
			ObjectID.POH_MOUNTED_MYTHICALCAPE, ObjectID.POH_PORTAL_MARBLE_VARROCK})
		{
			assertTrue(HouseScan.isHouseFeature(id));
			assertTrue(id + " " + HouseDebug.name(id), !"Object".equals(HouseDebug.name(id)));
		}
	}
}
