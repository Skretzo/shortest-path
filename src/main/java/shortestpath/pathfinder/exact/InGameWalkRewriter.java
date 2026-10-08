package shortestpath.pathfinder.exact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;

/**
 * Rewrites the canonical walking legs of an exact route into the tiles the game's own walking
 * takes when the player clicks along them, without changing what the route costs or does.
 * <p>
 * {@link ExactWalkCanonicalizer} picks the intended walk of each leg: the cleanest of its equally
 * cheap walks. The player does not walk tiles, though; they click a tile, and the game runs a
 * breadth-first search over a 128x128 window centred on them, expanding neighbours in the order
 * W, E, S, N, SW, SE, NW, NE, and walks the path traced back from the clicked tile along each
 * tile's first discoverer. That path need not be the intended one even when it is as short.
 * <p>
 * Each leg is split into clicks on its intended walk. From the leg's current tile, the click is
 * the furthest later tile of the leg within {@link #DEFAULT_CLICK_RADIUS a minimap click's reach}
 * whose in-game path is exactly as long as the intended walk to it, and the intended walk up to it
 * is replaced by that in-game path. The in-game path may leave the intended walk between clicks;
 * every click is on it. A shorter in-game path is refused too, as it would change the route's
 * cost. So is one whose traced path steps through a tile the search's {@link SearchRestrictions}
 * gate off — the game does not know those gates, so keeping the canonical step preserves both
 * the cost and the restriction. A step no click realises (one the game does not walk, such as
 * onto a blocked transport origin) keeps the intended step. Legs keep their tiles at both ends
 * and their length, so every other step of the route, and the route's cost, are unchanged.
 */
public final class InGameWalkRewriter
{
	/** Roughly the reach of a minimap click, in tiles. */
	public static final int DEFAULT_CLICK_RADIUS = 15;

	private static final int WINDOW = 128;
	private static final int HALF_WINDOW = WINDOW / 2;
	private static final int UNVISITED = -1;
	// The game's expansion order lives in GameWalkOrder, shared with the canonicaliser.

	private final CollisionMap map;
	private final int clickRadius;
	private final SearchRestrictions restrictions;
	private final int[] distance = new int[WINDOW * WINDOW];
	private final int[] parent = new int[WINDOW * WINDOW];
	private final int[] queue = new int[WINDOW * WINDOW];

	public InGameWalkRewriter(CollisionMap map)
	{
		this(map, DEFAULT_CLICK_RADIUS, SearchRestrictions.none());
	}

	public InGameWalkRewriter(CollisionMap map, int clickRadius)
	{
		this(map, clickRadius, SearchRestrictions.none());
	}

	/**
	 * @param restrictions the search's positional gates: the spliced in-game paths must obey them
	 * too, or a leg could be drawn through tiles the forward search was not allowed to enter
	 */
	public InGameWalkRewriter(CollisionMap map, SearchRestrictions restrictions)
	{
		this(map, DEFAULT_CLICK_RADIUS, restrictions);
	}

	public InGameWalkRewriter(CollisionMap map, int clickRadius, SearchRestrictions restrictions)
	{
		if (map == null || restrictions == null) throw new NullPointerException();
		if (clickRadius < 2) throw new IllegalArgumentException("click radius must reach a diagonal neighbour");
		this.map = map;
		this.clickRadius = clickRadius;
		this.restrictions = restrictions;
	}

	/** A rewritten route and the indices of its steps the player clicks to walk it. */
	public static final class Result
	{
		private final List<PathStep> path;
		private final List<Integer> clickPoints;
		private final List<Integer> keptStepIndices;

		Result(List<PathStep> path, List<Integer> clickPoints, List<Integer> keptStepIndices)
		{
			this.path = path;
			this.clickPoints = clickPoints;
			this.keptStepIndices = keptStepIndices;
		}

		/** The route, as long as the canonical one and with the same steps outside its walking legs. */
		public List<PathStep> path()
		{
			return path;
		}

		/**
		 * Ascending indices into {@link #path()} of the tiles to click, one per click. Every
		 * index is a real click: the game's own walking path lands on that tile.
		 */
		public List<Integer> clickPoints()
		{
			return clickPoints;
		}

		/**
		 * Ascending indices into {@link #path()} of the steps that kept the canonical step
		 * because no in-game click realises them; they are not clicks.
		 */
		public List<Integer> keptStepIndices()
		{
			return keptStepIndices;
		}

		/** Walking steps that kept the canonical step because no in-game click realises them. */
		public int keptSteps()
		{
			return keptStepIndices.size();
		}
	}

	/** Rewrites the walking legs of a canonicalised route; every other step is kept. */
	public Result rewrite(ExactWalkCanonicalizer.Result canonical)
	{
		int[] legBounds = new int[2 * canonical.legs()];
		for (int leg = 0; leg < canonical.legs(); leg++)
		{
			legBounds[2 * leg] = canonical.legStart(leg);
			legBounds[2 * leg + 1] = canonical.legEnd(leg);
		}
		return rewrite(canonical.path(), legBounds);
	}

	/**
	 * Rewrites the walking legs of {@code path}: leg {@code k} runs from step {@code legBounds[2k]}
	 * to step {@code legBounds[2k + 1]}, and its steps are single-tile walking moves.
	 */
	Result rewrite(List<PathStep> path, int[] legBounds)
	{
		List<PathStep> rewritten = new ArrayList<>(path);
		List<Integer> clicks = new ArrayList<>();
		List<Integer> kept = new ArrayList<>();
		for (int leg = 0; leg < legBounds.length; leg += 2)
			rewriteLeg(path, legBounds[leg], legBounds[leg + 1], rewritten, clicks, kept);
		return new Result(List.copyOf(rewritten), List.copyOf(clicks), List.copyOf(kept));
	}

