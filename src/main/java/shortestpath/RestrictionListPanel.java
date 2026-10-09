package shortestpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.util.SwingUtil;
import shortestpath.items.OwnedItems;
import shortestpath.requirement.TeleportRestriction;
import shortestpath.settings.Settings;
import shortestpath.settings.TeleportationItem;

/**
 * The Teleport Restrictions checklist: one row per {@link TeleportRestriction.Family}
 * with an icon, name, allow-checkbox and a chevron expander revealing the
 * per-family "min. tiles saved" override. Checked means routable; unchecking
 * writes every member id to the {@code blockedTeleportItems} CSV, checking
 * removes them, and a spinner value &gt; 0 writes {@code id:N} threshold
 * overrides for all member ids.
 *
 * <p>
 * The panel filters itself in {@link #applyFilter(String)} — the owning
 * {@link ShortestPathPanel} treats it as one searchable row and delegates the
 * per-family matching here so a member-label hit can force its row open.
 * Container reads for the Owned filter run on the client thread via
 * {@link ClientThread#invoke}; only the Swing update hops back to the EDT.
 * </p>
 */
class RestrictionListPanel extends JPanel
{
	private static final int ICON_LABEL_GAP = 4;
	private static final int DETAIL_INDENT = ShortestPathPanel.CARD_ICON_SIZE + ICON_LABEL_GAP;
	private static final int SPINNER_FIELD_WIDTH = 6;
	private static final int EXPANDER_WIDTH = 18;

	private final ShortestPathPanel panel;
	private final Settings settings;
	private final Client client;
	private final ClientThread clientThread;

	private final List<RestrictionRow> rows = new ArrayList<>();
	private final Set<Integer> blocked = new HashSet<>();
	private final Map<Integer, Integer> thresholds = new HashMap<>();
	private volatile Set<Integer> ownedIds = Collections.emptySet();

	private final JComponent disabledHint;
	private final JComponent ownedEmpty;
	private final JPanel footer;

	private boolean itemsDisabled;
	private boolean ownedOnly;
	private String lastQuery = "";
	// Set while a config sync mirrors the blocked/threshold model into the
	// controls, so the programmatic selection changes do not write back.
	private boolean syncing;

	RestrictionListPanel(ShortestPathPanel panel, Settings settings, Client client,
		ClientThread clientThread)
	{
		this.panel = panel;
		this.settings = settings;
		this.client = client;
		this.clientThread = clientThread;

		setLayout(new DynamicGridLayout(0, 1, 0, 3));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		TeleportRestriction.parseBlocked(blockedCsv(), blocked, thresholds);
		itemsDisabled = TeleportationItem.NONE.equals(
			settings.configuredValue("useTeleportationItems"));

		for (TeleportRestriction.Family family : TeleportRestriction.loadFamilies())
		{
			rows.add(new RestrictionRow(family));
		}

		disabledHint = hintLabel(ShortestPathPanel.RESTRICTIONS_DISABLED_HINT);
		ownedEmpty = hintLabel(ShortestPathPanel.html(
			ShortestPathPanel.OWNED_EMPTY_LINE_1 + "<br>" + ShortestPathPanel.OWNED_EMPTY_LINE_2));

		footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);
		footer.setBorder(new EmptyBorder(6, 0, 2, 0));
		JButton reset = new JButton("Reset restrictions");
		reset.addActionListener(e -> confirmReset());
		footer.add(reset, BorderLayout.CENTER);

		// External edits to either key rebuild the checklist state in place.
		panel.registerConfigListener("blockedTeleportItems", this::syncFromConfig);
		panel.registerConfigListener("useTeleportationItems", this::syncFromConfig);

