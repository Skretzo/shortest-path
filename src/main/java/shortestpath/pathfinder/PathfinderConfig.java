package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarbitID;
import shortestpath.Destination;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.ShortestPathConfig;
import shortestpath.spirittree.SpiritTreeService;
import shortestpath.settings.ConfigKey;
import shortestpath.settings.EffectiveConfig;
import shortestpath.settings.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.items.ItemStateService;
import shortestpath.leagues.LeagueModeState;
import shortestpath.leagues.LeagueRegion;
import shortestpath.leagues.LeagueRegionChecker;
import shortestpath.pathfinder.astar.PreparedRoutingAccount;
import shortestpath.pathfinder.sailing.SailingMoves;
import shortestpath.poh.PohService;
import shortestpath.requirement.BankPickupRequirements.BankPickupResult;
import shortestpath.requirement.ClientPlayerStateSource;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.requirement.RequirementContext;
import shortestpath.requirement.RequirementHooks;
import shortestpath.requirement.Requirements;
import shortestpath.requirement.RoutingPolicy;
import shortestpath.settings.Settings;
import shortestpath.poh.PohNexusPortal;
import shortestpath.poh.PohMountedItem;
import shortestpath.transport.Transport;
import shortestpath.requirement.TransportEligibility;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;
import shortestpath.transport.TransportTypeConfig;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.requirement.model.Unlock;

@SuppressWarnings("SameParameterValue")
public class PathfinderConfig
{
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
	 * Every tile any loaded transport lands on. Transports can place the player onto
	 * collision-blocked tiles (e.g. fairy rings), so a blocked target is still
	 * potentially reachable when it is a transport destination.
	 */
	private final Set<Integer> transportDestinations;
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
	// Targets filterLocations set aside, with their required items, until they become valid again.
	private final Map<Integer, Target> filteredTargets = new LinkedHashMap<>(4);
	private final Client client;
	private final ShortestPathConfig config;
	/**
	 * The injected settings seam: {@code refresh()} reads effective (override-
	 * applied) values through it. Harness constructors self-wrap the stub config
	 * so override reads are a pure passthrough there.
	 */
	private final Settings settings;
	// Centralized transport type enable/disable config
	private final TransportTypeConfig transportTypeConfig;
	private Map<Integer, Integer> varbitValues = new HashMap<>();
	private Map<Integer, Integer> varPlayerValues = new HashMap<>();
	/**
	 * The single reader of client player state for the capture path. Every
	 * getter throws off the client thread, so a caller that skips the thread
	 * fails loudly instead of silently skipping the refresh.
	 */
	private final PlayerStateSource playerStateSource;
	@Getter
	private final LeagueModeState leagueModeState = new LeagueModeState();
	/**
	 * What the player held at the last {@link #refresh()}, collected on the client thread and read
	 * by searches when they are created.
	 */
	@Getter
	private HeldItems heldItems = HeldItems.NONE;
	// The spirit-tree seam: owns detection, persistence and the published
	// travelable-tree set that each refresh's RequirementContext snapshots.
	// The plugin wires the injected singleton in production; harnesses and
	// tests keep the default detached instance, whose refresh arm merges a
	// live in-region sample into the published set without persistence.
	@Getter private SpiritTreeService spiritTrees = SpiritTreeService.forTesting();

	public void setSpiritTreeService(SpiritTreeService spiritTrees)
	{
		this.spiritTrees = spiritTrees;
	}



	/**
	 * The item-state seam: owns the live open-bank container that feeds the
	 * eligibility capture below. The plugin wires the injected singleton in
	 * production; harnesses and tests keep the default detached instance.
	 */
	private ItemStateService itemState = ItemStateService.forTesting();

	public void setItemStateService(ItemStateService itemState)
	{
		this.itemState = itemState;
	}

	private final BankPickupProjection bankPickupProjection = new BankPickupProjection();

