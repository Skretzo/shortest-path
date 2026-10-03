package shortestpath.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import shortestpath.pathfinder.PathfinderConfig;

/**
 * Collects the items a player owns as item id to quantity, summing an item
 * held in several places. Pathfinding and the item hints share it so they
 * count items the same way. Items rejected by the {@code usable} predicate are
 * skipped, which is how members items are left out on free-to-play worlds.
 */
public final class OwnedItems
{
	public static final IntPredicate ALL_ITEMS = itemId -> true;

	private OwnedItems()
	{
	}

	public static void addContainer(Map<Integer, Integer> owned, ItemContainer container, IntPredicate usable)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() >= 0 && item.getQuantity() > 0 && usable.test(item.getId()))
			{
				owned.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}

	/**
	 * Adds the runes in the rune pouch, if {@code owned} already holds a rune pouch. A pouch
	 * rejected by {@code usable} was never added, so its runes are not added either.
	 */
	public static void addRunePouchContents(Client client, Map<Integer, Integer> owned, IntPredicate usable)
	{
		if (PathfinderConfig.RUNE_POUCHES.stream().noneMatch(owned::containsKey))
		{
			return;
		}
		runePouchContents(client, usable).forEach((runeId, amount) -> owned.merge(runeId, amount, Integer::sum));
	}

	/**
	 * Returns the runes in the rune pouch as rune id to amount, leaving out runes rejected by
	 * {@code usable}. The varbits that hold the contents are current wherever the pouch is,
	 * including the bank.
	 */
	public static Map<Integer, Integer> runePouchContents(Client client, IntPredicate usable)
	{
		Map<Integer, Integer> runes = new HashMap<>();
		EnumComposition runePouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runePouchEnum == null)
		{
			return runes;
		}
		for (int i = 0; i < PathfinderConfig.RUNE_POUCH_RUNE_VARBITS.length; i++)
		{
			int runeEnumId = client.getVarbitValue(PathfinderConfig.RUNE_POUCH_RUNE_VARBITS[i]);
			int runeId = runeEnumId > 0 ? runePouchEnum.getIntValue(runeEnumId) : 0;
			int runeAmount = client.getVarbitValue(PathfinderConfig.RUNE_POUCH_AMOUNT_VARBITS[i]);
			if (runeId > 0 && runeAmount > 0 && usable.test(runeId))
			{
				runes.merge(runeId, runeAmount, Integer::sum);
			}
		}
		return runes;
	}
}
