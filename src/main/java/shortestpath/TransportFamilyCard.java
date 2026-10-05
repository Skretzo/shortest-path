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
import net.runelite.client.ui.components.TitleCaseListCellRenderer;
import net.runelite.client.util.SwingUtil;

/**
 * One transport-family card in the sidebar panel: a 16px icon slot, the family
 * name, a chevron expander (only rendered once detail content exists) and the
 * family's master control on the right edge. Boolean masters render a checkbox;
 * enum-valued masters (teleportation items) render an enum combo. Detail rows
 * live in an indented well that is dimmed and disabled while the master is off.
 */
class TransportFamilyCard extends JPanel
{
	private static final int ICON_LABEL_GAP = 4;
	private static final int DETAIL_INDENT = ShortestPathPanel.CARD_ICON_SIZE + ICON_LABEL_GAP;
	private static final int COMBO_MAX_WIDTH = 110;
	private static final int COMBO_HEIGHT = 22;

	private final ShortestPathPanel panel;
	private final String keyName;

	private final DimmableJPanel header;
	private final JPanel nameArea;
	private final JLabel iconLabel;
	private final JLabel nameLabel;
	private final JPanel headerControls;
	private final JComponent masterControl;
	private final JPanel detail;
	private JButton expander;
	private boolean detailOpen;

	TransportFamilyCard(ShortestPathPanel panel, String keyName, String title, String description, Integer iconItemId)
	{
		this.panel = panel;
		this.keyName = keyName;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(4, 8, 4, 8));

		header = new DimmableJPanel();
		header.setLayout(new BorderLayout());
		header.setOpaque(false);

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

		nameLabel = new JLabel(title);
		nameLabel.setToolTipText(ShortestPathPanel.html(description));
		nameArea.add(nameLabel, BorderLayout.CENTER);
		header.add(nameArea, BorderLayout.CENTER);

		headerControls = new JPanel();
		headerControls.setOpaque(false);
		headerControls.setLayout(new BoxLayout(headerControls, BoxLayout.X_AXIS));

		masterControl = createMasterControl(description);
		headerControls.add(masterControl);
		header.add(headerControls, BorderLayout.EAST);
		add(header);

		detail = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		detail.setOpaque(false);
		// Indented under the icon column so detail rows line up with the name.
		detail.setBorder(new EmptyBorder(2, DETAIL_INDENT, 4, 0));
		detail.setVisible(false);
		add(detail);

		applyEnabled(isMasterOn());
		panel.registerSync(keyName, this::syncFromConfig);
	}

	String getKeyName()
	{
		return keyName;
	}

	JLabel getNameLabel()
	{
		return nameLabel;
	}

	JComponent getMasterControl()
	{
		return masterControl;
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

	boolean isDetailOpen()
	{
		return detailOpen;
	}

	void setDetailOpen(boolean open)
	{
		if (expander == null)
		{
			return;
		}
		detailOpen = open;
		expander.setIcon(open ? ShortestPathPanel.SECTION_RETRACT_ICON : ShortestPathPanel.SECTION_EXPAND_ICON);
		expander.setToolTipText(open ? "Retract" : "Expand");
		detail.setVisible(open);
		revalidate();
		repaint();
	}

	boolean isMasterOn()
	{
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
	 * Re-reads the master value from the config proxy and mirrors it into the
	 * master control + dim state. Runs under the panel's echo guard.
	 */
	void syncFromConfig()
	{
		Object value = panel.configValue(keyName);
		if (masterControl instanceof JCheckBox)
		{
			((JCheckBox) masterControl).setSelected(Boolean.TRUE.equals(value));
		}
		else if (masterControl instanceof JComboBox && value instanceof Enum)
		{
			((JComboBox<?>) masterControl).setSelectedItem(value);
		}
		applyEnabled(isMasterOn());
	}

	private JComponent createMasterControl(String description)
	{
		Object value = panel.configValue(keyName);
		if (value instanceof Enum)
		{
			@SuppressWarnings("unchecked")
			Class<? extends Enum> type = (Class<? extends Enum>) ((Enum<?>) value).getClass();
			JComboBox<Enum<?>> combo = new JComboBox<Enum<?>>(type.getEnumConstants()); // NOPMD: UseDiamondOperator
			combo.setRenderer(new TitleCaseListCellRenderer());
			combo.setSelectedItem(value);
			combo.setPreferredSize(new Dimension(Math.min(combo.getPreferredSize().width, COMBO_MAX_WIDTH), COMBO_HEIGHT));
			combo.setToolTipText(ShortestPathPanel.html(description));
			combo.addItemListener(e ->
			{
				if (e.getStateChange() == ItemEvent.SELECTED)
				{
					if (!panel.suppressConfigSync)
					{
						panel.writeConfig(keyName, combo.getSelectedItem());
					}
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
			if (!panel.suppressConfigSync)
			{
				panel.writeConfig(keyName, checkBox.isSelected());
			}
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
