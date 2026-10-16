package shortestpath.pathfinder;

import lombok.Getter;

/**
 * Completed-search counters exposed to the plugin's debug overlay.
 */
public class PathfinderStats
{
	@Getter
	int nodesChecked = 0, transportsChecked = 0;
	private long startNanos, endNanos;
	volatile boolean started = false, ended = false;

	public int getTotalNodesChecked()
	{
		return nodesChecked + transportsChecked;
	}

	public long getElapsedTimeNanos()
	{
		return endNanos - startNanos;
	}

	/** Nanoseconds rendered as a compact milliseconds string for diagnostics. */
	public static String millis(long nanos)
	{
		return String.format("%.2fms", nanos / 1_000_000.0);
	}

	void start()
	{
		started = true;
		nodesChecked = 0;
		transportsChecked = 0;
		startNanos = System.nanoTime();
	}

	void end()
	{
		endNanos = System.nanoTime();
		ended = true;
	}
}
