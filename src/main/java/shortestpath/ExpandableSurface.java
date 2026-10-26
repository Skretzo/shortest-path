package shortestpath;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.util.SwingUtil;

/**
 * The header-over-collapsible-detail-well contract every expandable
 * sidebar surface shares — transport family cards, restriction rows and
 * collapsible enum groups: a {@link #detail} well that starts hidden, a
 * chevron {@link #expander} that toggles it, and the search-expansion
 * semantics of {@link SearchExpandable} (a detail-label match opens a
 * collapsed surface; clearing the query restores user-collapsed state,
 * while a manual toggle marks the state user-owned and exempt from
 * collapse).
 *
 * <p>Subclasses lay out their own header, call {@link #newExpander()} when
 * detail content exists, add {@link #detail} after the header, and wire
 * click-anywhere-to-toggle through {@link #installExpandListener}. A
 * surface that never grows detail content stays expander-free —
 * {@link #setDetailOpen} and the search hooks all no-op then.</p>
 */
abstract class ExpandableSurface extends JPanel implements SearchExpandable
{
	final JPanel detail;
	JButton expander;
	private boolean detailOpen;
	private boolean searchExpanded;

	ExpandableSurface(Border detailBorder)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		detail = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		detail.setBorder(detailBorder);
		detail.setVisible(false);
	}

	boolean isDetailOpen()
	{
		return detailOpen;
	}

	/**
	 * The decorated chevron button — sized and placed by the caller's
	 * header layout. Creating it is also what enables the toggle paths:
	 * every method below treats {@link #expander} == null as "no detail
	 * affordance".
	 */
	JButton newExpander()
	{
		expander = new JButton(ShortestPathPanel.SECTION_EXPAND_ICON);
		expander.setToolTipText("Expand");
		SwingUtil.removeButtonDecorations(expander);
		expander.addActionListener(e -> setDetailOpen(!isDetailOpen()));
		return expander;
	}

	/**
	 * Manual toggle: counts as user intent, so search no longer owns the
	 * expansion state of this surface.
	 */
	void setDetailOpen(boolean open)
	{
		if (expander == null)
		{
			return;
		}
		searchExpanded = false;
		setDetailOpenInternal(open);
	}

	/**
	 * Search-driven expansion: opens the detail well when the query
	 * matched a detail label rather than the surface's own name, so the
	 * matched control is visible. {@link #clearSearchExpanded()} restores
	 * the collapsed state when the query clears.
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
			expander.setIcon(open ? ShortestPathPanel.SECTION_RETRACT_ICON
				: ShortestPathPanel.SECTION_EXPAND_ICON);
			expander.setToolTipText(open ? "Retract" : "Expand");
		}
		detail.setVisible(open);
		revalidate();
		repaint();
	}

	/**
	 * Click-anywhere-to-toggle wiring for the header components: Swing
	 * never forwards child mouse events to the parent, so each component
	 * needs the adapter. The expander's enabled flag gates the toggle — a
	 * surface whose detail is locked (disabled master) stays shut.
	 */
	void installExpandListener(JComponent... targets)
	{
		MouseAdapter adapter = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (expander != null && expander.isEnabled())
				{
					setDetailOpen(!isDetailOpen());
				}
			}
		};
		for (JComponent target : targets)
		{
			target.addMouseListener(adapter);
		}
	}
}
