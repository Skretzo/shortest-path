package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import shortestpath.ShortestPathPlugin;
import shortestpath.items.ItemStateService;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.requirement.BankPickupRequirements;
import shortestpath.settings.Settings;

public class BankItemHighlightOverlay extends AbstractHighlightOverlay
{
	private final ItemManager itemManager;
	private final ItemStateService itemState;
	private Widget bankContainer;

	@Inject
	public BankItemHighlightOverlay(Client client, ShortestPathPlugin plugin, ItemManager itemManager, Settings settings,
		ItemStateService itemState)
	{
		super(client, plugin, settings);
		this.itemManager = itemManager;
		this.itemState = itemState;
	}

	@Override
	protected boolean enabled()
	{
		return settings.display().highlightBankPickupItems();
	}

	@Override
	protected boolean extraGatesPass()
	{
		bankContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankContainer == null || bankContainer.isHidden())
		{
			return false;
		}

		if (itemState.getBank() == null)
		{
			return false;
		}

		return plugin.getPathfinderConfig().getDestinations("bank") != null;
	}

	@Override
	protected boolean pathIndexUsable(int pathIndex, List<PathStep> path)
	{
		// Bank never indexes path.get(pathIndex + 1) — it only needs the
		// player to be on the path at all.
		return pathIndex >= 0;
	}

	@Override
	protected Dimension drawHighlights(Graphics2D graphics, List<PathStep> path, int pathIndex,
		ActiveSearch search)
	{
		BankPickupRequirements.BankPickupResult pickup = plugin.getPathfinderConfig().getBankPickup(path, pathIndex, search);
		if (pickup == null || pickup.bankItemIds.isEmpty())
		{
			return null;
		}

		Color highlight = settings.display().colourBankPickupHighlight();
		drawItemOutlines(graphics, itemManager, bankContainer.getChildren(), pickup.bankItemIds,
			highlight);

		return null;
	}
}

