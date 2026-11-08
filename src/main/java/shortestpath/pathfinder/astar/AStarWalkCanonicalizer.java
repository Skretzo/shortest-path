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
 * Each leg is split into clicks. A backward search from the leg's end finds every tile that can lie
 * on a walk of the leg's length, with the least axis travel (|dx| + |dy| over the moves) left from
 * it. From the leg's current tile, the game's search finds the tiles in its window that lie exactly
 * as far as the leg has left to walk to the end, and the click is the one within
 * {@link #CLICK_RADIUS a minimap click's reach} with the least axis travel, the game's path to it
 * included. The walk is as direct as the leg's ticks allow, whatever detour the A* search took, and
 * the player clicks on the way the game walks to where they are going.
 * A candidate whose traced
 * path steps through a tile the search's {@link SearchRestrictions} gate off is refused, since the game does
 * not know those gates and keeping the search's step preserves both the cost and the restriction,
 * and so is one needing more than {@link #MAX_CHECKPOINTS} checkpoint tiles, which the game would walk
 * only up to the 25th corner. A step no click realises is kept: the search's own step onto a
 * blocked transport origin, and the game's first step on its way off a blocked tile a transport
 * landed on. The client's fallback search for an approximate target when a click is unreachable is
 * deliberately not emulated. Legs keep their length and their first tile, so every other step of
 * the route, and the route's cost, are unchanged.
 * <p>
 * A leg need not end on the tile the search chose. The leg before a global teleport may end on any
 * tile the teleport can be cast from, and the leg before a transport on any origin of the same
 * action (the prepared account's local action class). When the leg is within one click of its
 * end, it ends on the first such tile the game's search discovers at exactly the leg's remaining
 * length, so the cost is unchanged; a qualifying tile nearer than that is never taken, and
 * without one the leg ends on the search's own end tile. Every other leg keeps its end tile: the
 * final destination, a bank visit, or a transport taken straight after banking.
 */
public final class AStarWalkCanonicalizer
{
	/** Roughly the reach of a minimap click, in tiles. */
	static final int CLICK_RADIUS = 35;

	/**
	 * Checkpoint tiles the client keeps for one click's walk: the direction-change corners of
	 * the traced path plus the destination. A click needing more is walked only up to the 25th
	 * checkpoint and the player stops short, so such a candidate is refused rather than drawn.
	 */
	private static final int MAX_CHECKPOINTS = 25;

	private static final int WINDOW = 128;
	private static final int HALF_WINDOW = WINDOW / 2;
	private static final int UNVISITED = -1;
	// The game's expansion order lives in GameWalkOrder.

	private final CollisionMap map;
	private final PreparedRoutingAccount account;
	private final SearchRestrictions restrictions;
	private final int[] distance = new int[WINDOW * WINDOW];
	private final int[] parent = new int[WINDOW * WINDOW];
	private final int[] queue = new int[WINDOW * WINDOW];
	private final int[] axis = new int[WINDOW * WINDOW];
	// The current leg's walking distance to its end: see reverse().
	private PagedSearchArray toEnd, axisToEnd;
	private int endX, endY, endSide;

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
	 * Replaces {@code leg[from + 1..to]} by the in-game walk along its clicks, appending each click's
	 * index to {@code clicks} and the index of each step no click realises to {@code kept}. Within
	 * one click of {@code to}, a {@code goal} lets the walk end on another tile than {@code leg[to]}.
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
		int ex = WorldPointUtil.unpackWorldX(leg.get(last).getPackedPosition());
		int ey = WorldPointUtil.unpackWorldY(leg.get(last).getPackedPosition());
		if (last > from) reverse(plane, ex, ey, WorldPointUtil.unpackWorldX(start), WorldPointUtil.unpackWorldY(start), last - from);
		int current = from;
		while (current < last)
		{
			int source = leg.get(current).getPackedPosition();
			int sx = WorldPointUtil.unpackWorldX(source), sy = WorldPointUtil.unpackWorldY(source);
			int originX = sx - HALF_WINDOW, originY = sy - HALF_WINDOW;
			// A transport may land on a blocked tile: the game walks off it, but is not clicked from it.
			boolean blocked = map.isBlocked(sx, sy, plane);
			int remaining = last - current;
			// Once the leg's end is a click away, it may end on any tile the goal accepts; the BFS
			// then runs as deep as the leg is long, to see every tile that far from the source.
			boolean finalStretch = goal != null && !blocked && to - current <= CLICK_RADIUS;
			int discovered = search(originX, originY, plane, sx, sy,
				finalStretch ? to - current : blocked ? 1 : Math.min(remaining, HALF_WINDOW),
				finalStretch ? -1 : remaining);

			if (finalStretch)
			{
				int end = goalTile(goal, originX, originY, plane, sx, sy, to - current, discovered);
				if (end >= 0)
				{
					splice(leg, to, current, end, originX, originY, plane, banked);
					clicks.add(to);
					return;
				}
			}

			// Of the tiles the game finds exactly as far as the leg has left to walk to the end, in
			// one click's reach, the one with the least axis travel left: the walk stays as direct as
			// the leg's ticks allow, whatever detour the search took. A trace the restrictions or
			// checkpoints refuse is skipped.
			int best = -1, bestAxis = Integer.MAX_VALUE;
			for (int k = 1; k < discovered; k++)
			{
				int at = queue[k];
				int x = originX + at % WINDOW, y = originY + at / WINDOW;
				int travel = remaining - distance[at] < 0 || toEnd(x, y) != remaining - distance[at] ? Integer.MAX_VALUE : axis[at] + axisToEnd(x, y);
				if (travel == Integer.MAX_VALUE || travel > bestAxis || travel == bestAxis && distance[at] <= distance[best]
					|| !withinReach(sx, sy, WorldPointUtil.packWorldPoint(x, y, plane))
					|| !traceAllowed(originX, originY, plane, at, distance[at]))
				{
					continue;
				}
				best = at;
				bestAxis = travel;
			}
			if (best < 0)
			{
				// No step of the game's keeps the leg's length: the search's own step stands.
				kept.add(++current);
				continue;
			}
			current += distance[best];
			splice(leg, current, current - distance[best], best, originX, originY, plane, banked);
			if (blocked) kept.add(current);
			else clicks.add(current);
		}
		if (blockedEnd) kept.add(to);
	}

	/**
	 * Fills {@code toEnd} with the game's walking distance to {@code (ex, ey)} over the tiles that can
	 * lie on a walk of {@code length} steps from {@code (ox, oy)}: a backward breadth-first search over
	 * the steps the search's restrictions allow, pruned where even a straight walk from the origin
	 * could not make it.
	 */
	private void reverse(int plane, int ex, int ey, int ox, int oy, int length)
	{
		endX = ex - length;
		endY = ey - length;
		endSide = 2 * length + 1;
		toEnd = new PagedSearchArray(endSide * endSide, UNVISITED);
		axisToEnd = new PagedSearchArray(endSide * endSide, 0);
		int[] frontier = new int[1024];
		int head = 0, tail = 0;
		toEnd.set(length * endSide + length, 0);
		frontier[tail++] = length * endSide + length;
		while (head < tail)
		{
			int at = frontier[head++];
			int x = endX + at % endSide, y = endY + at / endSide;
			int steps = toEnd.get(at) + 1;
			int next = WorldPointUtil.packWorldPoint(x, y, plane);
			for (int i = 0; i < GameWalkOrder.ORDER_BITS.length; i++)
			{
				int px = x - GameWalkOrder.ORDER_DX[i], py = y - GameWalkOrder.ORDER_DY[i];
				int seen = toEnd(px, py);
				if (seen != UNVISITED && seen != steps || px < endX || py < endY || px >= endX + endSide
					|| py >= endY + endSide || Math.max(Math.abs(px - ox), Math.abs(py - oy)) + steps > length)
				{
					continue;
				}
				int previous = WorldPointUtil.packWorldPoint(px, py, plane);
				if ((map.ordinaryWalkingMask(previous) & 1 << GameWalkOrder.ORDER_BITS[i]) == 0
					|| !restrictions.stepAllowed(previous, next))
				{
					continue;
				}
				int index = (py - endY) * endSide + px - endX;
				int travel = axisToEnd.get(at) + Math.abs(GameWalkOrder.ORDER_DX[i]) + Math.abs(GameWalkOrder.ORDER_DY[i]);
				if (seen == UNVISITED)
				{
					if (tail == frontier.length) frontier = Arrays.copyOf(frontier, 2 * tail);
					toEnd.set(index, steps);
					axisToEnd.set(index, travel);
					frontier[tail++] = index;
				}
				else if (travel < axisToEnd.get(index)) axisToEnd.set(index, travel);
			}
		}
	}

	private int axisToEnd(int x, int y)
	{
		return axisToEnd.get((y - endY) * endSide + x - endX);
	}

	/** The distance to the leg's end found by {@link #reverse}, or -1 for a tile it did not reach. */
	private int toEnd(int x, int y)
	{
		int ix = x - endX, iy = y - endY;
		return ix < 0 || iy < 0 || ix >= endSide || iy >= endSide ? UNVISITED : toEnd.get(iy * endSide + ix);
	}

	/**
	 * The local index of the first tile the game's search discovered at exactly {@code remaining}
	 * steps that {@code goal} accepts, is within a click's reach and whose traced path the
	 * restrictions allow; -1 when there is none. A tile nearer than {@code remaining} is never
	 * taken: the leg would end short of its length and change the route's cost.
	 */
	private int goalTile(IntPredicate goal, int originX, int originY, int plane, int sx, int sy, int remaining,
		int discovered)
	{
		for (int k = 0; k < discovered; k++)
		{
			int at = queue[k];
			if (distance[at] != remaining) continue;
			int tile = WorldPointUtil.packWorldPoint(originX + at % WINDOW, originY + at / WINDOW, plane);
			if (withinReach(sx, sy, tile) && goal.test(tile) && traceAllowed(originX, originY, plane, at, remaining))
				return at;
		}
		return -1;
	}

	/** Writes the traced path from the search's source to local index {@code at} over {@code rewritten[from + 1..click]}. */
	private void splice(List<PathStep> rewritten, int click, int from, int at, int originX, int originY, int plane,
		BankVisitState banked)
	{
		for (int i = click; i > from; i--, at = parent[at])
			rewritten.set(i, new PathStep(WorldPointUtil.packWorldPoint(originX + at % WINDOW,
				originY + at / WINDOW, plane), banked));
	}

	/**
	 * Whether the in-game path traced back {@code hops} steps from local index {@code at} may be
	 * spliced into the leg: every hop must be a step the search's restrictions allow, directed
	 * from the tile nearer the click's source to the tile it lands on, and the path must fit the
	 * client's per-click budget of {@link #MAX_CHECKPOINTS} checkpoint tiles (its
	 * direction-change corners plus the destination), or the game would stop short of the click.
	 */
	private boolean traceAllowed(int originX, int originY, int plane, int at, int hops)
	{
		int previousDx = 0, previousDy = 0, turns = 0;
		for (int i = 0; i < hops; i++, at = parent[at])
		{
			int from = parent[at];
			if (!restrictions.stepAllowed(
				WorldPointUtil.packWorldPoint(originX + from % WINDOW, originY + from / WINDOW, plane),
				WorldPointUtil.packWorldPoint(originX + at % WINDOW, originY + at / WINDOW, plane)))
			{
				return false;
			}
			int dx = at % WINDOW - from % WINDOW, dy = at / WINDOW - from / WINDOW;
			if (i > 0 && (dx != previousDx || dy != previousDy)) turns++;
			previousDx = dx;
			previousDy = dy;
		}
		return turns + 1 <= MAX_CHECKPOINTS;
	}

	private boolean withinReach(int sx, int sy, int tile)
	{
		int dx = WorldPointUtil.unpackWorldX(tile) - sx, dy = WorldPointUtil.unpackWorldY(tile) - sy;
		return dx * dx + dy * dy <= CLICK_RADIUS * CLICK_RADIUS;
	}

	/**
	 * The game's walking BFS from {@code (sx, sy)}, stopped once every tile within {@code depth} is
	 * found. Returns how many tiles it discovered; {@code queue} lists them in discovery order. With
	 * {@code remaining} not negative, only tiles that lie on a walk of that many steps to the leg's
	 * end are explored: the others cannot be clicked, and leaving them out changes neither the
	 * distances nor the first discoverers of those that can.
	 */
	private int search(int originX, int originY, int plane, int sx, int sy, int depth, int remaining)
	{
		Arrays.fill(distance, UNVISITED);
		int start = (sy - originY) * WINDOW + (sx - originX);
		distance[start] = 0;
		parent[start] = start;
		axis[start] = 0;
		int head = 0, tail = 0;
		queue[tail++] = start;
		while (head < tail)
		{
			int at = queue[head++];
			if (distance[at] >= depth) break;
			int x = at % WINDOW, y = at / WINDOW;
			int packed = WorldPointUtil.packWorldPoint(originX + x, originY + y, plane);
			int mask = map.ordinaryWalkingMask(packed);
			// A blocked start is walked off as the A* search does; any other start's neighbours are
			// its mask's own moves.
			if (at == start)
				for (int next : map.ordinaryWalkingNeighbors(packed))
					mask |= 1 << GameWalkOrder.ORDER_BITS[direction(WorldPointUtil.unpackWorldX(next) - originX - x,
						WorldPointUtil.unpackWorldY(next) - originY - y)];
			for (int i = 0; i < GameWalkOrder.ORDER_BITS.length; i++)
			{
				if ((mask & (1 << GameWalkOrder.ORDER_BITS[i])) == 0) continue;
				int nx = x + GameWalkOrder.ORDER_DX[i], ny = y + GameWalkOrder.ORDER_DY[i];
				if (nx < 0 || ny < 0 || nx >= WINDOW || ny >= WINDOW) continue;
				int next = ny * WINDOW + nx;
				if (distance[next] != UNVISITED
					|| remaining >= 0 && toEnd(originX + nx, originY + ny) != remaining - distance[at] - 1)
				{
					continue;
				}
				distance[next] = distance[at] + 1;
				parent[next] = at;
				axis[next] = axis[at] + Math.abs(GameWalkOrder.ORDER_DX[i]) + Math.abs(GameWalkOrder.ORDER_DY[i]);
				queue[tail++] = next;
			}
		}
		return tail;
	}
}
