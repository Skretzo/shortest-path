package shortestpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import net.runelite.api.gameval.ItemID;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import shortestpath.settings.TeleportationItem;

/**
 * The sidebar's home view: a 2x3 grid of category tiles — one per
 * {@link CategoryPanel} in the order General, Transports, Teleport
 * Restrictions, Unlocks, Bank, Player-Owned House — each a square raised
 * button carrying a centered item icon, the bold category name and a live
 * status line (e.g. {@code "19/23 on"}, {@code "3 blocked"}). Clicking a
 * tile drills into its sub-panel through the owning panel's muxer.
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

	private final ShortestPathPanel panel;
	private final FixedWidthPanel content;

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
		JPanel grid = new JPanel(new DynamicGridLayout(3, 2, GRID_HGAP, GRID_VGAP));
		grid.setOpaque(false);
		for (JComponent tile : tiles)
		{
			grid.add(tile);
		}
		content.add(grid);
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

			for (ShortestPathPanel.SearchRow row : matches)
			{
				if (row.component instanceof RestrictionListPanel)
				{
					for (String label : panel.restrictionListPanel().matchingLabels(q))
					{
						content.add(resultRow(category, row, label));
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
	 * the same item imagery the transport cards do.
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
		Tile tile = new Tile(category.categoryName(),
			() -> panel.drillInto(category));
		panel.loadItemIcon(iconFor(category.categoryName()), tile.iconLabel, TILE_ICON_SIZE);
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
		for (String key : new String[]{"unlockCanoeAxe", "unlockXericsHonour",
			"unlockDragontoothPassage", "unlockMorUlRek", "balloonLogBasketRoutes",
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
				int blocked = panel.restrictionListPanel().blockedItemCount();
				return blocked == 0 ? "All allowed" : blocked + " blocked";
			case "Unlocks":
				int unlocked = 0;
				for (String key : new String[]{"unlockCanoeAxe", "unlockXericsHonour",
					"unlockDragontoothPassage", "unlockMorUlRek"})
				{
					if (panel.settings.configuredBool(key))
					{
						unlocked++;
					}
				}
				return unlocked + "/4 on";
			case "Bank":
				return panel.settings.configuredBool("includeBankPath") ? "On" : "Off";
			case "Player-Owned House":
				return panel.settings.configuredBool("usePoh") ? "On" : "Off";
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
			setToolTipText(name);
			addActionListener(e -> onClick.run());
			// Armed/pressed transitions repaint so the pressed animation
			// shows even though no UI delegate draws a state effect.
			getModel().addChangeListener(e -> repaint());

			iconLabel = new JLabel();
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
			iconLabel.setPreferredSize(new Dimension(0, TILE_ICON_SIZE + 8));
			add(iconLabel, BorderLayout.NORTH);

			JLabel nameLabel = new JLabel(name, SwingConstants.CENTER);
			nameLabel.setForeground(Color.WHITE);
			nameLabel.setFont(FontManager.getRunescapeBoldFont());
			add(nameLabel, BorderLayout.CENTER);

			statusLabel = new JLabel("", SwingConstants.CENTER);
			statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			statusLabel.setFont(FontManager.getRunescapeSmallFont());
			add(statusLabel, BorderLayout.SOUTH);
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
