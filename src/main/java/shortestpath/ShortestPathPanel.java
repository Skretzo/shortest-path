package shortestpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

/**
 * Sidebar panel hosting the route-affecting plugin options. The keys stay on
 * {@link ShortestPathConfig} as hidden items; this panel is their only rendered
 * surface and reads/writes them through the config proxy and
 * {@link ConfigManager#setConfiguration}, so live recalculation rides the same
 * {@code ConfigChanged} path as a settings-panel edit.
 */
public class ShortestPathPanel extends PluginPanel
{
	private static final int SECTION_TOGGLE_WIDTH = 18;
	private static final int CARD_ICON_SIZE = 16;
	private static final int SPINNER_FIELD_WIDTH = 6;
	private static final String SEARCH_TOOLTIP = "Search transports, unlocks and restrictions";

	private static final ImageIcon SECTION_EXPAND_ICON;
	private static final ImageIcon SECTION_RETRACT_ICON;

	static
	{
		BufferedImage chevron = ImageUtil.loadImageResource(ShortestPathPanel.class, "/expand.png");
		chevron = ImageUtil.luminanceOffset(chevron, -121);
		SECTION_EXPAND_ICON = new ImageIcon(chevron);
		SECTION_RETRACT_ICON = new ImageIcon(ImageUtil.rotateImage(chevron, Math.PI / 2));
	}

	private final ShortestPathConfig config;
	private final ConfigManager configManager;
	// Held for later sections (item icons, owned-items detection) so the
	// constructor signature does not churn as cards are added.
	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;

	private final IconTextField searchBar;
	private final JPanel contentPanel;
	private final JLabel noResultsLabel;
	private final Map<String, Boolean> sectionExpandStates = new HashMap<>();
	private final List<Section> sections = new ArrayList<>();

	// Boats card controls, kept as fields so external config edits can update
	// them in place without rebuilding the panel.
	private JCheckBox useBoatsToggle;
	private JLabel boatsNameLabel;
	private JButton boatsDetailToggle;
	private JPanel boatsDetail;
	private JLabel costBoatsLabel;
	private JSpinner costBoatsSpinner;
	private boolean boatsDetailOpen;

	private boolean suppressConfigSync;

	ShortestPathPanel(ShortestPathConfig config, ConfigManager configManager, Client client,
		ClientThread clientThread, ItemManager itemManager)
	{
		super(false);

		this.config = config;
		this.configManager = configManager;
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

		Section transports = createSection("transports", "Transports", true);
		JPanel boatsCard = createBoatsCard();
		transports.rows.add(new SearchRow(boatsCard, searchable("useBoats", "Boats", "costBoats", "Boat threshold")));
		contentPanel.add(transports.root);

		noResultsLabel = new JLabel();
		noResultsLabel.setFont(FontManager.getRunescapeSmallFont());
		noResultsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		noResultsLabel.setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel northWrap = new JPanel(new BorderLayout());
		northWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		northWrap.add(contentPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(northWrap);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);
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
		return section;
	}

	private void toggleSection(Section section)
	{
		boolean newState = !section.contents.isVisible();
		section.contents.setVisible(newState);
		section.toggle.setIcon(newState ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
		section.toggle.setToolTipText(newState ? "Retract" : "Expand");
		sectionExpandStates.put(section.id, newState);
		SwingUtilities.invokeLater(section.contents::revalidate);
	}

	/**
	 * The Boats family card: name label, master {@code useBoats} checkbox and a
	 * chevron revealing the {@code costBoats} threshold spinner. This is the
	 * card shape the remaining transport families are built from.
	 */
	private JPanel createBoatsCard()
	{
		final boolean enabled = config.useBoats();
		ConfigItem useBoatsItem = configItem("useBoats");
		ConfigItem costBoatsItem = configItem("costBoats");

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(4, 0, 4, 0));

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);

		// Reserved icon slot: a representative family sprite lands here in
		// later cards; keeping the empty column aligns the detail indent.
		JPanel iconSlot = new JPanel();
		iconSlot.setOpaque(false);
		iconSlot.setPreferredSize(new Dimension(CARD_ICON_SIZE, CARD_ICON_SIZE));
		header.add(iconSlot, BorderLayout.WEST);

		boatsNameLabel = new JLabel("Boats");
		boatsNameLabel.setToolTipText(html(useBoatsItem == null ? "" : useBoatsItem.description()));
		boatsNameLabel.setForeground(enabled ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		header.add(boatsNameLabel, BorderLayout.CENTER);

		JPanel headerControls = new JPanel();
		headerControls.setOpaque(false);
		headerControls.setLayout(new BoxLayout(headerControls, BoxLayout.X_AXIS));

		useBoatsToggle = new JCheckBox();
		useBoatsToggle.setSelected(enabled);
		useBoatsToggle.setToolTipText(html(useBoatsItem == null ? "" : useBoatsItem.description()));
		useBoatsToggle.addActionListener(e -> setBoatsEnabled(useBoatsToggle.isSelected()));
		headerControls.add(useBoatsToggle);

		boatsDetailToggle = new JButton(boatsDetailOpen ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
		boatsDetailToggle.setPreferredSize(new Dimension(SECTION_TOGGLE_WIDTH, 0));
		boatsDetailToggle.setBorder(new EmptyBorder(0, 0, 0, 5));
		boatsDetailToggle.setToolTipText(boatsDetailOpen ? "Retract" : "Expand");
		SwingUtil.removeButtonDecorations(boatsDetailToggle);
		boatsDetailToggle.addActionListener(e -> toggleBoatsDetail());
		headerControls.add(boatsDetailToggle);

		header.add(headerControls, BorderLayout.EAST);

		MouseAdapter expandAdapter = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleBoatsDetail();
			}
		};
		header.addMouseListener(expandAdapter);
		iconSlot.addMouseListener(expandAdapter);
		boatsNameLabel.addMouseListener(expandAdapter);

