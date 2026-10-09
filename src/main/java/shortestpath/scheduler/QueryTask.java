package shortestpath.scheduler;

import java.util.concurrent.Future;

/**
 * A query that has been submitted to the executor: the caller's {@code id} plus the task's
 * future, so it can be answered or cancelled when the displayed path or plugin shutdown
 * takes precedence.
 */
class QueryTask
{
	final Object id;
	Future<?> future;

	QueryTask(Object id)
	{
		this.id = id;
	}
}
