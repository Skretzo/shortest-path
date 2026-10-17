package shortestpath;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.bank.BankSearch;
import shortestpath.overlay.AbstractHighlightOverlay;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.presentation.BankPickupRequirements.BankPickupResult;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;

/**
 * Filters the open bank down to the items the current path needs to pick up, the same items
 * the bank highlight outlines. The bank is put in its search state the way the Bank Tags plugin
 * shows a tag tab, so the filter works without that plugin. Viewing a bank tab, a tag tab or
 * searching dismisses the filter until the bank is next opened.
 */
@Singleton
public class BankPickupFilter
{
	private static final String TITLE = "Shortest Path items";
	private static final Set<String> DISMISS_OPTIONS = Set.of("View tab", "View all items", "View tag tab", "Search");

	private final Client client;
	private final ClientThread clientThread;
	private final Settings settings;
	private final PathScheduler scheduler;
	private final PathAnnotator annotator;
	private final BankSearch bankSearch;

	// The items the bank was last laid out with; empty while the bank is not filtered.
	private Set<Integer> filteredItemIds = Set.of();
	private boolean dismissed;

	@Inject
	BankPickupFilter(Client client, ClientThread clientThread, Settings settings, PathScheduler scheduler,
		PathAnnotator annotator, BankSearch bankSearch)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.settings = settings;
		this.scheduler = scheduler;
		this.annotator = annotator;
		this.bankSearch = bankSearch;
	}

	/** Restores the unfiltered bank, for when the plugin shuts down with the bank open. */
	public void reset()
	{
		dismissed = false;
		if (!filteredItemIds.isEmpty())
		{
			filteredItemIds = Set.of();
			clientThread.invokeLater(this::relayoutIfOpen);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			dismissed = false;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			dismissed = false;
			filteredItemIds = Set.of();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// The needed items change when a route finishes, the player withdraws them or the
		// config is toggled; the bank only rebuilds itself on some of those, so catch the rest.
		if (isBankOpen() && !wantedItemIds().equals(filteredItemIds))
		{
			bankSearch.layoutBank();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!filteredItemIds.isEmpty()
			&& WidgetUtil.componentToInterface(event.getParam1()) == InterfaceID.BANKMAIN
			&& DISMISS_OPTIONS.contains(event.getMenuOption()))
		{
			// Runs before the click is handled, so the game's own relayout shows the normal bank.
			dismissed = true;
			filteredItemIds = Set.of();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			// Every bank layout asks whether the bank is searching; answering yes runs the
			// bankSearchFilter callback for each item.
			filteredItemIds = wantedItemIds();
			if (!filteredItemIds.isEmpty())
			{
				client.getIntStack()[client.getIntStackSize() - 1] = 1;
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING && !filteredItemIds.isEmpty())
		{
			Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
			if (title != null)
			{
				title.setText(TITLE);
			}
		}
	}

	// Runs after the bank plugins' own search handlers so this filter has the final say.
	@Subscribe(priority = -1)
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (!"bankSearchFilter".equals(event.getEventName()) || filteredItemIds.isEmpty())
		{
			return;
		}
		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		int itemId = intStack[intStackSize - 1];
		intStack[intStackSize - 2] = filteredItemIds.contains(itemId) ? 1 : 0;
	}

	private Set<Integer> wantedItemIds()
	{
		if (!settings.display().filterBankPickupItems() || dismissed || client.getLocalPlayer() == null)
		{
			return Set.of();
		}
		ActiveSearch search = scheduler.getActiveSearch();
		List<PathStep> path = search == null ? null : search.getPath();
		if (path == null || path.isEmpty())
		{
			return Set.of();
		}
		int playerLocation = WorldPointUtil.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
		int pathIndex = AbstractHighlightOverlay.playerPathIndex(path, playerLocation,
			settings.lifecycle().recalculateDistance());
		BankPickupResult pickup = annotator.getBankPickup(path, pathIndex, search);
		return pickup == null ? Set.of() : pickup.bankItemIds;
	}

	private boolean isBankOpen()
	{
		Widget bankContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return bankContainer != null && !bankContainer.isHidden();
	}

	private void relayoutIfOpen()
	{
		if (isBankOpen())
		{
			bankSearch.layoutBank();
		}
	}
}
