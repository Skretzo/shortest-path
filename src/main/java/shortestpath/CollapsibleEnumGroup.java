package shortestpath;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.SwingUtil;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * One collapsible bucket inside a {@code Set<Enum>} config sub-list: a
 * header row (chevron + group name + a live "n/total" selected count) over
 * a detail well holding one {@link JCheckBox} per member. Collapsed by
 * default; clicking the header toggles it.
 *
 * <p>Member read/write rides the exact mechanics the flat sub-list used:
 * checked state mirrors {@code currentSet(keyName)}, a toggle writes a
 * mutated {@link EnumSet} copy through {@code settings.write}, and the
 * shared {@link #syncing} guard keeps the config listener's programmatic
 * {@code setSelected} calls from writing the mirrored set back. Grouping
 * is display structure only — the config key, type and persistence are
 * untouched.</p>
 */
class CollapsibleEnumGroup<E extends Enum<E>> extends JPanel implements SearchExpandable
{
	private final ShortestPathPanel panel;
	private final String keyName;
	private final Class<E> type;
	private final List<E> members;
	private final List<JCheckBox> boxes = new ArrayList<>();

	private final JPanel header;
	@Getter private final JLabel nameLabel;
	@Getter(AccessLevel.PACKAGE) private final JLabel countLabel;
	private final JButton expander;
	private final JPanel detail;
	@Getter(AccessLevel.PACKAGE) private boolean detailOpen;
	private boolean searchExpanded;
	// Set while a config sync mirrors the stored set into the checkboxes,
	// so the programmatic setSelected calls do not write it back.
	private boolean syncing;

	CollapsibleEnumGroup(ShortestPathPanel panel, String keyName, Class<E> type,
		String groupLabel, List<E> members, String description)
	{
		this.panel = panel;
		this.keyName = keyName;
		this.type = type;
		this.members = members;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);

		header = new JPanel(new BorderLayout());

		expander = new JButton(ShortestPathPanel.SECTION_EXPAND_ICON);
		expander.setPreferredSize(new Dimension(ShortestPathPanel.SECTION_TOGGLE_WIDTH, 0));
		expander.setBorder(new EmptyBorder(0, 0, 0, 5));
		expander.setToolTipText("Expand");
		SwingUtil.removeButtonDecorations(expander);
		expander.addActionListener(e -> setDetailOpen(!detailOpen));
		header.add(expander, BorderLayout.WEST);

		nameLabel = new JLabel(ShortestPathPanel.html(groupLabel));
		nameLabel.setToolTipText(ShortestPathPanel.html(description));
		header.add(nameLabel, BorderLayout.CENTER);

		countLabel = new JLabel();
		countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		countLabel.setFont(FontManager.getRunescapeSmallFont());
		header.add(countLabel, BorderLayout.EAST);
		add(header);

		detail = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		detail.setBorder(new EmptyBorder(0, ShortestPathPanel.SUB_LIST_INDENT, 4, 0));
		detail.setVisible(false);
		add(detail);

		// The shared expandable chrome: dark header bar over the
		// category-toned, orange-bordered detail well.
		ShortestPathPanel.styleExpandable(header, detail);

		for (E member : members)
		{
			JCheckBox checkBox = new JCheckBox(ShortestPathPanel.html(member.toString()));
			checkBox.setSelected(panel.currentSet(keyName, type).contains(member));
			checkBox.setToolTipText(ShortestPathPanel.html(description));
			checkBox.addActionListener(e ->
			{
				if (syncing)
				{
					return;
				}
				EnumSet<E> updated = EnumSet.noneOf(type);
				updated.addAll(panel.currentSet(keyName, type));
				if (checkBox.isSelected())
				{
					updated.add(member);
				}
				else
				{
					updated.remove(member);
				}
				panel.settings.write(keyName, updated);
			});
			boxes.add(checkBox);
			detail.add(checkBox);
		}

		panel.registerConfigListener(keyName, this::syncFromConfig);
		updateCount();
		installExpandListener();
	}

	void setDetailOpen(boolean open)
	{
		// A manual toggle counts as user intent, so search no longer owns
		// the expansion state of this group.
		searchExpanded = false;
		setDetailOpenInternal(open);
	}

	/**
	 * Search-driven expansion: opens the detail well when the query matched
	 * a member label rather than the group name, so the matched checkbox is
	 * visible. {@link #clearSearchExpanded()} restores the collapsed state
	 * when the query clears — the same contract transport cards follow.
	 */
	@Override
	public void setSearchExpanded()
	{
		if (!detailOpen)
		{
			searchExpanded = true;
			setDetailOpenInternal(true);
		}
	}

	@Override
	public void clearSearchExpanded()
	{
		if (searchExpanded)
		{
			searchExpanded = false;
			setDetailOpenInternal(false);
		}
	}

	private void setDetailOpenInternal(boolean open)
	{
		detailOpen = open;
		expander.setIcon(open ? ShortestPathPanel.SECTION_RETRACT_ICON : ShortestPathPanel.SECTION_EXPAND_ICON);
		expander.setToolTipText(open ? "Retract" : "Expand");
		detail.setVisible(open);
		revalidate();
		repaint();
	}

	/**
	 * Re-reads the stored set and mirrors it into the member checkboxes +
	 * the header's "n/total" count — one listener keeps both in step, so
	 * the count never carries separate state.
	 */
	void syncFromConfig()
	{
		Set<E> set = panel.currentSet(keyName, type);
		syncing = true;
		try
		{
			for (int i = 0; i < members.size(); i++)
			{
				boxes.get(i).setSelected(set.contains(members.get(i)));
			}
		}
		finally
		{
			syncing = false;
		}
		updateCount();
	}

	private void updateCount()
	{
		Set<E> set = panel.currentSet(keyName, type);
		int selected = 0;
		for (E member : members)
		{
			if (set.contains(member))
			{
				selected++;
			}
		}
		countLabel.setText(selected + "/" + members.size());
	}

	private void installExpandListener()
	{
		MouseAdapter adapter = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setDetailOpen(!detailOpen);
			}
		};
		header.addMouseListener(adapter);
		nameLabel.addMouseListener(adapter);
		countLabel.addMouseListener(adapter);
	}
}
