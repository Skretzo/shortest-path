package shortestpath.requirement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import shortestpath.Util;

/**
 * Static metadata for the five plantable spirit tree patches (Port Sarim,
 * Etceteria, Brimhaven, Hosidius, Farming Guild) — the single source for
 * patch metadata: the region-scoped {@code FARMING_TRANSMIT_*} varbit to
 * sample and the tile bounds (inclusive) used to gate planted-tree
 * transports. Bounds must stay inside the patch's own region or the varbit
 * would not describe the tiles they cover.
 */
public final class SpiritTreePatches
{
	private SpiritTreePatches()
	{
	}

	// patch name -> {regionID, varbitID, x1, y1, x2, y2}
	private static final Map<String, int[]> PATCHES;
	private static final Map<Integer, String> PATCH_BY_REGION;

	static
	{
		Map<String, int[]> patches = new LinkedHashMap<>();
		try
		{
			Util.each("/spirit-tree-patches.tsv", (line, c) ->
				patches.put(c[0], new int[]{Integer.parseInt(c[1]), Integer.parseInt(c[2]),
					Integer.parseInt(c[3]), Integer.parseInt(c[4]), Integer.parseInt(c[5]), Integer.parseInt(c[6])}));
		}
		catch (Exception e)
		{
			throw new IllegalStateException("failed to load spirit-tree-patches.tsv", e);
		}
		PATCHES = Collections.unmodifiableMap(patches);

		Map<Integer, String> byRegion = new LinkedHashMap<>();
		for (Map.Entry<String, int[]> entry : patches.entrySet())
		{
			byRegion.put(entry.getValue()[0], entry.getKey());
		}
		PATCH_BY_REGION = Collections.unmodifiableMap(byRegion);
	}

	public static Set<String> patchNames()
	{
		return PATCHES.keySet();
	}

	public static boolean isPatch(String patchName)
	{
		return PATCHES.containsKey(patchName);
	}

	public static String patchNameForRegion(int regionId)
	{
		return PATCH_BY_REGION.get(regionId);
	}

	public static int regionForPatch(String patchName)
	{
		int[] entry = PATCHES.get(patchName);
		return entry == null ? -1 : entry[0];
	}

	public static int varbitForPatch(String patchName)
	{
		int[] entry = PATCHES.get(patchName);
		return entry == null ? -1 : entry[1];
	}

	/**
	 * Returns the planted spirit tree patch whose bounds contain the tile,
	 * or null when the tile is outside every patch. Used to gate planted-tree
	 * transport origins and destinations against the observed tree set.
	 */
	public static String patchNameForTile(int x, int y)
	{
		for (Map.Entry<String, int[]> entry : PATCHES.entrySet())
		{
			int[] patch = entry.getValue();
			if (x >= patch[2] && x <= patch[4] && y >= patch[3] && y <= patch[5])
			{
				return entry.getKey();
			}
		}
		return null;
	}
}
