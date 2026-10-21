package shortestpath.overlay;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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

	// A bank chest at (3000, 3000), with the route's bank step on its south side
	private static final List<PathStep> TO_BANK_SOUTH_SIDE = Arrays.asList(
		step(2995, 2999, 0),
		step(2996, 2999, 0),
		step(2997, 2999, 0),
		step(2998, 2999, 0),
		step(2999, 2999, 0),
		step(3000, 2999, 0));
	private static final Set<Integer> SOUTH_SIDE_BANK = Set.of(player(3000, 2999, 0));

	@Test
	public void bankStepIndexIsTheBankStepWhenStandingOnIt()
	{
		assertEquals(5, AbstractHighlightOverlay.bankStepIndex(TO_BANK_SOUTH_SIDE, player(3000, 2999, 0),
			SOUTH_SIDE_BANK, 10));
	}

	@Test
	public void bankStepIndexFindsTheBankStepFromTheOtherSideOfTheChest()
	{
		// From the north side of the chest. That's off the route, and the nearest route tile isn't the bank step.
		assertEquals(5, AbstractHighlightOverlay.bankStepIndex(TO_BANK_SOUTH_SIDE, player(3000, 3001, 0),
			SOUTH_SIDE_BANK, 10));
	}

	// The Grand Exchange bank tiles from bank.tsv, on the west and east sides
	private static final Set<Integer> GRAND_EXCHANGE_BANK = Set.of(
		player(3162, 3489, 0), player(3162, 3490, 0), player(3167, 3489, 0), player(3167, 3490, 0));

	@Test
	public void bankStepIndexFindsTheBankStepFromTheFarSideOfTheGrandExchange()
	{
		// The route walks up the west side and banks there
		List<PathStep> route = Arrays.asList(
			step(3162, 3482, 0), step(3162, 3483, 0), step(3162, 3484, 0), step(3162, 3485, 0),
			step(3162, 3486, 0), step(3162, 3487, 0), step(3162, 3488, 0), step(3162, 3489, 0));
		// Opened from the east side, two tiles past the east bank tile diagonally
		assertEquals(7, AbstractHighlightOverlay.bankStepIndex(route, player(3169, 3492, 0),
			GRAND_EXCHANGE_BANK, 10));
	}

	@Test
	public void bankStepIndexIsMinusOneWhenTheBankStepIsOutOfReach()
	{
		assertEquals(-1, AbstractHighlightOverlay.bankStepIndex(TO_BANK_SOUTH_SIDE, player(3000, 3030, 0),
			SOUTH_SIDE_BANK, 10));
	}
}
