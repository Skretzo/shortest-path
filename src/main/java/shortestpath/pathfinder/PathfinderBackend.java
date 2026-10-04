package shortestpath.pathfinder;

/**
 * Which engine computes a route.
 *
 * <p>{@link #EXACT} is opt-in and matches legacy on reachability and legality, but two
 * cost-level divergences are deliberate: transports recorded with a zero duration are
 * taken at that cost, so free hops legacy's ordering never exploited can appear in a
 * route; and legacy's queue-ordering penalty for chaining onto a shared-destination
 * transport (e.g. a one-tick hop that lands on another transport's origin) is not
 * reproduced, so such chains can price differently. Both affect the route's cost, not
 * whether it is valid.
 */
public enum PathfinderBackend
{
	/** The default engine and the reference for behaviour. */
	LEGACY,
	/** Opt-in tile-level search; see the class note for the cost divergences it keeps. */
	EXACT
}
