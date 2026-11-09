package shortestpath.pathfinder.astar;

import java.util.*;
import java.util.function.IntPredicate;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.*;

/**
 * Rewrites the walking legs of an A* route into the tiles the game's own walking takes when the
 * player clicks along them, without changing what the route costs or does.
 * <p>
 * The A* search minimises route cost only. Walking costs one tick in each of the eight
 * directions, so a walking leg usually has many equally cheap realisations, and the search
 * returns whichever its queue order found. The player does not walk tiles, though; they click a
 * tile, and the game runs a breadth-first search over a 128x128 window centred on them,
 * expanding neighbours in the order W, E, S, N, SW, SE, NW, NE, and walks the path traced back
 * from the clicked tile along each tile's first discoverer.
 * <p>
 * Each leg is walked end to end. A breadth-first search from the leg's start, in the game's order,
 * explores only the tiles that can lie on a walk of the leg's length (a tile's distance from the
 * start plus a straight walk to the end must fit), so the path traced back from the end through
 * each tile's first discoverer is the game's own walk to it, whatever detour the A* search took.
 * Every stretch of it is also the game's path between its own two tiles, so the player can click
 * along it. The clicks are placed afterwards: from each tile, the furthest one in a
 * {@link #CLICK_RADIUS minimap click's reach} whose path needs at most {@link #MAX_CHECKPOINTS}
 * checkpoint tiles, which the game would walk only up to the 25th corner. Steps the search's
 * {@link SearchRestrictions} gate off are not taken, since the game does not know those gates and
 * keeping the search's step preserves both the cost and the restriction. A leg no game walk
 * matches in length keeps the search's steps, and so does a step no click realises: the search's
 * own step onto a blocked transport origin, and the game's first step on its way off a blocked
 * tile a transport landed on. Legs keep their length and their first tile, so every other step of
 * the route, and the route's cost, are unchanged.
 * <p>
 * A leg need not end on the tile the search chose. The leg before a global teleport may end on any
 * tile the teleport can be cast from, and the leg before a transport on any origin of the same
 * action (the prepared account's local action class). It ends on the first such tile the game's
 * search discovers at exactly the leg's length, so the cost is unchanged; a qualifying tile nearer
 * than that is never taken, and without one the leg ends on the search's own end tile. Every other leg keeps its end tile: the
 * final destination, a bank visit, or a transport taken straight after banking.
 */
public final class AStarWalkCanonicalizer
{
	/** Roughly the reach of a minimap click, in tiles. */
	static final int CLICK_RADIUS = 35;

	/**
	 * Checkpoint tiles the client keeps for one click's walk: the direction-change corners of
	 * the path plus the destination. A click needing more is walked only up to the 25th
	 * checkpoint and the player stops short, so a click never spans more.
	 */
	private static final int MAX_CHECKPOINTS = 25;

	private static final int UNVISITED = -1;
	// The game's expansion order lives in GameWalkOrder.

	private final CollisionMap map;
	private final PreparedRoutingAccount account;
	private final SearchRestrictions restrictions;

	public AStarWalkCanonicalizer(CollisionMap map, PreparedRoutingAccount account)
	{
		this(map, account, SearchRestrictions.none());
	}

	/**
	 * @param restrictions the search's positional gates: the in-game paths must obey them too, or a
	 * leg could be drawn through tiles the forward search was not allowed to enter
	 */
	public AStarWalkCanonicalizer(CollisionMap map, PreparedRoutingAccount account, SearchRestrictions restrictions)
	{
		this.map = Objects.requireNonNull(map, "map");
		this.account = Objects.requireNonNull(account, "account");
		this.restrictions = Objects.requireNonNull(restrictions, "restrictions");
	}

	/** A rewritten route and the indices of its steps the player clicks to walk it. */
	public static final class Result
	{
		/** The route, as long as the search's and with the same steps outside its walking legs. */
		@Getter @Accessors(fluent = true) private final List<PathStep> path;
		/**
		 * Ascending indices into {@link #path()} of the tiles to click, one per click. Every
		 * index is a real click: the game's own walking path lands on that tile.
		 */
		@Getter @Accessors(fluent = true) private final List<Integer> clickPoints;
		/**
		 * Ascending indices into {@link #path()} of the steps no in-game click realises, such as
		 * onto a blocked transport origin or off a blocked landing; they are not clicks.
		 */
		@Getter @Accessors(fluent = true) private final List<Integer> keptStepIndices;

