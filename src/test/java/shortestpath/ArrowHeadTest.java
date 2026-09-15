package shortestpath;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ArrowHeadTest
{
	private static final int SIZE = 40;
	private static final int OPAQUE_WHITE = 0xFFFFFFFF;

	private static BufferedImage drawHead(double x1, double y1, double x2, double y2)
	{
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		ArrowHead.draw(graphics, x1, y1, x2, y2, 8);
		graphics.dispose();
		return image;
	}

	private static boolean painted(BufferedImage image, int x, int y)
	{
		return (image.getRGB(x, y) >>> 24) != 0;
	}

	@Test
	public void tipIsAtSegmentEnd()
	{
		BufferedImage image = drawHead(10, 20, 30, 20);
		assertTrue("A pixel just behind the tip must be painted", painted(image, 29, 20));
		assertTrue("Nothing may be painted past the tip", !painted(image, 32, 20));
	}

	@Test
	public void headPointsAlongSegmentDirection()
	{
		// East-pointing: the body extends west of the tip, not north/south of the segment axis by more
		// than the head's half-width, and nothing lands near the segment's start.
		BufferedImage east = drawHead(10, 20, 30, 20);
		assertTrue("Body extends back along the segment", painted(east, 24, 20));
		assertTrue("Start of the segment stays unpainted", !painted(east, 12, 20));

		// South-pointing: same shape rotated 90°.
		BufferedImage south = drawHead(20, 10, 20, 30);
		assertTrue("Body extends back along the segment", painted(south, 20, 24));
		assertTrue("Start of the segment stays unpainted", !painted(south, 20, 12));
	}

	@Test
	public void usesCurrentGraphicsColour()
	{
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		ArrowHead.draw(graphics, 10, 20, 30, 20, 8);
		graphics.dispose();
		assertEquals("Head is filled with the graphics' current colour", OPAQUE_WHITE, image.getRGB(28, 20));
	}

	@Test
	public void restoresGraphicsTransform()
	{
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.WHITE);
		java.awt.geom.AffineTransform before = graphics.getTransform();
		ArrowHead.draw(graphics, 10, 20, 30, 20, 8);
		assertEquals("Drawing must not disturb the caller's transform", before, graphics.getTransform());
		graphics.dispose();
	}
}
