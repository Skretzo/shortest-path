package shortestpath;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

/**
 * Small 16px UI icons for the alternative-routes panel, rendered with Java2D so the plugin carries no
 * image assets. Each action has a base (grey) and a hover (accent) variant, mirroring the
 * base/hover icon swap used by the tile-packs panel controls.
 */
final class RouteIcons
{
	private static final int SIZE = 16;

	private static final Color GREY = new Color(0xA8, 0xA8, 0xA8);
	private static final Color LIGHT = new Color(0xED, 0xED, 0xED);
	private static final Color RED = new Color(0xE3, 0x1C, 0x1C);
	private static final Color GREEN = new Color(0x4C, 0xAF, 0x50);
	private static final Color GREEN_LIGHT = new Color(0x7C, 0xD6, 0x80);
	private static final Color ORANGE = new Color(0xFF, 0x98, 0x1F);
	private static final Color ORANGE_LIGHT = new Color(0xFF, 0xC0, 0x6A);
	private static final Color GOLD = new Color(0xF2, 0xC1, 0x4E);

	// Show / hide a route on the map (map pin). Active = currently shown.
	static final ImageIcon SHOW = new ImageIcon(pin(GREY));
	static final ImageIcon SHOW_HOVER = new ImageIcon(pin(LIGHT));
	static final ImageIcon SHOW_ACTIVE = new ImageIcon(pin(ORANGE));
	static final ImageIcon SHOW_ACTIVE_HOVER = new ImageIcon(pin(ORANGE_LIGHT));
	// Exclude a method from the next search (no-entry).
	static final ImageIcon EXCLUDE = new ImageIcon(ban(GREY));
	static final ImageIcon EXCLUDE_HOVER = new ImageIcon(ban(RED));
	// Re-include an excluded method (plus).
	static final ImageIcon INCLUDE = new ImageIcon(plus(GREY));
	static final ImageIcon INCLUDE_HOVER = new ImageIcon(plus(GREEN));
	// Recompute routes (circular refresh arrow).
	static final ImageIcon REFRESH = new ImageIcon(refresh(GREY));
	static final ImageIcon REFRESH_HOVER = new ImageIcon(refresh(LIGHT));
	// Clear all exclusions (trash can).
	static final ImageIcon CLEAR = new ImageIcon(trash(GREY));
	static final ImageIcon CLEAR_HOVER = new ImageIcon(trash(RED));
	// Catalog toggles: included (check), excluded (cross), partially-included category (dash).
	static final ImageIcon CHECK = new ImageIcon(check(GREEN));
	static final ImageIcon CHECK_HOVER = new ImageIcon(check(GREEN_LIGHT));
	static final ImageIcon CROSS = new ImageIcon(cross(GREY));
	static final ImageIcon CROSS_HOVER = new ImageIcon(cross(RED));
	static final ImageIcon DASH = new ImageIcon(dash(ORANGE));
	static final ImageIcon DASH_HOVER = new ImageIcon(dash(ORANGE_LIGHT));
	// Expand/collapse a category.
	static final ImageIcon CHEVRON_RIGHT = new ImageIcon(chevron(GREY, false));
	static final ImageIcon CHEVRON_RIGHT_HOVER = new ImageIcon(chevron(LIGHT, false));
	static final ImageIcon CHEVRON_DOWN = new ImageIcon(chevron(GREY, true));
	static final ImageIcon CHEVRON_DOWN_HOVER = new ImageIcon(chevron(LIGHT, true));
	// Method the player can't use right now (missing item/level/quest/unlock).
	static final ImageIcon LOCKED = new ImageIcon(lock(ORANGE));
	// Method whose required item is owned but sitting in the bank (route through a bank to grab it).
	static final ImageIcon IN_BANK = new ImageIcon(coins(GOLD));

	private RouteIcons()
	{
	}

	private interface Drawer
	{
		void draw(Graphics2D g);
	}

	private static BufferedImage render(Drawer drawer)
	{
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		drawer.draw(g);
		g.dispose();
		return image;
	}

	private static BufferedImage pin(Color colour)
	{
		return render(g ->
		{
			final double cx = 8, cy = 6.4, r = 4.0;
			Path2D body = new Path2D.Double();
			body.moveTo(cx - 3.0, cy + 1.6);
			body.curveTo(cx - 2.0, cy + 4.4, cx - 0.5, cy + 5.4, cx, 14.6);
			body.curveTo(cx + 0.5, cy + 5.4, cx + 2.0, cy + 4.4, cx + 3.0, cy + 1.6);
			body.closePath();
			g.setColor(colour);
			g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
			g.fill(body);
			g.setComposite(AlphaComposite.Clear);
			final double hr = 1.65;
			g.fill(new Ellipse2D.Double(cx - hr, cy - hr, 2 * hr, 2 * hr));
			g.setComposite(AlphaComposite.SrcOver);
		});
	}

	private static BufferedImage ban(Color colour)
	{
		return render(g ->
		{
			g.setColor(colour);
			final double m = 1.7, d = SIZE - 2 * m;
			g.draw(new Ellipse2D.Double(m, m, d, d));
			g.draw(new Line2D.Double(5.0, 8.0, 11.0, 8.0));
		});
	}

