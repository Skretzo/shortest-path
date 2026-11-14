package shortestpath.poh;

import java.util.*;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.runelite.api.gameval.ObjectID;

/**
 * A snapshot of what the house detector knows about the house the player
 * stands in: whether it counts as the player's own and why, whether and
 * when it was read, and what that read found. Shown on the debug panel.
 */
@Getter
@Accessors(fluent = true)
public final class HouseDebug
{
	private final boolean own;
	private final String reason;
	private final String state;
	/** The last read of this house, or null when it has not been read. */
	private final HouseScan scan;
	private final int objectCount;
	/** The house features of the last read, one per object. */
	private final List<HouseObject> houseObjects;
	/**
	 * Destinations the nexus menu listed in this house: read so far while it
	 * is open, then the last one opened until the player leaves; null when
	 * none was opened here.
	 */
	private final Set<PohNexusPortal> nexusVisit;
	/** The player's own nexus destinations as last read, or null when unread or not the player's house. */
	private final Set<PohNexusPortal> nexusSeen;
	/** Routing uses this house's teleports instead of the player's own (another player's house). */
	private final boolean routingHere;
	/** This house's nexus is not read yet, so routing assumes it has every destination. */
	private final boolean nexusUnread;

	HouseDebug(boolean own, String reason, String state, HouseScan scan, int objectCount,
		List<HouseObject> houseObjects, Set<PohNexusPortal> nexusVisit, Set<PohNexusPortal> nexusSeen,
		boolean routingHere, boolean nexusUnread)
	{
		this.nexusUnread = nexusUnread;
		this.routingHere = routingHere;
		this.nexusSeen = nexusSeen;
		this.own = own;
		this.reason = reason;
		this.state = state;
		this.scan = scan;
		this.objectCount = objectCount;
		this.houseObjects = houseObjects;
		this.nexusVisit = nexusVisit;
	}

	/**
	 * The nexus destinations to show: the menu read in this house when there
	 * is one, else, in the player's own house, the list last saved for this
	 * character; null when neither is known.
	 */
	public Set<PohNexusPortal> nexusList()
	{
		return nexusVisit != null ? nexusVisit : nexusSeen;
	}

	/** The read state without the tick counter or a reason the house line already gives. */
	public String shortState()
	{
		return state.replaceFirst("^\\d+ ticks ago, ", "").replace("not applied (not yours)", "not applied");
	}

	/** The nexus for the on-screen panel: "none", "not read" or a count of its teleports. */
	public String nexusShort()
	{
		if (scan == null || !scan.nexus())
		{
			return "none";
		}
		Set<PohNexusPortal> list = nexusList();
		return list == null ? "not read" : list.size() + (list.size() == 1 ? " teleport" : " teleports");
	}

	/** The short nexus line: built or not, and how many teleports are known. */
	public String nexusSummary()
	{
		if (scan == null || !scan.nexus())
		{
			return "no";
		}
		Set<PohNexusPortal> list = nexusList();
		if (list == null)
		{
			return "yes, open it to read";
		}
		return "yes, " + list.size() + (list.size() == 1 ? " teleport" : " teleports");
	}

	/** Whether one object is both the fairy ring and the spirit tree (a spiritual fairy tree). */
	public boolean hasCombo()
	{
		for (HouseObject object : houseObjects)
		{
			if (object.serves().contains("fairy ring") && object.serves().contains("spirit tree"))
			{
				return true;
			}
		}
		return false;
	}

	/** Everything about the house, one fact per line, for the sidebar's debug section. */
	public static String text(HouseDebug house)
	{
		if (house == null)
		{
			return "Not in a house.";
		}
		StringBuilder sb = new StringBuilder();
		line(sb, "House", house.own ? "yours" : "not yours");
		line(sb, "Why", house.reason);
		// The tick counter is left out here: the sidebar text then changes only when the facts do.
		line(sb, "Routing", !house.routingHere() ? "your own house"
			: house.nexusUnread() ? "this house's teleports; its nexus is not opened yet, so every nexus teleport is assumed"
			: "this house's teleports");
		line(sb, "Read", house.state.replaceFirst("^\\d+ ticks ago, ", "read, "));
		HouseScan scan = house.scan;
		if (scan != null)
		{
			boolean combo = house.hasCombo();
			line(sb, "Objects in scene", String.valueOf(house.objectCount));
			line(sb, "Fairy ring", combo ? "combo" : yesNo(scan.fairyRing()));
			line(sb, "Spirit tree", combo ? "combo" : yesNo(scan.spiritTree()));
			line(sb, "Obelisk", yesNo(scan.obelisk()));
			line(sb, "Jewellery box", scan.boxTier().toString());
			line(sb, "Mounted", scan.mountedItems().isEmpty() ? "none" : join(scan.mountedItems()));
			line(sb, "Nexus", house.nexusSummary());
			List<String> objects = new ArrayList<>();
			for (HouseObject object : house.houseObjects)
			{
				objects.add(describe(object.id()));
			}
			list(sb, "House objects", objects);
			list(sb, "Nexus teleports", house.nexusList());
			list(sb, "Portal chamber", scan.chamberPortals());
		}
		return sb.toString();
	}

	private static void line(StringBuilder sb, String label, String value)
	{
		if (sb.length() > 0)
		{
			sb.append('\n');
		}
		sb.append(label).append(": ").append(value);
	}

	private static void list(StringBuilder sb, String label, Collection<?> items)
	{
		if (items == null || items.isEmpty())
		{
			return;
		}
		sb.append('\n').append('\n').append(label).append(" (").append(items.size()).append("):");
		for (Object item : items)
		{
			sb.append('\n').append("  ").append(item);
		}
	}

	private static String yesNo(boolean value)
	{
		return value ? "yes" : "no";
	}

	private static String join(Collection<?> items)
	{
		StringBuilder sb = new StringBuilder();
		for (Object item : items)
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(item);
		}
		return sb.toString();
	}

	/** A readable name for a house object the scan reads, with its id. */
	public static String describe(int id)
	{
		return name(id) + " (" + id + ")";
	}

	public static String name(int id)
	{
		switch (id)
		{
			case ObjectID.POH_FAIRY_RING:
				return "Fairy ring";
			case ObjectID.POH_SPIRIT_TREE:
			case ObjectID.LEAGUE_5_POH_SPIRIT_TREE:
			case ObjectID.XMAS20_POH_SPIRIT_TREE:
				return "Spirit tree";
			case ObjectID.POH_SPIRIT_RING:
			case ObjectID.LEAGUE_5_POH_SPIRIT_RING:
			case ObjectID.XMAS20_POH_SPIRIT_RING:
				return "Spiritual fairy tree";
			case ObjectID.POH_WILDERNESS_OBELISK:
				return "Obelisk";
			default:
				break;
		}
		if (HouseScan.isNexus(id))
		{
			return "Portal Nexus";
		}
		JewelleryBoxTier tier = HouseScan.boxTier(id);
		if (tier != JewelleryBoxTier.NONE)
		{
			return tier + " jewellery box";
		}
		PohMountedItem item = HouseScan.mountedItem(id);
		if (item != null)
		{
			return "Mounted " + item;
		}
		PohNexusPortal portal = HouseScan.Portals.BY_OBJECT.get(id);
		if (portal != null)
		{
			return portal + " portal";
		}
		return "Object";
	}
}
