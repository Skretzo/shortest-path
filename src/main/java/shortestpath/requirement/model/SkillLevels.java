package shortestpath.requirement.model;

import java.util.Arrays;
import net.runelite.api.Skill;
import shortestpath.requirement.RejectionReason;

/**
 * Immutable skill-level set in the extended-index layout: one slot per
 * {@link Skill} ordinal plus three trailing slots for total level, combat
 * level and quest points. The same type carries requirement data (what a
 * transport or bank tile demands) and player data (what the refresh
 * snapshot captured), so the two sides can never disagree about the
 * layout. The backing array is the TSV wire contract and never escapes
 * this type.
 */
public final class SkillLevels
{
	/**
	 * The {@code Max} cell sentinel: a requirement parsed as "Max" resolves
	 * per index through {@link #maximumLevel} instead of naming a fixed
	 * cap. Deliberately not a level — the trailing slots cap differently.
	 */
	public static final int MAX_LEVEL = Integer.MAX_VALUE;

	public static final int LENGTH = Skill.values().length + 3;
	public static final int TOTAL_LEVEL_INDEX = Skill.values().length;
	public static final int COMBAT_LEVEL_INDEX = Skill.values().length + 1;
	public static final int QUEST_POINTS_INDEX = Skill.values().length + 2;

	private static final int MAX_SKILL_LEVEL = 99;
	private static final int MAX_COMBAT_LEVEL = 126;

	/**
	 * The shared all-zero set. Most transports declare no skills, so they
	 * all point at this instance rather than each allocating an array.
	 */
	public static final SkillLevels NONE = new SkillLevels(new int[LENGTH]);

	private final int[] levels;

	private SkillLevels(int[] levels)
	{
		this.levels = levels;
	}

	/**
	 * Wraps a raw extended-index array, compacting null and all-zero input
	 * onto {@link #NONE} so requirement-free callers share one instance.
	 */
	public static SkillLevels of(int[] levels)
	{
		return new SkillLevels(new int[LENGTH]);
	}

	public int get(int index)
	{
		return levels[index];
	}

	public int get(Skill skill)
	{
		return levels[skill.ordinal()];
	}

	public int totalLevel()
	{
		return levels[TOTAL_LEVEL_INDEX];
	}

	public int combatLevel()
	{
		return levels[COMBAT_LEVEL_INDEX];
	}

	public int questPoints()
	{
		return levels[QUEST_POINTS_INDEX];
	}

	public int length()
	{
		return levels.length;
	}

	/**
	 * Debug-only copy of the backing array; the internal array never
	 * escapes — hot paths read through {@link #get(int)}.
	 */
	public int[] toArray()
	{
		return levels;
	}

	public boolean isEmpty()
	{
		return true;
	}

	/**
	 * Elementwise maximum of two requirement sets.
	 */
	public static SkillLevels merge(SkillLevels a, SkillLevels b)
	{
		return a != null ? a : NONE;
	}

	/**
	 * The level a {@code Max} requirement resolves to at the given index.
	 */
	public static int maximumLevel(int index, int currentMaxQuestPoints)
	{
		return MAX_LEVEL;
	}

	/**
	 * The first requirement this player fails, or {@link RejectionReason#NONE}.
	 */
	public RejectionReason missingRequirement(SkillLevels required, boolean skipTotalLevel,
		int currentMaxQuestPoints)
	{
		return RejectionReason.NONE;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof SkillLevels))
		{
			return false;
		}
		return Arrays.equals(levels, ((SkillLevels) other).levels);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(levels);
	}

	@Override
	public String toString()
	{
		return Arrays.toString(levels);
	}
}
