package shortestpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.BoxLayout;
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
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.DimmableJPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.TitleCaseListCellRenderer;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import shortestpath.settings.Settings;
import shortestpath.transport.PohMountedItem;
import shortestpath.transport.PohNexusPortal;

/**
 * Sidebar panel hosting the route-affecting plugin options. The keys stay on
 * {@link ShortestPathConfig} as hidden items; this panel is their only rendered
 * surface and reads/writes them through the {@link Settings} service — keyed
 * configured reads for control state, {@link Settings#write} for edits and
 * {@link Settings#listen} for same-key sync — so live recalculation rides the
 * same {@code ConfigChanged} path as a settings-panel edit.
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
		"Open your bank once so the plugin can see its contents.";
	static final String RESTRICTIONS_DISABLED_HINT =
		"Teleportation items are disabled. Enable \"Use teleportation items\" "
			+ "in Transports to restrict individual items.";

	static final ImageIcon SECTION_EXPAND_ICON;
	static final ImageIcon SECTION_RETRACT_ICON;

	static
	{
		BufferedImage chevron = ImageUtil.loadImageResource(ShortestPathPanel.class, "/expand.png");
		chevron = ImageUtil.luminanceOffset(chevron, -121);
		SECTION_EXPAND_ICON = new ImageIcon(chevron);
		SECTION_RETRACT_ICON = new ImageIcon(ImageUtil.rotateImage(chevron, Math.PI / 2));
	}

	final Settings settings;
	// Held for the restrictions section (per-item sprites, owned-items
	// detection) so the constructor signature does not churn as cards land.
	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;

	private final IconTextField searchBar;
	private final JPanel contentPanel;
	private final JLabel noResultsLabel;
	private final Map<String, Boolean> sectionExpandStates = new HashMap<>();
	private final List<Section> sections = new ArrayList<>();

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

	private Section restrictionsSection;
	private RestrictionListPanel restrictionListPanel;

	ShortestPathPanel(Settings settings, Client client,
		ClientThread clientThread, ItemManager itemManager)
	{
		super(false);

		this.settings = settings;
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;

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

		contentPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		buildTransportsSection();
		buildRestrictionsSection();
		buildUnlocksSection();
		buildBankSection();
		buildPohSection();
		buildRoutingSection();

		noResultsLabel = new JLabel();
		noResultsLabel.setFont(FontManager.getRunescapeSmallFont());
		noResultsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		noResultsLabel.setHorizontalAlignment(SwingConstants.CENTER);
		noResultsLabel.setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel northWrap = new JPanel(new BorderLayout());
		northWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		northWrap.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(northWrap);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);

		// Rows only live in section.rows until the search filter runs — with
		// no initial pass the panel would render headers above empty bodies.
		applySearchFilter("");
	}

	/**
	 * One collapsible band in the scroll area: a full-width clickable header
	 * (chevron button + section name) above a contents grid, mirroring the
	 * settings panel's section chrome.
	 */
	private Section createSection(String id, String name, boolean defaultOpen)
	{
		final boolean isOpen = sectionExpandStates.getOrDefault(id, defaultOpen);

		Section section = new Section();
		section.id = id;
		section.defaultOpen = defaultOpen;

		section.root = new JPanel();
		section.root.setLayout(new BoxLayout(section.root, BoxLayout.Y_AXIS));
		section.root.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		section.root.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout());
		header.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		// The single-pixel right border compensates for the header extending
		// one pixel when closed — same quirk as the settings panel.
		header.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 3, 1)));
		section.root.add(header);

		section.toggle = new JButton(isOpen ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
		section.toggle.setPreferredSize(new Dimension(SECTION_TOGGLE_WIDTH, 0));
		section.toggle.setBorder(new EmptyBorder(0, 0, 0, 5));
		section.toggle.setToolTipText(isOpen ? "Retract" : "Expand");
		SwingUtil.removeButtonDecorations(section.toggle);
		header.add(section.toggle, BorderLayout.WEST);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		header.add(nameLabel, BorderLayout.CENTER);
		section.header = header;

		section.contents = new JPanel(new DynamicGridLayout(0, 1, 0, 5));
		section.contents.setMinimumSize(new Dimension(PANEL_WIDTH, 0));
		section.contents.setBackground(ColorScheme.DARK_GRAY_COLOR);
		section.contents.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 0, BORDER_OFFSET, 0)));
		section.contents.setVisible(isOpen);
		section.root.add(section.contents);

		MouseAdapter adapter = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleSection(section);
			}
		};
		section.toggle.addActionListener(e -> toggleSection(section));
		nameLabel.addMouseListener(adapter);
		header.addMouseListener(adapter);

		sections.add(section);
		contentPanel.add(section.root);
		return section;
	}

	private void toggleSection(Section section)
	{
		setSectionExpanded(section, !section.contents.isVisible());
	}

	private void setSectionExpanded(Section section, boolean expanded)
	{
		section.contents.setVisible(expanded);
		section.toggle.setIcon(expanded ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
		section.toggle.setToolTipText(expanded ? "Retract" : "Expand");
		sectionExpandStates.put(section.id, expanded);
		SwingUtilities.invokeLater(section.contents::revalidate);
	}

	// ---- Section builders -------------------------------------------------

	private void buildTransportsSection()
	{
		Section transports = createSection("transports", "Transports", true);

		addFamilyCard(transports, "useAgilityShortcuts", ItemID.AGILITY_JUMP,
			familyThresholdRows("useAgilityShortcuts"));
		addFamilyCard(transports, "useGrappleShortcuts", ItemID.XBOWS_GRAPPLE_HOOK,
			familyThresholdRows("useGrappleShortcuts"));
		addFamilyCard(transports, "useBoats", null,
			familyThresholdRows("useBoats"));
		addFamilyCard(transports, "useCanoes", ItemID.CANOEING_PADDLE,
			familyThresholdRows("useCanoes"));
		addFamilyCard(transports, "useCharterShips", null,
			familyThresholdRows("useCharterShips"));
		addFamilyCard(transports, "useShips", null,
			familyThresholdRows("useShips"));
		addFamilyCard(transports, "useFairyRings", ItemID.DRAMEN_STAFF,
			familyThresholdRows("useFairyRings"));
		addFamilyCard(transports, "useGnomeGliders", null,
			familyThresholdRows("useGnomeGliders"));
		addFamilyCard(transports, "useHotAirBalloons", null,
			familyThresholdRows("useHotAirBalloons"));
		addFamilyCard(transports, "useMagicCarpets", ItemID.MAGIC_CARPET,
			familyThresholdRows("useMagicCarpets"));
		addFamilyCard(transports, "useMagicMushtrees", null,
			familyThresholdRows("useMagicMushtrees"));
		addFamilyCard(transports, "useMinecarts", ItemID.DWARF_MINECART_TICKET_KELDA_ICE,
			familyThresholdRows("useMinecarts"));

		addFamilyCard(transports, "useQuetzals", ItemID.HG_QUETZALWHISTLE_BASIC,
			spinnerRow("costQuetzals"), spinnerRow("costQuetzalWhistle"));
		addFamilyCard(transports, "useSpiritTrees", ItemID.PLANTPOT_SPIRIT_TREE_SAPLING,
			familyThresholdRows("useSpiritTrees"));

		addFamilyCard(transports, "useTeleportationItems", ItemID.RING_OF_DUELING_8,
			spinnerRow("costConsumableTeleportationItems"),
			spinnerRow("costNonConsumableTeleportationItems"),
			spinnerRow("costTeleportationBoxes"),
			restrictionsPointer());

		addFamilyCard(transports, "useTeleportationLevers", null,
			familyThresholdRows("useTeleportationLevers"));
		addFamilyCard(transports, "useTeleportationPortals", null,
			familyThresholdRows("useTeleportationPortals"));
		addFamilyCard(transports, "useTeleportationSpells", ItemID.LAWRUNE,
			familyThresholdRows("useTeleportationSpells"));
		addFamilyCard(transports, "useTeleportationSpellsHome", ItemID.POH_TABLET_TELEPORTTOHOUSE,
			familyThresholdRows("useTeleportationSpellsHome"));
		addFamilyCard(transports, "useTeleportationMinigames", ItemID.MINIGAME_TELEPORT,
			familyThresholdRows("useTeleportationMinigames"));
		addFamilyCard(transports, "useWildernessObelisks", null,
			familyThresholdRows("useWildernessObelisks"));
		addFamilyCard(transports, "useSeasonalTransports", ItemID.LEAGUE_TWISTED_HOME_TELEPORT,
			familyThresholdRows("useSeasonalTransports"));
	}

	private void buildRestrictionsSection()
	{
		restrictionsSection = createSection("restrictions", "Teleport Restrictions", false);
		restrictionListPanel = new RestrictionListPanel(this, settings, client, clientThread);
		restrictionsSection.rows.add(new SearchRow(restrictionListPanel, searchable("teleport restrictions")));

		JCheckBox owned = new JCheckBox("Owned");
		owned.setOpaque(false);
		owned.setForeground(Color.WHITE);
		owned.setToolTipText(html(OWNED_FILTER_TOOLTIP));
		owned.addActionListener(e ->
		{
			owned.setForeground(owned.isSelected() ? ColorScheme.BRAND_ORANGE : Color.WHITE);
			restrictionListPanel.setOwnedOnly(owned.isSelected());
		});
		restrictionsSection.header.add(owned, BorderLayout.EAST);
	}

	private void buildUnlocksSection()
	{
		Section unlocks = createSection("unlocks", "Unlocks", true);
		unlocks.rows.add(checkRow("unlockCanoeAxe"));
		unlocks.rows.add(checkRow("unlockXericsHonour"));
		unlocks.rows.add(checkRow("unlockDragontoothPassage"));
		unlocks.rows.add(checkRow("unlockBalloonLogBasket"));
	}

	private void buildBankSection()
	{
		Section bank = createSection("bank", "Bank", true);
		bank.rows.add(checkRow("includeBankPath"));
		bank.rows.add(spinnerRow("costBankVisit"));
	}

	private void buildPohSection()
	{
		Section poh = createSection("poh", "Player-Owned House", false);

		SearchRow master = checkRow("usePoh", () -> applyPohEnabled(settings.configuredBool("usePoh")));
		poh.rows.add(master);

		pohSubRows.add(checkRow("usePohFairyRing"));
		pohSubRows.add(checkRow("usePohSpiritTree"));
		pohSubRows.add(checkRow("usePohObelisk"));
		pohSubRows.add(comboRow("pohJewelleryBoxTier"));
		pohSubRows.addAll(setSubListRows("pohNexusPortals", PohNexusPortal.class));
		pohSubRows.addAll(setSubListRows("pohMountedItems", PohMountedItem.class));
		poh.rows.addAll(pohSubRows);

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

	private void buildRoutingSection()
	{
		Section routing = createSection("routing", "Routing", true);
		routing.rows.add(checkRow("avoidWilderness"));
		routing.rows.add(spinnerRow("currencyThreshold"));
		routing.rows.add(checkRow("respawnPrifddinas"));
		routing.rows.add(spinnerRow("unreachableTargetDistanceThreshold"));
	}

	// ---- Card + row builders ------------------------------------------------

	/**
	 * Creates a transport-family card bound to {@code useKey} and appends it to
	 * the section as a searchable row. The card title is the config item name
	 * minus its leading "Use " (the checkbox carries the on/off meaning).
	 */
	private TransportFamilyCard addFamilyCard(Section section, String useKey, Integer iconItemId,
		SearchRow... detailRows)
	{
		ConfigItem item = settings.configItem(useKey);
		String name = item == null ? useKey : item.name();
		String title = name.startsWith("Use ") ? name.substring("Use ".length()) : name;
		String description = item == null ? "" : item.description();

		TransportFamilyCard card = new TransportFamilyCard(this, useKey, title, description, iconItemId);

		List<String> searchParts = new ArrayList<>();
		searchParts.add(name);
		for (SearchRow row : detailRows)
		{
			card.addDetailRow(row.component);
			searchParts.add(row.text);
		}
		section.rows.add(new SearchRow(card, searchable(searchParts.toArray(new String[0]))));
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
		return settings.configItem(costKey) != null ? new SearchRow[]{spinnerRow(costKey)} : new SearchRow[0];
	}

	private SearchRow restrictionsPointer()
	{
		JLabel pointer = new JLabel("Manage individual items below");
		pointer.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		pointer.setFont(FontManager.getRunescapeSmallFont());
		pointer.setBorder(new EmptyBorder(2, 0, 2, 0));
		pointer.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setSectionExpanded(restrictionsSection, true);
				SwingUtilities.invokeLater(() -> restrictionsSection.root.scrollRectToVisible(
					restrictionsSection.root.getBounds()));
			}
		});
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(pointer, BorderLayout.CENTER);
		return new SearchRow(row, searchable("Manage individual items below"));
	}

	private SearchRow checkRow(String keyName)
	{
		return checkRow(keyName, null);
	}

	private SearchRow checkRow(String keyName, Runnable onChange)
	{
		ConfigItem item = settings.configItem(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		JCheckBox checkBox = new JCheckBox();
		checkBox.setSelected(settings.configuredBool(keyName));
		checkBox.setToolTipText(html(description));
		checkBox.addActionListener(e ->
		{
			settings.write(keyName, checkBox.isSelected());
			if (onChange != null)
			{
				onChange.run();
			}
		});
		registerConfigListener(keyName, () ->
		{
			checkBox.setSelected(settings.configuredBool(keyName));
			if (onChange != null)
			{
				onChange.run();
			}
		});

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JLabel label = new JLabel(name);
		label.setForeground(Color.WHITE);
		label.setToolTipText(html(description));
		row.add(label, BorderLayout.CENTER);
		row.add(checkBox, BorderLayout.EAST);
		return new SearchRow(row, searchable(name));
	}

	private SearchRow spinnerRow(String keyName)
	{
		ConfigItem item = settings.configItem(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		Object value = settings.configuredValue(keyName);
		int current = value instanceof Number ? ((Number) value).intValue() : 0;
		Range range = settings.rangeOf(keyName);
		int min = range == null ? 0 : Math.max(0, range.min());
		Integer max = range == null || range.max() == Integer.MAX_VALUE ? null : range.max();

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
					spinner.setValue(((Number) updated).intValue());
				}
				finally
				{
					syncing[0] = false;
				}
			}
		});

		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		JLabel label = new JLabel(name);
		label.setForeground(Color.WHITE);
		label.setToolTipText(html(description));
		row.add(label, BorderLayout.CENTER);
		row.add(spinner, BorderLayout.EAST);
		return new SearchRow(row, searchable(name));
	}

	private SearchRow comboRow(String keyName)
	{
		ConfigItem item = settings.configItem(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		Object value = settings.configuredValue(keyName);
		@SuppressWarnings("unchecked")
		Class<? extends Enum> type = (Class<? extends Enum>) ((Enum<?>) value).getClass();
		JComboBox<Enum<?>> combo = new JComboBox<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
		combo.setRenderer(new TitleCaseListCellRenderer());
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
		JLabel label = new JLabel(name);
		label.setForeground(Color.WHITE);
		label.setToolTipText(html(description));
		row.add(label, BorderLayout.CENTER);
		row.add(combo, BorderLayout.EAST);
		return new SearchRow(row, searchable(name));
	}

	/**
	 * A {@code Set<Enum>} config rendered as a group label followed by one
	 * indented checkbox row per enum constant; toggling a box writes the
	 * mutated set back through the shared config path.
	 */
	private <E extends Enum<E>> List<SearchRow> setSubListRows(String keyName, Class<E> type)
	{
		ConfigItem item = settings.configItem(keyName);
		String name = configName(keyName);
		String description = item == null ? "" : item.description();

		List<SearchRow> rows = new ArrayList<>();
		// Shared across every member checkbox of this key: set while a config
		// sync mirrors the stored set into the controls, so the programmatic
		// setSelected calls do not write the same set back.
		boolean[] syncing = new boolean[1];

		JPanel groupRow = new JPanel(new BorderLayout());
		groupRow.setOpaque(false);
		JLabel groupLabel = new JLabel(name);
		groupLabel.setForeground(Color.WHITE);
		groupLabel.setToolTipText(html(description));
		groupRow.add(groupLabel, BorderLayout.CENTER);
		rows.add(new SearchRow(groupRow, searchable(name)));

		for (E constant : type.getEnumConstants())
		{
			JCheckBox checkBox = new JCheckBox(constant.toString());
			checkBox.setSelected(currentSet(keyName, type).contains(constant));
			checkBox.setToolTipText(html(description));
			checkBox.addActionListener(e ->
			{
				if (syncing[0])
				{
					return;
				}
				EnumSet<E> updated = EnumSet.noneOf(type);
				updated.addAll(currentSet(keyName, type));
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
					checkBox.setSelected(currentSet(keyName, type).contains(constant));
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
			rows.add(new SearchRow(row, searchable(name, constant.toString())));
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
		ConfigItem item = settings.configItem(keyName);
		return item == null ? keyName : item.name();
	}

	@SuppressWarnings("unchecked")
	private <E extends Enum<E>> Set<E> currentSet(String keyName, Class<E> type)
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
		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() -> SwingUtilities.invokeLater(() ->
			label.setIcon(new ImageIcon(ImageUtil.resizeImage(image, CARD_ICON_SIZE, CARD_ICON_SIZE)))));
	}

	// ---- Search -------------------------------------------------------------

	/**
	 * Live case-insensitive substring filter across every registered row.
	 * Matching rows are re-added to their (visually force-expanded) section;
	 * sections with no match drop out of the layout entirely. DynamicGridLayout
	 * does not reclaim cells of invisible children, so filtering rebuilds the
	 * container contents instead of toggling row visibility. Clearing the query
	 * restores the expand states the user recorded — the map is never touched
	 * by the filter itself.
	 */
	private void onSearchChanged()
	{
		applySearchFilter(searchBar.getText());
	}

	private void applySearchFilter(String rawQuery)
	{
		String query = rawQuery.trim().toLowerCase(Locale.ROOT);
		boolean anyVisible = false;

		contentPanel.removeAll();
		for (Section section : sections)
		{
			section.contents.removeAll();
			boolean anyMatch = false;
			for (SearchRow row : section.rows)
			{
				// The restriction checklist filters itself: family-name hits
				// stay collapsed while member-label hits expand their row, and
				// the owned-filter empty state renders inside the section
				// instead of dropping it.
				if (row.component instanceof RestrictionListPanel)
				{
					if (((RestrictionListPanel) row.component).applyFilter(query))
					{
						section.contents.add(row.component);
						anyMatch = true;
					}
					continue;
				}
				// Cards search-expanded by an earlier query return to their
				// recorded state once the search box empties.
				if (query.isEmpty() && row.component instanceof TransportFamilyCard)
				{
					((TransportFamilyCard) row.component).clearSearchExpanded();
				}
				if (row.text.contains(query))
				{
					section.contents.add(row.component);
					anyMatch = true;
					// A match on a detail label (e.g. a cost threshold name)
					// opens the card detail so the matched control is visible.
					if (row.component instanceof TransportFamilyCard && !query.isEmpty())
					{
						TransportFamilyCard card = (TransportFamilyCard) row.component;
						String cardName = card.getNameLabel().getText().toLowerCase(Locale.ROOT);
						if (!cardName.contains(query))
						{
							card.setSearchExpanded();
						}
					}
				}
			}
			if (!anyMatch)
			{
				continue;
			}
			if (query.isEmpty())
			{
				boolean open = sectionExpandStates.getOrDefault(section.id, section.defaultOpen);
				section.contents.setVisible(open);
				section.toggle.setIcon(open ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
				section.toggle.setToolTipText(open ? "Retract" : "Expand");
			}
			else
			{
				// Force-expand matched sections for the duration of the query
				// without touching the stored expand state.
				section.contents.setVisible(true);
				section.toggle.setIcon(SECTION_RETRACT_ICON);
				section.toggle.setToolTipText("Retract");
			}
			contentPanel.add(section.root);
			anyVisible = true;
		}

		if (!query.isEmpty() && !anyVisible)
		{
			noResultsLabel.setText("<html>No options matching \"" + escapeHtml(rawQuery.trim())
				+ "\". Clear the search to see everything.</html>");
			contentPanel.add(noResultsLabel);
		}

		contentPanel.revalidate();
		contentPanel.repaint();
	}

	private static String searchable(String... parts)
	{
		return String.join(" ", parts).toLowerCase(Locale.ROOT);
	}

	static String html(String text)
	{
		return "<html>" + text + "</html>";
	}

	private static String escapeHtml(String text)
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

	private static void applyEnabledDeep(Component component, boolean enabled)
	{
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

	private static final class Section
	{
		String id;
		boolean defaultOpen;
		JPanel root;
		JPanel header;
		JButton toggle;
		JPanel contents;
		final List<SearchRow> rows = new ArrayList<>();
	}

	private static final class SearchRow
	{
		final JComponent component;
		final String text;

		SearchRow(JComponent component, String text)
		{
			this.component = component;
			this.text = text;
		}
	}
}
