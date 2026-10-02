package shortestpath.transport.parser;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Parses skill level requirements from TSV field values.
 *
 * <p>
 * Format: {@code level skillName} (space-separated), multiple separated by
 * semicolons
 * </p>
 * <p>
 * Example: {@code 70 Agility;50 Strength}
 * </p>
 * <p>
 * Prefix a requirement with {@code Max} to use its maximum supported value.
 * </p>
 */
@Slf4j
public class SkillRequirementParser implements FieldParser<int[]>
{
	public static final int MAX_LEVEL = Integer.MAX_VALUE;
	private static final String DELIM_SPACE = " ";
	private static final String DELIM_MULTI = ";";

	@Override
	public int[] parse(String value)
	{
		int[] skillLevels = new int[Skill.values().length + 3];

		if (value == null)
		{
			return skillLevels;
		}

		String[] skillRequirements = value.split(DELIM_MULTI);

		try
		{
			for (String requirement : skillRequirements)
			{
				if (requirement.isEmpty())
				{
					continue;
				}
				String[] levelAndSkill = requirement.split(DELIM_SPACE);
				if (levelAndSkill.length != 2)
				{
					log.error("Invalid level and skill: '{}'", requirement);
					continue;
				}

				int level = "Max".equals(levelAndSkill[0])
					? MAX_LEVEL
					: Integer.parseInt(levelAndSkill[0]);
				String skillName = levelAndSkill[1] == null ? "" : levelAndSkill[1];

				Skill[] skills = Skill.values();
				int i = 0;
				for (; i < skills.length; i++)
				{
					if (skills[i].getName().equals(skillName))
					{
						skillLevels[i] = level;
					}
				}
				if (skillName.toLowerCase().startsWith("total"))
				{
					skillLevels[i] = level;
				}
				i++;
				if (skillName.toLowerCase().startsWith("combat"))
				{
					skillLevels[i] = level;
				}
				i++;
				if (skillName.toLowerCase().startsWith("quest"))
				{
					skillLevels[i] = level;
				}
			}
		}
		catch (NumberFormatException e)
		{
			log.error("Invalid level and skill: {}", value);
		}

		return skillLevels;
	}
}
