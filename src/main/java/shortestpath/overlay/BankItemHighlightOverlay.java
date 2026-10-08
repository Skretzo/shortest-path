package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import shortestpath.ShortestPathPlugin;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.requirement.BankPickupRequirements;
import shortestpath.settings.Settings;

public class BankItemHighlightOverlay extends AbstractHighlightOverlay
{
	private final ItemManager itemManager;
	private final Settings settings;

	@Inject
	public BankItemHighlightOverlay(Client client, ShortestPathPlugin plugin, ItemManager itemManager, Settings settings)
	{
		super(client, plugin);
		this.itemManager = itemManager;
		this.settings = settings;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!settings.display().highlightBankPickupItems())
		{
			return null;
		}

		ActiveSearch search = plugin.getActiveSearch();
		if (search == null)
		{
			return null;
		}

		List<PathStep> path = search.getPath();
		if (path == null || path.isEmpty())
		{
			return null;
		}

		Widget bankContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankContainer == null || bankContainer.isHidden())
		{
			return null;
		}

		if (plugin.getPathfinderConfig().getItemState().getBank() == null)
		{
			return null;
		}

		Set<Integer> bankLocations = plugin.getPathfinderConfig().getDestinations("bank");
		if (bankLocations == null)
		{
			return null;
		}

		int pathIndex = getPlayerPathIndex(path);
		if (pathIndex < 0)
		{
			return null;
		}

		BankPickupRequirements.BankPickupResult pickup = plugin.getBankPickup(path, pathIndex);
		if (pickup == null || pickup.bankItemIds.isEmpty())
		{
			return null;
		}

		Widget[] items = bankContainer.getChildren();
		if (items == null)
		{
			return null;
		}

		Color highlight = settings.display().colourBankPickupHighlight();
		for (Widget item : items)
		{
			if (item == null || item.isHidden() || item.getItemId() < 0)
			{
				continue;
			}
			if (pickup.bankItemIds.contains(item.getItemId()))
			{
				BufferedImage outline = itemManager.getItemOutline(item.getItemId(), item.getItemQuantity(), highlight);
				if (outline != null)
				{
					graphics.drawImage(outline, item.getBounds().x, item.getBounds().y, null);
				}
			}
		}

		return null;
	}
}

