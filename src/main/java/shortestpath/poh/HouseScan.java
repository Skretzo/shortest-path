package shortestpath.poh;

import java.util.*;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.runelite.api.gameval.ObjectID;
import shortestpath.Util;

/**
 * What one look around a house found: the house features the settings
 * describe, read from the object ids present in the loaded house.
 * <p>
 * Built from ids alone so the mapping is testable without a client. The
 * caller passes every object id in the scene together with the id each
 * multiloc currently shows.
 */
@Getter
@Accessors(fluent = true)
public final class HouseScan
{
	private final boolean fairyRing;
	private final boolean spiritTree;
	private final boolean obelisk;
	private final boolean nexus;
	private final JewelleryBoxTier boxTier;
	private final Set<PohMountedItem> mountedItems;
	/** Destinations of the portals built in the portal chamber. */
	private final Set<PohNexusPortal> chamberPortals;

	private HouseScan(boolean fairyRing, boolean spiritTree, boolean obelisk, boolean nexus,
		JewelleryBoxTier boxTier, Set<PohMountedItem> mountedItems, Set<PohNexusPortal> chamberPortals)
	{
		this.fairyRing = fairyRing;
		this.spiritTree = spiritTree;
		this.obelisk = obelisk;
		this.nexus = nexus;
		this.boxTier = boxTier;
		this.mountedItems = Collections.unmodifiableSet(mountedItems);
		this.chamberPortals = Collections.unmodifiableSet(chamberPortals);
	}

	public static HouseScan of(Collection<Integer> objectIds)
	{
		boolean fairyRing = false;
		boolean spiritTree = false;
		boolean obelisk = false;
		boolean nexus = false;
		JewelleryBoxTier tier = JewelleryBoxTier.NONE;
		Set<PohMountedItem> mounted = EnumSet.noneOf(PohMountedItem.class);
		Set<PohNexusPortal> portals = EnumSet.noneOf(PohNexusPortal.class);

		for (int id : objectIds)
		{
			switch (id)
			{
				case ObjectID.POH_FAIRY_RING:
					fairyRing = true;
					break;
				case ObjectID.POH_SPIRIT_TREE:
				case ObjectID.LEAGUE_5_POH_SPIRIT_TREE:
				case ObjectID.XMAS20_POH_SPIRIT_TREE:
					spiritTree = true;
					break;
				case ObjectID.POH_SPIRIT_RING:
				case ObjectID.LEAGUE_5_POH_SPIRIT_RING:
				case ObjectID.XMAS20_POH_SPIRIT_RING:
					// The spiritual fairy tree is both in one.
					fairyRing = true;
					spiritTree = true;
					break;
				case ObjectID.POH_WILDERNESS_OBELISK:
					obelisk = true;
					break;
				default:
					break;
			}
			nexus |= isNexus(id);
			JewelleryBoxTier boxTier = boxTier(id);
			if (boxTier.ordinal() > tier.ordinal())
			{
				tier = boxTier;
			}
			PohMountedItem item = mountedItem(id);
			if (item != null)
			{
				mounted.add(item);
			}
			PohNexusPortal portal = Portals.BY_OBJECT.get(id);
			if (portal != null)
			{
				portals.add(portal);
			}
		}
		return new HouseScan(fairyRing, spiritTree, obelisk, nexus, tier, mounted, portals);
	}

	/**
	 * A built Portal Nexus. The {@code POH_TELENEXUS_*} objects are the empty
	 * "Portal Nexus space" and the room's rug and curtain spaces, present in
	 * every house, so they never count.
	 */
	static boolean isNexus(int id)
	{
		switch (id)
		{
			case ObjectID.POH_NEXUS_PORTAL_1:
			case ObjectID.POH_NEXUS_PORTAL_2:
			case ObjectID.POH_NEXUS_PORTAL_3:
			case ObjectID.POH_NEXUS_PORTAL_LEAGUE_5:
				return true;
			default:
				return false;
		}
	}

