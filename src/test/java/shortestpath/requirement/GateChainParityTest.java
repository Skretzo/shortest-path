package shortestpath.requirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.ItemID;
import org.junit.Assert;
import org.junit.Test;

import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.leagues.LeagueRegion;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
import shortestpath.requirement.model.Unlock;
import shortestpath.requirement.model.VarCheckType;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.settings.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Record-first golden parity matrix over the composite requirement surface.
 * Each transport fixture records {@code check()}'s {@link RejectionReason}
 * plus {@code usable(CARRIED)} and {@code usable(BANKED)} on the eligibility
 * snapshot the context carries; each bank fixture records
 * {@code check(DestinationRequirements)} plus {@code satisfied}. The
 * committed goldens are the oracle the requirement-middleware consolidation
 * must reproduce verbatim — a verdict that drifts reads as a line diff.
 *
 * <p>The grid is deliberately non-vacuous: every {@link TeleportationItem}
 * mode crosses {@code isConsumable} in both directions across three pool
 * states, the divergent {@code usable} tails are pinned individually (the
 * fairy-ring Dramen-staff rule, the balloon log-basket grant, the currency
 * threshold, the item-teleport type set versus the planted-spirit-tree
 * triple), the per-gate rejection reasons each appear, and the null/empty
 * edges record their pass-through verdicts.
 *
 * <p>Fixtures run on the keyed/value substrate — real
 * {@link RequirementContext}/{@link RoutingPolicy}/{@link TransportEligibility}
 * values built through {@link RequirementTestFixtures} and
 * {@link TestRequirementHooks}. The eligibility instance injected into the
 * context is the same object the {@code usable} columns are read from, so
 * the {@code check} and {@code usable} verdicts can never describe different
 * pools. No Mockito — nothing in the chain is an interface boundary a mock
 * could falsy-default.
 */
public class GateChainParityTest
{
	private static final int FALADOR = WorldPointUtil.packWorldPoint(2965, 3378, 0);
	private static final int VARROCK = WorldPointUtil.packWorldPoint(3213, 3424, 0);
	private static final int POH = WorldPointUtil.packWorldPoint(1858, 7051, 0);
	// A real balloon landing (Entrana, 2808 3354 0) so the basket grant is
	// destination-scoped the way the TSV rows are.
	private static final int BALLOON_DEST = WorldPointUtil.packWorldPoint(2808, 3354, 0);
	private static final int BANK_TILE = WorldPointUtil.packWorldPoint(1804, 9501, 0);

	private static final TeleportationItem MODE = TeleportationItem.INVENTORY;
	private static final int LAW_RUNE = ItemID.LAWRUNE;
	private static final int COINS = ItemID.COINS;
	private static final int DRAMEN_STAFF = ItemID.DRAMEN_STAFF;
	private static final int LOGS = ItemID.LOGS;

	private static Transport.TransportBuilder transport(TransportType type)
	{
		return new Transport.TransportBuilder().type(type).origin(FALADOR).destination(VARROCK);
	}

	private static ItemRequirement.Branch branch(int... itemIds)
	{
		return new ItemRequirement.Branch(itemIds, null, null, 1);
	}

	private static ItemRequirement.Branch unlockBranch(Unlock unlock)
	{
		return new ItemRequirement.Branch(null, null, null, 1, unlock);
	}

	private static ItemRequirement item(int itemId)
	{
		return new ItemRequirement(new int[]{itemId}, null, null, 1);
	}

	private static ItemRequirement item(int itemId, int quantity)
	{
		return new ItemRequirement(new int[]{itemId}, null, null, quantity);
	}

	private static TransportItems items(ItemRequirement... requirements)
	{
		return new TransportItems(List.of(requirements));
	}

