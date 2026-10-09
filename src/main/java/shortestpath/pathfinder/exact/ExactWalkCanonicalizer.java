package shortestpath.pathfinder.exact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.BankVisitState;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;

/**
 * Picks a natural-looking walk for each walking leg of an exact route, without changing what the
 * route costs or does.
 * <p>
 * The exact search minimises route cost only (the primary objective). Walking costs one tick in
 * each of the eight directions, so a walking leg usually has many equally cheap realisations, and
 * the search returns whichever its queue order found: diagonals that zig-zag, sideways drift, or an
 * arbitrary tile along the edge of the area a teleport may be cast from. This pass runs after the
 * search and replaces each leg by the best leg under a secondary, presentation-only objective,
 * compared lexicographically:
 * <ol>
 * <li>walking ticks, which must equal the leg's ticks in the route: the primary cost is fixed;</li>
 * <li>axis travel, the sum of |dx| + |dy| over the moves (a cardinal move counts 1, a diagonal 2).
 * Once the ticks are fixed, the least axis travel removes needless coordinate backtracking: a leg
 * travels at least the Manhattan distance between its ends, and every move west that a later move
 * east cancels adds 2. Diagonals are not penalised as such; (0,0) to (40,40) remains 40 diagonal
 * moves, while (0,0) to (1,40) becomes one diagonal and 39 straight moves rather than alternating
 * diagonals;</li>
 * <li>heading changes, the number of moves whose direction differs from the previous move's. They
 * come last because they only describe how a path that is already short and direct reads, and
 * fewer turns must never buy extra distance;</li>
 * <li>a deterministic tie-break: among legs equal on all three, the search keeps the first one it
 * finds, expanding moves in the game's own order (W, E, S, N, SW, SE, NW, NE).</li>
 * </ol>
 * <p>
 * A leg need not end on the tile the search chose. It ends anywhere its next action can be taken
 * with the same consequences (a {@link WalkGoal}): the leg before a global teleport ends anywhere
 * the teleport can be cast, and the leg before a transport at any origin of the same action
 * ({@link PreparedRoutingAccount#localActionClass}). Every other leg keeps its end tile: the final
 * destination, a bank visit, or a transport taken straight after banking.
 * <p>
 * Each leg is searched with A* over (tile, incoming direction) under the lexicographic objective,
 * bounded by the leg's ticks. Since the exact search is optimal, no goal is fewer ticks away; a
 * leg whose goal is closer, or that cannot reach its goal in its ticks, means the route and this
 * pass disagree about walking, and the leg is kept unchanged with a {@link Diagnostic}.
 */
public final class ExactWalkCanonicalizer
{
	/** Legs longer than this keep the search's walk; their window would not fit the state keys. */
	static final int MAX_LEG_TICKS = 4096;
	/** States a leg's search may expand before the leg keeps the search's walk. */
	static final int MAX_EXPANSIONS = 250_000;

	private static final int NONE = 8;
	// CollisionMap.ordinaryWalkingMask bits (0-N, 1-NE, 2-E, 3-SE, 4-S, 5-SW, 6-W, 7-NW) in the
	// game's expansion order W, E, S, N, SW, SE, NW, NE; the first four are cardinal.
	private static final int[] ORDER_BITS = {6, 2, 4, 0, 5, 3, 7, 1};
	private static final int[] ORDER_DX = {-1, 1, 0, 0, -1, 1, -1, 1};
	private static final int[] ORDER_DY = {0, 0, -1, 1, -1, -1, 1, 1};

	private final CollisionMap map;
	private final PreparedRoutingAccount account;
	private final SearchRestrictions restrictions;
	private final StateTable states = new StateTable();
	private final LexHeap heap = new LexHeap();

	public ExactWalkCanonicalizer(CollisionMap map, PreparedRoutingAccount account)
	{
		this(map, account, SearchRestrictions.none());
	}

