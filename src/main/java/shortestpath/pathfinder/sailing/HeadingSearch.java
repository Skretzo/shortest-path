package shortestpath.pathfinder.sailing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;

/**
 * A search over a corridor of tiles for drawing a stretch at sea, with the boat's heading in the state: the boat turns
 * one heading a tick and moves as it turns, so going into a move it may have turned by at most as many headings as the
 * move takes ticks. Of the ways that take the fewest ticks, it finds one with the fewest turns: a turn costs no time,
 * so this only chooses between ways that take as long, and it favours holding a heading for a long way and turning
 * once, as a player at the helm does.
 */
final class HeadingSearch
{
	private static final int HEADINGS = 16;
	// Costs are (ticks, turns), compared ticks first: ticks in the high bits
	private static final int TURN_BITS = 20;
	// What the estimates hold for a tile with no way to an end
	private static final int NO_ROUTE = Integer.MAX_VALUE;

	/** The boat's tiles over a stretch, the heading it holds at the end (as set out, if it made no move) and its ticks. */
	static final class Sailed
	{
		final int[] tiles;
		final int heading;
		final int ticks;

		Sailed(int[] tiles, int heading, int ticks)
		{
			this.tiles = tiles;
			this.heading = heading;
			this.ticks = ticks;
		}
	}

	/** Where a stretch may end: on {@code tile}, holding {@code heading}. */
	interface End
	{
		boolean at(int tile, int heading);
	}

	private HeadingSearch()
	{
	}

