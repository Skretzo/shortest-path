package shortestpath.pathfinder;

import java.util.function.LongSupplier;

/**
 * The calculation cutoff as checked from a search loop.
 *
 * <p>Reading the clock can cost more than a whole search step: over a microsecond on Linux when
 * the kernel uses the HPET clock source instead of the TSC. {@link #expired()} is called for
 * every step but only reads the clock on every {@value #CHECK_INTERVAL}th call, so the cutoff
 * fires at most one interval (well under a millisecond of search) late.
 */
final class SearchDeadline
{
	static final int CHECK_INTERVAL = 1024;

	private final LongSupplier nanoClock;
	private final long cutoffNanos;
	private long deadline;
	private int unchecked;
	private boolean progressed;
	private boolean expired;

	/** A deadline {@code cutoffMillis} from now; zero expires at the first clock read. */
	SearchDeadline(long cutoffMillis)
	{
		this(cutoffMillis, System::nanoTime);
	}

	SearchDeadline(long cutoffMillis, LongSupplier nanoClock)
	{
		this.nanoClock = nanoClock;
		this.cutoffNanos = Math.max(0, cutoffMillis) * 1_000_000L;
		this.deadline = nanoClock.getAsLong() + cutoffNanos;
	}

	/**
	 * Restarts the countdown, for cutoffs measured from the last progress rather than from the
	 * start. Takes effect at the next clock read.
	 */
	void progressed()
	{
		progressed = true;
	}

	/** Whether the cutoff has passed, as of the most recent clock read. */
	boolean expired()
	{
		if (expired)
			return true;
		if (++unchecked < CHECK_INTERVAL)
			return false;
		unchecked = 0;
		long now = nanoClock.getAsLong();
		if (progressed)
		{
			progressed = false;
			deadline = now + cutoffNanos;
			return false;
		}
		expired = now - deadline > 0 || cutoffNanos == 0;
		return expired;
	}
}