	/**
	 * The verdict line one transport fixture records: the gate chain's first
	 * rejection, the pool verdicts the same eligibility snapshot reports,
	 * and the {@code gates()} prefix verdict. The line also asserts the
	 * post-split composite invariant: {@code check} is {@code NONE} exactly
	 * when the gate prefix passes and at least one item pool satisfies.
	 */
	private static String verdict(String id, Requirements requirements,
		TransportEligibility eligibility, Transport transport)
	{
		RejectionReason gates = requirements.gates(transport);
		boolean carried = eligibility.usable(transport, BankVisitState.CARRIED);
		boolean banked = eligibility.usable(transport, BankVisitState.BANKED);
		RejectionReason check = requirements.check(transport);
		Assert.assertEquals(id,
			check == RejectionReason.NONE,
			gates == RejectionReason.NONE && (carried || banked));
		return id
			+ " | check=" + check
			+ " | carried=" + carried
			+ " | banked=" + banked
			+ " | gates=" + gates;
	}

	/** The bank-side counterpart: {@code check} reason plus the {@code satisfied} adapter. */
	private static String bankVerdict(String id, Requirements requirements,
		DestinationRequirements destinationRequirements)
	{
		return id
			+ " | check=" + requirements.check(destinationRequirements)
			+ " | satisfied=" + requirements.satisfied(destinationRequirements);
	}

	/**
	 * The widest {@link TransportEligibility} constructor, exposing the knobs
	 * the divergent {@code usable} tails read — fairy-ring staff rule,
	 * currency threshold, declared unlocks and the balloon basket set.
	 */
	private static TransportEligibility eligibility(boolean staffRequired,
		TeleportationItem mode, Map<Integer, Integer> carried, Map<Integer, Integer> bankPath,
		int currencyThreshold, Set<Unlock> unlocks, Set<Integer> basketDestinations)
	{
		return new TransportEligibility(carried, bankPath, Map.of(), -1, Map.of(),
			staffRequired, mode, currencyThreshold, unlocks, true, basketDestinations);
	}

	private static LeagueModeState seasonal(LeagueRegion... unlocked)
	{
		LeagueModeState state = new LeagueModeState();
		// setForTest translates null to an empty EnumSet; Set.of() is empty
		// here, which EnumSet.copyOf rejects.
		state.setForTest(true, unlocked.length == 0 ? null : Set.of(unlocked));
		return state;
	}

	/**
	 * The real capture path: quest states flow through the
	 * {@link TestRequirementHooks#questState} knob into the context's
	 * captured map.
	 */
	private static RequirementContext capture(MapPlayerStateSource source, TestRequirementHooks hooks,
		List<Transport> transports, Map<Integer, List<DestinationRequirements>> bankRequirements)
	{
		return RequirementContext.capture(source, hooks, RequirementTestFixtures.policy(MODE), 0L,
			transports, bankRequirements, null, Set.of(), false, new LeagueModeState(), null, Set.of());
	}

	// ------------------------------------------------------------------
	// Mode × consumable × pool grid — the cells the two divergent mode
	// switches must agree on through every later consolidation step.
	// ------------------------------------------------------------------

	private static List<String> modeGrid(Map<Integer, Integer> carried, Map<Integer, Integer> bankPath)
	{
		List<String> lines = new ArrayList<>();
		for (TeleportationItem mode : TeleportationItem.values())
		{
			for (boolean consumable : new boolean[]{true, false})
			{
				Transport transport = transport(TransportType.TELEPORTATION_ITEM)
					.isConsumable(consumable)
					.itemRequirements(items(item(LAW_RUNE)))
					.build();
				TransportEligibility eligibility = bankPath == null
					? RequirementTestFixtures.eligibility(mode, carried)
					: RequirementTestFixtures.bankedEligibility(mode, carried, bankPath);
				RequirementContext context = RequirementTestFixtures.context()
					.eligibility(eligibility).build();
				lines.add(verdict(mode + "/" + (consumable ? "consumable" : "permanent"),
					RequirementTestFixtures.requirements(context, RequirementTestFixtures.policy(mode)),
					eligibility, transport));
			}
		}
		return lines;
	}

