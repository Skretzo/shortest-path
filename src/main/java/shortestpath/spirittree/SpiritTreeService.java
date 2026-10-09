package shortestpath.spirittree;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.HashTable;
import net.runelite.api.Player;
import net.runelite.api.WidgetNode;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.Text;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.ConfigManager;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.scheduler.RefreshCoordinator;
import shortestpath.settings.Effect;

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
public class SpiritTreeService
{
	/**
	 * The persisted config group — mirrors the {@code @ConfigGroup} value on
	 * {@link shortestpath.ShortestPathConfig}. Kept as a literal so this leaf
	 * package neither reads annotations reflectively nor references the plugin
	 * class; a divergence would surface as every persisted read/write missing
	 * its namespace.
	 */
	private static final String CONFIG_GROUP = "shortestpath";
	private static final String CONFIG_KEY_PREFIX = "spiritTree.";

	private static final Pattern SPIRIT_TREE_LABEL_PATTERN_MENU = Pattern.compile("<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)");
	private static final Pattern SPIRIT_TREE_LABEL_PATTERN_MENU_NEW = Pattern.compile("<col=ffffff>(.+)</col>: (<col=5f5f5f>)?(.+)");

	// patch name -> {regionID, varbitID, x1, y1, x2, y2} — the single source
	// for patch metadata: the region-scoped varbit to sample and the tile
	// bounds (inclusive) used to gate planted-tree transports. Bounds must
	// stay inside the patch's own region or the varbit would not describe
	// the tiles they cover.
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

