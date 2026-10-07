package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.Destination;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.JewelleryBoxTier;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.ShortestPathConfig;
import shortestpath.ShortestPathPlugin;
import shortestpath.SpiritTreePatchState;
import static shortestpath.ShortestPathPlugin.POH_LANDING_X;
import static shortestpath.ShortestPathPlugin.POH_LANDING_Y;
import shortestpath.settings.EffectiveConfig;
import shortestpath.settings.TeleportationItem;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.pathfinder.exact.PreparedRoutingAccount;
import shortestpath.requirement.ClientPlayerStateSource;
import shortestpath.requirement.PlayerStateSource;
import shortestpath.requirement.RequirementContext;
import shortestpath.requirement.RequirementHooks;
import shortestpath.requirement.Requirements;
import shortestpath.requirement.RoutingPolicy;
import shortestpath.requirement.TeleportRestriction;
import shortestpath.settings.Settings;
import shortestpath.transport.PohNexusPortal;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.Transport;
import shortestpath.requirement.TransportEligibility;
import shortestpath.transport.TransportLoader;
import shortestpath.transport.TransportType;
import shortestpath.transport.TransportTypeConfig;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.requirement.model.Unlock;

@SuppressWarnings("SameParameterValue")
public class PathfinderConfig
{
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
	public ItemContainer bank = null;
	// Written on the client thread and the plugin's patch-state updates, then
	// snapshotted into each refresh's RequirementContext — volatile keeps the
	// cross-thread contract explicit.
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
	/**
	 * Item ids the player excluded from routing via the hidden
	 * {@code blockedTeleportItems} CSV ({@code id} records), and per-item
	 * tiles-saved threshold overrides from {@code id:N} records. Reparsed on
	 * every {@link #refresh()}; the blocked set gates the candidate set in
	 * {@link shortestpath.requirement.Requirements} while the overrides feed
	 * {@link #getAdditionalTransportCost}.
	 */
	private volatile Set<Integer> blockedItemIds = Set.of();
	private volatile Map<Integer, Integer> itemThresholdOverrides = Map.of();
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
		this.settings = Settings.wrap(config);
		this.playerStateSource = new ClientPlayerStateSource(client);
		this.config = config;
		this.transportTypeConfig = new TransportTypeConfig(config, settings);
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
		// One captured publication for the whole refresh: every effective
		// read below draws from this snapshot, so a republish landing
		// mid-refresh cannot mix fields from two publications.
		EffectiveConfig effective = settings.effective();
		pathfinderBackend = config.pathfinderBackend();
		long evaluationTimeMinutes = currentTimeMinutes();
		calculationCutoffMillis = (long) config.calculationCutoff() * Constants.GAME_TICK_LENGTH;
		unreachableTargetDistance = effective.unreachableTargetDistance();
		// @Range only bounds the config panel, so also clamp overrides to the same 100-300% range.
		exactHeuristicWeight = Math.max(100, Math.min(300,
			effective.exactHeuristicWeight())) / 100.0;
		avoidWilderness = effective.avoidWilderness();
		usePoh = effective.usePoh();
		leagueModeState.refresh(playerStateSource);

		// Refresh transport type enabled states
		transportTypeConfig.refresh();
		// POH-specific settings
		usePohFairyRing = effective.usePohFairyRing();
		usePohSpiritTree = effective.usePohSpiritTree();
		usePohObelisk = effective.usePohObelisk();
		enabledPohNexusPortals = Set.copyOf(config.pohNexusPortals());
		Set<PohMountedItem> pohMountedItems = config.pohMountedItems();
		enabledPohMountedItems = pohMountedItems == null ? Set.of() : Set.copyOf(pohMountedItems);
		pohJewelleryBoxTier = effective.pohJewelleryBoxTier();

		// Other settings (useTeleportationItems is now managed by transportTypeConfig)
		currencyThreshold = effective.currencyThreshold();
		// Banked teleport items are only usable from the bankVisited path state, so a
		// mode that collects bank contents must also enable bank-path traversal —
		// otherwise the banked items are gathered but can never be offered.
		TeleportationItem teleportationItemSetting = transportTypeConfig.getTeleportationItemSetting();
		includeBankPath = effective.includeBankPath()
			|| TeleportationItem.INVENTORY_AND_BANK.equals(teleportationItemSetting)
			|| TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE.equals(teleportationItemSetting);
		respawnPrifddinas = effective.respawnPrifddinas();

		// Declared unlocks: states the game does not expose to the client, toggled in config.
		Set<Unlock> declaredUnlocks = EnumSet.noneOf(Unlock.class);
		if (effective.unlockCanoeAxe())
		{
			declaredUnlocks.add(Unlock.CANOE_AXE);
		}
		if (effective.unlockXericsHonour())
		{
			declaredUnlocks.add(Unlock.XERICS_HONOUR);
		}
		if (effective.unlockDragontoothPassage())
		{
			declaredUnlocks.add(Unlock.DRAGONTOOTH);
		}
		if (effective.unlockBalloonLogBasket())
		{
			declaredUnlocks.add(Unlock.BALLOON_LOG_BASKET);
		}
		unlocks = Collections.unmodifiableSet(declaredUnlocks);

