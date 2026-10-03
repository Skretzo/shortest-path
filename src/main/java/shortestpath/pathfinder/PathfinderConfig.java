package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.Destination;
import shortestpath.DestinationRequirements;
import shortestpath.JewelleryBoxTier;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.ShortestPathConfig;
import shortestpath.ShortestPathPlugin;
import shortestpath.SpiritTreePatchState;
import static shortestpath.ShortestPathPlugin.POH_LANDING_X;
import static shortestpath.ShortestPathPlugin.POH_LANDING_Y;
import shortestpath.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.leagues.LeagueRegion;
import shortestpath.leagues.LeagueRegionChecker;
import shortestpath.pathfinder.exact.PreparedRoutingAccount;
import shortestpath.transport.OwnedItems;
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportEligibility;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;
import shortestpath.transport.TransportTypeConfig;
import shortestpath.transport.parser.SkillRequirementParser;
import shortestpath.transport.parser.VarRequirement;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;
import shortestpath.transport.requirement.Unlock;

@SuppressWarnings("SameParameterValue")
public class PathfinderConfig
{
	private static final int MAX_SKILL_LEVEL = 99;
	public static final List<Integer> RUNE_POUCHES = Arrays.asList(
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
	);
	public static final int[] RUNE_POUCH_RUNE_VARBITS =
		{
			VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4,
			VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
		};
	public static final int[] RUNE_POUCH_AMOUNT_VARBITS =
		{
			VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4,
			VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
		};
	/**
	 * Item ids that only exist on Deadman Mode worlds ({@code WorldType.DEADMAN}).
	 * Transports requiring them are filtered out on every other world type,
	 * regardless of the teleportation-item setting.
	 */
	private static final Set<Integer> DEADMAN_ONLY_ITEM_IDS = Set.of(
		ItemID.MAGIC_ROCK_OF_FAIRIES);

	/**
	 * Respawn landing tiles used by the Respawn Teleport spell and the POH respawn portal.
	 * Every respawn except Prifddinas exposes an {@code *_SPAWN} varbit; when Prifddinas is
	 * the active respawn all of them read 0, which is the same signature as the Lumbridge
	 * default, so both landings are gated on the declared respawn in config instead.
	 */
	private static final int LUMBRIDGE_RESPAWN = WorldPointUtil.packWorldPoint(3221, 3218, 0);
	private static final int PRIFDDINAS_RESPAWN = WorldPointUtil.packWorldPoint(3265, 6077, 0);

	private final SplitFlagMap mapData;
	private final ThreadLocal<CollisionMap> map;
	/**
	 * All transports by origin. The WorldPointUtil.UNDEFINED key is used for transports centered on the player.
	 */
	// Flat list of every loaded transport. refreshTransports only ever iterates these (the origin
	// is re-derived from each transport), so the per-origin Set/HashMap/Integer-key map the loader
	// produces is flattened here and not retained (issue #491).
	private final Transport[] allTransports;
	/**
	 * Display view of every loaded transport, grouped by origin tile with POH origins
	 * collapsed into the landing tile (same layout as the available-transport display
	 * view). Used by overlays to render unavailable transports alongside available ones.
	 */
	@Getter
	private final PrimitiveIntHashMap<Transport[]> allDisplayTransports;
	private final Map<String, Set<Integer>> allDestinations;
	private final Map<String, Set<Integer>> filteredDestinations;
	/**
	 * Per packed tile; only bank.tsv rows with Skills/Quests/Varbits/VarPlayers.
	 */
	private final Map<Integer, DestinationRequirements> bankRequirements;
	private final List<Integer> filteredTargets = new ArrayList<>(4);
	private final Client client;
	private final ShortestPathConfig config;
	// Centralized transport type enable/disable config
	private final TransportTypeConfig transportTypeConfig;
	private final int[] boostedSkillLevelsAndMore = new int[Skill.values().length + 3];
	private int currentMaxQuestPoints;
	private final Map<Quest, QuestState> questStates = new HashMap<>();
	private final Map<Integer, Integer> varbitValues = new HashMap<>();
	private final Map<Integer, Integer> varPlayerValues = new HashMap<>();
	@Getter
	private final LeagueModeState leagueModeState = new LeagueModeState();
	public ItemContainer bank = null;
	// Written on the client thread, read by the pathfinder thread in
	// isPlantedSpiritTreeAllowed — volatile keeps the cross-thread contract explicit.
	public volatile Set<String> availableSpiritTrees = null;
	private SpiritTreePatchState spiritTreePatchState;

	public void setSpiritTreePatchState(SpiritTreePatchState spiritTreePatchState)
	{
		this.spiritTreePatchState = spiritTreePatchState;
	}
	/**
	 * Bank tiles the player may use for path banking state (requirements satisfied). Rebuilt in {@link #refresh()}.
	 */
	// Refresh-written state below is read by the pathfinder executor thread;
	// volatile keeps the cross-thread contract explicit (as availableSpiritTrees
	// already does). The two availability maps additionally travel together in
	// one volatile holder so a search never mixes sides of different refreshes.
	private volatile Set<Integer> accessibleBankTiles = Set.of();
	/**
	 * The banked/unbanked availability views of one refresh, published as a
	 * single volatile reference so a running search can never read one side
	 * from refresh N and the other from refresh N+1.
	 */
	private static final class TransportAvailabilities
	{
		private final TransportAvailability withoutBank;
		private final TransportAvailability withBank;

		private TransportAvailabilities(TransportAvailability withoutBank, TransportAvailability withBank)
		{
			this.withoutBank = withoutBank;
			this.withBank = withBank;
		}
	}