		Result(List<PathStep> path, List<Integer> clickPoints, List<Integer> keptStepIndices)
		{
			this.path = path;
			this.clickPoints = clickPoints;
			this.keptStepIndices = keptStepIndices;
		}
	}

	public Result canonicalize(AStarRoute route)
	{
		List<PathStep> steps = route.steps();
		int[] legBounds = new int[8];
		IntPredicate[] goals = new IntPredicate[4];
		int legs = 0;
		int legStart = 0;
		for (int i = 1; i <= steps.size(); i++)
		{
			if (i < steps.size() && isWalk(route, i)) continue;
			if (i - 1 > legStart)
			{
				if (legs == goals.length)
				{
					legBounds = Arrays.copyOf(legBounds, 4 * legs);
					goals = Arrays.copyOf(goals, 2 * legs);
				}
				legBounds[2 * legs] = legStart;
				legBounds[2 * legs + 1] = i - 1;
				goals[legs++] = goal(route, i - 1);
			}
			legStart = i;
		}
		return rewrite(steps, Arrays.copyOf(legBounds, 2 * legs), goals);
	}

	/**
	 * Rewrites the walking legs of {@code path}: leg {@code k} runs from step {@code legBounds[2k]}
	 * to step {@code legBounds[2k + 1]}, and its steps are single-tile walking moves. Every leg
	 * keeps its end tile.
	 */
	Result rewrite(List<PathStep> path, int[] legBounds)
	{
		return rewrite(path, legBounds, new IntPredicate[legBounds.length / 2]);
	}

	/** As above, with leg {@code k} free to end on any tile {@code goals[k]} accepts; null keeps its end. */
	private Result rewrite(List<PathStep> path, int[] legBounds, IntPredicate[] goals)
	{
		List<PathStep> rewritten = new ArrayList<>(path);
		List<Integer> clicks = new ArrayList<>();
		List<Integer> kept = new ArrayList<>();
		for (int leg = 0; leg < legBounds.length / 2; leg++)
			rewriteLeg(rewritten, legBounds[2 * leg], legBounds[2 * leg + 1], goals[leg], clicks, kept);
		return new Result(List.copyOf(rewritten), List.copyOf(clicks), List.copyOf(kept));
	}

	/** Whether step {@code index} is a walking move from the previous step, as the A* search walks. */
	private boolean isWalk(AStarRoute route, int index)
	{
		if (route.arrival(index) != AStarRoute.FROM_STEP) return false;
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
		if ((map.ordinaryWalkingMask(a) & 1 << GameWalkOrder.ORDER_BITS[direction]) != 0) return true;
		// The A* search also steps onto a blocked transport origin next to it, to take the transport.
		return direction < 4 && map.isBlocked(bx, by, plane) && hasLocalOrigin(b, from.getBankVisitState());
	}

	/**
	 * The tiles the leg ending at step {@code end} may end on instead of there, given the route's
	 * next action; {@code null} when it has to end where it does.
	 */
	private IntPredicate goal(AStarRoute route, int end)
	{
		List<PathStep> steps = route.steps();
		PathStep last = steps.get(end);
		int tile = last.getPackedPosition();
		if (end + 1 == steps.size()) return null;
		byte arrival = route.arrival(end + 1);
		if (arrival != AStarRoute.FROM_STEP)
		{
			// A global teleport: castable anywhere its hub's capability is.
			TeleportCapability capability = AStarRoute.hubCapability(arrival);
			return TeleportCapability.at(tile).allows(capability)
				? other -> TeleportCapability.at(other).allows(capability) : null;
		}
		PathStep next = steps.get(end + 1);
		// A bank visit, or a transport taken with banked items straight after it, stays where it is.
		if (next.getBankVisitState() != last.getBankVisitState()) return null;
		int[] origins = actionOrigins(tile, next.getPackedPosition(), route.cost(end + 1) - route.cost(end),
			last.getBankVisitState());
		return origins == null ? null : other -> contains(origins, other);
	}

	private static boolean contains(int[] values, int value)
	{
		for (int candidate : values)
			if (candidate == value) return true;
		return false;
	}

