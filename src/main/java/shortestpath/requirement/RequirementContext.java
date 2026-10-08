package shortestpath.requirement;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.items.ItemStateService;
import shortestpath.leagues.LeagueModeSnapshot;
import shortestpath.leagues.LeagueModeState;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.requirement.model.Unlock;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.transport.Transport;

/**
 * Immutable snapshot of every fact the requirement checks need about the player,
 * captured once per refresh on the client thread. What goes in here is state the
 * game or config asserts about the player: boosted skill levels (with total,
 * combat and quest points in the trailing indices, the same layout
 * {@code PathfinderConfig} fills), quest states, varbit and varplayer values, the
 * owned-item pools inside {@link TransportEligibility} (carried, bank-path, bank,
 * banked rune pouch and the fairy-ring staff rule), declared unlocks, the
 * respawn declaration, sailing-boat state, a frozen league-mode snapshot and
 * the planted spirit trees observed so far.
 *
 * <p>User routing policy stays out of the snapshot and is applied by the
 * settings filter instead: transport-type enablement, POH toggles, the
 * teleportation-item mode and the currency threshold. The blocked-item
 * restrictions are policy too — they say which items the user refuses to
 * route through, not what the player owns — so they never enter the snapshot.
 *
 * <p>{@link #capture} is the single entry point: it reads the player only
 * through a {@link PlayerStateSource} (whose getters throw off the client
 * thread) and quest states only through the {@link RequirementHooks} seam so
 * test overrides keep feeding the snapshot. Called from
 * {@code PathfinderConfig.refreshTransports} after
 * {@code refreshSpiritTreeAvailability()} has settled the planted-tree set, so
 * the whole refresh shares one consistent view of the player.
 */
public final class RequirementContext
{
	@Getter
	private final long evaluationTimeMinutes;
	private final int[] boostedSkillLevelsAndMore;
	@Getter
	private final int currentMaxQuestPoints;
	@Getter
	private final Map<Quest, QuestState> questStates;
	@Getter
	private final Map<Integer, Integer> varbitValues;
	@Getter
	private final Map<Integer, Integer> varPlayerValues;
	@Getter
	private final TransportEligibility eligibility;
	@Getter
	private final Set<Unlock> unlocks;
	@Getter
	private final boolean respawnPrifddinas;
	@Getter
	private final boolean isOnSailingBoat;
	/**
	 * The league facts frozen at capture time. Retaining a
	 * {@link LeagueModeSnapshot} rather than the live {@link LeagueModeState}
	 * keeps this context from tracking a later refresh's in-place writes.
	 */
	@Getter
	private final LeagueModeSnapshot leagueModeSnapshot;
	@Getter
	private final Set<String> availableSpiritTrees;

	public RequirementContext(
		long evaluationTimeMinutes,
		int[] boostedSkillLevelsAndMore,
		int currentMaxQuestPoints,
		Map<Quest, QuestState> questStates,
		Map<Integer, Integer> varbitValues,
		Map<Integer, Integer> varPlayerValues,
		TransportEligibility eligibility,
		Set<Unlock> unlocks,
		boolean respawnPrifddinas,
		boolean isOnSailingBoat,
		LeagueModeState leagueModeState,
		Set<String> availableSpiritTrees)
	{
		this.evaluationTimeMinutes = evaluationTimeMinutes;
		this.boostedSkillLevelsAndMore = boostedSkillLevelsAndMore.clone();
		this.currentMaxQuestPoints = currentMaxQuestPoints;
		this.questStates = Map.copyOf(questStates);
		this.varbitValues = Map.copyOf(varbitValues);
		this.varPlayerValues = Map.copyOf(varPlayerValues);
		this.eligibility = eligibility;
		this.unlocks = Set.copyOf(unlocks);
		this.respawnPrifddinas = respawnPrifddinas;
		this.isOnSailingBoat = isOnSailingBoat;
		this.leagueModeSnapshot = leagueModeState == null
			? LeagueModeSnapshot.NON_SEASONAL
			: leagueModeState.snapshot();
		this.availableSpiritTrees = availableSpiritTrees == null
			? null
			: Set.copyOf(availableSpiritTrees);
	}

