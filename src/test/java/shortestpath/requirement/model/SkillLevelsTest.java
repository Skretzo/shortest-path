package shortestpath.requirement.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import net.runelite.api.Skill;
import org.junit.Test;
import shortestpath.requirement.RejectionReason;

/**
 * Contract pin for {@link SkillLevels}: the extended-index layout
 * ({@code Skill.values().length + 3}), the {@code Max} sentinel, the
 * flyweight {@code NONE} compaction, clone discipline, the per-index
 * maximum table and the folded gate loop ({@code missingRequirement}).
 */
public class SkillLevelsTest
{
	private static final int QP_CAP = 300;

	private static int[] maxed()
	{
		int[] levels = new int[SkillLevels.LENGTH];
		Arrays.fill(levels, 99);
		levels[SkillLevels.TOTAL_LEVEL_INDEX] = 99 * Skill.values().length;
		levels[SkillLevels.COMBAT_LEVEL_INDEX] = 126;
		levels[SkillLevels.QUEST_POINTS_INDEX] = QP_CAP;
		return levels;
	}

	private static SkillLevels requiring(int index, int level)
	{
		int[] levels = new int[SkillLevels.LENGTH];
		levels[index] = level;
		return SkillLevels.of(levels);
	}

	@Test
	public void layoutConstantsMatchTheExtendedIndexContract()
	{
		assertEquals(Skill.values().length + 3, SkillLevels.LENGTH);
		assertEquals(Skill.values().length, SkillLevels.TOTAL_LEVEL_INDEX);
		assertEquals(Skill.values().length + 1, SkillLevels.COMBAT_LEVEL_INDEX);
		assertEquals(Skill.values().length + 2, SkillLevels.QUEST_POINTS_INDEX);
		assertEquals("the TSV Max sentinel is not a level", Integer.MAX_VALUE, SkillLevels.MAX_LEVEL);
	}

	@Test
	public void ofCompactsNullAndAllZeroOntoNone()
	{
		assertSame(SkillLevels.NONE, SkillLevels.of(null));
		assertSame(SkillLevels.NONE, SkillLevels.of(new int[SkillLevels.LENGTH]));
		assertSame("a short all-zero array still declares nothing",
			SkillLevels.NONE, SkillLevels.of(new int[3]));
		assertTrue(SkillLevels.NONE.isEmpty());
	}

	@Test
	public void ofClonesAndZeroPadsToLength()
	{
		int[] source = new int[SkillLevels.LENGTH];
		source[Skill.AGILITY.ordinal()] = 50;
		SkillLevels wrapped = SkillLevels.of(source);
		assertNotSame(SkillLevels.NONE, wrapped);
		assertEquals(50, wrapped.get(Skill.AGILITY));

		// Mutating the source afterwards changes nothing.
		source[Skill.AGILITY.ordinal()] = 1;
		assertEquals(50, wrapped.get(Skill.AGILITY));

		// Short input pads the tail with zeros; longer input normalises to LENGTH.
		SkillLevels shortWrapped = SkillLevels.of(new int[]{50});
		assertEquals(SkillLevels.LENGTH, shortWrapped.length());
		assertEquals(50, shortWrapped.get(0));
		assertEquals(0, shortWrapped.get(SkillLevels.TOTAL_LEVEL_INDEX));
		assertEquals(SkillLevels.LENGTH,
			SkillLevels.of(new int[SkillLevels.LENGTH + 2]).length());
	}

	@Test
	public void accessorsReadTheSkillOrdinalsAndTail()
	{
		SkillLevels levels = SkillLevels.of(maxed());
		assertEquals(99, levels.get(Skill.COOKING));
		assertEquals(99, levels.get(Skill.COOKING.ordinal()));
		assertEquals(99 * Skill.values().length, levels.totalLevel());
		assertEquals(126, levels.combatLevel());
		assertEquals(QP_CAP, levels.get(SkillLevels.QUEST_POINTS_INDEX));
		assertEquals(levels.totalLevel(), levels.get(SkillLevels.TOTAL_LEVEL_INDEX));
		assertEquals(levels.combatLevel(), levels.get(SkillLevels.COMBAT_LEVEL_INDEX));
	}

	@Test
	public void toArrayHandsOutADefensiveCopy()
	{
		SkillLevels levels = SkillLevels.of(maxed());
		int[] copy = levels.toArray();
		assertArrayEquals(maxed(), copy);
		copy[Skill.COOKING.ordinal()] = 1;
		assertEquals("mutating the copy must not corrupt the value", 99, levels.get(Skill.COOKING));
	}

	@Test
	public void isEmptyReportsOnlyTheAllZeroSet()
	{
		assertTrue(SkillLevels.NONE.isEmpty());
		assertTrue(SkillLevels.of(new int[SkillLevels.LENGTH]).isEmpty());
		assertFalse(requiring(Skill.COOKING.ordinal(), 1).isEmpty());
		assertFalse(requiring(SkillLevels.TOTAL_LEVEL_INDEX, 1).isEmpty());
	}

	@Test
	public void equalsAndHashCodeFollowTheContents()
	{
		SkillLevels first = SkillLevels.of(maxed());
		SkillLevels second = SkillLevels.of(maxed());
		assertNotSame(first, second);
		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertEquals(SkillLevels.NONE, SkillLevels.of(new int[SkillLevels.LENGTH]));
		assertNotEquals(first, SkillLevels.NONE);
		assertNotEquals(first, requiring(Skill.COOKING.ordinal(), 99));
	}

