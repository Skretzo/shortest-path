package shortestpath;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * A clickable icon control, modelled on the tile-packs panel action labels: shows a base icon, swaps
 * to a hover icon while the cursor is over it, runs an action on left-click, and carries a tooltip.
 */
class IconActionLabel extends JLabel
{
	IconActionLabel(ImageIcon icon, ImageIcon hoverIcon, String tooltip, Runnable onClick)
	{
		setIcon(icon);
		setToolTipText(tooltip);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					onClick.run();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setIcon(hoverIcon);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setIcon(icon);
			}
		});
	}
}
