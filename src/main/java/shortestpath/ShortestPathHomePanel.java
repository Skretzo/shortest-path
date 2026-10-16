package shortestpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import shortestpath.settings.TeleportationItem;

/**
 * The sidebar's home view: a 2x3 grid of category tiles — one per
 * {@link CategoryPanel} in the order General, Transports, Teleport
 * Restrictions, Unlocks, Bank, Player-Owned House — each carrying the
 * category name plus a live status line (e.g. {@code "19/23 on"},
 * {@code "3 blocked"}). Clicking a tile drills into its sub-panel through
 * the owning panel's muxer.
 *
 * <p>The same {@link FixedWidthPanel} scroll wrap the category panels use
 * keeps the home view at viewport width with the same 8px content inset.</p>
 */
class ShortestPathHomePanel extends PluginPanel
{
	private final ShortestPathPanel panel;
	private final FixedWidthPanel content;

	private final List<JPanel> tiles = new ArrayList<>();
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
		JPanel grid = new JPanel(new DynamicGridLayout(3, 2, 4, 4));
		grid.setOpaque(false);
		for (JPanel tile : tiles)
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

	private JPanel makeTile(CategoryPanel category)
	{
		JPanel tile = new JPanel(new BorderLayout());
		tile.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tile.setOpaque(true);
		tile.setBorder(new EmptyBorder(6, 8, 6, 8));

		JLabel nameLabel = new JLabel(category.categoryName());
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		tile.add(nameLabel, BorderLayout.CENTER);

		JLabel statusLabel = new JLabel();
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		tile.add(statusLabel, BorderLayout.SOUTH);
		statusLabels.add(statusLabel);

		MouseAdapter drill = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				panel.drillInto(category);
			}
		};
		tile.addMouseListener(drill);
		nameLabel.addMouseListener(drill);
		statusLabel.addMouseListener(drill);
		return tile;
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
					"unlockDragontoothPassage", "unlockBalloonLogBasket", "unlockMorUlRek"})
				{
					if (panel.settings.configuredBool(key))
					{
						unlocked++;
					}
				}
				return unlocked + "/5 on";
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
}