	/**
	 * What a house object can be used for, as route targets: "fairy ring",
	 * "spirit tree", "obelisk", "nexus", "box", "mounted:NAME" or
	 * "portal:NAME". Empty for anything else.
	 */
	public static Set<String> serves(int id)
	{
		Set<String> serves = new LinkedHashSet<>();
		switch (id)
		{
			case ObjectID.POH_FAIRY_RING:
				serves.add("fairy ring");
				break;
			case ObjectID.POH_SPIRIT_TREE:
			case ObjectID.LEAGUE_5_POH_SPIRIT_TREE:
			case ObjectID.XMAS20_POH_SPIRIT_TREE:
				serves.add("spirit tree");
				break;
			case ObjectID.POH_SPIRIT_RING:
			case ObjectID.LEAGUE_5_POH_SPIRIT_RING:
			case ObjectID.XMAS20_POH_SPIRIT_RING:
				serves.add("fairy ring");
				serves.add("spirit tree");
				break;
			case ObjectID.POH_WILDERNESS_OBELISK:
				serves.add("obelisk");
				break;
			default:
				break;
		}
		if (isNexus(id))
		{
			serves.add("nexus");
		}
		if (boxTier(id) != JewelleryBoxTier.NONE)
		{
			serves.add("box");
		}
		PohMountedItem item = mountedItem(id);
		if (item != null)
		{
			serves.add("mounted:" + item.name());
		}
		PohNexusPortal portal = Portals.BY_OBJECT.get(id);
		if (portal != null)
		{
			serves.add("portal:" + portal.name());
		}
		return serves;
	}

	/**
	 * The house objects that can take a transport, best first, from the
	 * object id its route row names. A portal destination is a chamber
	 * portal first and the nexus second, since the nexus offers the same
	 * destinations.
	 */
	public static List<String> targetsFor(int transportObjectId)
	{
		List<String> targets = new ArrayList<>(serves(transportObjectId));
		for (String target : serves(transportObjectId))
		{
			if (target.startsWith("portal:"))
			{
				targets.add("nexus");
			}
		}
		return targets;
	}

	/**
	 * The house objects that can take a route row, best first. A mounted item
	 * is found by its name in the row, the way the pathfinder enables it,
	 * since the object ids in its rows do not all name the item itself.
	 */
	public static List<String> targetsFor(String objectInfo)
	{
		PohMountedItem item = PohMountedItem.fromObjectInfo(objectInfo);
		if (item != null)
		{
			return List.of("mounted:" + item.name());
		}
		return targetsFor(objectId(objectInfo));
	}

	/** The hotkey a Portal Nexus menu line starts with ("6 : Barrows" gives "6"), or null. */
	public static String lineKey(String line)
	{
		if (line == null)
		{
			return null;
		}
		java.util.regex.Matcher m = KEYED_LINE.matcher(net.runelite.client.util.Text.removeTags(line).trim());
		return m.matches() ? m.group(1).toUpperCase(java.util.Locale.ROOT) : null;
	}

	/**
	 * Whether a Portal Nexus menu line names exactly the destination a route
	 * row names. Paired destinations share one {@link PohNexusPortal} (Varrock
	 * and the Grand Exchange, Camelot and Seers' Village, the Watchtower and
	 * Yanille) but are separate menu lines with separate keys.
	 */
	public static boolean isNexusLineFor(String displayInfo, String line)
	{
		if (displayInfo == null || line == null)
		{
			return false;
		}
		String name = PohService.normalize(PohService.stripKeyPrefix(net.runelite.client.util.Text.removeTags(line).trim()));
		return !name.isEmpty() && name.equals(PohService.normalize(displayInfo));
	}

	/** The destination a Portal Nexus menu line names, or null (titles, empty slots). */
	public static PohNexusPortal nexusLine(String line)
	{
		return NexusNames.portal(line);
	}

