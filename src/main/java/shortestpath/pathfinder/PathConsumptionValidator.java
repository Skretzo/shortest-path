package shortestpath.pathfinder;

import java.util.List;
import shortestpath.transport.Transport;
import shortestpath.requirement.TransportEligibility;

/**
 * Post-search check that a path's recorded transports can actually be paid for in
 * sequence. The search evaluates every edge against one item snapshot, so a
 * transport may be routed that an earlier transport on the same path already
 * consumed the items for. This walk replays the path against a
 * {@link TransportEligibility.ConsumptionLedger} seeded from the same snapshot —
 * bank-visited steps refill the pool — and reports the first transport whose
 * requirements can no longer be met. Validation is deliberately post-search:
 * keeping resource state out of the search graph avoids multiplying the state
 * space, and the walk is a single cheap pass over the finished path.
 * <p>
 * Transports are replayed as they were routed: a transport the search used under
 * a setting-level bypass (e.g. teleportation items set to All) owes nothing and is
 * never flagged, while one the search admitted on items must still be payable after
 * the earlier edges spent theirs.
 */
public final class PathConsumptionValidator
{
	private PathConsumptionValidator()
	{
	}

	/**
	 * The first transport on {@code path} the ledger cannot pay for after earlier
	 * consumption, or {@code null} when the path is fully payable — or when there
	 * is no snapshot or path to validate. Only transports recorded on the path
	 * steps are considered. {@code bankVisitState} is a state flag, not a visit
	 * event — every step after the banking point carries it — so the pool
	 * refills once, on the unbanked-to-banked transition. The refill precedes
	 * the transition step's own edge: the bank-visit node itself is not a path
	 * step, so the first banked step departs after banking and pays from the
	 * refilled pool, while a transport arriving at the bank is still an
	 * unbanked step and pays from the carried pool.
	 */
	public static Transport firstUnpayable(TransportEligibility eligibility, List<PathStep> path)
	{
		if (eligibility == null || path == null || path.isEmpty())
		{
			return null;
		}
		TransportEligibility.ConsumptionLedger ledger = eligibility.consumptionLedger();
		for (int i = 1; i < path.size(); i++)
		{
			PathStep step = path.get(i);
			if (step.getBankVisitState() == BankVisitState.BANKED
				&& path.get(i - 1).getBankVisitState() != BankVisitState.BANKED)
			{
				ledger.visitBank();
			}
			Transport transport = step.getTransport();
			if (transport != null)
			{
				if (!ledger.usableAsRouted(transport))
				{
					return transport;
				}
				ledger.spend(transport);
			}
		}
		return null;
	}
}
