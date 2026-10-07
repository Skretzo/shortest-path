package shortestpath.settings;

/**
 * The consequences a change to a config key carries for the plugin shell.
 * A {@link ConfigChange} carries the set of effects its key triggers; the
 * shell decides how to act on them.
 */
public enum Effect
{
	/** The running search can no longer be trusted; pathfinding must restart. */
	ROUTE_INVALIDATING,
	/** The debug overlay panel must be added to or removed from the overlay manager. */
	SIDE_EFFECT_DEBUG_OVERLAY,
	/** The selected pathfinding backend must be prepared (grid/prerender warm-up). */
	SIDE_EFFECT_BACKEND_PREP,
	/** Only display state changed; caches republish without touching the search. */
	DISPLAY_ONLY
}
