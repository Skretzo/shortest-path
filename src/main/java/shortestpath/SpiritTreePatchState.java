package shortestpath;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.HashTable;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.ConfigManager;

/**
 * Per-account availability state for the five plantable spirit tree patches
 * (Port Sarim, Etceteria, Brimhaven, Hosidius, Farming Guild). Detection is a
 * union of three sources:
 * <ul>
 *   <li>in-region {@code FARMING_TRANSMIT_*} varbit samples — authoritative, but
 *       only while the player stands in the patch's region: those varbits are
 *       region-scoped scratch slots shared between patches (A serves Port Sarim
 *       and Farming Guild, B serves Etceteria and Brimhaven), so reading them
 *       for any other region would corrupt a different patch's state.</li>
 *   <li>the spirit tree travel menu parse — authoritative for the patches it
 *       lists (a greyed row means planted but not usable).</li>
 *   <li>RSProfile-persisted observations, so discovered trees stay routable
 *       across client restarts.</li>
 * </ul>
 * A stale positive is worse than a miss for a router, so any authoritative
 * observation that reports a patch cleared, diseased, dead, or not grown
 * evicts the persisted travelable entry for that patch.
 */
@Singleton
public class SpiritTreePatchState
{
	private static final String CONFIG_KEY_PREFIX = "spiritTree.";

	// patch name -> {regionID, varbitID}, in PathfinderConfig.getPlantedSpiritTreeName order
	private static final Map<String, int[]> PATCHES;
	private static final Map<Integer, String> PATCH_BY_REGION;

	static
	{
		Map<String, int[]> patches = new LinkedHashMap<>();
		patches.put("Port Sarim", new int[]{12082, VarbitID.FARMING_TRANSMIT_A});
		patches.put("Etceteria", new int[]{10300, VarbitID.FARMING_TRANSMIT_B});
		patches.put("Brimhaven", new int[]{11058, VarbitID.FARMING_TRANSMIT_B});
		patches.put("Hosidius", new int[]{6711, VarbitID.FARMING_TRANSMIT_F});
		patches.put("Farming Guild", new int[]{4922, VarbitID.FARMING_TRANSMIT_A});
		PATCHES = Collections.unmodifiableMap(patches);

		Map<Integer, String> byRegion = new HashMap<>();
		for (Map.Entry<String, int[]> entry : patches.entrySet())
		{
			byRegion.put(entry.getValue()[0], entry.getKey());
		}
		PATCH_BY_REGION = Collections.unmodifiableMap(byRegion);
	}

	private final ConfigManager configManager;
	// patch name -> last observed availability value (20 = grown and travelable).
	private final Map<String, Integer> observedValues = new HashMap<>();
	private final Map<String, Integer> lastPersistedValues = new HashMap<>();
	private boolean dirty;

