package shortestpath;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.DimmableJPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.items.ItemStateService;
import shortestpath.settings.ConfigItems;
import shortestpath.settings.Settings;
import shortestpath.settings.TeleportationItem;
import shortestpath.poh.PohMountedItem;
import shortestpath.poh.PohNexusPortal;
import shortestpath.transport.BalloonRoute;

/**
 * Sidebar panel hosting the route-affecting plugin options. The keys stay on
 * {@link ShortestPathConfig} as hidden items; this panel is their only rendered
 * surface and reads/writes them through the {@link Settings} service — keyed
 * configured reads for control state, {@link Settings#write} for edits and
 * {@link Settings#listen} for same-key sync — so live recalculation rides the
 * same {@code ConfigChanged} path as a settings-panel edit.
 *
 * <p>The body is a two-state {@link CardLayout} under a pinned search
 * strip: a landing tile grid — one raised tile per section carrying its
 * icon, name and a live count — and, after a tile click, that section's
 * {@link CategoryPanel} filling the view with a back arrow returning to
 * the grid. A non-empty query filters the drilled section's rows in
 * place.</p>
 */
public class ShortestPathPanel extends PluginPanel
{
	static final int SECTION_TOGGLE_WIDTH = 18;
	static final int CARD_ICON_SIZE = 16;
	private static final int SPINNER_FIELD_WIDTH = 6;
	static final int SUB_LIST_INDENT = 16;
	/** The {@link CardLayout} name of the landing tile grid. */
	private static final String HOME_VIEW = "home";
	private static final int TILE_GAP = 4;
	/**
	 * One tile edge: the content column (viewport width minus the 8px
	 * side insets) minus the grid gap, split across the two columns.
	 */
	private static final int TILE_EDGE = (PluginPanel.PANEL_WIDTH - 2 * 8 - TILE_GAP) / 2;
	private static final int TILE_ICON_SIZE = 32;
	/**
	 * The chrome every expandable surface shares — transport cards,
	 * restriction rows and collapsible enum groups alike: the collapsed
	 * header bar carries an opaque dark tone while the expanded detail
	 * well keeps the category background, ringed by a thin orange border
	 * that marks the open well as a distinct region. Applied through
	 * {@link #styleExpandable} so no site can drift to its own colours.
	 * The detail well's frame is the header-dark tone with the top edge
	 * left open — the well reads as a boxed continuation of the header
	 * bar rather than a separate ringed region.
	 */
	static final Color EXPANDABLE_HEADER_BACKGROUND = ColorScheme.DARKER_GRAY_COLOR;
	static final Color EXPANDABLE_DETAIL_BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	static final Color EXPANDABLE_DETAIL_BORDER = ColorScheme.DARKER_GRAY_COLOR;
	private static final String SEARCH_TOOLTIP = "Search transports, unlocks and restrictions";
	private static final int FOOTER_BUTTON_EDGE = 22;
	private static final int FOOTER_ICON_SIZE = 18;
	private static final String GITHUB_URL = "https://github.com/Skretzo/shortest-path";
	private static final String DISCORD_URL = "https://discord.gg/uX47xg8u3M";
	private static final ImageIcon GITHUB_ICON = footerIcon("github_icon.png");
	private static final ImageIcon DISCORD_ICON = footerIcon("discord_icon.png");
	private static final String OWNED_FILTER_TOOLTIP =
		"Only show teleports for items in your inventory, equipment or bank";
	static final String OWNED_EMPTY_LINE_1 =
		"No teleport items detected in inventory, equipment or bank.";
	static final String OWNED_EMPTY_LINE_2 =
		"Bank items are remembered from the last time you opened it on this account.";
	static final String RESTRICTIONS_DISABLED_HINT =
		"Teleportation items are disabled. Enable \"Use Teleportation Items\" "
			+ "in the plugin configuration to restrict individual items.";

	static final ImageIcon SECTION_EXPAND_ICON;
	static final ImageIcon SECTION_RETRACT_ICON;

	static
	{
		BufferedImage chevron = ImageUtil.loadImageResource(ShortestPathPanel.class, "/shortestpath/expand.png");
		chevron = ImageUtil.luminanceOffset(chevron, -121);
		SECTION_EXPAND_ICON = new ImageIcon(chevron);
		SECTION_RETRACT_ICON = new ImageIcon(ImageUtil.rotateImage(chevron, Math.PI / 2));
	}