	@Test
	public void modesWithCarriedPool()
	{
		// The carried pool satisfies the single-rune requirement, so the
		// deferring modes pass on both pools.
		GateChainParityGolden.compareOrRegen("modes-carried-pool",
			modeGrid(Map.of(LAW_RUNE, 1), null));
	}

	@Test
	public void modesWithBankedPoolOnly()
	{
		// Only the bank-path pool satisfies — the carried verdict splits off
		// while check() still passes on the banked arm.
		GateChainParityGolden.compareOrRegen("modes-banked-pool",
			modeGrid(Map.of(), Map.of(LAW_RUNE, 1)));
	}

	@Test
	public void modesWithNoPool()
	{
		// Neither pool satisfies: BYPASS modes stay usable, EVALUATE_ITEMS
		// modes report ITEM_REQUIREMENT, REJECT modes report TELEPORT_MODE.
		GateChainParityGolden.compareOrRegen("modes-no-pool",
			modeGrid(Map.of(), null));
	}

	// ------------------------------------------------------------------
	// usable()'s divergent tails — everything after the mode switch.
	// ------------------------------------------------------------------

	@Test
	public void usableFairyRingTails()
	{
		// The Dramen/Lunar-staff rule rides the injected requirement on the
		// wrapped ring — staff-required with no staff fails both pools even
		// when the bank path could supply one, and the not-required case
		// passes with empty pools.
		Transport fairyRing = transport(TransportType.FAIRY_RING).build();
		List<String> lines = new ArrayList<>();

		TransportEligibility staffAbsent = eligibility(true, TeleportationItem.NONE,
			Map.of(), Map.of(), Integer.MAX_VALUE, Set.of(), Set.of());
		lines.add(verdict("fairy-ring/staff-required-absent",
			RequirementTestFixtures.requirements(
				RequirementTestFixtures.context().eligibility(staffAbsent).build(),
				RequirementTestFixtures.policy(TeleportationItem.NONE)),
			staffAbsent, staffAbsent.wrapFairyRingStaff(fairyRing)));

		TransportEligibility staffCarried = eligibility(true, TeleportationItem.NONE,
			Map.of(DRAMEN_STAFF, 1), Map.of(DRAMEN_STAFF, 1), Integer.MAX_VALUE, Set.of(), Set.of());
		lines.add(verdict("fairy-ring/staff-required-carried",
			RequirementTestFixtures.requirements(
				RequirementTestFixtures.context().eligibility(staffCarried).build(),
				RequirementTestFixtures.policy(TeleportationItem.NONE)),
			staffCarried, staffCarried.wrapFairyRingStaff(fairyRing)));

		TransportEligibility staffBanked = eligibility(true, TeleportationItem.NONE,
			Map.of(), Map.of(DRAMEN_STAFF, 1), Integer.MAX_VALUE, Set.of(), Set.of());
		lines.add(verdict("fairy-ring/staff-required-banked-only",
			RequirementTestFixtures.requirements(
				RequirementTestFixtures.context().eligibility(staffBanked).build(),
				RequirementTestFixtures.policy(TeleportationItem.NONE)),
			staffBanked, staffBanked.wrapFairyRingStaff(fairyRing)));

		TransportEligibility noStaff = eligibility(false, TeleportationItem.NONE,
			Map.of(), Map.of(), Integer.MAX_VALUE, Set.of(), Set.of());
		lines.add(verdict("fairy-ring/staff-not-required",
			RequirementTestFixtures.requirements(
				RequirementTestFixtures.context().eligibility(noStaff).build(),
				RequirementTestFixtures.policy(TeleportationItem.NONE)),
			noStaff, noStaff.wrapFairyRingStaff(fairyRing)));

		GateChainParityGolden.compareOrRegen("usable-fairy-ring", lines);
	}