		Map<Integer, String> byRegion = new HashMap<>();
		for (Map.Entry<String, int[]> entry : patches.entrySet())
		{
			byRegion.put(entry.getValue()[0], entry.getKey());
		}
		PATCH_BY_REGION = Collections.unmodifiableMap(byRegion);
	}

	private final ConfigManager configManager;
	private final Client client;
	private final RefreshCoordinator coordinator;
	// patch name -> last observed availability value (20 = grown and travelable).
	private final Map<String, Integer> observedValues = new HashMap<>();
	private final Map<String, Integer> lastPersistedValues = new HashMap<>();
	private boolean dirty;
	// Player region seen on the previous game tick (and at which tick), and
	// the region considered settled for transmit-slot sampling.
	private int lastPlayerRegionID = -1;
	private int lastPlayerRegionTick = -1;
	private int settledRegionID = -1;

	// The resolved travelable-tree set as last published — null while no
	// source has produced any observation (tri-state: null = unresolved,
	// empty = observed but nothing travelable). Written solely inside this
	// service as a single volatile write of an immutable copy; the engine's
	// capture step reads it through getAvailableSpiritTrees().
	private volatile Set<String> availableSpiritTrees;

	@Inject
	public SpiritTreeService(ConfigManager configManager, Client client,
		RefreshCoordinator coordinator)
	{
		this.configManager = configManager;
		this.client = client;
		this.coordinator = coordinator;
	}

	/**
	 * Detached-coordinator seam for tests that exercise the service's state
	 * and persistence without asserting declares — the declare no-ops on the
	 * missing coordinator.
	 */
	SpiritTreeService(ConfigManager configManager, Client client)
	{
		this(configManager, client, null);
	}

	SpiritTreeService()
	{
		this(null, null, null);
	}

	/**
	 * A detached service for harnesses and tests: no persistence and no
	 * client, so {@link #refreshAvailability} takes its detached arm and
	 * merges live samples into the published set instead of recording
	 * observations. No coordinator either, so change declares no-op.
	 */
	public static SpiritTreeService forTesting()
	{
		return new SpiritTreeService();
	}

	/**
	 * Test seam for asserting change declares: the returned instance hands
	 * admitted facts to the given (typically mocked) coordinator while
	 * staying detached from persistence and the client.
	 */
	public static SpiritTreeService forTesting(RefreshCoordinator coordinator)
	{
		return new SpiritTreeService(null, null, coordinator);
	}

	static boolean spiritTreeTravelable(int varbitValue)
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

	static String patchNameForRegion(int regionId)
	{
		return PATCH_BY_REGION.get(regionId);
	}

	static int regionForPatch(String patchName)
	{
		int[] entry = PATCHES.get(patchName);
		return entry == null ? -1 : entry[0];
	}

	static int varbitForPatch(String patchName)
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
	 * Inclusive tile bounds {x1, y1, x2, y2} for a patch, or null. Package-private
	 * for tests that tie the bounds to the patch's region.
	 */
	static int[] boundsForPatch(String patchName)
	{
		int[] entry = PATCHES.get(patchName);
		return entry == null ? null : new int[]{entry[2], entry[3], entry[4], entry[5]};
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

	/**
	 * Records the player's region for this game tick. The
	 * {@code FARMING_TRANSMIT_*} slots are repopulated for the new region
	 * after a region change, so a read taken on the entry tick can still
	 * carry the previous region's patch values. Sampling is only allowed
	 * once the same region has been seen on two consecutive ticks —
	 * {@link #isRegionSettled(int)}. Pass -1 when the player location is
	 * unknown; any gap (region change, loading tick, logout) un-settles.
	 * (Same region-stability guard as TimeTrackingPlugin.onGameTick.)
	 */
	void notePlayerRegion(int regionID, int tickCount)
	{
		settledRegionID = regionID != -1
			&& regionID == lastPlayerRegionID
			&& tickCount == lastPlayerRegionTick + 1
			? regionID : -1;
		lastPlayerRegionID = regionID;
		lastPlayerRegionTick = tickCount;
	}

	/**
	 * Whether the given region has been the player's region for at least two
	 * consecutive recorded ticks — i.e. a {@code FARMING_TRANSMIT_*} read for
	 * that region can no longer be a stale leftover from the previous region.
	 */
	boolean isRegionSettled(int regionID)
	{
		return regionID != -1 && settledRegionID == regionID;
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
	 * Returns a route-invalidating fact when the resolved set of travelable
	 * trees changed, null otherwise.
	 */
	TreeChange applyVarbitSample(String patchName, int varbitValue)
	{
		if (!PATCHES.containsKey(patchName))
		{
			return null;
		}
		boolean before = isTravelable(patchName);
		Integer previous = observedValues.get(patchName);
		if (previous == null || previous.intValue() != varbitValue)
		{
			observedValues.put(patchName, varbitValue);
			dirty = true;
		}
		return isTravelable(patchName) != before
			? new TreeChange("varbit:" + patchName, Set.of(Effect.ROUTE_INVALIDATING))
			: null;
	}

	/**
	 * Applies a travel-menu parse. The menu is authoritative for the patches it
	 * lists (greyed or not); patches it does not list keep their existing
	 * state — a partial snapshot never shrinks unvisited entries.
	 * Returns a route-invalidating fact when the resolved set of travelable
	 * trees changed, null otherwise.
	 */
	TreeChange applyMenuSnapshot(Set<String> listedTreeNames, Set<String> availableTreeNames)
	{
		if (listedTreeNames == null || availableTreeNames == null)
		{
			return null;
		}
		boolean changed = false;
		for (String name : listedTreeNames)
		{
			if (!PATCHES.containsKey(name))
			{
				continue;
			}
			changed |= applyVarbitSample(name, availableTreeNames.contains(name) ? 20 : 0) != null;
		}
		return changed
			? new TreeChange("menu", Set.of(Effect.ROUTE_INVALIDATING))
			: null;
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

	/**
	 * The resolved travelable-tree set as last published. Tri-state by
	 * contract: {@code null} while no detection source has produced an
	 * observation (consumers stay conservative), an empty set when every
	 * observed patch reported unusable, and the resolved names otherwise.
	 * The returned set is immutable — publication is a single volatile write.
	 */
	public Set<String> getAvailableSpiritTrees()
	{
		return availableSpiritTrees;
	}

	/**
	 * Seeds the published set for harnesses and tests — the detached refresh
	 * arm merges live samples into it. Never called in production. The set
	 * is defensively copied like {@link #publish} does, so a caller mutating
	 * its argument later cannot silently change published state.
	 */
	public void setAvailableSpiritTreesForTest(Set<String> trees)
	{
		this.availableSpiritTrees = trees == null ? null : Set.copyOf(trees);
	}

	/**
	 * Resolves the effective travelable set from every detection source — a
	 * live in-region {@code FARMING_TRANSMIT_*} varbit sample, recorded
	 * observations (RSProfile persistence + menu union), or — when the
	 * service runs detached (no config manager: tests, dashboard harness)
	 * — the live sample merged into the published set alone. An empty
	 * resolved set means every observed patch reported unusable and blocks
	 * planted-tree transports honestly; the unresolved {@code null} is kept
	 * while no source has produced any observation.
	 * <p>
	 * Client thread only, called from the engine's transport refresh.
	 */
	public void refreshAvailability(PlayerStateSource source)
	{
		String inRegionPatch = null;
		WorldPoint worldLocation = source.localPlayerWorldLocation();
		// Varbits are not transmitted while a modal widget is open; skip the
		// live sample (but not the patch-state resolution below) rather than
		// attribute a stale shared-slot value to the wrong patch. On the
		// region-entry tick the slot can likewise still carry the previous
		// region's values, so a tracked service samples only once the region
		// has settled; a detached service has no settled record to gate on.
		if (worldLocation != null && !source.modalWidgetOpen()
			&& (configManager == null
				|| isRegionSettled(worldLocation.getRegionID())))
		{
			inRegionPatch = patchNameForRegion(worldLocation.getRegionID());
		}

		if (configManager != null)
		{
			if (inRegionPatch != null)
			{
				// In-region sample is authoritative for this patch — a non-20
				// read evicts any stale persisted or menu-derived positive.
				applyVarbitSample(inRegionPatch,
					source.varbit(varbitForPatch(inRegionPatch)));
			}
			Set<String> resolved = getTravelableTreesOrNull();
			if (resolved != null)
			{
				publish(resolved);
			}
		}
		else if (inRegionPatch != null)
		{
			int varbitValue = source.varbit(varbitForPatch(inRegionPatch));
			Set<String> resolved = availableSpiritTrees == null
				? new HashSet<>() : new HashSet<>(availableSpiritTrees);
			if (spiritTreeTravelable(varbitValue))
			{
				resolved.add(inRegionPatch);
			}
			else
			{
				resolved.remove(inRegionPatch);
			}
			publish(resolved);
		}
	}

	/**
	 * Per-tick driver: notes the player's region, samples the in-region
	 * patch varbit once the region has settled, flushes persistence, and
	 * declares a route-invalidating fact to the coordinator when the
	 * resolved set changed.
	 */
	public void onGameTick()
	{
		if (client == null)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		WorldPoint worldLocation = localPlayer == null ? null : localPlayer.getWorldLocation();
		int playerRegion = worldLocation == null ? -1 : worldLocation.getRegionID();
		notePlayerRegion(playerRegion, client.getTickCount());
		TreeChange change = null;
		if (localPlayer != null
			// Varbits are not transmitted while a modal widget is open; a stale
			// read of the shared slot could carry another patch's value.
			&& !modalWidgetOpen(client)
			// On the region-entry tick the slot can still carry the previous
			// region's values; only sample once the region has settled.
			&& isRegionSettled(playerRegion))
		{
			// The FARMING_TRANSMIT_* varbits are region-scoped scratch slots, so
			// a planted spirit tree's varbit is only meaningful while standing in
			// that patch's region. Sample only on a region match.
			String spiritTreePatch = patchNameForRegion(playerRegion);
			if (spiritTreePatch != null)
			{
				change = applyVarbitSample(spiritTreePatch,
					client.getVarbitValue(varbitForPatch(spiritTreePatch)));
				if (change != null)
				{
					publish(getTravelableTrees());
				}
			}
		}

		// Persist after the same-tick sample so a fresh observation is written
		// on this tick rather than waiting for the next one.
		persistIfDirty();

		declare(change);
	}

	/**
	 * Scrapes a freshly opened spirit tree travel menu and applies the
	 * listed/available snapshot. Declares a route-invalidating fact to the
	 * coordinator when the resolved set changed; unrelated menus and
	 * unchanged parses declare nothing.
	 */
	public void onMenuOpened(boolean useNewMenu)
	{
		// Referencing
		// https://github.com/trs/runelite-teleport-maps/blob/e006270494500ab8e4826903b377bb945ca9fc96/src/main/java/com/mjhylkema/TeleportMaps/components/adventureLog/SpiritTreeMap.java#L141

		if (client == null)
		{
			return;
		}

		Widget container;
		if (useNewMenu)
		{
			container = client.getWidget(InterfaceID.MENU_NEW, 9);
		}
		else
		{
			container = client.getWidget(InterfaceID.MENU, 3);
		}

		if (container == null)
		{
			return;
		}

		Widget[] children = container.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return;
		}

		// Tree Gnome Village is always the first row and always available, so an
		// exact content match identifies the spirit tree menu. Interface group
		// MENU is a shared container (other MISCB_IF users load on the same
		// group), and this parse now runs on every load, so a loose check could
		// persist a false observation from an unrelated interface.
		String expectedFirstRow =
			(useNewMenu ? "<col=ffffff>1</col>: " : "<col=735a28>1</col>: ") + "Tree Gnome Village";
		if (!expectedFirstRow.equals(children[0].getText()))
		{
			return;
		}

		SpiritTreeMenuSnapshot snapshot = parseSpiritTreeMenuRows(children, useNewMenu);

		// The menu is authoritative for the patches it lists; persisted and
		// in-region-varbit observations fill the patches the menu never covered.
		TreeChange change = applyMenuSnapshot(snapshot.listed, snapshot.available);
		if (change != null)
		{
			publish(getTravelableTrees());
		}
		declare(change);
	}

	/**
	 * Parses the dynamic children of a spirit tree menu container into the
	 * listed/available patch-name sets. Package-private for tests.
	 */
	static SpiritTreeMenuSnapshot parseSpiritTreeMenuRows(Widget[] children, boolean useNewMenu)
	{
		Pattern pattern = useNewMenu ? SPIRIT_TREE_LABEL_PATTERN_MENU_NEW : SPIRIT_TREE_LABEL_PATTERN_MENU;
		SpiritTreeMenuSnapshot snapshot = new SpiritTreeMenuSnapshot();

		for (Widget child : children)
		{
			String text = child.getText();
			if (text == null)
			{
				continue;
			}
			Matcher matcher = pattern.matcher(text);
			if (!matcher.matches())
			{
				continue;
			}

			// Group 3 is spirit tree name; a greyed row can leave markup on it
			// (e.g. "Port Sarim</col>"), which would miss the patch table and
			// silently drop the row's eviction signal — strip tags before use.
			String name = Text.removeTags(matcher.group(3)).trim();
			snapshot.listed.add(name);

			// Group 2 is the disabled color tag; if present, the tree is unavailable
			if (matcher.group(2) != null)
			{
				continue;
			}

			snapshot.available.add(name);
		}

		return snapshot;
	}

	/**
	 * Reloads the persisted observations for the active profile. A profile
	 * reload always re-resolves state, so the declared fact is
	 * unconditional, matching the unconditional restart the shell used to
	 * apply.
	 */
	public void loadFromProfile()
	{
		dirty = false;
		observedValues.clear();
		lastPersistedValues.clear();
		if (configManager != null)
		{
			for (String patchName : PATCHES.keySet())
			{
				String stored = configManager.getRSProfileConfiguration(
					CONFIG_GROUP, configKey(patchName));
				Integer value = parseStoredValue(stored);
				if (value != null)
				{
					observedValues.put(patchName, value);
					lastPersistedValues.put(patchName, value);
				}
			}
		}
		publish(getTravelableTreesOrNull());
		declare(new TreeChange("profile", Set.of(Effect.ROUTE_INVALIDATING)));
	}

	public void persistIfDirty()
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
			if (spiritTreeTravelable(entry.getValue()))
			{
				if (last != null && last.equals(entry.getValue()))
				{
					continue;
				}
				configManager.setRSProfileConfiguration(CONFIG_GROUP,
					configKey(entry.getKey()), serializeObserved(entry.getValue(), now));
				lastPersistedValues.put(entry.getKey(), entry.getValue());
			}
			else if (last != null)
			{
				// An absent key means "never observed", so a non-travelable
				// observation removes the entry — including a legacy "0:<ts>"
				// residue — rather than leaving a dead value on the profile.
				// lastPersistedValues holds no marker for an absent key, so a
				// repeat non-travelable flush does not unset twice.
				configManager.unsetRSProfileConfiguration(CONFIG_GROUP,
					configKey(entry.getKey()));
				lastPersistedValues.remove(entry.getKey());
			}
		}
	}

	/**
	 * Hands an admitted fact to the refresh coordinator. Detached
	 * {@code forTesting} instances carry no coordinator, so the declare
	 * no-ops there; a null fact declares nothing, mirroring the return-null
	 * convention the shell's mapper used to guard.
	 */
	private void declare(TreeChange change)
	{
		if (change != null && coordinator != null)
		{
			coordinator.treeSetChanged(change);
		}
	}

	/**
	 * Single-point publication of the resolved set: one volatile write of an
	 * immutable copy, so a reader on the pathfinder thread can never observe
	 * a half-built set. {@code null} publishes the unresolved state verbatim.
	 */
	private void publish(Set<String> resolved)
	{
		availableSpiritTrees = resolved == null ? null : Set.copyOf(resolved);
	}

	private boolean isTravelable(String patchName)
	{
		Integer value = observedValues.get(patchName);
		return value != null && spiritTreeTravelable(value);
	}
}