		// Player-declared per-item restrictions: a bare id blocks the item from
		// routing entirely, while id:N pins the tiles-saved threshold used to
		// price transports that reference the id.
		Set<Integer> blockedItems = new HashSet<>();
		Map<Integer, Integer> thresholdOverrides = new HashMap<>();
		TeleportRestriction.parseBlocked(config.blockedTeleportItems(), blockedItems, thresholdOverrides);
		blockedItemIds = Set.copyOf(blockedItems);
		itemThresholdOverrides = Map.copyOf(thresholdOverrides);

		// Note: Transport type costs are now managed by transportTypeConfig.getCost()
		costConsumableTeleportationItems = effective.costConsumableTeleportationItems();
		bankVisitCost = effective.costBankVisit();

		if (GameState.LOGGED_IN.equals(playerStateSource.gameState()))
		{
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
		// A pinned per-item threshold is the whole additional cost for a
		// transport referencing that item — the user declared its exact
		// tiles-saved value, so it replaces type-level and consumable pricing
		// rather than stacking on top of them.
		int thresholdOverride = memberThresholdOverride(transport);
		if (thresholdOverride > 0)
		{
			return thresholdOverride;
		}
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

	/**
	 * Largest {@code id:N} threshold override matching any item id the
	 * transport's requirements reference. Member ids include the staff and
	 * offhand substitutes of each branch — an override pinned on a substitute
	 * (e.g. a staff standing in for a rune) applies to the transport too.
	 * Scoped to the same item-teleport types as the restriction gate;
	 * returns 0 when nothing matches.
	 */
	private int memberThresholdOverride(Transport transport)
	{
		if (itemThresholdOverrides.isEmpty() || !TeleportRestriction.isItemTeleportType(transport.getType()))
		{
			return 0;
		}
		TransportItems itemRequirements = transport.getItemRequirements();
		if (itemRequirements == null)
		{
			return 0;
		}
		int max = 0;
		for (ItemRequirement requirement : itemRequirements.getRequirements())
		{
			for (ItemRequirement.Branch branch : requirement.getBranches())
			{
				max = Math.max(max, maxOverride(branch.getItemIds()));
				max = Math.max(max, maxOverride(branch.getStaffIds()));
				max = Math.max(max, maxOverride(branch.getOffhandIds()));
			}
		}
		return max;
	}

	private int maxOverride(int[] ids)
	{
		int max = 0;
		if (ids != null)
		{
			for (int itemId : ids)
			{
				max = Math.max(max, itemThresholdOverrides.getOrDefault(itemId, 0));
			}
		}
		return max;
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

		refreshSpiritTreeAvailability();

		// The policy snapshot is taken only now — after the disableUnless
		// derivations above — so the chain freezes the effective transport-type
		// enablement, not the raw config view. The service builds it from the
		// same effective values this refresh already computed.
		RoutingPolicy policy = settings.buildRoutingPolicy(transportTypeConfig,
			usePoh, usePohFairyRing, usePohSpiritTree, usePohObelisk,
			enabledPohNexusPortals, enabledPohMountedItems, pohJewelleryBoxTier,
			currencyThreshold, includeBankPath, blockedItemIds, itemThresholdOverrides);

		// All player state the checks below read is captured once per refresh in
		// an immutable snapshot, so no check can observe the game mid-refresh.
		RequirementContext context = RequirementContext.capture(playerStateSource, requirementHooks,
			policy, evaluationTimeMinutes, Arrays.asList(allTransports), bankRequirements, bank,
			unlocks, respawnPrifddinas, leagueModeState, availableSpiritTrees);
		eligibility = context.getEligibility();
		eligibilityStale = false;
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
		WorldPoint worldLocation = playerStateSource.localPlayerWorldLocation();
		// Varbits are not transmitted while a modal widget is open; skip the
		// live sample (but not the patch-state resolution below) rather than
		// attribute a stale shared-slot value to the wrong patch. On the
		// region-entry tick the slot can likewise still carry the previous
		// region's values, so sample only once the region has settled.
		if (worldLocation != null && !playerStateSource.modalWidgetOpen()
			&& (spiritTreePatchState == null
				|| spiritTreePatchState.isRegionSettled(worldLocation.getRegionID())))
		{
			inRegionPatch = SpiritTreePatchState.patchNameForRegion(worldLocation.getRegionID());
		}

		if (spiritTreePatchState != null)
		{
			if (inRegionPatch != null)
			{
				// In-region sample is authoritative for this patch — a non-20
				// read evicts any stale persisted or menu-derived positive.
				spiritTreePatchState.applyVarbitSample(inRegionPatch,
					playerStateSource.varbit(SpiritTreePatchState.varbitForPatch(inRegionPatch)));
			}
			Set<String> resolved = spiritTreePatchState.getTravelableTreesOrNull();
			if (resolved != null)
			{
				availableSpiritTrees = resolved;
			}
		}
		else if (inRegionPatch != null)
		{
			int varbitValue = playerStateSource.varbit(SpiritTreePatchState.varbitForPatch(inRegionPatch));
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
			eligibility = RequirementContext.collectEligibility(playerStateSource, bank,
				transportTypeConfig.getTeleportationItemSetting(), currencyThreshold,
				includeBankPath, unlocks);
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

}
