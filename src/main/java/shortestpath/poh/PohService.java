package shortestpath.poh;

import java.util.Map;
import java.util.Set;

import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * The single public boundary for POH (Player Owned House) domain behavior:
 * geometry (the POH bounds and the canonical landing tile), transport
 * destination remapping, feature enablement, portal nexus keybinds, and
 * POH exit-info display.
 * <p>
 * This class owns the geometry contract verbatim — the bounds and landing
 * constants plus the inclusive-bounds {@link #isInsidePoh} predicate that
 * every POH geometry read crosses. The pure domain functions are statics so
 * they are reachable from construction-time and static-init call sites (the
 * transport load path remaps destinations before any service instance could
 * be injected); stateful members join them as the domain behavior moves in.
 */
public class PohService
{
	// POH (Player Owned House) bounds for detecting when path goes through POH
	// Note: POH_MIN_X is 1856 to exclude the Daddy's Home miniquest area
	private static final int POH_MIN_X = 1856;
	private static final int POH_MAX_X = 2047;
	private static final int POH_MIN_Y = 7040;
	private static final int POH_MAX_Y = 7111;
	// Co-ordinates for Basic theme landing tile
	public static final int POH_LANDING_X = 1858;
	public static final int POH_LANDING_Y = 7051;

	/**
	 * Checks if the given coordinates are inside the POH (Player Owned House) area.
	 *
	 * @param x The world X coordinate
	 * @param y The world Y coordinate
	 * @return true if inside POH, false otherwise
	 */
	public static boolean isInsidePoh(int x, int y)
	{
		return x >= POH_MIN_X && x <= POH_MAX_X && y >= POH_MIN_Y && y <= POH_MAX_Y;
	}

	/**
	 * Remaps POH transport destinations to the house landing tile.
	 * Transports that arrive inside the POH (e.g., fairy ring DIQ, spirit tree "Your house")
	 * are remapped so chaining with other POH transports is possible.
	 * Called once at load time since Transport objects in allTransports are shared references.
	 */
	public static void remapPohDestinations(Map<Integer, Set<Transport>> transports)
	{
		int pohLanding = WorldPointUtil.packWorldPoint(POH_LANDING_X, POH_LANDING_Y, 0);
		for (Set<Transport> transportSet : transports.values())
		{
			for (Transport transport : transportSet)
			{
				int destination = transport.getDestination();
				int destX = WorldPointUtil.unpackWorldX(destination);
				int destY = WorldPointUtil.unpackWorldY(destination);
				if (destination != pohLanding && isInsidePoh(destX, destY))
				{
					transport.setDestination(pohLanding);
				}
			}
		}
	}

	public static boolean isNexusPortalEnabled(Set<PohNexusPortal> enabledPortals, String displayInfo)
	{
		PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(displayInfo);
		return portal == null || enabledPortals.contains(portal);
	}

	public static boolean isMountedItemEnabled(Set<PohMountedItem> enabledItems, String objectInfo)
	{
		PohMountedItem item = PohMountedItem.fromObjectInfo(objectInfo);
		return item == null || enabledItems.contains(item);
	}

	/**
	 * Checks if a TELEPORTATION_BOX transport should be used based on POH settings.
	 * Handles jewellery box tiers and mounted items.
	 */
	public static boolean jewelleryBoxSatisfied(JewelleryBoxTier tier, Set<PohMountedItem> enabledItems, Transport transport)
	{
		String objectInfo = transport.getObjectInfo();
		if (objectInfo == null)
		{
			return false;
		}

		PohMountedItem mountedItem = PohMountedItem.fromObjectInfo(objectInfo);
		if (mountedItem != null)
		{
			// If mounted glory and ornate jewellery box is enabled, skip the glory
			// because the ornate box already covers all 4 destinations with correct prefixes
			if (PohMountedItem.GLORY.equals(mountedItem) && JewelleryBoxTier.ORNATE.equals(tier))
			{
				return false;
			}
			return isMountedItemEnabled(enabledItems, objectInfo);
		}

		// Filter jewellery boxes by tier
		if (JewelleryBoxTier.NONE.equals(tier))
		{
			return false;
		}

		// Basic box (37492): destinations 1-9
		if (objectInfo.contains("Basic Jewellery Box 37492"))
		{
			return true; // All tiers include basic
		}

		// Fancy box (37501): destinations A-J
		if (objectInfo.contains("Fancy Jewellery Box 37501"))
		{
			return JewelleryBoxTier.FANCY.equals(tier) ||
				JewelleryBoxTier.ORNATE.equals(tier);
		}

		// Ornate box (37520): destinations K-R
		if (objectInfo.contains("Ornate Jewellery Box 37520"))
		{
			return JewelleryBoxTier.ORNATE.equals(tier);
		}

		return false;
	}

	/**
	 * Per-type POH variant enablement: each POH-capable transport type reads
	 * its own toggle, and a nexus portal checks the enabled-portal set.
	 * Transports without a POH variant pass.
	 */
	public static boolean variantEnabled(Transport transport, boolean usePohFairyRing,
		boolean usePohSpiritTree, boolean usePohObelisk, Set<PohNexusPortal> enabledPortals)
	{
		TransportType type = transport.getType();

		// POH fairy ring
		if (TransportType.FAIRY_RING.equals(type))
		{
			return usePohFairyRing;
		}
		// POH spirit tree
		if (TransportType.SPIRIT_TREE.equals(type))
		{
			return usePohSpiritTree;
		}
		// POH obelisk
		if (TransportType.WILDERNESS_OBELISK.equals(type))
		{
			return usePohObelisk;
		}
		if (TransportType.TELEPORTATION_PORTAL_POH.equals(type))
		{
			return isNexusPortalEnabled(enabledPortals, transport.getDisplayInfo());
		}

		return true;
	}
}
