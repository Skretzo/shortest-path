package shortestpath.requirement.model;

import java.util.Arrays;
import net.runelite.api.Skill;
import shortestpath.requirement.RejectionReason;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

/**
 * Immutable skill-level set in the extended-index layout: one slot per
 * {@link Skill} ordinal plus four trailing slots for total level, combat
 * level, quest points and music unlocks. The same type carries requirement data (what a
 * transport or bank tile demands) and player data (what the refresh
 * snapshot captured), so the two sides can never disagree about the
 * layout. The backing array is the TSV wire contract and never escapes
 * this type.
 */
@AllArgsConstructor
@EqualsAndHashCode
public final class SkillLevels
{
	/**
	 * The {@code Max} cell sentinel: a requirement parsed as "Max" resolves
	 * per index through {@link #maximumLevel} instead of naming a fixed
	 * cap. Deliberately not a level — the trailing slots cap differently.
	 */
	public static final int MAX_LEVEL = Integer.MAX_VALUE;

	public static final int LENGTH = Skill.values().length + 4;
	public static final int TOTAL_LEVEL_INDEX = Skill.values().length;
	public static final int COMBAT_LEVEL_INDEX = Skill.values().length + 1;
	public static final int QUEST_POINTS_INDEX = Skill.values().length + 2;
	public static final int MUSIC_TRACKS_INDEX = Skill.values().length + 3;

	private static final int MAX_SKILL_LEVEL = 99;
	private static final int MAX_COMBAT_LEVEL = 126;

	/**
	 * The shared all-zero set. Most transports declare no skills, so they
	 * all point at this instance rather than each allocating an array.
	 */
	public static final SkillLevels NONE = new SkillLevels(new int[LENGTH]);

	private final int[] levels;

	/**
	 * Wraps a raw extended-index array into an immutable set. The input is
	 * cloned — later mutation of the source cannot move the value — and
	 * normalised to {@link #LENGTH} (short input zero-pads the tail, which
	 * is how a shorter requirement set declares nothing for the missing
	 * indices). Null and all-zero input compact onto {@link #NONE}, so
	 * requirement-free callers share one instance.
	 */
	public static SkillLevels of(int[] levels)
	{
		if (levels == null)
		{
			return NONE;
		}
		int[] copy = new int[LENGTH];
		boolean any = false;
		for (int i = 0; i < levels.length; i++)
		{
			if (i < LENGTH)
			{
				copy[i] = levels[i];
			}
			if (levels[i] != 0)
			{
				any = true;
			}
		}
		return any ? new SkillLevels(copy) : NONE;
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
		return levels.clone();
	}

	/**
	 * Whether every slot reads zero — the no-requirement / nothing-captured
	 * shape.
	 */
	public boolean isEmpty()
	{
		for (int level : levels)
		{
			if (level != 0)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The level a {@code Max} requirement resolves to at the given index:
	 * 99 for the skill ordinals, the all-99 total, 126 for combat, the
	 * refresh-state quest-point and music caps for their slots, and the sentinel
	 * itself anywhere else (an index outside the layout declares no cap).
	 * The quest and music caps arrive as parameters because they change
	 * between refreshes — constants here would go stale.
	 */
	public static int maximumLevel(int index, int currentMaxQuestPoints, int currentMaxMusicTracks)
	{
		if (index < TOTAL_LEVEL_INDEX)
		{
			return MAX_SKILL_LEVEL;
		}
		if (index == TOTAL_LEVEL_INDEX)
		{
			return MAX_SKILL_LEVEL * Skill.values().length;
		}
		if (index == COMBAT_LEVEL_INDEX)
		{
			return MAX_COMBAT_LEVEL;
		}
		if (index == QUEST_POINTS_INDEX)
		{
			return currentMaxQuestPoints;
		}
		if (index == MUSIC_TRACKS_INDEX)
		{
			return currentMaxMusicTracks;
		}
		return MAX_LEVEL;
	}

	/**
	 * The skill gate: the first requirement this set fails, or
	 * {@link RejectionReason#NONE} when every slot is met. Iterates this
	 * (player) set; in seasonal leagues the total-level slot is skipped
	 * because some skills are disabled and the reachable total is lower —
	 * holding the item (e.g. Max cape) already proves the player is maxed
	 * for the available skills. A requirement slot reading
	 * {@link #MAX_LEVEL} resolves through {@link #maximumLevel} per index.
	 * Runs allocation-free on the check path.
	 */
	public RejectionReason missingRequirement(SkillLevels required, boolean skipTotalLevel,
		int currentMaxQuestPoints, int currentMaxMusicTracks)
	{
		if (required == null)
		{
			return RejectionReason.NONE;
		}
		for (int i = 0; i < levels.length; i++)
		{
			if (skipTotalLevel && i == TOTAL_LEVEL_INDEX)
			{
				continue;
			}
			int level = levels[i];
			// A shorter requirement set declares no level for the missing
			// indices — read 0 rather than throwing.
			int requiredLevel = i < required.levels.length ? required.levels[i] : 0;
			if (requiredLevel == MAX_LEVEL)
			{
				requiredLevel = maximumLevel(i, currentMaxQuestPoints, currentMaxMusicTracks);
			}
			if (level < requiredLevel)
			{
				return RejectionReason.SKILL_LEVEL;
			}
		}
		return RejectionReason.NONE;
	}

	@Override
	public String toString()
	{
		return Arrays.toString(levels);
	}
}
