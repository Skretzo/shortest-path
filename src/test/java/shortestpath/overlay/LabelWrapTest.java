package shortestpath.overlay;

import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LabelWrapTest
{
	@Test
	public void aLabelTooWideForTheViewportWrapsAtSpaces()
	{
		String line = "Reach 99 Construction, carry Construct. cape to use: Teleportation item Construction cape";

		assertEquals(List.of(line), PathTileOverlay.wrap(line, String::length, line.length()));
		assertEquals(List.of("Reach 99 Construction, carry Construct. cape to use:", "Teleportation item Construction cape"),
			PathTileOverlay.wrap(line, String::length, 55));
		// A word wider than the viewport keeps a line of its own rather than being cut.
		assertEquals(List.of("Carry", "Construct.", "cape"), PathTileOverlay.wrap("Carry Construct. cape", String::length, 6));
	}
}
