package shortestpath;

import lombok.Getter;
import shortestpath.pathfinder.ActiveSearch;

/**
 * What the plugin last tried to do, for the debug overlay: the last restart attempt and its
 * outcome, which search is current, and the last errors. It records attempts as well as results,
 * so a restart that failed on the client thread is visible rather than leaving a stale route
 * behind silently. Written by the plugin, read when rendering.
 */
@Getter
public final class DebugState
{
	private static final int MAX_MESSAGE_LENGTH = 60;
	public static final String STARTED = "started";

	// Last restart attempt
	private volatile int restartCount;
	private volatile String restartReason;
	private volatile int restartTick = -1;
	private volatile String restartOutcome;

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

	void restartRequested(String reason, int tick)
	{
		restartCount++;
		restartReason = reason;
		restartTick = tick;
		restartOutcome = "pending";
	}

	void restartOutcome(String outcome)
	{
		restartOutcome = outcome;
	}

	void searchCancelled(ActiveSearch cancelled)
	{
		cancelledSearch = cancelled;
	}

	void searchStarted(ActiveSearch started)
	{
		search = started;
		restartOutcome = STARTED;
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
