package shortestpath.pathfinder.exact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;

/** Correctness-first forward search over tiles, capability hubs, and bank layers. */
public final class ExactForwardSearch
{
	private ExactForwardSearch()
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
		if (target == null || heuristic == null || cancelled == null) throw new NullPointerException();
		if (heuristic.overlay() != target) throw new IllegalArgumentException("heuristic belongs to another target overlay");
		validateHeuristicWeight(heuristicWeight);
		SearchSpace space = SearchSpace.create(target, start);
		Capability capability = capabilityAt(start);
		int tileStates = space.tileCount * 2;
		int stateCount = tileStates + (capability == Capability.ALL ? 0 : 4);
		int[] best = new int[stateCount]; Arrays.fill(best, ExactCosts.INF);
		int[] previous = new int[stateCount];
		boolean restrictedHeuristic = capability != Capability.ALL && hasGlobals(target.account());
		MutableCounters counters = new MutableCounters(capability, !restrictedHeuristic);
		int[] bestBankCost = {ExactCosts.INF};
		int[] globalBounds = restrictedHeuristic ? globalBounds(target, heuristic, space) : null;
		List<PathStep> startPath = List.of(new PathStep(start, false));
		if (cancelled.getAsBoolean()) return Result.cancelled(counters.snapshot(bestBankCost[0]), startPath);

		ExactMinHeap queue = new ExactMinHeap(Math.min(stateCount, 32_768));
		int startState = space.state(start, false);
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
		if (capability == Capability.WILDERNESS)
		{
			int hub = hub(tileStates, false, false);
			best[hub] = 0;
			push(queue, hub, 0, 0, counters, true, PUSH_GLOBAL);
		}
		seedGlobals(target, capability, best, previous, queue, counters, startState, space,
			restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);

