package shortestpath.requirement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;

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
		patches.put("Port Sarim", new int[]{12082, VarbitID.FARMING_TRANSMIT_A, 3058, 3256, 3062, 3260});
		patches.put("Etceteria", new int[]{10300, VarbitID.FARMING_TRANSMIT_B, 2611, 3855, 2615, 3860});
		patches.put("Brimhaven", new int[]{11058, VarbitID.FARMING_TRANSMIT_B, 2800, 3201, 2804, 3205});
		patches.put("Hosidius", new int[]{6711, VarbitID.FARMING_TRANSMIT_F, 1691, 3540, 1695, 3544});
		patches.put("Farming Guild", new int[]{4922, VarbitID.FARMING_TRANSMIT_A, 1251, 3748, 1255, 3752});
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

	/**
	 * Inclusive tile bounds {x1, y1, x2, y2} for a patch, or null. Public for
	 * tests that tie the bounds to the patch's region.
	 */
	public static int[] boundsForPatch(String patchName)
	{
		int[] entry = PATCHES.get(patchName);
		return entry == null ? null : new int[]{entry[2], entry[3], entry[4], entry[5]};
	}
}
