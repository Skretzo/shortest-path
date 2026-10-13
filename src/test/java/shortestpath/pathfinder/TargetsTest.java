package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.List;
import java.util.Set;
import org.junit.Test;

public class TargetsTest
{
	private static final int A = 1;
	private static final int B = 2;
	private static final int C = 3;

	@Test
	public void pointsMakeTargets()
	{
		Targets targets = Targets.at(List.of(A, B));
		assertEquals(Set.of(A, B), targets.points());
		assertEquals(Target.at(A), targets.get(A));
		assertSame(Targets.NONE, Targets.at(List.of()));
	}

	@Test
	public void restrictingKeepsTargetsAndAddsNewOnes()
	{
		Targets restricted = Targets.at(List.of(A, B)).restrictTo(List.of(A, C));
		assertEquals(Set.of(A, C), restricted.points());
	}

	@Test
	public void plusAddsTilesNotAlreadyThere()
	{
		assertEquals(Set.of(A, B, C), Targets.at(List.of(A, B)).plus(Targets.at(List.of(B, C))).points());
	}

	@Test
	public void equalTargetsAreEqualWhateverTheirOrder()
	{
		assertEquals(Targets.at(List.of(A, B)), Targets.at(List.of(B, A)));
	}
}