		while (queue.poll())
		{
			if (cancelled.getAsBoolean()) return Result.cancelled(counters.snapshot(bestBankCost[0]), startPath);
			int state = queue.state(), cost = queue.cost(), queuedPriority = queue.priority();
			if (cost != best[state])
	{ counters.staleEntries++; continue;
	}
			if (state >= tileStates)
			{
				relaxGlobalHub(target, space, state, tileStates, cost, best, previous, queue, counters);
				continue;
			}
			int node = state / 2;
			boolean banked = (state & 1) != 0;
			int tile = space.tile(node);
			if (capability != Capability.ALL)
				activateGlobal(tile, state, banked, tileStates, cost, best, previous, queue, counters);
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
				return Result.reached(cost, state, counters.snapshot(bestBankCost[0]), reconstruct(space, startState, state, previous));
			if (space.isBase(node))
			{
				walkBase(space, node, banked, state, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
				addBlockedOrigins(target, space, tile, banked, state, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
			}
			else
			{
				for (int next : target.collision().ordinaryWalkingNeighbors(tile))
					relaxWalking(space, next, banked, state, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
				addBlockedOrigins(target, space, tile, banked, state, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
			}
			relaxBank(target, space, node, banked, state, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, capability, bestBankCost);
			relaxLocalTransports(target, space, tile, banked, state, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		}
		return Result.unreachable(counters.snapshot(bestBankCost[0]), startPath);
	}

	private static void walkBase(SearchSpace space, int node, boolean banked, int from, int cost, int[] best,
		int[] previous, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic, int[] globalBounds,
		boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		int mask = space.stat.walkingMask(node);
		emit(space, banked, from, mask, 6, node - 1, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 2, node + 1, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 4, space.stat.southNode(node), cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 0, space.stat.northNode(node), cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		int south = space.stat.southNode(node), north = space.stat.northNode(node);
		emit(space, banked, from, mask, 5, south < 0 ? -1 : south - 1, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 3, south < 0 ? -1 : south + 1, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 7, north < 0 ? -1 : north - 1, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
		emit(space, banked, from, mask, 1, north < 0 ? -1 : north + 1, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
	}

	private static void emit(SearchSpace space, boolean banked, int from, int mask, int bit, int next, int cost,
		int[] best, int[] previous, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		if ((mask & (1 << bit)) != 0 && next >= 0 && next < space.baseCount)
			relaxState(space, from, stateForNode(next, banked), cost, 1, best, previous, queue, counters,
				restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_WALKING);
	}

	private static void addBlockedOrigins(TargetOverlay target, SearchSpace space, int tile, boolean banked, int from, int cost,
		int[] best, int[] previous, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		CollisionMap collision = target.collision();
		if (collision.isBlocked(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile), WorldPointUtil.unpackWorldPlane(tile))) return;
		int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile), plane = WorldPointUtil.unpackWorldPlane(tile);
		int[] cardinal = {WorldPointUtil.packWorldPoint(x - 1, y, plane), WorldPointUtil.packWorldPoint(x + 1, y, plane), WorldPointUtil.packWorldPoint(x, y - 1, plane), WorldPointUtil.packWorldPoint(x, y + 1, plane)};
		for (int next : cardinal)
			if (collision.isBlocked(WorldPointUtil.unpackWorldX(next), WorldPointUtil.unpackWorldY(next), WorldPointUtil.unpackWorldPlane(next)) && space.hasLocalOrigin(next, banked))
				relaxWalking(space, next, banked, from, cost, best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost);
	}

	private static void relaxWalking(SearchSpace space, int tile, boolean banked, int from, int cost,
		int[] best, int[] previous, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		int node = space.node(tile);
		if (node >= 0) relaxState(space, from, stateForNode(node, banked), cost, 1, best, previous, queue, counters,
			restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_WALKING);
	}

	private static void relaxBank(TargetOverlay target, SearchSpace space, int node, boolean banked, int from, int cost,
		int[] best, int[] previous, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight,
		Capability capability, int[] bestBankCost)
	{
		if (banked || !target.account().bankPathEnabled() || !space.isReachableBankNode(node)) return;
		relaxState(space, from, stateForNode(node, true), cost, 0, best, previous, queue, counters,
			restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_BANKING);
		if (capability == Capability.ALL)
			for (int i = 0; i < target.account().globalCount(true); i++)
				if (!optimized || cost <= bestBankCost[0])
					relaxTransport(space, from, true, cost, target.account().globalDestination(true, i), target.account().globalCost(true, i), best, previous, queue, counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_GLOBAL);
				else if (space.node(target.account().globalDestination(true, i)) >= 0)
					counters.bankGlobalSuppressed++;
	}

	private static void relaxLocalTransports(TargetOverlay target, SearchSpace space, int tile, boolean banked, int from, int cost,
		int[] best, int[] previous, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		PreparedRoutingAccount.View view = target.account().localView(banked);
		int start = lowerBound(view.origins, tile);
		for (int i = start; i < view.count && view.origins[i] == tile; i++)
			relaxTransport(space, from, banked, cost, view.destinations[i], view.costs[i], best, previous, queue,
				counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, PUSH_LOCAL);
	}

	private static void relaxTransport(SearchSpace space, int from, boolean banked, int cost, int destination, int stepCost,
		int[] best, int[] previous, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost,
		int pushKind)
	{
		counters.transportCandidates++;
		int node = space.node(destination);
		if (node >= 0) relaxState(space, from, stateForNode(node, banked), cost, stepCost, best, previous, queue,
			counters, restrictedHeuristic, globalBounds, optimized, heuristic, heuristicWeight, bestBankCost, pushKind);
	}

	private static void relaxState(SearchSpace space, int from, int state, int cost, int stepCost, int[] best,
		int[] previous, ExactMinHeap queue, MutableCounters counters, boolean restrictedHeuristic, int[] globalBounds,
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
		best[state] = candidate; previous[state] = from;
		push(queue, state, candidate, priority(candidate, h, heuristicWeight), counters, newState, pushKind);
		if (pushKind == PUSH_LOCAL || pushKind == PUSH_GLOBAL) counters.successfulTransportRelaxations++;
	}

	private static void seedGlobals(TargetOverlay target, Capability capability, int[] best, int[] previous,
		ExactMinHeap queue, MutableCounters counters, int startState, SearchSpace space, boolean restrictedHeuristic,
		int[] globalBounds, boolean optimized, PreparedHeuristic heuristic, double heuristicWeight, int[] bestBankCost)
	{
		if (capability == Capability.NONE) return;
		boolean wilderness = capability == Capability.WILDERNESS;
		int count = wilderness ? target.account().wildernessGlobalCount(false) : target.account().globalCount(false);
		for (int i = 0; i < count; i++)
		{
			counters.transportCandidates++;
			int destination = wilderness ? target.account().wildernessGlobalDestination(false, i) : target.account().globalDestination(false, i);
			int node = space.node(destination);
			if (node < 0) continue;
			int state = stateForNode(node, false);
			int cost = wilderness ? target.account().wildernessGlobalCost(false, i) : target.account().globalCost(false, i);
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
				push(queue, state, cost, priority(cost, h, heuristicWeight), counters, true, PUSH_GLOBAL);
				counters.successfulTransportRelaxations++;
		}
		}
	}

	private static void activateGlobal(int tile, int from, boolean banked, int tileStates, int cost,
		int[] best, int[] previous, ExactMinHeap queue, MutableCounters counters)
	{
		Capability capability = capabilityAt(tile);
		if (capability == Capability.NONE) return;
		int state = hub(tileStates, capability == Capability.ALL, banked);
		if (cost < best[state])
		{
			boolean newState = best[state] == ExactCosts.INF;
			best[state] = cost; previous[state] = from;
			push(queue, state, cost, cost, counters, newState, PUSH_GLOBAL);
	}
	}

	private static void relaxGlobalHub(TargetOverlay target, SearchSpace space, int state, int tileStates, int cost,
		int[] best, int[] previous, ExactMinHeap queue, MutableCounters counters)
	{
		boolean all = state >= tileStates + 2, banked = ((state - tileStates) & 1) != 0;
		int count = all ? target.account().globalCount(banked) : target.account().wildernessGlobalCount(banked);
		for (int i = 0; i < count; i++)
		{
			counters.transportCandidates++;
			int destination = all ? target.account().globalDestination(banked, i) : target.account().wildernessGlobalDestination(banked, i);
			int node = space.node(destination);
			if (node < 0) continue;
			int stepCost = all ? target.account().globalCost(banked, i) : target.account().wildernessGlobalCost(banked, i);
			int next = stateForNode(node, banked), candidate = ExactCosts.add(cost, stepCost);
			if (candidate == ExactCosts.INF) continue;
			counters.transportRelaxations++;
			if (candidate >= best[next]) continue;
			boolean newState = best[next] == ExactCosts.INF;
			best[next] = candidate; previous[next] = state;
			push(queue, next, candidate, candidate, counters, newState, PUSH_GLOBAL);
			counters.successfulTransportRelaxations++;
		}
	}

	private static List<PathStep> reconstruct(SearchSpace space, int start, int terminal, int[] previous)
	{
		ArrayList<PathStep> result = new ArrayList<>();
		for (int state = terminal; state != start; state = previous[state])
			if (state < space.tileCount * 2) result.add(new PathStep(space.tile(state / 2), (state & 1) != 0));
		result.add(new PathStep(space.tile(start / 2), (start & 1) != 0));
		java.util.Collections.reverse(result); return result;
	}

	private static int heuristic(PreparedHeuristic heuristic, SearchSpace space, int state)
	{
		int node = state / 2;
		return space.isBase(node)
			? heuristic.estimateBaseNode(space.tile(node), (state & 1) != 0, space.stat.routingComponent(node))
			: heuristic.estimate(space.tile(node), (state & 1) != 0, space.components(node));
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
		boolean banked = (state & 1) != 0;
		int resolved;
		if (banked)
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
			&& best[hub(space.tileCount * 2, true, banked)] <= cost;
		if (restrictedHeuristic && (capabilityAt(space.tile(state / 2)) != Capability.ALL
			|| !allGlobalsActivated))
		{
			counters.restrictedHeuristicStates++;
			int restricted = Math.min(resolved, globalBounds[banked ? 1 : 0]);
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
			&& space.isReachableBankNode(state / 2))
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

	static int stateForNode(int node, boolean banked)
	{
		return SiteGraph.stateId(node, banked);
	}

	private static boolean hasGlobals(PreparedRoutingAccount account)
	{
		return account.allowTransports() && (account.globalCount(false) != 0 || account.globalCount(true) != 0
			|| account.wildernessGlobalCount(false) != 0 || account.wildernessGlobalCount(true) != 0);
	}

	private static int[] globalBounds(TargetOverlay target, PreparedHeuristic heuristic, SearchSpace space)
	{
		int[] result = {ExactCosts.INF, ExactCosts.INF};
		for (int layer = 0; layer < 2; layer++)
		{
			boolean banked = layer != 0;
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

	private enum Capability
	{
		NONE, WILDERNESS, ALL
	}
	private static Capability capabilityAt(int tile)
	{
		int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile);
		if (inArea(x, y, 2944, 3760, 448, 448) || inArea(x, y, 2944, 10155, 518, 221)) return Capability.NONE;
		if (inArea(x, y, 2944, 3680, 448, 448) || inArea(x, y, 2944, 10075, 518, 301)) return Capability.WILDERNESS;
		return Capability.ALL;
	}
	private static boolean inArea(int x, int y, int left, int bottom, int width, int height)
	{
		return x >= left && x < left + width && y >= bottom && y < bottom + height;
	}
	private static int hub(int tileStates, boolean all, boolean banked)
	{
		return tileStates + (all ? 2 : 0) + (banked ? 1 : 0);
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
		private final int cost, terminalState;
		private final Counters counters;
		private final List<PathStep> path;
		private Result(boolean reached, boolean cancelled, int cost, int terminalState, Counters counters, List<PathStep> path)
		{
			this.reached = reached; this.cancelled = cancelled; this.cost = cost; this.terminalState = terminalState; this.counters = counters; this.path = path;
		}
		static Result reached(int cost, int state, Counters counters, List<PathStep> path)
	{ return new Result(true, false, cost, state, counters, path);
	}
		static Result unreachable(Counters counters, List<PathStep> path)
	{ return new Result(false, false, ExactCosts.INF, -1, counters, path);
	}
		static Result cancelled(Counters counters, List<PathStep> path)
	{ return new Result(false, true, ExactCosts.INF, -1, counters, path);
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
	{ return path;
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
		MutableCounters(Capability initialCapability, boolean normalHeuristicEnabledAtStart)
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
		final RoutingStatic stat; final int[] extraTiles, extraSites; final int[][] extraComponents;
		final int baseCount, tileCount; private final PreparedRoutingAccount account;
		private SearchSpace(RoutingStatic stat, PreparedRoutingAccount account, int[] extraTiles,
			int[][] extraComponents, int[] extraSites)
	{ this.stat = stat; this.account = account; this.extraTiles = extraTiles; this.extraComponents = extraComponents;
		this.extraSites = extraSites; baseCount = stat.searchTileCount(); tileCount = baseCount + extraTiles.length;
	}
		static SearchSpace create(TargetOverlay target, int start)
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
			return new SearchSpace(stat, target.account(), extras, components, sites);
		}
		private static int add(int[] values, int count, int value, RoutingStatic stat)
	{ if (stat.searchIndex(value) >= 0) return count; for (int i = 0; i < count; i++) if (values[i] == value) return count; values[count] = value; return count + 1;
	}
		int node(int tile)
	{ int base = stat.searchIndex(tile); if (base >= 0) return base; int extra = binarySearch(extraTiles, tile); return extra < 0 ? -1 : baseCount + extra;
	}
		int state(int tile, boolean banked)
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
		boolean isReachableBankNode(int node)
	{ return node < baseCount ? stat.isReachableBankNode(node) : stat.isReachableBankSite(extraSites[node - baseCount]);
	}
		boolean bankGlobalRelevant()
		{ return account.allowTransports() && account.bankPathEnabled();
		}
		boolean hasLocalOrigin(int tile, boolean banked)
	{ PreparedRoutingAccount.View view = account.localView(banked); int index = lowerBound(view.origins, tile); return index < view.count && view.origins[index] == tile;
	}
		private static int binarySearch(int[] values, int target)
	{ int low = 0, high = values.length - 1; while (low <= high)
	{ int middle = low + (high - low) / 2, compare = Integer.compareUnsigned(values[middle], target); if (compare == 0) return middle; if (compare < 0) low = middle + 1; else high = middle - 1;
	} return -1;
	}
	}
}