		refreshOwnedItems();
		applyFilter("");
	}

	/**
	 * Rebuilds the visible row set for {@code query}. Returns false when the
	 * query matches nothing and the section should drop out of the layout —
	 * except for the owned-filter empty state, which renders pinned copy
	 * inside the section rather than collapsing it.
	 */
	boolean applyFilter(String query)
	{
		lastQuery = query;
		removeAll();
		boolean showing = true;

		if (itemsDisabled)
		{
			// The hint still honours the search filter: a non-matching query
			// drops the section like every other.
			showing = query.isEmpty() || "teleport restrictions".contains(query);
			if (showing)
			{
				add(disabledHint);
			}
		}
		else
		{
			boolean anyShown = false;
			for (RestrictionRow row : rows)
			{
				if (query.isEmpty())
				{
					row.clearSearchExpanded();
				}
				boolean matches = query.isEmpty() || row.searchText.contains(query);
				boolean ownedOk = !ownedOnly || row.isOwned();
				if (matches && ownedOk)
				{
					add(row);
					anyShown = true;
					// A hit on a member label rather than the family name
					// opens the row detail so the matched item is visible.
					if (!query.isEmpty() && !row.nameLower.contains(query))
					{
						row.setSearchExpanded();
					}
				}
			}

			if (anyShown)
			{
				add(footer);
			}
			else
			{
				showing = false;
				if (query.isEmpty() && ownedOnly)
				{
					add(ownedEmpty);
					showing = true;
				}
			}
		}

		revalidate();
		repaint();
		return showing;
	}

	/**
	 * Display-only filter: with {@code ownedOnly} set, families with no member
	 * id found in inventory, equipment or bank drop out of the list. Routing
	 * semantics are untouched — the config CSV is never written here.
	 */
	void setOwnedOnly(boolean ownedOnly)
	{
		this.ownedOnly = ownedOnly;
		refreshOwnedItems();
		applyFilter(lastQuery);
	}

	/**
	 * Re-reads inventory, equipment and bank contents for the Owned filter.
	 * The container reads run on the client thread; the Swing rebuild hops
	 * back to the EDT. The bank container is only populated once the user has
	 * opened the bank this session.
	 */
	void refreshOwnedItems()
	{
		clientThread.invoke(() ->
		{
			Map<Integer, Integer> owned = new HashMap<>();
			OwnedItems.addContainer(owned, client.getItemContainer(InventoryID.INV));
			OwnedItems.addContainer(owned, client.getItemContainer(InventoryID.WORN));
			OwnedItems.addContainer(owned, client.getItemContainer(InventoryID.BANK));
			SwingUtilities.invokeLater(() ->
			{
				ownedIds = owned.keySet();
				if (ownedOnly)
				{
					applyFilter(lastQuery);
				}
			});
		});
	}

	/**
	 * Re-reads the hidden CSV key + the teleportation-items mode and mirrors
	 * them into every row. Runs under the {@link #syncing} guard, so the
	 * setValue / setSelected calls this triggers never write back to config.
	 */
	private void syncFromConfig()
	{
		blocked.clear();
		thresholds.clear();
		TeleportRestriction.parseBlocked(blockedCsv(), blocked, thresholds);
		itemsDisabled = TeleportationItem.NONE.equals(
			settings.configuredValue("useTeleportationItems"));
		syncing = true;
		try
		{
			for (RestrictionRow row : rows)
			{
				row.syncFromModel();
			}
		}
		finally
		{
			syncing = false;
		}
		applyFilter(lastQuery);
	}

	private String blockedCsv()
	{
		Object csv = settings.configuredValue("blockedTeleportItems");
		return csv instanceof String ? (String) csv : "";
	}

	private void writeCsv()
	{
		settings.write("blockedTeleportItems", TeleportRestriction.toCsv(blocked, thresholds));
	}

	private void confirmReset()
	{
		int result = JOptionPane.showOptionDialog(this,
			"Remove all teleport restrictions? All blocked items will be re-enabled.",
			"Reset restrictions",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE,
			null,
			new String[]{"Yes", "No"},
			"No");
		if (result == JOptionPane.YES_OPTION)
		{
			blocked.clear();
			thresholds.clear();
			writeCsv();
		}
	}

	private static JComponent hintLabel(String text)
	{
		JShadowedLabel label = new JShadowedLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(new EmptyBorder(8, 8, 8, 8));
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(label, BorderLayout.CENTER);
		return wrapper;
	}

	/**
	 * One family row: icon + name + allow checkbox + chevron expander on the
	 * header line, with the threshold spinner in the collapsible detail well.
	 */
	private final class RestrictionRow extends JPanel
	{
		final TeleportRestriction.Family family;
		final String nameLower;
		final String searchText;

		private final JLabel iconLabel;
		private final JLabel nameLabel;
		private final JCheckBox allow;
		private final JButton expander;
		private final JPanel detail;
		private final JSpinner thresholdSpinner;
		private boolean detailOpen;
		private boolean searchExpanded;

		RestrictionRow(TeleportRestriction.Family family)
		{
			this.family = family;
			this.nameLower = family.name().toLowerCase(Locale.ROOT);
			StringBuilder sb = new StringBuilder(nameLower);
			for (String label : family.memberLabels().values())
			{
				sb.append(' ').append(label.toLowerCase(Locale.ROOT));
			}
			this.searchText = sb.toString();

			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
			setBorder(new EmptyBorder(3, 0, 3, 0));

			JPanel header = new JPanel(new BorderLayout(ICON_LABEL_GAP, 0));
			header.setOpaque(false);

			iconLabel = new JLabel();
			iconLabel.setPreferredSize(new Dimension(
				ShortestPathPanel.CARD_ICON_SIZE, ShortestPathPanel.CARD_ICON_SIZE));
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
			panel.loadItemIcon(family.displayItemId(), iconLabel);
			header.add(iconLabel, BorderLayout.WEST);

			nameLabel = new JLabel(family.name());
			String rowTooltip = "Uncheck to never route via " + family.name();
			nameLabel.setToolTipText(ShortestPathPanel.html(rowTooltip));
			header.add(nameLabel, BorderLayout.CENTER);

			JPanel controls = new JPanel();
			controls.setOpaque(false);
			controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));

			expander = new JButton(ShortestPathPanel.SECTION_EXPAND_ICON);
			expander.setPreferredSize(new Dimension(EXPANDER_WIDTH, 0));
			expander.setToolTipText("Expand");
			SwingUtil.removeButtonDecorations(expander);
			expander.addActionListener(e -> setDetailOpen(!detailOpen));
			controls.add(expander);

			allow = new JCheckBox();
			// Selected state is set before the listener is attached so
			// construction never fires a spurious config write.
			allow.setSelected(!isBlocked());
			allow.setToolTipText(ShortestPathPanel.html(rowTooltip));
			allow.addActionListener(e ->
			{
				boolean allowed = allow.isSelected();
				applyAllowedState(allowed);
				if (!syncing)
				{
					if (allowed)
					{
						family.memberItemIds().forEach(blocked::remove);
					}
					else
					{
						blocked.addAll(family.memberItemIds());
						// Blocking supersedes any per-item threshold override.
						family.memberItemIds().forEach(thresholds::remove);
					}
					writeCsv();
				}
			});
			controls.add(allow);
			header.add(controls, BorderLayout.EAST);

			// Clicking anywhere on the header (icon/name area) also toggles the
			// detail well, mirroring the transport cards. Swing never forwards
			// child events to the parent, so each component needs the adapter.
			MouseAdapter expandOnClick = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (expander.isEnabled())
					{
						setDetailOpen(!detailOpen);
					}
				}
			};
			header.addMouseListener(expandOnClick);
			iconLabel.addMouseListener(expandOnClick);
			nameLabel.addMouseListener(expandOnClick);
			add(header);

			detail = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
			detail.setOpaque(false);
			// Indented under the icon column so the detail lines up with the name.
			detail.setBorder(new EmptyBorder(2, DETAIL_INDENT, 4, 0));

			JPanel thresholdRow = new JPanel(new BorderLayout());
			thresholdRow.setOpaque(false);
			JLabel thresholdLabel = new JLabel("Min. tiles saved");
			thresholdLabel.setForeground(Color.WHITE);
			thresholdLabel.setFont(FontManager.getRunescapeSmallFont());
			String thresholdTooltip = "Tiles this teleport must save to be used. 0 uses the family threshold.";
			thresholdLabel.setToolTipText(ShortestPathPanel.html(thresholdTooltip));
			thresholdRow.add(thresholdLabel, BorderLayout.CENTER);

			thresholdSpinner = new JSpinner(new SpinnerNumberModel(
				Integer.valueOf(currentThreshold()), Integer.valueOf(0), null, Integer.valueOf(1)));
			((JSpinner.DefaultEditor) thresholdSpinner.getEditor()).getTextField()
				.setColumns(SPINNER_FIELD_WIDTH);
			thresholdSpinner.setToolTipText(ShortestPathPanel.html(thresholdTooltip));
			thresholdSpinner.addChangeListener(e ->
			{
				if (syncing)
				{
					return;
				}
				int value = ((Number) thresholdSpinner.getValue()).intValue();
				if (value > 0)
				{
					for (int id : family.memberItemIds())
					{
						thresholds.put(id, value);
					}
				}
				else
				{
					family.memberItemIds().forEach(thresholds::remove);
				}
				writeCsv();
			});
			thresholdRow.add(thresholdSpinner, BorderLayout.EAST);
			detail.add(thresholdRow);
			detail.setVisible(false);
			add(detail);

			syncFromModel();
		}

		/**
		 * The row counts as blocked when any member id is in the blocked set —
		 * a partially blocked CSV renders unchecked, and re-checking clears all
		 * member ids back to allowed.
		 */
		boolean isBlocked()
		{
			for (int id : family.memberItemIds())
			{
				if (blocked.contains(id))
				{
					return true;
				}
			}
			return false;
		}

		boolean isOwned()
		{
			for (int id : family.memberItemIds())
			{
				if (ownedIds.contains(id))
				{
					return true;
				}
			}
			return false;
		}

		private int currentThreshold()
		{
			int current = 0;
			for (int id : family.memberItemIds())
			{
				current = Math.max(current, thresholds.getOrDefault(id, 0));
			}
			return current;
		}

		/**
		 * Mirrors the blocked/threshold model into the controls. Safe under
		 * the syncing guard: suppressed listeners skip the config write.
		 */
		void syncFromModel()
		{
			boolean blockedNow = isBlocked();
			allow.setSelected(!blockedNow);
			thresholdSpinner.setValue(currentThreshold());
			applyAllowedState(!blockedNow);
		}

		private void applyAllowedState(boolean allowed)
		{
			nameLabel.setForeground(allowed ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			iconLabel.setEnabled(allowed);
			expander.setEnabled(allowed);
			ShortestPathPanel.applyRowDisabledState(detail, allowed);
		}

		void setDetailOpen(boolean open)
		{
			// A manual toggle counts as user intent, so search no longer owns
			// the expansion state of this row.
			searchExpanded = false;
			setDetailOpenInternal(open);
		}

		void setSearchExpanded()
		{
			if (!detailOpen)
			{
				searchExpanded = true;
				setDetailOpenInternal(true);
			}
		}

		void clearSearchExpanded()
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
			expander.setIcon(open ? ShortestPathPanel.SECTION_RETRACT_ICON
				: ShortestPathPanel.SECTION_EXPAND_ICON);
			expander.setToolTipText(open ? "Retract" : "Expand");
			detail.setVisible(open);
			revalidate();
			repaint();
		}
	}
}
