package shortestpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.SoftBevelBorder;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import shortestpath.settings.TeleportationItem;

/**
 * The sidebar's home view: a two-column grid of category tiles — one per
 * {@link CategoryPanel} in the order General, Transports, Teleport
 * Restrictions, Unlocks, Bank, Player-Owned House, Display — each a
 * square raised button carrying a centered item icon, the bold category
 * name and a live status line (e.g. {@code "19/22 on"}, {@code "3 blocked"}).
 * Clicking a tile drills into its sub-panel through the owning panel's
 * muxer.
 *
 * <p>The same {@link FixedWidthPanel} scroll wrap the category panels use
 * keeps the home view at viewport width with the same 8px content inset.</p>
 */
class ShortestPathHomePanel extends PluginPanel
{
	private static final int GRID_HGAP = 4;
	private static final int GRID_VGAP = 4;
	/**
	 * One tile edge: the content column (viewport width minus the 8px side
	 * insets) minus the grid's horizontal gap, split across the two
	 * columns — reporting it as both width and height yields square cells
	 * once the uniform grid resolves.
	 */
	private static final int TILE_EDGE = (PluginPanel.PANEL_WIDTH - 2 * 8 - GRID_HGAP) / 2;
	private static final int TILE_ICON_SIZE = 32;
	/**
	 * The unlock keys the Unlocks tile counts — one list shared by the
	 * refresh-listener registration and the status computation so adding
	 * an unlock row can never leave the tile reading "n/4".
	 */
	private static final String[] UNLOCK_KEYS = {
		"unlockCanoeAxe", "unlockXericsHonour",
		"unlockDragontoothPassage", "unlockMorUlRek"};
	/**
	 * The display-side toggles the Display tile counts — shared by the
	 * category builder, the refresh-listener registration and the status
	 * computation so the tile can never read "n/3" over the wrong row
	 * count. Booleans only: the tile-counter combo and spinner render in
	 * the same category but carry no on/off state to count.
	 */
	static final String[] DISPLAY_KEYS = {
		"showTransportInfo", "highlightSpellbookSpells",
		"highlightInventoryItems"};

	private static final int FOOTER_BUTTON_EDGE = 22;
	private static final int FOOTER_ICON_SIZE = 18;
	private static final String GITHUB_URL = "https://github.com/Skretzo/shortest-path";
	private static final String DISCORD_URL = "https://discord.gg/uX47xg8u3M";

	private static final ImageIcon GITHUB_ICON = footerIcon("github_icon.png");
	private static final ImageIcon DISCORD_ICON = footerIcon("discord_icon.png");
	private static final ImageIcon CONFIG_ICON = footerIcon("settings_icon.png");

	private final ShortestPathPanel panel;
	private final FixedWidthPanel content;
	/**
	 * Bound to the plugin solely so the gear button's posted
	 * overlay-config click carries a resolvable plugin reference — the
	 * classic settings surface picks the page to open off
	 * {@code overlay.getPlugin()}. Never registered with the
	 * OverlayManager; it renders nothing and builds no menus.
	 */
	private final Overlay configOverlay;
	private final JComponent linkRow;

	private final List<JComponent> tiles = new ArrayList<>();
	private final List<JLabel> statusLabels = new ArrayList<>();