	private volatile TransportAvailabilities transportAvailabilities;
	/**
	 * Client-state snapshot answering transport item-requirement questions for both
	 * pathfinding ({@link TransportEligibility#usable}) and the bank-pickup display.
	 * Rebuilt by {@link #refreshTransports} and lazily by {@link #getEligibility()} after
	 * {@link #invalidateEligibility()}.
	 */
	private TransportEligibility eligibility;
	private boolean eligibilityStale = true;
	/**
	 * Reference that points to either allDestinations or filteredDestinations
	 */
	private volatile Map<String, Set<Integer>> destinations;
	@Getter
	private volatile long calculationCutoffMillis;
	@Getter
	private volatile int unreachableTargetDistance;
	@Getter
	private volatile double exactHeuristicWeight = 1;
	@Getter
	private volatile boolean avoidWilderness;
	// POH-specific settings (not tied to a single TransportType)
	private volatile boolean usePohFairyRing,
		usePohSpiritTree,
		usePoh,
		usePohObelisk,
		includeBankPath,
		respawnPrifddinas;
	private volatile Set<PohNexusPortal> enabledPohNexusPortals = Set.of();
	private volatile Set<PohMountedItem> enabledPohMountedItems = Set.of();
	@Getter
	private volatile Set<Unlock> unlocks = Set.of();
	private volatile JewelleryBoxTier pohJewelleryBoxTier;
	private volatile int costConsumableTeleportationItems;
	@Getter
	private volatile int bankVisitCost;
	private volatile int currencyThreshold;
	@Getter
	private volatile boolean isOnSailingBoat;
	@Getter
	private volatile PathfinderBackend pathfinderBackend = PathfinderBackend.LEGACY;

	public PathfinderConfig(Client client, ShortestPathConfig config)
	{
		this.client = client;
		this.config = config;
		this.transportTypeConfig = new TransportTypeConfig(config);
		this.mapData = SplitFlagMap.fromResources();
		this.map = ThreadLocal.withInitial(() -> new CollisionMap(mapData));
		Map<Integer, Set<Transport>> loadedTransports = TransportLoader.loadAllFromResources();
		remapPohDestinations(loadedTransports);
		this.allTransports = flatten(loadedTransports);
		this.allDisplayTransports = buildAllDisplayTransports(this.allTransports);
		this.transportAvailabilities = new TransportAvailabilities(
			new TransportAvailability.Builder(allTransports.length).build(),
			new TransportAvailability.Builder(allTransports.length).build());
		this.allDestinations = Destination.loadAllFromResources();
		this.filteredDestinations = filterDestinations(allDestinations);
		this.destinations = allDestinations;
		this.bankRequirements = Destination.loadBankRequirementsFromResources();
	}

	protected PathfinderConfig(Client client, ShortestPathConfig config,
		SplitFlagMap mapData, Map<Integer, Set<Transport>> allTransports,
		Map<String, Set<Integer>> allDestinations, Map<String, Set<Integer>> filteredDestinations,
		Map<Integer, DestinationRequirements> bankRequirements)
	{
		this.client = client;
		this.config = config;
		this.transportTypeConfig = new TransportTypeConfig(config);
		this.mapData = mapData;
		this.map = ThreadLocal.withInitial(() -> new CollisionMap(this.mapData));
		this.allTransports = flatten(allTransports);
		this.allDisplayTransports = buildAllDisplayTransports(this.allTransports);
		this.transportAvailabilities = new TransportAvailabilities(
			new TransportAvailability.Builder(this.allTransports.length).build(),
			new TransportAvailability.Builder(this.allTransports.length).build());
		this.allDestinations = allDestinations;
		this.filteredDestinations = filteredDestinations;
		this.destinations = allDestinations;
		this.bankRequirements = bankRequirements;
	}

	/**
	 * Pure combat-level formula, extracted for testability.
	 */
	static int computeCombatLevel(int attack, int strength, int defence, int hitpoints, int magic, int ranged, int prayer)
	{
		// Integer division is intentional here — it matches the OSRS floor(x/2) steps in the formula.
		double base = 0.25 * (defence + hitpoints + Math.floorDiv(prayer, 2));
		double melee = (13 * (attack + strength)) / 40.0;
		double range = (13 * (3 * Math.floorDiv(ranged, 2))) / 40.0;
		double mage = (13 * (3 * Math.floorDiv(magic, 2))) / 40.0;
		return (int) Math.floor(base + Math.max(Math.max(melee, range), Math.max(melee, mage)));
	}

	static String getPlantedSpiritTreeName(int x, int y)
	{
		// SpiritTreePatchState owns the patch table (region, varbit, bounds);
		// this shim keeps the planted-tree gate readable at its call sites.
		return SpiritTreePatchState.patchNameForTile(x, y);
	}

	public CollisionMap getMap()
	{
		return map.get();
	}

	/**
	 * WARNING: This method collapses the banked/unbanked transport distinction into a single view.
	 * <p>
	 * It exists only for legacy display-oriented callers such as overlays which want a coarse
	 * "currently relevant" set of transports to render. It must not be used for path-state-sensitive
	 * logic, because transport availability now depends on whether a path has visited a bank.
	 * <p>
	 * Use {@link #getTransportAvailability(boolean)}, {@link #getTransportsPacked(boolean)}, or
	 * {@link #getUsableTeleports(boolean)} for pathfinding and path analysis code.
	 */
	public PrimitiveIntHashMap<Transport[]> getTransports()
	{
		return getTransportAvailability(includeBankPath).getDisplayTransports();
	}

	public PrimitiveIntHashMap<Transport[]> getTransportsPacked(boolean bankVisited)
	{
		return getTransportAvailability(bankVisited).getTransportsPacked();
	}

	public Transport[] getUsableTeleports(boolean bankVisited)
	{
		return getTransportAvailability(bankVisited).getUsableTeleports();
	}

	public TransportAvailability getTransportAvailability(boolean bankVisited)
	{
		return bankVisited ? transportAvailabilities.withBank : transportAvailabilities.withoutBank;
	}

	public boolean isBankPathEnabled()
	{
		return includeBankPath;
	}

	/** Snapshot the already-evaluated account/config state for the exact graph. */
	public PreparedRoutingAccount prepareExactRoutingAccount(boolean allowTransports)
	{
		return PreparedRoutingAccount.compile(
			getTransportAvailability(false), getTransportAvailability(true), includeBankPath,
			accessibleBankTiles, bankVisitCost, allowTransports, this::getAdditionalTransportCost);
	}

