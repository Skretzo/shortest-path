package shortestpath.transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.TransportAvailability;

/**
 * Determines what items need to be picked up from the bank for a given path.
 * This handles transport-specific requirements like the Dramen staff for fairy rings.
 */
public class BankPickupRequirements
{
	/**
	 * Gets a list of items that need to be picked up from the bank at a given path step.
	 *
	 * @param client The game client
	 * @param bank The bank ItemContainer
	 * @param pathfinderConfig The pathfinder config for bank-aware transport lookups
	 * @param bankLocations Set of bank location coordinates
	 * @param path The current path
	 * @param pathIndex The current step index in the path
	 * @return List of item names to pick up, or empty list if none needed
	 */
	public static List<String> getRequiredBankItems(
			Client client,
			ItemContainer bank,
			PathfinderConfig pathfinderConfig,
			Set<Integer> bankLocations,
			List<PathStep> path,
			int pathIndex)
	{

		List<String> requiredItems = new ArrayList<>();

		if (bank == null || path == null || pathIndex < 0 || pathIndex >= path.size())
		{
			return requiredItems;
		}

		// Check if this is a bank step
		int currentPoint = path.get(pathIndex).getPackedPosition();
		if (!bankLocations.contains(currentPoint))
		{
			return requiredItems;
		}

		// Check what transports are used after this bank step
		boolean usesFairyRing = false;
		List<Transport> transportsWithItems = new ArrayList<>();

		for (int i = pathIndex; i < path.size() - 1; i++)
		{
			int stepPoint = path.get(i).getPackedPosition();
			int nextPoint = path.get(i + 1).getPackedPosition();
			boolean banked = path.get(i + 1).isBankVisited();
			TransportAvailability availability = pathfinderConfig.getTransportAvailability(banked);
			Set<Transport> stepTransports = availability.getTransportsByOrigin().get(stepPoint);
			if (stepTransports != null)
			{
				for (Transport t : stepTransports)
				{
					if (t.getDestination() == nextPoint)
					{
						if (TransportType.FAIRY_RING.equals(t.getType()))
						{
							usesFairyRing = true;
						}
						else if (t.getItemRequirements() != null && t.getItemRequirements().size() > 0)
						{
							transportsWithItems.add(t);
						}
					}
				}
			}
			// Transports with no fixed origin (e.g. Royal seed pod, Quetzal whistle) live in usableTeleports
			for (Transport t : availability.getUsableTeleports())
			{
				if (t.getDestination() == nextPoint
						&& t.getItemRequirements() != null && t.getItemRequirements().size() > 0)
				{
					transportsWithItems.add(t);
				}
			}
		}

		// Accumulate: bank item ID → total quantity needed for the trip
		LinkedHashMap<Integer, Long> itemQtyNeeded = new LinkedHashMap<>();

		// Dramen/Lunar staff for fairy rings
		if (usesFairyRing)
		{
			int[] staffIds = ItemVariations.DRAMEN_STAFF.getIds();
			if (!hasItemInInventoryOrEquipment(client, staffIds, 1))
			{
				int foundId = findItemIdInBank(bank, staffIds, 1);
				if (foundId != -1)
				{
					itemQtyNeeded.put(foundId, 1L);
				}
			}
		}

		// Accumulate item requirements for all transports along the path
		for (Transport transport : transportsWithItems)
		{
			if (transport.getItemRequirements() == null)
			{
				continue;
			}
			for (shortestpath.transport.requirement.ItemRequirement req : transport.getItemRequirements().getRequirements())
			{
				int[] itemIds = req.getItemIds();
				if (itemIds == null || itemIds.length == 0)
				{
					continue;
				}
				int reqQty = req.getQuantity() > 0 ? req.getQuantity() : 1;
				if (hasItemInInventoryOrEquipment(client, itemIds, reqQty))
				{
					continue;
				}
				int foundId = findItemIdInBank(bank, itemIds, reqQty);
				if (foundId != -1)
				{
					itemQtyNeeded.merge(foundId, (long) reqQty, Long::sum);
				}
			}
		}

		// Format display strings, re-checking bank against the accumulated total quantity
		for (Map.Entry<Integer, Long> entry : itemQtyNeeded.entrySet())
		{
			int itemId = entry.getKey();
			long totalQty = entry.getValue();
			if (!hasItemInBank(bank, new int[]{itemId}, (int) Math.min(totalQty, Integer.MAX_VALUE)))
			{
				continue;
			}
			String itemName = client.getItemDefinition(itemId).getName();
			if (itemName == null || itemName.isEmpty() || "null".equals(itemName))
			{
				itemName = "Unknown item";
			}
			boolean isCurrency = PathfinderConfig.CURRENCIES.contains(itemId);
			if (isCurrency && totalQty > 1)
			{
				itemName += " (" + String.format("%,d", totalQty) + ")";
			}
			requiredItems.add(itemName);
		}

		return requiredItems;
	}

	/**
	 * Returns the item ID from {@code itemIds} that is present in the bank with at least
	 * {@code requiredQty}, or -1 if none qualifies.
	 */
	private static int findItemIdInBank(ItemContainer bank, int[] itemIds, int requiredQty)
	{
		for (Item bankItem : bank.getItems())
		{
			for (int itemId : itemIds)
			{
				if (bankItem.getId() == itemId && bankItem.getQuantity() >= requiredQty)
				{
					return itemId;
				}
			}
		}
		return -1;
	}


	private static boolean hasItemInInventoryOrEquipment(Client client, int[] itemIds, int requiredQty)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				for (int itemId : itemIds)
				{
					if (item.getId() == itemId && item.getQuantity() >= requiredQty)
					{
						return true;
					}
				}
			}
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null)
		{
			for (Item item : equipment.getItems())
			{
				for (int itemId : itemIds)
				{
					if (item.getId() == itemId)
					{
						return true; // equipped items don't have a meaningful quantity
					}
				}
			}
		}

		return false;
	}

	private static boolean hasItemInBank(ItemContainer bank, int[] itemIds, int requiredQty)
	{
		for (Item bankItem : bank.getItems())
		{
			for (int itemId : itemIds)
			{
				if (bankItem.getId() == itemId && bankItem.getQuantity() >= requiredQty)
				{
					return true;
				}
			}
		}
		return false;
	}
}