	/**
	 * @param restrictions the search's positional gates: a canonical walk must obey them too, or
	 * it could reroute a leg through tiles the forward search was not allowed to enter
	 */
	public ExactWalkCanonicalizer(CollisionMap map, PreparedRoutingAccount account, SearchRestrictions restrictions)
	{
		if (map == null || account == null || restrictions == null) throw new NullPointerException();
		this.map = map;
		this.account = account;
		this.restrictions = restrictions;
	}

	/** Why a leg kept the search's own walk. */
	public enum Outcome
	{
		/** The leg was replaced by its canonical walk, which may be the same walk. */
		CANONICAL,
		/** A goal tile is fewer ticks away than the leg took: the route or the goal is wrong. */
		SHORTER_ROUTE,
		/** No goal tile is reachable in the leg's ticks: the walking models disagree. */
		NO_ROUTE,
		/** The search expanded {@link #MAX_EXPANSIONS} states without finishing. */
		SEARCH_LIMIT,
		/** The leg is longer than {@link #MAX_LEG_TICKS}. */
		TOO_LONG
	}

	/** A leg that kept the search's walk, and why. */
	public static final class Diagnostic
	{
		private final int startIndex, ticks, foundTicks;
		private final Outcome outcome;
		private final String goal;

		Diagnostic(int startIndex, int ticks, Outcome outcome, int foundTicks, String goal)
		{
			this.startIndex = startIndex;
			this.ticks = ticks;
			this.outcome = outcome;
			this.foundTicks = foundTicks;
			this.goal = goal;
		}

		public int startIndex()
		{
			return startIndex;
		}

		public int ticks()
		{
			return ticks;
		}

		public Outcome outcome()
		{
			return outcome;
		}

		@Override
		public String toString()
		{
			return "walking leg at step " + startIndex + " (" + ticks + " ticks, " + goal + "): " + outcome
				+ (outcome == Outcome.SHORTER_ROUTE ? " in " + foundTicks + " ticks" : "");
		}
	}

	/** A route with canonical walking legs. */
	public static final class Result
	{
		private final List<PathStep> path;
		private final int legs;
		private final List<Diagnostic> diagnostics;

		Result(List<PathStep> path, int legs, List<Diagnostic> diagnostics)
		{
			this.path = path;
			this.legs = legs;
			this.diagnostics = diagnostics;
		}

		public List<PathStep> path()
		{
			return path;
		}

		/** Walking legs of at least one tick in the route. */
		public int legs()
		{
			return legs;
		}

		/** Legs that kept the search's walk; empty when every leg was canonicalised. */
		public List<Diagnostic> diagnostics()
		{
			return diagnostics;
		}
	}

	/** A walk found by {@link #walk}: the tiles after the start, or why there is none. */
	static final class Walk
	{
		final Outcome outcome;
		final int[] tiles;
		final int foundTicks, expansions;

		Walk(Outcome outcome, int[] tiles, int foundTicks, int expansions)
		{
			this.outcome = outcome;
			this.tiles = tiles;
			this.foundTicks = foundTicks;
			this.expansions = expansions;
		}
	}

	public Result canonicalize(ExactRoute route)
	{
		List<PathStep> steps = route.steps();
		List<PathStep> path = new ArrayList<>(steps);
		List<Diagnostic> diagnostics = new ArrayList<>();
		int legs = 0;
		int legStart = 0;
		for (int i = 1; i <= steps.size(); i++)
		{
			if (i < steps.size() && isWalk(route, i)) continue;
			int legEnd = i - 1, ticks = legEnd - legStart;
			if (ticks > 0)
			{
				legs++;
				PathStep start = steps.get(legStart);
				WalkGoal goal = goal(route, legEnd);
				Walk walk = walk(start.getPackedPosition(), ticks, goal);
				if (walk.outcome == Outcome.CANONICAL)
				{
					for (int t = 0; t < ticks; t++)
						path.set(legStart + 1 + t, new PathStep(walk.tiles[t], start.getBankVisitState()));
				}
				else
				{
					diagnostics.add(new Diagnostic(legStart, ticks, walk.outcome, walk.foundTicks, goal.toString()));
				}
			}
			legStart = i;
		}
		return new Result(List.copyOf(path), legs, List.copyOf(diagnostics));
	}

