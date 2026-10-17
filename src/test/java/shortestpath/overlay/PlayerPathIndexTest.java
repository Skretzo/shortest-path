package shortestpath.overlay;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.PathStep;

public class PlayerPathIndexTest
{
	private static PathStep step(int x, int y, int plane)
	{
		return new PathStep(WorldPointUtil.packWorldPoint(x, y, plane), BankVisitState.CARRIED);
	}

	private static int player(int x, int y, int plane)
	{
		return WorldPointUtil.packWorldPoint(x, y, plane);
	}

	@Test
	public void exactMatchReturnsThatIndex()
	{
		List<PathStep> path = Arrays.asList(
			step(3000, 3000, 0),
			step(3001, 3000, 0),
			step(3002, 3000, 0));
		assertEquals(1, AbstractHighlightOverlay.playerPathIndex(path, player(3001, 3000, 0), 10));
	}

	@Test
	public void exactMatchWinsWithZeroRadius()
	{
		List<PathStep> path = Collections.singletonList(step(3000, 3000, 0));
		assertEquals(0, AbstractHighlightOverlay.playerPathIndex(path, player(3000, 3000, 0), 0));
	}

	@Test
	public void oneTileOffPicksStepWithinRadius()
	{
		List<PathStep> path = Arrays.asList(
			step(3000, 3000, 0),
			step(3005, 3000, 0));
		assertEquals(0, AbstractHighlightOverlay.playerPathIndex(path, player(3001, 3000, 0), 10));
	}

	@Test
	public void pastRadiusWithinMarginStillMatches()
	{
		List<PathStep> path = Collections.singletonList(step(3000, 3000, 0));
		assertEquals(0, AbstractHighlightOverlay.playerPathIndex(path, player(3010, 3000, 0), 10));
		assertEquals(0, AbstractHighlightOverlay.playerPathIndex(path, player(3014, 3000, 0), 10));
	}

	@Test
	public void atRadiusPlusMarginReturnsMinusOne()
	{
		List<PathStep> path = Collections.singletonList(step(3000, 3000, 0));
		assertEquals(-1, AbstractHighlightOverlay.playerPathIndex(path, player(3015, 3000, 0), 10));
	}

	@Test
	public void beyondRadiusPlusMarginReturnsMinusOne()
	{
		List<PathStep> path = Collections.singletonList(step(3000, 3000, 0));
		assertEquals(-1, AbstractHighlightOverlay.playerPathIndex(path, player(3020, 3000, 0), 10));
	}

	@Test
	public void otherPlaneNeverMatches()
	{
		List<PathStep> path = Collections.singletonList(step(3000, 3000, 1));
		assertEquals(-1, AbstractHighlightOverlay.playerPathIndex(path, player(3000, 3000, 0), 10));
		assertEquals(-1, AbstractHighlightOverlay.playerPathIndex(path, player(3000, 3000, 0), -1));
	}

	@Test
	public void negativeRadiusIsUnbounded()
	{
		List<PathStep> path = Arrays.asList(
			step(3000, 3000, 0),
			step(3200, 3000, 0));
		assertEquals(1, AbstractHighlightOverlay.playerPathIndex(path, player(3190, 3000, 0), -1));
	}

	@Test
	public void nearerOfTwoStepsWins()
	{
		List<PathStep> path = Arrays.asList(
			step(3000, 3000, 0),
			step(3003, 3000, 0));
		assertEquals(1, AbstractHighlightOverlay.playerPathIndex(path, player(3002, 3000, 0), 10));
	}

	@Test
	public void equidistantStepsPickEarliest()
	{
		List<PathStep> path = Arrays.asList(
			step(3000, 3000, 0),
			step(3002, 3000, 0));
		assertEquals(0, AbstractHighlightOverlay.playerPathIndex(path, player(3001, 3000, 0), 10));
	}

	@Test
	public void emptyPathReturnsMinusOne()
	{
		assertEquals(-1, AbstractHighlightOverlay.playerPathIndex(
			Collections.<PathStep>emptyList(), player(3000, 3000, 0), 10));
	}
}