	/**
	 * The origins of the local transport action from {@code origin} to {@code destination} that cost
	 * {@code cost}, or {@code null} unless exactly one action class matches.
	 */
	private int[] actionOrigins(int origin, int destination, int cost, BankVisitState banked)
	{
		PreparedRoutingAccount.View view = account.localView(banked);
		int actionClass = Integer.MIN_VALUE;
		for (int i = IntArrays.lowerBound(view.origins, origin); i < view.destinations.length && view.origins[i] == origin; i++)
		{
			if (view.destinations[i] != destination || view.costs[i] != cost) continue;
			if (actionClass != Integer.MIN_VALUE && actionClass != view.actionClasses[i]) return null;
			actionClass = view.actionClasses[i];
		}
		if (actionClass == Integer.MIN_VALUE) return null;
		int[] origins = new int[4];
		int count = 0;
		for (int i = 0; i < view.destinations.length; i++)
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
		int index = IntArrays.lowerBound(view.origins, tile);
		return index < view.destinations.length && view.origins[index] == tile;
	}

	private static int direction(int dx, int dy)
	{
		for (int i = 0; i < 8; i++)
			if (GameWalkOrder.ORDER_DX[i] == dx && GameWalkOrder.ORDER_DY[i] == dy) return i;
		throw new IllegalArgumentException("not a walking move: " + dx + "," + dy);
	}

	/**
	 * Replaces {@code leg[from + 1..to]} by the game's walk, appending the index of each click along
	 * it to {@code clicks} and the index of each step the game does not walk to {@code kept}. A
	 * {@code goal} lets the leg end on another tile than {@code leg[to]}.
	 */
	private void rewriteLeg(List<PathStep> leg, int from, int to, IntPredicate goal, List<Integer> clicks,
		List<Integer> kept)
	{
		BankVisitState banked = leg.get(from).getBankVisitState();
		int start = leg.get(from).getPackedPosition();
		int plane = WorldPointUtil.unpackWorldPlane(start);
		// The search may cross a blocked tile the game's search cannot, so the game's walk to the leg's
		// end would not exist from before it: each side is rewritten on its own around the kept step.
		for (int k = from + 1; k < to; k++)
		{
			int tile = leg.get(k).getPackedPosition();
			if (!map.isBlocked(WorldPointUtil.unpackWorldX(tile), WorldPointUtil.unpackWorldY(tile), plane)) continue;
			if (k - 1 > from) rewriteLeg(leg, from, k - 1, null, clicks, kept);
			kept.add(k);
			rewriteLeg(leg, k, to, goal, clicks, kept);
			return;
		}
		int endTile = leg.get(to).getPackedPosition();
		// The step onto a blocked transport origin is the search's own: the game does not walk it.
		boolean blockedEnd = map.isBlocked(WorldPointUtil.unpackWorldX(endTile), WorldPointUtil.unpackWorldY(endTile), plane);
		int last = blockedEnd ? to - 1 : to;
		if (last > from)
		{
			if (walk(leg, from, last, blockedEnd ? null : goal, banked, plane))
			{
				// A transport may land on a blocked tile: the game walks off it, but is not clicked from it.
				int first = from;
				if (map.isBlocked(WorldPointUtil.unpackWorldX(start), WorldPointUtil.unpackWorldY(start), plane)) kept.add(++first);
				clicksAlong(leg, first, last, clicks);
			}
			else
			{
				// No game walk has the leg's length: the search's own steps stand.
				for (int k = from + 1; k <= last; k++) kept.add(k);
			}
		}
		if (blockedEnd) kept.add(to);
	}