	/** Whether step {@code index} is a walking move from the previous step, as the exact search walks. */
	private boolean isWalk(ExactRoute route, int index)
	{
		if (route.arrival(index) != ExactRoute.FROM_STEP) return false;
		PathStep from = route.steps().get(index - 1), to = route.steps().get(index);
		// A transport can land one tile away; its edge paid the transport's duration, not a walk
		// tick, so it must not be folded into a walking leg.
		if (to.getTransport() != null) return false;
		if (from.getBankVisitState() != to.getBankVisitState()) return false;
		int a = from.getPackedPosition(), b = to.getPackedPosition();
		int plane = WorldPointUtil.unpackWorldPlane(a);
		if (plane != WorldPointUtil.unpackWorldPlane(b)) return false;
		int ax = WorldPointUtil.unpackWorldX(a), ay = WorldPointUtil.unpackWorldY(a);
		int bx = WorldPointUtil.unpackWorldX(b), by = WorldPointUtil.unpackWorldY(b);
		int dx = bx - ax, dy = by - ay;
		if (Math.max(Math.abs(dx), Math.abs(dy)) != 1) return false;
		if (map.isBlocked(ax, ay, plane))
		{
			for (int next : map.ordinaryWalkingNeighbors(a))
				if (next == b) return true;
			return false;
		}
		int direction = direction(dx, dy);
		if ((map.ordinaryWalkingMask(a) & 1 << ORDER_BITS[direction]) != 0) return true;
		// The exact search also steps onto a blocked transport origin next to it, to take the transport.
		return direction < 4 && map.isBlocked(bx, by, plane) && hasLocalOrigin(b, from.getBankVisitState());
	}