		card.add(header);

		boatsDetail = new JPanel(new BorderLayout());
		boatsDetail.setOpaque(false);
		// Indented under the icon column so the detail lines up with the label.
		boatsDetail.setBorder(new EmptyBorder(0, CARD_ICON_SIZE, 0, 0));
		boatsDetail.setVisible(boatsDetailOpen);

		costBoatsLabel = new JLabel(costBoatsItem == null ? "Boat threshold" : costBoatsItem.name());
		costBoatsLabel.setForeground(enabled ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		costBoatsLabel.setToolTipText(html(costBoatsItem == null ? "" : costBoatsItem.description()));
		costBoatsLabel.setEnabled(enabled);
		boatsDetail.add(costBoatsLabel, BorderLayout.CENTER);

		costBoatsSpinner = new JSpinner(new SpinnerNumberModel(config.costBoats(), 0, 10000, 1));
		((JSpinner.DefaultEditor) costBoatsSpinner.getEditor()).getTextField().setColumns(SPINNER_FIELD_WIDTH);
		costBoatsSpinner.setToolTipText(html(costBoatsItem == null ? "" : costBoatsItem.description()));
		costBoatsSpinner.setEnabled(enabled);
		costBoatsSpinner.addChangeListener(e ->
		{
			if (!suppressConfigSync)
			{
				onConfigWrite("costBoats", costBoatsSpinner.getValue());
			}
		});
		boatsDetail.add(costBoatsSpinner, BorderLayout.EAST);

		card.add(boatsDetail);
		return card;
	}

	private void toggleBoatsDetail()
	{
		boatsDetailOpen = !boatsDetailOpen;
		boatsDetail.setVisible(boatsDetailOpen);
		boatsDetailToggle.setIcon(boatsDetailOpen ? SECTION_RETRACT_ICON : SECTION_EXPAND_ICON);
		boatsDetailToggle.setToolTipText(boatsDetailOpen ? "Retract" : "Expand");
		SwingUtilities.invokeLater(boatsDetail::revalidate);
	}

	private void setBoatsEnabled(boolean enabled)
	{
		onConfigWrite("useBoats", enabled);
		applyBoatsEnabled(enabled);
	}

	private void applyBoatsEnabled(boolean enabled)
	{
		boatsNameLabel.setForeground(enabled ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		costBoatsLabel.setForeground(enabled ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		costBoatsLabel.setEnabled(enabled);
		costBoatsSpinner.setEnabled(enabled);
	}

	/**
	 * All panel-originated config writes funnel through here so the echo guard
	 * covers every control. {@code setConfiguration} posts {@code ConfigChanged}
	 * synchronously; the guard stops the panel's own listener from reacting to
	 * its own write.
	 */
	private void onConfigWrite(String keyName, Object value)
	{
		suppressConfigSync = true;
		try
		{
			configManager.setConfiguration(ShortestPathPlugin.CONFIG_GROUP, keyName, value);
		}
		finally
		{
			suppressConfigSync = false;
		}
	}

	/**
	 * Handles config edits made elsewhere (the settings panel) while this panel
	 * is open. Updates only the affected control in place so scroll and expand
	 * state survive the sync.
	 */
	void onExternalConfigChanged(ConfigChanged event)
	{
		if (suppressConfigSync || !ShortestPathPlugin.CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}
		SwingUtilities.invokeLater(() -> syncControl(event.getKey()));
	}

	private void syncControl(String keyName)
	{
		suppressConfigSync = true;
		try
		{
			if ("useBoats".equals(keyName))
			{
				boolean enabled = config.useBoats();
				useBoatsToggle.setSelected(enabled);
				applyBoatsEnabled(enabled);
			}
			else if ("costBoats".equals(keyName))
			{
				costBoatsSpinner.setValue(config.costBoats());
			}
		}
		finally
		{
			suppressConfigSync = false;
		}
	}

	/**
	 * Live case-insensitive substring filter across the registered rows. Rows
	 * that match are re-added to their (force-expanded) section; sections with
	 * no match drop out of the layout entirely. DynamicGridLayout does not
	 * reclaim cells of invisible children, so filtering rebuilds the container
	 * contents instead of toggling visibility.
	 */
	private void onSearchChanged()
	{
		String query = searchBar.getText().trim().toLowerCase(Locale.ROOT);
		boolean anyVisible = false;

		contentPanel.removeAll();
		for (Section section : sections)
		{
			section.contents.removeAll();
			boolean anyMatch = false;
			for (SearchRow row : section.rows)
			{
				if (row.text.contains(query))
				{
					section.contents.add(row.component);
					anyMatch = true;
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
			noResultsLabel.setText("<html>No options matching \"" + escapeHtml(query)
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

	private static ConfigItem configItem(String methodName)
	{
		try
		{
			return ShortestPathConfig.class.getMethod(methodName).getAnnotation(ConfigItem.class);
		}
		catch (NoSuchMethodException e)
		{
			return null;
		}
	}

	private static String html(String text)
	{
		return "<html>" + text + "</html>";
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;");
	}

	private static final class Section
	{
		String id;
		boolean defaultOpen;
		JPanel root;
		JButton toggle;
		JPanel contents;
		final List<SearchRow> rows = new ArrayList<>();
	}

	private static final class SearchRow
	{
		final JPanel component;
		final String text;

		SearchRow(JPanel component, String text)
		{
			this.component = component;
			this.text = text;
		}
	}
}
