package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IntDequeTest
{
	@Test
	public void keepsInsertionOrderWhenWrappingGrowingAndReusing()
	{
		IntDeque queue = new IntDeque(2);
		queue.addLast(1);
		queue.addLast(2);
		assertEquals(1, queue.pollFirst());
		queue.addLast(3);
		queue.addLast(4);
		assertEquals(2, queue.pollFirst());
		assertEquals(3, queue.pollFirst());
		assertEquals(4, queue.pollFirst());
		assertTrue(queue.isEmpty());

		queue.addLast(5);
		assertEquals(5, queue.pollFirst());
		assertTrue(queue.isEmpty());
	}
}
