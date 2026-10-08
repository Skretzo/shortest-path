package shortestpath.items;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Collects the items a player owns as item id to quantity, summing an item
 * held in several places. Pathfinding and the item hints share it so they
 * count items the same way.
 */
public final class OwnedItems
{
	private OwnedItems()
	{
	}

	public static final List<Integer> RUNE_POUCHES = Arrays.asList(
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER,
		ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
	);
	public static final int[] RUNE_POUCH_RUNE_VARBITS =
		{
			VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4,
			VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
		};
	public static final int[] RUNE_POUCH_AMOUNT_VARBITS =
		{
			VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4,
			VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
		};

	public static void addContainer(Map<Integer, Integer> owned, ItemContainer container)
	{
		if (container == null || container.getItems() == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() >= 0 && item.getQuantity() > 0)
			{
				owned.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}

	/**
	 * Adds the runes in the rune pouch, if {@code owned} already holds a rune pouch.
	 */
	public static void addRunePouchContents(Client client, Map<Integer, Integer> owned)
	{
		if (RUNE_POUCHES.stream().noneMatch(owned::containsKey))
		{
			return;
		}
		runePouchContents(client).forEach((runeId, amount) -> owned.merge(runeId, amount, Integer::sum));
	}

	/**
	 * Returns the runes in the rune pouch as rune id to amount. The varbits that
	 * hold the contents are current wherever the pouch is, including the bank.
	 */
	public static Map<Integer, Integer> runePouchContents(Client client)
	{
		Map<Integer, Integer> runes = new HashMap<>();
		EnumComposition runePouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runePouchEnum == null)
		{
			return runes;
		}
		for (int i = 0; i < RUNE_POUCH_RUNE_VARBITS.length; i++)
		{
			int runeEnumId = client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]);
			int runeId = runeEnumId > 0 ? runePouchEnum.getIntValue(runeEnumId) : 0;
			int runeAmount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
			if (runeId > 0 && runeAmount > 0)
			{
				runes.merge(runeId, runeAmount, Integer::sum);
			}
		}
		return runes;
	}
}
