package shortestpath;

import java.awt.BorderLayout;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import shortestpath.ShortestPathPanel.SearchRow;

/**
 * One drill-down sub-panel of the sidebar: a back-arrow header over a
 * {@link FixedWidthPanel} scroll view holding the category's rows. The back
 * icon reuses the bundled chevron rotated 180 degrees; the header keeps the
 * old section chrome (orange bold name, bottom divider) but carries a 4px
 * left inset so the arrow and title are not flush against the panel edge.
 *
 * <p>The scroll view is a {@code FixedWidthPanel} {@code BorderLayout}
 * wrapper north-anchoring the content {@code FixedWidthPanel} — mirroring
 * {@code PluginListPanel}, so short content does not stretch and the view
 * can never outgrow the 225px viewport.</p>
 */
class CategoryPanel extends PluginPanel
{
	private static final ImageIcon BACK_ICON;

	static
	{
		BufferedImage chevron = ImageUtil.loadImageResource(ShortestPathPanel.class, "/shortestpath/expand.png");
		chevron = ImageUtil.luminanceOffset(chevron, -121);
		BACK_ICON = new ImageIcon(ImageUtil.rotateImage(chevron, Math.PI));
	}

	private final ShortestPathPanel panel;
	private final String categoryName;
	// Lowercased name + aliases: a query contained here shows the whole
	// category (the names double as search aliases, e.g. "routing" finds
	// the renamed "General" category).
	private final String matchText;
	private final JPanel header;
	private final FixedWidthPanel content;
	private final List<SearchRow> rows = new ArrayList<>();

	CategoryPanel(ShortestPathPanel panel, String name, String alias)
	{
		super(false);
		this.panel = panel;
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
		back.addActionListener(e -> panel.backToHome());
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
			new EmptyBorder(BORDER_OFFSET, 8, BORDER_OFFSET, 8)));

		FixedWidthPanel view = new FixedWidthPanel();
		view.setLayout(new BorderLayout());
		view.add(content, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(view);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane, BorderLayout.CENTER);
	}

	String categoryName()
	{
		return categoryName;
	}

	/**
	 * Extra header slot on the right edge — the Restrictions category parks
	 * its "Owned" checkbox here.
	 */
	void setTrailing(JComponent component)
	{
		header.add(component, BorderLayout.EAST);
		header.revalidate();
	}

	/**
	 * Appends a searchable row to the category's scroll content.
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

	List<SearchRow> rows()
	{
		return rows;
	}

	/**
	 * The rows matching {@code query} (already lowercased): every row when
	 * the query is empty or hits the category's own name/alias, otherwise
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

	/**
	 * Muxer activation is the deep-link seam: a {@code pushState} into this
	 * category lands it pre-filtered on the current search query.
	 */
	@Override
	public void onActivate()
	{
		applyQuery(panel.getCurrentQuery());
	}
}
