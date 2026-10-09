package shortestpath.requirement;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import shortestpath.leagues.LeagueModeState;
import shortestpath.requirement.model.DestinationRequirements;
import shortestpath.poh.JewelleryBoxTier;
import shortestpath.requirement.model.Unlock;
import shortestpath.requirement.model.VarRequirement;
import shortestpath.settings.TeleportationItem;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;
import shortestpath.transport.TransportType;

/**
 * Shared fixture builders for the requirement middleware tests: a
 * everything-enabled {@link RoutingPolicy}, a defaulted
 * {@link RequirementContext} builder so each case overrides only what its
 * gate reads, an eligibility snapshot with a chosen teleportation-item mode,
 * and a maxed skill array in the {@code skills + total + combat + quest
 * points} layout the skill gate consumes.
 *
 * <p>Every builder produces the frozen values the chain reads at check time —
 * the tests mutate the <em>inputs</em> to prove the snapshot cannot move.
 */
final class RequirementTestFixtures
{
	static final int SKILL_SLOTS = Skill.values().length + 3;
	static final int TOTAL_LEVEL_SLOT = Skill.values().length;

	private RequirementTestFixtures()
	{
	}

	/** Every skill at 99 plus realistic total, combat and quest-point slots. */
	static int[] maxedSkills()
	{
		int[] skills = new int[SKILL_SLOTS];
		Arrays.fill(skills, 99);
		skills[TOTAL_LEVEL_SLOT] = 99 * Skill.values().length;
		skills[TOTAL_LEVEL_SLOT + 1] = 126;
		skills[TOTAL_LEVEL_SLOT + 2] = 300;
		return skills;
	}

	/**
	 * A policy with every transport type enabled, every POH toggle on, every
	 * portal and mounted item enabled, the ornate jewellery box, an
	 * unbounded currency threshold and bank paths included — only the
	 * teleportation-item mode varies between cases.
	 */
	static PolicyBuilder policy()
	{
		return new PolicyBuilder();
	}

	static RoutingPolicy policy(TeleportationItem mode)
	{
		return policy().teleportationItemSetting(mode).build();
	}

	static final class PolicyBuilder
	{
		private Set<TransportType> enabledTypes = EnumSet.allOf(TransportType.class);
		private TeleportationItem teleportationItemSetting = TeleportationItem.INVENTORY;
		private boolean usePoh = true;
		private boolean usePohFairyRing = true;
		private boolean usePohSpiritTree = true;
		private boolean usePohObelisk = true;
		private Set<PohNexusPortal> enabledPohNexusPortals = EnumSet.allOf(PohNexusPortal.class);
		private Set<PohMountedItem> enabledPohMountedItems = EnumSet.allOf(PohMountedItem.class);
		private JewelleryBoxTier pohJewelleryBoxTier = JewelleryBoxTier.ORNATE;
		private int currencyThreshold = Integer.MAX_VALUE;
		private boolean includeBankPath = true;

		PolicyBuilder enabledTypes(Set<TransportType> types)
		{
			enabledTypes = types;
			return this;
		}

		PolicyBuilder teleportationItemSetting(TeleportationItem setting)
		{
			teleportationItemSetting = setting;
			return this;
		}

		PolicyBuilder usePoh(boolean value)
		{
			usePoh = value;
			return this;
		}

		PolicyBuilder usePohFairyRing(boolean value)
		{
			usePohFairyRing = value;
			return this;
		}

		PolicyBuilder usePohSpiritTree(boolean value)
		{
			usePohSpiritTree = value;
			return this;
		}

		PolicyBuilder usePohObelisk(boolean value)
		{
			usePohObelisk = value;
			return this;
		}

		PolicyBuilder enabledPohNexusPortals(Set<PohNexusPortal> portals)
		{
			enabledPohNexusPortals = portals;
			return this;
		}

		PolicyBuilder enabledPohMountedItems(Set<PohMountedItem> items)
		{
			enabledPohMountedItems = items;
			return this;
		}

		PolicyBuilder pohJewelleryBoxTier(JewelleryBoxTier tier)
		{
			pohJewelleryBoxTier = tier;
			return this;
		}

		PolicyBuilder currencyThreshold(int threshold)
		{
			currencyThreshold = threshold;
			return this;
		}

		PolicyBuilder includeBankPath(boolean value)
		{
			includeBankPath = value;
			return this;
		}

		RoutingPolicy build()
		{
			return new RoutingPolicy(enabledTypes, teleportationItemSetting, usePoh,
				usePohFairyRing, usePohSpiritTree, usePohObelisk, enabledPohNexusPortals,
				enabledPohMountedItems, pohJewelleryBoxTier, currencyThreshold, includeBankPath,
				Set.of(), Map.of());
		}
	}