	@Test
	public void usableBalloonBasketTails()
	{
		Transport balloon = transport(TransportType.HOT_AIR_BALLOON)
			.destination(BALLOON_DEST)
			.itemRequirements(items(new ItemRequirement(List.of(
				branch(LOGS), unlockBranch(Unlock.BALLOON_LOG_BASKET)))))
			.build();
		List<String> lines = new ArrayList<>();

		TransportEligibility stocked = eligibility(false, MODE, Map.of(), Map.of(),
			Integer.MAX_VALUE, Set.of(), Set.of(BALLOON_DEST));
		RequirementContext stockedContext = RequirementTestFixtures.context()
			.eligibility(stocked).build();
		lines.add(verdict("balloon/basket-stocked-no-log",
			RequirementTestFixtures.requirements(stockedContext, RequirementTestFixtures.policy(MODE)),
			stocked, balloon));

		TransportEligibility unstocked = eligibility(false, MODE, Map.of(), Map.of(),
			Integer.MAX_VALUE, Set.of(), Set.of());
		RequirementContext unstockedContext = RequirementTestFixtures.context()
			.eligibility(unstocked).build();
		lines.add(verdict("balloon/basket-unstocked-no-log",
			RequirementTestFixtures.requirements(unstockedContext, RequirementTestFixtures.policy(MODE)),
			unstocked, balloon));

		TransportEligibility carried = eligibility(false, MODE, Map.of(LOGS, 1), Map.of(LOGS, 1),
			Integer.MAX_VALUE, Set.of(), Set.of());
		RequirementContext carriedContext = RequirementTestFixtures.context()
			.eligibility(carried).build();
		lines.add(verdict("balloon/basket-unstocked-log-carried",
			RequirementTestFixtures.requirements(carriedContext, RequirementTestFixtures.policy(MODE)),
			carried, balloon));

		GateChainParityGolden.compareOrRegen("usable-balloon-basket", lines);
	}

	@Test
	public void usableCurrencyThresholdTails()
	{
		Transport fare = transport(TransportType.TRANSPORT)
			.itemRequirements(items(item(COINS, 5)))
			.build();
		List<String> lines = new ArrayList<>();

		TransportEligibility unbounded = eligibility(false, MODE, Map.of(COINS, 100), Map.of(COINS, 100),
			Integer.MAX_VALUE, Set.of(), Set.of());
		RequirementContext unboundedContext = RequirementTestFixtures.context()
			.eligibility(unbounded).build();
		lines.add(verdict("currency/under-threshold",
			RequirementTestFixtures.requirements(unboundedContext, RequirementTestFixtures.policy(MODE)),
			unbounded, fare));

		TransportEligibility capped = eligibility(false, MODE, Map.of(COINS, 100), Map.of(COINS, 100),
			4, Set.of(), Set.of());
		RequirementContext cappedContext = RequirementTestFixtures.context()
			.eligibility(capped).build();
		lines.add(verdict("currency/over-threshold",
			RequirementTestFixtures.requirements(cappedContext, RequirementTestFixtures.policy(MODE)),
			capped, fare));

		GateChainParityGolden.compareOrRegen("usable-currency-threshold", lines);
	}

