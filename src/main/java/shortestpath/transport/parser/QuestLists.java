package shortestpath.transport.parser;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import net.runelite.api.Quest;

/**
 * Quest-cape eligibility uses every {@link Quest} except miniquests.
 * Miniquests appear on a separate quest-list tab and do not award quest points,
 * so they are not required to equip or teleport with the quest point cape.
 * Keep {@link #MINIQUESTS} in sync with the wiki miniquest list when new
 * miniquests are added to the RuneLite {@code Quest} enum.
 */
public final class QuestLists
{
	private QuestLists()
	{
	}

	public static final Set<Quest> MINIQUESTS = Collections.unmodifiableSet(EnumSet.of(
		Quest.ALFRED_GRIMHANDS_BARCRAWL,
		Quest.ENTER_THE_ABYSS,
		Quest.THE_GENERALS_SHADOW,
		Quest.BARBARIAN_TRAINING,
		Quest.SKIPPY_AND_THE_MOGRES,
		Quest.CURSE_OF_THE_EMPTY_LORD,
		Quest.LAIR_OF_TARN_RAZORLOR,
		Quest.BEAR_YOUR_SOUL,
		Quest.THE_ENCHANTED_KEY,
		Quest.MAGE_ARENA_I,
		Quest.FAMILY_PEST,
		Quest.MAGE_ARENA_II,
		Quest.IN_SEARCH_OF_KNOWLEDGE,
		Quest.DADDYS_HOME,
		Quest.THE_FROZEN_DOOR,
		Quest.HOPESPEARS_WILL,
		Quest.INTO_THE_TOMBS,
		Quest.HIS_FAITHFUL_SERVANTS,
		Quest.VALE_TOTEMS
	));

	public static final Set<Quest> ALL_QUESTS_EXCLUDING_MINIQUESTS;

	static
	{
		EnumSet<Quest> quests = EnumSet.allOf(Quest.class);
		quests.removeAll(MINIQUESTS);
		ALL_QUESTS_EXCLUDING_MINIQUESTS = Collections.unmodifiableSet(quests);
	}
}
