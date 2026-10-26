package shortestpath;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.components.DimmableJPanel;
import net.runelite.client.util.SwingUtil;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * One transport-family card in the sidebar panel: a 16px icon slot, the family
 * name, a chevron expander (only rendered once detail content exists) and the
 * family's master control on the right edge. Boolean masters render a checkbox;
 * enum-valued masters (teleportation items) render an enum combo. Detail rows
 * live in an indented well that is dimmed and disabled while the master is off.
 */
class TransportFamilyCard extends JPanel implements SearchExpandable
{
	private static final int ICON_LABEL_GAP = 4;
	private static final int DETAIL_INDENT = ShortestPathPanel.CARD_ICON_SIZE + ICON_LABEL_GAP;

	private final ShortestPathPanel panel;
	@Getter(AccessLevel.PACKAGE) private final String keyName;
	// True when the family's on/off state is owned by a control elsewhere
	// (the teleport-items mode combo lives in General): no master control
	// renders, and isMasterOn() reads the configured value directly.
	private final boolean masterless;
	// True when the card drops the collapsible chrome entirely — no
	// expander, no framed well, detail always expanded. A card that is a
	// section's only row (the house card) uses this so it reads like a
	// plain labelled group instead of a lone boxed expander.
	private final boolean flat;

	private final DimmableJPanel header;
	private final JPanel nameArea;
	private final JLabel iconLabel;
	@Getter private final JLabel nameLabel;
	private final JPanel headerControls;
	@Getter(AccessLevel.PACKAGE) private final JComponent masterControl;
	private final JPanel detail;
	private JButton expander;
	@Getter(AccessLevel.PACKAGE) private boolean detailOpen;
	private boolean searchExpanded;
	// Held while a config sync mirrors values into the controls, so the
	// programmatic selection changes do not write back to config.
	private final PanelRows.SyncGuard syncing = new PanelRows.SyncGuard();

	TransportFamilyCard(ShortestPathPanel panel, String keyName, String title, String description, Integer iconItemId)
	{
		this(panel, keyName, title, description, iconItemId, false);
	}

	TransportFamilyCard(ShortestPathPanel panel, String keyName, String title, String description, Integer iconItemId,
		boolean masterless)
	{
		this(panel, keyName, title, description, iconItemId, masterless, false);
	}

	TransportFamilyCard(ShortestPathPanel panel, String keyName, String title, String description, Integer iconItemId,
		boolean masterless, boolean flat)
	{
		this.panel = panel;
		this.keyName = keyName;
		this.masterless = masterless;
		this.flat = flat;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(4, 8, 4, 8));

		header = new DimmableJPanel();
		header.setLayout(new BorderLayout());

		nameArea = new JPanel(new BorderLayout(ICON_LABEL_GAP, 0));
		nameArea.setOpaque(false);

		iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(ShortestPathPanel.CARD_ICON_SIZE, ShortestPathPanel.CARD_ICON_SIZE));
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		if (iconItemId != null)
		{
			panel.loadItemIcon(iconItemId, iconLabel);
		}
		nameArea.add(iconLabel, BorderLayout.WEST);

		nameLabel = new JLabel(ShortestPathPanel.html(title));
		nameLabel.setToolTipText(ShortestPathPanel.html(description));
		nameArea.add(nameLabel, BorderLayout.CENTER);
		header.add(nameArea, BorderLayout.CENTER);

		headerControls = new JPanel();
		headerControls.setOpaque(false);
		headerControls.setLayout(new BoxLayout(headerControls, BoxLayout.X_AXIS));

		masterControl = masterless ? null : createMasterControl(description);
		if (masterControl != null)
		{
			headerControls.add(masterControl);
		}
		header.add(headerControls, BorderLayout.EAST);
		add(header);

		detail = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		// Indented under the icon column so detail rows line up with the name.
		detail.setBorder(new EmptyBorder(4, DETAIL_INDENT, 4, 0));
		detail.setVisible(false);
		add(detail);

		if (flat)
		{
			// No expandable chrome: the card and its rows sit transparent
			// on the section tone, and the detail stays expanded — no
			// expander ever installs, so the toggle paths no-op.
			setOpaque(false);
			header.setOpaque(false);
			detail.setOpaque(false);
			detail.setVisible(true);
		}
		else
		{
			// The shared expandable chrome: dark header bar over the
			// category-toned, orange-bordered detail well.
			ShortestPathPanel.styleExpandable(header, detail);
		}

		applyEnabled(isMasterOn());
		panel.registerConfigListener(keyName, this::syncFromConfig);
	}

	/**
	 * Adds a row to the detail well. The first detail row also installs the
	 * chevron expander and makes the header clickable — cards with no detail
	 * content never grow a dead expander.
	 */
	void addDetailRow(JComponent row)
	{
		detail.add(row);
		ShortestPathPanel.applyRowDisabledState(row, isMasterOn());
		if (expander == null)
		{
			expander = new JButton(ShortestPathPanel.SECTION_EXPAND_ICON);
			expander.setPreferredSize(new Dimension(ShortestPathPanel.SECTION_TOGGLE_WIDTH, 0));
			expander.setBorder(new EmptyBorder(0, 0, 0, 5));
			expander.setToolTipText("Expand");
			SwingUtil.removeButtonDecorations(expander);
			expander.addActionListener(e -> setDetailOpen(!detailOpen));
			headerControls.add(expander, 0);
			installExpandListener();
		}
		detail.revalidate();
	}

	void setDetailOpen(boolean open)
	{
		if (expander == null)
		{
			return;
		}
		// A manual toggle counts as user intent, so search no longer owns the
		// expansion state of this card.
		searchExpanded = false;
		setDetailOpenInternal(open);
	}

	/**
	 * Search-driven expansion: opens the detail well when the query matched a
	 * detail label rather than the card name, so the matched control is
	 * visible. Only fires on cards the user had collapsed, and
	 * {@link #clearSearchExpanded()} restores them when the query clears.
	 */
	@Override
	public void setSearchExpanded()
	{
		if (expander != null && !detailOpen)
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
		if (expander != null)
		{
			expander.setIcon(open ? ShortestPathPanel.SECTION_RETRACT_ICON : ShortestPathPanel.SECTION_EXPAND_ICON);
			expander.setToolTipText(open ? "Retract" : "Expand");
		}
		detail.setVisible(open);
		revalidate();
		repaint();
	}

	boolean isMasterOn()
	{
		if (masterless)
		{
			// No master control to ask — the configured value itself says
			// whether the family routes (enums: anything but NONE).
			Object value = panel.settings.configuredValue(keyName);
			if (value instanceof Enum)
			{
				return !"NONE".equals(((Enum<?>) value).name());
			}
			return Boolean.TRUE.equals(value);
		}
		if (masterControl instanceof JCheckBox)
		{
			return ((JCheckBox) masterControl).isSelected();
		}
		Object selected = ((JComboBox<?>) masterControl).getSelectedItem();
		return !(selected instanceof Enum) || !"NONE".equals(((Enum<?>) selected).name());
	}

	/**
	 * Master-off interaction contract: the card stays visible and clickable,
	 * the name label drops to LIGHT_GRAY and the icon + every detail control is
	 * disabled. The master control itself always stays enabled.
	 */
	void applyEnabled(boolean enabled)
	{
		nameLabel.setForeground(enabled ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		iconLabel.setEnabled(enabled);
		ShortestPathPanel.applyRowDisabledState(detail, enabled);
	}

	/**
	 * Re-reads the configured master value and mirrors it into the master
	 * control + dim state. The {@link #syncing} guard stops the programmatic
	 * selection change from writing back to config.
	 */
	void syncFromConfig()
	{
		Object value = panel.settings.configuredValue(keyName);
		PanelRows.resync(syncing, () ->
		{
			if (masterControl instanceof JCheckBox)
			{
				((JCheckBox) masterControl).setSelected(Boolean.TRUE.equals(value));
			}
			else if (masterControl instanceof JComboBox && value instanceof Enum)
			{
				((JComboBox<?>) masterControl).setSelectedItem(value);
			}
		}, () ->
		{
			// Masterless cards hold no control to mirror into — only the
			// dim state follows the configured value.
			applyEnabled(isMasterOn());
		});
	}

	private JComponent createMasterControl(String description)
	{
		Object value = panel.settings.configuredValue(keyName);
		if (value instanceof Enum)
		{
			@SuppressWarnings("unchecked")
			Class<? extends Enum> type = (Class<? extends Enum>) ((Enum<?>) value).getClass();
			JComboBox<Enum<?>> combo = new JComboBox<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
			combo.setSelectedItem(value);
			PanelComboStyle.apply(combo);
			combo.setToolTipText(ShortestPathPanel.html(description));
			combo.addItemListener(e ->
			{
				if (e.getStateChange() == ItemEvent.SELECTED)
				{
					PanelRows.guardedWrite(syncing,
						() -> panel.settings.write(keyName, combo.getSelectedItem()));
					applyEnabled(isMasterOn());
				}
			});
			return combo;
		}

		JCheckBox checkBox = new JCheckBox();
		checkBox.setSelected(Boolean.TRUE.equals(value));
		checkBox.setToolTipText(ShortestPathPanel.html(description));
		checkBox.addActionListener(e ->
		{
			PanelRows.guardedWrite(syncing,
				() -> panel.settings.write(keyName, checkBox.isSelected()));
			applyEnabled(checkBox.isSelected());
		});
		return checkBox;
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
		nameArea.addMouseListener(adapter);
		iconLabel.addMouseListener(adapter);
		nameLabel.addMouseListener(adapter);
	}
}
