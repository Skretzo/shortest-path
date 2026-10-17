package shortestpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.MultiplexingPluginPanel;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.DimmableJPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.TitleCaseListCellRenderer;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.items.ItemStateService;
import shortestpath.settings.ConfigItems;
import shortestpath.settings.Settings;
import shortestpath.pathfinder.PathfinderBackend;
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
 * <p>Navigation is a tile drill-down: a pinned search strip sits NORTH of a
 * {@link MultiplexingPluginPanel} card-stack whose root is the six-tile
 * {@link ShortestPathHomePanel}; each tile pushes its {@link CategoryPanel}
 * sub-panel and the sub-panel's back arrow pops home. Searching pushes the
 * home surface, which renders results grouped by category and deep-links
 * into the matching sub-panel.</p>
 */
public class ShortestPathPanel extends PluginPanel
{
	static final int SECTION_TOGGLE_WIDTH = 18;
	static final int CARD_ICON_SIZE = 16;
	private static final int SPINNER_FIELD_WIDTH = 6;
	private static final int COMBO_HEIGHT = 22;
	private static final int SUB_LIST_INDENT = 16;
	private static final String SEARCH_TOOLTIP = "Search transports, unlocks and restrictions";
	private static final String OWNED_FILTER_TOOLTIP =
		"Only show teleports for items in your inventory, equipment or bank";
	static final String OWNED_EMPTY_LINE_1 =
		"No teleport items detected in inventory, equipment or bank.";
	static final String OWNED_EMPTY_LINE_2 =
		"Bank items are remembered from the last time you opened it on this account.";
	static final String RESTRICTIONS_DISABLED_HINT =
		"Teleportation items are disabled. Enable \"Use Teleportation Items\" "
			+ "in General to restrict individual items.";

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
	private final ItemStateService itemState;

	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true) private final IconTextField searchBar;
	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true) private final MultiplexingPluginPanel muxer;
	@Getter @Accessors(fluent = true) private final ShortestPathHomePanel home;
	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true)
	private final List<CategoryPanel> categories = new ArrayList<>();
	private final List<TransportFamilyCard> transportCards = new ArrayList<>();

	/**
	 * Unregistration handles for every {@link Settings#listen} callback this
	 * panel (and its cards/restriction list) registered. {@link Settings} is
	 * a singleton that outlives the panel — {@link #dispose()} runs them all
	 * on teardown so a dead panel neither keeps firing nor stays reachable.
	 */
	private final List<Runnable> unlisteners = new CopyOnWriteArrayList<>();

	// Every POH control below the master row; dimmed + disabled while the
	// master is off, same contract as the transport cards.
	private final List<SearchRow> pohSubRows = new ArrayList<>();

	private CategoryPanel restrictionsCategory;
	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true) private RestrictionListPanel restrictionListPanel;
	/**
	 * The trimmed text currently in the search field — the query sub-panels
	 * apply when they activate.
	 */
	@Getter(AccessLevel.PACKAGE) private String currentQuery = "";

	ShortestPathPanel(Settings settings, Client client,
		ClientThread clientThread, ItemManager itemManager, ItemStateService itemState)
	{
		super(false);

		this.settings = settings;
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.itemState = itemState;

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

		// The categories exist before the home view so the tile callbacks
		// can push them; the muxer roots at home and stacks categories on top.
		buildGeneralCategory();
		buildTransportsCategory();
		buildRestrictionsCategory();
		buildUnlocksCategory();
		buildBankCategory();
		buildPohCategory();

		home = new ShortestPathHomePanel(this);
		muxer = new MultiplexingPluginPanel(home);
		add(muxer, BorderLayout.CENTER);

		// Tile statuses summarize live config: every key a status reads gets
		// a refresh listener so tiles track panel writes and external edits.
		// Ordering matters — listeners run in registration order, and the
		// card / restriction-list syncFromConfig listeners (registered while
		// the categories were built above) must update their model state
		// before home::refresh reads it. Keep these registrations last, or
		// have refresh() re-read config directly, or tiles go stale.
		home.registerRefreshListeners();
	}

	// ---- Category builders -------------------------------------------------

	private void buildGeneralCategory()
	{
		CategoryPanel general = new CategoryPanel(this, "General", "routing");
		categories.add(general);
		general.addRow(checkRow("avoidWilderness"));
		general.addRow(spinnerRow("currencyThreshold"));
		general.addRow(checkRow("respawnPrifddinas"));
		general.addRow(spinnerRow("unreachableTargetDistanceThreshold"));
		general.addRow(checkRow("collisionAwareBlockedTargets"));
		// The teleport-items mode is a routing policy, so its combo lives
		// with the other General rows; its Transports card is masterless.
		general.addRow(comboRow("useTeleportationItems"));
		general.addRow(comboRow("pathfinderAlgorithm"));

		// The heuristic weight only steers the A* backend — dim it while
		// legacy is selected, same contract as the transport cards.
		SearchRow weightRow = spinnerRow("aStarHeuristicWeight");
		general.addRow(weightRow);
		Runnable applyWeightEnabled = () -> applyRowDisabledState(weightRow.component,
			PathfinderBackend.ASTAR.equals(settings.configuredValue("pathfinderAlgorithm")));
		applyWeightEnabled.run();
		registerConfigListener("pathfinderAlgorithm", applyWeightEnabled);
	}

	private void buildTransportsCategory()
	{
		CategoryPanel transports = new CategoryPanel(this, "Transports", "");
		categories.add(transports);

		addFamilyCard(transports, "useAgilityShortcuts", ItemID.AGILITY_JUMP,
			familyThresholdRows("useAgilityShortcuts"));
		addFamilyCard(transports, "useGrappleShortcuts", ItemID.XBOWS_GRAPPLE_HOOK,
			familyThresholdRows("useGrappleShortcuts"));
		addFamilyCard(transports, "useBoats", ItemID.AHOY_TOY_BOAT,
			familyThresholdRows("useBoats"));
		addFamilyCard(transports, "useSailingMoves", ItemID.SAILING_CHARTING_SPYGLASS);
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
		// checkboxes — the legacy single "basket stocked" unlock is gone
		// from Unlocks now that routes are selected per destination.
		List<SearchRow> balloonDetail = new ArrayList<>(
			List.of(familyThresholdRows("useHotAirBalloons")));
		balloonDetail.addAll(balloonRouteRows());
		addFamilyCard(transports, "useHotAirBalloons", ItemID.ZEP_TOY_BALLOON_RED,
			balloonDetail.toArray(new SearchRow[0]));
		addFamilyCard(transports, "useMagicCarpets", ItemID.MAGIC_CARPET,
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
		addFamilyCard(transports, "useTeleportationPortals", ItemID.POH_DUMMY_PORTALNEXUS,
			familyThresholdRows("useTeleportationPortals"));
		addFamilyCard(transports, "useTeleportationSpells", ItemID.LAWRUNE,
			familyThresholdRows("useTeleportationSpells"));
		addFamilyCard(transports, "useTeleportationSpellsHome", ItemID.POH_TABLET_TELEPORTTOHOUSE,
			familyThresholdRows("useTeleportationSpellsHome"));
		addFamilyCard(transports, "useTeleportationMinigames", ItemID.MINIGAME_TELEPORT,
			familyThresholdRows("useTeleportationMinigames"));
		addFamilyCard(transports, "useWildernessObelisks", ItemID.POH_SUPERIOR_GARDEN_WILDERNESS_OBELISK,
			familyThresholdRows("useWildernessObelisks"));
		addFamilyCard(transports, "useSeasonalTransports", ItemID.LEAGUE_TWISTED_HOME_TELEPORT,
			familyThresholdRows("useSeasonalTransports"));
	}

	private void buildRestrictionsCategory()
	{
		restrictionsCategory = new CategoryPanel(this, "Teleport Restrictions", "");
		categories.add(restrictionsCategory);
		restrictionListPanel = new RestrictionListPanel(this, settings, client, clientThread, itemState);
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

	private void buildUnlocksCategory()
	{
		CategoryPanel unlocks = new CategoryPanel(this, "Unlocks", "");
		categories.add(unlocks);
		unlocks.addRow(checkRow("unlockCanoeAxe"));
		unlocks.addRow(checkRow("unlockXericsHonour"));
		unlocks.addRow(checkRow("unlockDragontoothPassage"));
		unlocks.addRow(checkRow("unlockMorUlRek"));
	}

	private void buildBankCategory()
	{
		CategoryPanel bank = new CategoryPanel(this, "Bank", "");
		categories.add(bank);
		bank.addRow(checkRow("includeBankPath"));
		bank.addRow(spinnerRow("costBankVisit"));
	}

	private void buildPohCategory()
	{
		CategoryPanel poh = new CategoryPanel(this, "Player-Owned House", "");
		categories.add(poh);

		SearchRow master = checkRow("usePoh", () -> applyPohEnabled(settings.configuredBool("usePoh")));
		poh.addRow(master);

		pohSubRows.add(checkRow("usePohFairyRing"));
		pohSubRows.add(checkRow("usePohSpiritTree"));
		pohSubRows.add(checkRow("usePohObelisk"));
		pohSubRows.add(comboRow("pohJewelleryBoxTier"));
		pohSubRows.addAll(setSubListRows("pohNexusPortals", PohNexusPortal.values()));
		pohSubRows.addAll(setSubListRows("pohMountedItems", PohMountedItem.values()));
		for (SearchRow row : pohSubRows)
		{
			poh.addRow(row);
		}

		boolean enabled = settings.configuredBool("usePoh");
		for (SearchRow row : pohSubRows)
		{
			applyRowDisabledState(row.component, enabled);
		}
	}

	private void applyPohEnabled(boolean enabled)
	{
		for (SearchRow row : pohSubRows)
		{
			applyRowDisabledState(row.component, enabled);
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
	 * {@code masterless} cards render no master control — their on/off state
	 * is owned elsewhere (the teleport-items mode combo lives in General) and
	 * {@link TransportFamilyCard#isMasterOn()} reads it straight from config.
	 */
	private TransportFamilyCard addFamilyCard(CategoryPanel category, String useKey, Integer iconItemId,
		boolean masterless, SearchRow... detailRows)
	{
		ConfigItems.Meta item = settings.item(useKey);
		String name = item == null ? useKey : item.name();
		String title = name.startsWith("Use ") ? name.substring("Use ".length()) : name;
		String description = item == null ? "" : item.description();

		TransportFamilyCard card = new TransportFamilyCard(this, useKey, title, description, iconItemId, masterless);
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
		boolean[] syncing = new boolean[1];
		checkBox.addActionListener(e ->
		{
			if (!syncing[0])
			{
				// write()'s synchronous listener delivery runs onChange —
				// calling it here too would invoke it twice per click.
				settings.write(keyName, checkBox.isSelected());
			}
		});
		registerConfigListener(keyName, () ->
		{
			syncing[0] = true;
			try
			{
				checkBox.setSelected(settings.configuredBool(keyName));
			}
			finally
			{
				syncing[0] = false;
			}
			if (onChange != null)
			{
				onChange.run();
			}
		});

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JLabel label = new JLabel(html(name));
		label.setForeground(Color.WHITE);
		label.setToolTipText(html(description));
		row.add(label, BorderLayout.CENTER);
		row.add(checkBox, BorderLayout.EAST);
		return new SearchRow(row, name, searchable(name));
	}

	private SearchRow spinnerRow(String keyName)
	{
		ConfigItems.Meta item = settings.item(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		Object value = settings.configuredValue(keyName);
		int current = value instanceof Number ? ((Number) value).intValue() : 0;
		int min = item == null || item.min() == null ? 0 : Math.max(0, item.min());
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
		// Set while a config sync mirrors the stored value into the control —
		// the same guard the family cards use, so the programmatic setValue
		// does not write the mirrored value straight back to config.
		boolean[] syncing = new boolean[1];
		spinner.addChangeListener(e ->
		{
			if (!syncing[0])
			{
				settings.write(keyName, spinner.getValue());
			}
		});
		registerConfigListener(keyName, () ->
		{
			Object updated = settings.configuredValue(keyName);
			if (updated instanceof Number)
			{
				syncing[0] = true;
				try
				{
					// Same clamp as construction: an out-of-range external
					// write degrades to the nearest bound rather than
					// throwing inside the listener chain.
					int updatedInt = ((Number) updated).intValue();
					spinner.setValue(Math.max(min,
						Math.min(max == null ? Integer.MAX_VALUE : max.intValue(), updatedInt)));
				}
				finally
				{
					syncing[0] = false;
				}
			}
		});

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JLabel label = new JLabel(html(name));
		label.setForeground(Color.WHITE);
		label.setToolTipText(html(description));
		row.add(label, BorderLayout.CENTER);
		row.add(spinner, BorderLayout.EAST);
		return new SearchRow(row, name, searchable(name));
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
			JPanel fallbackRow = new JPanel(new BorderLayout());
			fallbackRow.setOpaque(false);
			JLabel fallbackLabel = new JLabel(html(name));
			fallbackLabel.setForeground(Color.WHITE);
			fallbackLabel.setToolTipText(html(description));
			fallbackRow.add(fallbackLabel, BorderLayout.CENTER);
			return new SearchRow(fallbackRow, name, searchable(name));
		}
		@SuppressWarnings("unchecked")
		Class<? extends Enum> type = (Class<? extends Enum>) ((Enum<?>) value).getClass();
		JComboBox<Enum<?>> combo = new JComboBox<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
		combo.setRenderer(new TitleCaseListCellRenderer());
		combo.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		combo.setSelectedItem(value);
		combo.setPreferredSize(new Dimension(combo.getPreferredSize().width, COMBO_HEIGHT));
		combo.setToolTipText(html(description));
		boolean[] syncing = new boolean[1];
		combo.addItemListener(e ->
		{
			if (e.getStateChange() == ItemEvent.SELECTED && !syncing[0])
			{
				settings.write(keyName, combo.getSelectedItem());
			}
		});
		registerConfigListener(keyName, () ->
		{
			syncing[0] = true;
			try
			{
				combo.setSelectedItem(settings.configuredValue(keyName));
			}
			finally
			{
				syncing[0] = false;
			}
		});

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JLabel label = new JLabel(html(name));
		label.setForeground(Color.WHITE);
		label.setToolTipText(html(description));
		row.add(label, BorderLayout.CENTER);
		row.add(combo, BorderLayout.EAST);
		return new SearchRow(row, name, searchable(name));
	}

	/**
	 * A {@code Set<Enum>} config rendered as a group label followed by one
	 * indented checkbox row per enum constant; toggling a box writes the
	 * mutated set back through the shared config path.
	 */
	private <E> List<SearchRow> setSubListRows(String keyName, Collection<E> values)
	{
		ConfigItems.Meta item = settings.item(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		List<SearchRow> rows = new ArrayList<>();
		// Shared across every member checkbox of this key: set while a config
		// sync mirrors the stored set into the controls, so the programmatic
		// setSelected calls do not write the same set back.
		boolean[] syncing = new boolean[1];

		JPanel groupRow = new JPanel(new BorderLayout());
		groupRow.setOpaque(false);
		JLabel groupLabel = new JLabel(html(name));
		groupLabel.setForeground(Color.WHITE);
		groupLabel.setToolTipText(html(description));
		groupRow.add(groupLabel, BorderLayout.CENTER);
		rows.add(new SearchRow(groupRow, name, searchable(name)));

		for (E constant : values)
		{
			JCheckBox checkBox = new JCheckBox(html(constant.toString()));
			checkBox.setSelected(currentSet(keyName, type).contains(constant));
			checkBox.setToolTipText(html(description));
			checkBox.addActionListener(e ->
			{
				if (syncing[0])
				{
					return;
				}
				Set<E> updated = new LinkedHashSet<>(currentSet(keyName));
				if (checkBox.isSelected())
				{
					updated.add(constant);
				}
				else
				{
					updated.remove(constant);
				}
				settings.write(keyName, updated);
			});
			registerConfigListener(keyName, () ->
			{
				syncing[0] = true;
				try
				{
					checkBox.setSelected(currentSet(keyName).contains(constant));
				}
				finally
				{
					syncing[0] = false;
				}
			});

			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.setBorder(new EmptyBorder(0, SUB_LIST_INDENT, 0, 0));
			row.add(checkBox, BorderLayout.CENTER);
			rows.add(new SearchRow(row, constant.toString(), searchable(name, constant.toString())));
		}
		return rows;
	}

	/**
	 * The balloon card's "Log Basket Routes" detail block: a small-font
	 * header then one labelled checkbox per {@link BalloonRoute}. Checked
	 * means that route's destination basket is stocked — its log
	 * requirement is waived. Read/write ride the same {@code Set<Enum>}
	 * path the POH sub-lists use; labels come from
	 * {@link BalloonRoute#displayLabel()} ("Grand Tree (magic logs)") so
	 * the destination and its log type are both searchable.
	 */
	private List<SearchRow> balloonRouteRows()
	{
		String keyName = "balloonLogBasketRoutes";
		ConfigItem item = settings.configItem(keyName);
		String description = item == null ? "" : item.description();

		List<SearchRow> rows = new ArrayList<>();
		// Shared across the route checkboxes: set while a config sync
		// mirrors the stored set into the controls, so the programmatic
		// setSelected calls do not write the same set back.
		boolean[] syncing = new boolean[1];

		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setOpaque(false);
		// The basket set is account state, not routing state — it stays
		// editable while the balloon family itself is switched off.
		headerRow.putClientProperty(ALWAYS_ENABLED, Boolean.TRUE);
		JLabel header = new JLabel("Log Basket Routes");
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setToolTipText(html(description));
		headerRow.add(header, BorderLayout.CENTER);
		rows.add(new SearchRow(headerRow, "Log Basket Routes",
			searchable("Log Basket Routes")));

		for (BalloonRoute route : BalloonRoute.values())
		{
			JCheckBox checkBox = new JCheckBox(html(route.displayLabel()));
			checkBox.setSelected(currentSet(keyName, BalloonRoute.class).contains(route));
			checkBox.setToolTipText(html(description));
			checkBox.addActionListener(e ->
			{
				if (syncing[0])
				{
					return;
				}
				EnumSet<BalloonRoute> updated = EnumSet.noneOf(BalloonRoute.class);
				updated.addAll(currentSet(keyName, BalloonRoute.class));
				if (checkBox.isSelected())
				{
					updated.add(route);
				}
				else
				{
					updated.remove(route);
				}
				settings.write(keyName, updated);
			});
			registerConfigListener(keyName, () ->
			{
				syncing[0] = true;
				try
				{
					checkBox.setSelected(
						currentSet(keyName, BalloonRoute.class).contains(route));
				}
				finally
				{
					syncing[0] = false;
				}
			});

			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.putClientProperty(ALWAYS_ENABLED, Boolean.TRUE);
			row.add(checkBox, BorderLayout.CENTER);
			rows.add(new SearchRow(row, route.displayLabel(),
				searchable("Log Basket Routes", route.displayLabel())));
		}
		return rows;
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
	private <E> Set<E> currentSet(String keyName)
	{
		Set<E> set = new LinkedHashSet<>();
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

	// ---- Navigation ---------------------------------------------------------

	/**
	 * Push a category sub-panel onto the card stack.
	 */
	void drillInto(CategoryPanel category)
	{
		muxer.pushState(category);
	}

	/**
	 * Pop the top sub-panel, returning toward the tile home.
	 */
	void backToHome()
	{
		muxer.popState();
	}

	/**
	 * The rows of {@code category} matching the query — feeds the home
	 * results view so it renders the same match set the sub-panel would
	 * filter down to.
	 */
	List<SearchRow> matchingRows(CategoryPanel category, String query)
	{
		return category.matchingRows(query.toLowerCase(Locale.ROOT));
	}

	List<TransportFamilyCard> transportCards()
	{
		return transportCards;
	}

	/**
	 * Push {@code category} filtered on the current query (its
	 * {@code onActivate} applies it), then scroll the matched row into
	 * view once the push's layout has settled.
	 */
	void deepLink(CategoryPanel category, SearchRow row)
	{
		muxer.pushState(category);
		if (row != null)
		{
			// scrollRectToVisible takes the rect in the component's own
			// coordinate space — getBounds() is in parent space and would
			// double the row's offset, scrolling past the match.
			SwingUtilities.invokeLater(() ->
				row.component.scrollRectToVisible(
					new Rectangle(0, 0, row.component.getWidth(), row.component.getHeight())));
		}
	}

	/**
	 * The sidebar activates only this outer panel — forward activation into
	 * the muxer so the visible sub-panel's {@code onActivate} (the deep-link
	 * filter + status refresh) fires. {@link MultiplexingPluginPanel} only
	 * dispatches child activation while its own active flag is set.
	 */
	@Override
	public void onActivate()
	{
		muxer.onActivate();
	}

	@Override
	public void onDeactivate()
	{
		muxer.onDeactivate();
	}

	// ---- Search -------------------------------------------------------------

	/**
	 * Live filter across the card stack: a non-empty query pushes the home
	 * surface (whose {@code onActivate} renders the grouped results view —
	 * pushing the already-stacked home also pops any drilled sub-panel, so
	 * typing while drilled returns to results); clearing restores the tile
	 * grid and every category's full row set in place.
	 */
	private void onSearchChanged()
	{
		currentQuery = searchBar.getText().trim();
		if (currentQuery.isEmpty())
		{
			home.applyQuery("");
			for (CategoryPanel category : categories)
			{
				category.applyQuery("");
			}
		}
		else
		{
			muxer.pushState(home);
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
