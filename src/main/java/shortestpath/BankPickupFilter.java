package shortestpath;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
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
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.SpriteOverride;
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
 * If a tag tab like an Inventory Setups filter is open, the bank isn't filtered, since the tag tab
 * would save the items into its layout. The items are drawn on top of it instead.
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
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_WIDTH = 36;
	private static final int ITEM_HEIGHT = 32;
	private static final int ITEM_X_SPACING = 48;
	private static final int ITEM_Y_SPACING = 36;
	private static final int ITEM_START_X = 51;
	// From bankmain_init
	private static final int BANK_DRAG_DEAD_TIME = 5;
	private static final int BANK_ITEM_SLOTS = 1410;

	private final Client client;
	private final ClientThread clientThread;
	private final Settings settings;
	private final PathScheduler scheduler;
	private final PathAnnotator annotator;
	private final BankSearch bankSearch;
	private final SpriteManager spriteManager;

	// The items the bank was last laid out with; empty while the bank is not filtered.
	private Set<Integer> filteredItemIds = Set.of();
	// Stays open across bank visits, like other tabs; never saved
	private boolean open;
	private boolean reopen;
	// Auto-open waits for the bank's first layout
	private boolean autoOpenPending;
	private boolean closedThisVisit;
	private boolean tagTabShowing;
	private boolean searchShowing;
	private boolean drawingOverTagTab;
	private final List<Widget> drawnItems = new ArrayList<>();
	// Kept across bank visits, so items keep their spots when withdrawn and deposited
	private final List<Integer> routeItemIds = new ArrayList<>();
	private Targets routeItemsFor;
	private final Set<Integer> visitItemIds = new HashSet<>();
	private Widget buttonBackground;
	private Widget buttonIcon;

	@Getter
	@RequiredArgsConstructor
	private enum Sprite implements SpriteOverride
	{
		// Any id the game doesn't use
		ICON(-46301, "/shortestpath/panel_icon.png");

		private final int spriteId;
		private final String fileName;
	}

	@Inject
	BankPickupFilter(Client client, ClientThread clientThread, Settings settings, PathScheduler scheduler,
		PathAnnotator annotator, BankSearch bankSearch, SpriteManager spriteManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.settings = settings;
		this.scheduler = scheduler;
		this.annotator = annotator;
		this.bankSearch = bankSearch;
		this.spriteManager = spriteManager;
	}

	/** Loads the sidebar icon for the button, since bank widgets can only show game sprites. */
	public void startUp()
	{
		spriteManager.addSpriteOverrides(Sprite.values());
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
		clientThread.invokeLater(this::restoreDragging);
		spriteManager.removeSpriteOverrides(Sprite.values());
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
			// The bank builds once before it reports being open, and that build must not be filtered
			reopen = open;
			open = false;
			autoOpenPending = false;
			closedThisVisit = false;
			tagTabShowing = false;
			searchShowing = false;
			drawingOverTagTab = false;
			drawnItems.clear();
			visitItemIds.clear();
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
		if (isBankOpen())
		{
			// The route can start needing bank items while it's open, such as after a deposit
			autoOpen();
			// The needed items change when a route finishes, the player withdraws them or the
			// config is toggled; the bank only rebuilds itself on some of those, so catch the rest.
			if (!wantedItemIds().equals(filteredItemIds))
			{
				bankSearch.layoutBank();
			}
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
			// Tag tabs don't set this, Quest Helper's tab and typed searches do
			searchShowing = intStack[searching] == 1;
			if (open && searchShowing)
			{
				closeTab();
			}
			if (open && isPotionStoreOpen())
			{
				// A searching bank would close the potion store straight away
				closeTab();
			}
			filteredItemIds = wantedItemIds();
			drawingOverTagTab = tagTabShowing && !filteredItemIds.isEmpty();
			if (!filteredItemIds.isEmpty() && !drawingOverTagTab)
			{
				intStack[searching] = 1;
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			restoreDragging();
			if (!filteredItemIds.isEmpty())
			{
				// After the other bank plugins, so a tag tab's layout only sees its own items
				clientThread.invokeAtTickEnd(this::drawItems);
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
			tagTabShowing = intStack[intStackSize - 1] == 1;
		}
		if (filteredItemIds.isEmpty() || drawingOverTagTab)
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

	/**
	 * The items the tab shows, in the route's order. Items withdrawn during this bank visit stay as
	 * placeholders, and the list is empty once everything is grabbed.
	 */
	private Set<Integer> wantedItemIds()
	{
		// Group storage runs the bank's search scripts too; leave it unfiltered
		if (!open || isGroupStorageOpen())
		{
			return Set.of();
		}
		ActiveSearch search = scheduler.getActiveSearch();
		Targets targets = search == null ? null : search.getTargets();
		if (!Objects.equals(targets, routeItemsFor))
		{
			routeItemIds.clear();
			visitItemIds.clear();
			routeItemsFor = targets;
		}
		Set<Integer> pathItemIds = pathItemIds();
		if (pathItemIds.isEmpty())
		{
			return Set.of();
		}
		addInRouteOrder(pathItemIds);
		visitItemIds.addAll(pathItemIds);
		Set<Integer> items = new LinkedHashSet<>();
		for (int itemId : routeItemIds)
		{
			if (visitItemIds.contains(itemId))
			{
				items.add(itemId);
			}
		}
		return items;
	}

	/** Adds the route's new items to the remembered order, each before the next one it already has. */
	private void addInRouteOrder(Set<Integer> pathItemIds)
	{
		List<Integer> route = new ArrayList<>(pathItemIds);
		int insertAt = routeItemIds.size();
		for (int i = route.size() - 1; i >= 0; i--)
		{
			int index = routeItemIds.indexOf(route.get(i));
			if (index >= 0)
			{
				insertAt = index;
			}
			else
			{
				routeItemIds.add(insertAt, route.get(i));
			}
		}
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

	/** Gives drawn tiles back the bank's own drag delay, since the bank's rebuild doesn't reset it. */
	private void restoreDragging()
	{
		for (Widget item : drawnItems)
		{
			item.setDragDeadTime(BANK_DRAG_DEAD_TIME);
		}
		drawnItems.clear();
	}

	/**
	 * Draws the tab once the bank is built: hides every item, then draws the tab's items in order,
	 * with withdrawn ones as placeholders.
	 */
	private void drawItems()
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		Widget[] children = itemContainer == null ? null : itemContainer.getDynamicChildren();
		if (!open || filteredItemIds.isEmpty() || bank == null || children == null || itemContainer.isHidden())
		{
			return;
		}
		for (Widget child : children)
		{
			// Including a tag tab layout's empty drop targets, which block clicks
			if (!child.isSelfHidden())
			{
				child.setHidden(true);
			}
		}
		int position = 0;
		int spareSlot = Math.min(BANK_ITEM_SLOTS, children.length) - 1;
		for (int itemId : filteredItemIds)
		{
			int slot = bank.find(itemId);
			Item item = slot < 0 || slot >= children.length ? null : bank.getItem(slot);
			if (item != null && item.getQuantity() > 0)
			{
				// Its own slot's widget, so clicks withdraw from that slot
				drawItem(children[slot], itemId, item.getQuantity(), position);
				drawnItems.add(children[slot]);
			}
			else
			{
				// Withdrawn: a placeholder in a slot widget the bank isn't using
				while (spareSlot >= 0 && bank.getItem(spareSlot) != null)
				{
					spareSlot--;
				}
				if (spareSlot < 0)
				{
					continue;
				}
				drawItem(children[spareSlot], itemId, 0, position);
				drawnItems.add(children[spareSlot]);
				spareSlot--;
			}
			position++;
		}
		int rows = (position + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
		itemContainer.setScrollHeight(Math.max(rows * ITEM_Y_SPACING, itemContainer.getHeight()));
		clientThread.invokeLater(() -> client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR,
			InterfaceID.Bankmain.ITEMS, 0));
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null)
		{
			title.setText(TITLE);
		}
	}

	/**
	 * Draws one tile at a grid position: the item with the bank's withdraw options, or a faded
	 * placeholder like the bank's own when the quantity is 0. Tiles can't be dragged, since that would
	 * rearrange the tag tab's layout or the bank itself.
	 */
	private void drawItem(Widget widget, int itemId, int quantity, int position)
	{
		ItemComposition definition = client.getItemDefinition(itemId);
		widget.setItemId(itemId);
		widget.setItemQuantity(quantity);
		widget.setName("<col=ff9040>" + definition.getName() + "</col>");
		widget.clearActions();
		if (quantity > 0)
		{
			setWithdrawActions(widget, definition);
		}
		else
		{
			widget.setItemQuantityMode(ItemQuantityMode.ALWAYS);
			widget.setOpacity(120);
		}
		widget.setDragDeadTime(1000);
		widget.setOnDragCompleteListener((JavaScriptCallback) event -> client.setDraggedOnWidget(null));
		widget.setOriginalWidth(ITEM_WIDTH);
		widget.setOriginalHeight(ITEM_HEIGHT);
		widget.setOriginalX(position % ITEMS_PER_ROW * ITEM_X_SPACING + ITEM_START_X);
		widget.setOriginalY(position / ITEMS_PER_ROW * ITEM_Y_SPACING);
		widget.setHidden(false);
		widget.revalidate();
	}

	/** The bank's own withdraw options, as in Bank Tags' layouts and ~bankmain_drawitem. */
	private void setWithdrawActions(Widget widget, ItemComposition definition)
	{
		widget.setItemQuantityMode(ItemQuantityMode.STACKABLE);
		int quantityType = client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE);
		int requestedQuantity = client.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY);
		String quantity;
		switch (quantityType)
		{
			case 1:
				quantity = "5";
				break;
			case 2:
				quantity = "10";
				break;
			case 3:
				quantity = Integer.toString(Math.max(1, requestedQuantity));
				break;
			case 4:
				quantity = "All";
				break;
			default:
				quantity = "1";
				break;
		}
		widget.setAction(0, "Withdraw-" + quantity);
		if (quantityType != 0)
		{
			widget.setAction(1, "Withdraw-1");
		}
		widget.setAction(2, "Withdraw-5");
		widget.setAction(3, "Withdraw-10");
		if (requestedQuantity > 0)
		{
			widget.setAction(4, "Withdraw-" + requestedQuantity);
		}
		widget.setAction(5, "Withdraw-X");
		widget.setAction(6, "Withdraw-All");
		widget.setAction(7, "Withdraw-All-but-1");
		if (client.getVarbitValue(VarbitID.BANK_BANKOPS_TOGGLE_ON) == 1
			&& definition.getIntValue(ParamID.BANK_AUTOCHARGE) != -1)
		{
			widget.setAction(8, "Configure-Charges");
		}
		if (client.getVarbitValue(VarbitID.BANK_LEAVEPLACEHOLDERS) == 0)
		{
			widget.setAction(9, "Placeholder");
		}
		widget.setAction(10, "Examine");
		widget.setOpacity(0);
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
		buttonIcon = createGraphic(parent, Sprite.ICON.getSpriteId(), ICON_SIZE, ICON_SIZE,
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

	/**
	 * Opens the tab by itself when the setting is on and the route needs bank items, unless Quest
	 * Helper's tab or a search is showing, or the player closed it during this bank visit.
	 */
	private void autoOpen()
	{
		if (open || closedThisVisit || searchShowing || !settings.display().autoOpenBankPickupFilter()
			|| isGroupStorageOpen() || pathItemIds().isEmpty())
		{
			return;
		}
		openTab();
	}

	/** Also keeps auto-open off until the next bank visit, since the player chose to close it. */
	private void closeTab()
	{
		open = false;
		filteredItemIds = Set.of();
		closedThisVisit = true;
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
