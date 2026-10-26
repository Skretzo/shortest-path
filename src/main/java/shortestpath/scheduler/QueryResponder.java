package shortestpath.scheduler;

import shortestpath.pathfinder.ActiveSearch;

/**
 * How the scheduler talks back to the plugin-message surface: a query finished, a query was
 * refused, or the deferred plugin-message queue should be flushed. The plugin implements this
 * narrow seam; the scheduler never sees the plugin-message payload types itself.
 */
public interface QueryResponder
{
	void postQueryResult(Object id, ActiveSearch query);

	void postQueryFailure(Object id, String reason);

	void postPluginMessages();
}
