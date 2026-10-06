package shortestpath.requirement;

/**
 * Why a transport was rejected by the requirement gate chain — internal review
 * instrumentation for the pathfinder, not exposed to downstream consumers.
 * Every rejection site in the ordered gate chain reports the constant naming
 * its gate; {@link #NONE} is the acceptance verdict, meaning the transport
 * passed every gate. The constants are returned directly, so the
 * per-transport gate path allocates nothing.
 */
public enum RejectionReason
{
	/**
	 * The transport passed every gate.
	 */
	NONE,

	/**
	 * A teleport while the player is aboard a sailing boat.
	 */
	SAILING,

	/**
	 * A transport with an endpoint inside the player-owned house while POH
	 * routing is disabled.
	 */
	POH_DISABLED,

	/**
	 * A transport touching a league region the player has not unlocked.
	 */
	LEAGUE_REGION,

	/**
	 * A transport of a type disabled in config, including the
	 * quest/varbit-derived {@code disableUnless} toggles.
	 */
	TYPE_DISABLED,

	/**
	 * A POH-variant transport (fairy ring, spirit tree, obelisk or nexus
	 * portal) whose variant is disabled.
	 */
	POH_VARIANT,

	/**
	 * A seasonal-league transport evaluated on a non-seasonal world.
	 */
	SEASONAL_WORLD,

	/**
	 * A transport whose item alternatives are all Deadman-mode-only items,
	 * evaluated on a non-Deadman world.
	 */
	DEADMAN_ITEM,

	/**
	 * A transport that loses every alternative of a required item to the
	 * player's declared per-item restrictions.
	 */
	BLOCKED_ITEM,

	/**
	 * A teleportation-item-family transport excluded by the configured
	 * teleportation-item mode.
	 */
	TELEPORT_MODE,

	/**
	 * A respawn-destination transport for the landing the player has not
	 * declared in config.
	 */
	RESPAWN_DECLARED,

	/**
	 * A transport carrying a pure-unlock item requirement with no declared
	 * branch, or the Honour teleportation box without its unlock.
	 */
	UNLOCK_GATE,

	/**
	 * A teleportation box below the configured jewellery-box tier or with a
	 * disabled mounted item.
	 */
	JEWELLERY_BOX_TIER,

	/**
	 * A transport whose skill requirements exceed the player's boosted levels,
	 * including the total-level, combat and quest-point indices.
	 */
	SKILL_LEVEL,

	/**
	 * A quest-locked transport with a required quest not yet finished.
	 */
	QUEST,

	/**
	 * A transport with a failing varbit requirement.
	 */
	VARBIT,

	/**
	 * A transport with a failing varplayer requirement.
	 */
	VARPLAYER,

	/**
	 * A spirit-tree-family transport bound for or starting from a planted
	 * spirit tree the player cannot use.
	 */
	PLANTED_SPIRIT_TREE,

	/**
	 * A transport whose item requirements are met by neither the carried
	 * pool nor the bank-path pool.
	 */
	ITEM_REQUIREMENT,
	;
}