	final Settings settings;
	// Held for the restrictions category (per-item sprites, owned-items
	// detection) so the constructor signature does not churn as cards land.
	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final ItemStateService itemState;
	// The Settings tile posts an overlay-config menu click on the shared
	// bus; the event's overlay must resolve back to this plugin so the
	// config surface knows which settings page to open.
	final EventBus eventBus;
	final Plugin plugin;
	/**
	 * A render-nothing overlay bound to the plugin — the posted config
	 * click resolves the page to open off {@code overlay.getPlugin()}.
	 * Never registered with the OverlayManager.
	 */
	private final Overlay configOverlay;
	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true) private final IconTextField searchBar;
	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true)
	private final List<CategoryPanel> categories = new ArrayList<>();
	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true)
	private final List<TransportFamilyCard> transportCards = new ArrayList<>();

	/**
	 * Unregistration handles for every {@link Settings#listen} callback this
	 * panel (and its cards/restriction list) registered. {@link Settings} is
	 * a singleton that outlives the panel — {@link #dispose()} runs them all
	 * on teardown so a dead panel neither keeps firing nor stays reachable.
	 */
	private final List<Runnable> unlisteners = new CopyOnWriteArrayList<>();

	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true) private RestrictionListPanel restrictionListPanel;
	/**
	 * The trimmed text currently in the search field — the query sub-panels
	 * apply when they activate.
	 */
	@Getter(AccessLevel.PACKAGE) private String currentQuery = "";
	/**
	 * The two-state body: the {@link #HOME_VIEW} tile grid or one drilled
	 * {@link CategoryPanel} filling it — swapped by {@link CardLayout}
	 * name, never stacked.
	 */
	private final JPanel body = new JPanel(new CardLayout());
	private final List<Tile> tiles = new ArrayList<>();

	ShortestPathPanel(Settings settings, Client client,
		ClientThread clientThread, ItemManager itemManager,
		SpriteManager spriteManager, ItemStateService itemState,
		EventBus eventBus, Plugin plugin)
	{
		super(false);

		this.settings = settings;
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.itemState = itemState;
		this.eventBus = eventBus;
		this.plugin = plugin;
		this.configOverlay = new ConfigHandleOverlay(plugin);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchBar = new IconTextField();
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setToolTipText(SEARCH_TOOLTIP);
		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}
		});
		searchBar.addClearListener(this::onSearchChanged);

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setBorder(new EmptyBorder(8, 10, 8, 10));
		topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topPanel.add(searchBar, BorderLayout.CENTER);
		add(topPanel, BorderLayout.NORTH);

		// The sections build in drill order — each registers its
		// syncFromConfig listeners as it is constructed, so every control
		// is live before first paint.
		buildTransportsCategory();
		buildRestrictionsCategory();
		buildPohCategory();

		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(homeView(), HOME_VIEW);
		for (CategoryPanel category : categories)
		{
			body.add(category, category.categoryName());
		}
		add(body, BorderLayout.CENTER);

		// Tile count lines track the keys they summarize: every card
		// master plus the restriction checklist's record store.
		for (TransportFamilyCard card : transportCards)
		{
			registerConfigListener(card.getKeyName(), this::refreshTiles);
		}
		registerConfigListener("blockedTeleportItems", this::refreshTiles);
		showHome();
	}

	// ---- Section builders -------------------------------------------------

	private void buildTransportsCategory()
	{
		CategoryPanel transports = new CategoryPanel("Transports", "", this::showHome);
		categories.add(transports);

		addFamilyCard(transports, "useAgilityShortcuts", ItemID.AGILITY_JUMP,
			familyThresholdRows("useAgilityShortcuts"));
		addFamilyCard(transports, "useGrappleShortcuts", ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE,
			familyThresholdRows("useGrappleShortcuts"));
		addFamilyCard(transports, "useBoats", ItemID.AHOY_TOY_BOAT,
			familyThresholdRows("useBoats"));
		addFamilyCard(transports, "useCanoes", ItemID.CANOEING_PADDLE,
			familyThresholdRows("useCanoes"));
		addFamilyCard(transports, "useCharterShips", ItemID.SHILOSHIPTICKET,
			familyThresholdRows("useCharterShips"));
		addFamilyCard(transports, "useShips", ItemID.GRANDTREE_WARSHIP,
			familyThresholdRows("useShips"));
		addFamilyCard(transports, "useFairyRings", ItemID.DRAMEN_STAFF,
			familyThresholdRows("useFairyRings"));
		addFamilyCard(transports, "useGnomeGliders", ItemID.ALUFT_GNOME_GOGGLES,
			familyThresholdRows("useGnomeGliders"));
		// The balloon card's detail well also carries the per-route basket
		// group — the basket set is account state (which baskets are
		// stocked), not routing state, so the group stays editable while
		// the balloon family itself is switched off.
		List<SearchRow> balloonDetail = new ArrayList<>(
			List.of(familyThresholdRows("useHotAirBalloons")));
		SearchRow basketRoutes = enumSetGroupRow("balloonLogBasketRoutes",
			BalloonRoute.class, "Log Basket Routes", List.of(BalloonRoute.values()));
		basketRoutes.component.putClientProperty(ALWAYS_ENABLED, Boolean.TRUE);
		balloonDetail.add(basketRoutes);
		addFamilyCard(transports, "useHotAirBalloons", ItemID.ZEP_TOY_BALLOON_RED,
			balloonDetail.toArray(new SearchRow[0]));
		// The spriteless MAGIC_CARPET placeholder renders empty — an
		// opulent-rug sprite is the closest real item to a flying carpet.
		addFamilyCard(transports, "useMagicCarpets", ItemID.POH_RUG_3,
			familyThresholdRows("useMagicCarpets"));
		addFamilyCard(transports, "useMagicMushtrees", ItemID.EVENT_MUSHTREE_DUMMY,
			familyThresholdRows("useMagicMushtrees"));
		addFamilyCard(transports, "useMinecarts", ItemID.DWARF_MINECART_TICKET_KELDA_ICE,
			familyThresholdRows("useMinecarts"));

		addFamilyCard(transports, "useQuetzals", ItemID.HG_QUETZALWHISTLE_BASIC,
			spinnerRow("costQuetzals"), spinnerRow("costQuetzalWhistle"));
		addFamilyCard(transports, "useSpiritTrees", ItemID.PLANTPOT_SPIRIT_TREE_SAPLING,
			familyThresholdRows("useSpiritTrees"));

		// Masterless: the mode combo lives in General — this card groups the
		// teleport-item cost thresholds and still counts in "n/N on" while
		// the mode is anything but NONE.
		addFamilyCard(transports, "useTeleportationItems", ItemID.RING_OF_DUELING_8, true,
			spinnerRow("costConsumableTeleportationItems"),
			spinnerRow("costNonConsumableTeleportationItems"),
			spinnerRow("costTeleportationBoxes"));

		addFamilyCard(transports, "useTeleportationLevers", ItemID.POH_LEVER_OAK,
			familyThresholdRows("useTeleportationLevers"));
		addFamilyCard(transports, "useTeleportationPortals", ItemID.DT2_ANIMA_PORTAL,
			familyThresholdRows("useTeleportationPortals"));
		addFamilyCard(transports, "useTeleportationSpells", ItemID.LAWRUNE,
			familyThresholdRows("useTeleportationSpells"));
		addFamilyCard(transports, "useTeleportationSpellsHome", ItemID.POH_TABLET_TELEPORTTOHOUSE,
			familyThresholdRows("useTeleportationSpellsHome"));
		// The MINIGAME_TELEPORT spell-icon item has no inventory sprite —
		// the spellbook icon's flag motif maps to a real banner item.
		addFamilyCard(transports, "useTeleportationMinigames", ItemID.CASTLEWARS_SARADOMIN_BANNER,
			familyThresholdRows("useTeleportationMinigames"));
		addFamilyCard(transports, "useWildernessObelisks", ItemID.POH_SUPERIOR_GARDEN_WILDERNESS_OBELISK,
			familyThresholdRows("useWildernessObelisks"));
		addFamilyCard(transports, "useSeasonalTransports", ItemID.LEAGUE_TWISTED_HOME_TELEPORT,
			familyThresholdRows("useSeasonalTransports"));
	}

	private void buildRestrictionsCategory()
	{
		CategoryPanel restrictionsCategory = new CategoryPanel("Teleport Restrictions", "", this::showHome);
		categories.add(restrictionsCategory);
		restrictionListPanel = new RestrictionListPanel(this, settings, client,
			clientThread, itemState, restrictionsCategory.matchText());
		restrictionsCategory.addRow(restrictionListPanel,
			"Teleport Restrictions", searchable("teleport restrictions"));

		JCheckBox owned = new JCheckBox("Owned");
		owned.setOpaque(false);
		owned.setForeground(Color.WHITE);
		owned.setToolTipText(html(OWNED_FILTER_TOOLTIP));
		owned.addActionListener(e ->
		{
			owned.setForeground(owned.isSelected() ? ColorScheme.BRAND_ORANGE : Color.WHITE);
			restrictionListPanel.setOwnedOnly(owned.isSelected());
		});
		restrictionsCategory.setTrailing(owned);
	}

	/**
	 * The house section is a single flat transport-family card: the
	 * {@code usePoh} master gates the same dim/disable contract every
	 * family card carries, and the detail rows — the sub-feature toggles
	 * plus the two captioned sets of collapsible enum groups — sit always
	 * expanded beneath it without the expandable frame. One card is the
	 * whole section, so the collapsible chrome would only frame a lone
	 * boxed block; the flat variant reads like a plain labelled group.
	 */
	private void buildPohCategory()
	{
		CategoryPanel poh = new CategoryPanel("Player-Owned House", "", this::showHome);
		categories.add(poh);

		List<SearchRow> detail = new ArrayList<>();
		detail.add(checkRow("usePohFairyRing"));
		detail.add(checkRow("usePohSpiritTree"));
		detail.add(checkRow("usePohObelisk"));
		detail.add(comboRow("pohJewelleryBoxTier"));
		// The five nexus groups and the mounted group share identical
		// chrome — the captions split them into their two named sets.
		detail.add(captionRow("Portal Nexus", configName("pohNexusPortals")));
		detail.addAll(setGroupedSubListRows("pohNexusPortals", PohNexusPortal.class, PohNexusPortal::group));
		detail.add(captionRow("Mounted Items", configName("pohMountedItems")));
		detail.add(enumSetGroupRow("pohMountedItems", PohMountedItem.class,
			"All items", List.of(PohMountedItem.values())));

		addFlatFamilyCard(poh, "usePoh", ItemID.POH_TABLET_TELEPORTTOHOUSE,
			detail.toArray(new SearchRow[0]));
	}

	// ---- Navigation ---------------------------------------------------------

	/**
	 * The landing view: a two-column grid of tiles — one per section
	 * (icon, name and a live count line, drilling into the section on
	 * click) plus a Settings tile that opens the plugin's stock config
	 * page — over a footer row of external link buttons.
	 */
	private JComponent homeView()
	{
		JPanel grid = new JPanel(new DynamicGridLayout(0, 2, TILE_GAP, TILE_GAP));
		grid.setOpaque(false);
		for (CategoryPanel category : categories)
		{
			Tile tile = new Tile(category, () -> showSection(category));
			if ("Transports".equals(category.categoryName()))
			{
				// The Transports tile wears the world map's transport
				// POI glyph — a client sprite, not an item icon.
				loadSpriteIcon(SpriteID.Mapfunction.TRANSPORTATION,
					tile.iconLabel, TILE_ICON_SIZE);
			}
			else
			{
				loadItemIcon(tileIconFor(category), tile.iconLabel, TILE_ICON_SIZE);
			}
			tiles.add(tile);
			grid.add(tile);
		}

		// The fourth tile deep-links the plugin's stock config page;
		// its white cog item sprite matches the other tiles' item-icon
		// imagery.
		Tile settingsTile = new Tile("Settings", this::openConfigurationPanel);
		loadItemIcon(ItemID.WHITECOG, settingsTile.iconLabel, TILE_ICON_SIZE);
		tiles.add(settingsTile);
		grid.add(settingsTile);

		FixedWidthPanel content = new FixedWidthPanel();
		content.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(PluginPanel.BORDER_OFFSET, 8, PluginPanel.BORDER_OFFSET, 8));
		content.add(grid);
		content.add(linkRow());

		FixedWidthPanel view = new FixedWidthPanel();
		view.setLayout(new BorderLayout());
		view.add(content, BorderLayout.NORTH);
		JScrollPane scrollPane = new JScrollPane(view);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		return scrollPane;
	}

	/**
	 * Drill into a section: it fills the body under the pinned search
	 * strip; the back arrow in its header returns to the tile grid.
	 */
	private void showSection(CategoryPanel category)
	{
		((CardLayout) body.getLayout()).show(body, category.categoryName());
	}

	/**
	 * Back to the tile grid — the count lines recompute so the tiles
	 * always show live state.
	 */
	private void showHome()
	{
		((CardLayout) body.getLayout()).show(body, HOME_VIEW);
		refreshTiles();
	}

	private void refreshTiles()
	{
		for (Tile tile : tiles)
		{
			if (tile.category != null)
			{
				tile.statusLabel.setText(tileStatus(tile.category));
			}
		}
	}

	/**
	 * The item sprite a section's tile carries — the same item imagery
	 * the family cards use. The Transports tile is the one exception:
	 * it wears the world map's transport POI sprite, handled at build
	 * time in {@link #homeView}.
	 */
	private static int tileIconFor(CategoryPanel category)
	{
		switch (category.categoryName())
		{
			case "Teleport Restrictions":
				return ItemID.RING_OF_DUELING_8;
			case "Player-Owned House":
				return ItemID.POH_TABLET_TELEPORTTOHOUSE;
			default:
				return ItemID.TRAIL_SEXTANT;
		}
	}

	/**
	 * The live count line under a tile's name — an "n/N on" card tally
	 * for Transports, the restriction count for Teleport Restrictions
	 * and the master's on/off for Player-Owned House.
	 */
	private String tileStatus(CategoryPanel category)
	{
		switch (category.categoryName())
		{
			case "Transports":
				int on = 0;
				int total = 0;
				for (SearchRow row : category.rows())
				{
					if (row.component instanceof TransportFamilyCard)
					{
						total++;
						if (((TransportFamilyCard) row.component).isMasterOn())
						{
							on++;
						}
					}
				}
				return on + "/" + total + " on";
			case "Teleport Restrictions":
				if (TeleportationItem.NONE.equals(
					settings.configuredValue("useTeleportationItems")))
				{
					return "Items disabled";
				}
				int restricted = restrictionListPanel == null
					? 0 : restrictionListPanel.restrictionCount();
				return restricted == 0 ? "All allowed" : restricted + " restricted";
			case "Player-Owned House":
				return settings.configuredBool("usePoh") ? "On" : "Off";
			default:
				return "";
		}
	}

	/**
	 * Deep-links into the plugin's stock config page — the Settings tile
	 * posts the same overlay-config menu click a real overlay right-click
	 * would send, so the config surface resolves the page to open off the
	 * event overlay's plugin.
	 */
	private void openConfigurationPanel()
	{
		eventBus.post(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, null, null),
			configOverlay));
	}

	/**
	 * The footer link row under the tile grid: a thin divider, then small
	 * square chromeless buttons opening the project's GitHub page and the
	 * Discord invite through the system browser.
	 */
	private JComponent linkRow()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		row.setOpaque(false);
		row.setBorder(new CompoundBorder(
			new MatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(6, 0, 0, 0)));
		row.add(linkButton(GITHUB_ICON, "GitHub", GITHUB_URL));
		row.add(linkButton(DISCORD_ICON, "Discord", DISCORD_URL));
		return row;
	}

	private static JButton linkButton(ImageIcon icon, String tooltip, String url)
	{
		JButton button = footerButton(icon, tooltip);
		// The URL doubles as the action command so headless checks can
		// read the target without firing a real browser open.
		button.setActionCommand(url);
		button.addActionListener(e -> LinkBrowser.browse(url));
		return button;
	}

	private static JButton footerButton(ImageIcon icon, String tooltip)
	{
		JButton button = new JButton(icon);
		button.setToolTipText(tooltip);
		button.setPreferredSize(new Dimension(FOOTER_BUTTON_EDGE, FOOTER_BUTTON_EDGE));
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setContentAreaFilled(false);
		button.setBorderPainted(false);
		button.setFocusPainted(false);
		button.setOpaque(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	private static ImageIcon footerIcon(String file)
	{
		BufferedImage image = ImageUtil.loadImageResource(
			ShortestPathPanel.class, "/shortestpath/" + file);
		return new ImageIcon(
			ImageUtil.resizeImage(image, FOOTER_ICON_SIZE, FOOTER_ICON_SIZE));
	}

	/**
	 * A render-nothing {@link Overlay} — {@link Overlay} is abstract, so
	 * the config-open trick needs a concrete subclass to carry the plugin
	 * reference the posted menu click resolves through.
	 */
	private static final class ConfigHandleOverlay extends Overlay
	{
		ConfigHandleOverlay(Plugin plugin)
		{
			super(plugin);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			return null;
		}
	}

	// ---- Card + row builders ------------------------------------------------

	/**
	 * Creates a transport-family card bound to {@code useKey} and appends it to
	 * the category as a searchable row. The card title is the config item name
	 * minus its leading "Use " (the checkbox carries the on/off meaning).
	 */
	private TransportFamilyCard addFamilyCard(CategoryPanel category, String useKey, Integer iconItemId,
		SearchRow... detailRows)
	{
		return addFamilyCard(category, useKey, iconItemId, false, detailRows);
	}

	/**
	 * A family card without the collapsible chrome — used where a card is
	 * a section's only row (the house section), so its detail sits always
	 * expanded beneath the master row rather than behind a lone boxed
	 * expander.
	 */
	private TransportFamilyCard addFlatFamilyCard(CategoryPanel category, String useKey, Integer iconItemId,
		SearchRow... detailRows)
	{
		return addFamilyCard(category, useKey, iconItemId, false, true, detailRows);
	}

	/**
	 * {@code masterless} cards render no master control — their on/off state
	 * is owned elsewhere (the teleport-items mode combo lives in General) and
	 * {@link TransportFamilyCard#isMasterOn()} reads it straight from config.
	 */
	private TransportFamilyCard addFamilyCard(CategoryPanel category, String useKey, Integer iconItemId,
		boolean masterless, SearchRow... detailRows)
	{
		return addFamilyCard(category, useKey, iconItemId, masterless, false, detailRows);
	}

	private TransportFamilyCard addFamilyCard(CategoryPanel category, String useKey, Integer iconItemId,
		boolean masterless, boolean flat, SearchRow... detailRows)
	{
		ConfigItems.Meta item = settings.item(useKey);
		String name = item == null ? useKey : item.name();
		String title = name.startsWith("Use ") ? name.substring("Use ".length()) : name;
		String description = item == null ? "" : item.description();

		TransportFamilyCard card = new TransportFamilyCard(this, useKey, title, description, iconItemId, masterless, flat);
		transportCards.add(card);

		List<String> searchParts = new ArrayList<>();
		searchParts.add(name);
		for (SearchRow row : detailRows)
		{
			card.addDetailRow(row.component);
			searchParts.add(row.text);
		}
		category.addRow(new SearchRow(card, title, searchable(searchParts.toArray(new String[0]))));
		return card;
	}

	/**
	 * The {@code cost*} key matching a {@code use*} family — the pair is named
	 * identically apart from the prefix, so this resolves e.g.
	 * useAgilityShortcuts → costAgilityShortcuts.
	 */
	private SearchRow[] familyThresholdRows(String useKey)
	{
		String costKey = "cost" + useKey.substring("use".length());
		return settings.item(costKey) != null ? new SearchRow[]{spinnerRow(costKey)} : new SearchRow[0];
	}

	/**
	 * The row tail every label + control row shares: an html-wrapped label
	 * in CENTER and the control in EAST of a transparent BorderLayout row —
	 * {@code control} == null renders a label-only degrade row.
	 */
	private SearchRow labeledRow(String name, String description, JComponent control)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JLabel label = new JLabel(html(name));
		label.setForeground(Color.WHITE);
		label.setToolTipText(html(description));
		row.add(label, BorderLayout.CENTER);
		if (control != null)
		{
			row.add(control, BorderLayout.EAST);
		}
		return new SearchRow(row, name, searchable(name));
	}

	private SearchRow checkRow(String keyName)
	{
		return checkRow(keyName, null);
	}

	private SearchRow checkRow(String keyName, Runnable onChange)
	{
		ConfigItems.Meta item = settings.item(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		JCheckBox checkBox = new JCheckBox();
		checkBox.setSelected(settings.configuredBool(keyName));
		checkBox.setToolTipText(html(description));
		PanelRows.controlRow(this, keyName, checkBox,
			(control, guard) -> control.addActionListener(e ->
			{
				// write()'s synchronous listener delivery runs onChange —
				// calling it here too would invoke it twice per click.
				PanelRows.guardedWrite(guard,
					() -> settings.write(keyName, control.isSelected()));
			}),
			() -> checkBox.setSelected(settings.configuredBool(keyName)),
			onChange);
		return labeledRow(name, description, checkBox);
	}

	private SearchRow spinnerRow(String keyName)
	{
		ConfigItems.Meta item = settings.item(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		Object value = settings.configuredValue(keyName);
		int current = value instanceof Number ? ((Number) value).intValue() : 0;
		// The declared range is honoured verbatim: keys like
		// recalculateDistance/finishDistance take a negative min whose
		// -1 sentinel means "never", so no floor is applied.
		int min = item == null || item.min() == null ? 0 : item.min();
		Integer max = item == null || item.max() == null || item.max() == Integer.MAX_VALUE ? null : item.max();
		// A persisted value can land outside the model's bounds (hand-edited
		// settings, or keys without a @Range bound the stock panel leaves
		// unclamped) — SpinnerNumberModel throws on that, so clamp to the
		// nearest bound instead of letting panel construction fail.
		current = Math.max(min, Math.min(max == null ? Integer.MAX_VALUE : max.intValue(), current));

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(
			Integer.valueOf(current), Integer.valueOf(min), max, Integer.valueOf(1)));
		((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(SPINNER_FIELD_WIDTH);
		spinner.setToolTipText(html(description));
		PanelRows.controlRow(this, keyName, spinner,
			(control, guard) -> control.addChangeListener(e ->
				PanelRows.guardedWrite(guard,
					() -> settings.write(keyName, control.getValue()))),
			() ->
			{
				Object updated = settings.configuredValue(keyName);
				if (updated instanceof Number)
				{
					// Same clamp as construction: an out-of-range external
					// write degrades to the nearest bound rather than
					// throwing inside the listener chain.
					int updatedInt = ((Number) updated).intValue();
					spinner.setValue(Math.max(min,
						Math.min(max == null ? Integer.MAX_VALUE : max.intValue(), updatedInt)));
				}
			},
			null);
		return labeledRow(name, description, spinner);
	}

	private SearchRow comboRow(String keyName)
	{
		ConfigItems.Meta item = settings.item(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		Object value = settings.configuredValue(keyName);
		if (!(value instanceof Enum))
		{
			// A null or corrupt persisted value gives no enum type to derive
			// the constants from — render a label-only row (mirroring the
			// TransportFamilyCard degrade path) rather than throwing during
			// panel construction.
			return labeledRow(name, description, null);
		}
		@SuppressWarnings("unchecked")
		Class<? extends Enum> type = (Class<? extends Enum>) ((Enum<?>) value).getClass();
		JComboBox<Enum<?>> combo = new JComboBox<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
		combo.setSelectedItem(value);
		PanelComboStyle.apply(combo);
		combo.setToolTipText(html(description));
		PanelRows.controlRow(this, keyName, combo,
			(control, guard) -> control.addItemListener(e ->
			{
				if (e.getStateChange() == ItemEvent.SELECTED)
				{
					PanelRows.guardedWrite(guard,
						() -> settings.write(keyName, control.getSelectedItem()));
				}
			}),
			() -> combo.setSelectedItem(settings.configuredValue(keyName)),
			null);
		return labeledRow(name, description, combo);
	}

	/**
	 * A section caption inside a card detail well — names the grouped rows
	 * that follow it, rendered in the panel's regular row typography so it
	 * reads as an ordinary label rather than a small secondary hint. The
	 * caption is pure chrome: it owns no control, but it dims with the card
	 * master like every detail row, and its search text covers the
	 * section's config name so a matching query keeps the card surfaced.
	 */
	private SearchRow captionRow(String caption, String configName)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JLabel label = new JLabel(caption);
		label.setForeground(Color.WHITE);
		row.add(label, BorderLayout.CENTER);
		return new SearchRow(row, caption, searchable(caption, configName));
	}

	/**
	 * A {@code Set<Enum>} config rendered as one {@link CollapsibleEnumGroup}
	 * per value of {@code groupingFn}, emitted in the group enum's
	 * declaration order (e.g. PortalGroup STANDARD … OTHER). Grouping is
	 * display-only — the member checkboxes keep the same
	 * read-set → checkbox → write-EnumSet mechanics the flat sub-list used.
	 */
	private <E extends Enum<E>, G extends Enum<G>> List<SearchRow> setGroupedSubListRows(
		String keyName, Class<E> type, Function<E, G> groupingFn)
	{
		Map<G, List<E>> buckets = new LinkedHashMap<>();
		for (E constant : type.getEnumConstants())
		{
			buckets.computeIfAbsent(groupingFn.apply(constant), g -> new ArrayList<>())
				.add(constant);
		}
		List<G> groupOrder = new ArrayList<>(buckets.keySet());
		groupOrder.sort(Comparator.comparingInt(Enum::ordinal));
		List<SearchRow> rows = new ArrayList<>();
		for (G group : groupOrder)
		{
			rows.add(enumSetGroupRow(keyName, type,
				titleCase(group.name().toLowerCase(Locale.ROOT)), buckets.get(group)));
		}
		return rows;
	}

	/**
	 * One {@link CollapsibleEnumGroup} over {@code members} as a searchable
	 * row: display text is the group label, search text covers the config
	 * name, the label and every member label so a member-only query still
	 * surfaces (and expands) the group.
	 */
	private <E extends Enum<E>> SearchRow enumSetGroupRow(
		String keyName, Class<E> type, String groupLabel, List<E> members)
	{
		ConfigItems.Meta item = settings.item(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		CollapsibleEnumGroup<E> group = new CollapsibleEnumGroup<>(
			this, keyName, type, groupLabel, members, description);
		List<String> searchParts = new ArrayList<>();
		searchParts.add(name);
		searchParts.add(groupLabel);
		for (E member : members)
		{
			searchParts.add(member.toString());
		}
		return new SearchRow(group, groupLabel,
			searchable(searchParts.toArray(new String[0])));
	}

	// ---- Config access ------------------------------------------------------

	/**
	 * Register a {@link Settings#listen} callback owned by this panel; the
	 * unlisten handle is retained for {@link #dispose()}. Sub-components
	 * (family cards, the restriction list) register through here too so one
	 * teardown drops the whole graph.
	 */
	void registerConfigListener(String key, Runnable listener)
	{
		unlisteners.add(settings.listen(key, listener));
	}

	/**
	 * Drop every config listener this panel registered. Called from plugin
	 * shutdown before the panel reference is released.
	 */
	void dispose()
	{
		for (Runnable unlisten : unlisteners)
		{
			unlisten.run();
		}
		unlisteners.clear();
	}

	private String configName(String keyName)
	{
		ConfigItems.Meta item = settings.item(keyName);
		return item == null ? keyName : item.name();
	}

	@SuppressWarnings("unchecked")
	<E extends Enum<E>> Set<E> currentSet(String keyName, Class<E> type)
	{
		Set<E> set = EnumSet.noneOf(type);
		Set<?> value = settings.configuredSet(keyName);
		if (value != null)
		{
			set.addAll((Set<E>) value);
		}
		return set;
	}

	/**
	 * Inventory/equipment/bank contents changed: refresh the restriction
	 * checklist's owned-item snapshot. The container reads themselves are
	 * marshalled onto the client thread inside {@link RestrictionListPanel}.
	 */
	void onItemContainersChanged()
	{
		if (restrictionListPanel != null)
		{
			restrictionListPanel.refreshOwnedItems();
		}
	}

	void loadItemIcon(int itemId, JLabel label)
	{
		loadItemIcon(itemId, label, CARD_ICON_SIZE);
	}

	/**
	 * Same item-icon load at a caller-chosen edge size — the home tiles
	 * render their category sprite larger than the 16px card icons.
	 */
	void loadItemIcon(int itemId, JLabel label, int size)
	{
		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() -> SwingUtilities.invokeLater(() ->
			label.setIcon(new ImageIcon(ImageUtil.resizeImage(image, size, size)))));
	}

	/**
	 * The client-sprite counterpart to {@link #loadItemIcon}: some tiles
	 * carry cache sprites rather than item imagery (the Display tile uses
	 * the Settings&gt;Display monitor glyph). {@code getSpriteAsync} runs
	 * through {@link ClientThread}, which re-queues the request until the
	 * sprite cache is loaded — the icon lands whenever the client reaches
	 * the login screen.
	 */
	void loadSpriteIcon(int spriteId, JLabel label, int size)
	{
		spriteManager.getSpriteAsync(spriteId, 0, image ->
			SwingUtilities.invokeLater(() ->
				label.setIcon(new ImageIcon(ImageUtil.resizeImage(image, size, size)))));
	}

	// ---- Search -------------------------------------------------------------

	/**
	 * Live filter across the drilled sections: every section rebuilds its
	 * own visible rows in place on each keystroke; clearing restores the
	 * full row set.
	 */
	private void onSearchChanged()
	{
		currentQuery = searchBar.getText().trim();
		for (CategoryPanel category : categories)
		{
			category.applyQuery(currentQuery);
		}
	}

	static String searchable(String... parts)
	{
		return String.join(" ", parts).toLowerCase(Locale.ROOT);
	}

	/**
	 * Wraps {@code text} in an {@code <html>} body: a label or checkbox
	 * renders {@code <html>}-tagged content word-wrapped to its laid-out
	 * width instead of clipping at the fixed 225px viewport. Applied to
	 * every panel label that can outgrow its row.
	 */
	static String html(String text)
	{
		return "<html>" + text + "</html>";
	}

	/**
	 * Pins the shared expandable contract on a surface's two children:
	 * the header bar turns opaque in {@link #EXPANDABLE_HEADER_BACKGROUND}
	 * and the detail well keeps the {@link #EXPANDABLE_DETAIL_BACKGROUND}
	 * category tone framed by a 3px {@link #EXPANDABLE_DETAIL_BORDER}
	 * border on its left, bottom and right edges — the top edge stays
	 * open so the well joins the header bar above it. The frame wraps —
	 * never replaces — the border each call site already installed, so
	 * the detail indents and paddings survive. Their children keep their
	 * transparent fills so the tones show through.
	 */
	static void styleExpandable(JComponent header, JComponent detail)
	{
		header.setOpaque(true);
		header.setBackground(EXPANDABLE_HEADER_BACKGROUND);
		detail.setOpaque(true);
		detail.setBackground(EXPANDABLE_DETAIL_BACKGROUND);
		detail.setBorder(new CompoundBorder(
			new MatteBorder(0, 3, 3, 3, EXPANDABLE_DETAIL_BORDER),
			detail.getBorder()));
	}

	/**
	 * Capitalises the first letter of every word — the same convention
	 * RuneLite's {@code TitleCaseListCellRenderer} applies to enum values —
	 * so data-derived labels (restriction family names) match the Title
	 * Case the config names carry. Word boundaries: whitespace, '(' and
	 * '-'.
	 */
	static String titleCase(String text)
	{
		StringBuilder sb = new StringBuilder(text.length());
		boolean wordStart = true;
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c == ' ' || c == '\t' || c == '(' || c == '-')
			{
				wordStart = true;
			}
			else if (wordStart && Character.isLetter(c))
			{
				c = Character.toUpperCase(c);
				wordStart = false;
			}
			else
			{
				wordStart = false;
			}
			sb.append(c);
		}
		return sb.toString();
	}

	static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;");
	}

	/**
	 * Dims a whole row subtree (labels grey out via their disabled rendering)
	 * and disables every interactive descendant. Kept package-visible as the
	 * seam the teleport-restriction rows reuse for their blocked state.
	 */
	static void applyRowDisabledState(JComponent root, boolean enabled)
	{
		applyEnabledDeep(root, enabled);
	}

	/**
	 * Client-property marker keeping a detail-row subtree editable no
	 * matter the surrounding row/card state — used by controls like the
	 * balloon log-basket routes, which record account state (which baskets
	 * the account has stocked) and must stay editable even while the
	 * transport family itself is switched off.
	 */
	static final String ALWAYS_ENABLED = "shortestpath.alwaysEnabled";

	/**
	 * Client-property marker identifying a home-grid {@link Tile}. The
	 * home scroll wrap's own chrome contains plain {@link JButton}s on
	 * look-and-feels that draw scrollbar arrow buttons (Metal, Nimbus),
	 * so a bare {@code instanceof JButton} sweep cannot tell a tile from
	 * platform chrome.
	 */
	static final String HOME_TILE = "shortestpath.homeTile";

	private static void applyEnabledDeep(Component component, boolean enabled)
	{
		if (component instanceof JComponent
			&& Boolean.TRUE.equals(((JComponent) component).getClientProperty(ALWAYS_ENABLED)))
		{
			return;
		}
		component.setEnabled(enabled);
		if (component instanceof DimmableJPanel)
		{
			((DimmableJPanel) component).setDimmed(!enabled);
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				applyEnabledDeep(child, enabled);
			}
		}
	}

	/**
	 * One home-grid tile: a square raised button — centered section icon
	 * on top, the bold section name beneath it and a live count line at
	 * the bottom — painted with a bevelled edge that lowers while the
	 * button is pressed and an orange accent that lifts while armed
	 * (hover). A real {@link JButton}, so Swing's button model supplies
	 * the armed/pressed states and the click action for free; the labels
	 * inside carry no listeners of their own, so a click anywhere on the
	 * tile reaches the button.
	 */
	private static final class Tile extends JButton
	{
		private static final SoftBevelBorder RAISED = new SoftBevelBorder(BevelBorder.RAISED);
		private static final SoftBevelBorder LOWERED = new SoftBevelBorder(BevelBorder.LOWERED);
		/**
		 * The tile's own text inset — the platform button border, though
		 * never painted, would otherwise shrink the label column.
		 */
		private static final int TEXT_INSET = 4;

		/**
		 * The section this tile drills into — {@code null} for action
		 * tiles like Settings, which open no section and carry no live
		 * count line.
		 */
		private final CategoryPanel category;
		private final JLabel iconLabel;
		private final JLabel statusLabel;

		Tile(CategoryPanel category, Runnable onClick)
		{
			this(category.categoryName(), onClick, category);
		}

		Tile(String name, Runnable onClick)
		{
			this(name, onClick, null);
		}

		private Tile(String name, Runnable onClick, CategoryPanel category)
		{
			this.category = category;
			setLayout(new BorderLayout());
			// The bevel + fill are painted by hand, so the platform
			// button chrome stays off — only the model states are wanted.
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setBorder(new EmptyBorder(TEXT_INSET, TEXT_INSET, TEXT_INSET, TEXT_INSET));
			setToolTipText(name);
			putClientProperty(HOME_TILE, Boolean.TRUE);
			addActionListener(e -> onClick.run());
			// Armed/pressed transitions repaint so the pressed animation
			// shows even though no UI delegate draws a state effect.
			getModel().addChangeListener(e -> repaint());

			iconLabel = new JLabel();
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
			iconLabel.setPreferredSize(new Dimension(0, TILE_ICON_SIZE + 8));
			add(iconLabel, BorderLayout.NORTH);

			// The html wrap keeps long names ("Teleport Restrictions",
			// "Player-Owned House") readable at the tile edge; a single
			// token wider than the allotted label width ("Player-Owned")
			// falls back to the small font rather than clipping mid-glyph.
			JLabel nameLabel = new JLabel(
				html("<center>" + name + "</center>"), SwingConstants.CENTER);
			nameLabel.setForeground(Color.WHITE);
			Font bold = FontManager.getRunescapeBoldFont();
			nameLabel.setFont(
				widestTokenWidth(name, nameLabel.getFontMetrics(bold))
					<= TILE_EDGE - 2 * TEXT_INSET
				? bold
				: FontManager.getRunescapeSmallFont());
			add(nameLabel, BorderLayout.CENTER);

			statusLabel = new JLabel("", SwingConstants.CENTER);
			statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			statusLabel.setFont(FontManager.getRunescapeSmallFont());
			add(statusLabel, BorderLayout.SOUTH);
		}

		/**
		 * The widest unbreakable run in a tile name — the html wrap only
		 * breaks at spaces, so this is the minimum width the name can
		 * render without clipping.
		 */
		private static int widestTokenWidth(String name, FontMetrics metrics)
		{
			int widest = 0;
			for (String token : name.split(" "))
			{
				widest = Math.max(widest, metrics.stringWidth(token));
			}
			return widest;
		}

		/**
		 * Square preferred size: the uniform grid then resolves every
		 * cell to the same square bounds at the pinned content width.
		 */
		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(TILE_EDGE, TILE_EDGE);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), getHeight());
			super.paintComponent(g);

			(getModel().isPressed() ? LOWERED : RAISED)
				.paintBorder(this, g, 0, 0, getWidth(), getHeight());

			g.setColor(ColorScheme.BRAND_ORANGE);
			if (getModel().isArmed())
			{
				g.drawRect(3, 3, getWidth() - 7, getHeight() - 7);
			}
			else
			{
				g.drawLine(3, getHeight() - 4, getWidth() - 4, getHeight() - 4);
			}
		}
	}

	static final class SearchRow
	{
		final JComponent component;
		// The human-readable label a search-result row shows for this match.
		final String display;
		final String text;

		SearchRow(JComponent component, String display, String text)
		{
			this.component = component;
			this.display = display;
			this.text = text;
		}
	}
}
