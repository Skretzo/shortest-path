package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.requirement.model.ItemRequirement;
import shortestpath.requirement.model.TransportItems;
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
	// Fairy-ring scan cache: pathfinder searches hand out immutable path
	// lists, so the last scanned list doubles as the cache key by reference
	// identity. lastFairyRingEdgeIndex is the last edge index whose edge
	// offers a fairy ring; fairyRingItemIds is the union of item
	// requirements those rings carry — the injected staff rule, so a
	// staff-free ring (POH, or every ring with Lumbridge Elite done)
	// contributes nothing.
	private List<PathStep> scannedPath;
	private int lastFairyRingEdgeIndex = -1;
	private Set<Integer> fairyRingItemIds = Set.of();

	@Inject
	public InventoryHighlightOverlay(Client client, ItemManager itemManager, Settings settings,
		PathScheduler scheduler, PathAnnotator annotator)
	{
		super(client, scheduler, annotator, settings);
		this.itemManager = itemManager;
	}

	@Override
	protected boolean enabled()
	{
		return settings.display().highlightInventoryItems();
	}

	@Override
	protected Dimension drawHighlights(Graphics2D graphics, List<PathStep> path, int pathIndex,
		ActiveSearch search)
	{
		Set<Transport> transports = annotator.transportsForEdge(path.get(pathIndex), path.get(pathIndex + 1));
		Set<Integer> itemsToHighlight = new HashSet<>();

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
			addRequirementIds(transport.getItemRequirements(), itemsToHighlight);
		}

		// A carried staff satisfies the ring wherever it is, so surface it
		// while a staff-requiring ring remains ahead — not only while
		// standing on the ring edge. The ids ride the ring's own injected
		// requirement; nothing is derived here.
		scanFairyRings(path);
		if (lastFairyRingEdgeIndex >= pathIndex)
		{
			itemsToHighlight.addAll(fairyRingItemIds);
		}

		if (itemsToHighlight.isEmpty())
		{
			return null;
		}

		Color highlight = settings.display().colourBankPickupHighlight();

		// Highlight matching items in the inventory
		Widget inventoryContainer = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (inventoryContainer != null && !inventoryContainer.isHidden())
		{
			drawItemOutlines(graphics, itemManager, inventoryContainer.getChildren(),
				itemsToHighlight, highlight);
		}

		// Highlight matching items in the equipment tab. Equipped items are
		// rendered by child item widgets of the static slot components; the slot
		// widgets themselves never carry an item id. The fairy-staff ids are
		// the skip set — wearing one already satisfies the ring, so it needs
		// no outline — and equipment outlines pin quantity 1 because worn
		// items are not stacks.
		for (int slotWidgetId : EQUIPMENT_SLOT_WIDGET_IDS)
		{
			Widget slot = client.getWidget(slotWidgetId);
			if (slot == null || slot.isHidden())
			{
				continue;
			}
			drawItemOutlines(graphics, itemManager, slot.getChildren(), itemsToHighlight,
				fairyRingItemIds, 1, highlight);
		}

		return null;
	}

	/**
	 * Scans the path once (paths are immutable per search) for fairy-ring
	 * edges, recording the last ring edge index and the union of item
	 * requirement ids those rings carry. The ring's requirements are read
	 * straight off the transport — the injected staff rule — so this stays
	 * a generic read with no fairy-specific item knowledge of its own.
	 */
	private void scanFairyRings(List<PathStep> path)
	{
		if (path == scannedPath)
		{
			return;
		}
		scannedPath = path;
		lastFairyRingEdgeIndex = -1;
		Set<Integer> ids = new HashSet<>();
		for (int i = 0; i + 1 < path.size(); ++i)
		{
			for (Transport transport : annotator.transportsForEdge(path.get(i), path.get(i + 1)))
			{
				if (TransportType.FAIRY_RING.equals(transport.getType()))
				{
					lastFairyRingEdgeIndex = i;
					addRequirementIds(transport.getItemRequirements(), ids);
				}
			}
		}
		fairyRingItemIds = ids;
	}

	private static void addRequirementIds(TransportItems items, Set<Integer> target)
	{
		if (items == null)
		{
			return;
		}
		for (ItemRequirement req : items.getRequirements())
		{
			addItemIds(target, req.getItemIds());
			addItemIds(target, req.getStaffIds());
			addItemIds(target, req.getOffhandIds());
		}
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