	public boolean hasDestination(String destinationType)
	{
		return destinations.containsKey(destinationType);
	}

	public Set<Integer> getDestinations(String destinationType)
	{
		return destinations.get(destinationType);
	}

	/**
	 * Whether standing on this tile may flip the path into {@code bankVisited} (inventory-from-bank) state.
	 */
	public boolean bankAccessible(int packedPosition)
	{
		return accessibleBankTiles.contains(packedPosition);
	}

	public void refresh()
	{
		pathfinderBackend = config.pathfinderBackend();
		long evaluationTimeMinutes = currentTimeMinutes();
		calculationCutoffMillis = (long) config.calculationCutoff() * Constants.GAME_TICK_LENGTH;
		unreachableTargetDistance = ShortestPathPlugin.override("unreachableTargetDistanceThreshold", config.unreachableTargetDistance());
		// @Range only bounds the config panel, so also clamp overrides to the same 100-300% range.
		exactHeuristicWeight = Math.max(100, Math.min(300,
			ShortestPathPlugin.override("exactHeuristicWeight", config.exactHeuristicWeight()))) / 100.0;
		avoidWilderness = ShortestPathPlugin.override("avoidWilderness", config.avoidWilderness());
		usePoh = ShortestPathPlugin.override("usePoh", config.usePoh());
		leagueModeState.refresh(client);

		// Refresh transport type enabled states
		transportTypeConfig.refresh();
		// POH-specific settings
		usePohFairyRing = ShortestPathPlugin.override("usePohFairyRing", config.usePohFairyRing());
		usePohSpiritTree = ShortestPathPlugin.override("usePohSpiritTree", config.usePohSpiritTree());
		usePohObelisk = ShortestPathPlugin.override("usePohObelisk", config.usePohObelisk());
		enabledPohNexusPortals = Set.copyOf(config.pohNexusPortals());
		Set<PohMountedItem> pohMountedItems = config.pohMountedItems();
		enabledPohMountedItems = pohMountedItems == null ? Set.of() : Set.copyOf(pohMountedItems);
		pohJewelleryBoxTier = ShortestPathPlugin.override("pohJewelleryBoxTier", config.pohJewelleryBoxTier());

		// Other settings (useTeleportationItems is now managed by transportTypeConfig)
		currencyThreshold = ShortestPathPlugin.override("currencyThreshold", config.currencyThreshold());
		// Banked teleport items are only usable from the bankVisited path state, so a
		// mode that collects bank contents must also enable bank-path traversal —
		// otherwise the banked items are gathered but can never be offered.
		TeleportationItem teleportationItemSetting = transportTypeConfig.getTeleportationItemSetting();
		includeBankPath = ShortestPathPlugin.override("includeBankPath", config.includeBankPath())
			|| TeleportationItem.INVENTORY_AND_BANK.equals(teleportationItemSetting)
			|| TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE.equals(teleportationItemSetting);
		respawnPrifddinas = ShortestPathPlugin.override("respawnPrifddinas", config.respawnPrifddinas());

		// Declared unlocks: states the game does not expose to the client, toggled in config.
		Set<Unlock> declaredUnlocks = EnumSet.noneOf(Unlock.class);
		if (ShortestPathPlugin.override("unlockCanoeAxe", config.unlockCanoeAxe()))
		{
			declaredUnlocks.add(Unlock.CANOE_AXE);
		}
		if (ShortestPathPlugin.override("unlockXericsHonour", config.unlockXericsHonour()))
		{
			declaredUnlocks.add(Unlock.XERICS_HONOUR);
		}
		if (ShortestPathPlugin.override("unlockDragontoothPassage", config.unlockDragontoothPassage()))
		{
			declaredUnlocks.add(Unlock.DRAGONTOOTH);
		}
		unlocks = Collections.unmodifiableSet(declaredUnlocks);

		// Note: Transport type costs are now managed by transportTypeConfig.getCost()
		costConsumableTeleportationItems = ShortestPathPlugin.override("costConsumableTeleportationItems", config.costConsumableTeleportationItems());
		bankVisitCost = ShortestPathPlugin.override("costBankVisit", config.costBankVisit());

		if (GameState.LOGGED_IN.equals(client.getGameState()))
		{
			isOnSailingBoat = client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT) != 0;

			int i = 0;
			for (; i < Skill.values().length; i++)
			{
				boostedSkillLevelsAndMore[i] = client.getBoostedSkillLevel(Skill.values()[i]);
			}
			boostedSkillLevelsAndMore[i++] = client.getTotalLevel(); // skill total level
			boostedSkillLevelsAndMore[i++] = getCombatLevel(); // combat level
			boostedSkillLevelsAndMore[i] = client.getVarpValue(VarPlayerID.QP); // quest points

			refreshTransports(evaluationTimeMinutes);
		}