	/**
	 * Walks {@code leg[from..last]} the way the game's search would from its start to its end: a
	 * breadth-first search in the game's order whose first-discoverer path back from the end is the
	 * game's walk. Only tiles that can lie on a walk of the leg's length are explored, so the end
	 * is found at exactly that length or the leg keeps its steps (returns false). With a
	 * {@code goal}, the end is the first tile it accepts that the search discovers at that length.
	 */
	private boolean walk(List<PathStep> leg, int from, int last, IntPredicate goal, BankVisitState banked, int plane)
	{
		int length = last - from;
		int sx = WorldPointUtil.unpackWorldX(leg.get(from).getPackedPosition());
		int sy = WorldPointUtil.unpackWorldY(leg.get(from).getPackedPosition());
		int ex = WorldPointUtil.unpackWorldX(leg.get(last).getPackedPosition());
		int ey = WorldPointUtil.unpackWorldY(leg.get(last).getPackedPosition());
		int side = 2 * length + 1, ox = sx - length, oy = sy - length;
		PagedSearchArray distance = new PagedSearchArray(side * side, UNVISITED);
		PagedSearchArray parent = new PagedSearchArray(side * side, UNVISITED);
		int start = length * side + length, found = -1;
		int[] queue = new int[256];
		int head = 0, tail = 0;
		distance.set(start, 0);
		queue[tail++] = start;
		while (head < tail && found < 0)
		{
			int at = queue[head++];
			int steps = distance.get(at);
			if (steps >= length) break;
			int x = ox + at % side, y = oy + at / side;
			int packed = WorldPointUtil.packWorldPoint(x, y, plane);
			int mask = map.ordinaryWalkingMask(packed);
			// A blocked start is walked off as the A* search does.
			if (at == start)
				for (int next : map.ordinaryWalkingNeighbors(packed))
					mask |= 1 << GameWalkOrder.ORDER_BITS[direction(WorldPointUtil.unpackWorldX(next) - x,
						WorldPointUtil.unpackWorldY(next) - y)];
			for (int i = 0; i < GameWalkOrder.ORDER_BITS.length && found < 0; i++)
			{
				if ((mask & (1 << GameWalkOrder.ORDER_BITS[i])) == 0) continue;
				int nx = x + GameWalkOrder.ORDER_DX[i], ny = y + GameWalkOrder.ORDER_DY[i];
				int next = (ny - oy) * side + nx - ox;
				int nextPacked = WorldPointUtil.packWorldPoint(nx, ny, plane);
				if (distance.get(next) != UNVISITED
					|| goal == null && steps + 1 + Math.max(Math.abs(nx - ex), Math.abs(ny - ey)) > length
					|| !restrictions.stepAllowed(packed, nextPacked))
				{
					continue;
				}
				distance.set(next, steps + 1);
				parent.set(next, at);
				if (goal == null ? nx == ex && ny == ey : steps + 1 == length && goal.test(nextPacked)) found = next;
				if (tail == queue.length) queue = Arrays.copyOf(queue, 2 * tail);
				queue[tail++] = next;
			}
		}
		if (found < 0) found = (ey - oy) * side + ex - ox;
		if (distance.get(found) != length) return false;
		for (int i = last, at = found; i > from; i--, at = parent.get(at))
			leg.set(i, new PathStep(WorldPointUtil.packWorldPoint(ox + at % side, oy + at / side, plane), banked));
		return true;
	}

	/**
	 * Appends the clicks along {@code leg[from..last]}, a walk the game takes: from each tile, the
	 * furthest one within a minimap click's reach whose path needs at most {@link #MAX_CHECKPOINTS}
	 * checkpoint tiles (its direction-change corners plus the destination), or the game would stop
	 * short of the click.
	 */
	private static void clicksAlong(List<PathStep> leg, int from, int last, List<Integer> clicks)
	{
		for (int at = from; at < last; )
		{
			int x = WorldPointUtil.unpackWorldX(leg.get(at).getPackedPosition());
			int y = WorldPointUtil.unpackWorldY(leg.get(at).getPackedPosition());
			int best = at + 1, turns = 0, previousDx = 0, previousDy = 0;
			for (int k = at + 1; k <= last; k++)
			{
				int tx = WorldPointUtil.unpackWorldX(leg.get(k).getPackedPosition());
				int ty = WorldPointUtil.unpackWorldY(leg.get(k).getPackedPosition());
				int dx = tx - WorldPointUtil.unpackWorldX(leg.get(k - 1).getPackedPosition());
				int dy = ty - WorldPointUtil.unpackWorldY(leg.get(k - 1).getPackedPosition());
				if (k > at + 1 && (dx != previousDx || dy != previousDy)) turns++;
				if (turns + 1 > MAX_CHECKPOINTS || (tx - x) * (tx - x) + (ty - y) * (ty - y) > CLICK_RADIUS * CLICK_RADIUS) break;
				previousDx = dx;
				previousDy = dy;
				best = k;
			}
			clicks.add(best);
			at = best;
		}
	}
}
