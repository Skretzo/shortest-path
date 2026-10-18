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
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsService;
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
 * shows a tag tab, so the filter works without that plugin. A button next to Quest Helper's opens
 * it, and viewing another tab or searching closes it.
 */
@Singleton
public class BankPickupFilter
{
	private static final String TITLE = "Tab <col=ff0000>Path Items</col>";

	private static final String BUTTON_NAME = "<col=ff9040>Path Items</col>";
	private static final String SHOW_ALL_ITEMS = "Show all items";
	// Makes other bank tabs close
	private static final String VIEW_TAB = "View tab";
	private static final String VIEW_TAG_TAB = "View tag tab";
	private static final String VIEW_ALL_ITEMS = "View all items";
	private static final String SEARCH = "Search";
	private static final int POTION_STORE_TAB = 15;
	// Centered between Quest Helper and the close button
	private static final int BUTTON_X = 434;
	private static final int BUTTON_Y = 5;
	private static final int BUTTON_WIDTH = 23;
	private static final int BUTTON_HEIGHT = 25;
	private static final int ICON_SIZE = 15;

	private final Client client;
	private final ClientThread clientThread;
	private final Settings settings;
	private final PathScheduler scheduler;
	private final PathAnnotator annotator;
	private final BankSearch bankSearch;
	private final BankTagsService bankTagsService;

	// The items the bank was last laid out with; empty while the bank is not filtered.
	private Set<Integer> filteredItemIds = Set.of();
	// Stays open across bank visits, like other tabs
	private boolean open;
	private Widget buttonBackground;
	private Widget buttonIcon;

	@Inject
	BankPickupFilter(Client client, ClientThread clientThread, Settings settings, PathScheduler scheduler,
		PathAnnotator annotator, BankSearch bankSearch, BankTagsService bankTagsService)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.settings = settings;
		this.scheduler = scheduler;
		this.annotator = annotator;
		this.bankSearch = bankSearch;
		this.bankTagsService = bankTagsService;
	}

	/** Restores the unfiltered bank, for when the plugin shuts down with the bank open. */
	public void reset()
	{
		open = false;
		Widget background = buttonBackground;
		Widget icon = buttonIcon;
		if (background != null)
		{
			clientThread.invokeLater(() ->
			{
				background.setHidden(true);
				icon.setHidden(true);
			});
		}
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
			createButton();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			filteredItemIds = Set.of();
			buttonBackground = null;
			buttonIcon = null;
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
		updateButton();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		if (open && !BUTTON_NAME.equals(event.getMenuTarget())
			&& WidgetUtil.componentToInterface(event.getParam1()) == InterfaceID.BANKMAIN
			&& (option.startsWith(VIEW_TAB) || option.startsWith(VIEW_TAG_TAB) || option.equals(VIEW_ALL_ITEMS)
			|| option.equals(SEARCH)))
		{
			// Runs before the click is handled, so the relayout that follows shows the other view.
			open = false;
			filteredItemIds = Set.of();
		}
	}

	// Runs last so other plugins' searches are on the stack
	@Subscribe(priority = -1)
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			// Every bank layout asks whether the bank is searching; answering yes runs the
			// bankSearchFilter callback for each item.
			int[] intStack = client.getIntStack();
			int searching = client.getIntStackSize() - 1;
			// Stay closed over other filters (a tag tab would save these items)
			if (intStack[searching] == 1 || bankTagsService.getActiveTag() != null
				|| client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == POTION_STORE_TAB)
			{
				open = false;
			}
			filteredItemIds = wantedItemIds();
			if (!filteredItemIds.isEmpty())
			{
				intStack[searching] = 1;
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
			if (title != null && !filteredItemIds.isEmpty())
			{
				title.setText(TITLE);
			}
			updateButton();
		}
	}

	// Runs after the bank plugins' own search handlers so this filter has the final say.
	@Subscribe(priority = -1)
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if (filteredItemIds.isEmpty())
		{
			return;
		}
		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		if ("bankSearchFilter".equals(event.getEventName()))
		{
			int itemId = intStack[intStackSize - 1];
			intStack[intStackSize - 2] = filteredItemIds.contains(itemId) ? 1 : 0;
		}
		else if ("bankBuildTab".equals(event.getEventName()))
		{
			// One list, no tab dividers
			intStack[intStackSize - 1] = 1;
		}
	}

	private Set<Integer> wantedItemIds()
	{
		return open ? pathItemIds() : Set.of();
	}

	private Set<Integer> pathItemIds()
	{
		if (!settings.display().filterBankPickupItems() || client.getLocalPlayer() == null)
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

	private void createButton()
	{
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}
		buttonBackground = createGraphic(parent, SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL,
			BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_X, BUTTON_Y);
		buttonBackground.setHasListener(true);
		buttonBackground.setOnOpListener((JavaScriptCallback) event -> toggleFilter());
		buttonIcon = createGraphic(parent, SpriteID.WorldmapIcon.PLANET, ICON_SIZE, ICON_SIZE,
			BUTTON_X + (BUTTON_WIDTH - ICON_SIZE) / 2, BUTTON_Y + (BUTTON_HEIGHT - ICON_SIZE) / 2);
		updateButton();
	}

	private static Widget createGraphic(Widget parent, int spriteId, int width, int height, int x, int y)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	private void toggleFilter()
	{
		open = !open;
		if (!open)
		{
			filteredItemIds = Set.of();
		}
		bankSearch.layoutBank();
		updateButton();
	}

	private void updateButton()
	{
		if (buttonBackground == null)
		{
			return;
		}
		boolean shown = !pathItemIds().isEmpty();
		boolean filtering = shown && open;
		buttonBackground.setHidden(!shown);
		buttonIcon.setHidden(!shown);
		buttonBackground.setSpriteId(filtering
			? SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
			: SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
		// Just "Show all items" while open
		buttonBackground.setName(filtering ? "" : BUTTON_NAME);
		buttonBackground.setAction(0, filtering ? SHOW_ALL_ITEMS : VIEW_TAB);
	}
}
