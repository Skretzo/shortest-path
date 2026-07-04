package shortestpath;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/**
 * Draws a small triangular arrowhead at the end of a line segment, rotated to the segment's
 * direction, in the current colour of the graphics context.
 * <p>
 * Adapted from Quest Helper's {@code DirectionArrow.drawLineArrowHead}
 * (Copyright (c) 2021, Zoinkwiz &lt;https://github.com/Zoinkwiz&gt;, BSD 2-Clause licence),
 * as also used by the port-tasks plugin.
 */
public final class ArrowHead
{
	private ArrowHead()
	{
	}

	/**
	 * Fills an arrowhead pointing from (x1, y1) towards (x2, y2), with its tip at (x2, y2).
	 *
	 * @param size length of the arrowhead in pixels (its width is {@code size})
	 */
	public static void draw(Graphics2D graphics, double x1, double y1, double x2, double y2, double size)
	{
		Path2D head = new Path2D.Double();
		head.moveTo(0, 0);
		head.lineTo(-size / 2.0, -size);
		head.lineTo(size / 2.0, -size);
		head.closePath();

		AffineTransform tx = new AffineTransform();
		double angle = Math.atan2(y2 - y1, x2 - x1);
		tx.translate(x2, y2);
		tx.rotate(angle - Math.PI / 2d);

		Graphics2D g = (Graphics2D) graphics.create();
		// Compose (not replace) so the overlay's existing transform/clip stay intact.
		g.transform(tx);
		g.fill(head);
		g.dispose();
	}
}