	ShortestPathHomePanel(ShortestPathPanel panel)
	{
		super(false);
		this.panel = panel;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		content = new FixedWidthPanel();
		content.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(BORDER_OFFSET, 8, BORDER_OFFSET, 8)));

		FixedWidthPanel view = new FixedWidthPanel();
		view.setLayout(new BorderLayout());
		view.add(content, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(view);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);

		for (CategoryPanel category : panel.categories())
		{
			tiles.add(makeTile(category));
		}

		configOverlay = new ConfigHandleOverlay(panel.plugin);
		linkRow = buildLinkRow();

		showTiles();
		refresh();
	}

	/**
	 * Empty query shows the tile grid; a non-empty query shows the grouped
	 * results view.
	 */
	void applyQuery(String query)
	{
		if (query.trim().isEmpty())
		{
			showTiles();
		}
		else
		{
			showResults(query);
		}
	}

	private void showTiles()
	{
		content.removeAll();
		// Two fixed columns, rows derived from the tile count — an odd
		// count just leaves the last cell empty.
		JPanel grid = new JPanel(new DynamicGridLayout(0, 2, GRID_HGAP, GRID_VGAP));
		grid.setOpaque(false);
		for (JComponent tile : tiles)
		{
			grid.add(tile);
		}
		content.add(grid);

		// Footer notes under the grid: the orange accent flags the new A*
		// backend (its option is one click into the General category); the
		// muted note covers the settings the sidebar does not surface.
		JLabel astarNote = new JLabel(ShortestPathPanel.html(
			"<center>New: the faster A* pathfinder - enable it under "
				+ "General, Pathfinder Algorithm.</center>"));
		astarNote.setForeground(ColorScheme.BRAND_ORANGE);
		astarNote.setFont(FontManager.getRunescapeSmallFont());
		content.add(astarNote);

		JLabel settingsNote = new JLabel(ShortestPathPanel.html(
			"<center>More settings live in the regular plugin settings panel: "
				+ "Display (world map, minimap, tiles, path style, teleport "
				+ "pulse), Colours, Hotkeys and Debug options.</center>"));
		settingsNote.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		settingsNote.setFont(FontManager.getRunescapeSmallFont());
		content.add(settingsNote);

		// Below a thin divider, the footer link row: GitHub/Discord open
		// the project links, the gear opens the classic settings page.
		content.add(linkRow);

		content.revalidate();
		content.repaint();
	}

	/**
	 * The grouped results view: one category header per category with ≥1
	 * match, then a clickable result row per match — the restriction
	 * checklist contributes one row per matching family name. Clicking a
	 * result deep-links into the category pre-filtered on the query.
	 */
	private void showResults(String query)
	{
		String q = query.trim().toLowerCase(Locale.ROOT);
		content.removeAll();
		boolean any = false;
		for (CategoryPanel category : panel.categories())
		{
			List<ShortestPathPanel.SearchRow> matches = panel.matchingRows(category, q);
			if (matches.isEmpty())
			{
				continue;
			}
			any = true;

			JLabel header = new JLabel(category.categoryName());
			header.setForeground(ColorScheme.BRAND_ORANGE);
			header.setFont(FontManager.getRunescapeBoldFont());
			header.setBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR));
			content.add(header);

			boolean nameMatch = category.nameMatches(q);
			for (ShortestPathPanel.SearchRow row : matches)
			{
				if (row.component instanceof RestrictionListPanel)
				{
					// The checklist expands through its own label matcher:
					// a category-name hit lists every family — the same
					// normalization the drilled category applies — and
					// the row's own display is the fallback so the header
					// never dangles over zero result rows (items disabled,
					// or the Owned filter emptying the list).
					List<String> labels = panel.restrictionListPanel()
						.matchingLabels(nameMatch ? "" : q);
					if (labels.isEmpty())
					{
						content.add(resultRow(category, row, row.display));
					}
					else
					{
						for (String label : labels)
						{
							content.add(resultRow(category, row, label));
						}
					}
					continue;
				}
				content.add(resultRow(category, row, row.display));
			}
		}

		if (!any)
		{
			JShadowedLabel hint = new JShadowedLabel("<html>No options matching \""
				+ ShortestPathPanel.escapeHtml(query.trim())
				+ "\". Clear the search to see everything.</html>");
			hint.setFont(FontManager.getRunescapeSmallFont());
			hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			hint.setHorizontalAlignment(SwingConstants.CENTER);
			hint.setBorder(new EmptyBorder(8, 8, 8, 8));
			content.add(hint);
		}

		content.revalidate();
		content.repaint();
	}

	/**
	 * The footer link row: a thin divider under the notes, then three
	 * small square chromeless buttons. GitHub and Discord open their
	 * links through the system browser; the gear opens the plugin's
	 * classic settings page by posting the same overlay-config menu
	 * click a real overlay right-click would send.
	 */
	private JComponent buildLinkRow()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		row.setOpaque(false);
		row.setBorder(new CompoundBorder(
			new MatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(6, 0, 0, 0)));
		row.add(linkButton(GITHUB_ICON, "GitHub", GITHUB_URL));
		row.add(linkButton(DISCORD_ICON, "Discord", DISCORD_URL));
		row.add(configButton());
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

	private JButton configButton()
	{
		JButton button = footerButton(CONFIG_ICON, "Plugin Settings");
		button.addActionListener(e -> panel.eventBus.post(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, null, null),
			configOverlay)));
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
			ShortestPathHomePanel.class, "/shortestpath/" + file);
		return new ImageIcon(
			ImageUtil.resizeImage(image, FOOTER_ICON_SIZE, FOOTER_ICON_SIZE));
	}

	/**
	 * A render-nothing {@link Overlay} — {@link Overlay} is abstract, so
	 * the config-open trick needs a concrete subclass to carry the
	 * plugin reference the posted menu click resolves through. It is
	 * never registered with the OverlayManager.
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

	private JComponent resultRow(CategoryPanel category, ShortestPathPanel.SearchRow row, String display)
	{
		JPanel result = new JPanel(new BorderLayout());
		result.setOpaque(false);
		result.setBorder(new EmptyBorder(4, 4, 4, 4));

		JLabel label = new JLabel(display);
		label.setForeground(Color.WHITE);
		result.add(label, BorderLayout.CENTER);

		JLabel chevron = new JLabel(ShortestPathPanel.SECTION_EXPAND_ICON);
		result.add(chevron, BorderLayout.EAST);

		MouseAdapter drill = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				panel.deepLink(category, row);
			}
		};
		result.addMouseListener(drill);
		label.addMouseListener(drill);
		chevron.addMouseListener(drill);
		return result;
	}

	/**
	 * The item sprite a category's tile carries — every tile icon routes
	 * through {@link ShortestPathPanel#loadItemIcon} so the home grid shows
	 * the same item imagery the transport cards do. The Display tile is
	 * the one exception: its glyph is a client sprite, handled in
	 * {@link #makeTile}.
	 */
	private static int iconFor(String categoryName)
	{
		switch (categoryName)
		{
			case "General":
				return ItemID.TRAIL_SEXTANT;
			case "Transports":
				return ItemID.CANOEING_PADDLE;
			case "Teleport Restrictions":
				return ItemID.RING_OF_DUELING_8;
			case "Unlocks":
				return ItemID.CRYSTAL_KEY;
			case "Bank":
				return ItemID.COINS;
			case "Player-Owned House":
				return ItemID.POH_TABLET_TELEPORTTOHOUSE;
			default:
				return ItemID.TRAIL_SEXTANT;
		}
	}

	private JComponent makeTile(CategoryPanel category)
	{
		String name = category.categoryName();
		Tile tile = new Tile(name, () -> panel.drillInto(category));
		if ("Display".equals(name))
		{
			// The Display tile wears the in-game Settings>Display monitor
			// glyph — a cache sprite, not an item icon.
			panel.loadSpriteIcon(SpriteID.OptionsIcons.DISPLAY,
				tile.iconLabel, TILE_ICON_SIZE);
		}
		else
		{
			panel.loadItemIcon(iconFor(name), tile.iconLabel, TILE_ICON_SIZE);
		}
		statusLabels.add(tile.statusLabel);
		return tile;
	}

	/**
	 * Registers the config keys the tile statuses summarize: every key a
	 * status reads gets a {@link #refresh()} listener so tiles track panel
	 * writes and external edits. Called from the owning panel's constructor
	 * after the categories exist — the card and restriction-list sync
	 * listeners registered during those builds must keep running first, or
	 * the statuses would read stale model state.
	 */
	void registerRefreshListeners()
	{
		for (TransportFamilyCard card : panel.transportCards())
		{
			panel.registerConfigListener(card.getKeyName(), this::refresh);
		}
		for (String key : UNLOCK_KEYS)
		{
			panel.registerConfigListener(key, this::refresh);
		}
		for (String key : DISPLAY_KEYS)
		{
			panel.registerConfigListener(key, this::refresh);
		}
		for (String key : new String[]{"balloonLogBasketRoutes",
			"includeBankPath", "usePoh", "pathfinderAlgorithm", "blockedTeleportItems"})
		{
			panel.registerConfigListener(key, this::refresh);
		}
	}

	/**
	 * Recomputes every tile's status line from live config. Bound to the
	 * summarized keys through {@code registerConfigListener} so tile
	 * statuses track both panel writes and external edits.
	 */
	void refresh()
	{
		List<CategoryPanel> categories = panel.categories();
		for (int i = 0; i < categories.size(); i++)
		{
			statusLabels.get(i).setText(statusFor(categories.get(i)));
		}
	}

	private String statusFor(CategoryPanel category)
	{
		switch (category.categoryName())
		{
			case "General":
				return String.valueOf(panel.settings.configuredValue("pathfinderAlgorithm"));
			case "Transports":
				int on = 0;
				for (TransportFamilyCard card : panel.transportCards())
				{
					if (card.isMasterOn())
					{
						on++;
					}
				}
				return on + "/" + panel.transportCards().size() + " on";
			case "Teleport Restrictions":
				if (TeleportationItem.NONE.equals(
					panel.settings.configuredValue("useTeleportationItems")))
				{
					return "Items disabled";
				}
				int restricted = panel.restrictionListPanel().restrictionCount();
				return restricted == 0 ? "All allowed" : restricted + " restricted";
			case "Unlocks":
				int unlocked = 0;
				for (String key : UNLOCK_KEYS)
				{
					if (panel.settings.configuredBool(key))
					{
						unlocked++;
					}
				}
				return unlocked + "/" + UNLOCK_KEYS.length + " on";
			case "Bank":
				return panel.settings.configuredBool("includeBankPath") ? "On" : "Off";
			case "Player-Owned House":
				return panel.settings.configuredBool("usePoh") ? "On" : "Off";
			case "Display":
				int lit = 0;
				for (String key : DISPLAY_KEYS)
				{
					if (panel.settings.configuredBool(key))
					{
						lit++;
					}
				}
				return lit + "/" + DISPLAY_KEYS.length + " on";
			default:
				return "";
		}
	}

	/**
	 * Returning home through the muxer re-renders the right view for the
	 * active query and refreshes the live status lines.
	 */
	@Override
	public void onActivate()
	{
		applyQuery(panel.getCurrentQuery());
		refresh();
	}

	/**
	 * One home-grid tile: a square raised button — centered category icon
	 * on top, the bold category name beneath it and the live status line at
	 * the bottom — painted with a bevelled edge that lowers while the
	 * button is pressed and an orange accent that lifts while armed
	 * (hover). A real {@link JButton}, so Swing's button model supplies the
	 * armed/pressed states and the click action for free; the labels inside
	 * carry no listeners of their own, so a click anywhere on the tile
	 * reaches the button.
	 */
	private static final class Tile extends JButton
	{
		private static final SoftBevelBorder RAISED = new SoftBevelBorder(BevelBorder.RAISED);
		private static final SoftBevelBorder LOWERED = new SoftBevelBorder(BevelBorder.LOWERED);
		/**
		 * The tile's own text inset. The hand-painted bevel is paint-only
		 * while the LAF's button border — though never painted — still
		 * contributes its (generous) insets to the layout and would
		 * silently shrink the label column; an explicit empty border
		 * keeps the allotted label width deterministic.
		 */
		private static final int TILE_TEXT_INSET = 4;

		private final JLabel iconLabel;
		private final JLabel statusLabel;

		Tile(String name, Runnable onClick)
		{
			setLayout(new BorderLayout());
			// The bevel + fill are painted by hand, so the platform button
			// chrome stays off — only the model states are wanted.
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setBorder(new EmptyBorder(TILE_TEXT_INSET, TILE_TEXT_INSET,
				TILE_TEXT_INSET, TILE_TEXT_INSET));
			setToolTipText(name);
			addActionListener(e -> onClick.run());
			// Armed/pressed transitions repaint so the pressed animation
			// shows even though no UI delegate draws a state effect.
			getModel().addChangeListener(e -> repaint());

			iconLabel = new JLabel();
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
			iconLabel.setPreferredSize(new Dimension(0, TILE_ICON_SIZE + 8));
			add(iconLabel, BorderLayout.NORTH);

			// The html wrap keeps long names ("Teleport Restrictions",
			// "Player-Owned House") readable at the ~102px tile edge —
			// they wrap to a second centered line instead of clipping.
			JLabel nameLabel = new JLabel(
				ShortestPathPanel.html("<center>" + name + "</center>"),
				SwingConstants.CENTER);
			nameLabel.setForeground(Color.WHITE);
			// Html only wraps at spaces, so a single token wider than the
			// allotted label width ("Player-Owned") would clip mid-glyph
			// in bold — such names fall back to the small font.
			Font bold = FontManager.getRunescapeBoldFont();
			nameLabel.setFont(
				widestTokenWidth(name, nameLabel.getFontMetrics(bold))
					<= TILE_EDGE - 2 * TILE_TEXT_INSET
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
		 * Square preferred size: the uniform grid then resolves every cell
		 * to the same square bounds at the pinned content width.
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
}
