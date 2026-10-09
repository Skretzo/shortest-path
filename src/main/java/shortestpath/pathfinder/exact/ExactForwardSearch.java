package shortestpath.pathfinder.exact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.WildernessChecker;
import shortestpath.transport.Transport;

/** Correctness-first forward search over tiles, capability hubs, and bank layers. */
public final class ExactForwardSearch
{
	/** A {@code progressed} callback for a search whose caller does not track progress. */
	public static final Runnable NO_PROGRESS = ExactForwardSearch::ignoreProgress;

	private ExactForwardSearch()
	{
	}

	private static void ignoreProgress()
	{
	}

	public static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start)
	{
		return search(target, heuristic, start, () -> false, true, 1);
	}

	public static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start,
		BooleanSupplier cancelled)
	{
		return search(target, heuristic, start, cancelled, true, 1);
	}

	public static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start,
		BooleanSupplier cancelled, double heuristicWeight)
	{
		return search(target, heuristic, start, cancelled, true, heuristicWeight);
	}

	static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start, boolean optimized)
	{
		return search(target, heuristic, start, () -> false, optimized, 1);
	}

	static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start,
		BooleanSupplier cancelled, boolean optimized, double heuristicWeight)
	{
		return search(target, heuristic, start, cancelled, optimized, heuristicWeight,
			SearchRestrictions.none(), NO_PROGRESS);
	}

	/**
	 * {@code restrictions} applies the same positional gates as legacy's per-edge checks: the
	 * caller decides them per search, while the prepared graph and heuristic stay unchanged —
	 * gating only removes edges, so the heuristic can only under-estimate the distances left.
	 */
	static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start,
		BooleanSupplier cancelled, boolean optimized, double heuristicWeight, SearchRestrictions restrictions)
	{
		return search(target, heuristic, start, cancelled, optimized, heuristicWeight, restrictions,
			NO_PROGRESS);
	}

	/**
	 * {@code progressed} runs whenever the closest-reached-tile record improves, the same
	 * moment legacy reports progress for its without-progress cutoff.
	 */
	static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start,
		BooleanSupplier cancelled, boolean optimized, double heuristicWeight, SearchRestrictions restrictions,
		Runnable progressed)
	{
		if (target == null || heuristic == null || cancelled == null || restrictions == null || progressed == null)
			throw new NullPointerException();
		if (heuristic.overlay() != target) throw new IllegalArgumentException("heuristic belongs to another target overlay");
		validateHeuristicWeight(heuristicWeight);
		SearchSpace space = SearchSpace.create(target, start, restrictions);
		TeleportCapability capability = capabilityAt(start);
		int tileStates = space.tileCount * 2;
		// From a start with every global castable they are all seeded there, since casting one later
		// costs the same; elsewhere each capability gets a hub per bank layer, opened the first time
		// the search reaches a tile with that capability.
		int stateCount = tileStates + (capability == TeleportCapability.ALL ? 0 : HUB_STATES);
		// These arrays span every reachable tile, so allocating them is a fixed cost on every query:
		// about 1 ms for the 1.4M reachable tiles, measured on a Ryzen 9 7900. If the reachable set
		// grows a lot (Sailing would add about 4M sea tiles), reuse them across searches, using a
		// per-search stamp in place of the fill.
		int[] best = new int[stateCount]; Arrays.fill(best, ExactCosts.INF);
		int[] previous = new int[stateCount];
		TransportWins transportWins = new TransportWins();
		boolean restrictedHeuristic = capability != TeleportCapability.ALL && hasGlobals(target.account());
		MutableCounters counters = new MutableCounters(capability, !restrictedHeuristic);
		int[] bestBankCost = {ExactCosts.INF};
		int[] globalBounds = restrictedHeuristic ? globalBounds(target, heuristic, space) : null;
		ExactRoute startPath = ExactRoute.of(List.of(new PathStep(start, BankVisitState.CARRIED)), new int[1]);
		Closest closest = new Closest(target);
		if (cancelled.getAsBoolean()) return Result.cancelled(counters.snapshot(bestBankCost[0]), startPath);

		ExactMinHeap queue = new ExactMinHeap(Math.min(stateCount, 32_768));
		int startState = space.state(start, BankVisitState.CARRIED);
		updateBestBank(space, startState, 0, optimized, bestBankCost, counters);
		int startH = effectiveHeuristic(heuristic, space, startState, 0, best, restrictedHeuristic, globalBounds,
			optimized, bestBankCost[0], counters);
		if (startH != ExactCosts.INF)
		{
			best[startState] = 0;
			push(queue, startState, 0, priority(0, startH, heuristicWeight), counters, true, PUSH_INITIAL);
		}
		else
		{
			counters.heuristicUnreachable++;
		}
		if (capability != TeleportCapability.ALL)
		{
			int hub = hub(tileStates, capability, BankVisitState.CARRIED);
			best[hub] = 0;
			push(queue, hub, 0, 0, counters, true, PUSH_GLOBAL);
		}
		seedGlobals(target, capability, best, previous, transportWins, queue, counters, startState, space,
			restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);

		while (queue.poll())
		{
			if (cancelled.getAsBoolean())
				return Result.cancelled(counters.snapshot(bestBankCost[0]), startPath,
					closest.path(space, startState, previous, transportWins, best, startPath), closest.cost());
			int state = queue.state(), cost = queue.cost(), queuedPriority = queue.priority();
			if (cost != best[state])
	{ counters.staleEntries++; continue;
	}
			if (state >= tileStates)
			{
				relaxGlobalHub(target, space, state, tileStates, cost, best, previous, transportWins, queue, counters);
				continue;
			}
			int node = state / 2;
			BankVisitState banked = (state & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
			int tile = space.tile(node);
			if (capability != TeleportCapability.ALL)
				activateGlobal(target.account(), tile, state, banked, tileStates, cost, best, previous, transportWins, queue, counters);
			int currentH = effectiveHeuristic(heuristic, space, state, cost, best, restrictedHeuristic, globalBounds,
				optimized, bestBankCost[0], counters);
			if (currentH == ExactCosts.INF) continue;
			int currentPriority = priority(cost, currentH, heuristicWeight);
			if (currentPriority > queuedPriority)
			{
				push(queue, state, cost, currentPriority, counters, false, PUSH_REKEY);
				counters.rekeys++;
				continue;
			}
			counters.statesPopped++;
			if (target.isTarget(tile))
				return Result.reached(cost, state, counters.snapshot(bestBankCost[0]), reconstruct(space, startState, state, previous, transportWins, best));
			if (closest.consider(state, tile, cost))
				progressed.run();
			if (space.isBase(node))
			{
				walkBase(space, node, banked, state, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
				addBlockedOrigins(target, space, tile, banked, state, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
			}
			else
			{
				for (int next : target.collision().ordinaryWalkingNeighbors(tile))
					relaxWalking(space, next, banked, state, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
				addBlockedOrigins(target, space, tile, banked, state, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
			}
			relaxBank(target, space, node, banked, state, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, capability, bestBankCost);
			relaxLocalTransports(target, space, tile, banked, state, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		}
		return Result.unreachable(counters.snapshot(bestBankCost[0]), startPath,
			closest.path(space, startState, previous, transportWins, best, startPath), closest.cost());
	}

	/**
	 * The popped tile nearest to any target, chosen like the legacy pathfinder's closest reachable
	 * tile: minimum squared Euclidean distance, then travelled cost, then x, then y.
	 *
	 * <p>Only popped states compete: a state improved by a rekey after its last pop never wins
	 * the tie-break, which can shift which partial path a cut-off search returns.
	 */
	/**
	 * The transport behind each state a transport edge last improved, keyed by state. Only those
	 * few states have an entry, so a search no longer pays for one slot per reachable tile. An
	 * entry records the cost it set; every later improvement of the state lowers {@code best},
	 * so an entry still applies exactly when its cost equals the state's best cost, and walking
	 * improvements need not touch the map.
	 */
	private static final class TransportWins
	{
		private final PrimitiveIntHashMap<Win> wins = new PrimitiveIntHashMap<>(64);

		void record(int state, Transport transport, int cost)
		{
			if (transport == null) return;
			Win win = wins.get(state);
			if (win == null) wins.put(state, new Win(transport, cost));
			else
			{
				win.transport = transport;
				win.cost = cost;
			}
		}

		/** The transport that reached {@code state} at {@code bestCost}, or null for any other step. */
		Transport at(int state, int bestCost)
		{
			Win win = wins.get(state);
			return win != null && win.cost == bestCost ? win.transport : null;
		}

		private static final class Win
		{
			Transport transport;
			int cost;

			Win(Transport transport, int cost)
			{
				this.transport = transport;
				this.cost = cost;
			}
		}
	}

	private static final class Closest
	{
		private final TargetOverlay target;
		private int state = -1, cost = ExactCosts.INF, x, y;
		private int distance = Integer.MAX_VALUE;

		Closest(TargetOverlay target)
		{
			this.target = target;
		}

		boolean consider(int state, int tile, int cost)
		{
			int remaining = Integer.MAX_VALUE;
			for (int i = 0; i < target.targetCount(); i++)
				remaining = Math.min(remaining, WorldPointUtil.distanceBetween(target.packedTarget(i), tile,
					WorldPointUtil.EUCLIDEAN_SQUARED_DISTANCE_METRIC));
			int tileX = WorldPointUtil.unpackWorldX(tile), tileY = WorldPointUtil.unpackWorldY(tile);
			if (remaining < distance
				|| (remaining == distance && cost < this.cost)
				|| (remaining == distance && cost == this.cost && tileX < x)
				|| (remaining == distance && cost == this.cost && tileX == x && tileY < y))
			{
				this.state = state;
				this.cost = cost;
				this.distance = remaining;
				this.x = tileX;
				this.y = tileY;
				return true;
			}
			return false;
		}

		ExactRoute path(SearchSpace space, int startState, int[] previous, TransportWins transportWins, int[] best, ExactRoute startPath)
		{
			return state < 0 ? startPath : reconstruct(space, startState, state, previous, transportWins, best);
		}

		int cost()
		{
			return state < 0 ? 0 : cost;
		}
	}

	private static void walkBase(SearchSpace space, int node, BankVisitState banked, int from, int cost, int[] best,
		int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic, int[] globalBounds,
		boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		int mask = space.stat.walkingMask(node);
		emit(space, banked, from, mask, 6, node - 1, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 2, node + 1, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 4, space.stat.southNode(node), cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 0, space.stat.northNode(node), cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		int south = space.stat.southNode(node), north = space.stat.northNode(node);
		emit(space, banked, from, mask, 5, south < 0 ? -1 : south - 1, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 3, south < 0 ? -1 : south + 1, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 7, north < 0 ? -1 : north - 1, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 1, north < 0 ? -1 : north + 1, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
	}

	private static void emit(SearchSpace space, BankVisitState banked, int from, int mask, int bit, int next, int cost,
		int[] best, int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		if ((mask & (1 << bit)) != 0 && next >= 0 && next < space.baseCount && space.stepAllowed(from / 2, next))
			relaxState(space, from, stateForNode(next, banked), cost, 1, null, best, previous, transportWins, queue, counters,
				restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_WALKING);
	}

	private static void addBlockedOrigins(TargetOverlay target, SearchSpace space, int tile, BankVisitState banked, int from, int cost,
		int[] best, int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		CollisionMap collision = target.collision();
		if (collision.isBlocked(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile), WorldPointUtil.unpackWorldPlane(tile))) return;
		int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile), plane = WorldPointUtil.unpackWorldPlane(tile);
		int[] cardinal = {WorldPointUtil.packWorldPoint(x - 1, y, plane), WorldPointUtil.packWorldPoint(x + 1, y, plane), WorldPointUtil.packWorldPoint(x, y - 1, plane), WorldPointUtil.packWorldPoint(x, y + 1, plane)};
		for (int next : cardinal)
			if (collision.isBlocked(WorldPointUtil.unpackWorldX(next), WorldPointUtil.unpackWorldY(next), WorldPointUtil.unpackWorldPlane(next)) && space.hasLocalOrigin(next, banked))
				relaxWalking(space, next, banked, from, cost, best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
	}

	private static void relaxWalking(SearchSpace space, int tile, BankVisitState banked, int from, int cost,
		int[] best, int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		int node = space.node(tile);
		if (node >= 0 && space.stepAllowed(from / 2, node))
			relaxState(space, from, stateForNode(node, banked), cost, 1, null, best, previous, transportWins, queue, counters,
				restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_WALKING);
	}

	private static void relaxBank(TargetOverlay target, SearchSpace space, int node, BankVisitState banked, int from, int cost,
		int[] best, int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight,
		TeleportCapability capability, int[] bestBankCost)
	{
		if (banked == BankVisitState.BANKED || !target.account().bankPathEnabled() || !space.isUsableBankNode(node)) return;
		// Every bank charges the same visit cost, so comparing arrival costs with bestBankCost
		// (an arrival cost too) still orders the banks correctly.
		int bankedCost = ExactCosts.add(cost, target.account().bankVisitCost());
		if (bankedCost == ExactCosts.INF) return;
		relaxState(space, from, stateForNode(node, BankVisitState.BANKED), bankedCost, 0, null, best, previous, transportWins, queue, counters,
			restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_BANKING);
		// With hubs (a start without every global castable) the banked hub of this tile's
		// capability casts them. Without, they are cast here, as far as this bank tile's own
		// wilderness level allows.
		if (capability == TeleportCapability.ALL)
		{
			TeleportCapability here = capabilityAt(space.tile(node));
			for (int i = 0; i < target.account().globalCount(here, BankVisitState.BANKED); i++)
			{
				int destination = target.account().globalDestination(here, BankVisitState.BANKED, i);
				if (!space.globalAllowed(here, destination)) continue;
				if (!optimized || cost <= bestBankCost[0])
					relaxTransport(space, from, BankVisitState.BANKED, bankedCost, destination, target.account().globalCost(here, BankVisitState.BANKED, i), target.account().globalTransport(here, BankVisitState.BANKED, i), best, previous, transportWins, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_GLOBAL);
				else if (space.node(destination) >= 0)
					counters.bankGlobalSuppressed++;
			}
		}
	}

	private static void relaxLocalTransports(TargetOverlay target, SearchSpace space, int tile, BankVisitState banked, int from, int cost,
		int[] best, int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		PreparedRoutingAccount.View view = target.account().localView(banked);
		int start = lowerBound(view.origins, tile);
		for (int i = start; i < view.count && view.origins[i] == tile; i++)
		{
			int node = space.node(view.destinations[i]);
			// Legacy checks the positional gates on a transport's destination node exactly like a
			// walked neighbour (crossing the wilderness ditch is a transport, and must not bypass
			// the avoid-wilderness gate), so apply them to the jump's landing tile.
			if (node < 0 || !space.stepAllowed(from / 2, node)) continue;
			relaxTransport(space, from, banked, cost, view.destinations[i], view.costs[i], view.transports[i], best, previous, transportWins, queue,
				counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_LOCAL);
		}
	}

	private static void relaxTransport(SearchSpace space, int from, BankVisitState banked, int cost, int destination, int stepCost,
		Transport transport, int[] best, int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters,
		boolean restrictedHeuristic, int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight,
		int[] bestBankCost, int pushKind)
	{
		counters.transportCandidates++;
		int node = space.node(destination);
		if (node >= 0) relaxState(space, from, stateForNode(node, banked), cost, stepCost, transport, best, previous, transportWins, queue,
			counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, pushKind);
	}

	private static void relaxState(SearchSpace space, int from, int state, int cost, int stepCost, Transport transport, int[] best,
		int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic, int[] globalBounds,
		boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost, int pushKind)
	{
		int candidate = ExactCosts.add(cost, stepCost);
		if (candidate == ExactCosts.INF) return;
		if (pushKind == PUSH_WALKING) counters.walkingRelaxations++; else counters.transportRelaxations++;
		if (candidate >= best[state]) return;
		updateBestBank(space, state, candidate, optimized, bestBankCost, counters);
		int h = effectiveHeuristic(heuristic, space, state, candidate, best, restrictedHeuristic, globalBounds,
			optimized, bestBankCost[0], counters);
		if (h == ExactCosts.INF)
	{ counters.heuristicUnreachable++; return;
	}
		boolean newState = best[state] == ExactCosts.INF;
		best[state] = candidate; previous[state] = from; transportWins.record(state, transport, candidate);
		push(queue, state, candidate, priority(candidate, h, heuristicWeight), counters, newState, pushKind);
		if (pushKind == PUSH_LOCAL || pushKind == PUSH_GLOBAL) counters.successfulTransportRelaxations++;
	}

	private static void seedGlobals(TargetOverlay target, TeleportCapability capability, int[] best, int[] previous, TransportWins transportWins,
		ExactMinHeap queue, MutableCounters counters, int startState, SearchSpace space, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		int count = target.account().globalCount(capability, BankVisitState.CARRIED);
		for (int i = 0; i < count; i++)
		{
			counters.transportCandidates++;
			int destination = target.account().globalDestination(capability, BankVisitState.CARRIED, i);
			// This duplicates the start-capability hub: the hub offers these transports again when
			// it pops, but seeding them here starts their states without a hub round-trip.
			if (!space.globalAllowed(capability, destination)) continue;
			int node = space.node(destination);
			if (node < 0) continue;
			int state = stateForNode(node, BankVisitState.CARRIED);
			int cost = target.account().globalCost(capability, BankVisitState.CARRIED, i);
			if (cost < best[state])
			{
				updateBestBank(space, state, cost, optimized, bestBankCost, counters);
				int h = effectiveHeuristic(heuristic, space, state, cost, best, restrictedHeuristic, globalBounds,
					optimized, bestBankCost[0], counters);
				if (h == ExactCosts.INF)
				{
					counters.heuristicUnreachable++;
					continue;
				}
				best[state] = cost; previous[state] = startState;
				transportWins.record(state, target.account().globalTransport(capability, BankVisitState.CARRIED, i), cost);
				push(queue, state, cost, priority(cost, h, heuristicWeight), counters, true, PUSH_GLOBAL);
				counters.successfulTransportRelaxations++;
		}
		}
	}

	private static void activateGlobal(PreparedRoutingAccount account, int tile, int from, BankVisitState banked,
		int tileStates, int cost, int[] best, int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters)
	{
		TeleportCapability capability = capabilityAt(tile);
		if (account.globalCount(capability, banked) == 0) return;
		int state = hub(tileStates, capability, banked);
		if (cost < best[state])
		{
			boolean newState = best[state] == ExactCosts.INF;
			best[state] = cost; previous[state] = from;
			push(queue, state, cost, cost, counters, newState, PUSH_GLOBAL);
	}
	}

	private static void relaxGlobalHub(TargetOverlay target, SearchSpace space, int state, int tileStates, int cost,
		int[] best, int[] previous, TransportWins transportWins, ExactMinHeap queue, MutableCounters counters)
	{
		TeleportCapability capability = hubCapability(tileStates, state);
		BankVisitState banked = ((state - tileStates) & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
		// A hub stands for every tile in its wilderness band, so the capability doubles as the
		// source position legacy checks a global teleport against.
		int count = target.account().globalCount(capability, banked);
		for (int i = 0; i < count; i++)
		{
			counters.transportCandidates++;
			int destination = target.account().globalDestination(capability, banked, i);
			if (!space.globalAllowed(capability, destination)) continue;
			int node = space.node(destination);
			if (node < 0) continue;
			int stepCost = target.account().globalCost(capability, banked, i);
			int next = stateForNode(node, banked), candidate = ExactCosts.add(cost, stepCost);
			if (candidate == ExactCosts.INF) continue;
			counters.transportRelaxations++;
			if (candidate >= best[next]) continue;
			boolean newState = best[next] == ExactCosts.INF;
			best[next] = candidate; previous[next] = state;
			transportWins.record(next, target.account().globalTransport(capability, banked, i), candidate);
			push(queue, next, candidate, candidate, counters, newState, PUSH_GLOBAL);
			counters.successfulTransportRelaxations++;
		}
	}

	private static ExactRoute reconstruct(SearchSpace space, int start, int terminal, int[] previous, TransportWins transportWins, int[] best)
	{
		int tileStates = space.tileCount * 2;
		ArrayList<PathStep> result = new ArrayList<>();
		int[] costs = new int[16];
		byte[] arrivals = new byte[16];
		int count = 0;
		for (int state = terminal; ; state = previous[state])
		{
			if (state >= tileStates) continue;
			if (count == costs.length)
			{
				costs = Arrays.copyOf(costs, count * 2);
				arrivals = Arrays.copyOf(arrivals, count * 2);
			}
			result.add(new PathStep(space.tile(state / 2),
				(state & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED,
				transportWins.at(state, best[state])));
			costs[count] = state == start ? 0 : best[state];
			int from = state == start ? start : previous[state];
			arrivals[count++] = from < tileStates ? ExactRoute.FROM_STEP
				: ExactRoute.fromHub(hubCapability(tileStates, from));
			if (state == start) break;
		}
		java.util.Collections.reverse(result);
		int[] forwardCosts = new int[count];
		byte[] forwardArrivals = new byte[count];
		for (int i = 0; i < count; i++)
		{
			forwardCosts[i] = costs[count - 1 - i];
			forwardArrivals[i] = arrivals[count - 1 - i];
		}
		return new ExactRoute(result, forwardArrivals, forwardCosts);
	}

	private static int heuristic(PreparedHeuristic heuristic, SearchSpace space, int state)
	{
		int node = state / 2;
		BankVisitState banked = (state & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
		return space.isBase(node)
			? heuristic.estimateBaseNode(space.tile(node), banked, space.stat.routingComponent(node))
			: heuristic.estimate(space.tile(node), banked, space.components(node));
	}

	static int priority(int cost, int heuristic, double weight)
	{
		if (heuristic == ExactCosts.INF) return ExactCosts.INF;
		long weighted = Math.round(weight * heuristic);
		return weighted >= ExactCosts.INF - cost ? ExactCosts.INF - 1 : cost + (int) weighted;
	}

	static void validateHeuristicWeight(double weight)
	{
		if (!(weight > 0) || !Double.isFinite(weight))
			throw new IllegalArgumentException("heuristic weight must be positive and finite");
	}

	private static int effectiveHeuristic(PreparedHeuristic heuristic, SearchSpace space, int state, int cost,
		int[] best, boolean restrictedHeuristic, int[] globalBounds, boolean optimized, int bestBankCost,
		MutableCounters counters)
	{
		BankVisitState banked = (state & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
		int resolved;
		if (banked == BankVisitState.BANKED)
		{
			counters.heuristicEvaluations++;
			resolved = heuristic(heuristic, space, state);
		}
		else
		{
			int unbanked = heuristic(heuristic, space, state);
			counters.heuristicEvaluations++;
			if (!optimized || !space.bankGlobalRelevant() || cost <= bestBankCost)
				resolved = unbanked;
			else
			{
				int bankedHeuristic = heuristic(heuristic, space, state | 1);
				counters.heuristicEvaluations++;
				counters.bankDominated++;
				resolved = bankedHeuristic == ExactCosts.INF ? unbanked : Math.max(unbanked, bankedHeuristic);
			}
		}
		boolean allGlobalsActivated = restrictedHeuristic
			&& best[hub(space.tileCount * 2, TeleportCapability.ALL, banked)] <= cost;
		if (restrictedHeuristic && (capabilityAt(space.tile(state / 2)) != TeleportCapability.ALL
			|| !allGlobalsActivated))
		{
			counters.restrictedHeuristicStates++;
			int restricted = Math.min(resolved, globalBounds[banked == BankVisitState.BANKED ? 1 : 0]);
			if (restricted == ExactCosts.INF)
			{
				counters.restrictedHeuristicZeroes++;
				return 0;
			}
			return restricted;
		}
		counters.normalHeuristicStates++;
		return resolved;
	}

	private static void updateBestBank(SearchSpace space, int state, int cost, boolean optimized,
		int[] bestBankCost, MutableCounters counters)
	{
		if (optimized && (state & 1) == 0 && space.bankGlobalRelevant() && cost < bestBankCost[0]
			&& space.isUsableBankNode(state / 2))
		{
			bestBankCost[0] = cost;
			counters.bestBankUpdates++;
		}
	}

	private static void push(ExactMinHeap queue, int state, int cost, int priority, MutableCounters counters,
		boolean uniqueState, int kind)
	{
		if (priority == ExactCosts.INF) return;
		queue.push(priority, cost, state);
		counters.pqPushes++;
		if (kind == PUSH_WALKING) counters.walkingPqPushes++;
		else if (kind == PUSH_LOCAL) counters.localTransportPqPushes++;
		else if (kind == PUSH_GLOBAL) counters.globalPqPushes++;
		else if (kind == PUSH_BANKING) counters.bankingPqPushes++;
		if (queue.size() > counters.maxQueueSize) counters.maxQueueSize = queue.size();
		if (uniqueState) counters.uniqueStatesReached++;
	}

	static int stateForNode(int node, BankVisitState banked)
	{
		return SiteGraph.stateId(node, banked);
	}

	private static boolean hasGlobals(PreparedRoutingAccount account)
	{
		// Every capability's globals are among ALL's.
		return account.allowTransports() && (account.globalCount(BankVisitState.CARRIED) != 0
			|| account.globalCount(BankVisitState.BANKED) != 0);
	}

	private static int[] globalBounds(TargetOverlay target, PreparedHeuristic heuristic, SearchSpace space)
	{
		int[] result = {ExactCosts.INF, ExactCosts.INF};
		for (int layer = 0; layer < 2; layer++)
		{
			BankVisitState banked = layer != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
			for (int i = 0; i < target.account().globalCount(banked); i++)
			{
				int node = space.node(target.account().globalDestination(banked, i));
				if (node >= 0)
					result[layer] = Math.min(result[layer], ExactCosts.add(target.account().globalCost(banked, i),
						heuristic(heuristic, space, stateForNode(node, banked))));
			}
		}
		return result;
	}

	private static final int PUSH_INITIAL = 0;
	private static final int PUSH_WALKING = 1;
	private static final int PUSH_LOCAL = 2;
	private static final int PUSH_GLOBAL = 3;
	private static final int PUSH_BANKING = 4;
	private static final int PUSH_REKEY = 5;

	private static TeleportCapability capabilityAt(int tile)
	{
		return TeleportCapability.at(tile);
	}
	private static final TeleportCapability[] CAPABILITIES = TeleportCapability.values();
	private static final int HUB_STATES = CAPABILITIES.length * 2;

	/** The hub state casting the globals {@code capability} allows, in one bank layer. */
	private static int hub(int tileStates, TeleportCapability capability, BankVisitState banked)
	{
		return tileStates + capability.ordinal() * 2 + (banked == BankVisitState.BANKED ? 1 : 0);
	}
	private static TeleportCapability hubCapability(int tileStates, int hubState)
	{
		return CAPABILITIES[(hubState - tileStates) / 2];
	}
	private static int lowerBound(int[] values, int target)
	{
		int low = 0, high = values.length;
		while (low < high)
	{ int middle = low + (high - low) / 2; if (Integer.compareUnsigned(values[middle], target) < 0) low = middle + 1; else high = middle;
	}
		return low;
	}

	public static final class Result
	{
		private final boolean reached, cancelled;
		private final int cost, terminalState, closestCost;
		private final Counters counters;
		private final ExactRoute path, closestPath;
		private Result(boolean reached, boolean cancelled, int cost, int terminalState, Counters counters, ExactRoute path,
			ExactRoute closestPath, int closestCost)
		{
			this.reached = reached; this.cancelled = cancelled; this.cost = cost; this.terminalState = terminalState; this.counters = counters; this.path = path;
			this.closestPath = closestPath; this.closestCost = closestCost;
		}
		static Result reached(int cost, int state, Counters counters, ExactRoute path)
	{ return new Result(true, false, cost, state, counters, path, path, cost);
	}
		static Result unreachable(Counters counters, ExactRoute path, ExactRoute closestPath, int closestCost)
	{ return new Result(false, false, ExactCosts.INF, -1, counters, path, closestPath, closestCost);
	}
		static Result cancelled(Counters counters, ExactRoute path)
	{ return cancelled(counters, path, path, 0);
	}
		static Result cancelled(Counters counters, ExactRoute path, ExactRoute closestPath, int closestCost)
	{ return new Result(false, true, ExactCosts.INF, -1, counters, path, closestPath, closestCost);
	}
		public boolean reached()
	{ return reached;
	}
		public boolean cancelled()
	{ return cancelled;
	}
		public int cost()
	{ return cost;
	}
		public int terminalState()
	{ return terminalState;
	}
		public Counters counters()
	{ return counters;
	}
		public List<PathStep> path()
	{ return path.steps();
	}
		/** {@link #path()} with the search's knowledge of how each step was reached. */
		public ExactRoute route()
	{ return path;
	}
		/** Route to the popped tile nearest a target; the full route when a target was reached. */
		public List<PathStep> closestPath()
	{ return closestPath.steps();
	}
		/** {@link #closestPath()} with the search's knowledge of how each step was reached. */
		public ExactRoute closestRoute()
	{ return closestPath;
	}
		/** Cost of {@link #closestPath()}. */
		public int closestCost()
	{ return closestCost;
	}
	}

	public static final class Counters
	{
		private final int statesPopped, staleEntries, pqPushes, uniqueStatesReached, walkingRelaxations, transportRelaxations,
			heuristicEvaluations, heuristicUnreachable, bestBankUpdates, bankDominated, bankGlobalSuppressed, rekeys,
			finalBestBankCost, maxQueueSize, walkingPqPushes, localTransportPqPushes, globalPqPushes,
			bankingPqPushes, transportCandidates, successfulTransportRelaxations, restrictedHeuristicStates,
			normalHeuristicStates, restrictedHeuristicZeroes;
		private final String initialCapability;
		private final boolean normalHeuristicEnabledAtStart;
		private Counters(int statesPopped, int staleEntries, int pqPushes, int uniqueStatesReached, int walkingRelaxations,
			int transportRelaxations, int heuristicEvaluations, int heuristicUnreachable, int bestBankUpdates,
			int bankDominated, int bankGlobalSuppressed, int rekeys, int finalBestBankCost, int maxQueueSize,
			int walkingPqPushes, int localTransportPqPushes, int globalPqPushes, int bankingPqPushes,
			int transportCandidates, int successfulTransportRelaxations, int restrictedHeuristicStates,
			int normalHeuristicStates, int restrictedHeuristicZeroes, String initialCapability,
			boolean normalHeuristicEnabledAtStart)
		{
			this.statesPopped = statesPopped; this.staleEntries = staleEntries; this.pqPushes = pqPushes; this.uniqueStatesReached = uniqueStatesReached; this.walkingRelaxations = walkingRelaxations; this.transportRelaxations = transportRelaxations; this.heuristicEvaluations = heuristicEvaluations; this.heuristicUnreachable = heuristicUnreachable; this.bestBankUpdates = bestBankUpdates; this.bankDominated = bankDominated; this.bankGlobalSuppressed = bankGlobalSuppressed; this.rekeys = rekeys; this.finalBestBankCost = finalBestBankCost;
			this.maxQueueSize = maxQueueSize; this.walkingPqPushes = walkingPqPushes;
			this.localTransportPqPushes = localTransportPqPushes; this.globalPqPushes = globalPqPushes;
			this.bankingPqPushes = bankingPqPushes; this.transportCandidates = transportCandidates;
			this.successfulTransportRelaxations = successfulTransportRelaxations;
			this.restrictedHeuristicStates = restrictedHeuristicStates;
			this.normalHeuristicStates = normalHeuristicStates;
			this.restrictedHeuristicZeroes = restrictedHeuristicZeroes; this.initialCapability = initialCapability;
			this.normalHeuristicEnabledAtStart = normalHeuristicEnabledAtStart;
		}
		public static Counters empty()
		{ return new Counters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ExactCosts.INF,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "UNKNOWN", false);
		}
		public Counters plus(Counters other)
		{
			if (other == null) throw new NullPointerException();
			return new Counters(statesPopped + other.statesPopped, staleEntries + other.staleEntries,
				pqPushes + other.pqPushes, uniqueStatesReached + other.uniqueStatesReached,
				walkingRelaxations + other.walkingRelaxations, transportRelaxations + other.transportRelaxations,
				heuristicEvaluations + other.heuristicEvaluations, heuristicUnreachable + other.heuristicUnreachable,
				bestBankUpdates + other.bestBankUpdates, bankDominated + other.bankDominated,
				bankGlobalSuppressed + other.bankGlobalSuppressed, rekeys + other.rekeys,
				Math.min(finalBestBankCost, other.finalBestBankCost), Math.max(maxQueueSize, other.maxQueueSize),
				walkingPqPushes + other.walkingPqPushes, localTransportPqPushes + other.localTransportPqPushes,
				globalPqPushes + other.globalPqPushes, bankingPqPushes + other.bankingPqPushes,
				transportCandidates + other.transportCandidates,
				successfulTransportRelaxations + other.successfulTransportRelaxations,
				restrictedHeuristicStates + other.restrictedHeuristicStates,
				normalHeuristicStates + other.normalHeuristicStates,
				restrictedHeuristicZeroes + other.restrictedHeuristicZeroes,
				initialCapability.equals(other.initialCapability) ? initialCapability : "MIXED",
				normalHeuristicEnabledAtStart && other.normalHeuristicEnabledAtStart);
		}
		public int statesPopped()
	{ return statesPopped;
	}
		public int staleEntries()
	{ return staleEntries;
	}
		public int pqPushes()
	{ return pqPushes;
	}
		public int uniqueStatesReached()
	{ return uniqueStatesReached;
	}
		public int walkingRelaxations()
	{ return walkingRelaxations;
	}
		public int transportRelaxations()
	{ return transportRelaxations;
	}
		public int heuristicEvaluations()
	{ return heuristicEvaluations;
	}
		public int heuristicUnreachable()
	{ return heuristicUnreachable;
	}
		public int bestBankUpdates()
	{ return bestBankUpdates;
	}
		public int bankDominated()
	{ return bankDominated;
	}
		public int bankGlobalSuppressed()
	{ return bankGlobalSuppressed;
	}
		public int rekeys()
	{ return rekeys;
	}
		public int finalBestBankCost()
	{ return finalBestBankCost;
	}
		public int maxQueueSize()
	{ return maxQueueSize;
	}
		public int walkingPqPushes()
	{ return walkingPqPushes;
	}
		public int localTransportPqPushes()
	{ return localTransportPqPushes;
	}
		public int globalPqPushes()
	{ return globalPqPushes;
	}
		public int bankingPqPushes()
	{ return bankingPqPushes;
	}
		public int transportCandidates()
	{ return transportCandidates;
	}
		public int successfulTransportRelaxations()
	{ return successfulTransportRelaxations;
	}
		public int restrictedHeuristicStates()
	{ return restrictedHeuristicStates;
	}
		public int normalHeuristicStates()
	{ return normalHeuristicStates;
	}
		public int restrictedHeuristicZeroes()
	{ return restrictedHeuristicZeroes;
	}
		public String initialCapability()
	{ return initialCapability;
	}
		public boolean normalHeuristicEnabledAtStart()
	{ return normalHeuristicEnabledAtStart;
	}
	}
	private static final class MutableCounters
	{
		int statesPopped, staleEntries, pqPushes, uniqueStatesReached, walkingRelaxations, transportRelaxations,
			heuristicEvaluations, heuristicUnreachable, bestBankUpdates, bankDominated, bankGlobalSuppressed, rekeys,
			maxQueueSize, walkingPqPushes, localTransportPqPushes, globalPqPushes, bankingPqPushes,
			transportCandidates, successfulTransportRelaxations, restrictedHeuristicStates, normalHeuristicStates;
		int restrictedHeuristicZeroes;
		final String initialCapability;
		final boolean normalHeuristicEnabledAtStart;
		MutableCounters(TeleportCapability initialCapability, boolean normalHeuristicEnabledAtStart)
		{
			this.initialCapability = initialCapability.name();
			this.normalHeuristicEnabledAtStart = normalHeuristicEnabledAtStart;
		}
		Counters snapshot(int finalBestBankCost)
	{ return new Counters(statesPopped, staleEntries, pqPushes, uniqueStatesReached, walkingRelaxations, transportRelaxations,
		heuristicEvaluations, heuristicUnreachable, bestBankUpdates, bankDominated, bankGlobalSuppressed, rekeys,
		finalBestBankCost, maxQueueSize, walkingPqPushes, localTransportPqPushes, globalPqPushes, bankingPqPushes,
		transportCandidates, successfulTransportRelaxations, restrictedHeuristicStates, normalHeuristicStates,
		restrictedHeuristicZeroes, initialCapability, normalHeuristicEnabledAtStart);
	}
	}

	private static final class SearchSpace
	{
		private static final int STATUS_CLEAR = 1;
		private static final int STATUS_WILDERNESS = 2;
		private static final int STATUS_BLOCKED_REGION = 4;

		final RoutingStatic stat; final int[] extraTiles, extraSites; final int[][] extraComponents;
		final int baseCount, tileCount; private final PreparedRoutingAccount account;
		private SearchRestrictions restrictions = SearchRestrictions.none();
		/**
		 * Per-node gate status bits, computed on first ask: entering the wilderness or the blocked
		 * league region is asked about on every walking edge, so the lookups are memoised. Only
		 * allocated while a gate is active.
		 */
		private byte[] gateBits;
		private SearchSpace(RoutingStatic stat, PreparedRoutingAccount account, int[] extraTiles,
			int[][] extraComponents, int[] extraSites)
	{ this.stat = stat; this.account = account; this.extraTiles = extraTiles; this.extraComponents = extraComponents;
		this.extraSites = extraSites; baseCount = stat.searchTileCount(); tileCount = baseCount + extraTiles.length;
	}
		static SearchSpace create(TargetOverlay target, int start, SearchRestrictions restrictions)
		{
			RoutingStatic stat = target.routingStatic(); int[] values = new int[stat.siteCount() + target.targetCount() + 1]; int count = 0;
			for (int site = 0; site < stat.siteCount(); site++) count = add(values, count, stat.siteTile(site), stat);
			for (int i = 0; i < target.targetCount(); i++) count = add(values, count, target.packedTarget(i), stat);
			count = add(values, count, start, stat);
			for (int i = 1; i < count; i++)
	{ int value = values[i], j = i - 1; while (j >= 0 && Integer.compareUnsigned(values[j], value) > 0) values[j + 1] = values[j--]; values[j + 1] = value;
	}
			int extraCount = 0; for (int i = 0; i < count; i++) if (stat.searchIndex(values[i]) < 0) values[extraCount++] = values[i];
			int[] extras = Arrays.copyOf(values, extraCount); int[][] components = new int[extraCount][];
			int[] sites = new int[extraCount];
			for (int i = 0; i < extraCount; i++)
	{ int tile = extras[i], site = stat.siteIndex(tile), targetIndex = target.targetIndex(tile); sites[i] = site;
		components[i] = targetIndex >= 0 ? target.componentsView(targetIndex) : site >= 0 ? stat.siteComponents(site) : stat.attachments(tile, target.collision());
	}
			SearchSpace space = new SearchSpace(stat, target.account(), extras, components, sites);
			space.restrictions = restrictions;
			if (restrictions.anyGate())
			{
				space.gateBits = new byte[space.tileCount];
			}
			return space;
		}
		private static int add(int[] values, int count, int value, RoutingStatic stat)
	{ if (stat.searchIndex(value) >= 0) return count; for (int i = 0; i < count; i++) if (values[i] == value) return count; values[count] = value; return count + 1;
	}
		int node(int tile)
	{ int base = stat.searchIndex(tile); if (base >= 0) return base; int extra = binarySearch(extraTiles, tile); return extra < 0 ? -1 : baseCount + extra;
	}
		int state(int tile, BankVisitState banked)
	{ return stateForNode(node(tile), banked);
	}
		int tile(int node)
	{ return node < baseCount ? stat.searchTile(node) : extraTiles[node - baseCount];
	}
		int[] components(int node)
	{ return extraComponents[node - baseCount];
	}
		boolean isBase(int node)
	{ return node < baseCount;
	}
		/** A bank in the world (static topology) that this account may use (prepared account). */
		boolean isUsableBankNode(int node)
	{ return isBankNode(node) && account.bankAccessible(tile(node));
	}
		boolean isBankNode(int node)
	{ return node < baseCount ? stat.isBankNode(node) : stat.isBankSite(extraSites[node - baseCount]);
	}
		boolean bankGlobalRelevant()
		{ return account.allowTransports() && account.bankPathEnabled();
		}
		boolean hasLocalOrigin(int tile, BankVisitState banked)
	{ PreparedRoutingAccount.View view = account.localView(banked); int index = lowerBound(view.origins, tile); return index < view.count && view.origins[index] == tile;
	}
		/** The node's wilderness/blocked-region status bits, computed on first ask. */
		private int gateStatus(int node)
		{
			int value = gateBits[node];
			if (value == 0)
			{
				int tile = tile(node);
				value = STATUS_CLEAR
					| (WildernessChecker.isInWilderness(tile) ? STATUS_WILDERNESS : 0)
					| (restrictions.inBlockedRegion(tile) ? STATUS_BLOCKED_REGION : 0);
				gateBits[node] = (byte) value;
			}
			return value;
		}
		/** Legacy's tile-to-tile gate: a step may not enter the wilderness or the blocked region. */
		boolean stepAllowed(int fromNode, int toNode)
		{
			if (gateBits == null) return true;
			int from = gateStatus(fromNode), to = gateStatus(toNode);
			return restrictions.stepAllowed((from & STATUS_WILDERNESS) != 0, (to & STATUS_WILDERNESS) != 0,
				(from & STATUS_BLOCKED_REGION) != 0, (to & STATUS_BLOCKED_REGION) != 0);
		}
		/** Whether a global cast with {@code capability} (the source band) may land on the tile. */
		boolean globalAllowed(TeleportCapability capability, int destination)
		{
			return restrictions.globalAllowed(capability, destination);
		}
		private static int binarySearch(int[] values, int target)
	{ int low = 0, high = values.length - 1; while (low <= high)
	{ int middle = low + (high - low) / 2, compare = Integer.compareUnsigned(values[middle], target); if (compare == 0) return middle; if (compare < 0) low = middle + 1; else high = middle - 1;
	} return -1;
	}
	}
}
