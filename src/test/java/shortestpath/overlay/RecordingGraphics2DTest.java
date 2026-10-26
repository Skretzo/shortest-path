package shortestpath.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * Self-pins for the recording delegate: the transcript records call order with
 * inherited draw state, {@code create()} children write into the same
 * transcript (the ArrowHead tunnel), and font metrics delegate for real.
 */
public class RecordingGraphics2DTest
{
	private static RecordingGraphics2D recorder(List<String> transcript)
	{
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		return new RecordingGraphics2D(image.createGraphics(), transcript);
	}

	@Test
	public void recordsCallOrderWithInheritedState()
	{
		List<String> transcript = new ArrayList<>();
		Graphics2D g = recorder(transcript);
		g.setColor(Color.RED);
		g.setStroke(new BasicStroke(4));
		g.setClip(2, 3, 20, 20);
		g.drawLine(1, 2, 3, 4);
		g.drawString("hi", 5, 6);
		g.dispose();

		assertEquals(6, transcript.size());
		assertTrue(transcript.get(0).startsWith("setColor rgb(255,0,0) a=255"));
		assertTrue(transcript.get(1).startsWith("setStroke BasicStroke(w=4.0)"));
		assertTrue(transcript.get(2).startsWith("setClip 2,3 20x20"));
		// Inherited state is captured at call time: the draw carries the
		// colour/stroke/clip the callee saw.
		String line = transcript.get(3);
		assertTrue(line.startsWith("drawLine 1,2->3,4"));
		assertTrue(line.contains("color=rgb(255,0,0) a=255"));
		assertTrue(line.contains("stroke=BasicStroke(w=4.0)"));
		assertTrue(line.contains("clip=Rect("));
		assertTrue(transcript.get(4).startsWith("drawString \"hi\" @5,6"));
		assertEquals("dispose | color=rgb(255,0,0) a=255 stroke=BasicStroke(w=4.0) clip=Rect(2.0,3.0 20.0x20.0)",
			transcript.get(5));
	}

	@Test
	public void createdChildrenShareTheTranscript()
	{
		// ArrowHead.draw's graphics.create()/dispose() draws must land in the
		// log — a raw-delegate child would lose them.
		List<String> transcript = new ArrayList<>();
		Graphics2D g = recorder(transcript);
		Graphics2D child = (Graphics2D) g.create();
		child.fill(new Rectangle(1, 1, 4, 4));
		child.dispose();

		assertEquals(3, transcript.size());
		assertTrue(transcript.get(0).startsWith("create"));
		assertTrue(transcript.get(1).startsWith("fill Rect("));
		assertTrue(transcript.get(2).startsWith("dispose"));
	}

	@Test
	public void fontMetricsAreReal()
	{
		List<String> transcript = new ArrayList<>();
		Graphics2D g = recorder(transcript);
		// drawCounter/drawLabel centre on getStringBounds — fake metrics would
		// poison the golden's coordinates.
		double width = g.getFontMetrics().getStringBounds("counter", g).getWidth();
		assertTrue("real font metrics give a positive width", width > 0);
	}

	@Test
	public void shapeStringificationRoundsToOneDecimal()
	{
		// The mask point for wall-clock-phased args: segment signature stays exact.
		Path2D path = new Path2D.Double();
		path.moveTo(1.234, 5.678);
		path.lineTo(9.999, 0.001);
		path.closePath();
		assertEquals("Path2D(M L Z)", RecordingGraphics2D.shape(path));
	}

	@Test
	public void goldenCompareFailsOnDrift()
	{
		// compareOrRegen must detect a mismatch — a comparator that can't fail
		// is no oracle. Point it at a missing scenario and demand the failure
		// message names it (regen path is exercised end-to-end by the
		// transcript suite's recorded goldens).
		try
		{
			TranscriptGolden.compareOrRegen("definitely-not-a-real-scenario", List.of("x"));
			fail("missing golden must fail");
		}
		catch (AssertionError expected)
		{
			assertTrue(expected.getMessage().contains("definitely-not-a-real-scenario"));
		}
	}
}
