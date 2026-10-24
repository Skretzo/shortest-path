package shortestpath.pathfinder.astar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.WildernessChecker;
import shortestpath.transport.Transport;

/** Correctness-first forward search over tiles, capability hubs, and bank layers. */
public final class AStarForwardSearch
{
	/** A {@code progressed} callback for a search whose caller does not track progress. */
	public static final Runnable NO_PROGRESS = AStarForwardSearch::ignoreProgress;

	private AStarForwardSearch()
	{
	}

	private static void ignoreProgress()
	{
	}

	public static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start)
	{
		return search(target, heuristic, start, () -> false, true, 1, SearchRestrictions.none(), NO_PROGRESS,
			(tile, banked) -> target.isTarget(tile));
	}

	/**
	 * {@code restrictions} applies the same positional gates as legacy's per-edge checks: the
	 * caller decides them per search, while the prepared graph and heuristic stay unchanged —
	 * gating only removes edges, so the heuristic can only under-estimate the distances left.
	 * {@code progressed} runs whenever the closest-reached-tile record improves, the same
	 * moment legacy reports progress for its without-progress cutoff.
	 */
	static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start,
		BooleanSupplier cancelled, boolean optimized, double heuristicWeight, SearchRestrictions restrictions,
		Runnable progressed)
	{
		return search(target, heuristic, start, cancelled, optimized, heuristicWeight, restrictions, progressed,
			target::accepts);
	}

	/**
	 * {@code goal} decides which target states finish the search; it must only accept target
	 * tiles, and leaves the prepared heuristic admissible since it can only accept fewer states.
	 */
	public static Result search(TargetOverlay target, PreparedHeuristic heuristic, int start,
		BooleanSupplier cancelled, boolean optimized, double heuristicWeight, SearchRestrictions restrictions,
		Runnable progressed, GoalTest goal)
	{
		if (target == null || heuristic == null || cancelled == null || restrictions == null || progressed == null
			|| goal == null)
			throw new NullPointerException();
		if (heuristic.overlay() != target) throw new IllegalArgumentException("heuristic belongs to another target overlay");
		validateHeuristicWeight(heuristicWeight);
		SearchCtx ctx = new SearchCtx(target, heuristic, start, optimized, heuristicWeight, restrictions);
		AStarRoute startPath = AStarRoute.of(List.of(new PathStep(start, BankVisitState.CARRIED)), new int[1]);
		Closest closest = new Closest(target);
		if (cancelled.getAsBoolean()) return Result.cancelled(ctx.counters.snapshot(ctx.bestBankCost[0]), startPath);

		int startState = ctx.space.state(start, BankVisitState.CARRIED);
		ctx.updateBestBank(startState, 0);
		int startH = ctx.effectiveHeuristic(startState, 0);
		if (startH != CostArithmetic.INF)
		{
			ctx.best[startState] = 0;
			ctx.push(startState, 0, priority(0, startH, heuristicWeight), true, PUSH_INITIAL);
		}
		else
		{
			ctx.counters.heuristicUnreachable++;
		}
		if (ctx.capability != TeleportCapability.ALL)
		{
			int hub = hub(ctx.tileStates, ctx.capability, BankVisitState.CARRIED);
			ctx.best[hub] = 0;
			ctx.push(hub, 0, 0, true, PUSH_GLOBAL);
		}
		ctx.seedGlobals(startState);

		while (ctx.queue.poll())
		{
			if (cancelled.getAsBoolean())
				return Result.cancelled(ctx.counters.snapshot(ctx.bestBankCost[0]), startPath,
					closest.path(ctx, startState, startPath), closest.cost());
			int state = ctx.queue.state(), cost = ctx.queue.cost(), queuedPriority = ctx.queue.priority();
			if (cost != ctx.best[state])
	{ ctx.counters.staleEntries++; continue;
	}
			if (state >= ctx.tileStates)
			{
				ctx.relaxGlobalHub(state, cost);
				continue;
			}
			int node = state / 2;
			BankVisitState banked = (state & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
			int tile = ctx.space.tile(node);
			if (ctx.capability != TeleportCapability.ALL)
				ctx.activateGlobal(tile, state, banked, cost);
			int currentH = ctx.effectiveHeuristic(state, cost);
			if (currentH == CostArithmetic.INF) continue;
			int currentPriority = priority(cost, currentH, heuristicWeight);
			if (currentPriority > queuedPriority)
			{
				ctx.push(state, cost, currentPriority, false, PUSH_REKEY);
				ctx.counters.rekeys++;
				continue;
			}
			ctx.counters.statesPopped++;
			if (target.isTarget(tile) && goal.accepts(tile, banked))
				return Result.reached(cost, state, ctx.counters.snapshot(ctx.bestBankCost[0]), ctx.reconstruct(startState, state));
			if (closest.consider(state, tile, cost))
				progressed.run();
			if (ctx.space.isBase(node))
			{
				ctx.walkBase(node, banked, state, cost);
				ctx.addBlockedOrigins(tile, banked, state, cost);
			}
			else
			{
				for (int next : target.collision().ordinaryWalkingNeighbors(tile))
					ctx.relaxWalking(next, banked, state, cost);
				ctx.addBlockedOrigins(tile, banked, state, cost);
			}
			ctx.relaxBank(node, banked, state, cost);
			ctx.relaxLocalTransports(tile, banked, state, cost);
		}
		return Result.unreachable(ctx.counters.snapshot(ctx.bestBankCost[0]), startPath,
			closest.path(ctx, startState, startPath), closest.cost());
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
		private int state = -1, cost = CostArithmetic.INF, x, y;
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

		AStarRoute path(SearchCtx ctx, int startState, AStarRoute startPath)
		{
			return state < 0 ? startPath : ctx.reconstruct(startState, state);
		}

		int cost()
		{
			return state < 0 ? 0 : cost;
		}
	}

	/**
	 * One search's mutable state: the workspace arrays, queue, counters and the per-query
	 * settings the relax helpers share. A single instance lives for the whole search — the
	 * {@code best}/{@code previous} arrays stay plain fields and nothing in the relax loop
	 * allocates — so the helpers stay monomorphic private methods the JIT inlines like the
	 * static methods they replace.
	 */
	private static final class SearchCtx
	{
		final TargetOverlay target;
		final SearchSpace space;
		final PreparedHeuristic heuristic;
		final TeleportCapability capability;
		final AStarMinHeap queue;
		final TransportWins transportWins = new TransportWins();
		final MutableCounters counters;
		final int[] best, previous, globalBounds;
		final int[] bestBankCost = {CostArithmetic.INF};
		final boolean restrictedHeuristic, optimized;
		final double heuristicWeight;
		final int tileStates;

		SearchCtx(TargetOverlay target, PreparedHeuristic heuristic, int start, boolean optimized,
			double heuristicWeight, SearchRestrictions restrictions)
		{
			this.target = target;
			this.heuristic = heuristic;
			this.optimized = optimized;
			this.heuristicWeight = heuristicWeight;
			space = SearchSpace.create(target, start, restrictions);
			capability = capabilityAt(start);
			tileStates = space.tileCount * 2;
			// These arrays span every reachable tile, so allocating them is a fixed cost on every query:
			// about 1 ms for the 1.4M reachable tiles, measured on a Ryzen 9 7900. If the reachable set
			// grows a lot (Sailing would add about 4M sea tiles), reuse them across searches, using a
			// per-search stamp in place of the fill.
			int stateCount = tileStates + (capability == TeleportCapability.ALL ? 0 : HUB_STATES);
			best = new int[stateCount]; Arrays.fill(best, CostArithmetic.INF);
			previous = new int[stateCount];
			restrictedHeuristic = capability != TeleportCapability.ALL && hasGlobals(target.account());
			counters = new MutableCounters(capability, !restrictedHeuristic);
			globalBounds = restrictedHeuristic ? computeGlobalBounds() : null;
			queue = new AStarMinHeap(Math.min(stateCount, 32_768));
		}

		private void walkBase(int node, BankVisitState banked, int from, int cost)
		{
			int mask = space.stat.walkingMask(node);
			emit(banked, from, mask, 6, node - 1, cost);
			emit(banked, from, mask, 2, node + 1, cost);
			emit(banked, from, mask, 4, space.stat.southNode(node), cost);
			emit(banked, from, mask, 0, space.stat.northNode(node), cost);
			int south = space.stat.southNode(node), north = space.stat.northNode(node);
			emit(banked, from, mask, 5, south < 0 ? -1 : south - 1, cost);
			emit(banked, from, mask, 3, south < 0 ? -1 : south + 1, cost);
			emit(banked, from, mask, 7, north < 0 ? -1 : north - 1, cost);
			emit(banked, from, mask, 1, north < 0 ? -1 : north + 1, cost);
		}

		private void emit(BankVisitState banked, int from, int mask, int bit, int next, int cost)
		{
			if ((mask & (1 << bit)) != 0 && next >= 0 && next < space.baseCount && space.stepAllowed(from / 2, next))
				relaxState(from, stateForNode(next, banked), cost, 1, null, PUSH_WALKING);
		}

		private void addBlockedOrigins(int tile, BankVisitState banked, int from, int cost)
		{
			CollisionMap collision = target.collision();
			if (collision.isBlocked(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile), WorldPointUtil.unpackWorldPlane(tile))) return;
			int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile), plane = WorldPointUtil.unpackWorldPlane(tile);
			for (int direction = 0; direction < 4; direction++)
			{
				int next = WorldPointUtil.packWorldPoint(x + GameWalkOrder.ORDER_DX[direction],
					y + GameWalkOrder.ORDER_DY[direction], plane);
				if (collision.isBlocked(WorldPointUtil.unpackWorldX(next), WorldPointUtil.unpackWorldY(next), WorldPointUtil.unpackWorldPlane(next)) && space.hasLocalOrigin(next, banked))
					relaxWalking(next, banked, from, cost);
			}
		}

		private void relaxWalking(int tile, BankVisitState banked, int from, int cost)
		{
			int node = space.node(tile);
			if (node >= 0 && space.stepAllowed(from / 2, node))
				relaxState(from, stateForNode(node, banked), cost, 1, null, PUSH_WALKING);
		}

		private void relaxBank(int node, BankVisitState banked, int from, int cost)
		{
			if (banked == BankVisitState.BANKED || !target.account().bankPathEnabled() || !space.isUsableBankNode(node)) return;
			// Every bank charges the same visit cost, so comparing arrival costs with bestBankCost
			// (an arrival cost too) still orders the banks correctly.
			int bankedCost = CostArithmetic.add(cost, target.account().bankVisitCost());
			if (bankedCost == CostArithmetic.INF) return;
			relaxState(from, stateForNode(node, BankVisitState.BANKED), bankedCost, 0, null, PUSH_BANKING);
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
						relaxTransport(from, BankVisitState.BANKED, bankedCost, destination, target.account().globalCost(here, BankVisitState.BANKED, i), target.account().globalTransport(here, BankVisitState.BANKED, i), PUSH_GLOBAL);
					else if (space.node(destination) >= 0)
						counters.bankGlobalSuppressed++;
				}
			}
		}

		private void relaxLocalTransports(int tile, BankVisitState banked, int from, int cost)
		{
			PreparedRoutingAccount.View view = target.account().localView(banked);
			int start = IntArrays.lowerBound(view.origins, tile);
			for (int i = start; i < view.destinations.length && view.origins[i] == tile; i++)
			{
				int node = space.node(view.destinations[i]);
				// Legacy checks the positional gates on a transport's destination node exactly like a
				// walked neighbour (crossing the wilderness ditch is a transport, and must not bypass
				// the avoid-wilderness gate), so apply them to the jump's landing tile.
				if (node < 0 || !space.stepAllowed(from / 2, node)) continue;
				relaxTransport(from, banked, cost, view.destinations[i], view.costs[i], view.transports[i], PUSH_LOCAL);
			}
		}

		private void relaxTransport(int from, BankVisitState banked, int cost, int destination, int stepCost,
			Transport transport, int pushKind)
		{
			counters.transportCandidates++;
			int node = space.node(destination);
			if (node >= 0) relaxState(from, stateForNode(node, banked), cost, stepCost, transport, pushKind);
		}

		private void relaxState(int from, int state, int cost, int stepCost, Transport transport, int pushKind)
		{
			int candidate = CostArithmetic.add(cost, stepCost);
			if (candidate == CostArithmetic.INF) return;
			if (pushKind == PUSH_WALKING) counters.walkingRelaxations++; else counters.transportRelaxations++;
			if (candidate >= best[state]) return;
			updateBestBank(state, candidate);
			int h = effectiveHeuristic(state, candidate);
			if (h == CostArithmetic.INF)
	{ counters.heuristicUnreachable++; return;
	}
			boolean newState = best[state] == CostArithmetic.INF;
			best[state] = candidate; previous[state] = from; transportWins.record(state, transport, candidate);
			push(state, candidate, priority(candidate, h, heuristicWeight), newState, pushKind);
			if (pushKind == PUSH_LOCAL || pushKind == PUSH_GLOBAL) counters.successfulTransportRelaxations++;
		}

		private void seedGlobals(int startState)
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
					updateBestBank(state, cost);
					int h = effectiveHeuristic(state, cost);
					if (h == CostArithmetic.INF)
					{
						counters.heuristicUnreachable++;
						continue;
					}
					best[state] = cost; previous[state] = startState;
					transportWins.record(state, target.account().globalTransport(capability, BankVisitState.CARRIED, i), cost);
					push(state, cost, priority(cost, h, heuristicWeight), true, PUSH_GLOBAL);
					counters.successfulTransportRelaxations++;
		}
			}
		}

		private void activateGlobal(int tile, int from, BankVisitState banked, int cost)
		{
			TeleportCapability tileCapability = capabilityAt(tile);
			if (target.account().globalCount(tileCapability, banked) == 0) return;
			int state = hub(tileStates, tileCapability, banked);
			if (cost < best[state])
			{
				boolean newState = best[state] == CostArithmetic.INF;
				best[state] = cost; previous[state] = from;
				push(state, cost, cost, newState, PUSH_GLOBAL);
		}
		}

		private void relaxGlobalHub(int state, int cost)
		{
			TeleportCapability hubCap = hubCapability(tileStates, state);
			BankVisitState banked = ((state - tileStates) & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
			// A hub stands for every tile in its wilderness band, so the capability doubles as the
			// source position legacy checks a global teleport against.
			int count = target.account().globalCount(hubCap, banked);
			for (int i = 0; i < count; i++)
			{
				counters.transportCandidates++;
				int destination = target.account().globalDestination(hubCap, banked, i);
				if (!space.globalAllowed(hubCap, destination)) continue;
				int node = space.node(destination);
				if (node < 0) continue;
				int stepCost = target.account().globalCost(hubCap, banked, i);
				int next = stateForNode(node, banked), candidate = CostArithmetic.add(cost, stepCost);
				if (candidate == CostArithmetic.INF) continue;
				counters.transportRelaxations++;
				if (candidate >= best[next]) continue;
				boolean newState = best[next] == CostArithmetic.INF;
				best[next] = candidate; previous[next] = state;
				transportWins.record(next, target.account().globalTransport(hubCap, banked, i), candidate);
				push(next, candidate, candidate, newState, PUSH_GLOBAL);
				counters.successfulTransportRelaxations++;
			}
		}

		private AStarRoute reconstruct(int start, int terminal)
		{
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
				arrivals[count++] = from < tileStates ? AStarRoute.FROM_STEP
					: AStarRoute.fromHub(hubCapability(tileStates, from));
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
			return new AStarRoute(result, forwardArrivals, forwardCosts);
		}

		private int heuristicValue(int state)
		{
			int[] page = space.heuristicPage(state);
			int slot = state & SearchSpace.HEURISTIC_PAGE_MASK;
			int cached = page[slot];
			if (cached != 0)
			{
				counters.heuristicCacheHits++;
				return cached == SearchSpace.CACHED_INF ? CostArithmetic.INF : cached - 1;
			}
			int node = state / 2;
			BankVisitState banked = (state & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
			int value = space.isBase(node)
				? heuristic.estimateBaseNode(space.tile(node), banked, space.stat.routingComponent(node))
				: heuristic.estimate(space.tile(node), banked, space.components(node));
			page[slot] = value == CostArithmetic.INF ? SearchSpace.CACHED_INF : value + 1;
			return value;
		}

		private int effectiveHeuristic(int state, int cost)
		{
			BankVisitState banked = (state & 1) != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
			int resolved;
			if (banked == BankVisitState.BANKED)
			{
				counters.heuristicEvaluations++;
				resolved = heuristicValue(state);
			}
			else
			{
				int unbanked = heuristicValue(state);
				counters.heuristicEvaluations++;
				if (!optimized || !space.bankGlobalRelevant() || cost <= bestBankCost[0])
					resolved = unbanked;
				else
				{
					int bankedHeuristic = heuristicValue(state | 1);
					counters.heuristicEvaluations++;
					counters.bankDominated++;
					resolved = bankedHeuristic == CostArithmetic.INF ? unbanked : Math.max(unbanked, bankedHeuristic);
				}
			}
			boolean allGlobalsActivated = restrictedHeuristic
				&& best[hub(tileStates, TeleportCapability.ALL, banked)] <= cost;
			if (restrictedHeuristic && (capabilityAt(space.tile(state / 2)) != TeleportCapability.ALL
				|| !allGlobalsActivated))
			{
				counters.restrictedHeuristicStates++;
				int restricted = Math.min(resolved, globalBounds[banked == BankVisitState.BANKED ? 1 : 0]);
				// Both infinite: no route from here reaches a target without a global teleport of this
				// layer (h), nor with one (every cast lands where h is infinite), so the state is pruned
				// like any other.
				if (restricted == CostArithmetic.INF) counters.restrictedHeuristicZeroes++;
				return restricted;
			}
			counters.normalHeuristicStates++;
			return resolved;
		}

		private void updateBestBank(int state, int cost)
		{
			if (optimized && (state & 1) == 0 && space.bankGlobalRelevant() && cost < bestBankCost[0]
				&& space.isUsableBankNode(state / 2))
			{
				bestBankCost[0] = cost;
				counters.bestBankUpdates++;
			}
		}

		private void push(int state, int cost, int priority, boolean uniqueState, int kind)
		{
			if (priority == CostArithmetic.INF) return;
			queue.push(priority, cost, state);
			counters.pqPushes++;
			if (kind == PUSH_WALKING) counters.walkingPqPushes++;
			else if (kind == PUSH_LOCAL) counters.localTransportPqPushes++;
			else if (kind == PUSH_GLOBAL) counters.globalPqPushes++;
			else if (kind == PUSH_BANKING) counters.bankingPqPushes++;
			if (queue.size() > counters.maxQueueSize) counters.maxQueueSize = queue.size();
			if (uniqueState) counters.uniqueStatesReached++;
		}

		private int[] computeGlobalBounds()
		{
			int[] result = {CostArithmetic.INF, CostArithmetic.INF};
			for (int layer = 0; layer < 2; layer++)
			{
				BankVisitState banked = layer != 0 ? BankVisitState.BANKED : BankVisitState.CARRIED;
				for (int i = 0; i < target.account().globalCount(banked); i++)
				{
					int node = space.node(target.account().globalDestination(banked, i));
					if (node >= 0)
						result[layer] = Math.min(result[layer], CostArithmetic.add(target.account().globalCost(banked, i),
							heuristicValue(stateForNode(node, banked))));
				}
			}
			return result;
		}
	}

	static int priority(int cost, int heuristic, double weight)
	{
		if (heuristic == CostArithmetic.INF) return CostArithmetic.INF;
		long weighted = Math.round(weight * heuristic);
		return weighted >= CostArithmetic.INF - cost ? CostArithmetic.INF - 1 : cost + (int) weighted;
	}

	static void validateHeuristicWeight(double weight)
	{
		if (!(weight > 0) || !Double.isFinite(weight))
			throw new IllegalArgumentException("heuristic weight must be positive and finite");
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


	public static final class Result
	{
		@Getter @Accessors(fluent = true) private final boolean reached, cancelled;
		@Getter @Accessors(fluent = true) private final int cost, terminalState, closestCost;
		@Getter @Accessors(fluent = true) private final Counters counters;
		private final AStarRoute path, closestPath;
		private Result(boolean reached, boolean cancelled, int cost, int terminalState, Counters counters, AStarRoute path,
			AStarRoute closestPath, int closestCost)
		{
			this.reached = reached; this.cancelled = cancelled; this.cost = cost; this.terminalState = terminalState; this.counters = counters; this.path = path;
			this.closestPath = closestPath; this.closestCost = closestCost;
		}
		static Result reached(int cost, int state, Counters counters, AStarRoute path)
	{ return new Result(true, false, cost, state, counters, path, path, cost);
	}
		static Result unreachable(Counters counters, AStarRoute path, AStarRoute closestPath, int closestCost)
	{ return new Result(false, false, CostArithmetic.INF, -1, counters, path, closestPath, closestCost);
	}
		static Result cancelled(Counters counters, AStarRoute path)
	{ return cancelled(counters, path, path, 0);
	}
		static Result cancelled(Counters counters, AStarRoute path, AStarRoute closestPath, int closestCost)
	{ return new Result(false, true, CostArithmetic.INF, -1, counters, path, closestPath, closestCost);
	}
		public List<PathStep> path()
	{ return path.steps();
	}
		/** {@link #path()} with the search's knowledge of how each step was reached. */
		public AStarRoute route()
	{ return path;
	}
		/** Route to the popped tile nearest a target; the full route when a target was reached. */
		public List<PathStep> closestPath()
	{ return closestPath.steps();
	}
		/** {@link #closestPath()} with the search's knowledge of how each step was reached. */
		public AStarRoute closestRoute()
	{ return closestPath;
	}
	}

	@Getter @Accessors(fluent = true)
	public static final class Counters
	{
		private final int statesPopped, staleEntries, pqPushes, uniqueStatesReached, walkingRelaxations, transportRelaxations,
			heuristicEvaluations, bestBankUpdates, bankDominated, bankGlobalSuppressed, rekeys,
			maxQueueSize, walkingPqPushes, localTransportPqPushes, globalPqPushes,
			bankingPqPushes, transportCandidates, successfulTransportRelaxations, restrictedHeuristicStates,
			normalHeuristicStates;
		/** Restricted states with no route to a target, with or without a global teleport: pruned. */
		private final int restrictedHeuristicZeroes;
		/** Heuristic lookups answered from the per-search cache rather than computed. */
		private final int heuristicCacheHits;
		private final String initialCapability;
		private final boolean normalHeuristicEnabledAtStart;
		private final int heuristicUnreachable;
		@Getter(AccessLevel.NONE) private final int finalBestBankCost;
		private Counters(int statesPopped, int staleEntries, int pqPushes, int uniqueStatesReached, int walkingRelaxations,
			int transportRelaxations, int heuristicEvaluations, int heuristicUnreachable, int bestBankUpdates,
			int bankDominated, int bankGlobalSuppressed, int rekeys, int finalBestBankCost, int maxQueueSize,
			int walkingPqPushes, int localTransportPqPushes, int globalPqPushes, int bankingPqPushes,
			int transportCandidates, int successfulTransportRelaxations, int restrictedHeuristicStates,
			int normalHeuristicStates, int restrictedHeuristicZeroes, int heuristicCacheHits, String initialCapability,
			boolean normalHeuristicEnabledAtStart)
		{
			this.heuristicCacheHits = heuristicCacheHits;
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
		{ return new Counters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CostArithmetic.INF,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "UNKNOWN", false);
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
				heuristicCacheHits + other.heuristicCacheHits,
				initialCapability.equals(other.initialCapability) ? initialCapability : "MIXED",
				normalHeuristicEnabledAtStart && other.normalHeuristicEnabledAtStart);
		}
	}
	private static final class MutableCounters
	{
		int statesPopped, staleEntries, pqPushes, uniqueStatesReached, walkingRelaxations, transportRelaxations,
			heuristicEvaluations, heuristicUnreachable, bestBankUpdates, bankDominated, bankGlobalSuppressed, rekeys,
			maxQueueSize, walkingPqPushes, localTransportPqPushes, globalPqPushes, bankingPqPushes,
			transportCandidates, successfulTransportRelaxations, restrictedHeuristicStates, normalHeuristicStates;
		int restrictedHeuristicZeroes, heuristicCacheHits;
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
		restrictedHeuristicZeroes, heuristicCacheHits, initialCapability, normalHeuristicEnabledAtStart);
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
		static final int HEURISTIC_PAGE_BITS = 10;
		static final int HEURISTIC_PAGE_MASK = (1 << HEURISTIC_PAGE_BITS) - 1;
		/** Cache encoding of an infinite heuristic; other values are stored plus one, so 0 means absent. */
		static final int CACHED_INF = -1;
		/**
		 * Each tile state's heuristic, filled on first lookup. The heuristic depends only on the
		 * state, but the search asks for it at every improving relaxation, again when the state
		 * pops and for the banked twin of a bank-dominated state. Pages are allocated on first
		 * touch, so a search pays only for the regions it reaches.
		 */
		private int[][] heuristicPages;
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
	{ int base = stat.searchIndex(tile); if (base >= 0) return base; int extra = IntArrays.binarySearch(extraTiles, tile); return extra < 0 ? -1 : baseCount + extra;
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
		int[] heuristicPage(int state)
		{
			if (heuristicPages == null) heuristicPages = new int[((tileCount * 2) >> HEURISTIC_PAGE_BITS) + 1][];
			int index = state >> HEURISTIC_PAGE_BITS;
			int[] page = heuristicPages[index];
			if (page == null)
			{
				page = new int[1 << HEURISTIC_PAGE_BITS];
				heuristicPages[index] = page;
			}
			return page;
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
	{ PreparedRoutingAccount.View view = account.localView(banked); int index = IntArrays.lowerBound(view.origins, tile); return index < view.destinations.length && view.origins[index] == tile;
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
	}
}