	@Test
	public void maximumLevelResolvesTheCapPerIndex()
	{
		assertEquals(99, SkillLevels.maximumLevel(Skill.COOKING.ordinal(), QP_CAP));
		assertEquals(99 * Skill.values().length,
			SkillLevels.maximumLevel(SkillLevels.TOTAL_LEVEL_INDEX, QP_CAP));
		assertEquals(126, SkillLevels.maximumLevel(SkillLevels.COMBAT_LEVEL_INDEX, QP_CAP));
		// The quest-point cap is refresh state — a parameter, not a constant.
		assertEquals(QP_CAP, SkillLevels.maximumLevel(SkillLevels.QUEST_POINTS_INDEX, QP_CAP));
		assertEquals(200, SkillLevels.maximumLevel(SkillLevels.QUEST_POINTS_INDEX, 200));
		// Out-of-range indices keep the sentinel — they declare no cap.
		assertEquals(SkillLevels.MAX_LEVEL, SkillLevels.maximumLevel(SkillLevels.LENGTH, QP_CAP));
	}

	@Test
	public void missingRequirementSkipsOnlyTheTotalSlotInSeasonalMode()
	{
		int[] seasonalPlayer = maxed();
		seasonalPlayer[SkillLevels.TOTAL_LEVEL_INDEX] = 0;
		SkillLevels player = SkillLevels.of(seasonalPlayer);
		SkillLevels totalGated = requiring(SkillLevels.TOTAL_LEVEL_INDEX, 99 * Skill.values().length);

		assertEquals("seasonal mode skips the total-level check",
			RejectionReason.NONE, player.missingRequirement(totalGated, true, QP_CAP));
		assertEquals("non-seasonal mode still checks the total",
			RejectionReason.SKILL_LEVEL, player.missingRequirement(totalGated, false, QP_CAP));

		// The skip names only the total index — a per-skill shortfall still rejects.
		int[] weakCooking = maxed();
		weakCooking[Skill.COOKING.ordinal()] = 50;
		weakCooking[SkillLevels.TOTAL_LEVEL_INDEX] = 0;
		SkillLevels seasonalWeak = SkillLevels.of(weakCooking);
		assertEquals(RejectionReason.SKILL_LEVEL,
			seasonalWeak.missingRequirement(requiring(Skill.COOKING.ordinal(), 99), true, QP_CAP));
	}

	@Test
	public void missingRequirementReadsMissingIndicesAsZero()
	{
		SkillLevels player = SkillLevels.of(maxed());

		// NONE declares nothing — it can never reject.
		assertEquals(RejectionReason.NONE,
			player.missingRequirement(SkillLevels.NONE, false, QP_CAP));
		assertEquals(RejectionReason.NONE,
			SkillLevels.NONE.missingRequirement(SkillLevels.NONE, false, QP_CAP));

		// A requirement shorter than the player reads 0 past its end.
		SkillLevels shortRequired = SkillLevels.of(new int[]{50});
		assertEquals(RejectionReason.NONE,
			player.missingRequirement(shortRequired, false, QP_CAP));
		assertEquals(RejectionReason.SKILL_LEVEL,
			SkillLevels.NONE.missingRequirement(shortRequired, false, QP_CAP));
	}

	@Test
	public void missingRequirementResolvesMaxPerIndex()
	{
		int[] allMax = new int[SkillLevels.LENGTH];
		Arrays.fill(allMax, SkillLevels.MAX_LEVEL);
		SkillLevels maxRequired = SkillLevels.of(allMax);
		SkillLevels player = SkillLevels.of(maxed());
		assertEquals(RejectionReason.NONE,
			player.missingRequirement(maxRequired, false, QP_CAP));

		// Max on the quest-point slot resolves against the refresh-state cap.
		int[] belowCap = maxed();
		belowCap[SkillLevels.QUEST_POINTS_INDEX] = QP_CAP - 1;
		assertEquals(RejectionReason.SKILL_LEVEL,
			SkillLevels.of(belowCap).missingRequirement(
				requiring(SkillLevels.QUEST_POINTS_INDEX, SkillLevels.MAX_LEVEL), false, QP_CAP));

		// A literal requirement at the Max sentinel resolves the same way.
		SkillLevels literalCap = requiring(Skill.COOKING.ordinal(), SkillLevels.MAX_LEVEL);
		assertEquals(RejectionReason.NONE, player.missingRequirement(literalCap, false, QP_CAP));
	}

	@Test
	public void missingRequirementReportsTheFirstShortfall()
	{
		int[] weak = maxed();
		weak[Skill.COOKING.ordinal()] = 50;
		weak[Skill.ATTACK.ordinal()] = 1;
		SkillLevels player = SkillLevels.of(weak);

		int[] required = new int[SkillLevels.LENGTH];
		required[Skill.COOKING.ordinal()] = 99;
		required[Skill.ATTACK.ordinal()] = 50;
		assertEquals(RejectionReason.SKILL_LEVEL,
			player.missingRequirement(SkillLevels.of(required), false, QP_CAP));

		// Meeting every requirement accepts.
		assertEquals(RejectionReason.NONE,
			player.missingRequirement(requiring(Skill.MAGIC.ordinal(), 50), false, QP_CAP));
	}
}
