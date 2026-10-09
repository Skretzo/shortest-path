package shortestpath.poh;

import java.util.Map;
import java.util.Set;

import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

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
}
