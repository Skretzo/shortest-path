package shortestpath.requirement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.requirement.model.SkillLevels;

/**
 * What the player could get to open a locked transport: quests to finish, skill levels to reach
 * and items to carry, in any mix. Bundles combine with {@link #plus}, compare with
 * {@link #covers} and read as one line through {@link #describe}. What one is worth depends on
 * the player, so {@link Requirements#cheapestFirst()} ranks them and
 * {@link RequirementContext#granting} applies them.
 */
@Getter
@EqualsAndHashCode
public final class Capability
{
	/** Coarse effort tiers, cheapest first; a bundle sits in the tier of its hardest part. */
	public enum Effort
	{
		/** Items the player already has in the bank. */
		OWNED_ITEMS,
		/** Coins, trading sticks and other currencies. */
		CURRENCY,
		ITEMS,
		QUESTS,
		SKILLS
	}

	private final Set<Quest> quests;
	/** The levels to reach; zero at every index that needs no change. */
	private final SkillLevels skillLevels;
	/** Item id to quantity. */
	private final Map<Integer, Integer> items;

	private Capability(Set<Quest> quests, SkillLevels skillLevels, Map<Integer, Integer> items)
	{
		this.quests = Set.copyOf(quests);
		this.skillLevels = skillLevels;
		this.items = Map.copyOf(items);
	}

	public static Capability of(Set<Quest> quests, SkillLevels skillLevels, Map<Integer, Integer> items)
	{
		return new Capability(quests, skillLevels, items);
	}

	public static Capability quests(Set<Quest> quests)
	{
		return of(quests, SkillLevels.NONE, Map.of());
	}

	public static Capability skills(SkillLevels levels)
	{
		return of(Set.of(), levels, Map.of());
	}

	public static Capability items(Map<Integer, Integer> items)
	{
		return of(Set.of(), SkillLevels.NONE, items);
	}

	public boolean isEmpty()
	{
		return quests.isEmpty() && skillLevels.isEmpty() && items.isEmpty();
	}

	/** Both bundles at once: every quest of either, the higher of each level, and both sets of items. */
	public Capability plus(Capability other)
	{
		Set<Quest> allQuests = new HashSet<>(quests);
		allQuests.addAll(other.quests);
		Map<Integer, Integer> allItems = new HashMap<>(items);
		other.items.forEach((id, quantity) -> allItems.merge(id, quantity, Integer::sum));
		return of(allQuests, SkillLevels.merge(skillLevels, other.skillLevels), allItems);
	}

	/** Whether having this capability also gives the player {@code other}. */
	public boolean covers(Capability other)
	{
		if (!quests.containsAll(other.quests))
		{
			return false;
		}
		for (Map.Entry<Integer, Integer> item : other.items.entrySet())
		{
			if (items.getOrDefault(item.getKey(), 0) < item.getValue())
			{
				return false;
			}
		}
		for (int i = 0; i < SkillLevels.LENGTH; i++)
		{
			if (skillLevels.get(i) < other.skillLevels.get(i))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * One line naming what to get: "Complete Twilight's Promise", "Reach 60 Agility",
	 * "Carry 20 Coins", "Complete Song of the Elves, reach 70 Agility and 70 Construction".
	 */
	public String describe(IntFunction<String> itemName)
	{
		List<String> clauses = new ArrayList<>();
		List<String> names = new ArrayList<>();
		quests.forEach(quest -> names.add(quest.getName()));
		clause(clauses, "complete ", names);
		for (int i = 0; i < SkillLevels.LENGTH; i++)
		{
			int level = skillLevels.get(i);
			if (level > 0)
			{
				names.add(i == SkillLevels.TOTAL_LEVEL_INDEX ? "total level " + level
					: i == SkillLevels.COMBAT_LEVEL_INDEX ? "combat level " + level
					: i == SkillLevels.QUEST_POINTS_INDEX ? level + " quest points"
					: level + " " + Skill.values()[i].getName());
			}
		}
		clause(clauses, "reach ", names);
		items.forEach((id, quantity) -> names.add(quantity > 1 ? quantity + " " + itemName.apply(id) : itemName.apply(id)));
		clause(clauses, "carry ", names);
		String line = String.join(", ", clauses);
		return line.isEmpty() ? line : Character.toUpperCase(line.charAt(0)) + line.substring(1);
	}

	/** Adds "verb a, b and c" for {@code names}, sorted, and clears them. */
	private static void clause(List<String> clauses, String verb, List<String> names)
	{
		if (names.isEmpty())
		{
			return;
		}
		names.sort(null);
		String last = names.remove(names.size() - 1);
		clauses.add(verb + (names.isEmpty() ? last : String.join(", ", names) + " and " + last));
		names.clear();
	}
}
