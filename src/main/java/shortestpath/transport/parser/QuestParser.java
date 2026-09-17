package shortestpath.transport.parser;

import java.util.HashSet;
import java.util.Set;

import net.runelite.api.Quest;

/**
 * Parses quest requirements from TSV field values.
 *
 * <p>
 * Format: Quest names separated by semicolons
 * </p>
 * <p>
 * Example: {@code Dragon Slayer I;Recipe for Disaster}
 * </p>
 * <p>
 * Special token: {@code All Quests} expands to every
 * {@link Quest} except miniquests. Used by the quest point cape teleport so
 * eligibility tracks the current quest list instead of a stale quest-point total.
 * </p>
 */
public class QuestParser implements FieldParser<Set<Quest>>
{
	private static final String DELIM_MULTI = ";";

	@Override
	public Set<Quest> parse(String value)
	{
		Set<Quest> quests = new HashSet<>();
		if (value == null || value.isEmpty())
		{
			return quests;
		}
		if (value.equals("All Quests"))
		{
			return QuestLists.ALL_QUESTS_EXCLUDING_MINIQUESTS;
		}

		String[] questNames = value.split(DELIM_MULTI);
		for (String questName : questNames)
		{
			for (Quest quest : Quest.values())
			{
				if (quest.getName().equals(questName))
				{
					quests.add(quest);
					break;
				}
			}
		}
		return quests;
	}
}