	@Test
	public void usableTypeSetBoundaries()
	{
		List<String> lines = new ArrayList<>();

		// A plain transport under NONE proves the mode dispatch only reaches
		// the item-teleport set.
		Transport plain = transport(TransportType.TRANSPORT)
			.itemRequirements(items(item(LAW_RUNE)))
			.build();
		TransportEligibility plainPool = RequirementTestFixtures.eligibility(
			TeleportationItem.NONE, Map.of(LAW_RUNE, 1));
		RequirementContext plainContext = RequirementTestFixtures.context()
			.eligibility(plainPool).build();
		lines.add(verdict("plain-transport/none-mode",
			RequirementTestFixtures.requirements(plainContext,
				RequirementTestFixtures.policy(TeleportationItem.NONE)),
			plainPool, plain));

		// SEASONAL_TRANSPORTS is inside the item-teleport set: usable()
		// bypasses under a non-consumable mode while check() still reports
		// SEASONAL_WORLD on a non-seasonal world — the seasonal gate lives on
		// the chain side only, and this line is the pin for that asymmetry.
		Transport seasonalTransport = transport(TransportType.SEASONAL_TRANSPORTS).build();
		TransportEligibility seasonalPool = RequirementTestFixtures.eligibility(
			TeleportationItem.ALL_NON_CONSUMABLE, Map.of());
		RequirementContext seasonalContext = RequirementTestFixtures.context()
			.eligibility(seasonalPool).build();
		lines.add(verdict("seasonal/nonconsumable-bypass-on-normal-world",
			RequirementTestFixtures.requirements(seasonalContext,
				RequirementTestFixtures.policy(TeleportationItem.ALL_NON_CONSUMABLE)),
			seasonalPool, seasonalTransport));

		// QUETZAL_WHISTLE shares the item-teleport set.
		Transport whistle = transport(TransportType.QUETZAL_WHISTLE).build();
		lines.add(verdict("quetzal-whistle/nonconsumable-bypass",
			RequirementTestFixtures.requirements(seasonalContext,
				RequirementTestFixtures.policy(TeleportationItem.ALL_NON_CONSUMABLE)),
			seasonalPool, whistle));

		// SPIRIT_TREE is outside the item-teleport set (it belongs to the
		// planted-tree triple instead) — NONE must not reject it.
		Transport spiritTree = transport(TransportType.SPIRIT_TREE).build();
		lines.add(verdict("spirit-tree/none-mode",
			RequirementTestFixtures.requirements(plainContext,
				RequirementTestFixtures.policy(TeleportationItem.NONE)),
			plainPool, spiritTree));

		GateChainParityGolden.compareOrRegen("usable-item-teleport-types", lines);
	}

	// ------------------------------------------------------------------
	// Gate verdicts — each rejection reason appears at least once.
	// ------------------------------------------------------------------

	@Test
	public void gateVarVerdicts()
	{
		List<String> lines = new ArrayList<>();

		Transport varbit = transport(TransportType.TRANSPORT).varbits("4481=1").build();
		TransportEligibility pool = RequirementTestFixtures.eligibility(MODE, Map.of());
		RequirementContext passing = RequirementTestFixtures.context()
			.eligibility(pool).varbitValues(Map.of(4481, 1)).build();
		lines.add(verdict("varbit/pass",
			RequirementTestFixtures.requirements(passing, RequirementTestFixtures.policy(MODE)),
			pool, varbit));
		lines.add(verdict("varbit/fail",
			RequirementTestFixtures.requirements(passing, RequirementTestFixtures.policy(MODE),
				new TestRequirementHooks().failVarbits(true)),
			pool, varbit));

		Transport varplayer = transport(TransportType.TRANSPORT).varPlayers("4130>1999").build();
		RequirementContext passingVarp = RequirementTestFixtures.context()
			.eligibility(pool).varPlayerValues(Map.of(4130, 2000)).build();
		lines.add(verdict("varplayer/pass",
			RequirementTestFixtures.requirements(passingVarp, RequirementTestFixtures.policy(MODE)),
			pool, varplayer));
		lines.add(verdict("varplayer/fail",
			RequirementTestFixtures.requirements(passingVarp, RequirementTestFixtures.policy(MODE),
				new TestRequirementHooks().failVarPlayers(true)),
			pool, varplayer));

		// A varbit requirement on a seasonal transport under a deferring
		// mode — the var gate sits after the mode dispatch, so on a seasonal
		// world the transport reaches it.
		Transport seasonalVarbit = transport(TransportType.SEASONAL_TRANSPORTS)
			.origin(POH).destination(POH)
			.varbits("4481=1")
			.build();
		RequirementContext seasonalContext = RequirementTestFixtures.context()
			.eligibility(pool)
			.varbitValues(Map.of(4481, 1))
			.leagueModeState(seasonal())
			.build();
		lines.add(verdict("seasonal-varbit/pass",
			RequirementTestFixtures.requirements(seasonalContext, RequirementTestFixtures.policy(MODE)),
			pool, seasonalVarbit));
		lines.add(verdict("seasonal-varbit/fail",
			RequirementTestFixtures.requirements(seasonalContext, RequirementTestFixtures.policy(MODE),
				new TestRequirementHooks().failVarbits(true)),
			pool, seasonalVarbit));

		GateChainParityGolden.compareOrRegen("gates-var", lines);
	}