	/**
	 * Captures the immutable snapshot the requirement checks read this
	 * refresh. All player-state reads go through {@code source} (loud off the
	 * client thread) and quest states go through {@code hooks} so the
	 * protected override seam keeps feeding the snapshot. Declared var and
	 * quest ids from both the transports and the bank requirements land in
	 * the same maps — a var or quest missing from them fails closed when the
	 * shared evaluator looks it up.
	 */
	public static RequirementContext capture(
		PlayerStateSource source,
		RequirementHooks hooks,
		RoutingPolicy policy,
		long evaluationTimeMinutes,
		Collection<Transport> transports,
		Map<Integer, DestinationRequirements> bankRequirements,
		ItemContainer bank,
		Set<Unlock> unlocks,
		boolean respawnPrifddinas,
		LeagueModeState leagueModeState,
		Set<String> availableSpiritTrees)
	{
		int[] boostedSkillLevelsAndMore = new int[Skill.values().length + 3];
		boolean onSailingBoat = source.varbit(VarbitID.SAILING_BOARDED_BOAT) != 0;
		int i = 0;
		for (; i < Skill.values().length; i++)
		{
			boostedSkillLevelsAndMore[i] = source.boostedSkillLevel(Skill.values()[i]);
		}
		boostedSkillLevelsAndMore[i++] = source.totalLevel(); // skill total level
		boostedSkillLevelsAndMore[i++] = source.combatLevel(); // combat level
		boostedSkillLevelsAndMore[i] = source.varp(VarPlayerID.QP); // quest points

		int currentMaxQuestPoints = source.maximumQuestPoints();

		TransportEligibility eligibilitySnapshot = ItemStateService.collectEligibility(source, bank,
			policy.teleportationItemSetting(), policy.currencyThreshold(), policy.includeBankPath(),
			unlocks);

		Map<Quest, QuestState> capturedQuestStates = new HashMap<>();
		Map<Integer, Integer> capturedVarbitValues = new HashMap<>();
		Map<Integer, Integer> capturedVarPlayerValues = new HashMap<>();
		Set<Quest> refreshedQuests = new HashSet<>();
		for (Transport transport : transports)
		{
			captureQuestStates(hooks, transport.getQuests(), refreshedQuests, capturedQuestStates);
			for (VarRequirement varRequirement : transport.getVarRequirements())
			{
				if (varRequirement.isVarbit())
				{
					capturedVarbitValues.put(varRequirement.getId(), source.varbit(varRequirement.getId()));
				}
				else
				{
					capturedVarPlayerValues.put(varRequirement.getId(), source.varp(varRequirement.getId()));
				}
			}
		}

		// Bank destinations can carry quest and var ids that no transport
		// declares; they must land in the same snapshot, because a var or
		// quest missing from these maps fails closed when the shared
		// evaluator looks it up.
		for (DestinationRequirements destinationRequirements : bankRequirements.values())
		{
			captureQuestStates(hooks, destinationRequirements.getQuests(), refreshedQuests, capturedQuestStates);
			for (VarRequirement varRequirement : destinationRequirements.getVarbits())
			{
				capturedVarbitValues.put(varRequirement.getId(), source.varbit(varRequirement.getId()));
			}
			for (VarRequirement varRequirement : destinationRequirements.getVarPlayers())
			{
				capturedVarPlayerValues.put(varRequirement.getId(), source.varp(varRequirement.getId()));
			}
		}

		return new RequirementContext(evaluationTimeMinutes, boostedSkillLevelsAndMore,
			currentMaxQuestPoints, capturedQuestStates, capturedVarbitValues, capturedVarPlayerValues,
			eligibilitySnapshot, unlocks, respawnPrifddinas, onSailingBoat,
			leagueModeState, availableSpiritTrees);
	}

	/**
	 * Captures each quest's state through the {@code hooks} seam, skipping
	 * quests already captured this refresh and tolerating a hook that
	 * returns null or throws {@link NullPointerException} for a quest it
	 * cannot answer. Any other exception propagates — an unexpected hook
	 * failure should fail the refresh loudly, not silently drop the quest.
	 */
	private static void captureQuestStates(RequirementHooks hooks, Collection<Quest> quests,
		Set<Quest> refreshedQuests, Map<Quest, QuestState> capturedQuestStates)
	{
		for (Quest quest : quests)
		{
			if (!refreshedQuests.add(quest))
			{
				continue;
			}
			try
			{
				QuestState state = hooks.getQuestState(quest);
				if (state != null)
				{
					capturedQuestStates.put(quest, state);
				}
			}
			catch (NullPointerException ignored)
			{
			}
		}
	}

	/**
	 * Builds a snapshot for tests without a {@code Client}: the supplied facts
	 * plus empty unlocks, no respawn declaration, no boat, a fresh non-seasonal
	 * league state and unresolved spirit-tree availability.
	 */
	public static RequirementContext forTesting(
		int[] boostedSkillLevelsAndMore,
		Map<Quest, QuestState> questStates,
		Map<Integer, Integer> varbitValues,
		Map<Integer, Integer> varPlayerValues,
		TransportEligibility eligibility)
	{
		return new RequirementContext(0L, boostedSkillLevelsAndMore, 0, questStates,
			varbitValues, varPlayerValues, eligibility, Set.of(), false, false,
			new LeagueModeState(), null);
	}

	public int[] getBoostedSkillLevelsAndMore()
	{
		return boostedSkillLevelsAndMore.clone();
	}
}
