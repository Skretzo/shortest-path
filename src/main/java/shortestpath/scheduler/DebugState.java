package shortestpath.scheduler;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import shortestpath.pathfinder.ActiveSearch;

/**
 * What the plugin last tried to do, for the debug overlay: the last restart attempt and its
 * outcome, which search is current, and the last errors. It records attempts as well as results,
 * so a restart that failed on the client thread is visible rather than leaving a stale route
 * behind silently. Written by the plugin, read when rendering.
 */
@Getter
@Singleton
public final class DebugState
{
	private static final int MAX_MESSAGE_LENGTH = 60;
	public static final String STARTED = "started";

	/**
	 * One restart attempt: a request plus the outcome it eventually got. Kept as a single
	 * immutable snapshot so the render thread never reads a torn reason/tick/outcome mix.
	 */
	public static final class Restart
	{
		public final int count;
		public final String reason;
		public final int tick;
		public final String outcome;

		private Restart(int count, String reason, int tick, String outcome)
		{
			this.count = count;
			this.reason = reason;
			this.tick = tick;
			this.outcome = outcome;
		}

		private Restart withOutcome(String newOutcome)
		{
			return new Restart(count, reason, tick, newOutcome);
		}
	}

	// Last restart attempt
	private volatile Restart restart;
	private int restartCount;

	// Current search
	private volatile ActiveSearch search;
	private volatile ActiveSearch cancelledSearch;

	// Errors
	private volatile int clientErrorCount;
	private volatile String clientError;
	private volatile int clientErrorTick = -1;
	private volatile int searchErrorCount;
	private volatile String searchError;
	private volatile int searchErrorTick = -1;

	@Inject
	public DebugState()
	{
	}

	void restartRequested(String reason, int tick)
	{
		restart = new Restart(++restartCount, reason, tick, "pending");
	}

	void restartOutcome(String outcome)
	{
		Restart current = restart;
		if (current != null)
		{
			restart = current.withOutcome(outcome);
		}
	}

	void searchCancelled(ActiveSearch cancelled)
	{
		cancelledSearch = cancelled;
	}

	void searchStarted(ActiveSearch started)
	{
		search = started;
		restartOutcome(STARTED);
	}

	void clientError(Throwable error, int tick)
	{
		clientErrorCount++;
		clientError = describe(error);
		clientErrorTick = tick;
	}

	void searchError(Throwable error, int tick)
	{
		searchErrorCount++;
		searchError = describe(error);
		searchErrorTick = tick;
	}

	static String describe(Throwable error)
	{
		String text = error.getClass().getSimpleName()
			+ (error.getMessage() == null ? "" : ": " + error.getMessage());
		return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
	}
}
