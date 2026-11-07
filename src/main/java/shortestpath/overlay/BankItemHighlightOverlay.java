package shortestpath.overlay;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.BankPickupRequirements;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;

public class BankItemHighlightOverlay extends AbstractHighlightOverlay
{
	// The BANK_CURRENTTAB value while potion storage is open, RuneLite's PotionStorage.BANKTAB_POTIONSTORE
	private static final int POTION_STORE_TAB = 15;

	private final ItemManager itemManager;
	// extraGatesPass() fetches the container; drawHighlights reads it in the
	// same render pass, keeping the widget lookup once per frame.
	private Widget bankContainer;

	@Inject
	public BankItemHighlightOverlay(Client client, ItemManager itemManager, Settings settings,
		PathScheduler scheduler, PathAnnotator annotator)
	{
		super(client, scheduler, annotator, settings);
		this.itemManager = itemManager;
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

		if (!annotator.isBankOpen())
		{
			return false;
		}

		// Potion storage covers the bank's item area, so the outlines would be drawn over its list
		if (client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == POTION_STORE_TAB)
		{
			return false;
		}

		return annotator.getDestinations("bank") != null;
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
		BankPickupRequirements.BankPickupResult pickup = annotator.getBankPickup(path, pathIndex, search);
		if (pickup == null || pickup.bankItemIds.isEmpty())
		{
			return null;
		}

		// bankContainer was populated by extraGatesPass() earlier in this same
		// render pass — the template always calls extraGatesPass before
		// drawHighlights, so the widget is non-null here (the gate returns
		// false on a null/hidden container).
		Color highlight = settings.display().colourBankPickupHighlight();
		// Items scrolled out of view keep their positions above or below the visible item area,
		// so only that area is drawn on. RuneLite resets the clip after each overlay.
		graphics.clip(bankContainer.getBounds());
		drawItemOutlines(graphics, itemManager, bankContainer.getChildren(), pickup.bankItemIds,
			highlight);

		return null;
	}
}
