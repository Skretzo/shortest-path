package shortestpath.leagues;

import java.util.Map;

import shortestpath.WorldPointUtil;
import shortestpath.transport.ResourceLoader;

/**
 * Resolves a packed world point to its {@link LeagueRegion} for the Demonic
 * Pacts League.
 *
 * <p>
 * Mirrors the static-utility shape of
 * {@link shortestpath.pathfinder.WildernessChecker}. The mapping is keyed by
 * OSRS map region id (a 64x64-tile chunk identifier matching
 * {@code WorldPoint.getRegionID()}: {@code (x >> 6) << 8 | (y >> 6)}) and
 * loaded from {@code /leagues/regions.tsv} on first access.
 * </p>
 *
 * <p>
 * Tiles whose region id is absent from the mapping fall back to
 * {@link LeagueRegion#NEUTRAL}. This is deliberate: it covers instances and
 * dynamic regions (POH, raids, dungeon entrances) which are universally
 * accessible during the league regardless of unlocked areas.
 * </p>
 */
public class LeagueRegionChecker
{
	private static volatile Map<Integer, LeagueRegion> regionsById;

	private LeagueRegionChecker()
	{
	}

	/**
	 * Returns the league region containing the supplied packed world point.
	 * Never returns {@code null}; unmapped tiles resolve to
	 * {@link LeagueRegion#NEUTRAL}.
	 */
	public static LeagueRegion getRegion(int packedPoint)
	{
		final int x = WorldPointUtil.unpackWorldX(packedPoint);
		final int y = WorldPointUtil.unpackWorldY(packedPoint);
		final int regionId = ((x >> 6) << 8) | (y >> 6);
		return regions().getOrDefault(regionId, LeagueRegion.NEUTRAL);
	}

	/**
	 * Returns the underlying region-id-to-region map. Lazily initialised on
	 * first call.
	 */
	private static Map<Integer, LeagueRegion> regions()
	{
		Map<Integer, LeagueRegion> snapshot = regionsById;
		if (snapshot == null)
		{
			synchronized (LeagueRegionChecker.class)
			{
				snapshot = regionsById;
				if (snapshot == null)
				{
					snapshot = ResourceLoader.leagueRegions();
					regionsById = snapshot;
				}
			}
		}
		return snapshot;
	}
}
