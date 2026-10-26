package shortestpath;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
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
class CollapsibleEnumGroup<E extends Enum<E>> extends ExpandableSurface
{
	private final ShortestPathPanel panel;
	private final String keyName;
	private final Class<E> type;
	private final List<E> members;
	private final List<JCheckBox> boxes = new ArrayList<>();

	private final JPanel header;
	@Getter private final JLabel nameLabel;
	@Getter(AccessLevel.PACKAGE) private final JLabel countLabel;
	// Held while a config sync mirrors the stored set into the checkboxes,
	// so the programmatic setSelected calls do not write it back.
	private final PanelRows.SyncGuard syncing = new PanelRows.SyncGuard();

	CollapsibleEnumGroup(ShortestPathPanel panel, String keyName, Class<E> type,
		String groupLabel, List<E> members, String description)
	{
		super(new EmptyBorder(0, ShortestPathPanel.SUB_LIST_INDENT, 4, 0));
		this.panel = panel;
		this.keyName = keyName;
		this.type = type;
		this.members = members;

		setOpaque(false);

		header = new JPanel(new BorderLayout());

		expander = newExpander();
		expander.setPreferredSize(new Dimension(ShortestPathPanel.SECTION_TOGGLE_WIDTH, 0));
		expander.setBorder(new EmptyBorder(0, 0, 0, 5));
		header.add(expander, BorderLayout.WEST);

		nameLabel = new JLabel(ShortestPathPanel.html(groupLabel));
		nameLabel.setToolTipText(ShortestPathPanel.html(description));
		header.add(nameLabel, BorderLayout.CENTER);

		countLabel = new JLabel();
		countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		countLabel.setFont(FontManager.getRunescapeSmallFont());
		header.add(countLabel, BorderLayout.EAST);
		add(header);

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
				PanelRows.guardedWrite(syncing, () ->
				{
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
				}));
			boxes.add(checkBox);
			detail.add(checkBox);
		}

		panel.registerConfigListener(keyName, this::syncFromConfig);
		updateCount();
		installExpandListener(header, nameLabel, countLabel);
	}

	/**
	 * Whether {@code query} (already lowercased) hits a member label —
	 * the nested expansion hook {@link CategoryPanel#applyQuery} runs so
	 * a member-name query opens a group stacked inside a card's detail
	 * well the same way it opens a free-standing one.
	 */
	boolean matchesMember(String query)
	{
		for (E member : members)
		{
			if (member.toString().toLowerCase(Locale.ROOT).contains(query))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Re-reads the stored set and mirrors it into the member checkboxes +
	 * the header's "n/total" count — one listener keeps both in step, so
	 * the count never carries separate state.
	 */
	void syncFromConfig()
	{
		Set<E> set = panel.currentSet(keyName, type);
		PanelRows.resync(syncing, () ->
		{
			for (int i = 0; i < members.size(); i++)
			{
				boxes.get(i).setSelected(set.contains(members.get(i)));
			}
		}, this::updateCount);
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

}
