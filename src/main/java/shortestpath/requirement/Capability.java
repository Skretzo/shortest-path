package shortestpath.requirement;

import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.Quest;
import shortestpath.requirement.model.SkillLevels;
import shortestpath.requirement.model.TransportItems;

/**
 * One thing the player could get that would open a locked transport: quests to finish, skill
 * levels to reach, or items to carry. Exactly one of the three is non-empty. Ordered by roughly
 * how much effort it takes, cheapest first.
 */
@Getter
@EqualsAndHashCode
public final class Capability implements Comparable<Capability>
{
	/** Coarse effort tiers, cheapest first. */
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

	private final Effort effort;
	private final Set<Quest> quests;
	/** The levels to reach; zero at every index that needs no change. */
	private final SkillLevels skillLevels;
	/** Item id to quantity. */
	private final Map<Integer, Integer> items;
	/** Orders capabilities within a tier: quests to finish, levels to gain or items to carry. */
	private final int amount;

	private Capability(Effort effort, Set<Quest> quests, SkillLevels skillLevels, Map<Integer, Integer> items,
		int amount)
	{
		this.effort = effort;
		this.quests = Set.copyOf(quests);
		this.skillLevels = skillLevels;
		this.items = Map.copyOf(items);
		this.amount = amount;
	}

	public static Capability quests(Set<Quest> quests)
	{
		return new Capability(Effort.QUESTS, quests, SkillLevels.NONE, Map.of(), quests.size());
	}

	public static Capability skills(SkillLevels levels, int levelsMissing)
	{
		return new Capability(Effort.SKILLS, Set.of(), levels, Map.of(), levelsMissing);
	}

	public static Capability items(Map<Integer, Integer> items, boolean owned)
	{
		Effort effort = owned ? Effort.OWNED_ITEMS
			: items.keySet().stream().allMatch(TransportItems.CURRENCIES::contains)
			? Effort.CURRENCY : Effort.ITEMS;
		return new Capability(effort, Set.of(), SkillLevels.NONE, items,
			items.values().stream().mapToInt(Integer::intValue).sum());
	}

	/** Whether having this capability also gives the player {@code other}. */
	public boolean covers(Capability other)
	{
		for (int i = 0; i < SkillLevels.LENGTH; i++)
		{
			if (skillLevels.get(i) < other.skillLevels.get(i))
			{
				return false;
			}
		}
		return quests.containsAll(other.quests) && other.items.entrySet().stream()
			.allMatch(item -> items.getOrDefault(item.getKey(), 0) >= item.getValue());
	}

	@Override
	public int compareTo(Capability other)
	{
		int result = effort.compareTo(other.effort);
		return result != 0 ? result : Integer.compare(amount, other.amount);
	}
}