	/**
	 * Whether a jewellery box menu line is the destination a route row names.
	 * Rows are "1: Emir's Arena"; the menu may carry its own key and colour
	 * tags, so both sides drop a "key:" prefix and compare names.
	 */
	public static boolean isBoxLine(String displayInfo, String line)
	{
		if (displayInfo == null || line == null)
		{
			return false;
		}
		String want = boxName(displayInfo);
		String have = boxName(net.runelite.client.util.Text.removeTags(line));
		return !want.isEmpty() && have.equals(want);
	}

	private static String boxName(String text)
	{
		return BOX_KEY.matcher(text.trim()).replaceFirst("").trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static final java.util.regex.Pattern KEYED_LINE =
		java.util.regex.Pattern.compile("^([1-9A-Za-z]|F(?:[1-9]|1[0-2]))\\s*:\\s*.+$");
	private static final java.util.regex.Pattern BOX_KEY =
		java.util.regex.Pattern.compile("^[0-9A-Za-z]\\s*:\\s*");

	/** The object id at the end of a transport's object info ("Enter Ardougne Portal 13619"), or -1. */
	public static int objectId(String objectInfo)
	{
		if (objectInfo == null)
		{
			return -1;
		}
		String[] words = objectInfo.trim().split(" ");
		String last = words[words.length - 1];
		return last.matches("\\d+") ? Integer.parseInt(last) : -1;
	}

	/** Whether two reads found the same house: every feature the scan reads alike. */
	public boolean sameHouse(HouseScan other)
	{
		return other != null && fairyRing == other.fairyRing && spiritTree == other.spiritTree
			&& obelisk == other.obelisk && nexus == other.nexus && boxTier == other.boxTier
			&& mountedItems.equals(other.mountedItems) && chamberPortals.equals(other.chamberPortals);
	}

	/** Whether the object is one of the house features a scan reads. */
	public static boolean isHouseFeature(int id)
	{
		switch (id)
		{
			case ObjectID.POH_FAIRY_RING:
			case ObjectID.POH_SPIRIT_TREE:
			case ObjectID.LEAGUE_5_POH_SPIRIT_TREE:
			case ObjectID.XMAS20_POH_SPIRIT_TREE:
			case ObjectID.POH_SPIRIT_RING:
			case ObjectID.LEAGUE_5_POH_SPIRIT_RING:
			case ObjectID.XMAS20_POH_SPIRIT_RING:
			case ObjectID.POH_WILDERNESS_OBELISK:
				return true;
			default:
				return isNexus(id) || boxTier(id) != JewelleryBoxTier.NONE || mountedItem(id) != null
					|| Portals.BY_OBJECT.containsKey(id);
		}
	}

	/**
	 * A jewellery box's tier. Each tier is one placed object plus a run of
	 * variants (one per left-click destination) and a later Colosseum one.
	 */
	static JewelleryBoxTier boxTier(int id)
	{
		if (id == ObjectID.POH_JEWELLERY_BOX_3 || id == ObjectID.POH_JEWELLERY_BOX_3_COLOSSEUM
			|| (id >= ObjectID.POH_JEWELLERY_BOX_3_BASE && id <= ObjectID.POH_JEWELLERY_BOX_3_KHARID))
		{
			return JewelleryBoxTier.ORNATE;
		}
		if (id == ObjectID.POH_JEWELLERY_BOX_2 || id == ObjectID.POH_JEWELLERY_BOX_2_COLOSSEUM
			|| (id >= ObjectID.POH_JEWELLERY_BOX_2_BASE && id <= ObjectID.POH_JEWELLERY_BOX_2_FARMING_GUILD))
		{
			return JewelleryBoxTier.FANCY;
		}
		if (id == ObjectID.POH_JEWELLERY_BOX_1 || id == ObjectID.POH_JEWELLERY_BOX_1_COLOSSEUM
			|| (id >= ObjectID.POH_JEWELLERY_BOX_1_BASE && id <= ObjectID.POH_JEWELLERY_BOX_1_WINTERTODT))
		{
			return JewelleryBoxTier.BASIC;
		}
		return JewelleryBoxTier.NONE;
	}

	static PohMountedItem mountedItem(int id)
	{
		switch (id)
		{
			case ObjectID.POH_TROPHY_AMULETOFGLORY_4:
				return PohMountedItem.GLORY;
			case ObjectID.POH_AMULET_XERIC:
			case ObjectID.POH_AMULET_XERIC_LOOKOUT:
			case ObjectID.POH_AMULET_XERIC_GLADE:
			case ObjectID.POH_AMULET_XERIC_INFERNO:
			case ObjectID.POH_AMULET_XERIC_HEART:
			case ObjectID.POH_AMULET_XERIC_HONOUR:
				return PohMountedItem.XERICS_TALISMAN;
			case ObjectID.POH_AMULET_DIGSITE:
			case ObjectID.POH_AMULET_DIG_DIGSITE:
			case ObjectID.POH_AMULET_DIG_FOSSIL:
			case ObjectID.POH_AMULET_DIG_LITHKREN:
				return PohMountedItem.DIGSITE_PENDANT;
			case ObjectID.POH_MOUNTED_MYTHICALCAPE:
			case ObjectID.POH_TROPHY_MYTHICAL_CAPE:
				return PohMountedItem.MYTHICAL_CAPE;
			default:
				return null;
		}
	}

	/**
	 * Portal chamber objects by destination. The transport table already
	 * names every portal object the routes use; the few the routes never
	 * click are added here: the Varrock, Camelot and Yanille portals before
	 * their destination is picked, and the respawn portal in its other two
	 * frames.
	 */
	static final class Portals
	{
		static final Map<Integer, PohNexusPortal> BY_OBJECT = load();

		private static Map<Integer, PohNexusPortal> load()
		{
			Map<Integer, PohNexusPortal> byObject = new HashMap<>();
			try
			{
				Util.each("/transports/teleportation_portals_poh.tsv", (line, cols) ->
				{
					if (cols.length < 6)
					{
						return;
					}
					PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(cols[5].trim());
					String[] words = cols[2].trim().split(" ");
					if (portal == null || words.length == 0 || !words[words.length - 1].matches("\\d+"))
					{
						return;
					}
					byObject.put(Integer.parseInt(words[words.length - 1]), portal);
				});
			}
			catch (Exception e)
			{
				throw new IllegalStateException("failed to read the house portal objects", e);
			}
			for (int id : new int[]{ObjectID.POH_PORTAL_TEAK_VARROCK, ObjectID.POH_PORTAL_MAG_VARROCK,
				ObjectID.POH_PORTAL_MARBLE_VARROCK})
			{
				byObject.put(id, PohNexusPortal.VARROCK);
			}
			for (int id : new int[]{ObjectID.POH_PORTAL_TEAK_CAMELOT, ObjectID.POH_PORTAL_MAG_CAMELOT,
				ObjectID.POH_PORTAL_MARBLE_CAMELOT})
			{
				byObject.put(id, PohNexusPortal.CAMELOT);
			}
			for (int id : new int[]{ObjectID.POH_PORTAL_TEAK_YANILLE, ObjectID.POH_PORTAL_MAG_YANILLE,
				ObjectID.POH_PORTAL_MARBLE_YANILLE})
			{
				byObject.put(id, PohNexusPortal.WATCHTOWER);
			}
			byObject.put(ObjectID.POH_PORTAL_MAG_RESPAWN, PohNexusPortal.RESPAWN);
			byObject.put(ObjectID.POH_PORTAL_MARBLE_RESPAWN, PohNexusPortal.RESPAWN);
			return Collections.unmodifiableMap(byObject);
		}
	}
}
