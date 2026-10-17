package shortestpath;

import java.awt.Dimension;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

/**
 * A panel whose preferred width is pinned to {@link PluginPanel#PANEL_WIDTH}
 * so a {@code JScrollPane} view can never outgrow the 225px viewport — the
 * RuneLite config-package pattern (package-private there, reimplemented
 * here). The outer {@code super(false)} panel requests
 * PANEL_WIDTH + SCROLLBAR_WIDTH = 242px and the scroll viewport is
 * 242 - 17 = 225px, so the view must prefer exactly 225: wider content would
 * lay out under the vertical scrollbar's column instead of shrinking to the
 * viewport. This is the only width contract in the panel tree — children
 * must never re-pin a width.
 */
class FixedWidthPanel extends JPanel
{
	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
	}
}
