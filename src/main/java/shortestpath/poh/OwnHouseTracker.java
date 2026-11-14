package shortestpath.poh;

import java.util.Locale;
import net.runelite.client.util.Text;

/**
 * Tells the player's own house from a friend's. Both are the same instance,
 * so the way in decides: a house teleport, the house portal's Home or Build
 * mode, or "Go to your house" in its dialog leads to one's own house; the
 * portal's Friend's house, a house teleport's Group option, or the house
 * advertisement board (View, Visit-Last) lead to someone else's. An entry by any other way stays unknown and is
 * treated as not one's own, so a friend's house is never read as the
 * player's.
 * <p>
 * Pure state, fed by the plugin: menu clicks, then one call per game tick
 * with whether the player stands in a house.
 */
public final class OwnHouseTracker
{
	/** How long a click keeps deciding the next entry: long enough to walk to a portal. */
	static final int ENTRY_WINDOW_TICKS = 100;
	/** How soon after a house teleport click its load comes; a teleport loads within a few ticks. */
	static final int RELOAD_WINDOW_TICKS = 10;

	private enum Entry
	{
		OWN,
		OTHER
	}

	private Entry pending;
	private String pendingWhy;
	private int pendingTick;
	private boolean inside;
	private boolean own;
	/** Known to be someone else's (not merely not known to be one's own). */
	private boolean other;
	/** Why the current house counts as own or not, for the debug panel. */
	private String reason = "";

	/**
	 * Records a menu click. {@code option} and {@code target} are the menu
	 * entry's texts; a chat dialog choice passes its line as the target.
	 */
	public void menuClicked(String option, String target, int tick)
	{
		Entry entry = classify(clean(option), clean(target));
		if (entry == null)
		{
			// A dialog choice that follows a house teleport (Group's teammate) is part of it.
			if (pending != null && clean(option).equals("continue"))
			{
				pendingTick = tick;
			}
			return;
		}
		boolean undecided = entry == Entry.OTHER && isUndecidedPortalEntry(clean(option), clean(target));
		pending = undecided ? null : entry;
		pendingWhy = undecided ? null : "\"" + plain(option) + "\" on " + plain(target);
		pendingTick = tick;
	}

	/**
	 * Advances one game tick. Returns true on the tick the player arrives in
	 * a house.
	 */
	public boolean tick(boolean inHouse, int tick)
	{
		if (pending != null && tick - pendingTick > ENTRY_WINDOW_TICKS)
		{
			pending = null;
			pendingWhy = null;
		}
		if (inHouse == inside)
		{
			return false;
		}
		inside = inHouse;
		if (inHouse)
		{
			own = pending == Entry.OWN;
			other = pending == Entry.OTHER;
			reason = pendingWhy != null ? pendingWhy : "way in unknown";
			pending = null;
			pendingWhy = null;
			return true;
		}
		own = false;
		other = false;
		reason = "";
		return false;
	}

	/**
	 * The house reloaded while the player stayed in the house area: a
	 * teleport from one house straight into another, or the same house
	 * rebuilt (building mode on or off, a reconnect). A house teleport
	 * clicked just before decides which house it is now; without one it is
	 * the same house and nothing changes. Returns whether it is now a
	 * different house.
	 * <p>
	 * The click counts only if the load came within a teleport's few ticks
	 * of it, {@code tick} being the tick of the load. A dialog choice made
	 * after it (Group's teammate) restarts that count.
	 */
	public boolean reloaded(int tick)
	{
		Entry entry = pending;
		String why = pendingWhy;
		boolean recent = entry != null && tick - pendingTick <= RELOAD_WINDOW_TICKS;
		// A load in the house uses up the click either way: one that loaded nothing (a teleport the
		// game refused) must not decide a later, unrelated reload.
		pending = null;
		pendingWhy = null;
		if (!inside || !recent)
		{
			return false;
		}
		own = entry == Entry.OWN;
		other = entry == Entry.OTHER;
		reason = why;
		return true;
	}

	/**
	 * The connection dropped. Game ticks stop while disconnected, so a
	 * click from before it could look recent to the reload that follows the
	 * reconnect; it is dropped.
	 */
	public void connectionLost()
	{
		pending = null;
		pendingWhy = null;
	}

	/** Only the owner can open the nexus's configuration, so seeing it settles the question. */
	public void ownerOnlyInterfaceOpened()
	{
		if (inside)
		{
			own = true;
			other = false;
			reason = "nexus configuration opened";
		}
	}

	/**
	 * The game refused something only the owner may do ("This is not your
	 * portal to configure."), so this house is someone else's whatever the
	 * way in suggested.
	 */
	public void ownerOnlyActionRefused()
	{
		own = false;
		pending = null;
		pendingWhy = null;
		if (inside)
		{
			other = true;
			reason = "\"not your portal to configure\"";
		}
	}

	public boolean isInOwnHouse()
	{
		return inside && own;
	}

	public boolean isInHouse()
	{
		return inside;
	}

	/**
	 * In a house known to be someone else's: entered by a way that leads to
	 * another player's house, or refused an owner-only action. A house
	 * entered some unknown way is neither this nor the player's own.
	 */
	public boolean isInOthersHouse()
	{
		return inside && other;
	}

	/** Why the current house counts as the player's or not; empty outside a house. */
	public String reason()
	{
		return reason;
	}

	public void reset()
	{
		pending = null;
		pendingWhy = null;
		inside = false;
		own = false;
		other = false;
		reason = "";
	}

	/** OWN or OTHER for a click that decides the next house entry, null for any other click. */
	private static Entry classify(String option, String target)
	{
		if (target.equals("teleport to house")
			&& (option.equals("break") || option.equals("cast") || option.equals("inside")))
		{
			return Entry.OWN;
		}
		// A Group Ironman's Group option goes to a teammate's house.
		if (target.equals("teleport to house") && option.equals("group"))
		{
			return Entry.OTHER;
		}
		if (option.equals("tele to poh"))
		{
			return Entry.OWN;
		}
		if (target.startsWith("go to your house"))
		{
			return Entry.OWN;
		}
		if (target.startsWith("go to a friend's house") || target.contains("house advertisement"))
		{
			return Entry.OTHER;
		}
		if (target.equals("portal"))
		{
			switch (option)
			{
				case "home":
				case "build mode":
					return Entry.OWN;
				case "friend's house":
				case "enter":
					return Entry.OTHER;
				default:
					return null;
			}
		}
		return null;
	}

	/**
	 * The portal's plain Enter asks where to go in a dialog; until the
	 * dialog is answered the entry is unknown, which clears any earlier
	 * pending choice.
	 */
	private static boolean isUndecidedPortalEntry(String option, String target)
	{
		return target.equals("portal") && option.equals("enter");
	}

	private static String clean(String text)
	{
		return text == null ? "" : Text.removeTags(text).trim().toLowerCase(Locale.ROOT);
	}

	private static String plain(String text)
	{
		return text == null ? "" : Text.removeTags(text).trim();
	}
}