	/**
	 * The bank-pickup projection for the active path — which items the route's
	 * target needs picked up at a bank step. {@code search} supplies the
	 * result the target-item pickup list is derived from.
	 */
	public BankPickupResult getBankPickup(List<PathStep> path, int pathIndex, ActiveSearch search)
	{
		return bankPickupProjection.get(client, itemState, this, path, pathIndex, search);
	}
	/**
	 * Bank tiles the player may use for path banking state (requirements satisfied). Rebuilt in {@link #refresh()}.
	 */
	// Refresh-written state below is read by the pathfinder executor thread;
	// volatile keeps the cross-thread contract explicit. The two availability
	// maps additionally travel together in
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
	 * The per-refresh gate chain, bound to the latest {@link RequirementContext}.
	 * {@link #rebuildAccessibleBankTiles} runs after {@link #refreshTransports}
	 * and evaluates bank destinations through this same chain, so both sides of
	 * a refresh share one snapshot and one definition of requirement
	 * satisfaction. {@code null} until the first successful transport refresh.
	 */
	private Requirements requirements;
	/**
	 * The seam the gate chain reads the protected override hooks through.
	 * {@link ConfigHooks} delegates to {@link #getQuestState},
	 * {@link #varbitChecks} and {@link #varPlayerChecks} so subclass overrides
	 * stay live for transport and bank-destination verdicts alike.
	 */
	private final RequirementHooks requirementHooks = new ConfigHooks();
	/**
	 * Reference that points to either allDestinations or filteredDestinations
	 */
	private volatile Map<String, Set<Integer>> destinations;
	@Getter
	private volatile long calculationCutoffMillis;
	@Getter
	private volatile int unreachableTargetDistance;
	@Getter
	private volatile boolean collisionAwareBlockedTargets;
	@Getter
	private volatile double aStarHeuristicWeight = 1;
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
	/**
	 * On a boat with the option on, search with sailing moves (see {@link SailingMoves}) instead of walking.
	 */
	@Getter
	private volatile boolean sailingMoves;
	/**
	 * Speed sailing moves are built for: the boat's base speed in tiles per tick, or an estimate when it
	 * can't be read. Speed boosts are random and temporary, so they are left out.
	 */
	@Getter
	private volatile double sailingSpeed = SailingMoves.ESTIMATED_SPEED;

	public PathfinderConfig(Client client, ShortestPathConfig config)
	{
		this(client, config, Settings.wrap(config));
	}

	public PathfinderConfig(Client client, ShortestPathConfig config, Settings settings)
	{
		this.client = client;
		this.settings = settings;
		this.playerStateSource = new ClientPlayerStateSource(client);
		this.config = config;
		this.transportTypeConfig = new TransportTypeConfig(config, settings);
		this.mapData = SplitFlagMap.fromResources();
		this.map = ThreadLocal.withInitial(() -> new CollisionMap(mapData));
		Map<Integer, Set<Transport>> loadedTransports = TransportLoader.loadAllFromResources();
		PohService.remapPohDestinations(loadedTransports);
		this.allTransports = flatten(loadedTransports);
		this.transportDestinations = collectTransportDestinations(this.allTransports);
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
		this.settings = Settings.wrap(config);
		this.playerStateSource = new ClientPlayerStateSource(client);
		this.config = config;
		this.transportTypeConfig = new TransportTypeConfig(config, settings);
		this.mapData = mapData;
		this.map = ThreadLocal.withInitial(() -> new CollisionMap(this.mapData));
		this.allTransports = flatten(allTransports);
		this.transportDestinations = collectTransportDestinations(this.allTransports);
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
	 * Pure combat-level formula, extracted for testability; the canonical
	 * implementation lives on {@link PlayerStateSource} so the capture path
	 * and this shim always agree.
	 */
	static int computeCombatLevel(int attack, int strength, int defence, int hitpoints, int magic, int ranged, int prayer)
	{
		return PlayerStateSource.computeCombatLevel(attack, strength, defence, hitpoints, magic, ranged, prayer);
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

	/** Snapshot the already-evaluated account/config state for the A* graph. */
	public PreparedRoutingAccount prepareAStarRoutingAccount(boolean allowTransports)
	{
		return prepareAStarRoutingAccount(allowTransports, false);
	}

	/**
	 * As {@link #prepareAStarRoutingAccount(boolean)}; {@code targetsNeedBank} lets the route visit
	 * a bank even when {@code includeBankPath} is off, for a target whose items are in the bank.
	 * The banked layer's transports still follow the settings, so that alone unlocks no teleports.
	 */
	public PreparedRoutingAccount prepareAStarRoutingAccount(boolean allowTransports, boolean targetsNeedBank)
	{
		return PreparedRoutingAccount.compile(
			getTransportAvailability(false), getTransportAvailability(true), includeBankPath || targetsNeedBank,
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
		// One captured publication for the whole refresh: every effective
		// read below draws from this snapshot, so a republish landing
		// mid-refresh cannot mix fields from two publications.
		EffectiveConfig effective = settings.effective();
		pathfinderBackend = (PathfinderBackend) settings.configuredValue("pathfinderAlgorithm");
		long evaluationTimeMinutes = currentTimeMinutes();
		calculationCutoffMillis = (long) ((Number) settings.configuredValue("calculationCutoff")).intValue() * Constants.GAME_TICK_LENGTH;
		unreachableTargetDistance = effective.num(ConfigKey.UNREACHABLE_TARGET_DISTANCE_THRESHOLD);
		collisionAwareBlockedTargets = effective.bool(ConfigKey.COLLISION_AWARE_BLOCKED_TARGETS);
		// @Range only bounds the config panel, so also clamp overrides to the same 100-300% range.
		aStarHeuristicWeight = Math.max(100, Math.min(300,
			effective.num(ConfigKey.A_STAR_HEURISTIC_WEIGHT))) / 100.0;
		avoidWilderness = effective.bool(ConfigKey.AVOID_WILDERNESS);
		usePoh = effective.bool(ConfigKey.USE_POH);
		leagueModeState.refresh(playerStateSource);

		// Refresh transport type enabled states
		transportTypeConfig.refresh();
		// POH-specific settings
		usePohFairyRing = effective.bool(ConfigKey.USE_POH_FAIRY_RING);
		usePohSpiritTree = effective.bool(ConfigKey.USE_POH_SPIRIT_TREE);
		usePohObelisk = effective.bool(ConfigKey.USE_POH_OBELISK);
		Set<PohNexusPortal> pohNexusPortals = (Set<PohNexusPortal>) settings.configuredValue("pohNexusPortals");
		enabledPohNexusPortals = pohNexusPortals == null ? Set.of() : Set.copyOf(pohNexusPortals);
		Set<PohMountedItem> pohMountedItems = (Set<PohMountedItem>) settings.configuredValue("pohMountedItems");
		enabledPohMountedItems = pohMountedItems == null ? Set.of() : Set.copyOf(pohMountedItems);
		pohJewelleryBoxTier = effective.pohJewelleryBoxTier();

		// Other settings (useTeleportationItems is now managed by transportTypeConfig)
		currencyThreshold = effective.num(ConfigKey.CURRENCY_THRESHOLD);
		// Banked teleport items are only usable from the bankVisited path state, so a
		// mode that collects bank contents must also enable bank-path traversal —
		// otherwise the banked items are gathered but can never be offered.
		TeleportationItem teleportationItemSetting = transportTypeConfig.getTeleportationItemSetting();
		includeBankPath = effective.bool(ConfigKey.INCLUDE_BANK_PATH)
			|| TeleportationItem.INVENTORY_AND_BANK.equals(teleportationItemSetting)
			|| TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE.equals(teleportationItemSetting);
		respawnPrifddinas = effective.bool(ConfigKey.RESPAWN_PRIFDDINAS);

		// Unlocks come from detected account state or config declarations.
		Set<Unlock> declaredUnlocks = EnumSet.noneOf(Unlock.class);
		if (effective.bool(ConfigKey.UNLOCK_CANOE_AXE))
		{
			declaredUnlocks.add(Unlock.CANOE_AXE);
		}
		if (effective.bool(ConfigKey.UNLOCK_XERICS_HONOUR))
		{
			declaredUnlocks.add(Unlock.XERICS_HONOUR);
		}
		if (effective.bool(ConfigKey.UNLOCK_DRAGONTOOTH_PASSAGE))
		{
			declaredUnlocks.add(Unlock.DRAGONTOOTH);
		}
		if (client.getVarbitValue(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE) == 1)
		{
			declaredUnlocks.add(Unlock.KARAMJA_ELITE);
		}
		if (effective.bool(ConfigKey.UNLOCK_MOR_UL_REK))
		{
			declaredUnlocks.add(Unlock.MOR_UL_REK);
		}
		unlocks = Collections.unmodifiableSet(declaredUnlocks);

		// Note: Transport type costs are now managed by transportTypeConfig.getCost()
		costConsumableTeleportationItems = effective.num(ConfigKey.COST_CONSUMABLE_TELEPORTATION_ITEMS);
		bankVisitCost = effective.num(ConfigKey.COST_BANK_VISIT);

		// Reset sailing state every refresh: a stale varbit read (e.g. after logging out aboard a
		// boat) must not keep the search sailing when there is no boat
		isOnSailingBoat = false;
		sailingMoves = false;
		sailingSpeed = SailingMoves.ESTIMATED_SPEED;
		if (GameState.LOGGED_IN.equals(playerStateSource.gameState()))
		{
			isOnSailingBoat = playerStateSource.varbit(VarbitID.SAILING_BOARDED_BOAT) != 0;
			sailingMoves = isOnSailingBoat && effective.bool(ConfigKey.USE_SAILING_MOVES);
			// The game stores the base speed in 1/128ths of a tile per tick; a read above any real
			// boat's speed is a stale or mis-scaled varbit rather than a fast boat — the moves it
			// would build are so long nothing can sail them — so estimate instead
			int baseSpeed = playerStateSource.varbit(VarbitID.SAILING_SIDEPANEL_BOAT_BASESPEED);
			double speed = baseSpeed / 128.0;
			sailingSpeed = baseSpeed > 0 && speed <= SailingMoves.MAX_SPEED ? speed : SailingMoves.ESTIMATED_SPEED;
			refreshTransports(evaluationTimeMinutes);
		}

		refreshDestinations();
		rebuildAccessibleBankTiles();
	}

	protected long currentTimeMinutes()
	{
		return System.currentTimeMillis() / 60_000L;
	}

	private void refreshDestinations()
	{
		destinations = avoidWilderness ? filteredDestinations : allDestinations;
	}

	private void rebuildAccessibleBankTiles()
	{
		Set<Integer> bankLocs = destinations.get("bank");
		if (bankLocs == null)
		{
			accessibleBankTiles = Set.of();
			return;
		}
		if (!GameState.LOGGED_IN.equals(playerStateSource.gameState()))
		{
			accessibleBankTiles = Set.copyOf(bankLocs);
			return;
		}
		// Bank destinations are gated by the chain the transport refresh built
		// from this refresh's snapshot; when no snapshot exists yet there is
		// nothing to gate on, matching the logged-out answer.
		Requirements requirements = this.requirements;
		if (requirements == null)
		{
			accessibleBankTiles = Set.copyOf(bankLocs);
			return;
		}
		Set<Integer> acc = new HashSet<>(bankLocs.size());
		for (Integer p : bankLocs)
		{
			DestinationRequirements req = bankRequirements.getOrDefault(p, DestinationRequirements.EMPTY);
			if (requirements.satisfied(req))
			{
				acc.add(p);
			}
		}
		accessibleBankTiles = Collections.unmodifiableSet(acc);
	}

	/**
	 * The targets still valid under the config, which might have invalidated some, e.g. those in the
	 * wilderness. Targets set aside here earlier come back once valid again, required items and all.
	 */
	public Targets filterLocations(Targets targets, boolean canReviveFiltered)
	{
		Targets kept = targets;
		boolean revive = canReviveFiltered;
		if (avoidWilderness)
		{
			List<Integer> valid = new ArrayList<>(targets.size());
			List<Target> filteredThisCall = new ArrayList<>(4);
			for (Target target : targets)
			{
				if (WildernessChecker.isInWilderness(target.getPoint()))
				{
					filteredTargets.put(target.getPoint(), target);
					filteredThisCall.add(target);
				}
				else
				{
					valid.add(target.getPoint());
				}
			}
			kept = targets.restrictTo(valid);
			// If we ended up with no valid locations we re-include the
			// targets this call filtered - not targets accumulated while
			// filtering unrelated earlier searches. An explicitly empty
			// input has nothing to keep and revives nothing.
			if (kept.isEmpty())
			{
				return kept.plus(Targets.of(filteredThisCall));
			}
		}
		if (revive)
		{
			kept = kept.plus(Targets.of(filteredTargets.values()));
			filteredTargets.clear();
		}
		return kept;
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
		if (transport.getType().differentialCostKey() != null)
		{
			return (Integer) settings.configuredValue(transport.getType().differentialCostKey());
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
		// Has to run on the client thread; every capture read below goes
		// through the source, which throws off-thread — this explicit check
		// makes the contract fail at the entry point rather than mid-capture.
		playerStateSource.checkOnClientThread();

		// Fairy ring staff/diary requirements are enforced by the eligibility snapshot.
		transportTypeConfig.disableUnless(TransportType.FAIRY_RING,
			playerStateSource.varbit(VarbitID.FAIRY2_QUEENCURE_QUEST) > 39);
		transportTypeConfig.disableUnless(TransportType.GNOME_GLIDER,
			QuestState.FINISHED.equals(getQuestState(Quest.THE_GRAND_TREE)));
		transportTypeConfig.disableUnless(TransportType.MAGIC_MUSHTREE,
			QuestState.FINISHED.equals(getQuestState(Quest.BONE_VOYAGE)));
		transportTypeConfig.disableUnless(TransportType.SPIRIT_TREE,
			QuestState.FINISHED.equals(getQuestState(Quest.TREE_GNOME_VILLAGE)));

		spiritTrees.refreshAvailability(playerStateSource);

		// The policy snapshot is taken only now — after the disableUnless
		// derivations above — so the chain freezes the effective transport-type
		// enablement, not the raw config view. The service builds it from the
		// same effective values this refresh already computed.
		RoutingPolicy policy = settings.buildRoutingPolicy(transportTypeConfig,
			usePoh, usePohFairyRing, usePohSpiritTree, usePohObelisk,
			enabledPohNexusPortals, enabledPohMountedItems, pohJewelleryBoxTier,
			currencyThreshold, includeBankPath);

		// All player state the checks below read is captured once per refresh in
		// an immutable snapshot, so no check can observe the game mid-refresh.
		RequirementContext context = RequirementContext.capture(playerStateSource, requirementHooks,
			policy, evaluationTimeMinutes, Arrays.asList(allTransports), bankRequirements, itemState.getBank(),
			unlocks, respawnPrifddinas, leagueModeState, spiritTrees.getAvailableSpiritTrees());
		eligibility = context.getEligibility();
		eligibilityStale = false;
		heldItems = eligibility.heldItems();
		varbitValues = context.getVarbitValues();
		varPlayerValues = context.getVarPlayerValues();
		isOnSailingBoat = context.isOnSailingBoat();
		requirements = new Requirements(context, policy, requirementHooks);
		TransportAvailability.Builder withoutBank = new TransportAvailability.Builder(allTransports.length);
		TransportAvailability.Builder withBank = new TransportAvailability.Builder(allTransports.length);
		for (Transport transport : allTransports)
		{
			if (!requirements.usable(transport))
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

	private static Transport[] flatten(Map<Integer, Set<Transport>> transports)
	{
		List<Transport> all = new ArrayList<>();
		for (Set<Transport> set : transports.values())
		{
			all.addAll(set);
		}
		return all.toArray(new Transport[0]);
	}

	private static Set<Integer> collectTransportDestinations(Transport[] transports)
	{
		Set<Integer> destinations = new HashSet<>(transports.length * 2);
		for (Transport transport : transports)
		{
			destinations.add(transport.getDestination());
		}
		return destinations;
	}

	/**
	 * Whether any loaded transport lands on the given tile, regardless of whether the
	 * transport is currently usable by this account.
	 */
	public boolean isTransportDestination(int packedPosition)
	{
		return transportDestinations.contains(packedPosition);
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

	public QuestState getQuestState(Quest quest)
	{
		return playerStateSource.questState(quest);
	}

	public boolean varbitChecks(Transport transport, long evaluationTimeMinutes)
	{
		// Iterate the unified var-requirement set: getVarbits() would
		// materialize a filtered HashSet for every transport in the refresh.
		return varbitChecks(transport.getVarRequirements(), evaluationTimeMinutes);
	}

	/**
	 * Whether any varbit requirement in the collection fails against this
	 * refresh's captured values. Returns {@code true} when a check FAILED —
	 * the polarity the bypass overrides rely on. Bank-destination requirement
	 * sets reach this same seam.
	 */
	public boolean varbitChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes)
	{
		return varbitChecks(requirements, varbitValues, evaluationTimeMinutes);
	}

	/**
	 * Whether any varbit requirement in the collection fails against the
	 * supplied values. The gate chain passes the maps captured into its own
	 * {@link RequirementContext}, so a chain from an earlier refresh keeps
	 * evaluating its own snapshot instead of the live fields.
	 */
	public boolean varbitChecks(Collection<VarRequirement> requirements,
		Map<Integer, Integer> values, long evaluationTimeMinutes)
	{
		for (VarRequirement varRequirement : requirements)
		{
			if (varRequirement.isVarbit() && !varRequirement.check(values, evaluationTimeMinutes))
			{
				return true;
			}
		}
		return false;
	}

	public boolean varPlayerChecks(Transport transport, long evaluationTimeMinutes)
	{
		return varPlayerChecks(transport.getVarRequirements(), evaluationTimeMinutes);
	}

	/**
	 * Whether any varplayer requirement in the collection fails against this
	 * refresh's captured values. Same failure polarity as
	 * {@link #varbitChecks(Collection, long)}.
	 */
	public boolean varPlayerChecks(Collection<VarRequirement> requirements, long evaluationTimeMinutes)
	{
		return varPlayerChecks(requirements, varPlayerValues, evaluationTimeMinutes);
	}

	/**
	 * Whether any varplayer requirement in the collection fails against the
	 * supplied values. Same snapshot binding as
	 * {@link #varbitChecks(Collection, Map, long)}.
	 */
	public boolean varPlayerChecks(Collection<VarRequirement> requirements,
		Map<Integer, Integer> values, long evaluationTimeMinutes)
	{
		for (VarRequirement varRequirement : requirements)
		{
			if (varRequirement.isVarPlayer() && !varRequirement.check(values, evaluationTimeMinutes))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Routes the {@link RequirementHooks} seam the gate chain consumes to the
	 * protected override points above. Dynamic dispatch keeps test overrides
	 * live with no test-side changes — an override of {@link #getQuestState},
	 * {@link #varbitChecks} or {@link #varPlayerChecks} still reaches every
	 * transport and bank-destination verdict.
	 */
	private final class ConfigHooks implements RequirementHooks
	{
		@Override
		public QuestState getQuestState(Quest quest)
		{
			return PathfinderConfig.this.getQuestState(quest);
		}

		@Override
		public boolean varbitChecks(Collection<VarRequirement> requirements,
			Map<Integer, Integer> values, long evaluationTimeMinutes)
		{
			return PathfinderConfig.this.varbitChecks(requirements, values, evaluationTimeMinutes);
		}

		@Override
		public boolean varPlayerChecks(Collection<VarRequirement> requirements,
			Map<Integer, Integer> values, long evaluationTimeMinutes)
		{
			return PathfinderConfig.this.varPlayerChecks(requirements, values, evaluationTimeMinutes);
		}
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
			&& playerStateSource.isOnClientThread())
		{
			eligibility = ItemStateService.collectEligibility(playerStateSource, itemState.getBank(),
				transportTypeConfig.getTeleportationItemSetting(), currencyThreshold,
				includeBankPath, unlocks);
			eligibilityStale = false;
			heldItems = eligibility.heldItems();
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

}
