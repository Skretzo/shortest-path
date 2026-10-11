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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import shortestpath.ItemVariations;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.transport.Transport;
import shortestpath.requirement.TransportEligibility;
import shortestpath.transport.TransportType;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.settings.Settings;

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
	// Fairy-ring scan cache: pathfinder searches hand out immutable path lists, so the
	// last scanned list doubles as the cache key by reference identity.
	private List<PathStep> scannedPath;
	private int lastFairyRingEdgeIndex = -1;
	private final Settings settings;

	@Inject
	public InventoryHighlightOverlay(Client client, ItemManager itemManager, Settings settings,
		PathScheduler scheduler, PathAnnotator annotator)
	{
		super(client, scheduler, annotator, settings);
		this.itemManager = itemManager;
		this.settings = settings;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!settings.display().highlightInventoryItems())
		{
			return null;
		}

		ActiveSearch search = scheduler.getActiveSearch();
		if (search == null)
		{
			return null;
		}

		List<PathStep> path = search.getPath();
		if (path == null || path.isEmpty())
		{
			return null;
		}

		int pathIndex = getPlayerPathIndex(path);
		if (pathIndex < 0 || pathIndex + 1 >= path.size())
		{
			return null;
		}

		Set<Transport> transports = annotator.transportsForEdge(path.get(pathIndex), path.get(pathIndex + 1));
		Set<Integer> itemsToHighlight = new HashSet<>();
		boolean lumbridgeEliteDone = client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;
		// The dramen staff requirement lives outside the transport's item requirements,
		// and the staff can be equipped ahead of time — so highlight it whenever the
		// remaining path uses a fairy ring, not only while standing on the ring edge.
		// The Lumbridge Elite diary removes the requirement entirely.
		boolean ringAhead = fairyRingAhead(path, pathIndex);
		if (!lumbridgeEliteDone && ringAhead)
		{
			addItemIds(itemsToHighlight, ItemVariations.DRAMEN_STAFF.getIds());
		}

		for (Transport transport : transports)
		{
			TransportType type = transport.getType();
			if (!TransportType.TELEPORTATION_ITEM.equals(type)
				&& !TransportType.TELEPORTATION_BOX.equals(type)
				&& !TransportType.FAIRY_RING.equals(type)
				&& !TransportType.QUETZAL_WHISTLE.equals(type))
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
		for (int id : TransportEligibility.fairyStaffIds())
		{
			dramenStaffIds.add(id);
		}

		Color highlight = settings.display().colourBankPickupHighlight();

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

		// Highlight matching items in the equipment tab. Equipped items are
		// rendered by child item widgets of the static slot components; the slot
		// widgets themselves never carry an item id.
		for (int slotWidgetId : EQUIPMENT_SLOT_WIDGET_IDS)
		{
			Widget slot = client.getWidget(slotWidgetId);
			if (slot == null || slot.isHidden())
			{
				continue;
			}
			Widget[] children = slot.getChildren();
			if (children == null)
			{
				continue;
			}
			for (Widget item : children)
			{
				if (item == null || item.isHidden() || item.getItemId() < 0)
				{
					continue;
				}
				int itemId = item.getItemId();
				if (!itemsToHighlight.contains(itemId))
				{
					continue;
				}
				// Dramen/lunar staff: skip when equipped — wearing it passively enables fairy rings
				if (dramenStaffIds.contains(itemId))
				{
					continue;
				}
				BufferedImage outline = itemManager.getItemOutline(itemId, 1, highlight);
				if (outline != null)
				{
					graphics.drawImage(outline, item.getBounds().x, item.getBounds().y, null);
				}
			}
		}

		return null;
	}

	/**
	 * Whether any remaining path edge at or after {@code fromIndex} is a fairy ring.
	 * Scans the whole path once per path instance and caches the last ring edge index;
	 * a ring is ahead exactly when that index is at or beyond the player's edge.
	 */
	private boolean fairyRingAhead(List<PathStep> path, int fromIndex)
	{
		if (path != scannedPath)
		{
			scannedPath = path;
			lastFairyRingEdgeIndex = -1;
			for (int i = 0; i + 1 < path.size(); ++i)
			{
				for (Transport transport : plugin.transportsForEdge(path.get(i), path.get(i + 1)))
				{
					if (TransportType.FAIRY_RING.equals(transport.getType()))
					{
						lastFairyRingEdgeIndex = i;
						break;
					}
				}
			}
		}
		return lastFairyRingEdgeIndex >= fromIndex;
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
