package shortestpath;

/**
 * Why a teleport method can or cannot be used right now, for the alternative-routes panel's per-method
 * status marker. Computed against live game state during the client-thread pathfinder refresh, so the
 * panel can explain (missing item, in the bank, missing level/quest, not unlocked) why an entry is
 * greyed out — in every mode, not just the possession-bypassing "All" modes.
 */
public enum MethodAvailability
{
	/** Usable right now from the inventory/equipment (all unlocks met). */
	AVAILABLE,
	/** Owned, but the required item is in the bank — withdraw it or use an "Inventory + bank" mode. */
	IN_BANK,
	/** Unlocked, but the required item isn't in the inventory, equipment or bank. */
	MISSING_ITEM,
	/** A skill level requirement isn't met. */
	MISSING_LEVEL,
	/** A quest requirement isn't met. */
	MISSING_QUEST,
	/** An unlock (diary, minigame, purchase, setting, ...) isn't met. */
	LOCKED;

	/**
	 * The more-available of two statuses (lower ordinal = more available). Used to collapse the several
	 * transports that can share one method identity down to a single, best-case status.
	 */
	public MethodAvailability best(MethodAvailability other)
	{
		if (other == null)
		{
			return this;
		}
		return ordinal() <= other.ordinal() ? this : other;
	}
}
