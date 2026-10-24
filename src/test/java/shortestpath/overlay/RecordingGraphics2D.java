package shortestpath.overlay;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.BasicStroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A hand-written {@link Graphics2D} delegate that records every call as one
 * transcript line before forwarding it to a real delegate (typically
 * {@code bufferedImage.createGraphics()}), so the wrapped overlay render runs
 * the genuine AWT pipeline while the test captures the exact call sequence.
 * <p>
 * Written out in full by hand — RuneLite rejects runtime reflection and
 * dynamic proxying, so no forwarding machinery may be used. Every draw- and
 * state-mutating call lands in the transcript carrying the colour, stroke, and
 * clip the callee would inherit at call time ({@code " | color=.. stroke=..
 * clip=.."}), which is what makes the deliberate non-restore leaks (clip,
 * stroke, colour) visible to the golden-transcript comparisons: a "safely
 * restored" state surfaces as a transcript diff even when the pixels match.
 * <p>
 * {@link #create()} returns a recording child that shares this transcript —
 * consumers that draw through {@code graphics.create()}/{@code dispose()}
 * (e.g. {@link ArrowHead#draw}) still land in the log. {@link
 * #getFontMetrics(Font)} and {@link #getFontRenderContext()} delegate for real
 * so label centring via {@code getStringBounds} keeps deterministic arguments.
 * <p>
 * Shape arguments are rendered through {@link #shape(Shape)} at fixed
 * one-decimal precision — the single normalisation point that absorbs
 * wall-clock-phased draw arguments (the TRACER moving marker and the teleport
 * pulse's {@code System.currentTimeMillis()} phase) so transcripts stay
 * deterministic.
 */
final class RecordingGraphics2D extends Graphics2D
{
	private final Graphics2D delegate;
	private final List<String> transcript;

	RecordingGraphics2D(Graphics2D delegate, List<String> transcript)
	{
		this.delegate = delegate;
		this.transcript = transcript;
	}

	// ---------------------------------------------------------------
	// Transcript rendering
	// ---------------------------------------------------------------

	private String state()
	{
		return " color=" + colour(getColor())
			+ " stroke=" + stroke(delegate.getStroke())
			+ " clip=" + shape(delegate.getClip());
	}

	private void rec(String call)
	{
		transcript.add(call + " |" + state());
	}

	private static String colour(Color c)
	{
		if (c == null)
		{
			return "null";
		}
		return String.format(Locale.ROOT, "rgba(%d,%d,%d,%d)", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
	}

	private static String stroke(Stroke s)
	{
		if (s == null)
		{
			return "null";
		}
		if (s instanceof BasicStroke)
		{
			BasicStroke b = (BasicStroke) s;
			return String.format(Locale.ROOT, "BasicStroke(w=%.1f)", b.getLineWidth());
		}
		return s.getClass().getSimpleName();
	}

	/**
	 * Shape stringification at fixed one-decimal precision: the mask point for
	 * wall-clock-phased coordinates (TRACER marker, teleport-pulse phase).
	 * Path contents are flattened to one decimal so a millisecond shift cannot
	 * flip a digit.
	 */
	static String shape(Shape s)
	{
		if (s == null)
		{
			return "null";
		}
		if (s instanceof Polygon)
		{
			Polygon p = (Polygon) s;
			StringBuilder sb = new StringBuilder("Polygon(");
			for (int i = 0; i < p.npoints; i++)
			{
				if (i > 0)
				{
					sb.append(' ');
				}
				sb.append(p.xpoints[i]).append(',').append(p.ypoints[i]);
			}
			return sb.append(')').toString();
		}
		if (s instanceof Line2D)
		{
			Line2D l = (Line2D) s;
			return String.format(Locale.ROOT, "Line2D(%.1f,%.1f->%.1f,%.1f)",
				l.getX1(), l.getY1(), l.getX2(), l.getY2());
		}
		if (s instanceof Ellipse2D)
		{
			Ellipse2D e = (Ellipse2D) s;
			return String.format(Locale.ROOT, "Ellipse(%.1f,%.1f %.1fx%.1f)",
				e.getX(), e.getY(), e.getWidth(), e.getHeight());
		}
		if (s instanceof Rectangle2D || s instanceof Rectangle)
		{
			Rectangle2D r = (Rectangle2D) s;
			return String.format(Locale.ROOT, "Rect(%.1f,%.1f %.1fx%.1f)",
				r.getX(), r.getY(), r.getWidth(), r.getHeight());
		}
		if (s instanceof Path2D)
		{
			PathIterator it = s.getPathIterator(null);
			List<String> segments = new ArrayList<>();
			double[] coords = new double[6];
			while (!it.isDone())
			{
				int type = it.currentSegment(coords);
				switch (type)
				{
					case PathIterator.SEG_MOVETO:
						segments.add(String.format(Locale.ROOT, "M%.1f,%.1f", coords[0], coords[1]));
						break;
					case PathIterator.SEG_LINETO:
						segments.add(String.format(Locale.ROOT, "L%.1f,%.1f", coords[0], coords[1]));
						break;
					case PathIterator.SEG_QUADTO:
						segments.add(String.format(Locale.ROOT, "Q%.1f,%.1f %.1f,%.1f",
							coords[0], coords[1], coords[2], coords[3]));
						break;
					case PathIterator.SEG_CUBICTO:
						segments.add(String.format(Locale.ROOT, "C%.1f,%.1f %.1f,%.1f %.1f,%.1f",
							coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]));
						break;
					case PathIterator.SEG_CLOSE:
						segments.add("Z");
						break;
					default:
						segments.add("?");
				}
				it.next();
			}
			return "Path2D(" + String.join(" ", segments) + ")";
		}
		// Composite shapes (e.g. the world-map clip Area) identify by class and bounds.
		Rectangle2D bounds = s.getBounds2D();
		return String.format(Locale.ROOT, "%s(%.1f,%.1f %.1fx%.1f)",
			s.getClass().getSimpleName(), bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
	}

	// ---------------------------------------------------------------
	// Child graphics — the transcript tunnels through create()/dispose()
	// ---------------------------------------------------------------

	@Override
	public Graphics create()
	{
		rec("create");
		return new RecordingGraphics2D((Graphics2D) delegate.create(), transcript);
	}

	@Override
	public void dispose()
	{
		rec("dispose");
		delegate.dispose();
	}

	// ---------------------------------------------------------------
	// Coordinate transforms and clip state
	// ---------------------------------------------------------------

	@Override
	public void translate(int x, int y)
	{
		rec("translate " + x + "," + y);
		delegate.translate(x, y);
	}

	@Override
	public void translate(double tx, double ty)
	{
		rec(String.format(Locale.ROOT, "translate %.1f,%.1f", tx, ty));
		delegate.translate(tx, ty);
	}

	@Override
	public void rotate(double theta)
	{
		rec(String.format(Locale.ROOT, "rotate %.4f", theta));
		delegate.rotate(theta);
	}

	@Override
	public void rotate(double theta, double x, double y)
	{
		rec(String.format(Locale.ROOT, "rotate %.4f @%.1f,%.1f", theta, x, y));
		delegate.rotate(theta, x, y);
	}

	@Override
	public void scale(double sx, double sy)
	{
		rec(String.format(Locale.ROOT, "scale %.4f,%.4f", sx, sy));
		delegate.scale(sx, sy);
	}

	@Override
	public void shear(double shx, double shy)
	{
		rec(String.format(Locale.ROOT, "shear %.4f,%.4f", shx, shy));
		delegate.shear(shx, shy);
	}

	@Override
	public void transform(AffineTransform tx)
	{
		rec("transform " + tx);
		delegate.transform(tx);
	}

	@Override
	public void setTransform(AffineTransform tx)
	{
		rec("setTransform " + tx);
		delegate.setTransform(tx);
	}

	@Override
	public AffineTransform getTransform()
	{
		return delegate.getTransform();
	}

	@Override
	public Rectangle getClipBounds()
	{
		return delegate.getClipBounds();
	}

	@Override
	public void clipRect(int x, int y, int width, int height)
	{
		rec("clipRect " + x + "," + y + " " + width + "x" + height);
		delegate.clipRect(x, y, width, height);
	}

	@Override
	public void setClip(int x, int y, int width, int height)
	{
		rec("setClip " + x + "," + y + " " + width + "x" + height);
		delegate.setClip(x, y, width, height);
	}

	@Override
	public Shape getClip()
	{
		return delegate.getClip();
	}

	@Override
	public void setClip(Shape clip)
	{
		rec("setClip " + shape(clip));
		delegate.setClip(clip);
	}

	@Override
	public void clip(Shape s)
	{
		rec("clip " + shape(s));
		delegate.clip(s);
	}

	@Override
	public void copyArea(int x, int y, int width, int height, int dx, int dy)
	{
		rec("copyArea " + x + "," + y + " " + width + "x" + height + " +" + dx + "," + dy);
		delegate.copyArea(x, y, width, height, dx, dy);
	}

	// ---------------------------------------------------------------
	// Colour / paint / stroke / composite state
	// ---------------------------------------------------------------

	@Override
	public Color getColor()
	{
		return delegate.getColor();
	}

	@Override
	public void setColor(Color c)
	{
		rec("setColor " + colour(c));
		delegate.setColor(c);
	}

	@Override
	public void setPaint(Paint paint)
	{
		rec("setPaint " + (paint instanceof Color ? colour((Color) paint) : String.valueOf(paint)));
		delegate.setPaint(paint);
	}

	@Override
	public Paint getPaint()
	{
		return delegate.getPaint();
	}

	@Override
	public void setStroke(Stroke s)
	{
		rec("setStroke " + stroke(s));
		delegate.setStroke(s);
	}

	@Override
	public Stroke getStroke()
	{
		return delegate.getStroke();
	}

	@Override
	public void setComposite(Composite comp)
	{
		rec("setComposite " + (comp == null ? "null" : comp.getClass().getSimpleName()));
		delegate.setComposite(comp);
	}

	@Override
	public Composite getComposite()
	{
		return delegate.getComposite();
	}

	@Override
	public void setPaintMode()
	{
		rec("setPaintMode");
		delegate.setPaintMode();
	}

	@Override
	public void setXORMode(Color c1)
	{
		rec("setXORMode " + colour(c1));
		delegate.setXORMode(c1);
	}

	@Override
	public void setBackground(Color color)
	{
		rec("setBackground " + colour(color));
		delegate.setBackground(color);
	}

	@Override
	public Color getBackground()
	{
		return delegate.getBackground();
	}

	// ---------------------------------------------------------------
	// Font state and metrics — metrics delegate for real so label
	// centring keeps deterministic arguments.
	// ---------------------------------------------------------------

	@Override
	public Font getFont()
	{
		return delegate.getFont();
	}

	@Override
	public void setFont(Font font)
	{
		rec("setFont " + (font == null ? "null" : font.getFontName() + " " + font.getSize()));
		delegate.setFont(font);
	}

	@Override
	public FontMetrics getFontMetrics(Font f)
	{
		return delegate.getFontMetrics(f);
	}

	@Override
	public FontRenderContext getFontRenderContext()
	{
		return delegate.getFontRenderContext();
	}

	// ---------------------------------------------------------------
	// Draw family
	// ---------------------------------------------------------------

	@Override
	public void drawLine(int x1, int y1, int x2, int y2)
	{
		rec("drawLine " + x1 + "," + y1 + "->" + x2 + "," + y2);
		delegate.drawLine(x1, y1, x2, y2);
	}

	@Override
	public void draw(Shape s)
	{
		rec("draw " + shape(s));
		delegate.draw(s);
	}

	@Override
	public void fill(Shape s)
	{
		rec("fill " + shape(s));
		delegate.fill(s);
	}

	@Override
	public void fillRect(int x, int y, int width, int height)
	{
		rec("fillRect " + x + "," + y + " " + width + "x" + height);
		delegate.fillRect(x, y, width, height);
	}

	@Override
	public void drawRect(int x, int y, int width, int height)
	{
		rec("drawRect " + x + "," + y + " " + width + "x" + height);
		delegate.drawRect(x, y, width, height);
	}

	@Override
	public void clearRect(int x, int y, int width, int height)
	{
		rec("clearRect " + x + "," + y + " " + width + "x" + height);
		delegate.clearRect(x, y, width, height);
	}

	@Override
	public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
	{
		rec("drawRoundRect " + x + "," + y + " " + width + "x" + height + " arc " + arcWidth + "x" + arcHeight);
		delegate.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
	}

	@Override
	public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight)
	{
		rec("fillRoundRect " + x + "," + y + " " + width + "x" + height + " arc " + arcWidth + "x" + arcHeight);
		delegate.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
	}

	@Override
	public void draw3DRect(int x, int y, int width, int height, boolean raised)
	{
		rec("draw3DRect " + x + "," + y + " " + width + "x" + height + " " + raised);
		delegate.draw3DRect(x, y, width, height, raised);
	}

	@Override
	public void fill3DRect(int x, int y, int width, int height, boolean raised)
	{
		rec("fill3DRect " + x + "," + y + " " + width + "x" + height + " " + raised);
		delegate.fill3DRect(x, y, width, height, raised);
	}

	@Override
	public void drawOval(int x, int y, int width, int height)
	{
		rec("drawOval " + x + "," + y + " " + width + "x" + height);
		delegate.drawOval(x, y, width, height);
	}

	@Override
	public void fillOval(int x, int y, int width, int height)
	{
		rec("fillOval " + x + "," + y + " " + width + "x" + height);
		delegate.fillOval(x, y, width, height);
	}

	@Override
	public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle)
	{
		rec("drawArc " + x + "," + y + " " + width + "x" + height + " " + startAngle + "+" + arcAngle);
		delegate.drawArc(x, y, width, height, startAngle, arcAngle);
	}

	@Override
	public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle)
	{
		rec("fillArc " + x + "," + y + " " + width + "x" + height + " " + startAngle + "+" + arcAngle);
		delegate.fillArc(x, y, width, height, startAngle, arcAngle);
	}

	@Override
	public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints)
	{
		rec("drawPolyline n=" + nPoints);
		delegate.drawPolyline(xPoints, yPoints, nPoints);
	}

	@Override
	public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints)
	{
		rec("drawPolygon n=" + nPoints);
		delegate.drawPolygon(xPoints, yPoints, nPoints);
	}

	@Override
	public void drawPolygon(Polygon p)
	{
		rec("drawPolygon " + shape(p));
		delegate.drawPolygon(p);
	}

	@Override
	public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints)
	{
		rec("fillPolygon n=" + nPoints);
		delegate.fillPolygon(xPoints, yPoints, nPoints);
	}

	@Override
	public void fillPolygon(Polygon p)
	{
		rec("fillPolygon " + shape(p));
		delegate.fillPolygon(p);
	}

	@Override
	public void drawString(String str, int x, int y)
	{
		rec("drawString \"" + str + "\" @" + x + "," + y);
		delegate.drawString(str, x, y);
	}

	@Override
	public void drawString(String str, float x, float y)
	{
		rec(String.format(Locale.ROOT, "drawString \"%s\" @%.1f,%.1f", str, x, y));
		delegate.drawString(str, x, y);
	}

	@Override
	public void drawString(AttributedCharacterIterator iterator, int x, int y)
	{
		rec("drawString(aci) @" + x + "," + y);
		delegate.drawString(iterator, x, y);
	}

	@Override
	public void drawString(AttributedCharacterIterator iterator, float x, float y)
	{
		rec(String.format(Locale.ROOT, "drawString(aci) @%.1f,%.1f", x, y));
		delegate.drawString(iterator, x, y);
	}

	@Override
	public void drawGlyphVector(GlyphVector g, float x, float y)
	{
		rec(String.format(Locale.ROOT, "drawGlyphVector @%.1f,%.1f", x, y));
		delegate.drawGlyphVector(g, x, y);
	}

	@Override
	public void drawChars(char[] data, int offset, int length, int x, int y)
	{
		rec("drawChars \"" + new String(data, offset, length) + "\" @" + x + "," + y);
		delegate.drawChars(data, offset, length, x, y);
	}

	@Override
	public void drawBytes(byte[] data, int offset, int length, int x, int y)
	{
		rec("drawBytes @" + x + "," + y);
		delegate.drawBytes(data, offset, length, x, y);
	}

	// ---------------------------------------------------------------
	// Images
	// ---------------------------------------------------------------

	@Override
	public boolean drawImage(Image img, int x, int y, ImageObserver observer)
	{
		rec("drawImage @" + x + "," + y);
		return delegate.drawImage(img, x, y, observer);
	}

	@Override
	public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer)
	{
		rec("drawImage @" + x + "," + y + " " + width + "x" + height);
		return delegate.drawImage(img, x, y, width, height, observer);
	}

	@Override
	public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer)
	{
		rec("drawImage @" + x + "," + y + " bg=" + colour(bgcolor));
		return delegate.drawImage(img, x, y, bgcolor, observer);
	}

	@Override
	public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer)
	{
		rec("drawImage @" + x + "," + y + " " + width + "x" + height + " bg=" + colour(bgcolor));
		return delegate.drawImage(img, x, y, width, height, bgcolor, observer);
	}

	@Override
	public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
		ImageObserver observer)
	{
		rec("drawImage dst " + dx1 + "," + dy1 + "->" + dx2 + "," + dy2);
		return delegate.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
	}

	@Override
	public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
		Color bgcolor, ImageObserver observer)
	{
		rec("drawImage dst " + dx1 + "," + dy1 + "->" + dx2 + "," + dy2 + " bg=" + colour(bgcolor));
		return delegate.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
	}

	@Override
	public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs)
	{
		rec("drawImage(xform)");
		return delegate.drawImage(img, xform, obs);
	}

	@Override
	public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y)
	{
		rec("drawImage(op) @" + x + "," + y);
		delegate.drawImage(img, op, x, y);
	}

	@Override
	public void drawRenderedImage(RenderedImage img, AffineTransform xform)
	{
		rec("drawRenderedImage");
		delegate.drawRenderedImage(img, xform);
	}

	@Override
	public void drawRenderableImage(RenderableImage img, AffineTransform xform)
	{
		rec("drawRenderableImage");
		delegate.drawRenderableImage(img, xform);
	}

	// ---------------------------------------------------------------
	// Hit testing and rendering hints
	// ---------------------------------------------------------------

	@Override
	public boolean hit(Rectangle rect, Shape s, boolean onStroke)
	{
		return delegate.hit(rect, s, onStroke);
	}

	@Override
	public GraphicsConfiguration getDeviceConfiguration()
	{
		return delegate.getDeviceConfiguration();
	}

	@Override
	public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue)
	{
		rec("setRenderingHint " + hintKey);
		delegate.setRenderingHint(hintKey, hintValue);
	}

	@Override
	public Object getRenderingHint(RenderingHints.Key hintKey)
	{
		return delegate.getRenderingHint(hintKey);
	}

	@Override
	public void setRenderingHints(Map<?, ?> hints)
	{
		rec("setRenderingHints n=" + hints.size());
		delegate.setRenderingHints(hints);
	}

	@Override
	public void addRenderingHints(Map<?, ?> hints)
	{
		rec("addRenderingHints n=" + hints.size());
		delegate.addRenderingHints(hints);
	}

	@Override
	public RenderingHints getRenderingHints()
	{
		return delegate.getRenderingHints();
	}
}
