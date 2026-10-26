package shortestpath;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import shortestpath.ShortestPathPanel.SearchRow;

/**
 * One drilled section of the sidebar: a back-arrow header pinned over its
 * own scroll of rows. A tile click on the home grid shows it; the arrow
 * returns to the grid. The back icon reuses the bundled chevron rotated
 * 180 degrees; the header keeps the section chrome (orange bold name,
 * bottom divider) and carries a trailing slot on its right edge — the
 * restrictions section parks its "Owned" checkbox there.
 *
 * <p>Each section filters its own rows in place on a search query; the
 * pinned header keeps the back affordance and the trailing slot reachable
 * while the rows scroll.</p>
 */
class CategoryPanel extends JPanel
{
	private static final ImageIcon BACK_ICON;

	static
	{
		BufferedImage chevron = ImageUtil.loadImageResource(ShortestPathPanel.class, "/shortestpath/expand.png");
		chevron = ImageUtil.luminanceOffset(chevron, -121);
		BACK_ICON = new ImageIcon(ImageUtil.rotateImage(chevron, Math.PI));
	}

	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true) private final String categoryName;
	// Lowercased name + aliases: a query contained here shows the whole
	// section (the names double as search aliases, e.g. "routing" finds
	// the renamed "General" section).
	/**
	 * The section's own match text (lowercased name + aliases). Rows
	 * that self-filter — the restriction checklist's items-disabled
	 * hint — match on this text rather than a second hardcoded copy,
	 * so renaming the section or its alias cannot desync them.
	 */
	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true) private final String matchText;
	private final JPanel header;
	private final FixedWidthPanel content;
	@Getter(AccessLevel.PACKAGE) @Accessors(fluent = true)
	private final List<SearchRow> rows = new ArrayList<>();

	CategoryPanel(String name, String alias, Runnable onBack)
	{
		this.categoryName = name;
		this.matchText = ShortestPathPanel.searchable(name, alias);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 4, 3, 1)));

		JButton back = new JButton(BACK_ICON);
		SwingUtil.removeButtonDecorations(back);
		back.setPreferredSize(new Dimension(22, 0));
		back.setBorder(new EmptyBorder(0, 0, 0, 5));
		back.setToolTipText("Back");
		back.addActionListener(e -> onBack.run());
		header.add(back, BorderLayout.WEST);

		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		header.add(nameLabel, BorderLayout.CENTER);

		add(header, BorderLayout.NORTH);

		content = new FixedWidthPanel();
		content.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(PluginPanel.BORDER_OFFSET, 8, PluginPanel.BORDER_OFFSET, 8)));

		FixedWidthPanel view = new FixedWidthPanel();
		view.setLayout(new BorderLayout());
		view.add(content, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(view);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Extra header slot on the right edge — the Restrictions section parks
	 * its "Owned" checkbox here.
	 */
	void setTrailing(JComponent component)
	{
		header.add(component, BorderLayout.EAST);
		header.revalidate();
	}

	/**
	 * Appends a searchable row to the section's content column.
	 */
	void addRow(JComponent component, String displayText, String searchText)
	{
		addRow(new SearchRow(component, displayText, searchText));
	}

	void addRow(SearchRow row)
	{
		rows.add(row);
		content.add(row.component);
	}

	/**
	 * The rows matching {@code query} (already lowercased): every row when
	 * the query is empty or hits the section's own name/alias, otherwise
	 * rows whose search text contains it. The restriction checklist matches
	 * by its own family/member labels rather than its row text.
	 */
	List<SearchRow> matchingRows(String query)
	{
		if (query.isEmpty() || matchText.contains(query))
		{
			return new ArrayList<>(rows);
		}
		List<SearchRow> matches = new ArrayList<>();
		for (SearchRow row : rows)
		{
			if (row.component instanceof RestrictionListPanel)
			{
				if (!((RestrictionListPanel) row.component).matchingLabels(query).isEmpty())
				{
					matches.add(row);
				}
				continue;
			}
			if (row.text.contains(query))
			{
				matches.add(row);
			}
		}
		return matches;
	}

	/**
	 * Rebuilds the visible rows for {@code query}: all rows on an empty
	 * query or a name/alias hit, otherwise only the matching rows — with
	 * the same per-row semantics the old section filter applied (detail-
	 * label hits force the card open; the restriction checklist filters
	 * itself). An empty query also collapses search-forced expansions.
	 */
	void applyQuery(String rawQuery)
	{
		String query = rawQuery.trim().toLowerCase(Locale.ROOT);
		boolean nameMatch = !query.isEmpty() && matchText.contains(query);

		content.removeAll();
		for (SearchRow row : rows)
		{
			if (row.component instanceof RestrictionListPanel)
			{
				RestrictionListPanel list = (RestrictionListPanel) row.component;
				if (list.applyFilter(nameMatch ? "" : query))
				{
					content.add(row.component);
				}
				continue;
			}
			if (query.isEmpty() && row.component instanceof SearchExpandable)
			{
				((SearchExpandable) row.component).clearSearchExpanded();
			}
			// Expandable rows can stack expandable children (enum groups
			// inside a family card): an empty query clears their search
			// state too, and a member-label hit expands the group inside
			// the card the same query opens.
			if (row.component instanceof ExpandableSurface)
			{
				for (Component child : ((ExpandableSurface) row.component).detail.getComponents())
				{
					if (child instanceof CollapsibleEnumGroup)
					{
						CollapsibleEnumGroup<?> group = (CollapsibleEnumGroup<?>) child;
						if (query.isEmpty())
						{
							group.clearSearchExpanded();
						}
						else if (!nameMatch && group.matchesMember(query))
						{
							group.setSearchExpanded();
						}
					}
				}
			}
			if (query.isEmpty() || nameMatch || row.text.contains(query))
			{
				content.add(row.component);
				// A match on a detail label (not the component's own name)
				// opens the detail well so the matched control is visible.
				if (!query.isEmpty() && !nameMatch && row.component instanceof SearchExpandable)
				{
					SearchExpandable expandable = (SearchExpandable) row.component;
					String rowName = expandable.getNameLabel().getText().toLowerCase(Locale.ROOT);
					if (!rowName.contains(query))
					{
						expandable.setSearchExpanded();
					}
				}
			}
		}
		content.revalidate();
		content.repaint();
	}
}
