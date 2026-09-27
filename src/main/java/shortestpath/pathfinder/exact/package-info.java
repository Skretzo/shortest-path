/**
 * Exact pathfinder core.
 *
 * <p>This package may depend on shared coordinates, collision, transport,
 * result, and prepared-account APIs. It must not depend on the legacy search
 * implementation or RuneLite's mutable client state.</p>
 *
 * <p>Static world data is plugin-lifetime and immutable. Prepared account data
 * is account/config-lifetime and immutable. Target overlays and reverse labels
 * are query-lifetime and immutable. Forward-search state is owned by one
 * active route and is mutable only while that route runs.</p>
 */
package shortestpath.pathfinder.exact;