	@Test
	public void gateQuestVerdicts()
	{
		List<String> lines = new ArrayList<>();
		Transport gated = transport(TransportType.TRANSPORT)
			.quests(Set.of(Quest.TREE_GNOME_VILLAGE))
			.build();

		TestRequirementHooks finished = new TestRequirementHooks().questState(QuestState.FINISHED);
		RequirementContext done = capture(new MapPlayerStateSource(), finished, List.of(gated), Map.of());
		lines.add(verdict("quest/finished",
			RequirementTestFixtures.requirements(done, RequirementTestFixtures.policy(MODE), finished),
			done.getEligibility(), gated));

		TestRequirementHooks unfinished = new TestRequirementHooks().questState(QuestState.NOT_STARTED);
		RequirementContext notDone = capture(new MapPlayerStateSource(), unfinished, List.of(gated), Map.of());
		lines.add(verdict("quest/not-started",
			RequirementTestFixtures.requirements(notDone, RequirementTestFixtures.policy(MODE), unfinished),
			notDone.getEligibility(), gated));

		GateChainParityGolden.compareOrRegen("gates-quest", lines);
	}

	@Test
	public void gateBlockedItemVerdicts()
	{
		List<String> lines = new ArrayList<>();
		Transport blocked = transport(TransportType.TELEPORTATION_ITEM)
			.itemRequirements(items(item(LAW_RUNE)))
			.build();

		// The blocked-item gate seats inside the teleportation-item gate
		// ahead of the mode dispatch, so NONE reports BLOCKED_ITEM rather
		// than TELEPORT_MODE — and even the ALL bypass cannot skip it. The
		// usable() side has no restriction gate at all: under NONE it
		// rejects at the mode switch, under ALL it reports usable.
		TransportEligibility nonePool = RequirementTestFixtures.eligibility(
			TeleportationItem.NONE, Map.of(LAW_RUNE, 1));
		RequirementContext noneContext = RequirementTestFixtures.context()
			.eligibility(nonePool).build();
		lines.add(verdict("blocked-item/none-mode",
			RequirementTestFixtures.requirements(noneContext,
				RequirementTestFixtures.policy(TeleportationItem.NONE, Set.of(LAW_RUNE))),
			nonePool, blocked));

		TransportEligibility allPool = RequirementTestFixtures.eligibility(
			TeleportationItem.ALL, Map.of(LAW_RUNE, 1));
		RequirementContext allContext = RequirementTestFixtures.context()
			.eligibility(allPool).build();
		lines.add(verdict("blocked-item/all-mode",
			RequirementTestFixtures.requirements(allContext,
				RequirementTestFixtures.policy(TeleportationItem.ALL, Set.of(LAW_RUNE))),
			allPool, blocked));

		GateChainParityGolden.compareOrRegen("gates-blocked-item", lines);
	}

	// ------------------------------------------------------------------
	// Bank-side check(DestinationRequirements) + satisfied — the surface
	// whose varbit/varplayer hook calls get rerouted later.
	// ------------------------------------------------------------------

	@Test
	public void bankVarbitVerdicts()
	{
		DestinationRequirements gated = RequirementTestFixtures.bankRequirements(null, null,
			Set.of(new VarRequirement(VarRequirement.VarType.VARBIT, 4481, 1, VarCheckType.EQUAL)),
			null);
		RequirementContext context = RequirementTestFixtures.context()
			.varbitValues(Map.of(4481, 1)).build();
		List<String> lines = new ArrayList<>();
		lines.add(bankVerdict("bank-varbit/pass",
			RequirementTestFixtures.requirements(context, RequirementTestFixtures.policy(MODE)), gated));
		lines.add(bankVerdict("bank-varbit/fail",
			RequirementTestFixtures.requirements(context, RequirementTestFixtures.policy(MODE),
				new TestRequirementHooks().failVarbits(true)), gated));
		GateChainParityGolden.compareOrRegen("bank-varbit", lines);
	}