	@Inject
	public SpiritTreePatchState(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	SpiritTreePatchState()
	{
		this(null);
	}

	public static boolean spiritTreeTravelable(int varbitValue)
	{
		// Matches PatchImplementation.SPIRIT_TREE: 0-7 weeds, 8-19 growing,
		// 21-31 diseased, 32-43 dead, 44 grown but check-health-only,
		// 45-63 weeds. Only exactly 20 is a grown, travelable tree.
		return varbitValue == 20;
	}

	static Set<String> patchNames()
	{
		return PATCHES.keySet();
	}

	public static String patchNameForRegion(int regionId)
	{
		return PATCH_BY_REGION.get(regionId);
	}

	static int regionForPatch(String patchName)
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
	 * Whether a modal widget is currently open. Varbit updates are not
	 * transmitted while one is, so {@code FARMING_TRANSMIT_*} reads return
	 * whatever was last received — which can be the value of the other patch
	 * sharing the region-scoped slot. Callers must skip sampling while this
	 * returns true rather than record a cross-contaminated value.
	 * (Same guard as FarmingTracker.updateData.)
	 */
	public static boolean modalWidgetOpen(Client client)
	{
		HashTable<WidgetNode> componentTable = client.getComponentTable();
		if (componentTable == null)
		{
			return false;
		}
		for (WidgetNode widgetNode : componentTable)
		{
			if (widgetNode.getModalMode() != WidgetModalMode.NON_MODAL)
			{
				return true;
			}
		}
		return false;
	}

	static String configKey(String patchName)
	{
		int[] entry = PATCHES.get(patchName);
		return entry == null ? null : CONFIG_KEY_PREFIX + entry[0] + "." + entry[1];
	}

	/**
	 * Persisted value shape, mirroring the FarmingTracker:
	 * {@code "<value>:<unixtime>"}.
	 */
	static String serializeObserved(int value, long unixTimeSeconds)
	{
		return value + ":" + unixTimeSeconds;
	}

	/**
	 * Strict inverse of {@link #serializeObserved}: returns the stored value,
	 * or null for anything malformed — persisted config is untrusted input.
	 */
	static Integer parseStoredValue(String stored)
	{
		if (stored == null || stored.isEmpty())
		{
			return null;
		}
		int split = stored.indexOf(':');
		if (split <= 0 || split == stored.length() - 1)
		{
			return null;
		}
		String valueText = stored.substring(0, split);
		String timeText = stored.substring(split + 1);
		if (timeText.indexOf(':') >= 0)
		{
			return null;
		}
		try
		{
			int value = Integer.parseInt(valueText);
			Long.parseLong(timeText);
			return value;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Applies an in-region varbit sample for one patch. The caller must only
	 * call this while the player is inside the patch's mapped region — the
	 * varbits are region-scoped and mean different patches elsewhere.
	 * Returns true when the resolved set of travelable trees changed.
	 */
	public boolean applyVarbitSample(String patchName, int varbitValue)
	{
		if (!PATCHES.containsKey(patchName))
		{
			return false;
		}
		boolean before = isTravelable(patchName);
		Integer previous = observedValues.get(patchName);
		if (previous == null || previous.intValue() != varbitValue)
		{
			observedValues.put(patchName, varbitValue);
			dirty = true;
		}
		return isTravelable(patchName) != before;
	}

	/**
	 * Applies a travel-menu parse. The menu is authoritative for the patches it
	 * lists (greyed or not); patches it does not list keep their existing
	 * state — a partial snapshot never shrinks unvisited entries.
	 * Returns true when the resolved set of travelable trees changed.
	 */
	boolean applyMenuSnapshot(Set<String> listedTreeNames, Set<String> availableTreeNames)
	{
		if (listedTreeNames == null || availableTreeNames == null)
		{
			return false;
		}
		boolean changed = false;
		for (String name : listedTreeNames)
		{
			if (!PATCHES.containsKey(name))
			{
				continue;
			}
			changed |= applyVarbitSample(name, availableTreeNames.contains(name) ? 20 : 0);
		}
		return changed;
	}

	/**
	 * The union of all detection sources: planted patches currently known to be
	 * grown and usable. Empty when nothing observed is travelable.
	 */
	public Set<String> getTravelableTrees()
	{
		Set<String> trees = new HashSet<>();
		for (Map.Entry<String, Integer> entry : observedValues.entrySet())
		{
			if (spiritTreeTravelable(entry.getValue()))
			{
				trees.add(entry.getKey());
			}
		}
		return trees;
	}

	/**
	 * Same as {@link #getTravelableTrees} but null while no detection source has
	 * produced any observation — lets the consumer stay conservative
	 * ("never checked") instead of asserting an empty set.
	 */
	public Set<String> getTravelableTreesOrNull()
	{
		return observedValues.isEmpty() ? null : getTravelableTrees();
	}

	void loadFromProfile()
	{
		dirty = false;
		observedValues.clear();
		lastPersistedValues.clear();
		if (configManager == null)
		{
			return;
		}
		for (String patchName : PATCHES.keySet())
		{
			String stored = configManager.getRSProfileConfiguration(
				ShortestPathPlugin.CONFIG_GROUP, configKey(patchName));
			Integer value = parseStoredValue(stored);
			if (value != null)
			{
				observedValues.put(patchName, value);
				lastPersistedValues.put(patchName, value);
			}
		}
	}

	void persistIfDirty()
	{
		if (!dirty)
		{
			return;
		}
		dirty = false;
		if (configManager == null)
		{
			return;
		}
		long now = System.currentTimeMillis() / 1000L;
		for (Map.Entry<String, Integer> entry : observedValues.entrySet())
		{
			Integer last = lastPersistedValues.get(entry.getKey());
			if (last != null && last.equals(entry.getValue()))
			{
				continue;
			}
			configManager.setRSProfileConfiguration(ShortestPathPlugin.CONFIG_GROUP,
				configKey(entry.getKey()), serializeObserved(entry.getValue(), now));
			lastPersistedValues.put(entry.getKey(), entry.getValue());
		}
	}

	private boolean isTravelable(String patchName)
	{
		Integer value = observedValues.get(patchName);
		return value != null && spiritTreeTravelable(value);
	}
}