	/** Where the leg ending at step {@code end} may end instead, given the route's next action. */
	private WalkGoal goal(ExactRoute route, int end)
	{
		List<PathStep> steps = route.steps();
		PathStep last = steps.get(end);
		int tile = last.getPackedPosition();
		if (end + 1 == steps.size()) return WalkGoal.tile(tile);
		byte arrival = route.arrival(end + 1);
		if (arrival != ExactRoute.FROM_STEP)
		{
			// A global teleport: castable anywhere its hub's capability is.
			WalkGoal area = WalkGoal.teleportArea(ExactRoute.hubCapability(arrival));
			return area.contains(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile))
				? area : WalkGoal.tile(tile);
		}
		PathStep next = steps.get(end + 1);
		// A bank visit, or a transport taken with banked items straight after it, stays where it is.
		if (next.getBankVisitState() != last.getBankVisitState()) return WalkGoal.tile(tile);
		int[] origins = actionOrigins(tile, next.getPackedPosition(), route.cost(end + 1) - route.cost(end),
			last.getBankVisitState());
		return origins == null ? WalkGoal.tile(tile) : WalkGoal.tiles(origins, WorldPointUtil.unpackWorldPlane(tile));
	}

	/**
	 * The origins of the local transport action from {@code origin} to {@code destination} that cost
	 * {@code cost}, or {@code null} unless exactly one action class matches.
	 */
	private int[] actionOrigins(int origin, int destination, int cost, BankVisitState banked)
	{
		PreparedRoutingAccount.View view = account.localView(banked);
		int actionClass = Integer.MIN_VALUE;
		for (int i = lowerBound(view.origins, origin); i < view.count && view.origins[i] == origin; i++)
		{
			if (view.destinations[i] != destination || view.costs[i] != cost) continue;
			if (actionClass != Integer.MIN_VALUE && actionClass != view.actionClasses[i]) return null;
			actionClass = view.actionClasses[i];
		}
		if (actionClass == Integer.MIN_VALUE) return null;
		int[] origins = new int[4];
		int count = 0;
		for (int i = 0; i < view.count; i++)
		{
			if (view.actionClasses[i] != actionClass) continue;
			if (count == origins.length) origins = Arrays.copyOf(origins, count * 2);
			origins[count++] = view.origins[i];
		}
		return Arrays.copyOf(origins, count);
	}

	private boolean hasLocalOrigin(int tile, BankVisitState banked)
	{
		PreparedRoutingAccount.View view = account.localView(banked);
		int index = lowerBound(view.origins, tile);
		return index < view.count && view.origins[index] == tile;
	}

	/**
	 * The lexicographically best walk of exactly {@code ticks} moves from {@code start} to a tile of
	 * {@code goal} on the same plane.
	 */
	Walk walk(int start, int ticks, WalkGoal goal)
	{
		if (ticks > MAX_LEG_TICKS) return new Walk(Outcome.TOO_LONG, null, 0, 0);
		int sx = WorldPointUtil.unpackWorldX(start), sy = WorldPointUtil.unpackWorldY(start);
		int plane = WorldPointUtil.unpackWorldPlane(start);
		// Tiles are numbered within the (2 ticks + 1)-wide square the leg cannot leave.
		int width = 2 * ticks + 1;
		// Lexicographic keys (f ticks, f axis travel, g turns) packed into one long.
		long axisRange = 2L * ticks + 1, turnRange = ticks + 1;
		states.reset();
		heap.clear();

		long startBound = goal.lowerBound(sx, sy);
		if (WalkGoal.ticks(startBound) > ticks) return new Walk(Outcome.NO_ROUTE, null, 0, 0);
		int startState = states.tile(ticks * width + ticks) * 9 + NONE;
		states.set(startState, 0, 0, 0, -1);
		heap.push((WalkGoal.ticks(startBound) * axisRange + WalkGoal.axisTravel(startBound)) * turnRange, startState);

		int expansions = 0;
		while (!heap.isEmpty())
		{
			int state = heap.pop();
			if (states.closed[state]) continue;
			states.closed[state] = true;
			int tile = state / 9, incoming = state % 9, local = states.locals[tile];
			int x = sx - ticks + local % width, y = sy - ticks + local / width;
			int gTicks = states.ticks[state], gAxis = states.axis[state], gTurns = states.turns[state];
			if (dominated(gTicks, gAxis, gTurns, tile)) continue;
			if (goal.contains(x, y))
			{
				if (gTicks < ticks) return new Walk(Outcome.SHORTER_ROUTE, null, gTicks, expansions);
				return new Walk(Outcome.CANONICAL, trace(state, sx, sy, ticks, width, plane), gTicks, expansions);
			}
			if (++expansions > MAX_EXPANSIONS) return new Walk(Outcome.SEARCH_LIMIT, null, 0, expansions);
			if (gTicks == ticks) continue;

			int packed = WorldPointUtil.packWorldPoint(x, y, plane);
			int mask;
			boolean blocked = map.isBlocked(x, y, plane);
			if (blocked)
			{
				// Only the start can be blocked (a transport may land on one); leave it as the search does.
				mask = 0;
				for (int next : map.ordinaryWalkingNeighbors(packed))
					mask |= 1 << ORDER_BITS[direction(WorldPointUtil.unpackWorldX(next) - x,
						WorldPointUtil.unpackWorldY(next) - y)];
			}
			else
			{
				mask = map.ordinaryWalkingMask(packed);
			}
			for (int direction = 0; direction < 8; direction++)
			{
				int nx = x + ORDER_DX[direction], ny = y + ORDER_DY[direction];
				if ((mask & 1 << ORDER_BITS[direction]) == 0)
				{
					// A blocked goal tile may still be stepped onto cardinally, as the exact search does.
					if (direction >= 4 || blocked || !goal.entersBlockedTiles() || !map.isBlocked(nx, ny, plane)
						|| !goal.contains(nx, ny)) continue;
				}
				if (!restrictions.stepAllowed(packed, WorldPointUtil.packWorldPoint(nx, ny, plane)))
				{
					continue;
				}
				long bound = goal.lowerBound(nx, ny);
				int nextTicks = gTicks + 1;
				if (nextTicks + WalkGoal.ticks(bound) > ticks) continue;
				int nextAxis = gAxis + (direction < 4 ? 1 : 2);
				int nextTurns = gTurns + (incoming == NONE || incoming == direction ? 0 : 1);
				int nextTile = states.tile((ny - sy + ticks) * width + nx - sx + ticks);
				if (dominated(nextTicks, nextAxis, nextTurns, nextTile)) continue;
				int next = nextTile * 9 + direction;
				if (states.closed[next] || !better(nextTicks, nextAxis, nextTurns, next)) continue;
				states.set(next, nextTicks, nextAxis, nextTurns, state);
				long fTicks = nextTicks + WalkGoal.ticks(bound), fAxis = nextAxis + WalkGoal.axisTravel(bound);
				heap.push((fTicks * axisRange + fAxis) * turnRange + nextTurns, next);
			}
		}
		return new Walk(Outcome.NO_ROUTE, null, 0, expansions);
	}

	/**
	 * Whether a state of {@code tile} reached with (ticks, axis, turns) cannot beat the tile's best
	 * state so far. Any walk on from the state can continue from the best one with the same moves,
	 * at the cost of at most one more turn for the first move, so only a state with less ticks or
	 * axis travel, or the same and no more turns, can still do better. Best states only improve, so
	 * a state this rejects stays rejected.
	 */
	private boolean dominated(int ticks, int axis, int turns, int tile)
	{
		if (ticks != states.bestTicks[tile]) return ticks > states.bestTicks[tile];
		if (axis != states.bestAxis[tile]) return axis > states.bestAxis[tile];
		return turns > states.bestTurns[tile];
	}

	/** Whether the state's recorded walk is lexicographically worse than (ticks, axis, turns); unset ones are. */
	private boolean better(int ticks, int axis, int turns, int state)
	{
		if (ticks != states.ticks[state]) return ticks < states.ticks[state];
		if (axis != states.axis[state]) return axis < states.axis[state];
		return turns < states.turns[state];
	}

	private int[] trace(int state, int sx, int sy, int ticks, int width, int plane)
	{
		int[] tiles = new int[states.ticks[state]];
		for (int i = tiles.length - 1; i >= 0; i--, state = states.parent[state])
		{
			int local = states.locals[state / 9];
			tiles[i] = WorldPointUtil.packWorldPoint(sx - ticks + local % width, sy - ticks + local / width, plane);
		}
		return tiles;
	}

	private static int direction(int dx, int dy)
	{
		for (int i = 0; i < 8; i++)
			if (ORDER_DX[i] == dx && ORDER_DY[i] == dy) return i;
		throw new IllegalArgumentException("not a walking move: " + dx + "," + dy);
	}

	private static int lowerBound(int[] values, int target)
	{
		int low = 0, high = values.length;
		while (low < high)
		{
			int middle = low + (high - low) / 2;
			if (Integer.compareUnsigned(values[middle], target) < 0) low = middle + 1;
			else high = middle;
		}
		return low;
	}

	/**
	 * The tiles a leg's search has reached, by their number in the leg's square, reused across legs.
	 * Tile {@code t} owns states {@code 9t} to {@code 9t + 8}, one per incoming direction (8 for the
	 * start's none), and the best* arrays hold the lexicographically least of them.
	 */
	private static final class StateTable
	{
		private static final int UNSET = Integer.MAX_VALUE;

		int size;
		int[] locals = new int[256], bestTicks = new int[256], bestAxis = new int[256], bestTurns = new int[256];
		int[] ticks = new int[256 * 9], axis = new int[256 * 9], turns = new int[256 * 9], parent = new int[256 * 9];
		boolean[] closed = new boolean[256 * 9];
		// Open addressing from tile number to tile; a slot is live only if stamped with this leg's generation.
		private int[] slots = new int[1024], stamps = new int[1024];
		private int generation;

		void reset()
		{
			size = 0;
			if (++generation == 0)
			{
				Arrays.fill(stamps, 0);
				generation = 1;
			}
		}

		/** The tile numbered {@code local}, added with no states if it is new. */
		int tile(int local)
		{
			int mask = slots.length - 1, slot = hash(local) & mask;
			for (; stamps[slot] == generation; slot = slot + 1 & mask)
				if (locals[slots[slot]] == local) return slots[slot];
			if (size == locals.length) grow();
			if (2 * (size + 1) > slots.length)
			{
				rehash();
				mask = slots.length - 1;
				for (slot = hash(local) & mask; stamps[slot] == generation; ) slot = slot + 1 & mask;
			}
			int tile = size++;
			locals[tile] = local;
			bestTicks[tile] = UNSET;
			Arrays.fill(ticks, tile * 9, tile * 9 + 9, UNSET);
			Arrays.fill(closed, tile * 9, tile * 9 + 9, false);
			stamps[slot] = generation;
			slots[slot] = tile;
			return tile;
		}

		void set(int state, int tick, int axisTravel, int turnCount, int from)
		{
			ticks[state] = tick;
			axis[state] = axisTravel;
			turns[state] = turnCount;
			parent[state] = from;
			int tile = state / 9;
			if (tick < bestTicks[tile] || tick == bestTicks[tile] && (axisTravel < bestAxis[tile]
				|| axisTravel == bestAxis[tile] && turnCount < bestTurns[tile]))
			{
				bestTicks[tile] = tick;
				bestAxis[tile] = axisTravel;
				bestTurns[tile] = turnCount;
			}
		}

		private void grow()
		{
			int capacity = locals.length * 2;
			locals = Arrays.copyOf(locals, capacity);
			bestTicks = Arrays.copyOf(bestTicks, capacity);
			bestAxis = Arrays.copyOf(bestAxis, capacity);
			bestTurns = Arrays.copyOf(bestTurns, capacity);
			ticks = Arrays.copyOf(ticks, capacity * 9);
			axis = Arrays.copyOf(axis, capacity * 9);
			turns = Arrays.copyOf(turns, capacity * 9);
			parent = Arrays.copyOf(parent, capacity * 9);
			closed = Arrays.copyOf(closed, capacity * 9);
		}

		private void rehash()
		{
			slots = new int[slots.length * 2];
			stamps = new int[slots.length];
			generation = 1;
			int mask = slots.length - 1;
			for (int tile = 0; tile < size; tile++)
			{
				int slot = hash(locals[tile]) & mask;
				while (stamps[slot] == generation) slot = slot + 1 & mask;
				stamps[slot] = generation;
				slots[slot] = tile;
			}
		}

		private static int hash(int key)
		{
			int h = key * 0x9E3779B9;
			return h ^ h >>> 16;
		}
	}

	/** A binary min-heap of states by key, first in first out among equal keys. */
	private static final class LexHeap
	{
		private long[] keys = new long[1024];
		private int[] sequence = new int[1024], states = new int[1024];
		private int size, pushed;

		void clear()
		{
			size = 0;
			pushed = 0;
		}

		boolean isEmpty()
		{
			return size == 0;
		}

		void push(long key, int state)
		{
			if (size == keys.length)
			{
				keys = Arrays.copyOf(keys, size * 2);
				sequence = Arrays.copyOf(sequence, size * 2);
				states = Arrays.copyOf(states, size * 2);
			}
			int at = size++, order = pushed++;
			while (at > 0)
			{
				int up = (at - 1) / 2;
				if (!less(key, order, keys[up], sequence[up])) break;
				move(up, at);
				at = up;
			}
			keys[at] = key;
			sequence[at] = order;
			states[at] = state;
		}

		int pop()
		{
			int result = states[0];
			size--;
			long key = keys[size];
			int order = sequence[size], state = states[size], at = 0;
			while (true)
			{
				int child = 2 * at + 1;
				if (child >= size) break;
				if (child + 1 < size && less(keys[child + 1], sequence[child + 1], keys[child], sequence[child])) child++;
				if (!less(keys[child], sequence[child], key, order)) break;
				move(child, at);
				at = child;
			}
			keys[at] = key;
			sequence[at] = order;
			states[at] = state;
			return result;
		}

		private void move(int from, int to)
		{
			keys[to] = keys[from];
			sequence[to] = sequence[from];
			states[to] = states[from];
		}

		private static boolean less(long key, int order, long otherKey, int otherOrder)
		{
			return key < otherKey || key == otherKey && order < otherOrder;
		}
	}
}
