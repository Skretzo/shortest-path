package shortestpath.settings;

/**
 * The consequences an observed change carries for the plugin shell — whether
 * the change came from a config key (via {@link ConfigChange}) or from a
 * producer observing game state (via the item-state change facts). A change
 * fact carries the set of effects it triggers; the shell decides how to act
 * on them.
 */
public enum Effect
{
	/** The running search can no longer be trusted; pathfinding must restart. */
	ROUTE_INVALIDATING,
	/** The debug overlay panel must be added to or removed from the overlay manager. */
	SIDE_EFFECT_DEBUG_OVERLAY,
	/** The selected pathfinding backend must be prepared (grid/prerender warm-up). */
	SIDE_EFFECT_BACKEND_PREP,
	/** No shell side effect beyond the views republishing; a running search continues. */
	DISPLAY_ONLY,
	/** The eligibility snapshot is stale; the engine rebuilds it lazily. */
	ELIGIBILITY_STALE
}
