package shortestpath.requirement;

import java.util.Map;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.leagues.LeagueModeState;
import shortestpath.requirement.model.Unlock;

/**
 * Immutable snapshot of every fact the requirement checks need about the player,
 * captured once per refresh on the client thread. What goes in here is state the
 * game or config asserts about the player: boosted skill levels (with total,
 * combat and quest points in the trailing indices, the same layout
 * {@code PathfinderConfig} fills), quest states, varbit and varplayer values, the
 * owned-item pools inside {@link TransportEligibility} (carried, bank-path, bank,
 * banked rune pouch and the fairy-ring staff rule), declared unlocks, the
 * respawn declaration, sailing-boat state, league-mode state and the planted
 * spirit trees observed so far.
 *
 * <p>User routing policy stays out of the snapshot and is applied by the
 * settings filter instead: transport-type enablement, POH toggles, the
 * teleportation-item mode and the currency threshold. The blocked-item
 * restrictions are policy too — they say which items the user refuses to
 * route through, not what the player owns — so they never enter the snapshot.
 *
 * <p>Built inside {@code PathfinderConfig.refreshTransports} after
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
	@Getter
	private final LeagueModeState leagueModeState;
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
		this.leagueModeState = leagueModeState;
		this.availableSpiritTrees = availableSpiritTrees == null
			? null
			: Set.copyOf(availableSpiritTrees);
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
