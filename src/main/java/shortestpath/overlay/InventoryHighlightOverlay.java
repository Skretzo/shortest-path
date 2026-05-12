package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import shortestpath.ItemVariations;
import shortestpath.ShortestPathPlugin;
import shortestpath.pathfinder.PathStep;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.transport.requirement.ItemRequirement;

public class InventoryHighlightOverlay extends AbstractHighlightOverlay
{
	private static final int[] EQUIPMENT_SLOT_WIDGET_IDS = {
		InterfaceID.Wornitems.SLOT0,
		InterfaceID.Wornitems.SLOT1,
		InterfaceID.Wornitems.SLOT2,
		InterfaceID.Wornitems.SLOT3,
		InterfaceID.Wornitems.SLOT4,
		InterfaceID.Wornitems.SLOT5,
		InterfaceID.Wornitems.SLOT7,
		InterfaceID.Wornitems.SLOT9,
		InterfaceID.Wornitems.SLOT10,
		InterfaceID.Wornitems.SLOT12,
		InterfaceID.Wornitems.SLOT13,
	};

	private final ItemManager itemManager;

	@Inject
	public InventoryHighlightOverlay(Client client, ShortestPathPlugin plugin, ItemManager itemManager)
	{
		super(client, plugin);
		this.itemManager = itemManager;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.highlightInventoryItems)
		{
			return null;
		}

		if (plugin.getPathfinder() == null)
		{
			return null;
		}

		List<PathStep> path = plugin.getPathfinder().getPath();
		if (path == null || path.isEmpty())
		{
			return null;
		}

		int pathIndex = getPlayerPathIndex(path);
		if (pathIndex < 0 || pathIndex + 1 >= path.size())
		{
			return null;
		}

		Set<Transport> transports = plugin.transportsForEdge(path.get(pathIndex), path.get(pathIndex + 1));
		Set<Integer> itemsToHighlight = new HashSet<>();

		for (Transport transport : transports)
		{
			TransportType type = transport.getType();
			if (!TransportType.TELEPORTATION_ITEM.equals(type)
				&& !TransportType.TELEPORTATION_BOX.equals(type)
				&& !TransportType.FAIRY_RING.equals(type))
			{
				continue;
			}
			if (transport.getItemRequirements() == null || transport.getItemRequirements().size() == 0)
			{
				continue;
			}
			for (ItemRequirement req : transport.getItemRequirements().getRequirements())
			{
				addItemIds(itemsToHighlight, req.getItemIds());
				addItemIds(itemsToHighlight, req.getStaffIds());
				addItemIds(itemsToHighlight, req.getOffhandIds());
			}
		}

		if (itemsToHighlight.isEmpty())
		{
			return null;
		}

		// Build set of dramen/lunar staff IDs — skip highlighting when already equipped,
		// since wearing them passively enables fairy rings without any active use.
		Set<Integer> dramenStaffIds = new HashSet<>();
		for (int id : ItemVariations.DRAMEN_STAFF.getIds())
		{
			dramenStaffIds.add(id);
		}

		ItemContainer wornContainer = client.getItemContainer(InventoryID.WORN);
		Set<Integer> equippedItemIds = new HashSet<>();
		if (wornContainer != null)
		{
			for (Item item : wornContainer.getItems())
			{
				if (item.getId() >= 0)
				{
					equippedItemIds.add(item.getId());
				}
			}
		}

		Color highlight = plugin.colourBankPickupHighlight;

		// Highlight matching items in the inventory
		Widget inventoryContainer = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (inventoryContainer != null && !inventoryContainer.isHidden())
		{
			Widget[] items = inventoryContainer.getChildren();
			if (items != null)
			{
				for (Widget item : items)
				{
					if (item == null || item.isHidden() || item.getItemId() < 0)
					{
						continue;
					}
					if (itemsToHighlight.contains(item.getItemId()))
					{
						BufferedImage outline = itemManager.getItemOutline(item.getItemId(), item.getItemQuantity(), highlight);
						if (outline != null)
						{
							graphics.drawImage(outline, item.getBounds().x, item.getBounds().y, null);
						}
					}
				}
			}
		}

		// Highlight matching items in the equipment tab
		for (int slotWidgetId : EQUIPMENT_SLOT_WIDGET_IDS)
		{
			Widget slot = client.getWidget(slotWidgetId);
			if (slot == null || slot.isHidden() || slot.getItemId() < 0)
			{
				continue;
			}
			int itemId = slot.getItemId();
			if (!itemsToHighlight.contains(itemId))
			{
				continue;
			}
			// Dramen/lunar staff: skip when equipped — wearing it passively enables fairy rings
			if (dramenStaffIds.contains(itemId) && equippedItemIds.contains(itemId))
			{
				continue;
			}
			BufferedImage outline = itemManager.getItemOutline(itemId, 1, highlight);
			if (outline != null)
			{
				graphics.drawImage(outline, slot.getBounds().x, slot.getBounds().y, null);
			}
		}

		return null;
	}

	private static void addItemIds(Set<Integer> target, int[] ids)
	{
		if (ids != null)
		{
			for (int id : ids)
			{
				if (id >= 0)
				{
					target.add(id);
				}
			}
		}
	}
}