		refreshDestinations();
		rebuildAccessibleBankTiles(evaluationTimeMinutes);
	}

	protected long currentTimeMinutes()
	{
		return System.currentTimeMillis() / 60_000L;
	}

	private void refreshDestinations()
	{
		destinations = avoidWilderness ? filteredDestinations : allDestinations;
	}

	private void rebuildAccessibleBankTiles(long evaluationTimeMinutes)
	{
		Set<Integer> bankLocs = destinations.get("bank");
		if (bankLocs == null)
		{
			accessibleBankTiles = Set.of();
			return;
		}
		if (!GameState.LOGGED_IN.equals(client.getGameState()))
		{
			accessibleBankTiles = Set.copyOf(bankLocs);
			return;
		}
		Set<Integer> acc = new HashSet<>(bankLocs.size());
		for (Integer p : bankLocs)
		{
			DestinationRequirements req = bankRequirements.getOrDefault(p, DestinationRequirements.EMPTY);
			if (satisfiesBankDestinationRequirements(req, evaluationTimeMinutes))
			{
				acc.add(p);
			}
		}
		accessibleBankTiles = Collections.unmodifiableSet(acc);
	}

	/**
	 * Quest/skill/var gates for bank tiles (not used for transport overlays).
	 */
	private boolean satisfiesBankDestinationRequirements(DestinationRequirements dr, long evaluationTimeMinutes)
	{
		if (dr == null || dr.isEmpty())
		{
			return true;
		}
		int[] requiredLevels = dr.getSkillLevels();
		for (int i = 0; i < boostedSkillLevelsAndMore.length; i++)
		{
			int need = i < requiredLevels.length ? requiredLevels[i] : 0;
			if (boostedSkillLevelsAndMore[i] < need)
			{
				return false;
			}
		}
		for (Quest quest : dr.getQuests())
		{
			if (!QuestState.FINISHED.equals(getQuestState(quest)))
			{
				return false;
			}
		}
		for (VarRequirement req : dr.getVarbits())
		{
			if (!req.checkValue(client.getVarbitValue(req.getId()), evaluationTimeMinutes))
			{
				return false;
			}
		}
		for (VarRequirement req : dr.getVarPlayers())
		{
			if (!req.checkValue(client.getVarpValue(req.getId()), evaluationTimeMinutes))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Changes to the config might have invalidated some locations, e.g. those in the wilderness
	 */
	public void filterLocations(Set<Integer> locations, boolean canReviveFiltered)
	{
		if (avoidWilderness)
		{
			List<Integer> filteredThisCall = new ArrayList<>(4);
			locations.removeIf(location ->
			{
				boolean inWilderness = WildernessChecker.isInWilderness(location);
				if (inWilderness)
				{
					filteredThisCall.add(location);
				}
				return inWilderness;
			});
			filteredTargets.addAll(filteredThisCall);
			// If we ended up with no valid locations we re-include the
			// locations this call filtered - not targets accumulated while
			// filtering unrelated earlier searches.
			if (locations.isEmpty())
			{
				locations.addAll(filteredThisCall);
			}
		}
		else if (canReviveFiltered)
		{ // Re-include previously filtered locations
			locations.addAll(filteredTargets);
			filteredTargets.clear();
		}
	}

	/**
	 * Returns the user-configured additional cost for a given transport
	 */
	public int getAdditionalTransportCost(Transport transport)
	{
		if (transport.isConsumable() && TransportType.TELEPORTATION_ITEM.equals(transport.getType()))
		{
			return costConsumableTeleportationItems;
		}
		if (transport.isConsumable() && TransportType.QUETZAL_WHISTLE.equals(transport.getType()))
		{
			return transportTypeConfig.getCost(transport.getType()) + costConsumableTeleportationItems;
		}
		return transportTypeConfig.getCost(transport.getType());
	}

	/**
	 * Returns the differential cost for a transport type that shares destinations with another type.
	 * This cost is only applied when the transport is in delayed-visit competition with its partner,
	 * not globally against all other transport types.
	 */
	public int getDifferentialCost(Transport transport)
	{
		if (transport.getType().differentialCostFunction() != null)
		{
			return transport.getType().differentialCostFunction().apply(config);
		}
		return 0;
	}

	static Map<String, Set<Integer>> filterDestinations(Map<String, Set<Integer>> allDestinations)
	{
		Map<String, Set<Integer>> filteredDestinations = new HashMap<>(allDestinations.size());
		for (Map.Entry<String, Set<Integer>> entry : allDestinations.entrySet())
		{
			String destinationType = entry.getKey();
			Set<Integer> usableDestinations = new HashSet<>(entry.getValue().size());
			for (Integer destination : entry.getValue())
			{
				// We filter based on whether the destination is inside or outside wilderness
				if (!WildernessChecker.isInWilderness(destination))
				{
					usableDestinations.add(destination);
				}
			}
			// If all destinations of a destination type have been filtered away then we don't add the entry
			if (!usableDestinations.isEmpty())
			{
				// If no destinations of a destination type have been filtered away then we re-use the same set reference
				filteredDestinations.put(destinationType, usableDestinations);
			}
		}
		return filteredDestinations;
	}

	private void refreshTransports(long evaluationTimeMinutes)
	{
		if (!Thread.currentThread().equals(client.getClientThread()))
		{
			return; // Has to run on the client thread; data will be refreshed when path finding commences
		}
		currentMaxQuestPoints = maximumQuestPoints();

		// Fairy ring staff/diary requirements are enforced by the eligibility snapshot.
		transportTypeConfig.disableUnless(TransportType.FAIRY_RING,
			client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST) > 39);
		transportTypeConfig.disableUnless(TransportType.GNOME_GLIDER,
			QuestState.FINISHED.equals(getQuestState(Quest.THE_GRAND_TREE)));
		transportTypeConfig.disableUnless(TransportType.MAGIC_MUSHTREE,
			QuestState.FINISHED.equals(getQuestState(Quest.BONE_VOYAGE)));
		transportTypeConfig.disableUnless(TransportType.SPIRIT_TREE,
			QuestState.FINISHED.equals(getQuestState(Quest.TREE_GNOME_VILLAGE)));

		refreshSpiritTreeAvailability();

		// The owned items depend only on which containers are included, so the eligibility
		// snapshot collects them once rather than once per transport.
		eligibility = collectEligibility();
		eligibilityStale = false;
		Set<Quest> refreshedQuests = new HashSet<>();
		TransportAvailability.Builder withoutBank = new TransportAvailability.Builder(allTransports.length);
		TransportAvailability.Builder withBank = new TransportAvailability.Builder(allTransports.length);
		for (Transport transport : allTransports)
		{
			for (Quest quest : transport.getQuests())
			{
				if (!refreshedQuests.add(quest))
				{
					continue;
				}
				try
				{
					questStates.put(quest, getQuestState(quest));
				}
				catch (NullPointerException ignored)
				{
				}
			}

			for (VarRequirement varRequirement : transport.getVarRequirements())
			{
				if (varRequirement.isVarbit())
				{
					varbitValues.put(varRequirement.getId(), client.getVarbitValue(varRequirement.getId()));
				}
				else
				{
					varPlayerValues.put(varRequirement.getId(), client.getVarpValue(varRequirement.getId()));
				}
			}

			if (!useTransport(transport, evaluationTimeMinutes))
			{
				continue;
			}

			boolean usableWithoutBank = eligibility.usable(transport, false);
			boolean usableWithBank = eligibility.usable(transport, true);
			if (usableWithoutBank)
			{
				withoutBank.add(transport);
			}
			if (usableWithBank)
			{
				withBank.add(transport);
			}
		}

		withoutBank.remapPohTransports();
		withBank.remapPohTransports();
		transportAvailabilities = new TransportAvailabilities(withoutBank.build(), withBank.build());
	}

	public boolean avoidWilderness(int packedPosition, int packedNeighborPosition, boolean targetInWilderness)
	{
		return avoidWilderness
			&& !targetInWilderness
			&& !WildernessChecker.isInWilderness(packedPosition)
			&& WildernessChecker.isInWilderness(packedNeighborPosition);
	}

	/**
	 * League-mode neighbour gate: parallels {@link #avoidWilderness} but
	 * blocks crossing into the always-blocked Misthalin region. Always
	 * returns {@code false} on non-seasonal worlds so vanilla pathfinding is
	 * unaffected.
	 */
	public boolean avoidBlockedRegion(int packedPosition, int packedNeighborPosition, boolean targetInBlockedRegion)
	{
		if (!leagueModeState.isSeasonal())
		{
			return false;
		}
		return !targetInBlockedRegion
			&& !leagueModeState.isInBlockedRegion(packedPosition)
			&& leagueModeState.isInBlockedRegion(packedNeighborPosition);
	}

	/**
	 * Whether both endpoints of the supplied transport are in unlocked
	 * regions for the current league state. Always-unlocked tiles
	 * (NEUTRAL, Varlamore, Karamja) pass through unchanged on any world.
	 *
	 * <p>If the transport declares a {@link Transport#getRegionOverride()
	 * region override}, it replaces the chunk-classifier result for the
	 * destination endpoint. Used for shortcuts whose destination chunk
	 * sits in a different region than the wiki classifies the shortcut
	 * under (e.g. Trollheim Wilderness climb — destination chunk is
	 * Wilderness, but the shortcut is wiki-listed as Asgarnia).
	 */
	private boolean isTransportRegionAllowed(Transport transport)
	{
		if (!leagueModeState.isSeasonal())
		{
			return true;
		}
		LeagueRegion origin = LeagueRegionChecker.getRegion(transport.getOrigin());
		if (!leagueModeState.isUnlocked(origin))
		{
			return false;
		}
		LeagueRegion destination = transport.getRegionOverride() != null
			? transport.getRegionOverride()
			: LeagueRegionChecker.getRegion(transport.getDestination());
		return leagueModeState.isUnlocked(destination);
	}

	/**
	 * Remaps POH transport destinations to the house landing tile.
	 * Transports that arrive inside the POH (e.g., fairy ring DIQ, spirit tree "Your house")
	 * are remapped so chaining with other POH transports is possible.
	 * Called once at load time since Transport objects in allTransports are shared references.
	 */
	private static Transport[] flatten(Map<Integer, Set<Transport>> transports)
	{
		List<Transport> all = new ArrayList<>();
		for (Set<Transport> set : transports.values())
		{
			all.addAll(set);
		}
		return all.toArray(new Transport[0]);
	}

	private static PrimitiveIntHashMap<Transport[]> buildAllDisplayTransports(Transport[] transports)
	{
		TransportAvailability.Builder builder = new TransportAvailability.Builder(transports.length);
		for (Transport transport : transports)
		{
			builder.add(transport);
		}
		builder.remapPohTransports();
		return builder.build().getDisplayTransports();
	}

	static void remapPohDestinations(Map<Integer, Set<Transport>> transports)
	{
		int pohLanding = WorldPointUtil.packWorldPoint(POH_LANDING_X, POH_LANDING_Y, 0);
		for (Set<Transport> transportSet : transports.values())
		{
			for (Transport transport : transportSet)
			{
				int destination = transport.getDestination();
				int destX = WorldPointUtil.unpackWorldX(destination);
				int destY = WorldPointUtil.unpackWorldY(destination);
				if (destination != pohLanding && ShortestPathPlugin.isInsidePoh(destX, destY))
				{
					transport.setDestination(pohLanding);
				}
			}
		}
	}

	public QuestState getQuestState(Quest quest)
	{
		return quest.getState(client);
	}

	private boolean completedQuests(Transport transport)
	{
		for (Quest quest : transport.getQuests())
		{
			if (!QuestState.FINISHED.equals(questStates.getOrDefault(quest, QuestState.NOT_STARTED)))
			{
				return false;
			}
		}
		return true;
	}

	public boolean varbitChecks(Transport transport, long evaluationTimeMinutes)
	{
		for (VarRequirement varRequirement : transport.getVarbits())
		{
			if (!varRequirement.check(varbitValues, evaluationTimeMinutes))
			{
				return true;
			}
		}
		return false;
	}

	public boolean varPlayerChecks(Transport transport, long evaluationTimeMinutes)
	{
		for (VarRequirement varRequirement : transport.getVarPlayers())
		{
			if (!varRequirement.check(varPlayerValues, evaluationTimeMinutes))
			{
				return true;
			}
		}
		return false;
	}

	private boolean useTransport(Transport transport, long evaluationTimeMinutes)
	{
		// Sailing: suppress teleports while the player is aboard a boat.
		// We don't model sailing navigation, so teleporting away mid-ocean would produce
		// confusing suggestions. Pathfinding resumes normally after disembarking.
		if (isOnSailingBoat && transport.getType().isTeleport())
		{
			return false;
		}

		// Master POH gate - if POH is disabled, reject all POH transports
		if (!usePoh)
		{
			int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
			int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
			int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
			int destY = WorldPointUtil.unpackWorldY(transport.getDestination());
			if (ShortestPathPlugin.isInsidePoh(originX, originY) || ShortestPathPlugin.isInsidePoh(destX, destY))
			{
				return false;
			}
		}

		// League region gate: in seasonal mode, drop transports that touch the
		// always-blocked region or a region the player has not unlocked.
		if (!isTransportRegionAllowed(transport))
		{
			return false;
		}

		final boolean isQuestLocked = transport.isQuestLocked();
		TransportType type = transport.getType();

		// Check if transport type is enabled in config
		if (!transportTypeConfig.isEnabled(type))
		{
			return false;
		}

		// Handle POH variants for types that have them
		if (!checkPohVariant(transport, type))
		{
			return false;
		}

		// Handle special cases for teleportation items and seasonal transports
		if (!checkTeleportationItemRules(transport, type))
		{
			return false;
		}

		// Respawn rows for Prifddinas (and the colliding Lumbridge default) are
		// gated on the declared respawn in config, not on varbits
		if (!checkRespawnGate(transport))
		{
			return false;
		}

		// Pure-unlock requirements are gated on the declared unlock set here,
		// ahead of the item evaluation in the eligibility snapshot — teleportation-item
		// modes can skip that evaluation entirely and must still honour the gate
		if (!checkUnlockGates(transport))
		{
			return false;
		}

		// Handle jewellery box tier filtering
		if (TransportType.TELEPORTATION_BOX.equals(type))
		{
			if (!checkJewelleryBoxTier(transport))
			{
				return false;
			}
		}

		if (!hasRequiredLevels(transport))
		{
			return false;
		}

		if (isQuestLocked && !completedQuests(transport))
		{
			return false;
		}

		if (varbitChecks(transport, evaluationTimeMinutes))
		{
			return false;
		}

		if (varPlayerChecks(transport, evaluationTimeMinutes))
		{
			return false;
		}

		if (TransportType.SPIRIT_TREE.equals(type) || TransportType.SEASONAL_TRANSPORTS.equals(type)
			|| TransportType.TELEPORTATION_ITEM.equals(type))
		{
			return checkPlantedSpiritTrees(transport);
		}

		return true;
	}

	/**
	 * Gates respawn-destination transports on the declared respawn in config.
	 * When the active respawn is Prifddinas every {@code *_SPAWN} varbit reads 0,
	 * which is indistinguishable from the Lumbridge default, so the config option
	 * resolves the ambiguity in both directions.
	 */
	private boolean checkRespawnGate(Transport transport)
	{
		if (!transport.hasDisplayInfo("Respawn"))
		{
			return true;
		}
		int destination = transport.getDestination();
		if (destination == PRIFDDINAS_RESPAWN)
		{
			return respawnPrifddinas;
		}
		if (destination == LUMBRIDGE_RESPAWN)
		{
			return !respawnPrifddinas;
		}
		return true;
	}

	/**
	 * Gates transports carrying a pure-unlock item requirement (every OR branch
	 * of the requirement is an unlock token) on the declared unlock set. Item
	 * pools can never satisfy such a requirement, and item evaluation is not the
	 * only path a transport can take to be counted usable — teleportation-item
	 * modes skip it entirely — so the gate is checked here for every transport.
	 */
	private boolean checkUnlockGates(Transport transport)
	{
		// The POH Honour teleportation box has no Items column to carry the
		// unlock term, so it is gated here by its display info, the same
		// singleton pattern as checkRespawnGate.
		if (TransportType.TELEPORTATION_BOX.equals(transport.getType())
			&& transport.hasDisplayInfo("Honour")
			&& !unlocks.contains(Unlock.XERICS_HONOUR))
		{
			return false;
		}
		TransportItems itemRequirements = transport.getItemRequirements();
		if (itemRequirements == null)
		{
			return true;
		}
		for (ItemRequirement requirement : itemRequirements.getRequirements())
		{
			if (!requirement.isPureUnlock())
			{
				continue;
			}
			boolean declared = false;
			for (ItemRequirement.Branch branch : requirement.getBranches())
			{
				if (unlocks.contains(branch.getUnlock()))
				{
					declared = true;
					break;
				}
			}
			if (!declared)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Resolves the effective {@link #availableSpiritTrees} from every detection
	 * source: a live in-region {@code FARMING_TRANSMIT_*} varbit sample, the
	 * plugin-maintained patch state (RSProfile persistence + menu union), or —
	 * when no patch-state helper is wired (tests, dashboard harness) — the live
	 * sample alone. An empty resolved set means every observed patch reported
	 * unusable and blocks planted-tree transports honestly; the unresolved
	 * {@code null} is kept while no source has produced any observation.
	 * <p>
	 * Client thread only, called from {@link #refreshTransports}.
	 */
	private void refreshSpiritTreeAvailability()
	{
		String inRegionPatch = null;
		Player localPlayer = client.getLocalPlayer();
		// Varbits are not transmitted while a modal widget is open; skip the
		// live sample (but not the patch-state resolution below) rather than
		// attribute a stale shared-slot value to the wrong patch. On the
		// region-entry tick the slot can likewise still carry the previous
		// region's values, so sample only once the region has settled.
		if (localPlayer != null && !SpiritTreePatchState.modalWidgetOpen(client))
		{
			WorldPoint worldLocation = localPlayer.getWorldLocation();
			if (worldLocation != null
				&& (spiritTreePatchState == null
					|| spiritTreePatchState.isRegionSettled(worldLocation.getRegionID())))
			{
				inRegionPatch = SpiritTreePatchState.patchNameForRegion(worldLocation.getRegionID());
			}
		}

		if (spiritTreePatchState != null)
		{
			if (inRegionPatch != null)
			{
				// In-region sample is authoritative for this patch — a non-20
				// read evicts any stale persisted or menu-derived positive.
				spiritTreePatchState.applyVarbitSample(inRegionPatch,
					client.getVarbitValue(SpiritTreePatchState.varbitForPatch(inRegionPatch)));
			}
			Set<String> resolved = spiritTreePatchState.getTravelableTreesOrNull();
			if (resolved != null)
			{
				availableSpiritTrees = resolved;
			}
		}
		else if (inRegionPatch != null)
		{
			int varbitValue = client.getVarbitValue(SpiritTreePatchState.varbitForPatch(inRegionPatch));
			Set<String> resolved = availableSpiritTrees == null
				? new HashSet<>() : new HashSet<>(availableSpiritTrees);
			if (SpiritTreePatchState.spiritTreeTravelable(varbitValue))
			{
				resolved.add(inRegionPatch);
			}
			else
			{
				resolved.remove(inRegionPatch);
			}
			availableSpiritTrees = resolved;
		}
	}

	private boolean checkPlantedSpiritTrees(Transport transport)
	{
		int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
		int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());

		// Check planted spirit tree origins (travel FROM a planted tree)
		if (isPlantedSpiritTreeAllowed(originX, originY))
		{
			return false;
		}

		// Check planted spirit tree destinations (travel TO a planted tree)
		int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
		int destY = WorldPointUtil.unpackWorldY(transport.getDestination());

		return !isPlantedSpiritTreeAllowed(destX, destY);
	}

	/**
	 * Checks POH-specific transport variants (fairy ring, spirit tree, obelisk inside POH).
	 * Returns false if the transport is a POH variant and that variant is disabled.
	 */
	private boolean checkPohVariant(Transport transport, TransportType type)
	{
		int originX = WorldPointUtil.unpackWorldX(transport.getOrigin());
		int originY = WorldPointUtil.unpackWorldY(transport.getOrigin());
		int destX = WorldPointUtil.unpackWorldX(transport.getDestination());
		int destY = WorldPointUtil.unpackWorldY(transport.getDestination());

		if (!ShortestPathPlugin.isInsidePoh(originX, originY) && !ShortestPathPlugin.isInsidePoh(destX, destY))
		{
			return true; // Not a POH transport
		}

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
			return isPohNexusPortalEnabled(enabledPohNexusPortals, transport.getDisplayInfo());
		}

		return true;
	}

	static boolean isPohNexusPortalEnabled(Set<PohNexusPortal> enabledPortals, String displayInfo)
	{
		PohNexusPortal portal = PohNexusPortal.fromDisplayInfo(displayInfo);
		return portal == null || enabledPortals.contains(portal);
	}

	static boolean isPohMountedItemEnabled(Set<PohMountedItem> enabledItems, String objectInfo)
	{
		PohMountedItem item = PohMountedItem.fromObjectInfo(objectInfo);
		return item == null || enabledItems.contains(item);
	}

	/**
	 * Checks teleportation item rules (consumable vs non-consumable, inventory settings).
	 * Returns false if the transport should be filtered out based on teleportation item settings.
	 */
	private boolean checkTeleportationItemRules(Transport transport, TransportType type)
	{
		if (!TransportType.TELEPORTATION_ITEM.equals(type)
			&& !TransportType.SEASONAL_TRANSPORTS.equals(type)
			&& !TransportType.QUETZAL_WHISTLE.equals(type))
		{
			return true; // Not a teleportation item type
		}

		// Seasonal transports only exist on seasonal worlds; a lingering config
		// toggle must not leak them into normal worlds.
		if (TransportType.SEASONAL_TRANSPORTS.equals(type) && !leagueModeState.isSeasonal())
		{
			return false;
		}

		// Mode-locked items (e.g. the Deadman-only Trinket of fairies) can never
		// be obtained on other world types, even when the ALL/UNLOCKED settings
		// bypass the inventory check.
		if (!leagueModeState.isDeadman() && requiresModeLockedItem(transport, DEADMAN_ONLY_ITEM_IDS))
		{
			return false;
		}

		switch (transportTypeConfig.getTeleportationItemSetting())
		{
			case ALL:
				return true;
			case ALL_NON_CONSUMABLE:
			case UNLOCKED_NON_CONSUMABLE:
			case INVENTORY_NON_CONSUMABLE:
			case INVENTORY_AND_BANK_NON_CONSUMABLE:
				return !transport.isConsumable();
			case UNLOCKED:
				return true; // Ownership is implied by the unlock check; items are never evaluated
			case INVENTORY:
			case INVENTORY_AND_BANK:
				return true; // Will be checked later by the eligibility snapshot
			case NONE:
				return false;
		}
		return true;
	}

	/**
	 * Whether the transport has an item requirement that can only be satisfied
	 * by mode-locked items — every alternative in some requirement branch is in
	 * {@code modeLockedItemIds}. A branch that also lists a normal item keeps
	 * the transport usable on every world.
	 */
	private static boolean requiresModeLockedItem(Transport transport, Set<Integer> modeLockedItemIds)
	{
		TransportItems itemRequirements = transport.getItemRequirements();
		if (itemRequirements == null)
		{
			return false;
		}
		for (int[] alternatives : itemRequirements.getItems())
		{
			boolean allLocked = alternatives.length > 0;
			for (int itemId : alternatives)
			{
				if (!modeLockedItemIds.contains(itemId))
				{
					allLocked = false;
					break;
				}
			}
			if (allLocked)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if a TELEPORTATION_BOX transport should be used based on POH settings.
	 * Handles jewellery box tiers and mounted items.
	 */
	private boolean checkJewelleryBoxTier(Transport transport)
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
			if (PohMountedItem.GLORY.equals(mountedItem) && JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier))
			{
				return false;
			}
			return isPohMountedItemEnabled(enabledPohMountedItems, objectInfo);
		}

		// Filter jewellery boxes by tier
		if (JewelleryBoxTier.NONE.equals(pohJewelleryBoxTier))
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
			return JewelleryBoxTier.FANCY.equals(pohJewelleryBoxTier) ||
				JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier);
		}

		// Ornate box (37520): destinations K-R
		if (objectInfo.contains("Ornate Jewellery Box 37520"))
		{
			return JewelleryBoxTier.ORNATE.equals(pohJewelleryBoxTier);
		}

		return false;
	}

	/**
	 * Checks if the player has all the required skill levels for the transport
	 */
	private boolean hasRequiredLevels(Transport transport)
	{
		// In leagues some skills are disabled so the max total level is lower than
		// the standard 2376. Holding the item (e.g. Max cape) already proves the
		// player is maxed for the available skills, so skip the total-level check.
		final int totalLevelIndex = Skill.values().length;
		int[] requiredLevels = transport.getSkillLevels();
		for (int i = 0; i < boostedSkillLevelsAndMore.length; i++)
		{
			if (leagueModeState.isSeasonal() && i == totalLevelIndex)
			{
				continue;
			}
			int boostedLevel = boostedSkillLevelsAndMore[i];
			int requiredLevel = requiredLevels[i];
			if (requiredLevel == SkillRequirementParser.MAX_LEVEL)
			{
				requiredLevel = maximumLevel(i);
			}
			if (boostedLevel < requiredLevel)
			{
				return false;
			}
		}
		return true;
	}

	private int maximumLevel(int index)
	{
		if (index < Skill.values().length)
		{
			return MAX_SKILL_LEVEL;
		}
		if (index == Skill.values().length)
		{
			return MAX_SKILL_LEVEL * Skill.values().length;
		}
		if (index == Skill.values().length + 1)
		{
			return 126;
		}
		if (index == Skill.values().length + 2)
		{
			return currentMaxQuestPoints;
		}
		return SkillRequirementParser.MAX_LEVEL;
	}

	private int maximumQuestPoints()
	{
		return client.getDBTableRows(DBTableID.Quest.ID).stream()
			.filter(row -> (Integer) client.getDBTableField(
				row,
				DBTableID.Quest.COL_RELEASE_TYPE,
				0
			)[0] != 0)
			.mapToInt(row -> (Integer) client.getDBTableField(
				row,
				DBTableID.Quest.COL_QUESTPOINTS,
				0
			)[0])
			.sum();
	}

	/**
	 * The transport eligibility snapshot captured at refresh time. When it is missing or
	 * stale (a container change called {@link #invalidateEligibility()}) it is rebuilt,
	 * but only on the client thread — off it the existing (possibly stale or null)
	 * snapshot is returned, so callers must null-guard. Client-thread readers only;
	 * never call from the pathfinder thread.
	 */
	public TransportEligibility getEligibility()
	{
		if ((eligibility == null || eligibilityStale)
			&& Thread.currentThread().equals(client.getClientThread()))
		{
			eligibility = collectEligibility();
			eligibilityStale = false;
		}
		return eligibility;
	}

	/**
	 * Marks the eligibility snapshot stale; the next client-thread {@link #getEligibility()}
	 * call rebuilds it. Called when a bank/inventory/equipment container change arrives;
	 * refreshTransports also rebuilds it directly.
	 */
	public void invalidateEligibility()
	{
		eligibilityStale = true;
	}

	/**
	 * Captures the client state both the pathfinding verdicts and the bank-pickup plans
	 * read: the carried pool (inventory + worn + rune pouch in hand), the bank-path pool
	 * (which adds the bank contents when bank paths are enabled), the bank contents
	 * themselves, the runes inside a banked rune pouch, the fairy-ring staff gate and
	 * the currency threshold.
	 */
	private TransportEligibility collectEligibility()
	{
		Map<Integer, Integer> carriedItems = collectItems(true, true, false, true);
		Map<Integer, Integer> bankPathItems = includeBankPath ? collectItems(true, true, true, true) : carriedItems;
		Map<Integer, Integer> bankHas = new HashMap<>();
		OwnedItems.addContainer(bankHas, bank);
		int bankPouchId = -1;
		for (int pouchId : RUNE_POUCHES)
		{
			if (bankHas.containsKey(pouchId))
			{
				bankPouchId = pouchId;
				break;
			}
		}
		Map<Integer, Integer> bankPouchRunes = bankPouchId == -1
			? Map.of()
			: OwnedItems.runePouchContents(client);
		boolean fairyRingStaffRequired =
			client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1;
		return new TransportEligibility(carriedItems, bankPathItems, bankHas, bankPouchId, bankPouchRunes,
			fairyRingStaffRequired, transportTypeConfig.getTeleportationItemSetting(), currencyThreshold,
			unlocks);
	}

	/**
	 * Item id to quantity over the selected containers, summed across containers.
	 */
	private Map<Integer, Integer> collectItems(
		boolean checkInventory,
		boolean checkEquipment,
		boolean checkBank,
		boolean checkRunePouch)
	{
		Map<Integer, Integer> itemsAndQuantities = new HashMap<>(28 + 11 + 500);

		if (checkInventory)
		{
			OwnedItems.addContainer(itemsAndQuantities, client.getItemContainer(InventoryID.INV));
		}

		if (checkEquipment)
		{
			OwnedItems.addContainer(itemsAndQuantities, client.getItemContainer(InventoryID.WORN));
		}

		if (checkBank)
		{
			TeleportationItem teleportSetting = transportTypeConfig.getTeleportationItemSetting();
			if (TeleportationItem.INVENTORY_AND_BANK.equals(teleportSetting)
				|| TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE.equals(teleportSetting))
			{
				OwnedItems.addContainer(itemsAndQuantities, bank);
			}
		}

		if (checkRunePouch)
		{
			OwnedItems.addRunePouchContents(client, itemsAndQuantities);
		}

		return itemsAndQuantities;
	}

	/**
	 * Calculates the combat level of the player
	 */
	private int getCombatLevel()
	{
		int attack = client.getRealSkillLevel(Skill.ATTACK);
		int strength = client.getRealSkillLevel(Skill.STRENGTH);
		int defence = client.getRealSkillLevel(Skill.DEFENCE);
		int hitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
		int magic = client.getRealSkillLevel(Skill.MAGIC);
		int ranged = client.getRealSkillLevel(Skill.RANGED);
		int prayer = client.getRealSkillLevel(Skill.PRAYER);
		return computeCombatLevel(attack, strength, defence, hitpoints, magic, ranged, prayer);
	}

	private boolean isPlantedSpiritTreeAllowed(int x, int y)
	{
		String treeName = getPlantedSpiritTreeName(x, y);
		if (treeName == null)
		{
			return false; // 
		}
		if (availableSpiritTrees == null)
		{
			return true;
		}
		return !availableSpiritTrees.contains(treeName);
	}
}
