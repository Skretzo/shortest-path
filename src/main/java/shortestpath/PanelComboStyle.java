package shortestpath;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.TitleCaseListCellRenderer;

/**
 * The panel's single combo styling path: a dark closed face over a lighter
 * medium-grey popup list — the contrast direction the teleport-restriction
 * surface already shows. Both call sites ({@link ShortestPathPanel}'s
 * {@code comboRow} and {@link TransportFamilyCard}'s enum master) run
 * through {@link #apply(JComboBox)} so the face colour, popup colours,
 * height and the width cap can never diverge per combo.
 */
final class PanelComboStyle
{
	/** Closed face: the dark control tone the transport-card combos show. */
	static final Color FACE_BACKGROUND = ColorScheme.DARKER_GRAY_COLOR;
	/** Popup rows: the lighter grey that reads as the expanded surface. */
	static final Color POPUP_BACKGROUND = ColorScheme.MEDIUM_GRAY_COLOR;
	/**
	 * The selection highlight inside the popup. The LAF's own selection
	 * colour is the same medium grey as {@link #POPUP_BACKGROUND} — reusing
	 * it would hide the highlight — so selected rows invert to the darker
	 * panel tone instead.
	 */
	static final Color POPUP_SELECTED_BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	/**
	 * Width ceiling for a combo's preferred size: past it a long enum
	 * constant squeezes the row's label column until the label wraps and
	 * the row grows extra lines.
	 */
	static final int MAX_WIDTH = 110;
	static final int HEIGHT = 22;

	private PanelComboStyle()
	{
	}

	/**
	 * Applies the shared renderer and face colour, then pins the preferred
	 * size: a fixed height with the width capped at {@link #MAX_WIDTH}. The
	 * renderer goes on first — {@code getPreferredSize()} consults it to
	 * measure the display text.
	 */
	static void apply(JComboBox<?> combo)
	{
		combo.setRenderer(Renderer.INSTANCE);
		combo.setBackground(FACE_BACKGROUND);
		combo.setPreferredSize(new Dimension(
			Math.min(combo.getPreferredSize().width, MAX_WIDTH), HEIGHT));
	}

	/**
	 * Title-Cases enum constants like RuneLite's own combos while restyling
	 * the colours: popup rows take the lighter grey with the inverted
	 * selection tone, and the index {@code -1} closed-face render takes the
	 * dark face colour for look-and-feels that style the face through the
	 * renderer (FlatLaf overrides it with the combo's own background, which
	 * {@link #apply} sets to the same tone).
	 */
	static final class Renderer extends DefaultListCellRenderer
	{
		private static final Renderer INSTANCE = new Renderer();

		// TitleCaseListCellRenderer is final — delegate for its text
		// transform rather than reimplementing the casing rules.
		private final TitleCaseListCellRenderer titleCase = new TitleCaseListCellRenderer();

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value,
			int index, boolean isSelected, boolean cellHasFocus)
		{
			Component c = titleCase.getListCellRendererComponent(
				list, value, index, isSelected, cellHasFocus);
			if (index < 0)
			{
				c.setBackground(FACE_BACKGROUND);
			}
			else
			{
				c.setBackground(isSelected ? POPUP_SELECTED_BACKGROUND : POPUP_BACKGROUND);
			}
			return c;
		}
	}
}