	private static BufferedImage plus(Color colour)
	{
		return render(g ->
		{
			g.setColor(colour);
			final double m = 1.7, d = SIZE - 2 * m;
			g.draw(new Ellipse2D.Double(m, m, d, d));
			g.draw(new Line2D.Double(8, 5, 8, 11));
			g.draw(new Line2D.Double(5, 8, 11, 8));
		});
	}

	private static BufferedImage refresh(Color colour)
	{
		return render(g ->
		{
			g.setColor(colour);
			final double m = 2.6, d = SIZE - 2 * m;
			final double start = 65, extent = 250;
			Arc2D arc = new Arc2D.Double(m, m, d, d, start, extent, Arc2D.OPEN);
			g.draw(arc);
			Point2D p0 = arc.getStartPoint();
			Point2D p1 = new Arc2D.Double(m, m, d, d, start + 5, 1, Arc2D.OPEN).getStartPoint();
			double angle = Math.atan2(p0.getY() - p1.getY(), p0.getX() - p1.getX());
			arrowHead(g, p0.getX(), p0.getY(), angle, 3.4, colour);
		});
	}

	private static BufferedImage trash(Color colour)
	{
		return render(g ->
		{
			g.setColor(colour);
			g.draw(new Line2D.Double(3.5, 4.6, 12.5, 4.6));
			g.draw(new Line2D.Double(6.5, 4.6, 6.5, 3.2));
			g.draw(new Line2D.Double(9.5, 4.6, 9.5, 3.2));
			g.draw(new Line2D.Double(6.5, 3.2, 9.5, 3.2));
			Path2D body = new Path2D.Double();
			body.moveTo(4.4, 5.4);
			body.lineTo(5.2, 13.0);
			body.lineTo(10.8, 13.0);
			body.lineTo(11.6, 5.4);
			g.draw(body);
			g.draw(new Line2D.Double(6.6, 6.4, 6.9, 12.0));
			g.draw(new Line2D.Double(8.0, 6.4, 8.0, 12.0));
			g.draw(new Line2D.Double(9.4, 6.4, 9.1, 12.0));
		});
	}

	private static BufferedImage check(Color colour)
	{
		return render(g ->
		{
			g.setColor(colour);
			g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Path2D tick = new Path2D.Double();
			tick.moveTo(3.5, 8.5);
			tick.lineTo(6.6, 11.6);
			tick.lineTo(12.5, 4.5);
			g.draw(tick);
		});
	}

	private static BufferedImage cross(Color colour)
	{
		return render(g ->
		{
			g.setColor(colour);
			g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Line2D.Double(4.2, 4.2, 11.8, 11.8));
			g.draw(new Line2D.Double(11.8, 4.2, 4.2, 11.8));
		});
	}

	private static BufferedImage dash(Color colour)
	{
		return render(g ->
		{
			g.setColor(colour);
			g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Line2D.Double(4.0, 8.0, 12.0, 8.0));
		});
	}

	private static BufferedImage lock(Color colour)
	{
		return render(g ->
		{
			g.setColor(colour);
			// Shackle
			g.draw(new Arc2D.Double(4.75, 2.5, 6.5, 7.5, 0, 180, Arc2D.OPEN));
			// Body
			g.fillRoundRect(3, 7, 10, 6, 3, 3);
		});
	}

	private static BufferedImage coins(Color colour)
	{
		return render(g ->
		{
			g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			final double x = 3.5, w = 9.0, h = 3.4;
			// Bottom-to-top so the upper coins overlap the lower ones, reading as a stack.
			final double[] ys = {9.6, 7.0, 4.4};
			for (double y : ys)
			{
				g.setColor(colour);
				g.fill(new Ellipse2D.Double(x, y, w, h));
				g.setColor(colour.darker());
				g.draw(new Ellipse2D.Double(x, y, w, h));
			}
		});
	}

	private static BufferedImage chevron(Color colour, boolean down)
	{
		return render(g ->
		{
			g.setColor(colour);
			Path2D triangle = new Path2D.Double();
			if (down)
			{
				triangle.moveTo(4.5, 6.0);
				triangle.lineTo(11.5, 6.0);
				triangle.lineTo(8.0, 11.0);
			}
			else
			{
				triangle.moveTo(6.0, 4.5);
				triangle.lineTo(11.0, 8.0);
				triangle.lineTo(6.0, 11.5);
			}
			triangle.closePath();
			g.fill(triangle);
		});
	}

	private static void arrowHead(Graphics2D g, double x, double y, double angle, double size, Color colour)
	{
		Path2D head = new Path2D.Double();
		head.moveTo(x, y);
		head.lineTo(x - size * Math.cos(angle - Math.PI / 6), y - size * Math.sin(angle - Math.PI / 6));
		head.lineTo(x - size * Math.cos(angle + Math.PI / 6), y - size * Math.sin(angle + Math.PI / 6));
		head.closePath();
		g.setColor(colour);
		g.fill(head);
	}
}
