package shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchDeadlineTest
{
	private static final long MS = 1_000_000L;

	/** A clock that only moves when told to and counts how often it is read. */
	private static final class FakeClock
	{
		long now = 5_000 * MS;
		int reads;

		long read()
		{
			reads++;
			return now;
		}
	}

	@Test
	public void readsTheClockOncePerCheckInterval()
	{
		FakeClock clock = new FakeClock();
		SearchDeadline deadline = new SearchDeadline(100, clock::read);
		clock.reads = 0;

		for (int i = 0; i < SearchDeadline.CHECK_INTERVAL * 5; i++)
			assertFalse(deadline.expired());

		assertEquals(5, clock.reads);
	}

	@Test
	public void expiresAtTheFirstClockReadAfterTheCutoff()
	{
		FakeClock clock = new FakeClock();
		SearchDeadline deadline = new SearchDeadline(100, clock::read);
		clock.now += 101 * MS;

		for (int i = 1; i < SearchDeadline.CHECK_INTERVAL; i++)
			assertFalse(deadline.expired());

		assertTrue(deadline.expired());
		assertTrue(deadline.expired());
	}

	@Test
	public void doesNotExpireExactlyAtTheCutoff()
	{
		FakeClock clock = new FakeClock();
		SearchDeadline deadline = new SearchDeadline(100, clock::read);
		clock.now += 100 * MS;

		assertFalse(checkInterval(deadline));
		clock.now += 1;
		assertTrue(checkInterval(deadline));
	}

	@Test
	public void progressRestartsTheCountdownAtTheNextClockRead()
	{
		FakeClock clock = new FakeClock();
		SearchDeadline deadline = new SearchDeadline(100, clock::read);
		clock.now += 90 * MS;
		deadline.progressed();

		assertFalse(checkInterval(deadline));
		clock.now += 95 * MS;
		assertFalse(checkInterval(deadline));
		clock.now += 10 * MS;
		assertTrue(checkInterval(deadline));
	}

	@Test
	public void zeroCutoffExpiresAtTheFirstClockRead()
	{
		FakeClock clock = new FakeClock();
		SearchDeadline deadline = new SearchDeadline(0, clock::read);

		assertTrue(checkInterval(deadline));
	}

	/** Runs one full check interval and returns the result of the call that read the clock. */
	private static boolean checkInterval(SearchDeadline deadline)
	{
		for (int i = 1; i < SearchDeadline.CHECK_INTERVAL; i++)
			deadline.expired();
		return deadline.expired();
	}
}
