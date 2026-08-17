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
 * Special token: {@code All Quests} (also {@code All}) expands to every
 * {@link Quest} except miniquests. Used by the quest point cape teleport so
 * eligibility tracks the current quest list instead of a stale quest-point total.
 * </p>
 */
public class QuestParser implements FieldParser<Set<Quest>>
{
	private static final String DELIM_MULTI = ";";
	private static final String ALL_QUESTS_TOKEN = "All Quests";
	private static final String ALL_TOKEN = "All";

	@Override
	public Set<Quest> parse(String value)
	{
		Set<Quest> quests = new HashSet<>();
		if (value == null || value.isEmpty())
		{
			return quests;
		}

		String[] questNames = value.split(DELIM_MULTI);
		for (String questName : questNames)
		{
			String trimmedName = questName.trim();
			if (trimmedName.isEmpty())
			{
				continue;
			}
			if (ALL_QUESTS_TOKEN.equalsIgnoreCase(trimmedName)
				|| ALL_TOKEN.equalsIgnoreCase(trimmedName))
			{
				quests.addAll(QuestLists.ALL_QUESTS_EXCLUDING_MINIQUESTS);
				continue;
			}
			for (Quest quest : Quest.values())
			{
				if (quest.getName().equals(trimmedName))
				{
					quests.add(quest);
					break;
				}
			}
		}
		return quests;
	}
}
