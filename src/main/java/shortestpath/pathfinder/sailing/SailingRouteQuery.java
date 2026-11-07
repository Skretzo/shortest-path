package shortestpath.pathfinder.sailing;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import lombok.Getter;
import lombok.experimental.Accessors;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.astar.CostArithmetic;
import shortestpath.pathfinder.astar.SideGraph;

/**
 * What sailing adds to one route query: the sea beside the walk ({@link SeaSearch}), which targets are walked to rather
 * than sailed to, the heuristic's targets for those sailed to, and, once a route is found, its stretches at sea drawn.
 * Targets at sea that a boat can sail to are reached aboard, never snapped to the shore.
 */
public final class SailingRouteQuery
{
	/** The heuristic's accepted layers for a boarding: reached with or without a bank visit. */
	private static final int ANY_BANK_STATE = 3;

	/** The heuristic's targets: tiles, the bank states each accepts, and the cost left from each. */
	@Getter
	@Accessors(fluent = true)
	public static final class HeuristicTargets
	{
		private final int[] tiles, layers, seedCosts;

		HeuristicTargets(int[] tiles, int[] layers, int[] seedCosts)
		{
			this.tiles = tiles;
			this.layers = layers;
			this.seedCosts = seedCosts;
		}
	}

	private final int start;
	// The sea, or null when the query can't sail
	private final SeaSearch sea;
	private final Set<Integer> landTargets;
	// Time spent drawing the found route's stretches at sea, in nanoseconds
	private long fillNanos;

	private SailingRouteQuery(int start, SeaSearch sea, Set<Integer> landTargets)
	{
		this.start = start;
		this.sea = sea;
		this.landTargets = landTargets;
	}

	/**
	 * The sailing for a query from {@code start} to {@code targets} with {@code capability}, every run checked against
	 * {@code gate}, the walking search's positional gates.
	 */
	public static SailingRouteQuery prepare(CollisionMap map, PreparedSailingCapability capability, int start,
		Set<Integer> targets, SeaGate gate)
	{
		if (!capability.active()) return new SailingRouteQuery(start, null, targets);
		SailingWaters waters = SailingWaters.of(map);
		int[] seaTargets = targets.stream().filter(waters::contains).mapToInt(Integer::intValue).toArray();
		SeaSearch sea = new SeaSearch(map, capability, start, seaTargets, gate);
		if (!sea.any()) return new SailingRouteQuery(start, null, targets);
		// A target at sea a boat can sail to is reached aboard, never walked to
		Set<Integer> landTargets = new HashSet<>(targets);
		for (int i = 0; i < seaTargets.length; i++) if (sea.reaches(i)) landTargets.remove(seaTargets[i]);
		return new SailingRouteQuery(start, sea, landTargets);
	}

	/** Whether the search has the sea beside the walk. */
	public boolean hasSea()
	{
		return sea != null;
	}

	/** The sea, as the search's side graph. */
	public SideGraph side()
	{
		return sea == null ? SideGraph.NONE : sea;
	}

	/** The targets walked to: all of them but those reached aboard. */
	public Set<Integer> landTargets()
	{
		return landTargets;
	}

	/**
	 * The heuristic's targets: the land goals {@code tiles} with their {@code layers}, and each mooring a target at sea
	 * is boarded at, seeded with a bound on boarding and sailing from it. With neither, no walk leads anywhere, as when
	 * a boat aboard can only sail straight to its targets: the start, never reached.
	 */
	public HeuristicTargets heuristicTargets(int[] tiles, int[] layers)
	{
		Map<Integer, Integer> boardings = sea == null ? Map.of() : sea.boardingBounds();
		if (tiles.length + boardings.size() == 0)
			return new HeuristicTargets(new int[] {start}, new int[] {ANY_BANK_STATE}, new int[] {CostArithmetic.INF});
		int total = tiles.length + boardings.size();
		int[] allTiles = java.util.Arrays.copyOf(tiles, total);
		int[] allLayers = java.util.Arrays.copyOf(layers, total);
		int[] seedCosts = new int[total];
		int i = tiles.length;
		for (Map.Entry<Integer, Integer> boarding : boardings.entrySet())
		{
			allTiles[i] = boarding.getKey();
			allLayers[i] = ANY_BANK_STATE;
			seedCosts[i++] = boarding.getValue();
		}
		return new HeuristicTargets(allTiles, allLayers, seedCosts);
	}

	/** Time spent setting up the sea and drawing the route's stretches at sea, in nanoseconds. */
	public long seaNanos()
	{
		return sea == null ? 0 : sea.setupNanos() + fillNanos;
	}

	/**
	 * {@code path} with each stretch at sea sailed on the tiles ({@link SeaSearch#fill}); {@code path} itself, with
	 * every step where it was, if it has none.
	 */
	public SeaSearch.Filled withLegs(List<PathStep> path, BooleanSupplier cancelled)
	{
		long started = System.nanoTime();
		SeaSearch.Filled filled = sea == null ? null : sea.fill(path, cancelled);
		fillNanos += System.nanoTime() - started;
		if (filled != null) return filled;
		int[] same = new int[path.size()];
		for (int i = 0; i < same.length; i++) same[i] = i;
		return new SeaSearch.Filled(path, same);
	}
}