	/**
	 * An eligibility snapshot whose carried and bank-path pools both read the
	 * supplied items, with no bank contents, no pouch, no fairy-ring staff
	 * requirement, an unbounded currency threshold and no declared unlocks —
 * the mode is set explicitly so a teleportation-item gate that defers to
	 * item evaluation sees the same setting the policy carries.
	 */
	static TransportEligibility eligibility(TeleportationItem mode, Map<Integer, Integer> carried)
	{
		return eligibility(mode, carried, Integer.MAX_VALUE);
	}

	static TransportEligibility eligibility(TeleportationItem mode, Map<Integer, Integer> carried,
		int currencyThreshold)
	{
		return new TransportEligibility(carried, carried, Map.of(), -1, Map.of(),
			false, mode, currencyThreshold, Set.of());
	}

	/**
	 * An eligibility snapshot where the bank-path pool carries extra items the
	 * carried pool lacks — the shape {@code collectEligibility} produces when
	 * bank paths are enabled and the bank holds usable items.
	 */
	static TransportEligibility bankedEligibility(TeleportationItem mode, Map<Integer, Integer> carried,
		Map<Integer, Integer> bankPath)
	{
		return new TransportEligibility(carried, bankPath, Map.of(), -1, Map.of(),
			false, mode, Integer.MAX_VALUE, Set.of());
	}

	/** A context with every field defaulted to the always-passing value. */
	static ContextBuilder context()
	{
		return new ContextBuilder();
	}

	static RequirementContext context(TransportEligibility eligibility)
	{
		return context().eligibility(eligibility).build();
	}

	static final class ContextBuilder
	{
		private long evaluationTimeMinutes;
		private int[] boostedSkillLevelsAndMore = maxedSkills();
		private int currentMaxQuestPoints = 300;
		private Map<Quest, QuestState> questStates = Map.of();
		private Map<Integer, Integer> varbitValues = Map.of();
		private Map<Integer, Integer> varPlayerValues = Map.of();
		private TransportEligibility eligibility = TransportEligibility.forPlayerAndBank(
			Map.of(), Map.of(), -1, Map.of());
		private Set<Unlock> unlocks = Set.of();
		private boolean respawnPrifddinas;
		private boolean onSailingBoat;
		private LeagueModeState leagueModeState = new LeagueModeState();
		private Set<String> availableSpiritTrees = null;

		ContextBuilder evaluationTimeMinutes(long minutes)
		{
			evaluationTimeMinutes = minutes;
			return this;
		}

		ContextBuilder boostedSkillLevelsAndMore(int[] levels)
		{
			boostedSkillLevelsAndMore = levels;
			return this;
		}

		ContextBuilder currentMaxQuestPoints(int points)
		{
			currentMaxQuestPoints = points;
			return this;
		}

		ContextBuilder questStates(Map<Quest, QuestState> states)
		{
			questStates = states;
			return this;
		}

		ContextBuilder varbitValues(Map<Integer, Integer> values)
		{
			varbitValues = values;
			return this;
		}

		ContextBuilder varPlayerValues(Map<Integer, Integer> values)
		{
			varPlayerValues = values;
			return this;
		}

		ContextBuilder eligibility(TransportEligibility value)
		{
			eligibility = value;
			return this;
		}

		ContextBuilder unlocks(Set<Unlock> values)
		{
			unlocks = values;
			return this;
		}

		ContextBuilder respawnPrifddinas(boolean value)
		{
			respawnPrifddinas = value;
			return this;
		}

		ContextBuilder onSailingBoat(boolean value)
		{
			onSailingBoat = value;
			return this;
		}

		ContextBuilder leagueModeState(LeagueModeState state)
		{
			leagueModeState = state;
			return this;
		}

		ContextBuilder availableSpiritTrees(Set<String> trees)
		{
			availableSpiritTrees = trees;
			return this;
		}

		RequirementContext build()
		{
			return new RequirementContext(evaluationTimeMinutes, boostedSkillLevelsAndMore,
				currentMaxQuestPoints, questStates, varbitValues, varPlayerValues, eligibility,
				unlocks, respawnPrifddinas, onSailingBoat, leagueModeState, availableSpiritTrees);
		}
	}

	/**
	 * A {@link Requirements} chain bound to {@code context} whose hooks
	 * evaluate the context's own captured var maps — the same wiring the
	 * refresh path produces.
	 */
	static Requirements requirements(RequirementContext context, RoutingPolicy policy)
	{
		return new Requirements(context, policy, new TestRequirementHooks());
	}

	static Requirements requirements(RequirementContext context, RoutingPolicy policy,
		TestRequirementHooks hooks)
	{
		return new Requirements(context, policy, hooks);
	}

	/** Convenience for bank-adapter cases: defaults except where named. */
	static DestinationRequirements bankRequirements(int[] skillLevels,
		Set<Quest> quests, Set<VarRequirement> varbits,
		Set<VarRequirement> varPlayers)
	{
		return new DestinationRequirements(skillLevels, quests, varbits, varPlayers);
	}
}
