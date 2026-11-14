package shortestpath.poh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OwnHouseTrackerTest
{
	private static boolean entersOwnHouseAfter(String option, String target)
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.tick(false, 1);
		tracker.menuClicked(option, target, 2);
		assertTrue(tracker.tick(true, 6));
		return tracker.isInOwnHouse();
	}

	@Test
	public void houseTeleportsLeadHome()
	{
		assertTrue(entersOwnHouseAfter("Break", "<col=ff9040>Teleport to house</col>"));
		assertTrue(entersOwnHouseAfter("Cast", "<col=00ff00>Teleport to House</col>"));
		assertTrue(entersOwnHouseAfter("Inside", "<col=ff9040>Teleport to house</col>"));
		assertTrue(entersOwnHouseAfter("Tele to POH", "<col=ff9040>Construct. cape</col>"));
		assertTrue(entersOwnHouseAfter("Home", "<col=ffff>Portal</col>"));
		assertTrue(entersOwnHouseAfter("Build mode", "<col=ffff>Portal</col>"));
		assertTrue(entersOwnHouseAfter("Continue", "Go to your house."));
	}

	@Test
	public void friendsHousesDoNot()
	{
		assertFalse(entersOwnHouseAfter("Friend's house", "<col=ffff>Portal</col>"));
		assertFalse(entersOwnHouseAfter("Visit-Last", "<col=ffff>House Advertisement</col>"));
		assertFalse(entersOwnHouseAfter("Continue", "Go to a friend's house."));
		assertFalse(entersOwnHouseAfter("View", "<col=ffff>House Advertisement</col>"));
		assertFalse(entersOwnHouseAfter("Outside", "<col=ff9040>Teleport to house</col>"));
		// Group Ironman: Group goes to a teammate's house.
		assertFalse(entersOwnHouseAfter("Group", "<col=ff9040>Teleport to house</col>"));
	}

	@Test
	public void saysWhy()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "<col=ff9040>Teleport to house</col>", 1);
		tracker.tick(true, 3);
		assertEquals("\"Break\" on Teleport to house", tracker.reason());
		tracker.ownerOnlyActionRefused();
		assertEquals("\"not your portal to configure\"", tracker.reason());
		tracker.tick(false, 10);
		assertEquals("", tracker.reason());
		tracker.tick(true, 20);
		assertEquals("way in unknown", tracker.reason());
		tracker.ownerOnlyInterfaceOpened();
		assertEquals("nexus configuration opened", tracker.reason());
	}

	@Test
	public void aRefusedConfigurationProvesItIsSomeoneElses()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		tracker.tick(true, 3);
		assertTrue(tracker.isInOwnHouse());
		tracker.ownerOnlyActionRefused();
		assertFalse(tracker.isInOwnHouse());
		assertTrue(tracker.isInHouse());
	}

	@Test
	public void houseToHouseTeleportsAreReDecided()
	{
		// Own house, then Group straight to a teammate's: the load never leaves the house area.
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		tracker.tick(true, 3);
		assertTrue(tracker.isInOwnHouse());
		tracker.menuClicked("Group", "Teleport to house", 10);
		tracker.reloaded(14);
		assertFalse(tracker.isInOwnHouse());

		// And back home with Inside.
		tracker.menuClicked("Inside", "Teleport to house", 20);
		tracker.reloaded(24);
		assertTrue(tracker.isInOwnHouse());
	}

	@Test
	public void aReloadWithoutATeleportIsTheSameHouse()
	{
		// Building mode on and off reloads the house in place.
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		tracker.tick(true, 3);
		tracker.reloaded(50);
		assertTrue(tracker.isInOwnHouse());
		// A Group click that loaded nothing does not decide a much later reload.
		tracker.menuClicked("Group", "Teleport to house", 60);
		assertFalse(tracker.reloaded(61 + OwnHouseTracker.RELOAD_WINDOW_TICKS));
		assertTrue(tracker.isInOwnHouse());
	}

	@Test
	public void anOldOwnHouseClickDoesNotDecideAReload()
	{
		// In a friend's house; a house tab Break the game refused, then a reload some ticks later.
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Friend's house", "Portal", 1);
		tracker.tick(true, 5);
		tracker.menuClicked("Break", "Teleport to house", 10);
		assertFalse(tracker.reloaded(11 + OwnHouseTracker.RELOAD_WINDOW_TICKS));
		assertTrue(tracker.isInOthersHouse());
	}

	@Test
	public void aSlowGroupTeleportStillCountsAsSomeoneElses()
	{
		// Own house; Group waits on a teammate choice (clicked at 18) and loads at 23.
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		tracker.tick(true, 3);
		tracker.menuClicked("Group", "Teleport to house", 10);
		tracker.menuClicked("Continue", "Iron Teammate", 18);
		assertTrue(tracker.reloaded(23));
		assertTrue(tracker.isInOthersHouse());
	}

	@Test
	public void aCancelledGroupDoesNotDecideABuildingModeReload()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		tracker.tick(true, 3);
		tracker.menuClicked("Group", "Teleport to house", 10);
		// Nothing loads; building mode toggled 30 ticks later.
		assertFalse(tracker.reloaded(40));
		assertTrue(tracker.isInOwnHouse());
	}

	@Test
	public void aReconnectDropsTheClick()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Friend's house", "Portal", 1);
		tracker.tick(true, 5);
		tracker.menuClicked("Break", "Teleport to house", 10);
		tracker.connectionLost();
		assertFalse(tracker.reloaded(11));
		assertTrue(tracker.isInOthersHouse());
	}

	@Test
	public void aRefusedTeleportDoesNotDecideALaterReload()
	{
		// A guest casts Teleport to house without the runes; the game refuses, nothing loads.
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Friend's house", "Portal", 1);
		tracker.tick(true, 5);
		tracker.menuClicked("Cast", "Teleport to house", 20);
		// Much later the scene reloads in place (a renderer plugin toggled).
		tracker.reloaded(45);
		assertFalse(tracker.isInOwnHouse());
		// The load used up the click, so it cannot decide the next one either.
		tracker.reloaded(46);
		assertFalse(tracker.isInOwnHouse());
	}

	@Test
	public void knowsAFriendsHouseFromAnUnknownOne()
	{
		OwnHouseTracker friend = new OwnHouseTracker();
		friend.menuClicked("Friend's house", "Portal", 1);
		friend.tick(true, 5);
		assertTrue(friend.isInOthersHouse());

		// Entered some unknown way: neither the player's own nor known to be someone else's.
		OwnHouseTracker unknown = new OwnHouseTracker();
		unknown.tick(true, 5);
		assertFalse(unknown.isInOthersHouse());
		assertFalse(unknown.isInOwnHouse());
		unknown.ownerOnlyActionRefused();
		assertTrue(unknown.isInOthersHouse());

		// The nexus configuration proves it is the player's own.
		friend.ownerOnlyInterfaceOpened();
		assertFalse(friend.isInOthersHouse());
		friend.tick(false, 20);
		assertFalse(friend.isInOthersHouse());
	}

	@Test
	public void groupClearsAnEarlierOwnClick()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Outside", "Teleport to house", 1);
		tracker.menuClicked("Break", "Teleport to house", 2);
		tracker.menuClicked("Group", "Teleport to house", 4);
		tracker.tick(true, 8);
		assertFalse(tracker.isInOwnHouse());
	}

	@Test
	public void anUnexplainedEntryIsNotOwn()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.tick(false, 1);
		assertTrue(tracker.tick(true, 2));
		assertTrue(tracker.isInHouse());
		assertFalse(tracker.isInOwnHouse());
	}

	@Test
	public void portalEnterClearsAnEarlierChoice()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		// Teleported outside instead, then entered through the portal's dialog by keyboard.
		tracker.menuClicked("Enter", "Portal", 5);
		tracker.tick(true, 9);
		assertFalse(tracker.isInOwnHouse());
	}

	@Test
	public void anOldClickNoLongerDecides()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		tracker.tick(false, 2 + OwnHouseTracker.ENTRY_WINDOW_TICKS);
		tracker.tick(true, 3 + OwnHouseTracker.ENTRY_WINDOW_TICKS);
		assertFalse(tracker.isInOwnHouse());
	}

	@Test
	public void leavingForgetsTheHouse()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		tracker.tick(true, 3);
		assertTrue(tracker.isInOwnHouse());
		assertFalse(tracker.tick(false, 10));
		assertFalse(tracker.isInOwnHouse());
		assertTrue(tracker.tick(true, 20));
		assertFalse(tracker.isInOwnHouse());
	}

	@Test
	public void staysOwnWhileInside()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.menuClicked("Break", "Teleport to house", 1);
		tracker.tick(true, 3);
		assertFalse(tracker.tick(true, 500));
		assertTrue(tracker.isInOwnHouse());
	}

	@Test
	public void nexusConfigurationProvesOwnership()
	{
		OwnHouseTracker tracker = new OwnHouseTracker();
		tracker.ownerOnlyInterfaceOpened();
		tracker.tick(true, 1);
		assertFalse(tracker.isInOwnHouse());
		tracker.ownerOnlyInterfaceOpened();
		assertTrue(tracker.isInOwnHouse());
	}
}
