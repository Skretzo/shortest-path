package shortestpath.requirement;

import shortestpath.settings.TeleportationItem;

/**
 * The single source for what a {@link TeleportationItem} mode means: given the
 * configured mode and whether the transport consumes its item, {@link #verdict}
 * answers the mode truth table both the requirement chain
 * ({@link Requirements#check}) and the eligibility snapshot
 * ({@link TransportEligibility#usable}) used to encode as twin switches.
 *
 * <p>Boundary: this class answers ONLY the mode × consumable dispatch (and the
 * bank-path axis — see the class's {@code includesBank}). The gates that run
 * before the mode dispatch — the seasonal-world, Deadman-item, per-item and
 * per-option restriction checks — stay {@code Requirements}-side; they are not
 * mode semantics. The verdict is deliberately NOT a {@link RejectionReason}:
 * deferring to item evaluation is not a rejection, and a defer value in the
 * gate vocabulary would force every {@code switch}/{@code equals} that reads
 * reasons to handle a verdict that can never be returned.
 */
public final class TeleportationItemPolicy
{
	private TeleportationItemPolicy()
	{
	}

	/**
	 * The mode × consumable verdict: what the teleportation-item setting does
	 * to a single item-teleport transport before its item requirements are
	 * consulted.
	 */
	public enum Verdict
	{
		/**
		 * The item pools are never consulted — the mode admits the transport
		 * outright. {@code ALL} and {@code UNLOCKED} bypass every transport;
		 * for {@code UNLOCKED} ownership is implied by the unlock check, so
		 * items are never evaluated. The {@code *_NON_CONSUMABLE} bypass modes
		 * land here for non-consumable transports only.
		 */
		BYPASS,

		/**
		 * Defer to {@link shortestpath.requirement.model.TransportItems#isSatisfiedBy}
		 * pool evaluation — neither a rejection nor a bypass. Callers fall
		 * through to the pool check; they must never treat this verdict as
		 * {@code true}, or the inventory modes would resurrect transports the
		 * pools would reject.
		 */
		EVALUATE_ITEMS,

		/** Evaluate only carried items, even after visiting a bank. */
		EVALUATE_CARRIED_ITEMS,

		/**
		 * The mode refuses this transport outright: {@code NONE} always, and
		 * modes restricted to permanent items on a consumable transport.
		 */
		REJECT
	}

	/**
	 * The verdict for one transport under the configured mode. The truth table
	 * (previously encoded identically in {@code Requirements.teleportationItem}
	 * and {@code TransportEligibility.usable}):
	 * {@code NONE} rejects; {@code ALL} and {@code UNLOCKED} bypass; the
	 * inventory modes evaluate items, with consumables limited to carried
	 * items in the mixed mode. Modes restricted to permanent items reject
	 * consumable transports.
	 */
	public static Verdict verdict(TeleportationItem mode, boolean consumable)
	{
		switch (mode)
		{
			case NONE:
				return Verdict.REJECT;
			case ALL:
			case UNLOCKED:
				return Verdict.BYPASS;
			case ALL_NON_CONSUMABLE:
			case UNLOCKED_NON_CONSUMABLE:
				return consumable ? Verdict.REJECT : Verdict.BYPASS;
			case INVENTORY:
			case INVENTORY_AND_BANK:
				return Verdict.EVALUATE_ITEMS;
			case INVENTORY_ALL_AND_BANK_NON_CONSUMABLE:
				return consumable ? Verdict.EVALUATE_CARRIED_ITEMS : Verdict.EVALUATE_ITEMS;
			case INVENTORY_NON_CONSUMABLE:
			case INVENTORY_AND_BANK_NON_CONSUMABLE:
				return consumable ? Verdict.REJECT : Verdict.EVALUATE_ITEMS;
			default:
				// A mode added to the enum lands here until a row is added
				// above — pool evaluation is the fail-closed default, and the
				// mode-matrix completeness guard catches the missing cell.
				return Verdict.EVALUATE_ITEMS;
		}
	}

	/**
	 * Whether the mode's bank-path pool includes the bank contents — the
	 * second axis of mode semantics, single-sourced with the verdict table
	 * above it. Modes supporting banked items collect the bank into the pool
	 * banked transports are evaluated against.
	 */
	public static boolean includesBank(TeleportationItem mode)
	{
		return TeleportationItem.INVENTORY_AND_BANK.equals(mode)
			|| TeleportationItem.INVENTORY_AND_BANK_NON_CONSUMABLE.equals(mode)
			|| TeleportationItem.INVENTORY_ALL_AND_BANK_NON_CONSUMABLE.equals(mode);
	}
}
