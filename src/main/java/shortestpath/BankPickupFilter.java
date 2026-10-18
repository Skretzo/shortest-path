package shortestpath;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.bank.BankSearch;
import shortestpath.overlay.AbstractHighlightOverlay;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.Targets;
import shortestpath.presentation.BankPickupRequirements.BankPickupResult;
import shortestpath.presentation.PathAnnotator;
import shortestpath.scheduler.PathScheduler;
import shortestpath.settings.Settings;

/**
 * Filters the open bank down to the items the current path needs to pick up, the same items
 * the bank highlight outlines. A button next to Quest Helper's opens it as a temporary tab, the
 * same way Quest Helper shows its own tab: the bank is put in its search state without the Bank
 * Tags plugin and nothing is saved. Viewing another tab, the potion store or searching closes it.
 * With the auto-open setting on, it also opens by itself once per new route, unless another tab
 * is already showing.
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
	private static final String POTION_STORE = "Potion store";
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

	// The items the bank was last laid out with; empty while the bank is not filtered.
	private Set<Integer> filteredItemIds = Set.of();
	private boolean open;
	// Stays open across bank visits, like other tabs; never saved
	private boolean reopen;
	// Auto-open waits for the bank's first layout
	private boolean autoOpenPending;
	// Auto-open runs once per route
	private Targets autoOpenedFor;
	// A tag tab or another search was showing at the last layout
	private boolean otherTabShowing;
	private Widget buttonBackground;
	private Widget buttonIcon;

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
		open = false;
		reopen = false;
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
			if (reopen)
			{
				// Reopen the tab once the bank has built, as Quest Helper does
				reopen = false;
				clientThread.invokeLater(this::openTab);
			}
			else
			{
				autoOpenPending = true;
			}
		}
		else if (event.getGroupId() == InterfaceID.SHARED_BANK)
		{
			updateButton();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			filteredItemIds = Set.of();
			// The bank builds once before it reports being open, so that build must not be filtered
			reopen = open;
			open = false;
			autoOpenPending = false;
			otherTabShowing = false;
			buttonBackground = null;
			buttonIcon = null;
		}
		else if (event.getGroupId() == InterfaceID.SHARED_BANK)
		{
			// Back in the bank, so show the tab again
			clientThread.invokeLater(() ->
			{
				relayoutIfOpen();
				updateButton();
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (scheduler.getActiveSearch() == null)
		{
			autoOpenedFor = null;
		}
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
			|| option.startsWith(POTION_STORE)))
		{
			// Runs before the click is handled, so the relayout that follows shows the other view.
			closeTab();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// The potion store opens by switching tabs, whatever its menu option reads
		if (open && event.getVarbitId() == VarbitID.BANK_CURRENTTAB && event.getValue() == POTION_STORE_TAB)
		{
			closeTab();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE && open)
		{
			closeTab();
			// Open the search input rather than closing the search the tab was shown with
			client.setVarcStrValue(VarClientID.MESLAYERINPUT, "");
			client.setVarcIntValue(VarClientID.MESLAYERMODE, 0);
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
			if (intStack[searching] == 1)
			{
				// Another tab, such as Quest Helper's, or a typed search is already showing
				otherTabShowing = true;
				if (open)
				{
					closeTab();
				}
			}
			if (open && isPotionStoreOpen())
			{
				// A searching bank would close the potion store straight away
				closeTab();
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
			if (autoOpenPending)
			{
				autoOpenPending = false;
				clientThread.invokeLater(this::autoOpen);
			}
		}
	}

	// Runs after the bank plugins' own search handlers so this filter has the final say.
	@Subscribe(priority = -1)
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		int[] intStack = client.getIntStack();
		int intStackSize = client.getIntStackSize();
		if ("getSearchingTagTab".equals(event.getEventName()))
		{
			otherTabShowing = intStack[intStackSize - 1] == 1;
			if (open && otherTabShowing)
			{
				// A tag tab is open, and shown over it these items would be saved into its layout
				closeTab();
				return;
			}
		}
		if (filteredItemIds.isEmpty())
		{
			return;
		}
		if ("getSearchingTagTab".equals(event.getEventName()))
		{
			// Laid out like a tag tab rather than a typed search
			intStack[intStackSize - 1] = 1;
		}
		else if ("bankSearchFilter".equals(event.getEventName()))
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
		// Group storage runs the bank's search scripts too; leave it unfiltered
		return open && !isGroupStorageOpen() ? pathItemIds() : Set.of();
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

	private boolean isGroupStorageOpen()
	{
		Widget groupStorage = client.getWidget(InterfaceID.SharedBank.UNIVERSE);
		return groupStorage != null && !groupStorage.isHidden();
	}

	private boolean isPotionStoreOpen()
	{
		return client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == POTION_STORE_TAB;
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
		buttonBackground.setOnOpListener((JavaScriptCallback) this::onButtonClicked);
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

	private void onButtonClicked(ScriptEvent event)
	{
		if (event.getOp() != 1)
		{
			return;
		}
		client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
		if (open)
		{
			closeTab();
			bankSearch.reset(true);
		}
		else
		{
			openTab();
		}
		client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	private void openTab()
	{
		// The potion store would stay open behind the tab and break deposits
		if (open || buttonBackground == null || isPotionStoreOpen())
		{
			return;
		}
		open = true;
		// Clears any search and lays the bank out as the tab
		bankSearch.reset(true);
		// The search button's timer would otherwise restore its searching look, as in bankmain_search_setbutton
		Widget searchButton = client.getWidget(InterfaceID.Bankmain.SEARCH);
		if (searchButton != null)
		{
			searchButton.setOnTimerListener((Object[]) null);
			searchButton.setSpriteId(SpriteID.Miscgraphics.EQUIPMENT_SLOT_TILE);
		}
		updateButton();
	}

	private void autoOpen()
	{
		ActiveSearch search = scheduler.getActiveSearch();
		if (open || otherTabShowing || search == null || !settings.display().autoOpenBankPickupFilter()
			|| search.getTargets().equals(autoOpenedFor) || isGroupStorageOpen()
			|| isPotionStoreOpen() || pathItemIds().isEmpty())
		{
			return;
		}
		autoOpenedFor = search.getTargets();
		openTab();
	}

	private void closeTab()
	{
		open = false;
		filteredItemIds = Set.of();
		updateButton();
	}

	private void updateButton()
	{
		if (buttonBackground == null)
		{
			return;
		}
		boolean shown = !isGroupStorageOpen() && !isPotionStoreOpen() && !pathItemIds().isEmpty();
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