	@Test
	public void bankVarPlayerVerdicts()
	{
		DestinationRequirements gated = RequirementTestFixtures.bankRequirements(null, null, null,
			Set.of(new VarRequirement(VarRequirement.VarType.VARPLAYER, 4130, 1999, VarCheckType.GREATER)));
		RequirementContext context = RequirementTestFixtures.context()
			.varPlayerValues(Map.of(4130, 2000)).build();
		List<String> lines = new ArrayList<>();
		lines.add(bankVerdict("bank-varplayer/pass",
			RequirementTestFixtures.requirements(context, RequirementTestFixtures.policy(MODE)), gated));
		lines.add(bankVerdict("bank-varplayer/fail",
			RequirementTestFixtures.requirements(context, RequirementTestFixtures.policy(MODE),
				new TestRequirementHooks().failVarPlayers(true)), gated));
		GateChainParityGolden.compareOrRegen("bank-varplayer", lines);
	}

	@Test
	public void bankQuestVerdicts()
	{
		DestinationRequirements gated = RequirementTestFixtures.bankRequirements(
			null, Set.of(Quest.BONE_VOYAGE), null, null);
		List<String> lines = new ArrayList<>();

		TestRequirementHooks finished = new TestRequirementHooks().questState(QuestState.FINISHED);
		RequirementContext done = capture(new MapPlayerStateSource(), finished,
			List.of(), Map.of(BANK_TILE, List.of(gated)));
		lines.add(bankVerdict("bank-quest/finished",
			RequirementTestFixtures.requirements(done, RequirementTestFixtures.policy(MODE), finished),
			gated));

		TestRequirementHooks unfinished = new TestRequirementHooks().questState(QuestState.NOT_STARTED);
		RequirementContext notDone = capture(new MapPlayerStateSource(), unfinished,
			List.of(), Map.of(BANK_TILE, List.of(gated)));
		lines.add(bankVerdict("bank-quest/not-started",
			RequirementTestFixtures.requirements(notDone, RequirementTestFixtures.policy(MODE), unfinished),
			gated));

		GateChainParityGolden.compareOrRegen("bank-quest", lines);
	}

	// ------------------------------------------------------------------
	// Null/empty edges — identical verdicts before and after the folds.
	// ------------------------------------------------------------------

	@Test
	public void emptyAndNullEdges()
	{
		List<String> lines = new ArrayList<>();
		Requirements requirements = RequirementTestFixtures.requirements(
			RequirementTestFixtures.context().build(),
			RequirementTestFixtures.policy(MODE));
		lines.add(bankVerdict("bank/null", requirements, null));
		lines.add(bankVerdict("bank/empty", requirements, DestinationRequirements.EMPTY));

		// A transport with no item requirements under a deferring mode still
		// defers to pool evaluation — which passes vacuously.
		TransportEligibility pool = RequirementTestFixtures.eligibility(MODE, Map.of());
		RequirementContext context = RequirementTestFixtures.context()
			.eligibility(pool).build();
		Requirements chain = RequirementTestFixtures.requirements(context,
			RequirementTestFixtures.policy(MODE));
		lines.add(verdict("transport/null-items/inventory-mode",
			chain, pool, transport(TransportType.TRANSPORT).build()));
		lines.add(verdict("teleport/null-items/inventory-mode",
			chain, pool, transport(TransportType.TELEPORTATION_ITEM).build()));
		lines.add(verdict("teleport/empty-items/inventory-mode",
			chain, pool, transport(TransportType.TELEPORTATION_ITEM)
				.itemRequirements(new TransportItems(List.of())).build()));

		GateChainParityGolden.compareOrRegen("empty-null", lines);
	}
}