	/**
	 * The boat's tiles sailing {@code moves} from one of {@code from}, holding {@code heading} there (-1 any), to an
	 * {@code end}, over the tiles {@code estimates} holds a number for (its estimate of the ticks left), each move ending
	 * where {@code landing} lets it; null if there is no way within them. {@code steered}, the boat turns at the turn
	 * rate and the fewest turns break ties; else only ticks count and the boat may turn any way into a move, which is
	 * quicker to search and enough to time a way.
	 */
	static Sailed sail(CollisionMap map, SailingMoves moves, BoatHull hull, IntPredicate landing, SeaGate gate,
		int[] from, int heading, TileInts estimates, List<Integer> corridor, End end, boolean steered, BooleanSupplier cancelled)
	{
		// Steered, a state is a tile and a heading; else just a tile, and only ticks count
		int headings = steered ? HEADINGS : 1;
		// Number the corridor's tiles
		TileInts index = new TileInts(map.regionExtent(), -1, -1);
		int[] tiles = new int[corridor.size()];
		int count = 0;
		for (int tile : corridor)
		{
			if (index.get(tile) >= 0) continue;
			index.set(tile, count);
			tiles[count++] = tile;
		}
		long[] best = new long[count * headings];
		Arrays.fill(best, Long.MAX_VALUE);
		int[] parent = new int[count * headings];
		PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(b[1], a[1]));
		for (int tile : from)
		{
			int i = index.get(tile);
			if (i < 0 || estimates.get(tile) == NO_ROUTE) continue;
			for (int h = 0; h < headings; h++)
			{
				if (steered && heading >= 0 && h != heading) continue;
				int state = i * headings + h;
				best[state] = 0;
				parent[state] = -1;
				open.add(new long[] {(long) estimates.get(tile) << TURN_BITS, 0, state});
			}
		}
		while (!open.isEmpty())
		{
			if (cancelled.getAsBoolean()) return null;
			long[] top = open.poll();
			int state = (int) top[2];
			long cost = top[1];
			if (cost > best[state]) continue;
			int tile = tiles[state / headings], facing = steered ? state % headings : -1;
			if (end.at(tile, facing))
			{
				return new Sailed(path(tiles, parent, state, headings), parent[state] == -1 && heading < 0 ? -1 : facing,
					(int) (cost >>> TURN_BITS));
			}
			int x = WorldPointUtil.unpackWorldX(tile), y = WorldPointUtil.unpackWorldY(tile);
			for (int move = 0; move < moves.size(); move++)
			{
				int toHeading = moves.heading(move), ticks = moves.ticks(move);
				int turn = Math.abs(toHeading - facing);
				turn = steered ? Math.min(turn, HEADINGS - turn) : 0;
				if (turn > ticks) continue;
				int to = WorldPointUtil.packWorldPoint(x + moves.dx(move), y + moves.dy(move), 0);
				int j = index.get(to);
				int estimate = estimates.get(to);
				if (j < 0 || estimate == NO_ROUTE || !landing.test(to)) continue;
				long next = cost + ((long) ticks << TURN_BITS) + (turn > 0 ? 1 : 0);
				int nextState = j * headings + (steered ? toHeading : 0);
				if (next >= best[nextState]) continue;
				if (!hull.canMove(map, x, y, 0, toHeading, moves.dx(move), moves.dy(move))) continue;
				if (gate != SeaGate.NONE && !gated(gate, tile, to, x, y)) continue;
				best[nextState] = next;
				parent[nextState] = state;
				open.add(new long[] {next + ((long) estimate << TURN_BITS), next, nextState});
			}
		}
		return null;
	}

	/**
	 * The boat's tiles sailing {@code moves} from one of {@code from}, holding {@code heading} (-1 any), along the
	 * {@code routes} (lines through tiles, ending together), within {@code corridor} tiles of one: to an {@code end}
	 * within {@code reach} tiles of where they end, by the fewest ticks and then the fewest turns. Each tile in the
	 * corridor is estimated at the open-water ticks to the end of the nearest stretch of a route and along it from there,
	 * the least of the routes; null if no way in the corridor.
	 */
	static Sailed along(CollisionMap map, SailingMoves moves, BoatHull hull, IntPredicate landing, SeaGate gate,
		int[] from, int heading, List<int[]> routes, End end, double reach, int corridor, boolean steered, BooleanSupplier cancelled)
	{
		double slack = reach * moves.ticksPerTile();
		TileInts estimates = new TileInts(map.regionExtent(), NO_ROUTE, NO_ROUTE);
		List<Integer> tiles = new ArrayList<>();
		double least = Double.MAX_VALUE;
		for (int[] route : routes)
		{
			int n = route.length;
			double[] xs = new double[n], ys = new double[n];
			for (int k = 0; k < n; k++)
			{
				xs[k] = WorldPointUtil.unpackWorldX(route[k]);
				ys[k] = WorldPointUtil.unpackWorldY(route[k]);
			}
			// Ticks along the route from each point to the end
			double[] rest = new double[n];
			for (int k = n - 2; k >= 0; k--) rest[k] = rest[k + 1] + moves.openWaterTicks(xs[k + 1] - xs[k], ys[k + 1] - ys[k]);
			least = Math.min(least, rest[0]);
			for (int k = 0; k + 1 < n; k++)
			{
				double dx = xs[k + 1] - xs[k], dy = ys[k + 1] - ys[k], length = Math.hypot(dx, dy);
				double ux = length == 0 ? 0 : dx / length, uy = length == 0 ? 0 : dy / length;
				int steps = (int) Math.ceil(length * 2);
				for (int step = 0; step <= steps; step++)
				{
					double cx = xs[k] + dx * step / Math.max(1, steps), cy = ys[k] + dy * step / Math.max(1, steps);
					for (int across = -corridor; across <= corridor; across++)
					{
						for (int along = -1; along <= 1; along++)
						{
							int tile = tile(cx - uy * across + ux * along, cy + ux * across + uy * along);
							double ahead = moves.openWaterTicks(xs[k + 1] - WorldPointUtil.unpackWorldX(tile),
								ys[k + 1] - WorldPointUtil.unpackWorldY(tile));
							int estimate = (int) Math.max(0, Math.floor(ahead + rest[k + 1] - slack));
							int known = estimates.get(tile);
							if (known == NO_ROUTE) tiles.add(tile);
							if (estimate < known) estimates.set(tile, estimate);
						}
					}
				}
			}
		}
		// Where it sets out may lie off the routes' ends: it is in the corridor whatever
		for (int tile : from)
		{
			if (estimates.get(tile) != NO_ROUTE) continue;
			estimates.set(tile, (int) Math.max(0, Math.floor(least - slack)));
			tiles.add(tile);
		}
		return sail(map, moves, hull, landing, gate, from, heading, estimates, tiles, end, steered, cancelled);
	}

	private static int tile(double x, double y)
	{
		return WorldPointUtil.packWorldPoint((int) Math.round(x), (int) Math.round(y), 0);
	}

	// Whether the gate lets the move through, its midpoint too, as the sailing solver checks it
	private static boolean gated(SeaGate gate, int from, int to, int x, int y)
	{
		int middle = WorldPointUtil.packWorldPoint((x + WorldPointUtil.unpackWorldX(to)) / 2,
			(y + WorldPointUtil.unpackWorldY(to)) / 2, 0);
		return gate.allowed(from, to) && gate.allowed(from, middle) && gate.allowed(middle, to);
	}

	private static int[] path(int[] tiles, int[] parent, int state, int headings)
	{
		List<Integer> reversed = new ArrayList<>();
		for (int at = state; at != -1; at = parent[at]) reversed.add(tiles[at / headings]);
		int[] path = new int[reversed.size()];
		for (int i = 0; i < path.length; i++) path[i] = reversed.get(path.length - 1 - i);
		return path;
	}
}