	/**
	 * Replaces {@code rewritten[from + 1..to]} by the in-game walk along the clicks on
	 * {@code path[from..to]}, appending each click's index to {@code clicks} and the index of
	 * each step the game cannot be made to walk to {@code kept}.
	 */
	private void rewriteLeg(List<PathStep> path, int from, int to, List<PathStep> rewritten,
		List<Integer> clicks, List<Integer> kept)
	{
		boolean banked = path.get(from).isBankVisited();
		int current = from;
		while (current < to)
		{
			int source = path.get(current).getPackedPosition();
			int sx = WorldPointUtil.unpackWorldX(source), sy = WorldPointUtil.unpackWorldY(source);
			int plane = WorldPointUtil.unpackWorldPlane(source);
			int originX = sx - HALF_WINDOW, originY = sy - HALF_WINDOW;

			// Leg tiles within reach; the BFS need only run as deep as the furthest of them.
			int deepest = 1;
			for (int i = current + 1; i <= to; i++)
				if (withinReach(sx, sy, path.get(i).getPackedPosition())) deepest = i - current;
			search(originX, originY, plane, sx, sy, deepest);

			int click = -1;
			for (int i = current + deepest; i > current && click < 0; i--)
			{
				int tile = path.get(i).getPackedPosition();
				if (!withinReach(sx, sy, tile)) continue;
				// withinReach bounds the tile to (64±15) locally, inside the window: never -1.
				int local = localIndex(originX, originY, tile);
				// Exactly as long as the canonical walk: no shorter, which would change the cost.
				if (distance[local] != i - current) continue;
				// The traced game path must also walk only steps the search's restrictions allow;
				// a candidate that violates them is refused, and a nearer one is tried instead.
				if (traceAllowed(originX, originY, plane, local, i - current)) click = i;
			}

			if (click < 0)
			{
				// Not a step the game walks; keep the canonical one. Record it as a kept step,
				// not a click: no click's in-game path lands on it, so showing it as a click
				// point would mark a tile the game cannot be made to walk to.
				kept.add(current + 1);
				current++;
			}
			else
			{
				int at = localIndex(originX, originY, path.get(click).getPackedPosition());
				for (int i = click; i > current; i--, at = parent[at])
					rewritten.set(i, new PathStep(WorldPointUtil.packWorldPoint(originX + at % WINDOW,
						originY + at / WINDOW, plane), banked));
				clicks.add(click);
				current = click;
			}
		}
	}

	/**
	 * Whether the in-game path traced back {@code hops} steps from local index {@code at} may be
	 * spliced into the leg: every hop must be a step the search's restrictions allow, directed
	 * from the tile nearer the click's source to the tile it lands on.
	 */
	private boolean traceAllowed(int originX, int originY, int plane, int at, int hops)
	{
		for (int i = 0; i < hops; i++, at = parent[at])
		{
			int from = parent[at];
			if (!restrictions.stepAllowed(
				WorldPointUtil.packWorldPoint(originX + from % WINDOW, originY + from / WINDOW, plane),
				WorldPointUtil.packWorldPoint(originX + at % WINDOW, originY + at / WINDOW, plane)))
			{
				return false;
			}
		}
		return true;
	}

	private boolean withinReach(int sx, int sy, int tile)
	{
		int dx = WorldPointUtil.unpackWorldX(tile) - sx, dy = WorldPointUtil.unpackWorldY(tile) - sy;
		return dx * dx + dy * dy <= clickRadius * clickRadius;
	}

	private static int localIndex(int originX, int originY, int tile)
	{
		int x = WorldPointUtil.unpackWorldX(tile) - originX, y = WorldPointUtil.unpackWorldY(tile) - originY;
		return x < 0 || y < 0 || x >= WINDOW || y >= WINDOW ? -1 : y * WINDOW + x;
	}

	/** The game's walking BFS from {@code (sx, sy)}, stopped once every tile within {@code depth} is found. */
	private void search(int originX, int originY, int plane, int sx, int sy, int depth)
	{
		Arrays.fill(distance, UNVISITED);
		int start = (sy - originY) * WINDOW + (sx - originX);
		distance[start] = 0;
		parent[start] = start;
		int head = 0, tail = 0;
		queue[tail++] = start;
		while (head < tail)
		{
			int at = queue[head++];
			if (distance[at] >= depth) break;
			int x = at % WINDOW, y = at / WINDOW;
			int mask = map.ordinaryWalkingMask(WorldPointUtil.packWorldPoint(originX + x, originY + y, plane));
			for (int i = 0; i < GameWalkOrder.ORDER_BITS.length; i++)
			{
				if ((mask & (1 << GameWalkOrder.ORDER_BITS[i])) == 0) continue;
				int nx = x + GameWalkOrder.ORDER_DX[i], ny = y + GameWalkOrder.ORDER_DY[i];
				if (nx < 0 || ny < 0 || nx >= WINDOW || ny >= WINDOW) continue;
				int next = ny * WINDOW + nx;
				if (distance[next] != UNVISITED) continue;
				distance[next] = distance[at] + 1;
				parent[next] = at;
				queue[tail++] = next;
			}
		}
	}
}
